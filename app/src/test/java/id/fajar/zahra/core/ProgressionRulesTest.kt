package id.fajar.zahra.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionRulesTest {
    @Test fun waterRewardScalesWithDifficulty() {
        assertEquals(5, ProgressionRules.waterForDifficulty(1))
        assertEquals(8, ProgressionRules.waterForDifficulty(2))
        assertEquals(52, ProgressionRules.waterForDifficulty(10))
    }

    @Test fun yearlyLevelAdvancesAndKeepsWithinLevelProgress() {
        assertEquals(1, ProgressionRules.levelForExperience(0))
        assertEquals(2, ProgressionRules.levelForExperience(100))
        assertEquals(1, ProgressionRules.levelForExperience(99))
        assertEquals(99, ProgressionRules.experienceIntoLevel(99))
        assertEquals(0, ProgressionRules.experienceIntoLevel(100))
    }

    @Test fun plantGrowsAndLeavesUnlockMessages() {
        assertEquals(0, ProgressionRules.plantStage(0))
        assertTrue(ProgressionRules.plantStage(72) > ProgressionRules.plantStage(12))
        assertEquals(1, ProgressionRules.leafCount(12))
        assertEquals(0, ProgressionRules.leafCount(11))
        assertEquals(12, ProgressionRules.leafCount(408))
        assertEquals(13, ProgressionRules.leafCount(480))
        assertEquals(0, ProgressionRules.secretMessageIndexForLeaf(1, 4))
        assertEquals(1, ProgressionRules.secretMessageIndexForLeaf(2, 4))
    }

    @Test fun plantStageProgressDoesNotLoopBackwardAtRoundNumbers() {
        assertTrue(ProgressionRules.plantStageProgress(11) > ProgressionRules.plantStageProgress(2))
        assertEquals(0f, ProgressionRules.plantStageProgress(12), 0.001f)
        assertEquals(1f, ProgressionRules.plantStageProgress(100), 0.001f)
    }
}
