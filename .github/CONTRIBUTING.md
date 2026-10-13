# Contributing to AuraSpend

Thanks for your interest! Here's how to get started.

## Build Flavors

AuraSpend uses two flavors, and they are **identical in features**:

| Flavor | Command | Use Case |
|--------|---------|----------|
| `dev` | `./gradlew assembleDevDebug` | Development / QA — `.dev` id, "AuraSpend Dev" label |
| `prod` | `./gradlew assembleProdDebug` | The build that ships (`com.awbuilds.auraspend`) |

**Every feature ships in both flavors, and neither build carries analytics or
ads.** The flavors exist for configuration only — application-id suffix, label,
signing. See [ADR 0007](../docs/adr/0007-no-paywall.md) and
[ADR 0010](../docs/adr/0010-dev-prod-flavors.md).

### The invariant to preserve

**No feature may live in a flavor source set that is absent from
`app/src/main/`.** `app/src/dev/` and `app/src/prod/` hold configuration only —
application-id suffix, label, signing (see
[ADR 0010](../docs/adr/0010-dev-prod-flavors.md)). Both builds are
feature-identical and neither carries analytics, ads or telemetry: the promise
that your bank SMS never leaves the phone has to be true of every build.

If you are tempted to add a Play-only capability, it almost certainly belongs
in `main` behind a runtime check instead.

## Development Setup

1. Clone the repo
2. Open in Android Studio
3. Create `secrets.properties` in the project root (see `secrets.properties.example`)
4. Sync Gradle and run

## Code Style

- Follow Material 3 / Jetpack Compose conventions
- Use MVI pattern for screens (ViewModel + StateFlow + sealed Intent)
- Name composable files with uppercase first letter, matching the composable function
- Resource strings go in `strings.xml`, not hardcoded
- Keep `.kt` files under 400 lines — split into smaller composables or files if needed

## Pull Request Process

1. Fork the repo and branch from `dev` — short-lived, named `phase-N-name` or
   `<type>/<topic>` with `feat|fix|chore|docs|perf|refactor`. `main` is
   release-ready and protected, and receives `dev` at release time, not feature
   work ([AGENTS.md](../AGENTS.md) rule 7)
2. Run `./gradlew assembleProdDebug` — it must build clean
3. Update docs if needed
4. Open a PR against `dev` with a clear title and description
5. A maintainer will review

## Reporting Issues

- Use the bug report or feature request templates
- Include device model, OS version, and steps to reproduce
- For crashes, include the full stack trace or logcat output

## License

AuraSpend is licensed under the **GNU Affero General Public License v3.0 or
later** (AGPL-3.0-or-later). See [`LICENSE`](../LICENSE).

By contributing you agree that your contribution is licensed under AGPL-3.0-or-later.

### Copyright assignment — please read

AuraSpend relicensed from Apache-2.0 to AGPL-3.0 in 0.1.1. The relicensing was
possible because the project had a single copyright holder. **That is no longer
true the moment you contribute**, and the difference matters:

- If you retain copyright and merely license your contribution under AGPL-3.0,
  you can no longer relicense the project later — a future relicensing, dual
  licensing, or commercial exception would need your consent, forever.
- To keep the project manageable, contributions must be made **under a copyright
  assignment to AW Builds**, so that the project can be relicensed as a whole if
  that ever becomes the right call.

By opening a pull request you confirm:

1. You are the original author of the contribution, or have the right to submit
   it under these terms.
2. You assign copyright in the contribution to AW Builds, and
3. You accept that the contribution is distributed under AGPL-3.0-or-later.

This is a plain-language summary. The authoritative text is the
[GNU AGPL-3.0 §5(c)](https://www.gnu.org/licenses/agpl-3.0.html) plus the
assignment you confirm in the pull request. If you would rather not assign
copyright, you are very welcome to open an issue describing your idea instead —
that costs you nothing and I will implement it.
