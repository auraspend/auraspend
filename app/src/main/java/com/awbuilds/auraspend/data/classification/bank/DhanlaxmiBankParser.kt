package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Dhanlaxmi Bank's sender IDs and its quoted UPI-detail clause.
 *
 * Dhanlaxmi buries the counterparty inside a quoted UPI block:
 * `"UPI TXN: /675325120952-MR /Payment from PhonePe/Q12345444@ybl/…"`. The
 * generic `from` pattern cannot cross the quote or the `/`, and the VPA that
 * follows belongs to the payment app, not the merchant — "Payment from X"
 * names the party the user recognises.
 */
class DhanlaxmiBankParser : BankParser {

    override val bankName = "Dhanlaxmi Bank"

    override fun canHandle(sender: String): Boolean =
        sender.contains("DHANBK") || sender.contains("DHANLAXMI") ||
            Regex("^[A-Z]{2}-DHANBK(?:-[A-Z])?$").containsMatchIn(sender)

    private val paymentFrom = Regex(
        """(?i)\bpayment\s+from\s+([A-Za-z][A-Za-z0-9&.'\- ]{1,38}?)(?=[/"\s]|\.|$)"""
    )

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = paymentFrom.find(rawMessage)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.length >= 2 }
            ?: base.merchant
        return base.copy(bankName = bankName, merchant = merchant)
    }
}
