package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Kerala Gramin Bank's sender IDs ("AD-KGBANK-S", "KGBANK"). The engine reads
 * the bank's formats — including VPA credits ("from 9000000042@upi" keeps the
 * number as the counterparty); this parser supplies the sender→bank
 * attribution. Its dual-account "debited … and credited to a/c …" UPI shape
 * deliberately stays with the project's skip-not-guess behaviour.
 */
class KeralaGraminBankParser : BankParser {

    override val bankName = "Kerala Gramin Bank"

    override fun canHandle(sender: String): Boolean =
        sender.contains("KGBANK") || sender.contains("KERALA GRAMIN") ||
            sender.contains("KERALAGR")

    override fun extract(rawMessage: String): ParsedBankMessage =
        BankMessageParser.parse(rawMessage).copy(bankName = bankName)
}
