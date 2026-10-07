package com.xnvalabs.smarteyex.data.companion

import org.junit.Assert.assertTrue
import org.junit.Test

class SoulPromptTest {
    @Test fun fitsTheCompanionContextBudgetWithRoomForTheUserModel() {
        assertTrue("soul is ${SoulPrompt.TEXT.length} chars", SoulPrompt.TEXT.length < 3_000)
    }

    @Test fun keepsTheNonNegotiableRules() {
        val text = SoulPrompt.TEXT.lowercase()
        assertTrue(text.contains("jujur bahwa kamu ai"))
        assertTrue(text.contains("ketergantungan"))
        assertTrue(text.contains("layanan darurat"))
        assertTrue(text.contains("tanpa markdown"))
    }
}
