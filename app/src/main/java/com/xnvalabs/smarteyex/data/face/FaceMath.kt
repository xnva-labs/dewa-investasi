package com.xnvalabs.smarteyex.data.face

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Satu orang terdaftar beserta sampel embedding-nya (hanya dipakai di logika pencocokan). */
class FaceTemplate(val id: String, val name: String, val samples: List<FloatArray>)

/** Hasil pencocokan. [person] null berarti "tidak dikenal". */
data class FaceMatch(val person: FaceTemplate?, val score: Float, val runnerUpScore: Float)

/**
 * Matematika pencocokan wajah. Murni dan bisa dites tanpa Android.
 *
 * Aturan keputusan sengaja konservatif: orang dianggap dikenal hanya bila skor cosine tertinggi
 * >= [threshold] DAN selisihnya dengan orang terdekat berikutnya >= [margin]. Selain itu hasilnya
 * "tidak dikenal" — lebih baik menolak daripada salah menyebut nama.
 *
 * Nilai default threshold HARUS dikalibrasi pada model embedding yang benar-benar dipasang
 * (lihat firmware/README dan docs di FACE_RECOGNITION.md); angka di sini hanya titik awal.
 */
object FaceMath {
    const val DEFAULT_THRESHOLD = 0.60f
    const val DEFAULT_MARGIN = 0.05f
    const val MIN_EMBEDDING_SIZE = 32
    const val MAX_EMBEDDING_SIZE = 1024

    fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x.toDouble() * x.toDouble()
        val norm = sqrt(sum).toFloat()
        if (norm < 1e-8f) return v.copyOf()
        return FloatArray(v.size) { v[it] / norm }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return -1f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            na += a[i].toDouble() * a[i].toDouble()
            nb += b[i].toDouble() * b[i].toDouble()
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom < 1e-12) -1f else (dot / denom).toFloat()
    }

    fun bestMatch(
        query: FloatArray,
        people: List<FaceTemplate>,
        threshold: Float = DEFAULT_THRESHOLD,
        margin: Float = DEFAULT_MARGIN,
    ): FaceMatch {
        val scored = people
            .map { person -> person to (person.samples.maxOfOrNull { cosine(query, it) } ?: -1f) }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return FaceMatch(null, -1f, -1f)
        val runnerUp = scored.getOrNull(1)?.second ?: -1f
        val accepted = best.second >= threshold && (best.second - runnerUp) >= margin
        return FaceMatch(if (accepted) best.first else null, best.second, runnerUp)
    }

    fun encode(v: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (x in v) buffer.putFloat(x)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray? {
        if (bytes.isEmpty() || bytes.size % 4 != 0) return null
        val count = bytes.size / 4
        if (count < MIN_EMBEDDING_SIZE || count > MAX_EMBEDDING_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(count) { buffer.getFloat() }
        return if (out.all { it.isFinite() }) out else null
    }
}
