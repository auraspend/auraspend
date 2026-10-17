# 0050 — Classification fixes: honest eval, sender-aware vetoes, merged routing, transfers

**Date:** 2026-10-17
**Branch/worktree:** `ui_change_test` (the 0048 working tree), **not committed** —
the owner asked for the working tree.
**Tests:** 616/616 green, 1 skipped (`:app:testProdDebugUnitTest --rerun-tasks`).
**Lint:** `:app:lintProdDebug` clean (1 hint; the 45 errors are baseline rows).
**Licence/privacy:** both checks pass.

This is the implementation session for the audit in
[`0049`](0049-classification-gap-analysis.md). All changed/added files are in
the classification path, its tests, the corpus, and docs; no unrelated source
was touched.

## What changed

### 1. The eval now measures the product

- `ClassificationEval.Case` carries `amount` and `date`; `RegexBaselineEval`
  scores **seven** fields. The corpus's 58 positive cases have a labelled
  amount, and the 17 messages that state a date have one (undated messages are
  not scored on date — "pay today" is not a parse error).
- A correct rejection is now a **pass**: a non-transaction that the pipeline
  skips scores `isTransaction=false`, `cat_other`, null type/merchant, false
  subscription. The old evaluator scored null as a miss on every field.
- `falseVetoes()` counts `case.isTransaction && rejection == ALERT_FILTER` (and
  model vetoes), not `predicted.isTransaction == false` — the regex path never
  emits that for a skip, so the old counter was structurally always zero.
- Merchant comparison is case/whitespace-insensitive; `SWIGGY` vs `Swiggy` is
  not a miss.
- The production `categoryResolver` runs in the eval; triage and the background
  pipeline share `TransactionClassifier.resolveCategory`.
- `FusedAiEval` scores through the same `RegexBaselineEval.score` helper and
  includes amount/date, so the no-model fixed-point test is meaningful again.
- Floors: exact 97.0 %, type 98.0 %, category 99.0 %, merchant 98.0 %, amount
  98.0 %, date 99.0 %. A regression in any field fails CI.

Measured: **80/82 exact (97.6 %), isTransaction 98.8 %, type 98.8 %, category
100 %, merchant 98.8 %, subscription 100 %, amount 98.8 %, date 100 %, one
accepted false veto** (`canara_debit_10000`). The two non-exact cases are that
documented trade-off and `flight_booking`, whose merchant is the destination
"Dubai" (deliberately left null).

### 2. The HDFC “If not you” false veto

`isOtpOrAlertMessage` takes an optional `sender`; the pipeline, ingestor, eval
and replay pass it. A known bank's own alarm tail (known sender + completed debit
+ call/SMS/report/block/contact after the conditional phrase) no longer vetoes
the message. Unknown senders keep the hard veto. Pinned by
`SmsMovementVerbRescueTest` and `p2p_upi_debit_person_refno`.

### 3. Sender routing specializes instead of replacing

`TransactionClassifier` parses globally first and merges the routed result over
it, non-null field by field; a date-only routed parse cannot overwrite a global
timestamp with the time. Single-line Axis keeps `SUNRISE DINER` and its time;
Canara NEFT keeps `ACME SOLUTIONS LIMITED` and its date. Pinned by
`RoutedParserMergeTest`.

### 4. Own transfers and multi-movement SMS

- A narrow `ownTransferPattern` detects "transfer to self", "from SBI to your
  own HDFC account", and explicit own-account transfers. It does **not** treat
  ordinary "debited from your own account at AMAZON" as a transfer.
- Own transfers are booked as `EXPENSE`/`INCOME` with `cat_transfer` (no
  `TRANSFER` enum yet) and **excluded from every aggregate** — summary,
  balance, category, daily and per-type totals. A two-leg transfer no longer
  inflates both sides; a "to self" message no longer inflates spending.
- A batched "INR 150 debited at SWIGGY and INR 200 credited" now books the
  first completed movement (the debit) instead of returning typeless. The full
  body remains the note; the second movement is not split into its own row.
- Pinned by `TransferAndInvestmentCategoryTest`, `TransactionDaoTest`.

### 5. Merchant extraction

- Digits are allowed unless they look like an account/reference (`\d{6,}`,
  masked `XXXX6021`); `SHOP 123` survives; `Kotak Bank a` is rejected by
  token-aware stop phrases.
- New patterns: salary `NEFT ACME CORP`, `refunded for cancelled Zomato order`,
  masked-mobile prefix cleanup, insurance-premium suffix cleanup.
- A shared `SubscriptionCatalog` names Netflix/Spotify/Amazon Prime/iCloud/etc.
  for both merchant extraction and `AiSignalFusion`, restoring the full service
  list and giving the Subscriptions screen a real service name.

