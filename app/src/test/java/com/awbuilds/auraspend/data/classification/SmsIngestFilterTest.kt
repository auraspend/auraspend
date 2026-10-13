package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.ui.classification.SmsInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ingest-time noise filter.
 *
 * OTP / alert / promo messages were already skipped by the *processor*, but they were still
 * queued first: they inflated the progress denominator, occupied the SMS tab's message list, and
 * burned a pipeline slot each. Filtering them at ingest means the queue holds only candidates.
 *
 * The predicate is the processor's own `SmsAutoClassifier.isOtpOrAlertMessage`, negated — one
 * source of truth, including its amount+movement-verb rescue. These tests exist so the two can
 * never drift apart again.
 */
class SmsIngestFilterTest {

    private fun sms(body: String) = SmsInfo(id = "1", address = "BANK", body = body, timestamp = 0L)

    @Test
    fun `an OTP is not ingestable`() {
        assertFalse(isIngestable(sms("Your OTP is 482913")))
        assertFalse(isIngestable(sms("One time password 445122 to login")))
    }

    @Test
    fun `a balance alert is not ingestable`() {
        assertFalse(isIngestable(sms("Your available balance is Rs. 5000 as on 15/08/2026")))
    }

    @Test
    fun `a promo is not ingestable`() {
        assertFalse(isIngestable(sms("Flat 10% off on Swiggy orders this weekend. Use coupon FEST10.")))
    }

    @Test
    fun `a phishing lure is not ingestable`() {
        // Parses identically to a genuine debit, so the hard veto is the only thing separating
        // it from one — and there is deliberately no rescue for it.
        assertFalse(
            isIngestable(
                sms("Rs.10,000 has been debited? If not done by you, dial 1930 to report cyber fraud")
            )
        )
    }

    @Test
    fun `a debit that ends in an OTP is still ingestable`() {
        // The rescue the AGENTS.md OTP invariant demands: dropping this loses real money.
        assertTrue(
            isIngestable(
                sms("INR 149.00 debited at SWIGGY. Your OTP for this transaction is 445122.")
            )
        )
    }

    @Test
    fun `an ordinary debit is ingestable`() {
        assertTrue(
            isIngestable(
                sms("INR 250.00 debited from A/c **1234 at swiggy on 15/08/2026. Not you? Call 1800.")
            )
        )
    }

    @Test
    fun `a credit is ingestable`() {
        assertTrue(
            isIngestable(sms("INR 12500.00 credited to A/c **1234 by SALARY on 01/09/2026"))
        )
    }

    @Test
    fun `a genuine debit with the bank's alarm tail is still ingestable`() {
        // ICICI appends "If not you, call ... / SMS BLOCK ..." to every genuine
        // card alert. The hard veto deleted three real spends on the owner's
        // device (2026-10-13) — they appeared nowhere, not even in the
        // unrecognised list.
        assertTrue(
            isIngestable(
                sms("INR 994.09 spent using ICICI Bank Card XX8001 on 12-Sep-26 on SWIGGY. Avl Limit: INR 40,140.08. If not you, call 1800 2662/SMS BLOCK 5003 to 9000000042.")
            )
        )
    }

    @Test
    fun `the alarm-tail exception does not rescue phishing framing`() {
        // The same alarm shape, but the framing is the phishing one — still vetoed.
        assertFalse(
            isIngestable(
                sms("INR 4,999.00 spent using your card. If not done by you, call 1800 2662/SMS BLOCK 5003 to 9000000042.")
            )
        )
    }

