# Decisions

Durable decisions with context. Latest first.

## D24 — 0.1.6 ships with pre-terracotta store visuals (2026-10-17)

The app UI is terracotta (0048), but the owner chose to release 0.1.6 with the
existing purple icon, feature graphic and screenshots rather than regenerate
them. This is an accepted mismatch for one release, not an oversight. The
canonical Play copy is `docs/store/play-listing-en-US.md`; the tag workflow
uploads the AAB and `docs/store/whatsnew/` notes only, not the listing text, so
the title/short/full description still need to be pasted into the Play Console.
Regenerate the visuals from the terracotta build in a later release.

## D23 — The classification floor is not parse accuracy; routing replaces instead of specializing (2026-10-17)

Read-only audit; the full report is
`docs/reviews/2026-10-17-message-classification-gap-analysis.md`. Four durable
findings:

- **The 31.7 % floor is not parse accuracy.** `ClassificationEval.Case` has no
  amount or date; `run()` scores a null result as a miss on every field, so 24
  correct rejections count as failures. Scored properly, the same five fields
  are 50/82 exact and 78/82 `isTransaction`. `falseVetoes()` only counts
  `predicted.isTransaction == false`, but the regex path returns null for a skip,
  so “0 false vetoes” cannot see the HDFC “If not you” debit that
  `conditionalFramingPattern` drops. `isFraudLineVeto` excuses every non-OTP
  alert drop; `p2p_upi_debit_person_refno` is the counterexample.
- **Sender routing replaces the global parse.** `TransactionClassifier` accepts
  a routed parse when amount/type are non-null, so a per-bank parser can drop a
  merchant/date/category the global parser got. Measured: single-line Axis loses
  `SUNRISE DINER` and its time; Canara NEFT loses `ACME SOLUTIONS LIMITED` and
  its date. Merge routed over global, or fall back on a field regression.
- **The alert filter is sender-blind.** `isOtpOrAlertMessage(message)` never sees
  the sender, so a known bank's own “If not you” tail is treated like a phishing
  lure. Pass the sender and keep the hard veto for unknown senders.
- **The domain model cannot represent a transfer.** Only `INCOME`/`EXPENSE`;
  totals sum all expenses, so a “transfer to self” inflates spending and a
  two-leg own transfer inflates both totals. Ingest is also a 22-word body
  allowlist (no `₹`/`Dr`/`Cr`/`sent`/`transferred`/ISO currency), and
  categorisation is a parser side effect with two divergent maps.

**Rule:** fix the eval first (correct rejections, amount/date, a real false-veto
counter, per-bank floors); do not tune the parser against 31.7 %.

**Resolved in 0050 (same day).** The eval now scores amount/date and correct
rejections; the floor is **80/82 (97.6 %) exact**, type 98.8 %, category 100 %,
merchant 98.8 %, amount 98.8 %, date 100 %, with one accepted false veto
(`canara_debit_10000`). Sender routing merges over the global parse, the HDFC
"If not you" tail is rescued by sender context, own transfers are excluded from
every aggregate, and merchant/category/ingest/memory gaps are closed. 616/616
tests. The structural follow-ups (first-class TRANSFER/INVESTMENT, full
multi-currency, a user-rules UI, Hindi) are listed in `0050`.

## D22 — The design language is terracotta clay; the `Aurora*` names stay (2026-10-17)
Full visual revamp, UI layer only: clay `#A64B2A` → ember `#D2764A` → sand
`#F2D8BC`, olive-sage secondary, ochre tertiary, warm cream `#FCF7F2` and warm
near-black `#17110D` canvases; shape scale 10/14/20/26/32; a new illustrated
`AuraMark` (clay pot + coin, Compose `Canvas`); `AuraCardStyle.Hero` for the one
gradient surface per screen. Three rules worth keeping:

- **Symbol names stay `Aurora*`.** Renaming a token across 40 files buys nothing
  and breaks every diff; "Aurora" is the design-system name, terracotta is its
  palette. `docs/design/aurora.md` states this at the top so nobody "fixes" it.
- **Fraunces is display-only; money is always Manrope.** Fraunces has no `tnum`,
  and animated money that jitters is a correctness bug here — `AuraType.money*`
  all pin Manrope + `tabular figures`. Keypad digits are Manrope too
  (`AuraType.keypadDigit`): the display serif uses old-style figures, which makes
  a 0–9 pad uneven.
