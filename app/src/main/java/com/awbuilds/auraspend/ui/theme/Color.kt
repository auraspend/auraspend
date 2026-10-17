package com.awbuilds.auraspend.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Terracotta brand palette ─────────────────────────────────────────────────
// AuraSpend's visual identity: warm terracotta clay, sun-baked sand and an
// olive-sage secondary, on a cream canvas. The token *names* keep their Aurora
// prefixes for source stability (they are referenced across the design system),
// but the values below are the Terracotta identity — the palette that ships.

/** Core brand colors. */
val AuroraPurple = Color(0xFFA64B2A)      // terracotta clay — brand primary
val AuroraViolet = Color(0xFFD2764A)      // ember — gradient midpoint
val AuroraLavender = Color(0xFFE9BFA2)    // sand — brand accent
val AuroraLavenderLight = Color(0xFFF3D9C6)
val AuroraTeal = Color(0xFFF2D8BC)        // gradient end (sun-bleached sand)
val AuroraInk = Color(0xFF17110D)         // dark canvas — warm near-black
val AuroraCream = Color(0xFFFCF7F2)       // light canvas — warm cream

// Terracotta gradient stops (used by hero surfaces and the FAB).
val AuroraGradientStart = AuroraPurple
val AuroraGradientMid = AuroraViolet
val AuroraGradientEnd = AuroraTeal

// ─── Semantic amounts (income / expense / status) ─────────────────────────────
val IncomeGreenLight = Color(0xFF1F7A4D)
val ExpenseRedLight = Color(0xFFB3372B)
val UpcomingBlueLight = Color(0xFF3E6494)
val OverdueIndigoLight = Color(0xFF8A4E6B)
val WarningOrangeLight = Color(0xFF96590E)

val IncomeGreenDark = Color(0xFF63D394)
val ExpenseRedDark = Color(0xFFF2938A)
val UpcomingBlueDark = Color(0xFF9DB8E8)
val OverdueIndigoDark = Color(0xFFD2A3CE)
val WarningOrangeDark = Color(0xFFF5BE7E)

// ─── Light scheme ─────────────────────────────────────────────────────────────
val LightPrimary = AuroraPurple
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFF8DFD2)
val LightOnPrimaryContainer = Color(0xFF3B1607)

val LightSecondary = Color(0xFF5F6B4A)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE2E8D3)
val LightOnSecondaryContainer = Color(0xFF1D2411)

val LightTertiary = Color(0xFF8A6320)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF6E4C3)
val LightOnTertiaryContainer = Color(0xFF2C1F03)

val LightBackground = AuroraCream
val LightOnBackground = Color(0xFF221A14)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF221A14)
val LightSurfaceVariant = Color(0xFFF3E9E0)
val LightOnSurfaceVariant = Color(0xFF564940)
val LightOutline = Color(0xFF8B7B6E)
val LightOutlineVariant = Color(0xFFDACDC1)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)

// Surface container roles (light) — warm parchment neutrals.
val LightSurfContainerLowest = Color(0xFFFFFFFF)
val LightSurfContainerLow = Color(0xFFFAF3ED)
val LightSurfContainer = Color(0xFFF5EDE6)
val LightSurfContainerHigh = Color(0xFFEFE6DD)
val LightSurfContainerHighest = Color(0xFFE9DFD5)

// ─── Dark scheme ──────────────────────────────────────────────────────────────
val DarkPrimary = Color(0xFFF0A47C)
val DarkOnPrimary = Color(0xFF4A200D)
val DarkPrimaryContainer = Color(0xFF78351A)
val DarkOnPrimaryContainer = Color(0xFFFCDCCA)

val DarkSecondary = Color(0xFFBCC7A2)
val DarkOnSecondary = Color(0xFF2A3318)
val DarkSecondaryContainer = Color(0xFF414C2C)
val DarkOnSecondaryContainer = Color(0xFFDCE4C6)

val DarkTertiary = Color(0xFFE7C48E)
val DarkOnTertiary = Color(0xFF432F08)
val DarkTertiaryContainer = Color(0xFF604517)
val DarkOnTertiaryContainer = Color(0xFFFAE3BE)

val DarkBackground = AuroraInk
val DarkOnBackground = Color(0xFFEFE3DA)
val DarkSurface = Color(0xFF211A14)
val DarkOnSurface = Color(0xFFEFE3DA)
val DarkSurfaceVariant = Color(0xFF342A20)
val DarkOnSurfaceVariant = Color(0xFFD7C7B8)
val DarkOutline = Color(0xFF9D8C7D)
val DarkOutlineVariant = Color(0xFF53463B)
val DarkError = Color(0xFFF2B8AB)
val DarkOnError = Color(0xFF601410)

// Surface container roles (dark) — toasted clay neutrals.
val DarkSurfContainerLowest = Color(0xFF0D0A07)
val DarkSurfContainerLow = Color(0xFF191310)
val DarkSurfContainer = Color(0xFF241D16)
val DarkSurfContainerHigh = Color(0xFF2F261D)
val DarkSurfContainerHighest = Color(0xFF3A3025)

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
val AmoledOnBackground = Color(0xFFEFE3DA)
val AmoledSurface = Color(0xFF0C0906)
val AmoledOnSurface = Color(0xFFEFE3DA)
val AmoledSurfaceVariant = Color(0xFF1B1510)
val AmoledOnSurfaceVariant = Color(0xFFD7C7B8)
val AmoledOutline = Color(0xFF9D8C7D)
val AmoledOutlineVariant = Color(0xFF3A3025)
val AmoledError = Color(0xFFF2B8AB)
val AmoledOnError = Color(0xFF601410)

val AmoledSurfContainerLowest = Color(0xFF000000)
val AmoledSurfContainerLow = Color(0xFF0A0806)
val AmoledSurfContainer = Color(0xFF15100C)
val AmoledSurfContainerHigh = Color(0xFF1F1914)
val AmoledSurfContainerHighest = Color(0xFF2B231C)

// ─── AMOLED semantic tokens ───────────────────────────────────────────────────
// AMOLED reuses the dark *Material* roles, but the semantic money colors were
// never tuned for a true-black background: the dark-mode green/red are contrast-
// matched to a #211A14 surface and read washed out against #000000. These are
// lifted so income/expense keep the same perceived weight on AMOLED.

val IncomeGreenAmoled = Color(0xFF74E0A3)
val ExpenseRedAmoled = Color(0xFFFFA096)
val WarningOrangeAmoled = Color(0xFFFFC489)
val UpcomingBlueAmoled = Color(0xFFAEC8F2)
val OverdueIndigoAmoled = Color(0xFFDEB4E4)
