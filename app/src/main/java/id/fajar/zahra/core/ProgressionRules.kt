package id.fajar.zahra.core

/** Reward and level rules are deterministic and deliberately unrelated to spiritual merit. */
object ProgressionRules {
    private val leafMilestones = listOf(12, 28, 48, 72, 100, 132, 168, 208, 252, 300, 352, 408)

    fun waterForDifficulty(difficulty: Int): Int = when (difficulty.coerceIn(1, 10)) {
        1 -> 5
        2 -> 8
        3 -> 12
        4 -> 16
        5 -> 22
        else -> 22 + (difficulty.coerceIn(1, 10) - 5) * 6
    }

    fun experienceFor(difficulty: Int, water: Int): Int =
        (water.coerceAtLeast(0) + difficulty.coerceIn(1, 10) * 8).coerceAtLeast(1)

    fun catFoodFor(difficulty: Int): Int = when (MissionDifficultyEstimator.band(difficulty)) {
        MissionDifficultyEstimator.Band.EASY -> 1
        MissionDifficultyEstimator.Band.MEDIUM -> 2
        MissionDifficultyEstimator.Band.HARD -> 3
    }

    fun levelForExperience(experience: Int): Int {
        var remaining = experience.coerceAtLeast(0)
        var level = 1
        var threshold = experienceToNextLevel(level)
        while (remaining >= threshold && level < 999) {
            remaining -= threshold
            level += 1
            threshold = experienceToNextLevel(level)
        }
        return level
    }

    fun experienceIntoLevel(experience: Int): Int {
        var remaining = experience.coerceAtLeast(0)
        var level = 1
        var threshold = experienceToNextLevel(level)
        while (remaining >= threshold && level < 999) {
            remaining -= threshold
            level += 1
            threshold = experienceToNextLevel(level)
        }
        return remaining
    }

    fun experienceToNextLevel(level: Int): Int = (100 + (level.coerceAtLeast(1) - 1) * 35).coerceAtMost(35_000)

    fun plantStage(water: Int): Int = when (water.coerceAtLeast(0)) {
        in 0..0 -> 0
        in 1..11 -> 1
        in 12..27 -> 2
        in 28..47 -> 3
        in 48..71 -> 4
        in 72..99 -> 5
        else -> 6
    }

    fun plantStageProgress(water: Int): Float {
        val value = water.coerceAtLeast(0)
        val stage = plantStage(value)
        if (stage >= 6) return 1f
        val lower = when (stage) {
            0 -> 0
            1 -> 1
            2 -> 12
            3 -> 28
            4 -> 48
            else -> 72
        }
        val upper = when (stage) {
            0 -> 1
            1 -> 12
            2 -> 28
            3 -> 48
            4 -> 72
            else -> 100
        }
        return ((value - lower).toFloat() / (upper - lower)).coerceIn(0f, 1f)
    }

    fun leafCount(water: Int): Int {
        val value = water.coerceAtLeast(0)
        val earned = leafMilestones.count { value >= it }
        if (earned < leafMilestones.size) return earned
        // Keep the leaf/message loop alive after the plant reaches its mature visual stage.
        val waterBeyondFinalMilestone = value - leafMilestones.last()
        return leafMilestones.size + waterBeyondFinalMilestone / 72
    }

    fun secretMessageIndexForLeaf(leafCount: Int, messageCount: Int): Int =
        if (messageCount <= 0 || leafCount <= 0) -1 else (leafCount - 1) % messageCount
}
