# AuraSpend code review — 32 findings

**Date:** 2026-10-10 · **Reviewed against:** `dev` @ `3bbae7c` · **Reviewer:** full-tree audit (six parallel read-only passes: data/domain, security, UI/design-system, AI/classification, tests/tooling, architecture/deps/docs), every finding re-verified against source.

**Verification baseline at review time:**
- `./gradlew :app:compileProdDebugKotlin --rerun-tasks` — green; `w:` lines match handoff 0025's list (GoogleSignIn deprecations, `OpenInNew`/`MenuBook` icon deprecations, UiError annotation) plus one non-deprecation warning that finding #10 explains.
- `./gradlew testProdDebugUnitTest --rerun-tasks` — 256/256 green.

**Priority table**

| # | Finding | Effort | Risk | Conf. |
|---|---|---|---|---|
| 1 | On-device AI is silently inert: centroid asset is little-endian, reader parses big-endian | S | LOW | HIGH |
| 2 | Triage swipe shows "Saved" without checking the save outcome; errors never rendered in SMS tab | S | LOW | HIGH |
| 3 | CSV import loses `recurrenceFrequency`, breaks quoted newlines, keeps the `'` formula guard | S–M | LOW | HIGH |
| 4 | Ingest watermark jumps past messages beyond the 500 cap on first run — history never ingested | S | LOW | HIGH |
| 5 | Background classifier never records unrecognized money-movement SMS | S | LOW | HIGH |
| 6 | Deposits/refunds with a balance tail are skipped before parsing (verb lexicon lacks them) | S | LOW-MED | HIGH |
| 7 | Duplicate `sourceSmsId` writes REPLACE-delete the user's edited row | S–M | MED | HIGH |
| 8 | `Enum.valueOf` + `!!` on DB strings; one bad row freezes the inbox collector | S | LOW | HIGH |
| 9 | `mayDiscard` confidence gate is unreachable for the shipped runtime (probability polarity) | S | MED | HIGH |
| 10 | Drive sign-in result never inspected — `account != null` is always true | S | LOW | HIGH |
| 11 | Restore re-introduces unmasked SMS bodies; class doc claims the choke-point covers it | S | LOW | HIGH |
| 12 | 30-day retention sweep is dead code — `unrecognized_sms` grows forever and ships in every backup | S | LOW | HIGH |
| 13 | Absolute privacy copy vs. a backup that carries SMS text; privacy page contents list one table short | S | LOW | HIGH |
| 14 | "Classify by sender first" is unimplemented — `BankParserRegistry.parserFor` has 0 production callers | M | MED | HIGH |
| 15 | ~10 unguarded repository writes/reads in composables (incl. unconfirmed destructive delete) | M | MED | HIGH |
| 16 | Hardcoded `₹` in 6 amount-field prefixes + `"₹0"`/`"₹0.00"` placeholders + detector reason | M | LOW | HIGH |
| 17 | Nav bar labels & content descriptions are hardcoded English | S | LOW | HIGH |
| 18 | `formatRelativeDate` ("Today"/"Nd ago") hardcoded English inside the design system | S | LOW | HIGH |
| 19 | Shared segmented control fixes its height — clips at 200% font scale | S | LOW | HIGH |
| 20 | Two 40dp touch targets; one is the only Settings entry on four tabs | S | LOW-MED | HIGH |
| 21 | Enum names rendered as UI copy; splash strings unlocalized; taxonomy matched on English names | S–M | LOW | HIGH |
| 22 | Budget spend computed 3 divergent ways; two full-table loads against ADR 0003 | M | MED | HIGH |
| 23 | Dead code with live tests/UI: dedup stubs + unreachable dialog (a `MarkAsDuplicate` path that would close without saving), `MessageDedup`, `groupTransactionsByDate` + its test | S–M | LOW | HIGH |
| 24 | Duplicate `"mart"` key (last wins = Shopping, contradicting its comment); two divergent keyword maps disagree | S/M | MED | HIGH |
| 25 | Most destructive op untested: no `BackupRestoreManager` test; no real-Room DAO tests; no ViewModel tests (`coroutines-test` absent) | M | LOW | HIGH |
| 26 | R8 + native-runtime audit gates exist but no workflow runs them | M | MED | HIGH |
| 27 | `ci.yml` and `pr_check.yml` both run full test+assemble on every PR | S | LOW | HIGH |
| 28 | In-app licenses screen + README attribute a "bundled" Lottie that isn't a dependency — and `check_license.sh` enforces the false row | S | LOW | HIGH |
| 29 | Docs drift: stale task commands, `src/play`, AGENTS.md points at a file deleted 20 handoffs ago, "Download APK" → zero releases (verified live) | S | LOW | HIGH |
| 30 | Latent: migration chain starts at v3 with destructive fallback off, `exportSchema=false` | S | LOW | MED |
| 31 | `profileable android:shell="true"` ships in the release manifest | S | MED | MED |
| 32 | Untriaged deprecations on the Drive path (GoogleSignIn → Credential Manager); unused `guava`/`espresso` deps; false catalog comment | S/M | LOW-MED | HIGH |

---

## 1. The on-device AI is inert in shipping code — asset/reader byte-order mismatch

- **Evidence**:
  - `app/src/main/assets/auraspend_embedding.bin` header bytes: `41 55 52 41 45 4d 42 31 | 80 01 00 00 | 30 01 00 00` — magic `AURAEMB1`, then dim = 384 and metaLen = 304 **little-endian** (0x180 / 0x130).
  - Writer agrees: `tools/build_embedding_asset.py:72,80,85,87` — `struct.pack("<I", dim)`, `struct.pack("<I", len(blob))`, `struct.pack("<B", …)`, `vec.tobytes()` (numpy native = little-endian).
  - Reader disagrees: `app/src/main/java/com/awbuilds/auraspend/data/ai/EmbeddingClassifier.kt:227-230` (`u32()` = `shl 24 … shl 16 … shl 8` = big-endian) and `:248-249` (`ByteOrder.BIG_ENDIAN` floats, with a comment asserting the asset is big-endian).
  - Consequence chain: `readCentroids` decodes dim = 0x80010000 and metaLen = 0x30010000, `String(bytes, p, metaLen)` (`:238`) throws against the 23,363-byte asset, `boundaryOrNull` (`:66`) sets `model = null`, `extract()` returns null at `:77` for **every** message.
  - Nothing detects it: no test constructs `EmbeddingClassifier` or parses the asset (the "shipped encoder" eval replays a recorded JSON report, `FusedAiEvalTest.kt:91`); the Python tool's own round-trip check (`build_embedding_asset.py:99-114`) verifies against its *own* little-endian writer, never the Kotlin reader; `LocalLlmProvider.isReady` (`LocalLlmProvider.kt:32-33`) still returns true and `build` logs "on-device classifier ready".
