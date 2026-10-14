package com.awbuilds.auraspend.ui.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.RecurringCost
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraSegmentedControl
import com.awbuilds.auraspend.ui.designsystem.AuraSkeleton
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.SettingsAvatarButton
import com.awbuilds.auraspend.ui.designsystem.TransactionEntryRow
import com.awbuilds.auraspend.ui.designsystem.categoryColor
import com.awbuilds.auraspend.ui.designsystem.categoryIconGlyph
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.designsystem.formatSignedMoney
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.awbuilds.auraspend.ui.designsystem.SwipeToAct

private enum class TxnFilter(val labelRes: Int, val type: TransactionType?) {
    ALL(R.string.activity_filter_all, null),
    OUTGOING(R.string.activity_filter_outgoing, TransactionType.EXPENSE),
    INCOMING(R.string.activity_filter_incoming, TransactionType.INCOME)
}

/**
 * Activity feed. Rows come from a Room-backed Paging 3 stream, so filters are
 * applied in SQL and only a bounded page is in memory. Day headers stay sticky
 * by comparing each row's local date with the previous row's date.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    categories: List<Category>,
    onOpenTransaction: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val monthSummary by viewModel.monthSummary.collectAsStateWithLifecycle()
    val transactions = viewModel.transactions.collectAsLazyPagingItems()
    val loadState = transactions.loadState

    // A drill-down may arrive with a query already applied; the field opens so
    // the filtered list explains itself.
    var showSearch by remember { mutableStateOf(filter.query.isNotBlank()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val extended = MaterialTheme.extendedColors
    val transactionDeletedMessage = stringResource(R.string.activity_transaction_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    val monthLabelFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val itemCount = transactions.itemCount
    val isInitialLoading = itemCount == 0 && loadState.refresh is LoadState.Loading
    // Paging 3.5 removed CombinedLoadStates.endOfPaginationReached; the append
    // source state still carries it.
    val appendEndReached =
        (loadState.append as? LoadState.NotLoading)?.endOfPaginationReached == true
    val hasMore = !appendEndReached && loadState.refresh is LoadState.NotLoading

    fun deleteWithUndo(transaction: Transaction) {
        viewModel.deleteTransaction(transaction.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = transactionDeletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreTransaction(transaction)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // ── Header
            if (showSearch) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = { Text(stringResource(R.string.activity_search_hint)) },
                    leadingIcon = {
                        IconButton(onClick = {
                            showSearch = false
                            viewModel.onQueryChange("")
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    trailingIcon = {
                        if (filter.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_clear)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.xs)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AuraSpacing.gutter, end = AuraSpacing.sm, top = AuraSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.activity_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            if (hasMore) {
                                pluralStringResource(
                                    R.plurals.activity_records_more, itemCount, itemCount
                                )
                            } else {
                                pluralStringResource(
                                    R.plurals.activity_records, itemCount, itemCount
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = extended.textLight
                        )
                    }
                    IconButton(onClick = { viewModel.onToggleSort() }) {
                        Icon(
                            Icons.Default.SwapVert,
                            // The description names the order in effect, so the
                            // icon never needs a second guess.
                            contentDescription = stringResource(
                                if (filter.sortAscending) R.string.activity_sort_oldest_first
                                else R.string.activity_sort_newest_first
                            ),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { showSearch = true }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    SettingsAvatarButton(onClick = onOpenSettings)
                }
            }

            // ── Month navigation: the list and the strip describe one month;
            // browsing history is the chevrons' job.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.sm, vertical = AuraSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.onPreviousMonth() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.activity_previous_month)
                    )
                }
                Text(
                    filter.month.format(monthLabelFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { viewModel.onNextMonth() },
                    // The future has no rows to show; the chevron says so.
                    enabled = !filter.isCurrentMonth
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.activity_next_month)
                    )
                }
            }

            // ── Summary strip: SQL aggregate over the browsed month, so it stays
            // correct while rows are still paging in.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
            ) {
                SummaryPill(
                    label = stringResource(R.string.activity_summary_in),
                    value = stringResource(
                        R.string.common_amount_plus,
                        formatMoney(monthSummary.income)
                    ),
                    valueColor = extended.incomeAmount,
                    modifier = Modifier.weight(1f)
                )
                SummaryPill(
                    label = stringResource(R.string.activity_summary_out),
                    value = stringResource(
                        R.string.common_amount_minus,
                        formatMoney(monthSummary.expense)
                    ),
                    valueColor = extended.expenseAmount,
                    modifier = Modifier.weight(1f)
                )
                SummaryPill(
                    label = stringResource(R.string.activity_summary_net),
                    value = formatMoney(monthSummary.income - monthSummary.expense),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Type filter
            AuraSegmentedControl(
                options = TxnFilter.entries.map { stringResource(it.labelRes) },
                selectedIndex = TxnFilter.entries.indexOfFirst { it.type == filter.type }
                    .coerceAtLeast(0),
                onSelect = { viewModel.onTypeSelected(TxnFilter.entries[it].type) },
                modifier = Modifier.padding(horizontal = AuraSpacing.gutter)
            )

            // ── Category chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
            ) {
                FilterChip(
                    selected = filter.categoryId == null,
                    onClick = { viewModel.onCategorySelected(null) },
                    label = { Text(stringResource(R.string.activity_filter_all)) }
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = filter.categoryId == category.id,
                        onClick = {
                            viewModel.onCategorySelected(
                                if (filter.categoryId == category.id) null else category.id
                            )
                        },
                        leadingIcon = {
                            com.awbuilds.auraspend.ui.designsystem.CategoryAvatar(
                                icon = category.icon,
                                color = categoryColor(category.color),
                                size = 22.dp
                            )
                        },
                        label = {
                            Text(category.name, maxLines = 1)
                        }
                    )
                }
            }

            // ── Paged list
            when {
                isInitialLoading -> TransactionListSkeleton()

                itemCount == 0 && loadState.refresh is LoadState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadErrorRow(onRetry = { transactions.retry() })
                }

                itemCount == 0 -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AuraEmptyState(
                        icon = if (filter.isActive || !filter.isCurrentMonth) Icons.Default.SearchOff
                        else Icons.AutoMirrored.Filled.ReceiptLong,
                        title = if (filter.isActive || !filter.isCurrentMonth) {
                            stringResource(R.string.activity_empty_title_filtered)
                        } else {
                            stringResource(R.string.activity_empty_title)
                        },
                        message = if (filter.isActive || !filter.isCurrentMonth) {
                            stringResource(R.string.activity_empty_message_filtered)
                        } else {
                            stringResource(R.string.activity_empty_message)
                        }
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AuraSpacing.gutter,
                        end = AuraSpacing.gutter,
                        bottom = AuraSpacing.xxl
                    )
                ) {
                    // `peek` is `itemSnapshotList[index]` and throws past the end
                    // — it is not a null-returning read. `itemCount` above is read
                    // during composition, but this builder runs later, during a
                    // layout pass, against a newer paging snapshot, and a search
                    // keystroke shrinks the list in between. That exact race
                    // crashed the app while typing in Activity search ("Illegal
                    // attempt to access index 14 in ItemSnapshotList of size 14",
                    // on-device 2026-10-13). Read the count here so it and the
                    // peeks below are the same snapshot.
                    repeat(transactions.itemCount) { index ->
                        val transaction = transactions.peek(index) ?: return@repeat
                        val day = transaction.date.toLocalDate()
                        if (index == 0 ||
                            transactions.peek(index - 1)?.date?.toLocalDate() != day
                        ) {
                            stickyHeader(
                                key = "header_${day.toEpochDay()}",
                                contentType = "header"
                            ) {
                                val dayNet = remember(day, itemCount) {
                                    loadedDayNet(transactions, index)
                                }
                                StickyGroupHeader(
                                    label = dayHeaderLabel(day),
                                    netAmount = dayNet
                                )
                            }
                        }
                        item(key = transaction.id, contentType = "transaction") {
                            val category = categories.find { it.id == transaction.categoryId }
                            // Swipe left to delete, with undo. See `SwipeToAct` for
                            // why this is not a bare `SwipeToDismissBox`:
                            // `confirmValueChange` is deprecated without replacement, so
                            // the row used to animate to its anchor, paint a red delete
                            // block, and change nothing. Found on-device 2026-09-29.
                            SwipeToAct(
                                onAction = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        deleteWithUndo(transaction)
                                    }
                                },
                                modifier = Modifier.animateItem(),
                                enabledFromStartToEnd = false,
                                backgroundContent = { _ ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = AuraSpacing.xxl)
                                        )
                                    }
                                }
                            ) {
                                TransactionEntryRow(
                                    transaction = transaction,
                                    categoryName = category?.name
                                        ?: stringResource(R.string.activity_other_category),
                                    categoryColor = categoryColor(category?.color),
                                    categoryEmoji = categoryIconGlyph(category?.icon),
                                    onClick = { onOpenTransaction(transaction.id) }
                                )
                            }
                        }
                    }
                    if (loadState.append is LoadState.Loading) {
                        item(key = "append_loading", contentType = "footer") {
                            AppendLoadingRow()
                        }
                    }
                    if (loadState.append is LoadState.Error ||
                        loadState.refresh is LoadState.Error
                    ) {
                        item(key = "load_error", contentType = "footer") {
                            LoadErrorRow(onRetry = { transactions.retry() })
                        }
                    }
                }
            }
        }

        // Floating snackbar (above the chrome).
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AuraSpacing.xxl)
        )
    }
}

/**
 * Net amount for the loaded rows of one local day, starting at [startIndex].
 * Paging keeps a day's rows contiguous; rows whose page has not arrived yet
 * (peek returns null) simply contribute once their page loads.
 */
