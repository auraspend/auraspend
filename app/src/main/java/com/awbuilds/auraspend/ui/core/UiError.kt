package com.awbuilds.auraspend.ui.core

import androidx.annotation.StringRes
import com.awbuilds.auraspend.R

/**
 * A user-facing failure reason.
 *
 * ViewModels used to set `error = e.message` straight into screen state, so a user
 * could be shown `SQLiteConstraintException: UNIQUE constraint failed ...` or a
 * bare "Failed to save" in English. That is both unlocalized and technically
 * meaningless to the person looking at their bank balance.
 *
 * The ViewModel now reports *what* went wrong and logs the cause; the composable
 * resolves the message from resources at render time, which keeps the copy
 * localizable and testable.
 */
enum class UiError(
    // `@param:` is explicit on purpose. `@StringRes` is applicable to a
    // constructor parameter *and* to a field, and Kotlin warns that the default
    // target will grow to include the field; only the parameter is meant here.
    // It is what makes lint check the `R.string.*` id each entry passes.
    @param:StringRes val messageRes: Int,
    /** Whether offering a retry makes sense for this failure. */
    val retryable: Boolean = false
) {
    /** An aggregate/read query failed; the screen has nothing to show. */
    LOAD_FAILED(R.string.error_load_failed, retryable = true),

    /** Writing a transaction, budget or subscription failed. */
    SAVE_FAILED(R.string.error_save_failed, retryable = true),

    /** Deleting failed. */
    DELETE_FAILED(R.string.error_delete_failed, retryable = true),

    /** A backup could not be read or applied. */
    RESTORE_FAILED(R.string.error_restore_failed, retryable = true),

    /** The classifier found no transaction in the supplied message. */
    NO_TRANSACTION_FOUND(R.string.error_no_transaction),

    /** The on-device model could not be loaded or started. */
    MODEL_UNAVAILABLE(R.string.error_model_unavailable),

    /** The app lacks a permission the action requires. */
    PERMISSION_DENIED(R.string.error_permission_denied),

    /** Catch-all. Never render a raw exception message to the user. */
    UNKNOWN(R.string.error_unknown, retryable = true)
}

/** Maps a throwable to a user-facing reason without leaking its message. */
fun Throwable.toUiError(fallback: UiError = UiError.UNKNOWN): UiError = when (this) {
    is SecurityException -> UiError.PERMISSION_DENIED
    is java.io.FileNotFoundException, is java.io.IOException -> fallback
    else -> fallback
}
