# 0026 — The 32-finding code review, fixed

**Status:** done 2026-10-11 — **everything is uncommitted** by owner instruction
(no commits, no pushes). Working tree only.

The review is `docs/reviews/2026-10-10-code-review.md` (32 findings against
`dev` @ `3bbae7c`). All 32 are addressed: 30 fixed in code/tests/docs, 2 resolved
by a recorded decision with the reason written down (#10's `appDataFolder`
question, #30's premise correction). Three findings had a **premise that turned
out to be wrong** — those are called out below, because the fix is different from
what the review asked for and a future reader should not "re-fix" it.

## The findings that mattered most

### The flagship AI was inert (#1) — and the reason is a lesson

`auraspend_embedding.bin` is written **little-endian** by
`tools/build_embedding_asset.py`; `EmbeddingClassifier` read it **big-endian**.
The dimension decoded as `0x80010000`, the metadata slice ran off the end of the
23 KB file, `boundaryOrNull` swallowed the failure into `model = null`, and the
22 MB encoder returned null for **every** message in production — while
`LocalLlmProvider` logged "on-device classifier ready" and Settings reported the
model as available.

Nothing could catch it, and that is the interesting part: the Python tool's
round-trip check validated its own little-endian writer, the "shipped encoder"
accuracy eval replays a *recorded JSON report* and never touches the asset, and
no test decoded it. Three independent-looking signals were all downstream of the
same bytes.

Fixed: `EmbeddingCentroidCodec` (new file) reads little-endian with bounds and
layout validation; `ModelConstants.CENTROIDS_SHA256` pins the asset;
`EmbeddingClassifierAssetTest` decodes the **real** asset and asserts magic, dim,
group counts, category ids, unit-length vectors and the digest — plus that a
big-endian header is *rejected* rather than silently mis-decoded;
`OnDeviceClassifier.isOperational` + `LocalLlmProvider.isReady()` now report a
runtime that failed to load as not ready, instead of claiming success.

Measured after the fix: the regex floor is unchanged at **13.8 % exact / type
95.4 % / category 36.9 %**, and the fused system at **16.9 % exact, 0 destroyed,
5 rescued** — i.e. the fix changed no accuracy number, it made the numbers real.

### The discard gate was dead code (#9)

`EmbeddingClassifier` reported `pTransaction` (probability of the *transaction*
class) in `isTransactionProbability`. `AiSignalFusion.mayDiscard` reads that field
as confidence in the answer *given*, so on a negative verdict it is below 0.5 by
construction and the 0.9 threshold could never be met. The recorded "0 of 46
destroyed" was produced by a gate that never ran.

Fixed: `SmsExtraction.verdictConfidence(isTransaction, p)` is the one conversion;
the runtime uses it; the recorded eval report declares
`"probabilitySemantics": "p_transaction"` and `FusedAiEval` converts on load;
`FusedAiEvalTest` fails if the gate becomes unreachable again. The eval numbers
are unchanged (0 destroyed, 5 rescued) — now genuinely produced by the gate.

### Money could be lost three different ways

- **Triage "Saved" without a save (#2).** The swipe fired the intent and showed
  "Saved" + undo immediately. `ClassificationViewModel` now emits a one-shot
  `SmsTriageSaved` only after the transaction is written; the screen shows the
  snackbar from that event, and failures surface through `state.error` (the SMS
  tab now renders `AuraErrorBanner`).
- **The ingest watermark stranded history (#4).** A single cursor advanced to
  `newest - 1` after a `DESC LIMIT 500` scan declared the inbox covered after its
  first 500 bank-like messages. The review's own suggested fixes do not work
  (advancing only on a short batch re-reads the same page for ever; `minOf` does
  the same), so the scan now keeps **three** values — high-water mark,
  history-walk cursor, walk-done flag — and walks down one page per pass, only
  when the live queue is empty. `SmsIngestPlannerTest` drives a simulated
  1,200-message inbox and asserts every message is queued.
- **A duplicate `sourceSmsId` deleted the user's row (#7).** `REPLACE` is
  delete-then-insert in SQLite and `sourceSmsId` is unique, so a second save of
  the same SMS-linked transaction destroyed the user's edits. Writes are now
  update-if-present, else insert-if-absent (`IGNORE`), with the dead
  constraint-exception branch removed from the pipeline.

### Deposits and refunds were being dropped (#6)

`isOtpOrAlertMessage` rescued a message from its alert filter only when it had an
amount **and** one of six debit verbs — so "Rs 10,000 has been deposited … Avl
balance …" was skipped *before* the parser that reads "deposited" ever ran, which
is the silent money loss the OTP/alert invariant exists to prevent. There is now
one shared predicate (`BankMessageParser.hasMovementVerb`, both lexicons) used by
the rescue and by the unrecognized-message recorder, replacing three divergent
copies. Measured: the floor is unchanged.

## Everything else, by finding

| # | Fix |
|---|---|
| 3 | `CsvManager`: quote-aware record reader (embedded newlines, `""` escapes, CRLF), index 8 → `recurrenceFrequency`, symmetric `'` formula-guard strip; round-trip test through the real exporter/importer |
| 5 | One `buildSmsPipelineProcessor` factory for both background paths (the coordinator had been silently omitting `recordUnrecognized`) |
| 8 | `SmsQueueRowMapper`: safe enum parsing (unknown type → re-parse the body; unknown status → show as pending, never hide), the collector is a single cancellable job with a `catch` → `LOAD_FAILED` |
| 10 | `Tasks.await` on the sign-in task (the old check was a tautology the compiler flagged), status codes logged, `AuraLog` throughout, `CancellationException` rethrown, `DEPRECATION` suppressed with a pointer to ADR 0011. **Not** moved to `appDataFolder` — that would make the backup invisible to the user and contradict the privacy page; recorded as an owner call |
| 11 | Restore masks `smsMessages` and `unrecognizedSms` on the way in, like transactions |
| 12 | `purgeOlderThan()` is called on the periodic worker tick; retention test pins the cutoff and that the SMS queue is never swept |
| 13 | Privacy copy qualified in EN + HI ("the only upload is the Drive backup you start yourself"), privacy page lists the unrecognized table and states the 30-day purge, and `tools/check_privacy_claims.sh` (+ CI) fails an absolute claim without its exception |
| 14 | `TransactionClassifier.classify(body, sender)` routes through `BankParserRegistry` first, falling back to the global parser; the pipeline, the queue mapper, the paste path and both evals pass the sender. Zero callers → three parsers are live |
| 15 | Every repository write/read in a composable wrapped in `boundary`/`boundaryOrNull`; sheets/dialogs stay open on failure and say why (in-surface `AuraErrorBanner` for Recurring/Savings/QuickAdd, the existing snackbar for TransactionDetail, a banner above the form for NewTransaction); reads degrade to `UiError` → `AuraErrorState` with retry; the Recurring delete gained the confirmation dialog it was missing (and so did the Budget delete — the review's "unlike every other destructive action" was wrong, Budget had the same gap); the detail screen reads one row by primary key instead of scanning the table |
| 16 | No hardcoded `₹` left: amount prefixes use `LocalCurrencyStyle`, placeholders use `formatMoney`, the decorative rupee icons are gone, `DuplicateDetector` returns a structured reason |
| 17–21 | Nav labels + content descriptions, `formatRelativeDate`, the segmented control's fixed height, the two 40dp touch targets, enum names / splash tagline / taxonomy labels — all localized in EN + HI with a `DesignSystemGuardTest` assertion for the height rule and a `TaxonomyMappingGuardTest` for the ids |
| 22 | `BudgetSpending.periodEpochRange` (one window definition for screens and `spentFor`); Budget and Plan consume the indexed `observeExpenseByCategory` aggregate instead of loading every transaction, and Settings' CSV read moved into the export coroutine; `withFreshSpent` is production-dead and stays for tests; `BudgetSpendAggregateTest` pins the aggregate to the full-scan definition per category/period and pins Budget/Plan/Dashboard's three mappings to the same number. Insights was deliberately left (it groups by merchant and switches period — it needs aggregates that do not exist, and a half-migration would add a second source of truth) |
| 23 | Deleted: the duplicate-detection stubs, the unreachable `DuplicateDetectionDialog`, `MessageDedup`, `groupTransactionsByDate` + its test, `searchTransactions` (interface, impl, DAO, fakes), `nextScanCursor` + its tests (dead, and its premise *was* the #4 bug) |
| 24 | Duplicate `"mart"` key removed (grocery, as its comment always claimed) and `KeywordMapGuardTest` fails on any duplicate key in either table; the entertainment/subscription divergence is pinned with the trace showing the fusion layer wins in production |
| 25 | New tests: `BackupRestoreManagerTest` (replace, atomic failure, masking, queue status), `TransactionDaoTest` (real SQL: aggregates, local-day buckets, paging, LIKE), `CsvManagerTest` round trips, `ClassificationViewModelTest` + `kotlinx-coroutines-test`, `EmbeddingClassifierAssetTest`, `SmsIngestPlannerTest`, `SmsQueueRowMapperTest`, `UnrecognizedSmsRetentionTest` |
| 26 | R8 gate wired into `pr_check.yml`; `audit_native_runtime.sh` **fixed** — its `nm` probes ignored their arguments, so the import scan could not fail — and wired in for both shipped arm64 `.so` files. The one ONNX loader exception is named, evidence-gated and documented in AGENTS.md |
| 27 | `ci.yml` is push-only; `pr_check.yml` is the single PR gate (license + privacy checks, widened paths so docs-only PRs still run them) |
| 28 | Lottie attribution removed everywhere (screen, `check_license.sh`, catalog, README, `docs/licenses`, ADR 0009), plus the now-unreferenced licence text and four 0-byte animation assets |
| 29 | Stale task names, the deleted `app/src/play/` rule, `main`-branching, the CashewComponents paragraph and glossary row, and every "Download APK"/Play claim — now honest ("build from source", "staged, not published") |
| 31 | The **`prod` flavor removes `profileable android:shell="true"`**; the `dev` flavor keeps it so baseline profiles can still be generated from a release-type build (same code, same R8 output) |
| 32 | Guava + Espresso declarations removed (Espresso's `androidx.test:runner` swapped in explicitly), catalog comments corrected, GoogleSignIn deprecations suppressed with ADR 0011 as the reason |

## Findings whose premise was wrong

1. **#30 — "destructive fallback disabled" is inverted.** In Room 2.8.5
   `fallbackToDestructiveMigration(false)` *enables* destructive recreation, so
   the real exposure was **silent data loss**, not a crash loop. Git evidence:
   0.1.0 shipped schema **v2** (`6f2202f`), `fe6615c` bumped to v3 with
   `savings_goals`, and the first migration appeared at v5 — no 1→2 or 2→3
   migration ever existed. Fixed by adding `MIGRATION_2_3` (the exact v2→v3
   delta), destructive-from-v1-only, a downgrade fallback, and
   `exportSchema = true` with `ksp { arg("room.schemaLocation", …) }` so
   `app/schemas/…/8.json` is now exported and reviewable.
2. **#4 — the suggested fix stalls.** Documented above; the three-cursor design
   is what actually makes progress possible.
3. **#24 — the two keyword maps diverge, but the fusion layer overrides the
   divergence in production** (`isSubscription` wins over the parser's category),
   so the entertainment/subscription split shows only in the regex-only
   measurement. Pinned with a test rather than "unified", because unifying moves
   measured category accuracy.

## Decisions made in this session

- **Deleting the duplicate-detection scaffolding rather than finishing it.**
  The review lists finishing it as Direction 1 (an option, not a defect). Its
  `MarkAsDuplicate` branch would have closed the editor *without saving*; the
  CSV path keeps its working duplicate filter. Finishing it needs a repository
  read the ViewModel does not have — an owner call.
- **`getRecurringTransactions()` is kept and documented**, not deleted: it is the
  hook Direction 2 needs. `searchTransactions` *was* deleted (superseded by the
  paged query).
- **The Drive backup stays in the Drive root** (#10's "consider
  `appDataFolder`"): making it invisible to the user is a privacy *and* copy
  change that contradicts the freshly written privacy page.
- **A profileable release build is not worth the benchmark convenience** (#31):
  the shipping flavor drops the tag, the dev flavor keeps it.
- **Lint baseline left alone.** Stale rows (deleted Lottie/`res/raw` resources,
  the three `ModelDownloadManager` entries the review mentions) do not fail lint;
  regenerating it is a separate deliberate pass.
- **Deprecations kept with a written reason:** the GoogleSignIn API (ADR 0011),
  `Locale(String, String)` in `MoneyFormattingTest` (JDK-deprecated, no
  behavioural difference for the locales under test) and
  `createComposeRule` in `SwipeToActTest` (the v2 rule queues tasks instead of
  running them eagerly, so migrating needs its own verification).

## Verification

- `./gradlew :app:compileProdDebugKotlin --rerun-tasks` — clean; the only `w:`
  lines are two pre-existing `app/build.gradle.kts` lint-report deprecations and
  the three test-file deprecations listed above.
- `./gradlew testProdDebugUnitTest` — **343/343 green** (324 at the first pass
  of this session; +19 in the follow-up pass below).
- `./gradlew :app:lintProdDebug` — green (0 errors, 0 warnings). The baseline was
  regenerated deliberately and the diff reviewed: 8 stale rows dropped (the
  removed guava/espresso declarations, the `NonObservableLocale` entry at its old
  location, the four deleted `res/raw` assets, one now-used string) and 1 added
  (the deliberate `kotlinx-coroutines-test` pin). 64 rows from 75.
- Every `w:` line triaged: the nine `Locale(String, String)` warnings in
  `MoneyFormattingTest` and the `createComposeRule` deprecation in
  `SwipeToActTest` were **fixed**, not suppressed (`forLanguageTag("en-IN")`, and
  the v2 rule — whose `StandardTestDispatcher` is why the test already calls
  `waitForIdle()`); the GoogleSignIn deprecations carry a suppression with a
  pointer to ADR 0011; the two `app/build.gradle.kts` lint-report deprecations are
  pre-existing and unrelated.
- `bash tools/check_license.sh` — exit 0, "Licence metadata is consistent."
- `bash tools/check_privacy_claims.sh` — exit 0.
- EN/HI string parity: 491 keys each, no duplicates, no one-sided keys.
- Measured classification numbers unchanged: regex floor 13.8 % / 95.4 % /
  36.9 %; fused 16.9 % exact, 0 destroyed, 5 rescued.
- Merged-manifest check: `prodDebug`/`prodRelease` contain **no** `profileable`
  element; `devDebug`/`devRelease` do.
- `app/schemas/com.awbuilds.auraspend.data.local.AppDatabase/8.json` exported
  (8 tables) — the fixture a future migration test needs.

## Follow-up pass — the residuals the advisor's verification found

A full re-verification of this diff against the 32 findings found one regression
introduced by the fixes and five partial leftovers. All are closed here:

- **Batch save lost atomicity.** `saveTransactions` had become a per-row loop of
  update+insert pairs, each in its own implicit transaction — a CSV import that
  failed halfway persisted the rows already written while the caller reported
  failure. `TransactionDao.upsertTransactions` is a `@Transaction` default method
  (update-if-present, else insert-if-absent) and the repository uses it, so the
  import is all-or-nothing again. `TransactionRepositoryBatchTest` pins both the
  rollback (a UNIQUE violation on the second row leaves the first one un-written)
  and the update/insert split against real Room.
- **The deposit/refund rescue had no test.** The shared predicate
  (`BankMessageParser.hasMovementVerb`) replaced the six-word lexicon but nothing
  asserted the case the whole fix was for, so a future narrowing of the lexicon
  would pass the whole suite. `SmsMovementVerbRescueTest` (10 cases) pins the
  predicate over both lexicons, the deposit/refund/credit-with-balance-tail
  rescues, and the filter's untouched vetoes. The golden corpus was deliberately
  left alone: it is the anchor the 13.8 % floor and the 0-destroyed eval are
  measured against, and re-measuring for two rows buys nothing these tests do not.
- **`LocalLlmProvider` had no tests.** `overrideForTest` had zero callers and the
  degradation/readiness property was verified only on a device. A `resetForTest`
  seam (the provider is a process-wide object; without it one test's `init()`
  leaks into the next) plus `LocalLlmProviderTest` (7 cases): used-before-init
  degrades, a failed build is cached, a real JVM build degrades instead of
  throwing, and `isReady` is false when the model is absent *or* the runtime
  reports itself non-operational — the #1 lie, pinned at the readiness level.
- **The category add dialog never closed on success** (and edit/delete closed
  optimistically). `CategoryManagementScreen`'s callbacks now return whether the
  write landed (suspend `(…) -> Boolean`); all three dialogs close only on
  success and stay open with the banner on failure.
- **Two stale task names survived #29**: `DemoDataSeeder.kt` still said
  `connectedFreeDebugAndroidTest` and launched the prod id, and README +
  `tech-stack.md` still sent the cold-start benchmark at the prod variant that
  #31 stripped `profileable` from. Both now use the dev flavor (with the full
  activity class name, matching `tools/seed_demo_data.sh`).

Smaller items from the same verification: the shadowed outer
`notificationPermissionLauncher` in `ClassificationScreen` is gone (#23's last
residual); the `AuraErrorBanner` dismiss button is 40dp and now has a 48dp
minimum (pre-existing — the review cited that line as a 48dp example and was
wrong); `AiSignalFusion.mayDiscard`'s KDoc no longer claims the null-probability
branch is "behaviour-preserving for the shipped model" (false after the #9
polarity fix — a wrong comment on the discard gate is what #9 was about); the
main manifest's comment points at `prodRelease/` (not the empty `prod/`); the
`RecurringScreen` KDoc no longer describes an `else` arm the exhaustive `when`
does not have; the dead `formatMoney` import is gone.

## Integration pass (after the four workstreams landed)

The workstreams could not run Gradle (the orchestrator owns builds), so the last
pass was: compile, run everything, and fix what the compiler and the tests found.

- **Three test expectations were wrong, not the production code.** Two in
  `TransactionDaoTest` (the `type` and `categoryId` paging expectations were each
  other's — the SQL is correct and the tests now assert what it returns) and one
  in the same file's localtime sanity probe, which used `00:00Z` and is therefore
  the *same calendar day* in IST; it now probes an instant that crosses a date
  boundary for any real offset. Worth recording because "the tests fail" and "the
  code is wrong" are different claims.
- **The by-id transaction read** (`getTransactionById` on the DAO, the repository
  and the impl) was added, since the review's claim that one already existed was
  wrong — the `getById` it saw belongs to `SmsMessageDao`.
- **The Recurring delete dialog got its own strings** (`recurring_delete_title` /
  `recurring_delete_message`, EN + HI) instead of standing in for the savings
  pair, and the **Budget delete** got the same confirmation dialog the Recurring
  one needed, with its own pair.
- **The `profileable` removal is scoped to `prodRelease`**, not the whole `prod`
  flavor: a `tools:node="remove"` with nothing to remove makes the unit-test
  manifest merge emit a warning, and scoping it to the shipping variant is both
  quieter and more precise. `devRelease` keeps the tag for baseline profiles.
- **Twelve strings that only the deleted code used** were removed from both
  locales (three date-group labels, nine duplicate-dialog strings), which is what
  lint's `UnusedResources` was pointing at.

## Open risks / pending owner calls

- **Nothing was run on a device.** Everything visual (48dp triage buttons, the
  segmented control at 200 % font scale, the new Recurring confirmation dialog,
  the Hindi taxonomy labels) and the Drive backup/restore round trip still need
  one manual pass.
- **Backfill behaviour change (intentional):** a fresh install (or an upgrade)
  now walks the whole inbox history, one 500-message page at a time, and only
  while the live queue is empty. On a long history that is hours of background
  classification — deliberate, but the owner should know it is new.
- **`LocalLlmProvider.isReady()` still has no production caller.** The Settings
  AI card reports *download* state; a runtime that fails to load is logged and
  degrades, but the card does not say so. Wiring `isOperational` into that card
  is a small follow-up.
- **Branch protection:** `ci.yml` no longer runs on PRs, so a required `CI`
  check must be changed to `PR Check` in the repo settings, or PRs will wait for
  a status that never arrives. Not visible from the repo.
- **The first CI run of the two new gates** (R8, native audit) is unverified on
  the runner image; both were exercised locally.
- **Terms §4.2 wording** was corrected from "published on Google Play" to
  "staged, not published" — the owner should eyeball legal copy.
- **`docs/privacy.html:82`** still says "Applies to the Google Play build …"
  (a scope phrase, not an availability claim) — left deliberately.
- **Flagged by the integration pass, not fixed at the time:** the category
  dialogs closed optimistically (edit/delete) or never (add) — closed in the
  follow-up pass above. Still open: `BudgetScreen` applies the scaffold padding
  on two nested containers, so the empty state is inset twice;
  `DuplicateDetector.reason` is an inert string with no consumer (typing it needs
  a field on `MerchantData.kt`).
- Deferred: unifying the two keyword maps; finishing duplicate detection;
  a `room-testing` migration test (the schema is exported now, so the next
  migration can have one); `sms_messages` retention (deliberately none);
  `LocalLlmProvider.isReady()` has no production caller, so the Settings AI card
  still reports download state rather than runtime state.

## Next step

Owner reviews the diff, then commits. Play launch steps are unchanged from 0024:
upload-key reset → replace the staged AAB → send for review.
