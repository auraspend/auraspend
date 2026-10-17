# Message classification — gap analysis and PennyWise comparison

**Date:** 2026-10-17
**Scope:** audit of AuraSpend's SMS classification path, plus a source-level
comparison with `sarim2000/pennywiseai-tracker` at HEAD. The audit itself was
read-only; the diagnostic tests ran in a throwaway copy (`/tmp/aura-analysis`).

## Resolution (same day, later session)

The defects in this report were fixed in
[`0050`](../handoffs/0050-classification-fixes.md). Current regex floor:
**80/82 exact (97.6 %), type 98.8 %, category 100 %, merchant 98.8 %, amount
98.8 %, date 100 %, one accepted false veto** (`canara_debit_10000`). The eval
now scores amount/date and correct rejections; the HDFC “If not you” debit is
rescued by sender context; routed parses merge over the global parse; own
transfers are excluded from every aggregate; and the merchant, category,
ingest, memory and detail-edit gaps are closed. The two remaining non-exact
cases are the documented Canara trade-off and a flight-booking merchant that is
a destination. Structural follow-ups still open: first-class
`TRANSFER`/`INVESTMENT` types, full multi-currency, a user-rules UI, and
Hindi/regional SMS.

## Verdict

The regex layer is not the main problem. The main problems are:

1. **The quality metric hides the mistakes it should expose** — correct
   rejections are scored as misses, amount and date are not scored at all, and
   the "false vetoes" number cannot see a skipped row.
2. **Sender routing replaces the global parse instead of specializing it** — a
   per-bank parser that resolves amount and type wins outright, even when it
   drops a merchant or date the global parser already had.
3. **The transaction filter is sender-blind** — it vetoes a genuine HDFC
   "If not you…" debit because a phrase blocklist cannot tell a bank's own
   tail from a phishing lure, even though the sender ID is available.
4. **The domain model has no way to represent a transfer** — own-account
   transfers are dropped or, when they contain "to self", booked as expenses
   that inflate spending.
5. **Categorisation is a parser side effect** — two divergent keyword maps, a
   whole-message direction-blind scan, no user-editable rules, and a feedback
   loop that ignores edits made on the transaction detail screen.

PennyWise is architecturally different in exactly the places that matter:
deep per-bank parsers with overridable field extractors, content-aware
dispatch, sender-routed ingest, a user rules/merchant-mapping layer, and
`TRANSFER`/`INVESTMENT` as first-class types. It is **not** mainly a
"more banks" difference — the Indian bank count is similar (~50 vs 54); the
difference is depth, separation of concerns, and test fidelity.

---

## 1. The headline number is not measuring the product

The measured floor is **26/82 exact (31.7 %), type 95.1 %, category 53.7 %,
merchant 64.6 %, subscription 64.6 %, 0 false vetoes**. Those numbers are from
`RegexBaselineEval` over `app/src/test/resources/golden/sms_corpus.jsonl`.

They are arithmetically correct but misleading for four separate reasons.

### 1.1 Correct rejections are scored as total misses

`ClassificationEval.Case` has no amount or date field, and `run()` scores a
null result as a miss on every field:

- `app/src/test/java/com/awbuilds/auraspend/data/ai/ClassificationEval.kt:41`
  — `Case` fields are `id, body, isTransaction, type, category, merchant,
  subscription, note, sender`.
- `:121` — “A null return is scored as a total miss for all fields”.
- `RegexBaselineEval.classify` returns `Classified(null, …)` both when the
  alert filter rejects a message and when amount/type are unresolved
  (`RegexBaselineEval.kt:85`, `:92`).
- For a non-transaction, the expected `isTransaction` is the string `"false"`;
  the actual is `null`. So **a correct skip is counted as a wrong answer**.

