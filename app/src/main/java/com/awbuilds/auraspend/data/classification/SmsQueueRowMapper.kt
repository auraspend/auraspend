package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageStatus
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.classification.SmsInfo

private const val TAG = "SmsQueueMapper"

/**
 * Parses a stored enum name without throwing.
 *
 * The `valueOf` this replaces was the same bug handoff 0015 fixed in `Mappers.kt`
 * — one row whose enum string did not match the current enum took down the whole
 * reader. There it blanked the Dashboard; here it froze the SMS inbox on its last
 * snapshot, because the throw happened inside the ViewModel's Room `Flow.map` and
 * nothing caught it. Drive restore repopulates these columns wholesale, so they
 * are backup-controlled input, not trusted values.
 *
 * Returns null for an unknown name; the call site decides what "unknown" means,
 * because the honest fallback differs: an unreadable *type* is re-derived from
 * the message body, while an unreadable *status* must not be treated as handled.
 */
internal inline fun <reified T : Enum<T>> String?.storedEnumOrNull(): T? {
    val raw = this ?: return null
    return enumValues<T>().firstOrNull { it.name == raw }
}

/**
 * Maps one persistent-queue row to the UI's classified-message model.
 *
 * Lives here rather than inside `ClassificationViewModel` so the enum-safety
 * rules above are unit-testable without a ViewModel, a Room database or a
 * dispatcher.
 */
internal fun SmsMessageEntity.toClassifiedSms(): ClassifiedSms {
    val storedType = type.storedEnumOrNull<TransactionType>()
    val parsed = if (amount != null && storedType != null) {
        ParsedBankMessage(
            amount = amount,
            type = storedType,
            merchant = merchant,
            categoryId = categoryId,
            date = null,
            note = body.take(100),
            rawMessage = body
        )
    } else {
        if (amount != null && type != null) {
            AuraLog.w(
                TAG,
                "SMS queue row $id has an unknown type '$type'; re-parsing the body instead"
            )
        }
        // No stored parse, or a corrupt one: derive it from the body. A re-parse
        // can come back empty, and an empty parse keeps the row in the triage
        // inbox rather than hiding it.
        TransactionClassifier.classify(body, address)
    }

    val storedStatus = status.storedEnumOrNull<SmsMessageStatus>()
    if (storedStatus == null) {
        // Surfaced as pending on purpose. An unparseable status means "unknown",
        // and hiding an unknown row whose body may hold a real debit is the
        // silent money loss this project refuses; showing it costs one swipe.
        AuraLog.w(
            TAG,
            "SMS queue row $id has an unknown status '$status'; showing it as pending"
        )
    }

    return ClassifiedSms(
        sms = SmsInfo(id = id, address = address, body = body, timestamp = receivedAt),
        parsed = parsed,
        // Derived from the parsed enum, never from a raw string comparison.
        isSaved = storedStatus == SmsMessageStatus.SAVED,
        isDismissed = storedStatus == SmsMessageStatus.SKIPPED,
        isSubscription = isSubscription,
        status = when (storedStatus) {
            SmsMessageStatus.NEW, null -> SmsStatus.PENDING
            SmsMessageStatus.PROCESSED -> SmsStatus.CLASSIFYING
            SmsMessageStatus.SAVED -> SmsStatus.SAVED
            SmsMessageStatus.SKIPPED -> SmsStatus.SKIPPED
            SmsMessageStatus.FAILED -> SmsStatus.FAILED
        }
    )
}
