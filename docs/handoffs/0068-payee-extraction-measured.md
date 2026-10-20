# 0068 — the parser measured against a second implementation

**Date:** 2026-10-20 · **Branch:** `main` + `dev` · **Status: committed.**

## What was done

A second SMS parser (PennyWise, AGPL-3.0 — already credited in the licences
index) was built and run over the same message corpus this app parses, so the
two could be compared message by message. The corpus is **not in this
repository** and is not described here; the findings that survived are rules, and
the fixtures are synthetic.

Two harnesses were used, and they answer different questions:

- **`ExternalCorpusEvalTest`** runs this app's **regex layer** (the production
  `TransactionClassifier`) over a corpus. Deterministic, and it measures the
  floor — what the app does before the on-device model touches a message.
- **The app itself, on a device, with the model present**, classifying the same
  corpus and exporting its own CSV. That is the shipped behaviour, and it is
  what the *category* comparison needed.

## What the comparison found

**Amounts: no disagreement at all.** The two apps extract the same number from
every message both of them parse.

**Direction: this app is right where the other is wrong.** A card-spend
notification ("Spent INR N") was booked by the other app as money *coming in* —
inflating income and hiding spending. It was the single largest disagreement
between the two, and it is the other app's.

**Categories: this app is ahead.** Over the messages both apps transacted, this
app gave a specific category where the other app fell back to its generic
bucket, and where the other app *was* specific the two agreed. The two cases
that went the other way were merchants this app's keyword map did not know.

**Payees: this app was behind, and is now ahead.** The other app always
produces a merchant, but its answer is very often the *account* phrase
("your account XXXX…") or a literal "Unknown Merchant" — it looks complete and
is not. This app produced no payee at all in a quarter of the messages. Four
rules closed most of that gap, and the cases where the other app now has a real
name and this app does not are down to a handful.

## The rules that came out of it

All in `BankMessageParser` and the two keyword maps, all general rather than
bank-specific, all with synthetic fixtures:

1. A **semicolon ends a payee name**. The "to X" pattern terminated on a word
   or on `[/.,]` and not on `;`, so "… to SOMEONE; UPI: …" never matched at all.
   Now it does — on the "to", "to:", "towards" and "from" patterns.
2. **A rejected candidate must not hide a later one.** The pattern loop took
   only the first match, so a message with two "to" clauses lost the payee to
   the first one (the account), which was then rejected. Every match of every
   pattern is tried now.
3. **A counterparty clause is not a merchant.** "towards reversal/cashback from
   THE MERCHANT" extracted "reversal"; "to register your UPI PIN" extracted
   "register". They are stop phrases now, and the loop falls through when a
   candidate is rejected.
4. **A written date is not a merchant.** "revised from October 22" extracted
   "October 22".
5. The **`UPI-<ref>-<NAME>`** shape (the payee is the last segment of a UPI
   reference, the way the NEFT patterns read a remitter out of a NEFT reference),
   and `for` now ends the name after `from`.
6. **The travel portals and the chains this app left blank.** A hotel-booking
   payment was "Other" here and travel there; a retail chain payment was "Other"
   here and shopping there. Both are a merchant the keyword map did not know.

## The harness

`ExternalCorpusEvalTest` applies the **production** classifier to an external
corpus and writes one result line per message. It takes its paths from the
environment, asserts nothing, and no-ops when they are absent — so it is a tool,
not a gate. It is how the comparison was run, and it makes it re-runnable
against any corpus.

## Verification

- `:app:testProdDebugUnitTest` — **623 tests, 0 failures, 1 skipped** (the
  pre-existing skip), up from 614: six payee fixtures, two category fixtures and
  the harness.
- The golden corpus is unchanged and `RegexBaselineEval` still holds its
  measured floor — the new rules moved the parse of a live corpus the right way
  without regressing the scored one.

## Not done

- The app's **own-account transfer** handling and the other app's
  split-message handling (one SMS becoming several transactions) were not
  judged: they are design differences, not correctness ones.
- The comparison could not be run over the **whole** corpus end-to-end on a
  device: the messages were injected with one timestamp, and the app's ingest
  walks the provider by date, so it read one page and stopped. The category
  comparison therefore rests on the messages that were ingested, joined exactly
  by their source SMS id.
