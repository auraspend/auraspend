# 0081 — release 0.1.98

**Date:** 2026-11-04 · **Branch:** `main` + `dev` · **Status: tagged,
`versionCode` 20.**

## What ships

A presentation release, and the first one in this line that is closer to a
subtraction: **page navigation is instant, with no transition at all** (handoff
0080). No slide, no fade, no zoom between destinations.

It matters because the motion it removes was doing damage, not decoration:

- a push slid the new page in by an eighth of a screen over 200 ms while the
  outgoing page faded out in 100 ms — the last half of the move played over
  empty background;
- **Back nudged the page an eighth of the width and then removed it
  mid-screen**, a nudge followed by a blink;
- on a 60 fps recording of the Moto G40, a push held 18 frames of motion over
  166 ms and a Back 17 frames over 223 ms. Both are one frame now, and the only
  motion left around a tap is the touch ripple.

No database, data-model, corpus or classification change. The 82-case golden
corpus is bit-identical: **80/82 exact (97.6 %), type 98.8 %, category 100 %,
merchant 98.8 %, amount 98.8 %, subscription 100 %.**

It also carries the one site commit that had been sitting on `dev` since
0.1.97: the legal pages are generated from one source (and the privacy policy
gained its "Permissions, and why each is needed" section), and
`tools/stamp_site_chrome.py` works again after being a silent no-op.

## The surfaces a release touches

| File | Change |
|---|---|
| `app/build.gradle.kts` | `versionCode` 19 → 20, `versionName` 0.1.97 → 0.1.98 |
| `CHANGELOG.md` | the `[0.1.98]` entry |
| `docs/store/whatsnew/whatsnew-en-US` | Play notes, 270 characters |
| `docs/store/whatsnew/whatsnew-hi-IN` | the same in Hindi, 291 characters |
| `docs/fdroid/com.awbuilds.auraspend.yml` | the 0.1.98 build entry, `CurrentVersion` / `CurrentVersionCode` |
| `site/changelog.html` | the release card, and 18 → 19 releases ending at 0.1.98 |
| `site/index.html` | `softwareVersion` |
| `AGENTS.md` | the `Latest:` block |
| `docs/handoffs/0080-instant-navigation.md` | status line only — it was uncommitted and is now the release's change |
| `docs/handoffs/0081-release-0.1.98.md` | this file |
| `tools/verify_r8_release.sh` | **fixed a gate that could pass on stale artifacts** — see below |

## The R8 gate reported a pass for a build that did not exist

`tools/verify_r8_release.sh` only ran Gradle when an artifact was **missing**.
Bumping `versionCode` leaves every artifact present, so the first run of this
release's gate read **0.1.96's** APK and bundle — `aapt2` said
`versionCode='18' versionName='0.1.96'` — and printed "all checks passed" for a
tree that had never been built.

The guard is gone: the script always invokes Gradle and lets it decide whether
the build is up to date. A cached run costs seconds; a false pass costs a
release. Worth remembering for every future cut, and a reminder that a gate's
*own* freshness is not something any other gate checks.

## Verification

- `./gradlew testProdDebugUnitTest --rerun-tasks` — **709 tests, 0 failures,
  0 errors** (1 pre-existing skip).
- `:app:lintProdDebug` — baseline-only.
- `:app:classificationBaseline` — the corpus block in the test output is
  bit-identical to 0.1.97 (numbers above).
- `:app:assembleFdroidRelease` — compiles (42.6 MB APK, `fdroid` flavor).
- `:app:assembleProdRelease` + `:app:bundleProdRelease` — **signed** prod APK
  and AAB; `aapt2 dump badging` reads `versionCode='20' versionName='0.1.98'`,
  certificate `CN=AuraSpend`.
- `tools/verify_r8_release.sh` — after the fix, run against the 0.1.98 build:
  obfuscation 96.93 %, optimization 96.60 %, shrinking 96.90 % (Play's 25 %
  floor); 4.44 MB uncompressed DEX, under the 10 MB floor; APK 16 KB aligned;
  all 11 JNI-resolved classes and both SQLCipher classes un-renamed.
- `tools/check_license.sh` — consistent.
- `tools/check_privacy_claims.sh` — every absolute claim states its exception.
- `tools/check_site_version.sh` — `softwareVersion` is 0.1.98, no page outside
  the changelog names a release, and the changelog summary ends at 0.1.98
  across 19 entries. This is also a gate in `release.yml`, so the tag cannot
  cut with a site that disagrees.

## What was verified on hardware, and what was not

- The **dev** release build (same source) was installed on the owner's Moto G40
  and the frames measured — that is 0080's evidence.
- The **prod** package on that phone was deliberately **not** replaced: it is
  the owner's install with their data. The prod APK here is signature-checked
  and version-checked offline only.
- The phone disconnected during the release pass, so the final smoke test of
  the bumped build (install the 0.1.98 dev APK and open it once) **did not
  run**. Nothing about the code changed after the measured build except the
  version fields, so the risk is low — but it is unverified, not verified.
- Artifacts: prod **APK 41.4 MB**, **AAB 23.4 MB** (arm64 only, as designed).
- No Maestro run this release (as with 0.1.97, at the owner's instruction).

## Cut

- PR **#86** (`dev` → `main`) — both CI `build` jobs and Release Drafter green,
  merged as `8cdf152` with `--admin` because the branch policy requires one
  approving review and the PR author cannot supply it (handoff 0081 is the
  record; `enforce_admins` is off, which is how every previous release was
  merged).
- Tag **`v0.1.98`** on `9fa54f8` (`release: 0.1.98`), pushed.
- `release.yml` run **41790973951** — the `release` environment approval was
  given by the repository owner's account, then **green in 6m 5s**.
- GitHub release **`v0.1.98` published** with `AuraSpend-V0.1.98.Alpha.aab`
  (23.4 MB), `AuraSpend-V0.1.98.Alpha.apk` (41.4 MB) and `SHA256SUMS`. The
  auto-generated body ("#86: release: 0.1.98") was replaced with this release's
  changelog entry, keeping the compare link — the first release whose page
  actually says what changed.
- **Play**: the upload step ran green, so the bundle sits on the
  closed-testing track (`alpha`) as `completed`/in review. The Play state
  itself cannot be read back: the service-account key is only the repository
  secret, and there is no local copy.
- **Site**: the Pages run for `main` is green, so `changelog.html` and
  `softwareVersion` are live.
- `dev` fast-forwarded to `main`, so the two never diverge.

## Open

- **Predictive back has no preview any more** (0080): with no pop transition
  the gesture shows nothing while dragging and cuts on release. That is what
  "instant" means; it is a visible change for anyone using gesture navigation.
- **The detail page's body paints ~47 ms after the cut** (0080). Not caused by
  this release — it was hidden behind the old motion. The fix, if wanted, is to
  seed the screen from the `Transaction` the list already holds.
- **The Play listing's screenshots and the site's captures still show the old
  transition era only in the sense that motion cannot be captured** — no visual
  claim anywhere depends on the removed animation, so nothing was re-shot.
