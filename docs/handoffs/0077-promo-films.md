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
| Hero | 1920×1080 | 25.8 s | `promo/out/auraspend-hero.mp4` |
| Vertical | 1080×1920 | 16.0 s | `promo/out/auraspend-vertical.mp4` |

Posters are baked as **frame 0** of each file (`out/*.jpg` as well), so a
player that grabs the first frame shows the hook rather than a blank sheet.

Both projects pass `npx hyperframes check` with **0 errors, 0 warnings** and
**49/49 text checks at WCAG AA**.

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

## The device is the store deck's iPhone front

The owner asked for the iPhone mockup instead of the Android emulator
screenshot, and handoff 0063 has already established that device for the
store assets. So the phone in the film is `site/img/mockup.png` (1022×2082)
with its measured screen rect (918×1990 at 52,46) — **the app's own capture
inside it, with the iOS chrome drawn over the top**: the 46px status bar with
9:41, signal, wifi and battery, and the 104×26 island. The capture is nudged
down by (46 − 24)px so the Android status bar hides behind the drawn one and
the gesture pill drops off the bottom.

Every number is `docs/store/src/lib/constants.ts`, verbatim. The device is
DOM rather than an image, so the chrome stays crisp at any scale and the
camera can dive through the screen.

Two consequences worth knowing: the bezel is a **gold** phone on warm paper,
and the dive is now a *through* move — the phone's black front sweeps past the
camera instead of a hairline frame flying off.

## The numbers are re-derived, and two on the site are stale

Every figure in the film was re-derived from the source, not copied from the
website:

| On screen | Source of truth |
|---|---|
| `54` bank parsers | `BankParserRegistry.all` — **54** entries |
| `97.6 %` exact match | the 82-case corpus, re-measured 2026-10-17 |
| `0` trackers | no analytics, advertising or crash-reporting SDK in any build |
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

## The read-time floor changed the copy

`check` enforces a rule from the framework's own motion reference: *a line the
viewer must read holds about 0.5 s plus 0.3 s a word, the film's last line at
least 2 s.* The first cut failed it in eleven places and it was right —
"Nothing is saved without your say." had 0.47 s on screen, which is not
readable by anyone.

The fix was **not to hold longer**. It was to cut the copy and re-time the
beats:

- "Your money data stays on your phone — unless you ask it to leave." (13
  words, 4.4 s needed) became two ruled columns plus "It never calls your
  bank."
- "Nothing is saved without your say." became "Nothing saves without you."
- The stat labels lost "sender-routed" and "82-case corpus"; the parse sheet
  lost its foot line; the message lost "Avl Bal Rs 24,180.55".
- Every label in the film is now **28px** — the phone-readable floor — and the
  depicted micro-type (the `SMS` chip, the badge numerals, the mock-up's own
  9:41) is marked `data-layout-ignore` as texture.
- The hero grew 25.0 → 25.8 s and the vertical 15.0 → 16.0 s. The extra
  seconds are what the reads needed.

## The vertical got its own composition, not a crop

1080×1920 is a different frame, and the first cut treated it as a wide one
with the sides removed: every block sat in the top half, the type was sized
for 1920, and the end card had no product in it at all. The polish pass:

- the headline 78 → 96px, the message body 28 → 32px, the parse values
  36 → 44px, the stat numbers 104 → 124px, the privacy statement 46 → 54px;
- every block moved into the vertical safe zone (the top ~250px and the
  bottom ~400px belong to the platform, not to the film);
- the device grew 620 → 720px wide — 67% of the frame, with the violet
  spotlight landing on the Swiggy row;
- the end card now has the app in it: the lockup, the rule, the tagline and
  the `Get the app` pill at the top, and the Home screen rising in below on
  a spring.

## Six traps, all of which cost a render

1. **GSAP owns `transform-origin`.** Its default is `50% 50%` and it
   *overrides* the CSS. Every tween that places or scales a screen must
   declare `transformOrigin: "0px 0px"`, or the camera scales about the wrong
   point and the dive lands 95 px off.
2. **A cropped image has its own origin.** The row crop starts at source
   `y 810`, so it sits at `ROW_Y` — not at the full screen's `BAND_Y`.
3. **`top: 100%` resolves against the nearest positioned ancestor**, not the
   element's own box. The review hint was a *sibling* of its card, so `100%`
   pushed it 1080 px down and off the frame.
4. **A container hidden in CSS must be animated itself.** The vertical's
   privacy scene animated `#privacy-foot` and `#privacy-cols` but not their
   parent `#privacy`, so the whole scene sat at `opacity: 0` and rendered as
   an empty sheet — while mounting, registering, and passing `check`.
5. **`--faint` is not a text colour.** `#918c7d` on paper is 3.1:1 and fails
   WCAG AA. In the film every label uses `--muted` (`#6e6a5c`, 4.97:1) and
   `--faint` stays decorative. The app's own axis-label grey is a different
   problem at a different size — but the *site* has the same `--faint` and
   should be checked for the same reason.
6. **The Studio preview rewrites the source.** It stamps `data-hf-id`
   attributes into the composition files, and it restored its own copy of a
   file over an edit made while the preview was running. Stop the preview
   before editing; strip the ids afterwards.

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

- The owner has not yet watched the second cut. Nothing is published; the site
  does not embed them.
- If the site takes the hero film, `site/video/` needs the file and
  `index.html` needs the embed, and the two stale numbers above should be
  corrected in the same pass.
- A square (1080×1080) cut for the social feed is not made.
- No captions. The Play promo slot does not require them, but the vertical
  cut would benefit if it is ever posted with sound off.
