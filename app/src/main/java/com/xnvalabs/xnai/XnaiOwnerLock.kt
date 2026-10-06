package com.xnvalabs.xnai

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * XNAI obeys only its owner. The owner identity is fixed to "Fajar Muhaji" and proven with a passphrase
 * (PBKDF2, salted, stored through the Keystore-backed secret store). Until unlocked, XNAI answers nothing.
 */
object XnaiOwnerLock {
    const val OWNER_NAME = "Fajar Muhaji"
    private const val ITERATIONS = 120_000
    private const val MIN_PASSPHRASE = 6

    @Volatile private var unlocked = false

    fun isUnlocked(): Boolean = unlocked
    fun lock() { unlocked = false }

    fun isConfigured(context: Context): Boolean {
        val s = SecureSecretStore(context.applicationContext)
        return !s.get("owner_lock_hash").isNullOrBlank() && !s.get("owner_lock_salt").isNullOrBlank()
    }

    private fun nameMatches(name: String) = name.trim().replace(Regex("\\s+"), " ").equals(OWNER_NAME, ignoreCase = true)

    private fun hash(passphrase: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance(if (android.os.Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)).encoded

    /** Returns null on success, otherwise an error message. */
    fun setup(context: Context, name: String, passphrase: String, confirm: String): String? {
        if (!nameMatches(name)) return "Hanya $OWNER_NAME yang boleh menjadi pemilik XNAI."
        if (passphrase.length < MIN_PASSPHRASE) return "Kata sandi minimal $MIN_PASSPHRASE karakter."
        if (passphrase != confirm) return "Konfirmasi kata sandi tidak sama."
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val s = SecureSecretStore(context.applicationContext)
        s.put("owner_lock_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        s.put("owner_lock_hash", Base64.encodeToString(hash(passphrase, salt), Base64.NO_WRAP))
        unlocked = true
        return null
    }

    fun unlock(context: Context, name: String, passphrase: String): String? {
        if (!nameMatches(name)) return "XNAI hanya tunduk kepada $OWNER_NAME."
        val s = SecureSecretStore(context.applicationContext)
        val salt = s.get("owner_lock_salt")?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return "Kunci pemilik belum dibuat."
        val expected = s.get("owner_lock_hash")?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return "Kunci pemilik belum dibuat."
        return if (MessageDigest.isEqual(expected, hash(passphrase, salt))) { unlocked = true; null } else "Kata sandi salah."
    }
}

@Composable
fun OwnerGate(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(XnaiOwnerLock.isUnlocked()) }
    if (open) { content(); return }

    val configured = remember { XnaiOwnerLock.isConfigured(context) }
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("XNAI TERKUNCI", color = XNACyan, fontWeight = FontWeight.Bold)
        Text(
            if (configured) "XNAI hanya tunduk kepada ${XnaiOwnerLock.OWNER_NAME}. Masukkan identitas dan kata sandi."
            else "Buat kunci pemilik. Hanya ${XnaiOwnerLock.OWNER_NAME} yang boleh mengendalikan XNAI.",
            modifier = Modifier.padding(vertical = 12.dp)
        )
        OutlinedTextField(name, { name = it }, label = { Text("Nama pemilik") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            pass, { pass = it }, label = { Text("Kata sandi") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        if (!configured) {
            OutlinedTextField(
                confirm, { confirm = it }, label = { Text("Ulangi kata sandi") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
        }
        if (error.isNotBlank()) Text(error, color = XNACyan, modifier = Modifier.padding(top = 8.dp))
        Button(
            modifier = Modifier.padding(top = 12.dp),
            onClick = {
                val result = if (configured) XnaiOwnerLock.unlock(context, name, pass)
                else XnaiOwnerLock.setup(context, name, pass, confirm)
                if (result == null) open = true else error = result
            }
        ) { Text(if (configured) "Buka" else "Buat kunci") }
    }
}
