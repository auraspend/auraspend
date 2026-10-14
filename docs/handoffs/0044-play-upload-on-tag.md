# 0044 — one tag now ships to GitHub and Google Play

**Status:** done. PR #67 (merge `212c2a9`) → `dev`. The Play upload credential
is stored as the `PLAY_SERVICE_ACCOUNT_JSON` secret on the `release` environment,
and its identity is **deliberately unrecorded anywhere in this repository**
(AGENTS.md now carries that as a durable rule). No tests changed; PR Check green.

## What a `v*` tag does now

1. Builds and verifies the signed AAB + APK (unchanged), attaches them to the
   GitHub Release (unchanged).
2. **New:** uploads the signed AAB to Google Play's closed-testing track with
   - the per-language notes from `docs/store/whatsnew/whatsnew-<locale>`
     (`en-US` + `hi-IN`, both verified ≤500 characters), and
   - the R8 `mapping.txt`, so crash reports stay readable.

Tag runs only: a `workflow_dispatch` builds artifacts without touching any
store. The run stays behind the same `release` environment approval as before —
the approval now also gates the Play upload.

Knobs, both documented in the workflow header:
- `PLAY_TRACK` repository variable overrides the destination track
  (default `alpha`);
- `changesNotSentForReview: true` on the upload step stages a release for a
  manual "send for review" instead of auto-submitting.

## Pre-flight evidence (before any tag depends on it)

A local, read-only probe with the stored credential confirmed:

- the service account authenticates for the `androidpublisher` scope;
- it has release access to `com.awbuilds.auraspend`;
- the track list is `production / beta / alpha / internal`, with `alpha` (the
  closed-testing track) holding one existing release — so the workflow's
  `alpha` default is the confirmed correct name;
- the draft edit the probe opened was deleted; nothing in Play changed.

## Open precondition

If the Play **upload-key reset** is still pending from the identity scrub, the
first tag run's upload will fail with a signature error until Google approves
it — the failure is loud and safe (nothing publishes). First real proof of the
whole path is therefore the next tag.

## F-Droid

Unchanged plan, sequence now explicit on issue #65: cut the release (the Play
upload happens in that run), then open the fdroiddata merge request pointing at
that tag — everything is staged in `docs/fdroid/`, and the MR itself needs the
owner's GitLab account. No app-side work remains.

## Pending on the owner's device

Still waiting on the phone's wireless debugging: the visual pass for the
theme/chart fixes, the two 0041 leftovers, and re-typing the saved EPF rows
(Expense + Investments).

## Next

- Cut the next release on the owner's word (dev→main PR + tag): that single run
  now feeds both GitHub and Play, and unblocks the fdroiddata MR.