- **Default category colours were retuned** (`DefaultCategories.kt`) from the
  2014-era Material 500s to the same hue families muted ~30 %. These are *seed*
  values only — `missingDefaultCategories` never rewrites an existing row — so
  the change reaches new installs and leaves stored user categories alone.

The session also re-taught D21 the cheap way: the full JVM suite, lint and both
shell gates passed while (a) the selected bottom-nav *label* was white-on-cream
(the gradient pill only wraps the icon), (b) the keypad digits rendered in the
display serif, and (c) the notification-permission dialog still wore the old
purple because `res/values/colors.xml` (`android:colorAccent`) had not been
recoloured. None of those is reachable from a unit test — they were found by
installing the build and looking at it.

## D21 — A device-only path gets run on the emulator before it is called verified (2026-10-15)
PR #77 passed 549 unit tests, lint, the licence and privacy gates, the native
audit and the R8 gate — and still shipped three release-blocking defects, all
found within minutes of running the instrumented suite on the local arm64
emulator: nothing loads `libsqlcipher.so` (the app must call
`System.loadLibrary`); SQLCipher's documented ATTACH direction — encrypted target
attached from an unkeyed connection — fails on Android with `SQLITE_CANTOPEN`,
so the export must run as keyed target + plaintext source attached with
`KEY ''`; and `SupportOpenHelperFactory` ignores Room's journal mode, so WAL has
to be requested in the factory. The rule: when a change touches a
`.so`-backed path, "it compiles and the JVM suite is green" is not evidence —
boot the AVD (`Android_17`, headless, ~1 minute) and run
`:app:connectedDevDebugAndroidTest`. `-wipe-data` if `/data` is full.

## D20 — A native-loader exception is granted per *binary*, pinned by digest (2026-10-14)
`tools/audit_native_runtime.sh` blocked SQLCipher because `libsqlcipher.so`
imports `dlopen`/`dlsym`, and the only named allowance was ONNX Runtime's
execution-provider loader. The imports are real and belong to **SQLite's
loadable-extension support**: the strings `sqlite3_load_extension` /
`sqlite3_extension_init` / `load_extension` are in the binary, and
`sqlcipher-android`'s Java layer neither declares nor calls any of them — Room
does not either, and Android leaves extension loading disabled by default. So the
script grew `APPROVED_LOADERS`, keyed on `<library>|<sha256>|<evidence-regex>|<reason>`:
the same library name with a different digest gets nothing, and the *evidence*
pattern must be present in the binary as well. A one-byte-mutated copy still
blocks, which is the property that makes the entry evidence rather than a
rubber stamp. Consequence to remember: a dependency bump now **fails the gate**
until someone re-reviews the artifact — that is the design, not an annoyance.
Never widen an entry to a glob.

## D19 — Encryption that can silently switch itself off is not encryption (2026-10-14)
The database is encrypted at rest with SQLCipher Community Edition
(BSD-3-Clause, AGPL-compatible). Three decisions came out of the work:

1. **The passphrase is derived, never stored** — a non-exportable Android
   Keystore HMAC-SHA256 key over a fixed seed, base64-encoded because
   `ATTACH … KEY ?` binds the passphrase as a *string* and raw random bytes
   cannot survive that round-trip. A copy of the app's files cannot produce it.
2. **`AppDatabase.getInstance` takes the passphrase and the open-helper factory
   as parameters.** The alternative — hiding `DbPassphrase.get()` inside and
   falling back when the Keystore is unreachable — would have meant that on
   exactly the compromised devices the feature protects, the database quietly
   becomes plaintext. Parameters make the caller choose; only
   `RobolectricWorkManagerApp` substitutes, visibly.
3. **Policy tests in the JVM, the recipe on a device.** `libsqlcipher.so` cannot
   load on the host (`UnsatisfiedLinkError`), so the migration sits behind
   `SqlCipherGateway`: `SqlCipherMigratorTest` covers what to migrate, what to
   leave alone, and that **a failed export leaves the original file**;
   `SqlCipherDeviceTest` proves the real upgrade, byte-level. CI now compiles
   `androidTest`, because `R8ReleaseSmokeTest` sat broken since 0026 and nobody
   noticed for five releases.

Also: the artifact is `net.zetetic:sqlcipher-android` (4.19.1). The
similarly-named `net.zetetic:android-database-sqlcipher` on Maven Central is
4.5.4 and ships the **legacy** `net.sqlcipher.*` API — Zetetic's own S3 repo
answers 403 and JitPack only has 3.x tags, so both look like plausible fixes and
neither is.

