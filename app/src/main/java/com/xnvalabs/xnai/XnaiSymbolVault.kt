package com.xnvalabs.xnai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * Symbol-only storage for everything XNAI thinks and remembers.
 *
 * - One active JSON file and one active MD file at a time, each capped at 20 MiB; then a new file starts.
 * - File names are chosen by XNAI itself from the composite symbols it used most.
 * - File content is symbols only (self-delimiting numeric codes). Meaning lives in the on-device codebook.
 */
class XnaiSymbolVault private constructor(private val appContext: Context) {
    companion object {
        const val FILE_LIMIT = 20L * 1024L * 1024L
        private const val TAIL_CAP = 2000
        private const val INIT_LINES = 20000
        /** Categories XNAI reads back into working memory; traces, skills and reports are write-only journals. */
        private val CACHED = setOf("thinking", "experiments", "memory", "knowledge", "index", "codebook")
        private const val FOOTER = "\n]}"
        private val LEGACY_CATEGORIES = listOf("thinking", "experiments", "memory", "knowledge", "index", "codebook")

        @Volatile private var instance: XnaiSymbolVault? = null

        fun get(context: Context): XnaiSymbolVault =
            instance ?: synchronized(this) {
                instance ?: XnaiSymbolVault(context.applicationContext).also { instance = it }
            }
    }

    val codebook = RecursiveCodebook(context = appContext)
    private val store: XnaiLexiconStore = codebook.lexiconStore() ?: error("Lexicon store tidak tersedia.")
    private val codecPrefs = appContext.getSharedPreferences("xnai_codec", Context.MODE_PRIVATE)
    val codec = SymbolCodec(LexiconSymbolBackend(store), codecPrefs.getInt("threshold", 3)).also { c ->
        c.onPolicyChange = { codecPrefs.edit().putInt("threshold", it).apply() }
    }
    val lexicon: XnaiLexiconStore get() = store
    val mind: XnaiMind by lazy { XnaiMind(LexiconMindStore(store)) }
    private val lastGlyphs = ArrayDeque<String>()

    private val root = File(appContext.filesDir, "XNAI_LIBRARY").apply { mkdirs() }
    private val dir = File(root, "vault").apply { mkdirs() }
    private val manifestFile = File(root, "vault_manifest.json")
    private val legacyDir = File(appContext.filesDir, "XNAI_LEGACY_PLAIN")
    private val legacyMarker = File(dir, ".legacy_done")

    private class Entry(val name: String, var sealed: Boolean, var bytes: Long, var sha: String)

    private val jsonEntries = ArrayList<Entry>()
    private val mdEntries = ArrayList<Entry>()
    private val tails = HashMap<String, ArrayDeque<JSONObject>>()
    private var tailLoaded = false

    init { loadManifest() }

    // ---- public API ----

    @Synchronized
    fun put(category: String, record: JSONObject) = putAll(category, listOf(record))

    @Synchronized
    fun putAll(category: String, records: List<JSONObject>) {
        if (records.isEmpty()) return
        ensureTailLoaded()
        val k = codec.exactIdOf(category)
        for (record in records) {
            val vals = codec.encode(record.toString())
            val body = SymbolAlphabet.encode(vals)
            lastGlyphs.addLast(GlyphNotation.render(vals, 10))
            while (lastGlyphs.size > 40) lastGlyphs.removeFirst()
            val line = "{\"k\":$k,\"t\":${System.currentTimeMillis()},\"s\":\"$body\"}"
            appendJsonLine(line)
            if (category in CACHED) {
                val tail = tails.getOrPut(category) { ArrayDeque() }
                tail.addLast(record)
                while (tail.size > TAIL_CAP) tail.removeFirst()
            }
        }
    }

    /** Appends a symbol-encoded block to the active MD ledger. */
    @Synchronized
    fun appendMarkdown(category: String, text: String): File {
        val header = SymbolAlphabet.encode(longArrayOf(codec.exactIdOf(category), System.currentTimeMillis()))
        val block = "@$header\n${codec.encodeToString(text)}\n\n"
        val bytes = block.toByteArray(Charsets.UTF_8)
        val file = activeFile(mdEntries, "M", ".md", bytes.size.toLong()) { f, title ->
            f.writeText("# $title\n\n", Charsets.UTF_8)
        }
        file.appendBytes(bytes)
        return file
    }

    /** Most recent decoded records for a category, oldest first. */
    @Synchronized
    fun recent(category: String, max: Int): List<JSONObject> {
        ensureTailLoaded()
        val tail = tails[category] ?: return emptyList()
        return if (max >= tail.size) tail.toList() else tail.toList().takeLast(max)
    }

