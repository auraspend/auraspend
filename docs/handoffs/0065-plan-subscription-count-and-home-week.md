# 0065 — Plan said "0 subscriptions" above a screen listing four

**Date:** 2026-10-18 · **Branch:** `main` + `dev` · **Status: committed and
pushed.** Not tagged; ships with the next cut.

## The subscription count (owner report)

Plan's subscription row read "0 active subscriptions" while tapping it revealed
four. The two screens read **different lists**:

- `RecurringScreen` shows tracked rows (`getActiveSubscriptions()`) **and** the
  recurring payments the classifier flagged, which it derives with
  `detectedSubscriptions(...)` and filters through `SuggestionDismissals`.
- `PlanHubScreen` read only `getActiveSubscriptions()` — so anything that was
  *detected but not yet tracked* was invisible to the count.

The count was not wrong about the data; it was wrong about **the screen it links
to**. That is the failure mode to watch for: a number whose source is narrower
than the list behind it.

Fix: the hub now reads `getRecurringTransactions()` too, applies the same
dismissals, and shows
`%1$d active · %2$d detected` (new plural, both locales) whenever there are
suggestions — so the number can never contradict what the tap reveals. Tracked
amounts are still excluded from the trailing monthly total, which is the 0039
rule: suggestions stay out of the money until the user tracks them.

## Home was empty on a quiet day

Below the budgets card, the only thing left was "Today". With no transactions
today the screen was a hero, a bar and a lot of paper — the app's whole job
(where did the money go) invisible.

Re-added a seven-day strip, in the design system's own chart component rather
than a new one:

- `DashboardViewState.dailySpend: List<Double>` — seven raw amounts, oldest
  first with today last, quiet days present as zeroes so the strip keeps its
  columns.
- `DashboardViewModel` observes `repository.observeDailyExpense(week7Start,
  week7End)` inside the existing `combine`, with the window **ending today, not
  a calendar week** — a Monday-to-Sunday window reads empty every Monday
  (handoff 0043).
- `DashboardScreen` renders `AuraSparkColumns` (the same component Insights uses
  for "Daily spending", same `AuraCard(Outlined)` shell, same axis labels), so
  the two screens share one visual language. Today's column is the ink one.
- Hidden entirely when the whole week is zero, rather than showing a flat row.

Strings: `home_last_seven_days` (EN "Last 7 days" / HI "पिछले 7 दिन") and the
`plan_subscriptions_and_detected` plural. EN/HI parity verified: 543 keys each,
no orphans.

## The per-screen audit — partial, and why

The ask was to walk every page and find this class of mistake. What was done:

- **Code pass** over each screen's displayed aggregates against their list
  sources. Found and fixed the subscription count. Checked and found consistent:
  Home's "N of M budgets over" (same list as the rows), the review-queue count
  (Home rail, Settings row and the queue screen all read `unrecognized_sms`),
  Insights' "N more categories" (derived from the ranking it prints) and the
  Plan hero's "across N budgets".
- **One candidate left deliberately unchanged:** Activity's header
  ("October · ₹44,663 out") is a SQL aggregate over the *month* and does not
  follow the category chip or the search box, while the rows below it do. That
  may be exactly what is wanted (the header names the month, the chip is a
  filter) or it may be the same complaint in a different place. It needs the
  owner's call, not a quiet change.
- **The device walk did not happen.** The Moto runs 0.1.71, which predates the
  Quiet Ledger redesign entirely, so auditing it would have audited the old UI.
  The vivo has 0.1.9 but spent the session with the notification shade holding
  focus — `cmd statusbar collapse`, `KEYCODE_HOME`, a swipe-up and relaunching
  the app all left `mCurrentFocus=NotificationShade`, and tapping tab
  coordinates blind on someone's phone is how quick-settings toggles get
  flipped. So this is the remaining work: walk Home → Activity → Plan →
  Insights → each detail screen on the vivo with 0.1.9 and look for
  number-vs-list mismatches.

## Verification

- `:app:compileProdDebugKotlin` clean; **614 tests, 0 failures, 1 skipped**;
  `lintProdDebug` baseline-only.
- `assembleProdRelease` installed on the emulator's prod app (same upload key, so
  data was preserved) and Home inspected at 1170×2532: "Last 7 days · $5,187"
  with the strip, quiet days as faint tracks, today in ink, axis labels
  12/15/18 Oct, and the Today rows below.
- EN/HI parity 543/543 by a name-and-tag comparison of both string files.
