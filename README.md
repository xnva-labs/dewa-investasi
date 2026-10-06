# XNAI Native Android

Native Android conversion and expansion of the supplied XNAI dashboard.

## Core idea

XNAI is no longer a WebView-style dashboard. The project is structured as a native Kotlin + Jetpack Compose application with explicit subsystems for memory, formulas, vision, knowledge, algorithms, programming-language knowledge, and autonomous exploration.

## Added in this build

### 1. Native dashboard

- Jetpack Compose UI
- XNAI Core
- Perpustakaan
- Pengetahuan
- Kompres Kompleks
- Code Book
- Cara Berfikir / Exploration Core
- Reasoning training
- Riwayat
- Pengaturan
- Vision Lab
- Language + Algorithm Atlas

### 2. Formula entry is intentionally simple

The user does not need to write JSON.

Use:

1. Name
2. Domain
3. Main formula
4. Meaning
5. Variables
6. Example

The resulting record can be turned into a compact learning summary and saved to the library.

### 3. Paper-to-knowledge pipeline

The camera workflow is:

camera image → OCR → extracted text → structured artifact

The app can save the extracted knowledge instead of treating the paper photo itself as the knowledge record. The capture file is temporary and removed after the processing callback completes.

### 4. Realtime vision

The Vision Lab can combine:

- OCR/text recognition
- object detection/tracking
- geometric shape extraction with OpenCV contours

The build now also includes native image labeling, face detection, and barcode/QR scanning in the vision pipeline. The architecture remains prepared for pose detection, language identification, segmentation, and custom models.

### 5. Language + Algorithm Atlas

The current built-in catalog has **73 programming languages** and **116 algorithms**, plus **24 reasoning methods**. It spans:

- systems and embedded languages
- JVM / .NET languages
- web languages
- functional and logic languages
- scripting shells
- scientific/data languages
- database languages
- smart-contract and proof systems
- HDLs
- GPU/shader languages
- game languages
- theorem-proving/dependent-type languages

Algorithm coverage includes sorting/searching, graph algorithms, data structures, dynamic programming, strings, numerical methods, linear algebra, ML, optimization, simulation, computer vision, compression, cryptography, streaming, distributed systems, databases, compiler algorithms, and agent/reasoning patterns.

This is a curated starting catalog, not a claim that the world has exactly 73 languages. New languages/algorithms can be appended without changing the screen architecture.

### 6. Cara Berfikir becomes an autonomous exploration loop

The Exploration Core explicitly cycles through:

`observe → retrieve → combine → hypothesize → experiment → verify → reflect → remember`

The engine can combine:

- artifacts from memory
- formulas
- language paradigms
- algorithm knowledge
- reasoning methods
- prior experiment outcomes

It then ranks candidate hypotheses and executes a bounded native experiment when an experiment type is available.

Current local experiments include:

- Bubble Sort
- Insertion Sort
- Selection Sort
- Merge Sort
- Quick Sort
- Heap Sort
- Binary Search
- Linear Search
- Euclidean GCD
- fast modular exponentiation
- structured thought experiments

The UI also has a continuous exploration mode. The user can stop it at any time.

### 7. Why the loop is not literally unrestricted

"Tanpa batas" is implemented as an **open-ended exploration mode**, not an unbounded native process. Every concrete experiment remains resource-bounded so XNAI cannot accidentally burn the device's CPU, battery, memory, or run arbitrary unsafe code indefinitely.

## Architecture

```text
XNAI Native
│
├── UI / Compose
│
├── XNAI Core
│   ├── local reasoning
│   └── model/provider extension point
│
├── Memory
│   ├── artifacts
│   ├── formulas
│   ├── knowledge nodes
│   ├── snippets
│   └── activity logs
│
├── Knowledge Catalog
│   ├── 73 languages
│   ├── 116 algorithms
│   └── 24 reasoning methods
│
├── Exploration Core
│   ├── candidate generation
│   ├── hypothesis ranking
│   ├── experiment execution
│   ├── verification
│   └── reflection + memory
│
└── Vision Lab
    ├── CameraX
    ├── ML Kit OCR
    ├── ML Kit objects
    └── OpenCV geometry
```

## Native stack

- Android Gradle Plugin 9.3.0
- Kotlin 2.2.10 in the Android build configuration
- Jetpack Compose BOM 2026.09.00 / Material 3
- CameraX 1.6.2
- ML Kit Text Recognition 16.0.1
- ML Kit Object Detection 17.0.2
- ML Kit Image Labeling 17.0.9
- ML Kit Face Detection 16.1.7
- ML Kit Barcode Scanning 17.3.0
- OpenCV 4.14.0

