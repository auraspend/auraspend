# 0033 — wave 3 (28 banks/wallets), card-payment semantics, corpus re-baseline

**Status:** done 2026-10-13. Branch `feat/per-bank-parsers-3`. **465/465 tests**,
lint clean, licence check green.

## 1. Card-bill settlements are not transactions (Option A)

Both sides of a credit-card bill payment are now excluded at ingest:

- **card side** — "Payment of Rs X has been received on your … Credit Card",
  "… credited to your SBI Credit Card", "received as payment towards your PNB
  credit card";
- **bank side** — "… debited … towards PNB credit card payment", "… for credit
  card bill payment".

The app counts spending when the card is swiped, so the settlement is money
moving between the user's own pockets: booking either side double-counted the
spend (a ₹5,000-swipe month paying a ₹5,000 bill showed ₹10,000 spent), and the
card side used to inflate income. Carve-outs keep **refunds, reversals and
cashbacks on the card ingestable** (money coming back stays income) and card
spends untouched. Nine pinning tests, including the two positive controls.

The guard sits in `SmsAutoClassifier` — upstream of the classification memory,
for the reason the last device pass proved: the memory can resurrect a
wrongly-saved class from its own history, so the exclusion must fire before
the parser/enricher ever see the message.

## 2. Wave 3 — 28 sender-routed parsers (registry now 46)

- **National banks**: Indian Bank, Indian Overseas Bank, IDBI, UCO, Karnataka,
  City Union, Dhanlaxmi, Department of Post, Equitas, Punjab & Sind, Utkarsh,
  JK Bank.
- **Cards & wallets**: HSBC, Standard Chartered, AMEX, OneCard, Slice, CRED,
  LazyPay, Jupiter, JioPaymentsBank, JioPay, Airtel Payments Bank, NSDL,
  Pluxee, Amazon Pay, Cashfree, Saraswat.

All are *attribution-first* (sender→bank over the engine); bank-specific
corrections are staged for wave 4 where fixtures demand them. The global
bank-name table gained the same brands, and **IOB is its own bank now** — it
used to resolve to "Indian Bank".

## 3. Privacy: synthetic-only fixtures + a tripwire

- Device-derived fixture values re-templated: amounts (749.50 / 735.50 /
  645.00-style), emails (`*@example.com`), a synthetic UPI reference, and the
  reference project's payee names replaced with placeholders.
- New **`FixturePrivacyTest`** scans `src/test` for real-looking Indian mobile
  numbers, non-`example.com` emails and known production names; its allow-list
  is the synthetic vocabulary only. It made the rule mechanical instead of a
  review habit.
- Repository rule going forward, as instructed: fixtures are public-source
  formats plus our placeholders — nothing from any device, ever.

## 4. Corpus + re-baseline

- Corpus 65 → **72** cases: seven synthetic twins for the wave/device classes,
  each carrying its sender so the registry gate measures the new banks'
  routing.
- Regex floor re-measured on the new set: **13.9 % exact, type 95.8 %,
  category 34.7 %** (was 13.8 / 95.4 / 36.9 on 65). `FusedAiEvalTest`'s
  corpus-size assertion now derives the count from the corpus, and
  `docs/evals/README.md` notes the fused rows are dated measurements on the
  pre-extension set.

## Staged

- **Wave 4** — corrections for the wave-3 cohort where their fixtures demand
  them, plus any new general classes a device pass surfaces.
- **Optional device pass** — the dev app's DB on the phone is corrected from
  the last pass; a targeted re-stage is quick if wanted. Not done in this
  session.
- No release cut: this lives on `dev` until a 0.1.4 (not requested).
