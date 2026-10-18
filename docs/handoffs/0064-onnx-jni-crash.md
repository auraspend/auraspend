# 0064 — the crash 0.1.8 shipped: R8 stripped the ONNX classes

**Date:** 2026-10-18 · **Branch:** `main` (released as 0.1.9) · **Status:
committed and pushed; tag release needs the `release` approval.**

## What happened

0.1.8 was released (Play alpha + GitHub) and the owner installed it from Play on
their vivo. It **aborted the moment a bank message was categorised** — i.e. as
soon as the 22 MB on-device model ran. The tombstone:

```
Abort message: 'JNI DETECTED ERROR IN APPLICATION: java_class == null
    in call to GetMethodID
    from boolean[] ai.onnxruntime.OrtSession.run(...)'
  #06 convertToTensorInfo+628
  #08 Java_ai_onnxruntime_OrtSession_run+836
```

## Root cause

ONNX Runtime reaches back into Java in **two** ways, and only one was protected:

1. `JNI_OnLoad` → `RegisterNatives` resolves the class that declares the native
   methods (`OrtSession`). AGP's default
   `-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }`
   covers this — and `proguard-rules.pro` had a long comment explaining,
   correctly, that no local rule was needed *for that*.
2. **At inference time the C++ constructs Java objects by literal name** —
   `FindClass("ai/onnxruntime/TensorInfo")`, then a `GetMethodID` whose signature
   is `(Lai/onnxruntime/OnnxJavaType;[J)V`. Those classes declare no native
   methods, so rule 1 does not cover them, and R8 shrank them away.

Verified in the mapping from the shipped build: `ai.onnxruntime.OnnxJavaType ->
h32`, and both `TensorInfo` and `OnnxJavaType` **absent from the APK's DEX**. The
lookup then returns null, `GetMethodID(null, …)` aborts the VM, and a
`SIGABRT` cannot be caught by the `try/catch` around the classifier — which is why
`Log.w("On-device categorisation crashed…")` never printed and the app just
vanished.

**Why every earlier check passed:** debug builds are not minified
(`devDebug` is what was on the phones), so the encoder had never actually run in
a minified build. Handoff 0046 even recorded "unverified: an R8-minified run".
The R8 gate existed but checked the wrong three classes
(`OrtEnvironment`, `OrtSession`, `OnnxTensor`).

## The fix

- `app/proguard-rules.pro`: `-keep class ai.onnxruntime.** { *; }`, with the
  reasoning (and the falsified assumption that it was unnecessary) written down.
  R8 scores are unchanged in practice: obfuscation 96.9 %, shrinking 96.9 %,
  DEX 4.40 MB, so Play's 25 % floor does not come close.
- `tools/verify_r8_release.sh` now **derives the expected class set from the
  shipped `libonnxruntime4j_jni.so`** — every `ai/onnxruntime/X` string the
  native library asks for must exist in the mapping with its name intact. An ORT
  bump that introduces a class the keep rules miss fails the gate instead of
  shipping. Also ported off `mapfile` (bash 4+) because macOS ships bash 3.2.

## Verification

Static:
- `tools/verify_r8_release.sh` → "all 11 classes the JNI resolves by name
  survived un-renamed", plus the SQLCipher checks and the alignment/score checks.
- The release APK's DEX now contains `Lai/onnxruntime/TensorInfo;`,
  `OnnxJavaType`, `SequenceInfo`, `MapInfo`, `OrtException`.

Dynamic, on the **minified** `prodRelease` APK (emulator — both phones carry
Play-signed builds, so a sideload cannot go over them):

1. Installed the release APK, downloaded the 22 MB model through the app,
   granted SMS access, enabled auto-categorise.
2. `adb emu sms send` a bank message.
3. `LocalLlmProvider: on-device classifier ready: minilm-centroid` — the session
   loaded.
4. `SmsAiEnricher` calls `runtime.extract()` for **every** message with no
   memory hit (a fresh install has none), so that message went through
   `OrtSession.run` → output `TensorInfo` conversion, exactly the failing path.
5. The transaction landed in Activity (`SWIGGY · Food & Dining`), the crash
   buffer is empty, and no `JNI DETECTED ERROR` was logged.

Verdict: the crash path is fixed on a minified build, proven by the path that
killed 0.1.8.

## Page-transition lag — not fixed, not yet measured

0.1.8 **does** contain the 0061 composition fix, and the vivo's install is
`[status=speed-profile]` (the baseline profile is applied — so the "profile never
compiles on install" trap is not the explanation this time).

The device dropped off wireless debugging (`Host is down`) before it could be
measured, so this is **open**. The plan when it is back: `dumpsys SurfaceFlinger
--latency <layer>` sampling (gfxinfo reports 0 frames on this vivo, handoff
0038), over the transitions the owner actually uses, comparing 0.1.9 with 0.1.8.
Nothing has been changed speculatively.

## Release

`0.1.9` / versionCode 12. Play notes EN 223 / HI 199 chars. 0.1.8 remains on
alpha and is broken — 0.1.9 supersedes it.
