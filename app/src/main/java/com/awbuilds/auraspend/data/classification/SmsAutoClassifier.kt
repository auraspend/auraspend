package com.awbuilds.auraspend.data.classification

import android.content.Context
import android.provider.Telephony
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import com.awbuilds.auraspend.ui.classification.SmsInfo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Live status of a message while it is being categorized, shown in the UI. */
enum class SmsStatus { PENDING, CLASSIFYING, CLASSIFIED, SAVED, FAILED, SKIPPED }

data class ClassifiedSms(
    val sms: SmsInfo,
    val parsed: ParsedBankMessage,
    val isSaved: Boolean = false,
    /**
     * Swiped left, or rejected as noise (an OTP, a promo, a KYC nag).
     *
     * Distinct from "not saved" on purpose. It used to be folded into
     * [SmsStatus.CLASSIFIED], the same status a freshly-classified message has, so
     * nothing could tell "ready to save" from "dismissed" — which is why
     * `saveAllClassified` could pick up a dismissed message and save it.
     */
    val isDismissed: Boolean = false,
    val isSubscription: Boolean = false,
    val status: SmsStatus = SmsStatus.PENDING
)

/**
 * The messages a user still has to act on: neither saved nor dismissed.
 *
 * Extracted as a pure function because the rule is easy to break and expensive to
 * notice. A handled message must leave the triage list, and an undone one must come
 * back — and "coming back" is not extra logic, it is this same predicate applied to
 * a row whose status the undo reverted. Both properties are pinned in
 * `TriageInboxTest`.
 */
fun pendingInbox(messages: List<ClassifiedSms>): List<ClassifiedSms> =
    messages.filter { !it.isSaved && !it.isDismissed }

/**
 * Messages the pipeline (or the user) skipped — the audit surface.
 *
 * Skipped rows are deliberately absent from the triage inbox and the pick-list;
 * this is the one place they are listed, with a way back in ("Classify again"
 * re-queues the row, which is how a parser fix reaches old messages).
 */
fun skippedInbox(messages: List<ClassifiedSms>): List<ClassifiedSms> =
    messages.filter { it.isDismissed }

object SmsAutoClassifier {

    private val bankKeywords = listOf(
        "HDFC", "ICICI", "SBI", "Axis", "Kotak", "Yes Bank",
        "PNB", "Canara", "BOB", "UPI", "credited", "debited",
        "A/c", "account", "transaction", "spent", "paid",
        "INR", "Rs.", "withdrawal", "deposit", "balance"
    )

    // Patterns to filter out OTP messages. Structural: an OTP keyword plus a
    // standalone 4-8 digit code, or an explicit one-time-password phrase.
    private val otpPatterns = listOf(
        Regex("(?i)\\b(otp|one\\s*time\\s*(password|code)|security\\s+code)\\b"),
        Regex("(?i)\\b\\d{4,8}\\b\\s*(?:is|was|to)\\s+(?:your|the|a)\\s+(?:OTP|password|code|PIN)")
    )

