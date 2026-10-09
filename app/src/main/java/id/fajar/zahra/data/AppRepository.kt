package id.fajar.zahra.data

import android.content.Context
import androidx.room.withTransaction
import id.fajar.zahra.camera.ProofResult
import id.fajar.zahra.core.RepeatRules
import id.fajar.zahra.core.MissionDifficultyEstimator
import id.fajar.zahra.core.ProgressionRules
import id.fajar.zahra.core.RewardGuard
import id.fajar.zahra.reminder.ReminderScheduler
import id.fajar.zahra.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar

class AppRepository(
    private val db: ZahraDatabase,
    private val context: Context,
    private val settings: SettingsStore
) {
    val profile: Flow<ProfileEntity?> = db.profileDao().observe()
    val missions: Flow<List<MissionEntity>> = db.missionDao().observeAll()
    val rewards: Flow<List<RewardEntity>> = db.rewardDao().observeAll()
    val points: Flow<Int> = db.pointDao().observeTotal()
    val pointHistory: Flow<List<PointLedgerEntity>> = db.pointDao().observeRecent()
    val events: Flow<List<AppEventEntity>> = db.eventDao().observeRecent()
    val lists: Flow<List<ListEntity>> = db.listDao().observeLists()
    val yearlyProgress: Flow<List<YearlyProgressEntity>> = db.yearProgressDao().observeAll()

    fun listItems(listId: Long): Flow<List<ListItemEntity>> = db.listItemDao().observeItems(listId)

    /** Seeds a small, editable starter set only once per title; user-authored activities stay untouched. */
    suspend fun ensureDefaultMissions() {
        val now = System.currentTimeMillis()
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val starters = listOf(
            StarterMission("Sholat Subuh", "Sholat wajib Subuh.", 2, RepeatRules.DAILY),
            StarterMission("Sholat Dzuhur", "Sholat wajib Dzuhur.", 2, RepeatRules.DAILY),
            StarterMission("Sholat Ashar", "Sholat wajib Ashar.", 2, RepeatRules.DAILY),
            StarterMission("Sholat Maghrib", "Sholat wajib Maghrib.", 2, RepeatRules.DAILY),
            StarterMission("Sholat Isya", "Sholat wajib Isya.", 2, RepeatRules.DAILY),
            StarterMission("Sholat Dhuha", "Sunnah pilihan, sesuaikan dengan kemampuan.", 3, RepeatRules.DAILY),
            StarterMission("Tilawah Al-Qur'an", "Mulai dari target yang terasa ringan.", 2, RepeatRules.DAILY),
            StarterMission("Dzikir pagi", "Dzikir pagi sebagai pengingat harian.", 2, RepeatRules.DAILY),
            StarterMission("Dzikir petang", "Dzikir petang sebagai pengingat harian.", 2, RepeatRules.DAILY),
            StarterMission("Puasa sunnah Senin", "Jadwal puasa sunnah Senin; sesuaikan dengan kondisi diri.", 4, RepeatRules.WEEKLY, Calendar.MONDAY),
            StarterMission("Puasa sunnah Kamis", "Jadwal puasa sunnah Kamis; sesuaikan dengan kondisi diri.", 4, RepeatRules.WEEKLY, Calendar.THURSDAY),
            StarterMission("Puasa Daud", "Pola selang-seling; atur ulang tanggal awal bila diperlukan.", 7, RepeatRules.DAWUD)
        )
        val created = db.withTransaction {
            if (db.yearProgressDao().findByYear(year) == null) {
                db.yearProgressDao().save(YearlyProgressEntity(year = year, createdAt = now, updatedAt = now))
            }
            val existingTitles = db.missionDao().getAllForSeeding()
                .map { it.title.trim().lowercase() }
                .toMutableSet()
            val newlyCreated = mutableListOf<Triple<Long, String, Long>>()
            for (starter in starters) {
                if (!existingTitles.add(starter.title.trim().lowercase())) continue
                val scheduledAt = when (starter.weekday) {
                    null -> now
                    else -> nextWeekdayAt(starter.weekday, now)
                }
                val inferredDifficulty = maxOf(
                    starter.difficulty,
                    MissionDifficultyEstimator.estimate(starter.title, starter.description, "Ibadah")
                )
                val id = db.missionDao().insert(
                    MissionEntity(
                        title = starter.title,
                        description = starter.description,
                        category = "Ibadah",
                        difficulty = inferredDifficulty,
                        points = ProgressionRules.waterForDifficulty(inferredDifficulty),
                        scheduleType = "RECURRING",
                        scheduledAt = scheduledAt,
                        repeatRule = starter.repeatRule,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                newlyCreated += Triple(id, starter.title, scheduledAt)
            }
            newlyCreated
        }
        if (settings.notifications.first()) {
            created.filter { it.third > now }.forEach { (id, title, scheduledAt) ->
                ReminderScheduler.scheduleAt(context, id, title, "Pengingat lembut: $title", scheduledAt)
            }
        }
    }

    suspend fun feedCat(): Boolean {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val fed = db.withTransaction {
            db.yearProgressDao().consumeCatFood(year) > 0
        }
        if (fed) db.eventDao().insert(AppEventEntity(type = "CAT_FED", title = "Mimi diberi makan", detail = "Satu pakan dari reward misi dipakai untuk Mimi."))
        return fed
    }

    private data class StarterMission(
        val title: String,
        val description: String,
        val difficulty: Int,
        val repeatRule: String,
        val weekday: Int? = null
    )

    private fun nextWeekdayAt(weekday: Int, now: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val days = (weekday - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (days > 0) calendar.add(Calendar.DAY_OF_YEAR, days)
        if (calendar.timeInMillis <= now) calendar.add(Calendar.WEEK_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    suspend fun saveProfile(name: String, age: Int) {
        val clean = name.trim().take(80)
        require(clean.isNotBlank()) { "Nama panggilan tidak boleh kosong." }
        val old = db.profileDao().get()
        val now = System.currentTimeMillis()
        db.profileDao().save(
            ProfileEntity(
                id = 1,
                name = clean,
                age = age.coerceIn(0, 120),
                createdAt = old?.createdAt ?: now,
                updatedAt = now
            )
        )
        db.eventDao().insert(AppEventEntity(type = "PROFILE", title = "Profil disimpan", detail = if (age in 1..120) "$clean · ${age} tahun" else "Sapaan: $clean · usia tidak diisi", createdAt = now))
    }

    suspend fun seedRewards() {
        if (db.rewardDao().count() == 0) {
            db.rewardDao().insertAll(
                listOf(
                    RewardEntity(1, "Tema lembut", "Milestone untuk ruang Zahra yang nyaman", 100),
                    RewardEntity(2, "Momen bersama Mimi", "Pengingat untuk beristirahat sejenak", 250),
                    RewardEntity(3, "Kartu apresiasi diri", "Catatan untuk menghargai usahamu", 500),
                    RewardEntity(4, "Lencana konsistensi", "Tanda progres kebiasaan yang terjaga", 1000)
                )
            )
        }
        db.rewardDao().unlockEligible(db.pointDao().getTotal())
    }

    suspend fun addMission(
        title: String,
        description: String,
        category: String,
        points: Int,
        difficulty: Int,
        proofType: String,
        proofTarget: String,
        scheduledAt: Long?,
        repeatRule: String?
    ): Long {
        val clean = title.trim().take(120)
        require(clean.isNotBlank()) { "Nama misi wajib diisi." }
        val normalizedProof = proofType.uppercase().let { if (it in setOf("NONE", "PHOTO", "POSE", "OBJECT")) it else "NONE" }
        require(normalizedProof != "OBJECT" || proofTarget.trim().isNotBlank()) { "Target objek wajib diisi untuk proof objek." }
        val rule = repeatRule?.uppercase()?.takeIf { it in setOf(RepeatRules.DAILY, RepeatRules.WEEKLY, RepeatRules.MONTHLY, RepeatRules.DAWUD) }
        val requestedSchedule = scheduledAt?.coerceAtLeast(System.currentTimeMillis())
        val normalizedSchedule = if (rule != null) requestedSchedule ?: System.currentTimeMillis() else requestedSchedule
        val id = db.missionDao().insert(
            MissionEntity(
                title = clean,
                description = description.trim().take(500),
                category = category.trim().take(50).ifBlank { "General" },
                points = points.coerceIn(0, 1000),
                difficulty = difficulty.coerceIn(1, 10),
                proofType = normalizedProof,
                proofTarget = proofTarget.trim().take(100),
                scheduleType = when {
                    rule != null -> "RECURRING"
                    normalizedSchedule != null -> "ONE_TIME"
                    else -> "NONE"
                },
                scheduledAt = normalizedSchedule,
                repeatRule = rule
            )
        )
        if (normalizedSchedule != null && settings.notifications.first()) {
            ReminderScheduler.scheduleAt(context, id, clean, "Saatnya mengingat kembali misi: $clean", normalizedSchedule)
        }
        db.eventDao().insert(AppEventEntity(type = "MISSION_CREATED", title = "Misi dibuat", detail = "#$id $clean"))
        return id
    }

    suspend fun updateMission(
        current: MissionEntity,
        title: String,
        description: String,
        category: String,
        points: Int,
        difficulty: Int,
        proofType: String,
        proofTarget: String,
        scheduledAt: Long?,
        repeatRule: String?
    ): Boolean {
        val cleanTitle = title.trim().take(120)
        require(cleanTitle.isNotBlank()) { "Nama misi wajib diisi." }
        val normalizedProof = proofType.uppercase().let { if (it in setOf("NONE", "PHOTO", "POSE", "OBJECT")) it else "NONE" }
        require(normalizedProof != "OBJECT" || proofTarget.trim().isNotBlank()) { "Target objek wajib diisi untuk proof objek." }
        val rule = repeatRule?.uppercase()?.takeIf { it in setOf(RepeatRules.DAILY, RepeatRules.WEEKLY, RepeatRules.MONTHLY, RepeatRules.DAWUD) }
        val cleanSchedule = scheduledAt?.takeIf { it >= System.currentTimeMillis() }
        val normalizedSchedule = if (rule != null) cleanSchedule ?: System.currentTimeMillis() else cleanSchedule
        val updated = current.copy(
            title = cleanTitle,
            description = description.trim().take(500),
            category = category.trim().take(50).ifBlank { "General" },
            points = points.coerceIn(0, 1000),
            difficulty = difficulty.coerceIn(1, 10),
            proofType = normalizedProof,
            proofTarget = proofTarget.trim().take(100),
            scheduleType = when { rule != null -> "RECURRING"; normalizedSchedule != null -> "ONE_TIME"; else -> "NONE" },
            scheduledAt = normalizedSchedule,
            repeatRule = rule,
            updatedAt = System.currentTimeMillis()
        )
        val changed = db.missionDao().update(updated)
        if (changed > 0) {
            ReminderScheduler.cancel(context, current.id)
            if (settings.notifications.first() && updated.status == "ACTIVE" && updated.scheduledAt != null) {
                ReminderScheduler.scheduleAt(context, updated.id, updated.title, "Pengingat misi: ${updated.title}", updated.scheduledAt)
            }
            db.eventDao().insert(AppEventEntity(type="MISSION_UPDATED", title="Misi diperbarui", detail="#${updated.id} ${updated.title}"))
        }
        return changed > 0
    }

    suspend fun completeMission(m: MissionEntity): Boolean {
        val now = System.currentTimeMillis()
        var nextReminder: Long? = null
        var completedTitle = ""
        val changed = db.withTransaction {
            val current = db.missionDao().findById(m.id) ?: return@withTransaction false
            if (current.status != "ACTIVE") return@withTransaction false

            val repeating = current.repeatRule in setOf(RepeatRules.DAILY, RepeatRules.WEEKLY, RepeatRules.MONTHLY, RepeatRules.DAWUD)
            if (repeating && !RewardGuard.canCompleteRecurring(current.completedAt, current.scheduledAt, now)) {
                db.eventDao().insert(
                    AppEventEntity(
                        type = "MISSION_BLOCKED_DUPLICATE",
                        title = "Siklus belum jatuh tempo",
                        detail = "${current.title}: completion berikutnya menunggu jadwal berikutnya.",
                        createdAt = now
                    )
                )
                return@withTransaction false
            }

            if (current.proofType != "NONE") {
                val latest = db.proofDao().latestForMission(current.id)
                val cycleStart = current.completedAt ?: current.createdAt
                val validProof = latest != null &&
                    latest.createdAt >= cycleStart &&
                    latest.proofType == current.proofType &&
                    latest.status in setOf("CONFIRMED", "MANUAL_CONFIRMED")
                if (!validProof) {
                    db.eventDao().insert(
                        AppEventEntity(
                            type = "MISSION_BLOCKED_PROOF",
                            title = "Proof diperlukan",
                            detail = "${current.title}: ambil dan konfirmasi bukti terlebih dahulu.",
                            createdAt = now
                        )
                    )
                    return@withTransaction false
                }
            }

            var recurring = if (repeating) RepeatRules.next(current.scheduledAt ?: now, current.repeatRule) else null
            while (recurring != null && recurring <= now) recurring = RepeatRules.next(recurring, current.repeatRule)

            val updated = if (recurring != null) {
                db.missionDao().completeRecurring(current.id, recurring, now)
            } else {
                db.missionDao().complete(current.id, now)
            }
            if (updated <= 0) return@withTransaction false

            val cycleKey = if (repeating) {
                RewardGuard.cycleMissionKey(current.id, current.scheduledAt ?: current.createdAt)
            } else {
                RewardGuard.oneTimeMissionKey(current.id)
            }
            val pointId = db.pointDao().addIfNew(
                PointLedgerEntity(
                    sourceType = "MISSION",
                    sourceId = current.id,
                    sourceKey = cycleKey,
                    label = current.title,
                    points = current.points,
                    createdAt = now
                )
            )
            check(pointId > 0L) { "Duplicate mission reward blocked" }

            val year = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
            val priorProgress = db.yearProgressDao().findByYear(year) ?: YearlyProgressEntity(year = year, createdAt = now)
            val waterReward = ProgressionRules.waterForDifficulty(current.difficulty)
            val newWater = (priorProgress.water + waterReward).coerceAtLeast(0)
            val newExperience = (priorProgress.experience + ProgressionRules.experienceFor(current.difficulty, waterReward)).coerceAtLeast(0)
            val newLeafCount = ProgressionRules.leafCount(newWater)
            val messageIndex = if (newLeafCount > priorProgress.leafDrops) {
                ProgressionRules.secretMessageIndexForLeaf(newLeafCount, LEAF_MESSAGE_COUNT)
            } else priorProgress.lastLeafMessageIndex
            db.yearProgressDao().save(
                priorProgress.copy(
                    level = ProgressionRules.levelForExperience(newExperience),
                    experience = newExperience,
                    water = newWater,
                    catFood = (priorProgress.catFood + ProgressionRules.catFoodFor(current.difficulty)).coerceAtMost(999_999),
                    plantStage = ProgressionRules.plantStage(newWater),
                    leafDrops = newLeafCount,
                    lastLeafMessageIndex = messageIndex,
                    updatedAt = now
                )
            )

            completedTitle = current.title
            nextReminder = recurring
            db.rewardDao().unlockEligible(db.pointDao().getTotal(), now)
            db.eventDao().insert(
                AppEventEntity(
                    type = "MISSION_COMPLETED",
                    title = "Misi selesai",
                    detail = "${current.title} · +$waterReward tetes air · +${ProgressionRules.experienceFor(current.difficulty, waterReward)} EXP",
                    createdAt = now
                )
            )
            true
        }

        if (!changed) return false

        if (nextReminder != null && settings.notifications.first()) {
            ReminderScheduler.scheduleAt(context, m.id, completedTitle, "Pengingat misi berulang: $completedTitle", nextReminder!!)
        }
        return true
    }

    suspend fun recordProof(result: ProofResult): Boolean {
        val mission = db.missionDao().findById(result.missionId) ?: return false
        if (mission.status != "ACTIVE") return false
        if (mission.proofType == "NONE" || mission.proofType != result.proofType) return false
        if (result.status !in setOf("CONFIRMED", "MANUAL_CONFIRMED")) return false

        db.proofDao().insert(
            ProofEntity(
                missionId = result.missionId,
                proofType = result.proofType,
                status = result.status,
                message = result.message.take(500),
                confidence = result.confidence.coerceIn(0f, 1f),
                photoRetained = false,
                createdAt = System.currentTimeMillis()
            )
        )
        db.eventDao().insert(
            AppEventEntity(
                type = "PROOF",
                title = "Bukti misi dicatat",
                detail = "Misi #${result.missionId}: ${result.message}",
            )
        )
        return true
    }

    suspend fun addList(title: String, description: String): Long {
        val clean = title.trim().take(120)
        require(clean.isNotBlank()) { "Nama daftar wajib diisi." }
        val id = db.listDao().insert(ListEntity(title = clean, description = description.trim().take(500)))
        db.eventDao().insert(AppEventEntity(type = "LIST_CREATED", title = "Daftar dibuat", detail = "#$id $clean"))
        return id
    }

    suspend fun addListItem(listId: Long, title: String): Long {
        require(db.listDao().findById(listId) != null) { "Daftar tidak ditemukan." }
        val clean = title.trim().take(160)
        require(clean.isNotBlank()) { "Item daftar wajib diisi." }
        val id = db.listItemDao().insert(ListItemEntity(listId = listId, title = clean))
        db.eventDao().insert(AppEventEntity(type = "LIST_ITEM_CREATED", title = "Item daftar dibuat", detail = "$clean"))
        return id
    }

    suspend fun setListItemChecked(itemId: Long, checked: Boolean) {
        val changed = db.listItemDao().setChecked(itemId, checked, if (checked) System.currentTimeMillis() else null)
        if (changed > 0) {
            db.eventDao().insert(AppEventEntity(type = "LIST_ITEM_CHECKED", title = if (checked) "Item selesai" else "Item dibuka kembali", detail = "#$itemId"))
        }
    }

    suspend fun archiveList(id: Long) {
        if (db.listDao().archive(id) > 0) {
            db.listItemDao().deleteForList(id)
            db.eventDao().insert(AppEventEntity(type = "LIST_ARCHIVED", title = "Daftar diarsipkan", detail = "#$id"))
        }
    }

    suspend fun claimReward(id: Long): Boolean {
        val claimed = db.withTransaction {
            val total = db.pointDao().getTotal()
            db.rewardDao().claimIfEligible(id, total)
        }
        if (claimed > 0) {
            db.eventDao().insert(AppEventEntity(type = "REWARD_CLAIMED", title = "Reward diklaim", detail = "#$id"))
            return true
        }
        return false
    }

    suspend fun pauseMission(id: Long): Int {
        val result = db.missionDao().pause(id)
        if (result > 0) {
            ReminderScheduler.cancel(context, id)
            db.eventDao().insert(AppEventEntity(type="MISSION_PAUSED", title="Misi dijeda", detail="#$id"))
        }
        return result
    }

    suspend fun resumeMission(id: Long): Int {
        val result = db.missionDao().resume(id)
        if (result <= 0) return result
        val mission = db.missionDao().findById(id)
        db.eventDao().insert(AppEventEntity(type = "MISSION_RESUMED", title = "Misi dilanjutkan", detail = mission?.title ?: "#$id"))
        if (settings.notifications.first() && mission != null && mission.scheduledAt != null) {
            val now = System.currentTimeMillis()
            var next: Long? = mission.scheduledAt
            val rule = mission.repeatRule
            if (next != null && next <= now && rule != null) {
                next = RepeatRules.next(next, rule)
                while (next != null && next <= now) next = RepeatRules.next(next, rule)
            }
            if (next != null && next > now) {
                ReminderScheduler.scheduleAt(context, id, mission.title, "Pengingat misi: ${mission.title}", next)
            }
        }
        return result
    }

    suspend fun rescheduleFutureReminders() {
        ReminderScheduler.cancelAll(context)
        if (!settings.notifications.first()) return
        val now = System.currentTimeMillis()
        db.missionDao().getAllNonArchived()
            .filter { it.status == "ACTIVE" && it.scheduledAt != null && it.scheduledAt > now }
            .forEach { m ->
                ReminderScheduler.scheduleAt(context, m.id, m.title, "Pengingat misi: ${m.title}", m.scheduledAt!!)
            }
    }

    suspend fun setNotifications(enabled: Boolean) {
        settings.setNotifications(enabled)
        if (!enabled) {
            ReminderScheduler.cancelAll(context)
        } else {
            rescheduleFutureReminders()
        }
    }

    suspend fun resetApplication() {
        ReminderScheduler.cancelAll(context)
        db.withTransaction {
            db.profileDao().deleteAll()
            db.missionDao().deleteAll()
            db.rewardDao().deleteAll()
            db.pointDao().deleteAll()
            db.proofDao().deleteAll()
            db.listItemDao().deleteAll()
            db.listDao().deleteAll()
            db.eventDao().deleteAll()
            db.yearProgressDao().deleteAll()
        }
        settings.setNotifications(false)
    }

    suspend fun archiveMission(m: MissionEntity) {
        if (db.missionDao().archive(m.id) > 0) {
            ReminderScheduler.cancel(context, m.id)
            db.eventDao().insert(AppEventEntity(type = "MISSION_ARCHIVED", title = "Misi diarsipkan", detail = m.title))
        }
    }

    companion object {
        private const val LEAF_MESSAGE_COUNT = 8
    }
}