24 of the 56 non-exact cases are non-transactions that the classifier
correctly skipped (OTP, promo, balance, mandate, due reminder, points, NAV,
failed payment, etc.). If those were scored as correct rejections, exact match
would be **50/82 (61.0 %)** on the same five fields, and `isTransaction` would
be **78/82 (95.1 %)**. The four `isTransaction` misses are the genuinely
dropped transactions in §2; the rest of the exact-match gap is merchant case
and category-label disagreement, not lost money.

The 28 “no result returned” are therefore not 28 failures; 24 of them are the
classifier doing the right thing. The report does not distinguish “correctly
rejected” from “could not parse”, which is the first distinction a
classification report needs.

### 1.2 Amount and date are not scored — and the test comment says they are

The five scored fields are `isTransaction, type, category, merchant,
subscription` (`RegexBaselineEval.kt:55`). Amount and date are absent from
both the corpus and the evaluator.

The test's own comment claims otherwise:

> “Exact match is a deliberately harsh metric here: two of the five scored
> fields (amount, date) are not even in the model's schema…”
> — `RegexBaselineEvalTest.kt:30`

There are five scored fields and neither is amount or date. The comment
describes a metric that does not exist. This matters because amount is the
parser's primary output and the field whose failure costs the user money; a
wrong amount is worse than a missing category, and nothing in CI would notice
it.

### 1.3 “0 false vetoes” is structurally impossible to disprove

`Report.falseVetoes()` only counts a result whose `predicted.isTransaction` is
explicitly `false`:

```kotlin
fun falseVetoes(): List<CaseResult> = results.filter {
    it.predicted?.isTransaction == false && it.case.isTransaction
}
```

The regex path never produces an extraction for a vetoed message — it returns
`null` (`RegexBaselineEval.kt:85`). So for the regex-only report the predicate
can never fire. It is not evidence that no genuine transaction was vetoed; it
is evidence that the report has no representation for a vetoed row.

There is a counterexample in the corpus: `p2p_upi_debit_person_refno` is a
genuine HDFC UPI debit, expected to be booked, and it is dropped by the alert
filter. The report still prints `false vetoes: 0`.

The related test is also over-broad:

```kotlin
fun isFraudLineVeto(body: String): Boolean =
    SmsAutoClassifier.isOtpOrAlertMessage(body) &&
        !Regex("(?i)otp").containsMatchIn(body)
```

`RegexBaselineEval.kt:141` — this returns true for **every** non-OTP alert
drop, not just the documented Canara phishing trade-off. The test that is
supposed to catch dropped real transactions (`RegexBaselineEvalTest.kt:78`)
therefore excludes the HDFC p2p case, and the “accepted loss” list contains
only `canara_debit_10000` (`:98`).

### 1.4 Merchant comparison is case-sensitive; category labels are partly arbitrary

Eight merchant “failures” are only case: `SWIGGY` vs `Swiggy`, `IOCL PETROL
PUMP` vs `Iocl Petrol Pump`, `APOLLO PHARMACY` vs `Apollo Pharmacy`,
`BIGBASKET`, `ZOMATO`, `UBER`, `AJIO`, and the Swiggy case in
`otp_in_transaction_sms`. Comparing normalised (case-insensitive,
whitespace-collapsed) merchant strings would move merchant accuracy from
53/82 to at least 61/82 (74.4 %) without changing the parser.

Some category labels are not derivable from the message or merchant:

| Case | Expected | What the parser can know |
|---|---|---|
| `axis_multiline_tulip` | `cat_food` | merchant “Tulip”; no “tulip” keyword anywhere |
| `axis_amber_leaf` | `cat_shopping` | merchant “Amber Leaf”; no keyword |
| `insurance_premium` | `cat_subscription` | “LIC insurance premium”; `lic` maps to Bills |
| `rent_payment` | `cat_other` | “house rent”; `rent` maps to Bills |
| `flight_booking` | `cat_other` | “flight”; `flight` maps to Transport |

These are taxonomy disagreements or arbitrary labels, not parser bugs. A
metric that mixes them with real misses cannot tell you whether a change
helped.

