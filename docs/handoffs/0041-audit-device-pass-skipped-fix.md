# 0041 — the audit device pass, and the Skipped fix it found

**Status:** done. The ten 0040 features were walked on the owner's device (dev
build, real inbox, model intentionally not downloaded). The pass found one real
gap and fixed it: with **no model downloaded and an empty queue** — the fresh,
model-less configuration — the Auto Detect tab shows the model prompt, and the
**Skipped section was unreachable** (PR #59 had wired it into the QUEUE/EMPTY
branches only). `fix/skipped-under-model-prompt` (PR #60 → `dev`, merge
`5bea266`): the MODEL_PROMPT branch now lays the prompt out on top and the
skipped list below it, mirroring EMPTY. **531/531 tests**, lint clean, CI green
(build job 7m27s on a cold day).

## Verified on-device, one pass each

- **Settings** — the SMS-access row reads the live permission ("Granted — new
  bank messages are read on-device"); the categorisation-notifications toggle
  and the "Delete all data" row render where the audit planned them.
- **Categories** — the add/edit dialog shows the Icon and Color pickers.
- **Activity** — the month header + chevrons render; stepping back moves the
  window and both the list and the summary strip follow it (the record count
  changes with the month); the sort control is present.
- **Smart Add** — "Skipped (N)" + subtitle + rows + "Classify again"; after the
  fix, the same section renders **under the model prompt** (the model-less
  default). Live check of "Classify again": the skipped count decrements, the
  row re-enters the queue ("message found" + Save All / Skip All appear), and
  the drain picks it up.
- **Transaction detail** — the hero's category row opens the picker; Share and
  Duplicate both render.
- **Insights** — tapping a category slice lands on Activity filtered (the
  record count drops to that category's rows).
- **Quick Add** — the date chip shows today's date; the sheet scrolls to it.

Not visually verified (the keyguard returned mid-pass; both test-covered):
the **editor's recurring switch** and the **subscription suggestion dismiss**.
The sheets they belong to (transaction editor, recurring add) were verified in
earlier sessions.

## The gap, and why it mattered

The Auto Detect tab's model prompt is what a settled, model-less install shows
by design (`autoDetectTabMode`: permission → scanning → queue → model prompt →
empty). The skipped history is the only surface skipped messages have ever had,
and reviewing what was set aside is exactly this tab's job after a drain — so it
must never hide behind an optional feature's invitation. MODEL_PROMPT now
mirrors EMPTY (`weight(1f)` LazyColumn below the prompt block).

## Session notes

- **The consent card and the tab's model prompt are two separate layers**:
  `ClassificationScreen` renders the inline consent card (`consentRequired`,
  with "Not now" / "Add (~22 MB)") above the tab body, and `AutoDetectTab`'s
  MODEL_PROMPT branch renders its own download block. Dumps on a model-less
  install show text from both, which reads like one duplicated dialog — it is
  not. "Not now" only dismisses the consent layer; the tab's own prompt stays.
- **uiautomator taps must use a dump taken immediately before the tap.** Twice
  this pass a tap computed from an older dump hit the wrong control (it opened
  Activity's search field, and once pressed the download CTA — the download
  never started and the prompt was cancelled). When a coordinate looks
  suspicious (a "Cancel" resolving to the top-left), treat the dump as stale
  and re-take it.
- The phone re-locked mid-pass; the keyguard cannot be dismissed from adb. The
  screen timeout was set to 30 min for the pass and restored to 15 s after.
- The re-queued statement notice was still draining when the keyguard returned;
  its landing (expected: re-skipped by the same guards) was not re-checked.

## Owner items still open

- **EPF classification**: "contribution … has been received" books as INCOME —
  keep, reclassify, or dismiss?
- **Play**: upload-key reset (Google approval) → AAB upload → release notes →
  send for review. The 0.1.3 notes are staged in `docs/store/`.
- **Cut 0.1.4 when asked** — it carries: the on-device encoder finally live,
  month-first dates + arrival fallback, the Activity search crash fix, the
  Smart Add lag fix, the paise-flat duplicate gate, detected subscriptions,
  and the audit features + this fix.
- **Cleanups**: GitHub Support purge (pre-scrub history + PR #58 residuals);
  delete the temp audit dir (holds real SMS) and the PennyWise clone; retire
  repo-level signing secrets; verify the Dependabot PRs.

## Next step

- The 0.1.4 dev→main PR + tag on the owner's word.
