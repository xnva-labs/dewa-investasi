# XNAI Maximum Indonesian Language Stack

This stack is designed to maximize Indonesian lexical, morphological, semantic, syntactic, parallel-text, and speech coverage using sources with an explicitly reusable license or a clearly documented external-access rule.

## 1. Lexical foundation

- Kaikki / Indonesian Wiktionary: machine-readable JSONL. Use for word forms and senses with Wiktionary's applicable CC BY-SA/GFDL terms.
- LibreOffice Indonesian Hunspell: spelling lexicon plus affix flags. Keep upstream notices and license files.
- MALINDO Morph 2024: root and surface forms with prefix, suffix, circumfix, and reduplication analysis. CC BY 4.0.
- Wordnet Bahasa: Indonesian/Malay semantic lexical graph with synset and quality information. MIT in the maintained raw-data mirror used by this project.

## 2. Sentence, grammar, and context

- Universal Dependencies Indonesian-GSD: CoNLL-U token, lemma, POS, morphology, and dependency annotations. CC BY-SA 4.0.
- TALPCo Indonesian: Indonesian parallel/sentence data. CC BY 4.0.
- Tatoeba Indonesian: sentence corpus with per-sentence licensing rules; preserve attribution and license metadata.
- Indonesian Wikipedia: live research source only. XNAI does not bulk-mirror the encyclopedia into the APK.

## 3. Speech

- Mozilla Common Voice Indonesian: CC0 dataset catalog, currently a large external speech dataset. Because Mozilla distributes datasets through its Data Collective and asks users not to mirror them elsewhere, XNAI keeps this as an external acquisition target rather than embedding or mirroring the dataset in this source tree.

## 4. Structured lexical/entity links

- Wikidata structured data: CC0 for applicable structured data. Use primarily for identifiers and relations, not as a prose dictionary.

## 5. KBBI

KBBI remains a primary reference for Indonesian lexicography, but the official KBBI Daring states that its contents are protected by copyright. XNAI therefore does not scrape, bypass access controls, or bundle an unlicensed complete KBBI dump. An authorized KBBI export can be imported through `XnaiDictionaryImporter` using the same stable numeric-ID pipeline.

## 6. Numbering contract

Base lexical IDs are intentionally minimal and sequential on first registration:

`mengapa = 1`

`gravitasi = 2`

`planet = 3`

The registration order is the only thing that determines the number. Existing numbers are immutable and are never renumbered. Composite symbols do not consume the lexical sequence. Composite symbols use separate stable non-sequential identifiers and point to their child IDs.

## 7. Autonomous growth

When XNAI encounters a new Indonesian form in an authorized source, it registers the unseen word with the next numeric ID, stores source/provenance information separately, and can connect the token to morphology, senses, synsets, sentence contexts, and other knowledge records. The small number remains the lexical key; descriptive metadata is stored outside the human-facing numeric representation.

## 8. Data size strategy

Logical library units are limited to 500 MiB. GitHub transport chunks are limited to 50 MiB. Large sources are streamed instead of loaded wholly into RAM. A source that is too large or subject to external distribution rules is referenced and acquired through its provider rather than copied into the APK.
