package com.awbuilds.auraspend.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.ui.designsystem.LocalCurrencyStyle
import com.awbuilds.auraspend.ui.designsystem.MoneyConfig
import com.awbuilds.auraspend.ui.designsystem.rememberCurrencyStyle

enum class AppThemeMode {
    /** Follow the OS. Previously there was no SYSTEM option, so a user with
     *  system dark mode got a light app on first launch. */
    SYSTEM, LIGHT, DARK, AMOLED;

    companion object {
        /** Tolerant parse: a corrupt preference falls back instead of crashing. */
        fun fromName(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Semantic design tokens that go beyond the Material color roles.
 * Field names are stable — screens read them through [MaterialTheme.extendedColors].
 */
data class ExtendedColors(
    val incomeAmount: Color,
    val expenseAmount: Color,
    val upcoming: Color,
    val overdue: Color,
    val warningOrange: Color,
    val textLight: Color,
    val canvasContainer: Color,
    /** Row hairline inside a card — lighter than the card's own border. */
    val lineSoft: Color,
    /** Progress-bar / spark-column track. */
    val track: Color,
    /** Eyebrow, axis and rank-percentage grey. */
    val faint: Color,
    /** Chip label and secondary ink — between `muted` and `onSurface`. */
    val inkSoft: Color
)

val LightExtendedColors = ExtendedColors(
    incomeAmount = IncomeGreenLight,
    expenseAmount = ExpenseRedLight,
    upcoming = UpcomingBlueLight,
    overdue = OverdueIndigoLight,
    warningOrange = WarningOrangeLight,
    textLight = LightOnSurfaceVariant,
    canvasContainer = LightSurfContainerHighest,
    lineSoft = LineSoftLight,
    track = TrackLight,
    faint = FaintLight,
    inkSoft = InkSoftLight
)

val DarkExtendedColors = ExtendedColors(
    incomeAmount = IncomeGreenDark,
    expenseAmount = ExpenseRedDark,
    upcoming = UpcomingBlueDark,
    overdue = OverdueIndigoDark,
    warningOrange = WarningOrangeDark,
    textLight = DarkOnSurfaceVariant,
    canvasContainer = DarkSurfaceVariant,
    lineSoft = LineSoftDark,
    track = TrackDark,
    faint = FaintDark,
    inkSoft = InkSoftDark
)

/** AMOLED needs its own semantic set — see the note in `Color.kt`. */
val AmoledExtendedColors = ExtendedColors(
    incomeAmount = IncomeGreenAmoled,
    expenseAmount = ExpenseRedAmoled,
    upcoming = UpcomingBlueAmoled,
    overdue = OverdueIndigoAmoled,
    warningOrange = WarningOrangeAmoled,
    textLight = DarkOnSurfaceVariant,
    canvasContainer = AmoledSurfContainerHigh,
    lineSoft = LineSoftDark,
    track = TrackDark,
    faint = FaintDark,
    inkSoft = InkSoftDark
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable get() = LocalExtendedColors.current

// ─── Typography ───────────────────────────────────────────────────────────────
// Plus Jakarta Sans (variable, OFL) — a geometric humanist face with confident
// numerals. `tnum` keeps money columns from jittering while they animate.
//
// The family is a *variable* font: one file carries every weight on its `wght`
// axis. Registering that one file six times under six `weight`s — which is what
// this did — only tells Compose which entry to *match*; the renderer still draws
// the file's default instance, so every 700 in the app was drawn at regular
// weight and every screen read lighter and narrower than the design. The axis
// has to be set explicitly, per weight.

private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val PlusJakartaSans = FontFamily(
    jakarta(300),
    jakarta(400),
    jakarta(500),
    jakarta(600),
    jakarta(700),
    jakarta(800)
)

private const val NUMERIC_FEATURES = "tnum"

private fun auraStyle(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
    numeric: Boolean = false
) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = if (numeric) NUMERIC_FEATURES else null
)

private val AuraSpendTypography = Typography(
    displayLarge = auraStyle(FontWeight.Bold, 52, 60, -0.5, numeric = true),
    displayMedium = auraStyle(FontWeight.Bold, 42, 50, -0.4, numeric = true),
    displaySmall = auraStyle(FontWeight.SemiBold, 34, 42, -0.3, numeric = true),
    headlineLarge = auraStyle(FontWeight.Bold, 30, 38, -0.2, numeric = true),
    headlineMedium = auraStyle(FontWeight.SemiBold, 26, 34, -0.1, numeric = true),
    headlineSmall = auraStyle(FontWeight.Bold, 23, 29, -0.5, numeric = true),
    titleLarge = auraStyle(FontWeight.SemiBold, 20, 26),
    titleMedium = auraStyle(FontWeight.SemiBold, 16, 22, 0.1),
    titleSmall = auraStyle(FontWeight.Medium, 14, 20, 0.1),
    bodyLarge = auraStyle(FontWeight.Normal, 16, 24, 0.15),
    bodyMedium = auraStyle(FontWeight.Normal, 14, 20, 0.2),
    bodySmall = auraStyle(FontWeight.Normal, 12, 16, 0.3),
    labelLarge = auraStyle(FontWeight.SemiBold, 14, 20, 0.1),
    labelMedium = auraStyle(FontWeight.SemiBold, 12, 16, 0.4),
    labelSmall = auraStyle(FontWeight.Medium, 11, 14, 0.5)
)

// ─── Color schemes ────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    surfaceContainerLowest = LightSurfContainerLowest,
    surfaceContainerLow = LightSurfContainerLow,
    surfaceContainer = LightSurfContainer,
    surfaceContainerHigh = LightSurfContainerHigh,
    surfaceContainerHighest = LightSurfContainerHighest
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    surfaceContainerLowest = DarkSurfContainerLowest,
    surfaceContainerLow = DarkSurfContainerLow,
    surfaceContainer = DarkSurfContainer,
    surfaceContainerHigh = DarkSurfContainerHigh,
    surfaceContainerHighest = DarkSurfContainerHighest
)

private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = AmoledOnPrimary,
    primaryContainer = AmoledPrimaryContainer,
    onPrimaryContainer = AmoledOnPrimaryContainer,
    secondary = AmoledSecondary,
    onSecondary = AmoledOnSecondary,
    secondaryContainer = AmoledSecondaryContainer,
    onSecondaryContainer = AmoledOnSecondaryContainer,
    tertiary = AmoledTertiary,
    onTertiary = AmoledOnTertiary,
    tertiaryContainer = AmoledTertiaryContainer,
    onTertiaryContainer = AmoledOnTertiaryContainer,
    background = AmoledBackground,
    onBackground = AmoledOnBackground,
    surface = AmoledSurface,
    onSurface = AmoledOnSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = AmoledOnSurfaceVariant,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant,
    error = AmoledError,
    onError = AmoledOnError,
    errorContainer = AmoledErrorContainer,
    onErrorContainer = AmoledOnErrorContainer,
    surfaceContainerLowest = AmoledSurfContainerLowest,
    surfaceContainerLow = AmoledSurfContainerLow,
    surfaceContainer = AmoledSurfContainer,
    surfaceContainerHigh = AmoledSurfContainerHigh,
    surfaceContainerHighest = AmoledSurfContainerHighest
)

@Composable
fun AuraSpendTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    /**
     * The currency the user picked, or null to follow the device locale. Hoisted
     * by `MainActivity` (like [themeMode]) because a preference is not observable
     * state: reading it here would leave the symbol stale until a cold start.
     */
    currencyCode: String? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicAvailable = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Resolve SYSTEM against the OS setting. isSystemInDarkTheme() is reactive, so
    // the app follows a live configuration change rather than only at launch.
    val isDark = isSystemInDarkTheme()
    val resolved = when (themeMode) {
        AppThemeMode.SYSTEM -> if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT
        else -> themeMode
    }

