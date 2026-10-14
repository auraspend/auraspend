# 0036 — the encoder finally runs + three on-device defects

**Status:** done 2026-10-13. Branch `fix/on-device-defects` (PR #55 → `dev`,
merge `4442785`). **490/490 tests** (3 new), lint clean, licence + privacy
checks green, zero compiler warnings.

## Where this came from

The owner pasted a caught boundary failure from a **release build**:

```
Boundary failed, using fallback
java.lang.IllegalArgumentException: tokenizer.json missing at
  /data/user/0/com.awbuilds.auraspend/files/models/tokenizer.json
```

…and then three reports from their own testing: **1.** the app crashed,
**2.** a July message recorded as "today", **3.** a crash while searching a
name in the Activity page. They turned out to be four distinct defects — one
of which had been hiding two more.

Dropbox confirms today's only AuraSpend crash was the search one
(2026-10-13 18:57:38, `com.awbuilds.auraspend.dev`, v0.1.3-dev); the Oct‑10 /
Oct‑12 entries belong to `com.awbuilds.shipmate` (a different app). Items #1
and #3 are the same crash.

## 1. The on-device encoder had never run (two stacked defects) — `4b6d645`

The boundary log pointed at `tokenizer.json`; the download manager has always
written **`minilm-tokenizer.json`** (`ModelConstants.TOKENIZER_FILE_NAME`).
The reader wanted a literal `"tokenizer.json"`, its `require` threw, the
boundary swallowed it into `vocab = emptyMap()`, the tokenizer stayed null and
**every worker pass since the encoder shipped (0.1.1) logged the same warning
and degraded to regex-only behind a caught exception** — the exact
"inert in production" class as the 0026 centroid byte-order bug, one layer
down. Fix: single-source both sides through the constant; the reader is now a
testable top-level `readWordPieceVocab`; `EmbeddingClassifierTokenizerTest`
pins the writer/reader contract (it fails on the old code).

Fixing the name exposed the second layer on the **first inference ever run**
(device, genuine 22 MB model + tokenizer pushed, both SHA-verified against the
pinned digests):

```
OrtException … Gather node … Missing Input: token_type_ids
```

The exported MiniLM graph takes the full BERT input set; `encode()` fed two of
three. Fix: an all-zero `token_type_ids` tensor, closed alongside the others
(all zeros is the standard single-segment encoding).

**Proof:** `LocalLlmProvider: on-device classifier ready: minilm-centroid` on
every launch, the drain's queue processed *with* the model, and zero
`EmbeddingClassifier` / `OrtException` lines in the log. These are the first
transactions this app has ever saved with the on-device model actually
running.

**Why CI could never catch this:** `onnxruntime-android` ships Android `.so`
files only, so the ONNX session never runs in JVM tests — readiness and
asset tests can pass while the pipeline is inert. An on-device inference smoke
run is the only proof; a model swap must re-run it (now written into the
`AGENTS.md` AI invariants).

## 2. A July message was booked as the October scan day — `f740d0b`

Body: *"Rs.270.00 debited from your RWallet Account on Jul 22 2026 6:19AM …"*
→ booked **13 Oct 2026 18:56** (the drain time). Two causes, both fixed:

- **Month-first English dates** (`Jul 22 2026`) match none of the day-first
  extraction patterns, and no `MMM d yyyy` shape existed in `dateFormats`.
  Added the pattern + `"MMM d yyyy" / "MMM d yy" / "MMMM d yyyy"` (comma
  variants normalize away before matching).
- **The no-date fallback was the save clock.** `SmsAutoClassifier.toTransaction`
  now takes the provider `receivedAt` and dates an undated message by arrival;
  `now()` survives only for callers with no message (tests, manual inserts).
  The queue processor and both triage save paths pass it.

Device evidence: `9910` re-booked **2026-07-22 06:19:00** and `10124`
**2026-09-10 17:52:00** (re-staged: `@Update` matches by primary key, so
re-processing can never *repair* a saved row — the 0026 invariant doing its
job; the repair path is delete transaction + re-stage the SMS row); a
still-queued sibling (`9594`) parsed **2026-05-31 10:16:00** organically on
the running drain.

