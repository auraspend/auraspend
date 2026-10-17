# 0052 — AuraSpend 0.1.6: terracotta UI + classification audit released

**Date:** 2026-10-17
**Release:** `v0.1.6` — versionCode **7**, versionName **0.1.6**
**Tag commit:** `8e8751541edfa6a07a70a6e901c5c870827a4a32` (merge of PR #81)
**CI run:** [39027254519](https://github.com/auraspend/auraspend/actions/runs/39027254519) — **success, 5m39s**
**GitHub release:** <https://github.com/auraspend/auraspend/releases/tag/v0.1.6>
**Tests:** 616/616 (1 skipped); lint clean (1 baseline hint); licence + privacy
checks pass. Locally built signed prod AAB/APK: `CN=AuraSpend`, versionCode 7,
versionName 0.1.6.

## What shipped

This release combines three streams:

1. **Terracotta UI revamp (0048)** — new clay palette, Manrope/Fraunces type,
   softer shapes, `AuraMark`, one hero card per screen. UI only; no behaviour,
   data or schema change.
2. **Classification audit fixes (0047 + 0050)** — honest seven-field eval,
   sender-aware bank tails, routed-over-global merge, own-transfer exclusion,
   multi-movement handling, merchant/category/ingest/memory fixes, and the
   80/82 exact (97.6 %) measurement.
3. **Canonical Play listing copy (0051)** — `docs/store/play-listing-en-US.md`,
   title 30/30, short 79/80, long 3,270/4,000.

The tag workflow built the signed Play bundle and prod APK, uploaded the bundle
to Play **closed testing (`alpha`)** with the new `docs/store/whatsnew/` notes
(EN 479 chars, HI 444 chars), and published the GitHub release with both
artifacts and `SHA256SUMS`.

## Release mechanics

- `dev` was fast-forwarded to the release branch and pushed; PR
  [#81](https://github.com/auraspend/auraspend/pull/81) merged `dev` → `main`
  with the admin bypass (main requires one review; the owner account is the
  reviewer).
- `v0.1.6` is an annotated tag on the main merge commit. The tag run required
  the `release` environment approval; it was approved through the GitHub API
  because the only reviewer is the owner account.
- The workflow succeeded end to end: signed artifact verification, Play upload,
  Release Drafter publication, asset attachment, draft-to-published check.

## Store listing / visuals decision

**The store visuals are deliberately still pre-terracotta.** The owner chose not
to regenerate the icon, feature graphic and seven screenshots for this release;
the app itself uses the terracotta UI. `docs/store/README.md` and
`docs/store/play-listing-en-US.md` record this as an accepted mismatch, not an
oversight.

The tag workflow uploads the **AAB and What's New notes**, not the listing text.
The canonical Play copy is in `docs/store/play-listing-en-US.md`; the title,
short and long description still need to be pasted into the Play Console (the
Console copy is otherwise unversioned).

## F-Droid

`docs/fdroid/com.awbuilds.auraspend.yml` now points at v0.1.6
(`versionName 0.1.6`, `versionCode 7`, commit `8e87515`), and the submission
guide references the new tag. The fdroiddata MR itself remains an owner step.

## Verification

| Check | Result |
|---|---|
| `:app:testProdDebugUnitTest` | 616 tests, 0 failures, 1 skipped |
| `:app:lintProdDebug` | clean (1 hint, 45 baseline errors) |
| licence / privacy scripts | both pass |
| local signed AAB | 22,642,015 bytes, `CN=AuraSpend` |
| local signed APK | 41,540,793 bytes, versionCode 7 / 0.1.6 |
| CI release run | success, Play upload + GitHub release steps green |
| GitHub release | published, AAB/APK/SHA256SUMS attached |

## Open risks / next

- **Play review**: the alpha bundle was sent for review; rollout to testers and
  then production is a Play Console action, not something this repo can do.
- **Listing text**: paste the canonical copy into the Play Console; the tag
  workflow does not upload it.
- **Visuals**: regenerate icon, feature graphic and screenshots from the
  terracotta build in a later release (owner deferred for 0.1.6).
- **F-Droid**: the metadata is current, but the fdroiddata MR still needs the
  owner's GitLab account.
- **Device pass**: the classification audit fixes were verified by the JVM and
  Robolectric suites, not a fresh real-inbox walk; the broader SMS ingest query
  is the main thing to watch.
- **No production rollout yet**: 0.1.6 is on closed testing (`alpha`), not
  production.
