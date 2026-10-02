package com.xnvalabs.smarteyex.data.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import java.util.Locale

/** Notification content kept only in volatile app memory. */
data class RawNotification(val app: String, val msg: String, val timestamp: Long)

data class ReplyTarget(
    val app: String,
    val action: Notification.Action,
)

object NotificationRepository {
    private const val MAX_KEPT = 30
    private const val KEY_VOICE_REPLY_ENABLED = "listener.voice_reply_enabled"
    private const val KEY_NOTIFICATION_MODE = "listener.notification_mode"
    private const val KEY_PRIORITY_ORDER = "listener.priority_order"
    private const val FALLBACK_PREFS = "smarteyex_listener_state"
    const val MODE_GETAR = 0
    const val MODE_SPEAK = 1
    const val MODE_RING = 2
    const val MODE_SILENT = 3

    var notifications = mutableStateOf(emptyList<RawNotification>())
        private set
    var latestReplyTarget = mutableStateOf<ReplyTarget?>(null)
        private set

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun loadPreferences(): ListenerPreferences {
        val prefs = appPreferences()
        val mode = (prefs.getString(KEY_NOTIFICATION_MODE, null) ?: SecureStorage.getString(KEY_NOTIFICATION_MODE))
            ?.toIntOrNull()?.coerceIn(MODE_GETAR, MODE_SILENT) ?: MODE_GETAR
        val voice = prefs.getString(KEY_VOICE_REPLY_ENABLED, null)?.toBooleanStrictOrNull()
            ?: SecureStorage.getString(KEY_VOICE_REPLY_ENABLED)?.toBooleanStrictOrNull()
            ?: false
        val order = (prefs.getString(KEY_PRIORITY_ORDER, null) ?: SecureStorage.getString(KEY_PRIORITY_ORDER))
            ?.split('|')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(3)
            ?.let { if (it.size == 3) it else DEFAULT_PRIORITY }
            ?: DEFAULT_PRIORITY
        return ListenerPreferences(voice, mode, order)
    }

    fun saveVoiceReplyEnabled(enabled: Boolean): Boolean = savePreference(KEY_VOICE_REPLY_ENABLED, enabled.toString())

    fun saveNotificationMode(mode: Int): Boolean = savePreference(KEY_NOTIFICATION_MODE, mode.coerceIn(MODE_GETAR, MODE_SILENT).toString())

    fun savePriorityOrder(order: List<String>): Boolean {
        val candidate = order.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(3)
        if (candidate.size != 3) return false
        return savePreference(KEY_PRIORITY_ORDER, candidate.joinToString("|"))
    }

    private fun savePreference(key: String, value: String): Boolean {
        val secure = runCatching { SecureStorage.putStringSync(key, value) }
            .onFailure { AppDiagnostics.warn("Notification preference secure write failed", it) }
            .getOrDefault(false)
        val fallback = runCatching {
            appPreferences().edit().putString(key, value).commit()
        }.onFailure { AppDiagnostics.warn("Notification preference fallback write failed", it) }.getOrDefault(false)

        if (secure && !fallback) {
            // The secure value is authoritative in this case. Remove the stale
            // mirror so the next process start cannot resurrect an older value.
            runCatching { appPreferences().edit().remove(key).commit() }
        }
        return secure || fallback
    }

    private var preferenceContext: Context? = null

    fun bind(context: Context) {
        preferenceContext = context.applicationContext
    }

    /** Ask Android to reconnect the listener so onListenerConnected() can backfill active notifications. */
    fun requestListenerRefresh(context: Context): Boolean = runCatching {
        NotificationListenerService.requestRebind(
            ComponentName(context.applicationContext, com.xnvalabs.smarteyex.service.SmartEyeXNotificationListener::class.java),
        )
        true
    }.onFailure { AppDiagnostics.warn("Notification listener rebind failed", it) }.getOrDefault(false)

    fun requestListenerRefreshFromBoundContext(): Boolean =
        preferenceContext?.let { requestListenerRefresh(it) } ?: false