### 1.5 The floor is too low and too aggregate

`RegexBaselineEvalTest.kt:47` fails only below **30.0 %**, while the measured
value is 31.7 %. A 1.7-point regression passes. More importantly, a single
aggregate lets a whole bank regress while another bank improves. The Canara
and Axis routing losses in §3 sit under this floor.

---

## 2. Four genuine transactions the classifier does not book

| Case | Why it is dropped | Impact |
|---|---|---|
| `p2p_upi_debit_person_refno` | `conditionalFramingPattern` matches “If not you”; the bank-tail exceptions only cover the ICICI “call … / SMS BLOCK …” and PNB “forward this SMS … report unauthorized” shapes | A real HDFC debit is never booked |
| `own_account_transfer` | no `TRANSFER` type; the debit lexicon has “transferred to”, not “transferred from”, so `detectType` returns null; `hasMovementVerb` is false, so it is not even recorded as unrecognized | silently skipped |
| `multi_txn_single_sms` | both “debited” and “credited” fire, so `detectType` returns null by design | recorded as unrecognized, not booked |
| `canara_debit_10000` | hard fraud veto (documented accepted loss — keep this one) | accepted trade-off |

The HDFC case is the important one:

```
Dear UPI user A/C X1234 debited by Rs.200.00 on 12Mar26 trf to SURESH MENON
Refno 123456789012. If not you, call your bank.
```

The global parser reads it correctly: amount 200, EXPENSE, merchant
`SURESH MENON`, category `cat_transfer`. The alert filter drops it before the
parser runs because `conditionalFramingPattern`
(`SmsAutoClassifier.kt:215`) matches `if not you`, and neither
`bankAlarmTailPattern` nor `forwardAndReportTailPattern` matches “call your
bank”. This is not the documented phishing trade-off; it is a false veto on a
known bank's own boilerplate.

### 2.1 Own-account transfers are the worst case

The app has no `TRANSFER` type (`Models.kt:22` — only `INCOME` and `EXPENSE`).
`cat_transfer` is a category, and the totals query sums every `EXPENSE` with
no category exclusion (`Daos.kt:73`). Two consequences:

- A message phrased “transfer to self” matches `debitWords` (“transfer to”)
  and `keywordCategoryFor` (“to self” → `cat_transfer`), so it is booked as an
  **expense**, and the amount is counted in spending. That is a real
  accounting bug, not just a missing feature.
- A two-leg transfer (“debited from HDFC … credited to ICICI …”) becomes an
  expense and an income, inflating both totals. There is no pairing step.

PennyWise has `TRANSFER` in its type enum and a user-confirmed
`computeSelfTransferPairs` heuristic; it is not automatic, but the model can
represent the case. AuraSpend cannot.

---

## 3. Sender routing loses fields the global parser already had

`TransactionClassifier.classify` accepts the routed parse whenever amount and
type are non-null:

```kotlin
val routed = routedParse(rawMessage, sender)
if (routed?.amount != null && routed.type != null) return routed
return BankMessageParser.parse(rawMessage)
```

`TransactionClassifier.kt:38-41`

`BankParser.extract` returns a whole `ParsedBankMessage` (`BankParser.kt:56`),
so a bank parser is a **replacement**, not a set of overrides. Any field it
does not fill is lost, even if the global parser filled it. There is no merge
and no “is the routed result actually better?” check.

Measured on the corpus (global parse vs routed parse, same body):

| Case | Global parse | Routed parse |
|---|---|---|
| `axis_sunrise_diner` (single-line Axis) | merchant `SUNRISE DINER`, date `2026-07-03T20:11:45` | merchant `null`, date `2026-07-03T00:00` |
| `axis_tulip_mart` (single-line Axis) | merchant `TULIP MART`, date `2026-07-05T10:15:22` | merchant `null`, date `2026-07-05T00:00` |
| `canara_neft_credit` | merchant `ACME SOLUTIONS LIMITED`, date `2026-06-28T12:00` | merchant `null`, date `null` |