### 6. Categorisation

- Income goes through its own resolver: salary explicit, rent received → Other,
  refunds keep the original merchant category (Zomato refund → Food), P2P
  transfers still → Transfer. The expense map no longer turns "rent received"
  into a bill.
- The parser map and `CategoryKeywordMap` agree on streaming services
  (Subscriptions); `CategoryMapConsistencyTest` fails on any future divergence.
- Insurance premiums are a recurring subscription (`LIC insurance premium`).
- Manual triage uses `TransactionClassifier.resolveCategory`, matching the
  background pipeline; the old `?: "cat_other"` is gone.
- The corpus had five non-derivable/debatable labels corrected (three Axis
  categories, Canara NEFT salary, ATM merchant, rent, flight); notes in the JSONL
  state why. The audit had already identified them as label noise.

### 7. Memory / feedback loop

- `MemoryKeys.candidates` tries the full normalised key and then its first
  token, so a correction for "Swiggy" reaches "SWIGGY BANGALORE".
- A category change on the transaction detail screen now teaches the classifier
  (`SOURCE_USER_SAVE`), not just a triage save.
- A memory hit fills the category/type but still runs `AiSignalFusion`, so a
  remembered Entertainment category no longer suppresses recurring detection.
- `AiSignalFusion` only sets recurring-by-keyword for expenses; a credit that
  mentions "auto-debit" is not a subscription.

### 8. Ingest and foreign currency

- The provider query is now `(body markers) OR (sender markers)`; body markers
  add `₹`, `Dr.`, `Cr.`, `sent`, `received`, `transferred`, USD/EUR/GBP/AED/SGD;
  sender markers cover banks outside the old regex (Jupiter, JioPay, Pluxee,
  IPPB, NSDL, …). Pinned by `SmsIngestKeywordsTest`.
- A foreign-currency amount is **not** booked as rupees. There is no
  per-transaction currency field yet, so `USD 10 spent …` parses no amount and
  is surfaced as unrecognized rather than saved as ₹10.

## Decisions and trade-offs

- **No `TRANSFER`/`INVESTMENT` enum.** They would touch UI, paging filters and
  every `when`; the category-based exclusion fixes the accounting bug now. The
  structural follow-up is recorded in the review.
- **No full multi-currency.** A currency column, migration, backup format and
  display path are a feature, not a fix. The safety guard prevents a wrong INR
  booking; the row is visible in Unrecognized.
- **No full user-rules UI.** The audit's user-rules gap is mitigated by
  detail-edit learning, prefix-generalised memory and the consistent resolver.
  A visible rules/alias editor remains a follow-up.
- **Sender-aware phishing rescue** accepts that a spoofed bank sender ID could
  rescue a lure; the DLT sender is already trusted for parsing, and the rescue
  needs a completed debit plus a bank-tail instruction. The Canara lure remains
  vetoed because its "report" precedes the conditional phrase.
- **Corpus labels are policy.** Changing them was part of fixing the metric, not
  a way to raise the score; each changed note explains the reason.

## Verification

| Command | Result |
|---|---|
| `./gradlew :app:testProdDebugUnitTest --rerun-tasks` | **616 tests, 0 failures, 1 skipped** |
| `./gradlew :app:lintProdDebug` | clean (1 hint, 45 baseline errors) |
| `bash tools/check_license.sh` | “Licence metadata is consistent.” |
| `bash tools/check_privacy_claims.sh` | “every absolute claim states its exception.” |

The eval block now prints **80/82 exact (97.6 %), one accepted false veto**.

## Open risks / next

- **Device pass not run.** The broader sender query can add queue noise; the
  detail-screen learning and the transfer-exclusion queries need a real inbox
  walk. The owner's `ui_change_test` tree is uncommitted.
- **Existing rows:** `cat_transfer` is now excluded from totals. Any historical
  row a user had filed under Transfer disappears from spending/income — that is
  the intended correction, but a device check should confirm the Dashboard/Plan
  numbers move only by the transfer amounts.
- **Two-leg transfers are not paired.** Each leg is excluded, so totals are
  right, but the Activity list shows two rows and the user cannot mark the pair.
  `computeSelfTransferPairs` (PennyWise model) remains a follow-up.
- **Full multi-currency** and **first-class TRANSFER/INVESTMENT** need schema
  and UI work; the review's §9 P2 items stand.
- **Hindi/regional SMS** are still not parsed; the ingest query does not add
  Devanagari markers because the parser has no Hindi lexicon yet.
- The corpus is still 82 synthetic cases; per-bank amount/date fixtures for the
  top 20 banks are the next measurement investment.
