# 0047 — Misclassified SMS: informational families and the rail-to-Transfer mapping

**Date:** 2026-10-16
**Branch:** `fix/classification-informational-and-rails`
**Tests:** 584/584 (was 565) · lint clean · licence + privacy checks pass

## What was reported

A closed tester's CSV export (155 rows, 2022–2026) contained rows the owner
called wrongly classified. The aggregate analysis — no message text left the
device, and nothing from that export is in this repository — found three
distinct problems:

| Family | Rows | Why it was wrong |
|---|---|---|
| Reward/loyalty points (credited, earned, expiring) | ~26 | booked as income/expense; points are not rupees |
| Card/loan marketing ("pre-qualified", "special offer … cashback") | 4 | booked as income; an offer is not a payment |
| Fund NAV notices | 25 | **not** phantoms, as first assumed: identical amount, 31-day median gap — real monthly SIP debits whose merchant came out as "NAV of 24.53" and whose category was Other |
| Anything with a rail in it | 66 (43 %) | filed as **Transfer** because `upi`, `neft`, `imps`, `rtgs` and `transfer` were category cues |

## Root causes, all in the classifier

1. **No informational gate.** `isOtpOrAlertMessage` had OTP, fraud, conditional
   and alert families, but nothing for points, fund notices, offers or expiry —
   and every one of those carries an amount plus a word the movement lexicon
   knows ("points have been **credited**"), so the amount+verb rescue kept them.
2. **Rails as categories.** `keywordCategoryFor` mapped the five rail tokens to
   `cat_transfer`, so any UPI/NEFT payment whose merchant was not otherwise
   recognised lost its real category.
3. **Nothing mapped to Investments.** No keyword resolved to `cat_investment`, so
   SIP debits could not be categorised even in principle.
4. **"Refno" was not a reference suffix.** `merchantRefSuffix` wanted "Ref No"
   with a space; the compact form left a 12-digit run in the capture, which
   `looksLikeMerchant` rightly rejects as an account-number fragment — so the
   payee vanished, and with it the person-to-person decision.

## The fix, and the property that keeps it safe

- **Informational vetoes** (`pointsVetoPatterns`, `marketingVetoPatterns`), plus
  rescued additions for expiry, NAV and account-update notices. The load-bearing
  rule: a **completed debit outranks every informational phrase**
  (`BankMessageParser.hasDebitVerb`), because "Rs.1,499 spent at X. You have
  earned 15 reward points." is a purchase. The points patterns are precise enough
  that a cashback credit which merely mentions points is not caught.
- **Rails left the category map.** A rail plus a person-shaped counterparty is a
  person-to-person transfer, decided in `categoryIdFor` where the direction is
  known — received is income, sent is expense (the owner's rule, 2026-10-16).
  A VPA-derived single-token payee counts, because "…to rahim@ybl" is a
  counterparty.
- **Investments exist as a keyword target** (`mutual fund`, `sip`, `folio`,
  `nav`), and fund prices can no longer become merchants (`nav`, `units`,
  `folio`, `as on`, `sip` are stop phrases).
- **`Refno`/`RRN`/`UTR` are reference suffixes** now, so the payee survives.

## Measurements

- **Corpus 72 → 82** with the families above (synthetic cases only), and the
  floor re-measured: **26/82 exact (31.7 %), type 95.1 %, category 53.7 %, zero
  false vetoes**. Most of the jump from 12.5 % is a **metric fidelity fix**, not
  classification: a missing category is what gets *saved* as `cat_other`, and the
  evaluator was comparing the intermediate `null` against it — thirteen rows that
  were right all along were scored as misses. Both evaluators now share
  `ClassificationEval.savedCategory` so they cannot disagree again (the fused
  evaluator had drifted and failed its "must reproduce the baseline" assertion).
- **Replay against the export** (88 message-like notes; the `Note` column is
  truncated message text, which is faithful for the informational families and
  *not* for rows whose note is a bare payee name — stated, not hidden):
  45 refused as informational (expiry 13, marketing 4, nav 25, points 3),
  **0 refused despite naming a completed debit**, 39 parsed, 4 unreadable. The
  nav refusals are the truncation artifact: the real SIP messages name a debit,
  and the corpus case `sip_purchase_quoting_nav` pins that they stay transactions.

## The process guard this session added

`ClassificationReplayTest` — opt-in, path-driven (`AURASPEND_REPLAY_NOTES`),
prints aggregates only, never data. It is now the rule that every
misclassification report ends with (a) a synthetic fixture in the corpus and
(b) a replay against the export that produced it, with the
"refused despite a completed debit" counter as the property to check.

## Working-tree hygiene, for the record

This checkout was shared with another session while the work was done: it left an
abandoned rebase earlier (0046), and during this session it edited the same
handoff file. Nothing was lost by staging explicit paths and never using
`git add -A` in a shared tree; that is the habit worth keeping.

## Next

1. Merge this to `dev`, then a 0.1.6 release (0.1.5 is out: GitHub release +
   Play alpha, and a closed tester's phone has already upgraded and migrated in
   place).
2. The tester's existing rows: re-ingest with the fixed build is the clean repair
   (their messages are still on the phone); individual rows can be corrected in
   the app meanwhile.
3. Still open: the biometric-unlock check on the owner's phone, F-Droid's CI
   trigger on !52537, the okhttp 4 → 5 Dependabot PR as its own change.
