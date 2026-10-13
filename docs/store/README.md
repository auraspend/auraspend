# Play Store assets — AuraSpend

Everything here is what is uploaded to Google Play for the default (en-US) store
listing. The authoritative listing is the one saved in the Play Console — staged
but not published, as of 2026-10-11; this directory is the versioned source so
assets can be changed and re-uploaded later.

| File | Used for | Play requirements |
|---|---|---|
| `icon-512.png` | Store listing icon | 512×512, 32-bit PNG |
| `feature-1024x500.png` | Feature graphic | 1024×500, PNG/JPEG |
| `screenshots/01..07` | Phone screenshots, in listing order | 2–8 images, 16:9 or 9:16, 320–3840 px per side |
| `raw/` | Raw app captures (1080×2400) the composed slides are built around; the `*-light` files pair with their dark twins for the website's theme switch | — |
| `feature-graphic.html` | Source for the feature graphic (embeds `raw/home-dark.png`) | render with headless Chrome (see below) |
| `app-store-screenshots.json` | Project state for the screenshot editor that composed the slides | see "Screenshot editor" below |

## Listing text (en-US)

These are the exact strings saved on Play (also kept here so they can be
diffed/updated):

**Title** (30 max):

```
AuraSpend: Expense Tracker
```

**Short description** (80 max):

```
Offline expense tracker: budgets, subscriptions, insights. No ads, no tracking.
```

**Full description** (4000 max): see `../../docs/handoffs/0017-play-console-setup.md`
— the text was composed for the 0.1.1 listing and lives in the Play Console's
store listing editor. If it is edited there, copy the new text back here in the
next handoff.

## Release notes ("What's new")

Per-release notes for the Play Console's *What's new* field (500 characters per
language). Latest: `play-release-notes-0.1.3.md`.

## Regenerating the feature graphic

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
  --force-device-scale-factor=1 --window-size=1024,500 \
  --screenshot=docs/store/feature-1024x500.png \
  "file://$PWD/docs/store/feature-graphic.html"
```

## Screenshot provenance

The seven listing screenshots are **composed advertisements**, not raw captures:
each is a 1080×1920 (9:16, Play-valid) canvas with a headline and a 9:20 phone
mockup showing a real app capture. They were composed with the
`app-store-screenshots` skill's editor (see below); the composed PNGs are what
is committed here and what is live on Play.

The raw captures behind them are in `raw/`: taken on the `aura_spike` AVD
(Play Store image, Android 17 / API 37) at its **native 1080×2400**, dark theme
(plus one light-theme Home capture for the themes slide), `en-IN` rupee
formatting, and demo data from
`app/src/androidTest/java/com/awbuilds/auraspend/DemoDataSeeder.kt`. One extra
₹88,000 "Consulting invoice" income row was inserted into the emulator's debug
database to make the balance positive; it exists only on that emulator, not in
the seeder or the app.

Note: the `dev` and `prod` flavors are feature-identical (ADR 0010); the
captures come from the prod build so the label and app id match the store build.

## Screenshot editor

The deck was composed with the [`app-store-screenshots` skill](https://github.com/ParthJadhav/app-store-screenshots)
editor (a Next.js app). The editor project used for this deck lives outside
this repo at `~/projects/auraspend-store-assets/`; its state file is
archived here as `app-store-screenshots.json` so the deck can be resumed or
re-rendered later:

```bash
cd ~/projects/auraspend-store-assets   # or a fresh copy of the skill template
cp <this dir>/app-store-screenshots.json .
pnpm install && pnpm dev
# exports: node tools/export.js android dist/android.zip   (Playwright-driven, deterministic)
```

Two local modifications were needed for Android Phone assets and are documented
in the editor project: the Android frame aspect is 9:20 (matching the native
captures — a 9:16 frame crops 11% off each side via `object-fit: cover`), and
the deck runs in **isolated** canvas mode because no element crosses screens.
