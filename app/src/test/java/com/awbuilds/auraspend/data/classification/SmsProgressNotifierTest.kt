package com.awbuilds.auraspend.data.classification

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rules behind the categorisation notification.
 *
 * The old behaviour posted "N new transactions saved" on EVERY pass, so a two-month backfill
 * produced a notification every six hours (and on every screen entry) for as long as the
 * backlog lasted. These tests pin the replacement rules:
 *
 *  - a backlog shows ONE ongoing determinate notification;
 *  - when it finishes, that notification becomes ONE summary and then goes away;
 *  - a batch too small to watch produces a single result notification;
 *  - a drained queue with more history still to walk shows an indeterminate bar, not a
 *    completed one.
 *
 * The posting itself (channel, permission, icons) is Android plumbing; the decision is what
 * loses a user's trust when it is wrong.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsProgressNotifierTest {

    private fun snapshot(
        pendingNew: Int = 0,
        queueTotal: Int = 0,
        moreHistoryMayCome: Boolean = false,
        passSaved: Int = 0,
        progressWasShowing: Boolean = false,
        drain: DrainTally = DrainTally()
    ) = SmsDrainSnapshot(
        pendingNew = pendingNew,
        queueTotal = queueTotal,
        moreHistoryMayCome = moreHistoryMayCome,
        passSaved = passSaved,
        progressWasShowing = progressWasShowing,
        drain = drain
    )

    @Test
    fun `a backlog keeps a determinate bar going with the queue's own numbers`() {
        val copy = copyForSnapshot(
            snapshot(pendingNew = 176, queueTotal = 210, drain = DrainTally(saved = 34, skipped = 0))
        )

        assertTrue(copy is SmsNotificationCopy.Progress)
        copy as SmsNotificationCopy.Progress
        assertEquals(34, copy.processed)
        assertEquals(210, copy.queueTotal)
        assertTrue("progress cannot be swiped away", copy.ongoing)
    }

    @Test
    fun `a batch too small for a bar produces no progress notification`() {
        // Three messages arriving late at night is one result line, not a notification that
        // appears, ticks once and disappears.
        assertNull(SmsProgressCopy.progress(processed = 1, queueTotal = 3))
        assertNotNull(SmsProgressCopy.progress(processed = 0, queueTotal = 4))
    }

    @Test
    fun `a small backlog that is still draining says nothing at all`() {
        // With no bar to show and nothing finished, a 3-message backlog must be silent until
        // it lands; the result line below follows.
        assertNull(copyForSnapshot(snapshot(pendingNew = 1, queueTotal = 3)))
    }

    @Test
    fun `a finished backlog replaces the bar with exactly one summary`() {
        val copy = copyForSnapshot(
            snapshot(
                pendingNew = 0,
                queueTotal = 210,
                progressWasShowing = true,
                drain = DrainTally(saved = 148, skipped = 61, failed = 1)
            )
        )

        assertTrue(copy is SmsNotificationCopy.Summary)
        copy as SmsNotificationCopy.Summary
        assertEquals(210, copy.reviewed)
        assertEquals(148, copy.saved)
        assertEquals(61, copy.skipped)
        assertTrue("the summary can be dismissed", copy.autoCancel)
        assertTrue("and it is not a bar", !copy.ongoing)
    }

    @Test
    fun `a drained queue with history still to walk shows an indeterminate bar`() {
        // "210 of 210" while the next history page is being pulled would claim the drain is
        // complete when it is not.
        val copy = copyForSnapshot(snapshot(pendingNew = 0, queueTotal = 210, moreHistoryMayCome = true))

        assertEquals(SmsNotificationCopy.Scanning, copy)
    }

    @Test
    fun `a small batch with nothing else to say gives one result notification`() {
        val copy = copyForSnapshot(snapshot(pendingNew = 0, queueTotal = 2, passSaved = 2))

        assertEquals(SmsNotificationCopy.Saved(2), copy)
    }

    @Test
    fun `an idle pass says nothing`() {
        // Nothing arrived, no bar is showing: the shade must stay quiet.
        assertNull(copyForSnapshot(snapshot()))
    }

    @Test
    fun `the notifier accumulates a drain and closes it with one summary`() {
        val posted = mutableListOf<SmsNotificationCopy>()
        val notifier = SmsProgressNotifier(
            context = ApplicationProvider.getApplicationContext(),
            isForeground = { false },
            onCopy = { posted += it }
        )

        // Three passes of a 210-row backlog.
        notifier.onPassFinished(
            result = SmsProcessResult(saved = 8, skipped = 4, failed = 0, attempted = 12),
            pendingNew = 198,
            queueTotal = 210,
            moreHistoryMayCome = true
        )
        notifier.onPassFinished(
            result = SmsProcessResult(saved = 8, skipped = 4, failed = 0, attempted = 12),
            pendingNew = 186,
            queueTotal = 210,
            moreHistoryMayCome = true
        )
        notifier.onPassFinished(
            result = SmsProcessResult(saved = 8, skipped = 4, failed = 0, attempted = 12),
            pendingNew = 174,
            queueTotal = 210,
            moreHistoryMayCome = true
        )

        assertEquals(3, posted.size)
        assertTrue(posted.all { it is SmsNotificationCopy.Progress })

        // The last pass finishes the drain: exactly one summary, and it covers the whole
        // drain rather than just the final pass.
        notifier.onPassFinished(
            result = SmsProcessResult(saved = 124, skipped = 58, failed = 0, attempted = 174),
            pendingNew = 0,
            queueTotal = 210,
            moreHistoryMayCome = false
        )

        assertEquals(4, posted.size)
        val summary = posted.last() as SmsNotificationCopy.Summary
        assertEquals(218, summary.reviewed)
        assertEquals(148, summary.saved)
        assertEquals(70, summary.skipped)
    }

    @Test
    fun `the accumulation resets after the drain closes`() {
        val posted = mutableListOf<SmsNotificationCopy>()
        val notifier = SmsProgressNotifier(
            context = ApplicationProvider.getApplicationContext(),
            isForeground = { false },
            onCopy = { posted += it }
        )

        // A drain that never warranted a bar (the app was on screen for all of it) closes
        // with one result line, and its totals are forgotten.
        notifier.onPassFinished(
            result = SmsProcessResult(saved = 148, skipped = 62, failed = 0, attempted = 210),
            pendingNew = 0,
            queueTotal = 210,
            moreHistoryMayCome = false
        )
        assertEquals(listOf(SmsNotificationCopy.Saved(148)), posted)

        // One message arrives later: a single result notification, with no leftover totals
        // from the previous drain folded into it.
        notifier.onPassFinished(
            result = SmsProcessResult(saved = 1, skipped = 0, failed = 0, attempted = 1),
            pendingNew = 0,
            queueTotal = 1,
            moreHistoryMayCome = false
        )
        assertEquals(
            listOf(SmsNotificationCopy.Saved(148), SmsNotificationCopy.Saved(1)),
            posted
        )
    }

    @Test
    fun `while the app is on screen the shade copy is dropped`() {
        val posted = mutableListOf<SmsNotificationCopy>()
        val notifier = SmsProgressNotifier(
            context = ApplicationProvider.getApplicationContext(),
            // The app came to the foreground mid-drain.
            isForeground = { true },
            onCopy = { posted += it }
        )

        notifier.onPassFinished(
            result = SmsProcessResult(saved = 8, skipped = 4, failed = 0, attempted = 12),
            pendingNew = 198,
            queueTotal = 210,
            moreHistoryMayCome = true
        )

        // The Classification screen is already showing the queue live; a second copy in the
        // shade would be noise, not information.
        assertTrue(posted.isEmpty())
    }
}

/**
 * The drain-continuation rule for the worker.
 *
 * Stopping when the live queue empties but the history walk has not finished would leave the
 * backlog half-read and wait six hours for the rest; chaining on either condition keeps the
 * drain moving pass-by-pass with the app closed.
 */
class SmsDrainPolicyTest {

    @Test
    fun `pending messages keep the drain going`() {
        assertTrue(shouldContinueDraining(pendingNew = 4, moreHistoryMayCome = false))
    }

    @Test
    fun `an unfinished history walk keeps the drain going`() {
        assertTrue(shouldContinueDraining(pendingNew = 0, moreHistoryMayCome = true))
    }

    @Test
    fun `a caught-up queue and a finished walk stop the drain`() {
        assertTrue(!shouldContinueDraining(pendingNew = 0, moreHistoryMayCome = false))
    }
}
