# 0054 — Play listing title updated; tags are Console-only

**Date:** 2026-10-17
**Scope:** Play listing text only. No app source, tests or release artifacts
changed.

## What changed

- The en-US Play title is now **`AuraSpend: SMS Expense Tracker`** (30/30),
  up from `AuraSpend: Expense Tracker` (26/30). The Play API response confirmed
  the before/after values and the edit committed.
- `docs/store/README.md` now records the live title, the unchanged short
  description and the still-unversioned full description.
- The canonical copy in `docs/store/play-listing-en-US.md` is unchanged apart
  from a status note.

## Tags

The public Play Developer API's listing resource exposes only `language`,
`title`, `shortDescription`, `fullDescription` and `video`
([`edits.listings`](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.listings));
`edits.details` exposes only contact/default-language fields. There is **no
API field for Play store tags**, so the tags could not be changed through the
API. They remain a Play Console field and need a manual selection. Suggested
tags: Budgeting, Expense tracking, Personal finance, Money management, SMS.

## Privacy / credentials

No credential, account identity, project name or key was added to the
repository, docs, commit messages or workflow logs. The one-off dispatch
workflow and its helper were removed after the update; the only retained
record is this handoff and the store docs above.

## Verification

- Dispatch run succeeded; logs showed
  `current title: 'AuraSpend: Expense Tracker'` and
  `updated title: 'AuraSpend: SMS Expense Tracker'`.
- No build/test run was needed; only Play metadata and Markdown changed.

## Open

- Set the five tags manually in the Play Console (API limitation above).
- The short/full descriptions still need the manual paste from
  `docs/store/play-listing-en-US.md`.
- 0.1.6 remains on Play closed testing (`alpha`), not production.
