package com.awbuilds.auraspend.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.domain.model.RecurrenceFrequency
import com.awbuilds.auraspend.domain.model.Subscription
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.ui.core.UiError
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.LocalCurrencyStyle
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RecurringScreen(
    repository: TransactionRepository,
    onBack: () -> Unit
) {
    val subscriptions by repository.getActiveSubscriptions().collectAsState(initial = emptyList())
    // Payments the classifier flagged recurring at ingest. Suggestions, not
    // tracked entries: the section below turns one into a tracked subscription
    // on the user's tap.
    val recurringPayments by repository.getRecurringTransactions().collectAsState(initial = emptyList())
    val context = LocalContext.current
    var dismissedSuggestions by remember { mutableStateOf(SuggestionDismissals.load(context)) }
    val detected = remember(recurringPayments, subscriptions, dismissedSuggestions) {
        detectedSubscriptions(
            recurringPayments,
            trackedNames = subscriptions.map { it.name },
            dismissedNames = dismissedSuggestions
        )
    }
    // Non-null while the sheet edits an existing subscription instead of adding one.
    var editingSubscription by remember { mutableStateOf<Subscription?>(null) }
    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showAddSheet by remember { mutableStateOf(false) }
    var subscriptionName by remember { mutableStateOf("") }
    var subscriptionAmount by remember { mutableStateOf("") }
    var subscriptionCategory by remember { mutableStateOf("") }
    var subscriptionCycle by remember { mutableStateOf(RecurrenceFrequency.MONTHLY) }

    // Removing a subscription is destructive and had no confirmation, unlike every
    // other destructive action in the app. The pending row lives here until the
    // user confirms.
    var confirmRemove by remember { mutableStateOf<Subscription?>(null) }

    // A write that failed. Neither the add sheet nor the remove dialog is dismissed
    // on failure, so the form survives and the banner explains what happened.
    var error by remember { mutableStateOf<UiError?>(null) }
    val errorMessage = error?.let { stringResource(it.messageRes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        error = null
                        showAddSheet = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.recurring_add))
                    }
                }
            )
        }
    ) { padding ->
        if (subscriptions.isEmpty() && detected.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                AuraEmptyState(
                    icon = Icons.Default.Subscriptions,
                    title = stringResource(R.string.recurring_empty_title),
                    message = stringResource(R.string.recurring_empty_message)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    horizontal = AuraSpacing.gutter,
                    vertical = AuraSpacing.lg
                ),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.md)
            ) {
                if (subscriptions.isNotEmpty()) {
                    item {
                        // Monthly-normalised: weekly ×52/12, yearly ÷12, daily ×30 —
                        // the old card summed raw amounts and mislabeled them "/mo".
                        val monthlyEquivalent = subscriptions.sumOf { sub ->
                            sub.amount * when (sub.billingCycle) {
                                RecurrenceFrequency.DAILY -> 30.0
                                RecurrenceFrequency.WEEKLY -> 52.0 / 12.0
                                RecurrenceFrequency.MONTHLY -> 1.0
                                RecurrenceFrequency.YEARLY -> 1.0 / 12.0
                            }
                        }
                        AuraCard(
                            modifier = Modifier.fillMaxWidth(),
                            style = AuraCardStyle.Tonal
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.recurring_monthly),
                                        style = AuraType.metricLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                    Text(
                                        stringResource(R.string.recurring_monthly_amount, formatMoney(monthlyEquivalent)),
                                        style = AuraType.moneyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (subscriptions.any { it.billingCycle != RecurrenceFrequency.MONTHLY }) {
                                        Spacer(modifier = Modifier.height(AuraSpacing.xs))
                                        Text(
                                            stringResource(R.string.recurring_normalised),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.Subscriptions,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    items(subscriptions) { sub ->
                        val category = categories.find { it.id == sub.categoryId }
                        val categoryColor = category?.let { Color(it.color.toLong()) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant
                        AuraCard(
                            modifier = Modifier.fillMaxWidth(),
                            style = AuraCardStyle.Outlined
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Subscriptions,
                                        contentDescription = null,
                                        tint = categoryColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(AuraSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        sub.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(AuraSpacing.xxs))
                                    Text(
                                        stringResource(
                                            R.string.recurring_category_cycle,
                                            category?.name ?: stringResource(R.string.recurring_other_category),
                                            stringResource(sub.billingCycle.labelRes())
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatMoney(sub.amount),
                                        style = AuraType.moneySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(
                                        LocalDateTime.now().toLocalDate(),
                                        sub.nextBillingDate.toLocalDate()
                                    )
                                    val dueLabel = when {
                                        daysUntil < 0L -> stringResource(R.string.recurring_overdue)
                                        daysUntil == 0L -> stringResource(R.string.recurring_due_today)
                                        daysUntil == 1L -> stringResource(R.string.recurring_tomorrow)
                                        else -> stringResource(
                                            R.string.recurring_next,
                                            sub.nextBillingDate.format(DateTimeFormatter.ofPattern("dd MMM"))
                                        )
                                    }
                                    val urgent = daysUntil <= 3L
                                    Spacer(modifier = Modifier.height(AuraSpacing.xxs))
                                    Text(
                                        dueLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (daysUntil < 0L) MaterialTheme.colorScheme.error
                                        else if (urgent) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    error = null
                                    editingSubscription = sub
                                    subscriptionName = sub.name
                                    subscriptionAmount = fieldAmount(sub.amount)
                                    subscriptionCategory = sub.categoryId
                                    subscriptionCycle = sub.billingCycle
                                    showAddSheet = true
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.action_edit),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    error = null
                                    confirmRemove = sub
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.action_remove),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (detected.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.recurring_detected_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }
                            )
                            Spacer(modifier = Modifier.height(AuraSpacing.xxs))
                            Text(
                                stringResource(R.string.recurring_detected_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(detected, key = { it.name.lowercase() }) { det ->
                        val detCategory = categories.find { it.id == det.categoryId }
                        val detColor = detCategory?.let { Color(it.color.toLong()) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant
                        AuraCard(modifier = Modifier.fillMaxWidth(), style = AuraCardStyle.Outlined) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(detColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Subscriptions,
                                        contentDescription = null,
                                        tint = detColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(AuraSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        det.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(AuraSpacing.xxs))
                                    Text(
                                        stringResource(
                                            R.string.recurring_detected_last_paid,
                                            det.lastPaid.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatMoney(det.amount),
                                        style = AuraType.moneySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(AuraSpacing.xs))
                                TextButton(
                                    onClick = {
                                        error = null
                                        editingSubscription = null
                                        subscriptionName = det.name
                                        subscriptionAmount = fieldAmount(det.amount)
                                        subscriptionCategory =
                                            det.categoryId?.takeIf { id -> categories.any { it.id == id } }
                                                ?: categories.firstOrNull()?.id
                                                ?: ""
                                        subscriptionCycle = RecurrenceFrequency.MONTHLY
                                        showAddSheet = true
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(stringResource(R.string.recurring_track))
                                }
                                IconButton(onClick = {
                                    dismissedSuggestions =
                                        SuggestionDismissals.dismiss(context, det.name)
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.recurring_detected_dismiss),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false; editingSubscription = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.xxl)
                    .padding(bottom = AuraSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.xl)
            ) {
                // A failed save is reported inside the sheet — where the user is
                // looking — instead of the sheet dismissing as if it had saved.
                errorMessage?.let { message ->
                    AuraErrorBanner(message = message)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Subscriptions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            stringResource(
                                if (editingSubscription != null) R.string.recurring_edit
                                else R.string.recurring_add
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.recurring_add_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = subscriptionName,
                    onValueChange = { subscriptionName = it },
                    label = { Text(stringResource(R.string.recurring_name)) },
                    placeholder = { Text(stringResource(R.string.recurring_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                OutlinedTextField(
                    value = subscriptionAmount,
                    onValueChange = { subscriptionAmount = it },
                    label = { Text(stringResource(R.string.recurring_amount)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    // Theme currency, not a hardcoded ₹; the field holds raw digits,
                    // so the prefix cannot double a symbol. The decorative
                    // `Icons.Default.CurrencyRupee` beside it was rupee-specific in
                    // every locale and is gone with it.
                    prefix = { Text(LocalCurrencyStyle.current.symbol) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Column(verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)) {
                    Text(
                        stringResource(R.string.recurring_billing_cycle),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                    ) {
                        RecurrenceFrequency.entries.forEach { cycle ->
                            FilterChip(
                                selected = subscriptionCycle == cycle,
                                onClick = { subscriptionCycle = cycle },
                                label = {
                                    Text(
                                        stringResource(cycle.labelRes()),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)) {
                    Text(
                        stringResource(R.string.recurring_category),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (categories.isEmpty()) {
                        Text(
                            stringResource(R.string.recurring_no_categories),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Wrapping chip flow shows every category without a scroll.
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AuraSpacing.xs)
                        ) {
                            categories.forEach { cat ->
                                val catColor = Color(cat.color.toLong())
                                FilterChip(
                                    selected = subscriptionCategory == cat.id,
                                    onClick = { subscriptionCategory = cat.id },
                                    label = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.xs + AuraSpacing.xxs)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(catColor)
                                            )
                                            Text(cat.name, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val amt = subscriptionAmount.toDoubleOrNull() ?: return@Button
                        if (subscriptionName.isBlank() || subscriptionCategory.isBlank()) return@Button
                        // Next billing follows the chosen cycle instead of a hardcoded month.
                        val now = LocalDateTime.now()
                        val next = when (subscriptionCycle) {
                            RecurrenceFrequency.DAILY -> now.plusDays(1)
                            RecurrenceFrequency.WEEKLY -> now.plusWeeks(1)
                            RecurrenceFrequency.MONTHLY -> now.plusMonths(1)
                            RecurrenceFrequency.YEARLY -> now.plusYears(1)
                        }
                        scope.launch {
                            // The sheet used to close before the write was attempted, so a
                            // Room failure looked exactly like a save. It now closes only
                            // after the write succeeds; the form survives a failure.
                            val target = editingSubscription
                            val saved = boundary(TAG, false) {
                                if (target != null) {
                                    // Edit in place: same id, stored next billing date
                                    // kept (the sheet has no date field; recomputing it
                                    // would silently move the due date).
                                    repository.saveSubscription(
                                        target.copy(
                                            name = subscriptionName.trim(),
                                            amount = amt,
                                            categoryId = subscriptionCategory,
                                            billingCycle = subscriptionCycle
                                        )
                                    )
                                } else {
                                    repository.saveSubscription(
                                        Subscription(
                                            name = subscriptionName.trim(),
                                            amount = amt,
                                            categoryId = subscriptionCategory,
                                            billingCycle = subscriptionCycle,
                                            nextBillingDate = next
                                        )
                                    )
                                }
                                true
                            }
                            if (saved) {
                                error = null
                                showAddSheet = false
                                editingSubscription = null
                                subscriptionName = ""
                                subscriptionAmount = ""
                                subscriptionCategory = ""
                            } else {
                                error = UiError.SAVE_FAILED
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = subscriptionName.isNotBlank() &&
                            subscriptionAmount.toDoubleOrNull() != null &&
                            subscriptionCategory.isNotBlank()
                ) {
                    Text(
                        stringResource(
                            if (editingSubscription != null) R.string.recurring_edit
                            else R.string.recurring_add
                        )
                    )
                }
            }
        }
    }

    confirmRemove?.let { sub ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = {
                Text(
                    stringResource(R.string.recurring_delete_title, sub.name),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.recurring_delete_message))
                    // The delete failed: the dialog stays open so the user can retry,
                    // and says so instead of vanishing as if the row were removed.
                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(AuraSpacing.md))
                        AuraErrorBanner(message = message)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val removed = boundary(TAG, false) {
                                repository.deleteSubscription(sub.id, false)
                                true
                            }
                            if (removed) {
                                error = null
                                confirmRemove = null
                            } else {
                                error = UiError.DELETE_FAILED
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/** Log tag for this screen's IO boundaries. */
private const val TAG = "RecurringScreen"

/**
 * A billing cycle as copy.
 *
 * `RecurrenceFrequency` is a domain enum, so `cycle.name.lowercase()` put the
 * identifier on screen in every locale. The `when` is exhaustive on purpose: a
 * cycle added later fails to compile here rather than silently reading "Unknown"
 * or a raw name on screen.
 */
private fun RecurrenceFrequency.labelRes(): Int = when (this) {
    RecurrenceFrequency.DAILY -> R.string.recurring_cycle_daily
    RecurrenceFrequency.WEEKLY -> R.string.recurring_cycle_weekly
    RecurrenceFrequency.MONTHLY -> R.string.recurring_cycle_monthly
    RecurrenceFrequency.YEARLY -> R.string.recurring_cycle_yearly
}

/** The amount as the sheet's plain-digit field wants it: no trailing ".0". */
private fun fieldAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
