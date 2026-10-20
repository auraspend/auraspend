package com.awbuilds.auraspend.domain.model

import java.util.UUID
import java.time.LocalDateTime

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val categoryId: String,
    val note: String,
    val merchant: String? = null,
    val bankName: String? = null,
    val date: LocalDateTime,
    val type: TransactionType,
    val isRecurring: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency? = null,
    val nextDueDate: LocalDateTime? = null,
    val subscriptionName: String? = null,
    val sourceSmsId: String? = null
)

enum class TransactionType {
    INCOME, EXPENSE
}

enum class RecurrenceFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String,
    val color: Int,
    val isDefault: Boolean = false
)

data class Budget(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val limitAmount: Double,
    val spentAmount: Double = 0.0,
    val period: BudgetPeriod
)

enum class BudgetPeriod {
    WEEKLY, MONTHLY, YEARLY
}

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val categoryId: String,
    val billingCycle: RecurrenceFrequency,
    val nextBillingDate: LocalDateTime,
    val active: Boolean = true
)

data class ParsedBankMessage(
    val amount: Double? = null,
    val type: TransactionType? = null,
    val merchant: String? = null,
    val bankName: String? = null,
    val date: LocalDateTime? = null,
    val note: String? = null,
    val categoryId: String? = null,
    val confidence: Float = 0f,
    /**
     * The bank's own "Avl Bal" figure, when the message states one. Never used
     * as the transaction amount — it is the account's position after the
     * movement, which is the only figure in an SMS that is a *balance*.
     */
    val balanceAfter: Double? = null,
    /**
     * The last four digits of the account or card the message is about, and
     * whether it is a card. Together with [bankName] this identifies the
     * instrument a [balanceAfter] belongs to.
     */
    val instrumentLast4: String? = null,
    val instrumentIsCard: Boolean = false,
    val rawMessage: String = ""
)

/** Aggregate totals for a date range (dashboard/activity summaries). */
data class TransactionSummary(
    val income: Double,
    val expense: Double,
    val incomeCount: Int,
    val expenseCount: Int
)

/** Total expense for one category in a range. */
data class CategoryTotal(
    val categoryId: String,
    val amount: Double
)

/** Total expense for one local calendar day. */
data class DayTotal(
    val date: java.time.LocalDate,
    val amount: Double
)
