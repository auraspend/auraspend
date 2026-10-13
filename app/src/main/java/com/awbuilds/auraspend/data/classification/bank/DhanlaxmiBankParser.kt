package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Dhanlaxmi Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Dhanlaxmi Bank-specific
 * corrections.
 */
class DhanlaxmiBankParser : BankParser {

    override val bankName = "Dhanlaxmi Bank"

    override fun canHandle(sender: String): Boolean = sender.contains("DHANBK") || sender.contains("DHANLAXMI") || Regex("^[A-Z]{2}-DHANBK(?:-[A-Z])?$").containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
