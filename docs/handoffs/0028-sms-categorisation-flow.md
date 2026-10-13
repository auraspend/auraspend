# 0028 — SMS categorisation: one-by-one, visible status, honest notifications

**Status:** done 2026-10-11, committed-ready (uncommitted by default — commit only on
request). Branch `feat/sms-categorisation-flow` off `dev`.

The owner's ask: *"make sure the OTPs are skipped, messages should be categorized one by
one in the background and the status shown in the background. Right now when I close the
app it processes messages from two months ago and shows the notification. Make sure the
flow is correct. If I missed anything, think from your side."* This session implemented
the plan in `.kilo/plans/1791732706005-sms-categorisation-flow.md`, scored 4 design
decisions with the owner first (all four recorded under *Decisions*).

The queue pipeline itself was already sound. What was broken was everything around it —
the status surface, the notification model, and the lifespan of the background loop.

## What the request uncovered

- **The notification was the noise, and it had no summary.** `AutoClassificationWorker`
  posted "N new transactions saved" (hardcoded English, notification id 1001) on *every*
  run with `saved > 0`. With a 2-month backfill that is one notification per worker run —
  every 6 hours, plus one on every Classification-screen entry (the ViewModel called
  `runNow` on tab entry) and one on consent. There was no "here is where the drain stands"
  and no single "done" line.
- **Live status was structurally impossible.** Nothing wrote an in-flight status while
  classifying; `SmsStatus.CLASSIFYING` existed in the mapper only as the target of the two
  *undo* paths, so no row was ever "being classified". `aiProgressCurrent` /
  `aiProgressTotal` were **never assigned** — the determinate progress bars in the triage
  and SMS tabs were unreachable code — and `isBatchClassifying` was flipped false inside
  the collector that was started right after it was set true, so the spinner flashed.