- **Impact**: The flagship feature of the current phase (22 MB on-device encoder) contributes nothing in production — silently regex-only — while readiness UI and logs claim otherwise. Anyone following the documented regeneration workflow re-breaks it with no test failure.
- **Effort**: S.
- **Risk**: LOW — format fix plus a test; decide deliberately which side is canonical.
- **Confidence**: HIGH.
- **Fix sketch**: Align both sides on little-endian (writer is the natural choice; or add an explicit byte-order field to the format). Add a JVM test that decodes the real asset and asserts magic, dim, group counts, `categoryIds` length. Pin the asset's SHA-256 next to the model digests in `ModelConstants`.

## 2. Triage swipe confirms "Saved" before/without checking the save outcome

- **Evidence**:
  - `app/src/main/java/com/awbuilds/auraspend/ui/classification/ClassificationScreen.kt:247-266` — `onTriageSwipe` fires `ClassificationViewIntent.SaveClassifiedSms(smsId)` and *immediately* shows the "saved" snackbar with undo; no outcome is awaited.
  - `ui/classification/ClassificationViewModel.kt:411` — a message with no amount/type sets `error = UiError.NO_TRANSACTION_FOUND` and returns without saving; `:438-439` — a DB failure sets `error = UiError.SAVE_FAILED`, row stays in place.
  - `ui/classification/ClassificationSmsTab.kt` (all 551 lines) never reads `state.error`.
  - `DesignSystemGuardTest`'s `ErrorStateWiringTest` concatenates *all* `*Screen.kt`/`*Tab.kt` files for a feature, so `ClassificationReviewTab.kt:157` (`state.error?.let`) satisfies the check for the whole `classification` feature — the triage tab's own errors are invisible to the test.
- **Impact**: User swipes right, gets a green "Saved", and no transaction exists — silent money loss with success UI. `pendingInbox` (`SmsAutoClassifier.kt:40-41`) filters only saved/dismissed, so unparseable-but-queued messages really do reach this gesture.
- **Effort**: S.
- **Risk**: LOW — snackbar becomes conditional on the save result.
- **Confidence**: HIGH.
- **Fix sketch**: Make the save report completion (a one-shot channel or a save-outcome state field); show "saved" only on success and an `AuraErrorBanner`/snackbar on failure; render `state.error` in the auto-detect tab too; tighten `ErrorStateWiringTest` to require the error be read in the composable that can produce it.

## 3. CSV import cannot read its own export

- **Evidence**:
  - `app/src/main/java/com/awbuilds/auraspend/data/local/CsvManager.kt:158` — import reads `parts.getOrElse(9)` (`sourceSmsId`) but never index 8 (`recurrenceFrequency`), while export writes it as column 9 (`:78`).
  - `CsvManager.kt:111` — `reader.forEachLine` splits inside quoted fields, so the multi-line notes export deliberately quotes (`:168`) are shredded into two malformed rows on re-import. The existing "newline survives round trip" test (`CsvManagerTest.kt:98-101`) only exercises a comma.
  - `CsvManager.kt:166-173` — export prefixes `'` to values starting with `=`, `+`, `-`, `@`, tab, CR; `parseTransactionRow` (`:134-160`) never strips it, so the guard character becomes part of the stored note/merchant.
- **Impact**: The documented matched pair silently corrupts recurring rows (frequency gone → period math wrong), drops multi-line notes, and mangles any note/merchant beginning with a formula character. Re-importing your own backup corrupts it.
- **Effort**: S–M.
- **Risk**: LOW — self-contained parser change with an existing test class to extend.
- **Confidence**: HIGH.
- **Fix sketch**: Parse index 8 into `recurrenceFrequency`; strip the leading `'` on import; replace `forEachLine` with a quote-aware record reader; add a round-trip test with a real newline and a recurring row. (Related: guard on `value.trimStart().firstOrNull()` so a leading-space formula slips through — `SEC-03` in the security pass.)

