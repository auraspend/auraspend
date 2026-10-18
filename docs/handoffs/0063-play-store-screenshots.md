# 0063 — Play Store screenshots: the frame was wrong, twice

**Date:** 2026-10-18 · **Branch:** `ui_enhance` · **Status: uncommitted, nothing
pushed.** No app code changed in this session — it is a store-assets pass.

## What this is

The Play listing's screenshots were composed ads built against the old purple
Aurora UI. This pass rebuilt the deck on the Quiet Ledger concept, and then —
after the owner looked at the first attempt and said the phone mockup "is not
good" — rebuilt the *device* as an iPhone. Both variations are kept; the second
is the live one.

Owner decisions, taken up front: the editor lives in `docs/store/` (not the
repo root), the deck mixes light and dark, the feature graphic is redone, the
deck is English-only, and (after seeing v1) the device is **the skill template's
real iPhone mockup** inside which the **iOS chrome is drawn over the capture**.

## v1's four defects (parked in `variants/01-android-frame/`)

v1 transcribed the concept's own drawn device — 11px bezel, 155° gradient,
60/50px radii — which was the wrong read of "make it look like the HTML", and
had four concrete, invisible-at-a-glance defects:

1. **The island was an ellipse.** `borderRadius: "50%"` on a 92×17 box draws an
   ellipse; the HTML's `border-radius:14px` on a 104×26 box is a stadium. This
   is the thing that made the mockup look wrong.
2. **The island's numbers had been changed** (92×17 at 5px) to fit the Android
   status bar, when the right move was to stop using the Android status bar.
3. **No status bar was drawn at all**, so the emulator's own icons showed — a
   gear, a shield, and a cramped time.
4. **The Android gesture pill** sat at the bottom, which the HTML has nowhere.

## v2 — what the deck is now

The bezel is the skill template's `public/mockup.png` (1022×2082) with the
screen rect it was measured for (`PHONE_SCREEN`: 918×1990 at 52,46). The
captures are 1170×2532 — 390×844 @3x, *an iPhone 15 Pro screen* — so they drop
into that rect with 0.17 % of cover-crop. Nothing was re-shot.

Inside the screen, the Android chrome is removed and the concept's iPhone chrome
drawn in its place:

- **Status bar.** The HTML's `.statusbar`: 46px, `padding: 0 30px`, "9:41" at
  13px/700, and the file's own three SVGs (signal bars, wifi, battery) lifted
  verbatim into one SVG with a `390 46` viewBox, so it scales with the frame
  instead of needing per-size math. `fill="currentColor"`, so it takes the
  slide's ink.
