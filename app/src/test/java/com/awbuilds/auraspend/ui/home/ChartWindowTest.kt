package com.awbuilds.auraspend.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * The chart says "last 7 days", and it must mean it: the window ends today and
 * reaches back six days, so a spend from six days ago still draws a bar. A
 * calendar-week window (the previous behaviour) read "no spending" from Monday
 * morning even while the user's week still held real spending.
 */
class ChartWindowTest {

    @Test
    fun `the window is the seven days ending today, both bounds inclusive`() {
        val now = LocalDateTime.of(2026, 10, 14, 9, 30)
        val (start, end) = chartWindow(now)

        assertEquals(LocalDateTime.of(2026, 10, 8, 0, 0), start)
        assertEquals(LocalDateTime.of(2026, 10, 14, 23, 59, 59, 999_999_999), end)

        val days = (0..6).map { start.plusDays(it.toLong()).toLocalDate() }
        assertEquals(7, days.size)
        assertEquals(now.toLocalDate(), days.last())
        assertEquals(now.toLocalDate().minusDays(6), days.first())
    }

    @Test
    fun `a Monday still reaches back through the weekend`() {
        // The regression case: on a Monday the calendar week holds nothing, and
        // the chart must still show the weekend's spending.
        val monday = LocalDateTime.of(2026, 10, 12, 8, 0)
        val (start, _) = chartWindow(monday)
        assertEquals(LocalDateTime.of(2026, 10, 6, 0, 0), start)
    }
}
