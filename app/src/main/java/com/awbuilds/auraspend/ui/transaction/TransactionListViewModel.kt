package com.awbuilds.auraspend.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionFilter
import com.awbuilds.auraspend.domain.model.nextMonthOrCurrent
import com.awbuilds.auraspend.domain.model.toEpochRange
import com.awbuilds.auraspend.domain.model.TransactionSummary
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the Activity list.
 *
 * The list itself is a Room-backed Paging 3 stream: filters are pushed down to
 * SQL and only one page (50 rows) is in memory at a time. Query keystrokes are
 * debounced, and the month strip comes from the existing SQL aggregate so it
 * stays correct while rows are still paging in.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TransactionListViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionFilter())

    /** Current filter for the chips/segmented control (updates immediately). */
    val filter: StateFlow<TransactionFilter> = _filter.asStateFlow()

    /**
     * Query changes settle for [QUERY_DEBOUNCE_MS]. The first (blank) emission
     * has no delay so the initial page is not held back.
     */
    private val debouncedQuery: Flow<String> = _filter
        .map { it.query }
        .distinctUntilChanged()
        .debounce { query -> if (query.isBlank()) 0L else QUERY_DEBOUNCE_MS }

    /**
     * Paged rows for the current filter. A new filter cancels the previous
     * Pager, and [PagingData] is cached so config changes do not reload pages.
     */
    val transactions: Flow<PagingData<Transaction>> = combine(
        debouncedQuery,
        _filter.map { it.type }.distinctUntilChanged(),
        _filter.map { it.categoryId }.distinctUntilChanged(),
        _filter.map { it.month }.distinctUntilChanged(),
        _filter.map { it.sortAscending }.distinctUntilChanged()
    ) { query, type, categoryId, month, sortAscending ->
        TransactionFilter(query, type, categoryId, month, sortAscending)
    }
        .flatMapLatest { repository.pagedTransactions(it) }
        .catch { error ->
            AuraLog.e(TAG, "Paged transactions failed", error)
            emit(PagingData.empty())
        }
        .cachedIn(viewModelScope)

    /** SQL aggregate for the summary strip, over the month being browsed. */
    val monthSummary: StateFlow<TransactionSummary> = _filter
        .map { it.month }
        .distinctUntilChanged()
        .flatMapLatest { createMonthSummary(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUMMARY_STOP_TIMEOUT_MS),
            initialValue = TransactionSummary(income = 0.0, expense = 0.0, incomeCount = 0, expenseCount = 0)
        )

    fun onQueryChange(query: String) {
        _filter.update { it.copy(query = query) }
    }

    fun onTypeSelected(type: TransactionType?) {
        _filter.update { it.copy(type = type) }
    }

    fun onCategorySelected(categoryId: String?) {
        _filter.update { it.copy(categoryId = categoryId) }
    }

    /** Steps the browsed month back; there is no lower bound — history is the point. */
    fun onPreviousMonth() {
        _filter.update { it.copy(month = it.month.minusMonths(1)) }
    }

    /** Steps forward, clamped at the current month: no browsing the future. */
    fun onNextMonth() {
        _filter.update { it.copy(month = nextMonthOrCurrent(it.month)) }
    }

    /** Flips newest-first / oldest-first for the list. */
    fun onToggleSort() {
        _filter.update { it.copy(sortAscending = !it.sortAscending) }
    }

    /** Swipe-to-delete; failures are logged and leave the row restored. */
    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            boundary(TAG, Unit) { repository.deleteTransaction(id) }
        }
    }

    /** UNDO action from the snackbar. */
    fun restoreTransaction(transaction: Transaction) {
        viewModelScope.launch {
            boundary(TAG, Unit) { repository.saveTransaction(transaction) }
        }
    }

    private fun createMonthSummary(month: YearMonth): Flow<TransactionSummary> {
        val (start, end) = month.toEpochRange(ZoneId.systemDefault())
        return repository.observeSummary(start, end)
            .catch { error ->
                AuraLog.e(TAG, "Month summary failed", error)
                emit(TransactionSummary(income = 0.0, expense = 0.0, incomeCount = 0, expenseCount = 0))
            }
    }

    private companion object {
        const val TAG = "TransactionList"
        const val QUERY_DEBOUNCE_MS = 250L
        const val SUMMARY_STOP_TIMEOUT_MS = 5_000L
    }
}