    /**
     * Non-transaction notice patterns. A match only skips the message when the
     * amount/verb rescue below does not fire (i.e. no completed transaction).
     */
    private val alertPatterns = listOf(
        Regex("(?i)\\b(approved|approval|confirmation)\\b(?!.*(?:debited|credited|spent|paid))"),
        Regex("(?i)\\b(alert|notification|notify)\\b"),
        Regex("(?i)\\b(data\\s+usage|data\\s+plan|gb\\s+data|internet\\s+usage)\\b"),
        Regex("(?i)\\b(maintenance|system\\s+update|upgrade|server)\\b"),
        Regex("(?i)\\b(avail(?:able)?\\s+balance|bal(ance)?\\s+(?:amount|is|of)|current\\s+balance)\\b"),
        Regex("(?i)\\b(due|overdue|in\\s+arrears|deadline|maturity|interest\\s+(?:charged|posted)|annual\\s+fee)\\b"),
        // Payment asks are not payments: "Pls pay your bill of Rs X by <date>".
        Regex("(?i)\\b(?:please|pls)\\s+pay\\b"),
        Regex("(?i)\\b(congratulations|welcome|thank\\s+you)\\b"),
        Regex("(?i)\\b(limit\\s+(?:exceed|reached)|threshold|invalid|failed|declined|unsuccessful)\\b"),
        Regex("(?i)\\b(application|document|status|pending|submitted)\\b"),
        Regex("(?i)\\b(have\\s+used|utilization|remaining|offer|promo|%\\s*off|discount|sale|coupon)\\b"),
        Regex("(?i)\\b(kyc|re-?kyc|block(?:ed)?|unblock|token|de-?register)\\b"),
        Regex("(?i)\\b(fraud|scam|phishing|report\\s+cyber)\\b"),
        Regex("(?i)\\b(win|won|lucky\\s+draw|prize|lottery)\\b"),
        // Expiry reminders are dates, not money: "Use your 500 points at the
        // rewards store before they expire on 31-Oct." has an amount and no
        // movement, so the rescue below keeps it out of the veto's way only when
        // money really moved.
        Regex("(?i)\\b(expir(?:e|es|ed|ing|y)|before\\s+they\\s+expire)\\b"),
        // A fund's NAV is a price. A real SIP purchase that quotes one also says
        // "debited"/"purchased", which the rescue reads — a bare notice does not.
        Regex("(?i)\\b(NAV)\\b"),
        // Account-update notices ("An update has been made to your … account")
        // are framed as changes, not as movements.
        Regex("(?i)\\bupdate\\s+has\\s+been\\s+made\\b")
    )

    /**
     * Informational families that veto **even when an amount and a credit word are
     * present**, because the phrase itself is the money statement and no genuine
     * transaction says it. Measured on a closed tester's export (2026-10-16),
     * where a third of the rows were these shapes:
     *
     * - **Reward/loyalty points** — points are not rupees. "Rs.150 loyalty points
     *   have been credited" is a points balance moving, and it was booked as
     *   income; the same for earning and expiry notices.
     * - **Marketing with a strong signature** — "Pre-Qualified … Credit Card",
     *   "without income proof", "Special OFFER! … cashback", a joining gift. The
     *   offers quote amounts and even the word "cashback", which is why the
     *   rescued alert list let them through as income.
     *
     * Both stand down when [BankMessageParser.hasDebitVerb] finds money actually
     * leaving — "Rs.1,499 spent at X. You have earned 15 reward points." is a
     * purchase and must stay one.
     */
    private val pointsVetoPatterns = listOf(
        Regex("(?i)\\b(?:loyalty|reward|bonus)\\s+points?\\b[^.!]{0,40}\\b(?:credited|added|earned|refunded|expiring|expired|expire)\\b"),
        Regex("(?i)\\b(?:credited|added|earned|refunded)\\b[^.!]{0,24}\\b(?:loyalty|reward|bonus)\\s+points?\\b"),
        Regex("(?i)(?:Rs\\.?|INR|₹)\\s*[\\d,]+(?:\\.\\d+)?\\s*(?:loyalty|reward|bonus)\\s+points?\\b")
    )

    private val marketingVetoPatterns = listOf(
        Regex("(?i)\\b(?:pre-?qualified|without\\s+income\\s+proof|exclusive\\s+offer|special\\s+offer|joining\\s+(?:gift|bonus)|lucky\\s+draw|limited\\s+period\\s+offer)\\b")
    )

