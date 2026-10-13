package com.awbuilds.auraspend.domain.model

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Single source of truth for "how much has been spent against a budget".
 *
 * Spent amounts are always recomputed from live transactions instead of being
 * trusted from storage, so budgets stay correct as SMS auto-classification
 * saves new expenses in the background.
 */
object BudgetSpending {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Inclusive [start, end] range of the calendar period containing [now]. */
    fun periodRange(period: BudgetPeriod, now: LocalDateTime = LocalDateTime.now()): Pair<LocalDateTime, LocalDateTime> {
        val start = when (period) {
            BudgetPeriod.WEEKLY -> now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).with(java.time.LocalTime.MIN)
            BudgetPeriod.MONTHLY -> now.withDayOfMonth(1).with(java.time.LocalTime.MIN)
            BudgetPeriod.YEARLY -> now.withDayOfYear(1).with(java.time.LocalTime.MIN)
        }
        val end = when (period) {
            BudgetPeriod.WEEKLY -> now.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)).with(java.time.LocalTime.MAX)
            BudgetPeriod.MONTHLY -> now.with(TemporalAdjusters.lastDayOfMonth()).with(java.time.LocalTime.MAX)
            BudgetPeriod.YEARLY -> now.with(TemporalAdjusters.lastDayOfYear()).with(java.time.LocalTime.MAX)
        }
        return start to end
    }

    /** Expenses for [budget]'s category inside its current period. */
    fun spentFor(
        budget: Budget,
        transactions: List<Transaction>,
        now: LocalDateTime = LocalDateTime.now()
    ): Double {
        val (start, end) = periodRange(budget.period, now)
        val startEpoch = start.atZone(zone).toInstant().toEpochMilli()
        val endEpoch = end.atZone(zone).toInstant().toEpochMilli()
        return transactions.asSequence()
            .filter { it.categoryId == budget.categoryId && it.type == TransactionType.EXPENSE }
            .filter {
                val ts = it.date.atZone(zone).toInstant().toEpochMilli()
                ts in startEpoch..endEpoch
            }
            .sumOf { it.amount }
    }

    /** Fresh copy of [budgets] with recomputed spent amounts. */
    fun withFreshSpent(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        now: LocalDateTime = LocalDateTime.now()
    ): List<Budget> = budgets.map { it.copy(spentAmount = spentFor(it, transactions, now)) }

    /**
     * [periodRange] as inclusive epoch-millis bounds, for the indexed SQL
     * aggregates (`observeExpenseByCategory`).
     *
     * ADR 0003 makes the aggregate the production path and [spentFor] the
     * small-table/test path, which only works if both agree on the window. They
     * now derive it from the same [periodRange], so Budget, Plan and the Dashboard
     * cannot drift apart on a boundary.
     */
    fun periodEpochRange(
        period: BudgetPeriod,
        now: LocalDateTime = LocalDateTime.now()
    ): Pair<Long, Long> {
        val (start, end) = periodRange(period, now)
        return start.atZone(zone).toInstant().toEpochMilli() to
            end.atZone(zone).toInstant().toEpochMilli()
    }
}
