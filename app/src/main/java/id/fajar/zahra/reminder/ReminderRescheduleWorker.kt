package id.fajar.zahra.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.fajar.zahra.data.ZahraDatabase
import id.fajar.zahra.settings.SettingsStore
import kotlinx.coroutines.flow.first

class ReminderRescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        ReminderScheduler.cancelAll(applicationContext)
        if (!settings.notifications.first()) return Result.success()

        val now = System.currentTimeMillis()
        ZahraDatabase.get(applicationContext).missionDao().getAllNonArchived()
            .asSequence()
            .filter { it.status == "ACTIVE" && it.scheduledAt != null && it.scheduledAt > now }
            .forEach { mission ->
                ReminderScheduler.scheduleAt(
                    applicationContext,
                    mission.id,
                    mission.title,
                    "Pengingat misi: ${mission.title}",
                    mission.scheduledAt!!
                )
            }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "zahra-reminder-resync"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<ReminderRescheduleWorker>().build()
            )
        }
    }
}
