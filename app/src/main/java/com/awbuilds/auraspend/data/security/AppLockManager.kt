package com.awbuilds.auraspend.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The optional local app lock: a 4-digit PIN (only its salted hash is stored),
 * an optional biometric shortcut, and the background-grace policy.
 *
 * Forgetting the PIN always has a recovery path: the lock screen's "Forgot PIN"
 * wipes every user-owned row and the lock itself. There is deliberately no back
 * door, because a back door is a lock that opens.
 */
class AppLockManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("auraspend_app_lock", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit { putBoolean(KEY_ENABLED, value) } }

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_SALT)

    var useBiometric: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) { prefs.edit { putBoolean(KEY_BIOMETRIC, value) } }

    /** Epoch millis when the app last left the foreground; 0 = never (cold). */
    var lastBackgroundedAt: Long
        get() = prefs.getLong(KEY_LAST_BG, 0L)
        set(value) { prefs.edit { putLong(KEY_LAST_BG, value) } }

    fun setPin(pin: String) {
        require(pin.length == PIN_LENGTH) { "the app-lock PIN is $PIN_LENGTH digits" }
        val salt = PinHasher.newSalt()
        prefs.edit {
            putString(KEY_PIN_SALT, salt)
            putString(KEY_PIN_HASH, PinHasher.hash(pin, salt))
        }
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return PinHasher.verify(pin, salt, hash)
    }

    fun clearPin() {
        prefs.edit {
            remove(KEY_PIN_SALT)
            remove(KEY_PIN_HASH)
            remove(KEY_ENABLED)
            remove(KEY_BIOMETRIC)
        }
    }

    /**
     * True when the app must show the lock now: the lock is on, a PIN exists,
     * and either the process just (re)started or the app sat in the background
     * longer than [BACKGROUND_GRACE_MS]. The grace exists because returning
     * from a share sheet, the SAF picker or Drive sign-in is still the same
     * session and must not re-prompt.
     */
    fun shouldLock(now: Long = System.currentTimeMillis()): Boolean {
        if (!isEnabled || !hasPin) return false
        val last = lastBackgroundedAt
        return last == 0L || now - last > BACKGROUND_GRACE_MS
    }

    companion object {
        const val PIN_LENGTH = 4
        const val BACKGROUND_GRACE_MS = 60_000L

        private const val KEY_ENABLED = "enabled"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_LAST_BG = "last_backgrounded_at"
    }
}
