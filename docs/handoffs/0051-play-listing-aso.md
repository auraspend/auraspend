# 0051 — Play listing ASO audit and canonical long description

**Date:** 2026-10-17
**Scope:** docs only. No app source, test or build file changed.
**Tests:** unchanged from 0050 — 616/616 (1 skipped); not re-run for a docs-only
change.

## What the question was

“Is the app long description ASO ready?”

## Finding: no, not yet

1. **The Play long description was not in the repo.** Handoff 0017 records it
   as 2,355/4,000 chars composed directly in the Play Console. The only long
   description checked into the tree was
   `fastlane/metadata/android/en-US/full_description.txt`: 786 chars, and it
   describes the **no-Google-Play-Services** build (“Google Drive backup is not
   available here”). That is F-Droid copy, not Play copy.
2. **It is too short and too technical for ASO.** 786/4,000 chars, no
   “expense tracker”/“budget app”/“money manager”/“UPI” keyword coverage, and
   the second paragraph leads with a 22 MB MiniLM encoder instead of a user
   benefit.
3. **Its privacy claim is wrong for Play.** “your messages never leave the
   device” is true for the F-Droid build, but the Play prod build offers
   user-initiated Google Drive backup. A Play listing that repeats the absolute
   claim would contradict the app and the Data safety form.
4. **The listing sources disagree.** `docs/store/README.md` says the Play title
   is `AuraSpend: Expense Tracker` and the short description is 79 chars;
   `fastlane/metadata/android/en-US/title.txt` is the brand-only `AuraSpend`
   and its short description is different. There is no single source of truth.
5. **The visuals are pre-terracotta.** The committed icon, feature graphic and
   seven screenshots were captured 2026-10-10, before the 0048 UI revamp. They
   still show the purple Aurora palette and Plus Jakarta Sans. They are valid
   Play sizes but no longer match the installed app — a conversion and trust
   problem independent of the copy.
6. **The Hindi listing is F-Droid copy too.** It says Drive backup is absent;
   that is false for the Play build. It needs a Play-specific translation.

## What was added

- `docs/store/play-listing-en-US.md` — canonical **Play prod** copy:
  - Title: `AuraSpend: SMS Expense Tracker` (30/30; recommended; current
    Console title is `AuraSpend: Expense Tracker`, 26/30).
  - Short: `Offline expense tracker: budgets, subscriptions, insights. No ads,
    no tracking.` (79/80; keep).
  - Full: 3,270/4,000 chars, keyword-led, feature sections, privacy with the
    Drive-backup exception, permissions, “who it is for”, CTA.
  - ASO rationale and the remaining gaps.
- `docs/store/README.md` — points at the canonical Play copy, records the
  current-vs-recommended title, warns that `fastlane/` is F-Droid copy, updates
  the Play status to 0.1.4 closed testing, and flags the pre-terracotta
  visuals.

## Why the new copy is ASO-shaped

Primary keywords (“expense tracker”, “SMS expense tracker”, “bank SMS”,
“budget app”, “money manager”) appear in the title/short and naturally in the
first paragraph and section headings. Secondary long-tail terms (“offline”,
“UPI”, “NEFT”, “IMPS”, “ATM”, “India”, “INR”, “Hindi”, “students”, “families”,
“rent”, “school fees”) are covered without stuffing. The first line carries
the hook and the primary term; the privacy section keeps the Drive exception;
the closing CTA is explicit.

## Verification

- Long description measured at **3,270 characters / 533 words** after the
  final edit; title 30/30, short 79/80.
- `docs/store/screenshots/01-home.png` and `feature-1024x500.png` were opened
  and confirmed to show the old purple brand, not the terracotta build.
- No build/test run; this session changed only Markdown.

## Next steps

1. Paste the title/short/full into the Play Console (or copy the Console text
   back to `play-listing-en-US.md` if it is kept instead). One source of truth.
2. Regenerate the icon, feature graphic and all seven screenshots from the
   current terracotta build (0048 follow-up).
3. Translate the Play copy to `hi-IN` (not the F-Droid copy); keep
   `fastlane/metadata` with the F-Droid build.
4. Refresh `docs/store/play-release-notes-0.1.3.md` for the next release.
5. Re-check the Data safety form against the new copy: no AuraSpend servers, no
   ads/analytics; Drive backup goes to the user’s own account.
