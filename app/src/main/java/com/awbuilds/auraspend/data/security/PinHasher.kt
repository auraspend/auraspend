package com.awbuilds.auraspend.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the app-lock PIN.
 *
 * The PIN gates the UI only — the durable at-rest protection is the database
 * encryption layer — so the prefs must not hold anything reversible: a fresh
 * 16-byte salt per PIN, 120k iterations of PBKDF2-HMAC-SHA256, compared in
 * constant time.
 */
object PinHasher {

    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32

    fun newSalt(): String = ByteArray(SALT_BYTES)
        .also { SecureRandom().nextBytes(it) }
        .let { Base64.getEncoder().encodeToString(it) }

    fun hash(pin: String, saltBase64: String): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, HASH_BYTES * 8)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return Base64.getEncoder().encodeToString(key.encoded)
    }

    fun verify(pin: String, saltBase64: String, expectedBase64: String): Boolean {
        val expected = runCatching { Base64.getDecoder().decode(expectedBase64) }.getOrNull() ?: return false
        val actual = runCatching { Base64.getDecoder().decode(hash(pin, saltBase64)) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, actual)
    }
}
