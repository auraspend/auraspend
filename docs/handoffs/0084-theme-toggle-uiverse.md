# 0084 — The site's theme switch is the Uiverse day/night pill

**Date:** 2026-11-06 · **Branch:** `dev` · **Scope:** the public website only — no app
code, schema, strings or dependencies.

## What changed

The header's moon/sun **icon button** is replaced by RiccardoRapelli's MIT-licensed
Uiverse switch ["jolly-chicken-91"](https://uiverse.io/RiccardoRapelli/jolly-chicken-91):
a 60×34 pill whose knob is the sun by day and the moon at night, with six clouds
drifting across it and four stars that rise into the left half after dark.

- **`site/index.html`** carries the new markup; `tools/stamp_site_chrome.py`
  copied it into all ten pages.
- **`site/css/style.css`** gains the component, credited in a comment and in
  `docs/licenses/README.md` (full notice in
  `docs/licenses/MIT-RiccardoRapelli-ThemeSwitch.txt`). The colours are **not**
  the component's blue/yellow/black: they are Quiet Ledger tokens — a lavender
  daylight sky, the warm ink night, an amber sun, paper moon, muted craters.
- **`site/js/site.js`** listens for `change` on the checkbox instead of `click`
  on a button, and `paint()` syncs `checked` to the resolved theme (setting
  `checked` programmatically does not fire `change`, so there is no loop).
- **The control is a real `role="switch"`** named "Dark theme", so the checked
  state is the accessible announcement instead of a rewritten action label.
  The input covers the pill (opacity 0, not `display:none`) and a `::before`
  extends the tap target to 44 px without changing the 34 px drawing.
- **The switch's state is driven by CSS variables set by the resolved theme**
  (`--toggle-track`, `--toggle-knob`, `--toggle-knob-x`, `--toggle-craters`,
  `--toggle-stars`, …), not by `:checked`. That means it is painted correctly
  **before `site.js` runs and with scripting off**, when the old icon button
  relied on CSS alone.
- **`tools/stamp_site_chrome.py`'s `CURRENT_FOOTER` map still named the retired
  `privacy.html` / `terms.html` slugs**, so the first stamp run aborted with
  "no link with href=privacy.html". It now names `privacy-policy.html` /
  `terms-of-service.html`; the two legal pages are stamped with the shared
  chrome for the first time (they had carried `<!--@HEADER-->` placeholders).
- **The header CTA is hidden at ≤480 px.** The 60 px switch replaced a ~25 px
  squeezed icon button, and at 360–480 px the header wrapped "Get the app" onto
  two lines and shrank the menu to ~21 px. The mobile nav already lists
  Download and the hero repeats the call to action, so the header keeps logo,
  switch and menu; `.icon-btn` also gets `flex: none` so the menu keeps its
  40 px target at 481–860 px.

## Decisions

- **Adapted, not copied verbatim.** The component's structure and motion are
  kept; its `#2196f3`/`black`/`gray` palette would have been the only blue on a
  warm-paper site. The dark track is deliberately theme-independent (`#17160f`,
  not `var(--ink)`), because `--ink` flips to near-white in dark mode and the
  switch must show *what you are switching to*, not the current theme.
- **`role="switch"` + stable name** over the old action-phrased `aria-label`;
  screen readers now announce the on/off state instead of a button whose name
  flips.
- **Reduced motion is already handled globally** (`prefers-reduced-motion`
  collapses animations and transitions), so the clouds and twinkle snap rather
  than drift.

## Verified

- `./tools/check_site_version.sh` → green (0.1.98).
- `./tools/check_license.sh` → green, including the new attribution.
- `./tools/check_privacy_claims.sh` → green.
- All ten pages in headless Chrome (Playwright, system Chrome) at 390 px:
  exactly one `role="switch"` named "Dark theme"; a tap 3 px above the 34 px
  pill sets `data-theme="dark"`, checks the input and persists across reload;
  **zero console/page errors; zero horizontal overflow**.
- Header measured at 360, 390, 430, 480, 481, 520, 600, 720, 860, 900 and 1280:
  switch 60×34, CTA single-line 109 px where visible, menu 40×40; CTA hidden
  at ≤480. The index's header was captured in light and dark at 360, 481, 768
  and 1280; the focus ring is a visible violet outline offset from the pill.
- 44 px tap target: a click 3 px above the 34 px pill toggles the theme.
- Light and dark captures at 360, 481, 768 and 1280; the focus ring is a visible
  violet outline offset from the pill.
- `javaScriptEnabled: false` + system dark → the page and the switch are both
  night (track `rgb(23, 22, 15)`, knob translated 26 px, stars visible).
- Local storage/`prefers-color-scheme` behaviour unchanged: stored choice wins,
  system changes are followed only when nothing is stored.

## Open

- At **861–899 px** the full desktop nav wraps to two lines and the CTA to two
  lines. This is pre-existing (the old header wrapped at 861 too) and the wider
  switch moves the clean threshold from ~880 to ~900. A 900 px nav breakpoint
  would fix it; deliberately not bundled into this change.
- `site/download.html`'s body still links the retired `privacy.html` page, and
  the retired `terms.html` links the retired `privacy.html`; that is the open
  legal-page migration item from handoff 0080, not touched here.
- The component's three 10 % light-ray circles overlap at night and read as a
  soft grey glow around the moon. Faithful to the source; if the owner wants a
  flatter night pill, reduce `.theme-toggle__light-ray` opacity or hide the
  rays in the night state.
- Visual verification is headless Chrome only — not yet walked on a real phone.
- No app code changed, so the app test suite was not run.

## Next step

Walk the site's header in light and dark on a real phone (the 360 px capture is
the reference), then decide whether the night glow should be softened.
