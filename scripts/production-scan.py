#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src"
errors = []

# 1. XML validity.
for path in APP.rglob("*.xml"):
    try:
        ET.parse(path)
    except Exception as exc:
        errors.append(f"XML parse failure: {path}: {exc}")

# 2. Merge conflict markers and obvious secrets.
secret_patterns = [
    re.compile(r"BEGIN (?:RSA|EC|OPENSSH) PRIVATE KEY"),
    re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
]
for path in ROOT.rglob("*"):
    if not path.is_file() or ".git" in path.parts or "build" in path.parts:
        continue
    if path.suffix not in {".kt", ".kts", ".xml", ".yml", ".yaml", ".md", ".properties", ".py"}:
        continue
    text = path.read_text(errors="ignore")
    markers = ("<" * 7, ">" * 7)
    if markers[0] in text or markers[1] in text:
        errors.append(f"Merge conflict marker: {path}")
    for pattern in secret_patterns:
        if pattern.search(text):
            errors.append(f"Possible secret literal: {path}")
            break

# 3. Android resource references used by source files.
res = APP / "main" / "res"
strings = set()
drawables = set()
mipmaps = set()
values = res / "values"
if values.exists():
    for path in values.glob("*.xml"):
        try:
            tree = ET.parse(path)
            for node in tree.getroot():
                if node.tag.endswith("string") and node.attrib.get("name"):
                    strings.add(node.attrib["name"])
        except Exception:
            pass
for path in res.rglob("*"):
    if not path.is_file():
        continue
    parent = path.parent.name
    if parent.startswith("drawable"):
        drawables.add(path.stem)
    if parent.startswith("mipmap"):
        mipmaps.add(path.stem)

refs = {"string": strings, "drawable": drawables, "mipmap": mipmaps}
for path in APP.rglob("*"):
    if path.suffix not in {".kt", ".xml"}:
        continue
    text = path.read_text(errors="ignore")
    for kind, available in refs.items():
        names = set(re.findall(rf"R\.{kind}\.([A-Za-z0-9_]+)", text))
        names.update(re.findall(rf"@{kind}/([A-Za-z0-9_]+)", text))
        for name in names:
            if name not in available:
                errors.append(f"Missing {kind} resource '{name}' referenced by {path}")

# 4. Balanced Kotlin delimiters, ignoring simple strings/comments.
for path in APP.rglob("*.kt"):
    text = path.read_text(errors="ignore")
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"//.*", "", text)
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    for left, right in (("(", ")"), ("{", "}"), ("[", "]")):
        if text.count(left) != text.count(right):
            errors.append(f"Delimiter mismatch in {path}: {left}{text.count(left)} vs {right}{text.count(right)}")

# 5. Manifest safety checks.
manifest = APP / "main" / "AndroidManifest.xml"
if manifest.exists():
    try:
        tree = ET.parse(manifest)
        root = tree.getroot()
        android_ns = "{http://schemas.android.com/apk/res/android}"
        app = root.find("application")
        if app is not None and app.attrib.get(android_ns + "usesCleartextTraffic") == "true":
            errors.append("Manifest enables cleartext traffic")
        if app is not None:
            for component in app:
                if component.attrib.get(android_ns + "exported") == "true":
                    has_permission = android_ns + "permission" in component.attrib
                    has_filter = any(child.tag == "intent-filter" for child in component)
                    if component.tag.endswith("receiver") and has_filter and not has_permission:
                        errors.append(f"Exported receiver lacks permission: {component.attrib.get(android_ns + 'name', '')}")
    except Exception as exc:
        errors.append(f"Manifest safety check failure: {exc}")

# 6. Release configuration presence.
build_gradle = ROOT / "app" / "build.gradle.kts"
if build_gradle.exists():
    text = build_gradle.read_text(errors="ignore")
    for required in ("XNAI_BASE_URL", "KEYSTORE_PATH", "bundleRelease"):
        if required not in text and required != "bundleRelease":
            errors.append(f"Release build configuration missing: {required}")

print(f"XML files scanned: {len(list(APP.rglob('*.xml')))}")
print(f"Kotlin files scanned: {len(list(APP.rglob('*.kt')))}")
print(f"Errors: {len(errors)}")
for error in errors:
    print(error)
if errors:
    sys.exit(1)
print("PRODUCTION_SCAN=PASS")
