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

## On a real phone (Moto G40 Fusion, 2026-10-15)
The owner connected their phones, so the emulator work was repeated on real
hardware — and this is where the remaining claim was settled.

**The migration ran on a real device, against a real database.** The Moto had a
`0.1.3-dev` install whose data was almost entirely in its WAL (main file 4,096 B,
`-wal` 218 KB, schema v8) — the exact shape the recipe has to survive. After
updating it in place to the new dev build:

| Check | Result |
|---|---|
| Startup | clean; `SQLCipherLibrary: libsqlcipher.so loaded` in logcat |
| File header | random bytes — no longer `SQLite format 3` |
| File size | 4,096 B + 218 KB WAL → 102,400 B encrypted |
| Leftovers | no `.plain.bak`, no `.enc` — the crash-safe swap cleaned up |
| Journal | a fresh 16 KB `-wal` — the app now runs WAL, as intended |
| Data | `the_apps_own_database_is_encrypted_at_rest` passed: encrypted **and** the seeded rows (which lived in the WAL) were readable |

**The full instrumented suite: 10/10 on the phone, 11/11 on the emulator**, with
the phone's screen asleep at the start. Running it exposed three defects that
per-class runs had hidden:

1. **`DemoDataSeeder` closed the application's singleton database** — every test
   after it in the same process failed with *"Database is closed"*, and every
   routine run **wiped the app's data first**. It is opt-in now
   (`-e seedDemoData true`) and closes nothing it did not open.
2. **Compose emits no semantics while the display sleeps.** The lock tests timed
   out on a phone whose lock screen was rendering perfectly; the test wakes the
   screen itself now.
3. **The PIN has to exist before `MainActivity.onCreate` runs** — a PIN set after
   the activity launches locks nothing. It is set in an outer rule that wraps the
   compose rule, with cleanup in a `finally`.

And the lock was confirmed by hand on the phone: PIN set, the lock screen
rendered, tapping `2-4-6-8` on the physical keypad unlocked it, and the app came
up fully usable — dashboard, ₹0 balance, "No transactions yet" — reading its
encrypted database.

### The fourth defect, and it was not a test artefact

After the vivo run, its dev app came back up **wearing a PIN no human had
chosen**. `AppLockManager.clearPin()` used the KTX `edit {}` default, which is
`apply()`: the write is asynchronous, and when the instrumentation killed the
process the removal never reached disk.

That is a security bug in both directions, not a test artefact — a PIN that
never lands leaves the app **unlocked while the user believes it is locked**, and
a PIN that lingers after "turn the lock off" leaves it locked. The four writes
that make up the lock's state machine (`setPin`, `clearPin`, `isEnabled`,
`useBiometric`) now commit synchronously; `lastBackgroundedAt` keeps the async
path, because it is written on every backgrounding and is not a security
decision. Re-verified on the vivo: 2/2, and no PIN or enabled flag in the prefs
afterwards.

### The owner's own rows, migrated on their own phone (vivo V2158)

The Moto run proved the recipe; this one proves it on the data that matters. The
production install is Play-signed, so its database cannot be read from a
workstation — the owner exported it through the app instead (**492 transaction
records**, 1,095 file lines, 121 of them carrying embedded newlines), and the
rehearsal carried those rows into a pre-encryption dev build, upgraded it in
place, and compared fingerprints:

| Step | Result |
|---|---|
| Import through `CsvManager` | `parsed=492 imported=492 skippedRows=0 written=492` |
| Fingerprint A (pre-upgrade) | transactions 492, categories 13, total `rows=505 sha256=b088c22610231d3970de2de5767873a37c8ffe3fb5bd9fd816f0397e02281b02` |
| In-place upgrade | `libsqlcipher.so loaded`; header no longer `SQLite format 3`; only `auraspend_db`, `-wal`, `-shm` left |
| Fingerprint B (post-upgrade) | **identical — every table and the total** |
| The app itself | dashboard renders the balance, the cash-flow chart and the recent list from the encrypted database |
| The production app | untouched: `versionName=0.1.4`, `lastUpdateTime` unchanged |

The harness (`MigrationRehearsalTest`) and the runbook
(`docs/release/migration-rehearsal.md`) are in #77, and the data copies — mine
on the workstation and the app's staged file — were deleted afterwards; the
owner's own export is left in their Downloads for them to delete.

One trap for the next operator, because it happened here: `diff` on raw logcat
lines reports a difference that is not there (timestamps and pids). Strip the
prefix first; the runbook now says so.

### What could not be tested, and why

**Both phones' production installs are Play-signed** (identical APKs, signer
`O=Google Inc.` — Google's app-signing key), so no locally built APK can be
installed over them, and their data directories are unreadable without root.
Their *rows*, however, were rehearsed — see the vivo section below: the app's own
export carried 492 real transactions into a pre-encryption build, the upgrade ran
in place, and the fingerprints matched. What still cannot be done by hand is
upgrading the production install itself; that happens on the next Play release.

