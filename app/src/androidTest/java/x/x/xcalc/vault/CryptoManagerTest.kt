package x.x.xcalc.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class CryptoManagerTest {

    @Test
    fun streamEncryptDecryptRoundtrip() {
        val original = "Hello, Vault!".toByteArray()
        val encOut = ByteArrayOutputStream()
        CryptoManager.encrypt(ByteArrayInputStream(original), encOut)

        val encrypted = encOut.toByteArray()
        assertFalse(encrypted.contentEquals(original))

        val decOut = ByteArrayOutputStream()
        CryptoManager.decrypt(ByteArrayInputStream(encrypted), decOut)
        assertArrayEquals(original, decOut.toByteArray())
    }

    @Test
    fun bytesEncryptDecryptRoundtrip() {
        val original = "Secret data 12345".toByteArray()
        val encrypted = CryptoManager.encryptBytes(original)
        assertFalse(encrypted.contentEquals(original))
        val decrypted = CryptoManager.decryptBytes(encrypted)
        assertArrayEquals(original, decrypted)
    }

    @Test
    fun emptyData() {
        val original = ByteArray(0)
        val encrypted = CryptoManager.encryptBytes(original)
        val decrypted = CryptoManager.decryptBytes(encrypted)
        assertArrayEquals(original, decrypted)
    }

    @Test
    fun largeData() {
        val original = ByteArray(100_000) { (it % 256).toByte() }
        val encrypted = CryptoManager.encryptBytes(original)
        val decrypted = CryptoManager.decryptBytes(encrypted)
        assertArrayEquals(original, decrypted)
    }

    @Test
    fun unicodeData() {
        val original = "Привет мир! 你好世界 🎉🔐".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoManager.encryptBytes(original)
        val decrypted = CryptoManager.decryptBytes(encrypted)
        assertArrayEquals(original, decrypted)
        assertEquals("Привет мир! 你好世界 🎉🔐", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun streamEmptyData() {
        val original = ByteArray(0)
        val encOut = ByteArrayOutputStream()
        CryptoManager.encrypt(ByteArrayInputStream(original), encOut)
        val decOut = ByteArrayOutputStream()
        CryptoManager.decrypt(ByteArrayInputStream(encOut.toByteArray()), decOut)
        assertArrayEquals(original, decOut.toByteArray())
    }

    @Test
    fun differentEncryptionEachTime() {
        val original = "same data".toByteArray()
        val enc1 = CryptoManager.encryptBytes(original)
        val enc2 = CryptoManager.encryptBytes(original)
        // IV is random, so encrypted bytes should differ
        assertFalse(enc1.contentEquals(enc2))
        // But both should decrypt to the same original
        assertArrayEquals(original, CryptoManager.decryptBytes(enc1))
        assertArrayEquals(original, CryptoManager.decryptBytes(enc2))
    }
}
