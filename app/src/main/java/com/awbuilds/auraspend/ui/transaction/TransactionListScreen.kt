package com.awbuilds.auraspend.ui.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraChip
import com.awbuilds.auraspend.ui.designsystem.AuraSkeleton
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.AuraType
import com.awbuilds.auraspend.ui.designsystem.SettingsAvatarButton
import com.awbuilds.auraspend.ui.designsystem.TransactionEntryRow
import com.awbuilds.auraspend.ui.designsystem.categoryColor
import com.awbuilds.auraspend.ui.designsystem.categoryIconGlyph
import com.awbuilds.auraspend.ui.designsystem.formatMoney
import com.awbuilds.auraspend.ui.designsystem.formatSignedMoney
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val extended = MaterialTheme.extendedColors
    val transactionDeletedMessage = stringResource(R.string.activity_transaction_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    val monthLabelFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val monthOnlyFormatter = remember { DateTimeFormatter.ofPattern("MMMM") }
    val itemCount = transactions.itemCount
    val isInitialLoading = itemCount == 0 && loadState.refresh is LoadState.Loading

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
            //
            // The concept's `.head`: 6dp above the title, 16dp below the sub-line,
            // both ends on the 20dp gutter. No eyebrow above the title — "Activity"
            // is the whole header, and the month it describes is the sub-line.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AuraSpacing.gutter,
                        // No trailing padding: `SettingsAvatarButton` carries 16dp
                        // of its own end inset, so the circle's edge lands 1dp
                        // inside the concept's 20dp gutter. Padding the row here as
                        // well would walk the avatar 20dp in from the screen edge.
                        top = 6.dp,
                        bottom = AuraSpacing.lg
                    ),
                // The concept's `.head` is `align-items:flex-start`: the avatar sits
                // level with the title, not centred on the block.
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.activity_title),
                        style = AuraType.screenTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() }
                    )
                    // 5dp: `.head .eyebrow{margin-bottom:5px}`, which is both the gap
                    // under the title and the sub-line's own trailing margin.
                    Spacer(modifier = Modifier.height(5.dp))
                    // "October · ₹44,663 out" — the browsed month and what left
                    // the account in it. The record count and the In/Out/Net tiles
                    // that used to sit here said the same thing in three more
                    // rows of chrome above the list.
                    Text(
                        stringResource(
                            // "in" when the list is showing income only: the
                            // header has to count what the rows below it are,
                            // and "₹5,767 out" above a list of credits is the
                            // same contradiction this line was fixed for.
                            if (filter.type == TransactionType.INCOME) {
                                R.string.activity_month_in
                            } else {
                                R.string.activity_month_out
                            },
                            // The scope: the category when one is selected (the
                            // month is still named by the stepper below), the
                            // month otherwise.
                            if (filter.categoryId != null) {
                                categories.find { it.id == filter.categoryId }?.name
                                    ?: filter.month.format(monthOnlyFormatter)
                            } else {
                                filter.month.format(monthOnlyFormatter)
                            },
                            // Whole rupees: the concept's `inr()` rounds, and a
                            // header summary does not need the paise a row does.
                            formatMoney(
                                if (filter.type == TransactionType.INCOME) {
                                    monthSummary.income
                                } else {
                                    monthSummary.expense
                                },
                                fractionDigits = 0
                            )
                        ),
                        // The concept's `.eyebrow` with `text-transform:none` and
                        // `letter-spacing:.02em` — 11.5sp/600, +0.23sp tracking.
                        style = AuraType.flowCaption.copy(letterSpacing = 0.23.sp),
                        color = extended.faint,
                        modifier = Modifier.padding(bottom = 5.dp)
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
                        // `inkSoft`, not ink: a sort toggle is a control, and the
                        // concept keeps full ink for the one thing you read first.
                        tint = extended.inkSoft,
                        modifier = Modifier.size(24.dp)
                    )
                }
                SettingsAvatarButton(onClick = onOpenSettings)
            }

            // ── Search, always visible.
            //
            // It used to hide behind a magnifier in the header, which cost a tap on
            // the most common way to find a transaction once an account has more
            // than a screenful of rows.
            ActivitySearchField(
                query = filter.query,
                onQueryChange = { viewModel.onQueryChange(it) },
                hint = stringResource(R.string.activity_search_hint),
                clearLabel = stringResource(R.string.action_clear)
            )

            // ── Month navigation: the list and the strip describe one month;
            // browsing history is the chevrons' job.
            //
            // Not drawn by the prototype — the owner keeps it — so it is styled in
            // the concept's language: 24dp chevrons in `faint` on the gutter grid,
            // the month at the row-title size, all inside a 48dp row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = AuraSpacing.gutter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthChevron(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.activity_previous_month),
                    alignment = Alignment.CenterStart,
                    onClick = { viewModel.onPreviousMonth() }
                )
                Text(
                    filter.month.format(monthLabelFormatter),
                    style = AuraType.budgetName,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                MonthChevron(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.activity_next_month),
                    // The future has no rows to show; the chevron says so.
                    enabled = !filter.isCurrentMonth,
                    alignment = Alignment.CenterEnd,
                    onClick = { viewModel.onNextMonth() }
                )
            }

            // ── Filters: type and category in one scrolling row.
            //
            // Two controls stacked vertically turned one question ("show me this
            // slice") into a mode switch. The divider keeps the two groups legible
            // without spending a second row of height above the list.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(
                        start = AuraSpacing.gutter,
                        end = AuraSpacing.gutter,
                        // 14dp, the concept's `.chips` margin above the pill.
                        top = 14.dp
                    ),
                // 7dp, the concept's `.chips` gap.
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TxnFilter.entries.forEach { option ->
                    AuraChip(
                        label = stringResource(option.labelRes),
                        selected = if (option.type == null) {
                            // "All" means all — type *and* category cleared.
                            filter.type == null && filter.categoryId == null
                        } else {
                            filter.type == option.type
                        },
                        onClick = {
                            if (option.type == null) {
                                viewModel.onTypeSelected(null)
                                viewModel.onCategorySelected(null)
                            } else {
                                viewModel.onTypeSelected(option.type)
                            }
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        // `.chip-div` sits 2dp clear of the 7dp gaps either side,
                        // so the padding wraps the 1dp hairline.
                        .padding(horizontal = AuraSpacing.xxs)
                        .width(1.dp)
                        .heightIn(min = 18.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                categories.forEach { category ->
                    AuraChip(
                        label = category.name,
                        selected = filter.categoryId == category.id,
                        dotColor = categoryColor(category.color),
                        onClick = {
                            viewModel.onCategorySelected(
                                if (filter.categoryId == category.id) null else category.id
                            )
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
                    // No horizontal padding: `TransactionEntryRow` and the sticky
                    // day header each carry the 20dp gutter themselves, and padding
                    // them again here indented every avatar and every hairline by a
                    // second gutter (40dp of margin where the concept has 20).
                    contentPadding = PaddingValues(bottom = AuraSpacing.xxl)
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

/**
 * A day's sticky header: the uppercase date on the left, the day's net on the
 * right.
 *
 * 16dp/4dp of padding, the concept's `.day` (`.day.sticky` trims the top to 10dp
 * while it is pinned; that is a scroll-position artefact, not the resting rhythm).
 * The net total is not in the prototype — the owner keeps it — so it takes the
 * concept's own sub-row voice: 11sp/500 in `onSurfaceVariant`, right-aligned on
 * the label's line rather than coloured like money, which would compete with the
 * amounts in the rows below it.
 */
@Composable
private fun StickyGroupHeader(label: String, netAmount: Double) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = AuraSpacing.gutter,
                end = AuraSpacing.gutter,
                top = AuraSpacing.lg,
                bottom = AuraSpacing.xs
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = remember(label, locale) { label.uppercase(locale) },
            style = AuraType.groupLabel,
            color = MaterialTheme.extendedColors.faint,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(AuraSpacing.sm))
        Text(
            // Whole rupees, like the header's month total: a day's net is a
            // summary, and `formatSignedMoney` already carries the sign.
            formatSignedMoney(netAmount, fractionDigits = 0),
            style = AuraType.budgetSub,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The concept's `.search`: a flat 42dp pill — `onBackground` at 5 %, a 17dp
 * glyph, a 9dp gap, 14dp of side padding — not a Material text field. An
 * `OutlinedTextField` carries a 56dp floor and a label slot of its own, which
 * made this the tallest thing above the list and the only raised-looking
 * surface on a screen the concept keeps flat.
 *
 * The pill sits in a 48dp band, the trick `AuraChip` uses: the visible pill
 * stays 42dp while the target clears the touch floor. The clear affordance
 * lives in that band for the same reason — inside the pill it could only ever
 * be 42dp tall.
 */
@Composable
private fun ActivitySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    clearLabel: String,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.gutter)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                // The whole pill focuses the field, so a tap anywhere in it
                // lands the caret instead of doing nothing.
                .clickable { focusRequester.requestFocus() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.faint,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        hint,
                        style = AuraType.searchPlaceholder,
                        color = MaterialTheme.extendedColors.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // The hint is decoration, not the field's name. The
                        // clickable pill below merges its descendants, and a
                        // merged node takes its *text* from the field, which
                        // is empty — so leaving this in the tree merged the
                        // field into an unnamed EditText. The field carries
                        // the label instead, and this is taken out of the
                        // tree so it is not announced twice.
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = AuraType.searchPlaceholder.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = hint }
                )
            }
            if (query.isNotEmpty()) {
                // Room for the clear glyph the band draws over the pill's end.
                Spacer(modifier = Modifier.width(26.dp))
            }
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = { onQueryChange("") }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = clearLabel,
                    tint = MaterialTheme.extendedColors.inkSoft,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

/**
 * A month chevron: a 24dp glyph in a 48dp target, aligned to the gutter edge
 * rather than centred inside its target.
 *
 * Centring would inset the glyph 12dp past the search pill's and the chips'
 * edge; the concept's icons sit on the same 20dp grid as everything else.
 */
@Composable
private fun MonthChevron(
    icon: ImageVector,
    contentDescription: String,
    alignment: Alignment,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = alignment
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.extendedColors.faint,
            modifier = Modifier.size(24.dp)
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

