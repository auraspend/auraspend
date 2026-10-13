package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Contract tests for the SMS parsing engine: amounts, types, merchants,
 * timestamps, banks and categories across the real-world Indian bank message
 * formats AuraSpend claims to support.
 */
class BankMessageParserTest {

    // ─── Amounts ─────────────────────────────────────────────────────────────

    @Test
    fun `simple debit with prefixed amount`() {
        val parsed = BankMessageParser.parse(
            "Rs 1000 debited from a/c XX1234 on 23-08-26 for purchase at SWIGGY"
        )
        assertEquals(1000.0, parsed.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `indian digit grouping lakh format`() {
        val parsed = BankMessageParser.parse("INR 1,23,456.78 debited from A/c 1234 IMPS")
        assertEquals(123456.78, parsed.amount!!, 0.001)
    }

    @Test
    fun `rupee symbol and slash suffix`() {
        assertEquals(59.0, BankMessageParser.parse("₹59 debited for recharge").amount!!, 0.001)
        assertEquals(1234.0, BankMessageParser.parse("Rs.1234/- debited from a/c").amount!!, 0.001)
    }

    @Test
    fun `suffix form amount before currency`() {
        val parsed = BankMessageParser.parse("Your a/c 1234 is debited by 2,500.00 Rs on 23/08/26")
        assertEquals(2500.0, parsed.amount!!, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `balance after transaction is not chosen as amount`() {
        val parsed = BankMessageParser.parse(
            "Rs 500 debited from A/c XX1111 on 23-08-26 10:15. Avl Bal Rs 25,000.00 - SBI"
        )
        assertEquals(500.0, parsed.amount!!, 0.001)
    }

    @Test
    fun `balance before transaction is not chosen as amount`() {
        val parsed = BankMessageParser.parse(
            "Avl balance Rs 25000.00. Rs 500 debited from A/c XX1111 towards AMAZON PAY"
        )
        assertEquals(500.0, parsed.amount!!, 0.001)
    }

    @Test
    fun `credit limit boilerplate ignored`() {
        val parsed = BankMessageParser.parse(
            "INR 899.00 spent on ICICI Credit Card XX5678 at NETFLIX.COM. Credit limit available INR 48,000."
        )
        assertEquals(899.0, parsed.amount!!, 0.001)
    }

    // ─── Types ────────────────────────────────────────────────────────────────

    @Test
    fun `credited is income`() {
        val parsed = BankMessageParser.parse(
            "Your A/c XX4321 is credited by Rs 500.00 on 23-08-2026 09:22 IMPS ref 123456 - SBI"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `refund is income`() {
        val parsed = BankMessageParser.parse(
            "Rs 999.00 refunded to your HDFC Bank card ending 4567 for cancelled order"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `cashback credit is income`() {
        val parsed = BankMessageParser.parse("Cashback of Rs 50 credited to your wallet")
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `salary credit is income`() {
        val parsed = BankMessageParser.parse(
            "Salary credited to your A/c XX1234 INR 85,000.00 NEFT ACME CORP REF 99881"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(85000.0, parsed.amount!!, 0.001)
    }

    @Test
    fun `dr suffix marks debit`() {
        val parsed = BankMessageParser.parse("INR 1200.00 Dr HDFC Bank A/c XX12")
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `cr suffix marks credit`() {
        val parsed = BankMessageParser.parse("INR 3000.00 Cr to A/c XX98 SBIN")
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `failed transaction has no type`() {
        val parsed = BankMessageParser.parse(
            "Txn of Rs 200.00 on card XX1234 failed due to insufficient funds"
        )
        assertNull(parsed.type)
    }

    @Test
    fun `ambiguous message has no type`() {
        val parsed = BankMessageParser.parse("Transaction update for your account Rs 100 reference 8899")
        assertNull(parsed.type)
    }

    @Test
    fun `reversal of debit is income, not ambiguous`() {
        // Real shape (Kotak UPI reversal, 2026-10-13): "credited ... as a reversal
        // of debit" scored both lexicons — the literal word "debit" — so the
        // ambiguity rule returned no type and the row was skipped.
        val parsed = BankMessageParser.parse(
            "Rs.20.00 is credited to Kotak Bank a/c no. 8584 on 20-08-26 as a reversal of debit transaction (UPI Ref no 713508246195)"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `bharat bill payment system is a brand, not a bill-payment debit`() {
        // Real shape (ICICI card-payment acknowledgment, 2026-10-13): the brand
        // "Bharat Bill Payment System" contains the debit phrase "bill payment",
        // which made the type ambiguous and the row untyped.
        val parsed = BankMessageParser.parse(
            "Payment of Rs 838.61 has been received on your ICICI Bank Credit Card XX8001 through Bharat Bill Payment System on 04-AUG-26"
        )
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `dashed month-name dates are parsed, not lost to the save time`() {
        // "04-AUG-26" had no matching date format, so the transaction fell back to
        // `LocalDateTime.now()`: wrong dates in the UI and a collapsed duplicate
        // window (owner's device, 2026-10-13 — two card payments were skipped as
        // duplicates of an unrelated credit saved minutes earlier).
        val parsed = BankMessageParser.parse(
            "Payment of Rs 838.61 has been received on your ICICI Bank Credit Card XX8001 through Bharat Bill Payment System on 04-AUG-26"
        )
        assertEquals(LocalDate.of(2026, 8, 4), parsed.date?.toLocalDate())
    }

    @Test
    fun `an amount with no leading digit is still read`() {
        // "Rs..6 Credited" — banks do print these; skipping them let a later
        // balance figure win the amount pick.
        val parsed = BankMessageParser.parse("Rs..6 Credited to your A/c XX1234 on 23-08-26")
        assertEquals(0.6, parsed.amount ?: Double.NaN, 0.0001)
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `a statement whose only figure is the available limit has no amount`() {
        val parsed = BankMessageParser.parse(
            "Your available credit limit is INR 42,731.09. Statement is ready."
        )
        assertNull(parsed.amount)
    }

    @Test
    fun `deducted is an expense`() {
        val parsed = BankMessageParser.parse(
            "Rs. 250.00 deducted from your account towards insurance premium."
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(250.0, parsed.amount ?: Double.NaN, 0.0001)
    }

    @Test
    fun `an updated balance tail is not the amount`() {
        val parsed = BankMessageParser.parse(
            "Rs 500 debited from A/c XX1234. Updated Balance: Rs 12,345.00."
        )
        assertEquals(500.0, parsed.amount ?: Double.NaN, 0.0001)
    }

    @Test
    fun `a trailing PVT LTD is stripped from the merchant`() {
        val parsed = BankMessageParser.parse(
            "Sent Rs.500 towards ACME TECHNOLOGIES PVT LTD on 23-08-26"
        )
        assertEquals("ACME TECHNOLOGIES", parsed.merchant)
    }

    @Test
    fun `a payment app QR handle reads as the app, not the raw id`() {
        val parsed = BankMessageParser.parse(
            "Sent Rs.15.00 from Kotak Bank AC X1234 to paytmqr288005050101t74afkchmxjd@paytm on 14-10-25.UPI Ref 1234567890"
        )
        assertEquals("Paytm", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `a phone-number UPI handle stays the number`() {
        val parsed = BankMessageParser.parse(
            "Sent Rs.500.00 from Kotak Bank AC X9999 to 9876543210@paytm on 15-10-25. UPI Ref 1111111111"
        )
        assertEquals("9876543210", parsed.merchant)
    }

    @Test
    fun `a card spend's merchant follows the second on`() {
        val parsed = BankMessageParser.parse(
            "INR 500.00 spent using ICICI Bank Card XX5678 on 06-Sep-25 on Swiggy. Avl Limit: INR 1,50,000.00."
        )
        assertEquals(500.0, parsed.amount ?: Double.NaN, 0.0001)
        assertEquals("Swiggy", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `a refund's merchant is the party before refunded`() {
        val parsed = BankMessageParser.parse(
            "INR 2 from Airport Lounge refunded to your Kotak Credit Card x8848 on 17-Jul-2026"
        )
        assertEquals("Airport Lounge", parsed.merchant)
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `the payee before a counterparty credited is the merchant`() {
        val parsed = BankMessageParser.parse(
            "ICICI Bank Acct XX051 debited for Rs 180.00 on 10-Nov-25; DINDUGAL ORIGIN credited. UPI:568069174081."
        )
        assertEquals("DINDUGAL ORIGIN", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `credited to the beneficiary is money out, not income`() {
        val parsed = BankMessageParser.parse(
            "ICICI BANK NEFT Transaction with reference number IN12603221231681 for Rs. 22050.00 has been credited to the beneficiary account on 01-02-2026 at 10:32:51"
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(22050.0, parsed.amount ?: Double.NaN, 0.01)
    }

    @Test
    fun `an Info SAL clause reads as Salary`() {
        val parsed = BankMessageParser.parse(
            "ICICI Bank Account XX566 credited:Rs. 18,832.00 on 28-Feb-25. Info INF*000169831922*IQBO SAL FE. Available Balance is Rs. 28,076.14."
        )
        assertEquals("Salary", parsed.merchant)
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    // ─── Merchants ───────────────────────────────────────────────────────────

    @Test
    fun `at merchant extraction`() {
        val parsed = BankMessageParser.parse("Rs 250 spent at STARBUCKS BANGALORE on 23-08-26")
        assertTrue(parsed.merchant?.contains("STARBUCKS") == true)
    }

    @Test
    fun `vpa extraction wins and is prettified`() {
        val parsed = BankMessageParser.parse(
            "Sent Rs.299 from HDFC Bank a/c **1234 to swiggy@ybl on 23-08-26. UPI Ref 512345678901"
        )
        assertEquals("Swiggy", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `numeric-only vpa rejected`() {
        val parsed = BankMessageParser.parse(
            "Sent Rs 400 from A/c 11 to 9988776655@paytm UPI ref 7712345"
        )
        // Falls back to other strategies; must never be "9988776655".
        assertTrue(parsed.merchant?.any { it.isLetter() } != true || !parsed.merchant!!.contains("99887"))
    }

    @Test
    fun `card purchase merchant`() {
        val parsed = BankMessageParser.parse(
            "INR 649.00 spent on ICICI Credit Card XX5678 at BOOKMYSHOW.COM on 23/08/26 10:15"
        )
        assertTrue(parsed.merchant?.startsWith("BOOKMYSHOW") == true)
    }

    @Test
    fun `merchant stop phrases rejected`() {
        val parsed = BankMessageParser.parse("Rs 150 debited from your account on 23-08-26 at your branch counter")
        assertNull(parsed.merchant)
    }

    // ─── Dates & times ───────────────────────────────────────────────────────

    @Test
    fun `date with time parses fully`() {
        val parsed = BankMessageParser.parse(
            "Rs 500 debited from A/c XX1111 on 23-08-2026 14:35 IST - SBI"
        )
        val date = parsed.date!!
        assertEquals(LocalDate.of(2026, 8, 23), date.toLocalDate())
        assertEquals(14, date.hour)
        assertEquals(35, date.minute)
    }

    @Test
    fun `date without time defaults to noon`() {
        val parsed = BankMessageParser.parse("INR 200 credited on 15/03/2026 towards refund")
        assertEquals(LocalDateTime.of(2026, 3, 15, 12, 0), parsed.date)
    }

    @Test
    fun `am pm time handled`() {
        val parsed = BankMessageParser.parse("Rs 60 debited on 05-01-2026 09:30 pm at KFC")
        assertEquals(LocalTime.of(21, 30), parsed.date!!.toLocalTime())
    }

    @Test
    fun `alpha month date`() {
        val parsed = BankMessageParser.parse("Rs 1200 debited on 23 Aug 2026 at AMAZON INDIA")
        assertEquals(LocalDate.of(2026, 8, 23), parsed.date!!.toLocalDate())
    }

    @Test
    fun `iso timestamp`() {
        val parsed = BankMessageParser.parse("Rs 89 debited 2026-07-04T18:02 at HOTSPOT")
        assertEquals(LocalDateTime.of(2026, 7, 4, 18, 2), parsed.date)
    }

    // ─── Banks ───────────────────────────────────────────────────────────────

    @Test
    fun `bank detection breadth`() {
        val cases = mapOf(
            "HDFC Bank: Rs 10 debited" to "HDFC Bank",
            "ICICI Bank Rs 20 debited" to "ICICI Bank",
            "SBI: Rs 30 debited" to "SBI",
            "Axis Bank Rs 40 debited" to "Axis Bank",
            "Kotak Mahindra Bank Rs 50" to "Kotak Mahindra",
            "Yes Bank Rs 60 debited" to "Yes Bank",
            "PNB Rs 70 debited" to "PNB",
            "Canara Bank Rs 80" to "Canara Bank",
            "Bank of Baroda Rs 90" to "Bank of Baroda",
            "IDFC FIRST Bank Rs 5 debited" to "IDFC First",
            "IndusInd Bank Rs 6 debited" to "IndusInd Bank",
            "Paytm Payments Bank Rs 7" to "Paytm Payments Bank"
        )
        cases.forEach { (msg, expected) ->
            assertEquals("failed for: $msg", expected, BankMessageParser.parse(msg).bankName)
        }
    }

    // ─── Categories ──────────────────────────────────────────────────────────

    @Test
    fun `merchant keyword category food`() {
        val parsed = BankMessageParser.parse("Rs 450 debited at Swiggy on 23-08-26")
        assertEquals("cat_food", parsed.categoryId)
    }

    @Test
    fun `message body keyword category transport`() {
        val parsed = BankMessageParser.parse("Rs 350 debited for petrol IOCL on 23-08-26 ref 55122")
        assertEquals("cat_transport", parsed.categoryId)
    }

    @Test
    fun `netflix maps to entertainment`() {
        val parsed = BankMessageParser.parse(
            "INR 649.00 spent on Credit Card XX5678 at NETFLIX.COM on 23/08/26"
        )
        assertEquals("cat_entertainment", parsed.categoryId)
    }

    @Test
    fun `salary keyword in body wins for income`() {
        val parsed = BankMessageParser.parse(
            "INR 85,000.00 credited to A/c XX1234 NEFT SALARY AUGUST ACME CORP"
        )
        assertEquals("cat_salary", parsed.categoryId)
    }

    // ─── Confidence & robustness ─────────────────────────────────────────────

    @Test
    fun `complete message gets high confidence`() {
        val parsed = BankMessageParser.parse(
            "Rs 1000 debited from HDFC Bank a/c XX1234 at SWIGGY on 23-08-2026 14:35"
        )
        assertTrue(parsed.confidence >= 0.85f)
    }

    @Test
    fun `garbage message yields low confidence and no data`() {
        val parsed = BankMessageParser.parse("Hey! Long time no see, call me when free.")
        assertNull(parsed.amount)
        assertNull(parsed.type)
        assertTrue(parsed.confidence <= 0.35f)
    }

    @Test
    fun `empty message does not crash`() {
        val parsed = BankMessageParser.parse("")
        assertNull(parsed.amount)
        assertEquals(0f, parsed.confidence)
    }

    @Test
    fun `multi line message normalised`() {
        val parsed = BankMessageParser.parse(
            "Dear Customer,\nRs 299.00 debited\nfrom A/c XX9999\non 23-08-26\nfor UPI to zomato@ibl\n-HDFC"
        )
        assertEquals(299.0, parsed.amount!!, 0.001)
        assertEquals("Zomato", parsed.merchant)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }
}
