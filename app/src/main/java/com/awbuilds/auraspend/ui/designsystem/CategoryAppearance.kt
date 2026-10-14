package com.awbuilds.auraspend.ui.designsystem

/**
 * The icon keys the category editor offers.
 *
 * Every entry must render through [categoryIconGlyph] — a choice the renderer
 * does not know would silently draw the neutral fallback and make the picker
 * look broken. `CategoryAppearanceTest` fails the build if that drifts.
 */
val categoryIconChoices: List<String> = listOf(
    "restaurant", "local_grocery_store", "directions_car", "shopping_bag",
    "receipt_long", "movie", "local_hospital", "school", "account_balance",
    "subscriptions", "swap_horiz", "category",
)

/**
 * Colors the category editor offers — the same opaque ARGB palette the seeded
 * defaults use, stored exactly as `Category.color` stores it.
 */
val categoryColorChoices: List<Int> = listOf(
    0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF5E35B1.toInt(),
    0xFF1E88E5.toInt(), 0xFF00ACC1.toInt(), 0xFF00897B.toInt(), 0xFF43A047.toInt(),
    0xFFFF8F00.toInt(), 0xFFF4511E.toInt(), 0xFF6D4C41.toInt(), 0xFF757575.toInt(),
)
