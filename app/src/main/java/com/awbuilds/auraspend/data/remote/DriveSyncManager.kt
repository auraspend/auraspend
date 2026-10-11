// GoogleSignIn / GoogleSignInOptions / GoogleSignInClient are deprecated in favour
// of Credential Manager. The migration is a deliberate, larger change with its own
// decision record — `docs/adr/0011-google-signin-pinned.md` — so the deprecation is
// triaged here rather than left as eleven warnings on the Drive path for the next
// reader to scroll past (AGENTS.md rule 8). Removing this suppression without
// migrating means the warnings come back, which is the intent.
@file:Suppress("DEPRECATION")

package com.awbuilds.auraspend.data.remote

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.awbuilds.auraspend.core.AuraLog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class DriveSyncManager(private val context: Context) {

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
        .requestEmail()
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    companion object {
        private const val BACKUP_FILE_NAME = "auraspend_backup.json"
        private const val TAG = "DriveSyncManager"

        /**
         * `GoogleSignInStatusCodes.SIGN_IN_CANCELLED`. Written out because the
         * status-code holder class is deprecated with the rest of this API and
         * referencing it would add another warning to the pile this file already
         * documents; the value is part of the wire contract and does not move.
         */
        private const val STATUS_SIGN_IN_CANCELLED = 12501
    }

    /**
     * Note on placement: the backup is written to the user's Drive root, not to
     * the hidden `appDataFolder`. That is deliberate and documented — the privacy
     * page states plainly that the backup is unencrypted JSON in the user's own
     * Drive, and `drive.file` only ever grants access to files this app created.
     * Moving it into `appDataFolder` would make it invisible to the user, which is
     * a privacy *and* copy change (and would need its own owner decision), not a
     * tidy-up.
     */
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    /**
     * True only when the sign-in actually succeeded.
     *
     * `getSignedInAccountFromIntent` returns a `Task`, and a `Task` is **never
     * null**: a cancelled, denied or failed sign-in produces a *failed* task, not
     * a thrown exception. The previous check — `val account = ...; account != null`
     * — was therefore always true (the compiler said so: "Condition is always
     * 'true'"), and it never observed the failure. A user who dismissed the Google
     * prompt was reported as signed in, and the app then failed closed somewhere
     * further down and told them "backup failed" or "no backup found" instead of
     * "sign-in failed" — the one authentication check on the egress path validated
     * nothing.
     */
    suspend fun handleSignInResult(data: Intent?): Boolean = withContext(Dispatchers.IO) {
        try {
            // Blocking on IO, which is the dispatcher this runs on: awaiting the
            // task is the only way to see the outcome. `await` raises on failure.
            Tasks.await(GoogleSignIn.getSignedInAccountFromIntent(data))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuraLog.e(TAG, "Drive sign-in failed${failureDetail(e)}", e)
            false
        }
    }

    /** The Play-services status code behind a failed task, when there is one. */
    private fun failureDetail(e: Exception): String {
        val api = (e.cause as? ApiException) ?: (e as? ApiException) ?: return ""
        return when (api.statusCode) {
            STATUS_SIGN_IN_CANCELLED -> " (cancelled by the user)"
            CommonStatusCodes.SIGN_IN_REQUIRED -> " (no account selected)"
            else -> " (status ${api.statusCode})"
        }
    }

    suspend fun backupLocalData(dataJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: return@withContext false

            val existingFileId = findBackupFile(drive)
            val content = com.google.api.client.http.ByteArrayContent(
                "application/json", dataJson.toByteArray(Charsets.UTF_8)
            )

            if (existingFileId != null) {
                drive.files().update(existingFileId, File(), content).execute()
                AuraLog.d(TAG, "Backup updated for file: $existingFileId")
            } else {
                val file = File().apply {
                    name = BACKUP_FILE_NAME
                    mimeType = "application/json"
                }
                drive.files().create(file, content).execute()
                AuraLog.d(TAG, "Backup created")
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuraLog.e(TAG, "Backup failed", e)
            false
        }
    }

    suspend fun restoreLocalData(): String? = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: return@withContext null
            val fileId = findBackupFile(drive) ?: return@withContext null

            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream)

            val json = outputStream.toString(Charsets.UTF_8.name())
            AuraLog.d(TAG, "Restore successful, ${json.length} chars")
            json
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuraLog.e(TAG, "Restore failed", e)
            null
        }
    }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = Account(account.email ?: return null, "com.google")
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("AuraSpend").build()
    }

    private fun findBackupFile(drive: Drive): String? {
        val result = drive.files().list()
            .setQ("name='$BACKUP_FILE_NAME' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        return result.files.firstOrNull()?.id
    }
}
