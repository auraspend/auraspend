package com.awbuilds.auraspend.data.local

import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.data.local.entities.*
import com.awbuilds.auraspend.domain.model.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private const val TAG = "Mappers"

/**
 * Parses a stored enum name, falling back instead of throwing.
 *
 * Every *write* path into this database already validates: `BackupSerializer`
 * uses `enumOrNull(...) ?: return@forEachObject`, `CsvManager` wraps `valueOf`
 * in a try/catch, and `SmsAiEnricher` uses `runCatching`. The read path did not,
 * which made the defence one-sided: a single row whose enum string did not match
 * the current enum threw inside a Room `Flow.map`, and the exception propagated
 * out of the Dashboard's aggregation and blanked the whole home screen with
 * "Couldn't load your data" — with no way back short of reinstalling.
 *
 * That is not hypothetical. It is what happened on 2026-09-29 when a
 * subscription row carried `"ANNUAL"` where `RecurrenceFrequency` has
 * `YEARLY`, and it is exactly the hazard `AGENTS.md` calls out for
 * `AppThemeMode`: never bare `valueOf` on stored data.
 *
 * Renaming or adding an enum constant would have the same effect on every row
 * ever written by the previous build, which is why this fails soft and logs.
 */
private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(
    fallback: T,
    label: String
): T {
    val raw = this ?: return fallback
    return runCatching { enumValueOf<T>(raw) }
        .onFailure {
            AuraLog.w(TAG, "Unknown $label value '$raw' in database; using $fallback")
        }
        .getOrDefault(fallback)
}

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = amount,
    categoryId = categoryId,
    note = note,
    merchant = merchant,
    bankName = bankName,
    date = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTimestamp), ZoneId.systemDefault()),
    // Expense is the safe default: it never inflates a balance, whereas a
    // mis-parsed INCOME would.
    type = type.toEnumOrDefault(TransactionType.EXPENSE, "TransactionType"),
    isRecurring = isRecurring,
    // Null stays null: a non-recurring transaction has no frequency, and
    // defaulting it would make every transaction look like it recurs monthly.
    recurrenceFrequency = recurrenceFrequency
        ?.toEnumOrDefault(RecurrenceFrequency.MONTHLY, "RecurrenceFrequency"),
    nextDueDate = nextDueDateTimestamp?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
    },
    balanceAfter = balanceAfter,
    instrumentLast4 = instrumentLast4,
    instrumentIsCard = instrumentIsCard,
    subscriptionName = subscriptionName,
    sourceSmsId = sourceSmsId
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount,
    categoryId = categoryId,
    note = note,
    merchant = merchant,
    bankName = bankName,
    dateTimestamp = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    type = type.name,
    isRecurring = isRecurring,
    recurrenceFrequency = recurrenceFrequency?.name,
    nextDueDateTimestamp = nextDueDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
    subscriptionName = subscriptionName,
    sourceSmsId = sourceSmsId,
    balanceAfter = balanceAfter,
    instrumentLast4 = instrumentLast4,
    instrumentIsCard = instrumentIsCard
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    icon = icon,
    color = color,
    isDefault = isDefault
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    color = color,
    isDefault = isDefault
)

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    limitAmount = limitAmount,
    spentAmount = spentAmount,
    period = period.toEnumOrDefault(BudgetPeriod.MONTHLY, "BudgetPeriod")
)

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    categoryId = categoryId,
    limitAmount = limitAmount,
    spentAmount = spentAmount,
    period = period.name
)

fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    name = name,
    amount = amount,
    categoryId = categoryId,
    billingCycle = billingCycle.toEnumOrDefault(
        RecurrenceFrequency.MONTHLY, "RecurrenceFrequency (subscription billingCycle)"
    ),
    nextBillingDate = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(nextBillingDateTimestamp), ZoneId.systemDefault()
    ),
    active = active
)

fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id,
    name = name,
    amount = amount,
    categoryId = categoryId,
    billingCycle = billingCycle.name,
    nextBillingDateTimestamp = nextBillingDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    active = active
)
