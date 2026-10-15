#!/usr/bin/env bash
# Offline privacy audit of a prebuilt native inference runtime.
#
# The app's whole promise is that financial data stays on the device. Adding a
# third-party native library is the single highest-risk change for that promise,
# so this checks the artifact rather than trusting the vendor's documentation.
#
# Needle's README states telemetry is on by default and is disabled with
# NEEDLE_TELEMETRY=0 / DO_NOT_TRACK=1 — both environment variables, which Android
# does not reliably hand to a statically-linked library. Before wiring it in, the
# question "can this code phone home?" deserves a direct answer.
#
# The decisive check is the import scan: a network capability has to come from a
# symbol the artifact *imports*, so `-u` has to reach `nm`, and a scan that reads
# no imports at all is a failure rather than a clean result. An earlier version
# of this script got both of those wrong and reported a clean result for every
# artifact it was given.
#
# Two dlopen/dlsym uses are allowed, each by name and only on evidence:
# ONNX Runtime's execution-provider loader, which loads the
# libonnxruntime_providers_*.so plugins shipped beside it in the same APK (the
# plugin names must be embedded in the artifact), and — for the encrypted
# database's SQLCipher — a digest-pinned entry in APPROVED_LOADERS below. Any
# other loader symbol, or any network symbol, still fails.
#
# Usage:  tools/audit_native_runtime.sh <path-to-lib.a> [expected-symbol-regex]
# Re-run whenever the vendor publishes a new build; a changed digest means the
# answer below no longer applies and this must be repeated.
set -uo pipefail

LIB="${1:-}"
# Symbol that proves the scan is actually reading the artifact. Defaults to the
# Needle C API; override to audit a different library.
EXPECT="${2:-needle_(init|complete|load)}"
if [[ -z "$LIB" || ! -f "$LIB" ]]; then
  echo "usage: $0 <path to .a or .so> [expected-symbol-regex]" >&2
  exit 2
fi

echo "artifact : $LIB"
echo "size     : $(wc -c < "$LIB") bytes"
echo "sha256   : $(shasum -a 256 "$LIB" | awk '{print $1}')"
echo

fail=0

# Symbols come from three places, and which one works depends on how the vendor
# shipped the artifact. A release .so is usually stripped of its static symbol
# table, so `nm` alone returns nothing and the scan below would be reading an
# empty file — which is exactly the failure this validation exists to catch.
# Try each in turn and report which one answered.
#
# The optional `-u` (undefined symbols, i.e. the imports) has to reach `nm`
# itself. An earlier version of this script filtered the *defined* symbol list
# for "U" lines, which matches nothing in any artifact — so the import checks
# below reported a clean result for every library it was given, including one
# that links a network stack. The probes forward their arguments, and the filter
# follows the mode: in `-u` mode a weak undefined symbol (`w`, `v`) is an import
# too, and dropping it would hide a weakly-linked `socket`.
nm_filter() {
  if [[ "${1:-}" == "-u" ]]; then grep -E ' [Uwv] '; else grep -E ' [TtWw] '; fi
}
nm_defined()  { nm "$@" "$LIB" 2>/dev/null | nm_filter "$@"; }
nm_dynamic()  { nm -D "$@" "$LIB" 2>/dev/null | nm_filter "$@"; }
SYM_SRC=""
SYM_COUNT=0
for probe in nm_defined nm_dynamic; do
  n=$($probe | wc -l | tr -d ' ')
  if [[ "$n" -gt 0 ]]; then SYM_SRC="$probe"; SYM_COUNT="$n"; break; fi
done

if [[ "$SYM_COUNT" -eq 0 ]]; then
  echo "[FAIL] no symbol table found via nm or nm -D. The artifact may be fully"
  echo "       stripped, or unreadable. Either way the symbol checks below would"
  echo "       be scanning nothing, so this run proves nothing."
  echo "       Use a reader that understands the format (e.g. llvm-nm, radare2,"
  echo "       or objdump) and audit with EXPECT set to a symbol that library emits."
  exit 1
