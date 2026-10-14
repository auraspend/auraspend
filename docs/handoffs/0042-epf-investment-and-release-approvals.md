# 0042 — EPF is an investment; releases stop minting PR approvals

**Status:** done. Two owner calls (2026-10-14). PR #61 (merge `55d31dc`):
release runs only on tags. PR #62 (merge `035052e`): EPF contributions book as
Investments. **540/540 tests** (was 531), lint clean, both CI builds green.

## EPF contributions are investments (PR #62)

- The owner's answer to the open question (keep, reclassify, or dismiss) —
  **reclassify: an EPF contribution is an investment, not income.**
- What was wrong: sender `EPFOHO`'s confirmation ("Dear XXXXXXXX41 your
  passbook balance against PYKRP… is Rs. B/-. Contribution of Rs. C/- for due
  month Jun-26 has been received.") booked as **income** under **Other**. The
  audit trail shows the saved *amount* was already the contribution — the
  engine's amount heuristic landed on it — so the defect was the type and the
  missing category.
- What ships: `EpfoParser` (sender-routed on `EPFOHO`) pins the contribution
  amount (the balance can never win), types it **EXPENSE**, files it under the
  new default category `cat_investment` ("Investments"), merchant "EPF"; the
  due month stays out of the date (the arrival timestamp books it — the 0036
  rule). OTP and UAN-linking notices from the same sender are untouched,
  test-pinned — re-typing an OTP would resurrect exactly the class the ingest
  filter drops.
- **Existing installs receive the category** because startup seeding now
  reconciles missing defaults instead of only filling an empty table
  (`missingDefaultCategories`); a parser filing into an id no row renders
  would have shown a blank category.
- Type judgement call: EXPENSE (money allocated out of the spendable pool).
  One line to flip if the owner wants the income side instead.

## Releases ask for approval only when they are releases (PR #61)

- What was wrong: `release.yml` ALSO ran on every PR targeting main, and every
  run waits for the `release` environment's reviewer — so each Dependabot PR
  and the dev→main PR minted a pending approval request (seven were sitting
  un-actioned).
- What ships: the `pull_request` trigger is gone (tags + dispatch remain,
  still environment-gated). The seven stale pending runs were cancelled.
- Resulting approval surfaces: **(1)** PRs into `main` (branch protection —
  unchanged; `dev` was never protected), **(2)** actual release runs
  (environment reviewer — unchanged). Nothing else mints an approval request.

## Pending on the owner's phone

- Re-type the saved EPF rows (editor: **Expense** + **Investments**; the
  amounts are already right).
- The two UI spot checks the 0041 pass could not finish (editor recurring
  switch, subscription suggestion dismiss) — both test-covered.

## Next

- 0.1.4 cut on the owner's word — it now also carries EPF + the workflow
  change on top of the 0041 list.
