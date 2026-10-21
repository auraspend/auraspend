# ADR 0014 — In-app update surfaces, and the urgent-update exception

**Status:** Accepted · 2026-10-21 · **Amends ADR 0013**

## Context

ADR 0013 shipped Play In-App Updates as one user-triggered row in Settings →
About, and stated explicitly: *"The app does not force an update on launch; the
check is user-triggered from Settings. That avoids blocking a user who is in the
middle of entering a transaction."*

That was right for the first release of the feature and wrong as a resting
state for two reasons.

1. **A check nobody can see is a check nobody uses.** The row only answers when
   the user already suspects there is an update. Meanwhile a user on a build
   with a fixed classifier bug has no way of knowing, and the app's only advice
   is "go look in Settings".
2. **A severe release needs a path that does not depend on the user looking.**
   A security fix that waits for someone to browse Settings is not a fix.

The owner asked for both: the update should be visible without opening Settings,
and an urgent release should be able to insist. This ADR records how far
"insist" goes, and what keeps it from becoming the nagging the project's UI
avoids everywhere else.

## Decision

### One check, several surfaces

A **single** silent check — at most once a day, never starting a flow — fills
one piece of state. Every surface reads that state; none of them checks on its
own.

| Surface | When |
|---|---|
| A line in Settings → About | An update is available; the row becomes an answer before it is a question |
| A dot on the header avatar | An update is available and not dismissed |
| One dismissible strip below the balance on Home | As above; this is the only surface with a dismiss control |
| "Update ready — restart" | A flexible download already finished |
| A system notification | The background check found an update the user has not been told about |

The decisions are a pure function, `UpdatePolicy`, not a condition per call
site:

- A finished download outranks an available one.
- An urgent release skips the quiet path (see below).
- **A dismissal is scoped to the installed version code.** "Not now" holds until
  the app is actually updated; the same release never asks twice.
- The notification waits **three stale days**, fires **once per available
  version code**, never fires when Play cannot say how old the update is, and
  never fires for an urgent release — a notification that leads to a screen the
  user cannot dismiss is worse than silence.

### The urgent exception

This **supersedes ADR 0013's no-forced-update rule**, under two conditions that
must both hold:

1. The upload set Play's `inAppUpdatePriority` to **4 or 5** (the release
   workflow takes it from the repository variable `PLAY_UPDATE_PRIORITY`,
   default `0`).
2. Play reports that the **immediate** update type is allowed for this install.

Then the app opens Play's full-screen update flow on launch. Otherwise the
quiet path applies and a routine release never interrupts.

The priority belongs to the **release**, not to the branch: a security fix sets
the variable for one tag, and nothing in the code changes.

### Placement

- The Play implementation stays in `src/gms`, the no-op in `src/fdroid`, and the
  shared interface, the policy, the notifier and the daily worker in `main`.
  `BuildConfig.IN_APP_UPDATES` is `true` only for `prod`, so the worker is never
  scheduled and the surfaces are never rendered elsewhere.
- The notification has its **own channel** (`app_updates`). Riding the
  classification channel would couple two unrelated preferences in both
  directions.
- The immediate flow is launched from Home, the landing screen. The nav host is
  composed only when the app is unlocked (`MainActivity` puts it in the `else`
  branch of the lock gate), so the flow cannot open behind the PIN screen.

## Consequences

- A routine release never interrupts; a release the owner marks urgent can.
  That is the trade this ADR accepts, and it is why priority is a per-release
  decision rather than a constant.
- The user can dismiss the Home strip, and can disable the notification channel
  in Android's settings. Neither disables the urgent path — by design.
- **No privacy change.** The check asks the Play Store already on the device; no
  data reaches AuraSpend, which has no server. The site's privacy page says so.
- **The surfaces cannot be exercised on a development build or a sideload**:
  `IN_APP_UPDATES` is prod-only, and `isAvailable` is true only when the
  installer is `com.android.vending`. Verification is therefore the policy's
  unit tests, the components' rendering tests, and a Play-installed device —
  the same limitation ADR 0013 recorded, now covering more surfaces.
- A new background worker (daily) joins the six-hourly classification worker.
  It is inert wherever `IN_APP_UPDATES` is false.

## References

- ADR 0012 — F-Droid flavor without Play Services
- ADR 0013 — Play-only in-app updates (amended here)
- Handoff 0074 — the implementation and its verification
- Play In-App Updates — <https://developer.android.com/guide/playcore/in-app-updates>
- `UpdatePolicy` and `UpdatePolicyTest` — the seventeen rules
