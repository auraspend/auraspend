package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a payment rail means, and what it does not.
 *
 * The owner's rule (2026-10-16): **a person-to-person UPI received is income, a
 * person-to-person UPI sent is expense** — the *category* of both is Transfer,
 * and the type follows the direction of the money, as it does everywhere else.
 *
 * That rule replaced a mapping that made the rail itself the category
 * (`upi`, `neft`, `imps`, `rtgs`, `transfer` → Transfer), which filed 66 of 155
 * rows in a closed tester's export under Transfer — 43 %, including payments to
 * shops whose real category was perfectly readable.
 *
 * Synthetic messages, written to the shapes the tester's export contained.
 */
class TransferAndInvestmentCategoryTest {

    private fun parsed(body: String) = BankMessageParser.parse(body)

    // ─── Person to person: category Transfer, type from direction ────────────

    @Test
    fun `a person-to-person UPI credit is income, filed as a transfer`() {
        val message = "Dear SBI UPI User, your A/c X1234 has been credited with Rs.500.00 " +
            "on 12Mar26 from GEETHA VARASSIAR K V. UPI ref 123456789012."
        val p = parsed(message)
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals("cat_transfer", p.categoryId)
    }

    @Test
    fun `a person-to-person UPI debit is an expense, filed as a transfer`() {
        val message = "Dear UPI user A/C X1234 debited by Rs.200.00 on 12Mar26 trf to " +
            "SURESH MENON Refno 123456789012. If not you, call your bank."
        val p = parsed(message)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("cat_transfer", p.categoryId)
    }

    @Test
    fun `a transfer to self stays a transfer`() {
        val message = "Rs.10,000.00 transferred to self A/c XX4321 on 12-10-2026. Ref 998877."
        val p = parsed(message)
        assertEquals("cat_transfer", p.categoryId)
    }

    // ─── A rail is not a category: merchants keep their own ──────────────────

    @Test
    fun `a UPI payment to a shop is not a transfer`() {
        val message = "Rs.450.00 debited from A/c X1234 on 12-10-2026 to SWIGGY UPI ref 456789. " +
            "Avl Bal Rs.1,200.00"
        val p = parsed(message)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("cat_food", p.categoryId)
    }

    @Test
    fun `a NEFT credit from a company is not a transfer`() {
        val message = "Rs.25,000.00 credited to A/c X1234 by NEFT on 12-10-2026 from " +
            "EXAMPLE TECHNOLOGIES PVT LTD. Ref NEFT123456."
        val p = parsed(message)
        assertEquals(TransactionType.INCOME, p.type)
        // A company is not a person, so this must not fall into Transfer; it is
        // uncategorised rather than wrong until a better signal appears.
        assertTrue("company NEFT must not be Transfer", p.categoryId != "cat_transfer")
    }

    // ─── Mutual-fund SIP debits get a real category, and no fake merchant ────

    @Test
    fun `a monthly SIP debit is an investment with no NAV merchant`() {
        val message = "Example Mutual Fund: NAV of Example Bluechip Fund is Rs.24.53, " +
            "units allotted 203.8 under folio 1234567. Rs.5,000.00 debited from A/c X1234 " +
            "towards SIP purchase on 12-10-2026."
        val p = parsed(message)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("cat_investment", p.categoryId)
        assertNull("a NAV clause must not become the merchant", p.merchant)
    }
}
