# Tech Stack & Environment

## Build
| Item | Version |
|------|---------|
| AGP | 9.3.1 (built-in Kotlin 2.2.10) |
| Gradle | 9.5.0 (config cache + parallel + caching on) |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2026.09.00 → Compose UI 1.12.1, Material 3 1.4.0 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 30 |
| Room | 2.8.5 |
| WorkManager | 2.10.0 |
| Navigation Compose | 2.10.1 |
| On-device AI | ONNX Runtime (Maven AAR) + int8 `all-MiniLM-L6-v2` encoder (22 MB, downloaded at runtime, SHA-256 verified) |

## Commands
```bash
./gradlew :app:compileProdDebugKotlin  # fast compile check (use this constantly)
./gradlew testProdDebugUnitTest        # unit tests (required before handoff)
./gradlew assembleDevDebug              # dev APK (fast inner loop; no native toolchain)
```

## Dependency management
`gradle/libs.versions.toml` is the single source of truth. Do not add versions
inline in build files. Every catalog entry is referenced by a build file — the
"staged for the phase that needs them" entries are gone (paging was already
wired; lottie was never used and was removed).

## Key source locations
| Path | Contents |
|------|---------|
| `app/src/main/java/com/awbuilds/auraspend/ui/designsystem/` | Aurora components + tokens |
| `app/src/main/java/com/awbuilds/auraspend/ui/theme/` | Palette, typography, shapes, theme |
| `app/src/main/java/com/awbuilds/auraspend/ui/navigation/NavGraph.kt` | Current navigation (rebuilt in P1) |
| `app/src/main/java/com/awbuilds/auraspend/data/classification/` | SMS parsing + classification |
| `app/src/main/java/com/awbuilds/auraspend/core/AuraLog.kt` | Logging + error boundaries |
| `docs/design/aurora.md` | Design language spec |
| `docs/handoffs/` | Session handoffs |

## Workflows
| Task | Command / location |
|------|--------------------|
| Cold-start benchmark | `./gradlew :benchmark:connectedDevBenchmarkAndroidTest` (dev flavor keeps `profileable`; same code and R8 output as prod) |
| Baseline profile | `:benchmark:connectedDevBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=…BaselineProfileGenerator` → copy to `app/src/main/baseline-prof.txt` |
| Localization | `values/strings.xml` + `values-hi/strings.xml`; no literals in screens |
| Decisions | `docs/adr/` (0001–0011) |
