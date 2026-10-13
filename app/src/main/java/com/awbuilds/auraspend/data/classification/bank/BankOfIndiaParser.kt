package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Bank of India's sender IDs ("BOIIND", "BOIBNK", "XX-BOIIND-ST"). The engine
 * reads the bank's formats; this parser supplies the sender→bank attribution
 * for messages that never name the bank in the body.
 */
class BankOfIndiaParser : BankParser {

    override val bankName = "Bank of India"

    private val dlt = Regex("^[A-Z]{2}-(?:BOIIND|BOIBNK|BOI)(?:-[ST])?$")

    override fun canHandle(sender: String): Boolean =
        sender == "BOIIND" || sender == "BOIBNK" || dlt.containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
