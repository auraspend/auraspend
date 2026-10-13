package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Saraswat Bank sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future Saraswat Bank-specific
 * corrections.
 */
class SaraswatBankParser : BankParser {

    override val bankName = "Saraswat Bank"

    override fun canHandle(sender: String): Boolean = sender == "SARBNK" || sender == "SARASWAT" || sender == "SARASWATBANK" || Regex("^[A-Z]{2}-(?:SARBNK|SARASWAT)(?:-[ST])?$").containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
