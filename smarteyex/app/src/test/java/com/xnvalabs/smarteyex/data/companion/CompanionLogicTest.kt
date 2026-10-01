package com.xnvalabs.smarteyex.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionLogicTest {
    @Test fun voiceRouterRecognizesNavigation() {
        assertEquals(VoiceIntent.OPEN_MEMORY, VoiceCommandRouter.parse("buka memori").intent)
        assertEquals(VoiceIntent.OPEN_TRANSLATION, VoiceCommandRouter.parse("tolong terjemahkan ini").intent)
    }

    @Test fun voiceRouterRequiresConfirmationForEmergency() {
        assertTrue(VoiceCommandRouter.parse("mode darurat").requiresConfirmation)
    }

    @Test fun emotionInferenceIsBounded() {
        val snapshot = EmotionEngine.inferFromText("gila keren banget saya bahagia")
        assertEquals(EmotionalState.JOY, snapshot.state)
        assertTrue(snapshot.intensity in 0f..1f)
    }

    @Test fun emotionDecays() {
        val old = EmotionalSnapshot(EmotionalState.EXCITEMENT, 1f, 1f, 1L)
        val decayed = EmotionEngine.decay(old, old.updatedAt + 15 * 60 * 1000L)
        assertTrue(decayed.intensity < old.intensity)
    }
}
