package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.data.classification.bank.BankParserRegistry
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

object TransactionClassifier {

    private const val TAG = "TransactionClassifier"

    /**
     * Parses one bank message, **routing by sender first**.
     *
     * The sender ID is free, reliable routing metadata that arrives with every
     * message, and a bank's own format disambiguates things no global regex can:
     * Axis puts the real amount on the first money line and a decoy limit on the
     * last, and Canara decorates every genuine debit with a fraud footer. This is
     * an AGENTS.md invariant ("classify by sender first") that was documented
     * while [BankParserRegistry.parserFor] had **zero** production callers — three
     * parsers and the registry were dead code, and only a unit test of the
     * registry suggested routing happened at all.
     *
     * Fall-through, in order:
     *  1. the parser registered for [sender], when it claims the message;
     *  2. [BankMessageParser], when no parser claims it — a normal outcome for
     *     most users, who bank somewhere that has no hand-written parser here;
     *  3. [BankMessageParser] again when the routed parser recognised the sender
     *     but could not resolve an amount *or* a type, because a half-parse is
     *     not worth more than the global parser's full one.
     *
     * [sender] is nullable: a pasted message has no sender, and neither does a
     * queue row written by a build that did not store it.
     */
    fun classify(rawMessage: String, sender: String? = null): ParsedBankMessage {
        // Parse globally first, then let the bank parser override only the
        // fields it actually resolved. The old code returned the routed result
        // whenever amount and type were non-null, which silently discarded a
        // merchant, date or category the global parser had already got right.
        val global = BankMessageParser.parse(rawMessage)
        return merge(global, routedParse(rawMessage, sender))
    }

    /**
     * Merge a routed parse over the global one, non-null field by field.
     *
     * The bank parser is a correction layer, not a replacement: a field it did
     * not resolve must fall through to the global parse. Date is the one field
     * with a precision rule — a routed parser that only knows the day must not
     * overwrite a global timestamp that carries the time.
     */
    internal fun merge(global: ParsedBankMessage, routed: ParsedBankMessage?): ParsedBankMessage {
        if (routed == null) return global
        return ParsedBankMessage(
            amount = routed.amount ?: global.amount,
            type = routed.type ?: global.type,
            merchant = routed.merchant ?: global.merchant,
            bankName = routed.bankName ?: global.bankName,
            date = morePreciseDate(routed.date, global.date),
            note = routed.note ?: global.note,
            categoryId = routed.categoryId ?: global.categoryId,
            confidence = maxOf(routed.confidence, global.confidence),
            rawMessage = global.rawMessage
        )
    }

    private fun morePreciseDate(routed: LocalDateTime?, global: LocalDateTime?): LocalDateTime? = when {
        routed == null -> global
        global == null -> routed
        routed.toLocalTime() == LocalTime.MIDNIGHT && global.toLocalTime() != LocalTime.MIDNIGHT -> global
        else -> routed
    }

    /**
     * The per-bank parse, or null when no parser claims this sender.
     *
     * Wrapped in a boundary even though [com.awbuilds.auraspend.data.classification.bank.BankParser.extract]
     * is documented never to throw: a parser bug must degrade to the global
     * parser, not take down whatever is classifying the user's messages.
     */
    private fun routedParse(rawMessage: String, sender: String?): ParsedBankMessage? {
        val parser = sender
            ?.takeIf { it.isNotBlank() }
            ?.let { BankParserRegistry.parserFor(it) }
            ?: return null
        return boundaryOrNull(TAG) { parser.extract(rawMessage) }
    }

    /**
     * The category a manual save should start on: the parser's answer, else the
     * merchant/message resolver, else Other. The triage screen used to stop at
     * `parsed.categoryId ?: "cat_other"` and disagree with the background
     * pipeline, which did apply the resolver.
     */
    fun resolveCategory(parsed: ParsedBankMessage): String =
        parsed.categoryId
            ?: BankMessageParser.categoryIdFor(parsed.rawMessage, parsed.merchant, parsed.type)
            ?: "cat_other"

    fun toTransaction(
        parsed: ParsedBankMessage,
        categoryId: String? = null,
        note: String? = null,
        manualAmount: Double? = null,
        manualType: TransactionType? = null,
        manualMerchant: String? = null,
        manualDate: LocalDateTime? = null
    ): Transaction {
        return Transaction(
            id = UUID.randomUUID().toString(),
            amount = manualAmount ?: parsed.amount ?: 0.0,
            categoryId = categoryId ?: parsed.categoryId ?: "cat_other",
            note = note ?: parsed.note ?: parsed.rawMessage.take(100),
            merchant = manualMerchant ?: parsed.merchant,
            bankName = parsed.bankName,
            date = manualDate ?: parsed.date ?: LocalDateTime.now(),
            type = manualType ?: parsed.type ?: TransactionType.EXPENSE
        )
    }
}
