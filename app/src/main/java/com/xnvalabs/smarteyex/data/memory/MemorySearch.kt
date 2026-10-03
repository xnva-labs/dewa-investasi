package com.xnvalabs.smarteyex.data.memory

/** Pencarian memori lokal: tanpa huruf besar/kecil, semua kata harus muncul (AND), di judul atau isi. */
object MemorySearch {
    fun filter(entries: List<MemoryEntry>, query: String): List<MemoryEntry> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return entries
        return entries.filter { entry ->
            val haystack = (entry.title + " " + entry.content).lowercase()
            words.all { haystack.contains(it) }
        }
    }
}