    @Synchronized
    fun logicalFiles(): List<File> {
        val out = ArrayList<File>()
        (jsonEntries + mdEntries).forEach { e -> File(dir, e.name).takeIf { it.isFile }?.let(out::add) }
        if (manifestFile.isFile) out.add(manifestFile)
        return out
    }

    /** Files still being appended to (their upload is throttled until they are sealed). */
    @Synchronized
    fun activeFiles(): Set<File> =
        (jsonEntries + mdEntries).filter { !it.sealed }.map { File(dir, it.name) }.toSet()

    /** What XNAI's latest thoughts look like once stored (display projection only). */
    @Synchronized
    fun glyphPreview(n: Int): List<String> = lastGlyphs.toList().takeLast(n)

    @Synchronized
    fun stats(): String {
        val bytes = (jsonEntries + mdEntries).sumOf { File(dir, it.name).length() }
        return "file=${jsonEntries.size + mdEntries.size} · ${bytes / 1024} KiB · kata=${codebook.wordCount()} · simbol=${store.mergeCount()} · ambang=${codec.threshold}"
    }

    /** Moves old plaintext library files out of the sync tree and re-stores them as symbols. Safe to call repeatedly. */
    @Synchronized
    fun migrateLegacyPlaintext() {
        if (legacyMarker.exists()) return
        ensureTailLoaded()
        for (cat in LEGACY_CATEGORIES) {
            val src = File(root, cat)
            if (!src.isDirectory) continue
            val dst = File(legacyDir, cat).apply { mkdirs() }
            src.listFiles()?.sortedBy { it.name }?.forEach { f ->
                try {
                    when {
                        f.extension == "jsonl" && f.name.startsWith("$cat-") -> {
                            val batch = ArrayList<JSONObject>(256)
                            f.bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    if (line.isBlank()) return@forEach
                                    try { batch.add(JSONObject(line)) } catch (_: Throwable) { }
                                    if (batch.size >= 256) { putAll(cat, batch.toList()); batch.clear() }
                                }
                            }
                            if (batch.isNotEmpty()) putAll(cat, batch.toList())
                        }
                        f.extension == "md" -> appendMarkdown(cat, f.readText())
                    }
                    f.renameTo(File(dst, f.name)) || f.copyTo(File(dst, f.name), true).exists().also { f.delete() }
                } catch (_: Throwable) {
                    return@forEach
                }
            }
        }
        File(root, "index").listFiles()?.forEach { f ->
            if (f.name.startsWith("library_index")) {
                File(legacyDir, "index").mkdirs()
                f.renameTo(File(File(legacyDir, "index"), f.name))
            }
        }
        legacyMarker.writeText("ok")
    }

    // ---- JSON file handling ----

    private fun activeFile(list: MutableList<Entry>, prefix: String, ext: String, incoming: Long, init: (File, String) -> Unit): File {
        val current = list.lastOrNull()?.takeIf { !it.sealed }
        if (current != null) {
            val f = File(dir, current.name)
            if (f.exists() && f.length() + incoming <= FILE_LIMIT) return f
            seal(current)
        }
        val entry = newEntry(prefix, ext, list)
        val f = File(dir, entry.name)
        init(f, entry.name.removeSuffix(ext))
        saveManifest()
        return f
    }

    private fun appendJsonLine(line: String) {
        val bytes = line.toByteArray(Charsets.UTF_8)
        val file = activeFile(jsonEntries, "J", ".json", bytes.size + 2L) { f, title ->
            f.writeText("{\"fmt\":\"xnai-sym-1\",\"title\":\"$title\",\"r\":[$FOOTER", Charsets.UTF_8)
        }
        RandomAccessFile(file, "rw").use { raf ->
            val len = raf.length()
            raf.seek(len - 4)
            val empty = raf.read() == '['.code
            raf.seek(len - FOOTER.length)
            raf.write((if (empty) "\n" else ",\n").toByteArray(Charsets.UTF_8))
            raf.write(bytes)
            raf.write(FOOTER.toByteArray(Charsets.UTF_8))
        }
    }

    private fun newEntry(prefix: String, ext: String, list: MutableList<Entry>): Entry {
        val dominant = codec.takeDominantSymbols(2)
        val a = dominant.getOrNull(0) ?: (System.nanoTime() ushr 9) and 0xFFFFFL
        val b = dominant.getOrNull(1) ?: (System.currentTimeMillis() and 0xFFFFFL)
        var seq = list.size
        var name: String
        do {
            name = "$prefix.${SymbolAlphabet.encode(longArrayOf(a))}.${SymbolAlphabet.encode(longArrayOf(b))}.${seq.toString().padStart(4, '0')}$ext"
            seq++
        } while (File(dir, name).exists())
        return Entry(name, false, 0L, "").also { list.add(it) }
    }

    private fun seal(entry: Entry) {
        val f = File(dir, entry.name)
        entry.sealed = true
        entry.bytes = f.length()
        entry.sha = if (f.isFile) XnaiLibraryStore.fileSha256(f) else ""
        saveManifest()
    }

    // ---- manifest ----

    private fun loadManifest() {
        try {
            if (manifestFile.isFile) {
                val o = JSONObject(manifestFile.readText())
                fun read(key: String, into: MutableList<Entry>) {
                    val arr = o.optJSONArray(key) ?: return
                    for (i in 0 until arr.length()) {
                        val e = arr.optJSONObject(i) ?: continue
                        into.add(Entry(e.getString("n"), e.optBoolean("sealed"), e.optLong("bytes"), e.optString("sha")))
                    }
                }
                read("json", jsonEntries)
                read("md", mdEntries)
            }
        } catch (_: Throwable) {
            jsonEntries.clear(); mdEntries.clear()
        }
        if (jsonEntries.isEmpty() && mdEntries.isEmpty()) {
            dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }?.forEach { f ->
                when (f.extension) {
                    "json" -> jsonEntries.add(Entry(f.name, true, f.length(), ""))
                    "md" -> mdEntries.add(Entry(f.name, true, f.length(), ""))
                }
            }
            jsonEntries.lastOrNull()?.let { if (File(dir, it.name).length() < FILE_LIMIT) it.sealed = false }
            mdEntries.lastOrNull()?.let { if (File(dir, it.name).length() < FILE_LIMIT) it.sealed = false }
        }
        jsonEntries.lastOrNull()?.takeIf { !it.sealed }?.let { repairJsonTail(File(dir, it.name)) }
    }

    private fun saveManifest() {
        fun arr(list: List<Entry>) = JSONArray().also { a ->
            list.forEach { e ->
                a.put(JSONObject().apply { put("n", e.name); put("sealed", e.sealed); put("bytes", e.bytes); put("sha", e.sha) })
            }
        }
        manifestFile.writeText(JSONObject().apply {
            put("fmt", "xnai-sym-1"); put("json", arr(jsonEntries)); put("md", arr(mdEntries))
        }.toString())
    }

    /** If a crash left the active JSON file without its footer, keep valid record lines and rewrite the footer. */
    private fun repairJsonTail(f: File) {
        if (!f.isFile || f.length() < 8) return
        val ok = RandomAccessFile(f, "r").use { raf ->
            raf.seek(raf.length() - FOOTER.length)
            val buf = ByteArray(FOOTER.length)
            raf.readFully(buf)
            String(buf, Charsets.UTF_8) == FOOTER
        }
        if (ok) return
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.bufferedWriter().use { w ->
            var header: String? = null
            var first = true
            f.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trimEnd(',')
                    if (header == null && line.startsWith("{\"fmt\"")) { header = line.trimEnd('[', '\n'); return@forEach }
                    if (line.startsWith("{\"k\":") && line.endsWith("}")) {
                        if (first) { w.write((header ?: "{\"fmt\":\"xnai-sym-1\",\"r\":") + "[\n"); first = false } else w.write(",\n")
                        w.write(line)
                    }
                }
            }
            if (first) w.write((header ?: "{\"fmt\":\"xnai-sym-1\",\"r\":") + "[")
            w.write(FOOTER)
        }
        tmp.renameTo(f)
    }

    // ---- tail cache ----

    private fun ensureTailLoaded() {
        if (tailLoaded) return
        tailLoaded = true
        var need = INIT_LINES
        val chunks = ArrayList<List<String>>()
        for (entry in jsonEntries.asReversed()) {
            if (need <= 0) break
            val f = File(dir, entry.name)
            if (!f.isFile) continue
            val ring = ArrayDeque<String>()
            try {
                f.bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        if (raw.startsWith("{\"k\":")) {
                            ring.addLast(raw.trimEnd(','))
                            if (ring.size > need) ring.removeFirst()
                        }
                    }
                }
            } catch (_: Throwable) { }
            need -= ring.size
            chunks.add(0, ring.toList())
        }
        for (chunk in chunks) for (line in chunk) {
            try {
                val o = JSONObject(line)
                val cat = codec.exactTokenOf(o.getLong("k")) ?: continue
                if (cat !in CACHED) continue
                val rec = JSONObject(codec.decodeString(o.getString("s")))
                val tail = tails.getOrPut(cat) { ArrayDeque() }
                tail.addLast(rec)
                while (tail.size > TAIL_CAP) tail.removeFirst()
            } catch (_: Throwable) { }
        }
    }
}
