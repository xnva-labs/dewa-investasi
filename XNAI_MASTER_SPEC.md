# XNAI MASTER SPECIFICATION

## 0. Identity and separation
XNAI is a standalone AI system under XNVA Labs. SmartEyeX is a separate public-facing product. SmartEyeX must not display or call the internal system “XNAI” as a public user-facing assistant/brand unless Fajar explicitly changes that decision. The present ZIP is the standalone XNAI application.

## 1. Non-negotiable autonomy principle
Fajar must not have to direct each individual thought, question, experiment, or next reasoning step. XNAI is responsible for selecting its own internal direction from evidence, uncertainty, knowledge gaps, prior experience, feasibility, novelty, and policy constraints.

The intended loop is:

`Observe → Retrieve → Combine → Hypothesize → Experiment → Verify → Reflect → Try to Implement → Try to Use → Remember Previous Experiences → Combine with All Relevant Knowledge → Remember`

The loop is self-directed. Human input supplies context, permissions, and ownership policy rather than a step-by-step command stream.

## 2. What self-direction means in the implementation
XNAI should independently:

1. inspect existing memory and current observations;
2. identify missing or weak knowledge;
3. generate its own questions;
4. rank questions using uncertainty, relevance, novelty, prior repetition, and expected value;
5. retrieve relevant local and permitted external evidence;
6. connect new information to related existing knowledge;
7. combine concepts across domains, including relationships that were not explicitly supplied by the user;
8. generate falsifiable hypotheses;
9. keep evidence, confidence, and hypothesis status separate;
10. design an experiment or verification strategy;
11. implement a safe test strategy;
12. execute it inside the sandbox;
13. compare the result against an oracle/baseline/invariant when available;
14. retain positive, negative, and inconclusive results;
15. reflect on weaknesses and limitations;
16. choose follow-up questions and experiments without waiting for a human prompt;
17. store experiences and validated knowledge;
18. create relations between the new result and all relevant existing knowledge;
19. repeat continuously within the allowed cage.

A model response alone is never treated as experimental proof. A hypothesis does not silently become a fact.

## 3. Knowledge from many sources
The architecture accepts documents, text, local artifacts, formulas, code snippets, programming languages, techniques, algorithms, experiments, public web research, and model/provider outputs. Every externally retrieved source needs provenance, source name, URL where relevant, retrieval time, permission/license state where known, and a checksum.

Research adapters currently provide public-web retrieval paths for Wikipedia, Crossref, and GitHub. Additional adapters may be added without changing the autonomy loop.

KBBI/dictionary content may be imported only from a source Fajar has legitimate permission to use. The application provides a dictionary import path rather than bundling an unverified copy of copyrighted/licensed content.

## 4. Cumulative knowledge integration
Every validated learning should be connected to relevant earlier knowledge rather than stored as an isolated note. The library keeps questions, hypotheses, evidence, experiments, reflections, experiences, validated knowledge, source records, relations, and algorithm proposals linked by stable IDs.

Cross-domain integration is a first-class behavior. Example: a new observation about gravity can be linked to existing astronomy/black-hole knowledge, producing a new question about whether the relationship can be tested or bounded.

## 5. Algorithm generation and experimentation
XNAI can propose algorithmic variants and test safe variants against known baselines. The initial implementation includes bounded synthesized families for sorting, searching, numerical differential checks, compression accounting, and general invariants. The catalog supplies many languages, algorithms, and reasoning methods as candidate building blocks.

Arbitrary model-generated shell commands or arbitrary host code are not executed. Safe algorithm templates and deterministic local experiments are used unless a future isolated external execution service is explicitly approved and added.

## 6. Self-questioning
XNAI generates questions without requiring Fajar to supply a “goal” for every run. Questions may come from:
- weak-confidence knowledge;
- overlapping/cross-domain concepts;
- failed or inconclusive experiments;
- reproducibility gaps;
- boundary conditions;
- missing evidence;
- meta-learning about which knowledge/method would improve the next cycle.

## 7. Recursive hierarchical codebook
The codebook maps lexical tokens to stable immutable IDs and can recursively combine existing symbols into higher-order symbols. Definitions are versioned. Old symbol meanings are not silently rewritten. Each symbol records:
- stable ID;
- namespace;
- level;
- child references;
- definition;
- version;
- provenance;
- checksum;
- decoding path through its children.

A high-level symbol can point to a large known structure only because the codebook contains its definitions. This is not arbitrary lossless compression of novel information.

