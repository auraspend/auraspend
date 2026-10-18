# 0059 — Quiet Ledger: prototype-fidelity pass

**Date:** 2026-10-18
**Branch:** `ui_enhance` — **working tree only, not committed** (owner's
instruction this session: nothing committed, pushed or merged).
**Scope:** `app/` only. No behaviour change beyond the items below; no schema,
no classifier, no dependency, no release.

## What this is

0058's build was compared, screen by screen, against the 0057 prototype
(`docs/design/interface-concept/index.html`) — first from the committed
screenshots, then on the Moto. The committed `implemented/moto-*.png` captures
predate the last edits of 0058 (source files were modified 02:44–02:54, the
captures were taken 02:40), so they were not a fair basis: every comparison
here was redone against a freshly built dev APK.

## Closed gaps

**One chip control everywhere — new `AuraChip` (design system).**
32 dp pill in a 48 dp touch target; unselected = surface + hairline; selected =
ink fill with the text inverted; an optional 7 dp category dot that gains a halo
on the ink fill. It replaced M3 `FilterChip` in the Activity filter row, Quick
Add, the transaction editor, the classification review tab, and the
budget/subscription pickers. The M3 chip's `secondaryContainer` fill was the one
selected state that did not follow the concept's ink rule.

**Quick Add — the gap 0058 itself flagged.**
- Order is the concept's: type → amount → caption → keypad → chips →
  merchant + date → Save → Smart Add | Manual.
- The sheet opens **expanded** (`rememberModalBottomSheetState(skipPartiallyExpanded = true)`).
  It used to open at the half-height peek, so Save sat below the fold even
  though the whole sheet fits — verified fixed on-device in both themes.
- Merchant field and date button share one compact row; the date reads "Today"
  until the picker moves it, then "d MMM".
- The amount caption is `AuraEyebrow` (uppercase micro-label).

**Activity.**
- Sub-line is "October · ₹44,663 out" (browsed month + its outgoing total);
  the record-count line is gone.
- The In/Out/Net summary pills are gone — the sub-line, Home and Insights carry
  that information.
- One chip row (All / Outgoing / Incoming | ● categories).
- Day headers are uppercase eyebrows; the day net stays on the right.
- Row meta is the **time of day** ("Food & Dining · 10:32 pm"), not a relative
  date: the sticky header already names the day, and "Yesterday" under
  "YESTERDAY" was noise. `formatRelativeDate` and its strings were deleted with
  it (the `activity_days_ago` plural and the "N records" plurals).

**Home.**
- The rail's over-budget card reads as a sentence ("Shopping is over" /
  "₹13,797 over your ₹3,000 budget"); its second card is the review queue
  ("N messages to review" / "Not sure where they belong"), fed by
  `unrecognized_sms` through `DashboardViewModel` and opening
  `Routes.UNRECOGNIZED_SMS`. The subscriptions card left Home; subscriptions
  live on Plan.
- "Recent" became "Today" (today's rows, time meta) with a quiet "Nothing
  recorded today" when the day is empty — the row meta is a time, which is only
  honest under a single day.
- Flow captions lost their +/− signs; the dot and the word carry direction.
- The cash-flow area chart is off Home (analysis is a deliberate visit); the
  daily trend now lives on Insights as a column strip.

**Plan.**
- The hero's over-budget line names the categories: "2 categories are over —
  Grocery, Shopping" (the two numbers above already say by how much).
- Over-budget rows show the true percentage on the right (121 % / 559 %) where
  they showed "of ₹6,000".
- Goal cards read "₹1,85,000 of ₹3,00,000" on one line, with "₹1,15,000 to go"
  in the sub-row instead of repeating the target.

**Insights.**
- Period labels are the concept's ("This month / Last 30 days / All time").
- The category ranking stops at six rows and sums the tail: "4 more categories
  — ₹6,362".
- New **"Daily spending"** section: one column per day of the period
  (`AuraSparkColumns` in the design system; the last column in ink, track
  columns behind it), "N days" in the header, first/middle/last date labels.
  Hidden under "All time", where a column per day is not a glance.
- Top merchants are a plain ranked list with rank numbers, not a card.

**Settings.**
- Groups are the concept's five: **Money** (Currency, Categories,
  Subscriptions, Budgets) → **Messages** (SMS access, Review queue, Local AI,
  Auto-categorize, Notifications) → **Appearance** (Theme, Dynamic colour, App
  lock) → **Data** → **About**. Security folded into Appearance.
