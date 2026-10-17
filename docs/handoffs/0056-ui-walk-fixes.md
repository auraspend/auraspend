# 0056 — On-device UI walk: category chips and honest budget percentages

**Date:** 2026-10-17
**Branch:** `fix/ui-walk-fixes` (off `dev`)
**No release.** 0.1.7 stays the shipped build; this is a UI pass on `dev`.

## What the walk was

Every reachable page walked on the dev build with the seeder's dummy data, on
the owner's device rather than an emulator: Home (top and bottom), Activity,
Plan, Insights, Subscriptions, Savings goals, Manage categories, Settings,
transaction detail, transaction editor, Quick Add (all three tabs), in dark and
light themes, plus the Plan hub at **200 % font scale**. The owner's report —
"the circles around the category … feels off" — was the Quick Add category
picker.

## Fixed

### 1. Quick Add's category picker is now the app's standard chip

The old control was screen-local: an `AuraCard` with `width(96.dp)` under a
`CircleShape`, plus a second `.background()` painting over the card's own
Tonal/Filled state. On the device that produced three separate defects: labels
taller than the 96 dp box ("Food & Dining", "Bills & Utilities" overflowed the
pill), a selected chip indistinguishable from an unselected one (its entire
selected-state signal was painted over), and no selected semantics for
assistive tech.

A hand-rolled replacement (`AuraCategoryChip`, deleted) was written first and
was *also* wrong — a two-line avatar-over-label `Column` under `CircleShape`
makes ~61 dp-tall ovals whose semicircular ends crowd the label, which the 2×
device crop showed plainly. It read as "circles", i.e. exactly the complaint.

The final fix is the control the app already had in two other places:
**`FilterChip` with a `CategoryAvatar(size = 22.dp)` leading icon**, identical
to `TransactionListScreen`'s filter row and `TransactionEditor`'s picker.
`categoryColor()` (`ui/designsystem/TransactionRow.kt`) supplies the avatar
tint. All three category pickers are now one control; the bespoke component and
its tests were deleted rather than kept — the standard control already carries
the sizing, the touch target and the selected semantics.

### 2. Budget percentages stop lying

`PlanHubScreen` computed `(totalSpent / totalLimit).coerceIn(0f, 1f)` and used
it for **both** the ring and the text, so ₹33,467 against a ₹26,000 budget
rendered as "₹33,467 of ₹26,000 — 100 % used": two numbers on one card that
visibly disagree, with no hint of the overspend and a purple ring.

- The hub hero now uses the **raw** ratio. `AuraProgressRing` clamps its own
  sweep, so the arc still caps at a full circle while the number reads **128 %**.
  When over, the supporting line becomes **"Over by ₹7,467"** in error colour
  (same wording as the per-budget rows) and the ring goes error ≥100 %,
  tertiary ≥80 %, matching `BudgetScreen`'s own thresholds.
- `BudgetScreen`'s two ring labels (overall and per-row) were clamped the same
  way; both now show the true number (Grocery **121 %**, Shopping **559 %**) —
  the rows' "Over by" lines already said by how much.
- Home's budget-chip `contentDescription` announced "…100 percent used" for an
  over-budget budget; it now announces the true percent.

### 3. The Insights saving badge takes its minus from the money helper

"Saving **-98 %**" used the platform hyphen while "Net this month **−₹32,163**"
sat directly above it in the same card with U+2212 — two different minus glyphs
next to each other read as a rendering bug. The badge now uses
`moneySign(rate)` + magnitude, and `analytics_saving_percent`'s placeholder
changed `%1$d` → `%1$s` in **both** locales so the sign travels with the
number. A rate that truncates to 0 gets no sign ("−0 %" is noise).

### 4. The Plan hub no longer clips at 200 % font scale

The screen was a plain `Column`; at 200 % the two cards and four rows exceed
the viewport and the overflow is simply unreachable. The header is now pinned
and the body scrolls. Verified on-device at 200 %: the Categories row (last)
scrolls into view.

## Investigated and deliberately not changed

- **"Subscriptions row hidden behind the bottom nav bar" — not a defect.** The
  bottom bar is a *sibling* of the content in `AuraSpendScaffold`, not an
  overlay, so content cannot sit under it; the partial card in a still is a
  mid-scroll viewport cut. Verified on-device: at max scroll the last row
  clears the bar with room to spare.
- **The wide `−` in the hero that reads like an em-dash is the font's own
  glyph.** `hb-shape --font-file=app/src/main/res/font/plus_jakarta_sans.ttf
  --variations=wght=700 "−₹9,412"` → the minus advances **660/1000 em**, wider
  than any digit; a hyphen is far shorter and sits high, which is exactly what
  `Money.kt` documents against. It is correct typography for money — **do not
  "fix" it by swapping the character.**
- **M3 `FilterChip` has no avatar ring for selection.** The app's chips signal
  selection by container fill; verified legible in both themes (`All` selected
  vs the category chips unselected in the Activity row). No ring needed.

## Known limitation (recorded, not fixed)

Three-digit percentages inside the 64 dp / 72 dp rings crowd the ring's edge at
≥150 % font scale. Pre-existing in kind — the old clamped "100 %" was the same
four glyphs — and invisible at default scale. The same pass showed the nav-bar
labels clip without an ellipsis at 200 %; a one-line candidate
(`TextOverflow.Ellipsis`) is left for a future pass.

## Verification

- `./gradlew :app:compileProdDebugKotlin --rerun-tasks --no-build-cache` —
  zero `w:` lines.
- `./gradlew testProdDebugUnitTest` — **616/616, 1 skipped** (the 0.1.7 count;
  the three tests written for the deleted hand-rolled chip went with it).
- `./gradlew :app:lintProdDebug` — clean; 1 hint, none new against the
  baseline.
- On-device (dev flavor, seeded dummy data, 400 dpi 1080×2460):
  - Plan hub: "Over by ₹7,467" + error ring reading **128 %**
    (33,467 / 26,000 = 128.7).
  - Budgets: overall **128 %** (was 100), Food & Dining 61 % (4,928/8,000),
    Grocery **121 %** + "Over by ₹1,299" (7,299/6,000), Transport 55 %
    (2,795/5,000), Shopping **559 %** + "Over by ₹13,797" (16,797/3,000).
  - Insights: "Saving **−98 %**" with the typographic minus matching the amount
    above.
  - Quick Add: Food & Dining (selected, filled) / Transport / Shopping …,
    labels fully visible, in dark and light.
  - Plan hub at 200 % font scale: scrolls to the last row; font scale reverted
    to 1.0 and the app theme restored to System afterwards.
- No classification, storage, or release path was touched.

## Next

- Nothing blocks. When the terracotta branch lands, re-check these layouts
  against the new palette (the chips are M3 defaults, so they follow it).
- Optional one-liner: nav-bar label ellipsis at large font scales.