private fun loadedDayNet(transactions: LazyPagingItems<Transaction>, startIndex: Int): Double {
    // `startIndex` was captured when the header was built and the list may have
    // shrunk since; same throwing-peek hazard as the builder above.
    if (startIndex >= transactions.itemCount) return 0.0
    val day = transactions.peek(startIndex)?.date?.toLocalDate() ?: return 0.0
    var net = 0.0
    var index = startIndex
    while (index < transactions.itemCount) {
        val row = transactions.peek(index) ?: break
        if (row.date.toLocalDate() != day) break
        net += if (row.type == TransactionType.EXPENSE) -row.amount else row.amount
        index++
    }
    return net
}

/** "Today" / "Yesterday" for recent days, a localised date otherwise. */
@Composable
private fun dayHeaderLabel(date: LocalDate): String {
    val today = remember { LocalDate.now() }
    return when (date) {
        today -> stringResource(R.string.activity_group_today)
        today.minusDays(1) -> stringResource(R.string.activity_group_yesterday)
        else -> remember(date) {
            date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
        }
    }
}

@Composable
private fun StickyGroupHeader(label: String, netAmount: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = AuraSpacing.md, bottom = AuraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.extendedColors.textLight,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatSignedMoney(netAmount),
            style = MaterialTheme.typography.labelMedium,
            color = if (netAmount >= 0) MaterialTheme.extendedColors.incomeAmount
            else MaterialTheme.extendedColors.expenseAmount
        )
    }
}