## 4. Ingest watermark strands all history beyond the 500-message cap

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/data/classification/SmsIngestor.kt:34-52` — `queryBankSms(..., maxMessages = 500)` returns the 500 newest matches; the watermark is then set to `messages.maxOf { it.timestamp } - 1` (`:51-52`).
- **Impact**: On a fresh install (or any gap with >500 bank-like messages) only the 500 newest are enqueued; every older message falls permanently below the `DATE > newest-1` cursor — never ingested, never classified, never surfaced. Historical transactions are silently invisible to auto-detect.
- **Effort**: S.
- **Risk**: LOW — re-scans are ignored by the `OR IGNORE` insert.
- **Confidence**: HIGH.
- **Fix sketch**: Set the watermark to `messages.minOf { it.timestamp } - 1`, or advance to `newest - 1` only when the batch is smaller than the cap. Add a test with 600 messages asserting nothing older than the cursor is skipped on a subsequent pass.

## 5. Background path never records unrecognized money-movement SMS

- **Evidence**:
  - `app/src/main/java/com/awbuilds/auraspend/data/classification/SmsPipelineProcessor.kt:49-51` — `recordUnrecognized` defaults to a no-op lambda.
  - `data/classification/AutoClassificationWorker.kt:101-112` wires the real `unrecognizedSms.record`, but `data/classification/BackgroundClassificationCoordinator.kt:88-104` constructs the processor **without** it (`dao`, `transactionRepository`, `enrich`, `onSaved`, `interMessageDelayMs` only).
- **Impact**: The "amount + movement verb but no parser claims it → `unrecognized_sms`" invariant holds only on the 6-hourly worker path. The coordinator runs whenever the app is backgrounded with the process alive — the dominant in-process path — where such messages are marked SKIPPED and vanish with no UI and no record.
- **Effort**: S.
- **Risk**: LOW.
- **Confidence**: HIGH.
- **Fix sketch**: Extract a `makeProcessor(app, dao)` factory used by both wiring sites, or drop the default so the compiler forces every construction site to decide.

## 6. Deposits/refunds with a balance tail are skipped before parsing

- **Evidence**:
  - `app/src/main/java/com/awbuilds/auraspend/data/classification/SmsAutoClassifier.kt:192-195` — `movementVerb = (?:debited|credited|spent|paid|transferred|withdrawn)`; `looksLikeTransaction` (`:198-199`) requires amount **and** one of those verbs; the rescue at `:207-209` and `:221-224` keeps a message only when both hold.
  - The balance-alert pattern at `:68` matches "Available balance", so a deposit message ("Rs 10,000 has been deposited to A/c XX123. Available balance is Rs 45,200") enters the alert branch and is dropped because "deposited" is not a movement verb.
  - `BankMessageParser.kt:84-86` knows the richer credit lexicon (`deposited`, `received`, `refund`, `cashback`, `reversal`, `salary`). The same 6-verb copy is duplicated at `SmsPipelineProcessor.kt:86-89`.
- **Impact**: Real deposits/refunds/reversals with a balance tail are skipped before the parser that would have read them correctly ever runs — the "loses the user's money silently" failure the OTP/alert invariant exists to prevent, and a violation of the invariant as written ("no amount **and** no movement verb" — the message has an amount and a real movement).
- **Effort**: S.
- **Risk**: LOW-MED — a wider rescue lets marginally more messages through; the fraud hard-vetoes are unaffected.
- **Confidence**: HIGH.
- **Fix sketch**: One shared predicate reusing `BankMessageParser`'s debit/credit lexicons (or ask the parser for amount+type); delete the duplicated copy; add corpus cases "deposit + available balance" and "refund + available balance".

## 7. Duplicate `sourceSmsId` writes REPLACE-delete the user's row

- **Evidence**:
  - `app/src/main/java/com/awbuilds/auraspend/data/local/dao/Daos.kt:99-100` — `insertTransaction` uses `OnConflictStrategy.REPLACE`; `data/local/entities/Entities.kt:10` declares `Index(value = ["sourceSmsId"], unique = true)`.
  - SQLite REPLACE deletes the conflicting row and inserts the new one; the pipeline's terminal-skip branch keys on `e.message?.contains("UNIQUE constraint failed")` (`SmsPipelineProcessor.kt:254-262`) — unreachable with REPLACE — and its test injects that exception by hand (`SmsPipelineProcessorTest.kt:173-186`).
  - Secondary path: `ui/navigation/NavGraph.kt:672-674` filters re-imported CSV rows at similarity ≥ 0.85, then `saveTransactions(fresh)` REPLACE-overwrites an edited row whose edits dropped similarity below the threshold.
- **Impact**: Any second save of the same SMS-linked transaction (worker/triage race, CSV re-import) deletes the existing row — losing user edits (merchant/category/amount) and its id — rather than being ignored.
- **Effort**: S–M.
- **Risk**: MED — must keep the Drive restore clear+reinsert path working.
- **Confidence**: HIGH.
- **Fix sketch**: Make SMS-linked writes insert-if-absent (`INSERT OR IGNORE` + explicit update path) so a duplicate is a no-op; delete or retarget the dead constraint-exception branch.

## 8. `Enum.valueOf` + `!!` on DB strings in the classification collector

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/ui/classification/ClassificationViewModel.kt:373` — `type = TransactionType.valueOf(type!!)`; `:383` — `SmsMessageStatus.valueOf(status)`; both inside `dao.observeAll()...collectLatest` (`:335-349`) which has no catch. Drive restore repopulates these string columns wholesale (`Daos.kt:321-327`), so they are backup-controlled input. `loadAndClassifyAll()` is invoked from three intents (`:101,106,124`), each launching a new never-cancelled full-table collector.
- **Impact**: One row with a bad status/type string throws inside the collector; the SMS inbox silently freezes on its last snapshot with no error surfaced and no retry. Each extra trigger adds another full-table collector re-mapping every row on every DB write. This is the exact bug class handoff 0015 fixed in `Mappers.kt` ("one bad string bricked the dashboard"), violating AGENTS.md hard requirement 2.
- **Effort**: S.
- **Risk**: LOW.
- **Confidence**: HIGH.
- **Fix sketch**: Safe parse (`enumValues<T>().firstOrNull { it.name == raw }`, matching `AppThemeMode.fromName` precedent) mapping unknowns to a skipped/hidden row with a logged warning; hoist the collector into a single job cancelled on re-entry.

## 9. `mayDiscard`'s confidence gate is unreachable for the shipped runtime

- **Evidence**:
  - `app/src/main/java/com/awbuilds/auraspend/data/classification/AiSignalFusion.kt:57` — the veto path is consulted only when `ai != null && !ai.isTransaction`; `mayDiscard` (`:145-147`) then requires `probability >= DISCARD_MIN_CONFIDENCE (0.9)` **and** `base.amount == null`.
  - `data/ai/EmbeddingClassifier.kt:115` reports `isTransactionProbability = pTransaction` where `pTransaction = softmax(...)[1]` is the probability of the *transaction* class, and `isTransaction = pTransaction >= 0.5` (`:105`). Inside the `!isTransaction` branch, `pTransaction < 0.5`, so `>= 0.9` can never hold.
  - `OnDeviceClassifier.kt:29-51` documents the field as "how sure the runtime is that `isTransaction` is right" — i.e. `1 − p` on a negative verdict. AGENTS.md/handoff 0013 call the constraint "load-bearing".
- **Impact**: The destructive-veto canary is dead code in production (safe direction, but untested and misleading); the recorded 0/46 false-veto result is produced by the dead half, not the calibrated half — so the actual safety margin is unmeasured, and the first runtime to "fix" the polarity activates a row-deleting path without an eval re-run.
- **Effort**: S.
- **Risk**: MED — flipping the polarity activates the discard path; must re-run `FusedAiEval` with the shipped runtime before merging.
- **Confidence**: HIGH.
- **Fix sketch**: Report the probability *of the verdict emitted*: `isTransactionProbability = if (isTransaction) pTransaction else 1f - pTransaction`, then re-run the fused eval and confirm the 0-deletion result is now genuinely produced by the gate.

