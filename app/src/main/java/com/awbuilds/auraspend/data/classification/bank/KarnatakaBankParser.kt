package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Karnataka Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Karnataka Bank-specific
 * corrections.
 */
class KarnatakaBankParser : BankParser {

    override val bankName = "Karnataka Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("KARNATAKA BANK") || sender.contains("KARNATAKABANK") || sender.contains("KBLBNK") || sender.contains("KTKBANK") || sender.contains("KARBANK")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
