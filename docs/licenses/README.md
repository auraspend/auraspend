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
| `OFL-Manrope.txt` | Manrope (bundled UI font, `app/src/main/res/font/`) | SIL Open Font License 1.1 |
| `OFL-Fraunces.txt` | Fraunces (bundled display font, `app/src/main/res/font/`) | SIL Open Font License 1.1 |
| `MIT-ONNX-Runtime.txt` | ONNX Runtime for Android (native inference, Maven AAR) | MIT |
| `SQLCipher-Community.txt` | SQLCipher Community Edition (encrypted database, Maven AAR) | BSD-3-Clause |
| `Apache-2.0.txt` | AndroidX (Room, Compose, Lifecycle, Work, Navigation, Paging, ProfileInstaller), Google API Client, Google Drive API, play-services-auth, OkHttp, Guava, google-http-client-gson | Apache-2.0 |

## Components linked inside SQLCipher

`libsqlcipher.so` statically links **SQLite** (public domain, per
[sqlite.org/copyright.html](https://sqlite.org/copyright.html)) into AuraSpend's
APK. Zetetic's licence page
([zetetic.net/sqlcipher/license](https://www.zetetic.net/sqlcipher/license/))
lists the FOSS components a SQLCipher build may additionally rely on — SQLite
Android Bindings, OpenSSL, libtomcrypt, incbin, the Unicode data files and
others, each with its own licence — and requires that a product include SQLCipher's
own BSD notice in a user-accessible location, which the in-app license screen
does.

Which of those are actually in the shipped binary was **measured, not assumed**:
`libsqlcipher.so` exports no OpenSSL/libressl symbols or strings and imports no
network symbols at all, and `tools/audit_native_runtime.sh` audits both ABIs the
APK ships on every PR (its one `dlopen` allowance — SQLite's loadable-extension
support — is pinned to these exact binaries' SHA-256 digests, so a version bump
blocks until the new build is reviewed).

## Adapted code

Parts of the SMS parsing engine are adapted from other open-source projects.
The adapted files keep their own licence; the AGPL covers AuraSpend's own
source.

| Component | Licence | Where |
|---|---|---|
| [PennyWise AI](https://github.com/sarim2000/pennywiseai-tracker) — SMS amount/limit patterns, merchant and UPI-handle intelligence, ingest guards, and the sender IDs behind the sender-routed per-bank parsers | AGPL-3.0 | `data/classification/BankMessageParser.kt`, `data/classification/SmsAutoClassifier.kt`, `data/classification/bank/` (adapted 2026-10-13) |

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
