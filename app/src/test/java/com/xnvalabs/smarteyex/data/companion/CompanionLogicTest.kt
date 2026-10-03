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

class VoiceRouterTimeTest {
    @org.junit.Test fun eveningHourBecomes24h() {
        val c = VoiceCommandRouter.parse("ingatkan jam 8 malam minum obat")
        org.junit.Assert.assertEquals(VoiceIntent.CREATE_REMINDER, c.intent)
        org.junit.Assert.assertEquals(20, c.timeHour)
        org.junit.Assert.assertEquals(0, c.timeMinute)
        org.junit.Assert.assertEquals("minum obat", c.argument)
    }

    @org.junit.Test fun partOfDayConversions() {
        org.junit.Assert.assertEquals(0, VoiceCommandRouter.adjustHour(12, "malam"))
        org.junit.Assert.assertEquals(3, VoiceCommandRouter.adjustHour(3, "malam"))
        org.junit.Assert.assertEquals(15, VoiceCommandRouter.adjustHour(3, "sore"))
        org.junit.Assert.assertEquals(13, VoiceCommandRouter.adjustHour(1, "siang"))
        org.junit.Assert.assertEquals(11, VoiceCommandRouter.adjustHour(11, "siang"))
        org.junit.Assert.assertEquals(12, VoiceCommandRouter.adjustHour(12, "siang"))
        org.junit.Assert.assertEquals(5, VoiceCommandRouter.adjustHour(5, "pagi"))
        org.junit.Assert.assertEquals(9, VoiceCommandRouter.adjustHour(9, null))
    }

    @org.junit.Test fun tomorrowSetsDayOffsetAndOneOff() {
        val c = VoiceCommandRouter.parse("ingetin besok jam 6 pagi bayar listrik")
        org.junit.Assert.assertEquals(1, c.dayOffset)
        org.junit.Assert.assertTrue(c.dateSpecified)
        org.junit.Assert.assertEquals(6, c.timeHour)
        org.junit.Assert.assertEquals("bayar listrik", c.argument)
        org.junit.Assert.assertEquals(2, VoiceCommandRouter.parse("ingatkan lusa jam 10 rapat").dayOffset)
        org.junit.Assert.assertEquals(0, VoiceCommandRouter.parse("ingatkan jam 10 rapat").dayOffset)
    }

    @org.junit.Test fun halfHourIdiom() {
        val c = VoiceCommandRouter.parse("ingatkan setengah 8 berangkat")
        org.junit.Assert.assertEquals(7, c.timeHour)
        org.junit.Assert.assertEquals(30, c.timeMinute)
        org.junit.Assert.assertEquals("berangkat", c.argument)
    }

    @org.junit.Test fun questionsStayWithXnai() {
        org.junit.Assert.assertEquals(VoiceIntent.ASK_XNAI, VoiceCommandRouter.parse("apa itu system prompt").intent)
        org.junit.Assert.assertEquals(VoiceIntent.ASK_XNAI, VoiceCommandRouter.parse("jelaskan privasi data").intent)
        org.junit.Assert.assertEquals(VoiceIntent.ASK_XNAI, VoiceCommandRouter.parse("apa itu vision transformer").intent)
        org.junit.Assert.assertEquals(VoiceIntent.ASK_XNAI, VoiceCommandRouter.parse("apa maksud keadaan darurat").intent)
        org.junit.Assert.assertEquals(VoiceIntent.ASK_XNAI, VoiceCommandRouter.parse("minta tolong buatkan puisi").intent)
    }

    @org.junit.Test fun explicitCommandsStillRoute() {
        org.junit.Assert.assertEquals(VoiceIntent.OPEN_PRIVACY, VoiceCommandRouter.parse("buka privasi").intent)
        org.junit.Assert.assertEquals(VoiceIntent.OPEN_VISION, VoiceCommandRouter.parse("lihat kamera").intent)
        org.junit.Assert.assertEquals(VoiceIntent.OPEN_SYSTEM, VoiceCommandRouter.parse("buka system").intent)
        org.junit.Assert.assertEquals(VoiceIntent.READ_NOTIFICATIONS, VoiceCommandRouter.parse("bacain notifikasi gue").intent)
        org.junit.Assert.assertEquals(VoiceIntent.REPLY_NOTIFICATION, VoiceCommandRouter.parse("balas aja oke siap").intent)
        org.junit.Assert.assertEquals("oke siap", VoiceCommandRouter.parse("balas aja oke siap").argument)
    }
}
