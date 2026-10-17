package com.awbuilds.auraspend.data.classification

import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.ui.theme.*
import java.util.Locale

val defaultCategories = listOf(
    Category(id = "cat_food", name = "Food & Dining", icon = "restaurant", color = 0xFFE53935.toInt(), isDefault = true),
    Category(id = "cat_transport", name = "Transport", icon = "directions_car", color = 0xFF1E88E5.toInt(), isDefault = true),
    Category(id = "cat_shopping", name = "Shopping", icon = "shopping_bag", color = 0xFF8E24AA.toInt(), isDefault = true),
    Category(id = "cat_bills", name = "Bills & Utilities", icon = "receipt_long", color = 0xFFFF8F00.toInt(), isDefault = true),
    Category(id = "cat_entertainment", name = "Entertainment", icon = "movie", color = 0xFF00ACC1.toInt(), isDefault = true),
    Category(id = "cat_healthcare", name = "Healthcare", icon = "local_hospital", color = 0xFFE53935.toInt(), isDefault = true),
    Category(id = "cat_education", name = "Education", icon = "school", color = 0xFF5E35B1.toInt(), isDefault = true),
    Category(id = "cat_salary", name = "Salary", icon = "account_balance", color = 0xFF43A047.toInt(), isDefault = true),
    Category(id = "cat_investment", name = "Investments", icon = "account_balance", color = 0xFF00897B.toInt(), isDefault = true),
    Category(id = "cat_subscription", name = "Subscriptions", icon = "subscriptions", color = 0xFF6D4C41.toInt(), isDefault = true),
    Category(id = "cat_transfer", name = "Transfer", icon = "swap_horiz", color = 0xFF546E7A.toInt(), isDefault = true),
    Category(id = "cat_grocery", name = "Grocery", icon = "local_grocery_store", color = 0xFF2E7D32.toInt(), isDefault = true),
    Category(id = "cat_other", name = "Other", icon = "category", color = 0xFF757575.toInt(), isDefault = true)
)

/**
 * The default categories [existingIds] does not carry.
 *
 * Startup seeding used to run only on an empty table, so a category added to
 * this list later (Investments, 2026-10-14) would never reach an existing
 * install — and the EPFO parser would file into an id no row renders. Callers
 * insert these without touching rows the user may have edited: a default's id
 * never changes, only its name/icon/colour do.
 */
fun missingDefaultCategories(existingIds: Set<String>): List<Category> =
    defaultCategories.filterNot { it.id in existingIds }


/**
 * Merchant keyword -> category. Scanned with WORD-BOUNDARY matching and
 * longest-key-wins priority (see [keywordCategoryFor]) against both the
 * extracted merchant string and the raw message body.
 *
 * Keys are deliberately safe as standalone tokens: never add a key that is a
 * common substring or sub-word of unrelated text. ("fee" once matched
 * "coffee" -> Education and "credit" matched every "Credit Card" payment ->
 * Salary.)
 */
