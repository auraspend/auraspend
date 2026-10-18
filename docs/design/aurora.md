# Aurora Design Language

> Source of truth for AuraSpend's visual system. If a screen disagrees with this
> document, the screen is wrong.

## 1. Personality

Calm, premium, trustworthy fintech. Dark-first. Data-dense but breathable.
Nothing decorative without meaning. Two adjectives for every decision:
**quiet** and **precise**.

- No emoji in chrome (emoji only as a fallback for user-created categories).
- No drop shadows on content; depth comes from tonal surfaces + hairlines.
- **Chrome is monochrome, data is colour.** Violet marks the one thing you can
  act on; category colours belong to categories; red means something is wrong.
- One hero moment per screen, maximum — and it is a **number**, set in type.
  The brand sweep is for the splash and onboarding CTA only; it is never a
  background for text, because white on a violet→teal ramp is the least legible
  place to put the most important number on the screen.
- Money is always tabular: digits never shift while animating.

## 2. Color

| Role | Light | Dark | AMOLED |
|------|-------|------|--------|
| Primary | `#4A2E7A` | `#C9B3F2` | `#C9B3F2` |
| Primary container | `#EDE6F8` | `#3E2765` | `#3E2765` |
| Canvas | `#F7F5F0` | `#0F0F0D` | `#000000` |
| Surface | `#FFFFFF` | `#181815` | `#0A0A09` |
| Ink (`onSurface`) | `#17160F` | `#F4F1E8` | `#F4F1E8` |
| Income | `#1C6B4A` | `#7BCBA2` | `#8FE0B6` |
| Expense / status | `#A8402F` | `#E89A8A` | `#FFA99A` |
| Brand sweep | `#4A2E7A → #5E3A8B → #6B4A9E` |

Neutrals are **warm** (paper and ink, never violet-tinted, never pure gray).
Dynamic color is opt-in; brand colors are the fallback and the default identity.

**Expense is a status colour, not a transaction colour.** Outgoing amounts are
set in ink — the minus sign already says "out", and thirty red numbers in a list
is noise that leaves nothing to signal a real problem. Red is reserved for
over-budget, failures and destructive actions; green is the only amount that
carries a hue, and only for money arriving.

**Contrast:** all text must meet WCAG AA (4.5:1 body, 3:1 large). Chart series
must stay distinguishable in the two most common color-vision deficiencies —
prefer labels + shapes over hue alone.

**There is no brand gradient.** The aurora sweep, the radial glow behind the
splash mark and the gradient onboarding medallion are all retired: "type does the
work gradients used to", and a surface is either paper, a card, or a 6 % ink wash.
`AuraGradients` no longer exists.

## 3. Typography — Plus Jakarta Sans (variable, OFL)

| Style | Use |
|-------|-----|
| `displayLarge`–`displaySmall` | Hero balances, splash |
| `headline*` | Screen titles, big numbers |
| `title*` | Section headers, card titles |
| `body*` | Copy |
| `label*` | Buttons, chips, micro-labels |
| `AuraType.moneyHero` / `moneyHeroSmall` | The one hero number on a screen (45sp), and the hero where a second element shares it (38sp) |
| `AuraType.moneyLarge/Medium/Small` | Card metrics, goal amounts, list money (tabular figures) |
| `AuraType.screenTitle` / `sectionTitle` / `sectionAction` | Screen title (23sp), section header (13sp), its trailing link (12sp) |
| `AuraType.rowTitle` / `rowMeta` / `rowMoney` | Transaction rows: 13.5/600, 11.5/400, 13.5/700 |
| `AuraType.eyebrow` / `groupLabel` / `axisLabel` | Uppercase micro-labels at 10.5sp, 10.5sp and 10sp |
| `AuraType.budgetName` / `budgetAmount` / `budgetSub` | Budget rows and their 11sp sub-row |
| `AuraType.settingsRowTitle` / `settingsRowValue` | Settings rows: 13sp label, 12sp value |
| `AuraType.amountDisplay` / `keypadDigit` / `buttonPrimary` | Quick Add: 44sp amount, 20sp keys, 14sp CTA |
| `AuraType.metricLabel` | Small caption above a metric |
| `AuraType.bodyCopy` | Reading text on a full-screen surface (onboarding, licence pages) — 15sp/400 |
| `AuraType.wordmark` | The app's name set large (splash) — 28sp/700 |

Rules: never invent font sizes; use `MaterialTheme.typography` or `AuraType`.
Maximum two weights per screen (Medium/SemiBold + Bold for emphasis).

**The type scale is the interface concept's, verbatim** — sizes, weights and
letter-spacing from `docs/design/interface-concept/index.html`. The tracking values
look arbitrary because they are the concept's em-relative tracking resolved at each
size (−0.038em at 45sp is −1.71sp); do not round them.

