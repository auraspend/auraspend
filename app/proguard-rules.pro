# AuraSpend R8 rules (full mode is the AGP 9 default)

# ─── Native (JNI) runtime ────────────────────────────────────────────────────
# ONNX Runtime's AAR ships no consumer rules of its own, and the library's JNI
# reaches back into Java in two different ways:
#
#   1. JNI_OnLoad → RegisterNatives finds the class that declares the native
#      methods (OrtSession). AGP's default
#      `-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }`
#      covers this, which is why FindClass() at load time resolves.
#
#   2. **At inference time the C++ constructs Java objects by literal name:**
#      FindClass("ai/onnxruntime/TensorInfo"), then a GetMethodID whose signature
#      is `(Lai/onnxruntime/OnnxJavaType;[J)V`. Rule 1 does not cover these — they
#      declare no native methods — so R8 shrank them away.
#
# That gap shipped in 0.1.8 and crashed the production build the moment a message
# was categorised:
#
#   Abort message: 'JNI DETECTED ERROR IN APPLICATION: java_class == null
#       in call to GetMethodID
#       from boolean[] ai.onnxruntime.OrtSession.run(...)'
#     #06 convertToTensorInfo+628
#
# The debug flavour never showed it (debug builds are not minified), and the R8
# gate checked only the three classes JNI_OnLoad touches (handoff 0064).
#
# So this rule is load-bearing: it keeps the package's names *and* its members,
# including the synthesized constructor `TensorInfo(OnnxJavaType, long[])` the
# JNI calls. It is deliberately the whole package rather than a list — the list
# is whatever classes the shipped .so asks for, and that changes with the next
# ORT bump. `tools/verify_r8_release.sh` now derives the expected set from the
# native library itself, so a bump that adds a class fails the gate instead of
# shipping.
-keep class ai.onnxruntime.** { *; }

# This block previously held `-keep class com.arm.aichat.** { *; }` for the
# vendored llama.cpp library, deleted in handoff 0013. It matched nothing and
# was the only native-runtime rule in the file, which made the JNI story look
# handled locally when it was handled by AGP.

# ─── Enums persisted as strings ──────────────────────────────────────────────
# TransactionType / SmsMessageStatus / BudgetPeriod values round-trip through Room as names,
# and are restored with Enum.valueOf().
-keepclassmembers enum com.awbuilds.auraspend.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Crash diagnostics ───────────────────────────────────────────────────────
# Keep source file and line numbers so stack traces from release builds remain actionable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Release noise reduction ─────────────────────────────────────────────────
# Verbose/debug logging is dead weight in release builds; R8 removes the calls entirely.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
