# Submitting AuraSpend to F-Droid

The app side is done (ADR 0012): the `fdroid` flavor builds without Google
Play Services, CI compiles it on every PR, and the metadata draft sits next to
this file. What remains is the fdroiddata merge request, which needs a GitLab
account — so it is an owner step.

## 0. Prerequisite — cut the next release

fdroiddata builds from a **tag**. The `Builds:` entry points at `v0.1.7`
(adjust the three values if the version is different):

```
dev → main PR, then: git tag v0.1.7 && git push origin v0.1.7
```

Then check `app/build.gradle.kts` — `versionName` / `versionCode` must match
the tag, and both must match `docs/fdroid/com.awbuilds.auraspend.yml`.

## 1. Fork fdroiddata and open the MR

1. Create an account on <https://gitlab.com> (F-Droid's fdroiddata lives at
   <https://gitlab.com/fdroid/fdroiddata>; a GitLab.com account works — when
   opening the MR, a comment bot offers authorization if that is ever needed,
   but for metadata-only MRs it is not).
2. Fork `fdroid/fdroiddata`, create `metadata/com.awbuilds.auraspend.yml`
   from `docs/fdroid/com.awbuilds.auraspend.yml`, commit, push, open a merge
   request against `master` using the repository's MR template.
3. First-time contributors are asked to confirm they are the app's author (or
   that the author does not oppose inclusion) in the MR thread — answer there
   as the upstream author.

## 2. What the reviewers will look at, and the answers

- **Non-free dependencies** — the `fdroid` flavor (ADR 0012) has none:
  no Play Services, no Firebase, no ads, no analytics. `./gradlew
  :app:dependencies --configuration fdroidDebugRuntimeClasspath` shows no
  `com.google.android.gms` / `com.google.api.*` entries, and
  `assembleFdroidDebug` is built in CI on every PR.
- **Antifeatures** — none expected: no tracking, no ads, no non-free network
  dependency. The only network use is the *optional* model download.
- **The optional model download** — an Apache-2.0 MiniLM encoder (~22 MB),
  behind an explicit in-app consent tap, SHA-256 pinned
  (`ModelConstants`), and unnecessary for every feature. The model is an
  anti-feature candidate, so it is stated plainly in the MR description; the
  reviewer may still raise it as a question rather than an antifeature.
- **Binary dependencies** — all from Maven (androidx, ONNX Runtime
  (MIT/Apache-2.0), OkHttp, Compose). No prebuilt blobs in the repo.
- **Licensing** — AGPL-3.0-or-later; `./tools/check_license.sh` guards the
  licence strings and third-party attributions; the in-app licence screen
  lists everything.

## 3. After the MR

- Once merged, the app appears on f-droid.org within a build cycle
  (usually days). Watch the build log from the MR; failures there are ours to
  fix (the buildserver environment occasionally differs from GitHub CI — the
  `prebuild: sdkmanager …` line in the metadata handles the SDK pins).
- Each release afterwards updates automatically: `AutoUpdateMode: Version`
  + `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` watch the `v*` tags.
- Add the F-Droid badge/link to the website's download section once the
  listing is live.

## Local pre-check (optional, on a machine with Python)

```bash
pip install fdroidserver
fdroid lint com.awbuilds.auraspend        # metadata sanity, run from a fdroiddata checkout
```
