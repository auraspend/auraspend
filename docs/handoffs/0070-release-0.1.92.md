# 0070 — 0.1.92: the second patch of the 0.1.9 line

**Date:** 2026-10-20 · **Branch:** `main` + `dev` · **Status: tagged, run
green, uploaded to Play (closed testing, `alpha`).**

## The number

`0.1.92`, which is the project's own convention rather than a new one: the
tags run `v0.1.2 … v0.1.9`, then `v0.1.71` and `v0.1.91` — the first patch of
the 0.1.7 and 0.1.9 lines respectively — so the second patch of the 0.1.9 line
is `0.1.92`. `versionCode` 14 is what Play enforces (it rejects an upload that
reuses one); the version *name* is the label.

**The one thing the changelog must not repeat.** 0.1.91's entry says "No data,
schema or classification change — presentation only". This delta *does* carry a
schema migration (v9: two nullable columns for the balance a message states,
additive, exercised by the suite) and two features (the rebuilt home screen and
the accounts ledger). So the entry says that plainly instead of borrowing
0.1.91's framing.

## What is in it

| Change | Handoff |
|---|---|
| **The home screen, rebuilt** — hero as a flat tonal block with the month's in and out split by a hairline, a seven-day cash-flow area chart, the budgets as a carousel of rings, the subscriptions, the accounts, today's rows | 0069 |
| **Fixed:** rupee amounts with paise were mis-stated — ₹1,23,456.78 rendered as ₹1,23,456.80, ₹1.05 as ₹1.10 (the Indian path formatted with a one-decimal pattern and padded the second digit with a zero) | 0069 |
| **Fixed:** the Activity header showed the month's *unfiltered* total above a filtered — sometimes empty — list | 0069 |
| **Added:** accounts and cards as a ledger, from the balance the bank states (schema v9) | 0068 |
| **Added:** a fresh install reads the inbox at once instead of waiting for the periodic worker | 0069 |
| **Fixed:** a sender-routed bank's messages never carried a balance — the global engine read it, but the per-bank parser bypassed it | 0069 |
| **Fixed:** payee extraction (a semicolon ends a payee name, a rejected candidate no longer hides a later one, a counterparty clause is not a merchant, a written date is not a merchant) and the category gaps | 0068 |
| **Fixed:** a model may no longer assign the transfer category | 0068 |

## Verification before the tag

- `:app:testProdDebugUnitTest` — **637 tests, 0 failures**.
- `:app:lintProdDebug` — baseline-only.
- `assembleProdRelease` + `bundleProdRelease` green, signed with the upload key
  (`CN=AuraSpend`); the APK reports `versionCode=14 versionName=0.1.92`.
- `tools/verify_r8_release.sh` — **all checks passed**, including "all 11
  classes the JNI resolves by name survived un-renamed" and the SQLCipher
  classes kept un-renamed. 4.42 MB uncompressed DEX, 16 KB aligned.
- `tools/check_license.sh`, `tools/check_privacy_claims.sh` — clean.
- Store notes EN 450 / HI 355 characters (limit 500).

## The run

Run **39408271870**, every step green, including "Upload to Google Play" and
"Publish the release if it is still a draft".

- GitHub release `v0.1.92` **published** (not a draft) with
  `AuraSpend-V0.1.92.Alpha.aab`, `AuraSpend-V0.1.92.Alpha.apk` and
  `SHA256SUMS`.
- Play track `alpha`: releaseName **0.1.92**, versionCode **14**,
  state `RELEASE_LIFECYCLE_STATE_IN_REVIEW` — the state every previous upload
  passed through (`status: completed` assigns the release and sends it for
  review), and the state 0.1.91 is in now that its review finished.

The `release` environment's required reviewer is the maintainer, and the run
was approved through that gate with the owner's instruction in this chat.

## The store listing went out earlier, separately

The listing's seven screenshots are the ones this release's home screen
actually has (re-shot, deck re-exported, uploaded and **SHA-1 verified**
against the local files). The Play API must not be edited while a release run
uploads, so that was done before the tag and not after.

## Open

- The **F-Droid** metadata version (the fdroiddata fork) is the owner's next
  step, as it was for 0.1.91.
- `0.1.92` is in review on `alpha`; it reaches testers when that review
  finishes.
- The **site's changelog page** still describes the app at 0.1.91.
