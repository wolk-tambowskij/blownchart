package app.lawnchair.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the settings-lock PIN. The raw PIN is never persisted; only the
 * output of [hash] (iteration count, salt, and derived key, colon-separated) is stored.
 */
object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(pin, salt, ITERATIONS)
        return listOf(
            ITERATIONS.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(derived, Base64.NO_WRAP),
        ).joinToString(separator = ":")
    }

    fun verify(pin: String, storedHash: String): Boolean {
        // PBEKeySpec throws IllegalArgumentException for a non-null but zero-length password,
        // and no valid PIN is empty anyway (SettingsLockGate.MIN_PIN_LENGTH is 4).
        if (pin.isEmpty()) return false
        val parts = storedHash.split(":")
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = decodeBase64OrNull(parts[1]) ?: return false
        val expected = decodeBase64OrNull(parts[2]) ?: return false
        val actual = deriveKey(pin, salt, iterations)
        return constantTimeEquals(actual, expected)
    }

    private fun deriveKey(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private fun decodeBase64OrNull(value: String): ByteArray? = try {
        Base64.decode(value, Base64.NO_WRAP)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