- **Opening the app froze the queue.** `AuraSpendApp.onStart` called
  `BackgroundClassificationCoordinator.stop()`, by design ("zero LLM work while an
  activity is visible"). A user who opened the Classification screen therefore watched a
  static list; the drain only resumed after they put the app away. "Show me the status"
  and "work only when I'm not looking" cannot both be true.
- **OTPs were skipped one step too late.** `isOtpOrAlertMessage` correctly skipped them in
  the processor (tested, and the AGENTS.md rescue semantics are preserved), but they were
  still *ingested* into the queue first: they inflated any progress denominator, appeared
  in the SMS tab's pick-list, and each burned a pipeline slot.
- **Closing the app killed the drain.** The coordinator is in-process; only the 6-hourly
  worker (12 messages/run) survived a swipe, so a backlog took days.
- **Two dead-code bugs found while tracing:** `saveAllClassified` filtered
  `status == SmsStatus.CLASSIFIED`, a status the mapper can never emit (NEW/PROCESSED →
  PENDING, CLASSIFYING → CLASSIFYING, terminal → itself), so **"Save All" saved nothing**;
  and a row left `CLASSIFYING` by a killed pass was invisible to `getPending(NEW, 1)`
  forever.

## What shipped

**Queue statuses (the status channel).** New `SmsMessageStatus.CLASSIFYING` = in-flight;
`PROCESSED` keeps its real meaning (where both undo paths park a row) and now maps to
`SmsStatus.PENDING` — it used to map to `CLASSIFYING`, so an un-dismissed message looked
like it was being processed. A pass claims a row (writes `CLASSIFYING`) *before* the
expensive classify+enrich work and sweeps stale `CLASSIFYING` rows back to `NEW` at pass
start (`SmsMessageDao.sweepInFlight`), so a killed pass strands nothing. **The sweep must
not touch `PROCESSED`** — re-processing a parked row would silently resurrect the
undo-dismiss / undo-save the user just made; that is pinned by test.

**OTP/alerts dropped at ingest.** New pure `isIngestable` is the *negation of the same
`SmsAutoClassifier.isOtpOrAlertMessage` predicate the processor uses*, so the two filters
cannot drift (the amount+movement-verb rescue and the unrescued fraud veto are unchanged).
The processor keeps the check as defence in depth. The queue now holds only candidates.

**One notification, with a summary.** New `SmsProgressNotifier` (data/classification) is
the only place this feature posts notifications:
- while a backlog drains: ONE ongoing, silent, silent-alert-once, **determinate**
  notification ("Categorizing messages… 34 of 210", tap-through-free, id 1001 reused so the
  old notification is replaced);
- when it finishes: that same notification becomes ONE summary ("210 messages reviewed ·
  148 saved · 62 skipped", `setAutoCancel`) and is gone;
- batches smaller than `MIN_BATCH_FOR_PROGRESS = 4` get a single "N new transactions
  saved" line and no bar;
- nothing is posted while any activity is visible (`ProcessLifecycleOwner` gate) — the
  Classification screen shows the same queue live;
- nothing is posted if `POST_NOTIFICATIONS` was denied; the pipeline is never blocked on a
  notification (AGENTS.md hard requirement 1).
Channel is now IMPORTANCE_LOW with a localised name (`sms_channel_name`) instead of the
hardcoded "Transaction Detection"; the two hardcoded English notification strings are now
resources with Hindi twins.

**Work never stops when you look at it.** `AuraSpendApp` now starts the coordinator in
`onStart` *and* `onStop` (idempotent), so the drain runs in the foreground too, at the
same polite pacing (background thread priority, 750 ms between messages). The
Classification triage tab shows a determinate header, a "N messages waiting" line when
the loop is between passes, and a "Categorizing…" `TintBadge` on the exact row that is in
flight; the SMS pick-tab no longer lists skipped rows.

**The drain survives the app being killed.** After a pass, the worker enqueues its own
continuation (same unique one-shot name, REPLACE) when `pendingNew > 0` **or** the inbox
history walk is unfinished (`SmsIngestor.hasMoreHistory` + pure
`shouldContinueDraining`) — the second half matters: stopping when the live queue empties
but the walk has pages left would leave the backlog half-read for six hours. One pass =
12 messages, well inside WorkManager's execution limits. Toggling auto-detect off stops the
coordinator, cancels the pending work and clears the notification.

**Queue progress is derived, not reported.** `queueProgress(rows)` (pure) computes
processed/pending/in-flight/saved/skipped from the observed queue; the ViewModel's
`progressCurrent/progressTotal/isCategorising/queuedCount` come from it, so the screen and
the notification are the same numbers by construction. `runNow` no longer fires on screen
entry.

## Decisions (owner-scored)

1. **Work runs wherever the user is.** Rejected the old "only in background" rule; the
   owner asked to watch it happen one-by-one. Politeness is enforced by pacing, not by
   stopping.
2. **Live progress + one summary.** Rejected both "one summary only" and "silent".
3. **Keep draining after a swipe.** Rejected pausing until the 6-hourly tick.
4. **Filter OTPs at ingest** (not just at process time), so the queue, the progress
   denominator and the pick-list carry only real candidates.

Judgement calls made in-session: `MIN_BATCH_FOR_PROGRESS = 4` (a bar below that is noise);
an indeterminate bar while the history walk still has pages (a completed "210 of 210"
would lie about the drain); the notification's small-icon keeps `ic_dialog_info`; the lint
fixes below avoided growing the baseline.

## Verification

- `./gradlew :app:compileProdDebugKotlin` — clean, **zero `w:` lines** after a
  `--rerun-tasks` pass (main and unit-test compiles).
- `./gradlew testProdDebugUnitTest` — **382 tests, 0 failures** (was 350; +32 new, plus one
  updated). The classification baseline numbers are untouched (no classifier change).
- `./gradlew :app:lintProdDebug` — "no new issues". Two new lint errors were fixed in code
  rather than added to the baseline: an inline `POST_NOTIFICATIONS` guard so lint can see
  it (the model-download notifications cost three baseline entries for the same call), and
  the dead `SDK_INT >= O` check in `ensureChannel` removed (minSdk is 30). The baseline was
  then regenerated **deliberately**: the diff is one `ObsoleteSdkInt` entry removed (the
  worker's deleted SDK check) plus line-number shifts — no unrelated entries disappeared.
- `bash tools/check_license.sh` — all green, including "every English string has a Hindi
  twin" (the 12 new keys are in both locales).
- `bash tools/check_privacy_claims.sh` — green.
- **On-device verification is NOT done** (no device in this session). The plan's manual
  pass (fresh install → consent → watch the progress header; background → ongoing
  notification; swipe away → notification keeps advancing; queue drains → one summary;
  OTP count in the inbox vs. saved transactions; undo a save and confirm the next pass does
  not re-save it; 200% font scale; AMOLED theme) must still be run.

## Tests added

`SmsIngestFilterTest` (OTP/balance-alert/promo/phishing dropped; a debit that ends in an
OTP kept via the amount+verb rescue), `SmsQueueProgressTest` (the "X of Y" arithmetic,
including a history page raising the denominator without moving `processed`),
`SmsProgressNotifierTest` (every branch of `copyForSnapshot`, the accumulation-and-summary
round trip, the foreground gate, and `shouldContinueDraining`), `ClassificationQueueStateTest`
(live progress, the in-flight row staying visible, the pick-list dropping skipped rows, and
"Save All" now actually saving the awaiting rows with a resolved parse while leaving saved
and dismissed rows alone). `SmsPipelineProcessorTest` pins the in-flight write, the sweep
recovery, and that an undo-parked `PROCESSED` row is **neither swept nor re-saved**.
`SmsQueueRowMapperTest` updated for the new mapping plus the in-flight row staying in the
inbox.

To make a ViewModel test possible at all, `AuraSpendApp` is now `open` and a test-only
`RobolectricWorkManagerApp` implements `Configuration.Provider` — without that,
`AuraSpendApp.onCreate`'s `WorkManager.getInstance` throws inside the Robolectric sandbox
and pollutes later tests with `UncaughtExceptionsBeforeTest`. Recorded in
`memory/context/tech-stack.md`.

## Open risks / next step

- **On-device verification is the next step** (list above). The flow's timing — the
  notification's cadence while the history walk pulls pages, and how the progress header
  reads at the moment a page lands — is genuinely only checkable on a phone.
- A new instal now drains in the foreground too, so the first minutes after consent do LLM
  work while the user is on screen. Pacing is unchanged and unmeasured for jank; if a
  frame drop shows up, throttle the foreground pass (same knobs: `INTER_MESSAGE_DELAY_MS`,
  `MAX_BATCH`) rather than reverting to stop-on-foreground.
- A message dropped at ingest can never be re-evaluated if `isOtpOrAlertMessage` changes
  later. Accepted: it was going to be skipped anyway, and the alternative is unbounded
  queue growth. Worth a preference-based reset if the filter ever regresses.
- `COUNT` of the notification copy: when a pass has failures the summary's
  "N saved · M skipped" no longer adds up to "K reviewed" — failed rows stay in the triage
  inbox on purpose. Cosmetic; only visible with failures.
- The summary is plain "N saved · M skipped" (no "need attention" count).
