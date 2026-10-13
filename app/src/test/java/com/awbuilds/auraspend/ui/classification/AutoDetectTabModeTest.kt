package com.awbuilds.auraspend.ui.classification

import com.awbuilds.auraspend.ui.classification.AutoDetectTabMode.EMPTY
import com.awbuilds.auraspend.ui.classification.AutoDetectTabMode.MODEL_PROMPT
import com.awbuilds.auraspend.ui.classification.AutoDetectTabMode.PERMISSION
import com.awbuilds.auraspend.ui.classification.AutoDetectTabMode.QUEUE
import com.awbuilds.auraspend.ui.classification.AutoDetectTabMode.SCANNING
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the Auto Detect tab's precedence.
 *
 * The regression these exist for was measured on the owner's device (2026-10-13):
 * the model-download prompt short-circuited ahead of the queue, so with no model
 * downloaded the tab showed *only* the prompt while 150 real messages drained.
 * The queue must never hide behind the optional model.
 */
class AutoDetectTabModeTest {

    private fun mode(
        smsPermissionGranted: Boolean = true,
        isScanningQueue: Boolean = false,
        isAiEnriching: Boolean = false,
        queueIsEmpty: Boolean = false,
        isModelDownloaded: Boolean = false
    ) = autoDetectTabMode(smsPermissionGranted, isScanningQueue, isAiEnriching, queueIsEmpty, isModelDownloaded)

    @Test
    fun `queue wins over the model prompt`() {
        assertEquals(QUEUE, mode(queueIsEmpty = false, isModelDownloaded = false))
    }

    @Test
    fun `model prompt only for an empty queue`() {
        assertEquals(MODEL_PROMPT, mode(queueIsEmpty = true, isModelDownloaded = false))
    }

    @Test
    fun `empty queue with the model downloaded shows the empty state`() {
        assertEquals(EMPTY, mode(queueIsEmpty = true, isModelDownloaded = true))
    }

    @Test
    fun `permission precedes everything`() {
        assertEquals(
            PERMISSION,
            mode(smsPermissionGranted = false, queueIsEmpty = false, isModelDownloaded = true)
        )
        assertEquals(
            PERMISSION,
            mode(smsPermissionGranted = false, queueIsEmpty = true, isScanningQueue = true)
        )
    }

    @Test
    fun `scanning precedes the queue`() {
        assertEquals(SCANNING, mode(isScanningQueue = true, queueIsEmpty = false))
    }

    @Test
    fun `enriching only takes over while the queue is empty`() {
        assertEquals(SCANNING, mode(isAiEnriching = true, queueIsEmpty = true))
        assertEquals(QUEUE, mode(isAiEnriching = true, queueIsEmpty = false))
    }
}
