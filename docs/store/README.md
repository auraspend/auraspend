# Play Store assets — AuraSpend

Everything here is what is uploaded to Google Play for the default (en-US) store
listing. The authoritative listing is the one saved in the Play Console. As of
2026-10-17, **0.1.7 (versionCode 9) is uploaded to Play closed testing
(`alpha`) and sent for review** by the `v0.1.7` tag run. GitHub release:
<https://github.com/auraspend/auraspend/releases/tag/v0.1.7>. This directory is
the versioned source so assets can be changed and re-uploaded later.

| File | Used for | Play requirements |
|---|---|---|
| `icon-512.png` | Store listing icon | 512×512, 32-bit PNG |
| `feature-1024x500.png` | Feature graphic | 1024×500, PNG/JPEG |
| `screenshots/01..07` | Phone screenshots, in listing order | 2–8 images, 16:9 or 9:16, 320–3840 px per side |
| `app-store-screenshots.json` | Deck state (copy, layout, element positions) | see "Screenshot deck" below |
| `seed-deck.js` | Regenerates that state file from scratch | run after editing copy in bulk |
| `feature-graphic.html` | Source for the feature graphic | render with headless Chrome (below) |
| `public/screenshots/android/phone/en/` | The captures the deck is built from | — |
| `export.js` | Headless, deterministic bundle export | needs `pnpm dev` running |
| `variant.sh` | Parks the current deck into `variants/<name>/` | run before trying a new frame |
| `variants/` | Every deck variation that has been composed, with its frame source | — |
| `raw/` | **The website's** captures (1080×2400), *not* the deck's | still pre-concept; see below |

**These assets are live on the listing.** The seven screenshots and the feature
graphic were uploaded on 2026-10-18, replacing the old Aurora set. Note that the
tag workflow uploads the *bundle* and the release notes only, never images — so
if you change `screenshots/` or the banner here, the listing does not follow on
its own.

## Listing text (en-US)

**Canonical Play copy (2026-10-17): [`play-listing-en-US.md`](play-listing-en-US.md).**
It has the recommended title, short description, a 3,270-character long
description, the ASO rationale and the remaining gaps.

| Field | Current Play Console | Notes |
|---|---|---|
| Title (30) | `AuraSpend: SMS Expense Tracker` (30) | Updated 2026-10-17 from the 26-char brand title |
| Short (80) | `Offline expense tracker: budgets, subscriptions, insights. No ads, no tracking.` (79) | unchanged |
| Full (4000) | 2,355 chars, **not versioned in this repo** | paste the 3,270-char copy from `play-listing-en-US.md` |

Play's public API exposes the title, short/full description and video only
([`edits.listings`](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.listings));
the store **tags** are a Play Console field and are not exposed by the API.
Set them manually (suggested: Budgeting, Expense tracking, Personal finance,
Money management, SMS).

**Not Play copy:** `fastlane/metadata/android/en-US/` describes the
**no-Google-Play-Services** build (no Drive backup). Keep it with the `fdroid`
build; do not paste it into the Play listing.

## Release notes ("What's new")

Per-release notes for the Play Console's *What's new* field (500 characters per
language). The tag workflow uploads `whatsnew/whatsnew-en-US` and
`whatsnew/whatsnew-hi-IN` directly. Latest: 0.1.7.

## The deck

Seven composed 1080×1920 (9:16, Play-valid) slides: a headline plus a real
capture inside **an iPhone front**. The bezel is the skill template's
`public/mockup.png` (1022×2082) with the screen rect it was measured for
(`PHONE_SCREEN`); the captures are 1170×2532 — 390×844 @3x, an iPhone 15 Pro
screen — so they drop in with 0.17 % of cover-crop. An earlier variation drew
the concept's own device instead and is parked in
`variants/01-android-frame/`.

Inside that screen the **Android chrome is removed and the concept's iPhone
chrome is drawn in its place**, because an Android status bar (a gear, a
shield, a gesture pill) inside an iPhone bezel is exactly the tell that makes a
mockup read as fake:

| | What happens |
|---|---|
| Status bar | The 46px bar from the HTML — `padding: 0 30px`, "9:41" at 13px/700, and the file's own three SVGs (signal, wifi, battery) — drawn as one SVG in a `390 46` viewBox so it scales with the frame |
| The Android status bar | Measured, not guessed: `dumpsys window` reports `statusBars frame=[0,0][1170,72]` at 3x = **24dp**. The capture is nudged down by `46 − 24 = 22` concept-px, so the Android row lands behind the drawn bar instead of above it |
| The Android gesture pill | Falls off the bottom from that same nudge — nothing is drawn in its place, which is also what the HTML does |
| Island | The concept's **104×26 at 11px**, a stadium (`border-radius: 999px`, not `50%`, which on a 4:1 box draws an ellipse) — it also covers the mockup's own punch-hole dot |

The stage is flat (`#EBE7DE` paper / `#080807` dark) with no accent blobs, and
the type is `src/app/fonts/plus-jakarta-sans.ttf`, a copy of the file the app
ships (OFL, already attributed in the licences index).

| Slide | Screen | Copy |
|---|---|---|
| 1 hero (light) | Home | *Track expenses automatically.* |
| 2 device-bottom (dark) | Activity | *Every payment, categorised.* |
| 3 device-top (light) | Plan | *A budget for every category.* |
| 4 device-bottom (dark) | Insights | *See where it all goes.* |
| 5 two-devices (light) | Quick Add over Home | *Add an expense in seconds.* |
| 6 device-top (dark) | Subscriptions | *Every recurring payment, counted.* |
| 7 no-device (dark) | — | *Offline. Encrypted. No ads.* |

