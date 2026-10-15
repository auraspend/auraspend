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

    /**
     * A fixed passphrase, and no open-helper factory, for JVM tests. The
     * AndroidKeyStore provider and `libsqlcipher.so` both do not exist on the
     * test host, so the real app's database wiring throws there. Room falls back
     * to framework SQLite under Robolectric, which is fine for tests that are
     * about the queue, the notifications or the repository layer — the
     * encryption paths themselves are covered on a device by
     * `SqlCipherDeviceTest`.
     */
    override val databasePassphrase: ByteArray =
        "auraspend-robolectric-test-passphrase".toByteArray()

    override val databaseOpenHelperFactory: androidx.sqlite.db.SupportSQLiteOpenHelper.Factory?
        get() = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.ERROR)
            .build()
}
