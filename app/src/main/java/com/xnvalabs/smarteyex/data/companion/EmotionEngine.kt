package com.xnvalabs.smarteyex.data.companion

import kotlin.math.exp
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
        val normalized = text.lowercase()
        val scores = linkedMapOf(
            EmotionalState.JOY to score(normalized, listOf("senang", "bahagia", "gembira", "berhasil", "mantap", "yes", "hore")),
            EmotionalState.SADNESS to score(normalized, listOf("sedih", "kecewa", "gagal", "menangis", "hancur", "kehilangan")),
            EmotionalState.FRUSTRATION to score(normalized, listOf("kesal", "marah", "error", "gagal", "pusing", "benci", "anjing")),
            EmotionalState.EXCITEMENT to score(normalized, listOf("gila", "keren", "gas", "launch", "ide baru", "menarik banget")),
            EmotionalState.CONCERN to score(normalized, listOf("takut", "khawatir", "bahaya", "bingung", "cemas", "darurat")),
            EmotionalState.CURIOSITY to score(normalized, listOf("kenapa", "bagaimana", "apakah", "mungkin", "penasaran", "jelaskan")),
        )
        val best = scores.maxByOrNull { it.value } ?: return EmotionalSnapshot()
        if (best.value <= 0f) return EmotionalSnapshot(EmotionalState.CALM, 0.1f, 0.25f, System.currentTimeMillis())
        return EmotionalSnapshot(best.key, best.value.coerceIn(0f, MAX_INTENSITY), 0.65f, System.currentTimeMillis())
    }

    fun merge(current: EmotionalSnapshot, incoming: EmotionalSnapshot, now: Long = System.currentTimeMillis()): EmotionalSnapshot {
        val decayed = decay(current, now)
        val weight = incoming.confidence.coerceIn(0f, 1f)
        return EmotionalSnapshot(
            state = if (incoming.intensity * weight >= decayed.intensity) incoming.state else decayed.state,
            intensity = max(decayed.intensity * (1f - weight * 0.35f), incoming.intensity * weight).coerceIn(0f, 1f),
            confidence = max(decayed.confidence * 0.7f, incoming.confidence),
            updatedAt = now,
        )
    }

    fun decay(snapshot: EmotionalSnapshot, now: Long = System.currentTimeMillis()): EmotionalSnapshot {
        if (snapshot.updatedAt <= 0L) return snapshot
        val elapsed = (now - snapshot.updatedAt).coerceAtLeast(0L).toDouble()
        val factor = exp(-elapsed / DECAY_HALF_LIFE_MS.toDouble()).toFloat()
        return snapshot.copy(intensity = (snapshot.intensity * factor).coerceIn(0f, 1f), confidence = snapshot.confidence * factor, updatedAt = now)
    }

    private fun score(text: String, terms: List<String>): Float {
        if (terms.isEmpty()) return 0f
        val hits = terms.count { text.contains(it) }
        return (hits.toFloat() / terms.size * 0.85f).coerceAtMost(1f)
    }
}
