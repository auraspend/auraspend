package com.awbuilds.auraspend.domain.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Activity month stepper's rules.
 *
 * The list used to be all-time while the summary strip was month-to-date, so
 * the two never described the same rows; both now describe the filter's month.
 * The stepper may browse history but never the future, and the SQL range must
 * cover the month's first and last millis or boundary-day rows disappear.
 */
class TransactionFilterTest {

    @Test
    fun `a filter starts on the current month`() {
        val filter = TransactionFilter()

        assertEquals(YearMonth.now(), filter.month)
        assertTrue(filter.isCurrentMonth)
    }

    @Test
    fun `newest-first is the default order`() {
        assertFalse(TransactionFilter().sortAscending)
    }

    @Test
    fun `stepping forward clamps at the current month`() {
        val now = YearMonth.of(2026, 10)

        assertEquals(YearMonth.of(2026, 8), nextMonthOrCurrent(YearMonth.of(2026, 7), now))
        assertEquals(now, nextMonthOrCurrent(now, now))
        assertEquals("a future month falls back to now", now, nextMonthOrCurrent(YearMonth.of(2026, 12), now))
    }

    @Test
    fun `the epoch range covers the first and last millis of the month`() {
        val (start, end) = YearMonth.of(2026, 2).toEpochRange(ZoneOffset.UTC)

        assertEquals(
            LocalDateTime.of(2026, 2, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            start
        )
        assertEquals(
            LocalDateTime.of(2026, 2, 28, 23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC).toEpochMilli(),
            end
        )
    }

    @Test
    fun `the range ends on the 29th in a leap February`() {
        val (start, end) = YearMonth.of(2028, 2).toEpochRange(ZoneOffset.UTC)

        assertEquals(1, Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).dayOfMonth)
        assertEquals(29, Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).dayOfMonth)
    }
}
