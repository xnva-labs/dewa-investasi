package com.xnvalabs.smarteyex.data.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Satu wajah hasil identifikasi. [name] null = tidak dikenal (atau model belum terpasang). */
data class FaceIdentity(val name: String?, val score: Float)

data class FaceOutcome(
    val faceCount: Int,
    val identities: List<FaceIdentity>,
    /** Catatan jujur untuk UI, mis. model embedding belum terpasang sehingga hanya deteksi. */
    val note: String?,
)

/**
 * Pipeline wajah on-device:
 *  1. Deteksi — ML Kit Face Detection (model ikut di APK). Klasifikasi (senyum/mata) dan landmark
 *     SENGAJA dimatikan: sistem tidak boleh menyimpulkan emosi atau atribut dari wajah.
 *  2. Embedding — model TFLite `face_embedding.tflite` di assets (mis. MobileFaceNet). File model
 *     TIDAK disertakan di repo ini. Tanpa model, mesin hanya mendeteksi dan melaporkan hal itu.
 *  3. Pencocokan — [FaceMath.bestMatch] terhadap template di [FaceRepository].
 *
 * Semua pemrosesan terjadi di perangkat; foto tidak dikirim ke mana pun dan tidak disimpan.
 */
object FaceEngine {
    const val MODEL_FILE = "face_embedding.tflite"
    private const val MAX_FACES = 5
    private const val MAX_DECODE_DIMENSION = 1280

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(0.12f)
                .build(),
        )
    }

    private var appContext: Context? = null
    private var interpreter: Interpreter? = null
    private var modelChecked = false
    private val modelLock = Any()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isModelInstalled(): Boolean = loadInterpreter() != null

    /** Deteksi + pencocokan. Mengembalikan failure bila consent OFF atau gambar tidak terbaca. */
    suspend fun identify(jpeg: ByteArray, rotationDegrees: Int = 0): Result<FaceOutcome> = withContext(Dispatchers.Default) {
        gate()?.let { return@withContext Result.failure(it) }
        runCatching {
            val bitmap = decode(jpeg, rotationDegrees)
            try {
                val faces = detect(bitmap).sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }.take(MAX_FACES)
                if (faces.isEmpty()) return@runCatching FaceOutcome(0, emptyList(), null)
                val model = loadInterpreter()
                if (model == null) {
                    return@runCatching FaceOutcome(faces.size, emptyList(), MODEL_MISSING_NOTE)
                }
                val gallery = FaceRepository.templates()
                val identities = faces.map { face ->
                    val embedding = embed(model, bitmap, face)
                    val match = FaceMath.bestMatch(embedding, gallery)
                    FaceIdentity(match.person?.name, match.score)
                }
                val note = if (gallery.isEmpty()) "Belum ada orang terdaftar." else null
                FaceOutcome(faces.size, identities, note)
            } finally {
                bitmap.recycle()
            }
        }.onFailure { AppDiagnostics.warn("Face identify failed", it) }
    }

    /** Daftarkan satu wajah. Gambar harus berisi tepat satu wajah supaya tidak salah orang. */
    suspend fun enroll(name: String, consentConfirmed: Boolean, jpeg: ByteArray, rotationDegrees: Int = 0): Result<FacePerson> =
        withContext(Dispatchers.Default) {
            gate()?.let { return@withContext Result.failure(it) }
            if (!consentConfirmed) return@withContext Result.failure(IllegalStateException("Konfirmasi persetujuan orang yang didaftarkan dulu."))
            val model = loadInterpreter()
                ?: return@withContext Result.failure(IllegalStateException(MODEL_MISSING_NOTE))
            runCatching {
                val bitmap = decode(jpeg, rotationDegrees)
                try {
                    val faces = detect(bitmap)
                    check(faces.isNotEmpty()) { "Tidak ada wajah terdeteksi. Coba cahaya lebih terang dan hadap kamera." }
                    check(faces.size == 1) { "Terdeteksi ${faces.size} wajah. Pendaftaran harus tepat satu orang di frame." }
                    FaceRepository.enroll(name, embed(model, bitmap, faces[0]), consentConfirmed).getOrThrow()
                } finally {
                    bitmap.recycle()
                }
            }.onFailure { AppDiagnostics.warn("Face enroll failed", it) }
        }

    private const val MODEL_MISSING_NOTE =
        "Model embedding ($MODEL_FILE) belum dipasang di assets — wajah hanya terdeteksi, belum bisa dikenali."

    private fun gate(): Throwable? {
        val settings = PrivacyRepository.settings.value
        if (!settings.faceRecognitionEnabled) return IllegalStateException("Face Recognition OFF — aktifkan di Privacy Control.")
        return null
    }

    private fun decode(jpeg: ByteArray, rotationDegrees: Int): Bitmap {
        require(jpeg.isNotEmpty()) { "Gambar kosong." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Gambar tidak bisa dibaca." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_DIMENSION) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
            ?: throw IllegalArgumentException("Gambar tidak bisa didekode.")
        val turn = ((rotationDegrees % 360) + 360) % 360
        if (turn == 0) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private suspend fun detect(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { cont ->
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun loadInterpreter(): Interpreter? = synchronized(modelLock) {
        interpreter?.let { return it }
        if (modelChecked) return null
        val ctx = appContext ?: return null
        modelChecked = true
        runCatching {
            val bytes = ctx.assets.open(MODEL_FILE).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes)
            buffer.rewind()
            Interpreter(buffer, Interpreter.Options().setNumThreads(2)).also { interpreter = it }
        }.onFailure { AppDiagnostics.info("Face embedding model not available") }.getOrNull()
    }

    /** Potong wajah (diluruskan oleh sudut roll dari detektor), skala ke input model, jalankan model. */
    private fun embed(model: Interpreter, source: Bitmap, face: Face): FloatArray {
        val shape = model.getInputTensor(0).shape() // [1, h, w, 3]
        require(shape.size == 4 && shape[3] == 3) { "Bentuk input model tidak didukung." }
        val h = shape[1]
        val w = shape[2]
        val box = face.boundingBox
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        val side = max(box.width(), box.height()) * 1.3f
        val crop = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            postTranslate(-cx, -cy)
            // Tanda sudut perlu diverifikasi di perangkat nyata; wajah tegak tidak terpengaruh.
            postRotate(face.headEulerAngleZ)
            postScale(w / side, h / side)
            postTranslate(w / 2f, h / 2f)
        }
        Canvas(crop).drawBitmap(source, m, Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(w * h)
        crop.getPixels(pixels, 0, w, 0, 0, w, h)
        crop.recycle()
        val input = ByteBuffer.allocateDirect(4 * w * h * 3).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            input.putFloat((((p shr 16) and 0xFF) - 127.5f) / 128f)
            input.putFloat((((p shr 8) and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p and 0xFF) - 127.5f) / 128f)
        }
        input.rewind()
        val outSize = model.getOutputTensor(0).shape().last()
        val output = Array(1) { FloatArray(outSize) }
        model.run(input, output)
        return FaceMath.l2Normalize(output[0])
    }
}
