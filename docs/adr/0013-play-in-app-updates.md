# ADR 0013 — Play-only in-app updates

**Status:** Accepted · 2026-10-17 · **Amends ADR 0012** · **Amended by ADR 0014**
("The app does not force an update on launch" no longer holds for a release the
owner marks urgent — see ADR 0014.)

## Context

AuraSpend ships three flavors: `dev`, `prod` (Play and the GitHub APK) and
`fdroid`. F-Droid's inclusion policy forbids Google Play Services and other
non-free Play dependencies, so the `fdroid` build already omits Drive backup
(ADR 0012).

Google Play's In-App Updates API requires the Play Core SDK
(`com.google.android.play:app-update`, Play Core SDK Terms). It can only work
for an install that Play itself installed; a sideloaded or F-Droid build cannot
use it. The owner asked for an in-app update check in the Play build only, not
in the F-Droid build.

## Decision

- Add `com.google.android.play:app-update` to **dev and prod only**. The
  `fdroid` flavor carries no Play Core dependency at all.
- Put the Play implementation in `app/src/gms/.../data/update/` and a no-op in
  `app/src/fdroid/.../data/update/`, with one shared `AppUpdateController`
  interface in `main`.
- Set `BuildConfig.IN_APP_UPDATES = true` only in `prod`. The Settings → About
  "Check for updates" row is hidden everywhere else.
- Prefer a **flexible** update when Play allows it; an immediate update is used
  only when Play says flexible is not allowed. After a flexible download the
  row becomes "Update ready — tap to restart" and calls `completeUpdate()`.
- The app does not force an update on launch; the check is user-triggered from
  Settings. That avoids blocking a user who is in the middle of entering a
  transaction.

## Consequences

- Play users can update without leaving the app; F-Droid users continue to
  update through F-Droid.
- The F-Droid build remains free of Play Services (verified by compiling
  `:app:compileFdroidDebugKotlin` with no Play Core on its classpath).
- This is a second Play-only surface in addition to Drive backup. ADR 0012's
  "Drive backup is the only difference" no longer holds; the two documented
  differences are Drive backup and in-app updates.
- Play Core is under the Play Core SDK Terms, not an open-source licence. It is
  attributed in the in-app licences screen and `docs/licenses/README.md`, and
  it is **not** in the F-Droid build.
- The update check cannot be exercised on the JVM; verification is build-level
  plus a real Play-installed device check.

## References

- ADR 0012 — F-Droid flavor without Play Services
- Play In-App Updates — <https://developer.android.com/guide/playcore/in-app-updates>
- Play Core SDK Terms — <https://developer.android.com/guide/playcore/license>
