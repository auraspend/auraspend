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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.data.ai.AiModelState
import com.awbuilds.auraspend.data.classification.ClassifiedSms
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
import com.awbuilds.auraspend.ui.designsystem.LocalCurrencyStyle
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)

// Paste/parse flow: step indicator, analysing state, result card, category and manual entry.

@Composable
internal fun PasteMessageTab(
    state: ClassificationViewState,
    viewModel: ClassificationViewModel
) {
    var showCategoryDialog by remember { mutableStateOf(false) }

    // Wizard position: input → analyzing → review.
    val step = when {
        state.parsedMessage == null -> 0
        state.isAiEnriching && !state.aiRefined -> 1
        else -> 2
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AuraSpacing.gutter,
            vertical = AuraSpacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(AuraSpacing.lg)
    ) {
        item {
            ClassificationStepIndicator(currentStep = step)
        }

        item {
            OutlinedTextField(
                value = state.rawMessage,
                onValueChange = {
                    viewModel.handleIntent(ClassificationViewIntent.MessageChanged(it))
                },
                label = { Text(stringResource(R.string.classification_paste_label)) },
                placeholder = { Text(stringResource(R.string.classification_paste_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                minLines = 5,
                maxLines = 8,
                shape = RoundedCornerShape(14.dp)
            )
        }

        if (step == 0) {
            item {
                Button(
                    onClick = { viewModel.handleIntent(ClassificationViewIntent.ClassifyMessage) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    enabled = state.rawMessage.isNotBlank(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.classification_classify), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (step == 1) {
            item { AnalyzingCard() }
        }

        if (step == 2) {
            item {
                ClassificationResultCard(
                    state = state,
                    viewModel = viewModel,
                    onOpenCategoryDialog = { showCategoryDialog = true }
                )
            }

            item {
                Button(
                    onClick = { viewModel.handleIntent(ClassificationViewIntent.SaveTransaction) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    enabled = !state.isSaving,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.classification_save_transaction), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // A failed save is reported inline: the user keeps their work on screen
            // and the banner is announced, instead of a bare red line of text.
            state.error?.let { error ->
                item {
                    AuraErrorBanner(
                        message = stringResource(error.messageRes),
                        onDismiss = { viewModel.handleIntent(ClassificationViewIntent.ClearError) }
                    )
                }
            }
        }
    }

    if (showCategoryDialog) {
        HierarchicalCategoryDialog(
            categories = state.availableCategories,
            selectedCategoryId = state.selectedCategoryId,
            onCategorySelected = { id ->
                viewModel.handleIntent(ClassificationViewIntent.SelectCategory(id))
            },
            onDismiss = { showCategoryDialog = false },
            merchantSuggestion = if (state.merchantConfidence > 0) {
                Pair(state.parsedMessage?.merchant ?: stringResource(R.string.classification_unknown_merchant), state.merchantConfidence)
            } else null
        )
    }
}

@Composable
internal fun ClassificationStepIndicator(currentStep: Int) {
    val steps = listOf(
        R.string.classification_step_message,
        R.string.classification_step_analyzing,
        R.string.classification_step_review
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
    ) {
        steps.forEachIndexed { index, labelRes ->
            val reached = index <= currentStep
            val activeColor by animateColorAsState(
                targetValue = if (reached) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "stepColor"
            )
            val contentColor = if (reached) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(activeColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < currentStep) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor
                        )
                    }
                }
                Spacer(modifier = Modifier.width(AuraSpacing.xs))
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (index == currentStep) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (index == currentStep) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Step 2 of the wizard: an on-device analysis in progress. */
@Composable
internal fun AnalyzingCard() {
    AuraCard(style = AuraCardStyle.Tonal, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(AuraSpacing.md))
            Column {
                Text(
                    stringResource(R.string.classification_analyzing_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.classification_analyzing_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(AuraSpacing.lg))
        AuraSkeleton(modifier = Modifier.fillMaxWidth().height(14.dp))
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        AuraSkeleton(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        AuraSkeleton(modifier = Modifier.fillMaxWidth(0.45f).height(14.dp))
    }
}

@Composable
internal fun ClassificationResultCard(
    state: ClassificationViewState,
    viewModel: ClassificationViewModel,
    onOpenCategoryDialog: () -> Unit = {}
) {
    val parsed = state.parsedMessage ?: return

    AuraCard(
        modifier = Modifier.fillMaxWidth(),
        style = AuraCardStyle.Tonal,
        contentPadding = PaddingValues(AuraSpacing.lg)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AuraSpacing.md)
        ) {
            Text(
                stringResource(R.string.classification_detected_details),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            ConfidenceBadge(confidence = parsed.confidence)

            // Live AI status: refining in flight, or refined result applied.
            if (state.isAiEnriching && !state.aiRefined) {
                AiStatusBadge(text = stringResource(R.string.classification_ai_refining), tint = MaterialTheme.colorScheme.tertiary)
            } else if (state.aiRefined) {
                AiStatusBadge(text = stringResource(R.string.classification_ai_refined), tint = MaterialTheme.colorScheme.primary)
            }

            // Show merchant confidence if available
            if (state.merchantConfidence > 0) {
                MerchantConfidenceBadge(
                    merchant = parsed.merchant ?: stringResource(R.string.classification_unknown_merchant),
                    confidence = state.merchantConfidence
                )
            }

            AmountDetailRow(amount = parsed.amount)

            // Localized copy, not `type.name`: the enum's identifier is not a label,
            // and the mapping's `else` arm keeps a future value from becoming one.
            val typeLabel = parsed.type?.let { stringResource(it.transactionTypeLabelRes()) }
                ?: stringResource(R.string.classification_not_detected)
            DetailRow(
                label = stringResource(R.string.classification_type),
                value = typeLabel
            )

            DetailRow(
                label = stringResource(R.string.classification_merchant),
                value = parsed.merchant ?: stringResource(R.string.classification_not_detected)
            )

            DetailRow(
                label = stringResource(R.string.classification_bank),
                value = parsed.bankName ?: stringResource(R.string.classification_not_detected)
            )

            Text(
                stringResource(R.string.classification_category),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
            ) {
                CategorySelector(
                    categories = state.availableCategories,
                    selectedCategoryId = state.selectedCategoryId,
                    onCategorySelected = { id ->
                        viewModel.handleIntent(ClassificationViewIntent.SelectCategory(id))
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onOpenCategoryDialog,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("…", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider()

            TextButton(
                onClick = { viewModel.handleIntent(ClassificationViewIntent.ToggleManualEntry) }
            ) {
                Text(
                    if (state.useManualEntry) stringResource(R.string.classification_use_detected)
                    else stringResource(R.string.classification_edit_manually)
                )
            }

            if (state.useManualEntry) {
                ManualEntryFields(state, viewModel)
            }
        }
    }
}

/**
 * Rounded tinted pill shared by the AI/confidence badges — one line, quiet,
 * and consistent with the badge style used across the app.
 */
@Composable
internal fun TintBadge(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = AuraSpacing.md, vertical = AuraSpacing.xs)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
internal fun AiStatusBadge(text: String, tint: Color) = TintBadge(text = text, tint = tint)

@Composable
internal fun MerchantConfidenceBadge(merchant: String, confidence: Float) {
    val color = when {
        confidence >= 0.9f -> MaterialTheme.colorScheme.primary
        confidence >= 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    TintBadge(
        text = stringResource(R.string.classification_merchant_confidence, merchant, (confidence * 100).toInt()),
        tint = color
    )
}

@Composable
internal fun ConfidenceBadge(confidence: Float) {
    val color = when {
        confidence >= 0.8f -> MaterialTheme.colorScheme.primary
        confidence >= 0.5f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val label = when {
        confidence >= 0.8f -> stringResource(R.string.classification_confidence_high)
        confidence >= 0.5f -> stringResource(R.string.classification_confidence_medium)
        else -> stringResource(R.string.classification_confidence_low)
    }

    TintBadge(text = label, tint = color)
}

@Composable
internal fun AmountDetailRow(amount: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.classification_amount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (amount != null) {
            AnimatedMoney(
                amount = amount,
                style = AuraType.moneySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Text(
                text = stringResource(R.string.classification_not_detected),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategorySelector(
    categories: List<com.awbuilds.auraspend.domain.model.Category>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) {
        Text(stringResource(R.string.classification_no_categories), style = MaterialTheme.typography.bodySmall)
        return
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onCategorySelected(category.id) },
                label = {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    CategoryAvatar(
                        icon = category.icon,
                        color = Color(category.color.toLong()),
                        size = 22.dp
                    )
                },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

@Composable
internal fun ManualEntryFields(
    state: ClassificationViewState,
    viewModel: ClassificationViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(AuraSpacing.md)) {
        OutlinedTextField(
            value = state.manualAmount,
            onValueChange = { viewModel.handleIntent(ClassificationViewIntent.AmountChanged(it)) },
            label = { Text(stringResource(R.string.classification_amount)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            // The theme currency, not a hardcoded ₹: the field holds raw digits, so
            // the prefix is the only symbol here and cannot double one.
            prefix = { Text(LocalCurrencyStyle.current.symbol) },
            shape = RoundedCornerShape(14.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)
        ) {
            FilterChip(
                selected = state.manualType == TransactionType.EXPENSE,
                onClick = { viewModel.handleIntent(ClassificationViewIntent.TypeChanged(TransactionType.EXPENSE)) },
                label = { Text(stringResource(R.string.classification_manual_expense)) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = state.manualType == TransactionType.INCOME,
                onClick = { viewModel.handleIntent(ClassificationViewIntent.TypeChanged(TransactionType.INCOME)) },
                label = { Text(stringResource(R.string.classification_manual_income)) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = state.manualMerchant,
            onValueChange = { viewModel.handleIntent(ClassificationViewIntent.MerchantChanged(it)) },
            label = { Text(stringResource(R.string.classification_manual_merchant)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        OutlinedTextField(
            value = state.manualNote,
            onValueChange = { viewModel.handleIntent(ClassificationViewIntent.NoteChanged(it)) },
            label = { Text(stringResource(R.string.classification_manual_note)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
internal fun SmsPermissionRequired(onGrant: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AuraEmptyState(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.classification_permission_title),
            message = stringResource(R.string.classification_permission_message),
            actionLabel = stringResource(R.string.classification_grant_permission),
            onAction = onGrant
        )
    }
}