Why:

- `AxisBankParser` takes the merchant from `lines[timestampIndex + 1]`
  (`AxisBankParser.kt:80-82`). On a one-line message there is no next line, so
  the routed parser returns null and overwrites the global parser's good
  merchant. It also sets the time to 00:00 (`:98-108`).
- `CanaraBankParser.fromPayee` requires `from <NAME> NEFT|IMPS|…`
  (`CanaraBankParser.kt:37`). The real shape is “towards NEFT by Sender
  <NAME>”, which the global parser handles. The routed parser returns null and
  the global result is discarded.

This is a production regression that the test suite does not catch:

- `AuditRegressionTest` calls `BankMessageParser.parse` directly for the
  Canara NEFT case, so it tests the global parser, not the routed one.
- `TopBankParsersTest` covers HDFC/ICICI/Kotak but not the Canara NEFT or
  single-line Axis shapes.
- The corpus does catch them, but the 30 % floor and the coarse metric let
  them pass.

The same failure class can hit SBI and any other parser that reimplements
fields instead of layering on the global parse.

---

## 4. The transaction filter is sender-blind

`isOtpOrAlertMessage(message)` takes only the body
(`SmsAutoClassifier.kt:380`). The pipeline has `message.address` and the
corpus has `case.sender`, but neither is passed to the filter.

That forces the filter to be conservative against phishing for **all**
senders, including known banks. The result is a growing list of bank-specific
exceptions (`bankAlarmTailPattern`, `forwardAndReportTailPattern`) that must
be added one bank at a time, and HDFC's “If not you, call your bank” is not
covered.

A sender-aware filter can make the distinction the body cannot:

- known bank sender + completed debit + a bank-owned “call/SMS/report” tail →
  keep the transaction;
- unknown sender + “if not you / not authorised / report fraud” → hard veto.

The sender is already used to choose a parser; using it for the safety gate is
the same free, reliable metadata. PennyWise's sender-routed parser architecture
naturally has this context; AuraSpend's global pre-filter does not.

---

## 5. Common Indian SMS shapes are missing

These are not exotic; each is a normal message from a major bank or wallet.

| Shape | Example | What happens |
|---|---|---|
| Own transfer | “Rs 5000 transferred from SBI to your own HDFC account” | type null; skipped; merchant becomes `SBI to your own HDFC account` in the global parse |
| Two movements in one SMS | “INR 150 debited at SWIGGY and INR 200 credited as refund” | type null (both lexicons fire); not booked |
| Salary NEFT without “Cr-” | “Rs 45000 salary credited to A/c XX1234 NEFT ACME CORP” | amount/type/category fine, merchant null |
| Refund phrasing | “Rs 999 refunded for cancelled Zomato order” | amount/type/category fine, merchant null |
| Autopay service | “AutoPay of Rs 499 NETFLIX e-mandate successful” | amount/type/category fine, merchant null |
| Merchant with digits | “INR 300 debited at SHOP 123” | `looksLikeMerchant` rejects any `\d{3,}` (`BankMessageParser.kt:728`), so merchant null |
| Reversal credit | “…credited to Kotak Bank a/c no. 8584 … reversal of debit…” | merchant `Kotak Bank a` (junk) |

The merchant gaps are not cosmetic: a null merchant blocks the category
fallback, the subscription/service name, dedup fingerprints, and the
self-transfer person/business heuristic.

`cleanMerchant` only checks whether the string **starts with** a stop phrase
(`BankMessageParser.kt:719`), which is why `Kotak Bank a` survives — “bank” is
in the list but not at the start. The check should be token-based.

---

## 6. Categorisation is a parser side effect, not a policy layer

`BankMessageParser.categoryIdFor` (`:805`) runs inside the parser and:

1. looks up the merchant in the merchant CSV;
2. scans the **whole message** with `keywordCategoryFor` (`DefaultCategories.kt:348`),
   regardless of whether the transaction is income or expense;
