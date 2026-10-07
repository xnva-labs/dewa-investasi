#!/usr/bin/env python3
"""Audit statis lintas-bahasa (tanpa kompiler). Dijalankan: python3 tools/audit/audit_all.py

Ini BUKAN pengganti build. Tujuannya menangkap kelas kesalahan yang terbukti lolos dari pemeriksaan sebelumnya
(v0.13.0): DAO tak terdaftar di database, import Compose yang tidak ada, urutan argumen widget Compose,
inferensi tipe `:=` pada GDScript dari nilai Variant, dan pemanggilan method yang bukan API String Godot.
"""
import pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
KT = ROOT / "app/src/main/java"
GD = ROOT / "app/src/main/assets/scripts"
problems = []

def fail(msg): problems.append(msg)

# ---------- Kotlin ----------
daos = {}
for p in KT.rglob("*.kt"):
    t = p.read_text(encoding="utf-8")
    for m in re.finditer(r"@Dao\s*interface\s+(\w+)", t):
        daos[m.group(1)] = p
db_text = "".join(p.read_text(encoding="utf-8") for p in KT.rglob("*Database.kt"))
for dao in daos:
    if not re.search(r":\s*" + dao + r"\b", db_text):
        fail(f"DAO {dao} tidak diekspos oleh kelas Database (abstract fun xxxDao(): {dao})")

for p in KT.rglob("*.kt"):
    t = p.read_text(encoding="utf-8")
    rel = p.relative_to(ROOT)
    if re.search(r"^import\s+androidx\.compose\.foundation\.layout\.weight\b", t, re.M):
        fail(f"{rel}: import androidx.compose.foundation.layout.weight tidak ada (weight adalah member RowScope/ColumnScope)")
    if re.search(r"^import\s+androidx\.compose\.ui\.platform\.LocalLifecycleOwner\b", t, re.M):
        fail(f"{rel}: LocalLifecycleOwner lama; pakai androidx.lifecycle.compose.LocalLifecycleOwner")
    for m in re.finditer(r"\b(Button|OutlinedButton|TextButton|FilledTonalButton)\(", t):
        rest = t[m.end():].lstrip()
        ok = (rest.startswith("{") or re.match(r"(onClick|[a-zA-Z_]\w*)\s*=(?!=)", rest)
              or re.match(r"[a-zA-Z_]\w*\s*[,)]", rest) or re.match(r"[a-zA-Z_][\w.]*::", rest))
        if not ok:
            line = t.count("\n", 0, m.start()) + 1
            fail(f"{rel}:{line}: argumen pertama {m.group(1)} bukan onClick -> {rest[:60]!r}")
    for m in re.finditer(r"\b(ElevatedCard|Card|OutlinedCard)\(\s*Modifier[^\n]*onClick\s*=", t):
        line = t.count("\n", 0, m.start()) + 1
        fail(f"{rel}:{line}: Card(Modifier..., onClick=...) tidak valid; onClick harus argumen pertama")
    if "org.jetbrains.kotlin.android" in t and p.name.endswith(".kts"):
        fail(f"{rel}: plugin kotlin-android tidak kompatibel dengan AGP 9")

for p in list(ROOT.glob("*.kts")) + list((ROOT / "app").glob("*.kts")):
    t = p.read_text(encoding="utf-8")
    if re.search(r'id\("org\.jetbrains\.kotlin\.android"\)', t):
        fail(f"{p.relative_to(ROOT)}: plugin org.jetbrains.kotlin.android tidak boleh dipakai bersama AGP 9+")
    if "kotlinOptions" in t:
        fail(f"{p.relative_to(ROOT)}: kotlinOptions{{}} dihapus di DSL baru AGP 9; pakai kotlin {{ compilerOptions {{ }} }}")

# konstanta yang diuji harus sama dengan nilai sumber
bc = (KT / "id/fajar/zahra/backup/BackupContract.kt").read_text(encoding="utf-8")
schema = re.search(r"CURRENT_SCHEMA\s*=\s*(\d+)", bc).group(1)
for p in (ROOT / "app/src/test").rglob("BackupContractTest.kt"):
    m = re.search(r"assertEquals\((\d+),\s*BackupContract\.CURRENT_SCHEMA\)", p.read_text(encoding="utf-8"))
    if m and m.group(1) != schema:
        fail(f"{p.name}: menguji skema {m.group(1)} tetapi CURRENT_SCHEMA={schema}")

# ---------- GDScript ----------
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import gd_infer_check
STRING_ONLY_BAD = [".take(", ".drop(", ".trim(", ".isBlank(", ".isNotBlank(", ".isEmpty(", ".substring(", ".toInt(", ".toFloat(", ".contains_key("]
for p in GD.rglob("*.gd"):
    t = p.read_text(encoding="utf-8")
    rel = p.relative_to(ROOT)
    for no, name, line, why in gd_infer_check.analyse(str(p)):
        fail(f"{rel}:{no}: `:=` dari nilai Variant ({', '.join(why)}) -> {line[:90]}")
    for bad in STRING_ONLY_BAD:
        for m in re.finditer(re.escape(bad), t):
            fail(f"{rel}:{t.count(chr(10), 0, m.start()) + 1}: method {bad} bukan API Godot")
    if re.search(r"^\t", t, re.M) and re.search(r"^ {2,}\S", t, re.M):
        fail(f"{rel}: indentasi campur tab dan spasi")
    defined = set(re.findall(r"^\s*func\s+(\w+)", t, re.M))
    for m in re.finditer(r"^\s*(?:await\s+)?(_[a-z][a-z0-9_]*)\(", t, re.M):
        if m.group(1) not in defined and m.group(1) not in ("_init", "_ready", "_process"):
            fail(f"{rel}: fungsi privat {m.group(1)}() dipanggil tetapi tidak didefinisikan")

print("\n".join(f"- {x}" for x in problems) if problems else "Audit statis: tidak ada temuan.")
sys.exit(1 if problems else 0)
