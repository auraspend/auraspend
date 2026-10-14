# 0031 — sender-routed parsers, wave 1: HDFC, ICICI, Kotak

**Status:** done 2026-10-13. Branch `feat/per-bank-parsers`, off `dev`
(`942e58e`, which already carries 0030). **426/426 tests**, lint clean,
licence check green.

## How the wave was ordered (the prioritisation question)

**General engine first, bank parsers second.** They fix different failure
modes and the ordering is not a preference:

- **General** changes cover every bank, every message, pasted text, and banks
  we will never hand-write; one change retires a whole failure class. This
  project's invariants live there (amounts, directions, ingest guards).
- **Bank-wise** parsing earns its keep only where a bank's own format is
  structurally ambiguous (multiple amounts, decoy limits, POS layouts) or
  where the sender carries information the text does not (short SMS that never
  name the bank, masked formats). There, generality means guessing.

Within the bank-wise queue: **coverage × measured breakage × cost of the
error.** SBI/HDFC/ICICI/Axis/Kotak lead (user count); a *silently lost debit*
outranks a missing merchant every time, so guards and amounts lead, labels
follow. Regional banks (JK, South Indian, Karnataka…) come after the
nationals.

## What changed

**General engine (round 3)**
- HDFC-style NEFT credits name the remitting organisation as a segment —
  "…for NEFT Cr-CITI0100000-ACME TECHNOLOGIES-PERSON NAME-CITIN…" — and
  ICICI-style credits name the payer after the reference —
  "…Info NEFT-FDRLM4175907234-JOHN JOSE." Both are now merchant sources.
- "towards Netflix Entertainment Ser **for** AutoPay …" stops the merchant at
  "for" instead of swallowing the mandate tail.
- "…received … **for processing**" (NACH mandate / cheque acknowledgements) is
  vetoed at ingest — without it, "NACH Mandate : Rs. 100000.00 … received
  today for processing" read "received" as a credit verb and booked the whole
  mandate amount as phantom income.

**Sender-routed parsers (wave 1)** — each *layers corrections on the global
parse* rather than re-implementing it, so the amount/merchant logic cannot
drift between two copies; the sender mainly supplies the bank name the body
often lacks:
- **HDFC Bank** — DLT templates (`CP-HDFCBK-S`, `VM-HDFCBK-S`), direct IDs,
  RCS display name; mutual-fund senders (`AD-HDFCMF-S`) deliberately *not*
  routed here (unit allotments, not account transactions — the reference
  project orders its MF parser first for the same reason). Correction:
  "Transaction Reversed!… By AMAZON …" names the merchant after "By".
- **ICICI Bank** — `contains("ICICI")` plus direct IDs. Corrections: ATM
  markers nothing else explains — "NFSCASH WDL" / "NFS*CASH WDL*" (shared
  network → "Cash Withdrawal"), "CAM*…" (own ATM → "ATM Withdrawal") — and
  billers from "InfoBIL*Auto Loan" clauses.
- **Kotak Mahindra** — `contains("KOTAK")` (covers RCS display names).
  Correction: IMPS credits whose counterparty is a masked mobile ("by an A/C
  linked to mobile x111") use the mask as the merchant. QR-handle names,
  refund merchants, short-SMS payees and reversed-refund types were already
  resolved by the engine in 0030.

## Verification

- `./gradlew :app:testProdDebugUnitTest` — **426/426** (was 413; +13:
  10 in `TopBankParsersTest` incl. routing and the MF exclusion, 2 NEFT
  merchant cases, 1 NACH guard case).
- The registry's hard gate ("per-bank parsers beat the global regex on their
  own banks' cases", `BankParserRegistryTest`) still passes with the new
  routing.
- `:app:lintProdDebug` clean; `tools/check_license.sh` green; attribution
  paths in `docs/licenses/README.md` and `README.md` now include
  `data/classification/bank/`.

## Deliberately staged

- **Wave 2**: PNB, Bank of Baroda, IDFC First, IndusInd, Federal, Yes, Union,
  Central, South Indian, JK, UCO, AU, Bandhan, City Union, Indian Bank, IOB,
  wallets (Slice, CRED, OneCard, JioPay/Airtel/NSDL…). Same shape: sender IDs
  + structural corrections + tests.
- **Corpus additions + re-baseline** (deferred from 0029/0030): add synthetic
  entries for these banks, re-run `:app:classificationBaseline` and
  `FusedAiEval`, update the quoted numbers in AGENTS/`docs/evals`.
- **Foreign-currency amounts** remain unread by design — one number without a
  per-transaction currency would have to mislabel them.

## Open risks

- The layered parsers inherit the engine's read; if a bank's format changes,
  the *engine* regression tests catch it first, and the parser tests pin the
  bank-specific corrections only.