    private fun appPreferences(): android.content.SharedPreferences =
        requireNotNull(preferenceContext) { "NotificationRepository.bind(context) must be called first" }
            .getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)

    fun push(app: String, msg: String, replyAction: Notification.Action? = null) {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return
        val cleanApp = app.trim().take(80)
        val cleanMessage = msg.trim().take(2000)
        if (cleanApp.isBlank() || cleanMessage.isBlank()) return
        notifications.value = (listOf(RawNotification(cleanApp, cleanMessage, System.currentTimeMillis())) + notifications.value)
            .take(MAX_KEPT)
        latestReplyTarget.value = replyAction
            ?.takeIf { it.remoteInputs?.isNotEmpty() == true }
            ?.let { ReplyTarget(cleanApp, it) }
    }

    /** Apply the persisted delivery mode to a newly received notification. */
    fun deliver(context: Context, app: String, message: String) {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return
        val cleanApp = app.trim().take(80)
        val cleanMessage = message.trim().take(1200)
        if (cleanApp.isBlank() || cleanMessage.isBlank()) return
        deliverMode(context, "$cleanApp: $cleanMessage", loadPreferences().notificationMode, 85)
    }

    /** Used by scheduled reminders whose delivery mode is per-event, not global. */
    fun deliverMode(context: Context, message: String, mode: Int, volume: Int = 85) {
        when (mode.coerceIn(MODE_GETAR, MODE_SILENT)) {
            MODE_GETAR -> vibrate(context)
            MODE_SPEAK -> speak(context, message.take(2000))
            MODE_RING -> ring(volume)
            MODE_SILENT -> Unit
        }
    }

    fun sendReply(context: Context, message: String): Result<Unit> {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) {
            return Result.failure(IllegalStateException("Notification Content OFF."))
        }
        val target = latestReplyTarget.value
            ?: return Result.failure(IllegalStateException("Belum ada notifikasi yang bisa dibalas."))
        val clean = message.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Pesan balasan kosong."))
        return runCatching {
            val action = target.action
            val input = action.remoteInputs?.firstOrNull()
                ?: error("Notifikasi tidak menyediakan input balasan.")
            val fillIn = Intent()
            val results = Bundle().apply { putCharSequence(input.resultKey, clean.take(2000)) }
            RemoteInput.addResultsToIntent(action.remoteInputs, fillIn, results)
            action.actionIntent.send(context, 0, fillIn)
            latestReplyTarget.value = null
        }.onFailure { AppDiagnostics.warn("Notification quick reply failed", it) }
    }

    fun clear() {
        notifications.value = emptyList()
        latestReplyTarget.value = null
    }

    fun releaseDelivery() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
        pendingSpeech = null
    }

    fun isAccessGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun vibrate(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        }.onFailure { AppDiagnostics.warn("Notification vibration failed", it) }
    }

    private fun ring(volume: Int = 85) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume.coerceIn(0, 100))
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 320)
            tone.release()
        }.onFailure { AppDiagnostics.warn("Notification ring failed", it) }
    }

    private var pendingSpeech: String? = null

    private fun speak(context: Context, text: String) {
        runCatching {
            pendingSpeech = text
            if (tts == null) {
                tts = TextToSpeech(context.applicationContext) { status ->
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) {
                        tts?.language = Locale.getDefault()
                        val queued = pendingSpeech
                        pendingSpeech = null
                        if (!queued.isNullOrBlank()) {
                            tts?.speak(queued, TextToSpeech.QUEUE_ADD, null, "notif-${System.currentTimeMillis()}")
                        }
                    }
                }
            } else if (ttsReady) {
                pendingSpeech = null
                tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "notif-${System.currentTimeMillis()}")
            }
        }.onFailure {
            pendingSpeech = null
            AppDiagnostics.warn("Notification speech failed", it)
        }
    }

    private val DEFAULT_PRIORITY = listOf("WhatsApp", "Telegram", "Gmail")
}

data class ListenerPreferences(
    val voiceReplyEnabled: Boolean,
    val notificationMode: Int,
    val priorityOrder: List<String>,
)
