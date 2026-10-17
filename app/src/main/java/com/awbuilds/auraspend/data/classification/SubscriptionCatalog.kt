package com.awbuilds.auraspend.data.classification

/**
 * Subscription services by identity, with the display name the user recognises.
 *
 * Shared by the merchant extractor (so "AutoPay of Rs 499 NETFLIX e-mandate"
 * names the service instead of leaving the merchant null) and by
 * [AiSignalFusion] (so the recurring flag and the Subscriptions category agree
 * with the merchant). Keeping one list means the two cannot drift.
 */
internal object SubscriptionCatalog {

    /** Longest key first so "amazon prime" wins over "amazon". */
    private val services: Map<String, String> = linkedMapOf(
        "amazon prime" to "Amazon Prime",
        "prime video" to "Prime Video",
        "youtube premium" to "YouTube Premium",
        "youtube music" to "YouTube Music",
        "kindle unlimited" to "Kindle Unlimited",
        "google one" to "Google One",
        "google ai" to "Google AI",
        "gemini advanced" to "Gemini Advanced",
        "microsoft 365" to "Microsoft 365",
        "office 365" to "Office 365",
        "linkedin premium" to "LinkedIn Premium",
        "disney+" to "Disney+",
        "sony liv" to "Sony LIV",
        "jiocinema" to "JioCinema",
        "jiosaavn" to "JioSaavn",
        "audible" to "Audible",
        "netflix" to "Netflix",
        "spotify" to "Spotify",
        "hotstar" to "Hotstar",
        "zee5" to "ZEE5",
        "icloud" to "iCloud",
        "chatgpt" to "ChatGPT",
        "openai" to "OpenAI",
        "claude" to "Claude",
        "notion" to "Notion",
        "canva" to "Canva",
        "adobe" to "Adobe",
        "dropbox" to "Dropbox",
        "duolingo" to "Duolingo",
        "wynk" to "Wynk",
        "crunchyroll" to "Crunchyroll",
        "zoom pro" to "Zoom Pro",
        "figma" to "Figma",
        "lastpass" to "LastPass",
        "1password" to "1Password",
        "tata play" to "Tata Play",
        "times prime" to "Times Prime",
        "cult fit" to "Cult.fit"
    )

    /** Canonical display name when [text] names a known service, else null. */
    fun match(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val haystack = " " +
            text.lowercase()
                .replace(Regex("[^a-z0-9&+ ]+"), " ")
                .replace(Regex("\\s+"), " ") +
            " "
        return services.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { (key, _) -> haystack.contains(" $key ") }
            ?.value
    }

    fun all(): List<String> = services.keys.toList()
}