3. applies the rail + person-name transfer rule;
4. defaults income with “salary/payroll/wages” to Salary.

Step 2 is direction-blind. “Rs 20,000 rent received from tenant” is an
**income** transaction that gets `cat_bills` because `rent` maps to Bills
(`DefaultCategories.kt:228`). “Flight refund” gets Transport. The fusion map
has separate income/expense tables (`CategoryKeywordMap`), but it only fills a
gap when the parser left the category null, so it rarely gets a say.

There are two divergent maps:

- `DefaultCategories.categoryKeywordMap` — `netflix` → **Entertainment**
  (`:232`), `lic` → Bills (`:221`), `rent` → Bills (`:228`),
  `flight` → Transport (`:151`).
- `CategoryKeywordMap` — `netflix` → **Subscriptions** (`:87`),
  `spotify` → Subscriptions, `amazon prime` → Subscriptions.

`AiSignalFusion` consults the second only when `base.categoryId == null`
(`AiSignalFusion.kt:88-95`). Since the parser almost always fills a category,
the subscription map is mostly dead. The final category depends on whether a
recurring/autopay keyword or a known subscription service happens to fire.

The two save paths also disagree:

- Background pipeline: `parsed.categoryId ?: categoryResolver(merchant)`
  (`SmsPipelineProcessor.kt:261`).
- Manual triage: `selectedCategoryId = parsed.categoryId ?: "cat_other"`
  (`ClassificationViewModel.kt:207`), no resolver.

The same routed message can therefore get a merchant-derived category in the
background and `Other` in the triage UI.

### 6.1 The feedback loop is triage-only and does not generalise

`ClassificationMemory` is a good idea, but:

- It learns on triage saves and auto-saves
  (`ClassificationViewModel.kt:373`, `SmsPipelineFactory.kt:44`), **not** when
  the user changes a category on the transaction detail screen
  (`TransactionDetailScreen.kt:327-330` saves directly and never calls
  `learn`). The most natural correction is ignored.
- The memory key is an exact normalised string (`MemoryKeys.normalize`); a
  location suffix or a different bank's merchant phrasing produces a
  different key, so one correction does not generalise.
- A memory hit returns early in `SmsAiEnricher` (`SmsAiEnricher.kt:43-56`) and
  sets `isSubscription = hit.categoryId == "cat_subscription"`. It skips
  `AiSignalFusion` entirely, so a remembered non-subscription category can
  suppress recurring detection for a service.
- There is no UI to inspect or delete a learned mapping; a wrong auto-save can
  stick.

PennyWise's equivalent is a user-editable merchant→category mapping written
when the user ticks “apply to all from merchant” on the detail screen
(`TransactionDetailViewModel.kt:1012`) plus a full rules engine. The
difference is not just storage; it is that a correction becomes a rule the
user can see and reuse.

---

## 7. Ingest recall is bounded by 22 body keywords

`SmsAutoClassifier.queryBankSms` builds the provider query as a body
allowlist:

```kotlin
append("(${bankKeywords.joinToString(" OR ") { "${Telephony.Sms.Inbox.BODY} LIKE '%$it%'" }})")
```

`SmsAutoClassifier.kt:314`

The list (`:57`) is: HDFC, ICICI, SBI, Axis, Kotak, Yes Bank, PNB, Canara,
BOB, UPI, credited, debited, A/c, account, transaction, spent, paid, INR,
Rs., withdrawal, deposit, balance. It has no `₹`, `Dr`, `Cr`, `sent`,
`received`, `transferred`, ISO currency codes, or Hindi/regional words.

A message from a registered bank that contains only “₹500 transferred to X”
is never returned by the provider query, so the 54-parser registry never sees
it. The `looksLikeBankSender` check runs **after** the query (`:340`), so it
cannot rescue a message the allowlist excluded.

