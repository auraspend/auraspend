# 0069 — Home is a glance *and* a dashboard again

**Date:** 2026-10-20 · **Branch:** `main` + `dev` · **Status: committed.**

## What was done

The owner asked for the Home screen to be redesigned "to include things like the
last aurora design theme but matching this theme". The Aurora home (0.1.71) had
a hero, four quick actions, a cash-flow chart, a carousel of budget rings, a
subscriptions card and the recent rows; the Quiet Ledger pass (0058–0062) cut
all of that to a bare glance and moved the chart to Insights. Both were right
about their own question. This screen now answers both, in the concept's
language:

| # | Section | Notes |
|---|---|---|
| 1 | Greeting + avatar | unchanged |
| 2 | **Hero** | Aurora's *shape* — a label, the figure, and two stats either side of a hairline — on a **flat tonal block**. The gradient stays retired: a brand sweep is the one thing that would not match the theme. |
| 3 | **Needs-you rail** | unchanged (over-budget sentence, review queue) |
| 4 | **Cash flow** | Aurora's `AuraAreaChart`, seven days ending today, in ink — the Quiet Ledger's *Daily spending* strip stays on Insights. |
| 5 | **Budgets** | Aurora's carousel of ring cards. The ring is ink, not the category's colour; brick appears **only** when a budget is over, because that is a status. |
| 6 | **Subscriptions** | Count + monthly cost, from the same table the Subscriptions screen reads. |
| 7 | **Accounts** | The ledger from 0068 (b26530a), unchanged. |
| 8 | Today | unchanged |

The owner then asked for the **quick-action row to be removed** (Expense ·
Income · Smart Add · Stats), so Home is the hero, the rail, the chart, the
budget rings, the subscriptions, the accounts and today's rows — with the
centre **+** in the nav bar as the one way to record something. The row's
component (`AuraQuickAction`) and its four labels went with it rather than
being left behind as dead code.

### New design-system components

`ui/designsystem/Tiles.kt` (new): `AuraBudgetRingCard` (and, until the owner
removed the row, `AuraQuickAction`).
They live in the design system rather than in the screen so the shape, the
tokens and the 200 % font-scale behaviour are decided once, and so
`DesignSystemGuardTest` covers them.

### Data

`DashboardViewState` regains `activeSubscriptions` and `totalSubscriptionCost`,
observed from `getActiveSubscriptions()` — the same table the Subscriptions
screen reads, so the new Home card and that screen cannot disagree about how
many there are or what they cost (the failure 0065 was about on Plan). The
monthly cost is normalised through `RecurringCost.monthlyTotal`, so a yearly
subscription counts as a twelfth of its price.

### Strings

