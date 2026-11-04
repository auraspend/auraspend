# 0080 — page navigation is instant, everywhere

**Date:** 2026-11-04 · **Branch:** `dev` · **Status: shipped in 0.1.98**
(handoff 0081) — no version bump of its own.

## What the owner asked for

Page navigation that is **instant, with no animation at all**. Not a better
easing — no motion between destinations.

## What was there

Measured from the code and from a screen recording of 0.1.97 on the Moto G40
(`ZD2222DD42`, dev release, `dev` flavor, speed-profile compiled):

| Move | Enter | Exit |
|---|---|---|
| tab → tab | `EnterTransition.None` | `ExitTransition.None` (already instant) |
| push | slide in from the right by **width / 8**, 200 ms | fade out, **100 ms** |
| pop / back | fade in, 200 ms | slide right by **width / 8**, 200 ms |

So: no zoom, and no page-out either. Two defects came out of that pairing, and
they are why it felt rough rather than merely animated:

- **The push spent its last 100 ms over bare background.** The outgoing page was
  fully transparent at 100 ms while the incoming one was still an eighth of a
  screen away from home, so the strip at the left edge was the window
  background.
- **Back nudged the page and then blinked.** `popExit` moved the detail page an
  eighth of the width and the transition then ended, removing it mid-screen. It
  was a 200 ms nudge followed by a cut, which is worse than either.

Recording at 60 fps: the push held 18 consecutive frames of motion over
**166 ms**, the pop **17 frames over 223 ms**. The tab switch — the control,
already instant — held 3 frames over 32 ms (the tap ripple).

## The change

`ui/navigation/NavGraph.kt`, and only that file. The motion layer is deleted,
not retuned:

- `TRANSITION_MS`, `pushEnter`, `pushExit`, `popEnter`, `popExit` and
  `isTabSwitch()` are gone.
- `NavHost` now pins all four transitions to `None`
  (`enterTransition`, `exitTransition`, `popEnterTransition`,
  `popExitTransition`).
- The ten per-destination `enterTransition`/`exitTransition`/`pop…` blocks are
  removed (settings, add transaction, classification, unrecognized SMS, budgets,
  subscriptions, categories, goals, licenses, transaction detail), and the
  imports that went dead with them are pruned.

**The four `None`s are load-bearing.** `navigation-compose`'s own default is a
**700 ms crossfade**. Delete a destination's override without pinning `None` at
the `NavHost` and navigation gets seven times slower, silently, with no test
failing — a JVM test cannot observe a transition. The comment in the file says
so, next to the code.

This supersedes **0064**, which made *tab switches* instant for fill-rate
reasons and deliberately kept a subtle push. The rule is now one sentence: no
page transitions.

## Verification

- `./gradlew :app:compileProdDebugKotlin --rerun-tasks` — **BUILD SUCCESSFUL**,
  zero `w:` lines (the unused-import pruning is the risk here).
- `./gradlew testProdDebugUnitTest --rerun-tasks` — **709 tests, 0 failures**
  (1 pre-existing skip).
- `./gradlew :app:lintProdDebug` — baseline-only.
- On the Moto G40, dev release APK, `cmd package compile -m speed-profile`:

| Move | Before | After |
|---|---|---|
| push (Home → detail) | 18 frames / 166 ms of motion | **one frame** — the cut, then 62 ms of residual motion (tap ripple + the detail body painting) |
| back (detail → Home) | 17 frames / 223 ms | **one frame** — 64 ms residual |
| tab switch (control) | 3 frames / 32 ms | 3 frames / 50 ms |

  The recordings were decoded frame by frame (`ffprobe` PTS + per-frame mean
  absolute difference). The tab switch is the control: it was instant before,
  and push/pop now sit in the same band, so what is left is the tap ripple, not
  a page transition. The frames either side of the push are Home and the detail
  page — there is no frame showing two pages.

- **The chrome does not lag.** Frames around the push show the bottom bar gone
  in the same frame the page cuts, so the page does not reflow mid-navigation.
  In 0.1.97 the bar was still drawn for the first frames of the slide, so the
  page grew while it was moving.

## Known consequences

- **Predictive back has no preview any more.** `enableOnBackInvokedCallback`
  stays `true` in the manifest, but with no pop transition there is nothing for
  the gesture to drive: the drag shows no panel and the screen cuts on release.
  That is what "instant" means; it is not a regression to fix.
- **The detail body paints ~47 ms after the cut** (measured: 4 frames on the
  Moto). The page arrives immediately with its app bar, then the row is read
  from the encrypted database and the card appears. It existed before — the
  166–200 ms motion hid it. Colouring it in (`AuraSkeleton`) would trade one
  flash for another; the real fix is to seed the detail from the `Transaction`
  the list already holds, which changes three `onOpenTransaction` call sites
  from `(String) -> Unit` to `(Transaction) -> Unit`. Left for the owner to
  decide, deliberately not smuggled in here.
- A screen recording is the only evidence for any of this. There is no JVM test
  for a transition, and the four `None`s could be deleted by a future edit with
  every gate still green.

## Next step

Owner decision on the 47 ms detail-body gap. If it is wanted: prefetch the row
at tap time and pass it into `TransactionDetailScreen` as its initial state,
then keep the existing read as the refresh.
