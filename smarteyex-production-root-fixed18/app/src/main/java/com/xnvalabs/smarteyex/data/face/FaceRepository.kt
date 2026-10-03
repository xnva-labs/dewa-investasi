package com.xnvalabs.smarteyex.data.face

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Data orang terdaftar yang aman ditampilkan di UI (tanpa embedding). */
data class FacePerson(val id: String, val name: String, val sampleCount: Int, val createdAt: Long)

/**
 * Penyimpanan template wajah terenkripsi (Android Keystore lewat [SecureStorage]).
 *
 * - Pendaftaran hanya jika Face Recognition ON di Privacy Control DAN pemanggil menyatakan
 *   persetujuan orang yang didaftarkan sudah didapat ([enroll] `consentConfirmed`).
 * - Yang disimpan hanya vektor embedding ter-normalisasi, bukan foto.
 * - State di memori hanya diubah setelah penulisan terenkripsi berhasil diverifikasi.
 * - Nama dan embedding tidak pernah ditulis ke log.
 */
object FaceRepository {
    const val MAX_PEOPLE = 20
    const val MAX_SAMPLES_PER_PERSON = 5
    const val MAX_NAME_LENGTH = 40

    private const val KEY_PROFILES = "face.profiles.v1"

    private class Record(
        val id: String,
        val name: String,
        val createdAt: Long,
        val consentAt: Long,
        val samples: List<FloatArray>,
    )

    val people = mutableStateOf<List<FacePerson>>(emptyList())

    private var records: List<Record> = emptyList()
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        records = load()
        publish()
        initialized = true
    }

    @Synchronized
    fun templates(): List<FaceTemplate> = records.map { FaceTemplate(it.id, it.name, it.samples) }

    @Synchronized
    fun hasProfiles(): Boolean = records.isNotEmpty()

    @Synchronized
    fun enroll(rawName: String, embedding: FloatArray, consentConfirmed: Boolean): Result<FacePerson> {
        if (!PrivacyRepository.settings.value.faceRecognitionEnabled) {
            return Result.failure(IllegalStateException("Face Recognition OFF — aktifkan di Privacy Control."))
        }
        if (!consentConfirmed) {
            return Result.failure(IllegalStateException("Konfirmasi dulu bahwa orang ini setuju wajahnya didaftarkan."))
        }
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) {
            return Result.failure(IllegalArgumentException("Nama harus 1–$MAX_NAME_LENGTH karakter."))
        }
        if (embedding.size < FaceMath.MIN_EMBEDDING_SIZE || embedding.size > FaceMath.MAX_EMBEDDING_SIZE || !embedding.all { it.isFinite() }) {
            return Result.failure(IllegalArgumentException("Embedding wajah tidak valid."))
        }
        val normalized = FaceMath.l2Normalize(embedding)
        val now = System.currentTimeMillis()
        val existing = records.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null && existing.samples.first().size != normalized.size) {
            return Result.failure(IllegalStateException("Ukuran embedding berbeda dari data lama. Hapus profil ini lalu daftarkan ulang."))
        }
        val next: List<Record> = if (existing != null) {
            if (existing.samples.size >= MAX_SAMPLES_PER_PERSON) {
                return Result.failure(IllegalStateException("Sampel untuk $name sudah penuh ($MAX_SAMPLES_PER_PERSON). Hapus profil untuk mendaftar ulang."))
            }
            records.map { if (it === existing) Record(it.id, it.name, it.createdAt, now, it.samples + listOf(normalized)) else it }
        } else {
            if (records.size >= MAX_PEOPLE) {
                return Result.failure(IllegalStateException("Batas $MAX_PEOPLE orang tercapai."))
            }
            records + Record(UUID.randomUUID().toString(), name, now, now, listOf(normalized))
        }
        if (!persist(next)) return Result.failure(IllegalStateException("Gagal menyimpan data wajah terenkripsi."))
        records = next
        publish()
        val saved = next.first { it.name.equals(name, ignoreCase = true) }
        return Result.success(FacePerson(saved.id, saved.name, saved.samples.size, saved.createdAt))
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val next = records.filterNot { it.id == id }
        if (next.size == records.size) return true
        val ok = if (next.isEmpty()) SecureStorage.removeSync(KEY_PROFILES) else persist(next)
        if (ok) {
            records = next
            publish()
        }
        return ok
    }

    /** Hapus seluruh template wajah. Mengembalikan true hanya bila penyimpanan benar-benar bersih. */
    @Synchronized
    fun deleteAll(): Boolean {
        val ok = SecureStorage.removeSync(KEY_PROFILES)
        if (ok) {
            records = emptyList()
            publish()
        }
        return ok
    }

    private fun publish() {
        people.value = records.map { FacePerson(it.id, it.name, it.samples.size, it.createdAt) }
    }

    private fun persist(list: List<Record>): Boolean {
        val root = JSONObject().put("v", 1)
        val array = JSONArray()
        list.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("createdAt", r.createdAt)
                    .put("consentAt", r.consentAt)
                    .put(
                        "samples",
                        JSONArray().also { arr -> r.samples.forEach { arr.put(Base64.encodeToString(FaceMath.encode(it), Base64.NO_WRAP)) } },
                    ),
            )
        }
        root.put("people", array)
        return SecureStorage.putStringSync(KEY_PROFILES, root.toString())
    }

    private fun load(): List<Record> {
        val raw = SecureStorage.getString(KEY_PROFILES) ?: return emptyList()
        return runCatching {
            val array = JSONObject(raw).optJSONArray("people") ?: JSONArray()
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val samples = o.optJSONArray("samples")?.let { arr ->
                    (0 until arr.length()).mapNotNull { j ->
                        runCatching { FaceMath.decode(Base64.decode(arr.getString(j), Base64.NO_WRAP)) }.getOrNull()
                    }
                }.orEmpty()
                val id = o.optString("id")
                val name = o.optString("name")
                if (id.isBlank() || name.isBlank() || samples.isEmpty()) null
                else Record(id, name, o.optLong("createdAt"), o.optLong("consentAt"), samples)
            }
        }.getOrElse {
            AppDiagnostics.warn("Face profiles unreadable; treating as empty", it)
            emptyList()
        }
    }
}