## D18 — A privacy claim states its exception, and a script enforces it (2026-10-11)
The AI subtitle said "Nothing leaves your phone" and onboarding's second page
said a local AI "never uploads your data". Both were written before Drive backup
existed and were still on screen afterwards, while a user-initiated backup
uploads the SMS queue (masked, but message text) as plain JSON. The copy is now
qualified — "the only upload is the Drive backup you start yourself" — and
`tools/check_privacy_claims.sh` (wired into the PR gate beside
`check_license.sh`) fails when an absolute claim appears without its exception on
the same line. Add the exception; do not delete the claim.

## D17 — A duplicate `sourceSmsId` is a no-op, never a REPLACE (2026-10-11)
`insertTransaction` used `OnConflictStrategy.REPLACE`, and `sourceSmsId` is
unique. SQLite implements REPLACE as *delete the conflicting row, then insert*,
so a second save of the same SMS-linked transaction (worker/triage race, CSV
re-import, a re-processed queue row) deleted the row the user had edited and
rebuilt it from the pipeline's parse — losing their merchant, category and note.
An edit arrives with the row's existing id, so `saveTransaction` now updates
first and inserts only when no row matched, with `IGNORE` on the unique index.
The pipeline's `UNIQUE constraint failed` branch was unreachable under REPLACE
(it deleted instead of throwing) and is gone.

## D16 — The inbox scan keeps three cursors, not one (2026-10-11)
A single high-water mark advanced to `newest - 1` after a `DESC LIMIT 500` scan
declared the whole inbox covered after its first 500 bank-like messages: on a
fresh install every older transaction was never ingested, never classified and
never surfaced. "Advance only on a short batch" does not fix it either — the next
scan re-reads the same newest page for ever. `SmsIngestCursor` therefore carries a
high-water mark, a downwards-walk cursor and a done flag; the walk pulls one page
per pass and only when the live queue is empty, so a backfill never outruns the
classifier. `SmsIngestPlannerTest` drives it against a simulated 1,200-message
inbox.

## D15 — A probability means "confidence in the verdict given" (2026-10-11)
`EmbeddingClassifier` reported `pTransaction`, the probability of the
*transaction* class, in `isTransactionProbability`. `AiSignalFusion.mayDiscard`
reads that field as confidence in the answer actually emitted, and on a "not a
transaction" verdict `pTransaction` is below 0.5 by construction — so the 0.9
discard gate could never fire. It was dead code, and the recorded "0 of 46
destroyed" came from it never being consulted rather than from the encoder being
trustworthy. The runtime now reports `SmsExtraction.verdictConfidence(...)`, the
eval report declares its `probabilitySemantics` (`p_transaction`, converted on
load), and `FusedAiEvalTest` fails if the gate becomes unreachable again.

## D14 — The bundled centroid asset is little-endian, and readiness must be real
(2026-10-11)
`tools/build_embedding_asset.py` writes little-endian; `EmbeddingClassifier` read
big-endian. The dimension decoded as `0x80010000`, the metadata slice ran off the
end of the 23 KB file, the boundary swallowed it into `model = null`, and the
flagship 22 MB encoder returned null for *every* message in production while the
readiness UI and the log both said "ready". Nothing caught it: the build tool
verified its own writer, the "shipped encoder" eval replays a recorded JSON, and
no test decoded the asset. The reader is now little-endian and bounds-checked
(`EmbeddingCentroidCodec`), the digest is pinned in `ModelConstants`,
`EmbeddingClassifierAssetTest` decodes the real asset, and
`OnDeviceClassifier.isOperational` makes `LocalLlmProvider.isReady()` honest.

## D13 — Consent is a tap, never a `LaunchedEffect` (2026-09-29)
`ClassificationScreen` once ran `LaunchedEffect { ConsentResult(true) }` the instant
`consentRequired` became true. On a fresh install, opening Smart Add silently
downloaded the 22 MB model, enabled the `AutoDetect` auto-read toggle and ran an inbox
scan — with nothing rendered. Verified on device: 23,026,053 bytes in `files/models`
after one tap, no prompt on screen. It also broke the promise ADR 0008 is built on.
Consent is now a rendered inline card with a real **Not now** / **Add** action, and
declining clears the offer *without* recording consent. Reusable rule: **never
auto-accept on the user's behalf, and never let a `LaunchedEffect` stand in for a UI
affordance** — if a string exists to explain something, something has to be shown.

