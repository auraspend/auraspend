# 0049 — Message classification gap analysis (read-only)

**Date:** 2026-10-17
**Branch/worktree:** current tree (the 0048 terracotta working tree), **no source
changes**.
**Tests:** 584/584 green, 1 skipped (`:app:testProdDebugUnitTest --rerun-tasks`).
The classification diagnostic that produced the numbers below ran in a
throwaway copy at `/tmp/aura-analysis`, not in this tree.
**Licence/privacy checks:** both pass.

## What this session did

Audited the SMS classification path end to end and compared it with
`sarim2000/pennywiseai-tracker` (cloned to `/tmp/pennywiseai-tracker`, read
only). The full report is
[`docs/reviews/2026-10-17-message-classification-gap-analysis.md`](../reviews/2026-10-17-message-classification-gap-analysis.md).

The trigger was the tester's 0047 export and the current floor: 26/82 exact
(31.7 %), type 95.1 %, category 53.7 %, “0 false vetoes”. The audit found that
the floor is **not measuring what it claims**, and that sender routing is
silently discarding fields the global parser already extracted.

## Headline findings

1. **The eval scores correct rejections as failures.**
   `ClassificationEval.Case` has no amount or date; `run()` scores a null
   result as a miss on every field. 24 of the 56 non-exact cases are
   non-transactions the classifier correctly skipped. Scored properly, the same
   five fields give **50/82 (61.0 %)** exact and **78/82 (95.1 %)**
   `isTransaction`; the four `isTransaction` misses are the dropped
   transactions in §2, and the rest of the exact gap is merchant case/label
   noise.
2. **“0 false vetoes” cannot be true.**
   `Report.falseVetoes()` only counts `predicted.isTransaction == false`, but
   the regex path returns `null` for a skipped row. The corpus contains a real
   counterexample: `p2p_upi_debit_person_refno` (genuine HDFC UPI debit) is
   dropped by `conditionalFramingPattern` on “If not you, call your bank”.
   `isFraudLineVeto` excuses every non-OTP alert drop, so the test that should
   catch it does not.
3. **Sender routing replaces the global parse.** `TransactionClassifier`
   accepts a routed parse whenever amount/type are non-null
   (`TransactionClassifier.kt:38-41`). Measured: single-line Axis loses
   `SUNRISE DINER` and its time; Canara NEFT loses `ACME SOLUTIONS LIMITED`
   and its date. The per-bank tests call `BankMessageParser.parse` directly for
   these shapes, so they do not catch it.
4. **The transaction filter is sender-blind.** `isOtpOrAlertMessage(message)`
   never sees `message.address`, so it must treat a known bank's own
   “If not you…” tail like a phishing lure. The ICICI/PNB exceptions are
   ad hoc; HDFC's tail is not covered.
5. **The domain model cannot represent a transfer.** Only
   `INCOME`/`EXPENSE` exist; totals sum every expense with no category
   exclusion (`Daos.kt:73`). `own_account_transfer` is typeless and skipped;
   a “transfer to self” message is booked as an expense and inflates spending.
6. **Categorisation is a parser side effect.** Two divergent keyword maps
   (`DefaultCategories` says Netflix → Entertainment, `CategoryKeywordMap`
   says Subscriptions), a direction-blind whole-message scan (“rent received”
   → Bills), a triage-only feedback loop, and no user rules. PennyWise's rules
   engine + merchant mapping is the architecture gap, not its bank count
   (India ~50 vs AuraSpend 54).
7. **Ingest is a 22-word body allowlist.** `queryBankSms` filters the provider
   by `BODY LIKE` before sender routing (`SmsAutoClassifier.kt:314`); no `₹`,
   `Dr`, `Cr`, `sent`, `received`, `transferred`, ISO currency or Hindi
   markers. A registered bank message without one of those tokens is never
   read. PennyWise streams all inbox SMS and routes by sender.

## PennyWise comparison in one line

Same sender-routed idea, different seam: PennyWise's per-bank parsers override
**individual field extractors** (29,358 lines, 161 parsers, 150 test files /
260 tests), dispatch to the first parser that successfully parses, keep
categorisation in a user rules/merchant-mapping layer, and have `TRANSFER` /
`INVESTMENT` types plus account/balance/currency fields. AuraSpend's per-bank
parsers return a whole `ParsedBankMessage` (HDFC 56 lines vs 734; ICICI 44 vs
672; SBI 67 vs 696; Canara 81 vs 194) and lose whatever they do not fill.
The full comparison, including what **not** to copy (PennyWise's blunt
“offer/discount” blocklist, one-transaction-per-SMS assumption, 25-country
breadth), is in the review.

## Recommended order

1. **Fix the eval first** (P0): correct rejections, amount/date fields, a real
   false-veto counter, production `categoryResolver`, case-insensitive
   merchant, per-bank floors. Do not tune the parser against the current
   number.
2. **Fix the HDFC “If not you” false veto** (P0): sender-aware filter or a
   bank-owned-tail exception; add corpus cases for every major bank tail.
3. **Merge routed parses over the global parse** (P0): non-null fields win, or
   fall back when merchant/date/category regress. Add routed tests.
4. P1: self-transfer/multi-movement, merchant gaps (digits, salary/refund/
   autopay), categoriser extraction + user rules + detail-edit learning,
   sender-routed ingest + foreign currency.

## Verification

| Command | Result |
|---|---|
| `./gradlew :app:testProdDebugUnitTest --rerun-tasks` | **584 tests, 0 failures, 1 skipped** |
| `bash tools/check_license.sh` | “Licence metadata is consistent.” |
| `bash tools/check_privacy_claims.sh` | “every absolute claim states its exception.” |
| `./gradlew :app:lintProdDebug` | clean (up-to-date; no source changed) |

The corpus/routed diagnostics were run in `/tmp/aura-analysis` with two
temporary test classes; they are not committed. The PennyWise Gradle test run
was attempted but its 9.7.1 wrapper download timed out, so the comparison is
source-level, not a measured run of its suite.

## Open risks / notes for the next agent

- The 31.7 % figure is **not** parse accuracy. Read the review before using it
  as a baseline; amount and date are not in the corpus at all.
- The four §2 dropped transaction cases are the ones to pin first; the Canara
  `canara_debit_10000` phishing trade-off stays the only accepted loss.
- The corpus's category labels are partly arbitrary (Tulip → Food,
  Amber Leaf → Shopping, rent → Other, LIC → Subscriptions). Separate label
  policy from parser accuracy before treating category % as a gate.
- The `SensitiveDataMasker` was read but not re-tested; its 7+ digit
  unseparated integer behaviour is an edge case worth checking before adding
  large-amount fixtures.
- No source, test, corpus or eval file in this tree was modified; only this
  handoff and the review were added.
