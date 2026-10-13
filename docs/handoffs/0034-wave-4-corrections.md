# 0034 — wave 4: corrections the wave-3 cohort's fixtures demanded

**Status:** done 2026-10-13. Branch `feat/per-bank-parsers-4`. **478/478
tests**, lint clean, licence check green, privacy tripwire green (every
fixture below is a synthetic twin).

## Ported (six corrections across five parsers + one lexicon gap)

| Where | Fix |
|---|---|
| Global lexicon | **"used at" is a debit phrase** — "Your HSBC creditcard xxxxx1234 used at AMAZON for INR 305.00" had no readable direction before. |
| Indian Bank | ATM location labels: "withdrawn from ATM at MAIN STREET BRANCH" → "ATM - MAIN STREET BRANCH". |
| Saraswat | "towards ACH Credit:MERCHANT NAME" (colon clause) and the "for S.I. / NEFT / RTGS / IMPS" rail labels ("Standing Instruction", "NEFT Transfer", …). |
| Standard Chartered | "there is an NEFT/RTGS/IMPS credit of INR …" → rail labels. |
| Dhanlaxmi | The quoted UPI block's payee: `"UPI TXN: /ref-MR /Payment from PhonePe/Q…@ybl/…"` — quote- and slash-aware, and it wins over the app VPA that follows it. |
| Amazon Pay (Juspay) | Direction for "Payment of Rs X using Apay Balance successful at …" (no debit word; "Payment of" reads like a *receipt* elsewhere) + the "Amazon Pay Transaction" label for wallet debits that name no merchant. |

## Considered and deliberately NOT ported

- **HSBC's "from CHAS A/c ***6983 of John Doe" merchant** — the reference
  inserts the masked account *into* the merchant string; our merchant hygiene
  rejects names carrying 3+ digit runs by design. Amount and type parse fine;
  the merchant stays empty rather than listing an account.
- **PKR/IBFT/RAAST and USD card messages** (Standard Chartered PK) —
  foreign-currency amounts stay unread until the app has per-transaction
  currency; mislabelling them in rupees would be worse than skipping.
- **Dual-account self-transfer shapes** (Dhanlaxmi "credited … and debited
  from …", SC "debited … and credited to a/c X") — the project's
  skip-not-guess behaviour stands: the direction is genuinely ambiguous and a
  self-transfer must not book as spending.
- **Equitas and Pluxee** needed no corrections at all — the engine reads their
  fixtures; both are pinned as regression tests.

## Not done (on purpose)

- No corpus additions this wave: the floors were just re-baselined in 0033 and
  another shift for six labels is churn; these fixes are pinned by unit tests.
  The next re-baseline can fold them in with whatever wave 5 adds.
- No device pass; the phone's dev DB remains corrected from the last one.

## Staged

- Optional device pass (targeted re-stage) whenever wanted.
- Wave 5 = whatever a device pass or new fixtures surface; the bank registry
  (46 parsers) now covers the national banks, the card cohort and the wallets.
