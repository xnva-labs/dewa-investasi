package com.xnvalabs.smarteyex.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.xnvalabs.smarteyex.data.companion.CompanionRepository
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.voice.VoiceProfileRepository

/** Lifecycle-safe speech input/output facade for the XNAI voice experience. */
class VoiceController(context: Context) : RecognitionListener, TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var resultCallback: ((String) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null
    private var speechStartedAt = 0L
    private var rmsSum = 0f
    private var rmsSamples = 0
    private var wordEstimate = 0

    init {
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also { it.setRecognitionListener(this) }
        }
        tts = TextToSpeech(appContext, this)
    }

    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val speech = recognizer
        if (speech == null) {
            onError("Speech Recognition tidak tersedia di perangkat ini.")
            return
        }
        resultCallback = onResult
        errorCallback = onError
        speechStartedAt = System.currentTimeMillis()
        rmsSum = 0f
        rmsSamples = 0
        wordEstimate = 0
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        runCatching { speech.startListening(intent) }
            .onFailure { onError("Voice input gagal dimulai.") }
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        val profile = VoiceProfileRepository.profile.value
        val pitch = (0.92f + ((profile.meanEnergy - 0.5f) * 0.12f)).coerceIn(0.82f, 1.08f)
        val rate = (0.94f + ((profile.speakingRate - 0.5f) * 0.12f)).coerceIn(0.82f, 1.10f)
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)
        tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "xnai-${System.currentTimeMillis()}")
    }

    fun release() {
        runCatching { recognizer?.destroy() }
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        recognizer = null
        tts = null
        resultCallback = null
        errorCallback = null
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.getDefault()
    }

    override fun onResults(results: Bundle) {
        val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (text.isBlank()) {
            errorCallback?.invoke("Voice input tidak menangkap ucapan.")
        } else {
            val durationMs = (System.currentTimeMillis() - speechStartedAt).coerceAtLeast(1L)
            wordEstimate = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtMost(200)
            if (PrivacyRepository.settings.value.voicePersonalizationEnabled) {
                val energy = if (rmsSamples == 0) 0.5f else (rmsSum / rmsSamples).coerceIn(0f, 10f) / 10f
                val rate = (wordEstimate / (durationMs / 60_000f)).coerceIn(0f, 8f) / 8f
                VoiceProfileRepository.observeProsody(energy, rate, Locale.getDefault().toLanguageTag())
                CompanionRepository.recordVoiceProsody(energy, rate)
            }
            resultCallback?.invoke(text)
        }
        resultCallback = null
        errorCallback = null
    }

    override fun onError(error: Int) {
        errorCallback?.invoke("Voice input berhenti atau gagal ($error).")
        resultCallback = null
        errorCallback = null
    }
    override fun onReadyForSpeech(params: Bundle) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) {
        rmsSum += rmsdB.coerceIn(0f, 10f)
        rmsSamples++
    }
    override fun onBufferReceived(buffer: ByteArray) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle) = Unit
    override fun onEvent(eventType: Int, params: Bundle) = Unit
}