Two ways to close that earlier, if wanted:
- **CSV route** — export from Settings in the production app (it holds the rows),
  then import into a pre-encryption dev build and update that: the migration then
  runs on the owner's own transactions.
- **Play route** — ship the next closed-testing release and watch the first
  launch. If it ever fails, the crash-safe swap leaves the plaintext database
  intact and a rebuild of the previous version still opens it.

## Open risks / not verified

- **The app lock has been verified on the owner's phone** (manual: PIN set, lock
  screen, `2-4-6-8` on the physical keypad, unlocked to a working dashboard) and
  automatically on a Moto G40 and the emulator. What is still unverified on a
  device: the **biometric prompt** (no fingerprint enrolled on the test devices)
  and the "Forgot PIN" wipe (deliberately not run against real data — it is
  unit-tested, and the emulator covers the confirm dialog).
- **The owner's production database has not been migrated**: their Play-signed
  install cannot be side-loaded over, so it migrates on the next Play release.
  The recipe itself is now proven on a real device against a real schema-v8
  database whose data lived in the WAL — see above.
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
appears, the AVD's `/data` is full — relaunch with `-wipe-data`. On a real phone,
two things are needed that the emulator never teaches: **keep the screen awake**
(Compose has no semantics while the display sleeps) and remember that
`connectedAndroidTest` **uninstalls** the app afterwards, so a data-bearing
install must be exercised with a manual `am instrument` run instead. Three
production defects were found this way and by nothing else.


## F-Droid MR !52537: closed for template/structure, fixed and reopened

The maintainer reviewed it the same evening: **closed**, labelled
`waiting-on-response`, with three comments —

> "no need to provide any verification"
> "closing this MR since there is no app inclusion template nor the yaml
> structure is followed, please only re-open this MR only when you included app
> inclusion template and correct yaml structure"
> "remove comments from yaml and have a look at any other yaml to correct it -
> have a look at !52534 (diffs)"

Everything was read from the MR itself (description, both threads, the closure,
the diff) and then checked against what the maintainer pointed at:
`templates/build-gradle.yml` (which opens with *"PLEASE REMOVE ALL COMMENTS
BEFORE SUBMITTING TO F-DROID DATA!"*), the Build Metadata Reference, and
FilmLab's `!52534` diff as the model file.

**Three defects were real, not just the comments:**

1. **`sdks:` is not a metadata field at all** — zero occurrences in the Build
   Metadata Reference. The SDK pins became
   `prebuild: sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0'` —
   exactly the packages our own CI installs (`.github/workflows/pr_check.yml`),
   so it is evidence rather than a copy of FilmLab's line.
2. **`AutoUpdateMode: Version v%v` is wrong with `UpdateCheckMode: Tags`** — the
   reference says Tags requires `Version` *without any pattern*, because the
   checked tag is used directly. It is now plain `Version` with
   `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` (our tags are `v0.1.2`…).
3. **The 12-line comment header** — the template forbids it in capitals.

`MaintainerNotes` was dropped to match the model file; its content (the F-Droid
flavour has no Play Services, the optional model download, no other network use)
moved into the MR description where reviewers read it. `SUBMISSION.md` no longer
describes fields that are gone.

The MR description is now the **filled App inclusion template** (Policy / Docs /
MR Setup / Metadata / Pipeline, each box ticked with an annotation), with the two
honest non-ticks: reproducible builds declined with the flavour reason — and the
template's own note that no phone number or card details were submitted, asking
for a CI trigger.

**How it was applied:** our staging copy and `SUBMISSION.md` through a normal
commit on `dev` (`3cb0268`); the fork's branch `add-auraspend` and the MR
description, reopen and comment through the owner's logged-in GitLab session
(the stored git credential can read the fork but not push to it — 403 — and
GitLab's API needs a session for writes). Verified afterwards from the API: MR
`opened`, one file changed (+26/−0), no comments in the YAML, template sections
present, `blocking_discussions_resolved: true`.

**Still needed:** a maintainer CI trigger ("the fork's pipelines fail with zero
jobs" is stated in the description and in the comment, per the template), then
the Reports tab and any review comments. The stale `waiting-on-response` label is
the maintainers' to clear.

## Next

1. Owner reviews #76 and #77 (both target `dev`).
2. F-Droid: wait for the maintainer's CI trigger on !52537 and answer anything
   the Reports tab or review raises.
3. #73 global search, #74 budget alerts, #75 scheduled Drive backup remain.
4. Still pending from before: the owner-gated cleanups (GitHub Support purge,
   delete the temp audit dir and the PennyWise clone, retire repo-level signing
   secrets).
