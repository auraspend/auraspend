package com.awbuilds.auraspend.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.awbuilds.auraspend.data.security.DbPassphrase
import com.awbuilds.auraspend.data.local.dao.*
import com.awbuilds.auraspend.data.local.entities.*

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        SubscriptionEntity::class,
        SavingsGoalEntity::class,
        SmsMessageEntity::class,
        ClassificationMemoryEntity::class,
        UnrecognizedSmsEntity::class
    ],
    version = 9,
    // Exported so a migration can be tested against a real previous schema
    // (androidx.room:room-testing's MigrationTestHelper) and so a schema change
    // is visible in review. The KSP `room.schemaLocation` argument that says
    // *where* to write is a build-file setting; without it Room logs
    // "Schema export directory was not provided…" and exports nothing.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun classificationMemoryDao(): ClassificationMemoryDao
    abstract fun unrecognizedSmsDao(): UnrecognizedSmsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v2 -> v3: add the `savings_goals` table. This is the only schema
         * difference between the two versions — `fe6615c` added the table and
         * bumped `version = 2` to `version = 3` in the same commit — and it is the
         * one migration the chain was missing.
         *
         * Why v2 matters: the first shipped release was 0.1.0 (`6f2202f`,
         * "Initial open-source release — AuraSpend v0.1.0", versionCode 1,
         * `version = 2`; the CHANGELOG records it as the first alpha). The chain
         * only starts at 3 because the first migration was written later, at
         * `277cb48` (version 5, which needed a 3 -> 4 -> 5 path);
         * `git log -S "version = 4"` over this file is empty, so v4 was never a
         * shipped schema either. No `Migration(1, …)` or `Migration(2, …)` ever
         * existed, so an install that only ever ran 0.1.0 had no path forward.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `savings_goals` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `targetAmount` REAL NOT NULL,
                        `currentAmount` REAL NOT NULL,
                        `deadline` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v3 -> v4: add the unique source SMS id column used to prevent a message being saved
         * twice (by both Smart Add and the background auto-detect worker). Existing rows are all
         * NULL for the new column, and SQLite treats NULLs as distinct in unique indexes, so a
         * freshly created index cannot collide with legacy data.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN sourceSmsId TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sourceSmsId " +
                        "ON transactions(sourceSmsId)"
                )
            }
        }

        /**
         * v4 -> v5: add the persistent SMS classification queue. Each bank message is stored once
         * (keyed on the provider's `_id`) and processed one-by-one in the background; ingestion is
         * idempotent so this table can be safely back-filled from the inbox.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sms_messages` (
                        `id` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `receivedAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `amount` REAL,
                        `type` TEXT,
                        `merchant` TEXT,
                        `categoryId` TEXT,
                        `isSubscription` INTEGER NOT NULL,
                        `confidence` REAL NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v5 -> v6: add the learned classification memory. Maps normalized merchant / note keys to
         * the category the user (or a confirmed auto-save) last used, so repeat transactions are
         * categorized instantly without an LLM call. Starts empty; safe to create on upgrade.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `classification_memory` (
                        `rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `memoryKey` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `type` TEXT,
                        `source` TEXT NOT NULL,
                        `hits` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_classification_memory_memoryKey " +
                        "ON classification_memory(memoryKey)"
                )
            }
        }

        /**
         * v6 -> v7: add query indices. Dashboard, Activity and Insights filter
         * transactions by date/category/type on every screen; without these the
         * table is scanned fully once data grows.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_dateTimestamp " +
                        "ON transactions(dateTimestamp)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_categoryId " +
                        "ON transactions(categoryId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_type_dateTimestamp " +
                        "ON transactions(type, dateTimestamp)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_budgets_categoryId " +
                        "ON budgets(categoryId)"
                )
            }
        }

        /**
         * v7 -> v8: add the unrecognized-SMS table. Bank messages that carry a
         * movement verb and an amount but that no parser could read are recorded
         * here instead of being dropped, so a transaction missing from the
         * user's history is visible and fileable rather than silently lost.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `unrecognized_sms` (
                        `id` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `receivedAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // A balance snapshot per transaction, not a separate accounts
                // table: the account ledger is derived from these rows, so the
                // backup keeps its existing shape and there is one copy of the
                // truth. Nullable because most messages state no balance.
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `balanceAfter` REAL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `instrumentLast4` TEXT")
                db.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `instrumentIsCard` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private const val DB_NAME = "auraspend_db"

        /**
         * Both the passphrase and the open-helper factory are parameters rather
         * than hidden Keystore calls: production passes SQLCipher with a
         * Keystore-derived key, and a JVM test — where neither the
         * AndroidKeyStore provider nor `libsqlcipher.so` exists — passes a
         * fixed key and `null` for the factory. A silent "encryption
         * unavailable, carry on in plaintext" fallback is precisely the failure
         * this signature exists to prevent, so every caller has to choose.
         */
        fun getInstance(
            context: Context,
            passphrase: ByteArray,
            openHelperFactory: SupportSQLiteOpenHelper.Factory?
        ): AppDatabase {
            val appContext = context.applicationContext
            // `SupportOpenHelperFactory` does NOT load SQLCipher's native
            // library — it only supplies a passphrase. Without this call the
            // first open throws
            //   UnsatisfiedLinkError: No implementation found for
            //   net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen
            // i.e. an app that dies on its first database access. It was found
            // by `SqlCipherDeviceTest` on an emulator, not by the JVM suite: no
            // JVM test can load an Android `.so`, so every one of them passes
            // with the library missing entirely.
            if (openHelperFactory != null) {
                SQLCipherLibrary.ensureLoaded(appContext)
            }
            // Encrypted at rest (SQLCipher): run the one-time plaintext upgrade
            // before any Room handle exists. A fresh install skips it — the
            // factory below creates the file encrypted from the start.
            SqlCipherMigrator.migrateIfNeeded(appContext, DB_NAME, passphrase)
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7, MIGRATION_7_8,
                    MIGRATION_8_9
                    )
                    // Deliberate, and deliberately narrow: a destructive recreate is
                    // allowed *only* from version 1, whose schema never shipped (see
                    // MIGRATION_2_3) and so cannot be migrated faithfully.
                    //
                    // This replaces `fallbackToDestructiveMigration(false)`, which
                    // reads as "off" but is not: in Room that call sets
                    // `requireMigration = false` (the deprecated no-argument form is
                    // literally `ReplaceWith("fallbackToDestructiveMigration(false)")`),
                    // so *any* missing path silently recreated the database instead of
                    // failing. Only v1 keeps that behaviour now; every shipped version
                    // has a real path, and a future gap fails loudly instead of
                    // deleting the user's rows.
                    .fallbackToDestructiveMigrationFrom(dropAllTables = false, 1)
                    // Downgrades keep the old, friendlier behaviour: a beta user
                    // installing the stable build again gets a working (recreated)
                    // database rather than a crash loop. Room would otherwise throw
                    // because no 8 -> 7 path exists.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                    // Write-ahead logging lets dashboard reads proceed while the
                    // SMS pipeline writes transactions — no lock contention.
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                if (openHelperFactory != null) {
                    builder.openHelperFactory(openHelperFactory)
                }
                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
