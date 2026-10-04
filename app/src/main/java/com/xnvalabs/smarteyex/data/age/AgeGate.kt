package com.xnvalabs.smarteyex.data.age

enum class AgeBand { UNKNOWN, CHILD, TEEN, ADULT }

/**
 * Logika umur murni (tanpa Android) dari TAHUN lahir saja.
 *
 * Karena hanya tahun yang diketahui, umur sebenarnya bisa `tahunSekarang - tahunLahir` atau satu tahun
 * lebih muda (ulang tahun belum lewat). Setiap batas diputuskan memakai kemungkinan umur TERMUDA, jadi
 * kelompok yang lebih protektif selalu menang saat ragu:
 *  - lahir <= tahunSekarang-19  -> pasti >= 18 -> ADULT
 *  - lahir <= tahunSekarang-14  -> pasti >= 13 -> TEEN (termasuk yang mungkin sudah 18)
 *  - lainnya                    -> mungkin < 13  -> CHILD
 * Konsekuensinya: anak yang baru berusia 13 atau 18 bisa ditahan di kelompok lebih rendah sampai tahun berikutnya.
 */
object AgeGate {
    const val MIN_AGE = 13
    const val ADULT_AGE = 18
    private const val MAX_YEARS_BACK = 100

    fun isPlausibleYear(birthYear: Int, currentYear: Int): Boolean =
        birthYear in (currentYear - MAX_YEARS_BACK)..currentYear

    fun bandFor(birthYear: Int, currentYear: Int): AgeBand = when {
        !isPlausibleYear(birthYear, currentYear) -> AgeBand.UNKNOWN
        birthYear <= currentYear - (ADULT_AGE + 1) -> AgeBand.ADULT
        birthYear <= currentYear - (MIN_AGE + 1) -> AgeBand.TEEN
        else -> AgeBand.CHILD
    }

    /** Nilai header X-Age-Band untuk backend. Hanya kelompok, tidak pernah tahun lahir. */
    fun headerValue(band: AgeBand): String = when (band) {
        AgeBand.ADULT -> "ADULT"
        AgeBand.TEEN -> "TEEN"
        AgeBand.CHILD -> "CHILD"
        AgeBand.UNKNOWN -> "TEEN" // belum jelas: pakai profil yang lebih protektif
    }
}

enum class AgeFeature {
    CLOUD_CHAT,
    CLOUD_TRANSLATE,
    CLOUD_VISION,
    FACE_RECOGNITION,
    NOTIFICATION_CONTENT,
    VOICE_PERSONALIZATION,
    SEND_MEMORY_CONTEXT,
}

/** Siapa boleh memakai fitur sensitif apa. Satu tempat, supaya mudah diaudit dan dites. */
object AgePolicy {
    fun allows(band: AgeBand, parentConsent: Boolean, feature: AgeFeature): Boolean = when (band) {
        AgeBand.ADULT -> true
        AgeBand.TEEN -> when (feature) {
            AgeFeature.CLOUD_CHAT, AgeFeature.CLOUD_TRANSLATE -> parentConsent
            else -> false
        }
        AgeBand.CHILD, AgeBand.UNKNOWN -> false
    }

    fun denialMessage(band: AgeBand, feature: AgeFeature): String = when {
        band == AgeBand.UNKNOWN -> "Isi tahun lahir dulu di Pengaturan Usia."
        band == AgeBand.CHILD -> "SmartEyeX untuk usia 13 tahun ke atas."
        band == AgeBand.TEEN && (feature == AgeFeature.CLOUD_CHAT || feature == AgeFeature.CLOUD_TRANSLATE) ->
            "Butuh persetujuan orang tua/wali. Buka Pengaturan Usia."
        else -> "Fitur ini tersedia untuk usia 18 tahun ke atas."
    }
}
