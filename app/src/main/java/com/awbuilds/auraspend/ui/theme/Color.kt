package com.awbuilds.auraspend.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Aurora brand palette ──────────────────────────────────────────────────────
// AuraSpend's visual identity: deep violet, lavender light and an aurora teal
// accent. The in-app palette intentionally matches the app icon and landing page
// (see docs/design/aurora.md) instead of a generic Material baseline.

/** Core brand colors. */
val AuroraPurple = Color(0xFF5E3A8B)      // brand primary (icon background)
val AuroraViolet = Color(0xFF8B5CF6)      // aurora gradient midpoint
val AuroraLavender = Color(0xFFC4A6E6)    // brand accent (icon coin)
val AuroraLavenderLight = Color(0xFFD0BCFF)
val AuroraTeal = Color(0xFF2DD4BF)        // aurora gradient end
val AuroraInk = Color(0xFF14101C)         // dark canvas
val AuroraCream = Color(0xFFFAF7FD)       // light canvas

// Aurora gradient stops (used by hero surfaces and the FAB).
val AuroraGradientStart = AuroraPurple
val AuroraGradientMid = AuroraViolet
val AuroraGradientEnd = AuroraTeal

// ─── Semantic amounts (income / expense / status) ─────────────────────────────
val IncomeGreenLight = Color(0xFF1B7F4B)
val ExpenseRedLight = Color(0xFFC13B3B)
val UpcomingBlue = Color(0xFF3E6FBF)
val OverdueIndigo = Color(0xFF6C4FD8)
val WarningOrangeLight = Color(0xFFA15C00)

val IncomeGreenDark = Color(0xFF5BD68E)
val ExpenseRedDark = Color(0xFFF2857E)
val UpcomingBlueDark = Color(0xFF8AB4F8)
val OverdueIndigoDark = Color(0xFFB9A8FF)
val WarningOrangeDark = Color(0xFFFFB868)

// ─── Light scheme ─────────────────────────────────────────────────────────────
val LightPrimary = AuroraPurple
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEADDFF)
val LightOnPrimaryContainer = Color(0xFF21005D)

val LightSecondary = Color(0xFF625B71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE8DEF8)
val LightOnSecondaryContainer = Color(0xFF1D192B)

val LightTertiary = Color(0xFF00696E)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFF9CF1F6)
val LightOnTertiaryContainer = Color(0xFF002022)

val LightBackground = AuroraCream
val LightOnBackground = Color(0xFF1C1B1F)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1C1B1F)
val LightSurfaceVariant = Color(0xFFF3EDF7)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOutline = Color(0xFF79747E)
val LightOutlineVariant = Color(0xFFCAC4D0)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)

// Surface container roles (light) — subtle violet-tinted neutrals.
val LightSurfContainerLowest = Color(0xFFFFFFFF)
val LightSurfContainerLow = Color(0xFFF7F2FA)
val LightSurfContainer = Color(0xFFF1EBF7)
val LightSurfContainerHigh = Color(0xFFEBE5F1)
val LightSurfContainerHighest = Color(0xFFE6E0EC)

// ─── Dark scheme ──────────────────────────────────────────────────────────────
val DarkPrimary = Color(0xFFD0BCFF)
val DarkOnPrimary = Color(0xFF381E72)
val DarkPrimaryContainer = Color(0xFF4F378B)
val DarkOnPrimaryContainer = Color(0xFFEADDFF)

val DarkSecondary = Color(0xFFCCC2DC)
val DarkOnSecondary = Color(0xFF332D41)
val DarkSecondaryContainer = Color(0xFF4A4458)
val DarkOnSecondaryContainer = Color(0xFFE8DEF8)

val DarkTertiary = Color(0xFF4FD8DE)
val DarkOnTertiary = Color(0xFF00363A)
val DarkTertiaryContainer = Color(0xFF004F53)
val DarkOnTertiaryContainer = Color(0xFF9CF1F6)

val DarkBackground = AuroraInk
val DarkOnBackground = Color(0xFFE6E0E9)
val DarkSurface = Color(0xFF1D1825)
val DarkOnSurface = Color(0xFFE6E0E9)
val DarkSurfaceVariant = Color(0xFF2A2433)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
val DarkOutline = Color(0xFF938F99)
val DarkOutlineVariant = Color(0xFF47434F)
val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)

// Surface container roles (dark) — violet-cast ink.
val DarkSurfContainerLowest = Color(0xFF0E0B13)
val DarkSurfContainerLow = Color(0xFF171320)
val DarkSurfContainer = Color(0xFF211C2B)
val DarkSurfContainerHigh = Color(0xFF2B2536)
val DarkSurfContainerHighest = Color(0xFF362F42)

// ─── AMOLED (true black) ──────────────────────────────────────────────────────
val AmoledPrimary = DarkPrimary
val AmoledOnPrimary = DarkOnPrimary
val AmoledPrimaryContainer = DarkPrimaryContainer
val AmoledOnPrimaryContainer = DarkOnPrimaryContainer

val AmoledSecondary = DarkSecondary
val AmoledOnSecondary = DarkOnSecondary
val AmoledSecondaryContainer = DarkSecondaryContainer
val AmoledOnSecondaryContainer = DarkOnSecondaryContainer

val AmoledTertiary = DarkTertiary
val AmoledOnTertiary = DarkOnTertiary
val AmoledTertiaryContainer = DarkTertiaryContainer
val AmoledOnTertiaryContainer = DarkOnTertiaryContainer

val AmoledBackground = Color(0xFF000000)
val AmoledOnBackground = Color(0xFFE6E0E9)
val AmoledSurface = Color(0xFF0C0A12)
val AmoledOnSurface = Color(0xFFE6E0E9)
val AmoledSurfaceVariant = Color(0xFF16121E)
val AmoledOnSurfaceVariant = Color(0xFFCAC4D0)
val AmoledOutline = Color(0xFF938F99)
val AmoledOutlineVariant = Color(0xFF322C3D)
val AmoledError = Color(0xFFF2B8B5)
val AmoledOnError = Color(0xFF601410)

val AmoledSurfContainerLowest = Color(0xFF000000)
val AmoledSurfContainerLow = Color(0xFF0A0810)
val AmoledSurfContainer = Color(0xFF120E1A)
val AmoledSurfContainerHigh = Color(0xFF1B1724)
val AmoledSurfContainerHighest = Color(0xFF262030)

// ─── AMOLED semantic tokens ───────────────────────────────────────────────────
// AMOLED reuses the dark *Material* roles, but the semantic money colors were
// never tuned for a true-black background: the dark-mode green/red are contrast-
// matched to a #121212 surface and read washed out against #000000. These are
// lifted so income/expense keep the same perceived weight on AMOLED.

val IncomeGreenAmoled = Color(0xFF6FE3A2)
val ExpenseRedAmoled = Color(0xFFFF9A93)
val WarningOrangeAmoled = Color(0xFFFFB77A)
val UpcomingBlueAmoled = Color(0xFFA8C7FA)
val OverdueIndigoAmoled = Color(0xFFC0BCFF)
