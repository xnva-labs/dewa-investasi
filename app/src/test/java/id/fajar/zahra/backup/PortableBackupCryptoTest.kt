package id.fajar.zahra.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableBackupCryptoTest {
    @Test
    fun roundTripPreservesBytes() {
        val data = "zahra-backup-test-${System.nanoTime()}".toByteArray()
        val password = "secure-pass-123".toCharArray()
        val encrypted = PortableBackupCrypto.encrypt(data, password)
        val decrypted = PortableBackupCrypto.decrypt(encrypted, password)
        assertArrayEquals(data, decrypted)
    }

    @Test
    fun wrongPasswordFailsAuthentication() {
        val encrypted = PortableBackupCrypto.encrypt("secret".toByteArray(), "correct-pass".toCharArray())
        assertThrows(Exception::class.java) {
            PortableBackupCrypto.decrypt(encrypted, "wrong-pass".toCharArray())
        }
    }

    @Test
    fun tamperingFailsAuthentication() {
        val encrypted = PortableBackupCrypto.encrypt("secret".toByteArray(), "correct-pass".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted[encrypted.lastIndex].toInt() xor 0x01).toByte()
        assertThrows(Exception::class.java) {
            PortableBackupCrypto.decrypt(encrypted, "correct-pass".toCharArray())
        }
    }
}
