package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Central Bank of India's sender IDs ("CENTBK", "CBOI", "CENTRALBANK"). The
 * engine reads the bank's formats; this parser supplies the sender→bank
 * attribution for messages that never name the bank in the body.
 */
class CentralBankOfIndiaParser : BankParser {

    override val bankName = "Central Bank of India"

    private val dlt = Regex("^[A-Z]{2}-(?:CENTBK|CBOI)(?:-[A-Z])?$")

    override fun canHandle(sender: String): Boolean =
        sender.contains("CENTBK") || sender.contains("CBOI") ||
            sender.contains("CENTRALBANK") || dlt.containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
