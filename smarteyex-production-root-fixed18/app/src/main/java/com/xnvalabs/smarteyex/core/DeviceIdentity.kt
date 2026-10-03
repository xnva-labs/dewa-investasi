package com.xnvalabs.smarteyex.core

import java.util.UUID

/**
 * ID acak per instalasi untuk pembatasan laju (rate limit) dan pengendalian biaya di backend XNAI.
 * Bukan identitas pribadi: tidak berasal dari IMEI, nomor telepon, atau akun, dan berubah bila aplikasi
 * di-install ulang atau data dihapus. Bukan pengganti autentikasi; itu tahap Play Integrity.
 */
object DeviceIdentity {
    private const val KEY_ID = "device.install_id"
    private val lock = Any()
    private var cached: String? = null

    fun id(): String = synchronized(lock) {
        cached?.let { return it }
        val stored = runCatching { SecureStorage.getString(KEY_ID) }.getOrNull()
        val valid = stored?.takeIf { isValid(it) }
        val value = valid ?: UUID.randomUUID().toString().also { fresh ->
            // If persisting fails, the ID still works for this process; it is simply regenerated next launch.
            runCatching { SecureStorage.putStringSync(KEY_ID, fresh) }
        }
        cached = value
        value
    }

    /** Drop the cached and stored ID (used by "reset all data"). */
    fun reset() = synchronized(lock) {
        cached = null
        runCatching { SecureStorage.removeSync(KEY_ID) }
        Unit
    }

    internal fun isValid(candidate: String): Boolean =
        candidate.length in 16..64 && candidate.all { it.isLetterOrDigit() || it == '-' }
}
