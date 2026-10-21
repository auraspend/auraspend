# 0074 — 0.1.94: the update check gets a voice, and the site gets a 404

**Date:** 2026-10-21 · **Branch:** `main` + `dev` · **Status: tagged, run
39589870607 green, uploaded to Play (closed testing, `alpha`). `versionCode`
16.**

## The number

`0.1.94`, the fourth patch of the 0.1.9 line (`0.1.71` and `0.1.91` opened
theirirs, `0.1.92` and `0.1.93` followed). `versionCode` **16**.

**What the changelog must not repeat.** The last three entries each had to say
carefully what they were *not*. This one **is** a feature release: the Play
build's update check now has four in-app surfaces and a background
notification. What it is **not** is a data release — no schema migration, no
data-model change, no classification change, no new dependency. The entry says
that rather than calling it "presentation only", which would be false.

## What is in it

| Change | Where |
|---|---|
| **Added:** one silent update check (at most daily) now feeds four in-app surfaces: a line in Settings → About, a dot on the header avatar, a dismissible strip on Home, and the restart offer | 0074 (this file) |
| **Added:** Play's install status is read on every resume, so a flexible download finished while the app was closed offers its restart on the next launch | this file |
| **Added:** a release can be marked urgent — the upload passes `inAppUpdatePriority`, and priority 4–5 opens Play's blocking flow on launch | this file |
| **Added:** a daily background check with its own notification channel, once per release, only after three stale days | this file |
| **Added:** `AuraNoticeBanner` in the design system — an informational strip, deliberately not `AuraErrorBanner` | this file |
| **Fixed (site):** FAQ chevrons clipped by 3 px; footer links had 19 px tap targets; a wrong URL served GitHub's default 404 | this file |
| **Changed (site):** the download and privacy pages describe the new update behaviour | this file |

## The design: one check, five surfaces, one policy

Five surfaces over one check is exactly how a useful feature becomes the thing
people turn off, so the *decisions* are not spread across the call sites. They
live in `UpdatePolicy`, a pure object with seventeen tests:

1. **A finished download outranks an available one.** It already happened; the
   last step is a restart, and hiding it behind an "available" banner would make
   the app look like it forgot.
2. **An urgent release (priority ≥ 4) skips the quiet path** — but only when Play
   also permits the immediate type. Priority is the uploader's opinion;
   `isUpdateTypeAllowed` is Play's answer about *this* install.
3. **A dismissal is honoured until the app is actually updated.** The dismissal
   is stored against the *installed* version code, so a later release can ask
   once more but the same one never re-asks. This is the anti-nagging rule.
4. **The background notification waits.** Three stale days, one announcement per
   release (keyed on the *available* version code), never when Play cannot say
   how old the update is, and never for an urgent release — a notification that
   leads to a screen the user cannot dismiss is worse than none.

`UpdatePolicy.inApp` also returns nothing when Play permits **neither** flow,
which is the practical shape of a release withdrawn between the check and the
tap: a banner whose only button leads to "Couldn't check for updates" is worse
than no banner.

### Decisions worth not re-litigating

- **Play exposes the available version *code*, never its name.** `AppUpdateInfo`
  has `availableVersionCode()` and no `versionName`. A version code means
  nothing to a person, so every surface says "a newer version is available" and
  shows no number. Do not add one by joining it to a name you invented.
- **The avatar dot sits inside the circle, 6 dp in.** The avatar box is clipped
  to a circle, so a badge straddling its edge is cut in half. 6 dp is the least
  inset that keeps a 7 dp dot wholly within the circle's own corner — measured,
  not guessed.
- **The notification has its own channel (`app_updates`).** Riding
  `auto_classification` would couple two unrelated preferences: silencing
  categorisation would silently silence updates, and someone chasing an update
  would have to re-enable categorisation to get one.
- **The immediate flow is launched from Home.** Home is the landing screen and
  the nav host is only composed when the app is unlocked (`MainActivity` puts it
  in the `else` branch of the lock gate), so the flow cannot open behind the PIN
  screen.
- **`AuraNoticeBanner`, not `AuraErrorBanner`.** An update is information. The
  error banner's `errorContainer` teaches people to read red as "ignore me".
- **No new dependency.** `kotlinx-coroutines-play-services` is not in the graph,
  so `PlayAppUpdateController.checkNow()` bridges Play's `Task` by hand with
  `suspendCancellableCoroutine`. Adding a library for one `await()` would have
  to pass the licence check and earn its place; it did not.

## Traps, for the next run

- **Lint's `MissingPermission` does not follow helper methods.** The guarded
  `notify()` still failed the build behind a `canNotify()` check. The permission
  test has to be inline in the method that posts — the same conclusion
  `SmsProgressNotifier` reached. The `areNotificationsEnabled()` half can stay
  in a helper.
- **Play's `Task` has no `cancel()`.** `com.google.android.gms.tasks.Task` is not
  a Kotlin coroutine; the cancellation hook is a `CancellationTokenSource`, and
  this code guards on `continuation.isActive` instead.
- **WorkManager keeps worker class names.** `UpdateCheckWorker` is instantiated
  by name, so R8 renaming it would leave a worker that is retried for ever and
  never runs. Its consumer rules contain `-keepnames class * extends
  androidx.work.ListenableWorker` — verified by reading
  `work-runtime-2.10.0.aar`'s `proguard.txt`, not assumed.
