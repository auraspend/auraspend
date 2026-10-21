# 0073 — 0.1.93: the release the UI suite found the bugs in

**Date:** 2026-10-21 · **Branch:** `main` + `dev` · **Status: tagged, run
39534961151 green, uploaded to Play (closed testing, `alpha`).**

## The number

`0.1.93`, the third patch of the 0.1.9 line — `0.1.71` and `0.1.91` were the
first patches of theirs, `0.1.92` the second. `versionCode` **15**, which is
what Play enforces; the version *name* is the label.

**What the changelog must not repeat.** 0.1.91 said "no data, schema or
classification change — presentation only" and 0.1.92 had to walk that back,
because it *did* carry a schema migration and two features. 0.1.93 carries
neither — but it is **not** "presentation only" either, because one thing
behaves differently now: a settings row toggles when its title is tapped,
which is what every other row in that screen already did. The entry says so
rather than borrowing 0.1.91's framing.

## What is in it

| Change | Handoff |
|---|---|
| **Fixed:** the Activity search field had no accessible name — the hint was a `Text` sibling of the `BasicTextField` inside a clickable, and a clickable merges its descendants, so the merged node took its empty text from the field | 0072 |
| **Fixed:** the Quick Add merchant field had the same shape | 0072 |
| **Fixed:** the Settings switches had no accessible name — a `Switch` with no description, on a row whose title and subtitle are siblings of it rather than its label. Four of them, plus the editor's recurring switch | 0072 |
| **Fixed:** tapping a settings row's title did nothing; only the switch toggled. The row now toggles, and the switch still consumes its own touches so it cannot toggle twice | 0072 |
| **Added:** the end-to-end UI suite that found all of the above — thirteen Maestro flows, run by `tools/run_maestro.sh` | 0072 |

The first four are not a new feature. They are a screen reader being able to
read the app, which it could not do before, and they are what the Maestro
flows now assert — so the suite is the regression test for its own findings.

## Verification before the tag

- `:app:testProdDebugUnitTest` — **637 tests, 0 failures**.
- `:app:lintProdDebug` — baseline-only (2 hints).
- `assembleProdRelease` + `bundleProdRelease` green, signed with the upload
  key (**CN=AuraSpend**); the APK reports `versionCode=15 versionName=0.1.93`,
  minSdk 30, targetSdk 37.
- `tools/verify_r8_release.sh prod` — **all checks passed**: 96.9 % obfuscation
  and shrinking against Play's 25 % floor, 4.43 MB uncompressed DEX, 16 KB
  aligned, **all 11 classes the JNI resolves by name survived un-renamed**, and
  the SQLCipher classes kept un-renamed.
- `tools/check_license.sh`, `tools/check_privacy_claims.sh` — clean.
- Store notes **EN 340 / HI 335 characters** (limit 500).
- The Maestro suite passed end to end — **13 flows** — on the code this release
  ships. It was not re-run against the release commit, because the release
  commit changes the version number, the changelog, the F-Droid metadata, the
  store notes and the site's changelog page, and none of those can change what
  the app does. (It was also not runnable at that moment: another project was
  driving the same emulator with its own Maestro suite, and two suites cannot
  share one device — see the trap at the end.)

## The run

**39534961151**, every step green, including "Upload to Google Play" and
"Publish the release if it is still a draft".

- GitHub release `v0.1.93` **published** (not a draft) with
  `AuraSpend-V0.1.93.Alpha.aab` (24,520,417 bytes),
  `AuraSpend-V0.1.93.Alpha.apk` (43,415,780 bytes) and `SHA256SUMS` (188
  bytes, both digests).
- Play: the action created a new Edit, validated the `alpha` track and
  committed it — `releaseFiles: dist/AuraSpend-V0.1.93.Alpha.aab`,
  `status: completed`, with `docs/store/whatsnew` as the notes and the R8
  mapping attached.

**What could not be verified from here, and why.** The Play *state* could not
be read back: the service-account key for this app exists only as the
repository secret `PLAY_SERVICE_ACCOUNT_JSON`, and there is no copy on this
machine — the keys on disk belong to other projects. The upload commit is the
strongest signal available, and it is the same signal the previous releases
were cut on. The expected state is `IN_REVIEW`, which is where every previous
upload sits until the review finishes.

## The site's changelog went with it

`site/changelog.html` gained the 0.1.93 entry, and the **Pages** workflow
deployed it: run 39534112248, green. All seven pages answer **200**, and the
live changelog reads 0.1.93 at the top. This is the second release to ship the
site's own changelog — the first was 0.1.92, and the site's entry had been
stuck at 0.1.91 until then.

## Also in this release

The **F-Droid metadata** in this repository was still at 0.1.91 / versionCode
13 — it had been missed for 0.1.92 as well, because the F-Droid *fork* is
updated separately and the copy here only follows when someone remembers. It is
now 0.1.93 / 15 / `commit: v0.1.93`, and `CurrentVersion` matches.

## Open

- The **fdroiddata fork** still needs the same bump pushed. That is the
  owner's next step, as it was for 0.1.91 and 0.1.92.
- `0.1.93` is in review on `alpha`; it reaches testers when that review
  finishes.
- The **store listing's screenshots** were last re-shot for 0.1.92. This
  release changes no visible surface, so they are still correct — the
  accessibility work is invisible in a screenshot, which is rather the point.

## A trap, for the next run

**Two Maestro suites cannot share one emulator.** Another project was running
its own suite on the same AVD while this release was being cut, and the two
kept killing each other's device server — every AuraSpend run failed with
*"Device server died"*, and the other app was left in the foreground. The
failures look like the app is broken; they are not. The runner recovers from
the *stale session* half of it (it drops the device's entry in
`~/.maestro/sessions`, and reinstalls Maestro's driver and server from
`maestro-client.jar` if they are missing), but it cannot recover from
another suite actively holding the device. Check for one before blaming the
app.
