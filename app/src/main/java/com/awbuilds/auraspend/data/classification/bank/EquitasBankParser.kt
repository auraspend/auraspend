package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Equitas Small Finance Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Equitas Small Finance Bank-specific
 * corrections.
 */
class EquitasBankParser : BankParser {

    override val bankName = "Equitas Small Finance Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("EQUTAS") || sender.contains("EQUITA") || sender.contains("EQUITS")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
