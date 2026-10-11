package com.xnvalabs.smarteyex.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandRouterTest {
    @Test fun opensNamedPhoneAppsAndKeepsMultiWordNames() {
        val whatsapp = VoiceCommandRouter.parse("buka aplikasi WhatsApp")
        assertEquals(VoiceIntent.OPEN_APP, whatsapp.intent)
        assertEquals("whatsapp", whatsapp.argument)

        val maps = VoiceCommandRouter.parse("jalankan Google Maps")
        assertEquals(VoiceIntent.OPEN_APP, maps.intent)
        assertEquals("google maps", maps.argument)
    }

    @Test fun specificSmartEyeXRoutesWinOverGenericAppOpening() {
        assertEquals(VoiceIntent.OPEN_VISION, VoiceCommandRouter.parse("lihat kamera vision").intent)
        assertEquals(VoiceIntent.OPEN_PRIVACY, VoiceCommandRouter.parse("buka privacy").intent)
        assertEquals(VoiceIntent.OPEN_MEMORY, VoiceCommandRouter.parse("buka memori").intent)
    }

    @Test fun emergencyAndReplyCommandsStillRequireSafeHandling() {
        val emergency = VoiceCommandRouter.parse("panggil bantuan darurat")
        assertEquals(VoiceIntent.START_EMERGENCY, emergency.intent)
        assertTrue(emergency.requiresConfirmation)

        val reply = VoiceCommandRouter.parse("balas pesan dek zaa aku otw")
        assertEquals(VoiceIntent.REPLY_NOTIFICATION, reply.intent)
        assertEquals("dek zaa aku otw", reply.argument)
        assertTrue(reply.requiresConfirmation)
        assertEquals(false, reply.replyToLatest)

        val latestOnly = VoiceCommandRouter.parse("balas aja aku otw")
        assertEquals("aku otw", latestOnly.argument)
        assertTrue(latestOnly.replyToLatest)

        val jawab = VoiceCommandRouter.parse("jawab pesan ibu aku sudah sampai")
        assertEquals(VoiceIntent.REPLY_NOTIFICATION, jawab.intent)
        assertEquals("ibu aku sudah sampai", jawab.argument)
        assertTrue(jawab.requiresConfirmation)
    }

    @Test fun extractsNavigationDestinationsFromNaturalCommands() {
        val fromDirection = VoiceCommandRouter.parse("arah ke Stasiun Cirebon")
        assertEquals(VoiceIntent.OPEN_NAVIGATION, fromDirection.intent)
        assertEquals("stasiun cirebon", fromDirection.argument)

        val fromNavigation = VoiceCommandRouter.parse("navigasi ke Alun-alun kota")
        assertEquals(VoiceIntent.OPEN_NAVIGATION, fromNavigation.intent)
        assertEquals("alun-alun kota", fromNavigation.argument)
    }

    @Test fun readsNotificationsWithNaturalIndonesianVariants() {
        assertEquals(VoiceIntent.READ_NOTIFICATIONS, VoiceCommandRouter.parse("bacain notifikasi").intent)
        assertEquals(VoiceIntent.READ_NOTIFICATIONS, VoiceCommandRouter.parse("bacakan pesan").intent)
        assertEquals(VoiceIntent.READ_NOTIFICATIONS, VoiceCommandRouter.parse("baca notifikasi").intent)
    }
}
