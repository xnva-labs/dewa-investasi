package com.xnvalabs.smarteyex.data.age

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import java.util.Calendar

/**
 * Menyimpan TAHUN lahir (bukan tanggal) terenkripsi di perangkat. Backend hanya menerima kelompok umur.
 * Ini pernyataan diri pengguna, bukan verifikasi identitas.
 *
 * - Kelompok dihitung ulang setiap aplikasi dibuka, jadi pengguna "naik kelas" otomatis seiring waktu.
 * - Jika tahun yang dimasukkan masuk kelompok CHILD, entri ulang ditolak sampai pergantian tahun kalender,
 *   supaya layar ini tidak bisa dicoba berulang sampai lolos.
 * - Persetujuan orang tua/wali di sini adalah konfirmasi di perangkat, BUKAN verifikasi identitas orang tua.
 */
object AgeRepository {
    private const val KEY_YEAR = "age.birth_year"
    private const val KEY_CHILD_LOCK = "age.child_lock_year"
    private const val KEY_CONSENT = "age.parent_consent_at"

    val band = mutableStateOf(AgeBand.UNKNOWN)
    val parentConsent = mutableStateOf(false)

    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        reload(currentYear())
        initialized = true
    }

    private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    private fun reload(year: Int) {
        val lock = SecureStorage.getString(KEY_CHILD_LOCK)?.toIntOrNull()
        val stored = SecureStorage.getString(KEY_YEAR)?.toIntOrNull()
        band.value = when {
            lock != null && lock >= year -> AgeBand.CHILD
            stored != null -> AgeGate.bandFor(stored, year)
            else -> AgeBand.UNKNOWN
        }
        parentConsent.value = band.value == AgeBand.TEEN && SecureStorage.getString(KEY_CONSENT) != null
    }

    /** True bila pengguna masih harus melewati layar usia sebelum memakai app. */
    fun gateNeeded(): Boolean = band.value == AgeBand.UNKNOWN || band.value == AgeBand.CHILD

    fun headerValue(): String = AgeGate.headerValue(band.value)

    fun allows(feature: AgeFeature): Boolean = AgePolicy.allows(band.value, parentConsent.value, feature)

    fun denial(feature: AgeFeature): String = AgePolicy.denialMessage(band.value, feature)

    @Synchronized
    fun submitBirthYear(birthYear: Int): Result<AgeBand> {
        val year = currentYear()
        if (band.value == AgeBand.CHILD) {
            return Result.failure(IllegalStateException("SmartEyeX untuk usia 13 tahun ke atas."))
        }
        val result = AgeGate.bandFor(birthYear, year)
        if (result == AgeBand.UNKNOWN) return Result.failure(IllegalArgumentException("Tahun lahir tidak valid."))
        if (result == AgeBand.CHILD) {
            // Simpan hanya penanda kunci (tahun kalender), bukan tahun lahir yang dimasukkan.
            val locked = SecureStorage.putStringSync(KEY_CHILD_LOCK, year.toString())
            if (!locked) AppDiagnostics.warn("Age child lock could not be persisted")
            SecureStorage.removeSync(KEY_YEAR)
            SecureStorage.removeSync(KEY_CONSENT)
            band.value = AgeBand.CHILD
            parentConsent.value = false
            return Result.success(AgeBand.CHILD)
        }
        if (!SecureStorage.putStringSync(KEY_YEAR, birthYear.toString())) {
            return Result.failure(IllegalStateException("Tahun lahir belum tersimpan. Coba lagi."))
        }
        SecureStorage.removeSync(KEY_CHILD_LOCK)
        if (result != AgeBand.TEEN) SecureStorage.removeSync(KEY_CONSENT)
        band.value = result
        parentConsent.value = result == AgeBand.TEEN && SecureStorage.getString(KEY_CONSENT) != null
        return Result.success(result)
    }

    @Synchronized
    fun confirmParentConsent(): Boolean {
        if (band.value != AgeBand.TEEN) return false
        val saved = SecureStorage.putStringSync(KEY_CONSENT, System.currentTimeMillis().toString())
        if (saved) parentConsent.value = true
        return saved
    }

    @Synchronized
    fun revokeParentConsent(): Boolean {
        val removed = SecureStorage.removeSync(KEY_CONSENT)
        if (removed) parentConsent.value = false
        return removed
    }

    /**
     * Hapus tahun lahir dan persetujuan (untuk "hapus semua data"). Penanda kunci anak-anak sengaja dipertahankan
     * sampai pergantian tahun supaya reset tidak menjadi jalan pintas; isinya hanya angka tahun kalender.
     */
    @Synchronized
    fun clear(): Boolean {
        val ok = SecureStorage.removeSync(KEY_YEAR) && SecureStorage.removeSync(KEY_CONSENT)
        if (ok) {
            val lock = SecureStorage.getString(KEY_CHILD_LOCK)?.toIntOrNull()
            band.value = if (lock != null && lock >= currentYear()) AgeBand.CHILD else AgeBand.UNKNOWN
            parentConsent.value = false
        }
        return ok
    }
}
