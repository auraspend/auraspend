package com.awbuilds.auraspend.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * The database passphrase, derived from an Android Keystore HMAC key.
 *
 * Nothing stores the passphrase itself: the Keystore holds a non-exportable
 * HMAC-SHA256 key, and the passphrase is the HMAC over a fixed seed. The same
 * key + seed always yield the same bytes, so the encrypted database opens
 * across process restarts, while a copy of the app's files alone (backup,
 * adb pull) never contains it — the Keystore material lives behind the secure
 * hardware layer, not on disk next to the database.
 *
 * The HMAC output is base64-encoded before use so the passphrase is printable
 * ASCII: SQLCipher's `ATTACH ... KEY ?` takes the passphrase as a bound string,
 * and random binary bytes cannot survive that round-trip intact.
 */
object DbPassphrase {

    private const val KEY_ALIAS = "auraspend_db_passphrase"
    private const val SEED = "auraspend-db-v1"

    fun get(): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        val derived = mac.doFinal(SEED.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(derived, Base64.NO_WRAP).toByteArray(Charsets.UTF_8)
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build()
        )
        return generator.generateKey()
    }
}
