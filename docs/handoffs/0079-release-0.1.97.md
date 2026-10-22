# 0079 — release 0.1.97

**Date:** 2026-10-22 · **Branch:** `main` + `dev` · **Status: tagged,
`versionCode` 19.**

## What shipped

A patch on 0.1.96, and a quiet one by the usual measure: **no schema, no
data-model, no corpus change.** The 82-case golden corpus is bit-identical —
80/82 exact (97.6 %), type 98.8 %, category 100 %, merchant 98.8 %, amount
98.8 %, date 17/17.

What it is *not* quiet is what it stops doing. A real export — about
thirteen months, just over a thousand rows, analysed in memory only — exposed
five families of **silent** wrong rows, and they were silent because the
corpus is bank shapes and these are all counterparty and statement shapes.
Handoff 0077 has the families and the counts; the short version:

- a bank's **balance booked as the transaction amount** (~30 rows) — one bank
  writes the movement bare and the balance rupee-marked, so the balance was the
  only rupee figure and won the pick;
- a **statement summary booked as one movement** (~8 rows) — a numbered leg
  list closes with a balance, and the balance became the amount;
- a **counterparty's receipt booked as the user's income** (~18 rows) — a
  utility, an insurer, a lender and a courier all write "your payment has been
  received", and every one of those carries a credit word;
- **both legs of an own-account move booked as spending** (~30 rows);
- a **"valid until" clause putting a row in the future**.

Two more families made the list unreadable rather than the totals wrong:
fragments reaching the merchant column, and real merchants filed as
**Transfer**, which removes them from every spending total.

**The release is forward-only.** Transactions already in a user's database
keep the amounts, dates and categories they were given. A re-ingest is the
only way to correct history, and that is the owner's call, not the app's.
The release notes say so, in both languages.

## The surfaces a release touches

| File | Change |
|---|---|
| `app/build.gradle.kts` | `versionCode` 18 → 19, `versionName` 0.1.96 → 0.1.97 |
| `CHANGELOG.md` | the `[0.1.97]` entry, under `## [Unreleased]` |
| `docs/store/whatsnew/whatsnew-en-US` | the Play notes for this build |
| `docs/store/whatsnew/whatsnew-hi-IN` | the same, in Hindi |
| `docs/fdroid/com.awbuilds.auraspend.yml` | the F-Droid build entry and `CurrentVersion` |
| `site/changelog.html` | the release card, and the release count 17 → 18 |
| `site/index.html` | `softwareVersion`, and two stale proof numbers — see below |
| `AGENTS.md` | the `Latest:` block |
| `docs/handoffs/0079-release-0.1.97.md` | this file |

## Two stale numbers on the site, corrected here

`site/index.html` claimed **53** bank parsers and **637** unit tests. Both
were wrong, and they had been wrong for some time: the registry holds
**54** entries and the tree now holds **709** `@Test` methods. Requirement 10
says a number the app stopped producing is as bad as a stale number in the
app, and this release was already editing that file.

They are now re-derived. If the owner would rather the site not publish the
test count at all, that is a separate call and a one-line revert.

## Handoff numbering

The promo-film record was written as `0077-promo-films.md` while another
session was writing `0077-real-export-families.md`. The promo record is now
**`0078-promo-films.md`**; 0077 stays with the classifier fix it belongs to,
and this release is 0079.

## Verification

- `:app:compileProdDebugKotlin` — green.
- `:app:testProdDebugUnitTest` — **709 tests, 0 failures, 0 errors** (1
  pre-existing skip), re-run from scratch with `--rerun-tasks` rather than
  read off a cached result. +31 over 0.1.96.
- `:app:lintProdDebug` — baseline-only (2 hints).
- The golden corpus is bit-identical: 80/82 exact (97.6 %), type 98.8 %,
  category 100 %, merchant 98.8 %, amount 98.8 %, date 17/17.

**The Maestro suite was not run.** The owner asked for it to be left out of
this release.

## What is not in this release

- **The two promo films.** They live in `promo/` on `dev`, untracked, and are
  not published anywhere — see handoff 0078. Nothing about the site embeds
  them yet.
- **A fix for the history.** Forward-only, as above.
- **The three families the owner confirmed are not bugs**: the courier
  payee, the small monthly mandate, and the gold scheme. The first two keep
  their behaviour; the last is now categorised.

## Open

- **The Play upload needs the `release` environment approved.** The tag
  starts the workflow; the run waits for a reviewer, and the AAB goes to the
  closed-testing track (`alpha`).
- **Category coverage is still incomplete**, and the reason is now clear: the
  remaining `cat_other` rows are mostly *merchant* problems, not map
  problems. A truncated payee cannot be looked up.
- **One observation, unexplained:** a telecom prepaid row resolved to
  Transport. The keyword map files telecom under Bills and the merchant
  database files it under Utilities, so neither path explains it. Left alone
  rather than guessed at.
- **The site's proof numbers are now correct**, but the site's other claims
  have not been re-audited since 0.1.9x.
