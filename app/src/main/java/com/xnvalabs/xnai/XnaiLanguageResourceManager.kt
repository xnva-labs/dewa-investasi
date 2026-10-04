package com.xnvalabs.xnai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import android.util.Xml
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Streams public/open language resources into XNAI. It never downloads KBBI itself.
 * KBBI remains an external source that requires permission for redistribution.
 */
class XnaiLanguageResourceManager(context: Context) {
    private val appContext = context.applicationContext
    private val codebook = RecursiveCodebook(context = appContext)
    private val library = XnaiLibraryStore(appContext)
    private val root = File(appContext.filesDir, "language_sources").apply { mkdirs() }

    suspend fun importResource(resourceId: String): Long = withContext(Dispatchers.IO) {
        when (resourceId) {
            "kaikki-id" -> importKaikki(download(LanguageResourceUrl.kaikki))
            "ud-id-gsd" -> importUdGsd()
            "hunspell-id" -> importHunspell(download(LanguageResourceUrl.hunspell))
            "malindo-morph-id" -> importMalindoMorph(download(LanguageResourceUrl.malindoMorph))
            "wordnet-bahasa-id" -> importWordnet(download(LanguageResourceUrl.wordnetBahasa))
            "apertium-ind" -> importApertium(download(LanguageResourceUrl.apertiumInd), "Apertium-Indonesian")
            "apertium-ind-eng" -> importApertium(download(LanguageResourceUrl.apertiumIndEng), "Apertium-Indonesian-English", preferRightSide = false)
            "talpco-id" -> importPlainSentences(download(LanguageResourceUrl.talpco), "TALPCo")
            "tatoeba-id" -> importTatoeba(download(LanguageResourceUrl.tatoeba))
            "common-voice-id" -> error("Common Voice Indonesia adalah dataset suara eksternal besar; kelola melalui sumber Mozilla, bukan dibundel APK.")
            else -> error("Resource tidak dikenal: $resourceId")
        }
    }

    suspend fun importAllImportableResources(onProgress: (String) -> Unit = {}): Map<String, Long> =
        withContext(Dispatchers.IO) {
            buildMap {
                XnaiLanguageResourceCatalog.importable().forEach { resource ->
                    onProgress("Mengimpor ${resource.name}...")
                    runCatching { importResource(resource.id) }
                        .onSuccess { put(resource.id, it) }
                        .onFailure { put(resource.id, -1L); onProgress("Gagal ${resource.id}: ${it.message ?: it.javaClass.simpleName}") }
                }
            }
        }

    fun lexiconCount(): Long = codebook.wordCount()

