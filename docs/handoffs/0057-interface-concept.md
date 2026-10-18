# 0057 — Interface concept: "Quiet Ledger"

**Date:** 2026-10-18
**Scope:** docs only. **No file under `app/` was touched** — no Kotlin, no
strings, no tokens, no tests. The shipped UI is still Aurora.

## What this is

A clickable, whole-app redesign proposal, not a decision. It exists so the
direction can be argued about before any of it is built.

- `docs/design/interface-concept/index.html` — self-contained, no build step,
  no network dependency (falls back to the system sans if Plus Jakarta Sans
  does not load). Phone frame, live nav, working keypad, light/dark toggle,
  a per-screen rationale panel and a "system" tab with the tokens.
- `docs/design/interface-concept/board.png` — all six screens on one board.

Open `index.html` directly; `#home`, `#activity`, `#plan`, `#insights`,
`#settings`, `#sheet` deep-link, `?theme=dark` and `?view=board` work.

## The direction

Same personality `docs/design/aurora.md` asks for — quiet, precise, nothing
decorative without meaning — with the hierarchy rebuilt around how the app is
actually used. Five rules:

1. **Chrome is monochrome, data is colour.** Violet marks the one thing you can
   act on. Category colours belong to categories. Red means something is wrong.
2. **Type does the work gradients used to.** Size, weight and tracking carry
   hierarchy; surfaces stay flat.
3. **Hairlines, never shadows** (unchanged from Aurora).
4. **One hero per screen, and it is always a number.**
5. **Nothing tappable lives above the thumb's reach** unless it is back or a title.

## What moved, and why

| Screen | Change | Reason |
|---|---|---|
| Home | Gradient hero → typographic balance + a spend-vs-income flow bar | White on a violet→teal sweep had the weakest contrast on the most important number |
| Home | 4 quick actions → 1 (the centre add); "Stats" removed | "Stats" was the Insights tab, "Add" was the FAB, income is a toggle in the sheet |
| Home | 3-row budget carousel → one summary line + bar | Three rows pushed today's transactions below the fold, contradicting the glance rule |
| Home | New "needs you" rail (over-budget, review queue) | The two things that ever need action, reachable without a scroll |
| Activity | Search always visible | Hidden behind an icon cost a tap on the most common retrieval path |
| Activity | Outgoing amounts in ink, not red | Thirty red numbers is noise and leaves nothing to signal a real problem |
| Activity | Type filters and category filters in one scrolling row | No mode switch, no second screen |
| Plan | Rings → linear bars; hub-of-links → content | Bars compare; rings ask you to estimate an arc. Budgets, goals and subscriptions are shown, not just linked |
| Plan | Over-budget reads "Over by ₹1,299" | Continues 0056's honesty fix; never "100 % used" beside a larger number |
| Insights | Donut + legend → ranked bars | People compare lengths easily and angles badly; the legend was doing all the work |
| Insights | One chart per question | Ranking = bars, trend = daily column strip, "who" = a plain list |
| Quick Add | Same chip as Activity and the editor; filled selected state | 0056's fix, carried into the concept |
| Settings | Five short groups, value on the right | People scan settings for a keyword, not a hierarchy |

## What it deliberately does not do

- **No new features.** Every element maps to something the app already computes
  or already stores. The "needs you" rail reads the over-budget derivation and
  the review queue; nothing else is invented.
- **No IA removal.** Still Home / Activity / Plan / Insights with a centre add.
- **No change** to dynamic colour, AMOLED, the 200 % font-scale contract, or the
  five-states requirement.

## Two deliberate contradictions with `docs/design/aurora.md`

Both need the owner's call before anything is built:

1. **Expense colour.** Aurora §2 makes expense red everywhere. The concept makes
   outgoing amounts ink and reserves red for status (over budget, errors).
   Defensible on scan-ability, but it is the largest behavioural change here and
   Indian passbook conventions are red-for-debit.
2. **The hero gradient.** Aurora §1 reserves the aurora gradient for "one hero
   moment per screen". The concept has no gradient at all — the hero is
   typographic. If this is accepted, §1 and §5 of `aurora.md` need an edit.

## Open questions

- Does the Home rail have a real data source, or is "3 messages to review" the
  only honest second card today?
- Savings goals and subscriptions on Plan are shown as content; that is more
  than the current hub does and needs a ViewModel change, not just a layout one.
- The concept uses Plus Jakarta Sans throughout. A second display face was tried
  and rejected in 0048 (old-style figures in money) — do not reintroduce one.

## Demo data

The 0056 device-walk numbers, made internally consistent: budgets
₹27,712 of ₹35,000 (79 %; Grocery 122 %, Shopping 137 %), month out ₹70,712,
in ₹1,18,500, balance ₹1,42,380. The Insights category list sums to ₹70,712
exactly, and the Home flow bar reads from the same two constants.

## Verification

Rendered headless in Chrome at 390×844, light and dark, every screen plus the
sheet and the board; checked for the class-collision bug that broke every
progress bar (page header and progress bars shared `.bar`), a copy duplication
in the budget rows, and the Insights total mismatch. No app build was run
because no app file changed.
