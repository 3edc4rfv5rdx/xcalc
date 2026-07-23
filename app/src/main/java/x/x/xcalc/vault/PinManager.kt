package x.x.xcalc.vault

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "vault_pin_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_FAIL_COUNT = "fail_count"
        private const val KEY_COOLDOWN_UNTIL = "cooldown_until"
        private const val ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val MAX_FAILS = 3
        private const val COOLDOWN_MS = 30_000L
    }

    val hasPin: Boolean
        get() = prefs.contains(KEY_PIN_HASH)

    // Persisted so the cooldown survives leaving the vault and app restarts.
    val cooldownUntil: Long
        get() = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L)

    fun registerFailedAttempt() {
        val fails = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
        val editor = prefs.edit()
        if (fails >= MAX_FAILS) {
            editor.putInt(KEY_FAIL_COUNT, 0)
            editor.putLong(KEY_COOLDOWN_UNTIL, System.currentTimeMillis() + COOLDOWN_MS)
        } else {
            editor.putInt(KEY_FAIL_COUNT, fails)
        }
        editor.apply()
    }

    fun clearFailures() {
        prefs.edit()
            .remove(KEY_FAIL_COUNT)
            .remove(KEY_COOLDOWN_UNTIL)
            .apply()
    }

    fun setupPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash.toHex())
            .putString(KEY_PIN_SALT, salt.toHex())
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, null)?.fromHex() ?: return false
        val hash = hashPin(pin, salt)
        return hash.toHex() == storedHash
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
