package com.awbuilds.auraspend.data.update

import android.content.Context
import android.content.SharedPreferences

/**
 * The update feature's slice of the app's one preferences file.
 *
 * Shared by the Play controller (the dismissal, the daily throttle), the
 * background worker (what has already been announced) and the notifier, so each
 * key is spelled in exactly one place. A key duplicated across two packages is
 * a key that silently stops matching.
 */
internal object UpdatePreferences {
    /** The installed version code at which the in-app banner was dismissed. */
    const val KEY_DISMISSED_VERSION = "update_dismissed_version"

    /** When the last silent check ran, for the daily throttle. */
    const val KEY_LAST_CHECK_MS = "update_last_check_ms"

    /** The version code already announced by a notification. */
    const val KEY_NOTIFIED_VERSION = "update_notified_version"

    /** The same file `CurrencyCatalog` and `MainActivity` read. */
    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences("auraspend_prefs", Context.MODE_PRIVATE)
}
