package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * CRED sender IDs. The engine reads the bank's standard formats; this
 * parser supplies the sender→bank attribution for messages that never name
 * the bank in the body, and is the home of future CRED-specific
 * corrections.
 */
class CredParser : BankParser {

    override val bankName = "CRED"

    override fun canHandle(sender: String): Boolean = sender == "CRED" || sender == "CREDIN" || Regex("^[A-Z]{2}-CRED(?:IN)?(?:-[STPG])?$").containsMatchIn(sender)

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
