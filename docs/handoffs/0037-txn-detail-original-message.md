# 0037 — the transaction page shows the original message

**Status:** done 2026-10-13. Branch `feat/txn-detail-message` (PR #56 → `dev`,
merge `101baf3`). **501/501 tests** (11 new), lint clean, EN+HI parity, licence
and privacy checks green.

## What the owner asked for

"Clicking a record should show the full message" — the detail page only ever
showed a truncated stand-in (the pipeline's note). Bounded design approved in
chat before any code: full-message card with sender, received time and Copy;
hide the Note row only when it is pipeline filler.

## What shipped

- **Original-message card** on `TransactionDetailScreen` (only when the queue
  row is still on file): sender ID, "Received" time, the complete body
  (selectable, no truncation), Copy → "Message copied" snackbar. Manual
  transactions have no `sourceSmsId` and simply don't render the card.
- **`SmsMessageLookup`** (`data/local`): read-only `byId` over `sms_messages`,
  boundary-wrapped. Missing row, missing database or a failed read all degrade
  to "no card" — the transaction itself is never affected. Wired once in
  `NavGraph` (app-cast on the container, the same pattern the ViewModels use).
- **Note-row rule** (`isPipelineNote`, `ui/transaction`): the pipeline note is
  the merchant repeated or the body's first 120 characters
  (`BankMessageParser.truncateMessage`, visibility raised to `internal` so the
  two shapes cannot drift). Only that exact shape hides; an edited note, a
  stale-merchant note, a blank note or a missing body keep the row. Direction
  of error: show when in doubt.
- **Copy readback**: after `setClipEntry`, the app reads its own write back and
  logs lengths only (never content) — some OEM clipboards accept a write and
  keep serving something else. Deliberately `AuraLog.i`.

## Device verification (vivo V2158, dev build)

- The Railway-wallet July message: card shows
  `From AD-IRSMSa-S · Received 22 Jul 2026 · 6:19 AM` and the full 112-char
  body; **no Note row** (its content was pipeline filler); Copy shows the
  snackbar and logs
  `AuraSpend/TransactionDetail: message copy: 112 chars, clipboard readback=112`.
- The search → detail flow was re-exercised end to end (also re-verifies the
  0036 paging-crash fix; stayed stable).
- Screenshot reviewed against the design system: `AuraCard` Outlined, typography
  and spacing tokens only.
- **Device gotcha, worth remembering: vivo/OriginOS drops `Log.d` app lines.**
  The first readback used `.d` and never appeared in logcat while `I`/`E` lines
  from the same process do — field diagnostics on this device must use
  `i`/`w`/`e`. (Found by grepping a cleared buffer after a guaranteed-executed
  code path.)

## Tests

- `SmsMessageLookupTest` (Robolectric, real Room): returns the stored row;
  unknown id → null; unavailable database → null.
- `TransactionDetailNoteRuleTest` (8 cases): hidden for the merchant copy, the
  truncated body, a short body, a multi-line body; **visible** for an edited
  note, a stale merchant note, no body, a blank note.

## Open / next

- The card shows the stored body, which was masked at ingest — same privacy
  model as everywhere else; nothing new leaves the device.
- More fields on the page (balance-after, reference numbers) would be parser +
  storage work — deliberately not bundled here.
- 0.1.4 candidate now carries: the encoder actually running, the date fixes,
  the search fix, and this page.