fi

echo "[ok]   symbol source: $SYM_SRC ($SYM_COUNT defined symbols)"
echo

# A scan that finds nothing is only meaningful if it can find something. Prove
# the method works before trusting a clean result.
#
# The probe consumes its input fully instead of `$SYM_SRC | grep -qE "$EXPECT"`:
# under `pipefail`, a `grep -q` that exits on the first match sends SIGPIPE to
# the pipeline feeding it and returns 141, so a *successful* scan was reported
# as "scan is broken — no symbols matched" on CI for a library whose 187
# symbols had just been printed above. The count-based scans (wc -l, grep -c)
# read to EOF and were never affected.
SYMBOLS_BUFFER="$($SYM_SRC)"
if grep -qE "$EXPECT" <<< "$SYMBOLS_BUFFER"; then
  echo "[ok]   scan validated: found expected API symbols ($EXPECT)"
else
  echo "[FAIL] scan is broken - no symbols matched ($EXPECT), so a clean result below"
  echo "       would be meaningless. Do not trust this run."
  exit 1
fi

# The import scan needs its own proof, for the same reason the defined-symbol
# scan does. Every real shared library imports libc, so zero imports means `-u`
# read nothing and every check below would be vacuous again.
IMPORTS=$($SYM_SRC -u 2>/dev/null | wc -l | tr -d ' ')
if [[ "$IMPORTS" -eq 0 ]]; then
  echo "[FAIL] the import scan (-u) found no undefined symbols. This artifact"
  echo "       imports nothing at all, which means the scan read nothing — the"
  echo "       network and loader checks below would prove nothing. Do not trust"
  echo "       this run."
  exit 1
fi
echo "[ok]   import scan: $IMPORTS undefined symbols (what the checks below read)"
echo

# The decisive check: a network capability has to come from an import, because
# the library cannot reach the network without linking something that does it.
# NOTE: do not use \b here. POSIX ERE has no word boundary, so grep -E '\b'
# matches a literal backspace and this check would silently match nothing —
# i.e. it would pass every binary, including ones that phone home.
NETSYMS="(socket|connect|getaddrinfo|gethostbyname|gethostbyaddr|SSL_|curl_|bind|listen|sendto|recvfrom|recvmsg|sendmsg)"
NET=$($SYM_SRC -u 2>/dev/null | grep -cE "$NETSYMS")
if [[ "$NET" -eq 0 ]]; then
  echo "[ok]   no network symbols imported (socket/connect/SSL/curl/getaddrinfo)"
else
  echo "[FAIL] $NET network symbol(s) imported - this library can reach the network:"
  $SYM_SRC -u 2>/dev/null | grep -E "$NETSYMS" | head
  fail=1
fi

# A self-contained HTTP stack would still leave hostnames and URLs behind.
#
# Filtered, because a large inference library embeds documentation references in
# its kernels: arXiv links in Eigen/MLIR, docs.scipy.org matmul, CUDA cuBLAS
# docs, a Wikipedia article on rounding. Those are attribution comments, not
# endpoints, and treating them as findings makes this check useless — it would
# block every real runtime, so people would stop running it.
#
# What is NOT filtered is a URL whose *host* looks like a service rather than
# documentation, which is the shape a real callback would take. Verified against
# onnxruntime-android 1.22.0: 0 service hosts and zero network symbol imports, so
# the check has evidence behind it rather than a preference. (The doc-reference
# count itself varies with the `strings` implementation — 77 here, 79 when the
# check was written — which is exactly why it is reported, not asserted.)
DOC_HOST='arxiv\.org|doi\.org|wikipedia\.org|docs\.scipy\.org|scipy\.org|numpy\.org|ieeexplore\.ieee\.org|github\.com|android\.googlesource\.com|docs\.nvidia\.com|devblogs\.nvidia\.com|opensource\.org|pytorch\.org|huggingface\.co/(blog|docs)|papers\.nips\.cc|tensorflow\.org|keras\.io|microsoft\.com/en-us/research|dl\.acm\.org|aclanthology\.org'
URLS=$(strings -a "$LIB" 2>/dev/null | grep -oE 'https?://[a-zA-Z0-9._/-]+' | grep -vE "$DOC_HOST" | sort -u)
if [[ -z "$URLS" ]]; then
  DOC_ONLY=$(strings -a "$LIB" 2>/dev/null | grep -cE 'https?://' || true)
  echo "[ok]   no service URLs embedded ($DOC_ONLY documentation reference(s), all filtered)"
