# 0072 — the app, tested with Maestro

> The app had 637 unit tests, a lint gate, a licence gate, a privacy gate and a
> device suite that needed a phone attached. It had **no end-to-end UI test**: a
> flow that a person would actually perform — open the app, add a transaction,
> filter the ledger, change the theme, set a PIN — was never run as one thing.
> There is now a Maestro suite of thirteen flows, and it found three real
> defects in the first hour — plus one false alarm that turned out to be the
> test's own fault, which is worth recording too.

**637 unit tests, 0 failures. Lint baseline-only. No behaviour changed except
one: a settings row now toggles when its title is tapped, which is what every
other row in that screen already did.**

---

## What was built

`.maestro/flows/` — thirteen flows, plain Maestro YAML, no build step:

| Flow | What it covers |
|---|---|
| `00-onboarding` | First run: all four pages, the currency picker's caveat, the tabs |
| `01-home` | The glance, the cash-flow chart, budget rings, subscriptions, accounts, today's rows, and the way out to Plan and Activity |
| `02-quick-add` | The keypad, a category chip, a merchant, Save, the row appearing in the ledger, and the delete that cleans up after it |
| `03-activity` | Search, the type filters, the month chevrons, the sort toggle, the day groups |
| `04-plan` | The budget hero's true percentage, the per-budget rows, savings goals, the budgets screen, the subscriptions row |
| `05-insights` | The period selector, the ranked categories, the daily spending strip, the category drill-down |
| `06-settings` | Every group, and the honest state of each optional feature |
| `07-categories` | The editor: an empty name is refused, a name is created, the confirmation says what it does, the row is deleted |
| `08-theme` | System / Light / Dark / AMOLED, applied live and remembered |
| `09-currency` | The caveat above the list, dollars, and back to the device's own currency |
| `10-app-lock` | Set a PIN, lock the app, a wrong PIN is rejected, the right one gets in, and the lock is turned off again |
| `11-smart-add` | A pasted bank message, and what the classifier made of it |
| `12-review-queue` | The triage inbox, and the queue of messages the app could not read |
| `13-delete-data` | The destructive path, with its confirmation |

`tools/run_maestro.sh` drives it: clean install → onboarding → **the app's
locale set to en-IN** → the demo data seeded → the flows → the data seeded
again. The seeding is not optional. Home, Plan and Insights are aggregate
screens; on an empty ledger there is nothing to assert, and the two flows that
create a transaction and a category have to leave no trace behind them.

## What it found

**1. The Activity search field had no accessible name.** The hint was a `Text`
sibling of a `BasicTextField`, inside a row with `.clickable` — and a clickable
merges its descendants, so the merged node took its *text* from the field,
which is empty. The result: a node of class `EditText` with no text and no
content description. A screen-reader user was told a text field existed and
not what it was for. The same pattern was in the Quick Add merchant field.

**2. The Settings switches had no accessible name.** `Switch(checked, onCheckedChange)`
with no description, on a row whose title and subtitle are *siblings* of the
switch rather than its label. A screen reader announced "switch, off" and
stopped. Four switches are affected (App lock, Dynamic color, Auto-categorize
messages, Categorisation notifications), plus the recurring switch in the
transaction editor.

**3. A settings row whose title did nothing.** Tapping "App lock" — the title,
the thing a user aims at — did not toggle the lock; only the switch itself did.
Every other row in that screen opens something when it is tapped, so this one
was a surprise. The row now toggles, and the switch still consumes its own
touches so it cannot toggle twice.

**And one false alarm.** The Quick Add sheet's Save button looked like it was
out of reach — which turned out to be the *flow's* fault, not the app's: the
sheet opens expanded, which is the behaviour the concept asks for, and the
flow had to stop scrolling to look for it. Recorded because "the test was
wrong" is a finding too.

## What was changed in the app

Four files, all accessibility, all small:

- `TransactionListScreen.kt` — the search field carries the hint as its
  `contentDescription`; the hint `Text` is taken out of the tree with
  `clearAndSetSemantics` so it is not announced twice.
