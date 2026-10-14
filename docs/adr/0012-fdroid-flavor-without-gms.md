# ADR 0012 — an F-Droid flavor that drops Google Drive backup

**Status:** Accepted · 2026-10-14 · **Decision:** add a `fdroid` product flavor
built without the non-free Google dependencies

## Context

F-Droid's inclusion policy states it plainly: apps that depend on Google Play
Services cannot be built or shipped in the main repository — "upstream
developers must implement either a FLOSS alternative or a build flavour that
does not require these dependencies". AuraSpend's only Play-Services surface is
Google Drive backup: `DriveSyncManager` (play-services-auth + google-api-client
+ Drive v3). ADR 0011 deliberately pins that API for the dev/prod builds;
nothing about it can be bundled in an F-Droid build.

The owner asked for F-Droid distribution (2026-10-14). A GMS-free
*authentication* path (AppAuth or credential flows over the same Drive REST
API) would remove the dependency from every flavor, but it is the larger
migration ADR 0011 already scopes out — and today it would still be
device-verification work on the one feature that can lose user data if it
fails.

## Decision

`fdroid` joins the `env` flavor dimension (same application id as prod). It:

- depends on **none** of `play-services-auth`, `google-api-services-drive`,
  `google-api-client(-android)`, `google-http-client-gson`;
- compiles a **no-op `DriveSyncManager`** from `app/src/fdroid/java` — the real
  implementation moved to `app/src/gms/java`, wired into dev/prod via
  `sourceSets`; the real file stays the only source in the repo that imports
  `com.google.*`;
- sets `BuildConfig.DRIVE_BACKUP = false`; Settings → Data and the onboarding
  restore card hide the Drive entry points, so no dead buttons exist;
- changes nothing else: every other feature, resource and string is shared.

This is a deliberate, documented exception to the "flavors change configuration
only" rule (ADR 0010). Drive backup is the *only* difference.

## Consequences

- F-Droid can build and ship the app with no non-free dependency in the APK.
  `pr_check.yml` builds `assembleFdroidDebug` on every PR so the flavor cannot
  rot: a shared-code change that assumes a Drive-only type fails in CI.
- F-Droid users have no Drive backup. CSV export/import works, and the README
  says so per install source.
- Signing: F-Droid signs with its own key, so its APK cannot update over the
  Play/GitHub builds (package ids match, signatures differ). That is the
  standard F-Droid trade-off.
- The unification path is unchanged and still tracked: a GMS-free sign-in
  (ADR 0011's migration) would let every flavor keep Drive. Until then, the
  no-op stub is the honest answer.
- Metadata for the fdroiddata merge request lives in `docs/fdroid/`.

## References

- ADR 0011 — `play-services-auth` pinned for dev/prod
- F-Droid inclusion policy — free software requirement; non-free dependencies
- `app/build.gradle.kts` — flavor, `sourceSets`, per-flavor dependencies
