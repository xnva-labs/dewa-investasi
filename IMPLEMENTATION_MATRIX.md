# XNAI Implementation Matrix

| Requirement | Implementation | Verification status |
|---|---|---|
| Self-directed question generation | `XnaiAutonomousSystem.chooseQuestion()` | Code reviewed; runtime requires Android build/device test |
| Retrieve local knowledge | `XnaiAutonomousSystem.retrieveRelevant()` | Code present |
| Public research | `XnaiResearchGateway` | Code present; real network validation requires device/network |
| Combine cross-domain knowledge | relations + catalog-driven `AlgorithmSynthesis` | Code present |
| Hypothesis creation | `formHypothesis()` | Local fallback + optional model provider |
| Experiment planning | `AlgorithmSynthesis` | Code present |
| Safe experiment execution | `XnaiSafeSandbox` | Pure algorithms exercised separately |
| Verification/oracles | differential/invariant checks | Pure compression test + source review |
| Reflect | local/model reflection module | Code present |
| Try to implement/use | safe algorithm implementation then feed outcome into reflection/next questions | Code present |
| Remember prior experiences | `ExperienceRecord` + `XNAI_LIBRARY/memory` | Code present |
| Integrate validated knowledge | `ValidatedKnowledgeRecord` | Code present |
| Recursive hierarchical codebook | `RecursiveCodebook` + `XnaiLexiconStore` | Source + persistent numeric ID design; runtime Android test required |
| Exact recursive compression | `RecursiveCompression` | Passed exact decode tests for repetitive + random data |
| 500 MiB logical units | `XnaiLibraryStore` | Code present |
| ≤50 MiB physical transport | splitter, 50 MiB maximum transport chunks | Code present |
| GitHub sync | `GitHubSyncClient` with multiple authorized accounts/repositories and deterministic chunk placement | Requires user repository/token; not executed here |
| Secret protection | `SecureSecretStore` / Android Keystore | Source reviewed; device keystore test required |
| AI provider | `XnaiModelProvider` | Requires user endpoint/key |
| Autonomous background continuation | `WorkManager` scheduler | Requires Android build/device test |
| Voice STT/TTS | `XnaiVoice`, `VoiceScreen` | Requires Android device validation |
| Vision | CameraX + ML Kit + OpenCV | Requires Android device/camera validation |
| Existing data migration | `XnaiBootstrap` | Code present |
| No arbitrary host code execution | safe sandbox only | Enforced by architecture of local runner |
| External actions default locked | `SandboxPolicy.allowExternalActions=false` | Code present |
| STOP/pause in foreground | Autonomy UI | Code present |
| Provenance/checksums | source records + library checksums | Code present |
| Reports in Markdown | cycle report writer | Code present |

| Sequential numeric lexical IDs | `XnaiLexiconStore`: 1, 2, 3...; immutable existing IDs | Source reviewed; scale/device test required |
| Composite symbols use non-sequential stable codes | `RecursiveCodebook.combine()` | Source reviewed + pure codebook tests |
| Indonesian open dictionary bootstrap | `XnaiLanguageResourceManager` + catalog | Requires device/network run |
| Kaikki Indonesian dictionary ingestion | JSONL streaming importer | Requires network/device run |
| Indonesian UD treebank ingestion | CoNLL-U streaming importer | Requires network/device run |
| Indonesian Hunspell ingestion | flag-stripping lexical importer | Requires network/device run |
| Tatoeba Indonesian sentence ingestion | BZip2 streaming importer | Requires network/device run |
| MALINDO Morph ingestion | `XnaiLanguageResourceManager` | Requires network/device run; source reviewed |
| Wordnet Bahasa ingestion | `XnaiLanguageResourceManager` | Requires network/device run; source reviewed |
| TALPCo Indonesian ingestion | `XnaiLanguageResourceManager` | Requires network/device run; source reviewed |
| Indonesian Wikipedia live research | `XnaiResearchGateway` uses id.wikipedia.org | Source reviewed; live network requires device/network |
| Mozilla Common Voice Indonesian | external opt-in reference; not mirrored/bundled | Source/licensing reviewed; dataset acquisition external |
| Desktop/CI maximal language bootstrap | `scripts/bootstrap_language_resources.py` | Source reviewed + synthetic extraction tests |

