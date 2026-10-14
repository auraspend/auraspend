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
        // The direction matters, and the device test is what settled it.
        //
        // The SQLCipher CLI recipe attaches the *encrypted target* to a
        // plaintext connection. On Android that fails: an ATTACH from a
        // connection opened without a key cannot create the target file —
        // measured, all four spellings of it (literal and bound path, literal
        // and bound key) failed with SQLITE_CANTOPEN, and so did a plain
        // ATTACH with no key at all.
        //
        // So this goes the other way: SQLCipher opens (and creates) the
        // encrypted target with the key, then the *plaintext source* is
        // attached with an empty key — the documented "encrypt an existing
        // database" form — and `sqlcipher_export` copies it into `main`. The
        // attached source also gets SQLite's normal WAL recovery, so rows still
        // living in the original's `-wal` journal are read rather than lost.
        SQLiteDatabase.openOrCreateDatabase(target.path, passphrase, null, null).use { encrypted ->
            encrypted.rawExecSQL("ATTACH DATABASE ? AS plaintext KEY ''", source.path)
            encrypted.rawExecSQL("SELECT sqlcipher_export('main', 'plaintext')")
            encrypted.rawExecSQL("DETACH DATABASE plaintext")
        }
    }
}

/**
 * One-time in-place migration of a plaintext database to SQLCipher.
 *
 * Detection is the open itself: an encrypted file cannot be read without the
 * passphrase, and a plaintext file cannot be read *with* one — so the probe is
 * "can we read sqlite_master using the passphrase". If not, the file is
 * exported through the gateway and swapped in.
 *
 * The swap is **crash-safe**, which is the whole reason it is written as three
 * renames rather than "export, delete, rename". Deleting the original before
 * putting the new one in place leaves a window — a crash, a battery pull, the
 * process being killed — where neither file is where the app expects it and the
 * user's records exist only as an `auraspend_db.enc` the app never looks at.
 * Instead the plaintext is *renamed* to [PLAIN_BACKUP_SUFFIX], the encrypted
 * copy takes its place, and only then is the backup removed. Every crash point
 * leaves a readable database plus, at worst, a leftover backup — and
 * [recoverInterruptedSwap] puts things back if the process died mid-swap.
 *
 * A failed export throws with the original file exactly where it was: this
 * upgrade must never be the thing that loses the user's records.
 */
object SqlCipherMigrator {

    /** Suffix of the short-lived copy of the plaintext original. */
    const val PLAIN_BACKUP_SUFFIX = ".plain.bak"

    fun migrateIfNeeded(
        context: Context,
        dbName: String,
        passphrase: ByteArray,
        gateway: SqlCipherGateway = JniSqlCipherGateway
    ) {
        val file = context.getDatabasePath(dbName)
        recoverInterruptedSwap(file)
        // A fresh install has no file: the SupportOpenHelperFactory below Room
        // creates it encrypted from the start, so there is nothing to migrate.
        if (!file.exists()) return
        if (gateway.canOpenWithKey(file, passphrase)) return

        val encrypted = File(file.parentFile, "$dbName.enc")
        encrypted.delete()
        gateway.exportToEncrypted(file, encrypted, passphrase)

        // 1. Stale side files go first: they belong to the plaintext original,
        //    and the encrypted database must not inherit a journal.
        for (suffix in listOf("-wal", "-shm")) File(file.path + suffix).delete()

        // 2. Move the plaintext aside rather than deleting it. From here on, a
        //    crash leaves a readable database at the expected path plus a
        //    backup, instead of neither.
        val backup = File(file.path + PLAIN_BACKUP_SUFFIX)
        backup.delete()
        check(file.renameTo(backup)) { "could not move the plaintext database aside for the encryption upgrade" }

        // 3. Put the encrypted copy in place.
        if (!encrypted.renameTo(file)) {
            // Put the user's data back where the app will look for it, then fail
            // loudly — a half-finished upgrade is never a state to continue in.
            backup.renameTo(file)
            error("could not put the encrypted database in place; the plaintext original was restored")
        }

        // 4. Only now is the backup redundant.
        backup.delete()
    }

    /**
     * Undo the effects of a crash mid-swap, before anything else looks at the
     * database.
     *
     * Two states are possible after a kill: the plaintext was moved aside but
     * the encrypted copy never arrived (the file is missing, the backup holds
     * the records — restore it, and the next run migrates again), or the swap
     * completed and only the backup removal was missed (the encrypted file is
     * in place — drop the stale backup).
     */
    internal fun recoverInterruptedSwap(file: File) {
        val backup = File(file.path + PLAIN_BACKUP_SUFFIX)
        if (!backup.exists()) return
        if (file.exists()) {
            backup.delete()
        } else {
            backup.renameTo(file)
            for (suffix in listOf("-wal", "-shm")) File(file.path + suffix).delete()
        }
    }
}
