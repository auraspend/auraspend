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
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDevDebugAndroidTest \
          -Pandroid.testInstrumentationRunnerArguments.class=com.awbuilds.auraspend.data.local.SqlCipherDeviceTest
./tools/check_license.sh ; ./tools/check_privacy_claims.sh
./tools/audit_native_runtime.sh <aar>/jni/{arm64-v8a,armeabi-v7a}/libsqlcipher.so sqlite3_key
./tools/verify_r8_release.sh
```

- #76: **557/557** (545 + 12), lint clean, dev + fdroid compile.
- #77: **549/549** on `dev` alone (561 together with #76), lint clean, all three
  flavors assemble, `libsqlcipher.so` present for both ABIs in each, license and
  privacy checks pass, androidTest compiles.
- Also fixed on the way: the AGP-9 `srcDir` deprecations in the fdroid source
  wiring (rule 8 — they came from PR #64), 5 `UseKtx` lint errors and 2
  `LocalContextGetResourceValueCall` errors in the new code.
- **`tools/verify_r8_release.sh` on a real prod release bundle**: 3.89 MB DEX,
  99.07% obfuscated, 16 KB aligned, ONNX *and* SQLCipher JNI classes kept
  un-renamed. The gate's JNI check was extended to
  `net.zetetic.database.sqlcipher.SQLiteConnection` /
  `SupportOpenHelperFactory` — the AAR ships `-keep class
  net.zetetic.database.** { *; }`, and the check exists so a future version that
  drops it fails a build instead of shipping a release where the encrypted
  database cannot open.

**CI:** both PRs are green. #76 `PR Check` success in 9m46s; #77 success with
every step green, including the new "Instrumented tests compile" gate and the
extended native audit. The audit step is named after what it covers (ONNX arm64
+ SQLCipher, every shipped ABI) so the label cannot drift from the work.

## The emulator run, and the three defects it found

The owner's phone was unreachable (wireless debugging off), so the device suite
was run on the local arm64 emulator instead:

```
$ANDROID_HOME/emulator/emulator -avd Android_17 -no-window -no-audio \
    -no-boot-anim -gpu swiftshader_indirect -no-snapshot-load
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDevDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=...SqlCipherDeviceTest
```

(The AVD's `/data` was 93 % full and the first install died with
`INSTALL_FAILED_INSUFFICIENT_STORAGE`; `-wipe-data` fixed it.)

It was worth every minute, because **three release-blocking defects passed the
entire JVM suite, lint, and the licence/privacy gates** — none of which can load
an Android `.so`:

1. **Nothing loads `libsqlcipher.so`.** Disassembling `sqlcipher-android-4.19.1`
   shows no class calls `System.loadLibrary` — `SupportOpenHelperFactory` only
   carries a passphrase and `SQLiteDatabase`'s static initializer sets up maps.
   Without an explicit load, the first database access throws
   `UnsatisfiedLinkError: … SQLiteConnection.nativeOpen`. This was a
   crash-on-first-use in production, and the app's own database file sat at
   **0 bytes** on the emulator as evidence. `SQLCipherLibrary.ensureLoaded`
   (`System.loadLibrary("sqlcipher")`) now does it before the migration and
   before Room.
2. **The documented `ATTACH` recipe does not work on Android.** Attaching an
   encrypted target *from a connection opened without a key* fails with
   `SQLITE_CANTOPEN` — all four spellings measured (literal/bound path ×
   literal/bound key), and a plain `ATTACH` with no key also failed to create
   its file. The export now runs the other way, which is SQLCipher's documented
   form for encrypting an existing database: open the encrypted **target** with
   the key, attach the **plaintext** source with `KEY ''`, and
   `sqlcipher_export('main', 'plaintext')`. A throwaway diagnostic test on the
   emulator is what settled it; it has been deleted. Note the side effect: with
   the old code every existing install's upgrade would have **thrown and left
   the database un-migrated**.
3. **SQLCipher ignores Room's journal mode.** `setJournalMode(WRITE_AHEAD_LOGGING)`
   never reaches SQLCipher's factory — the device showed
   `auraspend_db-journal`, i.e. a rollback journal, so the app would have lost
   the concurrent-read behaviour the Room builder explicitly asks for. WAL is
   now requested in the factory, and the device test asserts
   `PRAGMA journal_mode == wal`.

Also fixed before this: the swap deleted the plaintext original *before*
renaming the encrypted file in, so a crash in that window stranded the user's
records in an `auraspend_db.enc` nothing looks at. It is now three renames plus a
recovery step (`recoverInterruptedSwap`), with four JVM tests covering each
crash point.

**Device result:** `SqlCipherDeviceTest` — 4 tests, all green, twice in a row
after the fixes, and deterministic (the app's-database test calls the app's own
idempotent seeding instead of racing the startup coroutine).

What the device run does **not** cover: the R8-minified release build. The R8
gate proves the JNI classes survive and the DEX is 3.89 MB, but no test runs
against a minified binary (`testBuildType` was tried in 0045 and reverted for
good reasons). Installing a signed release build on the emulator and watching
the first launch would close that last gap.


## The app lock on a device too

`feat/app-lock` gained `AppLockDeviceTest` the same way — it launches the real
`MainActivity` and asserts the claim the lock actually makes: while locked, the
navigation host is **not composed**, so neither the app's splash nor the
dashboard is behind the gate. Entering the PIN dismisses it and the app becomes
usable; a wrong PIN leaves it up. Two tests, green on the emulator, pushed to
#76.

Two things had to be fixed to get them running, both worth remembering:

- **Espresso 3.7.0 had to be pinned in the catalog.** Compose's test rule drives
  Espresso for idling, and the 3.5.0 it pulls transitively calls
  `InputManager.getInstance()` — removed in API 37 — so *every* Compose
  assertion on the current emulator image died inside `onIdle`. Device-side
  Compose testing is now possible for future work.
- **The unlock assertion had to wait rather than demand.** The first version
  wanted the dashboard immediately and failed on a fresh install for the right
  reason: the app was showing its splash, because onboarding had not been
  completed yet.

## Open risks / not verified

- **The app lock has not run on the owner's phone** — but it *has* run on the
  emulator (`AppLockDeviceTest`, 2 tests green): cold start gates the app, the
  right PIN unlocks, a wrong PIN does not, and nothing app-owned is composed
  behind the gate. What is still unverified on a device: the **biometric
  prompt** (the emulator has no enrolled fingerprint) and the "Forgot PIN" wipe.
- **The migration has not been run against the owner's real database** (a
  real install with real history). The emulator proved the recipe on a
  synthetic plaintext fixture with the same shape; doing it for real
  is still the first thing to try when the phone is reachable, and the
  0.1.4→next upgrade is when it will actually run for users.
- **No test runs against the R8-minified build.** The release gate proves the
  JNI classes are kept and the bundle builds; installing a signed release APK on
  the emulator would close it.
- The Keystore passphrase is exercised on a device only
  (`SqlCipherDeviceTest.the_keystore_passphrase_is_stable_across_calls`) — covered.
- A SQLCipher version bump **blocks the native gate** by design — re-review, then
  add the new digests.
- Drive backup payloads and CSV exports are still unencrypted (already disclosed
  in the app copy); out of scope here.

## Session note for the next agent

A device-only path can be verified **without the owner's phone**: the local AVD
(`Android_17`, arm64, API 37.1) boots headless and runs
`connectedDevDebugAndroidTest` in about a minute. If `INSTALL_FAILED_INSUFFICIENT_STORAGE`
appears, the AVD's `/data` is full — relaunch with `-wipe-data`. Three
production defects in this PR were found that way and by nothing else, plus two
harness problems for the lock tests (Espresso 3.7.0 needed for API 37; a fresh
install shows its splash, not the dashboard). It is the fastest feedback loop
this project has for anything touching a `.so` or the activity lifecycle.


## Next

1. Owner reviews #76 and #77 (both target `dev`).
2. Device pass when wireless debugging is back: `connectedDevDebugAndroidTest`,
   app-lock flows, and the migration on the owner's real database.
3. #73 global search, #74 budget alerts, #75 scheduled Drive backup remain.
4. Still pending from before: F-Droid maintainer review of MR !52537; the
   owner-gated cleanups (GitHub Support purge, delete the temp audit dir and the
   PennyWise clone, retire repo-level signing secrets).
