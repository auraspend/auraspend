package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * South Indian Bank's sender IDs ("SIBSMS", "SOUTHINDIANBANK", "SIBBANK",
 * DLT templates like "AD-SIBSMS-S"). The engine reads the bank's formats; this
 * parser supplies the sender→bank attribution for messages that never name the
 * bank in the body.
 */
class SouthIndianBankParser : BankParser {

    override val bankName = "South Indian Bank"

    private val dlt = Regex("^[A-Z]{2}-SIBSMS(?:-[ST])?$")

    override fun canHandle(sender: String): Boolean =
        sender.contains("SIBSMS") || sender.contains("SOUTHINDIANBANK") ||
            sender.contains("SIBBANK") || dlt.containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
