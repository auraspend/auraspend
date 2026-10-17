# Aurora Design Language

> Source of truth for AuraSpend's visual system. If a screen disagrees with this
> document, the screen is wrong.

## 1. Personality

Calm, premium, trustworthy fintech. Dark-first. Data-dense but breathable.
Nothing decorative without meaning. Two adjectives for every decision:
**quiet** and **precise**.

- No emoji in chrome (emoji only as a fallback for user-created categories).
- No drop shadows on content; depth comes from tonal surfaces + hairlines.
- One hero moment per screen, maximum (aurora gradient reserved for it).
- Money is always tabular: digits never shift while animating.

## 2. Color

| Role | Light | Dark | AMOLED |
|------|-------|------|--------|
| Primary | `#5E3A8B` | `#D0BCFF` | `#D0BCFF` |
| Primary container | `#EADDFF` | `#4F378B` | `#4F378B` |
| Canvas | `#FAF7FD` | `#14101C` | `#000000` |
| Surface | `#FFFFFF` | `#1D1825` | `#0C0A12` |
| Income | `#1B7F4B` | `#5BD68E` | `#5BD68E` |
| Expense | `#C13B3B` | `#F2857E` | `#F2857E` |
| Aurora gradient | `#5E3A8B → #8B5CF6 → #2DD4BF` |

Neutrals are violet-tinted (never pure gray). Dynamic color is opt-in; brand
colors are the fallback and the default identity.

**Contrast:** all text must meet WCAG AA (4.5:1 body, 3:1 large). Chart series
must stay distinguishable in the two most common color-vision deficiencies —
prefer labels + shapes over hue alone.

## 3. Typography — Plus Jakarta Sans (variable, OFL)

| Style | Use |
|-------|-----|
| `displayLarge`–`displaySmall` | Hero balances, splash |
| `headline*` | Screen titles, big numbers |
| `title*` | Section headers, card titles |
| `body*` | Copy |
| `label*` | Buttons, chips, micro-labels |
| `AuraType.moneyHero/Large/Medium/Small` | Every money value (tabular figures) |
| `AuraType.metricLabel` | Small caption above a metric |

Rules: never invent font sizes; use `MaterialTheme.typography` or `AuraType`.
Maximum two weights per screen (Medium/SemiBold + Bold for emphasis).

## 4. Spacing & shape

- 4dp grid via `AuraSpacing`; screen gutter is `AuraSpacing.gutter` (20dp).
- Vertical rhythm: `lg` between related elements, `xxl`/`xxxl` between sections.
- Shape scale: 12 / 16 / 20 / 28 / 36dp. Cards default to 20dp; sheets 28dp
  top corners; pills fully rounded.

## 5. Surfaces & components

| Component | Rules |
|-----------|-------|
| `AuraCard` | Default `Outlined` (hairline). `Tonal` for grouped rows, `Filled` for hero-adjacent, `Glass` only over imagery |
| Buttons | M3 buttons restyled by theme; primary CTA full-width in sheets; destructive actions text/outlined in error color |
| `CategoryAvatar` | 44–50dp circle, 14% tonal fill, optional hairline ring |
| `AuraSegmentedControl` | 44dp track, spring indicator, haptic tick |
| Charts | 2.5dp line, gradient area fill, no gridlines, labels inline; animate in once (800ms) |
| Sheets | 28dp top corners, drag handle, spring dismiss, predictive back |
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