**An audit found the full footprint:** 15 more pre-fix rows (arrived
June–September) carried the 13 Oct scan time; all 15 were re-staged the same
way and re-processed by the fixed build within minutes — **14 re-saved**
(dates now equal the arrival time, except `10108` which shows the *parsed*
`7:31PM` from its body) and **1 (`9674`) SKIPPED** by the current pipeline —
and the `SAVED`-row far-date audit is now **empty (0)**. The audit query to
reuse: `|transaction.date − sms.receivedAt| > 3 days` for `SAVED` rows.

**Open question for the owner:** 0.1.1/0.1.2 users' wrongly-dated rows stay
wrong after the fix (nothing overwrites a saved row on purpose). A one-time
repair pass — re-parse SAVED rows whose date is far from `receivedAt` — is
possible but deliberately not built.

New pinning tests: month-first parse with its time; undated message booked at
arrival; body date outranks arrival.

## 3. Activity search crash — `69ecf16`

```
IndexOutOfBoundsException: Illegal attempt to access index 14 in ItemSnapshotList of size 14
  at androidx.paging.ItemSnapshotList.get
  at androidx.paging.compose.LazyPagingItems.peek
  at …TransactionListScreen.kt:312
```

Read against the pinned `paging-compose 3.5.1` sources: `peek(index)` is
`itemSnapshotList[index]` and **throws** past the end (not a null-returning
read); `itemCount` and `peek` read the same `mutableStateOf`, but the screen
captured `itemCount` during **composition** while the `LazyColumn` builder
runs later, during a **layout** pass, against a newer snapshot. A search
keystroke shrinks the list in between → `peek(14)` on a size‑14 list. Fix:
read the count inside the builder (same-execution snapshot) and bounds-guard
`loadedDayNet`, whose captured `startIndex` had the identical exposure.

Device check: the exact flow that crashed — Activity → search → progressive
filters (`b → ba → bal → bala → Nadeera → BALAMUR`, then `a → zz`, including a
shrink to zero results) — process alive throughout, crash buffer empty, zero
`IndexOutOfBounds`/`ItemSnapshotList` lines.

## Operational notes (device pass)

- The dev app's data had been reset since the last pass (a re-onboarded
  install: fresh DB, the newest 375 queued, `auto_detect_sms` unset — the app
  ingested but never processed). Enabled the pref the way the UI would,
  pushed the SHA-verified model + tokenizer into `files/models/`, and let it
  drain, which then **completed**: **648 rows, 493 saved / 155 skipped / 0
  queued**, every date correct (the far-date audit reads 0). Auto-categorize
  stays ON with the model in place, so the dev app keeps classifying as new
  messages arrive.
- The owner's prod app (`0.1.1-play`) was not touched. Its paste-in crash log
  is fixed only by a build containing `4b6d645` — a 0.1.4 candidate.
- **Left on the dev app deliberately:** auto-categorize ON, model files in
  place (so the AI keeps working), screen timeout 30 min + stay-on while
  charging so the drain could proceed. Lockscreen re-enabled.
- The temp audit dir (real SMS) stays outside the repo; every fixture added
  is a synthetic twin.

## State

- `dev` carries the three fixes plus waves 1–5 + Option A; no release cut.
- A future 0.1.4 would contain: **an encoder that actually runs** (the
  release-worthy one), the date fixes, the search fix, waves 1–5, Option A.
- Play upload-key reset still pending with Google.

## Next

- 0.1.4 cut whenever the owner asks (same dev→main flow as 0.1.3).
- Fold month-first dates + tonight's classes into the golden corpus at the
  next re-baseline; the `docs/evals` fused rows pre-date this work and the
  live runtime that now backs the pipeline.
- Optional follow-ups: the pre-0.1.4 date-repair pass (owner's call); re-run
  the on-device inference smoke after any model/dependency swap.