    private fun download(url: String): File {
        val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "resource.dat" }
        val destination = File(root, name)
        if (destination.exists() && destination.length() > 0) return destination
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "XNAI/1.0 language-bootstrap")
        connection.connect()
        if (connection.responseCode !in 200..299) error("Download gagal HTTP ${connection.responseCode} untuk $url")
        destination.outputStream().buffered(128 * 1024).use { output ->
            connection.inputStream.use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                }
            }
        }
        connection.disconnect()
        return destination
    }

    private fun importKaikki(file: File): Long {
        var seen = 0L
        var batch = ArrayList<JSONObject>(256)
        file.bufferedReader(Charsets.UTF_8, 128 * 1024).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val obj = try { JSONObject(line) } catch (_: Throwable) { return@forEach }
                val word = obj.optString("word").trim()
                if (word.isBlank()) return@forEach
                val token = codebook.registerToken(word, "kaikki:idwiktionary")
                seen++
                val senses = obj.optJSONArray("senses")
                val firstGloss = senses?.optJSONObject(0)?.optJSONArray("glosses")?.optString(0).orEmpty()
                if (firstGloss.isNotBlank()) {
                    batch += JSONObject().apply {
                        put("id", "lex-${token.id}")
                        put("word", word)
                        put("meaning", firstGloss.take(5000))
                        put("lexicalId", token.id)
                        put("source", "Kaikki/Indonesian Wiktionary")
                    }
                }
                if (batch.size >= 256) { library.appendBatch("knowledge", batch); batch = ArrayList(256) }
            }
        }
        if (batch.isNotEmpty()) library.appendBatch("knowledge", batch)
        recordImport("kaikki-id", file.length(), seen)
        return seen
    }

    private fun importHunspell(file: File): Long {
        var added = 0L
        val batch = ArrayList<String>(2048)
        file.bufferedReader(Charsets.UTF_8, 128 * 1024).useLines { lines ->
            lines.forEachIndexed { index, raw ->
                val word = HunspellLexiconParser.parseLine(raw, isHeader = index == 0) ?: return@forEachIndexed
                batch += word
                if (batch.size >= 2048) {
                    added += codebook.registerTokens(batch, "Hunspell Indonesian dictionary")
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) added += codebook.registerTokens(batch, "Hunspell Indonesian dictionary")
        recordImport("hunspell-id", file.length(), added)
        return added
    }

    private fun importMalindoMorph(file: File): Long {
        var rows = 0L
        var batch = ArrayList<JSONObject>(256)
        file.bufferedReader(Charsets.UTF_8, 128 * 1024).useLines { lines ->
            lines.forEach { line ->
                val entry = MalindoMorphParser.parseLine(line) ?: return@forEach
                if (entry.root.isNotBlank()) codebook.registerToken(entry.root, "MALINDO-Morph-2024")
                if (entry.derived.isNotBlank()) codebook.registerToken(entry.derived, "MALINDO-Morph-2024")
                if (entry.stem.isNotBlank()) codebook.registerToken(entry.stem, "MALINDO-Morph-2024")
                if (entry.lemma.isNotBlank()) codebook.registerToken(entry.lemma, "MALINDO-Morph-2024")
                batch += JSONObject().apply {
                    put("id", "morph-${entry.id.sha256Hex().take(24)}")
                    put("sourceId", entry.id); put("root", entry.root); put("derived", entry.derived)
                    put("prefix", entry.prefix); put("suffix", entry.suffix); put("circumfix", entry.circumfix)
                    put("reduplication", entry.reduplication); put("source", entry.source)
                    put("stem", entry.stem); put("lemma", entry.lemma)
                    put("dataset", "MALINDO Morph 2024"); put("license", "CC BY 4.0")
                }
                rows++
                if (batch.size >= 256) { library.appendBatch("knowledge", batch); batch = ArrayList(256) }
            }
        }
        if (batch.isNotEmpty()) library.appendBatch("knowledge", batch)
        recordImport("malindo-morph-id", file.length(), rows)
        return rows
    }

    private fun importApertium(file: File, provenance: String, preferRightSide: Boolean = false): Long {
        var added = 0L
        val parser = Xml.newPullParser().apply {
            setInput(file.inputStream().buffered(), "UTF-8")
        }
        var active: String? = null
        val buffer = StringBuilder()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    if (parser.name == "l" || parser.name == "r") {
                        active = parser.name
                        buffer.setLength(0)
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> {
                    if (active != null) buffer.append(parser.text)
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (parser.name == "l" || parser.name == "r") {
                        val accept = if (preferRightSide) active == "r" else active == "l"
                        val value = buffer.toString().trim().lowercase()
                        if (accept && isLexicalValue(value)) {
                            if (codebook.tokenId(value) == null) added++
                            codebook.registerToken(value, provenance)
                        }
                        active = null
                        buffer.setLength(0)
                    }
                }
            }
            event = parser.next()
        }
        recordImport(provenance, file.length(), added)
        return added
    }

    private fun isLexicalValue(value: String): Boolean {
        if (value.isBlank() || value.length > 120) return false
        if (value.contains('<') || value.contains('>')) return false
        return value.any { it.isLetterOrDigit() }
    }

    private fun importWordnet(file: File): Long {
        var rows = 0L
        var batch = ArrayList<JSONObject>(256)
        file.bufferedReader(Charsets.UTF_8, 128 * 1024).useLines { lines ->
            lines.forEach { line ->
                val cols = line.split('\t')
                if (cols.size < 4) return@forEach
                val lang = cols[1].trim()
                val lemma = cols[3].trim()
                if (lang != "I" || lemma.isBlank()) return@forEach
                codebook.registerToken(lemma, "Wordnet-Bahasa")
                batch += JSONObject().apply {
                    put("id", "wn-${(cols[0] + "|" + cols[1] + "|" + lemma).sha256Hex().take(24)}")
                    put("synset", cols[0]); put("language", lang); put("quality", cols[2]); put("lemma", lemma)
                    put("source", "Wordnet Bahasa"); put("license", "MIT")
                }
                rows++
                if (batch.size >= 256) { library.appendBatch("knowledge", batch); batch = ArrayList(256) }
            }
        }
        if (batch.isNotEmpty()) library.appendBatch("knowledge", batch)
        recordImport("wordnet-bahasa-id", file.length(), rows)
        return rows
    }

    private fun importUdGsd(): Long {
        val files = listOf(
            "id_gsd-ud-train.conllu",
            "id_gsd-ud-dev.conllu",
            "id_gsd-ud-test.conllu"
        )
        var seen = 0L
        files.forEach { fileName ->
            val file = download("https://raw.githubusercontent.com/UniversalDependencies/UD_Indonesian-GSD/master/$fileName")
            var batch = ArrayList<JSONObject>(256)
            file.bufferedReader(Charsets.UTF_8, 128 * 1024).useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val cols = line.split('\t')
                    if (cols.size < 4 || !cols[0].matches(Regex("\\d+(?:-\\d+)?"))) return@forEach
                    val form = cols[1]
                    val lemma = cols[2]
                    if (form != "_" && form.isNotBlank()) { codebook.registerToken(form, "UD-Indonesian-GSD"); seen++ }
                    if (lemma != "_" && lemma.isNotBlank()) { codebook.registerToken(lemma, "UD-Indonesian-GSD:lemma") }
                    batch += JSONObject().apply {
                        put("id", "ud-${fileName}-${line.hashCode()}")
                        put("form", form); put("lemma", lemma); put("upos", cols[3])
                        put("features", cols.getOrNull(5).orEmpty()); put("dependency", cols.getOrNull(7).orEmpty())
                        put("source", "UD Indonesian-GSD")
                    }
                    if (batch.size >= 256) { library.appendBatch("knowledge", batch); batch = ArrayList(256) }
                }
            }
            if (batch.isNotEmpty()) library.appendBatch("knowledge", batch)
        }
        recordImport("ud-id-gsd", files.size.toLong(), seen)
        return seen
    }

    private fun importPlainSentences(file: File, sourceName: String): Long {
        var rows = 0L
        var batch = ArrayList<JSONObject>(256)
        file.bufferedReader(Charsets.UTF_8, 128 * 1024).useLines { lines ->
            lines.forEach { line ->
                val sentence = line.trim()
                if (sentence.isBlank() || sentence.startsWith("#")) return@forEach
                tokenizeSentence(sentence).forEach { codebook.registerToken(it, sourceName) }
                batch += JSONObject().apply {
                    put("id", "${sourceName.lowercase()}-${rows + 1}")
                    put("text", sentence)
                    put("language", "ind")
                    put("source", sourceName)
                }
                rows++
                if (batch.size >= 256) { library.appendBatch("knowledge", batch); batch = ArrayList(256) }
            }
        }
        if (batch.isNotEmpty()) library.appendBatch("knowledge", batch)
        recordImport(sourceName.lowercase().replace(Regex("[^a-z0-9]+"), "-"), file.length(), rows)
        return rows
    }

    private fun importTatoeba(file: File): Long {
        var added = 0L
        var batch = ArrayList<JSONObject>(256)
        BZip2CompressorInputStream(file.inputStream().buffered(128 * 1024), true).use { bz ->
            BufferedReader(InputStreamReader(bz, Charsets.UTF_8), 128 * 1024).useLines { lines ->
                lines.forEach { line ->
                    val cols = line.split('\t')
                    if (cols.size < 2) return@forEach
                    val sentence = cols[1].trim()
                    if (sentence.isBlank()) return@forEach
                    tokenizeSentence(sentence).forEach { codebook.registerToken(it, "Tatoeba") }
                    batch += JSONObject().apply {
                        put("id", "tatoeba-${cols[0]}")
                        put("sentenceId", cols[0])
                        put("text", sentence)
                        put("language", "ind")
                        put("source", "Tatoeba")
                        put("license", "CC BY 2.0 default, verify per sentence")
                    }
                    added++
                    if (batch.size >= 256) { library.appendBatch("knowledge", batch); batch = ArrayList(256) }
                }
            }
        }
        if (batch.isNotEmpty()) library.appendBatch("knowledge", batch)
        recordImport("tatoeba-id", file.length(), added)
        return added
    }

    private fun tokenizeSentence(sentence: String): List<String> = sentence
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}'-]+"))
        .filter { it.length in 1..120 }
        .distinct()

    private fun recordImport(id: String, bytes: Long, records: Long) {
        library.append("index", JSONObject().apply {
            put("id", "language-import-$id-${System.currentTimeMillis()}")
            put("resourceId", id)
            put("bytes", bytes)
            put("records", records)
            put("timestamp", System.currentTimeMillis())
        })
    }

    private object LanguageResourceUrl {
        const val kaikki = "https://kaikki.org/idwiktionary/Bahasa%20Indonesia/kaikki.org-dictionary-BahasaIndonesia.jsonl"
        const val hunspell = "https://raw.githubusercontent.com/shuLhan/hunspell-id/master/id_ID.dic"
        const val tatoeba = "https://downloads.tatoeba.org/exports/per_language/ind/ind_sentences.tsv.bz2"
        const val malindoMorph = "https://raw.githubusercontent.com/matbahasa/MALINDO_Morph/master/malindo_dic_2024.tsv"
        const val wordnetBahasa = "https://raw.githubusercontent.com/limaginaire/Bahasa-Wordnet/master/wn-msa-all.tab"
        const val apertiumInd = "https://raw.githubusercontent.com/apertium/apertium-ind/master/apertium-ind.ind.dix"
        const val apertiumIndEng = "https://raw.githubusercontent.com/apertium/apertium-ind-eng/master/apertium-ind-eng.ind-eng.dix"
        const val talpco = "https://raw.githubusercontent.com/matbahasa/TALPCo/master/ind/data_ind.txt"
    }
}
