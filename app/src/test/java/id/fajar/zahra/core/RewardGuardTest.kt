package id.fajar.zahra.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardGuardTest {
    @Test fun oneTimeKeyIsStable() {
        assertEquals("MISSION:9:ONCE", RewardGuard.oneTimeMissionKey(9))
    }

    @Test fun recurringCycleKeyChangesOnlyWithCycleAnchor() {
        assertEquals("MISSION:9:CYCLE:1000", RewardGuard.cycleMissionKey(9, 1000))
        assertEquals(RewardGuard.cycleMissionKey(9, 1000), RewardGuard.cycleMissionKey(9, 1000))
        assertTrue(RewardGuard.cycleMissionKey(9, 1000) != RewardGuard.cycleMissionKey(9, 2000))
    }

    @Test fun recurringCompletionBlockedBeforeNextSchedule() {
        assertFalse(RewardGuard.canCompleteRecurring(1000, 2000, 1999))
        assertTrue(RewardGuard.canCompleteRecurring(1000, 2000, 2000))
        assertTrue(RewardGuard.canCompleteRecurring(null, 2000, 1000))
    }
}
