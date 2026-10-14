package com.awbuilds.auraspend.data.local

import android.content.Context
import android.database.SQLException
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * The native half of the encryption upgrade, behind a seam.
 *
 * SQLCipher's Java API is a JNI wrapper, so its recipe cannot run on the JVM
 * test host at all — `libsqlcipher.so` is an Android library and loading it
 * off-device throws `UnsatisfiedLinkError`. The policy around it (what to
 * migrate, when to leave a file alone, how to swap it in) is ordinary code and
 * *is* covered by the unit suite; the recipe itself is covered by
 * `SqlCipherDeviceTest` on a device, where the native library actually exists.
 */
interface SqlCipherGateway {

    /** True when [file] can be read using [passphrase] — i.e. it is already encrypted. */
    fun canOpenWithKey(file: File, passphrase: ByteArray): Boolean

    /** Copies the plaintext [source] into [target], encrypted with [passphrase]. */
    fun exportToEncrypted(source: File, target: File, passphrase: ByteArray)
}

/**
 * The production gateway — SQLCipher's own documented plaintext-to-encrypted
 * recipe, run against a connection with *no* key (no key = read this file as
 * plaintext) and an `ATTACH`ed target that has the key.
 */
object JniSqlCipherGateway : SqlCipherGateway {

    override fun canOpenWithKey(file: File, passphrase: ByteArray): Boolean = try {
        SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null).use { db ->
            db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
        }
        true
    } catch (e: SQLException) {
        // "file is not a database" — the file is plaintext (or foreign).
        false
    }

    override fun exportToEncrypted(source: File, target: File, passphrase: ByteArray) {
        SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE).use { plain ->
            // Rows can still live in the -wal journal; checkpoint first so the
            // export carries them instead of stranding them on the file we are
            // about to delete.
            plain.rawExecSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            plain.rawExecSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?",
                arrayOf(target.path, String(passphrase, Charsets.UTF_8))
            )
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plain.rawExecSQL("DETACH DATABASE encrypted")
        }
    }
}

/**
 * One-time in-place migration of a plaintext database to SQLCipher.
 *
 * Detection is the open itself: an encrypted file cannot be read without the
 * passphrase, and a plaintext file cannot be read *with* one — so the probe is
 * "can we read sqlite_master using the passphrase". If not, the file is
 * exported through the gateway and swapped into place. A failed export throws
 * with the original file still on disk: this upgrade must never be the thing
 * that loses the user's records.
 */
object SqlCipherMigrator {

    fun migrateIfNeeded(
        context: Context,
        dbName: String,
        passphrase: ByteArray,
        gateway: SqlCipherGateway = JniSqlCipherGateway
    ) {
        val file = context.getDatabasePath(dbName)
        // A fresh install has no file: the SupportOpenHelperFactory below Room
        // creates it encrypted from the start, so there is nothing to migrate.
        if (!file.exists()) return
        if (gateway.canOpenWithKey(file, passphrase)) return

        val encrypted = File(file.parentFile, "$dbName.enc")
        encrypted.delete()
        gateway.exportToEncrypted(file, encrypted, passphrase)
        for (suffix in listOf("-wal", "-shm")) File(file.path + suffix).delete()
        check(file.delete()) { "could not remove the plaintext database during the encryption upgrade" }
        check(encrypted.renameTo(file)) { "could not put the encrypted database in place" }
    }
}