## 8. Recursive compression requirements
The compression layer must preserve exact bytes when decoded. It must account for dictionary overhead and only create substitutions when there is positive modeled net savings. Repetitive data may compress substantially; arbitrary/incompressible data may not. The implementation includes recursive phrase symbols with exact decode verification and a maximum recursion depth parameter.

Conceptual notation such as `1^111`, `2^111`, and `A^222` is treated as a symbolic hierarchy concept, not a claim that a tiny fixed token can losslessly represent arbitrary new data without a shared codebook.

## 9. Unified library
Logical library layout:

```text
XNAI_LIBRARY/
├── codebook/
│   ├── recursive-symbol records
│   └── packed binary codebook/payloads
├── knowledge/
│   ├── source records
│   ├── artifacts
│   ├── formulas
│   ├── domains
│   └── programming-language/algorithm knowledge
├── thinking/
│   ├── questions
│   ├── hypotheses
│   ├── algorithm proposals
│   └── reflections / human-readable reports
├── experiments/
│   ├── plans
│   ├── measurements
│   ├── conclusions
│   └── reproducibility records
├── memory/
│   ├── experiences
│   └── validated knowledge
└── index/
    ├── library index
    ├── dependency/relation map
    └── integrity metadata
```

JSON/JSONL is used for structured metadata and records. Markdown is used for human-readable reasoning/reports. Binary formats are reserved for dense packed codebook/payload data.

## 10. Storage sizing and chunking
A logical unit is capped at 500 MiB. When a unit would exceed the logical cap, the next unit is created and remains part of one linked library. Logical units use A/B/C-style suffixing.

Physical transport chunks must never exceed 50 MiB. The current Android implementation uses smaller chunks for mobile memory safety while preserving the ≤50 MiB requirement.

Every chunk records:
- chunk ID;
- logical file/source;
- sequence number;
- size;
- SHA-256;
- manifest relationship.

The logical file also records its checksum. GitHub is not treated as unlimited storage. Repository/account limits and service policies still apply. Git LFS or object storage remains an available extension for very large binary payloads.

## 11. GitHub sync and credentials
The app may sync the library to user-authorized GitHub repositories/accounts. This is not a method to evade platform quotas or terms.

GitHub tokens must never be hard-coded, committed, logged, or written into JSON/Markdown/source repositories. Secrets are stored using Android Keystore-protected encryption. The UI accepts a secret without embedding the value in source. The sync layer is prepared for fine-grained tokens with the required repository Contents permission.

The sync protocol supports chunked upload, branch selection, path prefixing, existing-file SHA handling, and per-upload HTTP verification. Tokens can be removed from settings.

## 12. AI provider integration
The Android app contains an OpenAI-compatible provider adapter. Endpoint and model are configurable. No API key is embedded in source. The provider is optional: XNAI keeps a deterministic local reasoning path when no remote model is configured.

The provider must receive sufficient local context to reason over formulas, artifacts, and memory. It must not be told that a generated hypothesis is a validated fact unless the evidence status confirms that.

## 13. Research gateway
External research is optional and budgeted. The current gateway retrieves evidence packets rather than executing arbitrary downloaded code.

Supported sources in the native implementation:
- Wikipedia public search API;
- Crossref works search API;
- GitHub public repository search API, optionally authenticated.

Fetched material is stored with provenance and checksum. Source licensing/permission status must be respected.

## 14. Safety cage
“No experiment limit, only a cage” means open-ended self-directed exploration inside explicit containment. It does not mean unlimited control of the real device, other systems, or the internet.

Default safety properties:
- sandbox enabled;
- bounded experiment time;
- bounded generated item count;
- bounded memory target;
- bounded network requests per cycle;
- no arbitrary host-shell command execution;
- no unrestricted filesystem access;
- no destructive external action by default;
- audit logging;
- pause/STOP controls;
- Android scheduled background execution subject to OS constraints.

Sensitive, destructive, financial, public-facing, privacy-sensitive, or consequential actions require explicit authorization. Fajar remains owner/admin for permissions and policy, not the micro-manager of thought.

## 15. Continuous autonomous operation
When autonomous learning is enabled, WorkManager schedules repeated cycles. Android may defer scheduled work, and the operating system controls execution windows. While the application is foregrounded, the Exploration Core can run repeated cycles until stopped.

This preserves long-lived self-direction without creating an unbounded background process that ignores Android resource and lifecycle policy.

## 16. Vision
The native Vision Lab integrates CameraX plus on-device ML Kit/OpenCV pathways for:
- OCR/text recognition;
- object detection/tracking;
- image labeling;
- face detection/counting;
- barcode/QR scanning;
- geometric contour/shape extraction.

