package com.xnvalabs.xnai

/** Parse one Hunspell .dic entry, removing affix flags without truncating escaped slashes. */
object HunspellLexiconParser {
    private val lexicalPattern = Regex("^[\\p{L}\\p{M}]+(?:[-'’][\\p{L}\\p{M}]+)*$")

    fun parseLine(raw: String, isHeader: Boolean = false): String? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        if (isHeader && line.toLongOrNull() != null) return null
        var slash = -1
        var escaped = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '/' && !escaped) { slash = i; break }
            if (c == '\\' && !escaped) escaped = true else escaped = false
        }
        val rawWord = (if (slash >= 0) line.substring(0, slash) else line).replace("\\/", "/")
        val word = rawWord.trim().lowercase()
        return word.takeIf { it.isNotEmpty() && it.length <= 120 && lexicalPattern.matches(it) }
    }
}
