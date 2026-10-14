package com.awbuilds.auraspend.data.local

import android.content.Context
import android.util.Log

/**
 * Loads SQLCipher's native library, before anything opens a database.
 *
 * This exists because it is easy to get wrong in a way no JVM test can see.
 * Disassembling `sqlcipher-android-4.19.1` shows **no class in the artifact
 * calls `System.loadLibrary`** — not `SupportOpenHelperFactory` (it only carries
 * a passphrase), not `SQLiteDatabase`, not `SQLiteConnection`. `libsqlcipher.so`
 * is packaged in the APK but nothing loads it, so the first database open throws
 *
 *     UnsatisfiedLinkError: No implementation found for
 *     net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen
 *
 * which on a real install is an app that cannot use its own database. Every JVM
 * test passes with the library missing entirely, because a `.so` cannot be
 * loaded on the host; the failure only appears on a device, where
 * `SqlCipherDeviceTest` found it.
 *
 * `System.loadLibrary` is idempotent per process, so the guard here is about
 * making the requirement visible at one call site rather than about avoiding a
 * second load.
 */
object SQLCipherLibrary {

    private const val TAG = "SQLCipherLibrary"

    /** The `jni/<abi>/libsqlcipher.so` the SQLCipher AAR ships. */
    private const val LIB_NAME = "sqlcipher"

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                System.loadLibrary(LIB_NAME)
            } catch (e: UnsatisfiedLinkError) {
                // Deliberately loud. There is no degraded mode here: the only
                // alternative to SQLCipher is an unencrypted database, and
                // silently opening one would undo this feature on exactly the
                // devices it is meant to protect.
                Log.e(TAG, "libsqlcipher.so could not be loaded; the database stays closed rather than falling back to plaintext", e)
                throw e
            }
            loaded = true
            Log.i(TAG, "libsqlcipher.so loaded")
        }
    }
}
