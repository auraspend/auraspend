# 0039 — detected subscriptions finally surface

**Status:** done. Branch `feat/detected-subscriptions` (PR #58 → `dev`, merge
`d60d82c`). **516/516 tests** (8 new), lint clean, EN+HI parity, licence and
privacy checks green.

## What the owner asked

"The subscriptions are not shown even though there is an option in Settings —
do the user need to download the 22 MB thing or will it automatically
categorize?"

## The model answer (for the record)

**No download is needed for anything in this flow.** Categorisation
(expense/income/category) and recurring-payment detection both run on the
offline engine — regex, the 53 per-bank parsers, and the keyword/brand
recurring detector — and the Settings card now says so itself ("AuraSpend
already works without it"). The 22 MB model is an optional **refinement** that
fills category gaps on messages the keyword engine is unsure about; it is
opted into from the Smart Add card and nothing else depends on it. The app
tests explicitly pin the model-absent path ("the default free-tier path", see
`SubscriptionDetectionTest`).

## The real gap

The detector had been working all along — the fresh re-ingest carried **8
transactions flagged `isRecurring = 1`** (Netflix ×2, Spotify ×2, Apple
services, plus parser-artifact rows) — but **nothing ever displayed them**: the
Subscriptions screen read only the manually-tracked `subscriptions` table, and
`getRecurringTransactions()` had no production consumer at all. A fresh
install showed "No subscriptions" while real subscriptions were being paid.

## What shipped

- **`detectedSubscriptions()`** (`ui/recurring/DetectedSubscriptions.kt`): a
  pure function grouping the flagged transactions into one suggestion per
  service.
  - case-insensitive grouping on the merchant; the newest transaction
    supplies the display name, amount, date and category; newest first;
  - rows without a merchant are skipped (a suggestion titled after a message
    paragraph is noise); names that are field-word artifacts ("merchant",
    "payment", …) or carry fewer than three letters are skipped; expenses
    only;
  - suggestions already covered by a tracked subscription are suppressed by a
    loose both-way match ("Netflix" silences "NETFLIX COM").
- **The screen** gains a "Detected from your messages" section: per-service
  rows (avatar in the category colour, last-paid date, amount) with a **Track**
  action that opens the existing add sheet **prefilled** (name, amount as
  plain digits, monthly cycle, category when it resolves). Suggestions stay
  out of the tracked list and the monthly total until the user confirms — the
  flag is a guess; the total must not be.
- The monthly total card only renders when something is tracked (no ₹0 card
  above a detected-only list). EN + HI strings, 8 new tests.

## Verified on-device (fresh dev install, owner's real inbox)

- Settings → Subscriptions rendered the section with the owner's three real
  services. **Names, amounts and dates are that person's private data and are
  deliberately not recorded here.** Observed in the run: repeats of one service
  collapse into a single row carrying its newest payment; ordering is
  newest-first; junk rows are absent; no ₹0 total card appears.
- Tapping **Track** opened the add sheet with the service name and the amount
  (plain digits, no trailing ".0") prefilled; the category is deliberately left
  for the user when
  the detected category does not resolve (the existing sheet validation keeps
  the save button disabled until one is picked). Dismissed without saving —
  tracking is the owner's tap to make.

## Session notes

- Two branches briefly existed for this work (`feat/detected-subscriptions`,
  `feat/recurring-detections`) because an edit/commit batch was re-run after a
  lost tool output; the stray branch was deleted, everything is on the PR's
  branch and merged.
- A KSP incremental-cache hiccup after the server restart was cleared with
  `rm -rf app/build/kspCaches`; not a code issue.

## Still open

- The owner's call on the EPF "contribution … has been received" row booked as
  INCOME (from the 0038 audit).
