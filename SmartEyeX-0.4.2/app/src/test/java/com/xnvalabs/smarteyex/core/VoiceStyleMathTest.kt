package com.xnvalabs.smarteyex.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStyleMathTest {
    @Test fun typicalSpeechDoesNotSaturateTheRateProfile() {
        val rate150Wpm = VoiceStyleMath.normalizedSpeakingRate(150, 60_000L)
        assertTrue(rate150Wpm in 0.6f..0.8f)
        assertTrue(VoiceStyleMath.normalizedSpeakingRate(75, 60_000L) < rate150Wpm)
    }

    @Test fun silentOrInvalidDurationInputsStayBounded() {
        assertEquals(0.5f, VoiceStyleMath.normalizedSpeakingRate(0, 0), 0.0001f)
        assertTrue(VoiceStyleMath.normalizedSpeakingRate(200, 1) <= 1f)
        assertTrue(VoiceStyleMath.normalizedSpeakingRate(200, 1) >= 0.15f)
    }

    @Test fun neutralProsodyKeepsNaturalTtsSettings() {
        assertEquals(1f, VoiceStyleMath.pitch(0.5f), 0.0001f)
        assertEquals(1f, VoiceStyleMath.speechRate(0.5f), 0.0001f)
        assertTrue(VoiceStyleMath.pitch(0.9f) > 1f)
        assertTrue(VoiceStyleMath.pitch(0.1f) < 1f)
    }
}
