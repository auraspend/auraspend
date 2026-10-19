# 0068 — payee extraction, measured against a second parser

**Date:** 2026-10-20 · **Branch:** `main` + `dev` · **Status: committed.**

## What was done

A second SMS parser (PennyWise, AGPL-3.0 — already credited in the licences
index) was built and run over the same message corpus this app parses, so the
two could be compared message by message. The corpus is **not in this
repository** and is not described here; the findings that survived are rules,
and the fixtures are synthetic.

## What the comparison found

- **Amounts: no disagreement at all.** The two apps extract the same number from
  every message both of them parse.
- **Direction: this app is right where the other is wrong.** A card-spend
  notification ("Spent INR N") was booked by the other app as money *coming in*.
  That inflates income and hides spending, and it was the single largest
  disagreement between the two.
- **Payees: this app was behind, and is now ahead.** The other app always
  produces a merchant, but its answer is very often the *account* phrase
  ("your account XXXX…") or a literal "Unknown Merchant" — it looks complete
  and is not. This app produced no payee at all in a quarter of the messages.
  Four rules closed most of that gap (see the commit), and the cases where the
  other app now has a real name and this app does not are down to a handful.
- **Categories cannot be compared this way.** The other app resolves by static
  rules and lands on its generic bucket for the large majority of rows; this
  app's categories come from the on-device model, which the comparison harness
  deliberately bypasses (it runs the regex layer alone, so the number would
  flatter the other app). Settling it needs a run with the model present.

## The rules that came out of it

All four are in `BankMessageParser`, all four are general rather than
bank-specific, and all four have synthetic fixtures in `PayeeExtractionTest`:

1. A **semicolon ends a payee name**. The "to X" pattern terminated on a word
   or on `[/.,]` and not on `;`, so "… to SOMEONE; UPI: …" never matched at
   all. Now it does — on the "to", "to:", "towards" and "from" patterns.
2. **A rejected candidate must not hide a later one.** The pattern loop took
   only the first match, so a message with two "to" clauses lost the payee to
   the first one (the account), which was then rejected. Every match of every
   pattern is tried now.
3. **A counterparty clause is not a merchant.** "towards reversal/cashback from
   THE MERCHANT" extracted "reversal"; "to register your UPI PIN" extracted
   "register". Both hid the name the message carried. They are stop phrases
   now, and the loop falls through when a candidate is rejected.
4. **A written date is not a merchant.** "revised from October 22" extracted
   "October 22".

Also added: the **`UPI-<ref>-<NAME>`** shape (the payee is the last segment of a
UPI reference, the way the NEFT patterns read a remitter out of a NEFT
reference), and `for` now ends the name after `from`, so "from THE COMPANY for
trxn." is the company.

## The harness

`ExternalCorpusEvalTest` applies the **production** classifier to an external
corpus and writes one result line per message. It takes its paths from the
environment, asserts nothing, and no-ops when they are absent — so it is a tool,
not a gate. It is how the comparison was run, and it makes it re-runnable
against any corpus.

## Verification

- `:app:testProdDebugUnitTest` — **621 tests, 0 failures, 1 skipped** (the
  pre-existing skip), up from 614: six new payee fixtures and the harness.
- The golden corpus is unchanged and `RegexBaselineEval` still holds its
  measured floor — the new rules moved the parse of a live corpus the right way
  without regressing the scored one.

## Not done

- **The category comparison needs the on-device model** and has not been run.
- The other app's split-message handling (one SMS becoming several
  transactions) is a capability this app does not have; it was not judged,
  because it is a design difference rather than a correctness one.
