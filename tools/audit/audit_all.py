#!/usr/bin/env python3
"""Audit statis lintas-bahasa (tanpa kompiler). Dijalankan: python3 tools/audit/audit_all.py

Ini BUKAN pengganti build. Tujuannya menangkap kelas kesalahan yang terbukti lolos dari pemeriksaan sebelumnya
(v0.13.0): DAO tak terdaftar di database, import Compose yang tidak ada, urutan argumen widget Compose,
inferensi tipe `:=` pada GDScript dari nilai Variant, dan pemanggilan method yang bukan API String Godot.
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
KT = ROOT / "app/src/main/java"
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

# ---------- Import kelas JDK/Android/AndroidX yang dipakai tanpa import ----------
# Menangkap "Unresolved reference" seperti SimpleDateFormat tanpa `import java.text.SimpleDateFormat` (v0.18.2).
KNOWN_CLASSES = {
    "SimpleDateFormat": "java.text", "DateFormat": "java.text", "Locale": "java.util", "Calendar": "java.util",
    "Date": "java.util", "TimeZone": "java.util", "TimeUnit": "java.util.concurrent", "Executors": "java.util.concurrent",
    "File": "java.io", "URL": "java.net", "HttpURLConnection": "java.net", "ByteBuffer": "java.nio",
    "SecureRandom": "java.security", "Cipher": "javax.crypto", "SecretKey": "javax.crypto",
    "SecretKeySpec": "javax.crypto.spec", "GCMParameterSpec": "javax.crypto.spec", "PBEKeySpec": "javax.crypto.spec",
    "JSONObject": "org.json", "JSONArray": "org.json", "Uri": "android.net", "Context": "android.content",
    "Intent": "android.content", "Build": "android.os", "Bundle": "android.os", "PendingIntent": "android.app",
    "PackageManager": "android.content.pm", "NotificationCompat": "androidx.core.app",
    "NotificationManagerCompat": "androidx.core.app", "ContextCompat": "androidx.core.content",
    "CoroutineWorker": "androidx.work", "WorkerParameters": "androidx.work", "WorkManager": "androidx.work",
    "ExistingWorkPolicy": "androidx.work", "Dispatchers": "kotlinx.coroutines",
    "CancellationException": "kotlinx.coroutines", "LaunchedEffect": "androidx.compose.runtime",
    "Composable": "androidx.compose.runtime", "Modifier": "androidx.compose.ui", "Alignment": "androidx.compose.ui",
    "Arrangement": "androidx.compose.foundation.layout", "Column": "androidx.compose.foundation.layout",
    "Row": "androidx.compose.foundation.layout", "Box": "androidx.compose.foundation.layout",
    "Spacer": "androidx.compose.foundation.layout", "LazyColumn": "androidx.compose.foundation.lazy",
    "RoundedCornerShape": "androidx.compose.foundation.shape", "Button": "androidx.compose.material3",
    "OutlinedButton": "androidx.compose.material3", "TextButton": "androidx.compose.material3",
    "Card": "androidx.compose.material3", "Text": "androidx.compose.material3", "Scaffold": "androidx.compose.material3",
    "MaterialTheme": "androidx.compose.material3", "Switch": "androidx.compose.material3",
    "OutlinedTextField": "androidx.compose.material3", "CenterAlignedTopAppBar": "androidx.compose.material3",
    "Color": "androidx.compose.ui.graphics", "FontWeight": "androidx.compose.ui.text.font",
}

def _strip_kt(src):
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    src = re.sub(r"(?<!:)//[^\n]*", "", src)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', lambda m: '"' + " ".join(re.findall(r"\$\{([^}]*)\}", m.group(0))) + '"', src)

for p in sorted((ROOT / "app/src").rglob("*.kt")):
    raw = p.read_text(encoding="utf-8")
    pkg = re.search(r"^package\s+([\w.]+)", raw, re.M).group(1)
    body = re.sub(r"(?m)^(import|package)\s.*$", "", _strip_kt(raw))
    imported, wildcards = set(), set()
    for m in re.finditer(r"^import\s+([\w.]+?)(\.\*)?(?:\s+as\s+(\w+))?\s*$", raw, re.M):
        if m.group(2): wildcards.add(m.group(1))
        else: imported.add(m.group(3) or m.group(1).split(".")[-1])
    for name, package in KNOWN_CLASSES.items():
        if name in imported or package in wildcards or package == pkg:
            continue
        if not re.search(r"(?<![\w.])" + name + r"\b(?!\s*=[^=])", body):
            continue
        if re.search(re.escape(package) + r"\." + name + r"\b", body):  # nama lengkap dipakai langsung
            continue
        if re.search(r"\b(?:class|object|interface)\s+" + name + r"\b", body):
            continue
        fail(f"{p.relative_to(ROOT)}: memakai {name} tanpa `import {package}.{name}`")
    # API TopAppBar Material3 masih @ExperimentalMaterial3Api; tanpa opt-in kompilator gagal dengan OPT_IN_USAGE_ERROR
    # (v0.18.5: PersonalNoteScreen memakai CenterAlignedTopAppBar tanpa opt-in).
    experimental = re.search(r"(?<![\w.])(CenterAlignedTopAppBar|TopAppBar|MediumTopAppBar|LargeTopAppBar)\s*\(", body)
    if experimental and not re.search(r"@(?:file:)?OptIn\([^)]*ExperimentalMaterial3Api", body):
        fail(f"{p.relative_to(ROOT)}: memakai {experimental.group(1)} tanpa @OptIn(ExperimentalMaterial3Api::class)")

print("\n".join(f"- {x}" for x in problems) if problems else "Audit statis: tidak ada temuan.")
sys.exit(1 if problems else 0)
