package com.awbuilds.auraspend.ui.classification

/**
 * Which body the Auto Detect tab renders.
 *
 * Extracted from the composable so the precedence is unit-tested. The load-bearing
 * rule is [AutoDetectTabMode.QUEUE]: a non-empty queue renders **regardless of the
 * optional on-device model**. Before this, `!isModelDownloaded` short-circuited
 * ahead of the queue, so a user who skipped the 22 MB model saw no queue, no
 * progress header and none of the triage actions — while the pipeline, the
 * notifications and the database all worked perfectly underneath. Measured on the
 * owner's device (2026-10-13): 150 messages draining with the tab showing nothing
 * but the download prompt.
 *
 * The model prompt is only for the empty queue — the moment a user would
 * actually consider it.
 */
internal enum class AutoDetectTabMode { PERMISSION, SCANNING, QUEUE, MODEL_PROMPT, EMPTY }

/**
 * @param queueIsEmpty `state.pendingSmsList.isEmpty()` — rows awaiting a decision.
 */
internal fun autoDetectTabMode(
    smsPermissionGranted: Boolean,
    isScanningQueue: Boolean,
    isAiEnriching: Boolean,
    queueIsEmpty: Boolean,
    isModelDownloaded: Boolean
): AutoDetectTabMode = when {
    // Nothing can be shown or done without the permission.
    !smsPermissionGranted -> AutoDetectTabMode.PERMISSION
    // A one-line progress state while the scan/enrichment phase owns the screen.
    isScanningQueue || (isAiEnriching && queueIsEmpty) -> AutoDetectTabMode.SCANNING
    // The queue always wins over the model prompt.
    !queueIsEmpty -> AutoDetectTabMode.QUEUE
    // Queue empty: pitch the optional categoriser, or show the generic empty state.
    !isModelDownloaded -> AutoDetectTabMode.MODEL_PROMPT
    else -> AutoDetectTabMode.EMPTY
}
