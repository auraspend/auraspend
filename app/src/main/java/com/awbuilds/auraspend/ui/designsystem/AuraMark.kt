package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The illustrated terracotta mark: a hand-thrown clay pot with a coin dropping
 * into its slot — "money you keep".
 *
 * Drawn entirely with Compose [Canvas] paths rather than a bitmap or a vector
 * asset so it inherits the theme palette in all three modes, scales to any size
 * without raster artefacts, and needs no drawable review. Keep the geometry
 * inside the unit box: callers size it via [modifier].
 *
 * Colour note: the pot deliberately never reaches the deep `onPrimaryContainer`
 * brown — at small sizes a dark gradient turns the pot into an unreadable blob.
 * It stays in the terracotta mid-tones and leans on the coin for the accent.
 */
@Composable
fun AuraMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    val clay = scheme.primary
    val clayLight = lerp(clay, Color.White, 0.24f)
    val clayDark = lerp(clay, Color.Black, 0.14f)
    val slot = lerp(clay, Color.Black, 0.58f)
    val coinFace = scheme.tertiary
    val coinRim = lerp(scheme.tertiary, Color.Black, 0.30f)

    Canvas(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
            role = Role.Image
        }
    ) {
        val w = size.width
        val h = size.height
        fun x(f: Float) = w * f
        fun y(f: Float) = h * f

        // ── Grounding shadow ─────────────────────────────────────────────────
        drawOval(
            color = clay.copy(alpha = 0.12f),
            topLeft = Offset(x(0.23f), y(0.905f)),
            size = Size(x(0.54f), y(0.06f))
        )

        // ── Body: shoulders wide, base tapered ───────────────────────────────
        val body = Path().apply {
            moveTo(x(0.36f), y(0.44f))
            cubicTo(x(0.24f), y(0.50f), x(0.17f), y(0.62f), x(0.19f), y(0.72f))
            cubicTo(x(0.21f), y(0.83f), x(0.28f), y(0.91f), x(0.37f), y(0.92f))
            lineTo(x(0.63f), y(0.92f))
            cubicTo(x(0.72f), y(0.91f), x(0.79f), y(0.83f), x(0.81f), y(0.72f))
            cubicTo(x(0.83f), y(0.62f), x(0.76f), y(0.50f), x(0.64f), y(0.44f))
            close()
        }
        drawPath(
            body,
            brush = Brush.verticalGradient(
                colors = listOf(clayLight, clay, clayDark),
                startY = y(0.44f),
                endY = y(0.92f)
            )
        )

        // Soft specular streak down the left shoulder: gives the clay volume.
        drawPath(
            path = Path().apply {
                moveTo(x(0.31f), y(0.52f))
                cubicTo(x(0.25f), y(0.62f), x(0.23f), y(0.72f), x(0.26f), y(0.80f))
            },
            color = Color.White.copy(alpha = 0.20f),
            style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
        )

        // ── Rim band ─────────────────────────────────────────────────────────
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(clayLight, clay),
                startY = y(0.39f),
                endY = y(0.47f)
            ),
            topLeft = Offset(x(0.29f), y(0.395f)),
            size = Size(x(0.42f), y(0.07f)),
            cornerRadius = CornerRadius(w * 0.035f)
        )

        // ── Coin slot ────────────────────────────────────────────────────────
        drawRoundRect(
            color = slot,
            topLeft = Offset(x(0.425f), y(0.415f)),
            size = Size(x(0.15f), y(0.028f)),
            cornerRadius = CornerRadius(w * 0.014f)
        )

        // ── Coin, about to drop in ───────────────────────────────────────────
        // Small and clearly separated from the rim: drawn any larger or lower it
        // stops reading as a coin mid-drop and becomes a stopper on the pot's
        // mouth.
        val coinCenter = Offset(x(0.5f), y(0.26f))
        val coinRadius = w * 0.088f
        drawCircle(color = coinFace, radius = coinRadius, center = coinCenter)
        drawCircle(
            color = coinRim,
            radius = coinRadius,
            center = coinCenter,
            style = Stroke(width = w * 0.018f)
        )
        // A small inner ring + highlight so it reads as a coin, not a dot.
        drawCircle(
            color = coinRim.copy(alpha = 0.55f),
            radius = coinRadius * 0.62f,
            center = coinCenter,
            style = Stroke(width = w * 0.012f)
        )
        drawArc(
            color = Color.White.copy(alpha = 0.55f),
            startAngle = 200f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(coinCenter.x - coinRadius * 0.62f, coinCenter.y - coinRadius * 0.62f),
            size = Size(coinRadius * 1.24f, coinRadius * 1.24f),
            style = Stroke(width = w * 0.016f, cap = StrokeCap.Round)
        )
    }
}

/**
 * The mark on a soft tonal medallion — the empty-state / onboarding treatment.
 * The medallion is a warm radial wash rather than a flat circle so it still
 * reads as an illustration at small sizes.
 */
@Composable
fun AuraMarkMedallion(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    contentDescription: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.primaryContainer,
                        scheme.primaryContainer.copy(alpha = 0.35f),
                        scheme.surface.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = this.size.minDimension * 0.62f
                )
            )
            drawCircle(
                color = scheme.primary.copy(alpha = 0.10f),
                radius = this.size.minDimension * 0.46f,
                center = center
            )
        }
        AuraMark(
            modifier = Modifier.size(size * 0.62f),
            contentDescription = contentDescription
        )
    }
}
