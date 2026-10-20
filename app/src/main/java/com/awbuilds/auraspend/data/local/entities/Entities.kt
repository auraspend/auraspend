package com.awbuilds.auraspend.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["sourceSmsId"], unique = true),
        Index(value = ["dateTimestamp"]),
        Index(value = ["categoryId"]),
        Index(value = ["type", "dateTimestamp"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amount: Double,
    val categoryId: String,
    val note: String,
    val merchant: String? = null,
    val bankName: String? = null,
    val dateTimestamp: Long,
    val type: String,
    val isRecurring: Boolean = false,
    val recurrenceFrequency: String? = null,
    val nextDueDateTimestamp: Long? = null,
    val subscriptionName: String? = null,
    val sourceSmsId: String? = null,
    /**
     * The balance the message stated ("Avl Bal" / a card's "Avl Limit"), and the
     * instrument it belongs to. A snapshot per transaction rather than a
     * separate accounts table: the account ledger is derived from these, which
     * keeps the backup a single existing shape.
     */
    val balanceAfter: Double? = null,
    val instrumentLast4: String? = null,
    val instrumentIsCard: Boolean = false
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: Int,
    val isDefault: Boolean
)

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryId"])]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val limitAmount: Double,
    val spentAmount: Double = 0.0,
    val period: String
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amount: Double,
    val categoryId: String,
    val billingCycle: String,
    val nextBillingDateTimestamp: Long,
    val active: Boolean = true
)

/**
 * Lifecycle of a bank SMS in the persistent classification queue. The id is the SMS provider's
 * `_id`, so ingestion is idempotent (`INSERT OR IGNORE`) and the pipeline can resume per-message
 * instead of re-scanning the inbox with a timestamp cursor.
 *
 * [CLASSIFYING] is the in-flight marker: the row is claimed by a pass right now, so the UI can
 * show "categorising…" for that exact message. [PROCESSED] is deliberately NOT in-flight — it is
 * where both undo paths park a row, and the pipeline must never claim or re-save it (re-processing
 * an undo-dismissed row would resurrect the decision the user just reversed). A pass killed
 * mid-row leaves a stale [CLASSIFYING] row, which the next pass sweeps back to [NEW].
 */
enum class SmsMessageStatus { NEW, CLASSIFYING, PROCESSED, SAVED, SKIPPED, FAILED }

@Entity(tableName = "sms_messages")
data class SmsMessageEntity(
    @PrimaryKey val id: String,
    val address: String,
    val body: String,
    val receivedAt: Long,
    val status: String = SmsMessageStatus.NEW.name,
    val amount: Double? = null,
    val type: String? = null,
    val merchant: String? = null,
    val categoryId: String? = null,
    val isSubscription: Boolean = false,
    val confidence: Float = 0f,
    val attempts: Int = 0,
    val updatedAt: Long = 0L
)
