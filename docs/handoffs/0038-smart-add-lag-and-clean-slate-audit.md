# 0038 — Smart Add stays fluid, the duplicate gate is honest, and a clean-slate audit

**Status:** done. Branch `perf/smart-add-mapping` (PR #57 → `dev`, merge
`9ba5fe3`). **509/509 tests**, lint clean, licence + privacy checks green.
One display-read verification pending a device unlock (see "Still open").

## Where this came from

The owner reported Smart Add lagging heavily while messages are automatically
categorised, and asked for a full device-side audit of the totals — every
message, saved or skipped — with the real data never recorded anywhere.

## 1. The lag — root-caused and measured

Every database write during a drain made the triage observer re-derive the
whole list, re-running a full regex parse for every row without a stored parse
(all skipped + pending rows — hundreds of parses per write), **on the main
thread**. Several writes per second → the reported stalls. Fixes:

- **`ParseMemo`** (`data/classification`): a bounded, self-validating,
  access-ordered LRU for the body re-parse. A queue row is immutable once
  inserted, so an id-keyed entry is exact; body and sender are re-checked on a
  hit anyway, so even a hypothetical id reuse re-classifies rather than serving
  a stale parse.
- The derivation runs on `Dispatchers.Default` with `conflate()` — only the
  newest list reaches the UI.

**Clean-slate A/B on the device** (uninstall → fresh full re-ingest → Smart Add
visible → SurfaceFlinger `--latency` sampling, 120 s; `gfxinfo` reports 0
frames for this app on the ROM — see device notes):

| | baseline (pre-fix) | fixed |
|---|---|---|
| late frames (>1.5× refresh) | 139 / 904 — **15.5 %** | 4 / 127 — **3.3 %** |
| frame interval p50 / p95 / max | 16.8 / **435** / 904 ms | 16.7 / **17.7** / 603 ms |
| rows processed in window | 150 | 147 |

The fixed build also drains faster — it no longer stalls its own pipeline.

## 2. The duplicate gate swallowed a real payment

Layer 3 of the semantic-duplicate guard filtered amounts with
`maxOf(1.0, 0.2 % of amount)` — a ±₹1 licence for small amounts. On the
owner's device a card swipe 52 minutes after a similar one at the same
merchant was auto-skipped as its "duplicate". A temporary instrumented build
named the branch (`duplicate=true` with a clean parse on both sides), which
also corrected an earlier wrong hypothesis (an accidental swipe). Fix: the
gate is paise-flat (`≤ 0.01`) — SMS amounts are deterministic; a re-delivered
copy states the same amount to the paisa. Red-first regression test added
(`same merchant within the hour is not a duplicate when the amounts differ`).
On the fresh re-ingest the fix rescued **two** payments automatically, and the
skipped set now contains **zero rows without an exact-amount twin or a clear
non-transaction reason**.

## 3. The clean-slate audit (owner-directed: "uninstall and start from scratch")

Protocol: uninstall → install the baseline build → seed permissions/prefs →
fresh re-ingest + A/B measurement → uninstall → install the fixed build →
fresh re-ingest → wait for the history walk to flag done and the queue to
empty → audit the settled database.

- The fresh run **reproduced the books exactly** (same saved/skipped counts as
  the meticulously hand-repaired dataset) — the strongest end-to-end
  validation there is; 1:1 saved-row↔transaction pairing, zero orphans.
- **Skipped rows:** every one is either a clear non-transaction
  (OTP/statement/bill/FD-renewal/freebie/failed-attempt) or has an
  exact-amount twin transaction.
- **Saved rows:** amounts and directions consistent with their messages. The
  one flagged row is an EPF "contribution … has been received" booked as
  INCOME — a judgment call left to the owner (it is a PF contribution, not
  spendable income).
- **Totals vs the display:** "Total balance" (all-time net) and "In this
  month" verified MATCH on the device; the month-expense read landed while the
  history walk was writing a new page, so one clean re-read is pending (see
  below).

## Device-pass notes (for the next session)

- **vivo/OriginOS: `dumpsys gfxinfo <pkg>` reports 0 frames for this app**
  (it works for system apps). Use `dumpsys SurfaceFlinger --latency <surface>`
  sampling instead; the app's usable surface name is the **plain**
  `com.awbuilds.auraspend.dev/...MainActivity#NNN` (not the hex-prefixed one —
  pick the candidate whose `--latency` returns more than the period line).
- The keyguard cannot be dismissed from adb once armed (face/PIN only) — plan
  screen-dependent steps while the owner is present.
- A fresh install needs `shared_prefs/auraspend_prefs.xml` seeded via `run-as`:
  `onboarding_completed`, `auto_detect_sms` — **and `currency_code=INR`**:
  without it the app follows the device locale, and this phone's locale
  renders `$`. (Found by accident during the audit; harmless, corrected.)
- The fresh dev install now runs with **auto-detect ON and no model
  installed** (AI off by design after the wipe) — the earlier mystery of the
  model files disappearing became moot; the model can be added any time from
  Settings.

## Still open

- One unlocked dashboard read to finish the month-expense comparison.
- The owner's call on the EPF row (income vs not).
