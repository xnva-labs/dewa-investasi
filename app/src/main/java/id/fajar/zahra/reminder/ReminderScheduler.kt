package id.fajar.zahra.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val PREFIX = "mission-reminder-"

    fun scheduleAt(context: Context, id: Long, title: String, body: String, epochMillis: Long) {
        val delay = (epochMillis - System.currentTimeMillis()).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
        val input = Data.Builder()
            .putLong("missionId", id)
            .putString("title", title)
            .putString("body", body)
            .putLong("scheduledAt", epochMillis)
            .build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(input)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("zahra-reminder")
            .addTag("mission:$id")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PREFIX + id,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(PREFIX + id)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("zahra-reminder")
    }
}
