package com.xnvalabs.smarteyex.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.xnvalabs.smarteyex.MainActivity
import com.xnvalabs.smarteyex.R
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.AppVisibility
import com.xnvalabs.smarteyex.core.ListeningState
import com.xnvalabs.smarteyex.data.apps.AppLauncher
import com.xnvalabs.smarteyex.data.assistant.AppMatcher
import com.xnvalabs.smarteyex.data.assistant.AssistantCommandParser
import com.xnvalabs.smarteyex.data.assistant.AssistantCommandParser.Command
import com.xnvalabs.smarteyex.data.assistant.LaunchableApp
import com.xnvalabs.smarteyex.data.companion.CompanionRepository
import com.xnvalabs.smarteyex.data.notifications.NotificationRepository
import com.xnvalabs.smarteyex.data.notifications.SenderReply
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.xnai.XnaiMessage
import com.xnvalabs.smarteyex.data.xnai.XnaiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Always-on voice mode ("Mic Live"). Runs as a visible microphone foreground service, so Android
 * shows its permanent notification and mic indicator; this cannot and should not be hidden.
 *
 * Only speech that starts with the wake word ("SmartEyeX ...") is acted on. The one exception is
 * "jawab <nama> <isi>" right after a message arrived from that sender; it never sends on its own:
 * SmartEyeX reads the message back and waits for "iya" or "batal". Everything else is dropped
 * immediately and never stored. Stop it from the notification button, the XNAI screen, or by
 * saying "SmartEyeX matikan mic".
 */
private data class PendingReply(val target: SenderReply, val message: String)

class ListeningService : Service(), RecognitionListener {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val restartRunnable = Runnable { listen() }
    private val speechQueue = ArrayDeque<String>()
    private val history = ArrayList<XnaiMessage>()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false
    private var listening = false
    private var speaking = false
    private var asking = false
    private var stopAfterSpeech = false
    private var armedUntil = 0L
    private var failures = 0
    private var pendingReply: PendingReply? = null
    private var pendingUntil = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        tts = TextToSpeech(applicationContext) { status -> onTtsInit(status) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY

        val foregroundOk = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        }.onFailure { AppDiagnostics.warn("Live mic foreground start failed", it) }.isSuccess
        val permitted = PrivacyRepository.settings.value.microphoneEnabled &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        recognizer = if (foregroundOk && permitted) createRecognizer() else null
        if (recognizer == null) {
            ListeningState.error.value = when {
                !permitted -> "Mic Live butuh Microphone ON di Privacy Control dan izin mikrofon."
                !foregroundOk -> "Android menolak menjalankan Mic Live. Buka app lalu coba lagi."
                else -> "Speech Recognition tidak tersedia di perangkat ini."
            }
            stopSelf()
            return START_NOT_STICKY
        }

