package com.awbuilds.auraspend.ui.recurring

import android.content.Context
import androidx.core.content.edit
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.core.boundaryOrNull
import org.json.JSONArray

/**
 * Services the user rejected with "Not a subscription" on the detected list.
 *
 * A dismissal is an opinion about a *suggestion*, not tracked data, so it lives
 * in preferences (a JSON array of normalized names) rather than the database —
 * no schema change for a display filter. A malformed value degrades to "nothing
 * dismissed", which can only re-show a suggestion, never hide one.
 */
internal object SuggestionDismissals {

    private const val PREFS = "auraspend_prefs"
    private const val KEY = "dismissed_subscription_suggestions"
    private const val TAG = "SuggestionDismissals"

    fun load(context: Context): Set<String> = parse(
        boundaryOrNull(TAG) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }
    )

    /** Records [name] as dismissed and returns the updated set. */
    fun dismiss(context: Context, name: String): Set<String> {
        val updated = load(context) + normalizedSuggestionName(name)
        boundaryOrNull(TAG) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { putString(KEY, serialize(updated)) }
        }
        return updated
    }

    internal fun parse(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
                .toSet()
        } catch (e: Exception) {
            AuraLog.w(TAG, "Dismissed-suggestion preferences were unreadable; re-showing them", e)
            emptySet()
        }
    }

    internal fun serialize(names: Set<String>): String {
        val array = JSONArray()
        names.sorted().forEach { array.put(it) }
        return array.toString()
    }
}
