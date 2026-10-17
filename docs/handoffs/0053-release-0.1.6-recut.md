# 0053 — 0.1.6 re-cut without the terracotta UI

**Date:** 2026-10-17
**Release:** `v0.1.6` — versionCode **8**, versionName **0.1.6**
**Tag commit:** `8ac8445c44b54e0f754a06fe27db42acf1f4aa7c`
**CI run:** [39031663532](https://github.com/auraspend/auraspend/actions/runs/39031663532) — **success**
**GitHub release:** <https://github.com/auraspend/auraspend/releases/tag/v0.1.6>
**Tests:** 616/616 (1 skipped); lint clean (1 hint, 46 baseline errors);
licence + privacy checks pass.

## Why this exists

The first `v0.1.6` tag (`8e87515`, run 39027254519) included handoff 0048's
terracotta UI. That was a release mistake: 0.1.6 was supposed to contain only the
classification code changes and the work that predates the UI revamp. 0052
records that first release; **it is superseded by this handoff and its artifacts
must not be used.**

## What changed

- Reverted `bb629c1` (`feat(ui): replace the purple brand with terracotta clay`)
  on `main`. The app is back to the Aurora palette, Plus Jakarta Sans and the
  pre-terracotta icon. The revert is commit `1e1288c`.
- Kept the classification audit fixes (0047 + 0050). The only conflict was
  `TransactionDetailScreen.kt`; it now has **both** the old `extendedColors`
  import (for the Aurora UI) and `Dispatchers` (for category learning).
- Version is **0.1.6 / versionCode 8**. VersionCode 7 is already on Play alpha;
  Play requires a strictly higher code for the replacement.
- Changelog and Play notes are classification-only. The terracotta bullet is
  gone; the notes are EN 474 chars / HI 447 chars (≤500).
- Store visuals are unchanged and now **match the app**: both are
  pre-terracotta.
- F-Droid metadata points at `v0.1.6`, versionCode 8.

## Release mechanics

- `main` / `dev` were fast-forwarded to `8ac8445` and pushed.
- The old GitHub release and tag were deleted, then `v0.1.6` was re-created on
  `8ac8445` and pushed. The corrected tag run rebuilt and uploaded.
- Play alpha now holds **versionCode 8**; the versionCode 7 (terracotta) upload
  was superseded before any production rollout.
- GitHub release assets: `AuraSpend-V0.1.6.Alpha.aab` (24,380,959 bytes),
  `AuraSpend-V0.1.6.Alpha.apk` (43,282,753 bytes), `SHA256SUMS`.
- The downloaded APK reports `versionCode=8`, `versionName=0.1.6`; its only
  packaged font is the 176,288-byte Plus Jakarta Sans (no Manrope/Fraunces).

## Verification

| Check | Result |
|---|---|
| `:app:testProdDebugUnitTest` | 616 tests, 0 failures, 1 skipped |
| `:app:lintProdDebug` | clean (1 hint, 46 baseline errors) |
| licence / privacy scripts | both pass |
| local signed AAB/APK | `CN=AuraSpend`, versionCode 8 / 0.1.6 |
| corrected CI run | success, Play upload + release publish green |
| downloaded release APK | versionCode 8, single Plus Jakarta font |
| tag | `v0.1.6` → `8ac8445` |

## Open

- Play review/rollout is a Console action; 0.1.6 (8) is on alpha, not production.
- The Play **listing text** is still manual: the tag workflow uploads the AAB and
  What's New notes only. Paste the canonical copy from
  `docs/store/play-listing-en-US.md` into the Console.
- F-Droid metadata is current, but the fdroiddata MR remains an owner step.
- The terracotta UI is not shipped; it remains an unmerged design exploration
  (0048), not part of 0.1.6.
