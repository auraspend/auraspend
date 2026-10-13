package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.BankMessageParser
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * Navi Mutual Fund's sender IDs ("AD-NAVAMC-S", "VK-NAVAMC-T"; keyed on
 * NAVAMC so the NAVI banking-arm senders stay out).
 *
 * SIP allotments name the fund in-clause — "…SIP purchase of Rs.499.98 in
 * Navi Nifty Next 50 Index Fund DG has been processed…" — and that clause
 * must take precedence over the "at applicable NAV" tail the generic `at`
 * pattern would otherwise read as a merchant.
 */
class NaviMutualFundParser : BankParser {

    override val bankName = "Navi Mutual Fund"

    override fun canHandle(sender: String): Boolean = sender.contains("NAVAMC")

    private val fundName = Regex(
        """(?i)\bin\s+([A-Za-z][A-Za-z0-9 .\-&]{1,48}?)\s+has\s+been\s+processed"""
    )

    override fun extract(rawMessage: String): ParsedBankMessage {
        val base = BankMessageParser.parse(rawMessage)
        val merchant = fundName.find(rawMessage)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.length >= 2 }
            ?: base.merchant
        return base.copy(bankName = bankName, merchant = merchant)
    }
}
