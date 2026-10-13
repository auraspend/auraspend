package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.ui.classification.SmsInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the inbox cursor against a simulated inbox.
 *
 * The bug this pins is the "loses the user's money silently" class in its
 * quietest form: the scan did not crash and it did not mis-file anything — it
 * declared the inbox covered after the first 500 messages and never looked below
 * them again. Every older transaction was invisible to auto-detect for ever, and
 * nothing in the suite noticed, because the only thing exercising the cursor was
 * a unit test of a function production never called.
 *
 * [Sim] reproduces both halves of the real contract: the provider query's
 * semantics (`DATE > since AND DATE < before ORDER BY DATE [ASC|DESC] LIMIT n`)
 * and the queue's `OR IGNORE` insert, so a re-read row does **not** become new
 * work for the classifier. That distinction is what the history gate reads.
 */
class SmsIngestPlannerTest {

    private val cap = 500

    private fun inbox(count: Int): List<SmsInfo> = (1..count).map { index ->
        SmsInfo(
            id = "sms-$index",
            address = "AXISBK",
            body = "Rs $index debited from A/c XX1234",
            timestamp = index * 1_000L
        )
    }

    private inner class Sim(private val inbox: List<SmsInfo>) {

        /** Every row a scan returned, re-reads included. */
        val scanned = mutableListOf<String>()

        /** Rows the queue accepted (`OR IGNORE` means a re-read inserts nothing). */
        val queued = mutableSetOf<String>()

        /** Messages still waiting for the classifier. */
        var pending = 0

        var cursor = SmsIngestCursor()
        var passes = 0

        /** Mirrors `SmsAutoClassifier.queryBankSms`. */
        private fun scan(scan: InboxScan): List<SmsInfo> {
            val window = inbox.filter {
                it.timestamp > scan.sinceTimestamp && it.timestamp < scan.beforeTimestamp
            }
            val ordered = if (scan.oldestFirst) window.sortedBy { it.timestamp }
            else window.sortedByDescending { it.timestamp }
            return ordered.take(cap)
        }

        private fun accept(batch: List<SmsInfo>) {
            scanned += batch.map { it.id }
            pending += batch.count { queued.add(it.id) }
        }

        /** One `SmsIngestor.ingest` pass. Returns the number of rows scanned. */
        fun pass(): Int {
            val before = scanned.size
            passes++

            val live = SmsIngestPlanner.liveScan(cursor)
            val liveBatch = scan(live)
            accept(liveBatch)
            cursor = SmsIngestPlanner.afterLiveScan(cursor, live, liveBatch, cap)

            val history = SmsIngestPlanner.historyScanWhenIdle(cursor, pendingMessages = pending)
            if (history != null) {
                val batch = scan(history)
                accept(batch)
                cursor = SmsIngestPlanner.afterHistoryScan(cursor, batch, cap)
            }
            return scanned.size - before
        }

        /** The classifier catching up: the gate exists to model exactly this. */
        fun classifyAll() {
            pending = 0
        }
    }

    @Test
    fun `a 1200-message inbox is enqueued from top to bottom`() {
        val inbox = inbox(1200)
        val sim = Sim(inbox)

        var guard = 0
        while (!sim.cursor.historyWalkDone && guard < 30) {
            sim.classifyAll()
            sim.pass()
            guard++
        }

        assertTrue("the walk must terminate", sim.cursor.historyWalkDone)
        assertEquals(
            "every message must be enqueued, including the 700 the old cursor " +
                "stranded below the newest page",
            inbox.map { it.id }.toSet(),
            sim.queued
        )
    }

    @Test
    fun `the first pass takes the newest page, not the oldest`() {
        val inbox = inbox(1200)
        val sim = Sim(inbox)
        sim.pass()

        assertEquals(
            "a cold start shows today's messages first; that page leaves the queue " +
                "non-empty, so no history page is pulled in the same pass",
            cap,
            sim.scanned.size
        )
        assertEquals("sms-1200", sim.scanned.first())
        assertEquals("sms-701", sim.scanned.last())
    }

