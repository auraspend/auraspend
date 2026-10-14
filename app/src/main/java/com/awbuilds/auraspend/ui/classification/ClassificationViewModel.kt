package com.awbuilds.auraspend.ui.classification

import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.core.boundary
import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.ui.core.UiError

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awbuilds.auraspend.AuraSpendApp
import com.awbuilds.auraspend.data.ai.LocalLlmProvider
import com.awbuilds.auraspend.data.ai.ModelDownloadManager
import com.awbuilds.auraspend.data.ai.SmsAiEnricher
import com.awbuilds.auraspend.data.classification.AutoClassificationWorker
import com.awbuilds.auraspend.data.classification.AutoDetect
import com.awbuilds.auraspend.data.classification.ClassifiedSms
import com.awbuilds.auraspend.data.classification.MerchantRepository
import com.awbuilds.auraspend.data.classification.SmsAutoClassifier
import com.awbuilds.auraspend.data.classification.SmsIngestor
import com.awbuilds.auraspend.data.classification.SmsStatus
import com.awbuilds.auraspend.data.classification.pendingInbox
import com.awbuilds.auraspend.data.classification.skippedInbox
import com.awbuilds.auraspend.data.classification.queueProgress
import com.awbuilds.auraspend.data.classification.TransactionClassifier
import com.awbuilds.auraspend.data.classification.toClassifiedSms
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.domain.usecase.ClassifyMessageUseCase
import com.awbuilds.auraspend.domain.usecase.SaveTransactionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.time.LocalDateTime

