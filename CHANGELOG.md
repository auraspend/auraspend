# Changelog

All notable changes to AuraSpend are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
from 0.1.0 onward — though note that below 1.0.0 the leading digit carries no
compatibility promise, so minor versions may contain breaking changes to
internal APIs.

## [Unreleased]

## [0.1.5] — 2026-10-15

A privacy release: the top two items of the improvement list, as separate PRs
that landed together.

### Added

- **App lock** (Settings → Security): an opt-in 4-digit PIN, stored only as a
  salted PBKDF2-HMAC-SHA256 hash, with a biometric shortcut where the hardware
  supports it. It locks on every cold start and again after more than a minute
  in the background — within that grace window (share sheet, file picker,
  Drive sign-in) the session stays unlocked. "Forgot PIN" wipes through the
  same sweep as Settings → Delete all data; there is no back door.
- **The database is encrypted at rest** (SQLCipher Community Edition,
  BSD-3-Clause). The passphrase is derived from a non-exportable Android
  Keystore HMAC key and never stored anywhere; an existing database is upgraded
  in place, once, before Room opens it.

### Notes

- The one-time upgrade is crash-safe: the plaintext original is moved aside
  rather than deleted, the encrypted copy takes its place, and only then is the
  backup removed. An interrupted upgrade restores the original and fails
  loudly instead of starting with an empty database.
- **Do not sideload an older release over 0.1.5** — a pre-0.1.5 build cannot
  open the encrypted database. Play prevents this for normal installs.
- Drive backup payloads and CSV exports are unchanged and remain unencrypted,
  as the app's own copy discloses.

## [0.1.4] — 2026-10-14

A correctness release measured against a real inbox: the on-device classifier
runs for the first time, the reconstructed books match message for message,
and the app gains its F-Droid build.

### Added

- **Detected subscriptions** — recurring payments the classifier had always
  flagged now surface under a "Detected from your messages" section, each with
  a one-tap Track that opens the add sheet prefilled. Suggestions stay out of
  the monthly total until confirmed.
- **Skipped-message history** in Smart Add — "Skipped (N)", Classify again and
  Skip all — so a parser fix can reach messages that were set aside.
- **Activity browsing**: month navigation, a newest/oldest sort toggle, and
  drill-down from Insights into the filtered list.
- **Settings**: SMS-access status, a categorisation-notifications toggle, and
  Delete all data (wipe, re-seed, re-scan).
- **Category icon and colour pickers** in the category editor.
- **Transaction detail**: tap the hero to re-categorize, Share as plain text,
  and the original SMS with sender and received time.
- **Investments category** — EPF contribution confirmations book as an
  investment, not income.