    @Test
    fun `history waits while the live queue is not empty`() {
        val cursor = SmsIngestCursor(newestIngested = 500_000L, historyBefore = 400_000L)

        assertNull(
            "no history page while messages are still waiting to be classified",
            SmsIngestPlanner.historyScanWhenIdle(cursor, pendingMessages = 1)
        )
        assertNotNull(
            "and the page is available as soon as the queue drains",
            SmsIngestPlanner.historyScanWhenIdle(cursor, pendingMessages = 0)
        )
    }

    @Test
    fun `a live backlog holds the walk back but does not cancel it`() {
        val inbox = inbox(1200)
        val sim = Sim(inbox)

        // First pass fills the queue with the newest page...
        sim.pass()
        assertEquals(cap, sim.queued.size)
        assertEquals(cap, sim.pending)

        // ...so a second pass with that backlog unclassified scans only the
        // boundary row again (ignored by the queue) and pulls no history.
        sim.pass()
        assertEquals("nothing new is queued while the backlog stands", cap, sim.queued.size)
        assertTrue(
            "the walk is still pending, not cancelled",
            sim.cursor.historyBefore > 0L && !sim.cursor.historyWalkDone
        )

        // Once the backlog is classified, the walk resumes and reaches the bottom.
        var guard = 0
        while (!sim.cursor.historyWalkDone && guard < 30) {
            sim.classifyAll()
            sim.pass()
            guard++
        }
        assertTrue("the walk resumes once the queue drains", sim.cursor.historyWalkDone)
        assertEquals(inbox.map { it.id }.toSet(), sim.queued)
    }

    @Test
    fun `an upgrade walks below its own high-water mark`() {
        // The state of an install written by the old cursor: a high-water mark and
        // no walk. Everything below that mark was stranded and must be re-read.
        val scan = SmsIngestPlanner.historyScanWhenIdle(
            SmsIngestCursor(newestIngested = 900_000L),
            pendingMessages = 0
        )
        assertNotNull(scan)
        assertEquals(0L, scan!!.sinceTimestamp)
        assertEquals(900_001L, scan.beforeTimestamp)
    }

    @Test
    fun `a burst larger than a page does not stall the live scan`() {
        // Newest-first here would return the same newest page for ever: the cursor
        // would never advance past it and the rest of the burst would be stranded.
        // Oldest-first walks forward through the burst instead.
        val inbox = inbox(1500)
        val sim = Sim(inbox)
        sim.cursor = SmsIngestCursor(newestIngested = 499_999L, historyWalkDone = true)

        var guard = 0
        while (guard < 20) {
            sim.classifyAll()
            if (sim.pass() == 0) break
            guard++
        }

        assertEquals(
            "the whole burst must be enqueued",
            (500..1500).map { "sms-$it" }.toSet(),
            sim.queued
        )
        // Every full page deliberately re-reads the row sitting exactly on the
        // cursor: an exclusive lower bound can never skip a message that shares
        // the boundary timestamp. The queue ignores the re-read, so it must stay
        // bounded to one row per page and never become a re-walk.
        assertTrue(
            "re-reads stayed bounded per page",
            sim.scanned.size - sim.queued.size <= sim.passes + 1
        )
        assertEquals(1_500_000L - 1, sim.cursor.newestIngested)
    }

    @Test
    fun `an inbox smaller than one page finishes the walk in one pass`() {
        val inbox = inbox(40)
        val sim = Sim(inbox)
        sim.pass()

        assertEquals(40, sim.queued.size)
        assertTrue(
            "nothing older exists, so the walk is done immediately",
            sim.cursor.historyWalkDone
        )
        assertNull(SmsIngestPlanner.historyScanWhenIdle(sim.cursor, pendingMessages = 0))
    }

    @Test
    fun `an empty inbox does not leave a walk pending`() {
        val sim = Sim(emptyList())
        sim.pass()

        assertTrue(sim.cursor.historyWalkDone)
        assertNull(SmsIngestPlanner.historyScanWhenIdle(sim.cursor, pendingMessages = 0))
    }

    @Test
    fun `a completed walk is not restarted`() {
        val done = SmsIngestCursor(newestIngested = 900_000L, historyWalkDone = true)
        assertNull(
            "re-walking the whole inbox on every app start would re-query every " +
                "message for ever",
            SmsIngestPlanner.historyScanWhenIdle(done, pendingMessages = 0)
        )
    }
}
