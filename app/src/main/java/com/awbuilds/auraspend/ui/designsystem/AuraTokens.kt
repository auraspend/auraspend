package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    /**
     * Gap between two blocks on a screen (a card, a section of rows).
     *
     * 26, not 24: the concept's rhythm is 4/8/12/16/20/26/32, and the extra 2dp
     * is what keeps a hairline card from reading as attached to the block above
     * it once the section headers lost their own vertical padding.
     */
    val section = 26.dp

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

/**
 * Numeric text styles with tabular figures — money must never jitter while it
 * animates or when digits change.
 *
 * Every size, weight and tracking value here is taken from the interface
 * concept's type scale (`docs/design/interface-concept/index.html`), which is the
 * reference the screens are measured against. The letter-spacing values look
 * arbitrary because they are the concept's own em-relative tracking resolved at
 * each size (e.g. −0.038em at 45sp is −1.71sp), not a rounded house number.
 */
object AuraType {
    /** The one hero number on a screen (Home's balance). */
    val moneyHero = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.71).sp,
        fontFeatureSettings = "tnum"
    )

    /**
     * The hero number where a second element shares the screen (Plan's spend,
     * Insights' total). Same role, one step down.
     */
    val moneyHeroSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 39.sp,
        letterSpacing = (-1.444).sp,
        fontFeatureSettings = "tnum"
    )

    val moneyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.84).sp,
        fontFeatureSettings = "tnum"
    )

    /** Card metric — a goal's saved amount, a compact stat. */
    val moneyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.34).sp,
        fontFeatureSettings = "tnum"
    )

    val moneySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.225).sp,
        fontFeatureSettings = "tnum"
    )

    /** Small label used above metrics. */
    val metricLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    )

    /**
     * Uppercase micro-label: the date line, "TOTAL BALANCE", a section's
     * overline. Small, wide-tracked and quiet — it labels without competing.
     */
    val eyebrow = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.05.sp
    )

    /** The screen's own title — "Good evening", "Activity", "Plan". */
    val screenTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.575).sp
    )

    /**
     * Section header inside a screen ("October budgets", "Where it went").
     *
     * Deliberately small. A 20sp header above every block turns one screen into
     * four competing titles; at 13sp the headers read as a rhythm the eye skips
     * until it needs them, and the numbers stay the loudest thing on the page.
     */
    val sectionTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.065).sp
    )

    /** Trailing action on a section header ("All budgets"). */
    val sectionAction = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp
    )

    /** Label inside [AuraChip] — the app's one selection control. */
    val chipLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    /** Bottom-bar destination label. */
    val navLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.5.sp,
        lineHeight = 11.sp,
        letterSpacing = 0.0475.sp
    )

    /** Transaction row: merchant, meta line and amount. */
    val rowTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.162).sp
    )

    val rowMeta = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 13.sp
    )

    val rowMoney = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.27).sp,
        fontFeatureSettings = "tnum"
    )

    /** A budget row inside Plan and Home: name, amount and the 11sp sub-row. */
    val budgetName = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.13).sp
    )

    val budgetAmount = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.26).sp,
        fontFeatureSettings = "tnum"
    )

    val budgetSub = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp
    )

    /** Alert-rail card: a 12.5sp sentence over an 11sp explanation. */
    val alertTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        lineHeight = 15.sp,
        letterSpacing = (-0.125).sp
    )

    /** The glyph or count inside an alert-rail badge. */
    val alertBadge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        lineHeight = 14.sp
    )

    val alertMeta = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp
    )

    /** The two captions under Home's flow bar. */
    val flowCaption = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 13.sp
    )

    /** Insights' category ranking. */
    val rankName = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 15.sp,
        letterSpacing = (-0.125).sp
    )

    val rankValue = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        lineHeight = 15.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    )

    val rankPercent = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 13.sp
    )

    /** Axis captions under the daily-spend strip. */
    val axisLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 12.sp
    )

    /** A settings row: 13sp label, 12sp current value on the right. */
    val settingsRowTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.13).sp
    )

    val settingsRowValue = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp
    )

    /** Uppercase group heading in Settings ("MONEY", "MESSAGES"). */
    val groupLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.945.sp
    )

    /** The always-visible Activity search field. */
    val searchPlaceholder = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp
    )

    /** A savings-goal card: name, amount, sub-row. */
    val goalTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        lineHeight = 15.sp,
        letterSpacing = (-0.125).sp
    )

    val goalAmount = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum"
    )

    /** The "12 % vs last month" pill. */
    val deltaLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 13.sp
    )

    /**
     * Body copy — the reading text of a full-screen surface (onboarding, the
     * licence pages). The app's *screens* are made of rows and numbers and have
     * almost no prose, so this is the one place a paragraph size is needed; it is
     * deliberately smaller than Material's bodyLarge (16sp), which read as a
     * different document next to 13sp rows.
     */
    val bodyCopy = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.075).sp
    )

    /** The app's name set large — the splash wordmark. */
    val wordmark = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.84).sp
    )

    /** The Plan/Insights hero's "79% used · of ₹35,000 across 6 budgets". */
    val heroMeta = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.sp
    )

    val heroMetaStrong = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 15.sp
    )

    /** A link row inside a card ("Subscriptions", "Backup & export"). */
    val rowLinkTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.13).sp
    )

    val rowLinkMeta = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 15.sp
    )

    /** Quick Add's amount display and keypad digits. */
    val amountDisplay = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 45.sp,
        letterSpacing = (-1.76).sp,
        fontFeatureSettings = "tnum"
    )

    val keypadDigit = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    )

    /** The primary button ("Save") and its peers. */
    val buttonPrimary = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.14).sp
    )
}

/** Convenience: aurora-tinted overlay for pressed/selected states. */
@Composable
@ReadOnlyComposable
fun auraTint(alpha: Float = 0.12f): Color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
