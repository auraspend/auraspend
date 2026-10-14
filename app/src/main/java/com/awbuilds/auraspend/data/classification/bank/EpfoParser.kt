package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType

/**
 * EPFO's sender IDs ("VA-EPFOHO-G", "AX-EPFOHO-S").
 *
 * A contribution confirmation names two amounts — the passbook balance first,
 * the month's contribution second — and ends "…has been received", so the
 * global engine booked the first amount as income. An employer's PF deposit
 * then inflated the month's income while touching nothing spendable. The
 * owner's call (2026-10-14): a PF contribution is an investment, not income.
 *
 * Only a message that actually carries the contribution clause is re-typed:
 * the OTP and UAN-linking notices from the same sender keep the base parse
 * (and are dropped by the unresolved-fields gate for having no amount).
 */
class EpfoParser : BankParser {

    override val bankName = "EPFO"

    override fun canHandle(sender: String): Boolean = sender.contains("EPFO")

    private val contribution = Regex(
        """(?i)\bcontribution\s+of\s+(?:Rs\.?\s*|INR\s*)([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val amount = contribution.find(rawMessage)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?: return base
        return base.copy(
            amount = amount,
            type = TransactionType.EXPENSE,
            categoryId = "cat_investment",
            merchant = "EPF",
            // "for due month Jun-26" names the period the contribution is for,
            // not a transaction day; the arrival timestamp is the honest date.
            date = null,
            bankName = bankName
        )
    }
}
