package com.xnvalabs.smarteyex.data.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.companion.CompanionMode
import com.xnvalabs.smarteyex.data.companion.CompanionRepository
import java.util.Locale

/** Notification content kept only in volatile app memory. */
data class RawNotification(val app: String, val msg: String, val timestamp: Long)

data class ReplyTarget(
    val app: String,
    val action: Notification.Action,
    val postedAt: Long,
    val sender: String,
)

/** A reply-capable notification remembered per sender, so voice can say "jawab <nama> ...". Volatile, never persisted. */
data class SenderReply(
    val sender: String,
    val app: String,
    val action: Notification.Action,
    val postedAt: Long,
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

    private const val MAX_SENDERS = 20
    private const val SENDER_TTL_MS = 30 * 60_000L
    private val senderTargets = LinkedHashMap<String, SenderReply>()

    /** Set by the always-on mic service while it runs; it reads new messages aloud itself. */
    @Volatile
    var incomingListener: ((app: String, sender: String, body: String, canReply: Boolean) -> Unit)? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeechQueue = java.util.ArrayDeque<String>()

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

    fun push(
        app: String,
        msg: String,
        replyAction: Notification.Action? = null,
        sender: String = "",
        body: String = "",
        announce: Boolean = false,
    ) {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return
        val cleanApp = app.trim().take(80)
        val cleanMessage = msg.trim().take(2000)
        if (cleanApp.isBlank() || cleanMessage.isBlank()) return
        notifications.value = (listOf(RawNotification(cleanApp, cleanMessage, System.currentTimeMillis())) + notifications.value)
            .take(MAX_KEPT)
        val postedAt = System.currentTimeMillis()
        latestReplyTarget.value = replyAction
            ?.takeIf { it.remoteInputs?.any { input -> !input.isDataOnly } == true }
            ?.let { ReplyTarget(cleanApp, it, postedAt, sender.trim().take(80).ifBlank { cleanApp }) }

        val cleanSender = sender.trim().take(80)
        val usableAction = replyAction?.takeIf { it.remoteInputs?.any { input -> !input.isDataOnly } == true }
        if (cleanSender.isNotBlank() && usableAction != null) {
            rememberSender(SenderReply(cleanSender, cleanApp, usableAction, System.currentTimeMillis()))
        }
        if (announce) {
            incomingListener?.invoke(cleanApp, cleanSender.ifBlank { cleanApp }, body.trim().take(400).ifBlank { cleanMessage }, usableAction != null)
        }
    }

    private fun rememberSender(entry: SenderReply) {
        synchronized(senderTargets) {
            val key = entry.sender.lowercase()
            senderTargets.remove(key)
            senderTargets[key] = entry
            while (senderTargets.size > MAX_SENDERS) senderTargets.remove(senderTargets.keys.first())
        }
    }

    /** Senders whose last message can still be answered (newest last). */
    fun recentSenders(): List<SenderReply> = synchronized(senderTargets) {
        val cutoff = System.currentTimeMillis() - SENDER_TTL_MS
        senderTargets.values.filter { it.postedAt >= cutoff }
    }

    fun sendReplyToSender(context: Context, target: SenderReply, message: String): Result<Unit> {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) {
            return Result.failure(IllegalStateException("Notification Content OFF."))
        }
        if (!loadPreferences().voiceReplyEnabled) {
            return Result.failure(IllegalStateException("Balas dengan Suara sedang OFF. Aktifkan dulu di pengaturan notifikasi."))
        }
        val clean = message.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Pesan balasan kosong."))
        return runCatching {
            val action = target.action
            val input = action.remoteInputs?.firstOrNull { !it.isDataOnly }
                ?: error("Notifikasi tidak menyediakan kolom balasan teks.")
            val fillIn = Intent()
            val results = Bundle().apply { putCharSequence(input.resultKey, clean.take(2000)) }
            RemoteInput.addResultsToIntent(action.remoteInputs, fillIn, results)
            action.actionIntent.send(context, 0, fillIn)
            synchronized(senderTargets) { senderTargets.remove(target.sender.lowercase()) }
            if (latestReplyTarget.value?.action?.actionIntent == action.actionIntent) latestReplyTarget.value = null
            Unit
        }.onFailure { AppDiagnostics.warn("Voice reply to sender failed", it) }
    }


    /** Resolve a spoken sender name to the exact reply-capable action remembered for that sender. */
    fun replyTargetForSender(sender: String, now: Long = System.currentTimeMillis()): ReplyTarget? {
        val clean = sender.trim()
        if (clean.isBlank()) return null
        val latest = latestReplyTarget.value
        if (latest != null && latest.sender.equals(clean, ignoreCase = true) && now - latest.postedAt in 0..SENDER_TTL_MS) {
            return latest
        }
        return synchronized(senderTargets) {
            val entry = senderTargets.values.firstOrNull {
                it.sender.equals(clean, ignoreCase = true) && now - it.postedAt in 0..SENDER_TTL_MS
            } ?: return@synchronized null
            ReplyTarget(entry.app, entry.action, entry.postedAt, entry.sender)
        }
    }

    /** Apply the persisted delivery mode to a newly received notification. */
    fun deliver(context: Context, app: String, message: String) {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return
        val cleanApp = app.trim().take(80)
        val cleanMessage = message.trim().take(1200)
        if (cleanApp.isBlank() || cleanMessage.isBlank()) return
        val mode = loadPreferences().notificationMode
        // The always-on mic service reads incoming messages itself; do not speak them twice.
        if (incomingListener != null && mode == MODE_SPEAK) return
        // Companion SENYAP suppresses spoken auto-read consistently whether Mic Live is running or not.
        if (mode == MODE_SPEAK && CompanionRepository.currentMode.value == CompanionMode.SILENT) return
        deliverMode(context, "$cleanApp: $cleanMessage", mode, 85)
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

    fun hasFreshReplyTarget(now: Long = System.currentTimeMillis()): Boolean {
        val target = latestReplyTarget.value ?: return false
        return now - target.postedAt in 0..SENDER_TTL_MS
    }

    fun sendReply(context: Context, message: String): Result<Unit> {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) {
            return Result.failure(IllegalStateException("Notification Content OFF."))
        }
        val target = latestReplyTarget.value
            ?: return Result.failure(IllegalStateException("Belum ada notifikasi yang bisa dibalas."))
        return sendReplyToTarget(context, target, message)
    }

    /**
     * Sends only to the exact notification action the user reviewed. This
     * prevents a newer message arriving during voice dictation from silently
     * changing the recipient before confirmation.
     */
    fun sendReplyToTarget(context: Context, target: ReplyTarget, message: String): Result<Unit> {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) {
            return Result.failure(IllegalStateException("Notification Content OFF."))
        }
        // Both callers originate from voice-drafted replies; enforce preference here too,
        // so switching the feature off while a draft is awaiting review blocks sending.
        if (!loadPreferences().voiceReplyEnabled) {
            return Result.failure(IllegalStateException("Balas dengan Suara sedang OFF. Aktifkan dulu di pengaturan notifikasi."))
        }
        if (System.currentTimeMillis() - target.postedAt !in 0..SENDER_TTL_MS) {
            if (latestReplyTarget.value?.postedAt == target.postedAt) latestReplyTarget.value = null
            return Result.failure(IllegalStateException("Target balasan sudah kedaluwarsa. Tunggu notifikasi baru."))
        }
        val clean = message.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Pesan balasan kosong."))
        return runCatching {
            val action = target.action
            val remoteInputs = action.remoteInputs.orEmpty()
            val input = remoteInputs.firstOrNull { !it.isDataOnly }
                ?: error("Notifikasi tidak menyediakan kolom balasan teks.")
            val fillIn = Intent()
            val results = Bundle().apply { putCharSequence(input.resultKey, clean.take(2000)) }
            RemoteInput.addResultsToIntent(remoteInputs, fillIn, results)
            action.actionIntent.send(context, 0, fillIn)
            if (latestReplyTarget.value?.action?.actionIntent == action.actionIntent) latestReplyTarget.value = null
            Unit
        }.onFailure { AppDiagnostics.warn("Notification quick reply failed", it) }
    }

    fun clear() {
        notifications.value = emptyList()
        latestReplyTarget.value = null
        synchronized(senderTargets) { senderTargets.clear() }
    }

    fun releaseDelivery() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
        synchronized(pendingSpeechQueue) { pendingSpeechQueue.clear() }
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

    fun ring(volume: Int = 85) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume.coerceIn(0, 100))
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 320)
            // release() stops playback, so give the beep time to finish first.
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 450L)
        }.onFailure { AppDiagnostics.warn("Notification ring failed", it) }
    }

    /** Read the current notification feed only after an explicit user request. */
    fun readLatestNotifications(context: Context, limit: Int = 3): Boolean {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) return false
        // SENYAP blocks spoken output, even if the listener screen is opened.
        if (CompanionRepository.currentMode.value == CompanionMode.SILENT) return false
        val latest = notifications.value.take(limit.coerceIn(1, 5)).asReversed()
        if (latest.isEmpty()) {
            speak(context, "Belum ada notifikasi untuk dibacakan.")
        } else {
            latest.forEach { speak(context, "${it.app}: ${it.msg.take(240)}") }
        }
        return true
    }

    private fun enqueuePendingSpeech(text: String) {
        synchronized(pendingSpeechQueue) {
            // Bound the queue if TTS initialization is stalled; retain the newest useful items.
            while (pendingSpeechQueue.size >= 10) pendingSpeechQueue.removeFirst()
            pendingSpeechQueue.addLast(text.take(1200))
        }
    }

    private fun drainPendingSpeech(): List<String> = synchronized(pendingSpeechQueue) {
        buildList {
            while (pendingSpeechQueue.isNotEmpty()) add(pendingSpeechQueue.removeFirst())
        }
    }

    private fun speak(context: Context, text: String) {
        val clean = text.trim().take(1200)
        if (clean.isBlank()) return
        runCatching {
            if (tts == null) {
                enqueuePendingSpeech(clean)
                tts = TextToSpeech(context.applicationContext) { status ->
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) {
                        tts?.language = Locale.forLanguageTag("id-ID").takeIf {
                            runCatching { tts?.isLanguageAvailable(it) ?: TextToSpeech.LANG_NOT_SUPPORTED }
                                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
                        } ?: Locale.getDefault()
                        drainPendingSpeech().forEach { queued ->
                            tts?.speak(queued, TextToSpeech.QUEUE_ADD, null, "notif-${System.currentTimeMillis()}")
                        }
                    } else {
                        synchronized(pendingSpeechQueue) { pendingSpeechQueue.clear() }
                    }
                }
            } else if (ttsReady) {
                tts?.speak(clean, TextToSpeech.QUEUE_ADD, null, "notif-${System.currentTimeMillis()}")
            } else {
                enqueuePendingSpeech(clean)
            }
        }.onFailure {
            synchronized(pendingSpeechQueue) { pendingSpeechQueue.clear() }
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
