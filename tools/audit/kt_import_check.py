import re, sys, os, glob

KOTLIN_BUILTIN = set("""String Int Long Double Float Boolean Char Byte Short Unit Any Nothing Array List MutableList Map MutableMap Set MutableSet
Pair Triple Regex Exception RuntimeException IllegalStateException IllegalArgumentException Throwable Thread Runnable System Math StringBuilder
Comparable Result Lazy Iterable Sequence Collection Number CharSequence IntArray LongArray ByteArray DoubleArray FloatArray BooleanArray Enum
Annotation Deprecated Suppress JvmStatic JvmField Volatile Synchronized Override Error UnsupportedOperationException NoSuchElementException
IndexOutOfBoundsException NullPointerException ClassCastException NumberFormatException ArithmeticException Void Object Class Process
Iterator Function Lazy Cloneable Serializable Appendable Readable Short ClosedRange IntRange LongRange UInt ULong UByte""".split())

def project_symbols(root):
    syms=set()
    for p in glob.glob(root+'/**/*.kt', recursive=True):
        t=open(p,encoding='utf-8').read()
        for m in re.finditer(r'\b(?:class|object|interface|typealias|enum class|data class|sealed class)\s+([A-Z][A-Za-z0-9_]*)', t): syms.add(m.group(1))
        for m in re.finditer(r'\bfun\s+(?:<[^>]+>\s*)?(?:[A-Za-z0-9_.<>?]+\.)?([A-Z][A-Za-z0-9_]*)\s*\(', t): syms.add(m.group(1))
        for m in re.finditer(r'\b(?:val|var)\s+([A-Z][A-Za-z0-9_]*)\b', t): syms.add(m.group(1))
        for m in re.finditer(r'\benum class\s+[A-Z]\w*\s*\(?[^{]*\{([^}]*)\}', t, re.S):
            for e in re.findall(r'\b([A-Z][A-Z0-9_]+)\b', m.group(1)): syms.add(e)
    return syms

def strip(t):
    t=re.sub(r'/\*.*?\*/','',t,flags=re.S)
    t=re.sub(r'//[^\n]*','',t)
    t=re.sub(r'"""(?:.|\n)*?"""','""',t)
    # string literals (keep ${} expressions roughly by dropping the literal)
    t=re.sub(r'"(?:\\.|[^"\\\n])*"','""',t)
    t=re.sub(r"'(?:\\.|[^'\\\n])'","''",t)
    return t

def check(path, symbols):
    raw=open(path,encoding='utf-8').read()
    t=strip(raw)
    imports=re.findall(r'^import\s+([\w.]+)(?:\.\*)?(?:\s+as\s+(\w+))?', raw, re.M)
    wild=[m for m in re.findall(r'^import\s+([\w.]+)\.\*', raw, re.M)]
    imported=set()
    for full,alias in imports:
        imported.add(alias or full.split('.')[-1])
    declared=set()
    for m in re.finditer(r'\b(?:class|object|interface|typealias)\s+([A-Z]\w*)', t): declared.add(m.group(1))
    for m in re.finditer(r'\bfun\s+(?:<[^>]+>\s*)?(?:[\w.<>?]+\.)?([A-Z]\w*)\s*\(', t): declared.add(m.group(1))
    for m in re.finditer(r'\b(?:val|var)\s+([A-Z]\w*)\b', t): declared.add(m.group(1))
    # nested/enum entries
    used=set(re.findall(r'(?<![\w.])([A-Z][A-Za-z0-9_]*)\s*(?:\(|<|\.|\{|::|\?|,|\)|=|\s*:)', t))
    # only consider identifiers used as types/calls: drop ALL_CAPS constants
    missing=[]
    for u in sorted(used):
        if u in imported or u in declared or u in symbols or u in KOTLIN_BUILTIN: continue
        if re.fullmatch(r'[A-Z][A-Z0-9_]+', u): continue
        if wild: # may come from wildcard import; flag softly
            missing.append(u+'*')
        else:
            missing.append(u)
    probs=[]
    # delegate operators
    if re.search(r'\bby\s+(?:remember|mutableStateOf|rememberSaveable|[\w.]+\.collectAsState)', t) or re.search(r'\bby\s+[\w.()]+collectAsState', t):
        if 'androidx.compose.runtime.getValue' not in raw and 'androidx.compose.runtime.*' not in raw: probs.append('butuh import androidx.compose.runtime.getValue')
        if re.search(r'\bvar\s+\w+\s*(?::[^=]+)?by\b', t) and 'androidx.compose.runtime.setValue' not in raw and 'androidx.compose.runtime.*' not in raw:
            probs.append('butuh import androidx.compose.runtime.setValue')
    # lowercase compose functions that need imports
    for fn,pkg in [('remember','androidx.compose.runtime.remember'),('mutableStateOf','androidx.compose.runtime.mutableStateOf'),('collectAsState','androidx.compose.runtime.collectAsState'),('rememberCoroutineScope','androidx.compose.runtime.rememberCoroutineScope'),('rememberSaveable','androidx.compose.runtime.saveable.rememberSaveable'),('mutableIntStateOf','androidx.compose.runtime.mutableIntStateOf'),('mutableLongStateOf','androidx.compose.runtime.mutableLongStateOf'),('derivedStateOf','androidx.compose.runtime.derivedStateOf'),('items','androidx.compose.foundation.lazy.items'),('itemsIndexed','androidx.compose.foundation.lazy.itemsIndexed'),('viewModel','androidx.lifecycle.viewmodel.compose.viewModel')]:
        if re.search(r'(?<![\w.])'+fn+r'\s*[({]', t) and fn not in imported and not any(pkg.startswith(w+'.') or pkg==w for w in wild):
            if re.search(r'fun\s+'+fn+r'\b', t): continue
            probs.append(f'pakai {fn}() tanpa import ({pkg})')
    return missing, probs

if __name__=='__main__':
    root=sys.argv[1]
    symbols=project_symbols(root)
    bad=0
    for p in sorted(glob.glob(root+'/**/*.kt', recursive=True)):
        if '/androidTest/' in p: pass
        miss,probs=check(p,symbols)
        if miss or probs:
            bad+=1
            print(os.path.relpath(p,root)); 
            if miss: print('   kemungkinan tidak ter-import:', ', '.join(miss))
            for pr in probs: print('   ',pr)
    print('selesai;', bad, 'file bertemuan')