## 10. Drive sign-in result is never inspected — the check is a tautology

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/data/remote/DriveSyncManager.kt:39-47` — `val account = GoogleSignIn.getSignedInAccountFromIntent(data); account != null`. `getSignedInAccountFromIntent` returns a `Task<GoogleSignInAccount>`, which is never null; success/failure is never observed (no `.result`, no listener, no await), and a failed sign-in produces a failed Task, not a thrown exception — so the `catch` at `:43` cannot see it either. Both call sites treat the return as an established session: `ui/navigation/NavGraph.kt:501` (onboarding restore) and `:621` (Settings backup/restore). The Kotlin compiler independently flags it: `w: DriveSyncManager.kt:42:13 Condition is always 'true'`.
- **Impact**: A cancelled, denied or failed sign-in is reported as "signed in"; the app proceeds to `getDriveService()` which returns null (`:95`) and fails closed — so the user sees "backup failed"/"no backup found" instead of "sign-in failed", and the one authentication check on the egress path validates nothing.
- **Effort**: S.
- **Risk**: LOW — failure already degrades safely; only the reported reason and the code's honesty change.
- **Confidence**: HIGH.
- **Fix sketch**: Await the task (`Tasks.await` or `kotlinx-coroutines-play-services`) and return true only on success, mapping the `ApiException` status code to the existing sign-in-failed string. While there: replace raw `android.util.Log` (`:44,60,67,71,86,89`) with `AuraLog`, rethrow `CancellationException` in the three `catch (e: Exception)` blocks (`:43,70,88`), and consider `setParents(listOf("appDataFolder"))` + matching query so the unencrypted backup isn't a visible, sync-everywhere file in the Drive root.

## 11. Restore re-introduces unmasked SMS bodies through the "choke-point" it documents

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/data/local/BackupRestoreManager.kt:24-25` — the class doc claims "Transaction rows are re-masked with `SensitiveDataMasker` on the way in, which keeps restore behind the same privacy choke-point as every other write path." The code masks only transactions (`:49`, `:84-87`); `:60` inserts `backup.smsMessages` and `:64` inserts `backup.unrecognizedSms` verbatim. `PrivacyBackfill.kt:29-32` is the only thing that cleans `sms_messages.body`, and it runs once at startup (`AuraSpendApp.kt:121-124`).
- **Impact**: Restoring a v1–v3 backup (produced before `SensitiveDataMasker` existed) writes raw bank message bodies into the DB and — because restore is a *replace* — overwrites the locally-masked rows with the unmasked ones. Those raw bodies persist until the next process start and are re-included in the next Drive backup. The documented invariant is false exactly on the path most likely to carry legacy content.
- **Effort**: S.
- **Risk**: LOW — masking is idempotent.
- **Confidence**: HIGH.
- **Fix sketch**: Route both lists through `SensitiveDataMasker.mask` on the restore path, mirroring `Transaction.masked()`; add a test with an unmasked legacy backup.

## 12. The 30-day retention sweep is dead code

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/data/local/UnrecognizedSmsRepository.kt:48-56` — `purgeOlderThan(30)` implements the documented policy ("so the list stays a recent-questions list"); the query exists at `data/local/dao/Daos.kt:287-289`. Repo-wide search: **zero production callers** (worker calls only `record`, screen calls only `observeAll`/`delete`); `observeCount()` (`:22`) is likewise uncalled. `sms_messages` has no retention path at all (`Daos.kt` exposes only `clear()`).
- **Impact**: The table accumulates every unrecognized bank SMS forever, contradicting its own documented retention, and every Drive backup snapshot and restore carries the growing list. For a two-year auto-detect user this is a permanent, unencrypted archive of bank messages. Handoff 0025 flagged this as an open risk; it is still true.
- **Effort**: S (purge) / M (add a `sms_messages` sweep for terminal rows).
- **Risk**: LOW — pending rows must be exempt; deleting a terminal queue row does not affect any transaction.
- **Confidence**: HIGH.
- **Fix sketch**: Call `purgeOlderThan()` on the existing periodic worker tick; add a test asserting the cutoff math and that pending rows survive.

## 13. Privacy copy overclaims against the backup the code actually produces

- **Evidence**:
  - `app/src/main/res/values/strings.xml:206` — `settings_ai_ready_subtitle`: "Messages are categorised on-device. Nothing leaves your phone." (Settings AI section, two sections above the Data card).
  - `strings.xml:298` — onboarding: "…by a local AI that never uploads your data."
  - Against: `data/local/BackupCreateManager.kt:40,42` — the Drive payload includes `smsMessages` (every `SmsMessageEntity.body` — masked, but text) and `unrecognizedSms`, uploaded unencrypted by `DriveSyncManager.kt:66`.
  - `docs/privacy.html:141` — the "Contents" list enumerates seven tables and omits `unrecognizedSms`, which v4 added (`BackupSerializer.kt:24,28,45`), so the page contradicts itself and the disclosure is one table short of the format.
- **Impact**: The promise that justifies the `READ_SMS` grant and the store listing is stated absolutely while the shipped egress path does carry message text on a single user-initiated tap. Low-severity technically, high-visibility reputationally — the only reason it stands is that nobody re-read the copy after `unrecognizedSms` was added to the payload.
- **Effort**: S (EN + HI twins + one doc bullet).
- **Risk**: LOW — string-only change; preserve key parity.
- **Confidence**: HIGH.
- **Fix sketch**: Qualify the claims to what is true ("categorised on-device; the only upload is the Drive backup you start yourself") and add `unrecognizedSms` to the privacy page contents list. Consider a grep-based check next to `tools/check_license.sh` that fails when an absolute-claim phrase appears without a documented exception.

## 14. "Classify by sender first" is unimplemented

- **Evidence**: `data/classification/bank/BankParserRegistry.kt:29-33` — `parserFor(sender)` has zero callers in `src/main` (only class/interface references and `BankParserRegistryTest.kt:73-106`). `TransactionClassifier.classify` takes only a body (`TransactionClassifier.kt:11-13`); `SmsPipelineProcessor.kt:199` calls it with `message.body` and ignores `message.address`; the manual path is body-only too. The corpus's `sender` column is loaded but consumed only by the registry test. Handoff 0013 §7.3 documents the intended fall-through flow the code does not implement.
- **Impact**: A stated AGENTS.md invariant is unimplemented; three parsers plus the registry are dead code in production; the registry test creates false assurance that routing is measured; sender metadata is carried through the queue yet never used for routing.
- **Effort**: M.
- **Risk**: MED — changes parse output for every Axis/Canara/SBI message; must be re-gated by `RegexBaselineEvalTest` and `FusedAiEval`.
- **Confidence**: HIGH.
- **Fix sketch**: `BankParserRegistry.parserFor(sender)?.extract(body)` first in `TransactionClassifier.classify(sender, body)`, falling back to `BankMessageParser.parse` when the per-bank parser returns null amount/type; pass `message.address` at `SmsPipelineProcessor.kt:199` and the sender in the use case/ViewModel.

## 15. Unguarded repository writes and reads in composables

- **Evidence**:
  - Writes: `ui/savings/SavingsGoalsScreen.kt:182,194,210` (`saveSavingsGoal` ×2, `deleteSavingsGoal`); `ui/recurring/RecurringScreen.kt:222` (`deleteSubscription` — destructive, unconfirmed, no `AlertDialog` unlike every other destructive action) and `:397-407` (`saveSubscription` then `showAddSheet = false` unconditionally); `ui/transaction/NewTransactionScreen.kt:69`; `ui/transaction/TransactionDetailScreen.kt:130,149,179`; `ui/navigation/NavGraph.kt:432,435,451,676`.
  - Reads: `ui/transaction/TransactionDetailScreen.kt:68-75` — `repository.getAllTransactions().first()` to find one row by id (an indexed `getById` exists and is used elsewhere) plus `getAllCategories().first()`, no try/catch; `ui/transaction/NewTransactionScreen.kt:31-33`; `ui/budget/BudgetViewModel.kt:41-45,80-86`.
- **Impact**: AGENTS.md hard requirement 1 says every IO boundary catches. On the write paths a Room failure propagates out of `rememberCoroutineScope().launch` and crashes, or the sheet closes as if the save succeeded and the edit is lost. On the read paths a SQLite failure takes the process down instead of rendering `AuraErrorState` (those states have no error field for these paths).
- **Effort**: M.
- **Risk**: MED — callers must decide between reverting optimistic state and keeping the sheet open.
- **Confidence**: HIGH.
- **Fix sketch**: Wrap each call in `boundary(TAG, Unit) { … }`; on failure keep the sheet/dialog open and surface `AuraErrorBanner`; wrap reads in `boundaryOrNull` → `error = UiError.LOAD_FAILED` → `AuraErrorState`; swap the detail screen's full-table scan for a single-row DAO query; add the missing confirmation dialog to the Recurring delete.

## 16. Hardcoded `₹` next to a working currency picker

- **Evidence** (verified occurrences):
  - `ui/transaction/TransactionEditor.kt:83`, `ui/budget/BudgetScreen.kt:387` (+ decorative `Icons.Default.CurrencyRupee` `:391`), `ui/savings/SavingsGoalsScreen.kt:402,497`, `ui/recurring/RecurringScreen.kt:305` (+ `:309`), `ui/classification/ClassificationReviewTab.kt:534` — `prefix = { Text("₹") }` on amount fields.
  - `ui/classification/ClassificationSmsTab.kt:458` — `Text("₹0.00")` (hardcoded symbol *and* format); `ui/transaction/QuickAddSheet.kt:115` — `"₹0"`; `ui/classification/HierarchicalCategorySelector.kt:234` — `"₹${String.format("%.2f", transaction.amount)}"` (hand-rolled money formatting).
  - `data/classification/DuplicateDetector.kt:121` — `reasons.add("Same amount: ₹${t1.amount}")` builds currency-formatted text in the data layer (field currently unrendered, so latent).
  - `ui/designsystem/Money.kt:16-22` documents the invariant this violates ("never hardcode a currency symbol or a locale" — the `₹`/`Locale.US` era is the bug the currency layer exists to kill).
- **Impact**: A user who set EUR/USD in Settings sees `€4,200` on every summary screen and `₹` typed into every field they edit and every zero-amount row — the app contradicts itself in the same viewport. `MoneyConfig.current` / `LocalCurrencyStyle.current` are available at all call sites.
- **Effort**: M.
- **Risk**: LOW/MED — risk of doubling the symbol where the prefix is added to already-formatted `formatMoney` output; needs a one-line check per site.
- **Confidence**: HIGH.
- **Fix sketch**: Use the theme currency for every prefix; replace the two literal placeholders with `formatMoney(0.0)`; drop or currency-key the `CurrencyRupee` icons; return a structured reason (or formatted amount) from `DuplicateDetector`.

## 17. Nav bar labels and content descriptions are hardcoded English

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/ui/core/AuraSpendScaffold.kt:47-50` — `HOME("home", "Home", …)`, `ACTIVITY(…, "Activity", …)`, `PLAN(…, "Plan", …)`, `INSIGHTS(…, "Insights", …)`; rendered as text (`:93`, `:262`) and as the accessibility label (`:92`, `:258`); `:207` — `contentDescription = "Add transaction"`; `ui/designsystem/SettingsAvatar.kt:39` — `contentDescription = "Settings"`. `tools/check_license.sh:155-167` only compares keys that exist in `values/strings.xml`, so literals are invisible to it.
- **Impact**: The entire app chrome — the persistent bottom bar on every top-level tab, its screen-reader labels, and the primary add action — stays English in the Hindi locale, while AGENTS.md advertises EN/hi parity. This is the most-seen UI surface in the app.
- **Effort**: S.
- **Risk**: LOW.
- **Confidence**: HIGH.
- **Fix sketch**: Change the field to `labelRes: Int` + `stringResource` (reuse `activity_title`/`plan_title`; add `home_title`/`insights_title`/`action_add_transaction` in `values/` **and** `values-hi/`); extend the i18n script to check keys referenced in Kotlin exist in both locales.

