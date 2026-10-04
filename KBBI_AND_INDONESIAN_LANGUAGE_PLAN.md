# KBBI and Indonesian Language Acquisition Plan for XNAI

## Objective

Build the broadest practical Indonesian lexical and language knowledge base from sources that XNAI is permitted to use. Do not claim that any finite dataset contains every Indonesian word or every meaning. The lexicon must remain extensible and provenance-aware.

## KBBI: official reference, not an automatically mirrored dataset

The official KBBI Daring is maintained by Badan Pengembangan dan Pembinaan Bahasa. Its current site states that its contents are protected by copyright and requires sign-in for access. XNAI must not scrape, bulk-download, redistribute, or bundle the complete KBBI dataset unless the rights holder has granted a suitable license or written permission.

Acquisition workflow:

1. Contact Badan Pengembangan dan Pembinaan Bahasa through the official contact channels listed on https://kbbi.kemendikdasmen.go.id/Beranda/Kontak.
2. Request a written permission/license for the intended use: local storage in XNAI, transformation into a machine-readable lexicon/codebook, derived indexes, backup/sync, redistribution (if any), commercial/noncommercial scope, attribution, update cadence, and retention/deletion requirements.
3. Request a supported API, licensed export, or a data package with a documented schema. Do not reverse-engineer access controls or use an unofficial mirror as proof of permission.
4. Keep the license/permission document with the acquired data and record its scope, date, version, and checksum.
5. Import only the fields and portions authorized by the license. If only lookup is authorized, use KBBI as an online reference and store only permitted facts or references, not copied definitions.

Official reference: https://kbbi.kemendikdasmen.go.id/
Official legal notice and update information: https://kbbi.kemendikdasmen.go.id/Beranda/Index
Official contact: https://kbbi.kemendikdasmen.go.id/Beranda/Kontak

## Complementary sources

Use each source only within its license and terms. Keep the original license/NOTICE and attribution alongside imported or redistributed data.

| Source | Intended contribution | Handling |
|---|---|---|
| Indonesian Hunspell dictionary | Spellings, lexical forms, affix flags | Preserve upstream notices and applicable MPL/LGPL terms. Import parsed forms, not affix flags as part of the word. |
| Kaikki / Indonesian Wiktionary | Word forms, senses, glosses, lexical relations | Preserve CC BY-SA/GFDL attribution and share-alike obligations for adapted material. |
| MALINDO Morph | Roots, surface forms, prefixes, suffixes, circumfixes, reduplication | Preserve CC BY 4.0 attribution and source version. |
| Wordnet Bahasa | Synsets, lemma relations, semantic graph | Preserve the exact upstream license and notice; check the selected data release before redistribution. |
| Universal Dependencies Indonesian-GSD | Lemmas, morphology, syntax, contextual examples | Track treebank annotation license separately from source-text conditions. Avoid republishing text unless its source terms permit it. |
| Tatoeba Indonesian | Example sentences and usage patterns | Retain sentence-level attribution/license metadata; filter entries according to the export's license information. |
| TALPCo Indonesian | Parallel sentences and translation alignment | Preserve CC BY 4.0 attribution and source version. |
| Wikidata | Structured lexical/entity identifiers and links | CC0 structured data; use as identifiers/relations, not as a prose dictionary. |
| Badan Bahasa Sipebi open resources | Orthography and morphology support | Use only explicitly identified nonconfidential/open resources and obey their published terms. The existence of a public page is not blanket permission to copy all Sipebi/KBBI content. |
| Korpus Indonesia (Koin) | Corpus evidence, collocations, usage frequency | Use only through permitted access/export terms. Corpus frequency is evidence of usage, not proof of KBBI status. |
| Apertium Indonesian | Monolingual morphological dictionary and linguistic rules | GPL-3.0; import the Indonesian side and retain the upstream license/notice. |
| Apertium Indonesian-English | Translation lexicon and lexical relations | GPL-3.0; import only the Indonesian side into the Indonesian lexical registry. |
| Korpus01 | Open Indonesian raw text | CC0-1.0; useful for context and candidate discovery when the repository is explicitly acquired. |
| IndoLEM / IndoBERT | Indonesian NLP benchmarks and pretrained-model research | Track each dataset/model's own terms. Do not infer redistribution rights from the training corpus. |
| OPUS | Parallel corpora and translations | Per-corpus/per-source license; never bulk-import the entire OPUS collection under one blanket assumption. |
| Authorized public-domain/open educational materials | Domain vocabulary and examples | Verify item-level or collection license before import. |
| XNAI observations and user contributions | New words, slang, names, technical terms, regional usage | Mark as candidate/user-contributed, retain consent and source, and do not silently promote to standard Indonesian. |

## XNAI's own lexical ID policy

- XNAI itself allocates a stable simple integer to each newly accepted lexical token, in deterministic first-seen order within the persistent registry.
- Existing IDs are immutable. Re-importing a duplicate must not allocate a second ID.
- Human-readable display may show `word = integer`; the system must not ask Fajar or the assistant to manually choose IDs.
- Composite symbols use opaque stable IDs and refer to child IDs. Meanings/definitions remain in protected internal records and must not be included in ordinary symbol listings or exports.
- The model may optimize grouping and storage, but must retain a reversible mapping, integrity checks, provenance, and version metadata. Never claim lossless compression if information has been discarded.
- “XNAI chooses the number” means its registration algorithm autonomously assigns the next available integer. It does not mean an AI model should freely rewrite existing IDs.

## Data quality and status labels

Every lexical item should carry:

- normalized form and original form;
- source ID, source version/date, license identifier, attribution, and import timestamp;
- entry type: lemma, inflected/surface form, reduplication, phrase, proper name, abbreviation, slang, regional form, technical term, or unknown;
- language/register/domain labels when supported;
- definition/sense and example only when source terms permit storing them;
- status: `source-attested`, `candidate`, `reviewed`, `conflicting`, or `rejected`;
- confidence and evidence references, kept distinct from verified facts.

Do not merge homographs or senses solely because their spellings match. Do not mark every corpus token as a dictionary headword. Keep spelling variants and source disagreements linked rather than deleting them.

## Morphology and candidate discovery

Use Hunspell affix rules and MALINDO Morph forms to identify likely Indonesian inflections and derivations. Generate a form only as a candidate unless supported by an explicit licensed resource or reviewed evidence. Record the root-to-surface relation and the rule/source that generated it. Avoid unbounded combinatorial generation.

XNAI may discover candidate terms from permitted corpora and authorized user input. Candidate scoring can consider repeated independent attestations, morphological plausibility, context diversity, and source reliability. Frequency alone must never promote a term to KBBI-standard status.

## Efficient processing

- Stream files line-by-line; do not load large corpora fully into RAM.
- Normalize Unicode consistently while preserving original spelling.
- Deduplicate by normalized lexical form while preserving all provenance records.
- Allocate IDs transactionally to avoid duplicate IDs during parallel imports.
- Batch writes and checkpoint progress; support resume after interruption.
- Keep source datasets separate from derived XNAI indexes so licensed source material can be removed without destroying independently permitted data.
- Verify checksums and store source manifests. For large resources, download only on explicit user action and respect bandwidth/storage budgets.
- Keep raw corpora out of the APK by default. Import only selected licensed data and use external/optional storage for large resources.

## Current implementation boundary

The application has a layered Indonesian source registry, explicit import policies, streaming import paths for selected open resources, an Apertium DIX parser, and an **import all eligible sources** action. A licensed KBBI package may require a schema-specific adapter after the rights holder supplies a documented format. This project does not claim that a complete KBBI dataset is included or that every Indonesian text ever published is captured.
