# 0067 — the website, rebuilt

> The public site was the last surface still carrying the old product. It showed
> the purple Aurora interface, quoted a Play listing that had been live for
> eight releases, and made a privacy claim the app had stopped honouring eight
> releases earlier. It is now rebuilt around the app as it actually is, and it
> is published from its own folder so that the engineering record stops being
> served as web pages.

**No app code changed.** The app is still 0.1.91 (versionCode 13), 614 tests,
lint baseline-only. This is a site-only change, plus two CI gates and a
deployment workflow.

---

## What was wrong

Three layers of drift, and the third was the serious one.

**The visual language.** The site was Aurora — violet and teal gradients,
glassy radial glows, 16px cards, pill buttons. The app is Quiet Ledger: warm
paper, near-black ink, one violet accent, hairline separators, bars instead of
rings. The site was showing screenshots of a UI that was two redesigns old
(`docs/store/raw/*.png`, captured 2026-10-10, explicitly marked stale in the
store README).

**The facts.** The hero said *"The Google Play listing is still staged and has
not been sent for review"* — false since 0.1.6, five weeks earlier. The
features page promised *"progress rings"* for budgets; the concept retired
rings deliberately, in favour of bars. *"Cash flow over the week"* described a
chart that had moved to Insights. *"Built with Material 3 and the Aurora
design system"* named a system that no longer existed. The changelog page was
**eight releases out of date** — it stopped at 0.1.3 and never mentioned the
terracotta revamp, the Quiet Ledger redesign, the ONNX crash fix, the
encrypted database or the app lock.

**The privacy policy said something false.** §6 read: *"The app does not have a
single 'erase all data' button. Use uninstall or Android's storage settings for
that."* The app has had **Settings → Data → Delete all data** since handoff
0040. §7's security section listed three bullets and mentioned neither the
encrypted database nor the app lock — both of which shipped in 0.1.5, two
releases before the page was last touched.

And underneath all of it: `docs/` was the GitHub Pages source, so **67 handoff
files, 14 ADRs, the evaluation reports and the review notes were being served
as web pages**, indexed by search engines, under the product's own domain.

## What the site is now

`site/` — seven pages, one stylesheet, one script, no build step, no
dependencies, no network requests.

| Page | What it is |
|---|---|
| `index.html` | The landing page: hero, how-it-works, review-first, the feature tour, your data, the privacy ledger, open source, distribution, FAQ, CTA |
| `download.html` | The three channels, what you need, checksum verification, building from source, updating, the 0.1.5 warning |
| `faq.html` | 22 questions in four groups, as native `<details>` accordions |
| `screenshots.html` | All eight screens, light and dark, with a note on how they were captured |
| `privacy.html` | The corrected policy — twelve sections, dated |
| `terms.html` | The corrected terms |
| `changelog.html` | All thirteen releases, newest first |

### The design language

Adopted from `docs/design/interface-concept/index.html` and
`ui/theme/Color.kt` **verbatim**, so the site and the product are visibly the
same object: `--stage #EBE7DE`/`#080807`, `--paper #F7F5F0`/`#0F0F0D`,
`--ink #17160F`/`#F4F1E8`, `--violet #4A2E7A`/`#C9B3F2`, plus the four
category colours, the income/expense status pair, the hairline and track
tokens. Plus Jakarta Sans, self-hosted, `tabular-nums` on every number.

The rules carried over intact: **no gradients, no glows, no box-shadow on any
content surface** (the one shadow in the system is on the floating device
frame), 1px hairlines for every separation, the accent spent only on things
you can act on, colour reserved for data.

### The five things the site now does that it did not

1. **Tells the actual story.** A three-panel transformation — a synthetic HDFC
   message → the parsed fields → the transaction row, rendered in the app's own
   row styles. It teaches the whole product in five seconds, and no competitor
   can copy it because it is true.
2. **Shows the review queue.** The triage card, the *Skipped (4)* line, the
   "messages it cannot read are kept, not dropped" rule. The app's strongest
   idea was invisible.
3. **Shows the privacy ledger.** A two-column table — *On the device* versus
   *Off the device* — plus SQLCipher at rest, the app lock, the identifier
   masking and Auto Backup being off. The app's strongest *feature* was
   invisible.
4. **Shows the proof.** 53 sender-routed parsers, 97.6 % exact on the 82-case
   corpus, 614 unit tests, 0 tracking SDKs — with the note that the model is
   optional and the parsing layer alone earns those numbers.
5. **Distributes honestly.** GitHub *available now*, Play *closed testing*,
   F-Droid *submitted*, each with what it does and does not carry. The old site
   said the Play listing was staged; the truth is better than that, because the
   truth is three channels.

### The logo