val categoryKeywordMap: Map<String, String> = mapOf(
    // Food & Dining
    "swiggy" to "cat_food",
    "zomato" to "cat_food",
    "dominos" to "cat_food",
    "pizza hut" to "cat_food",
    "mcdonald" to "cat_food",
    "kfc" to "cat_food",
    "burger king" to "cat_food",
    "subway" to "cat_food",
    "starbucks" to "cat_food",
    "cafe coffee day" to "cat_food",
    "chaayos" to "cat_food",
    "chai point" to "cat_food",
    "third wave coffee" to "cat_food",
    "barbeque nation" to "cat_food",
    "haldiram" to "cat_food",
    "bikanervala" to "cat_food",
    "wow momo" to "cat_food",
    "behrouz" to "cat_food",
    "faasos" to "cat_food",
    "oven story" to "cat_food",
    "ovenstory" to "cat_food",
    "freshmenu" to "cat_food",
    "box8" to "cat_food",
    "eatsure" to "cat_food",
    "eatfit" to "cat_food",
    "dining" to "cat_food",
    "restaurant" to "cat_food",
    "cafe" to "cat_food",
    "coffee" to "cat_food",
    "food" to "cat_food",
    "hotel" to "cat_food",
    "dhaba" to "cat_food",
    "tiffin" to "cat_food",
    "lunch" to "cat_food",
    "dinner" to "cat_food",
    "breakfast" to "cat_food",

    // Grocery
    "zepto" to "cat_grocery",
    "blinkit" to "cat_grocery",
    "instamart" to "cat_grocery",
    "grocery" to "cat_grocery",
    "bigbasket" to "cat_grocery",
    "jiomart" to "cat_grocery",
    "dmart" to "cat_grocery",
    "more supermarket" to "cat_grocery",
    "natures basket" to "cat_grocery",
    "nature basket" to "cat_grocery",
    "milk basket" to "cat_grocery",
    "amazon fresh" to "cat_grocery",
    "vegetable" to "cat_grocery",
    "milk" to "cat_grocery",
    "provision" to "cat_grocery",

    // Transport
    "uber" to "cat_transport",
    "ola" to "cat_transport",
    "rapido" to "cat_transport",
    "metro" to "cat_transport",
    "namma metro" to "cat_transport",
    "delhi metro" to "cat_transport",
    "metro rail" to "cat_transport",
    "petrol" to "cat_transport",
    "fuel" to "cat_transport",
    "indian oil" to "cat_transport",
    "iocl" to "cat_transport",
    "bharat petroleum" to "cat_transport",
    "bpcl" to "cat_transport",
    "hp petrol" to "cat_transport",
    "hpcl" to "cat_transport",
    "fastag" to "cat_transport",
    "nhai" to "cat_transport",
    "bus" to "cat_transport",
    "redbus" to "cat_transport",
    "abhibus" to "cat_transport",
    "taxi" to "cat_transport",
    "cab" to "cat_transport",
    "porter" to "cat_transport",
    "driveu" to "cat_transport",
    "zoomcar" to "cat_transport",
    "revv" to "cat_transport",
    "yulu" to "cat_transport",
    "parking" to "cat_transport",
    "toll" to "cat_transport",
    "railway" to "cat_transport",
    "irctc" to "cat_transport",
    "flight" to "cat_transport",
    "indigo" to "cat_transport",
    "spicejet" to "cat_transport",
    "vistara" to "cat_transport",
    "air india" to "cat_transport",
    "go air" to "cat_transport",
    "goair" to "cat_transport",
    "airasia" to "cat_transport",
    "akasa air" to "cat_transport",
    "goibibo" to "cat_transport",
    "makemytrip" to "cat_transport",

    // Shopping
    "amazon" to "cat_shopping",
    "flipkart" to "cat_shopping",
    "myntra" to "cat_shopping",
    "ajio" to "cat_shopping",
    "meesho" to "cat_shopping",
    "nykaa" to "cat_shopping",
    "tatacliq" to "cat_shopping",
    "croma" to "cat_shopping",
    "reliance digital" to "cat_shopping",
    "vijay sales" to "cat_shopping",
    "lenskart" to "cat_shopping",
    "titan" to "cat_shopping",
    "tanishq" to "cat_shopping",
    "kalyan jewellers" to "cat_shopping",
    "westside" to "cat_shopping",
    "zudio" to "cat_shopping",
    "lifestyle" to "cat_shopping",
    "pantaloons" to "cat_shopping",
    "max fashion" to "cat_shopping",
    "shoppers stop" to "cat_shopping",
    "firstcry" to "cat_shopping",
    "decathlon" to "cat_shopping",
    "shopping" to "cat_shopping",
    "mall" to "cat_shopping",
    "clothing" to "cat_shopping",
    "electronics" to "cat_shopping",

    // Bills & Utilities
    "electricity" to "cat_bills",
    "water" to "cat_bills",
    "gas" to "cat_bills",
    "broadband" to "cat_bills",
    "wifi" to "cat_bills",
    "recharge" to "cat_bills",
    "mobile" to "cat_bills",
    "airtel" to "cat_bills",
    "jio" to "cat_bills",
    "jiofiber" to "cat_bills",
    "jio fiber" to "cat_bills",
    "vodafone" to "cat_bills",
    "bsnl" to "cat_bills",
    "act fibernet" to "cat_bills",
    "act fibre" to "cat_bills",
    "hathway" to "cat_bills",
    "tata play fiber" to "cat_bills",
    "bescom" to "cat_bills",
    "tneb" to "cat_bills",
    "msedcl" to "cat_bills",
    "adani electricity" to "cat_bills",
    "torrent power" to "cat_bills",
    "mahanagar gas" to "cat_bills",
    "igl" to "cat_bills",
    "indane" to "cat_bills",
    "bharat gas" to "cat_bills",
    "hp gas" to "cat_bills",
    "bill" to "cat_bills",
    "bill payment" to "cat_bills",
    "lic" to "cat_bills",
    "policybazaar" to "cat_bills",
    "hdfc life" to "cat_bills",
    "sbi life" to "cat_bills",
    "icici prudential" to "cat_bills",
    "bajaj finserv" to "cat_bills",
    "cred" to "cat_bills",
    "rent" to "cat_bills",
    "maintenance" to "cat_bills",

    // Entertainment
    // Streaming services are subscriptions by identity; the same keys in
    // CategoryKeywordMap already said so. Keeping them Entertainment here made
    // the final category depend on which layer filled it first.
    "netflix" to "cat_subscription",
    "prime video" to "cat_subscription",
    "amazon prime" to "cat_subscription",
    "hotstar" to "cat_subscription",
    "disney" to "cat_subscription",
    "sony liv" to "cat_subscription",
    "sonyliv" to "cat_subscription",
    "zee5" to "cat_subscription",
    "youtube" to "cat_entertainment",
    "spotify" to "cat_subscription",
    "gaana" to "cat_subscription",
    "jiocinema" to "cat_subscription",
    "bookmyshow" to "cat_entertainment",
    "pvr" to "cat_entertainment",
    "inox" to "cat_entertainment",
    "movie" to "cat_entertainment",
    "cinema" to "cat_entertainment",
    "games" to "cat_entertainment",
    "playstation" to "cat_entertainment",
    "steam" to "cat_entertainment",

    // Healthcare
    "hospital" to "cat_healthcare",
    "doctor" to "cat_healthcare",
    "clinic" to "cat_healthcare",
    "pharmacy" to "cat_healthcare",
    "medical" to "cat_healthcare",
    "medicine" to "cat_healthcare",
    "health" to "cat_healthcare",
    "dentist" to "cat_healthcare",
    "diagnostic" to "cat_healthcare",
    "practo" to "cat_healthcare",
    "1mg" to "cat_healthcare",
    "apollo" to "cat_healthcare",
    "pharmeasy" to "cat_healthcare",
    "netmeds" to "cat_healthcare",
    "medplus" to "cat_healthcare",
    "wellness forever" to "cat_healthcare",
    "lal pathlabs" to "cat_healthcare",
    "thyrocare" to "cat_healthcare",
    "cult fit" to "cat_healthcare",
    "cultfit" to "cat_healthcare",

    // Education
    "udemy" to "cat_education",
    "coursera" to "cat_education",
    "unacademy" to "cat_education",
    "byju" to "cat_education",
    "vedantu" to "cat_education",
    "physics wallah" to "cat_education",
    "upgrad" to "cat_education",
    "great learning" to "cat_education",
    "duolingo" to "cat_education",
    "khan academy" to "cat_education",
    "college" to "cat_education",
    "school" to "cat_education",
    "tuition" to "cat_education",
    "course" to "cat_education",

    // Salary — deliberately narrow. Never map bare "credit"/"income": those
    // words appear in ordinary card-payment SMS and misfiled them as Salary.
    "salary" to "cat_salary",
    "payroll" to "cat_salary",
    "wages" to "cat_salary",

    // Subscriptions
    "subscription" to "cat_subscription",
    "renew" to "cat_subscription",
    "icloud" to "cat_subscription",
    "google one" to "cat_subscription",
    "dropbox" to "cat_subscription",
    "office 365" to "cat_subscription",

    // Investments — SIP and fund messages. Before this block existed NOTHING
    // mapped to cat_investment from a message body, so a monthly SIP debit whose
    // only readable words were "SIP purchase … NAV … folio" landed in "Other"
    // for years (25 rows in a closed tester's export, all one amount, one a
    // month — measured 2026-10-16).
    "mutual fund" to "cat_investment",
    "sip" to "cat_investment",
    "folio" to "cat_investment",
    "nav" to "cat_investment",

    // Transfer — deliberately NO rail tokens here. `upi`, `neft`, `imps` and
    // `rtgs` are routing metadata, not categories: with them in this map every
    // UPI/NEFT payment whose merchant was not otherwise recognised was filed as
    // "Transfer", which hid 66 of 155 rows in one tester's export (43 %, measured
    // 2026-10-16) — including payments to shops. A rail plus a person-shaped
    // counterparty is a person-to-person transfer, and that decision is made in
    // `BankMessageParser.categoryIdFor` where the direction (received → income,
    // sent → expense) is known.
    "to self" to "cat_transfer"
)

