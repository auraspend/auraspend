# 0032 — sender-routed parsers wave 2 + the real-inbox audit sweep

**Status:** done 2026-10-13. Commits `10c172c` (wave 2) + `12b0034` (device-pass
fixes) on `dev` locally, landing via a short-lived branch. **451/451 tests**,
lint clean. The device pass rewrote the picture twice — this handoff is mostly
about what the phone said.

## Wave 2 (what was built before the phone had its say)

**12 more sender-routed parsers** (registry now 18): PNB, Bank of Baroda,
IDFC First and IndusInd Bank carry corrections; Federal Bank, Yes Bank, AU
Small Finance Bank, Bandhan Bank, Union Bank, Central Bank of India, South
Indian Bank and Bank of India carry sender→bank attribution over the engine.
Layered on the engine, per the wave-1 pattern (`docs/handoffs/0031`).

Engine round 4 from their fixtures: BoB's `to:<payee>` form; `thru/through`
and `/` close merchant captures; "credited … by <NAME>"; "towards IMPS/…" →
`IMPS Transfer` and "credited to the beneficiary account" → `NEFT Transfer`
rail labels.

## The device pass (fresh full re-ingest, diffed per message)

Method: queue + ingest cursor reset on the dev app (DB rows + `auraspend_prefs`
keys — no `pm clear`, so onboarding survived), full re-ingest of the owner's
inbox, and a per-message diff against a pre-drain DB backup plus the previous
session's per-message outcomes. The drain ran with the screen locked (the
process needs no UI; the keyguard would not dismiss this time and did not
matter).

### What the diff found — six classes fixtures had not

1. **Balance ledger annotations.** "Aval Bal Rs.27000.00 CR." made "Debited
   with Rs.5000.00" *typeless* — the CR belongs to the balance, and the
   ledger-suffix scan counted it as the opposite direction. PNB's whole debit
   family came back unreadable. Fixed by masking `Bal … CR/DR` before scoring.
2. **Passive mail phrasing.** "Statement **is sent to** <email>" booked
   statements as expenses via the literal "sent"; "If not **received**, SMS
   ENRS" booked e-statements as income via "received". Fixed with scoped
   type-scoring neutralisations.
3. **Bill notices.** "is due for payment", "is due by", "Total amount
   payable", "Payment due date" — every monthly Jio bill and every card
   statement had booked as an **expense**. Now ingest vetoes.
4. **Future refunds.** "Amt **will be refunded** within 3-4 days" booked as
   income. Vetoed (same family as "refund … initiated").
5. **Biller receipts.** The user's own utility payments ("Payment of Rs X …
   has been received **on** <date>") booked as **INCOME**. Direction-fixed to
   a debit; the card-side "received **on your** Credit Card" income semantic
   is preserved; the bank+biller double announcement stays with the existing
   duplicate fingerprint (1 of 9 receipts deduped on-device).
6. **"Kindly ignore, if already paid"** slipped the reminder veto on its
   comma — and its "paid" then supplied the debit verb for the Jio bills.

### The discovery only the phone could make

The **classification memory** (`SmsAiEnricher` fast path) fills `type` from
remembered history when the regex leaves it null. Three Jio e-bills had been
wrongly saved *before* the wave; the memory learned from those saves and
**resurrected them as expenses after every text-level guard** (removing the
save did not stop the next pass — only a veto upstream of the memory did).
A full sweep of the 481 final saves found exactly those 3 memory-driven rows
and **zero** direction mismatches — everything else now matches the regex
layer. The lesson is recorded here because it generalises: *a save the regex
should not have made teaches the memory, and the memory then defends the
mistake*; guard classes at ingest, which is upstream of the memory.

### Final numbers on the owner's real inbox (1,952 messages)

| | Before this pass (old build) | After (wave 1+2 + fixes) |
|---|---|---|
| Candidates queued | 706 | **700** (6 notices kept out at ingest) |
| Saved | 452 | **481** |
| Skipped | 254 | **219** |
| Failed | 0 | **0** |
| Unrecognized | 9 | **2** |
| Phantom transactions | 24 (10 Jio bills, 7 ICICI statements, 5 SBI e-statements booked as income, IRCTC refund notice) | **0** |
| Utility payments | 9 as income | **8 as expense** (1 deduped) |

The 58 skipped→saved gains are mostly **old false-dedup casualties** recovered
by the clean rebuild (Canara UPI debits 27, SIP purchases 10, Jio payments 12,
ICICI/Kotak 7, misc 2) — real transactions the pre-0029 date-collapse era had
skipped and no re-run ever revisited.

## Deliberately staged (next waves)

- **Wave 3**: Indian Bank, IOB, UCO, IDBI, Karnataka, City Union, JK, Dhanlaxmi,
  DOP, Equitas, Punjab & Sind, Utkarsh, HSBC, Standard Chartered, AMEX, and the
  wallets (Slice, CRED, OneCard, JioPay/Airtel/NSDL, Pluxee, Jupiter).
- **Corpus additions + re-baseline** for all of wave 1–2 (deferred from
  0029–0031): add synthetic entries (incl. the six device-pass classes, so the
  suite measures them), re-run `:app:classificationBaseline` + `FusedAiEval`,
  update the quoted numbers.
- **Foreign-currency amounts** remain unread by design.

## Open risks

- The device DB retains memory rows for the corrected classes; they are
  unreachable (vetoed at ingest) but a future un-vetoing must remember them.
- The device DB (dev app on the vivo) is left in place — corrected and drained —
  for another pass; the temp audit dir (outside the repo) holds the per-message
  diff inputs and should be deleted when no longer needed.
