# 0030 — parsing coverage from real-world bank formats

**Status:** done 2026-10-13. Branch `feat/bank-parser-coverage` (off `dev` @
`9195965`), **deliberately not merged** — `dev` is currently the head of the
0.1.3 release PR (#47), and this work should not silently ride into that
release. **413/413 tests**, `:app:lintProdDebug` clean, `tools/check_license.sh`
green.

## What changed

The SMS engine absorbed the hard-won real-world handling of the PennyWise AI
parser knowledge base (AGPL-3.0 — attribution in `docs/licenses/README.md`,
`README.md` and the in-app licences screen; adapted 2026-10-13):

**Amounts**
- No-leading-digit amounts are read ("Rs..6 Credited", "₹ .50") — banks print
  them, and skipping them let a later balance figure win the amount pick.
- Credit/available-limit clauses ("Avl Limit: INR …", "Available Credit
  Limit: Rs…", "Avl Lmt INR …") are blanked (same length, so every collected
  range stays valid) before candidates are gathered — a statement whose only
  figure is the limit no longer books it as a spend.
- "deducted" joins the debit lexicon; "Updated / Remaining Balance" are
  covered by the boilerplate window.

**Merchants**
- UPI handles: payment-app QR ids read as the app ("paytmqr…@paytm" → Paytm,
  phonepeqr → PhonePe, amazonpayqr, bhimqr, bharatpeqr, freechargeqr,
  mobikwikqr); phone-number handles return the number (the person is the
  identity, not the app); long machine-generated handles fall back to a
  bank-code map (ybl → PhonePe, oksbi → SBI, okaxis → Axis, okhdfcbank →
  HDFC, okicici → ICICI, paytm, amazonpay, airtel, razorpay, bharatpe,
  jupiteraxis…).
- Card spends: "spent using <card> on <date> on <merchant>" — the merchant
  after the second "on" is captured; scoped to spend phrasing, and a
  date-shaped value can never become the merchant.
- Refunds: "INR 2 from Airport Lounge refunded to your Kotak Credit Card …" —
  the party between "from" and "refunded".
- UPI debits: "; DINDUGAL ORIGIN credited" — the counterparty before
  "credited" is the payee the user recognises.
- Salary credits: "Info INF*…*IQBO SAL FE" reads as "Salary".
- Trailing noise is stripped from merchants: "Ref No …", "on <date> …",
  "UPI …", "at hh:mm …", "(…)", "PVT LTD / PRIVATE LIMITED", "UPI-<ref>-"
  prefixes. Bare "LTD"/"LIMITED" is deliberately kept — it is part of real
  names (and of a pinned corpus expectation).

**Types**
- "…has been credited to the beneficiary account" (an outgoing NEFT) is
  EXPENSE, not income — the literal "credited" had it backwards.
- "; <PAYEE> credited" no longer makes a debit ambiguous (the counterparty
  clause is neutralised before scoring; dual-account "& Acct … credited"
  messages keep their skip-for-safety behaviour).

**Ingest guards** (`SmsAutoClassifier`; each fires only on messages that prove
no completed movement — a debit verb anywhere keeps the message):
- "…will be debited …" intimations (the completed debit arrives as its own
  SMS), "has/have requested …", "payment/collect request", "requests Rs …",
  convert-to-EMI invitations (a completed conversion still parses),
  "ignore if already paid", "overdue"/"in arrears", "pls/please pay"
  reminders.

## Verification

- `./gradlew :app:testProdDebugUnitTest` — **413/413** (was 396; +17 tests:
  8 parser cases, 6 ingest-guard cases, 3 amount/cleaning cases).
- `:app:lintProdDebug` clean; `tools/check_license.sh` passes.
- Formats are pinned as synthetic twins (altered numbers/names) drawn from the
  reference project's per-bank test matrix; no third-party fixture text is
  copied verbatim.

## Deliberately staged (next waves)

- **Per-bank parser wave.** The reference project routes ~171 banks; we route
  Axis / Canara / SBI. Their per-bank files (HDFC, ICICI, Kotak, Federal, PNB,
  IDFC First, BoB, IndusInd, South Indian, Union, Central, JK, City Union…)
  encode further structural formats and exact DLT sender sets — port them as
  our-style parsers, bank by bank, each with tests. The reference clone lives
  in the session temp dir, outside the repo.
- **Golden-corpus additions + re-baseline** for these shapes (also deferred
  from 0029): add synthetic corpus entries, re-run
  `:app:classificationBaseline` and `FusedAiEval`, update the quoted numbers.
- **Foreign-currency amounts** ("USD 11.80 spent") are still not read: the app
  stores one number with no per-transaction currency, so recording "11.80"
  under ₹ would be wrong. That is a product decision (per-transaction
  currency), not a parser bug.

## Open risks

- The new guards were verified against the corpus and the test suite, but not
  yet against a real inbox; the next device pass (or the corpus re-baseline)
  should re-confirm the drop/keep split on the owner's messages.
- The adapted patterns live in two files; if the reference project's licences
  ever change, `docs/licenses/README.md` is the pointer to revisit.
