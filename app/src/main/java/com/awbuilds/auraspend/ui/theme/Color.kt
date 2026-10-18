package com.awbuilds.auraspend.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Quiet Ledger palette ─────────────────────────────────────────────────────
// AuraSpend's identity: warm paper, near-black ink, and one violet accent that
// is spent only on the thing you can act on. Category colours carry the data;
// red is a *status* colour (over budget, failure), not the colour of spending.
//
// Symbol names keep the `Aurora*` prefix on purpose — they are referenced from
// AuraTokens and the onboarding/splash brand blocks, and renaming them buys
// nothing but a wider diff. Only the values changed.

/** Core brand colors. */
val AuroraPurple = Color(0xFF4A2E7A)      // brand primary (icon background)
val AuroraLavender = Color(0xFFC9B3F2)    // brand accent
val AuroraLavenderLight = Color(0xFFE4D9F7)
val AuroraInk = Color(0xFF0F0F0D)         // dark canvas
val AuroraCream = Color(0xFFF7F5F0)       // light canvas — warm paper, not violet

// ─── Quiet Ledger surface tokens ──────────────────────────────────────────────
// Three roles the Material scheme does not name, and which the concept uses on
// every screen. They are *not* interchangeable with the nearest M3 role: a
// progress-bar track (#E8E3D6) is deliberately a shade darker than a row
// hairline (#EFEBE0), and "faint" (the eyebrow/axis grey) is lighter than
// `outline`, which has to stay legible as a border.

/** Hairline between rows inside a card. Lighter than a card border. */
val LineSoftLight = Color(0xFFEFEBE0)
val LineSoftDark = Color(0xFF22211D)

/** Progress-bar and spark-column track. */
val TrackLight = Color(0xFFE8E3D6)
val TrackDark = Color(0xFF26251F)

/** Eyebrows, axis labels and rank percentages — quieter than `muted`. */
val FaintLight = Color(0xFF918C7D)
val FaintDark = Color(0xFF78746A)

/** Chip and secondary-label ink: one step up from `muted`, one below `ink`. */
val InkSoftLight = Color(0xFF3B382E)
val InkSoftDark = Color(0xFFD6D2C6)

// ─── Semantic amounts (income / expense / status) ─────────────────────────────
val IncomeGreenLight = Color(0xFF1C6B4A)
val ExpenseRedLight = Color(0xFFA8402F)
val UpcomingBlueLight = Color(0xFF3D6E9E)
val OverdueIndigoLight = Color(0xFF5A4B8A)
val WarningOrangeLight = Color(0xFFA66A1E)

val IncomeGreenDark = Color(0xFF7BCBA2)
val ExpenseRedDark = Color(0xFFE89A8A)
val UpcomingBlueDark = Color(0xFF8FB6D9)
val OverdueIndigoDark = Color(0xFFB9A8E0)
val WarningOrangeDark = Color(0xFFE5B072)

// ─── Light scheme ─────────────────────────────────────────────────────────────
val LightPrimary = AuroraPurple
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEDE6F8)
val LightOnPrimaryContainer = Color(0xFF241636)

val LightSecondary = Color(0xFF5F5A4E)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEDE8DC)
val LightOnSecondaryContainer = Color(0xFF1E1B14)

// Tertiary is the "caution" role: 80–99 % of a budget, medium classification
// confidence, an upcoming recurring charge. Amber, never a second green.
val LightTertiary = Color(0xFFA66A1E)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF7E7CE)
val LightOnTertiaryContainer = Color(0xFF2B1A05)

val LightBackground = AuroraCream
val LightOnBackground = Color(0xFF17160F)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF17160F)
val LightSurfaceVariant = Color(0xFFEFEBE0)
val LightOnSurfaceVariant = Color(0xFF6E6A5C)
val LightOutline = Color(0xFF8A8578)
val LightOutlineVariant = Color(0xFFE3DED1)
val LightError = Color(0xFFA8402F)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF7E3DE)
val LightOnErrorContainer = Color(0xFF3D1109)

