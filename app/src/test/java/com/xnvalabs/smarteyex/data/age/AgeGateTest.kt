package com.xnvalabs.smarteyex.data.age

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeGateTest {
    private val now = 2026

    @Test fun clearAdultTeenAndChild() {
        assertEquals(AgeBand.ADULT, AgeGate.bandFor(1990, now))
        assertEquals(AgeBand.TEEN, AgeGate.bandFor(2010, now))
        assertEquals(AgeBand.CHILD, AgeGate.bandFor(2018, now))
    }

    @Test fun boundariesUseTheYoungestPossibleAge() {
        // Lahir 2007: pasti >= 18 (paling muda 18). Lahir 2008: bisa 17 -> tetap TEEN.
        assertEquals(AgeBand.ADULT, AgeGate.bandFor(2007, now))
        assertEquals(AgeBand.TEEN, AgeGate.bandFor(2008, now))
        // Lahir 2012: pasti >= 13. Lahir 2013: bisa 12 -> CHILD.
        assertEquals(AgeBand.TEEN, AgeGate.bandFor(2012, now))
        assertEquals(AgeBand.CHILD, AgeGate.bandFor(2013, now))
    }

    @Test fun implausibleYearsAreUnknown() {
        assertEquals(AgeBand.UNKNOWN, AgeGate.bandFor(now + 1, now))
        assertEquals(AgeBand.UNKNOWN, AgeGate.bandFor(1800, now))
        assertEquals(AgeBand.UNKNOWN, AgeGate.bandFor(now - 101, now))
        assertEquals(AgeBand.CHILD, AgeGate.bandFor(now, now))
    }

    @Test fun usersAgeUpAutomaticallyAsYearsPass() {
        assertEquals(AgeBand.CHILD, AgeGate.bandFor(2013, 2026))
        assertEquals(AgeBand.TEEN, AgeGate.bandFor(2013, 2027))
        assertEquals(AgeBand.ADULT, AgeGate.bandFor(2008, 2027))
    }

    @Test fun headerNeverLeaksYearAndUnknownIsProtective() {
        assertEquals("ADULT", AgeGate.headerValue(AgeBand.ADULT))
        assertEquals("TEEN", AgeGate.headerValue(AgeBand.TEEN))
        assertEquals("CHILD", AgeGate.headerValue(AgeBand.CHILD))
        assertEquals("TEEN", AgeGate.headerValue(AgeBand.UNKNOWN))
    }

    @Test fun policyMatrix() {
        for (f in AgeFeature.values()) assertTrue(AgePolicy.allows(AgeBand.ADULT, false, f))
        for (f in AgeFeature.values()) {
            assertFalse(AgePolicy.allows(AgeBand.CHILD, true, f))
            assertFalse(AgePolicy.allows(AgeBand.UNKNOWN, true, f))
        }
        assertFalse(AgePolicy.allows(AgeBand.TEEN, false, AgeFeature.CLOUD_CHAT))
        assertTrue(AgePolicy.allows(AgeBand.TEEN, true, AgeFeature.CLOUD_CHAT))
        assertTrue(AgePolicy.allows(AgeBand.TEEN, true, AgeFeature.CLOUD_TRANSLATE))
        for (f in listOf(AgeFeature.CLOUD_VISION, AgeFeature.FACE_RECOGNITION, AgeFeature.NOTIFICATION_CONTENT, AgeFeature.VOICE_PERSONALIZATION, AgeFeature.SEND_MEMORY_CONTEXT)) {
            assertFalse("teen must not get $f even with consent", AgePolicy.allows(AgeBand.TEEN, true, f))
        }
    }

    @Test fun denialMessagesPointToTheRightFix() {
        assertTrue(AgePolicy.denialMessage(AgeBand.UNKNOWN, AgeFeature.CLOUD_CHAT).contains("tahun lahir"))
        assertTrue(AgePolicy.denialMessage(AgeBand.TEEN, AgeFeature.CLOUD_CHAT).contains("orang tua"))
        assertTrue(AgePolicy.denialMessage(AgeBand.TEEN, AgeFeature.FACE_RECOGNITION).contains("18"))
        assertTrue(AgePolicy.denialMessage(AgeBand.CHILD, AgeFeature.CLOUD_CHAT).contains("13"))
    }
}
