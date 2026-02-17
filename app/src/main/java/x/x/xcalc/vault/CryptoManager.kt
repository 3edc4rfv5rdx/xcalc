package x.x.xcalc.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoManager {

    private const val KEYSTORE_ALIAS = "xcalc_vault_master"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    fun encrypt(input: InputStream, output: OutputStream) {
        val plaintext = input.readBytes()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        output.write(cipher.iv)
        output.write(cipher.doFinal(plaintext))
    }

    fun decrypt(input: InputStream, output: OutputStream) {
        val iv = readExact(input, IV_SIZE)
        val ciphertext = input.readBytes()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE, iv))
        output.write(cipher.doFinal(ciphertext))
    }

    fun encryptBytes(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher.iv + cipher.doFinal(data)
    }

    fun decryptBytes(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, IV_SIZE)
        val ciphertext = data.copyOfRange(IV_SIZE, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val buf = ByteArray(size)
        var off = 0
        while (off < size) {
            val n = input.read(buf, off, size - off)
            if (n < 0) break
            off += n
        }
        return buf
    }
}