A generic detector is not claimed to recognize literally every object. Custom/domain-specific models remain an extension point.

## 17. Voice
The native app provides speech-to-text input and text-to-speech output. Microphone access is runtime-permission controlled. The app does not silently record microphone input in the background.

## 18. Core application behavior
The standalone application provides native Compose surfaces for:
- XNAI Core/chat;
- Voice;
- Perpustakaan/library;
- Pengetahuan/knowledge;
- Kompres Kompleks;
- Code Book;
- Autonomous/Cara Berfikir;
- language + algorithm atlas;
- reasoning exercises;
- history;
- Vision Lab;
- settings/security/sync.

Existing project screens/data are preserved while the new subsystems are integrated around them.

## 19. Physical boundary
Software can create/modify software artifacts and coordinate explicitly authorized hardware. Software alone cannot physically become a giant or gain physical capabilities without actual hardware, energy, engineering, permissions, and safety controls.

## 20. No false completion
A feature is only “complete” when its implementation exists and the relevant test/verification has been performed. External dependencies are not fabricated. This project must keep a visible distinction between:
- implemented locally;
- tested locally;
- requires external credentials/service;
- requires Android device validation;
- future extension.

## Indonesian language expansion implementation
The lexical layer uses a persistent numeric registry. Human-facing word IDs are sequential decimal integers only: the first unseen word receives `1`, the next receives `2`, and so on. Existing IDs never change. Composite symbols are separate and use non-sequential stable content-derived identifiers. The persistent registry is SQLite-backed so the entire vocabulary is not loaded into RAM just to look up a word.

XNAI now includes a resource catalog and streaming import paths for Indonesian Wiktionary/Kaikki data, Universal Dependencies Indonesian-GSD, the LibreOffice Indonesian Hunspell dictionary, and Tatoeba Indonesian sentences. The catalog preserves source URL, license, provenance, and notes. A desktop/CI bootstrap script can download and normalize these sources. KBBI is intentionally not auto-scraped or redistributed without authorization; an authorized KBBI dataset can be imported through the same numeric registry.


## Autonomous and opaque symbol assignment (additional explicit requirement)
Lexical numbering is allocated internally by XNAI at first registration: first unseen token receives 1, next unseen token receives 2, and so on. Neither Fajar nor an external assistant chooses individual IDs. Existing assignments are immutable; imports preserve first-seen allocation and de-duplicate normalized tokens. Numeric IDs should be encoded with compact variable-length integers in binary storage rather than padded strings where practical. Composite symbol IDs are stable opaque content-derived identifiers, not sequential lexical numbers. Human-facing diagnostics, reports, and exports should expose only opaque symbol ID, level, child references, version, and integrity checksum, never decoded meaning or lexical definitions by default. XNAI's authorized internal reasoning process must still be able to resolve its own codebook when semantic interpretation is needed. Therefore this is opacity at the interface/export boundary, not a claim that the running AI or an administrator with full device/database access can be mathematically prevented from recovering mappings. Strong confidentiality at rest would require a separate encrypted lexicon/index design with Android Keystore key management and migration testing.

## Indonesian language coverage contract

XNAI's Indonesian language subsystem is layered rather than tied to a single dictionary. It can ingest eligible sources for dictionary forms, spelling, morphology, semantics, corpus contexts, translation relations, and structured lexical links. Official/terms-dependent resources remain acquisition targets and are never treated as automatically redistributable. The same lexical ID can accumulate multiple independent provenance records without receiving a second ID.

## Multi-language programming mastery roadmap

XNAI's programming-language atlas is a knowledge catalog, not proof of compiler/runtime support. `ProgrammingLanguageEngine` adds canonical language profiles, aliases, source-extension hints, and a language-neutral learning/build/test plan. Each profile explicitly reports capability stages; current profiles are knowledge-indexed only. The planner describes a guarded lifecycle: clarify requirements, retrieve versioned references, design, draft, statically check, execute only through a trusted isolated adapter, test against oracles, repair within budgets, and retain reproducible evidence.

“Master all programming languages” is an open-ended objective, not a finite guarantee: languages and versions evolve, obscure and domain-specific languages exist, and practical mastery requires current documentation, examples, toolchains, test suites, and expert review. XNAI should expand its catalog continuously and measure per-language competence using versioned benchmarks. Never mark a language executable or build-ready merely because it appears in the catalog. Runtime adapters must be added one by one, sandboxed, and tested on supported devices/CI. Generated code is untrusted; no arbitrary host shell, secret access, unrestricted filesystem/network, or consequential external action is allowed.
