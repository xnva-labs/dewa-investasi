#!/usr/bin/env python3
"""Download and normalize maximally useful open Indonesian language resources.

This deliberately DOES NOT download or redistribute KBBI content. KBBI remains a
copyrighted resource whose redistribution requires appropriate authorization.
"""
from __future__ import annotations
import argparse, bz2, hashlib, json, re, sys, time
from pathlib import Path
from urllib.request import Request, urlopen

SOURCES = {
    "malindo-morph-id": {
        "url": "https://raw.githubusercontent.com/matbahasa/MALINDO_Morph/master/malindo_dic_2024.tsv",
        "license": "CC BY 4.0",
        "kind": "malindo",
    },
    "wordnet-bahasa-id": {
        "url": "https://raw.githubusercontent.com/limaginaire/Bahasa-Wordnet/master/wn-msa-all.tab",
        "license": "MIT",
        "kind": "wordnet",
    },
    "apertium-ind": {
        "url": "https://raw.githubusercontent.com/apertium/apertium-ind/master/apertium-ind.ind.dix",
        "license": "GPL-3.0",
        "kind": "apertium",
    },
    "apertium-ind-eng": {
        "url": "https://raw.githubusercontent.com/apertium/apertium-ind-eng/master/apertium-ind-eng.ind-eng.dix",
        "license": "GPL-3.0",
        "kind": "apertium",
    },
    "talpco-id": {
        "url": "https://raw.githubusercontent.com/matbahasa/TALPCo/master/ind/data_ind.txt",
        "license": "CC BY 4.0",
        "kind": "sentences",
    },
    "kaikki-id": {
        "url": "https://kaikki.org/idwiktionary/Bahasa%20Indonesia/kaikki.org-dictionary-BahasaIndonesia.jsonl",
        "license": "Wiktionary CC BY-SA + GFDL",
        "kind": "kaikki",
    },
    "hunspell-id": {
        "url": "https://raw.githubusercontent.com/titoBouzout/Dictionaries/master/Indonesia.dic",
        "license": "MPLv2/LGPLv3+ per upstream metadata; keep notices/licenses",
        "kind": "hunspell",
    },
    "ud-id-gsd-train": {
        "url": "https://raw.githubusercontent.com/UniversalDependencies/UD_Indonesian-GSD/master/id_gsd-ud-train.conllu",
        "license": "CC BY-SA 4.0",
        "kind": "conllu",
    },
    "ud-id-gsd-dev": {
        "url": "https://raw.githubusercontent.com/UniversalDependencies/UD_Indonesian-GSD/master/id_gsd-ud-dev.conllu",
        "license": "CC BY-SA 4.0",
        "kind": "conllu",
    },
    "ud-id-gsd-test": {
        "url": "https://raw.githubusercontent.com/UniversalDependencies/UD_Indonesian-GSD/master/id_gsd-ud-test.conllu",
        "license": "CC BY-SA 4.0",
        "kind": "conllu",
    },
    "tatoeba-id": {
        "url": "https://downloads.tatoeba.org/exports/per_language/ind/ind_sentences.tsv.bz2",
        "license": "CC BY 2.0 default; preserve per-sentence attribution/licensing",
        "kind": "tatoeba",
    },
}
TOKEN_RE = re.compile(r"[^\W_]+(?:[-'][^\W_]+)*", re.UNICODE)

def download(url: str, dest: Path) -> None:
    req = Request(url, headers={"User-Agent": "XNAI-language-bootstrap/1.0"})
    with urlopen(req, timeout=120) as r, dest.open("wb") as w:
        while True:
            b = r.read(1024 * 1024)
            if not b:
                break
            w.write(b)

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()

