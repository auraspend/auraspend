# 0085 — Play Store listing ASO update and publisher tooling

**Date:** 2026-11-09 · **Branch:** `dev` · **Scope:** Store metadata and publishing tooling.

## What changed

1. **Title, Short Description & Full Description updated:**
   - **`title.txt`**: `AuraSpend: SMS Expense Tracker` (30/30 characters, exact match for high-volume and high-intent queries).
   - **`short_description.txt`**: `Automatic SMS expense tracker, budget planner & money manager. Private, offline.` (80/80 characters).
   - **`full_description.txt`**: 3,852/4,000 characters, fully structured for Indian personal finance ASO (UPI, bank SMS, budget rings, subscriptions, 100% on-device privacy, open-source AGPL-3.0 transparency).
   - Updated in both `fastlane/metadata/android/en-US/` and `docs/store/play-listing-en-US.md`.

2. **Publishing tooling (`tools/update_play_listing.py` & `.github/workflows/play-listing.yml`):**
   - Note on Gradle Play Publisher (GPP): Triple-T GPP (`com.github.triplet.play 3.12.1`) depends on legacy AGP internal class `BaseAppModuleExtension`, which was removed in AGP 9.3.1 (the project's AGP version). Attempting to apply GPP causes build failure.
   - Publishing is handled via the official Google Play Publisher API (`androidpublisher` v3) via `tools/update_play_listing.py`, reading directly from `fastlane/metadata/android/en-US/`.
   - Dispatched securely in GitHub Actions via `.github/workflows/play-listing.yml` under the `release` environment containing the `PLAY_SERVICE_ACCOUNT_JSON` secret.

## Verification

- `tools/update_play_listing.py`: Title (30/30), Short (80/80), Full (3852/4000) all verified within Play constraints.
- `tools/check_privacy_claims.sh`: Green.
- `tools/check_site_version.sh`: Green.
