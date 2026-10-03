package com.xnvalabs.smarteyex.core

import android.speech.SpeechRecognizer

/** Pesan error pengenal suara yang jelas untuk pengguna, bukan kode angka mentah. */
object VoiceErrors {
    fun message(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Ucapan tidak dikenali. Coba ulangi dengan lebih jelas."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada suara terdengar. Coba lagi."
        SpeechRecognizer.ERROR_AUDIO -> "Ada masalah pada audio atau microphone."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin microphone belum diberikan."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Pengenalan suara butuh jaringan dan sedang bermasalah."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Pengenal suara sedang sibuk. Coba sebentar lagi."
        SpeechRecognizer.ERROR_SERVER -> "Layanan pengenal suara sedang bermasalah."
        SpeechRecognizer.ERROR_CLIENT -> "Perekaman dihentikan."
        else -> "Voice input gagal (kode $code)."
    }
}
