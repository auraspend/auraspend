# 0048 — Terracotta UI revamp (the app looks like a new app)

**Date:** 2026-10-17
**Branch:** `ui_change_test` (deliberately **not committed** — owner asked for the
working tree only)
**Tests:** 584/584 green · `:app:lintProdDebug` clean · licence + privacy checks pass

## What this session did

A ground-up visual revamp: new palette, new typefaces, a new illustrated mark,
and a pass over every screen and shared component. **No behaviour, data flow,
domain, ViewModel, MVI state or navigation route changed.** Everything below is
`ui/theme`, `ui/designsystem`, screen-layer styling, and the default *seed colour
values* of the categories.

The identity is now **terracotta clay**: warm, crafted, editorial. The old purple
("aurora") language is gone from the shipping surfaces.

| Layer | Before | After |
|---|---|---|
| Brand | purple `#5E3A8B` → lavender → teal | clay `#A64B2A` → ember `#D2764A` → sand `#F2D8BC` |
| Canvas | `#FAF7FD` / `#14101C` | `#FCF7F2` / `#17110D` (warm cream / warm near-black) |
| Secondary / tertiary | teal-ish / pink | olive-sage `#5F6B4A` / ochre `#8A6320` |
| UI font | Plus Jakarta Sans | **Manrope** (variable, OFL, real `tnum`) |
| Display font | — | **Fraunces** (variable, OFL) for display/headline/titleLarge |
| Shape scale | 4/8/12/16/28 | **10 / 14 / 20 / 26 / 32** (softer, friendlier) |

### Component work

- **`AuraMark` + `AuraMarkMedallion`** (new `ui/designsystem/AuraMark.kt`): an
  illustrated clay pot with a coin dropping into its slot, drawn with Compose
  `Canvas` paths (theme-aware, no bitmap, scales cleanly). Used by the splash,
  onboarding, empty states and the dashboard hero watermark. The feature icon
  rides as a corner badge, so every empty screen speaks one visual language
  without losing its meaning.
- **`AuraCardStyle.Hero`** (new): the one gradient "hero moment" per screen. It
  sets its own `LocalContentColor` to white; children use `AuraGradients.onAurora`.
  Applied to Dashboard (balance), Transaction detail, Plan (budget health),
  Budget, Savings (total), Recurring (monthly).
- **`AuraSegmentedControl`**: spring-animated gradient indicator, haptic tick.
- **`CategoryAvatar`**: rounded square (30% radius) with a tinted tonal fill.
- **`AuraSpendScaffold`**: floating nav bar with a gradient pill on the selected
  item and a squircle centre FAB; warm-tinted chrome shadows.
- **`Chrome.kt` / `SettingsScreen`**: a **terracotta tick** before every section
  header — the app's signature detail, in both the shared component and Settings.
- **`Feedback.kt`**: empty states use the medallion + icon badge.
- **`AuraTokens`**: `auroraSoft` / `sunrise` / `heroWash` gradients, `onAurora`,
  and a new `keypadDigit` style (see the defects below).

### Screens touched

Splash · Onboarding (mark per page, gradient page indicator) · Dashboard ·
Activity/Transaction list · Transaction detail · Plan hub · Budget · Savings ·
Recurring · Insights · Smart Add/Classification · Settings · Quick Add ·
App lock/PIN pad · Category management (chips/swatches only) · License screen
(attributions).

## Decisions worth recording

1. **The `Aurora*` symbol names stay.** The owner's call: source stability beats
   renaming a token in 40 files. "Aurora" is the design system's name; terracotta
   is its palette. `docs/design/aurora.md` says so at the top.
2. **Fraunces is display-only, money is always Manrope.** Fraunces has no `tnum`
   feature, and tabular figures are a correctness requirement here (animated
   money must not jitter). `AuraType.money*` all pin Manrope + `tnum`.
3. **One hero per screen.** The gradient is reserved for a single surface; the
   Insights summary deliberately stays tonal because its red/green income-expense
   semantics have to survive, and white-on-gradient would destroy them.
4. **Default category colours were retuned** (`DefaultCategories.kt`). The old
   values were the 2014-era Material 500s (`#E53935`, `#1E88E5`, `#8E24AA`) and
   they were the loudest, least on-brand pixels in the app — every Activity row,
   filter chip and donut segment carried one. The new set keeps the same hue
   families (so "red is food" still reads) ~30 % down in saturation. **Only new
   installs are affected**: `missingDefaultCategories` never rewrites a row that
   already exists, and no migration touches stored colours. Revert is a one-file
   diff if the owner disagrees.