## 18. `formatRelativeDate` hardcodes English date words inside the shared design system

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/ui/designsystem/TransactionRow.kt:38-46` — returns `"Today"`, `"Yesterday"`, `"${diff / day}d ago"`; consumed at `:112-116` by `TransactionEntryRow`, used by `DashboardScreen.kt:383` and `TransactionListScreen.kt:362` — the two highest-traffic lists. `R.string.activity_group_today`/`activity_group_yesterday` already exist in both locales. `DesignSystemGuardTest` scans only fontSize/hex/shadow/emoji, so a hardcoded literal in `ui/designsystem` is unchecked.
- **Impact**: Every transaction row in Home and Activity reads English regardless of locale; the fix propagates to both lists at once since it lives in the design system.
- **Effort**: S.
- **Risk**: LOW — the function becomes `@Composable` or takes the two strings as parameters; the only caller is in a composable scope.
- **Confidence**: HIGH.
- **Fix sketch**: Resolve `activity_group_today`/`activity_group_yesterday` plus a new plural (`%d d ago`) from resources, keeping the `SimpleDateFormat` fallback.

## 19. The shared segmented control fixes its height while holding text

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/ui/designsystem/AuraSegmentedControl.kt:55` — `BoxWithConstraints(… .height(44.dp))` contains the option `Text` (`:101-108`). No guard check covers `height(` on a text container (only `fontSize`); siblings use `heightIn(min = …)` (`AuraStatTile.kt:153`, `SettingsScreen.kt:462`, `UnrecognizedSmsScreen.kt:162`). Used by `AnalyticsScreen.kt:167`, `ClassificationScreen.kt:133`, `TransactionListScreen.kt:229`, `QuickAddSheet.kt:100`, `TransactionEditor.kt:68`.
- **Impact**: At the largest system font scale, `bodyMedium` (~28sp) exceeds the 44dp box and segment labels clip vertically on five screens at once — the exact property AGENTS.md rule 5 names.
- **Effort**: S.
- **Risk**: LOW — `heightIn(min = 44.dp)` keeps the current appearance at 100%; the animation math is unaffected.
- **Confidence**: HIGH.
- **Fix sketch**: Replace `.height(44.dp)` with `.heightIn(min = 44.dp)` and add a `heightIn`-on-text assertion to `DesignSystemGuardTest`.

## 20. Two interactive controls are 40dp — below the 48dp floor

