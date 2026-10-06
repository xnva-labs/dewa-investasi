package id.fajar.zahra.bridge

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import android.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Per-install HMAC key backed by Android Keystore. The raw key never leaves Keystore.
 * It protects the local Android <-> Godot bridge and the integrity tag for game saves.
 */
object BridgeSecurity {
    const val PROTOCOL_VERSION = 2
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "zahra_bridge_hmac_v2"
    private const val MAC_ALGORITHM = "HmacSHA256"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setKeySize(256)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }

    fun sign(payload: String): String {
        require(payload.length <= 512_000) { "Bridge payload terlalu besar" }
        return mac(payload)
    }

    fun verify(payload: String, signature: String): Boolean {
        if (payload.length > 512_000 || signature.isBlank()) return false
        return runCatching {
            val expected = Base64.decode(signature, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            MessageDigest.isEqual(expected, macBytes(payload))
        }.getOrDefault(false)
    }

    fun eventMessage(protocol: Int, eventId: String, payload: String): String =
        "$protocol|$eventId|$payload"

    fun signEvent(protocol: Int, eventId: String, payload: String): String =
        sign(eventMessage(protocol, eventId, payload))

    fun verifyEvent(protocol: Int, eventId: String, payload: String, signature: String): Boolean =
        verify(eventMessage(protocol, eventId, payload), signature)

    private fun mac(payload: String): String =
        Base64.encodeToString(macBytes(payload), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun macBytes(payload: String): ByteArray {
        require(payload.length <= 512_000) { "Bridge payload terlalu besar" }
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(key())
        return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
    }
}
