package com.awbuilds.auraspend.ui.transaction

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.data.local.SmsMessageLookup
import com.awbuilds.auraspend.data.local.entities.ClassificationMemoryEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.ui.core.UiError
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraErrorState
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.AnimatedMoney
import com.awbuilds.auraspend.ui.designsystem.CategoryAvatar
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.theme.extendedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Transaction detail — view, edit, duplicate and delete without leaving the
 * screen. This is the destination every row in the app opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    repository: TransactionRepository,
    messageLookup: SmsMessageLookup? = null,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // A category correction on a saved row is the strongest user signal in the
    // app, and it used to be ignored: only triage saves fed the classifier. Learn
    // it here so the next message from the same merchant starts in the right
    // category.
    val learnCategory: (Transaction) -> Unit = { transaction ->
        val app = context.applicationContext as? AuraSpendApp
        if (app != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    app.classificationMemory.learn(
                        transaction,
                        ClassificationMemoryEntity.SOURCE_USER_SAVE
                    )
                }
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val transactionUpdatedMessage = stringResource(R.string.txn_detail_updated)
    val duplicatedMessage = stringResource(R.string.txn_detail_duplicated)
    val messageCopiedMessage = stringResource(R.string.txn_detail_message_copied)
    // Typed failure copy, resolved from resources: a raw exception message must
    // never reach the screen (AGENTS.md rule 1).
    val saveFailedMessage = stringResource(UiError.SAVE_FAILED.messageRes)
    val deleteFailedMessage = stringResource(UiError.DELETE_FAILED.messageRes)

    var transaction by remember { mutableStateOf<Transaction?>(null) }
    var message by remember { mutableStateOf<SmsMessageEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    // A read that failed. With no row to show this is the whole screen
    // (AuraErrorState); once a row is loaded it degrades to an inline banner, since
    // the transaction is still worth looking at.
    var error by remember { mutableStateOf<UiError?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val errorMessage = error?.let { stringResource(it.messageRes) }

    LaunchedEffect(transactionId, reload) {
        // Both reads are IO boundaries. The row is an indexed primary-key lookup
        // now: this used to read the whole table and filter it in memory to find
        // one transaction. A SQLite failure renders AuraErrorState, not a crash.
        val row = boundaryOrNull(TAG) { repository.getTransactionById(transactionId) }
        val allCategories = boundaryOrNull(TAG) { repository.getAllCategories().first() }
        // The original message is context, not the transaction itself: a missing
        // row (or lookup) simply means the card is not shown.
        val smsRow = row?.sourceSmsId?.let { id -> messageLookup?.byId(id) }
        transaction = row
        message = smsRow
        categories = allCategories.orEmpty()
        error = if (row == null || allCategories == null) UiError.LOAD_FAILED else null
        loaded = true
    }

    val snackbarMessage = { message: String ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) stringResource(R.string.txn_detail_edit_title) else stringResource(R.string.txn_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (editing) editing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!editing) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val current = transaction
        when {
            !loaded -> Box(Modifier.fillMaxSize().padding(padding))
            error != null && current == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                // Nothing loaded, so the reason plus a retry *is* the screen.
                AuraErrorState(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.error_load_failed),
                    message = stringResource(R.string.error_load_failed_detail),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = {
                        error = null
                        reload++
                    }
                )
            }
            current == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                AuraEmptyState(
                    icon = Icons.Default.Edit,
                    title = stringResource(R.string.txn_detail_not_found_title),
                    message = stringResource(R.string.txn_detail_not_found_message),
                    actionLabel = stringResource(R.string.txn_detail_go_back),
                    onAction = onBack
                )
            }
            editing -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                errorMessage?.let { message ->
                    AuraErrorBanner(
                        message = message,
                        modifier = Modifier.padding(
                            horizontal = AuraSpacing.gutter,
                            vertical = AuraSpacing.sm
                        ),
                        onDismiss = { error = null }
                    )
                }
                TransactionEditor(
                    transaction = current,
                    categories = categories,
                    isNew = false,
                    onCancel = { editing = false },
                    onSave = { updated ->
                        scope.launch {
                            val saved = boundary(TAG, false) {
                                repository.saveTransaction(updated)
                                true
                            }
                            if (saved) {
                                if (updated.categoryId != current.categoryId) {
                                    learnCategory(updated)
                                }
                                transaction = updated
                                editing = false
                                snackbarMessage(transactionUpdatedMessage)
                            } else {
                                // The editor stays open with the user's edits, and the
                                // snackbar says the save did not happen.
                                snackbarMessage(saveFailedMessage)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                errorMessage?.let { message ->
                    AuraErrorBanner(
                        message = message,
                        modifier = Modifier.padding(
                            horizontal = AuraSpacing.gutter,
                            vertical = AuraSpacing.sm
                        ),
                        onDismiss = { error = null }
                    )
                }
                TransactionDetails(
                    transaction = current,
                    category = categories.find { it.id == current.categoryId },
                    message = message,
                    modifier = Modifier.weight(1f),
                    onCopied = { snackbarMessage(messageCopiedMessage) },
                    onPickCategory = { showCategoryPicker = true },
                    onDuplicate = {
                        val copy = current.copy(
                            id = UUID.randomUUID().toString(),
                            date = LocalDateTime.now(),
                            sourceSmsId = null
                        )
                        scope.launch {
                            val saved = boundary(TAG, false) {
                                repository.saveTransaction(copy)
                                true
                            }
                            if (saved) {
                                snackbarMessage(duplicatedMessage)
                                onOpenTransaction(copy.id)
                            } else {
                                // No navigation: a failed duplicate must not look like
                                // it opened a new row that does not exist.
                                snackbarMessage(saveFailedMessage)
                            }
                        }
                    }
                )
            }
        }
    }

    if (confirmDelete) {
        val current = transaction
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.txn_detail_delete_title), modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    current?.let {
                        stringResource(
                            R.string.txn_detail_delete_message,
                            it.merchant ?: it.note.ifBlank { stringResource(R.string.txn_detail_this_transaction) },
                            formatMoney(it.amount)
                        )
                    } ?: stringResource(R.string.txn_detail_delete_fallback)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val deleted = boundary(TAG, false) {
                                current?.let { repository.deleteTransaction(it.id) }
                                true
                            }
                            if (deleted) {
                                confirmDelete = false
                                onBack()
                            } else {
                                // The dialog stays open so the user can retry, instead
                                // of dismissing as if the row were gone.
                                snackbarMessage(deleteFailedMessage)
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showCategoryPicker) {
        val current = transaction
        if (current != null) {
            AlertDialog(
                onDismissRequest = { showCategoryPicker = false },
                title = {
                    Text(
                        stringResource(R.string.txn_editor_category),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        categories.forEach { cat ->
                            val selected = cat.id == current.categoryId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showCategoryPicker = false
                                        val updated = current.copy(categoryId = cat.id)
                                        scope.launch {
                                            val saved = boundary(TAG, false) {
                                                repository.saveTransaction(updated)
                                                true
                                            }
                                            if (saved) {
                                                learnCategory(updated)
                                                transaction = updated
                                                snackbarMessage(transactionUpdatedMessage)
                                            } else {
                                                snackbarMessage(saveFailedMessage)
                                            }
                                        }
                                    }
                                    .padding(vertical = AuraSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryAvatar(
                                    icon = cat.icon,
                                    color = androidx.compose.ui.graphics.Color(cat.color.toLong()),
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.width(AuraSpacing.md))
                                Text(
                                    cat.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCategoryPicker = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

/** Log tag for this screen's IO boundaries. */
private const val TAG = "TransactionDetail"

@Composable
private fun TransactionDetails(
    transaction: Transaction,
    category: Category?,
    message: SmsMessageEntity?,
    modifier: Modifier = Modifier,
    onCopied: () -> Unit,
    onPickCategory: () -> Unit,
    onDuplicate: () -> Unit
) {
    val extended = MaterialTheme.extendedColors
    val context = androidx.compose.ui.platform.LocalContext.current
    val shareText = remember(transaction) {
        listOfNotNull(
            (transaction.merchant ?: transaction.note).takeIf { it.isNotBlank() },
            formatMoney(transaction.amount),
            transaction.date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        ).joinToString(" · ")
    }
    val isExpense = transaction.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) MaterialTheme.colorScheme.onSurface else extended.incomeAmount
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMM yyyy · h:mm a") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuraSpacing.gutter)
    ) {
        Spacer(modifier = Modifier.height(AuraSpacing.md))

        AuraCard(style = AuraCardStyle.Tonal, modifier = Modifier.fillMaxWidth()) {
            // The category is the one field people correct most; tapping the
            // hero opens the picker instead of the full editor.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickCategory)
            ) {
                CategoryAvatar(
                    icon = category?.icon,
                    color = androidx.compose.ui.graphics.Color(category?.color?.toLong() ?: 0xFF757575),
                    size = 52.dp,
                    showRing = true
                )
                Spacer(modifier = Modifier.width(AuraSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        category?.name ?: stringResource(R.string.txn_detail_uncategorized),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (isExpense) stringResource(R.string.txn_detail_type_expense) else stringResource(R.string.txn_detail_type_income),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(AuraSpacing.lg))
            AnimatedMoney(
                amount = if (isExpense) -transaction.amount else transaction.amount,
                style = AuraType.moneyHero,
                color = amountColor,
                signed = true
            )
            Spacer(modifier = Modifier.height(AuraSpacing.xs))
            Text(
                transaction.date.atZone(ZoneId.systemDefault()).format(dateFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(AuraSpacing.lg))

        AuraCard(style = AuraCardStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
            DetailRow(stringResource(R.string.txn_detail_merchant), transaction.merchant ?: stringResource(R.string.txn_detail_dash))
            // The pipeline's note is not user content (it is the merchant repeated,
            // or the message's first 120 characters) — noise next to the full
            // message. Hide only that; an edited note always stays.
            if (!isPipelineNote(transaction.note, transaction.merchant, message?.body)) {
                DetailRow(stringResource(R.string.txn_detail_note), transaction.note.ifBlank { stringResource(R.string.txn_detail_dash) })
            }
            DetailRow(stringResource(R.string.txn_detail_bank), transaction.bankName ?: stringResource(R.string.txn_detail_dash))
            if (transaction.isRecurring) {
                DetailRow(stringResource(R.string.txn_detail_recurring), transaction.subscriptionName ?: stringResource(R.string.txn_detail_yes))
            }
            if (transaction.sourceSmsId != null) {
                DetailRow(stringResource(R.string.txn_detail_source), stringResource(R.string.txn_detail_source_sms))
            }
        }

        if (message != null) {
            Spacer(modifier = Modifier.height(AuraSpacing.lg))
            OriginalMessageCard(message = message, onCopied = onCopied)
        }

        Spacer(modifier = Modifier.height(AuraSpacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)) {
            OutlinedButton(
                onClick = {
                    // User-initiated share; the chooser keeps the app out of
                    // the decision of where it goes.
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    boundary(TAG, Unit) {
                        context.startActivity(Intent.createChooser(send, null))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(AuraSpacing.sm))
                Text(stringResource(R.string.action_share))
            }
            OutlinedButton(
                onClick = onDuplicate,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(AuraSpacing.sm))
                Text(stringResource(R.string.action_duplicate))
            }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.xxl))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AuraSpacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

/**
 * True when [note] is pipeline filler rather than user content: the merchant
 * repeated, or the message itself as [BankMessageParser] truncates it
 * (whitespace-collapsed, 120 characters + a trailing ellipsis). Anything else —
 * an edited note, or one with no message to compare against — stays visible.
 */
internal fun isPipelineNote(note: String, merchant: String?, messageBody: String?): Boolean {
    if (note.isBlank()) return false
    if (!merchant.isNullOrBlank() && note == merchant) return true
    return messageBody != null && note == BankMessageParser.truncateMessage(messageBody)
}

/** Clipboard label shown in the system's clipboard overlay. */
private const val MESSAGE_CLIP_LABEL = "AuraSpend message"

/**
 * The complete original SMS, for transactions the app detected from a message.
 *
 * The whole body, selectable — the Note row next to it is only ever the first
 * 120 characters — plus who sent it, when it arrived, and a Copy action.
 */
@Composable
private fun OriginalMessageCard(message: SmsMessageEntity, onCopied: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val receivedFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a") }
    val receivedText = remember(message.receivedAt) {
        Instant.ofEpochMilli(message.receivedAt)
            .atZone(ZoneId.systemDefault())
            .format(receivedFormatter)
    }

    AuraCard(style = AuraCardStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.txn_detail_message_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.txn_detail_message_meta, message.address, receivedText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    scope.launch {
                        // A failed copy must not claim success: the snackbar only
                        // fires when the clipboard write went through.
                        val copied = boundary(TAG, false) {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText(MESSAGE_CLIP_LABEL, message.body))
                            )
                            true
                        }
                        if (copied) {
                            // Read our own write back: some OEM clipboards accept a
                            // write and go on serving something else. Lengths only in
                            // the log, never the message.
                            val readBackLength = boundaryOrNull(TAG) {
                                clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.length
                            }
                            // `i`, not `d`: vivo/OriginOS drops debug-level app
                            // logs, and this line is the only evidence a copy
                            // actually reached the clipboard.
                            AuraLog.i(
                                TAG,
                                "message copy: ${message.body.length} chars, " +
                                    "clipboard readback=$readBackLength"
                            )
                            onCopied()
                        }
                    }
                }
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy_message)
                )
            }
        }
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        SelectionContainer {
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
