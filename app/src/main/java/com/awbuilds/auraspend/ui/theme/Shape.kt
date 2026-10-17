package com.awbuilds.auraspend.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Terracotta shape scale. Content surfaces lean on [medium]/[large]; floating
 * chrome uses [extraLarge] and pills use fully rounded shapes.
 *
 * The corners are one step softer than the old Aurora scale: a warm palette
 * reads as friendlier when surfaces are not sharply rectangular.
 */
val AuraSpendShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
