# Third-party licenses

AuraSpend itself is licensed under the **GNU Affero General Public License v3.0
or later** (`AGPL-3.0-or-later`) — see [`../LICENSE`](../LICENSE), and
[ADR 0009](../adr/0009-agpl-3.0-relicensing.md) for why.

The AGPL applies to AuraSpend's own source only. Everything AuraSpend bundles or
downloads keeps its own licence, reproduced in full below. The same list is
shown in the app under **Settings → About → Open-source licenses**, and mirrored
as a table in the [`README`](../README.md).

| File | Component | Licence |
|---|---|---|
| `OFL-PlusJakartaSans.txt` | Plus Jakarta Sans (bundled font, `app/src/main/res/font/`) | SIL Open Font License 1.1 |
| `MIT-ONNX-Runtime.txt` | ONNX Runtime for Android (native inference, Maven AAR) | MIT |
| `Apache-2.0.txt` | AndroidX (Room, Compose, Lifecycle, Work, Navigation, Paging, ProfileInstaller), Google API Client, Google Drive API, play-services-auth, OkHttp, Guava, google-http-client-gson | Apache-2.0 |

## Downloaded at runtime, not redistributed

| Component | Licence | Source |
|---|---|---|
| `all-MiniLM-L6-v2` (int8 ONNX encoder + tokenizer) | Apache-2.0 | [HuggingFace](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) |

The categorisation model is fetched by the app after the user opts in, and its
SHA-256 is verified against a pinned digest before use. Because it is never
redistributed inside the APK, it is listed here for transparency rather than as a
bundled dependency. Its Apache-2.0 licence is reproduced in
[`../LICENSE`](../LICENSE)'s sibling file `Apache-2.0.txt`.

## Keeping this list honest

`tools/check_license.sh` runs in CI and fails if a dependency is added to
`app/build.gradle.kts` without a corresponding row in:

- the table above,
- the `THIRD_PARTY` list in `app/src/main/java/com/awbuilds/auraspend/ui/settings/LicenseScreen.kt`,
- the table in `README.md`.

An out-of-date attribution is a licence-compliance bug, not a cosmetic one, and
nothing else in the build would notice it.