    /**
     * Hard vetoes: these prove NO completed transaction regardless of any amount or
     * debit/credit wording present. They bypass the amount+verb rescue below.
     *
     * NOTE: bare "fraud"/"report cyber fraud" is deliberately NOT a hard veto — Canara
     * appends "Dial 1930 to report cyber fraud" boilerplate to every genuine transaction
     * SMS. Only conditional/unauthorised framing and advance notices are unconditional;
     * the conditional framing carries one narrow exception in
     * [bankAlarmTailPattern], see there.
     */
    private val hardVetoPatterns = listOf(
        // Advance mandate registrations: money has NOT moved yet ("will be debited on <date>").
        Regex("(?i)\\bupcoming\\s+mandate\\b"),
        Regex("(?i)\\b(?:e|upi)[- ]?mandate\\b[\\s\\S]{0,120}?(?:has\\s+been\\s+registered|successfully\\s+created|set\\s+at|limit\\s+amount)"),
        // Payment reminders: "Ignore if paid" proves this SMS is not the payment itself.
        Regex("(?i)\\bignore\\s*,?\\s*if\\s+(?:already\\s+)?paid\\b"),
        Regex("(?i)\\b(?:installment|emi|bill)\\b[\\s\\S]{0,60}?\\bis\\s+due\\b"),
        // A refund only "initiated" has not happened yet; the credit message follows.
        Regex("(?i)\\brefund\\b[\\s\\S]{0,80}?\\binitiated\\b"),
        // Advance debit intimations — "Rs.500 will be debited from your A/c on
        // <date>". The completed debit arrives as its own SMS ("has been
        // debited"); booking the announcement doubles the spend. Covers the
        // auto-debit and EMI-deduction variants the mandate patterns miss.
        Regex("(?i)\\bwill\\s+be\\s+(?:auto[-\\s]?)?debit(?:ed|ing)?\\b"),
        // Payment requests: money is being ASKED for, not moved.
        Regex("(?i)\\b(?:has|have)\\s+requested\\b|\\b(?:payment|collect)\\s+request\\b|\\brequesting\\s+payment\\b|\\brequests\\s+rs\\b"),
        // "Convert your bill to EMI" invitations — a completed conversion
        // ("Rs.3282 debited … towards SmartEMI") still parses: the leading
        // lookahead keeps the veto only when no debit verb appears anywhere.
        Regex("(?i)^(?![\\s\\S]*\\b(?:debited|deducted|spent|charged|withdrawn)\\b)[\\s\\S]*\\bconvert(?:ed|s|ing)?\\b[\\s\\S]{0,80}?\\b(?:smartemi|into\\s+emi)\\b"),
        // "received … for processing" (NACH / cheque notices) — acknowledged,
        // not moved. Without this, "NACH Mandate … received today for
        // processing" read "received" as a credit verb and booked phantom
        // income of the full mandate amount.
        Regex("(?i)\\bfor\\s+processing\\b"),
        // Deposit-interest notices: "Net interest INR 248.07 paid on your
        // Deposit No …" — an intimation where "paid" read as an expense and
        // booked a phantom. A real credit ("net interest … credited") is
        // untouched: the veto needs "paid on".
        Regex("(?i)\\bnet\\s+interest\\b[\\s\\S]{0,80}?\\bpaid\\s+on\\b"),
        // Bill notices, not bills: "is due for payment", "…is due by
        // <date>". Measured on the owner's inbox (2026-10-13): every Jio
        // monthly bill and every card statement booked as an EXPENSE — the
        // amount is the amount owed, and no money has moved.
        Regex("(?i)\\b(?:is|are)\\s+due\\s+(?:for\\s+payment|by)\\b"),
        // Statement-mail notices: "E-statement … has been mailed. If not
        // received, SMS ENRS. Total Amt Due …" — "received" read as a credit
        // verb and booked phantom INCOME. No completed movement says "has
        // been mailed".
        Regex("(?i)\\bhas\\s+been\\s+mailed\\b"),
        // Future refunds: "Amt will be refunded within 3-4 days" — money has
        // not arrived yet; the credited message follows (same family as the
        // "refund … initiated" veto).
        Regex("(?i)\\bwill\\s+be\\s+refunded\\b"),
        // E-bill bodies that carry no "due for payment" phrase still announce
        // themselves: "Payment due date: …" / "Total amount payable : …".
        // The veto must live HERE rather than in type scoring, because the
        // classification memory can reinstate a type it once learned from a
        // wrongly-saved instance (measured 2026-10-13: three Jio e-bills were
        // resurrected by exactly that loop) — and the memory runs after the
        // ingest guards, never before them.
        Regex("(?i)\\bpayment\\s+due\\s+date\\b|\\btotal\\s+amount\\s+payable\\b"),
        // ── Credit-card bill settlements are the user's own money moving
        // between their own pockets, and both announcements look like
        // transactions alone:
        //  - card side — "Payment of Rs X has been received on your ICICI
        //    Bank Credit Card", "…payment … credited to your SBI Credit
        //    Card", "received as payment towards your PNB credit card";
        //  - bank side — "Rs X debited … towards PNB credit card payment",
        //    "… for credit card bill payment".
        // Spending is already counted when the card is swiped, so booking
        // either side double-counts it (and the card side used to inflate
        // income). Refunds, reversals and cashbacks ON the card are money
        // coming back and stay ingestable; so do card spends ("spent
        // using/at") — the lookaheads carve both out.
        Regex("(?i)^(?![\\s\\S]*\\b(?:refund|reversal|reversed|cashback|cash\\s+back)\\b)(?=[\\s\\S]*\\bcredit\\s+card\\b)[\\s\\S]*(?:\\bpayment\\b[\\s\\S]{0,80}?\\b(?:received|credited)\\b|\\breceived\\s+as\\s+payment\\b)"),
        Regex("(?i)^(?![\\s\\S]*\\b(?:spent|used|purchased?|charged|refund|reversal|cashback)\\b)(?=[\\s\\S]*\\bcredit\\s+card\\b)[\\s\\S]*\\b(?:towards|for|to)\\b[\\s\\S]{0,40}?\\b(?:bill|payment|dues?|outstanding)\\b")
    )

