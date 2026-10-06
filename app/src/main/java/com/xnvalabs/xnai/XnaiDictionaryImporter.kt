package com.xnvalabs.xnai

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Imports user-provided dictionaries, including authorized KBBI exports. */
class XnaiDictionaryImporter(context: Context) {
    private val appContext = context.applicationContext
    private val library = XnaiLibraryStore(appContext)
    private val codebook = RecursiveCodebook(context = appContext)

    fun importTextDictionary(context: Context, uri: Uri, provenance: String): Int {
        var count = 0
        var batch = ArrayList<JSONObject>(256)
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { raw ->
                val token = raw.substringBefore('\t').substringBefore(',').trim().removeSurrounding("\"")
                if (token.isBlank() || token.length > 120) return@forEach
                if (codebook.tokenId(token) == null) {
                    val symbol = codebook.registerToken(token, provenance)
                    batch += symbolRecord(symbol, "word")
                    count++
                }
                if (batch.size >= 256) { library.appendBatch("codebook", batch); batch = ArrayList(256) }
            }
        } ?: throw IllegalArgumentException("Berkas kamus tidak dapat dibuka.")
        if (batch.isNotEmpty()) library.appendBatch("codebook", batch)
        writeImportIndex(provenance, count)
        return count
    }

    fun importJsonLinesDictionary(context: Context, uri: Uri, provenance: String): Int {
        var count = 0
        var batch = ArrayList<JSONObject>(256)
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val obj = try { JSONObject(line) } catch (_: Throwable) { return@forEach }
                val word = obj.optString("word", obj.optString("kata")).trim()
                if (word.isBlank() || word.length > 120) return@forEach
                if (codebook.tokenId(word) == null) {
                    val symbol = codebook.registerToken(word, provenance)
                    batch += symbolRecord(symbol, "word")
                    count++
                    val meaning = extractMeaning(obj)
                    if (meaning.isNotBlank()) {
                        batch += JSONObject().apply {
                            put("id", "meaning-${symbol.id}")
                            put("word", word)
                            put("lexicalId", symbol.id)
                            put("meaning", meaning.take(12000))
                            put("source", provenance)
                        }
                    }
                }
                if (batch.size >= 256) { flushJsonBatch(batch); batch = ArrayList(256) }
            }
        } ?: throw IllegalArgumentException("Berkas JSONL tidak dapat dibuka.")
        if (batch.isNotEmpty()) flushJsonBatch(batch)
        writeImportIndex(provenance, count)
        return count
    }


    private fun flushJsonBatch(batch: List<JSONObject>) {
        val code = batch.filter { !it.optString("id").startsWith("meaning-") }
        val knowledge = batch.filter { it.optString("id").startsWith("meaning-") }
        if (code.isNotEmpty()) library.appendBatch("codebook", code)
        if (knowledge.isNotEmpty()) library.appendBatch("knowledge", knowledge)
    }

    private fun symbolRecord(symbol: CodebookSymbol, kind: String) = JSONObject().apply {
        put("id", symbol.id)
        put("level", symbol.level)
        put("children", JSONArray(symbol.children))
        put("definition", symbol.definition)
        put("version", symbol.version)
        put("provenance", symbol.provenance)
        put("checksum", symbol.checksum)
        put("kind", kind)
    }

    private fun extractMeaning(obj: JSONObject): String {
        val direct = obj.optString("arti", obj.optString("definition", "")).trim()
        if (direct.isNotBlank()) return direct
        val makna = obj.optJSONArray("makna") ?: return ""
        return buildString {
            for (i in 0 until makna.length()) {
                val s = makna.optString(i).trim()
                if (s.isNotBlank()) { if (isNotEmpty()) append("\n"); append(s) }
            }
        }
    }

    private fun writeImportIndex(provenance: String, count: Int) {
        library.append("index", JSONObject().apply {
            put("id", "dictionary-import-${System.currentTimeMillis()}")
            put("type", "dictionary-import")
            put("provenance", provenance)
            put("tokens", count)
            put("numericIds", true)
        })
    }
}
