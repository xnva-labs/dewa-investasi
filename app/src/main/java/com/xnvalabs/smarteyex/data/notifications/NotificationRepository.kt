package com.xnvalabs.smarteyex.data.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository

/** Notification content kept only in volatile app memory. */
data class RawNotification(val app: String, val msg: String, val timestamp: Long)

/** In-memory reply action extracted from a notification's RemoteInput action. */
data class ReplyTarget(
    val app: String,
    val action: Notification.Action,
)

object NotificationRepository {
    private const val MAX_KEPT = 30

    var notifications = mutableStateOf(emptyList<RawNotification>())
        private set
    var latestReplyTarget = mutableStateOf<ReplyTarget?>(null)
        private set

    fun push(app: String, msg: String, replyAction: Notification.Action? = null) {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return
        val cleanApp = app.trim().take(80)
        val cleanMessage = msg.trim().take(2000)
        if (cleanApp.isBlank() || cleanMessage.isBlank()) return
        notifications.value = (listOf(RawNotification(cleanApp, cleanMessage, System.currentTimeMillis())) + notifications.value).take(MAX_KEPT)
        if (replyAction?.remoteInputs?.isNotEmpty() == true) latestReplyTarget.value = ReplyTarget(cleanApp, replyAction)
    }

    fun sendReply(context: Context, message: String): Result<Unit> {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) {
            return Result.failure(IllegalStateException("Notification Content OFF."))
        }
        val target = latestReplyTarget.value ?: return Result.failure(IllegalStateException("Belum ada notifikasi yang bisa dibalas."))
        val clean = message.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Pesan balasan kosong."))
        return runCatching {
            val action = target.action
            val fillIn = Intent()
            val results = android.os.Bundle()
            action.remoteInputs.first().let { input -> results.putCharSequence(input.resultKey, clean.take(2000)) }
            RemoteInput.addResultsToIntent(action.remoteInputs, fillIn, results)
            action.actionIntent.send(context, 0, fillIn)
            latestReplyTarget.value = null
        }
    }

    fun clear() {
        notifications.value = emptyList()
        latestReplyTarget.value = null
    }

    fun isAccessGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
}