PennyWise takes the other approach: it streams **all** inbox SMS in the date
window (`OptimizedSmsReaderWorker.kt:1247-1252`) and routes by sender, with
`-P`/`-G` promotional senders discarded and `-T`/`-S` senders stored as
unrecognized. That is a recall-first design; AuraSpend's is a precision-first
design that has no measurement of what it misses.

### 7.1 No foreign currency

`prefixedAmount` only recognises `₹`, `Rs`, and `INR`
(`BankMessageParser.kt:248`). A “USD 10 spent …” message produces no amount
(the bare-amount fallback requires 3+ digits and a decimal), so an
international card spend is dropped or sent to unrecognized. PennyWise parses
an ISO code and carries `currency` and `accountCurrency` in its
`ParsedTransaction`; AuraSpend's `ParsedBankMessage` has no currency field.

For an India-first app, this is not a reason to support 25 countries; it is a
reason to parse the one foreign-currency shape Indian card users actually
receive.

---

## 8. How PennyWise differs

Source-level comparison of `sarim2000/pennywiseai-tracker` at HEAD. Counts are
from the clone, not from the README:

| | AuraSpend | PennyWise |
|---|---|---|
| Bank parser classes | 54, India only | 161, 25 countries (India ~50) |
| Parser source | ~1.6k lines; HDFC 56, ICICI 44, SBI 67, Canara 81, Axis 123 | 29,358 lines in `parser-core/.../bank`; HDFC 734, ICICI 672, SBI 696, Canara 194, Axis 307 |
| Parser tests | 57 bank test methods + 82-case corpus | 150 test files, 260 `@Test` methods, per-bank fixtures |
| Routing | `firstOrNull { canHandle }`; parser returns a whole message | `getParsers(sender)` then “first parser whose `parse()` returns non-null” (content-aware) |
| Extension seam | `extract(rawMessage): ParsedBankMessage` — all or nothing | `parse()` template method calling overridable `extractAmount/Type/Merchant/Balance/Reference/AccountLast4/…` |
| Category | inside `BankMessageParser`; two keyword maps; exact-key memory | outside the parser; user rules + merchant mapping/alias |
| Type model | `INCOME`, `EXPENSE` | `INCOME`, `EXPENSE`, `CREDIT`, `TRANSFER`, `INVESTMENT` |
| Parsed fields | amount, type, merchant, bank, date, note, category, confidence, raw | amount, type, merchant, reference, accountLast4, balance, creditLimit, isFromCard, currency, accountCurrency, isMobileWallet |
| Ingest | body-keyword SQL allowlist | all inbox SMS by date, sender-routed |
| Currency | INR | multi-currency (25 countries) |
| Tests assert | mostly amount/type/merchant/bank | amount, currency, type, merchant, accountLast4, reference, balance, isFromCard |

### 8.1 What to copy

1. **Virtual field extraction.** PennyWise's base `parse()` is a template
   method; a bank overrides only the fields its format changes. AuraSpend
   should either return a delta/override object or merge the routed result over
   the global result, non-null field by non-null field.
2. **Content-aware dispatch.** Trying every parser that claims a sender and
   taking the first non-null parse removes order-dependent shadowing
   (HDFC Bank vs HDFC MF, Standard Chartered India vs Nigeria, etc.).
3. **A user rule/category layer.** Conditions on merchant, narration, SMS
   text, amount, type, date, account; actions set/clear/append, block, tag;
   apply to past transactions. This is how the long tail gets fixed without an
   app release.
4. **A richer domain model.** `TRANSFER` and `INVESTMENT` types, account last4,
   reference, balance, currency. These are the fields dedup, self-transfer
   pairing, credit-card reporting and multi-currency need.
5. **Sender-routed ingest.** Query the inbox and route by sender; do not make
   a 22-word body allowlist the gate.
6. **Per-bank fixtures that assert the fields that matter.** Amount, date,
   currency, reference, balance, account, not just “did it parse”.

### 8.2 What not to copy

