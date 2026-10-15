package com.awbuilds.auraspend.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.data.classification.DuplicateDetector
import com.awbuilds.auraspend.data.local.toDomain
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migration rehearsal harness: get a real dataset into a pre-encryption
 * build, fingerprint it, then fingerprint it again after the encryption upgrade
 * and compare.
 *
 * **Opt-in only.** These are not tests with expectations; they are four
 * commands, selected with `-e rehearsal <action>`:
 *
 * | action | what it does |
 * |---|---|
 * | `seed` | imports `<filesDir>/rehearsal-import.csv` through `CsvManager` (the same code the Settings import uses) |
 * | `fingerprint` | logs a SHA-256 fingerprint of every user-owned table |
 * | `export` | writes all transactions to `<filesDir>/rehearsal-export.csv` through `CsvManager` |
 * | `restore` | restores `<filesDir>/rehearsal-backup.json` (a v4 backup payload) through `BackupRestoreManager` — full fidelity, every table |
 * | `wipe` | deletes every user-owned row and re-seeds the default categories |
 *
 * Run one at a time:
 * ```
 * adb shell am instrument -w -e rehearsal fingerprint \
 *     com.awbuilds.auraspend.dev.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Each method returns `void` and does nothing unless its action is selected, so
 * a routine suite run skips all four.
 *
 * ## Why the fingerprint is trustworthy
 *
 * It hashes each table's rows after sorting, using the entities' own
 * `toString()` — deterministic, total, and identical between two builds that do
 * not change the entities. It is computed by the *same code* on both builds, so
 * the comparison is not "does my host-side model of the data agree" but "does
 * the app see the same rows before and after". A false alarm (an entity change
 * between the builds) is a nuisance; a false pass is impossible, which is the
 * direction that matters.
 *
 * The rehearsal exists because the owner's production install is Play-signed:
 * its database cannot be read from a workstation at all, so the only honest way
 * to migrate *their* rows before a release is to carry them over as a CSV
 * through the app's own export, and to prove afterwards that the app sees the
 * same rows it saw before.
 */
@RunWith(AndroidJUnit4::class)
class MigrationRehearsalTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app: AuraSpendApp get() = context.applicationContext as AuraSpendApp
    private val db get() = app.database

    private val action: String? get() = InstrumentationRegistry.getArguments().getString("rehearsal")

    private fun file(name: String) = File(context.filesDir, name)

    private fun requireAction(required: String) {
        Assume.assumeTrue("pass -e rehearsal $required to run this action", action == required)
    }

    private fun sha256(value: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun import_a_csv_snapshot() {
        requireAction(ACTION_SEED)
        runBlocking {
            val source = file(IMPORT_FILE)
            assertTrue("$IMPORT_FILE is missing from ${context.filesDir}", source.exists())

            val result = CsvManager.importFromCsv(context, Uri.fromFile(source))
            // The same ordering the Settings import uses: read what is already
            // there, drop rows that look like duplicates, then write the rest.
            val existing = db.transactionDao().getAllOnce().map { it.toDomain() }
            val fresh = result.imported.filter { incoming ->
                DuplicateDetector.detectDuplicates(incoming, existing).isEmpty()
            }
            if (fresh.isNotEmpty()) {
                app.transactionRepository.saveTransactions(fresh)
            }
            Log.i(
                TAG,
                "seed: parsed=${result.totalRows} imported=${result.imported.size} " +
                    "skippedRows=${result.skippedRows} written=${fresh.size}"
            )
        }
    }

    @Test
    fun fingerprint_the_database() {
        requireAction(ACTION_FINGERPRINT)
        runBlocking {
            val digest = MessageDigest.getInstance("SHA-256")
            val perTable = linkedMapOf(
                "transactions" to db.transactionDao().getAllOnce().map { it.toString() },
                "categories" to db.categoryDao().getAllOnce().map { it.toString() },
                "budgets" to db.budgetDao().getAllOnce().map { it.toString() },
                "subscriptions" to db.subscriptionDao().getAllOnce().map { it.toString() },
                "savings_goals" to db.savingsGoalDao().getAllOnce().map { it.toString() },
                "sms_messages" to db.smsMessageDao().getAllOnce().map { it.toString() },
                "classification_memory" to db.classificationMemoryDao().getAll().map { it.toString() },
                "unrecognized_sms" to db.unrecognizedSmsDao().observeAll().first().map { it.toString() }
            )

            var totalRows = 0
            perTable.forEach { (table, rows) ->
                val sorted = rows.sorted()
                totalRows += sorted.size
                val tableHash = sha256(sorted.joinToString("\n"))
                digest.update("$table:${sorted.size}:$tableHash\n".toByteArray())
                Log.i(TAG, "fingerprint $table rows=${sorted.size} sha256=$tableHash")
            }
            Log.i(TAG, "FINGERPRINT-TOTAL rows=$totalRows sha256=${hex(digest.digest())}")
        }
    }

    @Test
    fun export_a_csv_snapshot() {
        requireAction(ACTION_EXPORT)
        runBlocking {
            val target = file(EXPORT_FILE)
            val transactions = db.transactionDao().getAllOnce().map { it.toDomain() }
            val rows = CsvManager.exportToCsv(context, transactions, Uri.fromFile(target))
            Log.i(TAG, "export: rows=$rows path=${target.absolutePath} bytes=${target.length()}")
        }
    }

    @Test
    fun restore_a_backup_json() {
        requireAction(ACTION_RESTORE)
        runBlocking {
            val source = file(RESTORE_FILE)
            assertTrue("$RESTORE_FILE is missing from ${context.filesDir}", source.exists())

            // Full fidelity: this is the app's own v4 backup payload (the file
            // the Drive backup uploads), restored through the same manager the
            // Drive restore uses. It carries every table, including the SMS
            // queue and the budgets/subscriptions CSV cannot express.
            val backup = BackupSerializer.deserialize(source.readText())
            val summary = BackupRestoreManager(db).restore(backup)
            if (summary == null) {
                Log.e(TAG, "restore: FAILED — the database was left untouched")
            } else {
                Log.i(
                    TAG,
                    "restore: transactions=${summary.transactions} categories=${summary.categories} " +
                        "budgets=${summary.budgets} subscriptions=${summary.subscriptions} " +
                        "savingsGoals=${summary.savingsGoals} smsMessages=${summary.smsMessages} " +
                        "classificationMemory=${summary.classificationMemory}"
                )
            }
        }
    }

    @Test
    fun wipe_every_user_row() {
        requireAction(ACTION_WIPE)
        runBlocking {
            db.clearAllTables()
            app.seedDefaultCategories()
            Log.i(TAG, "wipe: cleared every table and re-seeded the default categories")
        }
    }

    private companion object {
        const val TAG = "MIGRATION-REHEARSAL"
        const val ACTION_SEED = "seed"
        const val ACTION_FINGERPRINT = "fingerprint"
        const val ACTION_EXPORT = "export"
        const val ACTION_RESTORE = "restore"
        const val ACTION_WIPE = "wipe"
        const val IMPORT_FILE = "rehearsal-import.csv"
        const val EXPORT_FILE = "rehearsal-export.csv"
        const val RESTORE_FILE = "rehearsal-backup.json"
    }
}