- **`ExistingPeriodicWorkPolicy.UPDATE` does not reset an unchanged period.**
  That matters because `UpdateCheckWorker.schedule()` runs on every process
  start: if `UPDATE` reset the timer, someone who opens the app daily would
  never receive the worker. It only matters for people who *do not* open the
  app, and for them the call never happens.
- **`registerListener` is not idempotent.** Play holds a list, so the same
  listener registered on every resume fires N times. Registration is guarded.

## Verification

- `:app:testProdDebugUnitTest` — **658 tests, 0 failures** (1 pre-existing
  skip). +21 over 0.1.93: 17 for `UpdatePolicy`, 4 rendering the badge and the
  notice banner.
- `:app:lintProdDebug` — baseline-only (2 hints).
- `:app:compileFdroidDebugKotlin` — green, so the no-op controller still
  satisfies the seam.
- `:app:assembleProdRelease` + `:app:bundleProdRelease` — signed with the upload
  key (`CN=AuraSpend`); the APK reports `versionCode=16 versionName=0.1.94`,
  minSdk 30, targetSdk 37. APK 43,438,072 bytes · AAB 24,541,317 bytes.
- `tools/check_license.sh`, `tools/check_privacy_claims.sh`,
  `tools/check_site_version.sh` — clean.
- Store notes **EN 441 / HI 413 characters** (limit 500).

## Open — and the one that matters

**None of the five update surfaces has been seen on a device.** This is not an
oversight and it cannot be fixed from here:

- `IN_APP_UPDATES` is **false** in dev and fdroid, so those builds compile the
  surfaces out.
- Even in a prod build, `isAvailable` is true only when the installer is
  `com.android.vending`. A sideloaded APK — the only kind this machine can
  install — reports `adb`/`null` as its installer, so the controller reports
  itself unavailable and every surface stays hidden.

What *is* verified: the policy (pure, 17 tests), the two design-system
components by rendering them (4 tests), the release build, and the seam's
compilation in both flavours. What is **not**: the wiring from `onStart` to a
rendered banner, the Play flow itself, the notification, and the version check
against a real Play Store.

**The verification that would close it:** install 0.1.94 from the `alpha` track
on a tester's device, then cut any later release and open the app — the strip,
the dot and the Settings line should all appear without the user asking.

## The run

**39589870607**, every step green — the licence gate, the site-version gate, the
signed bundle, **Upload to Google Play**, and "Publish the release if it is still
a draft".

- GitHub release `v0.1.94` **published** (not a draft) with
  `AuraSpend-V0.1.94.Alpha.aab` (24,542,929 bytes),
  `AuraSpend-V0.1.94.Alpha.apk` (43,438,072 bytes — byte-for-byte the size of
  the local build, which is a small independent check that the tag built what
  was verified here) and `SHA256SUMS` (188 bytes).
- Play: the action created an Edit, validated the `alpha` track and committed
  it, with `docs/store/whatsnew` as the notes and the R8 mapping attached.
- The run sat at the `release` environment's approval gate first, which is the
  intended shape: nothing reaches Play or a GitHub release without a human.

**What cannot be read back from here**: the Play *state*. The service-account
key exists only as the repository secret `PLAY_SERVICE_ACCOUNT_JSON`, and there
is no copy on this machine. The committed Edit is the strongest signal
available, and it is the same signal every previous release was cut on.

## The site rode along

Three fixes the owner approved earlier were still uncommitted and ship here:
the FAQ chevrons (`margin: 3px` on the rotated pseudo-element — rotation paints
a longer diagonal than the box it turns in), footer tap targets
(`inline-block` + 1 px block padding = 25 px, with the list's gap dropped from
8 px to 6 px to keep the rhythm), and a real `site/404.html` with
`noindex, follow` and no canonical/OG/JSON-LD.

The download and privacy pages now describe the update behaviour instead of
saying "no prompt on launch", which this release makes false. The privacy page
gained the sentence that matters: the check asks the Play Store already on the
device, and reaches no AuraSpend server — there is none.

Two more were found **after** the tag went up. Both are site-only, so the release
artifact is unaffected:

- A wrong address served GitHub's default page. It now serves `site/404.html`,
  verified live: `…/this-page-does-not-exist-0074` returns **404** carrying the
  site's own copy ("That page is not here").
- The changelog's own summary still read "→ 0.1.93". That line is a *current*
  claim, and `check_site_version.sh` exempted the whole changelog because its
  history is not stale — so nothing looked at it. It says 0.1.94 now, and a
  **third check** verifies the target version *and* the count against the
  entries on the page. It was shown failing on the stale line before the fix,
  which is the only evidence that a gate works.

## Open (the others)

- The **fdroiddata fork** still needs 0.1.94 / 16 / `commit: v0.1.94` pushed to
  MR !52537. That is the owner's GitLab account, as it has been since 0.1.91.
- Play's state for the previous uploads cannot be read from this machine: the
  service-account key exists only as the repository secret.
- `PLAY_UPDATE_PRIORITY` is a **repository variable**, unset today (which means
  0). A future security release sets it to 4 or 5; nothing in the code needs to
  change.

## Next step

Install from `alpha` and watch the first banner arrive on a later release —
that is the only step that can turn the open risk above into evidence. Until
then, treat the update surfaces as *tested in policy, unproven in the field*.
