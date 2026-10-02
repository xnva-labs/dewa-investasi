package com.xnvalabs.smarteyex.data.companion

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Synthetic emotional state. This models expressive behavior; it does not
 * claim subjective consciousness or biological feelings.
 */
enum class EmotionalState {
    CALM, JOY, SADNESS, EXCITEMENT, FRUSTRATION, CURIOSITY, CONCERN, EMPATHY
}

data class EmotionalSnapshot(
    val state: EmotionalState = EmotionalState.CALM,
    val intensity: Float = 0f,
    val confidence: Float = 0f,
    val updatedAt: Long = 0L,
)

object EmotionEngine {
    private const val DECAY_HALF_LIFE_MS = 15 * 60 * 1000L
    private const val MAX_INTENSITY = 1f

    fun inferFromText(text: String): EmotionalSnapshot {
        val normalized = text.lowercase().trim()
        // Explicit emotion words carry more evidence than conversational slang.
        // This lets a sentence such as "gila keren, saya bahagia" resolve to JOY
        // without treating every enthusiastic adjective as the user's core emotion.
        val scores = linkedMapOf(
            EmotionalState.JOY to score(normalized, listOf("senang", "bahagia", "gembira", "berhasil", "mantap", "yes", "hore"), explicit = listOf("senang", "bahagia", "gembira")),
            EmotionalState.SADNESS to score(normalized, listOf("sedih", "kecewa", "gagal", "menangis", "hancur", "kehilangan"), explicit = listOf("sedih", "kecewa", "menangis")),
            EmotionalState.FRUSTRATION to score(normalized, listOf("kesal", "marah", "error", "gagal", "pusing", "benci", "anjing"), explicit = listOf("kesal", "marah", "benci")),
            EmotionalState.EXCITEMENT to score(normalized, listOf("gila", "keren", "gas", "launch", "ide baru", "menarik banget")),
            EmotionalState.CONCERN to score(normalized, listOf("takut", "khawatir", "bahaya", "bingung", "cemas", "darurat"), explicit = listOf("takut", "khawatir", "cemas")),
            EmotionalState.CURIOSITY to score(normalized, listOf("kenapa", "bagaimana", "apakah", "mungkin", "penasaran", "jelaskan")),
        )
        val best = scores.maxByOrNull { it.value } ?: return EmotionalSnapshot()
        if (best.value <= 0f) return EmotionalSnapshot(EmotionalState.CALM, 0.1f, 0.25f, System.currentTimeMillis())
        return EmotionalSnapshot(best.key, best.value.coerceIn(0f, MAX_INTENSITY), 0.65f, System.currentTimeMillis())
    }

    fun merge(current: EmotionalSnapshot, incoming: EmotionalSnapshot, now: Long = System.currentTimeMillis()): EmotionalSnapshot {
        val decayed = decay(current, now)
        val weight = incoming.confidence.coerceIn(0f, 1f)
        val incomingIntensity = incoming.intensity.coerceIn(0f, 1f)
        return EmotionalSnapshot(
            state = if (incomingIntensity * weight >= decayed.intensity) incoming.state else decayed.state,
            intensity = max(decayed.intensity * (1f - weight * 0.35f), incomingIntensity * weight).coerceIn(0f, 1f),
            confidence = max(decayed.confidence * 0.7f, weight).coerceIn(0f, 1f),
            updatedAt = now,
        )
    }

    fun decay(snapshot: EmotionalSnapshot, now: Long = System.currentTimeMillis()): EmotionalSnapshot {
        if (snapshot.updatedAt <= 0L) return snapshot
        val elapsed = (now - snapshot.updatedAt).coerceAtLeast(0L).toDouble()
        val factor = exp(-ln(2.0) * elapsed / DECAY_HALF_LIFE_MS.toDouble()).toFloat()
        return snapshot.copy(
            intensity = (snapshot.intensity.coerceIn(0f, 1f) * factor).coerceIn(0f, 1f),
            confidence = (snapshot.confidence.coerceIn(0f, 1f) * factor).coerceIn(0f, 1f),
            updatedAt = now,
        )
    }

    private fun score(text: String, terms: List<String>, explicit: List<String> = emptyList()): Float {
        if (terms.isEmpty()) return 0f
        val explicitHit = explicit.any { hasPositiveMention(text, it) }
        val hits = terms.count { hasPositiveMention(text, it) }
        val base = (hits.toFloat() / terms.size * 0.85f).coerceAtMost(1f)
        return if (explicitHit) max(base, 0.8f) else base
    }

    private fun hasPositiveMention(text: String, term: String): Boolean {
        var fromIndex = 0
        while (true) {
            val index = text.indexOf(term, fromIndex)
            if (index < 0) return false
            val before = text.substring(maxOf(0, index - NEGATION_WINDOW), index)
            val recentWords = before
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .takeLast(NEGATION_WORD_LOOKBACK)
                .map { it.trimEnd(',', '.', '!', '?', ':', ';') }
            if (recentWords.none { it in NEGATION_MARKERS }) return true
            fromIndex = index + term.length
        }
    }

    private const val NEGATION_WINDOW = 32
    private const val NEGATION_WORD_LOOKBACK = 3
    private val NEGATION_MARKERS = setOf(
        "tidak", "nggak", "gak", "ga", "bukan", "belum", "tak",
    )
}