Copy is deliberately plain and ASO-shaped — a noun the user already searches
for, and a benefit — rather than a clever line. An earlier pass had
*"It says 560%. Not a flat 100%."* and *"Sorted while you weren't looking."*;
both were replaced on the owner's call. Slides 4, 6 and 7 are kept verbatim
because they were already in that register.

### The captures show a healthy month, on purpose

The demo data behind these screenshots is shaped so no negative figure appears
anywhere: **balance positive**, every budget between 41 % and 93 %, and nothing
"over". An ad that shows "over budget" and a minus in front of the balance sells
the opposite of the product. `DemoDataSeeder.kt` was retuned for that — the rent
is ordinary, the mid-month invoice is large enough that the month's income beats
its spending (otherwise the flow bar renders fully brick-red), and the budgets
are all under with one near the limit at 93 % so the near-limit state is still
exercised. The over-limit paths are covered by unit tests, not by demo data.

**What still carries a minus:** individual expense rows (`−₹245`) and the
"₹44,663 out" figure. That is the app's own rendering of outgoing money — it is
a `formatMoney`/`TransactionRow` decision, not something a screenshot pass can
or should paint over.

Slides 2, 4, 6, 7 are inverted so the carousel alternates paper and near-black.
No two adjacent slides share a layout. Canvas mode is **isolated**: every
exported PNG has to stand alone in a carousel, so nothing crosses a seam.

### Measured, not eyeballed

`measure_chrome.py` (in the scratch workspace, not this repo) decodes an export
and reports the drawn chrome against the spec, because the failures here are
invisible at a glance — an ellipse island and a transparent status-bar band both
look "fine" in a thumbnail:

```
01-hero.png     island 157px wide (expected 158.1, 99.3%), 40px tall, aspect 3.92
                band row (247,245,240) — #F7F5F0, the light stage's paper
02-device-bottom.png  island 144px (expected 143.7, 100.2%), aspect 4.00
                band row (15,15,13)   — #0F0F0D, the dark stage's paper
```

Two variables feed that chrome — `--ql-paper` and the ink `color` — and both
**must sit on the slide root, not the background layer**: the device is a
sibling of the background, so anything set there never reaches it. Both
failures are silent (a paper-white status bar on a dark slide; dark chrome on a
dark slide), which is why they are called out here.

## Screenshot provenance

The captures are **not** UI screenshots of a user's phone. They come from the
local `emulator-5554` AVD, forced to **1170×2532 @480dpi = 390×844 dp** — the
concept's exact geometry, so one app pixel is one prototype pixel — with the
per-app locale set to `en-IN` (rupee formatting, lakh/crore grouping) and demo
data from
`app/src/androidTest/java/com/awbuilds/auraspend/DemoDataSeeder.kt`
(`ANDROID_SERIAL=emulator-5554 tools/seed_demo_data.sh`). Both app themes were
shot. Quick Add is captured with ₹640 typed and a category selected, because an
ad that shows a disabled Save button is a screenshot of nothing.

## Regenerating everything

```bash
cd docs/store
pnpm install
pnpm dev &                       # the editor on :3000

# captures (emulator must be running at the geometry above)
ANDROID_SERIAL=emulator-5554 ../../tools/seed_demo_data.sh

node seed-deck.js                # copy + layout -> app-store-screenshots.json
node export.js android           # -> dist/android.zip (7 × 1080×1920)
unzip -o dist/android.zip -d dist/android
cp dist/android/android/android/1080x1920/en/*.png …   # -> screenshots/, in order
```

Trying another frame? Park this one first, which keeps the slides, the deck
state and the frame source together:

```bash
./variant.sh 03-some-other-frame "one line about what is different"
```

Note `pnpm dev` and `pnpm build` both write `.next/`: running a build while the
dev server is up can leave the server serving stale chunks, and the export then
times out. Stop one before starting the other.

Editing copy by hand in the editor is the normal path; `seed-deck.js` exists so
the deck can be rebuilt or diffed as text. The editor saves to
`app-store-screenshots.json` at this directory's root, and that file is the
source of truth for what gets exported.

Feature graphic:

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless --disable-gpu --hide-scrollbars \
  --screenshot=docs/store/feature-1024x500.png --window-size=1024,500 \
  "file://$PWD/docs/store/feature-graphic.html"
```

## Known gaps

- **Off-designer detail: an iPhone in a Play listing.** Play screenshots for an
  Android app normally show an Android device; this deck deliberately shows an
  iPhone, which is a marketing choice the owner made, not an oversight.
- **`variants/` is excluded from the type check** (`tsconfig.json`), because it
  holds parked copies of `constants.ts` and `device-frames.tsx` that import
  `./types` from a directory that has no such file.
- **`raw/` is stale.** It holds the website's captures (`docs/index.html`
  embeds `store/raw/*.png` for its light/dark switch), captured 2026-10-10
  against the pre-concept UI. The website therefore still shows the old design.
  Refreshing it means new captures at 1080×2400 plus the four `<img>` tags in
  `docs/index.html`; it was left alone deliberately so this pass stayed scoped
  to the store assets.
- **The launcher icon is still the old artwork** (`icon-512.png`), which is why
  the feature graphic's mark reads violet against the paper stage. Tracked with
  the icon follow-up in handoff 0062; the deck does not depend on it.
- **No localised deck.** `locales: ["en"]` only; the app's hi-IN strings exist
  but the canonical Play listing is en-US.
- **Emulator status-bar icons** (a gear and a shield) appear in the captures.
  They are the AVD's, not the app's.
