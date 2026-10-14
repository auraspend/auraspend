package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Department of Post sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Department of Post-specific
 * corrections.
 */
class DopBankParser : BankParser {

    override val bankName = "Department of Post"

    override fun canHandle(sender: String): Boolean = sender.contains("DOPBNK") || sender.contains("DEPARTMENT OF POST") || sender == "DOP" || sender.endsWith("-DOP") || sender.contains("DOP-")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
