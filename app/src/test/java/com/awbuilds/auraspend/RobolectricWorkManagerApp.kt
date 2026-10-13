package com.awbuilds.auraspend

import androidx.work.Configuration

/**
 * The real application, able to initialize WorkManager inside a Robolectric sandbox.
 *
 * [AuraSpendApp.onCreate] schedules the periodic classification pass, which calls
 * `WorkManager.getInstance`. Under Robolectric the `WorkManagerInitializer` content provider
 * is not registered, so that call throws `IllegalStateException` on a background thread — noise
 * that a test dispatcher turns into `UncaughtExceptionsBeforeTest` two tests later. Implementing
 * [Configuration.Provider] lets WorkManager initialize on demand instead of requiring the
 * provider, which is the documented fallback path.
 *
 * Only for JVM tests; production does not need it.
 */
class RobolectricWorkManagerApp : AuraSpendApp(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.ERROR)
            .build()
}
