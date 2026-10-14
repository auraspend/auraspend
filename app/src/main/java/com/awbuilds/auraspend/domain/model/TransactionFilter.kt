package com.awbuilds.auraspend.domain.model

/**
 * Filter for the paged Activity list. `null` type/category mean "no
 * constraint" and a blank [query] matches everything, which mirrors the SQL
 * predicate used by Room's `PagingSource`.
 *
 * [matches] keeps the same semantics for in-memory repository fakes.
 */
data class TransactionFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val categoryId: String? = null,
    /**
     * The calendar month the list shows. Month-scoped by default: the list and
     * the summary strip describe the same window, and browsing history is the
     * stepper's job. Previously the list was all-time while the strip was
     * month-to-date, so the two never described the same rows.
     */
    val month: java.time.YearMonth = java.time.YearMonth.now()
) {
    /** True when any constraint is active (drives the filtered empty state). */
    val isActive: Boolean
        get() = query.isNotBlank() || type != null || categoryId != null

    /** False once the stepper has moved into history. */
    val isCurrentMonth: Boolean
        get() = month == java.time.YearMonth.now()

    fun matches(transaction: Transaction): Boolean =
        (type == null || transaction.type == type) &&
            (categoryId == null || transaction.categoryId == categoryId) &&
            (
                query.isBlank() ||
                    transaction.note.contains(query, ignoreCase = true) ||
                    (transaction.merchant?.contains(query, ignoreCase = true) == true)
                )
}


/**
 * The next month when stepping forward, clamped at the current month: the
 * list may browse history but never the future.
 */
internal fun nextMonthOrCurrent(
    current: java.time.YearMonth,
    now: java.time.YearMonth = java.time.YearMonth.now()
): java.time.YearMonth = if (current.isBefore(now)) current.plusMonths(1) else now

/** Inclusive epoch-millis bounds of this month in [zone], for SQL ranges. */
internal fun java.time.YearMonth.toEpochRange(
    zone: java.time.ZoneId
): Pair<Long, Long> {
    val start = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = atEndOfMonth().atTime(java.time.LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
    return start to end
}
