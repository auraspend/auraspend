# 0075 — the Activity route, and marketing that is not money

**Date:** 2026-10-21 · **Branch:** `main` + `dev` · **Status: tagged, run
39632544834 green, uploaded to Play (closed testing, `alpha`). `versionCode`
17.**

## What this is

Two reports from the owner, both real, with unrelated causes:

1. Home's "see all" opened **Activity with `{query}` written into the search
   box**, and every row filtered out.
2. **An income row that was an advertisement**, not a payment.

The second turned into a family fix: the owner asked for the whole measured
table of promotional shapes, not just the one row.

## 1. The Activity route

`Routes.ACTIVITY` was the **destination pattern**:

```
activity?categoryId={categoryId}&query={query}
```

and Home's "see all" passed it straight to `navigate()`. Navigation matched it
as written, so the arguments arrived as the literal strings `"{categoryId}"` and
`"{query}"` — the ViewModel was built with `initialQuery = "{query}"` and the
field opened pre-filled, filtering everything out.

**The same mismatch had a second symptom nobody had reported:** the bottom bar
compares `NavDestination.route` (always the *pattern*) with
`TopLevelDestination.ACTIVITY.route` (the bare `"activity"`), so the Activity tab
could never draw itself as selected.

**Fix — make the template un-navigable, not just correct the call:**

- `TopLevelDestination.ACTIVITY` now carries **two** strings: `route` (the
  registered pattern, used for selection and chrome) and `navigateRoute` (the
  bare `"activity"`). The defaults on both arguments mean the bare route means
  "no filter".
- `Routes.ACTIVITY` / `Routes.ACTIVITY_PATTERN` are **derived from the enum**, so
  there is one source of truth and no package cycle (`navigation` already
  imports `core`).
- `RoutesTest` (4 cases) pins the contract: the navigable route carries no
  `{…}` and no `?`, the pattern is the route plus its arguments, `topLevel`
  holds the *pattern*, and `activity()` fills both arguments.

The test is a naming guard, not a navigation replay — composing the graph to
tap one button would cost more than it catches. It makes the bug's shape
impossible to write again.

## 2. The advertisement

The first diagnosis was wrong, and the evidence that corrected it was the
message itself:

- It quotes a price and a call to action and has **no movement verb**. The
  parser's `detectType` scores debit/credit words; finding none, it returns
  **no type**. So the regex could not have produced `INCOME`.
- The ingest gate (`isOtpOrAlertMessage`) found no alert word either, so the
  message **passed**, and the pipeline reached `SmsAiEnricher` — which lets the
  **on-device classifier fill a missing type**, and the **learned classification
  memory** supply one it has seen before. Both run *after* the gate.

That is the trap handoff 0032 recorded once already: a wrongly-saved class can be
resurrected by the memory. It also explains why four rows of the same
advertisement across five years all carry the same type.

**So the fix belongs at the ingest gate**, upstream of the memory and the model
— a veto there kills every path at once, and it is testable without a model.
That is the whole design decision of this change.

### The families added

Eight, each guarded twice: the existing `hasDebitVerb` stand-down (money
actually leaving is a purchase however it is dressed), and — where it applies —
a new **"no completed-credit verb"** guard, because the difference between an
offer and a credit is the verb, not the noun.

| Family | Rule | Measured rows |
|---|---|---|
| Ad copy with a price | a link or call to action **and no movement verb at all** | the reported row |
| Cashback invitations | `get/earn/win/claim/flat/upto … cashback\|off`, no completed credit | 9 |
| Coupons / vouchers | `coupon\|voucher` + `credited\|valid` | 3 |
| Prize / future credit | `can/will/may be credited`, `welcome bonus`, `prize pool` | 3 |
| Card-spend marketing | `shop/swipe/spend/tap … reward points\|earn\|offer` | 4 |
| Discounts | `flat Rs X off`, `get upto Rs X` | 3 |
| Recharge confirmations | `pack\|data\|gb credited` — a benefit, not money | 2 |
| Wallet + link | `wallet … click\|http` — a lead, not a statement | 3 |

### Two defects the rules exposed

- **`[^.!]` windows could not cross an amount.** `Rs.1,499.00` contains dots, so
  a "get … cashback" window stopped dead at the price. The windows now stop at
  `!` and newline only.
