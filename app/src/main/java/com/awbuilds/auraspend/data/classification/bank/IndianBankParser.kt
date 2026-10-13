package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Indian Bank's sender IDs and its ATM label.
 *
 * The engine reads the standard shapes — VPA credits ("…by a/c linked to VPA
 * poweraccess.paytm3@axisbank" keeps its machine handle), "Sent Rs… to PAYEE"
 * UPI debits, NEFT credits with balance tails. This parser adds:
 *  - "Rs. 2000 withdrawn from ATM at MAIN STREET BRANCH" — the ATM location is
 *    the only identity the message carries, and "ATM - " names the channel.
 */
class IndianBankParser : BankParser {

    override val bankName = "Indian Bank"

    override fun canHandle(sender: String): Boolean =
        !sender.contains("SOUTH") &&
            (
                sender.contains("INDIANBANK") || sender.contains("INDIANBK") ||
                    sender.contains("INDIAN BANK") || sender == "INDBNK" ||
                    Regex("^[A-Z]{2}-INDBNK(?:-[STPG])?$").containsMatchIn(sender)
                )

    private val atmWithdrawal = Regex(
        """(?i)\bwithdrawn\s+from\s+ATM\s+at\s+([^.\n]{1,40}?)(?=\s+on\b|[.\n]|$)"""
    )

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = atmWithdrawal.find(rawMessage)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.length >= 2 }
            ?.let { "ATM - $it" }
            ?: base.merchant
        return base.copy(bankName = bankName, merchant = merchant)
    }
}