- `QuickAddSheet.kt` — the same for the merchant field.
- `SettingsScreen.kt` — the switch carries the row's title as its
  `contentDescription`, and the row toggles.
- `TransactionEditor.kt` — the recurring switch carries its label.

The `contentDescription` values are the rows' own localised titles, so they
are localised by construction — the project rule that a hardcoded
`contentDescription` is a defect is satisfied rather than worked around.

**637 unit tests, 0 failures. Lint baseline-only (2 hints).** No new test was
added for the fixes: they are semantics properties, and a Compose UI test would
have to assert on a merged node's internals, which is exactly the brittle thing
the Maestro suite is better at. The Maestro flows *are* the regression test —
`assertVisible: "Merchant (optional)"` and `assertVisible: "Search merchant or
note"` both fail on the old build and pass on the new one.

## The traps, for whoever runs this next

**Maestro's own driver has to be on the device.** It ships inside
`maestro-client.jar` as `maestro-app.apk` and `maestro-server.apk`. If they
are missing, every run fails with *"Device emulator-5554 was requested, but it
is not connected"*, which reads like the device is gone. The runner
reinstalls them from the jar.

**A stale session keeps a dead device server.** `~/.maestro/sessions` is keyed
by device, and a stale entry makes every later run fail with *"Device server
died"* while reporting nothing. The runner drops the device's entry.

**`launchApp` brings the existing task forward; it does not rebuild it.** The
navigation stack comes back with it, so a flow can resume on Settings, on
Categories, or with a sheet open. Every flow therefore opens with a conditional
back-out, and says which tab it wants.

**A list keeps its scroll position between launches.** A row that is merely
*above* the viewport fails an assertion that has nothing to do with it. Every
flow scrolls back to the top first, with `centerElement: true` — the element is
brought to the middle of the viewport, and the top of a list cannot be
scrolled past. A swipe is not used for this: a drag that starts over the
cash-flow chart is swallowed by the chart's own gesture handling, and a bare
`direction: DOWN` starts at the system status bar and pulls the notification
shade down over the app.

**`clearState: true` clears the per-app locale with it.** So the locale is
set *after* the onboarding flow, not before it.

**`Maestro`'s text matcher is a full-string regex.** "As of 20 Oct" does not
match `.*As of.*`… it does not match "As of" either, because the node is
"•••• 4523 · As of 20 Oct". Every assertion that is a *fragment* of a node
needs `.*` on both sides.

**`eraseText` is inert on this emulator.** It sends no key event to a field
that does not have the soft keyboard up, and the category editor's field
cannot be put at the end of its text by tapping a label. **Renaming a category
is therefore not covered** — creating one exercises the same editor, the same
field and the same save path, so what is left uncovered is the pre-fill of an
existing name rather than the save itself. This is the one deliberate gap.

**The emulator is shared.** Another project was being tested on it during
this session, and its app in the foreground broke three flows before the cause
was spotted. The suite launches its own app by id, so it recovers, but a
concurrent session will fight it.

## Not covered

- **Renaming a category** — see above, `eraseText` is inert.
- **The Drive backup and restore paths.** They need a Google account and a
  real Drive, and they are behind a sign-in the suite has no business
  automating.
- **The CSV import and export.** The Storage Access Framework opens a system
  document picker, and the file has to be put in the picker by hand.
- **The on-device categoriser's download.** It is 22 MB over the network, and
  the suite should not depend on that.
- **The notification permission prompt** is tolerated, not asserted.
- **Landscape, tablets and the 200 % font scale.** The suite is written
  against 390 × 844 dp in portrait.

## Next step

Run it:

```bash
export PATH="$HOME/.maestro/bin:$PATH"
tools/run_maestro.sh --device emulator-5554
```

and then extend it into CI. The suite needs an emulator on the runner, the
`dev` flavour installed, and the demo data seeded — which the script already
does. The one thing that would need a decision first is whether CI should run
the destructive `13-delete-data` flow at all, or leave it to a nightly run.
