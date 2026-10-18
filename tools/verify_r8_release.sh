#!/usr/bin/env bash
# Verify the release build against the Google Play technical requirements.
#
# Run this after touching dependencies, keep rules, the native runtime, or
# anything that changes what R8 can see. It answers four questions that are
# otherwise only answerable by rendering an HTML report in a browser and
# squinting at it:
#
#   1. Does Play's 25% optimization/obfuscation/shrinking floor apply, and if
#      so is it met?   (enforced from February 2027, for bundles > 10 MB DEX)
#   2. Is the packaged APK 16 KB page-size aligned?   (required for API 35+)
#   3. Did R8 preserve the classes the native runtime resolves by name at
#      JNI_OnLoad? A rename here is a release-only UnsatisfiedLinkError.
#   4. Is anything unsigned or missing from the bundle?
#
# Usage: tools/verify_r8_release.sh [variant]   (default: prod; dev also works)
set -uo pipefail

VARIANT="${1:-prod}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

AAR_OUT="app/build/outputs/apk/${VARIANT}/release"
AAB_OUT="app/build/outputs/bundle/${VARIANT}Release"
MAPPING="app/build/outputs/mapping/${VARIANT}Release/mapping.txt"
ANALYZER="app/build/outputs/mapping/${VARIANT}Release/configanalyzer.pb"

fail=0
note() { printf '\n== %s\n' "$1"; }
bad()  { printf '   FAIL  %s\n' "$1"; fail=1; }
good() { printf '   ok    %s\n' "$1"; }

# The AAB is a third input this script needs but does not produce elsewhere:
# on CI, `assembleProdRelease` yields mapping.txt and the config analyzer, so
# checking only those two skipped the build and then failed on a bundle that
# was never made. Anything the checks below read must trigger the build.
# `${VARIANT^}` is bash 4+; macOS ships bash 3.2, so capitalise portably.
VARIANT_CAP="$(printf '%s' "${VARIANT:0:1}" | tr '[:lower:]' '[:upper:]')${VARIANT:1}"
if [[ ! -f "$MAPPING" || ! -f "$ANALYZER" || -z "$(ls "${AAB_OUT}"/*.aab 2>/dev/null)" ]]; then
  note "Building ${VARIANT}Release (bundle + APK)"
  ./gradlew "bundle${VARIANT_CAP}Release" "assemble${VARIANT_CAP}Release" --console=plain -q \
    || { echo "build failed"; exit 1; }
  ./gradlew ":app:analyze${VARIANT_CAP}ReleaseR8Config" --console=plain -q || true
fi

note "1. R8 scores vs Play's 25% floor (enforced February 2027)"
if [[ -f "$ANALYZER" ]]; then
  python3 tools/r8_scores.py "$ANALYZER" || bad "one or more R8 scores below threshold"
else
  bad "no analyzer report at $ANALYZER"
fi

note "2. Release DEX size vs the 10 MB floor"
AAB="$(ls "${AAB_OUT}"/*.aab 2>/dev/null | head -1)"
if [[ -n "$AAB" ]]; then
  dex_bytes=$(unzip -l "$AAB" 2>/dev/null | awk '/\.dex$/ {s+=$1} END {print s+0}')
  dex_mb=$(awk -v b="$dex_bytes" 'BEGIN {printf "%.2f", b/1048576}')
  printf '   %s MB uncompressed DEX in the bundle\n' "$dex_mb"
  if awk -v b="$dex_bytes" 'BEGIN {exit !(b > 10*1048576)}'; then
    printf '   the 25%% floor APPLIES to this bundle — the scores above are binding\n'
  else
    printf '   under the 10 MB floor, so the 25%% requirement does not bind\n'
    printf '   (it is reported above anyway; the app clears it comfortably)\n'
  fi
else
  bad "no bundle at $AAB_OUT"
fi

