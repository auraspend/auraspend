# ADR 0011 — `play-services-auth` stays pinned until Credential Manager lands

**Status:** Accepted · 2026-10-11 · **Decision:** keep the deprecation, with a written expiry

## Context

Every Google Drive path in the app — onboarding restore, Settings → Data backup
and restore — goes through `data/remote/DriveSyncManager.kt`, which is built on
`GoogleSignIn`, `GoogleSignInClient` and `GoogleSignInOptions`. Google has
deprecated that API in favour of Credential Manager, so the compiler emits
deprecation warnings on the Drive critical path in every build.

AGENTS.md rule 8 requires every warning to be fixed, suppressed with a written
reason, or recorded as safe. Handoffs 0023–0025 listed these warnings and moved
on without doing any of the three, which is how they became institutionalised
scroll-past noise: a removal would land with zero compiler signal.

The app asks for exactly two things from sign-in:

- `Scope(DriveScopes.DRIVE_FILE)` — the narrow per-file Drive scope, so the app
  can only ever see the backup file it created, and
- the signed-in account, which `GoogleAccountCredential` then uses to obtain
  access tokens for the Drive v3 REST client.

## Decision

**`play-services-auth` (21.6.0) is pinned deliberately. The Credential Manager
migration is not part of this change.** It is a separate, larger piece of work
with a device-verifiable acceptance test, and half-migrating it would risk the
one feature that can lose user data if it fails (restore).

The pin is not a permanent exemption. It expires when any of these happens:

- Google announces a removal date for `GoogleSignIn`/`GoogleSignInClient`, or
- the Play SDK index / Play Console flags `play-services-auth`, or
- the next `targetSdk` bump makes the old API unusable.

Why it is not a drop-in swap:

- **The account, not just the token.** `GoogleSignIn.getLastSignedInAccount()`
  gives a silently re-obtainable account for `GoogleAccountCredential`.
  Credential Manager's `GoogleIdOption` returns a credential/token per request;
  the silent-reuse path and the sign-out path (`clearCredentialState`) have to be
  rebuilt around that.
- **OAuth client constraints.** An Android OAuth client is registered against a
  package id **and a signing certificate**. `dev` and `prod` have different
  package ids (`.dev` suffix) and therefore different clients — the dev client
  lives in Google Cloud, not in this repo (ADR 0010, handoff 0018). A migration
  must keep both registrations, plus the `drive.file` scope declaration on the
  OAuth consent screen, working.
- **Nothing in CI can verify it.** There is no automated coverage of the Drive
  round trip (review finding 25); sign-in needs a real Google account and a
  device, so a migration landed blind would be discovered by users, on the
  restore path.
- **The risk is bounded while pinned.** The API is deprecated, not removed: the
  dependency still resolves, builds and runs. The warnings stay visible in every
  compile, and this ADR is what they now point at.

## Consequences

- The deprecation warnings on `DriveSyncManager` remain, and that is now a
  recorded decision rather than an untriaged warning. `ui/navigation/NavGraph.kt`
  points at this ADR where the sign-in launcher is wired.
- A Credential Manager migration must touch, at minimum:
  - `data/remote/DriveSyncManager.kt` — client, sign-in intent, result handling,
    silent sign-in, token acquisition;
  - `ui/navigation/NavGraph.kt` — the two `StartActivityForResult` sign-in
    launchers and the pending-action state;
  - `gradle/libs.versions.toml` + `app/build.gradle.kts` — add
    `androidx.credentials` (+ the Google ID provider) and drop or narrow
    `play-services-auth`; `google-api-client` stays, because the Drive v3 client
    is not what is deprecated;
  - the attribution surface if the graph changes: `LicenseScreen.THIRD_PARTY`,
    `docs/licenses/README.md`, `README.md` and the `Google API Client` pattern in
    `tools/check_license.sh`;
  - the Drive setup docs (`secrets.properties.example`, README) for any new
    client requirement;
  - and it needs a manual on-device sign-in → backup → restore round trip
    recorded as evidence, because no test covers it.