- **Evidence**: `ui/designsystem/SettingsAvatar.kt:28-35` — `Box(… .size(40.dp) … .clickable(onClick))` with no `Role` and no minimum-interactive sizing; the KDoc calls it "the single entry point to Settings on every top-level tab" (used at `DashboardScreen.kt:94`, `TransactionListScreen.kt:191`, `AnalyticsScreen.kt:165`, `PlanHubScreen.kt:97`). `ui/classification/ClassificationSmsTab.kt:492,505` — `FilledTonalButton`/`TextButton` with `Modifier.size(40.dp)` (the triage save/dismiss buttons). Nearby code uses 48dp (`AuraSpendScaffold.kt:243`, `State.kt:272`, `UnrecognizedSmsScreen.kt:151,162`).
- **Impact**: Below the AGENTS.md ≥48dp touch-target rule; on four tabs this is the *only* way to reach Settings.
- **Effort**: S.
- **Risk**: LOW/MED — enlarging changes the header row's width budget; the triage row may need a height pass on-device.
- **Confidence**: HIGH.
- **Fix sketch**: `Modifier.sizeIn(min = 48.dp)` with padding outside the visible art; raise the triage buttons to 48dp.

## 21. Enum names, English taxonomy literals, and splash strings reach the screen

- **Evidence**:
  - Enum rendering: `ui/classification/ClassificationReviewTab.kt:316` (`parsed.type?.name?.lowercase()`), `ui/classification/ClassificationSmsTab.kt:464,471`, `ui/budget/BudgetScreen.kt:247,414` (`budget.period.name`), `ui/recurring/RecurringScreen.kt:185,332` (`sub.billingCycle.name`).
  - Splash: `ui/splash/SplashScreen.kt:108` (`Text("AuraSpend")`), `:115` (`Text("Beautiful expense management")` — no string resource exists in either locale, and the parity script only diffs two XML files).
  - Taxonomy: `ui/classification/HierarchicalCategorySelector.kt:174-186` — `mapHierarchicalToLocalId` matches **English** parent-category names ("Food", "Transport", …) to ids, while `:108,:134` render the raw English strings as dialog body.
- **Impact**: Every period, billing cycle and transaction type is an untranslated English word in all locales; the hierarchical selector is one taxonomy rename away from filing every transaction into `cat_other`.
- **Effort**: S–M.
- **Risk**: LOW — enum→string-resource mapping is mechanical; the taxonomy change must preserve current ids so existing rows keep their categories.
- **Confidence**: HIGH.
- **Fix sketch**: Add `budget_period_*`, `recurring_cycle_*`, `txn_type_*`, `splash_tagline` to both locales; replace the English `contains` matching with a stable key on `hierarchicalCategoryMap` entries.

## 22. Budget spend is computed three divergent ways; two full-table loads

- **Evidence**: `ui/budget/BudgetViewModel.kt:41-48` — loads all budgets, all categories **and all transactions**, then `BudgetSpending.withFreshSpent(...)`. `ui/plan/PlanHubScreen.kt:55-68` — same helper over a live `repository.getAllTransactions().collectAsState()` (its own comment recounts the 0015 "Plan ₹0 vs Dashboard real spend" bug). `ui/home/DashboardViewModel.kt:63-68,112-119` — the ADR-compliant indexed-aggregate path. `docs/adr/0003-sql-aggregates-and-indices.md:23-30` restricts `withFreshSpent` to small tables/tests and says new screens should use the aggregates. Two more full-table observers: `NavGraph.kt:247` (Insights) and `:559` (Settings/CSV export).
- **Impact**: The two most-visited tabs full-table-load on every entry and recomposition — exactly what ADR 0003 was written to stop — and three implementations of one number is the 0015 failure waiting to recur.
- **Effort**: M.
- **Risk**: MED — money math; needs a regression test that Budget/Plan/Dashboard agree before and after.
- **Confidence**: HIGH.
- **Fix sketch**: Add/consume a repository `observeSpentByCategory(period)` aggregate used by Budget and Plan; retire `withFreshSpent` from production; keep it for tests as ADR 0003 prescribes; move Settings' CSV read into the export coroutine.

## 23. Dead code with live tests, CI entries, and a dangerous UI branch

- **Evidence**:
  - `ui/classification/ClassificationViewModel.kt:187-194` — `checkForDuplicates()` has an empty body ("just proceed"); `:304-312` — `checkPotentialDuplicate` unconditionally `return null`, so the guard at `:279-291` can never fire; the fully wired intents `DuplicateDetected/IgnoreDuplicate/MarkAsDuplicate` (`:158,:175-183`) and `DuplicateDetectionDialog` (`HierarchicalCategorySelector.kt:194-264`) are unreachable — and `MarkAsDuplicate` sets `saveSuccess = true`, which `ClassificationScreen.kt:99-103` turns into `onBack()`: if it ever became reachable it would dismiss the editor **without saving the transaction**.
  - `data/classification/MessageDedup.kt` — 68-line object, zero callers anywhere; `app/lint-baseline.xml` still carries three stale issues attributed to it.
  - `ui/transaction/TransactionListScreen.kt:561-604` — `groupTransactionsByDate`/`DateGroup` has no production caller (the list uses paging + sticky headers), yet `TransactionGroupingTest.kt` still exercises it.
  - Also dead: `ClassificationScreen.kt:82,:295-307` (`showCategoryDialog` never set true), a shadowed notification launcher at `:91` vs `:177-179`, and `domain/repository/TransactionRepository.kt:27,29` (`searchTransactions`, `getRecurringTransactions` — no production callers).
