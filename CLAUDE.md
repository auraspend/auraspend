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
- **SMS queue statuses** (`data/local/entities/Entities.kt`) — `CLASSIFYING` is the
  *in-flight* marker (a pass claims a row before the expensive work); `PROCESSED` is
  *parked by the user* (both undo paths put it there) and must **never** be swept or
  auto-processed, or an undo is silently reverted. See 0028.
- **Categorisation progress is derived, not reported** — `queueProgress(rows)` over the
  observed queue feeds the screen, and `SmsProgressNotifier` reads the same counts from the
  database. Notification rules live in `copyForSnapshot` (pure, tested).
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

## Device-only paths (added 0046)

- **A JNI path that only compiles is not tested.** `libsqlcipher.so` and
  `libonnxruntime*.so` are Android libraries — loading them on the JVM host is
  an `UnsatisfiedLinkError`. When a new native dependency needs an upgrade or
  migration, the *policy* goes behind a seam with JVM tests (see
  `SqlCipherGateway`) and the *recipe* goes in `app/src/androidTest`. The
  instrumented source set is now compiled by CI (`:app:compileDevDebugAndroidTestKotlin`),
  because `R8ReleaseSmokeTest` sat broken for five releases before that gate
  existed.
- **Run the device suite on the local emulator, not only on the phone.** The
  arm64 AVD (`Android_17`) takes about a minute: boot it headless, then
  `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDevDebugAndroidTest`.
  Three release-blocking SQLCipher defects were found this way and by nothing
  else. `/data` at 93 % gives `INSTALL_FAILED_INSUFFICIENT_STORAGE`;
  `-wipe-data` fixes it. On a **real phone**, two extra rules: **keep the screen
  awake** (Compose emits no semantics while the display sleeps, so assertions
  time out on a UI that is drawing fine), and never use
  `connectedAndroidTest` on an install whose data you care about — it
  **uninstalls** the app afterwards; install both APKs and use
  `am instrument` instead.
- **A device test must not destroy the app's data or close its database.** The
  demo seeder did both: `DemoDataSeeder` is opt-in (`-e seedDemoData true`) and
  closes nothing it did not open, because closing the application's singleton
  breaks every test after it in the same process ("Database is closed"). Per-class
  runs hide this; run the whole suite.
- Robolectric tests that instantiate the real app get
  `RobolectricWorkManagerApp`, which substitutes the database passphrase and
  passes `null` for the open-helper factory. Point a test at it explicitly
  (`@Config(application = RobolectricWorkManagerApp::class)`); the manifest
  default runs `AuraSpendApp` and throws.

## Before ending a session

- Green build: `:app:compileProdDebugKotlin`, `testProdDebugUnitTest` and
  `:app:lintProdDebug`; read every `w:` warning.
- `bash tools/check_license.sh` and `bash tools/check_privacy_claims.sh`.
- Update the "Latest:" line in `AGENTS.md` and add `docs/handoffs/NNNN-*.md`.
