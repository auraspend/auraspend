package com.awbuilds.auraspend.ui.classification

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.data.ai.AiModelState
import com.awbuilds.auraspend.data.classification.ClassifiedSms
import com.awbuilds.auraspend.data.classification.SmsStatus
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.core.isNotificationPermissionNeeded
import com.awbuilds.auraspend.ui.core.rememberNotificationPermissionLauncher
import com.awbuilds.auraspend.ui.designsystem.AnimatedMoney
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraSegmentedControl
import com.awbuilds.auraspend.ui.designsystem.AuraSkeleton
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.CategoryAvatar
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.awbuilds.auraspend.ui.designsystem.SwipeToAct

@OptIn(ExperimentalMaterial3Api::class)

// SMS triage: permission gate, message list, swipe accept/reject, AI download status.

@Composable
internal fun SmsListTab(
    state: ClassificationViewState,
    viewModel: ClassificationViewModel,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (!state.smsPermissionGranted) {
        SmsPermissionRequired { permissionLauncher.launch(Manifest.permission.READ_SMS) }
    } else if (state.smsMessages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AuraEmptyState(
                icon = Icons.Default.Sms,
                title = stringResource(R.string.classification_no_sms_title),
                message = stringResource(R.string.classification_no_sms_message)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SmsTabError(state, viewModel)

            // No progress bar here on purpose. This tab is for picking a message to
            // classify by hand; the live drain status belongs to the triage tab below,
            // which renders the same queue rows as they are categorized.

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = AuraSpacing.gutter,
                    vertical = AuraSpacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
            ) {
                items(state.smsMessages) { sms ->
                    SmsItem(
                        sms = sms,
                        onClick = {
                            viewModel.handleIntent(ClassificationViewIntent.SmsSelected(sms))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmsItem(
    sms: SmsInfo,
    onClick: () -> Unit
) {
    AuraCard(
        modifier = Modifier.fillMaxWidth(),
        style = AuraCardStyle.Outlined,
        contentPadding = PaddingValues(AuraSpacing.md),
        onClick = onClick
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AuraSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sms.address,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTimestamp(sms.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = sms.body,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Live drain status for the triage tab.
 *
 * The numbers are derived from the observed queue, not reported by a worker, so this and the
 * background notification are the same numbers by construction. The previous pair of fields
 * behind this UI (`aiProgressCurrent` / `aiProgressTotal`) was never assigned, so the bar it
 * rendered was unreachable code.
 */
@Composable
private fun CategorisationProgress(state: ClassificationViewState) {
    when {
        state.isCategorising && state.progressTotal > 0 -> {
            Column {
                LinearProgressIndicator(
                    progress = {
                        state.progressCurrent.toFloat() / state.progressTotal.toFloat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    stringResource(
                        R.string.classification_ai_progress,
                        state.progressCurrent,
                        state.progressTotal
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = AuraSpacing.gutter,
                        vertical = AuraSpacing.xs
                    )
                )
            }
        }

        // Queued but not currently in flight: the loop is between passes (or the
        // toggle is off, in which case these rows will wait for the user).
        state.queuedCount > 0 -> {
            Text(
                pluralStringResource(
                    R.plurals.sms_categorizing_waiting,
                    state.queuedCount,
                    state.queuedCount
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = AuraSpacing.gutter,
                    vertical = AuraSpacing.xs
                )
            )
        }
    }

    // The same "saved · skipped" line the notification's summary uses, so the two surfaces
    // describe a drain in the same words.
    if (state.progressTotal > 0 && state.savedCount + state.skippedCount > 0) {
        Text(
            stringResource(
                R.string.sms_categorizing_summary,
                state.savedCount,
                state.skippedCount
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = AuraSpacing.gutter,
                vertical = AuraSpacing.xs
            )
        )
    }
}

/**
 * This tab's own error surface.
 *
 * The triage tab never read `state.error`, so a save that failed (no amount found,
 * or a database failure) looked exactly like one that succeeded — and because
 * `ClassificationReviewTab` does read it, `ErrorStateWiringTest` was satisfied for
 * the whole feature. Dismissing clears it through the existing `ClearError` intent.
 */
@Composable
private fun SmsTabError(
    state: ClassificationViewState,
    viewModel: ClassificationViewModel
) {
    state.error?.let { error ->
        AuraErrorBanner(
            message = stringResource(error.messageRes),
            modifier = Modifier.padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.sm),
            onDismiss = { viewModel.handleIntent(ClassificationViewIntent.ClearError) }
        )
    }
}

/**
 * The parsed transaction type as copy, shared by both classification tabs.
 *
 * `TransactionType` is a domain enum, so `parsed.type.name.lowercase()` rendered an
 * identifier rather than a label. Deliberately exhaustive: an `else` arm would
 * silence the compiler when a value is added, and the new value would reach the
 * screen as a raw identifier. Without it, adding one is a compile error here.
 */
internal fun TransactionType.transactionTypeLabelRes(): Int = when (this) {
    TransactionType.EXPENSE -> R.string.txn_type_expense
    TransactionType.INCOME -> R.string.txn_type_income
}

@Composable
internal fun AutoDetectTab(
    state: ClassificationViewState,
    viewModel: ClassificationViewModel,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onTriageSwipe: (smsId: String, save: Boolean) -> Unit = { _, _ -> }
) {
    val isModelDownloaded = state.aiModelState is AiModelState.Ready

    // Which body renders is decided by [autoDetectTabMode] — a pure function, so
    // the precedence rules are unit-tested rather than read off this file. The
    // load-bearing one: a non-empty queue renders even when the optional model
    // was never downloaded (the queue used to hide behind the model prompt).
    when (
        autoDetectTabMode(
            smsPermissionGranted = state.smsPermissionGranted,
            isScanningQueue = state.isScanningQueue,
            isAiEnriching = state.isAiEnriching,
            queueIsEmpty = state.pendingSmsList.isEmpty(),
            isModelDownloaded = isModelDownloaded
        )
    ) {
        AutoDetectTabMode.PERMISSION -> {
        SmsPermissionRequired { permissionLauncher.launch(Manifest.permission.READ_SMS) }
        }
        AutoDetectTabMode.MODEL_PROMPT -> {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AuraEmptyState(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.classification_local_ai_title),
                    message = stringResource(R.string.classification_local_ai_message)
                )
                Button(
                    onClick = { viewModel.handleIntent(ClassificationViewIntent.StartModelDownload) },
                    enabled = state.aiModelState !is AiModelState.Downloading,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(horizontal = AuraSpacing.xxl)
                        .fillMaxWidth()
                        // Grows with the label instead of clipping it at large font scales.
                        .heightIn(min = 52.dp)
                ) {
                    if (state.aiModelState is AiModelState.Downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(AuraSpacing.sm))
                        Text(stringResource(R.string.classification_downloading))
                    } else {
                        Text(stringResource(R.string.classification_download_model), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        }
        AutoDetectTabMode.SCANNING -> {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(AuraSpacing.xxxl)
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(AuraSpacing.lg))
                Text(
                    if (state.isAiEnriching) stringResource(R.string.classification_ai_categorizing)
                    else stringResource(R.string.classification_reading_sms),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }
        AutoDetectTabMode.EMPTY -> {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AuraSpacing.xl),
                contentAlignment = Alignment.Center
            ) {
                AuraEmptyState(
                    icon = Icons.Default.Sms,
                    title = stringResource(R.string.classification_no_sms_title),
                    message = stringResource(R.string.classification_scan_message),
                    actionLabel = stringResource(R.string.classification_scan_sms),
                    onAction = {
                        viewModel.handleIntent(ClassificationViewIntent.LoadAndClassifyAll)
                    }
                )
            }
            // Nothing is awaiting a decision, but the skipped history is still
            // the useful body of this tab — it is the only place those messages
            // are visible.
            if (state.skippedSmsList.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = AuraSpacing.gutter,
                        vertical = AuraSpacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                ) {
                    skippedItems(state, viewModel)
                }
            }
        }
        }
        AutoDetectTabMode.QUEUE -> {
        Column(modifier = Modifier.fillMaxSize()) {
            // The triage errors surface here: a save that found no transaction, a
            // failed dismiss, or a failed "Save All" all land in `state.error`.
            SmsTabError(state, viewModel)
            CategorisationProgress(state)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = AuraSpacing.gutter,
                    vertical = AuraSpacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = AuraSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.classification_messages_found,
                                state.pendingSmsList.size,
                                state.pendingSmsList.size
                            ),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (state.pendingSmsList.isNotEmpty()) {
                            Column(horizontalAlignment = Alignment.End) {
                                Button(
                                    onClick = {
                                        viewModel.handleIntent(ClassificationViewIntent.SaveAllClassified)
                                    },
                                    enabled = !state.isSavingAll,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    if (state.isSavingAll) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text(stringResource(R.string.classification_save_all), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                TextButton(
                                    onClick = {
                                        viewModel.handleIntent(ClassificationViewIntent.SkipAllClassified)
                                    },
                                    enabled = !state.isSavingAll,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(stringResource(R.string.classification_skip_all))
                                }
                                Spacer(modifier = Modifier.height(AuraSpacing.xxs))
                                Text(
                                    stringResource(R.string.classification_swipe_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                items(state.pendingSmsList, key = { it.sms.id }) { classified ->
                    // Triage inbox: swipe right to save, left to dismiss.
                    //
                    // Everything here is awaiting a decision — saved and dismissed
                    // messages are filtered out by `pendingSmsList`, so this branch
                    // used to carry a dead `if (classified.isSaved)` arm rendering a
                    // saved card with no-op buttons. Handling a message now removes
                    // it from the list; undo reverts the status and it returns.
                    //
                    // This used to pass `confirmValueChange`, which is deprecated
                    // without replacement in this Compose version — the callback is
                    // never called, so the card parked at the swiped anchor showing
                    // a full-height green or red block with the content translated
                    // away, and the action was never dispatched. Caught on-device
                    // 2026-09-29: after swiping, the list still read "6 messages
                    // found" and the cards reset on re-entry, i.e. the gesture did
                    // nothing at all.
                    //
                    // `SwipeToAct` drives it from the state that actually settles.
                    // Both actions are undoable — a save creates a transaction and a
                    // dismiss hides a message, so neither should be one accidental
                    // gesture from being permanent.
                    SwipeToAct(
                        onAction = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd ->
                                    onTriageSwipe(classified.sms.id, true)
                                SwipeToDismissBoxValue.EndToStart ->
                                    onTriageSwipe(classified.sms.id, false)
                                else -> Unit
                            }
                        },
                        modifier = Modifier.animateItem(),
                        backgroundContent = { direction -> TriageSwipeBackground(direction) }
                    ) {
                        ClassifiedSmsItem(
                            classified = classified,
                            // Routed through the same callback as the swipe, so the
                            // buttons and the gesture behave identically: both
                            // remove the message from the inbox and both offer
                            // undo. Dispatching the intent directly here gave the
                            // buttons a permanent delete while the identical swipe
                            // was undoable.
                            onSave = { onTriageSwipe(classified.sms.id, true) },
                            onDismiss = { onTriageSwipe(classified.sms.id, false) }
                        )
                    }
                }

                skippedItems(state, viewModel)
            }
        }
    }
    }
}

@Composable
private fun TriageSwipeBackground(direction: SwipeToDismissBoxValue) {
    val isSave = direction == SwipeToDismissBoxValue.StartToEnd
    val container = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            MaterialTheme.extendedColors.incomeAmount.copy(alpha = 0.18f)
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
        else -> Color.Transparent
    }
    val contentColor = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.extendedColors.incomeAmount
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(container),
        contentAlignment = if (isSave) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = if (isSave) Icons.Default.Check else Icons.Default.Close,
                contentDescription = if (isSave) stringResource(R.string.classification_save_desc)
                else stringResource(R.string.classification_dismiss_desc),
                tint = contentColor,
                modifier = Modifier.padding(horizontal = AuraSpacing.xxl)
            )
        }
    }
}

@Composable
private fun ClassifiedSmsItem(
    classified: ClassifiedSms,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val parsed = classified.parsed
    AuraCard(
        modifier = Modifier.fillMaxWidth(),
        style = if (classified.isSaved) AuraCardStyle.Tonal else AuraCardStyle.Outlined,
        contentPadding = PaddingValues(AuraSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = classified.sms.address,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatTimestamp(classified.sms.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    parsed.amount?.let { amount ->
                        AnimatedMoney(
                            amount = amount,
                            style = AuraType.moneySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            animate = false
                        )
                    } ?: Text(
                        // The theme currency, not a hardcoded ₹, and the currency's
                        // own fraction convention rather than a fixed "0.00".
                        text = formatMoney(0.0),
                        style = AuraType.moneySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    parsed.type?.let { type ->
                        // Localized label, never `type.name`.
                        val typeLabel = stringResource(type.transactionTypeLabelRes())
                        Text(
                            text = "• $typeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (parsed.merchant != null) {
                        Text(
                            text = "• ${parsed.merchant}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = classified.sms.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                // In-flight marker for this exact message: the queue row is CLASSIFYING
                // while a background pass is working through it. The user asked to watch
                // the categorisation happen one-by-one, and this is that row.
                if (classified.status == SmsStatus.CLASSIFYING) {
                    TintBadge(
                        text = stringResource(R.string.sms_categorizing_row),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (!classified.isSaved) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AuraSpacing.xs),
                    modifier = Modifier.padding(start = AuraSpacing.sm)
                ) {
                    FilledTonalButton(
                        onClick = onSave,
                        // 48dp, the AGENTS.md touch-target floor: these two buttons
                        // are the tap alternative to the triage swipe.
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = stringResource(R.string.action_save),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_dismiss),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                TintBadge(
                    text = stringResource(R.string.classification_saved),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
internal fun AiDownloadStatusBanner(progress: Float) {
    AuraCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.sm),
        style = AuraCardStyle.Tonal,
        contentPadding = PaddingValues(AuraSpacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)) {
            Text(
                stringResource(R.string.classification_download_progress, (progress * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.classification_download_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The audit surface for skipped messages: every message the pipeline (or the
 * user) set aside, with the way back in. Kept out of the inbox on purpose —
 * this section is for looking, not for deciding again. "Classify again"
 * re-queues the row, which is how a parser fix reaches old messages.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.skippedItems(
    state: ClassificationViewState,
    viewModel: ClassificationViewModel
) {
    val skipped = state.skippedSmsList
    if (skipped.isEmpty()) return
    item(key = "skipped_header") {
        Column(modifier = Modifier.padding(top = AuraSpacing.md)) {
            Text(
                stringResource(R.string.classification_skipped_title, skipped.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(AuraSpacing.xxs))
            Text(
                stringResource(R.string.classification_skipped_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    items(skipped, key = { "skipped_${it.sms.id}" }) { classified ->
        SkippedMessageRow(classified) {
            viewModel.handleIntent(ClassificationViewIntent.RestoreSkippedSms(classified.sms.id))
        }
    }
}

@Composable
private fun SkippedMessageRow(classified: ClassifiedSms, onRestore: () -> Unit) {
    AuraCard(modifier = Modifier.fillMaxWidth(), style = AuraCardStyle.Outlined) {
        Column {
            Text(
                classified.sms.address,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(AuraSpacing.xxs))
            Text(
                classified.sms.body.replace('\n', ' ').trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(AuraSpacing.xs))
            TextButton(
                onClick = onRestore,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(AuraSpacing.xs))
                Text(stringResource(R.string.classification_classify_again))
            }
        }
    }
}
