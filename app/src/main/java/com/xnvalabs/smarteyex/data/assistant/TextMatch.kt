package com.xnvalabs.smarteyex.data.assistant

import kotlin.math.max
import kotlin.math.min

/** Small dependency-free text helpers for voice-command matching (pure JVM, unit-tested). */
object TextMatch {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}\\s]")
    private val SPACES = Regex("\\s+")

    /** Lowercase, strip punctuation, collapse whitespace. */
    fun norm(text: String): String = NON_WORD.replace(text.lowercase(), " ").replace(SPACES, " ").trim()

    /** One spoken word reduced to lowercase letters and digits. */
    fun token(word: String): String = word.lowercase().filter { it.isLetterOrDigit() }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val swap = prev
            prev = cur
            cur = swap
        }
        return prev[b.length]
    }

    /** Speech recognition often gets a name wrong by a letter; tolerate that for words of 3+ letters. */
    fun similarWord(a: String, b: String): Boolean {
        if (a == b) return true
        if (min(a.length, b.length) < 3) return false
        val allowed = if (max(a.length, b.length) >= 8) 2 else 1
        return levenshtein(a, b) <= allowed
    }
}
