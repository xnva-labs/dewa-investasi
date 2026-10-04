package com.xnvalabs.xnai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** File-backed unified library with logical and transport chunk limits. */
class XnaiLibraryStore(context: Context) {
    companion object {
        const val LOGICAL_UNIT_LIMIT = 500L * 1024L * 1024L
        const val TRANSPORT_CHUNK_LIMIT = 50L * 1024L * 1024L

        fun splitFileForTransport(file: File, cacheDir: File): List<File> {
            require(file.length() <= LOGICAL_UNIT_LIMIT) { "File logis melewati batas 500 MiB." }
            val outDir = File(cacheDir, "xnai_chunks/${file.nameWithoutExtension}").apply { mkdirs() }
            val chunks = mutableListOf<File>()
            file.inputStream().buffered(128 * 1024).use { input ->
                var index = 0
                while (true) {
                    val chunk = File(outDir, "%04d.part".format(index))
                    var written = 0L
                    chunk.outputStream().buffered(128 * 1024).use { output ->
                        val buf = ByteArray(128 * 1024)
                        while (written < TRANSPORT_CHUNK_LIMIT) {
                            val want = minOf(buf.size.toLong(), TRANSPORT_CHUNK_LIMIT - written).toInt()
                            val n = input.read(buf, 0, want)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            written += n
                        }
                    }
                    if (written == 0L) {
                        chunk.delete()
                        break
                    }
                    chunks += chunk
                    index++
                }
            }
            val manifest = File(outDir, "manifest.json")
            val array = JSONArray()
            chunks.forEachIndexed { i, part ->
                array.put(JSONObject().apply {
                    put("chunkId", "%s-%04d".format(file.name, i))
                    put("sequence", i)
                    put("bytes", part.length())
                    put("sha256", checksumFile(part))
                    put("source", file.name)
                })
            }
            manifest.writeText(JSONObject().apply {
                put("logicalFile", file.name)
                put("logicalBytes", file.length())
                put("logicalSha256", checksumFile(file))
                put("maxChunkBytes", TRANSPORT_CHUNK_LIMIT)
                put("chunks", array)
            }.toString(2))
            return chunks + manifest
        }

        private fun checksumFile(file: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(128 * 1024).use { input ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private val root = File(context.filesDir, "XNAI_LIBRARY").apply { mkdirs() }
    private val lock = ReentrantReadWriteLock()

    private fun suffix(index: Int): String {
        var n = index
        val out = StringBuilder()
        do {
            out.append(('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return out.reverse().toString()
    }

    private fun categoryDir(category: String): File = File(root, category).apply { mkdirs() }

    fun append(category: String, record: JSONObject): File = appendBatch(category, listOf(record))

    /** Efficient streaming append for large language corpora; avoids rewriting one giant JSON index. */
    fun appendBatch(category: String, records: List<JSONObject>): File = lock.write {
        if (records.isEmpty()) return@write categoryDir(category)
        val lines = records.map { (it.toString() + "\n").toByteArray(Charsets.UTF_8) }
        require(lines.all { it.size.toLong() <= LOGICAL_UNIT_LIMIT }) { "Satu record melebihi batas unit logis 500 MiB." }
        val total = lines.sumOf { it.size.toLong() }
        require(total <= LOGICAL_UNIT_LIMIT) { "Batch melebihi batas unit logis; gunakan batch lebih kecil." }

        val dir = categoryDir(category)
        var index = 0
        var file: File
        while (true) {
            file = File(dir, "$category-${suffix(index)}.jsonl")
            if (!file.exists() || file.length() + total <= LOGICAL_UNIT_LIMIT) break
            index++
        }
        FileWriter(file, true).use { writer ->
            lines.forEach { writer.write(String(it, Charsets.UTF_8)) }
        }

        val indexDir = File(root, "index").apply { mkdirs() }
        val indexFile = File(indexDir, "library_index.jsonl")
        FileWriter(indexFile, true).use { writer ->
            records.forEachIndexed { i, record ->
                writer.write(JSONObject().apply {
                    put("category", category)
                    put("file", file.relativeTo(root).path)
                    put("bytesAdded", lines[i].size)
                    put("recordId", record.optString("id"))
                    put("recordChecksum", record.toString().sha256Hex())
                    put("updatedAt", System.currentTimeMillis())
                }.toString())
                writer.write("\n")
            }
        }
        val summary = File(indexDir, "library_index.json")
        summary.writeText(JSONObject().apply {
            put("updatedAt", System.currentTimeMillis())
            put("indexFormat", "jsonl")
            put("indexFile", "index/library_index.jsonl")
        }.toString(2))
        file
    }

    fun readAll(category: String, maxRecords: Int = Int.MAX_VALUE): List<JSONObject> = lock.read {
        val dir = categoryDir(category)
        val files = dir.listFiles { f -> f.name.startsWith("$category-") && f.extension == "jsonl" }
            ?.sortedBy { it.name } ?: return@read emptyList()
        val out = ArrayList<JSONObject>()
        for (file in files) {
            BufferedReader(FileReader(file)).useLines { lines ->
                lines.forEach { line ->
                    if (out.size < maxRecords && line.isNotBlank()) {
                        try { out.add(JSONObject(line)) } catch (_: Throwable) { }
                    }
                }
            }
            if (out.size >= maxRecords) break
        }
        return@read out
    }

    fun listLogicalFiles(): List<File> = lock.read {
        root.walkTopDown().filter { it.isFile && it.extension in setOf("jsonl", "json", "md", "bin") }.toList()
    }

    fun checksum(file: File): String = Companion.run { checksumFile(file) }

    fun splitForTransport(file: File, cacheDir: File): List<File> = splitFileForTransport(file, cacheDir)

    fun writeHumanReport(name: String, markdown: String): File = lock.write {
        val dir = File(root, "thinking").apply { mkdirs() }
        File(dir, name).apply { writeText(markdown) }
    }

    fun rootDirectory(): File = root

}
