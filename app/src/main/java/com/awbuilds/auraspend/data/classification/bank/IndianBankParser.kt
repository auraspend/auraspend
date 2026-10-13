package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Indian Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Indian Bank-specific
 * corrections.
 */
class IndianBankParser : BankParser {

    override val bankName = "Indian Bank"

    override fun canHandle(sender: String): Boolean = !sender.contains("SOUTH") && (sender.contains("INDIANBANK") || sender.contains("INDIANBK") || sender.contains("INDIAN BANK") || sender == "INDBNK" || Regex("^[A-Z]{2}-INDBNK(?:-[STPG])?$").containsMatchIn(sender))

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