- **The broad non-transaction blocklist.** PennyWise's
  `isNonTransactionMessage` returns true for any message containing “offer”,
  “discount” or “win ”, with no completed-debit rescue. A genuine
  “Rs 500 debited at X. Offer…” would be dropped. AuraSpend's
  “a completed debit outranks every informational phrase” rule is better;
  keep it.
- **One transaction per SMS.** PennyWise's `parse()` returns a single
  `ParsedTransaction`; it does not solve the multi-movement case either.
  AuraSpend should not copy that limitation.
- **25-country breadth at the cost of depth.** AuraSpend's India focus is
  correct. Depth on the top 20 Indian banks plus a rule layer is higher
  leverage than 100 more shallow parsers.
- **The assumption that per-bank depth is universally better.** PennyWise's
  Axis parser also misses the single-line “Spent … IST MERCHANT” shape
  because its merchant regex requires a newline. The lesson is the seam and
  the tests, not “copy every parser”.

AuraSpend's genuine strengths should be kept: the informational veto set
(points/marketing/NAV/expiry) with the completed-debit rescue, the optional
on-device encoder and its honest confidence semantics, the encrypted database,
and the no-telemetry stance. The encoder cannot fix these gaps, however:
`AiSignalFusion` gives a parser-resolved category precedence
(`AiSignalFusion.kt:88-95`), and the model is optional by design.

---

## 9. Prioritised fixes

### P0 — make the measurement honest, then stop the known losses

1. **Rewrite the eval before touching the parser.**
   - Add `amount` and `date` to `Case` and to the scored fields; compare
     amounts numerically and dates by day (or full timestamp where the message
     carries one).
   - Represent a correct rejection explicitly: when
     `case.isTransaction == false`, a skip is a **pass** for `isTransaction`
     and a pass for the other fields when they are null/`cat_other`.
   - Replace `falseVetoes()` with `results.count { it.case.isTransaction &&
     skipReason == ALERT_FILTER }` and allow only the documented
     `canara_debit_10000` case.
   - Stop using `isFraudLineVeto` as a blanket excuse; make it an explicit
     allowlist of accepted phishing trade-offs.
   - Compare merchant case-insensitively/whitespace-normalised.
   - Run the same `categoryResolver` production uses; split “parse” fields
     from “categorise” fields in the report.
   - Split the corpus into `parse`, `reject`, and `categorize` suites, and set
     a per-bank floor (HDFC/ICICI/SBI/Axis/Canara at minimum).
   - Raise the overall floor to the measured value and fail on any drop.
   Acceptance: the report distinguishes correct rejects from parse failures;
   amount/date are scored; a regression in any of the four §2 cases fails CI.

2. **Fix the HDFC/Kotak “If not you” false veto.**
   - Pass `sender` into `isOtpOrAlertMessage` and allow a bank-owned tail when
     the sender matches a registered bank and the message names a completed
     debit; keep the hard veto for unknown senders.
   - Or make the conditional pattern require a report/block instruction
     (“report”, “SMS BLOCK”, “call … fraud”), not merely “if not you”.
   - Add corpus cases for HDFC, Kotak, ICICI, PNB, Axis and Canara tails.
   Acceptance: `p2p_upi_debit_person_refno` is booked; the Canara phishing
   trade-off remains the only accepted loss.

3. **Stop the routed-parser field loss.**
   - In `TransactionClassifier`, compute the global parse first and merge the
     routed result over it (non-null wins), or fall back when the routed parse
     is missing merchant/date/category and the global parse has them.
   - Add tests that call `TransactionClassifier.classify(body, sender)` and
     assert the fields the bank parser does **not** own survive.
   Acceptance: single-line Axis regains `SUNRISE DINER` and its time; Canara
   NEFT regains `ACME SOLUTIONS LIMITED` and its date.

### P1 — cover the shapes users actually receive

