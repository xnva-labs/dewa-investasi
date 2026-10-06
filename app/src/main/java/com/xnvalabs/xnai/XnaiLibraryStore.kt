package com.xnvalabs.xnai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** File-backed unified library with logical and transport chunk limits. */
class XnaiLibraryStore(context: Context) {
    companion object {
        const val LOGICAL_UNIT_LIMIT = 500L * 1024L * 1024L
        const val TRANSPORT_CHUNK_LIMIT = 50L * 1024L * 1024L

        fun splitFileForTransport(file: File, cacheDir: File): List<File> {
            require(file.length() <= LOGICAL_UNIT_LIMIT) { "File logis melewati batas 500 MiB." }
            val outDir = File(cacheDir, "xnai_chunks/${file.name}").apply { deleteRecursively(); mkdirs() }
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

        fun fileSha256(file: File): String = checksumFile(file)

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
    private val vault by lazy { XnaiSymbolVault.get(context) }

    /** Everything is stored as symbols through the vault; no plaintext record ever reaches disk. */
    fun append(category: String, record: JSONObject): File {
        vault.put(category, record)
        return root
    }

    fun appendBatch(category: String, records: List<JSONObject>): File {
        vault.putAll(category, records)
        return root
    }

    fun readAll(category: String, maxRecords: Int = Int.MAX_VALUE): List<JSONObject> = vault.recent(category, maxRecords)

    fun listLogicalFiles(): List<File> = vault.logicalFiles()

    fun checksum(file: File): String = fileSha256(file)

    fun splitForTransport(file: File, cacheDir: File): List<File> = splitFileForTransport(file, cacheDir)

    /** Reports are symbol-encoded too: the MD ledger contains symbols only. */
    fun writeHumanReport(name: String, markdown: String): File = vault.appendMarkdown("report", markdown)

    fun rootDirectory(): File = root
}
