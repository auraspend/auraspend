package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage
import com.awbuilds.auraspend.domain.model.TransactionType

/**
 * Bank of Baroda's sender IDs ("VM-BOBTXN-S", "VM-BOBSMS", "VM-BOBCRD-S", …).
 *
 * The engine reads BoB's formats — including the "transferred … to:<payee>"
 * colon form, which is a general merchant pattern — this parser adds the
 * sender→bank attribution and one direction fix:
 *  - "Rs.X transferred from A/c …" is money leaving. The engine's debit
 *    lexicon deliberately leans on "transferred to" (a bare "transferred" is
 *    ambiguous between the two directions), so BoB's "transferred from A/c"
 *    phrasing — the bank's own transfer format — is restored to EXPENSE here,
 *    and only when the engine could not decide.
 */
class BankOfBarodaParser : BankParser {

    override val bankName = "Bank of Baroda"

    override fun canHandle(sender: String): Boolean =
        sender.contains("BOB") || sender.contains("BARODA")

    private val transferOut = Regex("""(?i)\btransferred\s+from\s+(?:A/c|a/c|account)\b""")

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val type = base.type
            ?: if (transferOut.containsMatchIn(rawMessage)) TransactionType.EXPENSE else null
        return base.copy(bankName = bankName, type = type)
    }
}
