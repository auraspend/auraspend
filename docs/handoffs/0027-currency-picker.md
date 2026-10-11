# 0027 — Currency picker (30 currencies, offline)

**Goal:** the owner asked for the currency list from
`GET https://api.frankfurter.dev/v1/currencies` to be captured now and hardcoded,
so users can pick a currency, "maybe" in onboarding.

## What the request uncovered

The currency *path* already worked end to end — `rememberCurrencyStyle()` →
`AuraSpendTheme` → `LocalCurrencyStyle` + `MoneyConfig`, so every money call site,
chart axis and CSV export formats correctly. **What did not exist was any way to
set it.** No picker in Settings, no currency step in onboarding, and
`CurrencyStyle.supported` — a hardcoded 10-currency list — had **zero call sites**.
Dead code from a picker that was never built, which is why the app's currency
could only ever be the device's own. (Same class as review finding #23, which was
cleaned up in 0026; the review did not list this one.)

So this was bounded work: UI for a preference path that already existed.

## What shipped

| File | Change |
|---|---|
| `ui/designsystem/CurrencyCatalog.kt` (new) | The 30 currencies captured from the API on 2026-10-11, verbatim, with the source URL in the KDoc. `currencyDisplayName` / `currencySymbol` (platform-localized, tolerant of an unknown code), `orderedCurrencies`, and `readStoredCurrencyCode` / `writeStoredCurrencyCode` / `currencyPreferences`. |
| `ui/designsystem/CurrencyPicker.kt` (new) | `CurrencyPickerList` — one widget, two hosts. Device currency first, then A–Z; symbol + localized name + code per row; `selected` semantics; 48dp `heightIn` rows; the "not converted" note. |
| `ui/designsystem/Money.kt` | `CurrencyStyle.supported` deleted. `rememberCurrencyStyle(chosenCode)` takes the hoisted choice instead of reading the preference (a preference is not observable state). |
| `ui/theme/Theme.kt` | `AuraSpendTheme(currencyCode = …)`. |
| `MainActivity.kt` | Owns `currencyCode` state and writes the preference — the same shape as `themeMode` / `dynamicColor`. |
| `ui/navigation/NavGraph.kt` | Threads `currencyCode` + `onCurrencyChanged` to onboarding and Settings. |
| `ui/onboarding/OnboardingScreen.kt` | A 4th page hosting the picker inline, with the device's currency preselected. |
| `ui/settings/SettingsScreen.kt` | A row in the existing **Finance** section (subtitle names the currency in use) → `ModalBottomSheet` with the same list, plus a "Match my device" reset. |
| `values/` + `values-hi/` | 6 new keys each (EN/HI now 489/489). |

## Decisions

- **No network at runtime, ever.** The list is a constant in source. A settings
  screen that phoned home for currency names would be a new network path and a new
  failure mode in an app whose on-screen claim is that nothing leaves the phone.
- **No FX conversion, and the picker says so.** Picking USD changes the symbol and
  digit formatting only: ₹1,500 renders as `$1,500.00`, not `$18`. Frankfurter has
  a rates endpoint, but conversion is a different feature with a network
  dependency — deliberately not built.
- **Currency names come from the platform** (`Currency.displayName` /
  `getSymbol`), which Android localizes. Hardcoding the API's English names as
  *display* text would have meant 30 more translated strings per locale; instead
  the catalog's names are provenance and fallback only.
- **The reset lives in Settings, not in the shared list.** Onboarding has nothing
  to reset; "follow my device" is what a user looks for after changing their mind.
  `writeStoredCurrencyCode(prefs, null)` is therefore reachable rather than a
  fresh-install-only state.
- **Onboarding page is last.** The user should know what the app will display
  before the SMS permission prompt on the button below it.

## Verification

- `./gradlew :app:compileProdDebugKotlin :app:compileProdDebugUnitTestKotlin --rerun-tasks`
  — **zero `w:` lines**.
- `./gradlew :app:testProdDebugUnitTest` — **350/350 green**. 7 are new here
  (`CurrencyCatalogTest`): the 30 codes pinned exactly (so a drift is a deliberate
  edit), no duplicates, every code resolvable by the platform, localized names for
  four locales, unknown-code fallbacks, the device-first ordering, and the
  preference round trip through Robolectric.
- `./gradlew :app:lintProdDebug` — green. One new finding (`UseKtx` on
  `edit().apply {}.apply()`) was **fixed**, not baselined, with the KTX `edit {}`
  extension.
- `bash tools/check_license.sh` and `bash tools/check_privacy_claims.sh` — both
  exit 0. EN/HI parity 489/489, no duplicates.
