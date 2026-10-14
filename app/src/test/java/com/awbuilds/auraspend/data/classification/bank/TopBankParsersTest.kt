package com.awbuilds.auraspend.data.classification.bank

import com.awbuilds.auraspend.data.classification.TransactionClassifier
import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sender-routed coverage for the three largest banks this app meets in the
 * field: HDFC Bank, ICICI Bank and Kotak Mahindra Bank.
 *
 * Each parser layers its corrections on the global engine, so these tests pin
 * two things at once: the routing (sender → bank name, including short
 * messages that never name the bank in the body) and the bank-specific
 * corrections the global parse does not make.
 */
class TopBankParsersTest {

    // ── routing ────────────────────────────────────────────────────────────────

    @Test
    fun `sender ids route to their bank`() {
        assertEquals("HDFC Bank", BankParserRegistry.parserFor("CP-HDFCBK-S")?.bankName)
        assertEquals("HDFC Bank", BankParserRegistry.parserFor("VM-HDFCBK-S")?.bankName)
        assertEquals("HDFC Bank", BankParserRegistry.parserFor("HDFCBANK")?.bankName)
        assertEquals("ICICI Bank", BankParserRegistry.parserFor("JM-ICICIT-S")?.bankName)
        assertEquals("ICICI Bank", BankParserRegistry.parserFor("AX-ICICIT-S")?.bankName)
        assertEquals("ICICI Bank", BankParserRegistry.parserFor("ICICIB")?.bankName)
        assertEquals("Kotak Mahindra", BankParserRegistry.parserFor("JD-KOTAKB-S")?.bankName)
        assertEquals("Kotak Mahindra", BankParserRegistry.parserFor("VM-KOTAKD-S")?.bankName)
        assertEquals("Kotak Mahindra", BankParserRegistry.parserFor("Kotak Mahindra Bank")?.bankName)
    }

    @Test
    fun `an HDFC mutual-fund sender routes to the mutual-fund parser`() {
        // MF messages are unit allotments; the bank parser's assumptions do
        // not hold for them — the dedicated parser claims the MF senders.
        assertEquals("HDFC Mutual Fund", BankParserRegistry.parserFor("AD-HDFCMF-S")?.bankName)
        assertEquals("HDFC Bank", BankParserRegistry.parserFor("VM-HDFCBK-S")?.bankName)
    }

    // ── HDFC ───────────────────────────────────────────────────────────────────

    @Test
    fun `hdfc short debit gets its bank name from the sender`() {
        val parsed = TransactionClassifier.classify(
            "Rs.500.00 debited from A/c XX1234 on 20-Oct-25 to swiggy@ybl (UPI Ref No 123456789012)",
            "CP-HDFCBK-S"
        )
        assertEquals(500.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("HDFC Bank", parsed.bankName)
        assertEquals("Swiggy", parsed.merchant)
    }

    @Test
    fun `hdfc reversal names the merchant after By`() {
        val parsed = TransactionClassifier.classify(
            "Transaction Reversed!On HDFC Bank CREDIT Card xx5555 Amt: Rs.862.16 By AMAZON                000 On 2026-08-21:05:20:26",
            "VM-HDFCBK-S"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("AMAZON", parsed.merchant)
        assertEquals("HDFC Bank", parsed.bankName)
    }

    // ── ICICI ──────────────────────────────────────────────────────────────────

    @Test
    fun `icici shared-network ATM withdrawal reads as Cash Withdrawal`() {
        val parsed = TransactionClassifier.classify(
            "ICICI Bank Acc XX921 debited Rs. 10,000.00 on 20-Jan-26 NFSCASH WDL. Avb Bal Rs. 3,943.84.",
            "JD-ICICIT-S"
        )
        assertEquals(10000.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Cash Withdrawal", parsed.merchant)
        assertEquals("ICICI Bank", parsed.bankName)
    }

    @Test
    fun `icici own ATM marker reads as ATM Withdrawal`() {
        val parsed = TransactionClassifier.classify(
            "ICICI Bank Acc XX611 debited Rs. 6,500.00 on 19-Jun-26 CAM*62712SRY*. Avb Bal Rs. 10,079.44.",
            "VM-ICICIT-S"
        )
        assertEquals("ATM Withdrawal", parsed.merchant)
    }

    @Test
    fun `icici biller comes from the InfoBIL clause`() {
        val parsed = TransactionClassifier.classify(
            "ICICI Bank Acc XX342 debited Rs. 8,927.00 on 07-Jun-26 InfoBIL*Auto Loan.Avl Bal Rs. 1,248.78.",
            "JD-ICICIT-S"
        )
        assertEquals("Auto Loan", parsed.merchant)
        assertEquals(8927.0, parsed.amount ?: Double.NaN, 0.001)
    }

    @Test
    fun `icici autopay debit names the subscription`() {
        val parsed = TransactionClassifier.classify(
            "Your account has been debited with Rs 649.00 towards Netflix Entertainment Ser for AutoPay MERCHANTMANDATE. RRN 421723106963. Avl Bal Rs 10,000.00",
            "AX-ICICIT-S"
        )
        assertEquals(649.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Netflix Entertainment Ser", parsed.merchant)
        assertEquals("ICICI Bank", parsed.bankName)
    }

    // ── Kotak ──────────────────────────────────────────────────────────────────

    @Test
    fun `kotak short UPI send gets bank and payee without the brand in the body`() {
        val parsed = TransactionClassifier.classify(
            "Sent Rs.51.00 from XXXXXX9722 to RAJESH KUMAR on 30/04/2026. UPI ref no. 651234567890. Not you? Tap https://kotk.in/KOTAKD/XXXXXX to report -Kotak",
            "VM-KOTAKD-S"
        )
        assertEquals(51.0, parsed.amount ?: Double.NaN, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("RAJESH KUMAR", parsed.merchant)
        assertEquals("Kotak Mahindra", parsed.bankName)
    }

    @Test
    fun `kotak IMPS credit from a masked mobile uses the mask as the counterparty`() {
        val parsed = TransactionClassifier.classify(
            "Received Rs. 329.00 on 15-04-26 in your Kotak Bank A/C x2451 by an A/C linked to mobile x111. IMPS Ref no 620000000034.",
            "JD-KOTAKB-S"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("x111", parsed.merchant)
        assertEquals("Kotak Mahindra", parsed.bankName)
    }
}
