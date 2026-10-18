# 0060 — Quiet Ledger: pixel-parity pass against the concept

**Date:** 2026-10-18
**Branch:** `ui_enhance` — **working tree only, not committed** (owner's instruction:
nothing committed, pushed or merged).
**Scope:** `app/` UI only — theme, design system, the six concept screens and the
Quick Add sheet. No schema, no classifier, no dependency, no release. One
pre-existing behaviour bug fixed (see "Also fixed").

## What this is

0058 built the concept and 0059 walked it screen by screen. This pass measures
instead of eyeballing: every screen was compared against
`docs/design/interface-concept/index.html` at the same physical size, and the
remaining differences were closed.

**The harness** (repeatable; all of it lives outside the repo in
`~/aura-fidelity/`):

1. The prototype is rendered by headless Chrome at exactly 390×844 CSS px with
   `--force-device-scale-factor=3` → 1170×2532 PNGs. A one-line appended script
   adds the file's own `body.embed` class (the prototype ships that mode "for
   pixel comparison against the real build") and selects the screen and theme.
2. The dev build runs on the arm64 AVD forced to **1170×2532 @ 480 dpi**, which is
   exactly 390×844 dp — so one app pixel is one prototype pixel.
3. The app's per-app locale is set to `en-IN`
   (`adb shell cmd locale set-app-locales com.awbuilds.auraspend.dev --user 0 --locales en-IN`).
   Without it the emulator's `en-US` locale renders `$`, Western grouping, `4:07 AM`
   and a hyphen-minus — four "fidelity bugs" that were really a locale mismatch.
4. Geometry was compared numerically, not by eye: the prototype's own DOM rects
   (`getBoundingClientRect` for every element inside `.scr`) against the app's
   `uiautomator dump` bounds, both in dp.

## The headline defect: the font was never bold

`PlusJakartaSans` registered **one variable TTF six times** under six `Font(weight = …)`
entries. That only tells Compose which entry to *match*; the renderer still draws
the file's default instance. Every 700 in the app was drawn at regular weight —
measured on the Activity title, the ink was 35 % lighter and 6.5 % narrower than
the prototype's. Fixed in `Theme.kt` with an explicit
`FontVariation.Settings(FontVariation.weight(n))` per entry. This single change
moves every screen, and it is the reason several 0059 "close enough" calls looked
close enough.

## Foundation

- **`AuraType` rebuilt to the concept's type scale** — sizes, weights *and*
  letter-spacing taken from the CSS (the odd values are the concept's em-relative
  tracking resolved at each size: −0.038em at 45sp is −1.71sp). New tokens for the
  roles the concept names but the app had been approximating: `screenTitle`,
  `moneyHeroSmall` (38sp — Plan and Insights were drawing the 45sp Home hero),
  `budgetName`/`budgetAmount`/`budgetSub`, `alertTitle`/`alertMeta`/`alertBadge`,
  `flowCaption`, `rankName`/`rankValue`/`rankPercent`, `axisLabel`,
  `settingsRowTitle`/`settingsRowValue`/`groupLabel`, `searchPlaceholder`,
  `goalTitle`/`goalAmount`, `deltaLabel`, `heroMeta`/`heroMetaStrong`,
  `rowLinkTitle`/`rowLinkMeta`, `amountDisplay`, `keypadDigit`, `buttonPrimary`.
