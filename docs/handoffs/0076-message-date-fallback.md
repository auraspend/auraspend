# 0076 — a date from the message, not from the scanner

**Date:** 2026-10-21 · **Branch:** `main` + `dev` · **Status: unreleased**,
after 0.1.95.

## The report

A transaction carried the day it was scanned rather than the day its message
arrived — years apart. The message names no date of its own; it is advertising
copy that happens to say "…match **Today**!".

## Why

`BankMessageParser.detectDateTime` ended with a relative-day branch:

```kotlin
val lower = message.lowercase(Locale.ENGLISH)
val now = LocalDateTime.now()
return when {
    "yesterday" in lower -> now.minusDays(1)
    "today" in lower -> now
    else -> null
}
```

The word "today" **anywhere in the body** was treated as a date and answered
with the wall clock. That made `parsed.date` non-null, so the honest fallback in
`SmsAutoClassifier.toTransaction` — the message's arrival timestamp, added in
0036 — never ran. The transaction was dated with the drain.

It was never one row. Any message containing "today" was exposed: "offer ends
today", "recharge today", "statement for today". And the branch made parsing
depend on the wall clock, so the same text could parse to a different date on a
different day — which is why `RegexBaselineEval` carried an exception for it.

## The fix

The branch is gone. A body with no stated date returns `null`, and
`toTransaction` fills in the **arrival timestamp** — the one clock this parser
cannot see, and the honest one for a message that names no date.

**Why not anchor "today" to the arrival instead.** The parser has no timestamp
at parse time (`TransactionClassifier.classify(body, sender)`), so anchoring
means threading the arrival through the parser API and every bank parser. And it
would change nothing: for a genuine message "today" *is* the arrival day, so the
anchor and the fallback agree. Only "yesterday" differs.

**Cost, stated plainly:** a body saying "yesterday" now gets the arrival day
rather than arrival − 1. One day, rare, against an error measured in years. If
it is ever wanted back, `toTransaction` is the place — it holds the anchor.

## Verification

- `MessageDateFallbackTest` (5): the word "today" is not a date; a stated date is
  still read; an undated advertisement is dated by **arrival**, not by the scan;
  a stated date still wins over arrival; an empty arrival still falls back to the
  save clock (the last resort stays).
- `:app:testProdDebugUnitTest` — **679 tests, 0 failures** (1 pre-existing skip).
- `:app:lintProdDebug` — baseline-only (2 hints).
- Golden corpus **unchanged**: 80/82 (97.6 %), type 98.8 %, category 100 %,
  merchant 98.8 %, amount 98.8 %, date 17/17. The one corpus case containing
  "today" expects no date, so the floor could not move.

## Open

- **The already-saved row still carries the wrong date.** The change is
  forward-only; that one transaction needs its date edited in the editor.
- The fix is **unreleased** — it ships with the next version, not 0.1.95.
