#!/usr/bin/env python3
"""Heuristic Kotlin checker for environments without a Kotlin compiler.

It is NOT a compiler. It catches the mistakes that most often break a Gradle build after hand edits:
  1. imports of project symbols that do not exist
  2. identifiers used without an import (e.g. BuildConfig, Modifier extensions, `by` delegates)
  3. calls to project functions/constructors/object members with a wrong argument count or unknown named argument
  4. members used on project objects that are not declared
Exit code 1 on any finding. A pass does not prove the code compiles.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app" / "src"
PKG_ROOT = "com.xnvalabs.smarteyex"
KT = sorted(SRC.rglob("*.kt"))

findings = []


def strip_code(text):
    """Blank out comments and string/char literals but keep offsets and newlines."""
    out = list(text)
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            j = text.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                out[k] = " "
            i = j
        elif c == "/" and nxt == "*":
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if text[k] != "\n":
                    out[k] = " "
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            for k in range(i, j):
                if text[k] != "\n":
                    out[k] = " "
            i = j
        elif c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                if text[j] == "\\":
                    j += 1
                j += 1
            for k in range(i + 1, min(j, n)):
                out[k] = " "
            i = j + 1
        elif c == "'" and i + 2 < n and (text[i + 2] == "'" or (text[i + 1] == "\\" and i + 3 < n and text[i + 3] == "'")):
            j = text.find("'", i + 2 if text[i + 1] != "\\" else i + 3)
            for k in range(i + 1, j):
                out[k] = " "
            i = j + 1
        else:
            i += 1
    return "".join(out)


def matching(text, open_idx, o="(", c=")"):
    depth = 0
    for i in range(open_idx, len(text)):
        if text[i] == o:
            depth += 1
        elif text[i] == c:
            depth -= 1
            if depth == 0:
                return i
    return -1


def split_top(s):
    parts, depth, cur = [], 0, ""
    for ch in s:
        if ch in "([{<" and not (ch == "<" and False):
            depth += 1 if ch != "<" else 0
        if ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


class FileInfo:
    def __init__(self, path):
        self.path = path
        self.raw = path.read_text(errors="ignore")
        self.code = strip_code(self.raw)
        m = re.search(r"^\s*package\s+([\w.]+)", self.code, re.M)
        self.pkg = m.group(1) if m else ""
        self.imports = {}  # simple/alias -> full
        self.import_lines = []
        for im in re.finditer(r"^\s*import\s+([\w.*]+)(?:\s+as\s+(\w+))?\s*$", self.code, re.M):
            full, alias = im.group(1), im.group(2)
            simple = alias or full.split(".")[-1]
            self.imports[simple] = full
            self.import_lines.append(full)
        self.rel = path.relative_to(ROOT)


files = [FileInfo(p) for p in KT]

DECL = re.compile(
    r"^(?P<ind>[ \t]*)(?:(?:private|internal|public|protected|data|sealed|enum|abstract|open|inline|value|annotation|const|lateinit|override|suspend|operator|infix|tailrec|external|fun)\s+)*"
    r"(?P<kind>class|interface|object|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(?P<name>[\w.]+)",
    re.M,
)


def decl_kind_line(line_match):
    return line_match.group("kind")


# ---- 1. collect top-level declarations per package --------------------------------------------
top = {}  # (pkg, name) -> list of (kind, FileInfo, match)
members = {}  # (pkg, objname) -> set of member names (objects/classes)
callables = {}  # (pkg, name) -> list of param specs for top-level funs and class constructors


def parse_params(code, open_idx):
    close = matching(code, open_idx)
    if close < 0:
        return None, -1
    inner = code[open_idx + 1:close]
    specs = []
    for part in split_top(inner):
        p = part.strip()
        if not p:
            continue
        p = re.sub(r"^(?:(?:private|public|internal|protected|override|vararg|noinline|crossinline|val|var)\s+)+", "", p)
        m = re.match(r"(\w+)\s*:", p)
        if not m:
            continue
        has_default = "=" in re.sub(r"\([^)]*\)|<[^>]*>|\{[^}]*\}", "", p.split(":", 1)[1]) or bool(re.search(r"\s=\s", p.split(":", 1)[1]))
        specs.append((m.group(1), has_default, "vararg" in part.split(":")[0]))
    return specs, close


def block_end(code, idx):
    b = code.find("{", idx)
    return matching(code, b, "{", "}") if b >= 0 else -1


for f in files:
    for m in DECL.finditer(f.code):
        if m.group("ind"):
            continue
        kind, name = m.group("kind"), m.group("name")
        name = name.split(".")[-1] if kind == "fun" else name
        top.setdefault((f.pkg, name), []).append((kind, f, m))
        if kind in ("class", "object", "interface"):
            end = block_end(f.code, m.end())
            body = f.code[m.end():end] if end > 0 else ""
            mem = set()
            depth = 0
            for line in body.splitlines():
                ldepth = depth
                depth += line.count("{") - line.count("}")
                if ldepth == 1 or (ldepth == 0 and False):
                    for dm in re.finditer(
                        r"^\s*(?:(?:private|internal|public|protected|override|open|abstract|const|lateinit|suspend|inline|operator|data|enum|sealed|value|annotation)\s+)*"
                        r"(?:fun|val|var|class|object|interface)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)",
                        line,
                    ):
                        mem.add(dm.group(1))
                if ldepth == 1:
                    pass
            # enum entries
            if re.search(r"enum\s+class\s+" + re.escape(name), f.code[m.start():m.end() + 5] ):
                pass
            for em in re.finditer(r"^\s{4}([A-Z][A-Z0-9_]*)\s*(?:\(|,|;|$)", body, re.M):
                mem.add(em.group(1))
            # constructor properties
            members[(f.pkg, name)] = mem
        if kind in ("fun", "class"):
            tail = f.code[m.end():]
            pm = re.match(r"\s*(?:<[^>]*>)?\s*\(", tail)
            if pm:
                specs, _ = parse_params(f.code, m.end() + pm.end() - 1)
                if specs is not None:
                    callables.setdefault((f.pkg, name), []).append(specs)
                    if kind == "class":
                        for s in specs:
                            members.setdefault((f.pkg, name), set()).add(s[0])
                        members.setdefault((f.pkg, name), set()).update({"copy", "component1", "equals", "hashCode", "toString"})

# object members declared in companion objects count as members of the class
for f in files:
    for m in re.finditer(r"companion\s+object\s*\{", f.code):
        pass

# ---- 2. project imports resolve ----------------------------------------------------------------
for f in files:
    for full in f.import_lines:
        if not full.startswith(PKG_ROOT + "."):
            continue
        if full.endswith(".*") or full in (PKG_ROOT + ".BuildConfig", PKG_ROOT + ".R"):
            continue  # generated by AGP
        parts = full.split(".")
        found = False
        for cut in range(len(parts) - 1, 0, -1):
            pkg, name = ".".join(parts[:cut]), parts[cut]
            if (pkg, name) in top:
                found = True
                break
        if not found:
            findings.append(f"{f.rel}: import tidak ditemukan di proyek: {full}")

# ---- 3. external imports: record which are new vs original baseline --------------------------
external = {}
for f in files:
    for full in f.import_lines:
        if not full.startswith(PKG_ROOT + "."):
            external.setdefault(full, set()).add(str(f.rel))

# ---- 4. identifiers used without import -------------------------------------------------------
simple_to_full = {}
for full in external:
    simple_to_full.setdefault(full.split(".")[-1], set()).add(full)

IMPLICIT = {
    # kotlin / java.lang / auto-imported
    "String", "Int", "Long", "Float", "Double", "Boolean", "Char", "Byte", "Short", "Any", "Unit", "Nothing", "Array", "List", "Map", "Set",
    "MutableList", "MutableMap", "MutableSet", "Pair", "Triple", "Result", "Throwable", "Exception", "RuntimeException", "IllegalStateException",
    "IllegalArgumentException", "Regex", "Math", "System", "Thread", "Runnable", "StringBuilder", "Comparable", "Number", "Collection", "Iterable",
    "Sequence", "IntArray", "LongArray", "FloatArray", "DoubleArray", "BooleanArray", "ByteArray", "CharArray", "Deprecated", "Suppress", "JvmStatic",
    "Volatile", "Synchronized", "OptIn", "Target", "Override", "SuppressWarnings", "Error", "NullPointerException", "UnsupportedOperationException",
    "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet", "Lazy", "Function", "Void", "Enum", "Iterator", "CharSequence", "Class",
    "Process", "Object", "Appendable", "ArithmeticException", "IndexOutOfBoundsException", "NumberFormatException", "ClassCastException", "Cloneable",
    "Character", "Integer", "Boolean", "Annotation", "Readable", "Record", "Runtime", "SecurityException", "StackTraceElement", "StrictMath",
    "StringBuffer", "ThreadLocal", "Iterable", "AutoCloseable", "InterruptedException", "OutOfMemoryError", "AssertionError", "NoSuchElementException",
    "ConcurrentModificationException", "NoSuchFieldException", "ClassNotFoundException", "CloneNotSupportedException",
    "ExceptionInInitializerError", "StackOverflowError", "UnsupportedOperationException", "BuildConfig",
}
BUILDCONFIG_FULL = PKG_ROOT + ".BuildConfig"

# extension / function names (lowercase) that need explicit imports when used as `.name(` or `name(`
CALLISH_EXT = {s for s, fulls in simple_to_full.items() if s[:1].islower() and len(fulls) == 1}
UPPER_KNOWN = {s for s, fulls in simple_to_full.items() if s[:1].isupper() and len(fulls) == 1}
# lowercase names that are also ordinary members and therefore ambiguous when used without parentheses
for f in files:
    code = f.code
    declared_here = set()
    for m in DECL.finditer(code):
        declared_here.add(m.group("name").split(".")[-1])
    # local/nested declarations anywhere in file count as "defined here"
    for m in re.finditer(r"\b(?:val|var|fun|class|object|interface)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)", code):
        declared_here.add(m.group(1))
    for m in re.finditer(r"[(,]\s*(\w+)\s*:", code):
        declared_here.add(m.group(1))
    for em in re.finditer(r"enum\s+class\s+\w+[^{]*\{([^}]*)\}", code):
        declared_here |= set(re.findall(r"\b([A-Za-z_]\w*)\b", em.group(1)))
    same_pkg = {n for (p, n) in top if p == f.pkg}
    imported = set(f.imports)
    wildcard_pkgs = {full[:-2] for full in f.import_lines if full.endswith(".*")}

    # BuildConfig
    if re.search(r"\bBuildConfig\b", code) and "BuildConfig" not in imported and f.pkg != PKG_ROOT:
        findings.append(f"{f.rel}: BuildConfig dipakai tanpa import {BUILDCONFIG_FULL}")

    # uppercase identifiers
    used_upper = set(re.findall(r"(?<![\w.])([A-Z][A-Za-z0-9_]*)\b", code))
    used_upper |= set(re.findall(r"\.\s*([A-Z][A-Za-z0-9_]*)\b(?=\s*[.(])", code)) - set()
    for name in sorted(used_upper):
        if name in IMPLICIT or name in imported or name in declared_here or name in same_pkg:
            continue
        if name in UPPER_KNOWN and not wildcard_pkgs:
            # only complain for standalone (not following a dot) usage
            if re.search(r"(?<![\w.])" + re.escape(name) + r"\b", code):
                findings.append(f"{f.rel}: '{name}' dipakai tanpa import (biasanya {next(iter(simple_to_full[name]))})")

    # lowercase call-like names
    ARG_REQUIRED = {"height", "width", "size", "padding", "offset", "background", "border", "clip", "alpha", "weight", "shadow", "scale", "rotate"}
    for name in sorted(CALLISH_EXT):
        if name in imported or name in declared_here or name in same_pkg or name == "launch":
            continue
        pat = re.compile(r"(?<![\w.])" + re.escape(name) + r"\s*(?:\(|\{)")
        pat2 = re.compile(r"(?<![\w.])[\w]+\s*\.\s*" + re.escape(name) + r"\s*(?:\(|\{)")
        hit = False
        for mm in pat.finditer(code):
            hit = True
        for mm in re.finditer(r"([\w.]*)\.\s*" + re.escape(name) + r"\s*(\(\s*\)|\(|\{)", code):
            chain, opener = mm.group(1), mm.group(2)
            root = chain.split(".")[0] if chain else ""
            if root in {"kotlinx", "androidx", "android", "java", "javax", "kotlin", "com", "org"}:
                continue  # fully-qualified call
            if name in ARG_REQUIRED and opener.replace(" ", "") == "()":
                continue  # member such as Rect.height()
            hit = True
        if hit:
            full = next(iter(simple_to_full[name]))
            if full.startswith(("androidx.", "kotlinx.coroutines")):
                findings.append(f"{f.rel}: '{name}(' dipakai tanpa import {full}")

    # delegates
    if re.search(r"\b(?:val|var)\s+\w+(?:\s*:\s*[\w<>?, ]+)?\s+by\s+(?:remember|mutableStateOf|rememberSaveable|derivedStateOf|produceState|collectAsState)", code):
        if "getValue" not in imported and "androidx.compose.runtime.*" not in f.import_lines:
            findings.append(f"{f.rel}: delegate 'by' tanpa import androidx.compose.runtime.getValue")
    if re.search(r"\bvar\s+\w+(?:\s*:\s*[\w<>?, ]+)?\s+by\s+(?:remember|mutableStateOf|rememberSaveable)", code):
        if "setValue" not in imported and "androidx.compose.runtime.*" not in f.import_lines:
            findings.append(f"{f.rel}: 'var ... by' tanpa import androidx.compose.runtime.setValue")

# ---- 5. calls: argument counts and object members ----------------------------------------------
def check_call(f, name, specs_list, args_text, pos, trailing=False):
    parts = [p for p in split_top(args_text) if p.strip()]
    named = []
    positional = 0
    for p in parts:
        m = re.match(r"\s*(\w+)\s*=(?!=)", p)
        if m:
            named.append(m.group(1))
        else:
            positional += 1
    trailing_extra = 1 if trailing else 0  # a trailing lambda binds to the LAST parameter
    best_err = None
    for specs in specs_list:
        names = [s[0] for s in specs]
        has_vararg = any(s[2] for s in specs)
        if any(n not in names for n in named) and not has_vararg:
            best_err = best_err or f"argumen bernama tidak dikenal {[n for n in named if n not in names]}"
            continue
        if positional + trailing_extra > len(specs) and not has_vararg:
            best_err = best_err or f"terlalu banyak argumen ({positional + trailing_extra} > {len(specs)})"
            continue
        required = [s[0] for s in specs if not s[1] and not s[2]]
        provided = set(named) | {names[i] for i in range(min(positional, len(names)))}
        if trailing and names:
            provided.add(names[-1])
        missing = [r for r in required if r not in provided]
        if missing:
            best_err = best_err or f"argumen wajib hilang {missing}"
            continue
        return None
    return best_err


for f in files:
    code = f.code
    # object-member calls: Obj.name(...)
    for (pkg, obj), mem in list(members.items()):
        kinds = {k for (k, _, _) in top.get((pkg, obj), [])}
        if "object" not in kinds:
            continue
        visible = (f.pkg == pkg) or (obj in f.imports and f.imports[obj] == f"{pkg}.{obj}")
        if not visible:
            continue
        for m in re.finditer(r"(?<![\w.])" + re.escape(obj) + r"\.(\w+)", code):
            member = m.group(1)
            if member not in mem and member not in {"toString", "hashCode", "equals", "javaClass"}:
                line = code.count("\n", 0, m.start()) + 1
                findings.append(f"{f.rel}:{line}: {obj}.{member} tidak dideklarasikan di object {obj}")

    # top-level function / constructor calls
    for (pkg, name), spec_list in callables.items():
        visible = (f.pkg == pkg) or (name in f.imports and f.imports[name] == f"{pkg}.{name}")
        if not visible:
            continue
        # skip names that are also declared as local/other symbols of different arity in this file (overloads, locals)
        for m in re.finditer(r"(?<![\w.])" + re.escape(name) + r"\s*\(", code):
            before = code[max(0, m.start() - 12):m.start()]
            if re.search(r"(?:fun|class|object|interface)\s+(?:<[^>]*>\s*)?$", before):
                continue
            open_idx = m.end() - 1
            close = matching(code, open_idx)
            if close < 0:
                continue
            args = code[open_idx + 1:close]
            trailing_lambda = re.match(r"\s*\{", code[close + 1:close + 40]) is not None
            err = check_call(f, name, spec_list, args, m.start(), trailing_lambda)
            if err:
                line = code.count("\n", 0, m.start()) + 1
                findings.append(f"{f.rel}:{line}: panggilan {name}(...) {err}")

# object function-call arity: Obj.fn(args)
obj_funs = {}
for f in files:
    for m in DECL.finditer(f.code):
        if m.group("ind") or m.group("kind") != "object":
            continue
        name = m.group("name")
        end = block_end(f.code, m.end())
        if end < 0:
            continue
        body_start = f.code.find("{", m.end())
        body = f.code[body_start:end]
        depth = 0
        pos = 0
        for fm in re.finditer(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\(", body):
            d = body.count("{", 0, fm.start()) - body.count("}", 0, fm.start())
            if d != 1:
                continue
            specs, _ = parse_params(body, fm.end() - 1)
            if specs is not None:
                obj_funs.setdefault((f.pkg, name, fm.group(1)), []).append(specs)

for f in files:
    code = f.code
    for (pkg, obj, fn), spec_list in obj_funs.items():
        visible = (f.pkg == pkg) or (obj in f.imports and f.imports[obj] == f"{pkg}.{obj}")
        if not visible:
            continue
        for m in re.finditer(r"(?<![\w.])" + re.escape(obj) + r"\." + re.escape(fn) + r"\s*\(", code):
            open_idx = m.end() - 1
            close = matching(code, open_idx)
            if close < 0:
                continue
            args = code[open_idx + 1:close]
            trailing_lambda = re.match(r"\s*\{", code[close + 1:close + 40]) is not None
            err = check_call(f, f"{obj}.{fn}", spec_list, args, m.start(), trailing_lambda)
            if err:
                line = code.count("\n", 0, m.start()) + 1
                findings.append(f"{f.rel}:{line}: panggilan {obj}.{fn}(...) {err}")

# ---- report -----------------------------------------------------------------------------------
if "--list-external" in sys.argv:
    for full in sorted(external):
        print(full)
    sys.exit(0)

seen = set()
unique = [x for x in findings if not (x in seen or seen.add(x))]
print(f"Kotlin files checked: {len(files)}")
print(f"Findings: {len(unique)}")
for item in unique:
    print(" -", item)
if unique:
    sys.exit(1)
print("KOTLIN_STATIC_CHECK=PASS (heuristic; bukan pengganti compiler)")
