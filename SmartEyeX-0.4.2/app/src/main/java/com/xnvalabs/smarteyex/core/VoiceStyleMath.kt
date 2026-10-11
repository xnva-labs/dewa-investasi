package com.xnvalabs.smarteyex.core

/** Pure, bounded normalization for optional user-consented prosody adaptation. */
internal object VoiceStyleMath {
    private const val REFERENCE_WORDS_PER_MINUTE = 220f

    fun normalizedSpeakingRate(wordCount: Int, durationMs: Long): Float {
        if (wordCount <= 0) return 0.5f
        val minutes = durationMs.coerceAtLeast(1L) / 60_000f
        val wordsPerMinute = wordCount.coerceAtLeast(0) / minutes
        return (wordsPerMinute / REFERENCE_WORDS_PER_MINUTE).coerceIn(0.15f, 1f)
    }

    fun pitch(meanEnergy: Float): Float =
        (1f + ((meanEnergy.coerceIn(0f, 1f) - 0.5f) * 0.30f)).coerceIn(0.85f, 1.15f)

    fun speechRate(normalizedRate: Float): Float =
        (1f + ((normalizedRate.coerceIn(0f, 1f) - 0.5f) * 0.30f)).coerceIn(0.85f, 1.15f)
}
