# 0077 — five silent families in a real export

**Date:** 2026-10-22 · **Branch:** `dev` · **Status: unreleased**, after 0.1.96.

## How this was read

A user's export — about thirteen months, just over a thousand rows — was
analysed **in memory only**. Nothing was written to disk, no script was fed the
rows, and no row, amount, name, reference or date appears in this record or in
any commit. What follows is families and counts, which is what AGENTS.md #11
now requires of a report like this.

The families below were invisible in the corpus because the corpus is bank
shapes; they are all **counterparty and statement shapes**.

## The five that moved the totals

**1. The balance was booked as the amount (~30 rows).** One bank writes the
movement bare and the balance rupee-marked — "4500 credited … BAL-Rs.6221.41".
The balance was the only rupee figure in the body, so the picker chose it. Most
of those rows *also* carried the correct deposit from a second message, so the
same money was counted twice, once right and once as the whole account balance.
`balanceClause` is now masked before amounts are collected, and a bare figure
next to its verb is read when nothing rupee-marked is left.

**2. A statement summary was booked as one figure (~8 rows, the largest
phantoms in the file).** "Tran for A/C … 1) … Cr 2) … Dr 3) …" lists several
legs and closes with a balance; the picker took the closing figure. A numbered
leg list is now a hard veto — a single movement never numbers its legs.

**3. The counterparty's receipt was booked as the user's income (~18 rows).**
A utility, an insurer, a lender and a courier all write "your payment has been
received" / "we are happy to inform you" / "payment update". Each carries a
credit word, so it became income — while the bank had already recorded the same
payment as an expense. Five measured signatures now veto it, and the
counter-case is pinned: a payment confirmation that is the user's **only**
record of a real expense still books.

**4. Both legs of an own-account move were booked as spending (~30 rows).**
One SMS can describe a debit from one of the user's accounts and the credit
into another ("… debited for Rs X and ‹account› credited"). It is now a
Transfer, which the totals exclude — and the credited thing must be an
**account number**, never a second amount, so a genuine debit-plus-refund is
untouched.

**5. A validity date put one row in the future.** A date read from a "valid
until" clause became the transaction day. `toTransaction` now clamps: a stated
date more than a day after the message's arrival falls back to the arrival.

## The two that made the list unreadable

**6. Fragments in the merchant column (~60 rows).** A clause cut mid-account
("debit of A"), a bare honorific, a bare number, a button label. Some came from
the extractor, some from the on-device classifier — the fusion accepted whatever
the runtime returned. Validation now happens in one place
(`BankMessageParser.sanitizeMerchant`) and **both** sources go through it. An
honorific attached to a name ("Mr K …") still survives; only the bare form is a
fragment.

**7. Categories that hid money.** About three quarters of the rows were
`cat_other`, and a set of real merchants were filed as **Transfer** — which
removes them from every spending total. Twelve families were added, word-bounded
and longest-key-wins: hospitals and pharmacies whose names are abbreviated,
fuel stations in the plural, a supermarket that resolved two ways depending on
spelling, a gold savings scheme (Investment), restaurants, bakeries, sweets and
a supermarket chain. The duplicated "A2Z"/"A TO Z" pair now agrees.

## What the owner confirmed, and was left alone

Three families looked like bugs and are not: **the courier/franchise payee**
(~120 rows — a real payee), **the small monthly mandate** (a real subscription),
and **the gold scheme** (a savings scheme, not a health expense). The last one
is categorised; the first two keep their behaviour.

## Verification

- `:app:testProdDebugUnitTest` — **709 tests, 0 failures** (1 pre-existing
  skip). +31 over 0.1.96: 9 amount/date, 9 receipt/transfer, 12 merchant and
  category, plus one corrected fixture.
- **The golden corpus is bit-identical**: 80/82 exact (97.6 %), type 98.8 %,
  category 100 %, merchant 98.8 %, amount 98.8 %, date 17/17. That is the bar
  for this work — every family here sits outside the corpus, so a change to the
  score would have meant a change to a real parse.
- `:app:lintProdDebug` — baseline-only (2 hints).
- `:app:compileFdroidDebugKotlin` — green.

## Open

- **Category coverage is still incomplete**, and the reason is now clear: the
  remaining `cat_other` rows are mostly *merchant* problems, not map problems.
  A truncated payee ("Kerala State Ro") cannot be looked up, and a body used as
  the merchant cannot either.
- **One observation, unexplained:** a telecom prepaid row resolved to Transport.
  The keyword map files telecom under Bills and the merchant database files it
  under Utilities, so neither path explains it. Left alone rather than guessed
  at — it needs a trace with the real message.
- **This fix is forward-only.** Rows already in the user's database keep their
  wrong amounts and dates. A re-ingest is the only way to correct history, and
  that is the owner's call, not this change's.
