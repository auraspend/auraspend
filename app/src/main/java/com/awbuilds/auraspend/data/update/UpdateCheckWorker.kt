package com.awbuilds.auraspend.data.update

import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.core.AuraLog
import java.util.concurrent.TimeUnit

/**
 * The background half of the update check.
 *
 * Every other surface in this feature needs the app open, which is exactly the
 * group a notification cannot help: someone who has not opened AuraSpend in a
 * week will not see Home's banner or Settings' row. This worker is the one path
 * that reaches them, so it is also the one most likely to become a nuisance —
 * [UpdatePolicy.background] is what keeps it to one announcement per release,
 * only after the update has waited days, and never for a release that would open
 * Play's blocking screen.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AuraSpendApp ?: return Result.success()
        val controller = app.appUpdateController
        // The fdroid build has no Play Core and its controller answers false, so
        // this worker is inert there even if a request outlives a flavour change.
        if (!controller.isAvailable) return Result.success()

        val availability = controller.checkNow() ?: return Result.success()
        val prefs = UpdatePreferences.of(applicationContext)
        val notified = prefs.getInt(UpdatePreferences.KEY_NOTIFIED_VERSION, 0)
            .takeIf { it > 0 }
        if (UpdatePolicy.background(availability, notified) != AppUpdatePrompt.Notify) {
            return Result.success()
        }

        if (AppUpdateNotifier(applicationContext).notifyAvailable()) {
            // Only record the announcement when one actually happened: a user
            // who has notifications off should still be told once they turn
            // them back on.
            availability.availableVersionCode?.let { code ->
                prefs.edit { putInt(UpdatePreferences.KEY_NOTIFIED_VERSION, code) }
            }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "UpdateCheck"
        private const val WORK_NAME = "update_check"

        /**
         * A daily check. WorkManager's floor is 15 minutes; a day is deliberate —
         * a newer version is not urgent enough to wake a device for, and the
         * in-app surfaces already cover anyone who opens the app.
         *
         * `UPDATE` rather than `KEEP` so a changed interval or constraint reaches
         * an install that already has the request queued.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiresBatteryNotLow(true).build()
                )
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }.onFailure { error ->
                AuraLog.w(TAG, "Could not schedule the update check", error)
            }
        }
    }
}