- **"SBI Debit Card" is not a debit.** `debit` is in the parser's debit lexicon,
  so every card-marketing SMS tripped the stand-down and read as a purchase —
  the export shows an instruction to spend booked as an expense. The guard
  is now evaluated with the **card instrument** masked out.

## Measurement

**The golden corpus did not move.** It scored exactly the same before and after:

```
cases : 82
exact match : 80/82 (97.6%)
  type     81/82  98.8%
  category 82/82 100.0%
  merchant 81/82  98.8%
  amount   81/82  98.8%
  date     17/17 100.0%
```

That is the whole point: the 82 cases contain no marketing of these shapes, so a
rule that changed the score would be a rule that changed a real parse.

**The owner's export, read in memory only** (nothing written, nothing
committed, no fixture — see AGENTS.md #11). Applying the new decision procedure
to the **promotional rows** and to a set of **real-transaction controls**:

- **24 of 31** promotional rows would now be skipped.
- **8 of 8** real controls would still be saved — an ATM withdrawal, a UPI debit,
  a SIP purchase, a reversal credit, a NEFT credit, a cash-deposit credit, and
  two cases that exist precisely to protect the other direction: a *cashback that
  has been credited* and a *recharge debit*.
- The **7 kept** are accounted for and deliberately left:
  - 3 contain `recharge of` or `purchase` — **debit words**. Those are the safest
    rows to leave alone: the same vocabulary is what saves a real purchase.
  - 3 are truncated in the export's note column (one ends mid-URL), so the check
    could not see the signature a full body would carry.
  - 1 is a wallet-credit shape that also reads as a legitimate credit.

**Honest limits.** The export check used the note column (truncated bodies) and a
regex-for-regex Python mirror, not the Kotlin runtime; it is evidence for
*scope*, not a unit test. The advertisement test is synthetic by rule. And the
model path itself is still unexercised here — but the veto is **upstream** of it,
which is exactly why it does not need to be.

## Verification

- `:app:testProdDebugUnitTest` — **674 tests, 0 failures** (1 pre-existing skip).
  +16 over 0.1.94: 4 for `RoutesTest`, 12 for the promotional families.
- `:app:lintProdDebug` — baseline-only (2 hints).
- `:app:compileFdroidDebugKotlin` — green.
- `:app:classificationBaseline` — the table above, unchanged.
- `tools/check_license.sh`, `tools/check_privacy_claims.sh`,
  `tools/check_site_version.sh` — clean.

## AGENTS.md gained a hard requirement

**#11 — never record personal information in this repository.** Public and
permanent: no real name, number, account/folio, sender, or message text, and no
screenshot of a real inbox, in docs, handoffs, tests, fixtures, comments, commit
messages or the site. Describe the shape and invent an example. External corpora
stay external, read through an environment variable and deleted afterwards.

This handoff follows its own rule: no message text, sender, amount or link from
the owner's inbox appears above — only shapes, families and counts.

## The run

**39632544834**, every step green — licence and site-version gates, the signed
bundle, **Upload to Google Play**, and "Publish the release if it is still a
draft". The GitHub release is published with the AAB, the APK and
`SHA256SUMS`; the local pre-flight build reported `versionCode=17
versionName=0.1.95` signed by `CN=AuraSpend`.

The site deployed with it: the changelog reads 0.1.95 at the top and its own
summary line follows, because `check_site_version.sh` now checks that line as
well as `softwareVersion`.

## Open

- **The already-saved row will not correct itself.** A classifier change
  only affects new ingests; once a message is `SAVED` it is terminal. Delete the
  row by hand — deleting a transaction does not re-queue its SMS, so it stays
  gone.
- **The seven kept rows** above are a deliberate residue, not an oversight.
  Widening any of them means either trusting a debit word less or reading
  truncated bodies, and both are worse than the residue.
- **A date anomaly, unverified.** The reported row carried the date it was
  saved rather than the message's own date, years apart. An undated body is
  supposed to fall back to the message's timestamp (0036), not the save clock.
  Not investigated; worth a separate trace.

## Next step

Install 0.1.95, open Home → "see all": the search box must be empty and the
Activity tab must be the selected one. Then let a marketing SMS arrive and check
it lands in Skipped, not in Income.
