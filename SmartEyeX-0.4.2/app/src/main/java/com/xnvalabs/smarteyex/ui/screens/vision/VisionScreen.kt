package com.xnvalabs.smarteyex.ui.screens.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.vision.CaptureLedController
import com.xnvalabs.smarteyex.data.vision.VisionPurpose
import com.xnvalabs.smarteyex.data.vision.VisionRepository
import com.xnvalabs.smarteyex.data.xnai.XnaiRepository
import com.xnvalabs.smarteyex.ui.components.BackChevron
import com.xnvalabs.smarteyex.ui.theme.AccentOrange
import com.xnvalabs.smarteyex.ui.theme.LightBgWarm
import com.xnvalabs.smarteyex.ui.theme.LightSurface
import com.xnvalabs.smarteyex.ui.theme.TextMutedLight
import com.xnvalabs.smarteyex.ui.theme.TextPrimaryLight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Computer Vision uses the phone's own camera. Snapshot analysis remains a
 * one-shot action; Live Monitor is explicitly opt-in and analyzes sequential
 * sampled frames (not a hidden continuous recording/video upload).
 */
@Composable
fun VisionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cameraEnabled = PrivacyRepository.settings.value.cameraEnabled
    val cloudEnabled = PrivacyRepository.settings.value.cloudProcessingEnabled

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var disposed by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isLiveMonitoring by remember { mutableStateOf(false) }
    var speakLiveResults by remember { mutableStateOf(true) }
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

    LaunchedEffect(tts, ttsReady) {
        val engine = tts ?: return@LaunchedEffect
        if (!ttsReady) return@LaunchedEffect
        val id = Locale.forLanguageTag("id-ID")
        val idStatus = runCatching { engine.isLanguageAvailable(id) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (idStatus >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = id
        } else {
            val fallback = Locale.getDefault()
            val fallbackStatus = runCatching { engine.isLanguageAvailable(fallback) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (fallbackStatus >= TextToSpeech.LANG_AVAILABLE) engine.language = fallback
        }
    }

    LaunchedEffect(cameraEnabled, hasPermission, cloudEnabled) {
        if (!cameraEnabled) {
            isLiveMonitoring = false
            imageCapture = null
            cameraProvider?.unbindAll()
            CaptureLedController.setActive(false)
        } else if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        if (!cloudEnabled && isLiveMonitoring) {
            isLiveMonitoring = false
            resultText = "Live Monitor dihentikan karena Cloud Processing OFF."
        }
    }

    // Bind whenever consent/permission changes. This lets the camera recover if
    // the user temporarily switches it off in Privacy Control without leaving
    // and reopening the screen.
    LaunchedEffect(cameraEnabled, hasPermission, previewView) {
        val targetView = previewView
        if (!cameraEnabled || !hasPermission || targetView == null || disposed) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed || !PrivacyRepository.settings.value.cameraEnabled ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ) return@addListener

            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(targetView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                cameraProvider = provider
                imageCapture = capture
                CaptureLedController.setActive(true)
            }.onFailure {
                imageCapture = null
                CaptureLedController.setActive(false)
                Log.w("SmartEyeX.Vision", "Camera bind failed", it)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            isLiveMonitoring = false
            cameraProvider?.unbindAll()
            imageCapture = null
            CaptureLedController.setActive(false)
        }
    }

    // Live mode is not a video stream: one frame is captured, analyzed, and
    // completed before another frame is requested. Any error stops the loop so
    // a disconnected/offline phone cannot generate repeated requests/cost.
    LaunchedEffect(isLiveMonitoring, imageCapture, cameraEnabled, hasPermission, cloudEnabled) {
        val capture = imageCapture
        if (!isLiveMonitoring || !cameraEnabled || !hasPermission || !cloudEnabled || capture == null) return@LaunchedEffect
        var previousObservation: String? = null
        var previousSpeechKey: String? = null
        try {
            while (isActive && isLiveMonitoring && cameraEnabled && hasPermission) {
                isAnalyzing = true
                val observation = try {
                    val captured = captureJpeg(capture, context)
                    captured.fold(
                        onSuccess = { bytes ->
                            VisionRepository.analyzeFrame(
                                optimizeJpeg(bytes),
                                purpose = VisionPurpose.LIVE_MONITOR,
                                previousObservation = previousObservation,
                            )
                        },
                        onFailure = { Result.failure(it) },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                } finally {
                    isAnalyzing = false
                }
                if (!isActive) break
                observation.fold(
                    onSuccess = { text ->
                        val clean = text.trim().take(5000)
                        resultText = clean
                        val speechKey = clean.lowercase().replace(Regex("\\s+"), " ").take(160)
                        val engine = tts
                        if (speakLiveResults && ttsReady && engine != null &&
                            speechKey.isNotBlank() && speechKey != previousSpeechKey && !runCatching { engine.isSpeaking }.getOrDefault(false)
                        ) {
                            val spoken = clean.replace(Regex("[`*_#]"), " ").take(240)
                            runCatching { engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "vision-live-${System.currentTimeMillis()}") }
                            previousSpeechKey = speechKey
                        }
                        previousObservation = clean.take(1000)
                    },
                    onFailure = { error ->
                        resultText = error.message ?: "Vision Live gagal. Coba lagi setelah koneksi/izin diperiksa."
                        isLiveMonitoring = false
                    },
                )
                if (isLiveMonitoring) delay(LIVE_INTERVAL_MS)
            }
        } finally {
            isAnalyzing = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(LightBgWarm)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BackChevron(onBack)
                Text("Computer Vision", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                Spacer(modifier = Modifier.weight(1f))
                if (CaptureLedController.isActive.value) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(AccentOrange, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CAMERA AKTIF", fontSize = 10.sp, color = AccentOrange)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Gunakan kamera HP. Analisis hanya berjalan ketika kamu menekan tombol; Live Monitor mengambil sampel berkala saat diaktifkan.",
                fontSize = 12.sp,
                color = TextMutedLight,
                lineHeight = 17.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                !cameraEnabled -> Text(
                    "Camera sedang OFF — aktifkan di Privacy Control untuk memakai Computer Vision.",
                    fontSize = 13.sp,
                    color = AccentOrange,
                )
                !hasPermission -> Text(
                    "Menunggu izin kamera dari Android...",
                    fontSize = 13.sp,
                    color = TextMutedLight,
                )
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(LightSurface, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFE8E1D7), RoundedCornerShape(16.dp)),
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx -> PreviewView(ctx).also { previewView = it } },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        VisionActionCard(
                            modifier = Modifier.weight(1f),
                            title = when {
                                isAnalyzing && !isLiveMonitoring -> "Menganalisis..."
                                else -> "📷 Ambil & Analisis"
                            },
                            selected = false,
                            enabled = imageCapture != null && !isAnalyzing && !isLiveMonitoring,
                            onClick = {
                                val capture = imageCapture ?: return@VisionActionCard
                                isAnalyzing = true
                                resultText = null
                                scope.launch {
                                    val result = try {
                                        val captured = captureJpeg(capture, context)
                                        captured.fold(
                                            onSuccess = { bytes ->
                                                runCatching { optimizeJpeg(bytes) }.fold(
                                                    onSuccess = { jpeg -> VisionRepository.analyzeFrame(jpeg, VisionPurpose.SNAPSHOT) },
                                                    onFailure = { Result.failure(it) },
                                                )
                                            },
                                            onFailure = { Result.failure(it) },
                                        )
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Throwable) {
                                        Result.failure(error)
                                    } finally {
                                        isAnalyzing = false
                                    }
                                    resultText = result.fold(
                                        onSuccess = { it },
                                        onFailure = { error -> error.message ?: "Gagal menganalisis gambar." },
                                    )
                                }
                            },
                        )
                        VisionActionCard(
                            modifier = Modifier.weight(1f),
                            title = if (isLiveMonitoring) "■ Hentikan Live" else "◎ Pantau Live",
                            selected = isLiveMonitoring,
                            enabled = imageCapture != null && (isLiveMonitoring || !isAnalyzing),
                            onClick = {
                                if (isLiveMonitoring) {
                                    isLiveMonitoring = false
                                    resultText = "Live Monitor dihentikan. Kamera tetap aktif selama halaman Vision terbuka."
                                } else if (!cloudEnabled) {
                                    resultText = "Cloud Processing OFF — aktifkan di Privacy Control sebelum memulai Live Monitor."
                                } else if (!XnaiRepository.isConfigured) {
                                    resultText = "Layanan XNAI/Vision belum dikonfigurasi untuk build ini. Hubungkan backend HTTPS sebelum memakai Live Monitor."
                                } else {
                                    resultText = "Live Monitor aktif. Sampel akan dianalisis satu per satu; tekan Hentikan Live untuk berhenti."
                                    isLiveMonitoring = true
                                }
                            },
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { speakLiveResults = !speakLiveResults },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            if (speakLiveResults) "🔊 Umpan balik suara: ON" else "🔇 Umpan balik suara: OFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMutedLight,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (isLiveMonitoring) Text("LIVE", fontSize = 10.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "Privasi: selama Live Monitor aktif, frame sampel dikirim lewat HTTPS untuk dianalisis. Siklus menunggu respons AI lalu jeda sekitar 3 detik; waktu total bergantung jaringan/model. Ini bukan streaming video nonstop atau alat keselamatan; jangan mengandalkan AI untuk RPM, takaran bahan, atau memastikan mesin aman.",
                        fontSize = 10.sp,
                        color = TextMutedLight,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )

                    resultText?.let { text ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LightSurface, RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0xFFE8E1D7), RoundedCornerShape(14.dp))
                                .padding(14.dp),
                        ) {
                            Text(text, fontSize = 13.sp, color = TextPrimaryLight, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisionActionCard(
    modifier: Modifier,
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (selected) AccentOrange else TextPrimaryLight
    Box(
        modifier = modifier
            .background(if (selected) Color(0xFFFFF0E7) else LightSurface, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) AccentOrange.copy(alpha = 0.65f) else Color(0xFFE8E1D7), RoundedCornerShape(14.dp))
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
            color = if (enabled) accent else TextMutedLight.copy(alpha = 0.6f),
        )
    }
}

private suspend fun captureJpeg(capture: ImageCapture, context: android.content.Context): Result<ByteArray> =
    suspendCancellableCoroutine { continuation ->
        try {
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val bytes = imageProxyToJpegBytes(image)
                            if (continuation.isActive) continuation.resume(Result.success(bytes))
                        } catch (error: Throwable) {
                            if (continuation.isActive) continuation.resume(Result.failure(error))
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resume(Result.failure(exception))
                    }
                },
            )
        } catch (error: Throwable) {
            if (continuation.isActive) continuation.resume(Result.failure(error))
        }
    }

