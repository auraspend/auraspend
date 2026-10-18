# 0062 — First run: the splash and onboarding in the concept's language

**Date:** 2026-10-18
**Branch:** `ui_enhance` — **working tree only, not committed**.
**Scope:** `ui/onboarding/OnboardingScreen.kt`, `ui/splash/SplashScreen.kt`,
`ui/designsystem/{CurrencyPicker,AuraTokens}.kt`, `ui/theme/Color.kt`, docs. No
behaviour change: the same four pages, the same permission ask, the same currency
picker, the same Drive restore.

## Why

The concept covers the six screens inside the app; the first-run surfaces were
never part of it and were still Aurora — a violet gradient medallion, a violet
pill button, a violet glow behind the splash mark. They were the last place in
the app where the old language showed, and the first thing a new user sees.

## What changed

**The gradient is gone, everywhere.** `AuraGradients` (`aurora`, `auroraSoft`,
`heroWash`, `onAurora`) is **deleted**, along with the three `AuroraGradient*`
stops in `Color.kt` and the now-unused `AuroraViolet`/`AuroraTeal`. Onboarding and
the splash were its only two users. The concept retires the gradient hero on
purpose — "type does the work gradients used to" — so the token was dead code
inviting a comeback.

**Onboarding.**
- The 148dp gradient medallion became a **120dp tonal disc**: the concept's own
  badge treatment (`.alert-ico`, `.rowlink-ic`) — `onBackground` at 6 % with an
  ink glyph, no border, no shadow. 64dp on the currency page, which needs the room.
- Icons are the **outlined** set, matching the nav bar and the category avatars.
- Title `headlineMedium` (26sp/600) → `AuraType.screenTitle` (23sp/700/−0.575).
- Description `bodyLarge` (16sp) → `AuraType.bodyCopy` (15sp/400, 22sp leading), a
  new token. Material's 16sp read as a different document next to 13sp rows.
- **Continue** was a violet pill. It is now the concept's `.btn-primary`: ink fill,
  paper label, 16dp radius, 52dp, `AuraType.buttonPrimary`, and `track`/`faint`
  when disabled.
- **Skip** is the concept's `.link` — 12sp/600 in the one accent, on a 48dp target.
  It is the only violet on the page, which is the point: violet marks what you can
  act on, and the filled button does not need it.
- The page indicator's active dot was violet; it is **ink** now (same rule).
- The Drive-restore card's bare violet glyph became the concept's `.rowlink-ic`
  tile (32dp, 10dp radius, 6 % ink, 16dp glyph) with a faint trailing chevron.
- Page motion dropped its scale and horizontal translation. Three graphics layers
  per page for one gesture — on top of the pager's own slide — and a zoom the
  motion rules do not have. Alpha only.

**Splash.** The 320dp radial violet glow is gone; the mark fades in instead of
springing in with overshoot (the concept's springs do not overshoot outside
selection); the wordmark uses the new `AuraType.wordmark` (28sp/700) rather than
`displaySmall`; the tagline is `bodyCopy`; the footer line is `budgetSub` in
`faint` rather than `labelMedium` at 60 % alpha (a fourth grey with no name).

**Currency picker** (shared by onboarding and the Settings sheet, so both moved):
rows now use the concept's settings-row metrics — 14dp vertical padding, 12dp
gaps, `settingsRowTitle`/`settingsRowValue` — the symbol is ink rather than the
accent, and the selected check is ink. The "amounts are not converted" note is
`rowLinkMeta`.

## Deliberately not changed

**The app icon stays on the splash.** It still carries the old violet artwork, and
it is now the only colourful thing in the app — but the concept does not specify
an icon (the "A" tile in its own topbar is the *document's* logo, outside the
phone frame), and the launcher icon, website and store visuals are a tracked
follow-up in `AGENTS.md`. Swapping the splash mark for a hand-drawn "A" would have
made the splash disagree with the launcher instead of agreeing with the app.

## Verification

- `:app:compileDevDebugKotlin` clean, no `w:`.
- Walked all four pages plus the splash, **light and dark**, on the arm64 AVD at
  390×844 dp (the emulator's app data cleared so the flow actually runs). The
  captures are in `~/aura-fidelity/app/onb-*.png`.
- No new strings: the copy is unchanged, so EN/HI parity is untouched.
- The currency sheet inside Settings was re-checked, since it shares the picker.

## Open

- The launcher icon / store visuals are still the old brand (`AGENTS.md` tracks
  it). The splash now makes that gap visible.
- Not walked on the Moto — the release dev APK on the phone predates this change.
