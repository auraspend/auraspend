# Aurora Design Language — Terracotta

> Source of truth for AuraSpend's visual system. If a screen disagrees with this
> document, the screen is wrong.
>
> The identity is **terracotta clay**: warm, crafted, editorial. The code keeps
> its `Aurora*` token names for source stability — the values below are what
> ships. ("Aurora" is the design system's name; terracotta is its palette.)

## 1. Personality

Warm, crafted, trustworthy. Light-first with a true-black AMOLED mode.
Data-dense but breathable. Two adjectives for every decision: **warm** and
**considered**.

- No emoji in chrome (emoji only as a fallback for user-created categories).
- No drop shadows on content; depth comes from tonal surfaces + hairlines.
  Warm-tinted shadows are allowed on floating chrome only (`Modifier.softShadow`).
- One hero moment per screen, maximum — the terracotta gradient is reserved
  for it (`AuraCardStyle.Hero`).
- Money is always tabular: digits never shift while animating.
- The illustrated mark (§5) is the only decorative object; everything else
  earns its place.

## 2. Color

| Role | Light | Dark | AMOLED |
|------|-------|------|--------|
| Primary (terracotta) | `#A64B2A` | `#F0A47C` | `#F0A47C` |
| Primary container | `#F8DFD2` | `#78351A` | `#78351A` |
| Secondary (olive-sage) | `#5F6B4A` | `#BCC7A2` | `#BCC7A2` |
| Tertiary (ochre) | `#8A6320` | `#E7C48E` | `#E7C48E` |
| Canvas | `#FCF7F2` | `#17110D` | `#000000` |
| Surface | `#FFFFFF` | `#211A14` | `#0C0906` |
| Income | `#1F7A4D` | `#63D394` | `#74E0A3` |
| Expense | `#B3372B` | `#F2938A` | `#FFA096` |
| Terracotta gradient | `#A64B2A → #D2764A → #F2D8BC` |

Neutrals are warm-tinted (never pure gray). Dynamic color is opt-in; brand
colors are the fallback and the default identity. AMOLED gets its own semantic
money tokens — the dark-mode green/red are contrast-matched to `#211A14` and
read washed out on true black.

**Contrast:** all text must meet WCAG AA (4.5:1 body, 3:1 large). Chart series
must stay distinguishable in the two most common color-vision deficiencies —
prefer labels + shapes over hue alone.

## 3. Typography

Two bundled variable fonts (both OFL, see `docs/licenses/`):

- **Manrope** — everything structural: body, labels, titles, and **all money**.
  It carries a real `tnum` feature, so columns never jitter while animating.
- **Fraunces** — the editorial voice: `display*`, `headline*` and `titleLarge`.
  A soft "wonky" serif; used for screen titles and the largest type only.

| Style | Use |
|-------|-----|
| `displayLarge`–`displaySmall` | Splash, hero balances (Fraunces) |
| `headline*` | Screen titles, big numbers (Fraunces) |
| `titleLarge` | Section headers (Fraunces) |
| `titleMedium`/`titleSmall` | Card titles (Manrope) |
| `body*` | Copy (Manrope) |
| `label*` | Buttons, chips, micro-labels (Manrope) |
| `AuraType.moneyHero/Large/Medium/Small` | Every money value (Manrope, tabular) |
| `AuraType.metricLabel` | Small caption above a metric |

Rules: never invent font sizes; use `MaterialTheme.typography` or `AuraType`.
Maximum two weights per screen (Medium/SemiBold + Bold for emphasis).

## 4. Spacing & shape

- 4dp grid via `AuraSpacing`; screen gutter is `AuraSpacing.gutter` (20dp).
- Vertical rhythm: `lg` between related elements, `xxl`/`xxxl` between sections.
- Shape scale: **10 / 14 / 20 / 26 / 32dp**. Cards default to 22dp; sheets 28dp
  top corners; pills fully rounded. Corners are one step softer than the old
  scale: a warm palette reads friendlier when surfaces are not sharply squared.

## 5. Surfaces & components

| Component | Rules |
|-----------|-------|
| `AuraCard` | Default `Outlined` (hairline). `Tonal` for grouped rows, `Filled` for base surfaces, `Glass` only over imagery, `Hero` for the one gradient moment — it sets its own content colour |
| Buttons | M3 buttons restyled by theme; primary CTA full-width in sheets; destructive actions text/outlined in error color |
| `CategoryAvatar` | 44–50dp **rounded square** (30% radius), gradient tonal fill, optional hairline ring |
| `AuraSegmentedControl` | 46dp track, terracotta gradient indicator, spring animation, haptic tick |
| Charts | 3dp line, gradient area fill, no gridlines, rounded caps on arcs; animate in once (820ms) |
| Sheets | 28dp top corners, drag handle, spring dismiss, predictive back |
| Skeletons | `AuraSkeleton` shapes that match the final layout |
| Empty states | `AuraEmptyState`: the illustrated mark (§5.1) + one sentence + one action |

### 5.1 The mark

`AuraMark` (`ui/designsystem/AuraMark.kt`) is the illustrated terracotta mark: a
clay pot with a coin slot and a sprout — *money you keep, growth you can see*.
It is drawn with Compose `Canvas` paths, so it inherits the theme palette in all
three modes and scales without raster artefacts. `AuraMarkMedallion` places it on
a soft radial wash for empty states, onboarding and the splash. Feature icons
ride as a small badge on the medallion's corner, so every empty screen speaks
with one visual voice without losing its meaning.

## 6. Motion

All specs come from `AuraMotion`:

| Token | Value | Use |
|-------|-------|-----|
| `DURATION_QUICK` | 150ms | Press feedback, scrims |
| `DURATION_STANDARD` | 220ms | Content swaps, fades |
| `DURATION_EMPHASIZED` | 320ms | Sheets, expanding cards |
| `DURATION_SLOW` | 620ms | Money counters |
| `DURATION_CHART` | 820ms | Chart draw-in |
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
4. **Detail** (Transaction, Subscription): gradient hero → metadata → actions.
5. **Flow** (Onboarding, Smart Add): step indicator → illustrated mark → single CTA.

## 8. Definition of Done (per screen)

- [ ] Only `ui/designsystem` components + `ui/theme` tokens (no raw colors/sizes).
- [ ] All five states: default, loading (skeleton), empty, error, offline.
- [ ] Enter/exit motion + press feedback per §6.
- [ ] Light + dark + AMOLED screenshots captured.
- [ ] 200% font scale and TalkBack labels verified.
- [ ] No jank: scroll/animation within frame budget when measured.
