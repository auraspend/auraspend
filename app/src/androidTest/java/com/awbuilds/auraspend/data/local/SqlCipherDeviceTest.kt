package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.awbuilds.auraspend.data.local.entities.TransactionEntity
import com.awbuilds.auraspend.data.security.DbPassphrase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device coverage for the database-at-rest encryption: the plaintext upgrade,
 * Room's encrypted factory, and the Keystore-derived passphrase.
 *
 * Run with:
 * ```
 * ./gradlew :app:connectedDevDebugAndroidTest
 * ```
 *
 * This class exists because none of it can run on the JVM: SQLCipher's API is a
 * JNI wrapper over `libsqlcipher.so`, and the JVM host cannot load an Android
 * native library (`UnsatisfiedLinkError`). The unit suite therefore covers only
 * the migration *policy* (`SqlCipherMigratorTest`); the recipe is proven here,
 * against the real library on a real device.
 *
 * The load-bearing assertions are the byte-level ones. "Nothing threw" would
 * pass even if the export silently wrote an empty shell over the user's data,
 * so every test checks what the file on disk actually is.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherDeviceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val passphrase = "auraspend-device-test-passphrase-0123456789".toByteArray()

    private fun freshFile(name: String): File =
        context.getDatabasePath(name).also {
            it.parentFile?.mkdirs()
            it.delete()
            File(it.path + "-wal").delete()
            File(it.path + "-shm").delete()
        }

    private fun File.isPlaintextOnDisk(): Boolean =
        readBytes().copyOf(16).toString(Charsets.ISO_8859_1).startsWith("SQLite format 3")

    @Test
    fun a_plaintext_database_migrates_and_stops_being_plaintext() {
        val name = "device_migrate.db"
        val file = freshFile(name)
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { plain ->
            plain.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY, note TEXT NOT NULL)")
            plain.execSQL("INSERT INTO t VALUES (1, 'a real row')")
        }
        assertTrue("the fixture must start as a plaintext database", file.isPlaintextOnDisk())

        SqlCipherMigrator.migrateIfNeeded(context, name, passphrase)

        assertFalse("the migrated file must not carry the plaintext header", file.isPlaintextOnDisk())
        // Readable with the key, and the data made the trip.
        SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READWRITE, null).use { db ->
            db.rawQuery("SELECT note FROM t WHERE id = 1", null).use { cursor ->
                assertTrue("the exported row is missing", cursor.moveToFirst())
                assertEquals("a real row", cursor.getString(0))
            }
        }
        // Unreadable without it.
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            }
            fail("an encrypted database must not open without the key")
        } catch (expected: Exception) {
            // Expected: "file is not a database".
        }
    }

    @Test
    fun room_creates_a_fresh_database_encrypted_and_reads_it_back() = runBlocking {
        val name = "device_room.db"
        val file = freshFile(name)

        val first = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .allowMainThreadQueries()
            .build()
        try {
            first.transactionDao().insertTransactionIfAbsent(
                TransactionEntity(
                    id = "cipher-1",
                    amount = 125.5,
                    categoryId = "cat_food",
                    note = "encrypted round-trip",
                    dateTimestamp = 1_757_000_000_000L,
                    type = "EXPENSE"
                )
            )
        } finally {
            first.close()
        }

        assertTrue("Room must have created the file", file.exists())
        assertFalse("a fresh Room database must be encrypted from the start", file.isPlaintextOnDisk())

        // Reopening through the same factory returns the row, which is the only
        // proof that the key derivation is stable across handles.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .allowMainThreadQueries()
            .build()
        try {
            val rows = db.transactionDao().getAllTransactions().first()
            assertEquals("the row did not survive an encrypted reopen", 1, rows.size)
            assertEquals("encrypted round-trip", rows.first().note)
        } finally {
            db.close()
        }
    }

    @Test
    fun the_keystore_passphrase_is_stable_across_calls() {
        val first = DbPassphrase.get()
        val second = DbPassphrase.get()
        assertTrue("a stable passphrase is the whole point", first.contentEquals(second))
        assertTrue("the passphrase must not be empty", first.isNotEmpty())
    }
}