    @Test
    fun `an unsuccessful payment attempt is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("Your last payment attempt of Rs. 49.00 on Jul 20, 2026 03:50:47 PM for Jio number 9000000042 was unsuccessful. Please try again.")
            )
        )
    }

    @Test
    fun `a refund that is only initiated is not ingestable`() {
        assertFalse(
            isIngestable(sms("Your refund for PNR- 4123456789 of Rs. 560.00 has been initiated by Confirmtkt."))
        )
    }

    @Test
    fun `an advance debit intimation is not ingestable`() {
        // The completed debit arrives as its own SMS ("has been debited");
        // booking the announcement doubles the spend.
        assertFalse(
            isIngestable(sms("Rs.500 will be debited from your A/c XX1234 on 25-10-26."))
        )
    }

    @Test
    fun `a payment request is not ingestable`() {
        assertFalse(
            isIngestable(sms("ZOMATO has requested payment of Rs 450 for your order."))
        )
    }

    @Test
    fun `a convert-to-EMI invitation is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("Your HDFC Bank Credit Card bill of Rs 2,847.92 can be converted into EMI easily. SMS EMI to 5676712.")
            )
        )
    }

    @Test
    fun `a completed EMI conversion is still ingestable`() {
        // The invitation is vetoed; the actual debit is not.
        assertTrue(
            isIngestable(
                sms("Your bill was converted into EMI. Rs.3282 debited from your card for the EMI.")
            )
        )
    }

    @Test
    fun `please pay reminders are not ingestable`() {
        assertFalse(
            isIngestable(sms("Pls pay your bill of Rs 5,000 by 25-10-26 to avoid charges."))
        )
    }

    @Test
    fun `a mandate processed-later notice is not ingestable`() {
        // "received today for processing" is an acknowledgement, not a credit:
        // without the guard, "received" read as a credit verb and the whole
        // mandate amount became phantom income.
        assertFalse(
            isIngestable(
                sms("Auto Pay HDFC Bank NACH Mandate : Rs. 100000.00 UMRN:HDFC7031703262015557 To:NationalSecuritiesClearin Freq ADHO received today for processing.")
            )
        )
    }

    @Test
    fun `a genuine debit with PNB's report tail stays ingestable`() {
        // PNB appends "forward this SMS … to report unauthorized txn" to real
        // debits — the same alibi-tail failure the ICICI exception covers.
        assertTrue(
            isIngestable(
                sms("Your a/c no XX1234 is debited for Rs 1000 on 01-01-25 12:00:00 and a/c XX456 credited (IMPS Ref no 123456789012). If not done by you, pl. forward this SMS from registered mobile to 1800123456 to report unauthorized txn & block IBS/MBS.")
            )
        )
    }

    @Test
    fun `a deposit-interest notice is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("Net interest INR 248.07 paid on your IndusInd Deposit No 300***123456 on 17/09/25. Call 18602677777 for assistance - IndusInd Bank")
            )
        )
    }

    @Test
    fun `a UPI-mandate creation notice is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("Your UPI-Mandate is successfully created towards Google for Rs.1500.00 from A/c No.XXXXXX4356. UMN:1d478c77808c410281f435rer5qwerty6@ybl-PNB")
            )
        )
    }

    @Test
    fun `a bill due-for-payment notice is not ingestable`() {
        // Measured on the owner's inbox (2026-10-13): every monthly bill notice
        // booked as an EXPENSE — the amount owed is not a payment.
        assertFalse(
            isIngestable(
                sms("Dear Customer, Bill dated 01-Aug-26 for your JioHome connection with JioFixedVoice Number +919000000000 is due for payment today. Total amount payable is Rs. 749.50. Please pay immediately. Kindly ignore, if already paid.")
            )
        )
    }

    @Test
    fun `a card statement notice is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("ICICI Bank Credit Card XX8000 Statement is sent to a*****h@example.com. Total of Rs 735.50 or minimum of Rs 73.55 is due by 20-AUG-26.")
            )
        )
    }

    @Test
    fun `an e-statement mailed notice is not ingestable`() {
        // "received" in the mail boilerplate booked these as phantom INCOME.
        assertFalse(
            isIngestable(
                sms("E-statement of SBI Credit Card ending XX79 dated 10/02/2026 has been mailed. If not received, SMS ENRS to 5676766. Total Amt Due Rs 2075; Min Amt Due Rs 208; Payable by 25/02/2026")
            )
        )
    }

    @Test
    fun `a future refund notice is not ingestable`() {
        assertFalse(
            isIngestable(sms("PNR 4123456789 ticket cancelled. Amt 645.00 will be refunded within 3-4 days. Please check your account/instrument from which this tkt was booked.-IRCTC"))
        )
    }

    @Test
    fun `an e-bill notice is not ingestable`() {
        // The e-bill wording carries no "due for payment", and the
        // classification memory once resurrected the type from a wrongly-saved
        // instance — so this veto must sit upstream of the memory.
        assertFalse(
            isIngestable(
                sms("Dear Customer, The 01-Feb-2026 ebill for your JioHome connection with JioFixedVoice Number +919000000000 has been sent to a*****h@example.com. Bill Summary: Bill period: 01-Jan-2026 to 01-Feb-2026 Total Amount payable : Rs. 749.50 Payment due date: 15-FEB-2026 To view and download your bill, click http://jio.com/my_bills Team JioHome")
            )
        )
    }

    @Test
    fun `a biller receipt of the user's own payment stays ingestable`() {
        // It is a real debit (the direction is fixed in the parser); only the
        // bill NOTICES are dropped.
        assertTrue(
            isIngestable(
                sms("Payment of Rs. 749.50 for your JioHome connection with JioFixedVoice Number +919000000000 through UPI Payments has been received on 01-Aug-26. Thank You!")
            )
        )
    }

    // ── Credit-card bill settlements: both sides excluded ────────────────────

    @Test
    fun `a card-side bill-payment receipt is not ingestable`() {
        // The spending was already counted when the card was swiped; the
        // payment itself is a settlement between the user's own pockets.
        assertFalse(
            isIngestable(
                sms("Payment of Rs 735.50 has been received on your ICICI Bank Credit Card XX8001 through Bharat Bill Payment System on 04-AUG-26.")
            )
        )
    }

    @Test
    fun `an SBI-style credited payment on the card is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("We have received payment of Rs.815.75 via BBPS & the same has been credited to your SBI Credit Card. Your available limit is Rs. 42,981.46")
            )
        )
    }

    @Test
    fun `a kotak-style credited payment on the card is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("Payment of INR 735.50 is credited to your Kotak Bank Credit Card x8848 on 17-Sep-26. Available Credit limit is INR 20,000.00.")
            )
        )
    }

    @Test
    fun `a received-as-payment card notice is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("Thank you Rs.668.79/- has been received as payment towards your PNB credit card XX1234 via Online Payment.")
            )
        )
    }

    @Test
    fun `a bank-side debit towards the card is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("Rs.270.00 debited from A/c XX1111 on 05-10-2026 towards PNB credit card payment. Avl Bal Rs.5,000.00")
            )
        )
    }

    @Test
    fun `a bank-side debit for the card bill is not ingestable`() {
        assertFalse(
            isIngestable(
                sms("Rs 10,000.00 debited from A/c XX0011 for credit card bill payment. Avl Bal Rs. 25,000.00")
            )
        )
    }

    @Test
    fun `a card spend is still ingestable`() {
        // Swipes are the actual spending; only the settlement is excluded.
        assertTrue(
            isIngestable(
                sms("INR 500.00 spent using ICICI Bank Card XX8001 on 06-Sep-25 on Swiggy. Avl Limit: INR 1,50,000.00.")
            )
        )
    }

    @Test
    fun `a refund to the card is still ingestable`() {
        // Money coming BACK to the card is not a settlement.
        assertTrue(
            isIngestable(
                sms("Dear Customer, refund of INR 250 from Swiggy Limited has been credited to your IndusInd Bank Credit Card XX1234 on 11-Jun-26.")
            )
        )
    }

    @Test
    fun `a cashback on the card is still ingestable`() {
        assertTrue(
            isIngestable(
                sms("Cashback of Rs.100.00 has been credited to your Kotak Bank Credit Card x8848 on 17-Jul-2026.")
            )
        )
    }
}