    /**
     * Conditional/unauthorised framing ("Rs.X debited? report cyber fraud if not done by
     * you"). A hard veto — with ONE exception, [bankAlarmTailPattern].
     */
    private val conditionalFramingPattern = Regex(
        "(?i)\\b(if\\s+not\\s+(?:done\\s+)?by\\s+you|if\\s+(?:it['’]?s?\\s+)?not\\s+you|if\\s+you\\s+(?:have\\s+)?not\\s+(?:done|authorised|initiated)|not\\s+authorised\\s+by\\s+you|unauthorised\\s+transaction)\\b"
    )

    /**
     * The bank's own alarm boilerplate: "If not you, call 1800 2662/SMS BLOCK 5003 to
     * <number>". ICICI appends this tail to every genuine card alert.
     *
     * Measured on the owner's real inbox (2026-10-13): vetoing this tail silently
     * deleted three real ICICI card spends — they appeared nowhere, not even in the
     * unrecognised list, because the veto has no rescue. This exception is deliberately
     * narrow (the exact call + "/ SMS BLOCK <code> to <number>" shape); all other
     * conditional framings stay unconditional, and the phishing case this veto exists
     * for ("if not done by you", "not authorised by you") is untouched.
     */
    private val bankAlarmTailPattern = Regex(
        "(?i)\\bif\\s+not\\s+you\\b[\\s\\S]{0,40}?\\bcall\\b[\\d\\s\\-]{6,}\\s*/\\s*sms\\s*block\\b[\\s\\S]{0,20}?\\d"
    )

    /**
     * PNB's alibi tail, which decorates genuine debits too: "If not done by
     * you, pl. forward this SMS from registered mobile to <number> to report
     * unauthorized txn & block IBS/MBS." Vetoing it deletes real spends — the
     * same failure the ICICI alarm tail caused. The shape is distinctly
     * bank-owned ("forward this SMS … report unauthorized"), so it rescues
     * while the phishing framings stay vetoed.
     */
    private val forwardAndReportTailPattern = Regex(
        "(?i)\\bif\\s+not\\s+done\\s+by\\s+you\\b[\\s\\S]{0,80}?\\bforward\\s+(?:this\\s+)?sms\\b[\\s\\S]{0,80}?\\breport\\s+unauthor"
    )

