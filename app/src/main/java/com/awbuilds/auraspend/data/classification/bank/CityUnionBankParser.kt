package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * City Union Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future City Union Bank-specific
 * corrections.
 */
class CityUnionBankParser : BankParser {

    override val bankName = "City Union Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("CUBANK") || sender.contains("CUBLTD") || sender == "CUB" || Regex("^[A-Z]{2}-CUBLTD(?:-[ST])?$").containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
