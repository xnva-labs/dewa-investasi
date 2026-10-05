package id.fajar.zahra.core

/** Pure, deterministic identifiers used by the point ledger to make rewards idempotent. */
object RewardGuard {
    fun oneTimeMissionKey(missionId: Long): String = "MISSION:${missionId}:ONCE"

    fun cycleMissionKey(missionId: Long, cycleAnchor: Long): String =
        "MISSION:${missionId}:CYCLE:${cycleAnchor}"

    fun canCompleteRecurring(completedAt: Long?, scheduledAt: Long?, now: Long): Boolean =
        completedAt == null || scheduledAt == null || scheduledAt <= now
}
