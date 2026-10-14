package com.awbuilds.auraspend.ui.classification

import com.awbuilds.auraspend.ui.core.UiError

import com.awbuilds.auraspend.data.ai.AiModelState
import com.awbuilds.auraspend.data.classification.ClassifiedSms
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType
import java.time.LocalDateTime

data class ClassificationViewState(
    val rawMessage: String = "",
    /**
     * Sender of [rawMessage] when it came from the inbox.
     *
     * Null for a pasted message. Carried through classification so the manual
     * path routes by sender exactly like the background pipeline does — the
     * per-bank parser is chosen from this, not from guessing at the body.
     */
    val rawMessageSender: String? = null,
    val parsedMessage: ParsedBankMessage? = null,
    val availableCategories: List<Category> = emptyList(),
    val selectedCategoryId: String = "cat_other",
    val manualAmount: String = "",
    val manualNote: String = "",
    val manualMerchant: String = "",
    val manualType: TransactionType = TransactionType.EXPENSE,
    val useManualEntry: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: UiError? = null,
    val smsMessages: List<SmsInfo> = emptyList(),
    val smsPermissionGranted: Boolean = false,
    val classifiedSmsList: List<ClassifiedSms> = emptyList(),

    /**
     * The triage inbox: only messages still awaiting a decision.
     *
     * Saved and dismissed messages leave this list, so handling one is a
     * one-way action on screen — undo puts it back by reverting the status,
     * which the observer reflows into this list. [classifiedSmsList] stays the
     * full queue and is kept for lookups that need it (`saveAllClassified`,
     * `saveClassifiedSms`).
     */
    val pendingSmsList: List<ClassifiedSms> = emptyList(),
    /**
     * Messages the pipeline skipped, newest first. The only surface where a
     * skipped message can be seen (and re-queued) at all.
     */
    val skippedSmsList: List<ClassifiedSms> = emptyList(),
    /**
     * True while the screen's first inbox snapshot is being taken.
     *
     * Not a progress bar: this covers the moment between opening the tab and the queue
     * observer's first emission, which for a cold start includes a large provider query.
     */
    val isScanningQueue: Boolean = false,
    /**
     * Live categorisation progress, derived from the queue itself rather than
     * reported by a worker.
     *
     * The queue row statuses ARE the status channel: a row moves NEW →
     * CLASSIFYING (in flight, rendered as a chip on that message) → SAVED /
     * SKIPPED / FAILED, and the UI observes the same table the pipeline writes.
     * A worker-reported progress number would be a second source of truth that
     * drifts the moment the app is backgrounded.
     */
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    /** True while at least one message is being classified right now. */
    val isCategorising: Boolean = false,
    /** Rows waiting to be classified but not currently in flight. */
    val queuedCount: Int = 0,
    /** Counts for this drain, shown next to the inbox header. */
    val savedCount: Int = 0,
    val skippedCount: Int = 0,
    val isSavingAll: Boolean = false,

    // On-device AI model
    val consentRequired: Boolean = false,
    val aiModelState: AiModelState = AiModelState.NotDownloaded,
    val isAiEnriching: Boolean = false,
    /** True once the async LLM/memory pass has refined the current classification. */
    val aiRefined: Boolean = false,
    val merchantConfidence: Float = 0f,
)

data class SmsInfo(
    val id: String,
    val address: String,
    val body: String,
    val timestamp: Long
)

/**
 * A triage "swipe right to save" that actually wrote a transaction.
 *
 * Deliberately an event rather than screen state, and deliberately emitted only on
 * success. The swipe used to fire the save intent and show a green "Saved" with an
 * undo action in the same breath, without ever looking at the outcome: a message
 * the classifier could not resolve, or a write that failed, still produced the
 * success surface while the row stayed (or vanished) with nothing behind it. As a
 * one-shot event it cannot be re-shown by a recomposition, and as a
 * success-only event it cannot claim something that did not happen — failures are
 * reported through `ClassificationViewState.error` and rendered as a banner.
 */
data class SmsTriageSaved(val smsId: String)

sealed class ClassificationViewIntent {
    data class MessageChanged(val message: String) : ClassificationViewIntent()
    object ClassifyMessage : ClassificationViewIntent()
    data class SelectCategory(val categoryId: String) : ClassificationViewIntent()
    data class AmountChanged(val amount: String) : ClassificationViewIntent()
    data class NoteChanged(val note: String) : ClassificationViewIntent()
    data class MerchantChanged(val merchant: String) : ClassificationViewIntent()
    data class TypeChanged(val type: TransactionType) : ClassificationViewIntent()
    object ToggleManualEntry : ClassificationViewIntent()
    object RequestSmsPermission : ClassificationViewIntent()
    data class SmsPermissionResult(val granted: Boolean) : ClassificationViewIntent()
    object LoadSmsMessages : ClassificationViewIntent()
    data class SmsSelected(val sms: SmsInfo) : ClassificationViewIntent()
    data class RestoreSkippedSms(val smsId: String) : ClassificationViewIntent()
    /** Dismisses every message still awaiting a decision. Recoverable from the Skipped list. */
    object SkipAllClassified : ClassificationViewIntent()
    object SaveTransaction : ClassificationViewIntent()
    object ResetSuccess : ClassificationViewIntent()
    object ClearError : ClassificationViewIntent()
    data class SetCategories(val categories: List<Category>) : ClassificationViewIntent()
    object LoadAndClassifyAll : ClassificationViewIntent()
    data class SaveClassifiedSms(val smsId: String) : ClassificationViewIntent()
    object SaveAllClassified : ClassificationViewIntent()
    data class DismissClassifiedSms(val smsId: String) : ClassificationViewIntent()

    // On-device AI model
    data class ConsentResult(val accepted: Boolean) : ClassificationViewIntent()

    /**
     * User declined the categoriser for now. Clears the offer **without** recording
     * consent, so it is not re-asked on this screen, but the model is still
     * downloadable later from Settings.
     */
    object DismissModelConsent : ClassificationViewIntent()
    /**
     * Reverse a triage "swipe right to save". Removes the transaction the save
     * created and returns the message to the inbox, so the gesture is safe to
     * perform by accident — which matters because it is a swipe.
     */
    data class UndoSaveClassifiedSms(val smsId: String) : ClassificationViewIntent()

    /** Reverse a triage "swipe left to dismiss": put the message back in the queue. */
    data class UndoDismissClassifiedSms(val smsId: String) : ClassificationViewIntent()

    object StartModelDownload : ClassificationViewIntent()
    object CancelModelDownload : ClassificationViewIntent()
    object RefreshAiModelState : ClassificationViewIntent()
}