- **Four colour roles the scheme did not name**: `lineSoft` (the `--line-2` row
  hairline, deliberately lighter than a card's border), `track` (progress-bar and
  spark-column fills, deliberately darker than a hairline), `faint` (eyebrows,
  axis labels, rank percentages) and `inkSoft` (chip labels). The dark
  `primaryContainer` (#241C33) and `errorContainer` (#2E1C18) were re-tuned to the
  concept's `--violet-soft`/`--brick-soft`; the old ones were saturated enough to
  read as a different design in dark mode.
- **Money.** `formatMoney`'s non-Indian path now prepends `MINUS_SIGN` instead of
  letting `NumberFormat` emit a hyphen (or accounting parentheses) — the app's own
  rule is one minus glyph everywhere. `MoneyFormattingTest` was asserting the
  hyphen and now asserts U+2212.
- **Components.** `AuraCard`'s hairline is full-opacity `outlineVariant` (55 % made
  cards read as tinted rectangles and sat under the 3:1 non-text contrast floor);
  `AuraChip` labels use `inkSoft`; `TransactionEntryRow` uses the concept's 11dp row
  padding and `lineSoft` divider; `AuraSectionHeader` lost its own vertical padding
  (10dp below only — the 26dp between blocks is the block's own top margin);
  `AuraProgressBar` defaults to the `track` token; `AuraEyebrow` defaults to `faint`.
- **`AuraSegmentedControl` rewritten** to the concept's `.segsm`: a 12dp track on a
  6 % ink wash, a 9dp pill on `surface`, 32dp segments, the chip's 12sp/600 label.
  It also had a real bug: `matchParentSize()` hands its child *fixed* constraints,
  and a fixed width silently beats `.width()`, so the "pill" painted the entire
  track. It now measures through a `wrapContentSize` slot; measured pill extent
  69–412 px against the prototype's 69–406.
- **Icons.** Category and nav glyphs switched from Material's *filled* set to the
  *outlined* set, which is what the concept draws (stroked, 1.8). Plan's tab icon
  became a bullseye (`Icons.Outlined.Adjust`) — the concept's `.nav-item` "plan"
  icon is concentric circles, not a piggy bank. The alert-rail badge is the
  concept's "!" glyph in a tinted circle, not a second circled-exclamation icon.
- **Default category colours** re-tuned to the concept's palette (`CATS` in the
  prototype). New installs and the demo seed only; an existing install keeps the
  colours already in its database.
- **The header avatar** is 38dp of art in a 48dp touch target that no longer
  stretches the header it sits in: a custom `avatarSlot` layout reports the art's
  height to the row and centres the target on it. Without it the shared target
  added 10dp to Insights' title-only head and pushed the whole screen down.
- **The hide-amounts eye is gone** from Home's header, per the owner's call, along
  with `HideAmountIconButton` and its three strings (`a11y_show_amounts`,
  `a11y_hide_amounts`, `state_hidden_amount`). Recoverable from `HEAD` if wanted.

## Screens

- **Home** — head 6/22dp with a 5dp eyebrow→title gap; hero 45sp with the concept's
  −1.71sp tracking; 18dp to the flow bar, 9dp to the captions (dot 6dp, gap 6dp,
  `faint`); the rail's 22dp top margin and 10dp gaps; the budget card on
  `budgetName`/`budgetAmount` with the concept's 15/16dp padding and 10/8dp bar
  margins; the balance block's stray 8dp bottom padding removed (it pushed the
  whole rail down); section headers at 26dp.
- **Activity** (delegated) — flat 42dp search pill instead of a 56dp
  `OutlinedTextField`; chip gaps 7dp; day headers as 10.5/700 uppercase `faint`;
  row meta at 11.5/400; and the list's extra 20dp `contentPadding` dropped, which
  had been double-guttering every avatar and hairline to 40dp.
- **Plan** (delegated) — hero at `moneyHeroSmall`, always-ink bar on the `track`
  token, real percentages (`roundToInt`, never a capped 100 %), budget rows on the
  concept's metrics, the goals rail edge-to-edge at 196dp/10dp, the subscriptions
  row-link card rebuilt.
- **Insights** (delegated) — the period selector at the concept's geometry, hero at
  `moneyHeroSmall`, delta pill rebuilt, baseline-aligned rank rows with the tail
  sum, the daily strip at 86dp with `axisLabel` dates, merchant rows with 36dp
  rank badges. "Where it went" gained the missing "View all" link.
