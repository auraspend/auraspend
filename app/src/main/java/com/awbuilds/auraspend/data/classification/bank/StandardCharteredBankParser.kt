package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Standard Chartered sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Standard Chartered-specific
 * corrections.
 */
class StandardCharteredBankParser : BankParser {

    override val bankName = "Standard Chartered"

    override fun canHandle(sender: String): Boolean = sender.contains("SCBANK") || sender.contains("STANCHART") || sender.contains("STANDARDCHARTERED") || sender.contains("STANDARD CHARTERED") || sender == "9220" || Regex("^[A-Z]{2}-SCBANK(?:-[STPG])?$").containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
