# 0055 — Play-only in-app updates; 0.1.7 released

**Date:** 2026-10-17
**Release:** `v0.1.7` — versionCode **9**, versionName **0.1.7**
**Tag commit:** `dad3e44a7e2b5c4e3b3fe5bca53afdeec3f5a06a`
**CI run:** [39034678870](https://github.com/auraspend/auraspend/actions/runs/39034678870) — **success**
**GitHub release:** <https://github.com/auraspend/auraspend/releases/tag/v0.1.7>

## What shipped

**Settings → About → Check for updates** in the Play build. It:

- asks Play whether an update is available;
- starts a **flexible** download when Play allows it (immediate only if
  flexible is not allowed);
- shows `Downloading update…`, then **Update ready — tap to restart**, which
  calls `completeUpdate()`;
- never forces an update and never prompts on launch.

The row is hidden unless the app was **installed by Play**
(`getInstallSourceInfo(...).installingPackageName == "com.android.vending"`).
The GitHub APK is prod but sideloaded, so it does not show the row either.

## Architecture

- `main`: `AppUpdateController` interface + `AppUpdateState`; no Play Core
  references.
- `src/gms`: `PlayAppUpdateController` using `com.google.android.play:app-update`
  2.1.0, plus `createAppUpdateController`.
- `src/fdroid`: no-op `AppUpdateController`; the F-Droid build carries **no Play
  Core dependency**.
- `BuildConfig.IN_APP_UPDATES` is true only in `prod`; the Settings row also
  requires the Play installer check.
- `AppUpdateController` is lazily created on the Application, so tests and
  f-droid never touch Play Core at startup.
- ADR 0013 documents the second Play-only surface (Drive backup remains the
  first, ADR 0012). Play Core is attributed in the licence screen and
  `docs/licenses/README.md`; it is absent from F-Droid.

## Verification

| Check | Result |
|---|---|
| `:app:testProdDebugUnitTest` | 616 tests (1 skipped), 0 failures |
| `:app:lintProdDebug` / `:app:lintFdroidDebug` | clean (baseline-only) |
| licence / privacy scripts | pass |
| `:app:bundleProdRelease` / `:app:assembleProdRelease` | signed, versionCode 9 / 0.1.7 |
| `:app:assembleFdroidRelease` | builds without Play Core |
| fdroid dependency graph | no `com.google.android.play:app-update` |
| prod APK | contains the Play Core update service; versionCode 9 |
| release run | success; Play alpha upload + GitHub release |

GitHub assets: `AuraSpend-V0.1.7.Alpha.aab`, `.apk`, `SHA256SUMS`.

## Caveats

- The actual update dialog/download/restart flow **has not been exercised on a
  Play-installed device**. It requires an install whose installer is Play and a
  newer version on the alpha track; the build-level checks only prove the code
  and dependency split. That is the first thing to verify on a test phone.
- The F-Droid APK's licence screen is shared, so it mentions the Play Core
  component even though the library is absent; the fdroid dependency graph and
  APK class scan confirm no Play Core code is packaged. This is a copy polish
  item for a later build.
- The Play **tags** remain manual (public API has no tag field); the title is
  `AuraSpend: SMS Expense Tracker`.
- 0.1.7 is on Play closed testing (`alpha`), sent for review, not production.

## Next

- Verify the update flow on a Play-installed test device.
- If it works, promote 0.1.7 to production when ready.
- Consider an automatic check-on-launch later; deliberately not added now so a
  user mid-entry is never interrupted.
