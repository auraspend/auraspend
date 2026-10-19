# 0066 — 0.1.91: the patch on 0.1.9

**Date:** 2026-10-19 · **Branch:** `main` · **Status: tagged; the release run
needs the `release` environment approval.**

## Why a patch

Three user-visible changes landed after 0.1.9 shipped, and one of them is a bug
the owner hit in normal use. Nothing in the delta touches data, schema, parsers
or classification — it is presentation only — so it belongs in a patch rather
than waiting for the next feature release. `0.1.91` follows the project's own
habit (0.1.7 → 0.1.71 was the same shape); `versionCode 13` keeps Play's
monotonic requirement.

## What is in it

| Change | Handoff |
|---|---|
| **Fixed:** Plan said "0 active subscriptions" above a Subscriptions screen listing four — the hub counted only tracked rows and not the classifier's detected ones | 0065 |
| **Added:** Home's seven-day strip (`AuraSparkColumns`, today's column in ink), so a quiet day is not an empty screen | 0065 |
| **Changed:** tab switches are instant; push/pop lost their alpha fades | 0064 |

## Verification

- `:app:testProdDebugUnitTest` **614 tests, 0 failures, 1 skipped**.
- `:app:lintProdDebug` baseline-only (1 hint; 45 errors + 17 hints from the
  baseline).
- `assembleProdRelease` + `bundleProdRelease` green, signed with the upload key;
  the artifact reports `versionCode=13 versionName=0.1.91`.
- `tools/verify_r8_release.sh` — **all checks passed**, including "all 11 classes
  the JNI resolves by name survived un-renamed". That gate is the reason 0.1.8
  can no longer recur silently.
- Play notes EN 253 / HI 232 characters (limit 500).

## The one caveat

The transition change is measured on the **emulator**, not the phone: worst tab
frame 17.4 → 14.3 ms against a 16.7 ms budget. It is also a deliberate change of
*feel* — tab switches stop cross-fading. If it reads worse on the vivo, reverting
is three lines in `NavGraph.kt` (`isTabSwitch()` returning false).

Everything else in this patch was verified on a device or by the suite.