The launcher icon is still the old purple Aurora artwork, so the site uses a
**typographic wordmark** instead — "Aura**Spend**" with the accent on the
second half and a violet dot. The favicon is a violet "A" on the ink tile,
drawn as a path so it does not depend on the webfont. The old artwork is
still what ships on the phone; the site no longer pretends otherwise.

### The phone frame

The device on the site is the **same iPhone the Play Store deck uses**, built
from the same numbers: `docs/store/public/mockup.png` (the skill template's
1022×2082 iPhone 15 Pro bezel), the capture dropped into the screen rect that
was measured for it (`PHONE_SCREEN` in `docs/store/src/lib/constants.ts`), the
Android chrome cropped away, and the concept's own iOS chrome drawn in its
place (`IPHONE_CHROME` — the 46px status bar with 9:41, the signal/wifi/battery
row, and the 104×26 island). The site and the store deck are now the same
picture rather than two approximations of one.

The three details that make it work, and that are easy to get wrong:

- **The capture is nudged down by (46 − 24)px.** 46 is the height of the
  drawn iOS status bar; 24 is the height of the Android one the captures
  carry. The nudge puts the Android row exactly behind the drawn band and
  drops the Android gesture pill off the bottom.
- **The band is painted in the app's own background colour**, not in the
  page's. The strip above the shifted capture then reads as the device rather
  than as a gap, and it is what makes the frame work in both themes.
- **The island is a stadium, not a circle.** `border-radius: 50%` on a 4:1 box
  draws an ellipse. It is `999px`. It also covers the mockup's own punch-hole
  camera dot, which would otherwise peek out below it.

The iOS chrome is one `<symbol>` with a 390×46 viewBox, referenced by every
frame on the page, so it scales with the frame instead of needing per-size
math. The frame's shadow is a `drop-shadow`, not a `box-shadow`, so it follows
the bezel PNG's silhouette instead of casting a rectangle.

**The screenshots gallery uses the same frame** — all eight screens, in the
same iPhone, four across, with the caption centred under each. The bezel *is*
the frame there, so the card that used to sit around each capture is gone;
eight bare phones on a paper stage read as one image rather than eight
components.

---

## The deployment change

GitHub Pages published `/docs` from the `main` branch. It now publishes
`/site`, from a workflow:

```
.github/workflows/pages.yml
  actions/checkout@v6 → configure-pages@v6 → upload-pages-artifact@v5 → deploy-pages@v5
```

**The public URL does not change** — a project site's base path is
independent of the source folder, so `https://auraspend.github.io/auraspend/`
still holds and every relative link still resolves. The old site's four pages
are gone from `/docs`; `docs/README.md` now explains that the folder is the
engineering record and is no longer published.

> **One manual step, and only the owner can do it:** repository settings →
> Pages → Build and deployment → Source → **GitHub Actions**. Until that is
> switched, the old branch-based deployment keeps serving the old site and the
> new workflow will fail with a permissions error. The workflow cannot flip it
> itself; `GITHUB_TOKEN` cannot change a repository's Pages source.

## Corrections made to the copy

| Was | Now |
|---|---|
| "The Google Play listing is still staged and has not been sent for review" | Removed. 0.1.91 is cut and on the Play `alpha` track. |
| "Per-category monthly budgets **with progress rings**" | Bars. The concept retired rings deliberately. |
| "Insights: cash flow **over the week**" | Insights is period-scoped (month / 30 days / all time): ranked categories, daily spending, top merchants, and a month-over-month delta. |
| "Built with Material 3 and the **Aurora design system**" | The Quiet Ledger language, described in the site's own tokens. |
| Privacy §6: "The app does not have a single 'erase all data' button" | **Settings → Data → Delete all data** wipes every table, re-seeds the defaults and forgets the ingest cursors. |
| Privacy §7: three security bullets | Encrypted database (SQLCipher, Keystore-derived key, no plaintext fallback), identifier masking before storage, app lock + biometrics, Auto Backup exclusions. |
| — | A new §8 on the three builds and what each carries. |
| Terms §4.2: "the listing is staged in the Play Console and has not been published yet" | The `alpha` closed-testing track, the GitHub APK and the F-Droid build, each described. |
| Changelog: 4 releases, ending at 0.1.3 | All 13 releases, ending at 0.1.91, with the canonical `CHANGELOG.md` linked for the unabridged text. |
| `sitemap.xml`: four pages, `lastmod` 2026-10-11 | Seven pages, `lastmod` 2026-10-19. |

New facts the site now states, each verified against the source before it was
written: 53 parsers, 97.6 % exact, 614 tests, 0 trackers, SQLCipher, the app
lock, identifier masking, the 30 currencies with **no FX conversion**, the
F-Droid build's differences, the optional 22 MB **encoder** (not a generator),
the three distribution channels, and the real APK size (about 41 MB, from the
published release).

**Deliberately not claimed:** that the app converts currency, that it has
global search, that it sends budget or subscription notifications, that it has
a server, that dynamic colour is on by default, or that a new launcher icon
exists. Each of those is false today, and the site says nothing about them.

