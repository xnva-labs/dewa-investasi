package com.xnvalabs.smarteyex.ui.screens.face

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xnvalabs.smarteyex.data.face.FaceEngine
import com.xnvalabs.smarteyex.data.face.FaceOutcome
import com.xnvalabs.smarteyex.data.face.FaceRepository
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.vision.CaptureLedController
import com.xnvalabs.smarteyex.ui.theme.AccentOrange
import com.xnvalabs.smarteyex.ui.theme.LightBgWarm
import com.xnvalabs.smarteyex.ui.theme.LightSurface
import com.xnvalabs.smarteyex.ui.theme.TextMutedLight
import com.xnvalabs.smarteyex.ui.theme.TextPrimaryLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Data Wajah: deteksi, pendaftaran (dengan persetujuan), pengenalan, dan penghapusan.
 * Seluruh pemrosesan on-device lewat [FaceEngine]; tidak ada foto yang dikirim atau disimpan.
 */
@Composable
fun FaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings = PrivacyRepository.settings.value
    val processingAllowed = settings.faceRecognitionEnabled && settings.cameraEnabled
    val people = FaceRepository.people.value

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    var modelInstalled by remember { mutableStateOf<Boolean?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var disposed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        modelInstalled = withContext(Dispatchers.Default) { FaceEngine.isModelInstalled() }
    }
    LaunchedEffect(processingAllowed, hasPermission) {
        if (!processingAllowed) {
            cameraProvider?.unbindAll()
            CaptureLedController.setActive(false)
        } else if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            cameraProvider?.unbindAll()
            CaptureLedController.setActive(false)
        }
    }

    fun capture(onJpeg: (ByteArray, Int) -> Unit) {
        val capture = imageCapture ?: return
        busy = true
        resultText = null
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    onJpeg(bytes, rotation)
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    resultText = "Gagal mengambil gambar. Coba lagi."
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(LightBgWarm)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("‹", fontSize = 26.sp, color = TextPrimaryLight, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
                Text("Data Wajah", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Pemrosesan wajah berjalan di perangkat. Foto tidak dikirim dan tidak disimpan; yang tersimpan hanya vektor wajah terenkripsi, dan hanya untuk orang yang setuju didaftarkan.",
                fontSize = 12.sp,
                color = TextMutedLight,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                when (modelInstalled) {
                    null -> "Model embedding: memeriksa..."
                    true -> "Model embedding: terpasang."
                    false -> "Model embedding: BELUM terpasang (${FaceEngine.MODEL_FILE} di assets). Deteksi wajah jalan, pengenalan nama belum."
                },
                fontSize = 12.sp,
                color = if (modelInstalled == false) AccentOrange else TextMutedLight,
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                !settings.faceRecognitionEnabled -> Notice("Face Recognition sedang OFF — nyalakan di Privacy Control. Mematikannya juga menghapus semua data wajah.")
                !settings.cameraEnabled -> Notice("Camera sedang OFF — nyalakan di Privacy Control.")
                !hasPermission -> Notice("Menunggu izin kamera dari sistem Android...")
                else -> {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(LightSurface, RoundedCornerShape(16.dp))) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val future = ProcessCameraProvider.getInstance(ctx)
                                future.addListener({
                                    if (disposed || !processingAllowed || !hasPermission) return@addListener
                                    val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                                    cameraProvider = provider
                                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                    val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                                    imageCapture = capture
                                    runCatching {
                                        provider.unbindAll()
                                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                        CaptureLedController.setActive(true)
                                    }.onFailure { CaptureLedController.setActive(false) }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ActionButton(if (busy) "Memproses..." else "Kenali Wajah", enabled = !busy) {
                        capture { jpeg, rotation ->
                            scope.launch {
                                val result = FaceEngine.identify(jpeg, rotation)
                                busy = false
                                resultText = result.fold({ describe(it) }, { it.message ?: "Gagal memproses wajah." })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("DAFTARKAN ORANG BARU", fontSize = 11.sp, color = TextMutedLight, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(12.dp)).padding(14.dp)) {
                        if (nameInput.isEmpty()) Text("Nama (mis. Budi)", fontSize = 14.sp, color = TextMutedLight)
                        BasicTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it.take(FaceRepository.MAX_NAME_LENGTH) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimaryLight),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it })
                        Text(
                            "Orang ini sudah setuju wajahnya didaftarkan dan boleh dihapus kapan saja.",
                            fontSize = 12.sp,
                            color = TextPrimaryLight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ActionButton("Daftarkan dari Kamera", enabled = !busy && nameInput.isNotBlank() && consent) {
                        val name = nameInput
                        capture { jpeg, rotation ->
                            scope.launch {
                                val result = FaceEngine.enroll(name, consent, jpeg, rotation)
                                busy = false
                                resultText = result.fold(
                                    {
                                        nameInput = ""
                                        consent = false
                                        "${it.name} terdaftar (${it.sampleCount} sampel)."
                                    },
                                    { it.message ?: "Gagal mendaftarkan wajah." },
                                )
                            }
                        }
                    }
                }
            }

            resultText?.let { text ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(16.dp)) {
                    Text(text, fontSize = 14.sp, color = TextPrimaryLight)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("ORANG TERDAFTAR (${people.size}/${FaceRepository.MAX_PEOPLE})", fontSize = 11.sp, color = TextMutedLight, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (people.isEmpty()) {
                Text("Belum ada.", fontSize = 13.sp, color = TextMutedLight)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(12.dp)).padding(14.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(person.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                                Text("${person.sampleCount} sampel", fontSize = 11.sp, color = TextMutedLight)
                            }
                            Text(
                                "Hapus",
                                fontSize = 13.sp,
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    resultText = if (FaceRepository.delete(person.id)) "${person.name} dihapus." else "Gagal menghapus ${person.name}."
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                ActionButton("Hapus Semua Data Wajah", enabled = true) {
                    resultText = if (FaceRepository.deleteAll()) "Semua data wajah dihapus." else "Gagal menghapus sebagian data wajah. Coba lagi."
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, fontSize = 13.sp, color = AccentOrange)
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LightSurface, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (enabled) AccentOrange else TextMutedLight)
    }
}

private fun describe(outcome: FaceOutcome): String {
    if (outcome.faceCount == 0) return "Tidak ada wajah terdeteksi."
    val lines = if (outcome.identities.isEmpty()) {
        listOf("${outcome.faceCount} wajah terdeteksi.")
    } else {
        outcome.identities.mapIndexed { index, id ->
            val score = "%.2f".format(id.score)
            val label = if (id.name != null) "${id.name} (skor $score)" else "Tidak dikenal"
            "Wajah ${index + 1}: $label"
        }
    }
    return (lines + listOfNotNull(outcome.note)).joinToString("\n")
}
