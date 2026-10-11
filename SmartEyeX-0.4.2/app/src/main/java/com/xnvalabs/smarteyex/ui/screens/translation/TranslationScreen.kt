package com.xnvalabs.smarteyex.ui.screens.translation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xnvalabs.smarteyex.core.ListeningState
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.translation.TranslationRepository
import com.xnvalabs.smarteyex.ui.components.BackChevron
import com.xnvalabs.smarteyex.ui.theme.AccentOrange
import com.xnvalabs.smarteyex.ui.theme.LightBgWarm
import com.xnvalabs.smarteyex.ui.theme.LightSurface
import com.xnvalabs.smarteyex.ui.theme.TextMutedLight
import com.xnvalabs.smarteyex.ui.theme.TextPrimaryLight
import kotlinx.coroutines.launch
import java.util.Locale

private val LANGUAGES = listOf("Indonesian", "English", "Japanese", "Mandarin", "Spanish")

/**
 * Phone-first translation: typed or one-shot spoken input, cloud translation,
 * and optional spoken output when the target voice is installed on the device.
 * Voice capture is user-triggered, not a background listener.
 */
@Composable
fun TranslationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceText by remember { mutableStateOf("") }
    var sourceLanguage by remember { mutableStateOf("Indonesian") }
    var targetLanguage by remember { mutableStateOf("English") }
    var activePicker by remember { mutableStateOf<String?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isTranslationSuccessful by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
        tts = engine
        onDispose {
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
            tts = null
            ttsReady = false
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotBlank()) {
                sourceText = spoken
                resultText = null
                isTranslationSuccessful = false
                voiceError = null
            } else {
                voiceError = "Ucapan belum tertangkap. Coba lebih dekat ke mikrofon lalu ulangi."
            }
        }
    }

    fun launchSpeechRecognition() {
        val languageTag = languageTagFor(sourceLanguage)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ucapkan teks dalam $sourceLanguage")
        }
        runCatching {
            isListening = true
            voiceError = null
            speechLauncher.launch(intent)
        }.onFailure {
            isListening = false
            voiceError = "Layanan speech recognition tidak tersedia di HP ini. Kamu tetap bisa mengetik teks."
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSpeechRecognition()
        else voiceError = "Izin mikrofon ditolak. Aktifkan izin jika ingin memasukkan teks lewat suara."
    }

    fun startVoiceInput() {
        if (ListeningState.active.value) {
            voiceError = "Mic Live sedang aktif. Matikan dulu sebelum memulai input suara terjemahan."
            return
        }
        if (!PrivacyRepository.settings.value.microphoneEnabled) {
            voiceError = "Microphone OFF di Privacy Control. Aktifkan untuk memakai input suara."
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognition()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun translateInput() {
        val clean = sourceText.trim()
        if (clean.isBlank()) {
            voiceError = "Masukkan atau ucapkan teks terlebih dahulu."
            return
        }
        if (!PrivacyRepository.settings.value.cloudProcessingEnabled) {
            voiceError = "Cloud Processing OFF. Aktifkan di Privacy Control untuk menerjemahkan."
            return
        }
        isTranslating = true
        resultText = null
        isTranslationSuccessful = false
        voiceError = null
        scope.launch {
            val result = runCatching { TranslationRepository.translate(clean, targetLanguage) }
                .getOrElse { Result.failure(it) }
            isTranslating = false
            result.fold(
                onSuccess = { translated ->
                    resultText = translated
                    isTranslationSuccessful = translated.isNotBlank()
                },
                onFailure = { error ->
                    resultText = error.message ?: "Gagal menerjemahkan."
                    isTranslationSuccessful = false
                },
            )
        }
    }

    fun speakTranslation() {
        val text = resultText?.takeIf { isTranslationSuccessful }.orEmpty()
        val engine = tts
        if (text.isBlank()) return
        if (!ttsReady || engine == null) {
            voiceError = "Mesin suara HP belum siap. Tunggu sebentar lalu tekan Dengarkan lagi."
            return
        }
        val locale = localeFor(targetLanguage)
        val availability = runCatching { engine.isLanguageAvailable(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (availability < TextToSpeech.LANG_AVAILABLE) {
            voiceError = "Suara $targetLanguage belum terpasang di Text-to-Speech HP. Pasang paket suara bahasa tersebut di pengaturan Android."
            return
        }
        engine.language = locale
        val status = engine.speak(text.take(3000), TextToSpeech.QUEUE_FLUSH, null, "smartex-translation-${System.currentTimeMillis()}")
        voiceError = if (status == TextToSpeech.ERROR) "TTS gagal memutar hasil. Coba pasang atau pilih mesin suara lain di Android." else null
    }

    Box(modifier = Modifier.fillMaxSize().background(LightBgWarm)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BackChevron(onBack)
                Text("Translation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Ketik atau ucapkan teks, lalu tinjau hasil terjemahan sebelum diputar. Input suara hanya dimulai saat kamu menekan Bicara; layanan speech recognition Android dapat memproses ucapan sesuai penyedianya.",
                fontSize = 12.sp,
                color = TextMutedLight,
                lineHeight = 17.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LanguagePicker(
                    modifier = Modifier.weight(1f),
                    label = "DARI",
                    selected = sourceLanguage,
                    expanded = activePicker == "source",
                    onExpand = { activePicker = if (activePicker == "source") null else "source" },
                    onDismiss = { activePicker = null },
                    onSelect = { sourceLanguage = it; activePicker = null },
                )
                LanguagePicker(
                    modifier = Modifier.weight(1f),
                    label = "KE",
                    selected = targetLanguage,
                    expanded = activePicker == "target",
                    onExpand = { activePicker = if (activePicker == "target") null else "target" },
                    onDismiss = { activePicker = null },
                    onSelect = { targetLanguage = it; activePicker = null },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp)
                    .background(LightSurface, RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFFE8E1D7), RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                Text("TEKS ASLI", fontSize = 10.sp, color = TextMutedLight, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(7.dp))
                Box {
                    if (sourceText.isEmpty()) Text("Ketik atau tekan Bicara...", fontSize = 14.sp, color = TextMutedLight)
                    BasicTextField(
                        value = sourceText,
                        onValueChange = { sourceText = it; resultText = null; isTranslationSuccessful = false },
                        textStyle = TextStyle(color = TextPrimaryLight, fontSize = 14.sp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TranslationAction(
                    modifier = Modifier.weight(1f),
                    title = if (isListening) "Mendengarkan..." else "🎙 Bicara",
                    enabled = !isListening && !isTranslating,
                    filled = false,
                    onClick = ::startVoiceInput,
                )
                TranslationAction(
                    modifier = Modifier.weight(1f),
                    title = if (isTranslating) "Menerjemahkan..." else "Terjemahkan",
                    enabled = !isTranslating && !isListening && sourceText.isNotBlank(),
                    filled = true,
                    onClick = ::translateInput,
                )
            }

            voiceError?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, fontSize = 11.sp, color = AccentOrange, lineHeight = 15.sp)
            }

            resultText?.let { text ->
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE8E1D7), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                ) {
                    Text("HASIL TERJEMAHAN", fontSize = 10.sp, color = TextMutedLight, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(text, fontSize = 15.sp, color = TextPrimaryLight, lineHeight = 21.sp)
                    if (isTranslationSuccessful) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TranslationAction(
                            modifier = Modifier.fillMaxWidth(),
                            title = "🔊 Dengarkan hasil",
                            enabled = true,
                            filled = false,
                            onClick = ::speakTranslation,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Terjemahan memerlukan koneksi dan Cloud Processing aktif. Ini mode bicara-satu-kali, bukan interpreter latar belakang yang selalu mendengarkan.",
                fontSize = 10.sp,
                color = TextMutedLight,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun LanguagePicker(
    modifier: Modifier,
    label: String,
    selected: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(LightSurface, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFE8E1D7), RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpand,
                )
                .padding(horizontal = 11.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 9.sp, color = TextMutedLight, fontWeight = FontWeight.Bold)
                Text(selected, fontSize = 12.sp, color = TextPrimaryLight, fontWeight = FontWeight.SemiBold)
            }
            Text("⌄", fontSize = 15.sp, color = AccentOrange)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            LANGUAGES.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language, color = if (language == selected) AccentOrange else TextPrimaryLight) },
                    onClick = { onSelect(language) },
                )
            }
        }
    }
}

@Composable
private fun TranslationAction(
    modifier: Modifier,
    title: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                if (filled) AccentOrange.copy(alpha = if (enabled) 1f else 0.35f) else LightSurface,
                RoundedCornerShape(12.dp),
            )
            .border(1.dp, if (filled) AccentOrange.copy(alpha = 0.25f) else Color(0xFFE8E1D7), RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> TextMutedLight
                filled -> Color.White
                else -> TextPrimaryLight
            },
        )
    }
}

private fun languageTagFor(language: String): String = when (language) {
    "Indonesian" -> "id-ID"
    "English" -> "en-US"
    "Japanese" -> "ja-JP"
    "Mandarin" -> "zh-CN"
    "Spanish" -> "es-ES"
    else -> Locale.getDefault().toLanguageTag()
}

private fun localeFor(language: String): Locale = Locale.forLanguageTag(languageTagFor(language))
