package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material icon names used by the seeded categories → vector icons.
 * User-created categories can still use emoji.
 */
private val knownCategoryIcons: Map<String, ImageVector> = mapOf(
    "restaurant" to Icons.Default.Restaurant,
    "directions_car" to Icons.Default.DirectionsCar,
    "shopping_bag" to Icons.Default.ShoppingBag,
    "receipt_long" to Icons.AutoMirrored.Filled.ReceiptLong,
    "movie" to Icons.Default.Movie,
    "local_hospital" to Icons.Default.LocalHospital,
    "school" to Icons.Default.School,
    "account_balance" to Icons.Default.AccountBalance,
    "subscriptions" to Icons.Default.Subscriptions,
    "swap_horiz" to Icons.Default.SwapHoriz,
    "local_grocery_store" to Icons.Default.LocalGroceryStore,
    "category" to Icons.Default.Category
)

fun categoryIconVector(icon: String?): ImageVector? = knownCategoryIcons[icon]

/**
 * Normalizes a category `icon` value for rendering.
 *
 * - Known Material icon names pass through so [CategoryAvatar] can draw the vector.
 * - Emoji (non-ASCII) values from user-created categories pass through.
 * - Anything unrecognised returns null so the avatar draws the neutral vector
 *   fallback instead of an emoji. A 🏷️ in chrome was an AGENTS.md rule-5
 *   violation and rendered inconsistently across platforms.
 */
fun categoryIconGlyph(icon: String?): String? {
    if (icon.isNullOrBlank()) return null
    if (icon.any { it.code > 127 }) return icon.take(2)
    return if (knownCategoryIcons.containsKey(icon)) icon else null
}

/** Vector shown when a category has no usable icon. */
private val FallbackVector = Icons.Default.Category

/**
 * Category badge. Known Material icon names render as vectors tinted with the
 * category colour; emoji (user-created categories) render as text.
 *
 * The badge is a soft rounded square rather than a circle — one of the small
 * shape decisions that separates the terracotta identity from the old look.
 */
@Composable
fun CategoryAvatar(
    icon: String?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    showRing: Boolean = false,
    ringStroke: Dp = 1.5.dp
) {
    val shape = RoundedCornerShape(size * 0.30f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        color.copy(alpha = 0.20f),
                        color.copy(alpha = 0.09f)
                    )
                )
            )
            .then(
                if (showRing) {
                    Modifier.border(
                        width = ringStroke,
                        color = color.copy(alpha = 0.45f),
                        shape = shape
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val vector = categoryIconVector(icon) ?: FallbackVector
        val isEmoji = categoryIconVector(icon) == null && !icon.isNullOrBlank() &&
            icon.any { it.code > 127 }
        if (isEmoji) {
            Text(
                text = icon,
                fontSize = (size.value / 2.3f).sp
            )
        } else {
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size * 0.46f)
            )
        }
    }
}