note "3. 16 KB page-size alignment (required for API 35+ targets)"
ZIPALIGN="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/zipalign 2>/dev/null | sort -V | tail -1)"
APK="$(ls "${AAR_OUT}"/*.apk 2>/dev/null | head -1)"
if [[ -x "$ZIPALIGN" && -n "$APK" ]]; then
  if "$ZIPALIGN" -c -P 16 -v 4 "$APK" >/dev/null 2>&1; then
    good "$(basename "$APK") is 16 KB aligned"
  else
    bad "$(basename "$APK") is NOT 16 KB aligned — Play will reject it"
  fi
else
  bad "zipalign or release APK not found (this APK is unsigned until secrets.properties exists)"
fi

note "4. JNI classes R8 must not rename"
# ONNX Runtime reaches back into Java two ways, and only the first is covered by
# AGP's default `-keepclasseswithmembernames,includedescriptorclasses`:
#
#   - JNI_OnLoad → RegisterNatives needs the class declaring the native methods.
#   - **At inference time the C++ constructs Java objects by literal name** —
#     FindClass("ai/onnxruntime/TensorInfo") and a GetMethodID whose signature
#     names OnnxJavaType. Those classes declare no native methods, so R8 shrank
#     them, and 0.1.8 aborted with `JNI DETECTED ERROR: java_class == null` the
#     moment a message was categorised (handoff 0064).
#
# So the expected set is *derived from the shipped .so* rather than maintained by
# hand: every `ai/onnxruntime/X` string the native library asks for must exist in
# the mapping with its name intact. An ORT bump that introduces a class the keep
# rules miss then fails here instead of on a user's phone.
# No `mapfile` here: macOS ships bash 3.2 and this script runs there too.
SO_NAMES="$(
  find app/build/intermediates/merged_native_libs -name 'libonnxruntime4j_jni.so' 2>/dev/null |
    head -1 |
    xargs -I{} strings {} 2>/dev/null |
    grep -oE '^ai/onnxruntime/[A-Za-z]+$' |
    sort -u
)"
if [[ -z "$SO_NAMES" ]]; then
  bad "could not read class names from libonnxruntime4j_jni.so"
elif [[ ! -f "$MAPPING" ]]; then
  bad "no mapping file at $MAPPING"
else
  missing=0
  count=0
  while IFS= read -r path; do
    [[ -z "$path" ]] && continue
    count=$((count + 1))
    cls="${path//\//.}"
    line="$(grep -m1 -E "^${cls} ->" "$MAPPING")"
    if [[ -z "$line" ]]; then
      bad "$cls is missing from the release build — the JNI will FindClass() null"
      missing=1
    elif [[ "${line%% -> *}" != "$(sed 's/.* -> //; s/:$//' <<<"$line")" ]]; then
      bad "$cls was RENAMED — inference will abort: $line"
      missing=1
    fi
  done <<< "$SO_NAMES"
  [[ $missing -eq 0 ]] && good "all $count classes the JNI resolves by name survived un-renamed"
fi

# SQLCipher is the same shape — JNI_OnLoad + RegisterNatives — but unlike ONNX it
# ships consumer rules, and a renamed SQLiteConnection would make the encrypted
# database impossible to open in a release build, so it is checked rather than
# trusted.
if [[ -f "$MAPPING" ]]; then
  for cls in net.zetetic.database.sqlcipher.SQLiteConnection net.zetetic.database.sqlcipher.SupportOpenHelperFactory; do
    line="$(grep -m1 -E "^${cls} ->" "$MAPPING")"
    if [[ -z "$line" ]]; then
      bad "$cls is missing from the release build entirely"
    elif [[ "${line%% -> *}" == "$(sed 's/.* -> //; s/:$//' <<<"$line")" ]]; then
      good "$cls kept un-renamed"
    else
      bad "$cls was RENAMED — the database cannot open: $line"
    fi
  done
  if grep -q "com.arm.aichat" "$MAPPING"; then
    bad "llama.cpp classes are still in the release build; handoff 0013 removed them"
  fi
fi

note "Result"
if [[ $fail -eq 0 ]]; then
  echo "   all checks passed"
else
  echo "   one or more checks FAILED"
fi
exit $fail
