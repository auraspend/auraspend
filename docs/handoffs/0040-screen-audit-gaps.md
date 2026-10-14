# 0040 — the screen-audit gaps, closed

**Status:** done. Branch `feat/audit-gaps` (PR #59 → `dev`, merge `e945b9a`).
**531/531 tests** (up from 522), lint clean, licence + privacy checks green.
On-device visual pass pending the owner's unlock (the phone re-locked).

## Where this came from

The owner asked for a screen-by-screen audit: what exists on each screen and
what should be there but is not. The audit walked every screen (dumps on the
device + the code) and produced a list; "fix everything" followed. Two of the
audit's suspects were **already present** and were corrected in the report
rather than "fixed": the transaction editor has a date picker, and the model
card has a remove action.

## What shipped (one commit each, all with EN + HI strings)

- **Subscriptions**: edit a tracked subscription in place — the add sheet
  doubles as the editor prefilled, saving under the same id; the stored next
  billing date is kept (the sheet has no date field; recomputing would move
  the due date silently). Dismiss a detected suggestion ("Not a
  subscription"): stored as normalized names in preferences, loosely matched,
  and a malformed value degrades to *re-showing* suggestions, never hiding
  them.
- **Smart Add**: a "Skipped (N)" section — the only surface skipped messages
  have ever had — with "Classify again" re-queuing a row (how a parser fix
  reaches history); plus "Skip All" beside Save All.
- **Activity**: month navigation (`‹ Month yyyy ›`); the paging query is now
  scoped to the month's epoch range, the same range the summary strip
  aggregates, so list and strip describe one window. Forward is clamped at
  the current month; a past empty month reads as filtered, not "yet". Also a
  newest/oldest **sort toggle** (one DAO query with a CASE on the boolean;
  Robolectric-pinned).
- **Settings**: an **SMS-access row** (granted/missing + Grant) — until now a
  revoked permission silently paused categorisation with recovery buried in
  Smart Add; a **categorisation-notifications toggle** (publish() returns
  early while muted; the toggle cancels any live notification via the
  notifier); **Delete all data** — the restore machinery fed an *empty*
  backup (one transactional sweep of every user table), then default
  categories re-seeded and the three ingest cursors forgotten so a fresh scan
  can re-import the inbox. Without the cursor reset the app would stay empty
  for ever.
- **Categories**: **icon and colour pickers** in the add/edit dialog. The
  offers are exactly the twelve icons `categoryIconGlyph` renders
  (`CategoryAppearanceTest` fails if that drifts) and the seeded ARGB palette
  (distinct + opaque, pinned).
- **Transaction detail**: tapping the **hero card re-categorizes** (the field
  people correct most) through a picker; **Share** joins Duplicate, sending a
  plain-text summary via the chooser.
- **Editor / Quick Add**: a **recurring-payment switch** for manual entries
  (only the detector could set the flag before); a **date chip** on Quick Add
  (now by default) opening the editor's DatePicker, with the chosen day
  threaded through the save.
- **Insights**: category slices and top merchants **drill down** into the
  filtered Activity through optional route arguments (blank defaults, so
  plain tab navigation is untouched; an incoming query opens the search field
  so the filtered list explains itself).

## Deliberately not ported

- **Dashboard month-stepping** — it would mix months on one screen (week
  chart and monthly budgets cannot follow); Activity + Insights cover
  browsing.
- **Per-budget alert thresholds/notifications** — a schema + notification
  feature, not a gap-fix; the limit badge already exists.
- **Multi-select bulk delete** — a UI mode; single-row swipe delete with undo
  is the current contract.
- **Unrecognized-message "why" line**, **global search entry point** — nice
  to have, no behavioural hole.

## Session notes

- Lint triage: a needless `LongArray(n) { 0L }` lambda fixed; the baseline
  regenerated for shifted line numbers + a dependency-version refresh only.
  Two lint errors caught while building (LocalContext.getString in a
  coroutine; KTX `edit` style) and fixed properly.
- One recurring unfiltered lint **hint** remains (a "newer paging-compose
  available" dependency note) — informational, non-failing.
- The KSP incremental cache needed clearing once after a server restart.
- Two edit batches collided with each other after a lost tool output (the
  first had partially applied); the repair is in the history of this branch —
  worth watching for when tool output is dropped mid-batch.