else
  echo "[FAIL] non-documentation URL(s) embedded:"
  echo "$URLS" | head
  fail=1
fi

# Runtime code loading is how a static scan gets side-stepped, so these symbols
# fail by default — `dlopen` included, unless the artifact is doing the one
# thing named below.
#
# The single allowed loader is ONNX Runtime's execution-provider loader. The
# shipped libonnxruntime.so imports dlopen/dlsym, and the names embedded in the
# binary are the provider plugins that ship beside it in the same APK
# (libonnxruntime_providers_*.so). That is static plugin loading: no URL, no
# download, and the network scan above is never relaxed for it. The exception is
# granted per artifact and on evidence — if those plugin names are absent, the
# artifact is loading something else, and this fails.
#
# Symbol naming has to be normalised and matched exactly: bionic imports
# `dlopen@LIBC` with no leading underscore, a Mach-O artifact would import
# `_dlopen`, and the earlier `U _(dlopen|...)$` pattern matched neither on
# Android. Matching a *substring* is not good enough either — `__register_atfork`
# ends in "fork" and `__system_property_get` starts with "system", and neither is
# runtime code loading. The symbol name is the last field of an nm line.
ALLOWED_DYN='dlopen|dlsym'
DENIED_DYN='dlopen|dlsym|system|popen|fork|execve'

# A runtime code loader is the one capability a static scan cannot rule out, so
# an allowance is granted per *artifact*, never per project or per family name:
# the entry names the library, pins the sha256 of the exact binary that was
# reviewed, and carries an evidence pattern that must also be present in that
# binary (plus the reason it is safe). A version bump changes the digest, the
# entry stops matching, and the audit blocks until the new binary is reviewed —
# which is the whole reason the digest is part of the key.
#
# Format: <library-basename>|<sha256>|<evidence-regex>|<reason>
APPROVED_LOADERS=(
  "libsqlcipher.so|32d4c1bb89a90b59a3d6be950bbc36c133f8762f627b333a3512205c8037a48b|sqlite3_load_extension|SQLCipher Community Edition (sqlcipher-android 4.19.1, arm64): the loader belongs to SQLite's loadable-extension support, reachable only through sqlite3_load_extension, which sqlcipher-android's Java layer never declares or calls (verified: no reference to it anywhere in classes.jar), Room never calls, and Android leaves extension loading disabled by default. No network symbols, no service URLs, no telemetry strings."
  "libsqlcipher.so|c2084efe2469e69ffafa09425800de4bf62fb34cb246b645ac7951294f3e28ba|sqlite3_load_extension|SQLCipher Community Edition (sqlcipher-android 4.19.1, armeabi-v7a): same evidence as the arm64 entry above, re-reviewed for this ABI."
)
DYN_LIST=$($SYM_SRC -u 2>/dev/null \
  | awk '{print $NF}' \
  | sed 's/^_//; s/@.*$//' \
  | grep -xE "$DENIED_DYN" | sort -u || true)
if [[ -z "$DYN_LIST" ]]; then
  echo "[ok]   no dlopen/dlsym/system/exec - no runtime code loading"
