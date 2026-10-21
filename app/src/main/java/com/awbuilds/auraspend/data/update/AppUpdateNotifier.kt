package com.awbuilds.auraspend.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.awbuilds.auraspend.MainActivity
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.core.AuraLog

private const val TAG = "AppUpdateNotifier"

/**
 * The one update notification (ADR 0013, extended).
 *
 * It has its own channel on purpose. Riding `auto_classification` would couple
 * two unrelated preferences: a user who turns categorisation notifications off
 * would silently stop hearing about updates, and a user chasing an update would
 * have to turn categorisation back on to get one.
 *
 * This is the only update surface that reaches someone who never opens the app,
 * so it is also the one most likely to be a nuisance. [UpdatePolicy.background]
 * is what keeps it quiet — it waits days, fires once per release, and never
 * fires for a release that would open Play's blocking screen.
 */
class AppUpdateNotifier(context: Context) {
    private val appContext = context.applicationContext

    /** True when the OS will actually show a notification from this app. */
    fun canNotify(): Boolean = runCatching {
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }.getOrDefault(false)

    /**
     * Announce an available update. Returns true when it was posted, so the
     * caller only records "already told them" for a notification that existed.
     */
    fun notifyAvailable(): Boolean {
        // Inline rather than behind `canNotify`: lint's MissingPermission
        // detector does not follow method calls, and the alternative is a
        // baseline entry that would also silence a real future call.
        // `SmsProgressNotifier` spells out the same guard for the same reason.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!canNotify()) return false
        return runCatching {
            ensureChannel()
            val intent = Intent(appContext, MainActivity::class.java).apply {
                // Reuse the running task rather than stacking a second copy of
                // the app behind the shade.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(appContext.getString(R.string.update_notification_title))
                .setContentText(appContext.getString(R.string.update_notification_body))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(appContext.getString(R.string.update_notification_body))
                )
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
            true
        }.onFailure { error ->
            AuraLog.w(TAG, "Could not post the update notification", error)
        }.getOrDefault(false)
    }

    /** Drop the notification once the user has updated or opened the app. */
    fun cancel() {
        runCatching { NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID) }
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            // Localized: the channel name is user-visible in system settings.
            appContext.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.update_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 7001
    }
}
