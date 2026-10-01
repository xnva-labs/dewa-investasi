package com.xnvalabs.smarteyex.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted key/value store backed by Android Keystore.
 * Sensitive local SmartEyeX state is encrypted at rest and never stored as
 * plaintext SharedPreferences values.
 */
object SecureStorage {
    private const val PREFS_NAME = "smarteyex_secure_v2"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SmartEyeXLocalDataKey.v2"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private lateinit var prefs: SharedPreferences
    private var initialized = false
    private var cachedKey: SecretKey? = null
    private val lock = Any()

    fun init(context: Context) {
        synchronized(lock) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cachedKey = getOrCreateKey()
            initialized = true
        }
    }

    fun putString(key: String, value: String): Boolean {
        ensureInitialized()
        val encrypted = encrypt(value)
        prefs.edit().putString(key, encrypted).apply()
        return true
    }

    fun putStringSync(key: String, value: String): Boolean {
        ensureInitialized()
        val encrypted = encrypt(value)
        return prefs.edit().putString(key, encrypted).commit()
    }

    fun getString(key: String): String? {
        ensureInitialized()
        val stored = prefs.getString(key, null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    fun remove(key: String) {
        ensureInitialized()
        prefs.edit().remove(key).apply()
    }

    fun removeSync(key: String): Boolean {
        ensureInitialized()
        return prefs.edit().remove(key).commit()
    }

    fun clear() {
        ensureInitialized()
        prefs.edit().clear().apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        getString(key)?.toBooleanStrictOrNull() ?: default

    fun putBoolean(key: String, value: Boolean): Boolean = putString(key, value.toString())

    fun putBooleanSync(key: String, value: Boolean): Boolean = putStringSync(key, value.toString())

    fun getInt(key: String, default: Int = 0): Int =
        getString(key)?.toIntOrNull() ?: default

    fun putInt(key: String, value: Int): Boolean = putString(key, value.toString())

    private fun ensureInitialized() {
        check(initialized) { "SecureStorage.init(context) must be called first" }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun key(): SecretKey = cachedKey ?: getOrCreateKey().also { cachedKey = it }

    private fun encrypt(value: String): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split('.', limit = 2)
        require(parts.size == 2) { "Corrupt encrypted value" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }
}
