package com.xnvalabs.smarteyex.data.voice

/** Non-identifying acoustic profile used for expressive TTS adaptation. Raw audio is not stored here. */
data class VoiceProfile(
    val meanEnergy: Float = 0.5f,
    val speakingRate: Float = 0.5f,
    val preferredLanguageTag: String = "id-ID",
    val sampleCount: Int = 0,
)