Seven keys added (the copy the 0059 fidelity pass deleted: "Cash flow", the
subscriptions card, the two hero stat labels, the ring's screen-reader string)
and nine orphans removed — the four quick-action labels went with the row, and
`home_out_short`, `home_in_short`, `home_last_seven_days`, `home_budgets_over`
and `home_left` with the sections that used them (`UnusedResources` is an error
in this project). EN/HI parity: **552/552**.

## A money bug this found

The new demo balance made the Home read **₹4,52,310.60** for a stored
**₹4,52,310.55**, and the cause was not the demo data.

`formatMoney`'s Indian-grouping path formatted with the pattern `#,##0.#` —
**one** decimal digit — and then padded the second one with a zero. Every
rupee amount with paise was mis-stated:

| Amount | Rendered | |
|---|---|---|
| ₹123,456.78 | ₹1,23,456.80 | ✗ |
| ₹1.05 | ₹1.10 | ✗ |
| ₹12,345.67 | ₹12,345.70 | ✗ |
| ₹0.99 | ₹1 | ✗ |

It went unnoticed because the demo data has no paise anywhere, and because the
old store captures were shot before the app had a balance at all. The value is
also a `Double`, whose exact binary value is not the number the user typed
(452,310.55 is really 452,310.54999…), so the amount was rounded twice.

The fix scales the decimal the user meant with `BigDecimal.setScale` and then
formats with a pattern that carries as many fraction digits as the currency
shows. Three tests pin it (`MoneyFormattingTest`), including that the
non-Indian path keeps the same paise.

## 200 % font scale

Three things did not survive the largest system font, and all three are now
right:

- The hero's two money strings share one line, and "₹46,006.40" wrapped into
  "₹46,006.4 / 0" — which reads as two numbers. The hero's stats round to
  whole rupees (the exact figure is in Activity, and the ledger card below
  rounds the same way).
- The budget card was Aurora's 168 dp, and the category name sits beside the
  ring: at 200 % "Grocery" broke to "Grocer / y". The card is 188 dp now.

## The store, the listing and the site

The Home is the app's first screen, so the assets that show it were stale and
had to follow (AGENTS.md rule 10 — the site and the listing must only say what
the app does):

- The **demo data** now carries a balance snapshot (one row, `•••• 4523`,
  ₹4,52,310.55), so the captures show the ledger and the hero says
  "Available" instead of the month's flow.
- The **Home and Quick Add captures** were re-shot on the emulator at
  1170×2532 @480 dpi with `en-IN` and the demo data, in light and dark. The
  Quick Add capture was two redesigns out of date — it still had the Aurora
  "TOTAL BALANCE" Home behind the sheet.
- The **store deck** was re-exported (`node export.js android`); slides 01 and
  05 changed, the other five are byte-identical. The seven slides were
  uploaded to the Play listing and **verified by SHA-1** — all seven digests
  match the local files.
- The **site** got the new Home and Quick Add images, a new alt text that names
  what is on the screen, and a re-derived test count (614 → 632). The old
  number was four releases out of date.

## The Activity header, measured on the phone

Testing the redesign on the owner's vivo surfaced a defect that had been
logged since 0065: **the Activity header described a different set of rows
from the list under it**. It showed the month SQL aggregate, which ignores the
direction chips, the category chips and the search box — so "October · ₹74,855
out" sat above a list filtered to one category, and above an *empty* one.

The aggregate is now narrowed by the same constraints the paged query uses
(`observeSummaryFiltered`), and the line names what it is counting: the
category when one is selected, the month otherwise, and "in" rather than "out"
when the list shows income only. On the phone:

| Filter | Before | Now |
|---|---|---|
| none | October · ₹74,855 out | October · ₹74,855 out |
| Incoming | October · ₹74,855 out | October · **₹5,767 in** |
| Food & Dining | October · ₹74,855 out | **Food & Dining · ₹0 out** |

Five tests (`FilteredSummaryTest`) pin it against a real in-memory database,
including the case the phone showed: a filter that matches nothing totals
nothing.

## Transitions on the vivo, measured

The outstanding item from 0038 — the screen-flicker/lag report — is now
measured, because `gfxinfo` reports zero frames on this device and the frame
source has to be the layer's own present timestamps.

- **SurfaceFlinger `--latency`**: p50 **16.74 ms**, p95 17.5 ms at the panel's
  60 Hz — one frame, on budget — with at most a single 33 ms frame per
  transition. The 770 ms hole the first run reported was a **measurement
  artefact**: the buffer is a ring of the last 128 presents and it keeps
  collecting while the screen is static, so the idle period between two taps
  reads as one enormous frame.
- **A screen recording** settles the flicker question: every tab switch and
  every detail transition lands as **one frame change**, and no stretch of the
  recording is static for 250 ms. The instant tab switch (0064) is therefore
  right on this device and stays.

## Verification

- `./gradlew :app:testProdDebugUnitTest` — **637 tests, 0 failures**.
  Two new ones (`DashboardSubscriptionsTest`: the confirmed count, the
  normalised monthly cost, the cancelled subscription left out), three in
  `MoneyFormattingTest` and five in `FilteredSummaryTest`.
- `./gradlew :app:lintProdDebug` — clean (baseline-only).
- `./tools/check_license.sh`, `./tools/check_privacy_claims.sh` — clean.
- On the emulator (1170×2532 @480 dpi): Home walked in **light and dark**, the
  the ledger populated from a real bank message
  ("Bal INR 66,000.00" → the hero flips to "Available" and an account row
  appears), the budgets carousel, the subscriptions card and the accounts card
  all read correctly.
- **On the owner's vivo** (the dev build, the real inbox): the new Home
  renders, and the filtered Activity header was checked against the phone's
  own data. The
  phone's screen timeout and stay-awake setting were restored to 15 s and 0
  afterwards.
- At a **200 % font scale** (light and dark): the hero, the chart, the budget
  cards and the account rows all survive.
- The Play listing was checked after the upload: seven images, digests equal to
  the local files.

## Open risks / next steps

- The **Play build (0.1.91) carries none of this** — no redesign, no ledger,
  no money fix. The next release must.
- The site's **changelog page** has not been updated for this redesign.
- The Activity header now names the **category** when one is selected, and
  the month is still named by the stepper below it — so the month is one line
  further away than it was.
- The **launcher icon and store assets** are still the old artwork (tracked
  since 0062).
- `AuraBudgetRingCard` is 188 dp wide with a fixed `width`; it is the one new
  component with a hard width, and a very long category name will still wrap
  (two lines, reserved ✓).
- The home-screen **first-screenful promise** ("the hero and the rail fit
  above the fold") is measured by eye on a 390×844 dp emulator only.
