# Agent memory — AuraSpend

Claude Code auto-loads only this file, so this is the entry point — everything
durable lives elsewhere. Keep it a pointer; promote findings to `memory/` or
`AGENTS.md` instead of growing this file.

**Start every session by reading `AGENTS.md` and the latest file in
`docs/handoffs/`.** The contract (hard requirements, commands, invariants) is
`AGENTS.md`; the session log is `docs/handoffs/`; the durable detail is
`memory/`.

| What | Where |
|---|---|
| Agent contract, commands, hard requirements | `AGENTS.md` |
| What shipped, session by session (latest first) | `docs/handoffs/` |
| Glossary, decisions, stack, project state | `memory/` |
| Architecture decisions | `docs/adr/` |
| Roadmap, deliberate no-gos | `ROADMAP.md` |
| Design system | `docs/design/aurora.md` + `ui/designsystem/` |
| Measured classification numbers | `docs/evals/README.md` |

## Classification hot spots (cited from source)

- `keywordCategoryFor` (`data/classification/DefaultCategories.kt`) —
  word-boundary, longest-key keyword scan; substring-unsafe keys ("fee",
  "credit", "vi") caused real misfiling. Never add one. A duplicate key is
  silently deduped by the map (last wins) — `KeywordMapGuardTest` fails the
  build on one, because `"mart"` was declared twice.
- `MerchantRepository.resolveMerchant` — normalized + token + phrase tiers;
  the merchant CSV is unquoted inside `install()`; wallets and card networks
  were removed on purpose (they forced wrong categories).
- `SmsAutoClassifier.isOtpOrAlertMessage` — the rescue uses
  `BankMessageParser.hasMovementVerb` (one shared lexicon, debit *and* credit).
  The OTP branch may skip only when there is no amount **and** no movement verb;
  the hard fraud veto has no rescue at all.
- `SmsIngestPlanner` (`data/classification/SmsIngestCursor.kt`) — three cursors:
  high-water mark, history-walk cursor, walk-done flag. Read D16 before touching.
- `SmsExtraction.isTransactionProbability` — confidence in the *verdict given*
  (`SmsExtraction.verdictConfidence`), not P(transaction). The whole
  delete-a-transaction gate depends on the polarity (D15).

## Currency (added 0027)

- The list is a **constant** (`ui/designsystem/CurrencyCatalog.kt`) captured from
  `api.frankfurter.dev/v1/currencies`; never fetch it at runtime. Display names
  come from the platform's `Currency.displayName`, not from 30 new strings.
- **No conversion exists.** The picker changes symbol/formatting only, and its
  copy says so — do not remove that line without adding real rates.
- The chosen code is **hoisted state** (`MainActivity` → `AuraSpendTheme`), not a
  preference read inside the theme: a preference is not observable, so the symbol
  would go stale until a cold start.
- In `CurrencyPickerList` the note sits **above** the `LazyColumn` on purpose: a
  Column gives each child what the previous ones left, so a non-weighted
  `LazyColumn` first in the column eats the whole bounded height and anything
  after it is measured to zero (it rendered as nothing on device until 0027).

## Before ending a session

- Green build: `:app:compileProdDebugKotlin`, `testProdDebugUnitTest` and
  `:app:lintProdDebug`; read every `w:` warning.
- `bash tools/check_license.sh` and `bash tools/check_privacy_claims.sh`.
- Update the "Latest:" line in `AGENTS.md` and add `docs/handoffs/NNNN-*.md`.
