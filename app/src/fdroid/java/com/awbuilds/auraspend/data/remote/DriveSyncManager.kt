package com.awbuilds.auraspend.data.remote

import android.content.Context
import android.content.Intent

/**
 * The `fdroid` flavor's stand-in for the Google Drive manager (ADR 0012).
 *
 * The F-Droid build carries no Google Play Services — its inclusion policy
 * forbids the non-free dependency — so Google Drive backup does not exist in
 * this build. `BuildConfig.DRIVE_BACKUP` is false here, and every UI entry
 * point (Settings → Data, the onboarding restore card) is hidden.
 *
 * The class keeps the real manager's shape so the shared application code
 * compiles against one type name in every flavor, and the methods fail closed:
 * `false`/`null` — "no backup", never a pretend success.
 */
class DriveSyncManager(@Suppress("UNUSED_PARAMETER") context: Context) {

    fun getSignInIntent(): Intent = Intent()

    fun isSignedIn(): Boolean = false

    suspend fun handleSignInResult(data: Intent?): Boolean = false

    suspend fun backupLocalData(dataJson: String): Boolean = false

    suspend fun restoreLocalData(): String? = null
}