- **F-Droid build** — a third flavor without Google Play Services (and without
  Drive backup, which F-Droid's inclusion policy excludes; ADR 0012).

### Fixed

- **The optional on-device classifier now actually runs.** A wrong tokenizer
  filename and a missing model input had kept it inert behind caught
  exceptions while the UI said "ready".
- **Dates**: month-first formats ("Jul 22 2026") parse, and messages without a
  date are booked at their arrival time instead of when they were scanned.
- **Smart Add no longer lags** while a drain is classifying messages.
- **Fewer wrongly skipped payments** — the duplicate guard is paise-flat, and
  payments it had swallowed are restored on the next pass.
- **Dark and AMOLED visibility**: back buttons, month chevrons and the
  navigation-bar icons were black on every background; the theme now supplies
  the scheme's content colour.
- **The homepage chart** shows the last 7 days instead of a calendar week that
  read empty every Monday.
- The Skipped section stays visible even with no categoriser model installed.

### Changed

- Releases publish to Google Play (closed testing) in the same tag run that
  publishes the GitHub release.

## [0.1.3] — 2026-10-13

A categorisation fix release, measured by draining a real 1,952-message inbox
on a device: the triage surface became visible, and four defects that had
silently dropped or mis-filed real money are fixed.

### Added

- **Visible categorisation status.** The triage tab shows a determinate progress
  header, an "N messages waiting" line and a "Categorizing…" chip on the
  message being processed. A message being worked on is claimed before the
  expensive work, and a pass that dies mid-message is swept back into the
  queue on the next pass — instead of leaving a row stuck forever.
- **One honest notification per drain** — a single ongoing determinate
  notification while messages are categorised, and one summary at the end; the
  per-run "N transactions saved" posts are gone. Draining also continues in
  the background after the app is swiped away.
- OTPs, alerts and promos are dropped when messages are ingested, so the
  queue, its progress count and the pick-list hold only real candidates.

### Fixed

- **Genuine ICICI card spends were silently deleted.** ICICI appends
  "If not you, call 1800 2662/SMS BLOCK …" to every card alert, and the hard
  anti-fraud veto treated that tail as a phishing lure — three real spends
  appeared nowhere, not even in the unrecognised list. The veto now has one
  narrow exception for the bank's own alarm shape; phishing framings
  ("if not done by you", "not authorised by you") stay vetoed.
- **The triage screen no longer hides behind the optional 22 MB model.** The
  queue, its progress and its swipe actions render whether or not the model is
  downloaded; the model prompt now appears only when the queue is empty.
- **Credits phrased around their reversal were skipped as typeless.**
  "…is credited … as a reversal of debit transaction" and "…received …
  through Bharat Bill Payment System" made the debit and credit lexicons both
  fire; these now record as income, as do the card-payment acknowledgements
  they carry.
- **Dashed month-name dates ("21-JUL-26") never parsed**, so those
  transactions were saved with the time they were processed and one payment
  was false-skipped as a duplicate of an unrelated credit hours earlier. The
  date pattern, month-name matching and the duplicate check's amount gate are
  all fixed; saved transactions carry their real dates again.
- **"Payment attempt … unsuccessful" and "refund … has been initiated"
  notices** are treated as alerts instead of piling into the unrecognised
  list — money has not moved in either case.

**Verified on a device:** the full inbox drains to exactly the predicted
candidate set (706 rows, 0 failures, no duplicates), notifications match the
new model, and the test suite grew 382 → 396.

## [0.1.2] — 2026-10-11

The reliability release. A 32-finding code review is fixed end to end — the
on-device categoriser had been silently inert in production, and three separate
paths could lose, hide or overwrite real money — plus Google Drive backup,
a currency picker, and guarded release signing.

### Added

- **Google Drive backup.** The app could restore from Drive but had no way to
  create a backup; Settings → Data now has "Back up to Google Drive" and
  "Restore from Google Drive". The backup serialises every user-owned table
  (format v4) inside one Room transaction, and restore (a replace, not a merge)
  asks for confirmation first.
- **Currency picker.** 30 currencies, captured offline as a source constant
  (no runtime network), with the device currency preselected and first. A
  fourth onboarding page and a Settings → Finance row host the same list, plus
  a "Match my device" reset. Formatting only — **no FX conversion, and the
  picker says so**.
- **Signed, approval-gated builds for PRs to `main`.** The release workflow now
  also runs for main-targeting pull requests, so a signing regression is caught
  before merge. Releases are still tag-driven.
- **Release signing moved to a protected GitHub environment**: required
  reviewer, restricted to `main`, `v*` tags and main-targeting PR refs.
- **The test suite grew 256 → 350** (real-asset decode, Room in-memory round
  trips, ingest planner, retention, ViewModel and repository-batch tests), and
  `tools/check_privacy_claims.sh` joins `check_license.sh` in CI: an absolute
  privacy claim without its exception now fails the build.

### Changed

- **Build flavors renamed: `free`/`play` → `dev`/`prod`**, with exactly four
  variants (dev/prod × debug/release). The dev build has a `.dev` application
  id and an "AuraSpend Dev" label so it can sit beside the store build; prod is
  the only distributed build. The `benchmark` build type was removed and the
  macrobenchmark module now links the prod release variant. See
  [ADR 0010](docs/adr/0010-dev-prod-flavors.md).
- **Public wording is honest about distribution**: "Download APK" claims are
  gone from the landing page, README and terms until a release is actually
  published ("build from source; the Play listing is staged, not published").
- **The privacy copy is qualified in-app and in the policy**: the only upload
  is the Drive backup the user starts; the policy now lists the
  unrecognized-message table and states the 30-day purge.
- **`secrets.properties.example` no longer lists `WEB_CLIENT_ID` /
  `DRIVE_API_KEY`** — nothing ever read them.
- The shipping variant removes the `profileable` manifest tag (a profileable
  release build allows heap dumps of the decrypted database); the dev flavor
  keeps it for baseline-profile generation.
- Lottie (attributed but never in the build) and its licence text are removed,
  and every attribution was re-checked with `tools/check_license.sh`.

### Fixed

- **The flagship on-device categoriser was inert in production.** The centroid
  asset is little-endian; the reader parsed big-endian, failed, and the failure
  was swallowed into `model = null` while settings said "ready". The codec now
  validates bounds and layout, the asset SHA-256 is pinned, a test decodes the
  real asset, and readiness reflects a failed load. Measured accuracy is
  unchanged (regex floor 13.8 % exact; fused 16.9 %, 0 destroyed, 5 rescued) —
  the fix made the numbers real.
- **The model's discard gate was dead code.** It reported the class probability
  instead of confidence in the emitted verdict, so the 0.9 threshold could
  never fire; it now converts via `verdictConfidence`, and a test fails if the
  gate becomes unreachable again.
- **A duplicate `sourceSmsId` could delete the user's edited transaction.**
  Transaction writes used `REPLACE` (delete-then-insert in SQLite); writes are
  now update-if-present, insert-if-absent, and CSV import is one all-or-nothing
  batch instead of a per-row loop.
- **A fresh install never ingested its SMS history.** A single watermark
  advanced past everything older than the newest 500 messages; the scan now
  keeps a high-water mark plus a downwards history walk that runs only when the
  live queue is empty.
- **Deposits and refunds with a balance tail were dropped before parsing**, and
  sender-first routing had zero production callers (three bank parsers were
  unreachable). Both fixed; the paste path, pipeline and evals now pass the
  sender.
- **Triage said "Saved" before the save happened**; the confirmation event now
  fires only after the transaction is written, and failures surface as errors.
- **Restore re-imported unmasked message bodies** from pre-masker backups;
  restore now masks the SMS queue and unrecognized messages on the way in.
- **The 30-day unrecognized-message purge was never called**; it now runs on
  the periodic worker tick — and the SMS queue is explicitly never swept.
- **A v1 database could have been destructively wiped instead of migrated.**
  The 2→3 migration is added, destructive fallback is limited to v1, and Room
  schemas are exported (`app/schemas/`) for review.
- Repository writes from UI were unguarded; every composable write now
  degrades to a banner or stays open with a reason (Recurring and Budget deletes
  gained their missing confirmation dialogs).
- Hardcoded `₹` removed from chrome (all money text follows the theme's
  currency); duplicate keyword-map key removed with a guard test; ~30 EN/HI
  strings localized (parity 489/489).
- **The R8 and native-runtime audits are now CI gates.** The native audit's
  `nm` probes had ignored their arguments, so it could not fail; fixed and run
  against both shipped arm64 libraries, with ONNX Runtime's loader exception
  named, evidence-gated and documented.

### Removed

- Dead code the review found: duplicate-detection stubs, the unreachable
  duplicate dialog, `MessageDedup`, `searchTransactions`, `nextScanCursor`
  (whose premise *was* the ingest bug), `CurrencyStyle.supported`, and the four
  unreferenced animation assets.

### Security

- Signing secrets live in the protected `release` environment (required
  reviewer; `main`, `v*` and main-PR refs only; forks never receive them).
- The production manifest no longer allows runtime heap profiling.
- Privacy claims are enforced by a build-time check, not review comments.

## [0.1.1] — 2026-09-30

Substantial pre-1.0 release. The headline is that the on-device categoriser
shrank from 468 MB to 22 MB, and the project changed licence.

### Changed

- **Licence: Apache-2.0 → AGPL-3.0-or-later.** So that a modified AuraSpend
  offered to users over a network must offer its source too, which Apache-2.0
  does not require. Irreversible for this code. See
  [ADR 0009](docs/adr/0009-agpl-3.0-relicensing.md), which also records what the
  change does *not* buy.
- **On-device categoriser: 468 MB generative model → 22 MB int8 encoder.**
  `EmbeddingClassifier` does nearest-centroid classification over ONNX Runtime
  and a quantised `all-MiniLM-L6-v2` sentence encoder, replacing a bundled
  Qwen/llama.cpp. Five candidate runtimes were measured; see
  [docs/evals](docs/evals/README.md).
- **Two ABIs instead of four** (`arm64-v8a`, `armeabi-v7a`). AAB 35.4 → 20.5 MB,
  APK 78.8 → 37.2 MB. ChromeOS is deliberately unsupported.
- The model is now downloaded at runtime after an explicit opt-in, and its
  SHA-256 is verified against a pinned digest before use.

### Fixed

- **A fresh install silently downloaded the 22 MB model** and enabled auto-read
  with no prompt. Consent was a `LaunchedEffect` that auto-accepted; it is now a
  tap on an inline card.
- **Swipe-to-save and swipe-to-delete were silent no-ops.** Both used
  `confirmValueChange`, deprecated without replacement in this Compose version,
  so the callback was never invoked — the row animated to its anchor, painted a
  coloured background, and changed nothing. On Activity that meant a transaction
  stayed in the database while the UI implied otherwise. Both now drive off the
  settled state, and both are undoable.
- **Triage inbox never drained.** Saved and dismissed messages stayed listed
  forever. They now leave the list, and undo brings them back. A dismissed
  message was also indistinguishable from a ready-to-save one, so "Save All"
  could resurrect one; dismissed now has its own status.
- **Plan reported ₹0 while Dashboard reported real spend** — a stale
  `spentAmount` column.
- **Dashboard and Plan disagreed on recurring totals** (₹3,745 vs ₹2,370.90).
- Unguarded `Enum.valueOf` calls in `Mappers.kt` — one corrupt value could brick
  the dashboard.
- Transport category showing `0%` alongside ₹2,795.
- A `↑46843%` delta in the Insights header.
- Settings and Quick Add ignored bottom system-bar insets.
- Emoji rendered as a monochrome Android robot instead of the intended glyph.
- Remaining ASCII minus signs in money strings, replaced with U+2212.

### Added

- **Open-source licenses screen** (Settings → About). Ships the full AGPL-3.0
  text and third-party attributions in-app, which AGPL-3.0 §4 requires. Before
  this the app displayed only the string `Apache-2.0` with no way to read
  anything.
- **A CLA with copyright assignment**, so contributions can be relicensed as a
  whole later. Under AGPL this is an obligation, not a formality: the 0.1.1
  relicensing was only possible because there was a single copyright holder.
- `tools/check_license.sh`, run in CI. Fails if the licence string is
  inconsistent across `LICENSE`, `README`, both string locales, the splash
  footer, the website, and the legal pages, or if a dependency has no
  third-party attribution. The licence had already drifted across eleven files.
- `tools/verify_r8_release.sh` and `tools/r8_scores.py` — release verification
  against the Google Play technical requirements.
- Demo data seeding (`tools/seed_demo_data.sh`) for manual testing.

### Verified

- Regex baseline: 13.8% exact match, type 95.4%, category 36.9%. Pinned by
  `RegexBaselineEvalTest` so the regex layer cannot regress.
- The shipped encoder destroys **0/46** transactions, where SmolLM2 destroyed
  43/46, Qwen2.5-0.5B 8/46, and FunctionGemma-270M 5/46. `mayDiscard` now
  requires both `isTransactionProbability >= 0.9` and an unparsed amount.
- Release bundle: 3.68 MB uncompressed DEX, so Play's February 2027 ≥25%
  shrinking floor does not bind. APK is 16 KB page-size aligned, and the JNI
  classes ONNX resolves at `JNI_OnLoad` survive R8 un-renamed.
- 274 unit tests, 0 failures. Screenshot baselines cover `DashboardScreen` in all
  three themes.

## [0.1.0] — 2026-05-13

First alpha. Feature-complete for its scope: Material 3 UI, bank SMS
classification, budgets, subscriptions, savings goals, and Google Drive backup.
Licensed under Apache-2.0.

[Unreleased]: https://github.com/auraspend/auraspend/compare/v0.1.3...HEAD
[0.1.3]: https://github.com/auraspend/auraspend/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/auraspend/auraspend/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/auraspend/auraspend/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/auraspend/auraspend/releases/tag/v0.1.0