// Surface container roles (light) — warm neutrals, no violet cast.
val LightSurfContainerLowest = Color(0xFFFFFFFF)
val LightSurfContainerLow = Color(0xFFFAF8F3)
val LightSurfContainer = Color(0xFFF1EDE4)
val LightSurfContainerHigh = Color(0xFFEAE5D9)
val LightSurfContainerHighest = Color(0xFFE3DED1)

// ─── Dark scheme ──────────────────────────────────────────────────────────────
val DarkPrimary = Color(0xFFC9B3F2)
val DarkOnPrimary = Color(0xFF2A1B47)
val DarkPrimaryContainer = Color(0xFF241C33)
val DarkOnPrimaryContainer = Color(0xFFE7DBFA)

val DarkSecondary = Color(0xFFCFC9BB)
val DarkOnSecondary = Color(0xFF33302A)
val DarkSecondaryContainer = Color(0xFF46433A)
val DarkOnSecondaryContainer = Color(0xFFEBE5D8)

val DarkTertiary = Color(0xFFE5B072)
val DarkOnTertiary = Color(0xFF3D2506)
val DarkTertiaryContainer = Color(0xFF57380F)
val DarkOnTertiaryContainer = Color(0xFFF8DFC0)

val DarkBackground = AuroraInk
val DarkOnBackground = Color(0xFFF4F1E8)
val DarkSurface = Color(0xFF181815)
val DarkOnSurface = Color(0xFFF4F1E8)
val DarkSurfaceVariant = Color(0xFF22211D)
val DarkOnSurfaceVariant = Color(0xFF9C978A)
val DarkOutline = Color(0xFF6B675C)
val DarkOutlineVariant = Color(0xFF2B2A25)
val DarkError = Color(0xFFE89A8A)
val DarkOnError = Color(0xFF4A1409)
val DarkErrorContainer = Color(0xFF2E1C18)
val DarkOnErrorContainer = Color(0xFFF8D9D2)

// Surface container roles (dark) — warm ink, not violet.
val DarkSurfContainerLowest = Color(0xFF0A0A09)
val DarkSurfContainerLow = Color(0xFF121210)
val DarkSurfContainer = Color(0xFF1A1A17)
val DarkSurfContainerHigh = Color(0xFF24231F)
val DarkSurfContainerHighest = Color(0xFF2E2D28)

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
val AmoledOnBackground = Color(0xFFF4F1E8)
val AmoledSurface = Color(0xFF0A0A09)
val AmoledOnSurface = Color(0xFFF4F1E8)
val AmoledSurfaceVariant = Color(0xFF141412)
val AmoledOnSurfaceVariant = Color(0xFF9C978A)
val AmoledOutline = Color(0xFF6B675C)
val AmoledOutlineVariant = Color(0xFF24231F)
val AmoledError = Color(0xFFF0A79A)
val AmoledOnError = Color(0xFF3D1109)
val AmoledErrorContainer = Color(0xFF4A170F)
val AmoledOnErrorContainer = Color(0xFFF8D9D2)

val AmoledSurfContainerLowest = Color(0xFF000000)
val AmoledSurfContainerLow = Color(0xFF080807)
val AmoledSurfContainer = Color(0xFF101010)
val AmoledSurfContainerHigh = Color(0xFF181816)
val AmoledSurfContainerHighest = Color(0xFF22211D)

// ─── AMOLED semantic tokens ───────────────────────────────────────────────────
// AMOLED reuses the dark *Material* roles, but the semantic money colors were
// never tuned for a true-black background: the dark-mode green/red are contrast-
// matched to a #121212 surface and read washed out against #000000. These are
// lifted so income/expense keep the same perceived weight on AMOLED.

val IncomeGreenAmoled = Color(0xFF8FE0B6)
val ExpenseRedAmoled = Color(0xFFFFA99A)
val WarningOrangeAmoled = Color(0xFFF5C08A)
val UpcomingBlueAmoled = Color(0xFFA9C8E8)
val OverdueIndigoAmoled = Color(0xFFC8BAF0)
