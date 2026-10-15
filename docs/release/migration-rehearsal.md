# Migration rehearsal: proving the encryption upgrade on your own rows

The database-at-rest encryption (PR #77) runs a **one-time, in-place upgrade** of
an existing database the first time the app starts after the update. For every
user that happens once, unattended, on data they cannot get back.

Your production install is **Play-signed**, so its database cannot be read from a
workstation at all — not with `adb`, not with `run-as`. The only honest way to
rehearse the upgrade on your rows before the release reaches everyone is to
carry them over yourself, through the app's own export, and then compare what
the app sees before and after.

This runbook does exactly that. **Level 1** carries your transactions (the money
data) with two taps from you. **Level 2** adds a full-fidelity snapshot — every
table, including the SMS queue and budgets — at the cost of one more export you
fetch from Drive.

The harness is `MigrationRehearsalTest` in `app/src/androidTest/`. All five
actions are opt-in and none of them run in a normal test suite.

---

## 0. What is and is not touched

| | |
|---|---|
| **Your production app** | never touched. The rehearsal installs the **dev** package (`com.awbuilds.auraspend.dev`), which is a separate app with separate data. Verify at any time: `adb shell dumpsys package com.awbuilds.auraspend \| grep versionName` — it stays `0.1.4` with an unchanged `lastUpdateTime`. |
| **The exported data** | keep it **outside the repository** and delete it when the rehearsal is done. The CSV carries dates, amounts, merchants and notes; the backup JSON carries masked SMS bodies (the same masker every export uses). Neither belongs in git. |
| **Rollback** | `adb shell pm uninstall com.awbuilds.auraspend.dev` — the rehearsal app and its copy are gone; nothing else changes. |

---

## 1. Your part: two taps (Level 1)

1. Open **AuraSpend** (the Play install — this is production).
2. **Settings → Data → Export CSV**, and save it as
   `auraspend-prod-export.csv` in **Downloads**.
3. Tell me it is there.

That file is your transactions, written by the app's own exporter.

### Level 2 (optional, full fidelity)

If you want every table carried over, not just transactions:

1. **Settings → Data → Back up to Google Drive** (if it is not already backed
   up), then fetch the backup from your Drive:
   `drive.google.com` → search for the AuraSpend backup file → download it as
   `auraspend-backup.json`.
2. Put it where I can pull it (Downloads is fine).

The JSON is the app's own v4 payload; the rehearsal restores it through the same
manager the Drive restore uses.

---

## 2. My part: the operator commands

Everything below is mechanical and repeatable. `$PHONE` is the device serial
(`adb devices -l`), `$PKG=com.awbuilds.auraspend.dev`.

### 2a. Build the two sides

```bash
# The pre-encryption build: what your production app is today.
git checkout dev
./gradlew :app:assembleDevDebug
cp app/build/outputs/apk/dev/debug/app-dev-debug.apk /tmp/app-dev-PRE.apk

# The encryption build + the rehearsal harness.
git checkout feat/encrypted-database          # or dev, after #77 merges
./gradlew :app:assembleDevDebug :app:assembleDevDebugAndroidTest
cp app/build/outputs/apk/dev/debug/app-dev-debug.apk /tmp/app-dev-POST.apk
cp app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk /tmp/app-dev-test.apk
```

### 2b. Pull the export and keep it outside the repo

```bash
adb -s $PHONE pull /sdcard/Download/auraspend-prod-export.csv ~/auraspend-prod-export.csv
shasum -a 256 ~/auraspend-prod-export.csv        # record it in the report
wc -l ~/auraspend-prod-export.csv                # rows (incl. header)
```

### 2c. Seed the pre-encryption build

```bash
adb -s $PHONE install -r -t /tmp/app-dev-PRE.apk
adb -s $PHONE install -r -t /tmp/app-dev-test.apk          # harness; runs against the installed app

# Level 1 (CSV)
adb -s $PHONE push ~/auraspend-prod-export.csv /data/local/tmp/rehearsal-import.csv
adb -s $PHONE shell run-as $PKG cp /data/local/tmp/rehearsal-import.csv files/rehearsal-import.csv
adb -s $PHONE shell am instrument -w -e rehearsal seed -e class \
    com.awbuilds.auraspend.data.local.MigrationRehearsalTest \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner

# Level 2 (backup JSON) — same idea, different action
adb -s $PHONE push ~/auraspend-backup.json /data/local/tmp/rehearsal-backup.json
adb -s $PHONE shell run-as $PKG cp /data/local/tmp/rehearsal-backup.json files/rehearsal-backup.json
adb -s $PHONE shell am instrument -w -e rehearsal restore -e class \
    com.awbuilds.auraspend.data.local.MigrationRehearsalTest \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner
```

`adb logcat -s MIGRATION-REHEARSAL` reports what was written; a `seed:` line
should show `imported=N skippedRows=0`.

### 2d. Fingerprint A — what the app sees before the upgrade

```bash
# The logcat prefix carries a timestamp and a pid, so strip it before comparing
# anything: `diff` on the raw lines reports differences that are not there, and
# a false "DIFFERS" is exactly the wrong thing to read at a release gate.
capture_fingerprint() {   # $1 = output file
  adb -s $PHONE logcat -d -s MIGRATION-REHEARSAL \
    | sed -E 's/^[0-9-]+ +[0-9:.]+ +[0-9]+ +[0-9]+ +I MIGRATION-REHEARSAL: //' \
    | grep -E "fingerprint |FINGERPRINT-TOTAL" > "$1"
}

adb -s $PHONE logcat -c
adb -s $PHONE shell am instrument -w -e rehearsal fingerprint -e class \
    com.awbuilds.auraspend.data.local.MigrationRehearsalTest \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner >/dev/null
capture_fingerprint /tmp/fingerprint-A.txt
cat /tmp/fingerprint-A.txt
```

### 2e. Upgrade in place — this is the migration

```bash
adb -s $PHONE install -r -t /tmp/app-dev-POST.apk        # -r keeps the data
adb -s $PHONE shell am start -n $PKG/com.awbuilds.auraspend.MainActivity
sleep 30                                                  # a Moto needs ~15 s
adb -s $PHONE logcat -d | grep -E "SQLCipherLibrary|libsqlcipher" | tail -2
```

### 2f. Fingerprint B — and the comparison

```bash
adb -s $PHONE logcat -c
adb -s $PHONE shell am instrument -w -e rehearsal fingerprint -e class \
    com.awbuilds.auraspend.data.local.MigrationRehearsalTest \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner >/dev/null
capture_fingerprint /tmp/fingerprint-B.txt

diff /tmp/fingerprint-A.txt /tmp/fingerprint-B.txt \
  && echo "IDENTICAL — the migration preserved every row"
```

Also check the file itself (the rehearsal app is debuggable, so `run-as` works):

```bash
adb -s $PHONE shell run-as $PKG ls -la databases/                  # no .plain.bak, no .enc; a -wal means WAL is on
adb -s $PHONE exec-out run-as $PKG sh -c 'head -c 15 databases/auraspend_db'   # must NOT read "SQLite format 3"
```

### 2g. Clean up

```bash
adb -s $PHONE shell am instrument -w -e rehearsal wipe -e class \
    com.awbuilds.auraspend.data.local.MigrationRehearsalTest \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner      # optional: empty the copy
adb -s $PHONE uninstall $PKG.test
adb -s $PHONE uninstall $PKG
rm ~/auraspend-prod-export.csv ~/auraspend-backup.json     # the copies, once the report is written
```

---

## 3. Acceptance criteria

The rehearsal **passes** when all of these hold:

1. `diff /tmp/fingerprint-A.txt /tmp/fingerprint-B.txt` is empty — the same
   `FINGERPRINT-TOTAL` and the same per-table hashes before and after. Compare
   the **captured** files (the executor in §2d strips the logcat timestamp and
   pid): a raw `logcat` diff reports differences that are not there, which is
   exactly what the vivo run did before the prefix was stripped — every hash
   underneath was identical.
2. The database file does not start with `SQLite format 3`; no `.plain.bak` and
   no `.enc` are left beside it; a `-wal` file is present.
3. `SQLCipherLibrary: libsqlcipher.so loaded` appears in logcat, and no
   `UnsatisfiedLinkError` does.
4. `seed:`/`restore:` reported the same row counts the export contained.

A **failure** of 1–3 is a release blocker: do not ship, and the plaintext
original is still on disk (the swap restores it) — reinstall the pre-encryption
APK to get a working app back.

## 4. What the rehearsal was proven able to do

Run end to end on the arm64 emulator on 2026-10-15 with a 32-transaction demo
dataset, and then — the run that matters — **on the owner's vivo V2158 with
their own production export**:

| Step | Result |
|---|---|
| Export from the production app | 492 transaction records (1,095 file lines; 121 records carry embedded newlines and the app's reader handled every one) |
| Import into the pre-encryption build | `parsed=492 imported=492 skippedRows=0 written=492` |
| Fingerprint A (pre-upgrade) | `transactions rows=492`, `categories rows=13`, total `rows=505 sha256=b088c22610231d3970de2de5767873a37c8ffe3fb5bd9fd816f0397e02281b02` |
| In-place upgrade | `SQLCipherLibrary: libsqlcipher.so loaded`; header no longer `SQLite format 3`; only `auraspend_db`, `-wal`, `-shm` left |
| Fingerprint B (post-upgrade) | **identical, every table and the total** |
| The app on the data | dashboard renders the balance, the cash-flow chart and the recent list from the encrypted database |
| The production app | untouched: `versionName=0.1.4`, unchanged `lastUpdateTime` |

The same upgrade has also run against a real schema-v8 device database whose
data lived almost entirely in its WAL (handoff 0046).

## 5. What it does not prove

- **R8-minified builds.** The harness runs against debug builds; the release
  gate proves the JNI classes survive shrinking, not that the app runs minified.
- **The biometric prompt** and anything else needing an enrolled fingerprint.
- **That the production flavour is identical** — it is the same source and the
  same storage layer, but the rehearsal runs the dev package. The rehearsal is
  evidence about the migration, not about code you have not run.
