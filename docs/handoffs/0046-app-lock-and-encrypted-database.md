# 0046 — App lock and an encrypted database (PRs #76, #77)

**Date:** 2026-10-14
**Branch:** `feat/app-lock` (#76), `feat/encrypted-database` (#77, rebased onto `dev`)
**Tests:** 557/557 on #76, 549/549 on #77 (561 together) · lint clean · license + privacy checks pass

## Why

The owner asked what should be improved; the top two items on that list were an
app lock and an encrypted database. Both are now implemented, filed as issues
first (#71–#75) so the backlog is explicit, and opened as two independent PRs.

Issues filed this session: **#71** app lock, **#72** encrypted database,
**#73** global search, **#74** budget alerts, **#75** scheduled Drive backup
(paired with #66).

## PR #76 — the app lock

Opt-in, off by default, Settings → Security.

- 4-digit PIN, stored only as a **salted PBKDF2-HMAC-SHA256 hash** (120k
  iterations, constant-time compare via `MessageDigest.isEqual`). `PinHasher`.
- Optional **biometric** shortcut (`androidx.biometric` 1.1.0, Apache-2.0),
  shown only when `BIOMETRIC_STRONG` is available and the user enabled it.
- **Policy**: locks on every cold start and after >60 s backgrounded. The grace
  window is what keeps the share sheet, the SAF picker and Drive sign-in from
  re-prompting — they are the same session. `AppLockManager.shouldLock`.
- The lock gates the whole UI in `MainActivity`: while locked, `AuraSpendNavHost`
  is **not composed**, so nothing user-owned renders behind it.
- **"Forgot PIN? Erase everything"** runs the same sweep as Settings → Delete all
  data: the logic was extracted to `AuraSpendApp.wipeAllData()` and both callers
  use it. One code path, no back door.
- `MainActivity` now extends `FragmentActivity` (BiometricPrompt requires it).
- 12 new tests: hasher round-trip/tamper/salt-uniqueness, lock policy including
  both sides of the grace window, and the PIN pad's unlock / wrong-PIN /
  forgot-PIN flows as Robolectric compose tests.
- EN + HI strings (18 keys).

## PR #77 — the database is encrypted at rest

SQLCipher Community Edition 4.19.1 (`net.zetetic:sqlcipher-android`,
**BSD-3-Clause**), both ABIs, all three flavors.

### Four things that were wrong on the way and cost real time

1. **`net.zetetic:android-database-sqlcipher` 4.6.1 does not exist.** Zetetic's
   S3 repo answers **403**, JitPack only has stale 3.x tags, and Maven Central
   hosts `android-database-sqlcipher` at **4.5.4 — the legacy `net.sqlcipher.*`
   API**, which is *not* androidx-compatible. The real artifact is
   **`net.zetetic:sqlcipher-android`**, and the classes live in
   `net.zetetic.database.sqlcipher` (not `…database.sqlite`). 4.19.1 tracks
   `androidx.sqlite` 2.7.0, so it lines up with Room 2.8.5.
2. **The recipe cannot run on the JVM.** `libsqlcipher.so` is an Android
   library: `UnsatisfiedLinkError: nativeOpen`. So the migration was put behind
   a `SqlCipherGateway` seam — 4 JVM tests cover the *policy* (fresh install,
   encrypted file left alone, plaintext exported and swapped with stale
   `-wal`/`-shm` removed, **a failed export keeps the user's records**) and the
   real recipe lives in `SqlCipherDeviceTest` (instrumented, third device-only
   path after ONNX JNI and Room).
3. **`AppDatabase.getInstance` now takes the passphrase *and* the factory.**
   Two JVM test classes instantiate the real `AuraSpendApp`, and both the
   AndroidKeyStore provider and the `.so` are absent on the host. A hidden
   Keystore call with a silent fallback would have meant "encryption
   unavailable → plaintext" shipping in production. Parameters instead:
   `RobolectricWorkManagerApp` substitutes both, visibly, in test code.
4. **`R8ReleaseSmokeTest` had been broken since 0026** — it called
   `insertTransaction`, renamed to `insertTransactionIfAbsent`, and nothing
   compiled `androidTest`, so it stayed broken across five releases unnoticed.
   Fixed, and **CI now runs `:app:compileDevDebugAndroidTestKotlin`** (no device
   needed) so the next stale device test fails a PR instead of a phone session.

### The passphrase

`DbPassphrase`: a non-exportable AndroidKeyStore HMAC-SHA256 key, HMAC over a
fixed seed, then **base64** — printable on purpose, because `ATTACH … KEY ?`
binds the passphrase as a *string* and raw random bytes cannot survive that
round-trip. Nothing stores the passphrase; a copy of the app's files cannot
produce it.

### The native audit — the interesting part

`tools/audit_native_runtime.sh` **blocked** SQLCipher: `dlopen`/`dlsym` present,
and the only named allowance was ONNX Runtime's provider loader. Investigated
rather than bypassed:

- The imports are real: `U dlopen@LIBC`, `U dlsym@LIBC`.
- They belong to **SQLite's loadable-extension support**. Evidence: the strings
  `sqlite3_load_extension`, `sqlite3_extension_init`, `load_extension`,
  `automatic extension loading failed`; and **no reference to those APIs
  anywhere in `classes.jar`** — sqlcipher-android never declares or calls them,
  Room never calls them, and Android leaves extension loading disabled by
  default.
- Also verified: **no** OpenSSL/libressl symbols or strings, **no** network
  symbols, no service URLs, no telemetry strings.

The script now supports `APPROVED_LOADERS` entries keyed on
`<library>|<sha256>|<evidence-regex>|<reason>`. Same file name, different
digest → no allowance; the message names the binary and its digest. **A
one-byte-mutated copy still blocks**, so the entry is not a rubber stamp. CI
audits both ABIs on every PR, and the ONNX paths were re-verified unchanged.

### Attribution

Verbatim Community Edition text in `docs/licenses/SQLCipher-Community.txt`,
rows in `docs/licenses/README.md` (incl. a note that SQLite is statically linked
and what was measured about OpenSSL), the in-app licence screen — which is where
Zetetic requires the notice to be user-accessible — the root `README.md` table,
and a `check_license.sh` attribution check.

## Verification

```
./gradlew :app:compileProdDebugKotlin :app:testProdDebugUnitTest \
          :app:lintProdDebug :app:compileDevDebugAndroidTestKotlin
./gradlew :app:assembleDevDebug :app:assembleProdDebug :app:assembleFdroidDebug
./tools/check_license.sh ; ./tools/check_privacy_claims.sh
./tools/audit_native_runtime.sh <aar>/jni/{arm64-v8a,armeabi-v7a}/libsqlcipher.so sqlite3_key
```

- #76: **557/557** (545 + 12), lint clean, dev + fdroid compile.
- #77: **549/549** on `dev` alone (561 together with #76), lint clean, all three
  flavors assemble, `libsqlcipher.so` present for both ABIs in each, license and
  privacy checks pass, androidTest compiles.
- Also fixed on the way: the AGP-9 `srcDir` deprecations in the fdroid source
  wiring (rule 8 — they came from PR #64), 5 `UseKtx` lint errors and 2
  `LocalContextGetResourceValueCall` errors in the new code.

## Open risks / not verified

- **Neither feature has run on a device.** The PIN gate, the biometric prompt,
  and above all the **real plaintext→encrypted migration of an existing install**
  are reasoned and unit-tested at the policy level only. Run
  `./gradlew :app:connectedDevDebugAndroidTest` with wireless debugging on, then
  install the dev build over the owner's current install and confirm the data is
  intact and the file no longer starts with `SQLite format 3`.
- The Keystore passphrase is only exercised on a device
  (`SqlCipherDeviceTest.the_keystore_passphrase_is_stable_across_calls`).
- A SQLCipher version bump **blocks the native gate** by design — re-review, then
  add the new digests.
- Drive backup payloads and CSV exports are still unencrypted (already disclosed
  in the app copy); out of scope here.

## Next

1. Owner reviews #76 and #77 (both target `dev`).
2. Device pass when wireless debugging is back: `connectedDevDebugAndroidTest`,
   app-lock flows, and the migration on the owner's real database.
3. #73 global search, #74 budget alerts, #75 scheduled Drive backup remain.
4. Still pending from before: F-Droid maintainer review of MR !52537; the
   owner-gated cleanups (GitHub Support purge, delete the temp audit dir and the
   PennyWise clone, retire repo-level signing secrets).