- The theme's four preview tiles moved into a bottom sheet (the currency
  picker's pattern); the row shows the current value.
- New **Review queue** row with the live count, opening the unrecognised
  messages screen (same source as Home's rail).
- Retired strings: `settings_intelligent_features`, `settings_finance`,
  `settings_security`, `common_amount_plus`, `common_amount_minus`.

**Design system.**
- `AuraType.chipLabel` (12 sp/600) and `AuraSparkColumns` added.
- `TransactionEntryRow`'s meta is the row's local time, resolved through the
  locale (`DateTimeFormatter.ofLocalizedTime(SHORT)`).

## Kept on purpose (deviations from the prototype)

- **Activity's month navigation and sort toggle stay.** The prototype draws one
  unbounded list; the app's month browsing predates it and is a feature, not
  chrome.
- **Settings rows keep their second line** where it carries state ("Not granted
  — automatic categorisation is paused"). The concept's rule is about rows that
  merely describe; these report.
- **Home keeps the hide-amounts eye** in the header (a privacy affordance the
  prototype does not model).
- **The flow bar turns brick past 100 % of income** — the concept's own rule
  (red means something is wrong) applied to the data the demo device has.

## Verification

- `:app:compileDevDebugKotlin` / `:app:compileProdDebugKotlin` — clean (the
  flaky-line "Daemon compilation failed: null" the Gradle/Kotlin daemon prints
  on this machine is followed by `BUILD SUCCESSFUL`; it did not reproduce as a
  real error).
- `testProdDebugUnitTest` — **614/614, 1 skipped** (616 minus the 3 tests for
  the deleted `AuraChip` attempt and the 2 for the removed `chartWindow` helper,
  plus nothing added).
- `:app:lintProdDebug` — clean; 1 hint, 0 new errors. The first run failed on
  `common_amount_plus`/`common_amount_minus` becoming unused; both are deleted
  (with `UnusedResources` as the tripwire it is).
- `tools/check_license.sh` / `tools/check_privacy_claims.sh` — pass.
- Strings: 545 keys in each locale, zero duplicates, exact EN/HI parity (a
  scripted diff, prompted by the `settings_finance_removed_marker` mistake this
  session made while editing the file: a key was "deleted" by renaming it to an
  empty string. The parity script caught it; do not edit these files by
  intermediate states).
- **On the Moto (dev flavour), dark and light, with the seeded demo data:**
  Home (rail sentence, unsigned captions, Today section, budget card), Activity
  (sub-line, ink-selected "All" chip, dot chips, "FRI, 16 OCT" headers,
  "10:32 pm" metas), Plan (over-line names, 121 %/559 %, goals), Insights
  (period row, 6-row cap with "4 more categories", the daily strip), Settings
  (Money/Messages/Appearance/Data/About, "Review queue — 0 waiting"), and the
  Quick Add sheet (chips under the keypad, merchant + Today row, Save visible
  without a scroll). Screenshots under
  `docs/design/interface-concept/` were not regenerated — the captures used
  here are in the session's scratch directory.

## Open

- The phone currently runs the APK from before the "−₹7,467 left" fix (the
  Home budget card hides the "left" cell when the month is over instead of
  showing a negative number) — rebuild/reinstall to confirm.
- The theme sheet (Settings → Appearance → Theme) was not tapped through on the
  device; the row and sheet compile and are wired.
- The prototype's "3 messages to review" rail card cannot be seen with the demo
  data (`unrecognized_sms` is empty); Settings' "Review queue — 0 waiting" is
  the visible half of the same wiring.
