# 0083 — AuraSpend is live on Google Play

**Date:** 2026-11-06 · **Branch:** `dev` → `main` · **Scope:** public copy only — site and README.

## What changed

The app moved from the `alpha` closed-testing track to production, so every place the public
copy described a testers-only listing was rewritten:

- **`site/index.html`** — the Google Play channel now reads "Free on Google Play", carries a
  green **Live** chip, gains a real **Get it on Google Play** button (the card had no link at
  all while it was in testing) and drops "Ask in the repository for an invitation to the tester
  list". The note is now "Free · Android 11 and later".
- **`site/download.html`** — the same block with its own wording ("Published on Google Play"),
  plus the three metas on lines 21/24/31: "Google Play closed testing" → "Google Play".
- **`README.md`** — the closing paragraph said "0.1.91 is the current alpha build, sent for
  review, and is not in production yet", three releases out of date. It now links Play and
  names no version at all, which is the rule `tools/check_site_version.sh` already enforces
  for the site. The Drive-backup bullet also loses "(closed testing)".

## Why

The Play listing is public, so "closed testing" was false in three places and the Play card
offered no way to install from Play. Naming no APK version keeps the README from drifting the
way it had.

## Verified

- `tools/check_privacy_claims.sh` → green. `tools/check_site_version.sh` → green (0.1.98).
- Both pages rendered in Chrome at 1440: channel text, chip, button label and href correct,
  no console errors, no horizontal overflow.
- Deployed by `pages.yml` on push to `main`; the live pages are checked after the run.

## Open

- The Play URL returned 404 while this was written — the listing is still in review, so the new
  button will 404 until Google publishes it. Everything else on the page is correct either way.
- `site/faq.html`, the in-app copy and `docs/` were untouched: none of them claimed a track.