/** Avoid reading the image plane after closing CameraX's proxy. */
private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
    require(image.format == android.graphics.ImageFormat.JPEG) {
        "Format gambar kamera tidak didukung. Coba lagi di perangkat ini."
    }
    val buffer = image.planes[0].buffer.duplicate()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    require(bytes.isNotEmpty()) { "Frame kamera kosong." }
    return bytes
}

/** Bounds upload size and resolution before a frame leaves the device. */
private fun optimizeJpeg(input: ByteArray): ByteArray {
    require(input.isNotEmpty()) { "Frame kamera kosong." }
    val maxDimension = 1280
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
    val sourceMax = maxOf(bounds.outWidth, bounds.outHeight)
    if (input.size <= 850_000 && sourceMax in 1..maxDimension) return input

    // Decode a sampled bitmap instead of loading a high-resolution phone photo at full size.
    val options = BitmapFactory.Options()
    var sampleSize = 1
    while (sourceMax > 0 && sourceMax / sampleSize > maxDimension * 2) sampleSize *= 2
    options.inSampleSize = sampleSize
    val bitmap = BitmapFactory.decodeByteArray(input, 0, input.size, options)
        ?: return input.takeIf { it.size <= 2_000_000 }
        ?: error("Gambar kamera terlalu besar untuk diproses dengan aman.")
    return try {
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat())
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        val encoded = ByteArrayOutputStream().use { output ->
            check(resized.compress(Bitmap.CompressFormat.JPEG, 78, output)) { "Gambar kamera gagal dikompresi." }
            output.toByteArray()
        }
        if (resized !== bitmap) resized.recycle()
        require(encoded.size <= 2_000_000) { "Gambar hasil kompresi masih terlalu besar." }
        encoded
    } finally {
        bitmap.recycle()
    }
}

private const val LIVE_INTERVAL_MS = 3_000L