**Plus Jakarta Sans is a variable font.** Registering the one TTF several times
under several `Font(weight = …)` entries does *not* set the weight axis — the
renderer draws the file's default instance and every bold in the app comes out
regular (it did, until 0060). Each entry needs
`variationSettings = FontVariation.Settings(FontVariation.weight(n))`.

**Compose floors a line box at the font's natural height (1.26em); the browser's
`normal` is ~1.15em.** A token's `lineHeight` cannot go below that floor, so text
occupies 1–4dp more than the prototype's box and the difference accumulates down a
screen (~4dp on Home, ~12dp after a hero). This is a platform metric difference,
not a design one — do not paper over it with negative spacers.

## 4. Spacing & shape

- 4dp grid via `AuraSpacing`; screen gutter is `AuraSpacing.gutter` (20dp).
- Vertical rhythm: `lg` between related elements, `AuraSpacing.section` (26dp)
  between blocks — a section header carries only its 10dp bottom margin, never
  padding above, or the rhythm depends on which block came first.
- Shape scale: 12 / 16 / 20 / 28 / 36dp. Cards default to 20dp; sheets 30dp
  top corners; pills fully rounded. The FAB is a 54dp square at 19dp.

## 5. Surfaces & components

| Component | Rules |
|-----------|-------|
| `AuraCard` | Default `Outlined` (full-opacity hairline, 20dp radius). `Tonal` for grouped rows, `Filled` for hero-adjacent, `Glass` only over imagery |
| Buttons | M3 buttons restyled by theme; primary CTA full-width in sheets; destructive actions text/outlined in error color |
| `CategoryAvatar` | 36dp in a transaction row (16dp outlined glyph), 14% tonal fill, optional hairline ring. Outlined icons, not filled |
| `AuraSegmentedControl` | The concept's quiet range selector: 12dp track on a 6% ink wash, 9dp pill on `surface`, 32dp segments, chip-weight labels, spring indicator, haptic tick. It changes data, never the screen |
| `AuraChip` | The one selection control: 32dp pill in a 48dp target, ink fill when selected, optional 7dp category dot |
| Charts | 2.5dp line, gradient area fill, no gridlines, labels inline; animate in once (800ms) |
| `AuraProgressBar` | The comparison primitive: 5–8dp rounded bar, animated. Use wherever several values are read against each other (budgets, goals, ranked spending) — a bar compares two lengths, a ring asks you to estimate an arc |
| Ranking | A ranked bar list, never a donut. Comparing lengths is something people do well; comparing angles is not, and the donut's legend was already doing all the reading |
| Sheets | 30dp top corners, drag handle, spring dismiss, predictive back |
| Skeletons | `AuraSkeleton` shapes that match the final layout |
| Empty states | `AuraEmptyState`: icon medallion + one sentence + one action |

## 6. Motion

All specs come from `AuraMotion`:

| Token | Value | Use |
|-------|-------|-----|
| `DURATION_QUICK` | 150ms | Press feedback, scrims |
| `DURATION_STANDARD` | 220ms | Content swaps, fades |
| `DURATION_EMPHASIZED` | 300ms | Sheets, expanding cards |
| `DURATION_SLOW` | 600ms | Money counters |
| `DURATION_CHART` | 800ms | Chart draw-in |
| `standard()` | no-overshoot spring | Layout changes |
| `expressive()` | slight overshoot | Selection, indicators |
| `snappy()` | stiff | Press states |
| `gentle()` | soft | Large surfaces |

Rules:
- Animate `graphicsLayer`/draw values, never layout in a scroll path.
- Every interactive element responds to press within 100ms.
- Haptics: light tick (selection/nav), confirm (save), reject (invalid), soft
  thud (sheet dismiss). Never haptic on scroll.
- Respect reduced motion: when the system animator scale is 0, crossfades only.
- No animation may delay input or block the first frame.

## 7. Screen archetypes

Every screen must pick one and follow it:

1. **Feed** (Home, Activity): collapsing header → hero/summary → sections → list.
2. **Hub** (Plan, Insights): segmented control → hero metric → cards.
3. **Composer** (Quick Add, editors): pinned primary CTA, inputs above, sheet.
4. **Detail** (Transaction, Subscription): shared-element hero → metadata → actions.
5. **Flow** (Onboarding, Smart Add): step indicator → animated centerpiece → single CTA.

## 8. Definition of Done (per screen)

- [ ] Only `ui/designsystem` components + `ui/theme` tokens (no raw colors/sizes).
- [ ] All five states: default, loading (skeleton), empty, error, offline.
- [ ] Enter/exit motion + press feedback per §6.
- [ ] Light + dark + AMOLED screenshots captured.
- [ ] 200% font scale and TalkBack labels verified.
- [ ] No jank: scroll/animation within frame budget when measured.