class ClassificationViewModel(
    private val classifyMessageUseCase: ClassifyMessageUseCase,
    private val saveTransactionUseCase: SaveTransactionUseCase,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ClassificationViewState())
    val state: StateFlow<ClassificationViewState> = _state.asStateFlow()

    /**
     * One-shot results of a triage gesture.
     *
     * The triage swipe used to fire the save intent and show "Saved" with an undo
     * action in the same breath, without ever looking at the outcome — so a
     * message the classifier could not resolve (`NO_TRANSACTION_FOUND`) or a
     * failed write still produced a green "Saved" snackbar and a row that vanished
     * from the inbox with nothing behind it. The outcome is an event, not state:
     * re-emitting it on recomposition would re-show a snackbar for a save that
     * happened once.
     */
    private val _triageOutcomes = Channel<SmsTriageSaved>(Channel.BUFFERED)
    val triageOutcomes = _triageOutcomes.receiveAsFlow()

    /** The single live observer of the SMS queue; re-entry cancels the previous one. */
    private var smsQueueJob: Job? = null

    init {
        LocalLlmProvider.init(context)
        ModelDownloadManager.sync(context)

        _state.update { it.copy(aiModelState = ModelDownloadManager.state.value) }

        viewModelScope.launch {
            ModelDownloadManager.state.collectLatest { modelState ->
                _state.update { it.copy(aiModelState = modelState) }
            }
        }

        if (!ModelDownloadManager.hasConsent(context) && !ModelDownloadManager.hasModel(context)) {
            _state.update { it.copy(consentRequired = true) }
        }
    }

    fun handleIntent(intent: ClassificationViewIntent) {
        when (intent) {
            is ClassificationViewIntent.MessageChanged -> {
                // Typed or pasted: there is no sender to route by.
                _state.update { it.copy(rawMessage = intent.message, rawMessageSender = null) }
            }
            is ClassificationViewIntent.ClassifyMessage -> classify()
            is ClassificationViewIntent.SelectCategory -> {
                _state.update { it.copy(selectedCategoryId = intent.categoryId) }
            }
            is ClassificationViewIntent.AmountChanged -> {
                _state.update { it.copy(manualAmount = intent.amount) }
            }
            is ClassificationViewIntent.NoteChanged -> {
                _state.update { it.copy(manualNote = intent.note) }
            }
            is ClassificationViewIntent.MerchantChanged -> {
                _state.update { it.copy(manualMerchant = intent.merchant) }
            }
            is ClassificationViewIntent.TypeChanged -> {
                _state.update { it.copy(manualType = intent.type) }
            }
            is ClassificationViewIntent.ToggleManualEntry -> {
                _state.update { it.copy(useManualEntry = !it.useManualEntry) }
            }
            is ClassificationViewIntent.RequestSmsPermission -> {
                // Handled by the UI layer
            }
            is ClassificationViewIntent.SmsPermissionResult -> {
                _state.update { it.copy(smsPermissionGranted = intent.granted) }
                if (intent.granted) {
                    loadAndClassifyAll()
                    // Immediately read + classify + save device messages.
                    AutoClassificationWorker.runNow(context)
                }
            }
            is ClassificationViewIntent.LoadSmsMessages -> loadAndClassifyAll()
            is ClassificationViewIntent.SmsSelected -> {
                _state.update {
                    it.copy(
                        rawMessage = intent.sms.body,
                        rawMessageSender = intent.sms.address,
                        parsedMessage = null
                    )
                }
                classify()
            }
            is ClassificationViewIntent.SaveTransaction -> saveTransaction()
            is ClassificationViewIntent.ResetSuccess -> {
                _state.update { it.copy(saveSuccess = false) }
            }
            is ClassificationViewIntent.ClearError -> _state.update { it.copy(error = null) }
            is ClassificationViewIntent.SetCategories -> {
                _state.update { it.copy(availableCategories = intent.categories) }
            }
            is ClassificationViewIntent.LoadAndClassifyAll -> loadAndClassifyAll()
            is ClassificationViewIntent.SaveClassifiedSms -> saveClassifiedSms(intent.smsId)
            is ClassificationViewIntent.SaveAllClassified -> saveAllClassified()
            is ClassificationViewIntent.RestoreSkippedSms -> restoreSkippedSms(intent.smsId)
            is ClassificationViewIntent.SkipAllClassified -> skipAllClassified()
            is ClassificationViewIntent.DismissClassifiedSms -> dismissClassifiedSms(intent.smsId)
            is ClassificationViewIntent.UndoSaveClassifiedSms -> undoSaveClassifiedSms(intent.smsId)
            is ClassificationViewIntent.UndoDismissClassifiedSms -> undoDismissClassifiedSms(intent.smsId)

            // On-device AI model
            is ClassificationViewIntent.ConsentResult -> {
                _state.update { it.copy(consentRequired = false) }
                if (intent.accepted) {
                    ModelDownloadManager.markConsentGiven(context)
                    // Opt the user into automatic classification of device messages.
                    AutoDetect.setEnabled(context, true)
                    ModelDownloadManager.start(context)
                    AutoClassificationWorker.runNow(context)
                }
            }
            is ClassificationViewIntent.DismissModelConsent -> {
                // Decline without recording consent. The offer stops showing here,
                // and Settings can still start the download later.
                _state.update { it.copy(consentRequired = false) }
            }
            is ClassificationViewIntent.StartModelDownload -> {
                _state.update { it.copy(consentRequired = false) }
                ModelDownloadManager.start(context)
            }
            is ClassificationViewIntent.CancelModelDownload -> ModelDownloadManager.cancel()
            is ClassificationViewIntent.RefreshAiModelState -> {
                ModelDownloadManager.sync(context)
                _state.update { it.copy(aiModelState = ModelDownloadManager.state.value) }
            }
        }
    }

    private fun classify() {
        val message = _state.value.rawMessage
        if (message.isBlank()) return

        val parsed = classifyMessageUseCase(message, _state.value.rawMessageSender)

        // Get merchant confidence from repository
        var merchantConfidence = 0f
        if (parsed.merchant != null) {
            val merchantSuggestion = MerchantRepository.suggestCategory(parsed.merchant)
            merchantConfidence = merchantSuggestion?.second ?: 0f
        }

        _state.update {
            it.copy(
                parsedMessage = parsed,
                selectedCategoryId = parsed.categoryId ?: "cat_other",
                manualAmount = parsed.amount?.let { formatAmount(it) } ?: "",
                manualNote = parsed.note ?: "",
                manualType = parsed.type ?: TransactionType.EXPENSE,
                merchantConfidence = merchantConfidence,
                aiRefined = false,
                isAiEnriching = true
            )
        }

        // Async refinement: learned memory first, then the on-device LLM. Regex results are shown
        // instantly; the AI result is only applied if the user has not moved on to another message.
        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as? AuraSpendApp
            val categoriesByName = _state.value.availableCategories.associate { it.id to it.name }
            val enricher = SmsAiEnricher(context, app?.classificationMemory)
            val probe = ClassifiedSms(
                sms = SmsInfo(id = "", address = "", body = message, timestamp = 0L),
                parsed = parsed
            )
            val enriched = runCatching { enricher.enrich(probe, categoriesByName) }.getOrNull()
                ?: return@launch

            _state.update { current ->
                // The user may have edited or replaced the message while the model was thinking.
                if (current.rawMessage != message) return@update current.copy(isAiEnriching = false)
                val refined = enriched.parsed
                current.copy(
                    parsedMessage = refined,
                    selectedCategoryId = refined.categoryId ?: current.selectedCategoryId,
                    merchantConfidence = MerchantRepository.suggestCategory(refined.merchant ?: "")?.second
                        ?: current.merchantConfidence,
                    aiRefined = true,
                    isAiEnriching = false
                )
            }
        }
    }

    private fun saveTransaction() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val s = _state.value
                val parsed = s.parsedMessage

                val amount = if (s.useManualEntry) {
                    s.manualAmount.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid amount")
                } else {
                    s.manualAmount.toDoubleOrNull() ?: parsed?.amount ?: throw IllegalArgumentException("No amount detected")
                }

                val note = if (s.useManualEntry) s.manualNote else (s.manualNote.ifBlank { parsed?.note } ?: "Transaction")
                val merchant = if (s.useManualEntry) s.manualMerchant else parsed?.merchant

                val transaction = TransactionClassifier.toTransaction(
                    parsed = parsed ?: ParsedBankMessage(rawMessage = s.rawMessage),
                    categoryId = s.selectedCategoryId,
                    note = note,
                    manualAmount = amount,
                    manualType = s.manualType,
                    manualMerchant = merchant,
                    manualDate = LocalDateTime.now()
                )

                // Duplicate detection is *not* attempted here. The stub that used to
                // sit at this point always returned null, so the guard below it could
                // never fire and the dialog it opened was unreachable — while a
                // "Mark as duplicate" branch that set `saveSuccess = true` would have
                // closed the editor *without saving* had it ever been reached. The
                // CSV import path still checks duplicates (`DuplicateDetector`, via
                // NavGraph); finishing this path is a feature (review Direction 1),
                // not a fix, and it needs a repository read the ViewModel does not have.
                saveTransactionUseCase(transaction)
                rememberClassification(transaction)
                _state.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                AuraLog.e(TAG, "Saving transaction failed", e)
                _state.update { it.copy(isSaving = false, error = UiError.SAVE_FAILED) }
            }
        }
    }

    private fun loadAndClassifyAll() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val app = context.applicationContext as? AuraSpendApp ?: return
        val dao = app.database.smsMessageDao()

        // One observer, not one per trigger. This used to launch a fresh
        // never-cancelled full-table collector on each of its three intents, so
        // every extra trigger re-mapped every row on every database write for the
        // lifetime of the screen.
        smsQueueJob?.cancel()
        smsQueueJob = viewModelScope.launch {
            _state.update { it.copy(isScanningQueue = true, error = null) }

            // Snapshot new device messages into the persistent queue (idempotent on the provider
            // `_id`). Everything below reads the queue, so the list is stable across background
            // runs instead of being a fresh inbox scan each time.
            //
            // No `AutoClassificationWorker.runNow` here: that used to fire on every screen and
            // tab entry, enqueueing a whole extra pass each time. The always-running
            // `BackgroundClassificationCoordinator` is already draining this same queue.
            withContext(Dispatchers.IO) {
                boundaryOrNull(TAG) { SmsIngestor.ingest(context = context, dao = dao) }
            }

            // Observe the queue live: rows advance to CLASSIFYING / SAVED / SKIPPED / FAILED as the
            // background loop works through them, and the UI reflects real status instead of a
            // one-shot snapshot. The progress numbers come from the same rows, so the screen and
            // the notification can never disagree.
            dao.observeAll()
                .map { rows -> rows.map { it.toClassifiedSms() } }
                // Deriving the list re-derives the parse for every row without a
                // stored one (skipped + pending), so it is real work. Run it off
                // the main thread, and consume only the newest list: a drain
                // writes several times a second and every intermediate list
                // queued behind the UI was jank on the Smart Add tab.
                .flowOn(Dispatchers.Default)
                .conflate()
                // A read failure must not leave the tab spinning with a stale list
                // and no explanation: report it like every other failed load, and
                // let the retryable error state offer the way out.
                .catch { e ->
                    AuraLog.e(TAG, "Observing the SMS queue failed", e)
                    _state.update {
                        it.copy(isScanningQueue = false, error = UiError.LOAD_FAILED)
                    }
                }
                .collectLatest { classified ->
                    val progress = queueProgress(classified)
                    _state.update {
                        it.copy(
                            classifiedSmsList = classified,
                            // Handled messages leave the inbox. Undo reverts the
                            // stored status, the observer re-emits, and the message
                            // reappears here.
                            pendingSmsList = pendingInbox(classified),
                            skippedSmsList = skippedInbox(classified),
                            // The "pick a message to classify by hand" list is for
                            // candidates. Skipped rows are OTP/alerts, duplicates the
                            // pipeline rejected, or messages the user dismissed —
                            // offering them again as candidates would undo those decisions.
                            smsMessages = classified
                                .filter { c -> c.status != SmsStatus.SKIPPED }
                                .map { c -> c.sms },
                            progressCurrent = progress.processed,
                            progressTotal = progress.total,
                            isCategorising = progress.inFlight > 0,
                            queuedCount = progress.pending,
                            savedCount = progress.saved,
                            skippedCount = progress.skipped,
                            isScanningQueue = false
                        )
                    }
                }
        }
    }

    /**
     * Persists the merchant/note -> category mapping so repeat transactions skip the LLM entirely.
     * User saves are the strongest signal; failures here must never block a save.
     */
    private fun rememberClassification(transaction: Transaction) {
        val app = context.applicationContext as? AuraSpendApp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.classificationMemory.learn(
                    transaction,
                    com.awbuilds.auraspend.data.local.entities.ClassificationMemoryEntity.SOURCE_USER_SAVE
                )
            }
        }
    }

    private fun saveClassifiedSms(smsId: String) {
        viewModelScope.launch {
            try {
                val app = context.applicationContext as? AuraSpendApp ?: return@launch
                val dao = app.database.smsMessageDao()
                val classified = _state.value.classifiedSmsList.find { it.sms.id == smsId } ?: return@launch
                if (classified.status == SmsStatus.SAVED) return@launch

                // Never persist a message without an amount and direction — that would
                // create a meaningless ₹0 expense (OTP / promo / balance alerts).
                if (classified.parsed.amount == null || classified.parsed.type == null) {
                    AuraLog.w(
                        TAG,
                        "Triage save skipped: ${classified.sms.id} has no amount/type"
                    )
                    _state.update { it.copy(error = UiError.NO_TRANSACTION_FOUND) }
                    return@launch
                }

                val categories = _state.value.availableCategories
                val categoryId = classified.parsed.categoryId
                val effectiveCategory = if (categories.any { it.id == categoryId }) categoryId else "cat_other"

                val transaction = SmsAutoClassifier.toTransaction(
                    classified,
                    effectiveCategory,
                    receivedAt = classified.sms.timestamp
                )
                saveTransactionUseCase(transaction)
                rememberClassification(transaction)

                dao.getById(smsId)?.let { row ->
                    dao.update(
                        row.copy(
                            status = SmsMessageStatus.SAVED.name,
                            amount = transaction.amount,
                            type = transaction.type.name,
                            merchant = transaction.merchant,
                            categoryId = transaction.categoryId,
                            isSubscription = transaction.isRecurring,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                // Only now is "Saved" a true statement. The screen shows the success
                // surface (and its undo) off this event, never off the intent.
                _triageOutcomes.trySend(SmsTriageSaved(smsId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuraLog.e(TAG, "Saving a classified message failed", e)
                _state.update { it.copy(error = UiError.SAVE_FAILED) }
            }
        }
    }

    /**
     * Re-queues one skipped message: status back to NEW so the pipeline
     * classifies it again. This is the recovery path for a message that was
     * skipped wrongly — a parser fix lands on history through this door.
     */
    /**
     * Dismisses every pending message in one write loop. The per-row "Classify
     * again" action in the Skipped list is the way back, so a bulk skip is not
     * a bulk delete.
     */
    private fun skipAllClassified() {
        viewModelScope.launch {
            val app = context.applicationContext as? AuraSpendApp ?: return@launch
            val dao = app.database.smsMessageDao()
            val ids = _state.value.pendingSmsList.map { it.sms.id }
            if (ids.isEmpty()) return@launch
            val done = boundary(TAG, false) {
                ids.forEach { id ->
                    dao.getById(id)?.let { row ->
                        dao.update(
                            row.copy(
                                status = com.awbuilds.auraspend.data.local.entities.SmsMessageStatus.SKIPPED.name,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                true
            }
            if (!done) _state.update { it.copy(error = UiError.SAVE_FAILED) }
        }
    }

    private fun restoreSkippedSms(smsId: String) {
        viewModelScope.launch {
            val app = context.applicationContext as? AuraSpendApp ?: return@launch
            val dao = app.database.smsMessageDao()
            val done = boundary(TAG, false) {
                dao.getById(smsId)?.let { row ->
                    dao.update(
                        row.copy(
                            status = com.awbuilds.auraspend.data.local.entities.SmsMessageStatus.NEW.name,
                            attempts = 0,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                true
            }
            if (!done) {
                _state.update { it.copy(error = UiError.SAVE_FAILED) }
            }
        }
    }

    private fun saveAllClassified() {
        viewModelScope.launch {
            _state.update { it.copy(isSavingAll = true, error = null) }
            try {
                val app = context.applicationContext as? AuraSpendApp ?: return@launch
                val dao = app.database.smsMessageDao()
                val categories = _state.value.availableCategories
                // Only persist messages the classifier actually resolved, and only ones still
                // awaiting a decision.
                //
                // This used to filter `status == SmsStatus.CLASSIFIED`, which no row can ever
                // have: the queue mapper maps NEW → PENDING, CLASSIFYING → CLASSIFYING and the
                // terminal states to themselves, so "Save All" silently saved nothing.
                // A handled row (SAVED/SKIPPED) must not be re-saved here — that is what the
                // undo paths exist for.
                val toSave = _state.value.classifiedSmsList.filter {
                    (it.status == SmsStatus.PENDING || it.status == SmsStatus.CLASSIFYING) &&
                        it.parsed.amount != null &&
                        it.parsed.type != null
                }

                for (classified in toSave) {
                    val categoryId = classified.parsed.categoryId
                    val effectiveCategory = if (categories.any { it.id == categoryId }) categoryId else "cat_other"
                    val transaction = SmsAutoClassifier.toTransaction(
                        classified,
                        effectiveCategory,
                        receivedAt = classified.sms.timestamp
                    )
                    saveTransactionUseCase(transaction)
                    rememberClassification(transaction)
                    dao.getById(classified.sms.id)?.let { row ->
                        dao.update(
                            row.copy(
                                status = SmsMessageStatus.SAVED.name,
                                amount = transaction.amount,
                                type = transaction.type.name,
                                merchant = transaction.merchant,
                                categoryId = transaction.categoryId,
                                isSubscription = transaction.isRecurring,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                _state.update { it.copy(isSavingAll = false, saveSuccess = true) }
            } catch (e: Exception) {
                AuraLog.e(TAG, "Batch save failed", e)
                _state.update { it.copy(isSavingAll = false, error = UiError.SAVE_FAILED) }
            }
        }
    }

    private fun dismissClassifiedSms(smsId: String) {
        viewModelScope.launch {
            val app = context.applicationContext as? AuraSpendApp ?: return@launch
            val dao = app.database.smsMessageDao()
            val row = dao.getById(smsId) ?: return@launch
            if (row.status != SmsMessageStatus.SAVED.name) {
                dao.update(row.copy(status = SmsMessageStatus.SKIPPED.name, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    /**
     * Undo a triage save: drop the transaction it created and put the message
     * back in the queue. Without this, a mis-swipe silently added a transaction
     * the user never chose — the exact class of mistake handoff 0013 spent a
     * whole session eliminating from the classifier.
     */
    private fun undoSaveClassifiedSms(smsId: String) {
        viewModelScope.launch {
            boundary(TAG, Unit) {
                val app = context.applicationContext as? AuraSpendApp ?: return@boundary
                val db = app.database
                db.transactionDao().getTransactionBySourceSmsId(smsId)?.let { row ->
                    db.transactionDao().deleteTransactionById(row.id)
                }
                db.smsMessageDao().getById(smsId)?.let { row ->
                    db.smsMessageDao().update(
                        row.copy(
                            status = SmsMessageStatus.PROCESSED.name,
                            amount = null, type = null, merchant = null,
                            categoryId = null, confidence = 0f,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    /** Undo a triage dismiss: return the message to the queue unchanged. */
    private fun undoDismissClassifiedSms(smsId: String) {
        viewModelScope.launch {
            boundary(TAG, Unit) {
                val app = context.applicationContext as? AuraSpendApp ?: return@boundary
                app.database.smsMessageDao().getById(smsId)?.let { row ->
                    app.database.smsMessageDao().update(
                        row.copy(
                            status = SmsMessageStatus.PROCESSED.name,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    companion object {}

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            String.format("%.2f", amount)
        }
    }
}

private const val TAG = "ClassificationViewModel"
