# 0061 — Navigation jank: measured, and what it actually was

**Date:** 2026-10-18
**Branch:** `ui_enhance` — **working tree only, not committed**.
**Scope:** `ui/core/AuraSpendScaffold.kt`, `ui/home/DashboardViewModel.kt`, the
`:benchmark` module, plus a release install on the owner's phone. No behaviour
change beyond the two fixes below; no schema, no dependency.

## The report

> "the page to page transitions are kinda laggy, especially if you go deeper and
> deeper, like activity and individual activity and all. Mainly it feels laggy
> when returning back from pages."

## How it was measured

`dumpsys gfxinfo <pkg> framestats` on the Moto G40 Fusion (Android 12), with the
app **force-stopped and restarted before every run** — the first measurement was
taken on a warm app and read 33 ms where a cold one read 382 ms, which is the
whole difference between "fine" and "laggy". Each phase (tab switch / push to a
detail / pop back) is measured in its own gfxinfo window, and the metric quoted
is the **worst UI-thread frame** in that window, median over the runs. Scripts:
`~/aura-fidelity/{nav_bench,settings_bench,frame_probe}.sh`.

`simpleperf record --app <pkg>` then gave the answer.

## What it was (three layers, measured one at a time)

| the path Activity → detail → **back**, worst UI-thread frame | tab switch | push to detail | **pop back** |
|---|---|---|---|
| dev **debug** | 289 ms | 117 ms | **382 ms** |
| dev **release**, profile not yet applied | 76 ms | 66 ms | **125 ms** |
| dev **release**, baseline profile applied | 46 ms | 33 ms | **61 ms** |
| dev release, full AOT (`compile -m speed`) | 52 ms | 26 ms | 64 ms |

So: 3× from debug → release, and another 2× from actually having a current
baseline profile. The last two rows are the same result by different means — a
profile *is* "AOT the hot methods", and it lands within 3 ms of compiling
everything.

**Layer 1 — the dev debug build runs interpreted.** The profile of a back navigation is
~50 % ART interpreter (`ExecuteSwitchImplCpp`, `DoCall`, `artQuickToInterpreterBridge`,
`NterpGetMethod`) and 0.5 % Compose runtime work. A debuggable app is not
AOT-compiled, so every screen's composition — the expensive part of a navigation —
runs as bytecode. The same walk on the release build profiles as a flat spread of
`nterp_helper` (the *fast* interpreter), GC and background JIT.

Measured on the phone, cold, worst UI-thread frame:

| navigation | dev **debug** | dev **release** | |
|---|---|---|---|
| Home → Settings | 435 ms | 30 ms | 14× |
| Settings → back | 215 ms | 64 ms | 3.4× |
| Home → Activity | 289 ms | — | |
| Activity → detail | 117 ms | — | |
| detail → back | 382 ms | — | |

A 60 Hz frame is 16.7 ms, so the debug build drops 10–25 frames per navigation
and the release build drops 2–4. **This is a build-type property, not an app
defect**, and it is why the release install is what belongs on a phone.

## Two real defects found on the way (fixed)

1. **`content()` had three call sites in `AuraAppChrome`.** The frame called the
   navigation host from three different branches (no chrome / rail / bottom bar),
   so every crossing between a top-level screen and a detail screen — and the back
   out of it — moved the whole `NavHost` subtree to a different position in the
   composition. Compose disposes a subtree whose position changes and rebuilds it,
   so each crossing tore down and recreated the entire navigation host *during*
   the transition. There is now one stable slot with the chrome as a sibling.
2. **Home re-ran its whole load on every return.** `DashboardScreen` fires
   `LoadDashboard` from a `LaunchedEffect(Unit)`, which re-runs whenever the
   composition is recreated — i.e. on every back navigation. The ViewModel
   outlives that (it is scoped to the nav entry) and its job is a live Room
   subscription, so the "load" cancelled a perfectly good stream and re-ran four
   aggregate queries mid-transition. A load now no-ops while the subscription is
   alive; the explicit refresh still restarts it.

## Layer 2 — the baseline profile was five weeks stale, and is now regenerated

`app/src/main/baseline-prof.txt` was generated **2026-09-12**, before the whole
Quiet Ledger UI. It listed 8,933 methods, and nothing in it came from the current
screens, so even the shipping app interpreted its hot UI code on first use.

Three things had to be fixed before a new one could be collected:

1. **The phone cannot generate it.** `BaselineProfileRule` requires **API 33+**;
   the Moto is Android 12 (API 31). The API 37 arm64 AVD can, and a profile is
   device-independent — it names dex methods, and the dex is the same APK.
2. **The generator profiled the wrong package.** `StartupBenchmark.PACKAGE` was
   the hardcoded `com.awbuilds.auraspend` — the *prod* id — so the README's own
   `:benchmark:connectedDevBenchmarkAndroidTest` (which links the `dev` flavor)
   would have driven the maintainer's signed Play install. It now comes from a
   per-flavor `buildConfigField` in `benchmark/build.gradle.kts`.
3. **Reading it from the instrumentation killed the run.** That was the
   "Process crashed": this module is *self-instrumenting*, so
   `InstrumentationRegistry.targetContext` is the benchmark itself, and
   macrobenchmark force-stopped its own process before the first sample. Hence
   the build-config field rather than a runtime lookup.

The walk itself was extended to the paths this report is about: all four tabs, a
transaction created through Quick Add (so the *populated* list is compiled, not
just its empty state), Settings, and the pop transitions. `baseline-prof.txt` is
regenerated from it.

**It does not take effect on install.** After `adb install`, the app sits at
`status=verify`; the profile is applied by the device's background dexopt (idle +
charging), which is exactly the state a test phone never reaches. Forcing it —
`adb shell cmd package compile -m speed-profile -f <pkg>` — is what produced the
61 ms row above, and is the honest way to measure a profile locally.

## Verification

- `:app:compileProdDebugKotlin` clean; `testDevDebugUnitTest` **614 tests, 0
  failures, 1 skipped**; `tools/verify_r8_release.sh` green after the profile
  change (R8 consumes the profile, so it is an input to the release build).
- The release dev APK (`app-dev-release.apk`, R8 + the regenerated profile) is
  what is now installed on the Moto as `com.awbuilds.auraspend.dev`, compiled
  `speed-profile`, with the demo data imported through Settings → Import from CSV
  (a release install cannot be seeded by the instrumentation seeder — different
  signing keys).

## Open

- **A profile is tied to the R8 output it was collected against** (its app-side
  entries are obfuscated names). Regenerate it whenever the UI changes materially
  — the file is only as good as the build it came from.
- On a real device the profile lands via background dexopt, which needs the phone
  idle and charging. A user's first day on a new version is the interpreted one.
- A debug build will always feel like this. Anything performance-shaped should be
  judged on a release install.
- Installing the release APK required uninstalling the debug one (different
  signing keys), so `tools/seed_demo_data.sh` needs an `adb uninstall` before it
  can seed again — and it will replace the release install with the debug one.
