# 0045 — 0.1.4 shipped to GitHub and Play; the draft-publish bug found and fixed

**Status:** done. `v0.1.4` tagged on `741042d` (the #68 merge). The GitHub
release is **published** with the signed AAB/APK/checksums, and **Play closed
testing (`alpha`) holds 0.1.4 — status `completed`**. PR #69 (merge `d7668d0`)
fixes the one bug the release exposed.

## The release

- 0.1.4 / versionCode 5, with the changelog entry and the per-language notes in
  `docs/store/whatsnew/` (both verified ≤500 chars).
- Release PR #68 (dev→main) merged; tag pushed; the tag run
  (`38531133280`) built and verified the artifacts and **uploaded to Play** —
  the log shows `Validating tracks: 'alpha'` → `Successfully uploaded 1
  artifacts` → `Successfully committed`. The former upload-key precondition is
  evidently cleared.
- The GitHub release page had been left as a **draft** by the run (see below);
  it was published by hand within the minute, so nothing was ever half-shipped.

## The bug the release exposed (PR #69)

The tag run's "already has a release" check treated **any** release under the
tag as published — and Release Drafter's rolling draft sits under exactly that
tag — so the publish step was skipped and the release stayed a draft with all
assets attached. The check now reads `--json isDraft` (only a non-draft counts
as published), and a final "publish if it is still a draft" step guarantees the
end state whatever the drafter decides.

Lesson for the house: `gh release view <tag>` sees draft releases too —
"exists" is not "published".

## Credential hygiene (the shared Play service account)

The public Actions log was scanned for identity strings: it contains only the
secret **name** (`PLAY_SERVICE_ACCOUNT_JSON`) and GitHub's own `***` masking —
no account, project or key material. The AGENTS.md rule stands: that identity
is never recorded in this repository.

## F-Droid

All app-side and metadata work is staged (`docs/fdroid/`). The fdroiddata merge
request — the owner's GitLab account, with the file, title and description
delivered — is the last step; issue #65 tracks it. Once merged, F-Droid builds
`assembleFdroidRelease` from `v0.1.4` itself and future tags update
automatically.

## Pending on the owner's device

Unchanged: re-enable wireless debugging, then the visual pass (dark/AMOLED
icons, the chart window), the two 0041 leftovers, and re-typing the saved EPF
rows (Expense + Investments).

## Next

- Owner: open the fdroiddata MR. Watch Play's alpha roll-out (already
  `completed`) and F-Droid's review queue.
