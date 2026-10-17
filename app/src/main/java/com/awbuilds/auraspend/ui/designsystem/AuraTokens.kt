package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.ui.theme.AuroraGradientEnd
import com.awbuilds.auraspend.ui.theme.AuroraGradientMid
import com.awbuilds.auraspend.ui.theme.AuroraGradientStart
import com.awbuilds.auraspend.ui.theme.PlusJakartaSans

/**
 * Aurora spacing scale — a strict 4dp grid. Screens must not invent one-off
 * paddings: use these tokens (or [gutter]) so every screen breathes the same.
 */
object AuraSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Standard horizontal screen gutter. */
    val gutter = 20.dp

    /** Comfortable bottom padding for lists behind the floating chrome. */
    val bottomContent = 112.dp
}

/**
 * Aurora motion tokens. All animation specs come from here so the app feels
 * like one product instead of a collection of screens.
 */
object AuraMotion {
    const val DURATION_QUICK = 150
    const val DURATION_STANDARD = 220
    const val DURATION_EMPHASIZED = 300
    const val DURATION_SLOW = 600
    const val DURATION_CHART = 800

    /** No-overshoot settle: default for layout and content changes. */
    fun <T> standard(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Playful but controlled: selection, indicators, chips. */
    fun <T> expressive(): SpringSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)

    /** Immediate feedback: press states and toggles. */
    fun <T> snappy(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /** Slow, soft settle: charts and large surfaces. */
    fun <T> gentle(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

    /** Standard eased tween for chart draw-ins and counters. */
    fun <T> eased(durationMillis: Int = DURATION_SLOW) =
        tween<T>(durationMillis, easing = FastOutSlowInEasing)
}

/** Aurora brand brushes. */
object AuraGradients {
    /** Signature aurora sweep — violet → purple → teal. */
    val aurora: Brush = Brush.linearGradient(
        colors = listOf(AuroraGradientStart, AuroraGradientMid, AuroraGradientEnd)
    )

    /** Softer variant for large surfaces. */
    val auroraSoft: Brush = Brush.linearGradient(
        colors = listOf(
            AuroraGradientStart.copy(alpha = 0.92f),
            AuroraGradientMid.copy(alpha = 0.86f),
            AuroraGradientEnd.copy(alpha = 0.82f)
        )
    )

    /** Faint vertical wash used behind hero content. */
    @Composable
    @ReadOnlyComposable
    fun heroWash(): Brush {
        val scheme = MaterialTheme.colorScheme
        return Brush.verticalGradient(
            colors = listOf(
                scheme.primaryContainer.copy(alpha = 0.55f),
                scheme.surface.copy(alpha = 0.0f)
            )
        )
    }

    /** Text/icon color that is legible on [aurora] in every theme. */
    val onAurora = Color(0xFFFFFFFF)
}

/**
 * Numeric text styles with tabular figures — money must never jitter while it
 * animates or when digits change.
 */
object AuraType {
    val moneyHero = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.6).sp,
        fontFeatureSettings = "tnum"
    )

    val moneyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum"
    )

    val moneyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = "tnum"
    )

    val moneySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    )

    /** Small uppercase label used above metrics. */
    val metricLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    )
}

/** Convenience: aurora-tinted overlay for pressed/selected states. */
@Composable
@ReadOnlyComposable
fun auraTint(alpha: Float = 0.12f): Color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
