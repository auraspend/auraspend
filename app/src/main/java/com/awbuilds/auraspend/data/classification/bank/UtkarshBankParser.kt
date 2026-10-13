package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Utkarsh Small Finance Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Utkarsh Small Finance Bank-specific
 * corrections.
 */
class UtkarshBankParser : BankParser {

    override val bankName = "Utkarsh Small Finance Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("UTKSPR") || sender.contains("UTKARSH") || sender.contains("UTKSFB")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
