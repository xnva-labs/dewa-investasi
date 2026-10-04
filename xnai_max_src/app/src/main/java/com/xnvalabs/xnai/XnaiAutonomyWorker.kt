package com.xnvalabs.xnai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class XnaiAutonomyWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = try {
        val settings = XnaiSettings(applicationContext)
        if (!settings.autonomyEnabled) Result.success()
        else {
            XnaiAutonomousSystem(applicationContext).runOneCycle()
            Result.success()
        }
    } catch (_: Throwable) {
        Result.retry()
    }
}

object XnaiAutonomyScheduler {
    private const val NAME = "xnai-autonomous-learning"

    fun setEnabled(context: Context, enabled: Boolean) {
        val wm = androidx.work.WorkManager.getInstance(context)
        if (enabled) {
            val request = androidx.work.PeriodicWorkRequestBuilder<XnaiAutonomyWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(androidx.work.Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, androidx.work.ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            wm.cancelUniqueWork(NAME)
        }
    }
}
