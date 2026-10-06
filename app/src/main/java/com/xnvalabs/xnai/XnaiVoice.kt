package com.xnvalabs.xnai

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class XnaiVoice(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    fun speak(text: String, locale: Locale = Locale("id", "ID")) {
        tts?.shutdown()
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = locale
                tts?.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, null, "xnai")
            }
        }
    }

    fun listen(onText: (String) -> Unit, onState: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onState("Speech recognition tidak tersedia di perangkat.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) = onState("Mendengarkan…")
                override fun onBeginningOfSpeech() = onState("Mendengarkan…")
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = onState("Memproses…")
                override fun onError(error: Int) = onState("Voice error: $error")
                override fun onResults(results: android.os.Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    onText(text)
                    onState(if (text.isBlank()) "Tidak ada teks" else "Selesai")
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
        }
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        })
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }
}