## Important remaining external/device verification

The current environment does not include an Android SDK/Gradle installation capable of assembling this Android project. Therefore no claim is made that `assembleDebug` has passed here. The included GitHub Actions workflow remains the build verification path. Android device validation is also required for CameraX, ML Kit, SpeechRecognizer, TextToSpeech, Android Keystore, permissions, WorkManager, and network behavior.

| XNAI internally allocates lexical IDs on first-seen registration | `XnaiLexiconStore.register/registerBatch` | Code present; Android concurrency/scale test required |
| Opaque composite-symbol projection for UI/export | `OpaqueSymbolProjection` | Source added; caller integration and UI/export review required |
| Keep semantic resolution internal to authorized reasoning | `RecursiveCodebook.decode` | Internal resolver exists; access-boundary/security review required |

| Indonesian maximum-source registry | Implemented | Catalog separates dictionary, spelling, morphology, semantics, corpus, translation, speech, official and terms-dependent resources |
| Apertium DIX ingestion | Implemented in source | Indonesian monolingual side and Indonesian side of ind-eng pair are parsed into the lexical registry |
| Multi-source word provenance | Implemented in source | `word_sources` table preserves multiple sources/status/evidence references per stable lexical ID |
| Import all eligible language sources | Implemented in source | Sequential, streaming acquisition action from Language Atlas; failures are isolated per source |
| Complete KBBI dump | Not included | Requires authorized data package/export/license |


## Local verification completed

- ZIP integrity: verified with `unzip -t`.
- Python bootstrap syntax: verified with `python3 -m py_compile`.
- Pure Kotlin tests: Hunspell parser and exact recursive compression passed on JVM; autonomous candidate generation and experiment runner passed on JVM with Android-independent test fixtures.
- MALINDO parser now follows the documented 2024 ten-column layout (`ID, root, derived, prefix, suffix, circumfix, reduplication, source, stem, lemma`).
- Apertium Indonesian-English import keeps the Indonesian side (left side) in the Indonesian lexical registry.
- Wikidata Lexemes is catalogued as external API data until a dedicated importer is implemented.
- Full Android build/device verification remains unavailable because this environment has no Android SDK/Gradle installation.
| Language-neutral programming profile registry and alias lookup | `ProgrammingLanguageEngine` | Source added; JVM/Android integration test pending |
| Multi-language coding lifecycle planner with explicit sandbox constraints | `ProgrammingLanguageEngine.plan()` | Source added; does not execute or compile code |
| Per-language executable/build capability declarations | `ProgrammingLanguageProfile.stages` | All current profiles remain knowledge-indexed only; runtime adapters not integrated |
| Topic-neutral universal sandbox task model | `XnaiUniversalSandbox.Task` accepts arbitrary subjects/objectives | Source added; standalone compile/test pending |
| Pluggable reviewed experiment adapters | `XnaiUniversalSandbox.register/run` | Source added; adapters must be integrated per capability |
| Distinguish execution from verification | Results remain `completed_unverified` pending independent oracle | Source added; integration pending |
| Subject unrestricted, resource-governed execution | Configurable input/output/step/time budgets; no topic allowlist | Source added; Android hard isolation not implemented |

## 2026-10-04: executable universal-task integration pass
- Added an in-app Universal Sandbox screen for submitting arbitrary-topic learning tasks, code drafts, programming plans, and local text analysis.
- Registered working adapters: `learn` and `code-draft` call the configured model provider when network research is enabled; their results remain unverified drafts. `text-analysis` computes local token/frequency statistics. `programming-plan` returns the local language profile plan.
- Task results are appended to the `universal_sandbox` library category for later review.
- The task runner is called from `Dispatchers.IO` to avoid network work on the UI thread.
- Standalone JVM smoke test passed for topic-neutral adapter dispatch, unverified result status, and safe handling of unknown adapters.
- Not implemented/verified: arbitrary generated-code execution, generic compilers/interpreters, full semantic research retrieval, durable evidence verification, Android build/device runtime. Universal subject acceptance does not imply every operation has an executable adapter. Android build unavailable in this environment.
