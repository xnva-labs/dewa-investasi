package com.xnvalabs.xnai

/**
 * Indonesian-language acquisition registry.
 *
 * "Importable" means the project has an explicit parser/ingestion path and the
 * source is intended to be acquired under its stated terms. External sources
 * remain discoverable without being silently copied into the APK.
 */
data class LanguageResource(
    val id: String,
    val name: String,
    val url: String,
    val format: String,
    val license: String,
    val notes: String,
    val commercialSafe: Boolean,
    val importableInApp: Boolean = true,
    val category: String = "lexicon",
    val acquisition: String = "remote"
)

object XnaiLanguageResourceCatalog {
    val resources = listOf(
        LanguageResource(
            "kaikki-id",
            "Kaikki / Indonesian Wiktionary machine-readable dictionary",
            "https://kaikki.org/idwiktionary/Bahasa%20Indonesia/kaikki.org-dictionary-BahasaIndonesia.jsonl",
            "JSONL",
            "Wiktionary: CC BY-SA + GFDL",
            "Kamus mesin: lema, bentuk, kelas kata, dan makna dari Wiktionary. Data Kaikki diperbarui berkala.",
            true,
            category = "dictionary"
        ),
        LanguageResource(
            "hunspell-id",
            "Hunspell Indonesian dictionary",
            "https://raw.githubusercontent.com/shuLhan/hunspell-id/master/id_ID.dic",
            "Hunspell DIC",
            "LGPLv3+",
            "Daftar lema/ejaan dengan aturan imbuhan. Simpan COPYING/notice upstream ketika mendistribusikan data.",
            true,
            category = "spelling"
        ),
        LanguageResource(
            "malindo-morph-id",
            "MALINDO Morph 2024",
            "https://raw.githubusercontent.com/matbahasa/MALINDO_Morph/master/malindo_dic_2024.tsv",
            "TSV",
            "CC BY 4.0",
            "Akar, bentuk permukaan, prefiks, sufiks, konfiks, dan reduplikasi untuk morfologi Indonesia/Melayu.",
            true,
            category = "morphology"
        ),
        LanguageResource(
            "wordnet-bahasa-id",
            "Wordnet Bahasa / Indonesian WordNet",
            "https://raw.githubusercontent.com/limaginaire/Bahasa-Wordnet/master/wn-msa-all.tab",
            "TSV",
            "MIT",
            "Jaringan semantik: synset, lemma, relasi leksikal, dan kualitas data.",
            true,
            category = "semantics"
        ),
        LanguageResource(
            "apertium-ind",
            "Apertium Indonesian monolingual dictionary",
            "https://raw.githubusercontent.com/apertium/apertium-ind/master/apertium-ind.ind.dix",
            "LT XML / DIX",
            "GPL-3.0",
            "Kamus morfologi dan aturan linguistik Apertium untuk analisis bahasa Indonesia.",
            true,
            category = "morphology"
        ),
        LanguageResource(
            "apertium-ind-eng",
            "Apertium Indonesian-English lexical pair",
            "https://raw.githubusercontent.com/apertium/apertium-ind-eng/master/apertium-ind-eng.ind-eng.dix",
            "LT XML / DIX",
            "GPL-3.0",
            "Pasangan leksikal bahasa Indonesia-Inggris. Dipakai untuk hubungan terjemahan, bukan untuk menganggap kosakata Inggris sebagai bahasa Indonesia.",
            true,
            category = "translation"
        ),
        LanguageResource(
            "ud-id-gsd",
            "Universal Dependencies Indonesian-GSD",
            "https://github.com/UniversalDependencies/UD_Indonesian-GSD",
            "CoNLL-U",
            "CC BY-SA 4.0 untuk treebank annotation, dengan kondisi pada sumber teks",
            "Menyediakan token, lemma, POS, fitur morfologi, dan dependency untuk contoh pemakaian nyata.",
            true,
            category = "corpus"
        ),
        LanguageResource(
            "talpco-id",
            "TALPCo Indonesian parallel corpus",
            "https://raw.githubusercontent.com/matbahasa/TALPCo/master/ind/data_ind.txt",
            "TXT",
            "CC BY 4.0",
            "Kalimat paralel Indonesia untuk konteks, pola kalimat, dan hubungan terjemahan.",
            true,
            category = "corpus"
        ),
        LanguageResource(
            "tatoeba-id",
            "Tatoeba Indonesian sentences",
            "https://downloads.tatoeba.org/exports/per_language/ind/ind_sentences.tsv.bz2",
            "TSV.BZ2",
            "CC BY 2.0 default dengan aturan lisensi per kontribusi",
            "Gunakan metadata lisensi/atribusi tiap kalimat. Corpus frequency bukan bukti status KBBI.",
            true,
            category = "corpus"
        ),
        LanguageResource(
            "korpus01-id",
            "Korpus01 Indonesian raw-text corpus",
            "https://github.com/to2k/korpus01",
            "TXT",
            "CC0-1.0",
            "Kumpulan teks mentah berbahasa Indonesia yang dapat dipakai sebagai bukti konteks dan bentuk kata.",
            true,
            importableInApp = false,
            category = "corpus",
            acquisition = "external-repository"
        ),
        LanguageResource(
            "wikidata-lexemes",
            "Wikidata structured lexical/entity data",
            "https://www.wikidata.org/wiki/Wikidata:Licensing",
            "Structured data",
            "CC0 untuk structured data yang berlaku",
            "ID lexeme/entity dan relasi terstruktur. Bukan pengganti kamus definisional.",
            false,
            importableInApp = false,
            category = "knowledge",
            acquisition = "external-api"
        ),
        LanguageResource(
            "wikipedia-id",
            "Wikipedia Bahasa Indonesia",
            "https://id.wikipedia.org",
            "Web/API",
            "CC BY-SA 4.0 / GFDL sesuai Wikimedia terms yang berlaku",
            "Sumber konteks, istilah, proper name, dan pengetahuan domain. Tidak dibundel sebagai dump APK.",
            true,
            importableInApp = false,
            category = "corpus",
            acquisition = "live-research"
        ),
        LanguageResource(
            "common-voice-id",
            "Mozilla Common Voice Indonesian",
            "https://commonvoice.mozilla.org/dav/datasets",
            "TSV + audio",
            "CC0-1.0 dataset, dengan ketentuan distribusi platform Mozilla",
            "Kumpulan data suara besar untuk ASR/TTS. Tetap eksternal dan jangan dimasukkan ke APK secara default.",
            true,
            importableInApp = false,
            category = "speech",
            acquisition = "external-large"
        ),
        LanguageResource(
            "kbbi-official",
            "KBBI VI Daring",
            "https://kbbi.kemendikdasmen.go.id/",
            "Official web dictionary",
            "Hak cipta Badan Pengembangan dan Pembinaan Bahasa",
            "Sumber otoritatif ejaan/kamus. Tidak disalin massal. Paket lokal hanya setelah izin/lisensi atau export resmi diberikan.",
            false,
            importableInApp = false,
            category = "official-dictionary",
            acquisition = "licensed-only"
        ),
        LanguageResource(
            "sipebi-official",
            "Sipebi Bahasa Indonesia",
            "https://kbbi.kemendikdasmen.go.id/",
            "Official application/resource",
            "Ketentuan Badan Bahasa yang berlaku",
            "Dipakai sebagai target integrasi ejaan/penyuntingan jika data/model resmi dapat diperoleh dengan izin. Tidak diasumsikan bebas untuk diekstrak.",
            false,
            importableInApp = false,
            category = "orthography",
            acquisition = "licensed-only"
        ),
        LanguageResource(
            "koin-indonesia",
            "Korpus Indonesia (Koin)",
            "https://korpusindonesia.kemdikbud.go.id/",
            "Corpus / web",
            "Terms of access/export must be checked per service",
            "Bukti frekuensi, kolokasi, dan konteks. Frekuensi diperlakukan sebagai evidence, bukan status kamus.",
            false,
            importableInApp = false,
            category = "corpus",
            acquisition = "terms-dependent"
        ),
        LanguageResource(
            "indolem",
            "IndoLEM / IndoBERT Indonesian NLP resources",
            "https://github.com/indolem/indolem",
            "NLP datasets + model",
            "Per-dataset/repository terms; tidak mewarisi lisensi sumber training secara otomatis",
            "Sangat berguna untuk evaluasi morpho-syntax, semantics, discourse, NER, sentiment, parsing, dan summarization. Raw source text tidak diasumsikan redistributable.",
            false,
            importableInApp = false,
            category = "nlp-benchmark",
            acquisition = "terms-dependent"
        ),
        LanguageResource(
            "opus-indonesian",
            "OPUS multilingual corpora with Indonesian",
            "https://opus.nlpl.eu/",
            "Parallel corpus",
            "Per-corpus / per-source license",
            "Sumber kalimat paralel untuk alignment dan translation memory. Setiap corpus wajib melewati pemeriksaan lisensi sendiri.",
            false,
            importableInApp = false,
            category = "corpus",
            acquisition = "terms-dependent"
        )
    )

    fun byId(id: String): LanguageResource? = resources.firstOrNull { it.id == id }
    fun importable(): List<LanguageResource> = resources.filter { it.importableInApp }
}