def extract_words(out: Path, source_id: str, path: Path) -> tuple[int, int]:
    words: dict[str, None] = {}
    records = 0
    if source_id == "kaikki-id":
        with path.open(encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue
                try:
                    obj = json.loads(line)
                except json.JSONDecodeError:
                    continue
                word = str(obj.get("word", "")).strip().lower()
                if word:
                    words[word] = None
                records += 1
    elif source_id.startswith("ud-id-gsd"):
        with path.open(encoding="utf-8") as f:
            for line in f:
                if not line.strip() or line.startswith("#"):
                    continue
                cols = line.rstrip("\n").split("\t")
                if len(cols) >= 3 and re.fullmatch(r"\d+(?:-\d+)?", cols[0]):
                    for field in (cols[1], cols[2]):
                        if field and field != "_":
                            words[field.lower()] = None
                records += 1
    elif source_id == "malindo-morph-id":
        with path.open(encoding="utf-8", errors="replace") as f:
            for line in f:
                cols = line.rstrip("\n").split("\t")
                if len(cols) >= 2:
                    for field in cols[:2]:
                        if field.strip():
                            words[field.strip().lower()] = None
                records += 1
    elif source_id == "wordnet-bahasa-id":
        with path.open(encoding="utf-8", errors="replace") as f:
            for line in f:
                cols = line.rstrip("\n").split("\t")
                if len(cols) >= 4 and cols[1].strip() == "I" and cols[3].strip():
                    words[cols[3].strip().lower()] = None
                records += 1
    elif source_id in {"apertium-ind", "apertium-ind-eng"}:
        import html
        xml = path.read_text(encoding="utf-8", errors="replace")
        # DIX files are LT XML. For the monolingual file, take <l>; for the
        # Indonesian-English pair, take <r> to avoid contaminating the
        # Indonesian lexical registry with English headwords.
        side = "r" if source_id == "apertium-ind-eng" else "l"
        pattern = re.compile(rf"<(?:{side})[^>]*>(.*?)</(?:{side})>", re.DOTALL | re.IGNORECASE)
        for match in pattern.finditer(xml):
            value = re.sub(r"<[^>]+>", "", match.group(1))
            value = html.unescape(value).strip().lower()
            if value and len(value) <= 120:
                words[value] = None
            records += 1
    elif source_id == "talpco-id":
        with path.open(encoding="utf-8", errors="replace") as f:
            for line in f:
                if line.strip() and not line.lstrip().startswith("#"):
                    for token in TOKEN_RE.findall(line.lower()):
                        words[token] = None
                records += 1
    elif source_id == "hunspell-id":
        with path.open(encoding="utf-8", errors="replace") as f:
            for i, line in enumerate(f):
                if i == 0 and line.strip().isdigit():
                    continue
                word = line.split("/", 1)[0].strip().lower()
                if word and not word.startswith("#"):
                    words[word] = None
                records += 1
    elif source_id == "tatoeba-id":
        with bz2.open(path, "rt", encoding="utf-8") as f:
            for line in f:
                cols = line.rstrip("\n").split("\t")
                if len(cols) >= 2 and cols[1].strip():
                    for token in TOKEN_RE.findall(cols[1].lower()):
                        words[token] = None
                records += 1
    out.mkdir(parents=True, exist_ok=True)
    existing = set()
    target = out / "language_words.tsv"
    if target.exists():
        with target.open(encoding="utf-8") as f:
            for line in f:
                if "\t" in line:
                    existing.add(line.split("\t", 1)[0])
    new = [w for w in words if w not in existing]
    with target.open("a", encoding="utf-8") as f:
        for w in new:
            f.write(w + "\t" + source_id + "\n")
    return records, len(new)

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="XNAI_LIBRARY_LANGUAGE")
    ap.add_argument("--source", action="append", choices=list(SOURCES), help="repeat for selected source; default all")
    ap.add_argument("--download", action="store_true", help="download source archives")
    args = ap.parse_args()
    out = Path(args.out)
    cache = out / "cache"
    selected = args.source or list(SOURCES)
    manifest = {"generatedAt": int(time.time() * 1000), "sources": []}
    for sid in selected:
        meta = SOURCES[sid]
        path = cache / sid.replace("/", "_")
        if args.download or not path.exists():
            path.parent.mkdir(parents=True, exist_ok=True)
            print("Downloading", sid, meta["url"])
            download(meta["url"], path)
        records, new_words = extract_words(out, sid, path)
        manifest["sources"].append({
            "id": sid,
            "url": meta["url"],
            "license": meta["license"],
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
            "records": records,
            "newWords": new_words,
        })
        print(sid, "records=", records, "newWords=", new_words)
    (out / "language_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Wrote", out / "language_manifest.json")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
