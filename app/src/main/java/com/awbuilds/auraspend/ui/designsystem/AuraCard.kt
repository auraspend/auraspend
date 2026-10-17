package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Aurora card styles.
 *
 * Content never uses drop shadows — depth comes from tonal surfaces and 1dp
 * hairline borders (cheaper to render and visually calmer). Glass is reserved
 * for floating chrome over imagery.
 */
enum class AuraCardStyle {
    /** Plain surface: reads as the base canvas. */
    Filled,

    /** Tinted container: groups related rows. */
    Tonal,

    /** Hairline border on the canvas: default for content cards. */
    Outlined,

    /** Translucent surface used by floating chrome. */
    Glass
}

/**
 * The single card primitive for the whole app. Screens should never hand-roll
 * a `Surface`/`Card` again — this keeps radius, padding and borders consistent.
 */
@Composable
fun AuraCard(
    modifier: Modifier = Modifier,
    style: AuraCardStyle = AuraCardStyle.Outlined,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(AuraSpacing.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val border: BorderStroke?

    when (style) {
        AuraCardStyle.Filled -> {
            container = scheme.surface
            border = null
        }
        AuraCardStyle.Tonal -> {
            container = scheme.surfaceContainer
            border = null
        }
        AuraCardStyle.Outlined -> {
            container = scheme.surface
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f))
        }
        AuraCardStyle.Glass -> {
            container = scheme.surface.copy(alpha = 0.78f)
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f))
        }
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(container)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content
    )
}
