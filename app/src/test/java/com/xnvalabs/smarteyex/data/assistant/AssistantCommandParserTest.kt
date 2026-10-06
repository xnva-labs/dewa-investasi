package com.xnvalabs.smarteyex.data.assistant

import com.xnvalabs.smarteyex.data.assistant.AssistantCommandParser.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantCommandParserTest {
    @Test fun wakeWordVariantsAreRecognized() {
        assertEquals("buka claude", AssistantCommandParser.stripWakeWord("SmartEyeX buka claude"))
        assertEquals("buka claude", AssistantCommandParser.stripWakeWord("hai smart eyes buka claude"))
        assertEquals("matikan mic", AssistantCommandParser.stripWakeWord("Smart Eye X, matikan mic"))
        assertEquals("ke pasar", AssistantCommandParser.stripWakeWord("smart eye ke pasar"))
        assertEquals("", AssistantCommandParser.stripWakeWord("smarteyex"))
    }

    @Test fun ordinarySpeechIsNotAWakeWord() {
        assertNull(AssistantCommandParser.stripWakeWord("buka claude"))
        assertNull(AssistantCommandParser.stripWakeWord("smartphone saya rusak"))
        assertNull(AssistantCommandParser.stripWakeWord(""))
    }

    @Test fun parsesCommands() {
        assertEquals(Command.StopListening, AssistantCommandParser.parse("matikan mic"))
        assertEquals(Command.OpenApp("claude"), AssistantCommandParser.parse("buka apk claude"))
        assertEquals(Command.OpenApp("whatsapp"), AssistantCommandParser.parse("tolong buka aplikasi whatsapp"))
        assertEquals(Command.OpenApp("google maps"), AssistantCommandParser.parse("buka google maps"))
        assertEquals(Command.ReadNotifications, AssistantCommandParser.parse("bacakan notifikasi"))
        assertEquals(Command.Reply("dek zaa aku otw"), AssistantCommandParser.parse("jawab dek zaa aku otw"))
        assertEquals(Command.Ask("kenapa langit biru?"), AssistantCommandParser.parse("kenapa langit biru?"))
        assertEquals(Command.WakeOnly, AssistantCommandParser.parse(""))
    }

    @Test fun replyShortcutNeedsVerbAndBody() {
        assertEquals("dek zaa halo", AssistantCommandParser.replyBody("Jawab dek zaa halo"))
        assertNull(AssistantCommandParser.replyBody("kita jawab nanti"))
        assertNull(AssistantCommandParser.replyBody("jawab"))
    }

    @Test fun splitsReplyBySenderName() {
        val senders = listOf("Dek Zaa", "Budi", "Zaa Putri")
        val exact = AssistantCommandParser.splitReply("dek zaa aku otw ya", senders)
        assertEquals("Dek Zaa", exact?.sender)
        assertEquals("aku otw ya", exact?.message)
        // recognizer heard one letter wrong
        val fuzzy = AssistantCommandParser.splitReply("dek jaa nanti ya", senders)
        assertEquals("Dek Zaa", fuzzy?.sender)
        assertEquals("nanti ya", fuzzy?.message)
        // the longer name wins when both could match
        assertEquals("Zaa Putri", AssistantCommandParser.splitReply("zaa putri halo", senders)?.sender)
        assertEquals("", AssistantCommandParser.splitReply("budi", senders)?.message)
    }

    @Test fun unknownOrAmbiguousSenderIsNeverGuessed() {
        assertNull(AssistantCommandParser.splitReply("rina halo", listOf("Dek Zaa", "Budi")))
        assertNull(AssistantCommandParser.splitReply("zca halo", listOf("Zaa", "Zia")))
        assertNotNull(AssistantCommandParser.splitReply("dek zaa halo", listOf("Dek Zaa", "Dek Zia")))
    }

    @Test fun appNamesResolveToInstalledApps() {
        val apps = listOf(
            LaunchableApp("Claude", "com.anthropic.claude"),
            LaunchableApp("Claude Code", "x.code"),
            LaunchableApp("WhatsApp", "com.whatsapp"),
            LaunchableApp("Instagram", "com.instagram.android"),
            LaunchableApp("Google Maps", "com.google.android.apps.maps"),
        )
        assertEquals("com.anthropic.claude", AppMatcher.resolve("claude", apps)?.packageName)
        assertEquals("com.whatsapp", AppMatcher.resolve("wa", apps)?.packageName)
        assertEquals("com.google.android.apps.maps", AppMatcher.resolve("maps", apps)?.packageName)
        assertEquals("com.instagram.android", AppMatcher.resolve("instagran", apps)?.packageName)
        assertNull(AppMatcher.resolve("spotify", apps))
        assertNull(AppMatcher.resolve("", apps))
    }

    @Test fun textMatchBasics() {
        assertEquals(3, TextMatch.levenshtein("kitten", "sitting"))
        assertEquals("dek zaa", TextMatch.norm("Dek Zaa!"))
    }
}