    /**
     * Converts a parsed message into a savable transaction. All ingestion flows through the
     * persistent Room queue (SmsIngestor -> sms_messages -> SmsPipelineProcessor), which takes
     * messages one at a time — never parse the inbox directly.
     *
     * [receivedAt] is the SMS provider's arrival timestamp (epoch millis). It dates the
     * transaction when the body carries no parseable date; zero means "unknown" and keeps the
     * old save-clock behaviour for callers that have no message (tests, manual inserts).
     */
    fun toTransaction(
        classified: ClassifiedSms,
        categoryId: String? = null,
        receivedAt: Long = 0L
    ): Transaction {
        val parsed = classified.parsed
        val subscriptionCategory = if (classified.isSubscription) "cat_subscription" else null
        val effectiveCategory = subscriptionCategory ?: categoryId ?: parsed.categoryId ?: "cat_other"
        return Transaction(
            amount = parsed.amount ?: 0.0,
            categoryId = effectiveCategory,
            note = parsed.note ?: parsed.rawMessage.take(100),
            merchant = parsed.merchant,
            bankName = parsed.bankName,
            // A body with no parseable date used to fall back to the save clock,
            // which booked a July message as the October day the drain reached it
            // (owner's device, 2026-10-13). The provider's arrival timestamp is
            // the honest fallback.
            date = parsed.date
                ?: if (receivedAt > 0L) {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(receivedAt), ZoneId.systemDefault())
                } else {
                    LocalDateTime.now()
                },
            type = parsed.type ?: TransactionType.EXPENSE,
            isRecurring = classified.isSubscription,
            sourceSmsId = classified.sms.id.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Latest bank-like messages from the device inbox.
     *
     * The bounds are **exclusive**, so a message sharing a cursor's exact
     * timestamp is re-readable rather than permanently skipped — re-reads are
     * ignored by the queue's `OR IGNORE` insert, whereas a skipped message is
     * lost. [oldestFirst] exists for the incremental scan: walking forward from a
     * high-water mark is the only ordering that makes progress when more than one
     * page has arrived, because newest-first would return the same page for ever.
     *
     * [beforeTimestamp] is the history walk's upper bound; [sinceTimestamp] alone
     * cannot express "older than what I have already looked at".
     */
    fun queryBankSms(
        context: Context,
        sinceTimestamp: Long = 0L,
        maxMessages: Int = 500,
        beforeTimestamp: Long = Long.MAX_VALUE,
        oldestFirst: Boolean = false
    ): List<SmsInfo> {
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.Inbox._ID,
            Telephony.Sms.Inbox.ADDRESS,
            Telephony.Sms.Inbox.BODY,
            Telephony.Sms.Inbox.DATE
        )

        val selection = buildString {
            append("(${bankKeywords.joinToString(" OR ") { "${Telephony.Sms.Inbox.BODY} LIKE '%$it%'" }})")
            if (sinceTimestamp > 0L) {
                append(" AND ${Telephony.Sms.Inbox.DATE} > $sinceTimestamp")
            }
            if (beforeTimestamp != Long.MAX_VALUE) {
                append(" AND ${Telephony.Sms.Inbox.DATE} < $beforeTimestamp")
            }
        }
        val direction = if (oldestFirst) "ASC" else "DESC"
        val sortOrder = "${Telephony.Sms.Inbox.DATE} $direction LIMIT $maxMessages"

        val cursor = context.contentResolver.query(uri, projection, selection, null, sortOrder)

        val messages = mutableListOf<SmsInfo>()
        cursor?.use { c ->
            val idIdx = c.getColumnIndex(Telephony.Sms.Inbox._ID)
            val addrIdx = c.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)
            val bodyIdx = c.getColumnIndex(Telephony.Sms.Inbox.BODY)
            val dateIdx = c.getColumnIndex(Telephony.Sms.Inbox.DATE)

            while (c.moveToNext()) {
                val id = if (idIdx >= 0) c.getString(idIdx) else ""
                val addr = if (addrIdx >= 0) c.getString(addrIdx) else ""
                val body = if (bodyIdx >= 0) c.getString(bodyIdx) else ""
                val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L

                if (looksLikeBankSender(addr) ||
                    bankKeywords.any { body.contains(it, ignoreCase = true) }
                ) {
                    messages.add(SmsInfo(id = id, address = addr, body = body, timestamp = date))
                }
            }
        }

