# 0043 — dark/AMOLED icon visibility, a populated chart, F-Droid, and two gate bugs

**Status:** done. PR #63 (theme + chart, merge `c0be7e4`) and PR #64 (F-Droid
flavor + two release-gate fixes, merge `0b7cf2e`) → `dev`. **545/545 tests**
(was 540), lint clean on dev/prod/fdroid, `assembleFdroidDebug` green, and both
release gates exercised green on CI for the first time.

## The invisible-in-dark icons (owner report)

- Root cause: Compose's `LocalContentColor` **defaults to black**; only Material
  containers set it, and the affected screens (back buttons, the Activity month
  chevrons) draw their own headers — so untinted icons and bare text were black
  on every background: readable in LIGHT, invisible in DARK and AMOLED.
- Fix: `AuraSpendTheme` provides `colorScheme.onBackground`; Material components
  still override it with their own container colours. The navigation-bar
  appearance was never set either (dark themes kept dark system icons) — both
  bars now follow the theme together.
- Guard: `ThemeContentColorTest` renders the real theme in LIGHT/DARK/AMOLED and
  fails on anything but the scheme's `onBackground` (i.e. on the black default).

## The homepage chart (owner report)

- Not a rendering bug: the device's last spend was a Friday, and a calendar-week
  window is empty from Monday on — "No spending recorded this week yet" while
  the month summary showed real spend. The chart now answers **the last 7 days
  ending today** (data-verified against the device DB copy: 3 spending days in
  that window). Labels updated EN+HI; the weekly *budget* window stays the
  calendar week on purpose — budgets and the chart are different questions.
- Guard: `ChartWindowTest` (inclusive bounds + the Monday regression case).

## F-Droid (owner request) — PR #64

- Policy check: F-Droid's inclusion policy asks upstream for an **untainted
  build flavor** without non-free dependencies. ADR 0012 adds `fdroid` (same
  package id as prod):
  - the real `DriveSyncManager` moved to `src/gms` (compiled into dev/prod);
    `src/fdroid` holds a fail-closed no-op stub;
  - the Google dependencies are dev/prod-only — `fdroidDebugRuntimeClasspath`
    verified free of `com.google.android.gms` / `com.google.api.*`;
  - `BuildConfig.DRIVE_BACKUP` hides the Drive rows (Settings → Data) and the
    onboarding restore card, so no dead buttons exist;
  - `pr_check.yml` builds `assembleFdroidDebug` on every PR so it cannot rot;
  - submission kit: `docs/fdroid/com.awbuilds.auraspend.yml` (fdroiddata
    metadata draft) + `docs/fdroid/SUBMISSION.md` (owner steps; the merge
    request needs a GitLab account) — tracked in issue #65.
- **AGP 9 gotcha, cost one debugging round:** the built-in Kotlin compiles from
  its own `kotlin` source directories; `java.srcDir` alone does not reach it —
  fdroid built green while dev lost the class (a test that looked green while
  nothing was wired). Both sets are now declared.
- Issue #66 tracks the unification path (OAuth + PKCE via AppAuth) that would
  let every flavor keep Drive; #15 stays open with the updated context.

## Two release gates had never actually run on CI — PR #64 found both

A gate that has never failed has often never run: both were skipped on every PR
since their introduction because no PR had touched a `.kts`/`gradle/` file
until this one, and both failed on their first genuine execution.

- **R8 gate** (`tools/verify_r8_release.sh`): its build guard checked
  `mapping.txt` + the config analyzer — both of which `assembleProdRelease`
  produces on CI — so the build was skipped and step 2 failed on an AAB nothing
  had made. The AAB is part of the guard now, and `${VARIANT^}` (bash 4+)
  became portable for macOS's bash 3.2, which a local run needs. Verified
  locally end to end: all four checks pass.
- **Native audit** (`tools/audit_native_runtime.sh`): the EXPECT probe was
  `$SYM_SRC | grep -qE …` — under `pipefail`, `grep -q` exits on the first
  match, the upstream pipe takes SIGPIPE, and the pipeline returns 141, so a
  *successful* scan (187 symbols listed just above) was reported as "scan is
  broken". The probe captures the symbol buffer and greps it directly. Both
  ONNX libraries pass locally and on CI.

## GitHub issues (owner request)

- Closed, each verified against the code first: **#16** (destructive migration
  narrowed to never-shipped v1 + explicit downgrade path), **#17**
  (`BillingManager` is gone; paywall removed), **#18** (no tests — 545 now),
  **#19** (throws are caught → `UiError.SAVE_FAILED`), **#20** (AutoMirrored
  arrows done), **#21** (SearchBar superseded).
- **#15** kept open with the F-Droid split + #66 pointer. New: **#65**
  (fdroiddata submission), **#66** (GMS-free OAuth for Drive).

## Pending on the owner's device

- Wireless debugging dropped (adb port closed while the phone stayed
  reachable) — re-enable it and the pass is quick: the visual checks for #63
  (dark/AMOLED back button + chevrons, chart populated), the two 0041 leftovers
  (editor recurring switch, subscription dismiss X), and re-typing the saved
  EPF rows (Expense + Investments; amounts already right).

## Next

- The next release cut (v0.1.4) carries: theme/chart fixes, EPF-as-investment,
  the fdroid flavor, the audit features, the gate fixes. fdroiddata MR (#65)
  follows the tag.