- **Impact**: Readers cannot tell live from dead; the duplicate-protection feature reads as shipped but silently cannot trigger; a green test measures an orphan.
- **Effort**: S–M.
- **Risk**: LOW to delete; MED to "complete" (duplicate protection needs a repository read the ViewModel doesn't have).
- **Confidence**: HIGH.
- **Fix sketch**: Delete the stubs, unreachable dialog branch, `MessageDedup`, the dead grouping code + its test, the two dead repository methods — or implement duplicate protection deliberately (see Direction 1).

## 24. Duplicate `"mart"` key and two divergent keyword maps

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/data/classification/CategoryKeywordMap.kt:52` — `"mart" to "cat_grocery"` under a comment ("Groceries — before Shopping, so 'bigbasket' is never read as retail"); `:64` — `"mart" to "cat_shopping"`. `linkedMapOf` keeps the last value, so "mart" resolves to Shopping and the ordering comment does not hold. Divergence: `DefaultCategories.kt:201-210` maps netflix/spotify/prime video/hotstar → `cat_entertainment` while `CategoryKeywordMap.kt:81-89` maps them → `cat_subscription`; the fusion map has an income table the parser map lacks.
- **Impact**: Every "X Mart"-style merchant ("Reliance Mart", "Vijay Mart"…) is filed as Shopping instead of Grocery with no test pinning either value; which category a transaction gets depends on which map answers for a given merchant.
- **Effort**: S (key) / M (unify maps).
- **Risk**: MED for unification — category assignments shift during consolidation.
- **Confidence**: HIGH.
- **Fix sketch**: Delete the duplicate key (keep the intended one) and add a test/lint failing on duplicate keys; longer term merge the two maps with per-type tables and a parity test.

## 25. The most dangerous operations have the least test coverage

- **Evidence**:
  - Zero tests reference `BackupRestoreManager` (grep `app/src/test`), while `BackupRestoreManager.kt:37-87` clears 8 tables and re-inserts inside one transaction. The create direction is tested; restore — the only operation that can delete every user row — is enforced by code review alone.
  - Only one test touches real Room (`BackupCreateManagerTest.kt:44`); every other test uses `TestTransactionRepositoryDefaults.kt`, which re-implements the aggregates in memory. Production SQL is untested: `Daos.kt:64-74` (SUM/CASE), `:79-87` (GROUP BY category), `:89-97` (`GROUP BY date(…,'unixepoch','localtime')`), `:25-42` (paged LIKE filter).
  - No test references any ViewModel (963 lines of state logic), and `kotlinx-coroutines-test` appears nowhere in `app/build.gradle.kts`, so `Dispatchers.setMain` is unavailable.
  - `LocalLlmProvider`'s degradation property ("swap the runtime, degrade silently, keep working on regex") is covered only by `R8ReleaseSmokeTest.kt` (androidTest, never run in CI); the `overrideForTest` seam (`LocalLlmProvider.kt:67`) has zero callers.
  - Untested: `SmsIngestor` (watermark boundary rule), `MessageDedup`, `SmsAiEnricher`.
- **Impact**: A wrong GROUP BY, a LIKE-escaping bug, or a shifted range boundary passes the whole suite while dashboard/chart/filters are wrong on-device; the 0015 class of bug (state populated then dropped) can recur in the wiring layer nothing exercises; a backup/restore regression is silent data loss discovered only in the field.
- **Effort**: M.
- **Risk**: LOW — new tests only; if they fail, they found a real bug.
- **Confidence**: HIGH.
- **Fix sketch**: `BackupRestoreManagerTest` via the proven in-memory-Room + Robolectric pattern (full replace, atomic failure leaves DB untouched, masked note, SMS status preserved); a `TransactionDaoTest` pinning aggregates/range inclusivity/local-day bucketing under a fixed timezone; `kotlinx-coroutines-test` + a `MainDispatcherRule` and intent-level ViewModel tests; JVM tests for `LocalLlmProvider` via `overrideForTest`.

## 26. R8 and native-runtime audit gates are manual

- **Evidence**: `tools/verify_r8_release.sh` and `tools/audit_native_runtime.sh` exist and are documented as mandatory (AGENTS.md: "Re-run … after touching dependencies or keep rules"; "must be re-run on every version bump"), but no workflow invokes either — `ci.yml`, `pr_check.yml`, `release.yml` call only `check_license.sh`. `dependabot.yml` opens weekly Gradle-dep PRs; `libs.versions.toml` pins `onnxruntime`.
- **Impact**: CI builds `assembleProdRelease` green while the checks that answer "will Play reject this bundle / will JNI_OnLoad resolve / can the .so phone home" depend on memory. An ONNX version bump lands with a stale audit pass.
- **Effort**: M.
- **Risk**: MED — adds minutes to PR CI and a new red-build source; needs the script exit codes verified on the runner image.
- **Confidence**: HIGH.
- **Fix sketch**: Add a "Release gates" step to `pr_check.yml` running `tools/verify_r8_release.sh prod`, plus a step that extracts the arm64 `.so` from the Gradle cache and pipes it through `tools/audit_native_runtime.sh`; scope with a paths filter on build files.

## 27. Every code PR runs two full CI pipelines

- **Evidence**: `.github/workflows/ci.yml:3-7` triggers on all `pull_request` to main/dev with **no paths filter**; `.github/workflows/pr_check.yml:3-17` triggers on the same events **with** a paths filter. Both run `testProdDebugUnitTest` (`ci.yml:60`, `pr_check.yml:63`), both `assembleDevDebug` + `assembleProdDebug`; pr_check additionally builds the R8 release and `:benchmark`.
- **Impact**: A PR touching Kotlin pays double for tests plus 2–4 duplicate assembles (one of them full R8) for zero added signal.
- **Effort**: S.
- **Risk**: LOW — verify the union still covers docs-only PRs (the license check runs in ci.yml today).
- **Confidence**: HIGH.
- **Fix sketch**: Give `ci.yml` the same `paths:` filter, or convert `pr_check.yml` into the single PR gate and make `ci.yml` push-only.

## 28. The app attributes a "bundled" Lottie that is not in the build

- **Evidence**: `ui/settings/LicenseScreen.kt:91-96` lists Lottie with `role = ThirdPartyRole.BUNDLED` on the user-facing Settings → About screen — contradicting its own KDoc (`:60-68`): "an out-of-date attribution is a licence-compliance bug". `lottie-compose` is absent from `app/build.gradle.kts` (declared in `libs.versions.toml:28` with the comment "Not yet on the classpath"); zero `com.airbnb.lottie` imports in `app/src`. Supporting claims: `README.md:350,395`, `docs/licenses/README.md:16`, `docs/adr/0009:67` — and `tools/check_license.sh:192` **fails CI if the row is removed**, i.e. the gate cements the false attribution.
- **Impact**: A shipped AGPL §4 attribution screen makes a factually false claim about what the app bundles, and the license gate blocks the one-line fix.
- **Effort**: S.
- **Risk**: LOW — but pick deliberately: remove the row *or* wire the dependency.
- **Confidence**: HIGH.
- **Fix sketch**: Remove the `Lottie` row from `LicenseScreen.THIRD_PARTY`, the `check_attribution "Lottie"` line, and the README mentions — or add and use `lottie-compose`. Note whether onboarding/splash animations are Canvas-based so the claim matches code.

## 29. Docs drift: stale commands, a deleted shim, a dead download button

- **Evidence**:
  - `README.md:274` tells contributors to run `./gradlew connectedAndroidTest` — with product flavors that task does not exist (it's `connectedDevDebugAndroidTest`). `R8ReleaseSmokeTest.kt:29,41` still says `connectedFreeDebugAndroidTest` / `testFreeDebugUnitTest`; `CONTRIBUTING.md:21` forbids features in `app/src/play/` (deleted in 0018); `CONTRIBUTING.md:47-48` instructs branching from `main`, contradicting AGENTS.md rule 7 (branches off `dev`).
  - `AGENTS.md` (the file every session must read first) still instructs migrating and deleting "Legacy `ui/core/CashewComponents.kt`" — a file deleted in handoff 0005; `memory/glossary.md:12` still defines it as an active layer.
  - `docs/index.html:91-93,254-256` — "Download APK" points at the GitHub releases page, which live-verified 2026-10-10 has **zero releases**; `README.md:371-372` duplicates the claim and `:149-151` claims Google Play distribution while the listing is staged-not-submitted.
- **Impact**: A first-time contributor copy-pastes a documented command and gets "task not found"; every agent session is injected a false premise about a file that no longer exists; the site's primary CTA sends users to an empty page.
- **Effort**: S.
- **Risk**: LOW.
- **Confidence**: HIGH.
- **Fix sketch**: Update the four stale command/path references; delete the CashewComponents paragraph from AGENTS.md and the glossary row; until a release exists, make the download buttons say so (or link the source-build section) and trim README to "planned"; flip all copy in one commit when the release ships.

## 30. Latent: migration chain starts at v3 with destructive fallback disabled

- **Evidence**: `app/src/main/java/com/awbuilds/auraspend/data/local/AppDatabase.kt:169-173` — `addMigrations(MIGRATION_3_4 … MIGRATION_7_8)` then `fallbackToDestructiveMigration(false)`; `:23-24` — `version = 8`, `exportSchema = false` (blocks `MigrationTestHelper`). No 1→2 or 2→3 migration exists. Handoff 0012 already records "no DAO or migration test exists… the v7→v8 migration is unverified against a real v7 database".
- **Impact**: If any install anywhere has a database at v1 or v2 (the migration docs start at "v3 → v4", implying earlier versions existed), Room throws on first database access — an unrecoverable startup crash loop with no in-app recovery.
- **Effort**: S.
- **Risk**: LOW.
- **Confidence**: MED — the code fact is certain; whether pre-v3 installs exist needs release-history verification.
- **Fix sketch**: Confirm which schema version 0.1.0 shipped; if pre-v3 is possible, add the missing migrations (or a deliberate destructive fallback for versions < 3); re-enable `exportSchema` so migration tests become possible.

## 31. `profileable android:shell="true"` ships in the release manifest

- **Evidence**: `app/src/main/AndroidManifest.xml:21-22` — `<profileable android:shell="true" />` with the comment "Macrobenchmark / baseline profile generation needs a profileable app." Both flavor manifests are empty (`app/src/prod/AndroidManifest.xml`, `app/src/dev/AndroidManifest.xml`), so nothing removes the tag for release.
- **Impact**: `profileable shell=true` lets the shell (adb) user attach a profiler and take a heap dump of a live process; on a device with USB debugging enabled, the decrypted finance DB and plaintext backup JSON are readable out of process memory.
- **Effort**: S.
- **Risk**: MED — `:benchmark` links prod release (`benchmark/build.gradle.kts:8-9`), so removing the tag may break local baseline-profile generation; verify before merging.
- **Confidence**: MED (HIGH that it ships; MED that it is removable without cost).
- **Fix sketch**: Move the tag into a debug/benchmark-only source set (or `tools:node="remove"` in a release manifest); re-run baseline-profile generation once to confirm `:benchmark` still works.

## 32. Untriaged deprecations and dead dependency declarations

- **Evidence**:
  - `data/remote/DriveSyncManager.kt:28,35,37,41` uses `GoogleSignIn`/`GoogleSignInClient`/`GoogleSignInOptions` — deprecated, on the Drive critical path; handoffs 0023–0025 list the warnings and move on, without a fix, a suppression with a written reason, or a recorded safety argument. AGENTS.md hard requirement 8 demands one of the three.
  - `app/build.gradle.kts:215` — `libs.guava` declared with zero `com.google.common` imports in `app/src` (while `LicenseScreen.kt:110` and README call it "Transitive" — it is direct); `:239-240` — `espresso-core` declared; the two androidTest files import no Espresso.
  - `gradle/libs.versions.toml:27-29` — comment "Not yet on the classpath" is false for paging (wired at `build.gradle.kts:233-234` and used in `TransactionRepositoryImpl`, `Daos.kt`, `TransactionListScreen`); `memory/context/tech-stack.md:25` repeats it.
- **Impact**: Every user-facing sync/backup feature depends on an API Google has moved to Credential Manager, and the deprecations have become institutionalized scroll-past noise — a removal lands with zero compiler signal. Dead classpath weight plus a catalog comment that actively misinforms the next dependency audit.
- **Effort**: S (deps/comment) / M–L (Credential Manager migration).
- **Risk**: LOW-MED — the OAuth client and `drive.file` scope must be preserved.
- **Confidence**: HIGH (deps) / MED (removal timeline for GoogleSignIn could not be verified offline).
- **Fix sketch**: Decide explicitly — migrate to Credential Manager now, or pin `play-services-auth` with a written expiry — and record it (ADR or handoff line); drop the unused declarations; correct the catalog comment and memory entry.

---

## Considered and rejected (recorded so they aren't re-audited)

- **Unencrypted Drive backup and the `drive.file` scope** — documented tradeoff (`docs/privacy.html`); only the copy overclaims (#13) and the placement/disclosure gaps (#10) are findings.
- **No analytics/telemetry; dev/prod flavor parity (ADR 0010); no paywall (ADR 0007); AGPL-3.0 licensing positions** — settled.
- **Removed screenshot tests and CI drift gate (handoff 0024)** — owner decision; visual verification is by hand.
- **The 78-issue lint baseline as a whole** — working as intended; only three stale `ModelDownloadManager` `MissingPermission` rows (guarded by `canNotify()` since) are worth a regenerate-and-diff.
- **`material-icons-extended` size** — used at 100+ sites and R8-shrunk.
- **`android:statusBarColor=transparent`**, god-file length per se, `Icons.Default` usage — no behavioral cost.

## Direction (options for the maintainer, not defects)

1. **Finish duplicate detection in Smart Add** — stubs, intents, dialog, and a `DuplicateDetector` trusted on the CSV path all exist; only the wiring is missing. Manually re-adding an expense auto-saved from the same SMS silently double-books the ledger, while the rarer CSV path is protected. S–M.
2. **Derive subscriptions from transaction history** — `getRecurringTransactions()` exists with zero callers; `isRecurring` is populated by the classifier; the false-positive guardrails (`SemanticDuplicateGuardTest`) are in the suite. Serves cash/UPI users the Plan hub implicitly promises. M.
3. **Budget-threshold and bill-reminder notifications** — channels, POST_NOTIFICATIONS flow, the periodic worker, and per-period aggregates all exist; ROADMAP lists it as deliberately unbuilt with the no-server constraint already honored. M, opt-in.

## Suggested execution order

Independently shippable, ordered by leverage: **1 → 2 → 3 → 4 → 5/6 (shared-predicate family) → 7/8 (write-path safety family) → 12 → 11 → 13**. Findings 14, 22, 23 are the structural changes that want their own characterization tests first (see 25).
