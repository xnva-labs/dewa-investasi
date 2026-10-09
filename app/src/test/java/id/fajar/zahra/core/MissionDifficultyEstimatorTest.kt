package id.fajar.zahra.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionDifficultyEstimatorTest {
    @Test fun corePrayerIsClassifiedAsEasy() {
        assertEquals(MissionDifficultyEstimator.Band.EASY, MissionDifficultyEstimator.band(
            MissionDifficultyEstimator.estimate("Sholat Subuh", category = "Ibadah")
        ))
    }

    @Test fun effortfulActivityGetsHigherDifficulty() {
        val easy = MissionDifficultyEstimator.estimate("Minum air")
        val hard = MissionDifficultyEstimator.estimate("Puasa Daud dan murajaah hafalan")
        assertTrue(hard > easy)
        assertEquals(MissionDifficultyEstimator.Band.HARD, MissionDifficultyEstimator.band(hard))
    }

    @Test fun resultAlwaysStaysWithinSupportedRange() {
        assertTrue(MissionDifficultyEstimator.estimate("", "", "") in 1..10)
        assertTrue(MissionDifficultyEstimator.estimate("Puasa Daud ".repeat(100)) in 1..10)
    }
}
