package com.awbuilds.auraspend.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.data.classification.ClassificationMemory
import com.awbuilds.auraspend.data.local.SmsMessageLookup
import com.awbuilds.auraspend.data.local.entities.ClassificationMemoryEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.ui.core.UiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

sealed interface TransactionDetailUiState {
    data object Loading : TransactionDetailUiState
    data object NotFound : TransactionDetailUiState
    data class Error(val error: UiError) : TransactionDetailUiState
    data class Success(
        val transaction: Transaction,
        val categories: List<Category>,
        val message: SmsMessageEntity?,
        val inlineError: UiError? = null
    ) : TransactionDetailUiState
}

class TransactionDetailViewModel(
    private val transactionId: String,
    private val repository: TransactionRepository,
    private val messageLookup: SmsMessageLookup? = null,
    private val classificationMemory: ClassificationMemory? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransactionDetailUiState>(TransactionDetailUiState.Loading)
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        loadTransaction()
    }

    fun loadTransaction() {
        _uiState.value = TransactionDetailUiState.Loading
        viewModelScope.launch {
            var isError = false
            val (row, allCategories) = withContext(ioDispatcher) {
                val r = try {
                    repository.getTransactionById(transactionId)
                } catch (e: Exception) {
                    isError = true
                    null
                }
                val c = boundaryOrNull(TAG) { repository.getAllCategories().first() }
                Pair(r, c)
            }

            if (isError || allCategories == null) {
                _uiState.value = TransactionDetailUiState.Error(UiError.LOAD_FAILED)
                return@launch
            }

            if (row == null) {
                _uiState.value = TransactionDetailUiState.NotFound
                return@launch
            }

            val smsRow = withContext(ioDispatcher) {
                row.sourceSmsId?.let { id -> messageLookup?.byId(id) }
            }

            _uiState.value = TransactionDetailUiState.Success(
                transaction = row,
                categories = allCategories,
                message = smsRow
            )
        }
    }

    fun saveTransaction(updated: Transaction, onResult: (Boolean) -> Unit) {
        val currentState = _uiState.value as? TransactionDetailUiState.Success ?: return
        viewModelScope.launch {
            val saved = withContext(ioDispatcher) {
                boundary(TAG, false) {
                    repository.saveTransaction(updated)
                    true
                }
            }
            if (saved) {
                if (updated.categoryId != currentState.transaction.categoryId) {
                    learnCategory(updated)
                }
                _uiState.value = currentState.copy(
                    transaction = updated,
                    inlineError = null
                )
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun deleteTransaction(onResult: (Boolean) -> Unit) {
        val currentState = _uiState.value as? TransactionDetailUiState.Success ?: return
        viewModelScope.launch {
            val deleted = withContext(ioDispatcher) {
                boundary(TAG, false) {
                    repository.deleteTransaction(currentState.transaction.id)
                    true
                }
            }
            onResult(deleted)
        }
    }

    fun duplicateTransaction(onResult: (String?) -> Unit) {
        val currentState = _uiState.value as? TransactionDetailUiState.Success ?: return
        val copy = currentState.transaction.copy(
            id = UUID.randomUUID().toString(),
            date = LocalDateTime.now(),
            sourceSmsId = null
        )
        viewModelScope.launch {
            val saved = withContext(ioDispatcher) {
                boundary(TAG, false) {
                    repository.saveTransaction(copy)
                    true
                }
            }
            if (saved) {
                onResult(copy.id)
            } else {
                onResult(null)
            }
        }
    }

    fun updateCategory(category: Category, onResult: (Boolean) -> Unit) {
        val currentState = _uiState.value as? TransactionDetailUiState.Success ?: return
        val updated = currentState.transaction.copy(categoryId = category.id)
        viewModelScope.launch {
            val saved = withContext(ioDispatcher) {
                boundary(TAG, false) {
                    repository.saveTransaction(updated)
                    true
                }
            }
            if (saved) {
                learnCategory(updated)
                _uiState.value = currentState.copy(
                    transaction = updated,
                    inlineError = null
                )
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun dismissInlineError() {
        val currentState = _uiState.value as? TransactionDetailUiState.Success ?: return
        _uiState.value = currentState.copy(inlineError = null)
    }

    private suspend fun learnCategory(transaction: Transaction) {
        withContext(ioDispatcher) {
            runCatching {
                classificationMemory?.learn(
                    transaction,
                    ClassificationMemoryEntity.SOURCE_USER_SAVE
                )
            }
        }
    }

    companion object {
        private const val TAG = "TransactionDetailVM"
    }
}
