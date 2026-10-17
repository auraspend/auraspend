package com.awbuilds.auraspend.data.classification

/**
 * Deterministic merchant → category lookup, used to fill category gaps.
 *
 * The model is not the right tool for this. The golden corpus shows it: the
 * shipped Qwen2.5-0.5B reaches 36.9 % category accuracy *through the regex
 * layer alone* and its own output was well-formed but wrong — returning
 * amounts, card numbers and bank names as merchants. A merchant name like
 * "Swiggy" or "BigBasket" is a closed, enumerable vocabulary problem, and
 * solving it with a word-bounded keyword scan is both more accurate and
 * auditable.
 *
 * Word boundaries are load-bearing, not decoration. `CLAUDE.md` records that
 * substring-unsafe keys ("fee", "credit", "vi") caused real misfiling, so
 * every key here is matched as a whole word or phrase. Longest key wins, so
 * "amazon prime" beats "amazon".
 *
 * This map only ever *fills a gap*. It must never override a category the
 * parser or the user already resolved.
 */
object CategoryKeywordMap {

    /**
     * Expense-side keywords. Keys are lowercase, matched as whole words or
     * whitespace-separated phrases.
     *
     * Resolution is longest-key-wins ([resolve]), **not** insertion order, so a
     * key listed twice silently resolves to whichever value was written last.
     * That is how `"mart"` came to mean Shopping: it was declared here as grocery
     * ("Reliance Mart", "Vijay Mart") and declared again under Shopping, and the
     * second declaration won without either entry looking wrong on its own.
     * `KeywordMapGuardTest` now fails the build on a duplicate key.
     */
    private val EXPENSE: Map<String, String> = linkedMapOf(
        // Food & Dining
        "swiggy" to "cat_food",
        "zomato" to "cat_food",
        "mcdonalds" to "cat_food",
        "mcdonald's" to "cat_food",
        "dominos" to "cat_food",
        "starbucks" to "cat_food",
        "cafe" to "cat_food",
        "coffee" to "cat_food",
        "restaurant" to "cat_food",
        "bakery" to "cat_food",
        "pizza" to "cat_food",
        "burger" to "cat_food",
        "foodpanda" to "cat_food",
        "eatsure" to "cat_food",
        // Groceries. "mart" is a whole-word match, so "Walmart" is untouched.
        "bigbasket" to "cat_grocery",
        "dmart" to "cat_grocery",
        "reliance fresh" to "cat_grocery",
        "blinkit" to "cat_grocery",
        "zepto" to "cat_grocery",
        "grocer" to "cat_grocery",
        "supermarket" to "cat_grocery",
        "mart" to "cat_grocery",
        // Shopping
        "amazon" to "cat_shopping",
        "flipkart" to "cat_shopping",
        "myntra" to "cat_shopping",
        "ajio" to "cat_shopping",
        "meesho" to "cat_shopping",
        "nykaa" to "cat_shopping",
        "ikea" to "cat_shopping",
        "decathlon" to "cat_shopping",
        "shop" to "cat_shopping",
        "store" to "cat_shopping",
        // Transport
        "uber" to "cat_transport",
        "ola" to "cat_transport",
        "rapido" to "cat_transport",
        "petrol" to "cat_transport",
        "pump" to "cat_transport",
        "indian oil" to "cat_transport",
        "indianoil" to "cat_transport",
        "bharat petroleum" to "cat_transport",
        "hpcl" to "cat_transport",
        "iocl" to "cat_transport",
        "metro" to "cat_transport",
        "irctc" to "cat_transport",
        "makemytrip" to "cat_transport",
        "redbus" to "cat_transport",
        // Subscriptions
        "netflix" to "cat_subscription",
        "spotify" to "cat_subscription",
        "amazon prime" to "cat_subscription",
        "hotstar" to "cat_subscription",
        "youtube premium" to "cat_subscription",
        "icloud" to "cat_subscription",
        "google one" to "cat_subscription",
        "disney" to "cat_subscription",
        "prime video" to "cat_subscription",
        "sony liv" to "cat_subscription",
        "zee5" to "cat_subscription",
        "gaana" to "cat_subscription",
        "jiocinema" to "cat_subscription",
        // Healthcare
        "hospital" to "cat_healthcare",
        "pharmacy" to "cat_healthcare",
        "apollo" to "cat_healthcare",
        "medplus" to "cat_healthcare",
        "practo" to "cat_healthcare",
        "doctor" to "cat_healthcare",
        // Education
        "school" to "cat_education",
        "college" to "cat_education",
        "university" to "cat_education",
        "tuition" to "cat_education",
        "udemy" to "cat_education",
        "coursera" to "cat_education",
        // Bills & Utilities
        "electricity" to "cat_bills",
        "adani electricity" to "cat_bills",
        "tata power" to "cat_bills",
        "bses" to "cat_bills",
        "airtel" to "cat_bills",
        "jio" to "cat_bills",
        "vodafone" to "cat_bills",
        "broadband" to "cat_bills",
        "recharge" to "cat_bills",
        "bescom" to "cat_bills",
        "water" to "cat_bills"
    )

    /**
     * Income-side keywords. Kept separate because an income must never resolve
     * to a spending category — a refund from Swiggy is not a Swiggy expense.
     */
    private val INCOME: Map<String, String> = linkedMapOf(
        "salary" to "cat_salary",
        "payroll" to "cat_salary",
        "wages" to "cat_salary",
        "refund" to "cat_other",
        "cashback" to "cat_other",
        "interest" to "cat_other",
        "dividend" to "cat_other"
    )

    /**
     * The category for [merchant], or null when nothing matches.
     *
     * Null is the common case and callers must handle it by leaving the category
     * unresolved — returning `cat_other` here would silently mis-file every
     * unrecognised merchant into a real budget bucket.
     *
     * @param isExpense false for income, which uses a separate table.
     */
    fun resolve(merchant: String?, isExpense: Boolean): String? {
        val normalized = merchant?.lowercase()?.trim() ?: return null
        if (normalized.isEmpty()) return null
        val table = if (isExpense) EXPENSE else INCOME
        return table.entries
            // Longest key first, so "amazon prime" wins over "amazon".
            .sortedByDescending { it.key.length }
            .firstOrNull { (keyword, _) -> containsKeyword(normalized, keyword) }
            ?.value
    }

    /**
     * Whole-word match for a single keyword or a whitespace-separated phrase.
     *
     * `\b` alone is not enough for phrases ("prime video" would match inside
     * "subprime video"), so each component is bounded individually.
     */
    private fun containsKeyword(haystack: String, keyword: String): Boolean {
        val pattern = keyword
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString("\\s+") { Regex.escape(it) }
        return Regex("\\b(?:$pattern)\\b", RegexOption.IGNORE_CASE).containsMatchIn(haystack)
    }
}
