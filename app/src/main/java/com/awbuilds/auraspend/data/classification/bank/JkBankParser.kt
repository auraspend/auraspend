package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * JK Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future JK Bank-specific
 * corrections.
 */
class JkBankParser : BankParser {

    override val bankName = "JK Bank"

    override fun canHandle(sender: String): Boolean = sender == "JKBANK" || sender == "JKB" || sender == "JKBANKL" || sender == "JKBNK" || Regex("^[A-Z]{2}-(?:JKBANK|JKBNK|JKB)(?:-[A-Z]+)?$").containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