/** Initial-load skeleton: row shapes that match the final list. */
@Composable
private fun TransactionListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AuraSpacing.gutter, vertical = AuraSpacing.sm)
    ) {
        repeat(7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AuraSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuraSkeleton(modifier = Modifier.size(46.dp), shape = CircleShape)
                Spacer(modifier = Modifier.width(AuraSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    AuraSkeleton(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(AuraSpacing.lg)
                    )
                    Spacer(modifier = Modifier.height(AuraSpacing.sm))
                    AuraSkeleton(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(AuraSpacing.md)
                    )
                }
                Spacer(modifier = Modifier.width(AuraSpacing.sm))
                AuraSkeleton(
                    modifier = Modifier
                        .width(56.dp)
                        .height(AuraSpacing.lg)
                )
            }
        }
    }
}

/** Small row shown while the next page loads. */
@Composable
private fun AppendLoadingRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AuraSpacing.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(AuraSpacing.sm))
        Text(
            stringResource(R.string.activity_loading_more),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textLight
        )
    }
}

/** Retry affordance for a failed refresh or append. */
@Composable
private fun LoadErrorRow(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AuraSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.activity_load_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textLight
        )
        Spacer(modifier = Modifier.height(AuraSpacing.sm))
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.activity_retry))
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = AuraSpacing.md, vertical = AuraSpacing.sm)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.extendedColors.textLight, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
    }
}
