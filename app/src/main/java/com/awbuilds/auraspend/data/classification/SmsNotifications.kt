package com.awbuilds.auraspend.data.classification

import android.content.Context
import androidx.core.content.edit

/**
 * Whether the categorisation notification may be posted.
 *
 * The notification is useful during a drain and pure noise when muted; the
 * pipeline never depends on it, so muting changes only the shade. Default is
 * on — the user asked for the categorisation in the first place.
 */
object SmsNotifications {

    const val PREF_FILE = "auraspend_prefs"
    private const val KEY_ENABLED = "sms_notifications_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ENABLED, enabled) }
    }
}
