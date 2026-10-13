# 0035 — targeted device pass + wave 5

**Status:** done 2026-10-13. Branch `feat/per-bank-parsers-5`. **484/484 tests**,
lint clean, licence check green.

## Targeted device pass — Option A on the real inbox

The phone's corrected DB still held **14 credit-card bill payments recorded as
phantom INCOME** (Axis ×6, ICICI BBPS ×5, SBI BBPS ×2, Kotak ×1): the card-side
"Payment of Rs X has been received …" messages that pre-dated the Option A
decision. All 22 credit-card-related rows (the 14 plus 3 OTP notices and 5 SBI
e-statements) were re-staged under the wave-3+4 build.

**Result: all 22 → SKIPPED, zero transactions.** The device is now **467 saved
/ 233 skipped / 467 transactions**; nothing mis-saved, no new phantoms, the
refund-free set stayed refund-free.

Operational note for the next pass: the first staging attempt issued the WAL
checkpoint inside the open transaction, which failed and (correctly) rolled the
staging back — the "malformed" read that followed was a torn pull taken while
the relaunched app was writing. Redone as commit → close → checkpoint →
push-with-empty-WAL; the staged count was verified on-device before the drain.
No data was lost at any point.

## Wave 5 — the last institutions (registry now 53)

- **HDFC Mutual Fund** — SIP purchases name the fund in-clause ("…under HDFC
  Mid Cap Fund-DG for Rs. 7,000.65…"); generic patterns cannot reach the
  clause, and the fund is the merchant of record.
- **Navi Mutual Fund** — SIP allotments name the fund before "has been
  processed"; that clause must take precedence over the "at applicable NAV"
  tail the generic `at` pattern misreads. Keyed on NAVAMC so the NAVI
  banking-arm senders stay out.
- **Kerala Gramin Bank, Kerala Bank, DBS Bank, India Post Payments Bank, Jana
  Small Finance Bank** — sender→bank attribution.
- Global bank-name table: both fund brands (ordered **before** HDFC Bank so
  "HDFC Mutual Fund" never resolves to the bank) plus the five names.
- Kerala Gramin Bank's dual-account "debited … and credited to a/c …" UPI
  shape deliberately keeps the project's skip-not-guess behaviour; its
  phone-number VPA credits keep the number as the counterparty.

Also adjusted: `TopBankParsersTest`'s mutual-fund assertion — it used to assert
that MF senders resolve to *no* parser; now the dedicated fund parser claims
them (the bank parser still declines MF senders, unchanged).

## State

- 53 parsers: national banks, private banks, cards, wallets, payments banks,
  both fund senders. The reference project's remaining parsers are
  non-Indian or niche (Thailand/Nigeria/Ethiopia/Gulf/US), out of scope for
  this app's market.
- No release cut; `dev` carries waves 1–5 plus the engine work.
- Optional later: fold new classes into the golden corpus at the next
  re-baseline; wave 6 only if a device pass finds new classes.
