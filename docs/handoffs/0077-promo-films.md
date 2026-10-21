# 0077 — two promo films, and two stale numbers on the site

**Date:** 2026-10-22 · **Branch:** `dev` · **Status: local only — not
committed.**

## What was built

Two promotional films for AuraSpend, authored with HyperFrames and the
`hyperframes-animation` / `hyperframes-creative` / `hyperframes-core` skills.
They live in `promo/` on this branch and are **deliberately untracked** (see
"The music" below and the `/promo/` entry in `.gitignore`):

| Film | Size | Length | File |
|---|---|---|---|
| Hero | 1920×1080 | 25.0 s | `promo/out/auraspend-hero.mp4` |
| Vertical | 1080×1920 | 15.0 s | `promo/out/auraspend-vertical.mp4` |

Posters are baked as **frame 0** of each file (`out/*.jpg` as well), so a
player that grabs the first frame shows the hook rather than a blank sheet.

Both projects pass `npx hyperframes check` with **0 errors, 0 warnings** and
**49/49 text checks at WCAG AA**. Studio previews run at `localhost:3002`
(hero) and `localhost:3003` (vertical).

## The idea

The app's identity is the **Quiet Ledger** — warm paper, near-black ink, one
violet accent, hairlines, type instead of gradients. A promo that
re-introduced the purple-gradient phone-on-a-glow look would advertise a
different product than the one that exists, so the film does not.

**The film is a sheet of the app's own paper.** A bank SMS arrives on it. The
parser lifts four fields out of the message onto ruled rows. The sheet becomes
the phone that holds the ledger. The magnified row is the app's own pixels —
a crop of the real Activity screen, not a mock-up, and the crop is the *same*
pixels at the same scale as the screen, which is what lets the screen dissolve
into the row invisibly.

The motion vocabulary is the `hyperframes-animation` rule set:
`spring-pop-entrance`, `waterfall-entry`, `cursor-drag`, `svg-path-draw`,
`counting-dynamic-scale`, `multi-phase-camera`, `logo-assemble-lockup`,
composed against the `typewriter-reveal`, `grid-card-assemble`,
`cursor-ui-demo`, `device-surface-showcase` and `dataviz-countup` blueprints.
Scene boundaries sit on the music's beat grid (114.84 BPM), and the strong
cues carry the reveals.

## The numbers are re-derived, and two on the site are stale

Every figure in the film was re-derived from the source, not copied from the
website:

| On screen | Source of truth |
|---|---|
| `54` sender-routed parsers | `BankParserRegistry.all` — **54** entries |
| `97.6 %` exact match | the 82-case corpus, re-measured 2026-10-17 |
| `0` trackers / accounts / ads | no analytics, advertising or crash-reporting SDK in any build |
| `−₹640.00` · `Swiggy` · `Food & Dining` | the seeded demo data — which is what the real Activity screen shows |

**`site/index.html` is wrong twice.** It says **53** parsers (the registry has
**54**) and **637** unit tests (the tree now has **694** `@Test` methods).
Requirement 10 says a number the app stopped producing is as bad as a stale
number in the app, and a promo built on the site's copy would have inherited
both. The film's numbers are the corrected ones. **The site itself is
unchanged** — the owner deferred that work until the film is signed off, so
this is recorded, not fixed.

Nothing personal is on screen. The sample message is the synthetic one the
website already publishes, and its reference number is shown masked — the way
the app masks UTRs before storing them.

## The music

The bed is *Happy Beats / Business Moves vol. 11* (ende.app) from the `/brag`
skill's bundle; the stingers are Kenney (CC0). The music's licence terms are
**not stated** in the skill that ships it, so nothing third-party is committed:
`promo/` is in `.gitignore` in full, and the rendered MP4s — which carry the
music — are not either. The owner's instruction was local-only, and this
repository is public and permanently archived.

## Four traps, all of which cost a render

1. **GSAP owns `transform-origin`.** Its default is `50% 50%` and it
   *overrides* the CSS. Every tween that places or scales a screen must
   declare `transformOrigin: "0px 0px"`, or the camera scales about the
   wrong point and the dive lands 95 px off.
2. **A cropped image has its own origin.** The row crop starts at source
   `y 810`, so it sits at `ROW_Y` — not at the full screen's `BAND_Y`.
3. **`top: 100%` resolves against the nearest positioned ancestor**, not the
   element's own box. The review hint was a *sibling* of its card, so `100%`
   pushed it 1080 px down and off the frame.
4. **`--faint` is not a text colour.** `#918c7d` on paper is 3.1:1 and fails
   WCAG AA. In the film every label uses `--muted` (`#6e6a5c`, 4.97:1) and
   `--faint` stays decorative. The app's own axis-label grey is a different
   problem at a different size — but the *site* has the same `--faint` and
   should be checked for the same reason.

One more, worth a rule of its own: **a sub-composition root also matches
`#root`**, so the root rule sets no background. An opaque root inside a slot
covers the master's ruled paper.

## Structure

Each project is standalone — HyperFrames serves the project directory, so
every asset a composition references is copied inside it.
`promo/sync-assets.sh` keeps the copies in step with `promo/shared/`.

The hero is nine sub-compositions (`compositions/*.html`), the vertical is six.
Each scene owns its own paused timeline; the master owns only the background
ambience. `lint` prefers this shape — a flat list of scenes in one file draws
a `nested_structure_needs_subcomposition` warning on every scene.

## Open

- The owner has not yet watched the films. Nothing is published; the site does
  not embed them.
- If the site takes the hero film, `site/video/` needs the file and
  `index.html` needs the embed, and the two stale numbers above should be
  corrected in the same pass.
- A square (1080×1080) cut for the social feed is not made.
- No captions. The Play promo slot does not require them, but the vertical
  cut would benefit if it is ever posted with sound off.