Corollary from the same review, worth as much as the bug: **a screenshot is a
hypothesis, not a measurement.** Three "findings" that looked clear in a screenshot
were wrong — the release build looked to have zero ONNX classes (wrong package name),
the minus glyph looked inconsistent (a font-size difference), and a form field looked
unreachable (it scrolled fine). Two were caught only by going back to the bytes.

## D12 — Two ABIs, and ChromeOS is deliberately unsupported (2026-09-29)
The app's size is native library, not code: ONNX Runtime's `.so` was 71.8 MB of
a 78.8 MB APK while the DEX was 3.7 MB, so the ABI list is the only size lever
that matters. Shipping **arm64-v8a + armeabi-v7a only** takes the release AAB
from 35.4 MB to **20.5 MB**. Dropped: 32-bit `x86` (no device, Android Studio no
longer ships an x86 image) and **`x86_64`, which means the app is uninstallable
on Chromebooks** — ChromeOS runs Android apps on x86_64, and that is a supported-
form-factor decision, not an oversight. Lint's `ChromeOsAbiSupport` is disabled
deliberately to express it; the comment in `build.gradle.kts` gives the two-step
way to reverse it. Real per-device download is unaffected (~23 MB) because Play
serves arm64 only — the saving is for F-Droid and universal APKs.

The generalisation, learned the hard way: **a dropped ABI is not always a lost
developer convenience.** My first pass framed the x86_64 removal as "emulators vs.
size" and was wrong; the emulator half was convenience, the ChromeOS half was
users. Check which one you are actually trading away before treating size as free.

## D11 — Play's Feb 2027 R8 thresholds do not bind, and here is the number (2026-09-29)
Google requires ≥25% optimization/obfuscation/shrinking from Feb 2027, but only for
bundles over **10 MB uncompressed DEX**. AuraSpend's release bundle is **3.68 MB**
(R8 takes the debug build's 71.5 MB down to that), so the requirement does not apply —
and it clears the thresholds anyway at ~99% each. Verified by
`tools/verify_r8_release.sh`, which also confirms 16 KB alignment and that the JNI
classes survive un-renamed. Re-run it after touching dependencies or keep rules; it is
**not** in CI yet, deliberately deferred.

Corollary worth keeping: **the ONNX Runtime AAR ships no consumer ProGuard rules and
registers its natives dynamically in `JNI_OnLoad`**, which looks like a guaranteed
release crash. It is not — AGP's default file keeps any class declaring a `native`
method, names included. Do **not** "fix" this with a broad
`-keep class ai.onnxruntime.** { *; }`; it would suppress the obfuscation score for
nothing. Note the package is `ai.onnxruntime`, not `com.microsoft.onnxruntime`.

## D10 — Score the system, never the model (2026-09-29)
The measured lesson of the runtime bake-off, and the one most likely to be
relearned expensively. Qwen and SmolLM2 both scored **29.2% standalone** and
reached opposite conclusions once fused into the pipeline, because the pipeline
is where a veto becomes a deletion. Judge every candidate with `FusedAiEval`
against the fused regex floor (13.8% exact, type 95.4%, category 36.9%), not
with a standalone prompt benchmark. Corollary: **size does not predict
quality.** Across a 21× size range the destruction rate ran 43 → 8 → 5 → 0,
non-monotonic — the 4.7×-smaller SmolLM2 deleted *more* than Qwen.

## D9 — A model may never delete a transaction on a bare boolean (2026-09-12 → refined 2026-09-29)
`AiSignalFusion` nulls `amount` and `type` on a "not a transaction" verdict, and
the pipeline's unresolved-fields gate then drops the row — so a false veto
*deletes* a real debit rather than mis-filing it. Measured destruction rates:
43/46 (SmolLM2), 8/46 (Qwen2.5-0.5B), 5/46 (FunctionGemma-270M), **0/46 (the
shipped encoder)**. `mayDiscard` therefore requires *both*
`isTransactionProbability >= 0.9` *and* `base.amount == null`. Confidence alone
is not enough, because the regex layer reading a message correctly is the
stronger signal. A runtime that cannot supply a probability reports `null` and
keeps the old behaviour — never fabricate one, since a higher probability is
licence to discard. "Destroyed" outranks "exact match" in every model decision.

## D8 — 22 MB encoder replaces the 468 MB decoder (2026-09-29)
Five runtimes were measured through the production pipeline; a quantised
MiniLM-L6-v2 ONNX **encoder** won. It is the first runtime that is not a text
generator, which matters structurally: an encoder emits no text, so it cannot
fabricate a "not a transaction" verdict in the first place. It is also the only
one that supplies `isTransactionProbability`, the field `mayDiscard` has
required since D9. llama.cpp was removed entirely (`:llama`,
`third_party/llama.cpp`, `llama-lib/`, the GGUF prompt/parser,
`ModelConsentDialog`) rather than kept as a fallback, and the blocking consent
modal was replaced by an inline offer — a 22 MB download does not warrant a
modal. Handoffs 0011–0013.

## D7 — Distribution flavors carry no features, no analytics, no ads (2026-09-29)
Question: should the Play build add analytics, ads and premium gating that the
GitHub/F-Droid build lacks? A standard open-source model, and it was considered
rather than refused. Three findings decided it. (1) The app's promise is an
*on-screen* claim, not a policy page: onboarding says a local AI "never uploads
your data" (wording since qualified to name the user-started Drive backup as the
one exception — see D18; the argument is unchanged, and stronger for being exact),
so an SDK in one flavor makes the Play listing contradict the first
run screen of the same APK. (2) D4 already ruled on gating. (3) Both flavor
source sets were empty, so nothing needed unwinding. Both published builds are
feature-identical; flavors carry distribution config only. No feature may live
in `src/play/` that is absent from `main/` — that invariant is what makes the
free build incapable of being a degraded build, and F-Droid (which builds from
source and accepts only FOSS) requires it. See ADR 0008. Consequence accepted:
no crash insight beyond what users volunteer.

