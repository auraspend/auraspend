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

## Page-transition lag — measured, and the measurement says the UI thread is not the problem

Measured with `dumpsys gfxinfo <pkg> framestats` on the **minified prodRelease**
build with real data, splitting the UI-thread portion (`DrawStart -
HandleInputStart`, which is Compose + layout + recording) from the total, because
the emulator renders in software and its totals are GPU-bound:

| Sequence | frames | UI p50 | UI p95 | UI max |
|---|---|---|---|---|
| 8 tab switches, crossfade | 41–43 | 1.9–2.3 ms | 9.7–10.5 ms | **16.1–17.4 ms** |
| 8 tab switches, instant | 41 | 1.5–1.6 ms | 5.3–8.9 ms | **12.3–14.3 ms** |
| detail push/pop ×3 | 61 | 2.3 ms | 12.8 ms | 13.9 ms |

So the app's composition work is **cheap** — p50 ~2 ms, and the worst tab frame
with the crossfade is 17.4 ms, i.e. just *over* the 16.7 ms budget. That is the
only over-budget frame found, and it is the crossfade: two full screens each need
their own alpha layer for the animation.

Change: **tab switches are now instant** (Material's own bottom-navigation
convention — `enterTransition`/`exitTransition` = `None` when both ends are in
`Routes.topLevel`), and the push/pop animations lost their alpha fades so they
translate one opaque surface instead of compositing two. Worst tab frame 17.4 →
14.3 ms, back inside the budget. Pushed screens keep a 200 ms slide.

**What this does not prove.** The emulator cannot stand in for the device: it is
fill-rate bound (GPU p50 14.1 ms against a 16.7 ms budget), so its *total* frame
times are meaningless, and the 61 ms outlier in the first mixed run did not
reproduce in either path afterwards (a cold-start artefact). The vivo — the
device the owner reported it on — **crashes within seconds of launch on 0.1.8**,
which is exactly why nothing could be measured there:

```
10-18 20:00:24  F DEBUG : Abort message: 'JNI DETECTED ERROR … GetMethodID
                             from boolean[] ai.onnxruntime.OrtSession.run(...)'
```

So the remaining question — whether the vivo's lag is this transition or
something draw-bound that only that device shows — needs 0.1.9 installed there and
the same measurement taken with `SurfaceFlinger --latency` (gfxinfo reports no
frames on that vivo, handoff 0038).

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