    val colorScheme = when (resolved) {
        AppThemeMode.AMOLED -> AmoledColorScheme
        AppThemeMode.DARK -> if (dynamicAvailable) dynamicDarkColorScheme(context) else DarkColorScheme
        // AMOLED is dark, so dynamic color would defeat the point of the mode.
        else -> if (dynamicAvailable) dynamicLightColorScheme(context) else LightColorScheme
    }

    val extendedColors = when (resolved) {
        AppThemeMode.LIGHT -> LightExtendedColors
        AppThemeMode.DARK -> DarkExtendedColors
        AppThemeMode.AMOLED -> AmoledExtendedColors
        AppThemeMode.SYSTEM -> if (isDark) DarkExtendedColors else LightExtendedColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = resolved == AppThemeMode.LIGHT
            // The navigation bar was never set, so DARK/AMOLED kept dark system
            // icons on the near-black bar — the same class of bug as the black
            // LocalContentColor default. Both bars follow the theme together.
            insetsController.isAppearanceLightNavigationBars = resolved == AppThemeMode.LIGHT
        }
    }

    // Currency is part of the theme so a change in Settings reaches every money
    // text, chart axis and export without threading a parameter through the UI.
    val currency = rememberCurrencyStyle(currencyCode)
    MoneyConfig.update(currency)

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalCurrencyStyle provides currency,
        // Compose's LocalContentColor default is black, which is what untinted
        // Icons and bare Text inherit when no Material component sets one:
        // readable in LIGHT, invisible in DARK and AMOLED. Observed on the
        // owner's device (back buttons, the month chevrons). Material components
        // still override it with their own container colours.
        LocalContentColor provides colorScheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AuraSpendTypography,
            shapes = AuraSpendShapes,
            content = content
        )
    }
}
