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
        assertTrue(snapshot.confidence in 0f..1f)
    }

    @Test fun explicitEmotionOutweighsPositiveSlang() {
        val snapshot = EmotionEngine.inferFromText("gila keren banget saya bahagia")
        assertEquals(EmotionalState.JOY, snapshot.state)
    }

    @Test fun neutralTextDoesNotInventStrongEmotion() {
        val snapshot = EmotionEngine.inferFromText("saya sedang membaca buku")
        assertEquals(EmotionalState.CALM, snapshot.state)
        assertTrue(snapshot.intensity <= 0.1f)
    }

    @Test fun emotionDecays() {
        val old = EmotionalSnapshot(EmotionalState.EXCITEMENT, 1f, 1f, 1L)
        val decayed = EmotionEngine.decay(old, old.updatedAt + 15 * 60 * 1000L)
        assertEquals(0.5f, decayed.intensity, 0.01f)
        assertEquals(0.5f, decayed.confidence, 0.01f)
    }

    @Test fun negatedEmotionDoesNotCreatePositiveState() {
        val snapshot = EmotionEngine.inferFromText("saya tidak bahagia hari ini")
        assertEquals(EmotionalState.CALM, snapshot.state)
        assertTrue(snapshot.intensity <= 0.1f)
    }

    @Test fun punctuationAndMultiWordNegationAreRespected() {
        assertEquals(EmotionalState.CALM, EmotionEngine.inferFromText("saya nggak bahagia, kok").state)
        assertEquals(EmotionalState.CALM, EmotionEngine.inferFromText("saya sama sekali tidak senang").state)
    }

    @Test fun mergeKeepsStateValuesBounded() {
        val merged = EmotionEngine.merge(
            EmotionalSnapshot(EmotionalState.JOY, 8f, 9f, 1L),
            EmotionalSnapshot(EmotionalState.EXCITEMENT, 7f, 6f, 1L),
            now = 1L,
        )
        assertTrue(merged.intensity in 0f..1f)
        assertTrue(merged.confidence in 0f..1f)
    }
    @Test
    fun voiceRouterExtractsReminderTimeAndTitle() {
        val command = VoiceCommandRouter.parse("ingatkan saya jam 19:30 belajar matematika")
        assertEquals(VoiceIntent.CREATE_REMINDER, command.intent)
        assertEquals("belajar matematika", command.argument)
        assertEquals(19, command.timeHour)
        assertEquals(30, command.timeMinute)
    }

    @Test
    fun voiceRouterDefaultsMinuteForWholeHourReminder() {
        val command = VoiceCommandRouter.parse("ingatkan gue jam 7 sekolah")
        assertEquals(VoiceIntent.CREATE_REMINDER, command.intent)
        assertEquals("sekolah", command.argument)
        assertEquals(7, command.timeHour)
        assertEquals(0, command.timeMinute)
    }

}