- **The crop.** `dumpsys window` reports `statusBars frame=[0,0][1170,72]` at 3x
  = **24dp**, so the capture is nudged down by `46 − 24 = 22` concept-px. That
  hides the Android row behind the drawn bar (which is opaque, painted in the
  app's own background colour) and drops the Android gesture pill off the
  bottom, where nothing is drawn — as the HTML does.
- **Island.** The concept's 104×26 at 11px, `borderRadius: 999px` — a stadium —
  which also covers the mockup's own punch-hole camera dot.

Copy, slide order, the light/dark alternation, the flat paper/near-black stage,
the app's own font file and the 1080×1920 Play export are unchanged from v1.
Device rects were recomputed because the mockup is wider than the concept device
(0.4909 vs 0.4757).

## The trap that cost the most

**Two variables must live on the slide root, not the background layer.** The
device is a *sibling* of `SlideBackground`, so `--ql-paper` set there never
reached it, and neither did the ink `color` — both fell back silently. The dark
slides therefore drew a **paper-white status bar with dark ink on a near-black
screen**, and it looked deliberate enough to nearly ship. `slideVars()` now
returns both, and the three call sites (SlideCanvas root, DeckCanvas's two
per-slide wrappers) spread it.

## Then the owner asked for the shots to look less "clever"

Two changes, one of them in the app:

1. **Copy.** Slides 1, 2, 3 and 5 were rewritten plain and ASO-shaped — a noun
   the user searches for plus a benefit — because *"It says 560%. Not a flat
   100%."* and *"Sorted while you weren't looking."* were writing, not selling.
   Slides 4, 6 and 7 were kept verbatim on the owner's instruction.
2. **No negative figures.** `DemoDataSeeder.kt` was retuned to a **healthy
   month**: the rent drops from ₹2,40,000 to ₹42,000, the mid-month consulting
   invoice rises from ₹32,500 to ₹72,500, and the grocery and shopping budgets
   rise (₹6,000 → ₹9,000, ₹3,000 → ₹18,000). The Home screen went from
   **−₹9,412** with *"Shopping is over / ₹13,797 over your ₹3,000 budget"* to
   **₹2,28,588**, *"0 of 5 budgets over"*, *"₹10,533 left"*; the Plan hub from
   *"2 categories are over — Grocery, Shopping"* with 122 %/560 % rows to
   41–93 % with nothing over. The balance is
   `SUM(INCOME) − SUM(EXPENSE)` excluding `cat_transfer`, and the old dataset
   sums to exactly −₹9,412 — that arithmetic is how the retune was checked, not
   the render.

The seeder's KDoc was rewritten to say what the data is now *for*. It is also
what the Play screenshots are shot from, so its old purpose — a deliberately
blown budget to exercise over-limit rendering — moved to the unit tests.

**Deliberately not changed:** individual expense rows still read `−₹245`. That
is how the app draws outgoing money (`TransactionRow` / `formatMoney`), and
painting it out of a screenshot would be a lie about the product. Flagged to the
owner as an app decision rather than a screenshot one.

## Verification

`measure_chrome.py` (scratch workspace, not this repo) decodes an export and
reports the drawn chrome against the spec, because both failure modes look fine
in a thumbnail:

| Slide | Island width | Height | Aspect | Band colour |
|---|---|---|---|---|
| 01 hero (light) | 157px vs 158.1 (**99.3 %**) | 40px vs 39.5 (101 %) | 3.92 | `(247,245,240)` = `#F7F5F0` ✓ |
| 02 activity (dark) | 144px vs 143.7 (**100.2 %**) | 36px vs 35.9 (100 %) | 4.00 | `(15,15,13)` = `#0F0F0D` ✓ |
| 06 subscriptions (dark) | 158px vs 158.1 (**99.9 %**) | 40px (101 %) | 3.95 | `#0F0F0D` ✓ |

The band row is uniform across the middle 40 % of the screen, which is what
proves no Android icon is bleeding through.

The re-captures were re-checked the same way, plus a text dump of the Plan hub
on the device (41 %, 56 %, 62 %, 81 %, 93 % — no "over" row anywhere).

Also: `pnpm build` green; the export is 7 × 1080×1920 (9:16, Play-valid),
reviewed as a contact sheet and at full size; `./tools/check_license.sh` and
`./tools/check_privacy_claims.sh` pass (the trust slide states the privacy claim
with its exception on the next line, in the app's own approved wording);
`./gradlew :app:testProdDebugUnitTest --rerun` → **614 tests, 0 failures,
1 skipped**. No app source changed after 13:00, so the captures match the tree
the other session is working on.

## Decisions worth keeping

- **The editor lives in `docs/store/`.** An Android repo should not grow a root
  `package.json`/`src/`/`node_modules`, and the deck's state file already lived
  here. `.gitignore` covers `node_modules`, `.next` and `dist`.
- **Variations are first-class.** `variant.sh <name> "note"` parks the slides,
  the feature graphic, the deck state *and* the two frame source files into
  `variants/<name>/`. v1's frame had never existed anywhere but the working
  tree, so parking the code is what makes it reproducible at all. `variants/` is
  in `tsconfig.json`'s `exclude`, because parked `constants.ts` imports
  `./types` from a directory that has no such file and Next type-checks it.
- **`pnpm dev` and `pnpm build` both write `.next/`.** Running a build while the
  dev server is up leaves it serving stale chunks and the export times out. Stop
  one before starting the other.
- **Nothing is uploaded to Play.** The live listing still shows the old Aurora
  set; images are a manual Console step.

## Not done

- No localised (hi-IN) deck.
- `raw/` is still the website's pre-concept captures — the site still shows the
  old design. Refreshing it means new 1080×2400 shots plus four `<img>` tags in
  `docs/index.html`.
- The launcher icon is still the old violet artwork, which is why the feature
  graphic's mark does not match the paper stage.

## Next step

Someone with the Play Console open uploads `screenshots/01..07` and
`feature-1024x500.png`, in that order. If the device frame should change again,
`./variant.sh` first.
