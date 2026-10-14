package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * UCO Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future UCO Bank-specific
 * corrections.
 */
class UcoBankParser : BankParser {

    override val bankName = "UCO Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("UCOBNK") || sender.contains("UCOBANK") || sender.contains("UCO BANK") || Regex("^[A-Z]{2}-UCOBNK(?:-[STPG])?$").containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
