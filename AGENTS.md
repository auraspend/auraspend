# AGENTS.md — AuraSpend agent contract

> Read this file and the latest file in `docs/handoffs/` before touching anything.
> Update both (plus `CLAUDE.md` / `memory/` when you learn something durable) before ending a session.
>
> Latest: `docs/handoffs/0054-play-listing-title.md` — the en-US Play title is
> now `AuraSpend: SMS Expense Tracker` (30/30), updated through the Play API.
> Play store **tags are not exposed by the public API** (only title/short/full/
> video and contact details), so they remain a manual Play Console step. No
> credential, account identity or project name is recorded; the one-off
> dispatch tooling was removed. The canonical copy remains
> `docs/store/play-listing-en-US.md`.
> Before: `docs/handoffs/0053-release-0.1.6-recut.md` — **0.1.6 was re-cut
> without the terracotta UI**: versionCode 8, tag `v0.1.6` on `8ac8445`, run
> 39031663532 green; Play alpha + GitHub release (AAB/APK/SHA256SUMS) done.
> 616 tests; the release APK packages only Plus Jakarta Sans. The app and the
> store visuals are both pre-terracotta and match. 0052's first 0.1.6 (terracotta,
> versionCode 7) is superseded.
> Before: `docs/handoffs/0052-release-0.1.6.md` — the superseded first 0.1.6:
> versionCode 7, tag on `8e87515`, terracotta UI included by mistake. Do not use
> its artifacts.
> Before: `docs/handoffs/0051-play-listing-aso.md` — the Play long description was
> not in the repo and the only checked-in copy was 786-char F-Droid text; the
> canonical Play copy is now `docs/store/play-listing-en-US.md` (title 30/30,
> short 79/80, full 3,270/4,000).
> Before: `docs/handoffs/0050-classification-fixes.md` — the audit's defects are
> fixed: the eval scores amount/date and correct rejections (80/82 exact, 97.6 %,
> type 98.8, category 100, merchant 98.8, amount 98.8), sender-aware bank tails
> rescue HDFC's “If not you” debit, routed parses merge over the global parse
> instead of replacing it, own transfers are excluded from totals, and merchant/
> category/ingest/memory gaps are closed. 616/616 tests (1 skipped), lint clean
> (1 baseline hint). Remaining structural items: first-class TRANSFER/INVESTMENT,
> full multi-currency, a user-rules UI, Hindi/regional SMS.
> Before: `docs/handoffs/0049-classification-gap-analysis.md` — the read-only audit
> that started it (31.7 % was not parse accuracy). Full report:
> `docs/reviews/2026-10-17-message-classification-gap-analysis.md`.
> Before: `docs/handoffs/0048-terracotta-ui-revamp.md` — the app has a new visual
> identity and **nothing but the UI changed**: terracotta clay replaces the purple
> brand (`#A64B2A` → `#D2764A` → `#F2D8BC`), warm cream/near-black canvases,
> softer shapes, an illustrated clay-pot mark (`AuraMark`) and a one-per-screen
> gradient hero (`AuraCardStyle.Hero`). Type is now **Manrope** (all UI + all
> money — it has `tnum`) plus **Fraunces** for display/headline/titleLarge only,
> with Plus Jakarta Sans gone and its OFL attribution swapped in nine files at
> once. **The `Aurora*` symbol names stay on purpose** — don't "fix" them.
> 584/584 tests, lint clean, licence + privacy checks green; captured in light
> and dark on the arm64 AVD. Looking at it on a device found four things the whole
> JVM suite had passed: the selected nav *label* was white-on-cream (the gradient
> pill only wraps the icon), keypad digits were rendering in the display serif
> (Fraunces has old-style figures — now `AuraType.keypadDigit`), the
> notification-permission dialog still wore the old purple because
> `res/values/colors.xml` (`android:colorAccent`) was never recoloured, and
> `tools/seed_demo_data.sh` had **silently never seeded anything** (the seeder is
> opt-in and the script never passed `-e seedDemoData true`; a skipped test still
> reports OK). Default category colours were retuned for new installs only. **Not
> committed** — the owner asked for the working tree. Launcher icon, website and
> store assets still carry the old brand (follow-ups in the handoff).
> Before: `docs/handoffs/0047-classification-informational-and-rails.md` — a tester's
> CSV export showed three misclassification families; the fixes were an
> informational veto set (points/marketing/NAV/expiry) with the load-bearing rule
> that **a completed debit outranks every informational phrase**, the removal of
> rail tokens (`upi`/`neft`/`imps`/`rtgs`) as *categories* so a UPI payment keeps
> its real category, Investments as a keyword target, and `Refno`/`RRN`/`UTR` as
> reference suffixes. Corpus 72 → 82 with the floor re-measured at 26/82 exact
> (31.7 %), type 95.1 %, category 53.7 %; most of the jump was a metric-fidelity
> fix. PR #78, 584/584 tests.
> Before: `docs/handoffs/0046-app-lock-and-encrypted-database.md` — the two
> privacy features from the improvement list are implemented as separate PRs:
> **#76 an optional app lock** (4-digit PIN as a salted PBKDF2 hash, biometric
> shortcut, locks on cold start and after >60 s backgrounded, "forgot PIN"
> wipes through the same `AuraSpendApp.wipeAllData()` the Settings action uses)
> and **#77 the database is encrypted at rest** (SQLCipher Community Edition,
> passphrase derived from a non-exportable Keystore HMAC key, one-time in-place
> plaintext upgrade). Issues **#71–#75** file the backlog (lock, encryption,
> global search, budget alerts, scheduled Drive backup). **Both have now run on
> real hardware** — the arm64 emulator *and* the owner's Moto G40 — which is
> where three release-blocking SQLCipher defects were found that the whole JVM
> suite, lint and every static gate had passed: nothing loads `libsqlcipher.so`
> (the app must `System.loadLibrary` it), the documented ATTACH direction fails
> on Android (`SQLITE_CANTOPEN`), and SQLCipher's factory ignores Room's journal
> mode. **The migration has run against a real schema-v8 database whose data
> lived almost entirely in its WAL** (4 KB main file + 218 KB WAL → 102 KB
> encrypted, rows intact) **and against the owner's own 492 production
> transactions on their vivo**, carried in through the app's own CSV export and
> compared by table fingerprint before and after (`docs/release/migration-rehearsal.md`)
> — their Play-signed install itself still upgrades on the next Play release. `connectedAndroidTest` **uninstalls** the app afterwards (use a manual
> `am instrument` for a data-bearing install), and Compose device tests need the
> **screen awake** — with the display off Compose emits no semantics at all.
> Other traps worth keeping:
> SQLCipher's real artifact is `net.zetetic:sqlcipher-android` (the
> similarly-named `android-database-sqlcipher` on Central is 4.5.4, the *legacy*
> API; Zetetic's S3 is 403, JitPack has only 3.x); the native audit's loader
> allowance is a **digest-pinned `APPROVED_LOADERS` entry**, so a version bump
> blocks until re-reviewed; **CI now compiles `androidTest`** (it had been
> broken since 0026 unnoticed); and device-side Compose tests need Espresso
> **3.7.0**, because 3.5.0 probes an API-37-removed method and kills every
> assertion. Both PRs are green (557/557 and 549/549, lint clean, 10/10
> instrumented on the phone). Unverified still: the migration on the owner's
> production rows, the biometric prompt, and an R8-minified run.
> **F-Droid**: MR !52537 was closed for the app-inclusion template and YAML
> structure; both are fixed and it is reopened, awaiting a maintainer CI
> trigger. The three real defects were `sdks:` (not a metadata field — use
> `prebuild: sdkmanager '…'`), `AutoUpdateMode: Version v%v` with
> `UpdateCheckMode: Tags` (the reference requires plain `Version`), and
> the comment header (`templates/build-gradle.yml`: "PLEASE REMOVE ALL
> COMMENTS…"). GitLab writes went through the owner's browser session:
> the stored git credential reads the fork but cannot push (403).
> Before: `docs/handoffs/0045-release-0.1.4-shipped.md` — **0.1.4 is out**:
> GitHub release published (AAB/APK/checksums) and **Play closed testing
> (`alpha`) holds 0.1.4 — status `completed`** (the tag run's Play upload
> committed; the former upload-key precondition is evidently cleared). The run
> left the release a **draft**: its exists-check counted Release Drafter's
> rolling draft as "published"; PR #69 fixes that (`isDraft` check + a
> guaranteed publish step). Public run logs scanned — only the secret **name**
> and GitHub's `***` masking, no credential identity. F-Droid: everything
> staged; the fdroiddata MR (owner's GitLab) is the last step — issue #65.
> Device pass still pending (wireless debugging off).
> Before: `docs/handoffs/0044-play-upload-on-tag.md` — one `v*` tag now ships to
> **both channels**: the GitHub release (as before) *and* a Google Play upload —
> signed AAB to the closed-testing track (`alpha`, `PLAY_TRACK` overrides) with
> the per-language notes in `docs/store/whatsnew/` and the R8 mapping. Tag runs
> only, still behind the `release` approval. The Play credential
> (`PLAY_SERVICE_ACCOUNT_JSON`) is a service account whose identity is
> **deliberately unrecorded anywhere in this repo** — never name it; AGENTS.md
> carries the rule. A read-only pre-flight confirmed auth + app access + the
> `alpha` track name. PR #67. Remaining precondition: the Play upload-key reset
> (a first upload without it fails loudly). F-Droid: sequence settled on #65 —
> the fdroiddata MR (owner's GitLab account) follows the next tag.
> Before: `docs/handoffs/0043-theme-visibility-chart-fdroid.md` — untinted icons
> (back buttons, month chevrons) were black on every background — Compose's
> `LocalContentColor` default; the theme now provides the scheme's
> `onBackground` (+ a LIGHT/DARK/AMOLED guard test), and the nav-bar appearance
> follows the theme too. The homepage chart is the **last 7 days ending today**
> (a calendar week reads empty every Monday). A third **`fdroid` flavor**
> ships without Google Play Services (ADR 0012: Drive backup absent by design,
> `BuildConfig.DRIVE_BACKUP` hides its rows, CI builds it, fdroiddata metadata
> staged in `docs/fdroid/`, submission tracked in #65). PRs #63/#64, **545/545
> tests.** The same PR fixed **two release gates that had never run on CI**: the
> R8 gate now builds the bundle it checks (+ bash 3.2), and the native audit's
> probe no longer SIGPIPEs itself into a failure. Issues: #16–#21 closed, #15
> updated, #65/#66 new. On-device visual pass pending (wireless debugging off).
> Before: `docs/handoffs/0042-epf-investment-and-release-approvals.md` — two
> owner calls: **EPF contributions are investments, not income** (sender-routed
> `EpfoParser` on EPFOHO — contribution amount pinned, type EXPENSE, new
> default `cat_investment`; startup seeding now reconciles missing defaults so
> existing installs get the category; PR #62) and **approvals only where they
> matter** (`release.yml` no longer runs on PRs — every PR onto main had been
> minting a `release`-environment approval request; stale runs cancelled;
> approvals now = main-branch review + real release runs; PR #61). **540/540
> tests.** Pending on-device: re-type the saved EPF rows (amounts already
> right) + the two 0041-pass spot checks.
> Before: `docs/handoffs/0041-audit-device-pass-skipped-fix.md` — the ten 0040
> features walked on the owner's device; the pass found one real gap: with **no
> model downloaded and the queue empty** — the model-less default — the Auto
> Detect tab showed the model prompt and PR #59's **Skipped section was
> unreachable** (wired into QUEUE/EMPTY only). The prompt branch now shows the
> skipped history below the prompt; PR #60, **531/531**, re-verified on-device.
> Also verified: Settings rows, category pickers, Activity month+sort, detail
> re-categorize + Share, Insights drill-down, Quick Add date. Session note: the
> consent card and the tab's model prompt are two stacked layers, not a
> duplicated dialog.
> Before: `docs/handoffs/0040-screen-audit-gaps.md` — the owner asked what each
> screen was missing; ten small tested changes closed the gaps: subscription
> **edit** + suggestion **dismiss**; a **Skipped (N)** section in Smart Add
> with Classify-again and Skip All; Activity **month navigation** and a
> **sort toggle**; Settings **SMS-access status**, a **notification toggle**
> and **Delete all data** (restore-with-empty-backup + re-seed + cursor
> reset); category **icon/colour pickers** (guarded by tests); detail-page
> **re-categorize + Share**; editor **recurring switch**; Quick Add **date**;
> Insights **drill-down** into filtered Activity. PR #59, **531/531 tests.**
> Deliberately not ported (reasons in the handoff): dashboard month-stepping,
> budget alert notifications, multi-select delete, global search.
> Before: `docs/handoffs/0039-detected-subscriptions.md` — the Subscriptions
> screen now shows the payments the classifier had **always** flagged recurring
> but never displayed (eight real recurring payments on the owner's fresh
> install; the owner's services and amounts are deliberately unrecorded): a
> "Detected from your messages" section, one row
> per service (newest amount/date, junk names filtered, tracked services
> suppressed), each with a Track action that opens the add sheet prefilled.
> Suggestions stay out of the monthly total until the user confirms. Also the
> recorded answer to "do I need the 22 MB model?": **no** — categorisation and
> recurring detection run offline without it; the model is optional refinement.
> PR #58, **516/516 tests.**
> Before: `docs/handoffs/0038-smart-add-lag-and-clean-slate-audit.md` — the Smart
> Add lag is root-caused and fixed (the triage observer re-parsed every
> skipped/pending row **on the main thread** on every queue write): the parse is
> memoized (`ParseMemo`) and the derivation runs on `Dispatchers.Default` with
> conflation. Clean-slate A/B on-device: late frames **15.5 % → 3.3 %**, p95
> frame interval **435 ms → 17.7 ms** at an identical workload. The
> duplicate guard's ±₹1 window for small amounts was swallowing real payments
> (a card swipe 52 min after a similar one at the same merchant) — the gate is
> paise-flat now and the fresh re-ingest rescued two payments automatically.
> Owner-directed clean-slate run (wipe → fresh full re-ingest under the fixed
> build) **reproduced the books exactly**; the skipped set has zero unmatched
> rows. PR #57, **509/509 tests.** Device notes: `gfxinfo` shows 0 frames for
> this app on vivo — use SurfaceFlinger `--latency` sampling; fresh installs
> must seed `currency_code=INR` or the locale renders `$`.
> Before: `docs/handoffs/0037-txn-detail-original-message.md` — the transaction
> detail page now shows the **original SMS**: sender, received time, the full
> body (selectable, no truncation) and a Copy action, read back and verified on
> device (`message copy: 112 chars, clipboard readback=112`). The Note row hides
> only when it is pipeline filler (merchant repeated / first 120 chars of the
> body — single-sourced from `BankMessageParser.truncateMessage`), never for an
> edited note. A read-only `SmsMessageLookup` degrades to "no card" on any
> failure. PR #56, **501/501 tests.** Device gotcha: vivo/OriginOS drops
> `Log.d` app lines — use `i`/`w`/`e` for field diagnostics.
> Before: `docs/handoffs/0036-encoder-live-and-reported-fixes.md` — the owner's
> on-device report peeled **four defects, two stacked under one another**: the
> on-device encoder had *never run* — the vocab reader looked for
> `tokenizer.json` while the downloader writes `minilm-tokenizer.json`, and
> behind that the model required a `token_type_ids` input `encode()` never fed.
> Both fixed; first real inference ever on the owner's device (`on-device
> classifier ready: minilm-centroid`), zero inference errors. Month-first
> dates now parse ("Jul 22 2026 6:19AM") and undated messages fall back to the
> provider `receivedAt` instead of the save clock — 17 re-staged rows
> re-booked their true July–September dates, far-date audit back to zero. The
> search crash was `LazyPagingItems.peek` throwing on a composition-captured
> `itemCount` vs a layout-time snapshot — the count is read inside the builder
> now. PR #55, **490/490 tests.** Branch `fix/on-device-defects`.
> Before: `docs/handoffs/0035-device-pass-and-wave-5.md` — the targeted device
> pass removed the last 14 phantom card-payment "incomes" (all 22
> credit-card rows re-verified as SKIPPED on-device; device now 467 saved /
> 233 skipped / 467 txns), and wave 5 added the last institutions (HDFC
> Mutual Fund + Navi Mutual Fund with their in-clause payees, Kerala Gramin
> Bank, Kerala Bank, DBS, IPPB, Jana — **53 parsers** in the registry).
> **484/484 tests.**
> Before: `docs/handoffs/0034-wave-4-corrections.md` — wave 4 ported the six
> corrections the wave-3 cohort's fixtures demanded ("used at" joins the debit
> lexicon; Indian Bank ATM labels; Saraswat ACH-payee + rail labels; Standard
> Chartered rail credits; Dhanlaxmi's quoted "Payment from X" payee; Amazon Pay
> wallet direction + label). Deliberately not ported: merchants containing
> masked accounts, foreign-currency messages, and self-transfer shapes.
> **478/478 tests.**
> Before: `docs/handoffs/0033-wave-3-and-card-payments.md` — wave 3 (28 more
> sender-routed parsers: national banks + cards/wallets; 46 total), credit-card
> bill payments excluded on **both** sides at ingest (spending is counted at
> swipe time; refunds/cashbacks stay income), synthetic-only fixtures with a new
> `FixturePrivacyTest` tripwire, and the golden corpus grown to 72 cases with the
> floor re-measured (13.9 % exact / type 95.8 % / category 34.7 %).
> **465/465 tests.**
> Before: `docs/handoffs/0032-wave-2-and-device-audit.md` — wave 2 (12 more
> sender-routed parsers; 18 total) went through a **fresh full re-ingest of the
> owner's real inbox**, diffed per message. The phone found six classes the
> fixtures had not: balance "CR." annotations making PNB debits typeless;
> passive "sent to"/"if not received" mail phrasing booking statements as
> expenses/income; bill notices ("due for payment", "Total amount payable")
> booking as expenses; future-refund notices as income; the user's own utility
> payments booked as income; and the comma variant "ignore, if already paid"
> slipping the reminder veto. It also proved the **classification memory can
> resurrect a wrongly-saved class** — the e-bill veto sits at ingest, upstream
> of it. Final inbox: **700 candidates, 481 saved / 219 skipped / 0 failed**,
> 24 phantoms gone, 58 old false-dedup casualties recovered, every save's
> direction now matches the regex. **451/451 tests.**
> Before: `docs/handoffs/0031-per-bank-parsers-wave-1.md` — sender-routed parsers
> for **HDFC, ICICI and Kotak** (corrections layered on the global engine, so the
> amount/merchant logic cannot drift), plus engine round 3: NEFT remitter names
> (HDFC "NEFT Cr-…" and ICICI "NEFT-<ref>-<name>"), "towards X for Y" cutoff, and
> the "received … for processing" veto that stops NACH mandates booking as
> phantom income. **426/426 tests.** Branch `feat/per-bank-parsers`.
> Wave 2 (PNB, BoB, IDFC First, IndusInd, Federal, Union, …) and the corpus
> re-baseline are staged next.
> Before: `docs/handoffs/0030-parsing-coverage.md` — the SMS engine absorbed the
> real-world amount/merchant/guard handling of the PennyWise AI parser knowledge
> base (AGPL-3.0, attributed in the licences index): no-leading-digit amounts,
> credit-limit clause masking, payment-app QR / payee / refund / salary merchant
> intelligence, the beneficiary-direction type fix, and five new ingest guards.
> **413/413 tests.** On branch `feat/bank-parser-coverage`, deliberately not
> merged (dev is the 0.1.3 release-PR head). The per-bank registry wave and the
> corpus re-baseline are staged next.
> Before: `docs/handoffs/0029-sms-categorisation-fix-verification.md` — the device
> pass on the owner's real inbox found and fixed four issues: genuine ICICI card
> spends were deleted by the hard fraud veto (the bank's own alarm tail was the
> trigger — now a narrow exception); the triage UI was hidden behind the optional
> 22 MB model (the queue now always wins); credits like "reversal of debit" and
> "Bharat Bill Payment System" came back typeless (phrases are neutralised before
> scoring); and dashed month-name dates ("21-JUL-26") never parsed — the year
> separator class `[ -,]` was a character range, and month text parsing is
> case-sensitive — so those transactions got the save-time date and one was
> false-skipped as a duplicate (the dedup amount gate is tightened too).
> 396/396 tests green. Landed on `dev` (PR #46) and cut as **0.1.3** —
> versionCode 4, tag `v0.1.3` + GitHub Release with signed artifacts; the
> dev→main PR is #47. The owner's phone still runs `0.1.1-play`; the Dev build
> used for the pass is installed alongside it. The corpus re-baseline for these
> shapes is deliberately left as a separate step.
> Before: `docs/handoffs/0028-sms-categorisation-flow.md` — the categorisation flow now
> matches what the owner expects to see. The queue pipeline was already sound; what was
> broken was the status surface and the notification model. New `CLASSIFYING` queue status
> is the in-flight marker (a pass claims a row before the expensive work, and a pass killed
> mid-row is swept back to NEW on the next pass); `PROCESSED` is *parked by the user* and is
> **never** swept or re-processed. OTP/alerts/promos are dropped **at ingest** with the same
> `isOtpOrAlertMessage` predicate the processor uses, so the queue, the progress denominator
> and the pick-list hold only real candidates. Live status is derived from the observed queue
> (`queueProgress`), not reported by a worker: the triage tab shows a determinate header, a
> "N messages waiting" line and a "Categorizing…" chip on the in-flight row. The
> per-run "N transactions saved" notification is replaced by ONE ongoing determinate
> notification during a drain and ONE summary at the end; work now also runs while the app
> is in the foreground (pacing unchanged) and the WorkManager pass chains itself so a
> two-month backlog keeps draining after the app is swiped away. Dead code removed:
> "Save All" filtered a status no row can have. **382 tests green** (32 new), lint clean with
> the permission guard written to be lint-visible, EN/HI green. **On-device verification is
> still outstanding.**
> Before: `docs/handoffs/0027-currency-picker.md` — the currency list is now real:
> the 30 currencies from `api.frankfurter.dev/v1/currencies` are captured in
> `ui/designsystem/CurrencyCatalog.kt` and picked from a shared list
> (`CurrencyPickerList`) on a new 4th onboarding page and a Settings → Finance
> row. The plumbing already worked (pref → theme → `LocalCurrencyStyle`); nothing
> could *set* it, and `CurrencyStyle.supported` was a 10-entry list with zero call
> sites. Names come from the platform's localized `Currency.displayName`, so 30
> currencies did not become 60 translated strings; no network at runtime; **no FX
> conversion, and the picker says so out loud**. 350 tests green (7 new), lint
> green, EN/HI 489/489. **A second session was editing this tree at the same
> time** — it added the `upsertTransactions` batch (all-or-nothing CSV import) and
> 19 tests, included in that green run; see 0027's "Concurrent changes" section.
> Landed in the 0.1.2 release commit (2026-10-11).
> Before: `docs/handoffs/0026-review-fixes.md` — the 32-finding code review
> (`docs/reviews/2026-10-10-code-review.md`) is fixed end to end. The headline
> three: the on-device encoder was **inert in production** (the centroid asset is
> little-endian, the reader parsed big-endian, and the failure was swallowed into
> `model = null` while readiness said "ready"); the transaction DAO wrote with
> `REPLACE`, so a duplicate `sourceSmsId` **deleted the user's edited row**; and
> the SMS ingest cursor advanced past everything older than the newest 500, so a
> fresh install never ingested its history. Also: the discard gate's probability
> polarity was unreachable (dead safety code), deposits/refunds with a balance
> tail were dropped before parsing, restore re-introduced unmasked SMS bodies,
> the 30-day purge was never called, sender-first routing had zero production
> callers, the R8 and native-audit gates were manual (and the audit's `nm` probes
> ignored their arguments, so it could not fail), a "bundled Lottie" was
> attributed but not in the build, and the privacy copy claimed nothing leaves
> the phone while the Drive backup uploads message text. **324 tests green** (was
> 256), lint clean on a regenerated baseline, both shell checks green, measured
> classification numbers unchanged (regex floor 13.8 %, fused 0 destroyed).
> **Landed in the 0.1.2 release commit (2026-10-11).** Three review
> findings had a wrong premise and are documented as such: #30's "destructive
> fallback disabled" was inverted (it was silent data loss), #4's suggested fix
> stalls, and #24's map divergence is overridden by the fusion layer. Pending
> owner calls: branch protection must require `PR Check` now that `ci.yml` is
> push-only; the terms §4.2 wording; nothing visual was verified on a device.
> Before: `docs/handoffs/0025-drive-backup-and-site-redesign.md` — Drive backup
> now exists in the app: Settings → Data has "Back up to Google Drive" and
> "Restore from Google Drive" (restore was onboarding-only before, and no
> screen ever triggered a backup). A new `BackupCreateManager` snapshots every
> table in one Room transaction; a Robolectric test seeds one row per table so
> a forgotten section fails the build instead of deleting data on a later
> restore. 256 tests green, lint clean. The website (`docs/index.html`,
> `privacy.html`, `terms.html`, `css/`) was redesigned to the app's Aurora
> purple with real screenshots and copy checked against code. Follow-ups the
> same session: international hero headline ("Know where your money goes."),
> device bezels on every phone at the hero's size, a responsive pass (nav
> collapses at ≤860 px), the real app icon + one-word wordmark, and a
> light/dark theme toggle with theme-matched phone screenshots. **The whole
> session is uncommitted — no commits or pushes.** Pending owner calls:
> publish a GitHub Release before "Download APK" has a target, Play-listing
> wording, terms' legal positions.
> Before: `docs/handoffs/0024-screenshots-removed-signed-release.md` — the
> Robolectric/Roborazzi screenshot tests and the CI drift gate are removed
> (owner call; 274 → 254 tests, all green) — visual changes are verified by
> hand again. Signed release pipeline verified end to end: a dispatch of
> `release.yml` produced a signed AAB + APK (`CN=AuraSpend`) with checksums.
> Before: `docs/handoffs/0023-ci-caching.md` — the CI download tax is gone:
> Robolectric jars are now cached (`~/.m2`), wrapper restore keys tightened,
> setup-gradle bumped to v6 (which renamed cache namespaces and re-seeded
> them). Caches are written on `main` and read everywhere — a same-commit
> rerun now completes in ~52 s vs 6–9 min cold.
> Before: `docs/handoffs/0022-ci-sdk-package-and-screenshot-gate.md` — CI was
> red for two independent reasons: `platforms;android-37` no longer resolves
> (it installs as **`platforms;android-37.0`**), and the byte-exact screenshot
> gate cannot survive cross-architecture antialiasing — it is now a tolerance
> gate (`tools/compare_screenshots.py`). CI green; `main` fast-forwarded.
> Before: `docs/handoffs/0021-branching-convention.md` — `dev` created from
> `main` and adopted as the integration branch (`main` stays release-ready,
> protected and default); CI/PR checks run on both again; work branches are
> short-lived (`phase-N-name` or `<type>/<topic>`) and deleted once merged.
> Before: `docs/handoffs/0020-identity-scrub-and-upload-key.md` — repo moved to
> the **auraspend** org; every personal identifier (name, personal email,
> GitHub handle, Play IDs, tester group, machine paths) and every **real
> production SMS** in fixtures/docs/history replaced with synthetic data;
> history rewritten with `git-filter-repo` (author/committer → AuraSpend) and
> the pack shrunk 696 MB → 19 MB; new upload key `CN=AuraSpend` (the old cert
> carried the maintainer's name and home city), secrets rotated, signed prod
> AAB/APK rebuilt. **Pending user actions: Play "Reset upload key" + re-upload
> the AAB; GitHub Support purge of cached PR commits; delete the local
> pre-scrub backup.**
> Before: `docs/handoffs/0019-play-marketing-assets.md` — the 0.1.1 listing's
> screenshots are now seven **composed ads** (1080×1920) built with the
> app-store-screenshots editor, plus a refreshed feature graphic; both uploaded
> to Play and saved as draft (**still not sent for review**). Captures redone at
> native 1080×2400 (the old 9:16 letterboxing explained and dropped); README now
> documents Drive backup per install source; `tools/seed_demo_data.sh` launch
> fixed. The SMS/Call-log permissions declaration is **filled** (use case "SMS
> based money management", still not submitted). Watch for: no Hindi screenshot
> variant yet.
> Before: `docs/handoffs/0018-dev-prod-flavors.md` — flavors renamed
> `free`/`play` → **`dev`/`prod`**, exactly four variants (the `benchmark` build
> type is gone; `:benchmark` links prod release), CI/tools/docs updated, and a
> public-surface audit that removed false F-Droid claims and unused secrets
> keys. Verified: 274 tests, signed dev+prod artifacts, lint clean on a
> regenerated 78-entry baseline. Watch for: local benchmarks now need the
> release keystore; dev Drive sign-in needs its own OAuth client.
> Before: `docs/handoffs/0017-play-console-setup.md` — 0.1.1 fully **staged** on
> Play (store listing, all 10 app-content declarations, closed test with a draft
> release carrying the signed AAB) but **not sent for review**, by instruction.
> Launch now waits on: send-for-review, the SMS permission declaration that will
> surface during review, 12 testers × 14 days, then "Apply for production".
> Screenshots for Play must be 16:9 or 9:16 — the old `docs/screenshots/*`
> (9:20) are not; the Play-compliant set + listing assets live in `docs/store/`.
> A tag-driven `release.yml` now builds the **signed** Play AAB + prod APK and
> attaches them to a GitHub Release (Release Drafter notes; no Play upload).
> Before: `docs/handoffs/0016-agpl-3.0-relicensing.md` — relicensed to
> **AGPL-3.0-or-later** and cut as 0.1.1. New in-app open-source licenses
> screen (an AGPL §4 requirement the app did not meet), a CLA with copyright
> assignment, `tools/check_license.sh` in CI, and `CHANGELOG.md`. **Do not
> weaken the copyleft, and do not "fix" third-party Apache-2.0 mentions**
> — they are correct. Before: `docs/handoffs/0015-ui-review-fixes.md` —
> on-device UI review. Fixed a
> consent bug where a fresh install **silently downloaded the 22 MB model** and enabled
> auto-read with no prompt; a transparent `TransactionEntryRow` that let the
> swipe-to-delete background show through on every Activity row; a stale
> `spentAmount` column that made Plan report ₹0 while Dashboard reported real spend;
> and unguarded `Enum.valueOf` in `Mappers.kt` where one bad string bricked the
> dashboard. **Consent must be a tap, never a `LaunchedEffect`** (ADR 0008 depends on
> it). A screenshot is a hypothesis, not a measurement.
>
> Before: `docs/handoffs/0014-r8-play-requirements.md` — R8 / Play technical
> requirements. Measured: release bundle is **3.68 MB** uncompressed DEX, so Play's
> Feb 2027 ≥25% optimization/obfuscation/shrinking floor **does not bind** (app clears
> it at ~99% anyway); APK is 16 KB aligned; the JNI classes survive R8 un-renamed.
> Re-run `tools/verify_r8_release.sh` after touching dependencies or keep rules.

## What this project is

AuraSpend is an offline-first personal finance app for Android (Kotlin + Jetpack
Compose). It classifies bank SMS on-device (regex + optional on-device LLM),
tracks expenses/budgets/subscriptions/goals, and backs up to Google Drive.
Apache-2.0, open source.

## Current phase

**On-device AI: a 22 MB encoder, not a generator** — latest is
`docs/handoffs/0013-encoder-runtime.md`. The runtime is `EmbeddingClassifier`
(nearest-centroid over cosine, ONNX Runtime, 22 MB int8 MiniLM), which replaced
a 468 MB Qwen/llama.cpp. Five runtimes were measured; the measured table is in
`docs/evals/README.md` and the reasoning in 0011–0013. Before that:
`0010-ui-revamp.md`.
Money is locale/currency-correct (explicit lakh-crore grouping), all hardcoded
font sizes are on the Material 3 scale, `SYSTEM` theme is the default, AMOLED has
its own semantic colours, there is an error state, all 12 screens carry heading
semantics, EN/hi have full key parity, and the design-system rules are enforced
by `DesignSystemGuardTest` + `ErrorStateWiringTest`. `ClassificationScreen` is
split into three files. (Screenshot tests were removed in handoff 0024 — visual
changes are verified by hand in all three themes.) Before that:
`0009-crash-paths-and-safety-nets.md`
(crash paths, atomic restore, release signing, CI safety nets), then
`0006`–`0008`. Remaining work is in 0010 §4 — wire `AuraErrorState` into screens,
fold the hand-rolled stat grids onto `AuraStatTile`, split `ClassificationScreen`.
Do not start new work without reading the latest handoff.

## Module map

| Module | What lives there |
|---|---|
| `:app` | Everything (UI, domain, data) |
| `:benchmark` | Macrobenchmark module for cold-start and baseline-profile generation. Not shipped, not part of the app. |

On-device inference is ONNX Runtime (an AAR, not a module). The `:llama` module
and the `third_party/llama.cpp` submodule were removed in handoff 0013 along with
the Qwen GGUF they served.

Kotlin source root: `app/src/main/java/com/awbuilds/auraspend/`

| Package | Contents |
|---|---|
| `core/` | Cross-cutting helpers (`AuraLog`, boundaries) |
| `data/` | Room, classification pipeline, AI, Drive, privacy |
| `domain/` | Models, repository interface, use cases |
| `ui/designsystem/` | Aurora tokens + reusable components (**use these**) |
| `ui/theme/` | Color palette, typography, shapes, `AuraSpendTheme` |
| `ui/<feature>/` | Feature screens (home, transaction, classification, …) |

## Commands

```bash
./gradlew :app:compileProdDebugKotlin     # fast compile check
./gradlew testProdDebugUnitTest           # unit tests (required before handoff)
./gradlew :app:lintProdDebug               # new issues fail; 64 pre-existing baselined
./gradlew assembleDevDebug                # dev APK (fast inner loop)
./gradlew assembleProdDebug               # prod APK (what ships; native build)

# Device-only paths (ONNX JNI, SQLCipher's migration) — no phone required: the
# local arm64 AVD boots headless and runs the instrumented suite in ~1 minute.
# If the install dies with INSTALL_FAILED_INSUFFICIENT_STORAGE the AVD's /data
# is full; relaunch the emulator with `-wipe-data`.
"$ANDROID_HOME/emulator/emulator" -avd Android_17 -no-window -no-audio \
    -no-boot-anim -gpu swiftshader_indirect -no-snapshot-load &
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDevDebugAndroidTest
```

Flavors: `dev` (development — `.dev` application id, "AuraSpend Dev" label),
`prod` (the Play build and the GitHub-release APK) and `fdroid` (F-Droid — no
Google Play Services, so no Drive backup: ADR 0012, the one documented feature
exception). dev/prod are feature-identical; see ADR 0010.
The Play paywall was removed in Phase 0 — do not reintroduce premium gating.

**Signed releases:** keystore credentials live in `secrets.properties`
(gitignored, see `secrets.properties.example`). Without them `assembleProdRelease`
still succeeds but produces `app-prod-release-unsigned.apk` and logs a warning —
check the filename before calling a release build shippable.
Tag runs of `release.yml` also upload the signed bundle to Google Play's
closed-testing track (`PLAY_TRACK` overrides). The Play credential is a
service account: **never record its account, project or key anywhere in this
repository** — not in docs, handoffs, comments, commit messages or workflow
logs. Reference it only as the `PLAY_SERVICE_ACCOUNT_JSON` secret.

**Lint:** `app/lint-baseline.xml` pins the pre-existing issues (64 rows after the
0026 regeneration) so only *new* ones fail CI. Run `./gradlew updateLintBaseline`
deliberately (and review the diff) when you intentionally fix or add lint
suppressions.

**Baseline profiles:** the shipping variant **removes** the `profileable`
manifest tag (`app/src/prodRelease/AndroidManifest.xml`), because a profileable
release build lets adb take a heap dump of the live process — which holds the
decrypted finance database. Macrobenchmark needs the tag, so run baseline-profile
generation against the `dev` flavor (same code, same R8 output, different
application id): `:benchmark:connectedDevBenchmarkAndroidTest` — see README.

## Hard requirements (non-negotiable)

1. **Errors never crash the UI.** Every IO/platform boundary (Room, network,
   Drive, LLM, CSV, SMS, WorkManager) must catch its exceptions, log via
   `AuraLog`, and degrade to a typed fallback/error state. Never write an empty
   `catch {}`. Always rethrow `CancellationException`. Use the `boundary { }` /
   `boundaryOrNull { }` helpers from `core/AuraLog.kt` for new code.
   **Never put a raw exception message in screen state** — log the cause, then
   report a `UiError` (`ui/core/UiError.kt`) and resolve the string from
   resources. `ErrorStateWiringTest` fails the build if a screen declares an
   `error` field it never renders.
2. **No `!!` in new code.** No `lateinit` where a constructor/`requireNotNull`
   with a real error path works.
3. **Handoff discipline.** Every session ends with a `docs/handoffs/NNNN-*.md`
   entry: what changed, decisions made, verification evidence (commands +
   results), open risks, next step. Every session starts by reading the latest
   handoff.
4. **Memory discipline.** Keep `CLAUDE.md` as a ≤100-line hot cache. Durable
   knowledge (decisions, glossary, project state) goes to `memory/`.
   **Never regress the OTP/alert filter.** `SmsAutoClassifier.isOtpOrAlertMessage`
   must skip an OTP only when the message has no amount *and* no movement verb —
   a real debit that ends in an OTP code is a real transaction, and dropping it
   loses the user's money silently. The hard fraud veto deliberately has *no*
   rescue, because a phishing lure parses identically to a genuine debit.
5. **Design system discipline.** Screens use `ui/designsystem` components and
   `ui/theme` tokens. No raw hex colors, no one-off `fontSize =`, no
   `Modifier.shadow` on content surfaces, no new emoji in chrome. New shared
   UI belongs in `ui/designsystem`, not in a screen file. **These are enforced by
   `DesignSystemGuardTest`** — they fail the build, so a violation is a red test
   rather than a review comment. Touch targets ≥48dp; anything holding text uses
   `heightIn`, not a fixed `height`, so it survives 200% font scale.
6. **Green build.** `:app:compileProdDebugKotlin` and `testProdDebugUnitTest`
   must pass before a handoff. Never commit red.
7. **Branching.** `main` is release-ready, protected and default; day-to-day
   work lands on `dev`. Work branches are short-lived and come off `dev`:
   `phase-N-name` for a planned phase, otherwise `<type>/<topic>` with
   `feat|fix|chore|docs|perf|refactor`. Delete a branch once merged; merge
   `dev` into `main` for a release. Small commits, conventional commit
   subjects (`feat:`, `fix:`, `chore:`, `docs:`, `perf:`, `refactor:`).
8. **Compiler warnings are triage items, not noise.** Read every `w:` line in
   a build before moving on. Two user-visible bugs in this project were caused
   by deprecation warnings that were present in a green build and read past
   twice: `rememberSwipeToDismissBoxState(confirmValueChange = ...)` is
   *deprecated without replacement*, so the callback stopped being called
   entirely — swipe-to-save and swipe-to-delete both became silent no-ops that
   still animated and still showed their coloured backgrounds, so the UI looked
   alive while doing nothing. A warning that says "deprecated without
   replacement" is a behaviour change, not a style note. Fix it, suppress it
   with a written reason, or record why it is safe — never just scroll past.
   `./gradlew :app:compileProdDebugKotlin --rerun-tasks 2>&1 | grep "^w:"` lists
   them.
9. **AuraSpend is AGPL-3.0-or-later. Do not weaken the copyleft.** The project
   relicensed from Apache-2.0 in 0.1.1 (ADR 0009). Two things follow, and both
   are easy to violate by accident:
   - **A new dependency must be AGPL-3.0-compatible.** Everything currently in
     the graph is Apache-2.0 or MIT, which flows one-way into AGPLv3. A GPL-2.0-
     only or non-commercial dependency would make the app undistributable, so
     check the candidate's licence *when it is proposed*, not at merge time.
     Apache-2.0 and MIT are fine.
   - **Never strip the notice.** A derivative work must keep the notices and add
     its own statement of modification (AGPL §4/§5). `docs/terms.html` used to
     carry an Apache-era clause forbidding reverse engineering; that clause was
     **removed** because AGPL §3 forbids anti-circumvention restrictions, and it
     must not come back. The trademark paragraph is separate and does stand.
   Run `./tools/check_license.sh` after touching anything licence-adjacent. It
   fails if the licence string is inconsistent across `LICENSE`, `README`, both
   string locales, the splash footer, the website and the legal pages, or if a
   dependency has no third-party attribution. The licence had silently drifted
   across eleven files before that check existed, so it is worth trusting it over
   your memory of where the licence is mentioned. **A mention of "Apache-2.0"
   about a dependency is correct and must not be "fixed"** — the bundled font is
   OFL, ONNX Runtime is MIT, AndroidX and the Google libraries are
   Apache-2.0, and the runtime-downloaded model is Apache-2.0. A global
   find-and-replace makes false licensing claims about third parties.
   `./tools/check_privacy_claims.sh` is the sibling check for the app's *copy*:
   it fails when an absolute claim ("nothing leaves your phone") appears without
   its exception on the same line. The in-app subtitle said exactly that, two
   features after Drive backup made it false — a promise that drifts by omission
   needs a tripwire, not another review comment. Add the exception, do not
   delete the claim.

## Design system quick reference

| Need | Use |
|---|---|
| Card | `AuraCard` (`Filled` / `Tonal` / `Outlined` / `Glass`) |
| Money text (animated) | `AnimatedMoney` + `AuraType.money*` |
| Category badge | `CategoryAvatar` |
| Filter/range selector | `AuraSegmentedControl` |
| Progress / donut / area | `AuraProgressRing`, `AuraDonutChart`, `AuraAreaChart` |
| Loading placeholder | `AuraSkeleton` |
| Empty state | `AuraEmptyState` |
| Error / retry state (no data to show) | `AuraErrorState` |
| Inline error banner (data still visible) | `AuraErrorBanner` |
| Metric tile (label + value) | `AuraStatTile` (`contained = false` inside a card) |
| Money text | `formatMoney` (locale-aware) / `AnimatedMoney` |
| Spacing | `AuraSpacing.*` (4dp grid, `gutter = 20dp`) |
| Motion | `AuraMotion.*` (never invent durations) |
| Brand gradient | `AuraGradients.aurora` |

## Money, theme and i18n invariants

- **Never hardcode a currency symbol or a locale.** Use `formatMoney` /
  `formatMoneyCompact` from `ui/designsystem/Money.kt`. Indian lakh/crore
  grouping is implemented by hand on purpose — `NumberFormat` and `#,##,##0` both
  return *Western* grouping for `en_IN`/`hi_IN` on this project's JDK, so
  reverting to the platform formatter reintroduces a real bug.
- **Currency comes from the theme** (`LocalCurrencyStyle` / `MoneyConfig`), set
  from the `currency_code` preference and defaulting to the device locale. Do
  not thread a currency parameter through call sites.
- **`AppThemeMode.SYSTEM` is the default.** Parse preferences with
  `AppThemeMode.fromName`, never `valueOf`, which throws on a corrupt value.
- **Every user-facing string needs both `values/` and `values-hi/`.** Key parity
  is checked; a new string without its Hindi twin is a defect.
- **Localize `contentDescription`.** Hardcoded English in chrome is untranslatable.
- **Apostrophes in `strings.xml` must be escaped as `\'`** (Android syntax).
  A bare `'` or a `&#39;` entity fails the resource build with a misleading
  "Invalid unicode escape sequence" error.

## Storage and restore invariants

- **Never write to public/external storage.** `minSdk = 30` forbids it without
  `MANAGE_EXTERNAL_STORAGE`; use the Storage Access Framework uri directly
  (see `CsvManager.exportToCsv`). **Also never sweep the SMS queue:**
  `unrecognized_sms` has a 30-day retention (`purgeOlderThan`, called on the
  periodic worker tick), while `sms_messages` deliberately has none — it is the
  visible history the SMS tab and Smart Add show the user.
- **`sms_messages` row statuses are a status channel, not flags.** `NEW` is
  work to do, `CLASSIFYING` is the in-flight marker (swept back to `NEW` by the
  next pass if a pass died mid-row), and the terminal states are
  `SAVED`/`SKIPPED`/`FAILED`. `PROCESSED` is where the undo paths **park** a row;
  it must never be swept or auto-processed, because re-processing it would
  resurrect the decision the user reversed (0028 pinned that by test).
- **Drive restore is a replace, not a merge**, and it runs inside one
  `BackupRestoreManager` Room transaction. Do not "restore" by calling repository
  save methods directly from a composable.
- **`BackupData` must carry every user-owned table** (transactions, categories,
  budgets, subscriptions, SMS queue, savings goals, classification memory,
  unrecognized SMS). Format is v4; deserialization must stay tolerant of
  missing/legacy fields.
- **Auto Backup is off for the DB.** `res/xml/backup_rules.xml` and
  `data_extraction_rules.xml` exclude `auraspend_db`, prefs and the model
  directory; keep them that way — the app promises data stays on-device.
- **Every write path goes through `TransactionRepositoryImpl.sanitized()`**, the
  single `SensitiveDataMasker` choke-point. New write paths must not bypass it.
  Drive restore masks the SMS queue and the unrecognized-message list on the way
  in too (`BackupRestoreManager`), because a v1–v3 backup predates the masker.
- **A transaction write is update-if-present, else insert-if-absent.** The DAO
  inserts with `OnConflictStrategy.IGNORE`, never `REPLACE`: SQLite implements
  REPLACE as delete-then-insert, and `sourceSmsId` is unique, so a second save of
  the same SMS-linked transaction (worker/triage race, CSV re-import, re-processed
  queue row) used to *delete the row the user had edited* and rebuild it from the
  pipeline's parse. An edit arrives with the row's existing id, so `saveTransaction`
  updates first and only inserts when no row matched.
- **The database is encrypted at rest, and that must never degrade silently.**
  `AppDatabase.getInstance` takes the passphrase **and** the open-helper factory
  as parameters precisely so there is no "Keystore unavailable → carry on in
  plaintext" path: production passes SQLCipher with `DbPassphrase`'s
  Keystore-derived key, and only `RobolectricWorkManagerApp` substitutes (a fixed
  key and `null`), because neither the AndroidKeyStore provider nor
  `libsqlcipher.so` exists on the JVM host. A silent fallback here would undo the
  feature silently on exactly the devices it protects. The passphrase is derived
  (non-exportable Keystore HMAC over a fixed seed, base64'd so it survives
  `ATTACH … KEY ?` as a bound string) and must never be written to disk, logs or
  a backup. The plaintext upgrade runs once, before any Room handle exists, via
  `SqlCipherMigrator` behind the `SqlCipherGateway` seam — keep the policy in the
  JVM suite and the recipe in `SqlCipherDeviceTest`, and remember the rule that
  produced the split: a JNI-backed path that cannot load on the host is not
  "tested" when it only compiles.
  **Three things the device tests found that no JVM test can** (0046), each a
  release-blocking defect, each worth re-checking on any SQLCipher bump:
  nothing in the artifact calls `System.loadLibrary`, so
  `SQLCipherLibrary.ensureLoaded` must run before the first open (otherwise
  `UnsatisfiedLinkError: … nativeOpen`); the SQLCipher CLI recipe of attaching
  an *encrypted target* from an unkeyed connection fails on Android with
  `SQLITE_CANTOPEN`, so the export goes the other way — keyed target, plaintext
  source attached with `KEY ''`, `sqlcipher_export('main', 'plaintext')`; and
  `SupportOpenHelperFactory` ignores Room's journal mode, so WAL must be
  requested in the factory or the app silently runs on a rollback journal.
- **The inbox scan has three cursor values, not one.** `newestIngested` (a
  high-water mark), `historyBefore` (the downwards walk) and `historyWalkDone`.
  A single watermark advanced to `newest - 1` permanently stranded every message
  older than the newest 500 — a fresh install never ingested its history, and
  "advance only on a short batch" stalls instead, because the provider query can
  only return the newest page. The walk pulls one page per pass and only when the
  live queue is empty, so a backfill never outruns the classifier.

## On-device AI invariants

- **The runtime seam is `OnDeviceClassifier`** (`data/ai/OnDeviceClassifier.kt`),
  which describes the *job*, not a mechanism: `extract(smsBody, categoryIdByName)
  -> SmsExtraction?`. Do not widen it back to prompt-in/text-out — a
  grammar-constrained runtime has no meaningful "prompt", and its parse guarantee
  and calibrated score cannot cross such an interface. `LocalLlmProvider` is the
  only place a backend is chosen, and `UnavailableClassifier` is the only
  degradation path.
- **`SmsExtraction.confidence` is nullable and must stay honest.** null means
  the runtime has no calibrated head. Never fabricate a score.
- **`SmsExtraction.isTransactionProbability` is confidence in the verdict the
  runtime *emitted*, not the probability of the transaction class.** On a "not a
  transaction" verdict the two differ, and reporting the raw class probability
  makes `AiSignalFusion.mayDiscard` arithmetically unreachable — it is below 0.5
  whenever the verdict is negative, so the 0.9 gate can never fire, and the
  measured "0 transactions destroyed" then comes from dead code rather than from
  a trustworthy runtime. Convert with `SmsExtraction.verdictConfidence(...)`, and
  keep the eval report's `probabilitySemantics` field honest: `FusedAiEval`
  converts `p_transaction` reports on load for exactly this reason.
- **The bundled centroid asset is little-endian and SHA-256 pinned.** The writer
  (`tools/build_embedding_asset.py`) and the reader (`EmbeddingCentroidCodec`)
  must agree on byte order; they did not, and the flagship encoder silently
  returned null for every message while the readiness UI said "ready".
  `EmbeddingClassifierAssetTest` decodes the real asset and fails on a digest or
  layout change, and `OnDeviceClassifier.isOperational` keeps readiness honest —
  a runtime whose session, vocabulary or centroids failed to load is *not* ready.
  **Readiness is not proof this path runs**: the vocabulary file name and a
  missing `token_type_ids` input each kept the encoder inert behind caught
  exceptions while everything "loaded" (0036), and nothing in JVM tests can
  catch that (the ONNX session never runs there) — only an on-device inference
  smoke proves it, and a model swap must re-run that smoke.
- **Any new runtime must beat the measured floor, *fused*.** Baseline with the
  model switched off: **97.6 % exact match**, type 98.8 %, category 100 %,
  merchant 98.8 %, amount 98.8 %, date 100 % (82-case corpus, re-measured
  2026-10-17; `RegexBaselineEval`, `./gradlew :app:classificationBaseline`).
  One accepted false veto remains (`canara_debit_10000`, the documented phishing
  trade-off).
  `RegexBaselineEvalTest` fails if the regex layer regresses. A model that beats
  that floor *standalone* may still be worse in the pipeline — Qwen and
  SmolLM2 both score 29.2 % standalone and reached opposite conclusions. Judge
  every candidate with `FusedAiEval`.
- **The `dev`/`prod` flavors are feature-identical.** No feature may live in a
  flavor source set that is absent from `app/src/main/`; only configuration
  (application-id suffix, label, signing) may differ. The **`fdroid`** flavor is
  the documented exception (ADR 0012/0013): it omits the non-free Google Play
  dependencies, so Drive backup and the Play in-app update check are absent
  there. **Neither build may carry
  analytics, ads or any telemetry** — the app's on-screen claim is that bank SMS
  never leaves the device, and an SDK in one build would make that claim false
  and the store listing self-contradictory. There is no paywall and no in-app
  purchase; every feature ships in every build. Do not reintroduce premium
  gating (ADR 0007), and do not add a Play-only capability without superseding
  ADR 0010 (the fdroid exceptions are ADR 0012/0013).
- **A model may never delete a transaction on a bare boolean.** A model "not a
  transaction" verdict nulls `amount` and `type` in `AiSignalFusion`, and the
  pipeline's unresolved-fields gate then drops the row — so a false veto
  *deletes* a real debit rather than mis-filing it. Measured rates of that
  failure: 43/46 (SmolLM2), 8/46 (Qwen2.5-0.5B), 5/46 (FunctionGemma-270M),
  **0/46 (the shipped encoder)**. `mayDiscard` requires **both**
  `isTransactionProbability >= 0.9` **and** `base.amount == null`; confidence
  alone is not enough, because the regex layer reading a message correctly is
  the stronger signal. A runtime that cannot supply a probability reports `null`
  and keeps the old behaviour — never fabricate one, since a higher probability
  is licence to discard. This is also the reason to prefer a **non-generative**
  classifier here: an encoder emits no text, so it cannot fabricate the verdict
  in the first place.
- **Classify by sender first.** The SMS sender ID is free, reliable routing
  metadata (`BankParserRegistry.parserFor`). Per-bank parsers live in
  `data/classification/bank/`. `CanaraBankParser` must never read the
  `Dial 1930 to report cyber fraud` footer — it decorates every genuine Canara
  debit. A message no parser claims but that carries an amount and a movement
  verb goes to the `unrecognized_sms` table, never silently into the bin.
- **Model downloads are SHA-256 verified** against a pinned digest in
  `ModelConstants` before the file is moved into place. A size check alone cannot
  detect a corrupt or substituted file, and the model is executed on-device.
- **A prebuilt native library must pass `tools/audit_native_runtime.sh`**
  (no network imports, no service URLs, no `dlopen`, no telemetry vendor
  strings) before it is wired in, and the audit must be re-run on every version
  bump — a pass is only valid for the digest it was run against. Two traps it
  must keep avoiding, both of which silently produce a meaningless result:
  POSIX ERE has **no word boundary**, so `grep -E '\b'` matches a backspace and
  finds nothing; and a release `.so` is usually **stripped**, so `nm` returns
  empty and the scan reads nothing at all. The script now falls back to `nm -D`,
  validates that it found symbols *before* reporting a clean result, and filters
  documentation URLs (arXiv, scipy, NVIDIA docs) and mangled C++ names containing
  "Telemetry" — a real library embeds both, and flagging them would train people
  to ignore the check. ONNX Runtime arm64 passes with zero network imports; the
  single loader exception is its execution-provider plugin loader (`dlopen`/
  `dlsym` on the `libonnxruntime_providers_*.so` shipped in the same APK),
  permitted by name and only when those plugin names are embedded in the
  artifact — any other loader symbol still fails. A second, **digest-pinned**
  exception exists for SQLCipher's `libsqlcipher.so`: its `dlopen`/`dlsym` come
  from SQLite's loadable-extension support, which nothing in
  `sqlcipher-android`'s Java layer, Room or this app declares or calls. It is
  granted only by an `APPROVED_LOADERS` entry keyed on
  `<library>|<sha256>|<evidence-regex>|<reason>` — the same library name with a
  different digest gets nothing, which is what makes a version bump block. Add
  the new digests only after re-reviewing the artifact; never widen an entry to
  a glob. When the exception list grows, the *evidence* pattern is what matters:
  a substring match that happens to be present is not evidence of anything.