- **Settings** (delegated) — 34dp back circle, 22dp group rhythm, 32dp/10dp glyph
  tiles tinted `faint` (not violet), one-line rows with a right-hand value, 16dp
  card padding with `lineSoft` hairlines between rows only. The inline
  "Grant Permission"/"Download categoriser" actions are now the concept's ink
  primary button.
- **Quick Add** — 44sp amount, 50dp/14dp keys on a 5 % ink wash with 7dp gaps,
  40dp meta pills for merchant and date (a Material text field's 56dp floor was
  what pushed Save under the fold), an ink Save, and the concept's 12sp violet
  footer.

## Kept on purpose (owner's call, asked and answered this session)

The owner was shown every control the prototype does not draw and chose to remove
**only** the hide-amounts eye. Everything else is kept and restyled into the
concept's language: Activity's month-navigation row, its sort toggle and its
per-day net totals; Settings' descriptive second lines and its inline actions and
toggles; the flow bar turning brick past 100 % of income (the concept's own "red
means something is wrong" rule, applied to data the demo device has).

## Known platform difference (not compensated, deliberately)

**Compose floors a line box at the font's natural height (1.26 em for Plus Jakarta
Sans); the browser's `normal` is ~1.15 em.** A token's `lineHeight` cannot go below
that floor, so each line of text is 1–4 dp taller than the prototype's and the
difference accumulates: ~1.4 dp per small label, ~12 dp after a 45sp hero. The
measured result on Home is a 4 dp total drift by the last row, and Plan/Insights
report ~9–12 dp by the bottom of the fold. Compensating means subtracting
font-metric constants from a dozen spacers, which trades a documented platform
difference for unexplained magic numbers; the design values themselves (sizes,
weights, tracking, gaps, radii, colours) now match exactly. `Trim.Both` does not
help — trimming only removes *positive* leading.

Also kept for the accessibility floor rather than the pixel: the chip row's 48dp
touch target makes that row 14dp taller than the prototype's 34dp, the avatar's
target is 48dp around 38dp of art, and the Plan row-link card is ~5dp taller than
the concept's for the same reason.

## Also fixed

`SettingsScreen`'s PIN create/verify dialogs were nested **inside**
`if (showDeleteDataDialog)`, so the App lock row, Change PIN and the biometric
toggle did nothing at all unless the "Delete all data?" confirmation happened to
be open. Present at `HEAD`; moved out one level.

## Verification

- `:app:compileDevDebugKotlin` / `:app:compileProdDebugKotlin` — clean, no `w:`.
- `testDevDebugUnitTest` — **614 tests, 0 failures, 1 skipped** (one assertion
  updated: the negative-money test now requires U+2212, which is the rule).
- `:app:lintProdDebug` — **0 errors, 0 warnings, 1 hint** (the pre-existing
  `AutoboxingStateCreation` hint). One baselined `PluralsCandidate` entry no longer
  matches anything; the baseline was deliberately *not* regenerated, because
  `updateLintBaseline` would also baseline anything new this pass introduced.
- `tools/check_license.sh`, `tools/check_privacy_claims.sh` — pass.
- Strings: 541 keys in each locale, exact EN/HI parity, no duplicates.
- **On the emulator at 390×844 dp, dark and light, with the seeded demo data:**
  Home, Activity, Plan, Insights, Settings and the Quick Add sheet captured and
  compared against the prototype's own renders, plus a spot check of the
  transaction editor (a non-concept screen) for the shared segmented control.
  Captures live in `~/aura-fidelity/{ref,app,work}/` — `work/cmp-<screen>-<theme>.png`
  is the side-by-side.

## Open

- **Not walked on the owner's Moto.** Everything here was measured on the AVD at
  390×844 dp; the phone is 720×1600 at a different density, so the same dp values
  land on different pixels and the *rhythm* should be re-checked there.
- The theme sheet (Settings → Appearance → Theme) was not tapped through.
- The app-lock PIN flow is now reachable but was not driven end to end.
- `docs/design/interface-concept/implemented/*.png` still holds the 0058 captures;
  they predate this pass.