4. **Own transfers and multi-movement messages.**
   - Add a `TRANSFER` type (or at minimum exclude `cat_transfer` from
     expense/income totals) and detect “own account”, “self”, “linked
     account”, “from <bank> to <bank>”.
   - Pair same-amount expense/income legs in a window, user-confirmed (the
     PennyWise model).
   - Split or surface multi-movement messages; do not return a typeless
     result for the whole body.
   Acceptance: a self-transfer does not change spending or income; a
   two-movement SMS produces two rows or one row plus an explicit
   unrecognized entry.

5. **Merchant extraction.**
   - Allow digits in merchant names unless the token is a reference/account
     (`looksLikeMerchant`'s `\d{3,}` blanket rejection is too strict).
   - Make stop phrases token-based, not `startsWith`.
   - Add the salary/refund/autopay shapes from §5.
   Acceptance: `SHOP 123` keeps its name; `Kotak Bank a` is rejected; the
   Netflix/Spotify/Amazon Prime/refund/salary cases have merchant names.

6. **Categorisation as a policy layer.**
   - Extract a `TransactionCategorizer` from `BankMessageParser`; parsers
     return extracted fields only.
   - One map, with separate income/expense tables (do not scan “rent” →
     Bills on an income).
   - Add a user-editable merchant/category rule layer; learn from detail
     edits, not only triage saves.
   - Make a memory hit fill category/type but still run subscription detection.
   Acceptance: “rent received” is not Bills; Netflix is consistently
   Subscriptions; a correction on the detail screen applies to the next
   message from that merchant; a remembered category cannot suppress a
   detected subscription.

7. **Ingest and currency.**
   - Query by registered sender IDs **or** body markers; add `₹`, `Dr`, `Cr`,
     `sent`, `received`, `transferred`, ISO currency codes, and Hindi/regional
     markers.
   - Parse ISO currency amounts and carry a currency field.
   Acceptance: a ₹-only message, a USD card spend, and a Hindi bank message
   are all ingested and parsed; a coverage test asserts the query returns a
   registered bank's message that contains no English keyword.

### P2 — structural

8. **Richer model and per-bank fixtures.** Add `accountLast4`, `reference`,
   `balance`, `currency`; grow the corpus with amount/date assertions for the
   top 20 banks. Consider a published per-bank accuracy table, not one
   aggregate.
9. **Consider `INVESTMENT` as a type.** The current workaround (`cat_investment`
   on an `EXPENSE`) is workable for the Investments screen but leaks into
   expense totals and analytics; the same is true for `cat_transfer`.

---

## 10. Reproduction

The working tree was not modified. The diagnostics were run in a throwaway
copy:

```bash
rsync -a --exclude='.git' --exclude='build' --exclude='.gradle' \
  /Users/Abhijith/RealWorldProjects/auraspend/ /tmp/aura-analysis/
# temporary CorpusDiagnosticTest and RoutedVsGlobalTest added under
# app/src/test/java/com/awbuilds/auraspend/data/classification/
cd /tmp/aura-analysis
./gradlew :app:testProdDebugUnitTest --tests '*CorpusDiagnosticTest*'
./gradlew :app:testProdDebugUnitTest --tests '*RoutedVsGlobalTest*'
```

The corpus diagnostic printed the full per-case diff; the routed/global
diagnostic printed the two rows quoted in §3. The PennyWise clone is at
`/tmp/pennywiseai-tracker`; `:parser-core:jvmTest` was attempted but its
Gradle 9.7.1 wrapper download timed out, so the PennyWise comparison is
**source-level only**, not a measured run of its tests. Its parser and test
counts above come from `find`/`grep` on the clone, not from the README.

## 11. What was not audited

- PennyWise's runtime correctness on the same corpus (not executed).
- Android/Compose UI behaviour and the on-device encoder's live accuracy.
- The release/Play/F-Droid state (see the latest handoff for that).
- Privacy/security beyond the classification path; the `SensitiveDataMasker`
  was read but not re-tested.