/**
 * Resolves a category for a merchant name / message fragment.
 *
 * 1. Curated merchant-database lookup (highest precision, normalized so noisy
 *    SMS strings like "SWIGGY*Zomato/BLR" still hit).
 * 2. Word-boundary keyword scan, longest key wins.
 */
fun getCategoryIdForKeyword(input: String): String? {
    if (input.isBlank()) return null

    val merchantSuggestion = MerchantRepository.suggestCategory(input)
    if (merchantSuggestion != null && merchantSuggestion.second >= 0.80f) {
        mapMerchantCategoryToLocal(merchantSuggestion.first)?.let { return it }
    }
    return keywordCategoryFor(input)
}

/**
 * Word-boundary, longest-key-wins keyword scan over [text]. Punctuation
 * becomes whitespace so "swiggy," still matches "swiggy" while "coffee"
 * can never match "fee".
 */
fun keywordCategoryFor(text: String): String? {
    val haystack = " " +
        text.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9& ]+"), " ")
            .replace(Regex("\\s+"), " ") + " "

    var bestLen = -1
    var bestId: String? = null
    for ((key, id) in categoryKeywordMap) {
        val k = key.lowercase(Locale.ENGLISH)
        if (k.length > bestLen && haystack.contains(" $k ")) {
            bestLen = k.length
            bestId = id
        }
    }
    return bestId
}

/**
 * Maps merchant-database category names to local category IDs.
 * Internal so tests can prove every CSV category maps somewhere.
 */
internal fun mapMerchantCategoryToLocal(merchantCategory: String): String? {
    return when (merchantCategory.lowercase(Locale.ENGLISH)) {
        "food" -> "cat_food"
        "groceries" -> "cat_grocery"
        "transport" -> "cat_transport"
        "shopping" -> "cat_shopping"
        "entertainment" -> "cat_entertainment"
        "health & fitness" -> "cat_healthcare"
        "software" -> "cat_subscription"
        "education" -> "cat_education"
        "utilities" -> "cat_bills"
        "travel" -> "cat_transport"
        "banking" -> "cat_bills"
        "finance" -> "cat_bills"
        "real estate" -> "cat_bills"
        "services" -> "cat_other"
        else -> null
    }
}
