package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.domain.model.ParsedBankMessage

/**
 * One bank's SMS formats, hand-written.
 *
 * A per-bank parser exists because a bank's transaction message carries several
 * amounts and several near-miss tokens, and only that bank's own format
 * disambiguates them. The golden corpus's Axis case is the clearest example:
 *
 * ```
 * Spent INR 135
 * Axis Bank Card
 * no. XX4821
 * 05-07-26 10:15:22 IST
 * Tulip
 * Avl Limit: INR 42731.09
 * ```
 *
 * There are two amounts, a card number that looks like an id, a timestamp, and
 * the merchant sitting between the timestamp and a balance line that a naive
 * "last amount wins" heuristic picks up instead. A single global regex has to
 * resolve all of that; a parser that knows the message is Axis, and knows the
 * Axis layout, does not.
 *
 * This is the same idea as `sarim2000/pennywiseai-tracker`'s per-bank parser
 * registry (which routes 200+ banks this way and uses no LLM in its SMS
 * pipeline at all). That project is AGPL-3.0; this file's routing shape is
 * learned, and where its patterns informed our engine the adaptation is
 * recorded in `docs/licenses/README.md`.
 */
interface BankParser {

    /** Display name, also written into [ParsedBankMessage.bankName]. */
    val bankName: String

    /**
     * True when this parser recognises [sender] as its bank.
     *
     * [sender] is the raw SMS address, already trimmed and upper-cased by
     * [BankParserRegistry]. Indian banks send from short IDs and from DLT
     * templates of the form `<CHANNEL>-<ID>-S`, so both must be matched.
     */
    fun canHandle(sender: String): Boolean

    /**
     * Extracts a transaction from [rawMessage], or returns a
     * [ParsedBankMessage] with null fields when the message carries no
     * completed transaction (a balance alert, an OTP, an offer).
     *
     * Implementations must not throw: an unparseable message is a normal
     * outcome, and the caller falls back to the global parser.
     */
    fun extract(rawMessage: String): ParsedBankMessage
}
