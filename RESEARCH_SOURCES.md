# XNAI Research / Verification Sources

These sources were used to validate the current architecture and dependency decisions against current documentation available in 2026.

## Android / Compose / persistence / background execution
- Compose setup and current BOM guidance: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Room release notes: https://developer.android.com/jetpack/androidx/releases/room
- WorkManager release notes: https://developer.android.com/jetpack/androidx/releases/work
- Foreground-service types/restrictions: https://developer.android.com/develop/background-work/services/fgs/service-types
- Background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android security crypto guidance: https://developer.android.com/reference/androidx/security/crypto/package-summary

## Vision
- ML Kit Text Recognition: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- ML Kit Object Detection: https://developers.google.com/ml-kit/vision/object-detection/android
- ML Kit Image Labeling: https://developers.google.com/ml-kit/vision/image-labeling/android
- ML Kit Face Detection: https://developers.google.com/ml-kit/vision/face-detection/android
- ML Kit Barcode Scanning: https://developers.google.com/ml-kit/vision/barcode-scanning/android
- OpenCV: https://docs.opencv.org/4.x/

## GitHub
- GitHub Repository Contents API: https://docs.github.com/en/rest/repos/contents
- GitHub REST API authentication / fine-grained permissions should be checked against the current docs when provisioning a token.

## Reasoning/agent research used as design inspiration
- ReAct: https://arxiv.org/abs/2210.03629
- Tree of Thoughts: https://arxiv.org/abs/2305.10601
- Self-Refine: https://arxiv.org/abs/2303.17651
- Reflexion: https://arxiv.org/abs/2303.11366

## Notes from current research
- Current Compose documentation recommends using a Compose BOM to keep Compose artifacts synchronized.
- Current WorkManager documentation lists 2.12.0 as stable as of September 23, 2026.
- Android foreground-service rules require explicit service types/permissions on recent targets; XNAI therefore uses WorkManager for scheduled autonomous cycles instead of pretending a permanently running background process is universally allowed.
- Android security-crypto helper classes such as `EncryptedSharedPreferences` and `MasterKey` are deprecated; the implementation stores secrets encrypted with an AES-GCM key managed directly by Android Keystore.
- ML Kit object detection is bounded in the documented API (up to five objects per image), so XNAI does not claim a universal every-object detector.
- GitHub repository contents APIs support fine-grained access with repository Contents permissions; write access must be granted deliberately for sync.

## Indonesian language resource research (checked 2026-10-04)
- Kaikki Indonesian machine-readable dictionary: https://kaikki.org/idwiktionary/Bahasa%20Indonesia/ . The page reports 47,407 distinct word forms, 79,814 senses, a 40.9 MB postprocessed JSONL download, based on a recent Indonesian Wiktionary dump. It follows Wiktionary licensing (CC BY-SA and GFDL).
- Universal Dependencies Indonesian-GSD: https://universaldependencies.org/treebanks/id_gsd/index.html . The current page lists CC BY-SA 4.0; the repository describes 5,598 sentences and about 122K words and includes lemmas/morphological features.
- Indonesian LibreOffice/Hunspell resource: https://github.com/titoBouzout/Dictionaries/blob/master/Indonesia.dic . Upstream metadata states post-2014 licensing under MPLv2/LGPLv3+ and the file has 31,129 lines.
- Tatoeba Indonesian export: https://downloads.tatoeba.org/exports/per_language/ind/ . The current per-language directory exposes Indonesian sentence exports, including `ind_sentences.tsv.bz2`; Tatoeba text contributions are attributed under its licensing rules, with CC BY 2.0 as the default for contributions.
- Wikidata licensing: https://www.wikidata.org/wiki/Wikidata:Licensing . Structured data is released under CC0.
- KBBI caution: official KBBI content is copyrighted; XNAI therefore does not scrape/bundle the full official KBBI automatically. A user-supplied authorized dataset can be imported through the same numeric lexicon pipeline.


## Expanded Indonesian language stack

- **MALINDO Morph 2024**: open morphological resource with root, surface form, prefix, suffix, circumfix, and reduplication fields. Source repository: https://github.com/matbahasa/MALINDO_Morph . Current SEACrowd card lists CC BY 4.0; the Indonesian-language Badan Bahasa discussion describes the historical resource as a large open Indonesian/Malay morphology dictionary.
- **Wordnet Bahasa**: Indonesian/Malay semantic lexical graph. The maintained raw-data mirror states MIT; the project reports about 49,668 synsets, 145,696 senses, and 64,431 unique words.
- **Mozilla Common Voice Indonesian**: current catalog lists Indonesian scripted speech at about 1.43 GB under CC0-1.0. Mozilla's terms say datasets are distributed through MDC and should not be mirrored elsewhere, so XNAI keeps this as an external acquisition target rather than packaging it in the APK.

- **TALPCo**: TUFS Asian Language Parallel Corpus includes Indonesian parallel text and is licensed CC BY 4.0.
