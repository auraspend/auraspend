package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType

/**
 * Amazon Pay (delivered over Juspay's infrastructure) sender IDs.
 *
 * Two shapes, one direction: "Your Apay Wallet balance is debited for INR X…"
 * and "Payment of Rs X using Apay Balance successful at <merchant>". Money
 * leaves the wallet in both, but the second has no debit word the lexicon
 * knows — "Payment of" is phrased like a receipt elsewhere — so the parser
 * supplies the direction, and the label for wallet debits that name no
 * merchant (the wallet is the identity).
 */
class JuspayParser : BankParser {

    override val bankName = "Amazon Pay"

    override fun canHandle(sender: String): Boolean =
        sender.contains("JUSPAY") || sender.contains("APAY") || sender == "AMAZON PAY"

    private val apayWalletDebit = Regex(
        """(?i)\bapay\b[\s\S]{0,40}?\b(?:wallet|balance)\b[\s\S]{0,40}?\bdebit(?:ed)?\b"""
    )
    private val payUsingApay = Regex(
        """(?i)\bpayment\s+of\b[\s\S]{0,60}?\busing\s+apay\b[\s\S]{0,60}?\bsuccessful\b"""
    )

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val isApayDebit = apayWalletDebit.containsMatchIn(rawMessage) ||
            payUsingApay.containsMatchIn(rawMessage)
        val type = base.type ?: if (isApayDebit) TransactionType.EXPENSE else null
        val merchant = base.merchant
            ?: if (apayWalletDebit.containsMatchIn(rawMessage)) "Amazon Pay Transaction" else null
        return base.copy(bankName = bankName, type = type, merchant = merchant)
    }
}