        return messages.sortedByDescending { it.timestamp }
    }

    /**
     * Indian banks send via DLT alphabetic sender IDs, commonly with a
     * two-letter channel prefix: "AD-HDFCBK", "VM-ICICIB", "JD-SBICRD".
     */
    private val bankSenderRegex = Regex(
        "(?i)(?:^[A-Z]{2}-?)?(hdfc|icici|sbic?|sbin|axisb?|kotak|yesb|pnbn?|canara|barb|" +
            "idfc|idbi|indusinb?k?|unionbnk|indbnk|federal|rblbnk|aubank|bandhan|paytm|airtel)"
    )

    internal fun looksLikeBankSender(address: String): Boolean =
        address.isNotBlank() && bankSenderRegex.containsMatchIn(address.trim())

    /** A currency amount written the way banks write it. */
    private val amountMarker = Regex("""(?:Rs\.?|INR|₹)\s*\d|₹\s*\d|\d+\s*(?:Rs|INR)""")

    /**
     * True when the message states both an amount and a movement — i.e. it is a
     * transaction.
     *
     * The movement half comes from [BankMessageParser.hasMovementVerb] so this
     * filter and the parser can never disagree about what money moving looks
     * like. The local six-word copy it replaced (`debited|credited|spent|paid|
     * transferred|withdrawn`) omitted the whole credit lexicon, so a deposit or
     * refund carrying a balance tail was dropped as an "alert" even though it
     * named a completed movement.
     */
    fun looksLikeTransaction(message: String): Boolean =
        amountMarker.containsMatchIn(message) && BankMessageParser.hasMovementVerb(message)

    fun isOtpOrAlertMessage(message: String): Boolean {
        // OTPs are skipped — unless the message is really a debit that happens to
        // end in an OTP code. "Spent INR 149 at SWIGGY. Your OTP for this
        // transaction is 445122." is a real transaction, and dropping it silently
        // loses the user's money entry. The OTP branch used to have no rescue and
        // lost exactly that case.
        if (otpPatterns.any { it.containsMatchIn(message) }) {
            return !looksLikeTransaction(message)
        }

        // Fraud warnings quote amounts and debit verbs to look real — no rescue.
        // This is deliberate: a phishing SMS ("...Rs.10,000 has been debited?
        // report cyber fraud... Phishing alert.") parses identically to a genuine
        // one, so the only thing separating them is this veto. Losing one real
        // transaction is far cheaper than inventing a phantom one.
        if (hardVetoPatterns.any { it.containsMatchIn(message) }) {
            return true
        }

        // Conditional framing is the same veto, minus the bank's own alarm tail
        // ([bankAlarmTailPattern]) — ICICI appends it to genuine card alerts, and
        // vetoing it deleted real spends. That tail falls through to the alert
        // branch below, which keeps its amount+verb rescue.
        if (conditionalFramingPattern.containsMatchIn(message) &&
            !bankAlarmTailPattern.containsMatchIn(message) &&
            !forwardAndReportTailPattern.containsMatchIn(message)
        ) {
            return true
        }

        // Informational families whose phrase *is* the money statement — points
        // balances and marketing offers. These outrank the amount+verb rescue,
        // because their wording ("points have been credited", "get Rs.1000*
        // cashback") is exactly what the rescue reads as a movement. A completed
        // debit still outranks them: a real purchase may mention points or an
        // offer, and it must not be dropped for it.
        if (!BankMessageParser.hasDebitVerb(message)) {
            if (pointsVetoPatterns.any { it.containsMatchIn(message) }) return true
            if (marketingVetoPatterns.any { it.containsMatchIn(message) }) return true
        }

        // Check for alert patterns
        if (alertPatterns.any { it.containsMatchIn(message) }) {
            // But make sure it's not a transaction (has amount + debit/credit pattern)
            return !looksLikeTransaction(message)
        }

        return false
    }
}
