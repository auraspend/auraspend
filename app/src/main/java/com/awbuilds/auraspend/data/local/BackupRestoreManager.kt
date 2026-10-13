package com.awbuilds.auraspend.data.local

import androidx.room.withTransaction
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.UnrecognizedSmsEntity
import com.awbuilds.auraspend.data.local.toEntity
import com.awbuilds.auraspend.data.privacy.SensitiveDataMasker
import com.awbuilds.auraspend.domain.model.Transaction

/**
 * Applies a Drive backup to the local database.
 *
 * Two properties the previous inline implementation in `NavGraph` did not have:
 *
 * 1. **Atomic.** Every table is cleared and re-inserted inside one Room
 *    transaction. Previously five independent DAO calls ran outside any
 *    transaction, so a process death mid-restore left the database half
 *    restored with no way back.
 * 2. **A true replace, not a merge.** Nothing was deleted before, so restoring
 *    an old backup on top of newer local data kept both. Worse, the SMS queue
 *    was re-inserted with `INSERT OR IGNORE`, so rows already present kept their
 *    *local* status and messages the backup still had pending were never retried.
 *
 * Transaction rows, SMS queue bodies and unrecognized-SMS bodies are re-masked
 * with [SensitiveDataMasker] on the way in, which keeps restore behind the same
 * privacy choke-point as every other write path. That matters most for a v1–v3
 * backup, which was produced before the masker existed: those payloads carry raw
 * message text, and because restore is a *replace* the unmasked rows would
 * otherwise overwrite the locally-masked ones and be re-uploaded in the next
 * backup.
 */
class BackupRestoreManager(private val database: AppDatabase) {

    private companion object {
        const val TAG = "BackupRestore"
    }

    /**
     * @return a summary of what was written, or null when the restore failed. A
     *   failure is never thrown at the UI: the database is left untouched.
     */
    suspend fun restore(backup: BackupData): RestoreSummary? = boundary(TAG, null) {
        database.withTransaction {
            database.transactionDao().clear()
            database.categoryDao().clear()
            database.budgetDao().clear()
            database.subscriptionDao().clear()
            database.savingsGoalDao().clear()
            database.smsMessageDao().clear()
            database.classificationMemoryDao().clear()
            database.unrecognizedSmsDao().clear()

            database.transactionDao().insertTransactions(
                backup.transactions.map { it.masked().toEntity() }
            )
            if (backup.categories.isNotEmpty()) {
                database.categoryDao().insertCategories(backup.categories.map { it.toEntity() })
            }
            backup.budgets.forEach { database.budgetDao().insertBudget(it.toEntity()) }
            backup.subscriptions.forEach { database.subscriptionDao().insertSubscription(it.toEntity()) }
            backup.savingsGoals.forEach { database.savingsGoalDao().insertSavingsGoal(it.toEntity()) }
            if (backup.smsMessages.isNotEmpty()) {
                // Safe now: the table was cleared above, so every row in the backup
                // lands with the status the backup recorded.
                database.smsMessageDao().insertAll(backup.smsMessages.map { it.masked() })
            }
            backup.classificationMemory.forEach { database.classificationMemoryDao().upsert(it) }
            if (backup.unrecognizedSms.isNotEmpty()) {
                database.unrecognizedSmsDao().insertAll(backup.unrecognizedSms.map { it.masked() })
            }

            AuraLog.i(
                TAG,
                "Restored ${backup.transactions.size} transactions, " +
                    "${backup.categories.size} categories, ${backup.smsMessages.size} SMS rows"
            )
            RestoreSummary(
                transactions = backup.transactions.size,
                categories = backup.categories.size,
                budgets = backup.budgets.size,
                subscriptions = backup.subscriptions.size,
                savingsGoals = backup.savingsGoals.size,
                smsMessages = backup.smsMessages.size,
                classificationMemory = backup.classificationMemory.size
            )
        }
    }

    private fun Transaction.masked(): Transaction = copy(
        note = SensitiveDataMasker.mask(note),
        merchant = merchant?.let { SensitiveDataMasker.mask(it) }
    )

    /**
     * The queue's `body` is the raw bank message, and it is the field
     * `SmsIngestor` masks before the row is ever written — so a restored row has
     * to go through the same masker or restore becomes a way to put unmasked text
     * back into the table. `merchant` is deliberately left alone: it is a parsed
     * merchant name (never the message text) and the local pipeline stores it
     * unmasked, so masking it here would make a restored row differ from an
     * identical locally-processed one. The transaction that carries the same
     * merchant is masked by [Transaction.masked] above.
     */
    private fun SmsMessageEntity.masked(): SmsMessageEntity =
        copy(body = SensitiveDataMasker.mask(body))

    /** Same rule for the "we could not read this message" table. */
    private fun UnrecognizedSmsEntity.masked(): UnrecognizedSmsEntity =
        copy(body = SensitiveDataMasker.mask(body))

    data class RestoreSummary(
        val transactions: Int,
        val categories: Int,
        val budgets: Int,
        val subscriptions: Int,
        val savingsGoals: Int,
        val smsMessages: Int,
        val classificationMemory: Int
    )
}