        running = true
        ListeningState.error.value = null
        ListeningState.active.value = true
        acquireWakeLock()
        NotificationRepository.incomingListener = { app, sender, body, canReply ->
            main.post { onIncoming(app, sender, body, canReply) }
        }
        enqueueSpeech("Mic aktif. Ucapkan SmartEyeX, lalu perintahmu.")
        listen()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        ListeningState.active.value = false
        NotificationRepository.incomingListener = null
        pendingReply = null
        main.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        scope.cancel()
        super.onDestroy()
    }

    // ---- listening loop -------------------------------------------------------------------

    private fun createRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) return null
        return SpeechRecognizer.createSpeechRecognizer(applicationContext).also { it.setRecognitionListener(this) }
    }

    private fun rebuildRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = createRecognizer()
    }

    private fun listen() {
        if (!running || listening || speaking) return
        if (!PrivacyRepository.settings.value.microphoneEnabled) {
            ListeningState.error.value = "Microphone OFF di Privacy Control — Mic Live dihentikan."
            stopSelf()
            return
        }
        val speech = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, RECOGNITION_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        refreshWakeLock()
        listening = true
        runCatching { speech.startListening(intent) }.onFailure {
            listening = false
            restartListening(1_500L)
        }
    }

    private fun restartListening(delayMs: Long) {
        if (!running) return
        main.removeCallbacks(restartRunnable)
        main.postDelayed(restartRunnable, delayMs)
    }

    override fun onResults(results: Bundle?) {
        listening = false
        failures = 0
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        runCatching { handleUtterance(text) }.onFailure { AppDiagnostics.warn("Live mic command failed", it) }
        if (!speaking) restartListening(250L)
    }

    override fun onError(error: Int) {
        listening = false
        if (speaking) return
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                ListeningState.error.value = "Izin microphone dicabut — Mic Live dihentikan."
                stopSelf()
            }
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restartListening(200L)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                rebuildRecognizer()
                restartListening(800L)
            }
            else -> {
                failures++
                restartListening((failures * 1_000L).coerceAtMost(10_000L))
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    // ---- commands -------------------------------------------------------------------------

    private fun handleUtterance(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return

        // A reply is waiting for "iya" / "batal". Short answers resolve it, a new wake-word command cancels it,
        // and other chatter is ignored until the window closes.
        val pending = pendingReply
        if (pending != null) {
            if (SystemClock.elapsedRealtime() > pendingUntil) {
                pendingReply = null
            } else {
                when (AssistantCommandParser.confirmation(text)) {
                    AssistantCommandParser.Confirmation.YES -> {
                        pendingReply = null
                        sendPending(pending)
                        return
                    }
                    AssistantCommandParser.Confirmation.NO -> {
                        pendingReply = null
                        enqueueSpeech("Oke, nggak jadi dikirim.")
                        return
                    }
                    null -> {
                        if (AssistantCommandParser.stripWakeWord(text) == null) return
                        pendingReply = null
                    }
                }
            }
        }

        val afterWake = AssistantCommandParser.stripWakeWord(text)
        val armed = SystemClock.elapsedRealtime() < armedUntil
        val command = when {
            afterWake != null -> afterWake
            armed -> text
            else -> null
        }
        if (command == null) {
            // No wake word: the only thing allowed is "jawab <nama> ..." for a message that just arrived.
            AssistantCommandParser.replyBody(text)?.let { replyBySpeech(it, announceErrors = false, windowOnly = true) }
            return
        }
        armedUntil = 0L
        when (val parsed = AssistantCommandParser.parse(command)) {
            Command.WakeOnly -> {
                armedUntil = SystemClock.elapsedRealtime() + ARM_WINDOW_MS
                enqueueSpeech("Ya?")
            }
            Command.StopListening -> {
                if (ttsReady) {
                    stopAfterSpeech = true
                    enqueueSpeech("Mic dimatikan.")
                } else {
                    stopSelf()
                }
            }
            Command.ReadNotifications -> readNotifications()
            is Command.OpenApp -> openApp(parsed.name)
            is Command.Reply -> replyBySpeech(parsed.body, announceErrors = true, windowOnly = false)
            is Command.Ask -> ask(parsed.text)
        }
    }

    private fun onIncoming(app: String, sender: String, body: String, @Suppress("UNUSED_PARAMETER") canReply: Boolean) {
        if (!running) return
        // Silent mode: stay quiet (the message still shows in the notification feed).
        if (NotificationRepository.loadPreferences().notificationMode == NotificationRepository.MODE_SILENT) return
        val who = if (sender.equals(app, ignoreCase = true)) app else "$app dari $sender"
        // Replyable messages are remembered per sender by NotificationRepository ("jawab <nama> ...").
        enqueueSpeech("$who: $body")
    }

    private fun replyBySpeech(body: String, announceErrors: Boolean, windowOnly: Boolean) {
        val now = System.currentTimeMillis()
        val candidates = NotificationRepository.recentSenders().filter { !windowOnly || now - it.postedAt <= REPLY_WINDOW_MS }
        if (candidates.isEmpty()) {
            if (announceErrors) enqueueSpeech("Tidak ada pesan yang bisa dibalas.")
            return
        }
        val split = AssistantCommandParser.splitReply(body, candidates.map { it.sender })
        if (split == null) {
            if (announceErrors) enqueueSpeech("Nama pengirim tidak ketemu atau kurang jelas.")
            return
        }
        if (split.message.isBlank()) {
            enqueueSpeech("Isi balasan untuk ${split.sender} kosong.")
            return
        }
        val target = candidates.firstOrNull { it.sender == split.sender } ?: return
        // Never send on a single hearing: speech recognition can be wrong and a sent message cannot be recalled.
        pendingReply = PendingReply(target, split.message)
        pendingUntil = SystemClock.elapsedRealtime() + CONFIRM_WINDOW_MS
        enqueueSpeech("Gue tangkepnya gini untuk ${target.sender}: ${split.message.take(160)}. Kirim?")
    }

    private fun sendPending(pending: PendingReply) {
        NotificationRepository.sendReplyToSender(this, pending.target, pending.message)
            .onSuccess { enqueueSpeech("Terkirim ke ${pending.target.sender}.") }
            .onFailure { enqueueSpeech("Gagal mengirim ke ${pending.target.sender}.") }
    }

    private fun readNotifications() {
        if (!PrivacyRepository.settings.value.notificationContentEnabled) {
            enqueueSpeech("Notification Content masih mati di Privacy Control.")
            return
        }
        val recent = NotificationRepository.notifications.value.take(3)
        if (recent.isEmpty()) {
            enqueueSpeech("Belum ada notifikasi.")
            return
        }
        recent.forEach { enqueueSpeech("${it.app}: ${it.msg.take(200)}") }
    }

    private fun ask(text: String) {
        if (asking) {
            enqueueSpeech("Sebentar, masih memproses.")
            return
        }
        if (!PrivacyRepository.settings.value.cloudProcessingEnabled) {
            enqueueSpeech("Cloud Processing masih mati. Aktifkan di Privacy Control.")
            return
        }
        asking = true
        CompanionRepository.observeUserText(text)
        val prompt = "$text\n\n(Jawab singkat, maksimal tiga kalimat, karena akan dibacakan dengan suara.)"
        val past = history.toList()
        scope.launch {
            val result = XnaiRepository.sendMessage(prompt, past, "RELAX")
            asking = false
            result.onSuccess { reply ->
                history.add(XnaiMessage("user", text))
                history.add(XnaiMessage("assistant", reply))
                while (history.size > MAX_HISTORY) history.removeAt(0)
                enqueueSpeech(reply)
            }.onFailure { enqueueSpeech(it.message ?: "Gagal menghubungi XNAI.") }
        }
    }

    private fun openApp(spoken: String) {
        val app: LaunchableApp? = AppMatcher.resolve(spoken, AppLauncher.listApps(this))
        if (app == null) {
            enqueueSpeech("Aplikasi $spoken tidak ketemu.")
            return
        }
        val launch = AppLauncher.launchIntent(this, app)
        if (launch == null) {
            enqueueSpeech("${app.label} tidak bisa dibuka.")
            return
        }
        // Android 10+ blocks apps from starting other apps while in the background. When SmartEyeX is
        // on screen the app opens directly; otherwise a tap-to-open notification is the allowed route.
        if (AppVisibility.foreground) {
            val started = runCatching { startActivity(launch) }.isSuccess
            if (started) {
                enqueueSpeech("Membuka ${app.label}.")
                return
            }
        }
        postOpenNotification(app, launch)
        enqueueSpeech("Ketuk notifikasi untuk membuka ${app.label}.")
    }

    private fun postOpenNotification(app: LaunchableApp, launch: Intent) {
        val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canPostNotifications) {
            val tap = PendingIntent.getActivity(
                this,
                app.packageName.hashCode(),
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_OPEN)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Buka ${app.label}")
                .setContentText("Ketuk untuk membuka ${app.label}.")
                .setContentIntent(tap)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(this).notify(OPEN_NOTIFICATION_ID, notification) }
        }
    }

    // ---- speech output --------------------------------------------------------------------

    private fun onTtsInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) return
        tts?.language = Locale.forLanguageTag(RECOGNITION_LANGUAGE)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                main.post { onSpeechFinished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { onSpeechFinished() }
            }
        })
        if (speechQueue.isNotEmpty() && !speaking) speakNext()
    }

    private fun enqueueSpeech(text: String) {
        val clean = text.trim().take(MAX_SPOKEN_CHARS)
        if (clean.isEmpty()) return
        speechQueue.addLast(clean)
        if (!speaking && ttsReady) speakNext()
    }

    private fun speakNext() {
        val next = speechQueue.removeFirstOrNull()
        if (next == null) {
            finishSpeaking()
            return
        }
        speaking = true
        // Stop listening while speaking so the assistant never hears (and obeys) its own voice.
        main.removeCallbacks(restartRunnable)
        runCatching { recognizer?.cancel() }
        listening = false
        val code = tts?.speak(next, TextToSpeech.QUEUE_FLUSH, null, "live-${System.nanoTime()}") ?: TextToSpeech.ERROR
        if (code != TextToSpeech.SUCCESS) speakNext()
    }

    private fun onSpeechFinished() {
        if (speechQueue.isNotEmpty()) speakNext() else finishSpeaking()
    }

    private fun finishSpeaking() {
        speaking = false
        if (stopAfterSpeech) {
            stopSelf()
            return
        }
        restartListening(250L)
    }

    // ---- plumbing -------------------------------------------------------------------------

    private fun acquireWakeLock() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smarteyex:live-mic").apply { setReferenceCounted(false) }
        refreshWakeLock()
    }

    private fun refreshWakeLock() {
        runCatching { wakeLock?.acquire(WAKE_LOCK_MS) }
    }

    private fun ensureChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_LIVE, "Mic Live", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_OPEN, "Buka aplikasi", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            flags,
        )
        return NotificationCompat.Builder(this, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SmartEyeX sedang mendengarkan")
            .setContentText("Ucapkan \"SmartEyeX …\". Ucapkan \"SmartEyeX matikan mic\" untuk berhenti.")
            .setContentIntent(openApp)
            .addAction(0, "Matikan mic", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.xnvalabs.smarteyex.action.STOP_LIVE_MIC"
        private const val CHANNEL_LIVE = "smarteyex_live_mic"
        private const val CHANNEL_OPEN = "smarteyex_open_app"
        private const val NOTIFICATION_ID = 4101
        private const val OPEN_NOTIFICATION_ID = 4102
        private const val RECOGNITION_LANGUAGE = "id-ID"
        private const val ARM_WINDOW_MS = 15_000L
        private const val REPLY_WINDOW_MS = 2 * 60_000L
        private const val CONFIRM_WINDOW_MS = 20_000L
        private const val WAKE_LOCK_MS = 10 * 60_000L
        private const val MAX_HISTORY = 20
        private const val MAX_SPOKEN_CHARS = 700

        fun start(context: Context) {
            ListeningState.error.value = null
            ContextCompat.startForegroundService(context, Intent(context, ListeningService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningService::class.java))
        }
    }
}
