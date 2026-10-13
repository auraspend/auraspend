package com.awbuilds.auraspend.ui.recurring

import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suggestion list on the Subscriptions screen.
 *
 * The classifier flags recurring payments at ingest (no model needed); these
 * tests pin what the screen does with that flag: one row per service, newest
 * first, unusable names dropped, tracked services removed. Amounts are
 * synthetic.
 */
class DetectedSubscriptionsTest {

    private fun txn(
        merchant: String?,
        amount: Double = 199.0,
        date: LocalDateTime = LocalDateTime.of(2026, 5, 4, 12, 0),
        recurring: Boolean = true,
        categoryId: String = "cat_subscription"
    ) = Transaction(
        amount = amount,
        categoryId = categoryId,
        note = merchant.orEmpty(),
        merchant = merchant,
        date = date,
        type = TransactionType.EXPENSE,
        isRecurring = recurring
    )

    @Test
    fun `repeated payments of one service collapse into one suggestion`() {
        val grouped = detectedSubscriptions(
            listOf(
                txn("SPOTIFY INDIA P", 119.0, LocalDateTime.of(2026, 3, 4, 9, 0)),
                txn("SPOTIFY INDIA P", 129.0, LocalDateTime.of(2026, 5, 4, 9, 0))
            )
        )

        assertEquals(1, grouped.size)
        assertEquals("SPOTIFY INDIA P", grouped.single().name)
        assertEquals(129.0, grouped.single().amount, 0.001)
        assertEquals(LocalDateTime.of(2026, 5, 4, 9, 0), grouped.single().lastPaid)
        assertEquals(2, grouped.single().occurrences)
    }

    @Test
    fun `grouping is case-insensitive`() {
        val grouped = detectedSubscriptions(
            listOf(
                txn("Netflix com", 149.0, LocalDateTime.of(2026, 2, 1, 9, 0)),
                txn("NETFLIX COM", 149.0, LocalDateTime.of(2026, 4, 1, 9, 0))
            )
        )

        assertEquals(1, grouped.size)
        assertEquals(2, grouped.single().occurrences)
    }

    @Test
    fun `services come back newest first`() {
        val grouped = detectedSubscriptions(
            listOf(
                txn("NETFLIX COM", date = LocalDateTime.of(2026, 2, 18, 9, 0)),
                txn("SPOTIFY INDIA P", date = LocalDateTime.of(2026, 5, 4, 9, 0)),
                txn("Appleservices Bdsi", date = LocalDateTime.of(2026, 4, 29, 9, 0))
            )
        )

        assertEquals(
            listOf("SPOTIFY INDIA P", "Appleservices Bdsi", "NETFLIX COM"),
            grouped.map { it.name }
        )
    }

    @Test
    fun `the latest payment carries the amount and category`() {
        val grouped = detectedSubscriptions(
            listOf(
                txn("Appleservices Bdsi", 99.0, LocalDateTime.of(2026, 2, 1, 9, 0), categoryId = "cat_old"),
                txn("Appleservices Bdsi", 149.0, LocalDateTime.of(2026, 4, 29, 9, 0), categoryId = "cat_food")
            )
        )

        assertEquals(149.0, grouped.single().amount, 0.001)
        assertEquals("cat_food", grouped.single().categoryId)
    }

    @Test
    fun `transactions without the recurring flag are ignored`() {
        val grouped = detectedSubscriptions(
            listOf(
                txn("NETFLIX COM", recurring = false),
                txn("SPOTIFY INDIA P")
            )
        )

        assertEquals(listOf("SPOTIFY INDIA P"), grouped.map { it.name })
    }

    @Test
    fun `unusable names are dropped`() {
        val grouped = detectedSubscriptions(
            listOf(
                txn(null),
                txn("  "),
                txn("12"),           // digits-only merchant artifact
                txn("merchant"),     // the field word itself, never a service
                txn("NETFLIX COM")
            )
        )

        assertEquals(listOf("NETFLIX COM"), grouped.map { it.name })
    }

    @Test
    fun `already-tracked services are removed from the suggestions`() {
        val grouped = detectedSubscriptions(
            listOf(txn("NETFLIX COM"), txn("SPOTIFY INDIA P")),
            trackedNames = listOf("netflix com")
        )

        assertEquals(listOf("SPOTIFY INDIA P"), grouped.map { it.name })
    }

    @Test
    fun `an empty input yields no suggestions`() {
        assertTrue(detectedSubscriptions(emptyList()).isEmpty())
    }
}
