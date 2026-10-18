# 0058 — Quiet Ledger, implemented

**Date:** 2026-10-18
**Branch:** `ui_enhance` (not committed, not pushed — the owner asked for the
working tree)
**Scope:** the design in `docs/design/interface-concept/` built for real, on top
of the existing components and state. **No behaviour, query, classifier or
storage logic was changed.**

## What this is

The 0057 concept, ported into Compose. The palette, the chrome, the Home
hierarchy, the Activity filters, Plan's bars, Insights' ranked list and the
transaction row's amount colour are all in `app/`; the prototype stays as the
reference for the intent.

## The five rules, as implemented

1. **Chrome is monochrome, data is colour.** `Color.kt` is now warm paper + ink
   with one violet accent; red is a status colour only.
2. **Type does the work gradients used to.** The gradient hero is gone;
   `AuraType.moneyHero` is 44 sp at −0.9 sp tracking. `AuraGradients` survives
   for the splash and onboarding CTA, retuned to a violet ramp (the teal end
   fought every category colour and the hero it was designed for no longer
   exists).
3. **Hairlines, never shadows** — unchanged, and the nav bar now uses one.
4. **One hero per screen, always a number.**
5. **Nothing tappable above thumb reach** except back and titles.

## Changed, file by file

| File | Change |
|---|---|
| `ui/theme/Color.kt` | Whole palette. Warm paper `#F7F5F0` / ink `#17160F`; violet `#4A2E7A`; income `#1C6B4A`; status brick `#A8402F`; tertiary becomes amber (the 80 % budget / confidence / upcoming role) |
| `ui/theme/Theme.kt` | Error containers from the same palette (M3's default crimson clashed with brick); `UpcomingBlue`/`OverdueIndigo` renamed to `…Light` |
| `ui/designsystem/AuraTokens.kt` | `moneyHero` 40 → 44 sp, tracking −0.9 |
| `ui/designsystem/Charts.kt` | New `AuraProgressBar` — the comparison primitive, with `progressBarRangeInfo` for TalkBack. `AuraProgressRing`/`AuraDonutChart` kept (BudgetScreen still uses the ring) |
| `ui/designsystem/TransactionRow.kt` | **Expense amounts are ink, income green.** Avatar 46 → 36 dp, ring removed, hairline divider between rows, new `divider` parameter |
| `ui/core/AuraSpendScaffold.kt` | Flat bar with a hairline top edge (was a floating rounded pill), ink squircle FAB (was the brand sweep), selection by colour instead of a pill, nav label ellipsis (the one-liner 0056 left open) |
| `ui/home/DashboardScreen.kt` | Reordered around the glance: balance + spend-vs-income bar → "needs you" rail → one budget aggregate → recent → cash flow. Quick-action row removed (Stats was the Insights tab, Add was the FAB); the budget carousel became one line |
| `ui/transaction/TransactionListScreen.kt` | Search always visible (was behind a magnifier); type + category filters merged into one scrolling chip row; "All" now clears both |
| `ui/transaction/QuickAddSheet.kt` | The amount being typed is ink, not red |
| `ui/transaction/TransactionDetailScreen.kt` | Same amount rule |
| `ui/plan/PlanHubScreen.kt` | Ring hero → number + bar; four hub-of-link rows → per-category bars, a goals rail, and one recurring-cost row; loads categories for the names |
| `ui/analytics/AnalyticsScreen.kt` | Donut + legend → ranked bars (`CategoryRankRow`); expense amounts ink |
| `ui/designsystem/TransactionRow.kt` | `formatRelativeDate` now counts **calendar days**, not elapsed hours (see "Found on the Moto") |
| `res/values/colors.xml` | Launch-window colours synced to the new palette (`#FAF7FD`→`#F7F5F0`, `#14101C`→`#0F0F0D`, `#5E3A8B`→`#4A2E7A`); the file's own comment requires this |
| `res/values{,-hi}/strings.xml` | 14 resources deleted — every one orphaned by the changes above (verified zero Kotlin references first) |
| `docs/design/aurora.md` | §1/§2/§5 updated: the palette, the expense rule, the hero rule, and `AuraProgressBar` + "ranked bars, never a donut" |

## Deliberate deviations from the prototype

- **The cash-flow chart stayed on Home**, below Recent, instead of moving to
  Insights. It answers a slower question, and deleting a data view is a product
  decision, not a layout one. Moving it down keeps the glance intact.
- **Settings was not restructured.** It already groups (APPEARANCE / SMART
  CATEGORIES / DATA) and the new tokens restyle it; rewriting 943 lines of
  working settings for a header style was not worth the risk.
- **No new string resources.** Everything reuses existing copy, so no locale
  work was needed — which is also why 14 strings had to be deleted rather than
  left unreferenced (lint's `UnusedResources` is an error here).

## Verification

- `:app:compileProdDebugKotlin` and `:app:compileDevDebugKotlin` — zero `w:` lines.
- `testProdDebugUnitTest` — **616/616, 1 skipped**, identical to the 0056/0057
  baseline. Includes `DesignSystemGuardTest` (no literal font sizes, no raw hex
  in screens, no `.shadow(`, no emoji, no fixed height around text) and
  `ThemeContentColorTest` in all three modes.
- `:app:lintProdDebug` — 1 hint, no errors. (One baseline entry no longer
  matches because its string was deleted; lint still passes.)
- `tools/check_license.sh` and `tools/check_privacy_claims.sh` — clean.
- **On the arm64 AVD**, dev flavour with the seeded demo data, light and dark:
  Home, Activity, Plan, Insights, Quick Add, Settings. Screenshots in
  `docs/design/interface-concept/implemented-*.png`. The device's
  `theme_mode` was switched to DARK for the dark pass and **restored to LIGHT**;
  `cmd uimode night` was returned to `no`.

## Verified on the owner's Moto G40 Fusion (Android 12, 1080×2460 @ 400 dpi)

Installed the **dev** flavour only (`com.awbuilds.auraspend.dev`, `install -r`).
The release package `com.awbuilds.auraspend` (0.1.7, versionCode 9, the owner's
real data) was **not touched** — not installed over, not uninstalled. The dev
database was byte-identical before and after (`98 KB` main + `391 KB` WAL).

Walked Home, Activity, Plan, Insights, Quick Add and Settings in dark and in
light. Screenshots: `docs/design/interface-concept/implemented/moto-*.png`.
`logcat -b crash` was empty across the whole walk.

**Two defects found on the phone, both fixed:**

1. **Rows said "Today" under a "Yesterday" header.** `formatRelativeDate` used
   elapsed hours (`diff < 24h → "Today"`), so a 22:30 purchase read as "Today"
   at 02:35 — while the sticky day header above it, which groups by local date,
   said "Yesterday". Now counts calendar days in the device zone, so the row and
   its header can never disagree. No test covered the helper.
2. **The system navigation strip stayed dark under a light app.** Caused by
   `res/values/colors.xml` still holding the old violet-cream window background;
   the file's own comment says to keep it in sync with `Color.kt`. Fixed.

**Device state restored:** `theme_mode` is back to `SYSTEM` (it was only flipped
to `LIGHT`/`DARK` for the captures) and `cmd uimode night` is back to `yes`,
which is how the phone was found.

## Known gaps

- The "needs you" rail has two honest sources only: the worst over-budget
  category and the subscription count. The prototype's "3 messages to review"
  card would need the review-queue count threaded into `DashboardViewState`.
- `PlanHubScreen` now collects `getAllCategories()` — one more flow on a screen
  that already collects six. Fine for now; worth folding into a ViewModel if the
  hub grows.
- The emulator's locale is en-US, so the emulator screenshots show `$` with two
  decimals; the Moto is en-IN and shows `₹33,467` with none. Both paths were
  checked, and the INR one is the tighter of the two.
- **Quick Add needs a scroll to reach Save on the Moto.** The sheet (segmented +
  amount + 4-row keypad + merchant + chips + date + Save + two text buttons) is
  taller than the ~615 dp viewport, so Save sits below the fold. Pre-existing,
  not introduced here, and the sheet does scroll — but it is the weakest moment
  in an otherwise glanceable flow, and the fix is to move the merchant/date row
  below the chips and pin Save, as the 0057 prototype does.