Kotlin 2.4.20 is the current stable release as of September 7, 2026. This build keeps Kotlin Gradle plugin 2.2.10 because the Android toolchain documents that pairing as the AGP 9.3 default; upgrade both together when moving toolchains. Upgrade can be performed later with the matching Android Studio/toolchain. 

## Sources

See `RESEARCH_SOURCES.md` for the official Android/Google references, language references, and reasoning papers used to shape the architecture.


## Autonomous system added in the latest implementation

The project now contains:

- `XnaiAutonomousSystem.kt` for self-directed questions, retrieval, hypotheses, safe experiments, verification, reflection, experience memory, validation, relations, and codebook integration.
- `XnaiSafeSandbox.kt` for bounded deterministic experiments without arbitrary host-shell execution.
- `XnaiLibraryStore.kt` for the unified file-backed XNAI library with 500 MiB logical units and transport chunking capped at 50 MiB (12 MiB mobile-safe chunks in the current implementation).
- `SecureSecretStore.kt` for Android Keystore AES-GCM secret storage.
- `GitHubSync.kt` for user-authorized repository synchronization.
- `XnaiResearchGateway.kt` for public research adapters.
- `XnaiModelProvider.kt` for an optional OpenAI-compatible provider.
- `XnaiAutonomyWorker.kt` for repeated autonomous cycles through WorkManager.
- `XnaiVoice.kt` / `VoiceScreen.kt` for speech input/output.
- `RecursiveCompression.kt` for exact byte-preserving recursive cost-aware dictionary compression.
- `XNAI_MASTER_SPEC.md` as the complete implementation contract.
- `IMPLEMENTATION_MATRIX.md` as the requirement-to-code verification ledger.

The source still distinguishes implemented local behavior from external/device-dependent validation. See `IMPLEMENTATION_MATRIX.md`.

## Indonesian language expansion
XNAI has a persistent numeric lexicon: `mengapa = 1`, then the next unseen word gets `2`, etc. Composite symbols are non-sequential stable codes. Language resources can be streamed into the lexicon from the catalog in `language_sources/SOURCES.json`. See `scripts/bootstrap_language_resources.py` for desktop/CI acquisition of reusable sources.


### Indonesian language bootstrap
XNAI now exposes open Indonesian resources for acquisition: Kaikki/Indonesian Wiktionary, UD Indonesian-GSD, LibreOffice Hunspell Indonesian, MALINDO Morph, Wordnet Bahasa, and Tatoeba Indonesian. The lexical key is always a simple stable sequential integer. Mozilla Common Voice Indonesian is referenced as an external speech dataset due its large size and distribution rules. Complete KBBI redistribution is not bundled without an authorized dataset/licence.


### Opaque symbol identifiers
XNAI allocates simple sequential numeric IDs for first-seen lexical entries internally. Composite symbols use stable opaque IDs. Use `OpaqueSymbolProjection` for human-facing views and exports so definitions and decoded meanings are not exposed by default. Internal semantic decoding remains available to XNAI; hiding a mapping from the running process itself is incompatible with using that mapping for interpretation.

## Indonesian language coverage and KBBI

XNAI's Indonesian lexicon is designed to grow from multiple permitted resources rather than claiming a complete static list of every word. See [`KBBI_AND_INDONESIAN_LANGUAGE_PLAN.md`](KBBI_AND_INDONESIAN_LANGUAGE_PLAN.md) for source scope, licensing, provenance, autonomous ID allocation, morphology, quality labels, and the official KBBI permission workflow. The official KBBI is not scraped or bundled automatically; a licensed/authorized dataset and schema are required for a full import.

## Maximum Indonesian language stack

XNAI now treats Indonesian language acquisition as a layered stack: official dictionary, open dictionaries, spelling, morphology, semantics, corpus context, translation, structured lexical links, speech, benchmarks, and candidate vocabulary. The app can run **Masukkan semua sumber terbuka** to process resources that have a parser and an explicit import policy; licensed/external resources remain visible in the registry without being silently copied.

### Programming languages
The programming-language atlas now has canonical profiles, common aliases, extension hints, and a guarded language-neutral code-learning plan. This is an extensible knowledge/planning layer only. It does **not** embed compilers or interpreters for every listed language; profiles remain `KNOWLEDGE_INDEXED` until a runtime adapter is integrated and independently tested. See `XNAI_MASTER_SPEC.md` for the multi-language mastery roadmap and sandbox requirements.