## Verification

Everything below was run, and the results are what they are.

- **`./tools/check_privacy_claims.sh`** — green. It caught two of my own
  lines on the first run (an unqualified "nothing is uploaded" and a "stays
  on your phone" table header) and both were reworded rather than suppressed.
  A stricter whole-site sweep that ignores line wrapping — so a phrase split
  across markup cannot escape — is also clean.
- **`./tools/check_license.sh`** — green. Its file list now points at
  `site/index.html` and `site/terms.html`.
- **HTML and links** — a parser-based check over all seven pages: no unclosed
  tags, no duplicate `id`s, no missing `alt` attributes, and every internal
  link and anchor resolves to a real file and a real `id`. Clean.
- **External links** — all 24 resolve (200), except the new pages' own URLs,
  which are 404 until the site is deployed, and `gnu.org`, which 403s a
  command-line client and is fine in a browser.
- **Rendered at 390, 1440 and full-page, in both themes** — checked by eye
  against the captures. Two real defects were found this way and fixed:
  - **The `<img>` height attribute beat the CSS width.** Every screenshot
    carried `width="1170" height="2532"`, and `width: 100%` without
    `height: auto` let the attribute's height win — the phone rendered 2,500px
    tall and pushed the entire hero copy below the fold. The fix is one
    declaration in the `img` rule, and the comment above it says why.
  - **The transaction row's title and meta were inline**, so they read
    "SwiggyFood & Dining · 8:14 pm" instead of stacking.
- **Interaction** — eleven assertions run in a real browser: the theme toggle
  flips the attribute, swaps every light/dark capture, persists the choice and
  updates its own label; the mobile menu opens and closes with the right
  `aria-expanded`. All pass.
- **No-JS** — the scroll-reveal styles are scoped to `.js`, which the inline
  head script adds, so without JavaScript the content is simply visible
  rather than invisible.

The app's own gates are unchanged and still green: 614 tests, lint
baseline-only, the R8 keep-rule gate, the native audit.

## Traps worth keeping

- **Chrome clamps `--window-size` to 500px.** A "390px" screenshot from
  `html_shot.py` is a 500px layout cropped to 390, so it *looks* like a
  horizontal overflow. The `--full-page` path uses real device emulation and
  can go below 500. Trust the full-page path for mobile.
- **The privacy gate is line-scoped.** A claim split across two lines of
  markup escapes it. The stricter sweep in this handoff is a strip-tags pass,
  not a line pass, and it is the one that would catch it.
- **The old site embedded the icon as an `<img>` with a `store/icon-512.png`
  path.** The new one uses an SVG favicon, because the old artwork is purple
  and the site is not.
- **The phone frame is not a CSS bezel.** It is a PNG with a screen rect
  measured for it, and the capture is positioned by hand inside that rect. If
  the store editor's `PHONE_SCREEN` or `IPHONE_CHROME` constants change, the
  site's `.iphone__screen` and `.iphone__chrome` rules change with them, or
  the capture will sit a few pixels off and the Android status bar will peek
  out from under the drawn one.
- **The header and footer are duplicated across seven pages.** There is no
  build step, so they are copied. `tools/stamp_site_chrome.py` copies them out
  of `index.html` verbatim and marks the current page for assistive
  technology; re-run it after changing the chrome in `index.html`.
- **The store's `raw/` folder is now referenced by nothing.** It was the old
  website's captures. The site uses the current ones from
  `docs/store/public/screenshots/android/phone/en/`. `raw/` was left on disk
  and the store README says so.

## Open items

1. **Switch the Pages source to "GitHub Actions"** — repository settings. The
   only step that cannot be done from a workflow, and the site does not go
   live without it.
2. **Merge `ui_enhance` into `main`.** The pages workflow triggers on `main`,
   and `main` is the release branch, so the site ships with the next release
   rather than ahead of it. That is deliberate.
3. **A Play Store link for the closed test.** The site currently says "in
   closed testing on the `alpha` track" with no link, because a listing that
   is not public has no URL worth publishing. If there is a tester-list link,
   it belongs in the two distribution sections.
4. **The handoff log is still not linked from the site.** The repository is
   public, so `docs/handoffs/` is readable on GitHub; the site's "Built in the
   open" section links the ADRs and the commit history instead. Linking the
   handoffs as well is a one-line change if the owner wants the flex.
5. **The README's feature table still says "Canvas pie charts".** That is the
   repository's own README rather than the site, so it was left alone, but it
   contradicts the current UI in the same way the old website did.

## Next step

Switch the Pages source, merge to `main`, and open the live site. Then read
the home page top to bottom once, against the app, and check that every
sentence is still true — the site makes a lot of specific claims now, and
that is the point, but it is also a new thing to keep honest.
