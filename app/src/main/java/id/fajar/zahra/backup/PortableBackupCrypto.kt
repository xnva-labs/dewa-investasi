package id.fajar.zahra.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable backup format. Unlike Android Keystore keys, this can be restored on another device when the password is known. */
object PortableBackupCrypto {
    private const val VERSION: Byte = 1
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val KEY_BITS = 256
    private const val ITERATIONS = 120_000

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(plain.isNotEmpty()) { "Data backup kosong" }
        require(password.size >= 8) { "Password backup minimal 8 karakter" }
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_SIZE).also(SecureRandom()::nextBytes)
        val key = derive(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plain)
        return ByteBuffer.allocate(1 + 1 + SALT_SIZE + IV_SIZE + ciphertext.size)
            .put(VERSION).put(SALT_SIZE.toByte()).put(salt).put(iv).put(ciphertext).array()
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        require(blob.size > 1 + 1 + SALT_SIZE + IV_SIZE + 16) { "Backup terlalu pendek" }
        require(blob[0] == VERSION) { "Versi backup tidak didukung" }
        val saltSize = blob[1].toInt() and 0xFF
        require(saltSize == SALT_SIZE) { "Format salt tidak valid" }
        val saltStart = 2
        val ivStart = saltStart + SALT_SIZE
        val payloadStart = ivStart + IV_SIZE
        val salt = blob.copyOfRange(saltStart, ivStart)
        val iv = blob.copyOfRange(ivStart, payloadStart)
        val payload = blob.copyOfRange(payloadStart, blob.size)
        val key = derive(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(payload)
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
