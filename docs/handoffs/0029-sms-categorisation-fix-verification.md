# 0029 — SMS categorisation: on-device findings and four fixes

**Status:** done 2026-10-13 — committed (`dba7783`, `d77612b`, `6ba4e78`), merged to
`dev` (PR #46), released as **0.1.3** (versionCode 4, tag `v0.1.3`); the dev→main PR
is #47.

## How the device was used (privacy)

The owner's vivo V2158 (Android 14) was connected over adb Wi-Fi. Its installed app is
`0.1.1-play` (versionCode 2) signed with a debug-class certificate we do not hold, so
nothing of theirs was replaced: a **Dev-flavor build of this branch was installed
alongside** (feature-identical per ADR 0010, debuggable, so its Room database could be
read) and pointed at the owner's real inbox. Every pulled artifact lives in
`$TMPDIR/opencode/vivo-audit-2026-10-13/`, **outside the repository**. No message text,
number or name from the device appears in this handoff or in any committed file — code
and tests carry synthetic twins only (`XX####`, `9000000042`).

## What the real inbox showed, and the four fixes

1. **Genuine ICICI card spends were deleted by the hard veto.** "INR ▮▮▮ spent …
   If not you, call 1800 2662/SMS BLOCK ▮▮▮▮ to …" — ICICI appends that alarm tail to
   every card alert (like Canara's "Dial 1930"), and the conditional-framing veto had
   no rescue, so three real spends appeared **nowhere** — not saved, not skipped, not
   in the unrecognised list. Fixed with a narrow `bankAlarmTailPattern` exception
   (`if not you` + `call <number>/SMS BLOCK <code> to <number>`); all other
   conditional framings stay unconditional. `SmsIngestFilterTest` pins the rescue and
   that phishing framing is not rescued by it.
2. **The queue UI was hidden behind the optional model.** `AutoDetectTab` checked
   `!isModelDownloaded` *before* the queue, so a user who skipped the 22 MB model saw
   no queue, no progress header and none of the triage actions while 150 messages
   drained. The precedence is extracted to a pure `autoDetectTabMode()`; the queue
   now always wins over the model prompt, which shows only for an empty queue.
   `AutoDetectTabModeTest` pins the order.
3. **Credits whose literal words poisoned the type score were skipped.**
   "reversal of debit transaction" scores both lexicons (the literal `debit`), and
   "Bharat Bill Payment System" contains the debit phrase "bill payment" — both came
   back typeless and the rows were skipped. The phrases are neutralised before
   scoring. Also: "refund … initiated" is a (no-completion) veto, and
   "unsuccessful" joins the decline notices.
4. **Dashed month-name dates never parsed — two bugs deep.** The date pattern's year
   separator class was written `[ -,]`: a character *range* (space–comma), so a
   literal dash could never match, "21-JUL-26" was unextractable, and those
   transactions fell back to `LocalDateTime.now()` at save time. Worse, java.time
   text parsing is case-sensitive, so even a matched "AUG" would not parse as `MMM`.
   Fixed the class to `[ ,-]`, made `parseDate` case-insensitive, added the
   dash-month formats. The wrong-date fallback had also collapsed the duplicate
   window onto the save hour — an ₹840.76 card payment was skipped as a "duplicate"
   of an unrelated ₹892 credit 37 minutes earlier; the duplicate detector's layer-3
   amount gate is tightened (same payment = same amount, 0.2 %/₹1 tolerance) as
   defence in depth. `BankMessageParserTest` and `SemanticDuplicateGuardTest` pin
   the date and the gate.

## Verified on the device (with the fixes installed)

- The full 1,952-message inbox drains to **exactly the offline-predicted candidate
  set: 706 rows** (469 saved / 237 skipped, 0 failed); every saved amount and type
  matches the parse layer; no duplicates; balance tails never misread.
- **Notifications**: one ongoing determinate notification during a background drain
  ("Categorizing messages… 694 of 706") and one end-of-drain summary; no per-run
  spam.
- **Queue UI without a model**: "44 messages waiting", "450 saved · 212 skipped",
  the triage list and swipe hints all render mid-drain with the model absent.
- **The false-skip pair re-run**: both rows now save as INCOME with their real dates
  (2026-07-21, 2026-08-04). Re-staged duplicates re-skip without double-saving
  (36- and 40-row rounds).
- `./gradlew :app:testProdDebugUnitTest` — **396/396 green** (382 before this
  session: +6 mode, +4 filter, +3 parser, +1 duplicate guard). `lintProdDebug`
  green.

## Deliberately not done

- **The golden corpus was not extended.** The new shapes (ICICI alarm tail, Kotak
  reversal, dashed dates) are pinned by unit tests; adding corpus entries shifts
  every measured number (and those quoted in AGENTS.md and `docs/evals/README.md`),
  so it is a deliberate re-baseline step: add synthetic twins, re-run
  `:app:classificationBaseline` + `FusedAiEval`, then update the quoted figures.
- Rows saved **before** the date fix keep their save-time dates in the Dev app's
  database (test data).
- The owner's own app still runs `0.1.1-play`: replacing it needs the original
  signing key (not held) or uninstall + install (the derived data would be rebuilt
  from SMS). The Dev build stays installed for verification.

## Open risks / next step

- The alarm-tail exception has one documented residual: a phishing SMS copying the
  exact "If not you, call …/SMS BLOCK …" shape *and* reading as a completed
  movement. The narrow pattern keeps this far below the alternative (losing every
  ICICI card spend), and the broader framings stay vetoed.
- Next: the corpus re-baseline (above); merge PR #47 to `main` so the release
  reaches the default branch.
