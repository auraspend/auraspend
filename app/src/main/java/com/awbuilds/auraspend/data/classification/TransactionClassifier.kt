package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.core.boundaryOrNull
import com.awbuilds.auraspend.data.classification.bank.BankParserRegistry
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import java.time.LocalDateTime
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
        val routed = routedParse(rawMessage, sender)
        if (routed?.amount != null && routed.type != null) return routed
        return BankMessageParser.parse(rawMessage)
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
