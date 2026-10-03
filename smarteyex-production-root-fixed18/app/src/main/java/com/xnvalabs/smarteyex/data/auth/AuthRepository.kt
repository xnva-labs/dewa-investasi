package com.xnvalabs.smarteyex.data.auth

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Production-grade optional PIN gate. The PIN is never stored directly. */
object AuthRepository {
    private const val KEY_PIN_HASH = "auth.pin.hash"
    private const val KEY_PIN_SALT = "auth.pin.salt"
    private const val KEY_LEGACY_PIN_HASH = "pin_hash"
    private const val LOCKOUT_THRESHOLD = 5
    private const val LOCKOUT_MS = 30_000L

    private var initialized = false
    private var failedAttempts = 0
    private var lockedUntil = 0L

    var isUnlocked = mutableStateOf(false)
        private set
    val isPinSetState = mutableStateOf(false)

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        migrateLegacyPin(context)
        isPinSetState.value = isPinSet()
        isUnlocked.value = !isPinSetState.value
        initialized = true
    }

    fun isPinSet(): Boolean = (SecureStorage.getString(KEY_PIN_HASH) != null && SecureStorage.getString(KEY_PIN_SALT) != null) ||
        SecureStorage.getString("auth.legacy.sha256") != null

    fun setPin(pin: String): Boolean {
        if (!pin.matches(Regex("\\d{4}"))) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        val saved = runCatching {
            SecureStorage.putStringsSync(
                mapOf(KEY_PIN_SALT to salt.toHex(), KEY_PIN_HASH to hash.toHex()),
            )
        }.onFailure { AppDiagnostics.warn("PIN persistence failed", it) }.getOrDefault(false)
        if (!saved) return false
        failedAttempts = 0
        lockedUntil = 0L
        isPinSetState.value = true
        isUnlocked.value = true
        return true
    }

    fun clearPin(): Boolean {
        val hashRemoved = SecureStorage.removeSync(KEY_PIN_HASH)
        val saltRemoved = SecureStorage.removeSync(KEY_PIN_SALT)
        val legacyRemoved = SecureStorage.removeSync("auth.legacy.sha256")
        val ok = hashRemoved && saltRemoved && legacyRemoved
        if (ok) {
            failedAttempts = 0
            lockedUntil = 0L
            isPinSetState.value = false
            isUnlocked.value = true
        } else {
            AppDiagnostics.warn("PIN removal did not complete")
        }
        return ok
    }

    fun lock() {
        if (isPinSet()) isUnlocked.value = false
    }

    fun remainingLockoutSeconds(): Int =
        ((lockedUntil - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L + 0.999).toInt()

    fun verify(pin: String): Boolean {
        if (!isPinSet()) {
            isPinSetState.value = false
            isUnlocked.value = true
            return true
        }
        if (System.currentTimeMillis() < lockedUntil) return false
        if (!pin.matches(Regex("\\d{4}"))) {
            failedAttempts++
            if (failedAttempts >= LOCKOUT_THRESHOLD) {
                lockedUntil = System.currentTimeMillis() + LOCKOUT_MS
                failedAttempts = 0
            }
            return false
        }

        val saltHex = SecureStorage.getString(KEY_PIN_SALT)
        val hashHex = SecureStorage.getString(KEY_PIN_HASH)
        if (saltHex == null || hashHex == null) return verifyLegacyAndMigrate(pin)
        val expected = runCatching { hashHex.hexToBytes() }.getOrNull() ?: return false
        val actual = runCatching { derive(pin, saltHex.hexToBytes()) }.getOrNull() ?: return false
        val ok = MessageDigest.isEqual(actual, expected)

        if (ok) {
            isPinSetState.value = true
            failedAttempts = 0
            lockedUntil = 0L
            isUnlocked.value = true
        } else {
            failedAttempts++
            if (failedAttempts >= LOCKOUT_THRESHOLD) {
                lockedUntil = System.currentTimeMillis() + LOCKOUT_MS
                failedAttempts = 0
            }
        }
        return ok
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun migrateLegacyPin(context: Context) {
        if (isPinSet()) return
        val legacy = context.applicationContext
            .getSharedPreferences("smarteyex_auth", Context.MODE_PRIVATE)
            .getString(KEY_LEGACY_PIN_HASH, null)
            ?: return
        // Legacy SHA-256 PINs remain verifiable while the user is migrated.
        // Store a derived PBKDF2 record after the first successful verification.
        if (!SecureStorage.putStringSync("auth.legacy.sha256", legacy)) return
        context.applicationContext
            .getSharedPreferences("smarteyex_auth", Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LEGACY_PIN_HASH)
            .apply()
    }

    fun verifyLegacyAndMigrate(pin: String): Boolean {
        val legacy = SecureStorage.getString("auth.legacy.sha256") ?: return false
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        val encoded = bytes.joinToString("") { "%02x".format(it) }
        val ok = MessageDigest.isEqual(encoded.toByteArray(), legacy.lowercase(Locale.ROOT).toByteArray())
        if (!ok) {
            failedAttempts++
            if (failedAttempts >= LOCKOUT_THRESHOLD) {
                lockedUntil = System.currentTimeMillis() + LOCKOUT_MS
                failedAttempts = 0
            }
            return false
        }
        if (!setPin(pin)) return false
        SecureStorage.removeSync("auth.legacy.sha256")
        return true
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