## D1 — Aurora purple replaces Cashew blue (2026-09-12)
The app icon and landing page already use purple (#5E3A8B / #6750A4 / #C4A6E6);
the in-app theme was Cashew blue (#1B447A). The rebuild adopts the existing
brand palette as the source of truth so app, icon and site finally match.
Dynamic color stays available but brand-first.

## D2 — Four tabs + center FAB; Settings behind header avatar (2026-09-12)
Bottom nav becomes Home / Activity / Plan / Insights with the FAB in the
middle. Settings moves behind the header avatar. Rationale: Budgets,
Subscriptions and Goals were buried in Settings or dead-end routes; the new
`Plan` hub makes them first-class while keeping four thumb-reachable tabs.

## D3 — material3 1.4.0 via Compose BOM 2026.09.00 (2026-09-12)
M3 1.4.0 is the newest stable and includes M3 Expressive APIs. BOM pins
Compose UI 1.12.1 and Material3 1.4.0 together. Alpha 1.5.x intentionally not
used.

## D4 — No paywall anywhere (2026-09-12)
PremiumGate, PremiumUpgradeScreen and the BillingManager stubs were unused or
stubbed; the free flavor unlocked everything already. For a free-software
project the paywall undermined trust. Removed. `free`/`play` flavors remain for
distribution differences. (Written when the licence was Apache-2.0; the project
relicensed to AGPL-3.0 in 0.1.1 — see ADR 0009. The conclusion is unchanged and
in fact stronger under copyleft.)

## D5 — Try/catch at every boundary is a hard rule (2026-09-12)
Every IO/platform call must catch exceptions, log via `AuraLog`, and degrade to
a typed fallback. `CancellationException` is always rethrown. Helpers live in
`core/AuraLog.kt`. See AGENTS.md.

## D6 — Phased rebuild with handoffs (2026-09-12)
The UI revamp is one phase per branch (P0–P6). Each phase ends with a handoff
doc and starts only after the previous gate passes. Agent memory
(`CLAUDE.md` + `memory/`) carries durable context between sessions.

## D7 — Flavors renamed to dev/prod, 4 variants total (2026-10-09)
`free`/`play` → `dev`/`prod` (dimension `env`), matching the other AW Builds
apps: dev gets `applicationIdSuffix ".dev"`, `versionNameSuffix "-dev"` and an
"AuraSpend Dev" label so it can sit beside the store build; prod is the only
distributed build. The `benchmark` build type was removed from `:app` — the
macrobenchmark module now links the prod **release** variant via
`matchingFallbacks` (running benchmarks locally needs the release keystore).
Feature parity and the no-analytics rule are unchanged; see ADR 0010, which
supersedes ADR 0008's naming while keeping its substance.