- The sheet's list is bounded by `weight(1f, fill = false)` rather than a magic
  `heightIn(max = …)`, so the `LazyColumn` scrolls inside the sheet instead of
  pushing the reset row off-screen.

## Concurrent changes in this tree (NOT from this session)

While this feature was being built, a **second session was editing the same
working tree**. Its changes are complementary and were included in the green run
above:

- `dao/Daos.kt` — `upsertTransactions`, a `@Transaction` default method that gives
  the **CSV-import batch** the same all-or-nothing property the 0026 per-row fix
  gave a single save. This closes a real gap 0026 left open: `saveTransactions`
  was a per-row loop, so a file that failed halfway persisted the rows already
  written while the caller reported failure. Built on 0026's
  `updateTransaction` + `insertTransactionIfAbsent`, so the REPLACE-deletes-your-
  edit bug stays fixed.
- `TransactionRepositoryBatchTest` (2 tests), `LocalLlmProviderTest` (7),
  `SmsMovementVerbRescueTest` (10) — 19 tests, all green against the 0026
  implementations.

Recorded because the counts in 0026 (324) predate them, and because a shared tree
means a shared verification: the tree was green at 350 tests when this handoff was
written, and the next session should re-run rather than trust either number.

## On-device verification (moto g40 fusion, Android 12, 2026-10-11)

Installed fresh — `adb uninstall com.awbuilds.auraspend` (+ the orphan
`com.awbuilds.auraspend.test` APK), then `:app:assembleProdDebug` and
`adb install`. The previously installed build was `0.1.1-free`, i.e. from before
the `free`/`play` → `dev`/`prod` rename, and its database held only the
`tools/seed_demo_data.sh` rows (`tx-salary-1` "ACME Corp Payroll"), so the wipe
cost nothing.

**The device pass found a real bug, which is why it was worth doing.** The
"Amounts are not converted" note did not render at all: `CurrencyPickerList` put
the note *after* a non-weighted `LazyColumn`, and a Column hands each child the
space the previous ones left — so the list took the whole bounded height and the
note was measured against zero. It was absent from the accessibility tree while
the list and the button were fine, which is exactly the kind of failure a
screenshot alone can miss. Fixed by moving the note above the list, with a
comment recording the trap. Confirmed afterwards at `[60,757][1020,835]` in the
dump and visible in the screenshot.

Verified on the device, in order:

| Check | Result |
|---|---|
| Clean install starts onboarding on **page 1** | ✔ "Track every rupee" + Continue |
| Three Continues reach the currency page | ✔ 4th indicator dot, "Get started" |
| Device currency preselected and first | ✔ ₹ Indian Rupee, check mark |
| Note visible | ✔ after the fix; absent before |
| Settings → Finance row | ✔ "Currency / Matching your device" |
| Sheet: reset row, note, list | ✔ scrolls; last row (ZAR) ends at y=2240, clear of the nav bar at ~2340 |
| Picking USD | ✔ sheet closes, subtitle → "US Dollar (USD)" |
| **Propagation without a restart** | ✔ Home re-rendered `₹0` → `$0`, `+$0`, `−$0` (hoisted state works) |
| "Match my device" reset | ✔ subtitle → "Matching your device" |

Screenshots: `docs/screenshots/device-2026-10-11/`.

**Follow-up, same day:** the owner asked for the onboarding copy to read globally,
so `onboarding_page1_title` changed from "Track every rupee" to "Track your money"
(Hindi: "हर रुपये का हिसाब रखें" → "अपने पैसे का हिसाब रखें") — the only
user-facing "rupee" in the app; the remaining ₹/rupee mentions are test comments
about INR parsing. Rebuilt, reinstalled, `pm clear`, confirmed "Track your money"
at `[274,1267][807,1348]` on the Moto. Per the owner's request this one was not
screenshot-verified by the agent — it is left on screen for the owner to check.

Two observations, neither a defect:

- **The platform's names differ from the API's**, as designed: USD shows as
  "US Dollar" (platform) rather than "United States Dollar" (API). The catalog's
  names are provenance/fallback, which is the point.
- The picker's symbol column is the platform's: `A$`, `R$`, `CA$`, `CN¥`. A
  locale that has no symbol for a code falls back to the code, e.g. `CHF`.

## Open risks / next step

- Still unverified on a device: the currency page at **200 % font scale**, and
  Hindi labels (the device ran in English).
- Next step for the currency work if the owner wants it: a Frankfurter **rates**
  integration for real conversion — a network feature, so it needs an explicit
  decision against the offline-first promise.
