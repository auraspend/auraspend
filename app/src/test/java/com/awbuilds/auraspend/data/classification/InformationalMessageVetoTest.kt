package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Informational messages that are **not transactions**, pinned.
 *
 * Measured on a closed tester's real export (155 rows, 2026-10-16): roughly a
 * third of it was not money at all — reward-points updates, mutual-fund NAV
 * notices, loyalty-expiry reminders and card marketing — and each one had been
 * booked as income or expense. The gate's existing promo/alert words caught
 * none of them, because every one of those messages also carries an amount and
 * a word the movement lexicon recognises ("…points have been credited" is not
 * money arriving; "NAV of a fund is Rs 24.53" is a price, not a payment).
 *
 * Two rules govern what belongs here, and both follow from AGENTS.md:
 *
 * - A phrase that **is** the money statement ("Rs 500 loyalty points have been
 *   credited", "Apply without income proof for a Pre-Qualified Credit Card") may
 *   veto unconditionally: no genuine debit says that.
 * - A phrase that merely *mentions* an informational subject (NAV, an account
 *   update, an expiry date) vetoes only when the message has no completed
 *   movement — the same amount+verb rescue the OTP branch uses — so a real SIP
 *   purchase that quotes a NAV is still saved.
 *
 * Every message here is synthetic, written to the same shape as the real ones.
 */
class InformationalMessageVetoTest {

    // ─── Reward / loyalty points: points are not rupees ──────────────────────

    @Test
    fun `reward points credited is not income`() {
        assertTrue(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "An update has been made to your myRewards Loyalty account 1234567. " +
                    "Rs.150 loyalty points have been credited. To know more, visit the app."
            )
        )
    }

    @Test
    fun `reward points earning notice is not income`() {
        assertTrue(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "You have earned 250 reward points on your card ending 4321. " +
                    "Redeem them at the rewards store."
            )
        )
    }

    @Test
    fun `points expiry reminder is not income`() {
        assertTrue(
            SmsAutoClassifier.isOtpOrAlertMessage(
                "500 reward points are expiring on 31-Oct-2026. " +
                    "Use them at the rewards store before they expire."
            )
        )
    }

    // ─── Mutual-fund NAV notices: a price is not a payment ───────────────────

    @Test
    fun `nav update is not an expense`() {
        assertTrue(
            SmsAutoFilterProbe.vetoed(
                "Mutual Fund: NAV of Example Bluechip Fund is Rs.24.53 as on 12-Oct-2026. " +
                    "To know more, call us."
            )
        )
    }

    /**
     * The shape that actually shipped a phantom in the field: a fund notice that
     * also names units and a folio, so a movement verb from the fund vocabulary
     * ("allotted", "processed") can carry it past the amount+verb rescue while
     * the amount is a NAV, not money.
     */
    @Test
    fun `nav notice with units and folio is not a transaction`() {
        assertTrue(
            SmsAutoFilterProbe.vetoed(
                "Example Mutual Fund: NAV of Example Bluechip Fund is Rs.24.53, " +
                    "units allotted 203.8 under folio 1234567 as on 12-Oct-2026."
            )
        )
    }

    // ─── Card and loan marketing ─────────────────────────────────────────────

    @Test
    fun `pre-qualified card offer is not income`() {
        assertTrue(
            SmsAutoFilterProbe.vetoed(
                "Dear Customer, Apply without income proof for a Pre-Qualified Example Bank " +
                    "Credit Card and get Rs.500* as a joining gift. Apply now."
            )
        )
    }

    @Test
    fun `exclusive offer is not income`() {
        assertTrue(
            SmsAutoFilterProbe.vetoed(
                "Special OFFER! Get Rs.1000* with your Example Bank Credit Card. " +
                    "Exclusive offer ends 30-Oct."
            )
        )
    }

    /**
     * The exact trap that let marketing through: the offer quotes a **credit**
     * word ("cashback") and an amount, so the amount+verb rescue fires and the
     * message is kept as income. A strong marketing signature has to outrank the
     * rescue, because no genuine debit is a pre-qualified offer.
     */
    @Test
    fun `an offer quoting cashback and an amount is still not a transaction`() {
        assertTrue(
            SmsAutoFilterProbe.vetoed(
                "Special OFFER! Get Rs.1000* cashback on your new Example Bank Credit Card. " +
                    "Apply now, offer valid till 30-Oct."
            )
        )
    }

    @Test
    fun `a loyalty points expiry reminder is not income`() {
        assertTrue(
            SmsAutoFilterProbe.vetoed(
                "Use your 500 points at the rewards store before they expire on 31-Oct-2026."
            )
        )
    }

    // ─── The rescue must survive: real money that merely mentions these ─────

    @Test
    fun `a real sip purchase quoting a nav is still a transaction`() {
        assertFalse(
            SmsAutoFilterProbe.vetoed(
                "Rs.5,000 debited from A/c XX1234 towards SIP purchase, folio 1234567, " +
                    "NAV Rs.24.53, units allotted 203.8."
            )
        )
    }

    @Test
    fun `a real spend that earns points is still a transaction`() {
        assertFalse(
            SmsAutoFilterProbe.vetoed(
                "Rs.1,499.00 spent on your card ending 4321 at EXAMPLE STORE on 12-10-2026. " +
                    "You have earned 15 reward points."
            )
        )
    }

    // ─── Promotional families (export, 2026-10-21) ───────────────────────────
    //
    // These assert the **gate**, not the composite probe below. That matters
    // for the first one: an advertisement has no movement verb, so the probe
    // would call it "not a transaction" even with no gate at all. What saved
    // the real one was `SmsAiEnricher`, which lets the on-device model or the
    // learned classification memory fill a missing type — both *after* this
    // gate. The gate is the only place that can stop them, so the gate is what
    // is tested.
    //
    // Every message here is synthetic, written to the shape of the measured
    // ones. No real sender, body, link or amount is reproduced.

    private fun gate(message: String) = SmsAutoClassifier.isOtpOrAlertMessage(message)

    @Test
    fun `an advertisement that quotes a price is stopped before a model can type it`() {
        assertTrue(
            gate(
                "DONT MISS tonight's match! Watch it LIVE on ExamplePlay+ with your " +
                    "ExampleTelco prepaid! Recharge Rs.501 = 1 year ExamplePlay+ mobile " +
                    "subscription + 3GB/day + UL calls, 28 days. Click bit.ly/ExamplePlan"
            )
        )
    }

    @Test
    fun `a cashback invitation is not income`() {
        assertTrue(
            gate("Get Rs.400 cashback on your next mobile recharge. Offer valid till 30-Oct.")
        )
    }

    @Test
    fun `a cashback that has been credited is still income`() {
        // The one direction this family must never break.
        assertFalse(gate("Rs.150 cashback credited to your account"))
        assertFalse(gate("Rs.75 cashback has been credited for your recent transaction"))
        assertFalse(
            gate("Cashback of Rs.100.00 has been credited to your Example Bank Credit Card x8848.")
        )
    }

    @Test
    fun `a coupon credited to an app is not income`() {
        assertTrue(
            gate(
                "Congratulations! Rs.60 cashback coupon credited to your ExampleTelco app. " +
                    "Redeem within 95 days."
            )
        )
    }

    @Test
    fun `a welcome bonus that can be credited is not income`() {
        assertTrue(
            gate(
                "Surprised? Rs.8,850 welcome bonus can be credited to your Example Rewards " +
                    "account. Register now."
            )
        )
    }

    @Test
    fun `a prize pool is not a balance`() {
        assertTrue(
            gate(
                "Dear User, Rs.2,000 free welcome bonus can be credited. " +
                    "Prize Pool - Rs.65,00,000. Create a free account at example.in"
            )
        )
    }

    @Test
    fun `an instruction to spend is not an expense`() {
        assertTrue(
            gate(
                "Dear Customer, Shop for more than Rs.2,000 this week and earn 100 bonus " +
                    "points with your Example Bank Debit Card."
            )
        )
    }

    @Test
    fun `a flat discount is not an expense`() {
        assertTrue(
            gate("Get Flat Rs.333 Off on a minimum spend of Rs.1,499 at Example Store.")
        )
    }

    @Test
    fun `a recharge confirmation is not income`() {
        assertTrue(
            gate(
                "Recharge successful! Pack credited: 1GB data for 1 day. MRP Rs.16. " +
                    "Dial *111# for details."
            )
        )
    }

    @Test
    fun `a wallet credit that wants a click is not a statement`() {
        assertTrue(
            gate("Received Rs.13,500 in your Wallet A/c. Click to check now example.in/wallet")
        )
    }

    // ─── The families must not touch real money ─────────────────────────────

    @Test
    fun `a real credit carrying a link is still a transaction`() {
        assertFalse(
            gate("Rs.5,000.00 credited to your account on 12-10-2026. View at example.bank.in")
        )
    }

    @Test
    fun `a real debit for a recharge is still a transaction`() {
        assertFalse(
            gate("Rs.199.00 debited from A/c XX1234 for your mobile recharge. Plan activated.")
        )
    }

    @Test
    fun `a real spend under a merchant promotion is still a transaction`() {
        assertFalse(
            SmsAutoFilterProbe.vetoed(
                "Rs.800.00 debited from A/c XX1234 to EXAMPLE MERCHANT on 12-10-2026. " +
                    "Offer valid until stocks last."
            )
        )
    }
}

/**
 * Small helper so these cases read the way the pipeline does: a message is
 * dropped when the ingest gate vetoes it **or** the classifier finds no
 * plausible movement to save. Kept separate from the gate itself so the two
 * conditions stay independently testable.
 */
private object SmsAutoFilterProbe {
    fun vetoed(message: String): Boolean =
        SmsAutoClassifier.isOtpOrAlertMessage(message) || !SmsAutoClassifier.looksLikeTransaction(message)
}