5. **Font swap = licence swap.** Plus Jakarta Sans is gone; Manrope + Fraunces
   (both OFL) are bundled. `docs/licenses/OFL-*.txt`, `docs/licenses/README.md`,
   the in-app License screen, `tools/check_license.sh` and `README.md` were all
   updated together — `check_license.sh` fails otherwise.
6. **The launcher icon, the website and the store assets were NOT touched.** They
   are separate brand surfaces and a half-recoloured icon is worse than a
   consistent old one. Tracked as follow-ups below.

## Defects found by actually looking at the app

Each of these passed the full JVM suite, lint and the static checks:

1. **The selected nav label was white-on-cream.** The gradient pill only wraps the
   *icon*, so tinting both icon and label `onAurora` left the selected label
   invisible on the bar's own surface. Icon and label now animate separately.
2. **Keypad digits were rendering in the display serif.** `QuickAddSheet` and
   `PinPad` used `MaterialTheme.typography.headlineSmall`, which is Fraunces —
   and Fraunces uses **old-style (text) figures**, so a 0–9 pad had uneven digit
   heights and read as a gimmick. New `AuraType.keypadDigit` (Manrope, `tnum`).
3. **The old purple was still the OS accent.** `res/values/colors.xml`
   (`aura_primary`, `aura_window_background`) drives the pre-Compose window and
   `android:colorAccent`, which tints **system dialogs** — the notification
   permission prompt was still showing the old brand. Now terracotta.
4. **`tools/seed_demo_data.sh` had silently never seeded anything.** The seeder
   is opt-in (`Assume.assumeTrue` on `-e seedDemoData true`) and the script never
   passed the flag; a skipped test still reports `OK (1 test)`, so the script
   printed "seeded" against an empty database. One-line fix + the script's success
   path now means what it says.

## Verification

Commands and results, all on this tree:

| Command | Result |
|---|---|
| `./gradlew :app:compileProdDebugKotlin` | clean |
| `./gradlew testProdDebugUnitTest` | **584 tests, 0 failures, 1 skipped** |
| `./gradlew :app:lintProdDebug` | clean (baseline: one stale `app_icon.png` row removed) |
| `tools/check_license.sh` | "Licence metadata is consistent." |
| `tools/check_privacy_claims.sh` | "every absolute claim states its exception." |

**On-device pass** (arm64 `Android_17` AVD, `dev` debug build, demo data seeded):

- Splash, all four onboarding pages (including the currency page), the
  notification-permission dialog (accent now terracotta), the empty dashboard,
  the seeded dashboard, Activity with data, Plan, Insights, Settings, transaction
  detail, Savings goals, the Quick Add sheet (keypad), Smart Add — captured in
  **light and dark**.
- Verified specifically: money stays tabular Manrope everywhere; the hero gradient
  carries white text legibly; section ticks appear on Activity/Insights/Settings;
  the nav pill + FAB read correctly; the medallion + badge holds at 96 dp.

Not walked on device this session (unchanged code paths, but visual changes
apply): the app-lock PIN screen, Subscriptions/Recurring, Category management,
the License screen, Unrecognized SMS, the transaction editor, AMOLED mode,
200 % font scale and Hindi.

## Follow-ups (deliberately not in this change)

1. **Launcher icon.** Still the teal/purple wallet with the template-green adaptive
   background. It should be regenerated from `AuraMark` (adaptive foreground +
   monochrome + legacy mipmaps + the Play 512×512).
2. **Website + store assets.** `docs/index.html`, `docs/css/style.css`,
   `docs/fonts/plus-jakarta-sans.ttf`, `docs/store/*` and `docs/screenshots/*`
   still carry the purple brand and Plus Jakarta Sans. The store feature graphic
   was repointed at the site's own font copy so it keeps rendering as designed.
3. **AMOLED + 200 % font scale + Hindi** visual pass.
4. `docs/design/aurora.md` was rewritten for the terracotta language — worth a
   read for anything that disagrees with the implementation.

## Notes for the next agent

- **Do not rename the `Aurora*` tokens.** They are intentionally "wrong" names for
  stable source.
- **Do not move money into Fraunces.** No `tnum`, and the animated counters need it.
- `AuraCardStyle.Hero` is exhaustive-safe today (no `when` outside `AuraCard.kt`),
  but any new `when` over the style must handle it.
- `tools/seed_demo_data.sh` is the fast path to a realistic device UI; it requires
  the `-e seedDemoData true` argument to do anything at all.
