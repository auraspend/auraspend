package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.TestApplication
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The *policy* of the one-time plaintext -> encrypted upgrade, with the native
 * export replaced by a fake. The real recipe is `SqlCipherDeviceTest` (a
 * device test) because `libsqlcipher.so` cannot load on the JVM host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class SqlCipherMigratorTest {

    private val passphrase = "auraspend-test-passphrase-0123456789abcdef".toByteArray()

    private class FakeGateway(
        private val alreadyEncrypted: Boolean,
        private val exportBody: (File, File) -> Unit = { _, target -> target.writeText("encrypted-bytes") }
    ) : SqlCipherGateway {
        var probes = 0
        var exports = 0
        var lastTarget: File? = null

        override fun canOpenWithKey(file: File, passphrase: ByteArray): Boolean {
            probes++
            return alreadyEncrypted
        }

        override fun exportToEncrypted(source: File, target: File, passphrase: ByteArray) {
            exports++
            lastTarget = target
            exportBody(source, target)
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun databaseFile(name: String, contents: String = "SQLite format 3"): File =
        File(context().getDatabasePath(name).path).also {
            it.parentFile?.mkdirs()
            it.writeText(contents)
        }

    @Test
    fun `a fresh install has nothing to migrate`() {
        val gateway = FakeGateway(alreadyEncrypted = false)
        SqlCipherMigrator.migrateIfNeeded(context(), "never_created.db", passphrase, gateway)
        assertEquals("no file means no probe and no export", 0, gateway.probes)
        assertEquals(0, gateway.exports)
    }

    @Test
    fun `an encrypted database is left untouched`() {
        val file = databaseFile("already.db")
        val gateway = FakeGateway(alreadyEncrypted = true)
        SqlCipherMigrator.migrateIfNeeded(context(), "already.db", passphrase, gateway)
        assertEquals(1, gateway.probes)
        assertEquals("an encrypted database must never be re-exported", 0, gateway.exports)
        assertEquals("SQLite format 3", file.readText())
    }

    @Test
    fun `a plaintext database is exported and swapped in`() {
        databaseFile("plain.db")
        File(context().getDatabasePath("plain.db").path + "-wal").writeText("wal")
        File(context().getDatabasePath("plain.db").path + "-shm").writeText("shm")
        val gateway = FakeGateway(alreadyEncrypted = false)

        SqlCipherMigrator.migrateIfNeeded(context(), "plain.db", passphrase, gateway)

        assertEquals(1, gateway.probes)
        assertEquals(1, gateway.exports)
        val file = context().getDatabasePath("plain.db")
        assertEquals("encrypted-bytes", file.readText())
        assertEquals("plain.db.enc", gateway.lastTarget?.name)
        assertFalse("the stale -wal must not outlive the plaintext file", File(file.path + "-wal").exists())
        assertFalse(File(file.path + "-shm").exists())
    }

    @Test
    fun `a failed export keeps the original database`() {
        databaseFile("fragile.db", contents = "the user's records")
        val gateway = FakeGateway(alreadyEncrypted = false) { _, _ ->
            throw IllegalStateException("export blew up")
        }

        try {
            SqlCipherMigrator.migrateIfNeeded(context(), "fragile.db", passphrase, gateway)
            fail("a failed export must not be swallowed")
        } catch (expected: IllegalStateException) {
            // Expected — and the file is still there.
        }
        assertEquals(
            "the plaintext database must survive a failed export",
            "the user's records",
            context().getDatabasePath("fragile.db").readText()
        )
    }

    @Test
    fun `the original is only unlinked after the encrypted copy is in place`() {
        // The swap must never be "delete, then rename": a crash in between
        // would leave neither file where the app looks. The export writes a
        // target that exists *before* the original is touched, and the backup
        // outlives every step until the encrypted file has arrived.
        databaseFile("order.db", contents = "plaintext")
        val gateway = FakeGateway(alreadyEncrypted = false)

        SqlCipherMigrator.migrateIfNeeded(context(), "order.db", passphrase, gateway)

        val file = context().getDatabasePath("order.db")
        assertEquals("the encrypted copy must be in place", "encrypted-bytes", file.readText())
        assertFalse(
            "the plaintext backup must be gone once the swap completed",
            File(file.path + SqlCipherMigrator.PLAIN_BACKUP_SUFFIX).exists()
        )
        assertFalse("the intermediate .enc file must not survive", File(file.path + ".enc").exists())
    }

    @Test
    fun `a crash between the two renames is recovered on the next run`() {
        // Exactly the state a kill mid-swap leaves: the original moved aside,
        // the encrypted copy never arrived.
        databaseFile("interrupted.db", contents = "the user's records")
        val file = context().getDatabasePath("interrupted.db")
        check(file.renameTo(File(file.path + SqlCipherMigrator.PLAIN_BACKUP_SUFFIX)))

        val gateway = FakeGateway(alreadyEncrypted = false)
        SqlCipherMigrator.migrateIfNeeded(context(), "interrupted.db", passphrase, gateway)

        assertEquals(
            "the interrupted swap must be undone and the upgrade retried",
            "encrypted-bytes",
            file.readText()
        )
        assertEquals(1, gateway.exports)
        assertFalse(File(file.path + SqlCipherMigrator.PLAIN_BACKUP_SUFFIX).exists())
    }

    @Test
    fun `a stale backup is dropped when the encrypted database is already in place`() {
        // The other crash point: the swap finished, only the cleanup was lost.
        // The gateway reports the file as already encrypted — which is what it
        // is — so the export must not run a second time.
        val file = databaseFile("stale.db", contents = "encrypted-bytes")
        File(file.path + SqlCipherMigrator.PLAIN_BACKUP_SUFFIX).writeText("leftover plaintext")
        val gateway = FakeGateway(alreadyEncrypted = true)

        SqlCipherMigrator.migrateIfNeeded(context(), "stale.db", passphrase, gateway)

        assertEquals(0, gateway.exports)
        assertEquals("encrypted-bytes", file.readText())
        assertFalse(
            "a stale backup next to a live database must not linger",
            File(file.path + SqlCipherMigrator.PLAIN_BACKUP_SUFFIX).exists()
        )
    }

    @Test
    fun `a swap that cannot complete restores the plaintext original`() {
        databaseFile("blocked.db", contents = "the user's records")
        // The export produced nothing at the expected target — the closest
        // in-process stand-in for "the encrypted copy never arrived". (A
        // directory renamed onto the path would not do: POSIX rename() moves a
        // directory just as happily as a file, which is how this test first
        // "failed" by succeeding.)
        val gateway = FakeGateway(alreadyEncrypted = false) { _, _ -> Unit }

        try {
            SqlCipherMigrator.migrateIfNeeded(context(), "blocked.db", passphrase, gateway)
            fail("a swap that cannot complete must fail loudly")
        } catch (expected: IllegalStateException) {
            assertTrue(
                "the failure must name the restore, not just the rename",
                expected.message?.contains("restored") == true
            )
        }
        assertEquals(
            "the user's records must be back where the app looks for them",
            "the user's records",
            context().getDatabasePath("blocked.db").readText()
        )
    }
}