else
  DYN_OTHER="$(printf '%s\n' "$DYN_LIST" | grep -vxE "$ALLOWED_DYN" || true)"
  DYN_PLUGINS="$(strings -a "$LIB" 2>/dev/null | grep -cE 'libonnxruntime_providers_[a-z0-9_]+\.so' || true)"
  # Digest-pinned allowance: the reviewed binary, with its evidence present.
  LIB_SHA="$(shasum -a 256 "$LIB" | awk '{print $1}')"
  LIB_BASE="$(basename "$LIB")"
  APPROVED_REASON=""
  # `strings | grep -q` under pipefail is the SIGPIPE trap described above: grep
  # exits on the first match, strings dies with 141, and a *found* pattern reads
  # as absent. Buffer the strings and grep the buffer instead.
  STRINGS_BUFFER="$(strings -a "$LIB" 2>/dev/null)"
  for entry in "${APPROVED_LOADERS[@]}"; do
    IFS='|' read -r approved_name approved_sha approved_evidence approved_reason <<< "$entry"
    [[ "$approved_name" == "$LIB_BASE" && "$approved_sha" == "$LIB_SHA" ]] || continue
    if grep -qE "$approved_evidence" <<< "$STRINGS_BUFFER"; then
      APPROVED_REASON="$approved_reason"
      break
    fi
  done
  if [[ -n "$DYN_OTHER" ]]; then
    echo "[FAIL] runtime code loading symbols present:"
    printf '%s\n' "$DYN_OTHER" | sed 's/^/         /'
    fail=1
  elif [[ "$DYN_PLUGINS" -gt 0 ]]; then
    echo "[ok]   dlopen/dlsym allowed by name: ONNX Runtime execution-provider loader"
    echo "       ($(printf '%s ' $DYN_LIST)against $DYN_PLUGINS embedded libonnxruntime_providers_*.so"
    echo "        name(s) — static plugin loading, no URL, no download. Any other"
    echo "        loader symbol, or a dlopen without those names, still fails.)"
  elif [[ -n "$APPROVED_REASON" ]]; then
    echo "[ok]   dlopen/dlsym allowed by digest-pinned entry ($(printf '%s ' $DYN_LIST)):"
    echo "       $APPROVED_REASON"
    echo "       Pinned to sha256 $LIB_SHA. A new build changes the digest and this"
    echo "        entry stops applying — re-review it, then add the new binary."
  else
    echo "[FAIL] dlopen/dlsym present but no execution-provider plugin name is embedded"
    echo "       and no APPROVED_LOADERS entry matches this binary:"
    echo "       $(basename "$LIB") sha256 $LIB_SHA. This artifact loads something else"
    echo "       at runtime, which is the case this check exists to catch. Do not ship it"
    echo "       until that is understood."
    fail=1
  fi
fi

# Explicit telemetry surface, independent of any capability to use it.
#
# The vendor-name list is what matters: posthog, sentry, amplitude, mixpanel,
# segment, google-analytics. A bare "telemetry" is NOT treated as a finding,
# because it also matches C++ mangled type names — onnxruntime 1.22.0 contains
# `N11onnxruntime9TelemetryE`, which is `onnxruntime::Telemetry`, a class. A check
# that flags every class named Telemetry would flag most inference runtimes and
# train people to ignore it.
TEL=$(strings -a "$LIB" 2>/dev/null | grep -icE 'posthog|sentry\.io|amplitude|mixpanel|segment\.io|analytics_key|google-analytics|firebase|crashlytics|appcenter' || true)
if [[ "$TEL" -eq 0 ]]; then
  echo "[ok]   no telemetry/analytics vendor strings in the binary"
else
  echo "[FAIL] $TEL telemetry vendor string(s) present:"
  strings -a "$LIB" 2>/dev/null | grep -iE 'posthog|sentry|amplitude|mixpanel|segment\.io|analytics_key' | sort -u | head
  fail=1
fi

echo
if [[ "$fail" -eq 0 ]]; then
  echo "RESULT: no reachable network or telemetry surface in this artifact."
  echo "        Still re-run on every version bump, and confirm on-device."
else
  echo "RESULT: blocked. Do not ship this artifact until understood."
fi
exit "$fail"
