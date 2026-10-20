#!/usr/bin/env bash
# Run the Maestro suite against a device.
#
#   tools/run_maestro.sh                 # the whole suite
#   tools/run_maestro.sh 01 02           # only the flows whose names start 01, 02
#   tools/run_maestro.sh --tag smoke     # only the flows tagged smoke
#   tools/run_maestro.sh --no-seed       # skip the demo data (the aggregate
#                                       #   screens will have nothing to show)
#
# The suite is plain Maestro YAML in .maestro/ — `maestro test .maestro` runs
# it directly. This script exists because the suite needs *state* that Maestro
# cannot create for itself:
#
#   1. a clean install, so the onboarding flow starts from nothing;
#   2. the app's locale set to en-IN, because the rupee and the lakh-crore
#      grouping are the app's home territory and the assertions are written in
#      them (the emulator's own locale is usually en-US, which would give
#      dollars and Western grouping). The locale is set **after** the
#      onboarding flow, because that flow clears app state — and clearing app
#      state clears the per-app locale with it;
#   3. the demo data seeded, because Home, Plan and Insights are aggregate
#      screens and there is nothing to assert on an empty ledger;
#   4. the demo data seeded *again* at the end, because the delete-all-data
#      flow is destructive and the suite should be re-runnable.
#
# It never touches com.awbuilds.auraspend (the prod package), which is the
# one with real data on it.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PKG=com.awbuilds.auraspend.dev
TEST_PKG=com.awbuilds.auraspend.dev.test
SEEDER_CLASS=com.awbuilds.auraspend.DemoDataSeeder
LOCALE=en-IN

DEVICE="${MAESTRO_DEVICE:-}"
SELECT=()
TAGS=()
DO_SEED=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag) TAGS+=("$2"); shift 2 ;;
    --device) DEVICE="$2"; shift 2 ;;
    --no-seed) DO_SEED=0; shift ;;
    *) SELECT+=("$1"); shift ;;
  esac
done

# ── Preflight ──────────────────────────────────────────────────────────────
command -v adb >/dev/null || { echo "adb not on PATH"; exit 1; }
command -v maestro >/dev/null || {
  echo "maestro not on PATH — it installs to ~/.maestro/bin, so:"
  echo "  export PATH=\"\$HOME/.maestro/bin:\$PATH\""
  exit 1
}

if [[ -z "$DEVICE" ]]; then
  # Prefer an emulator, and never pick a physical device by accident: the suite
  # clears app data, and the owner's phone is attached to this machine with
  # their real ledger on it. A physical device has to be named explicitly.
  DEVICE="$(adb devices | awk 'NR>1 && $2=="device" && $3 ~ /emulator-/ {print $1}' | head -1)"
  if [[ -z "$DEVICE" ]]; then
    DEVICE="$(adb devices | awk 'NR>1 && $2=="device" && $3 ~ /emulator-/ {print $1}' | head -1)"
  fi
  if [[ -z "$DEVICE" ]]; then
    echo "no emulator attached."
    echo "The suite clears app data, so it only runs on an emulator unless you"
    echo "name a device explicitly:  tools/run_maestro.sh --device <serial>"
    exit 1
  fi
fi
export ANDROID_SERIAL="$DEVICE"

echo "==> device: $DEVICE"
adb -s "$DEVICE" get-state >/dev/null || { echo "device $DEVICE is not ready"; exit 1; }

# A physical device is allowed, but only when it was named — and only the dev
# package is ever touched, never com.awbuilds.auraspend.
if [[ "$DEVICE" != emulator-* && "$DEVICE" != *emulator* ]]; then
  echo "    (physical device — only the $PKG package is touched)"
fi

# ── Recovery: Maestro's own driver and session ──────────────────────────────
# Two failure modes cost a lot of time here, and both look like "the app is
# broken" when they are not:
#
#   * Maestro installs its own driver and server apps on the device. If they
#     are missing, every run fails with "Device <id> was requested, but it is
#     not connected" — which reads like the device is gone. They ship inside
#     maestro-client.jar, so they are reinstalled from there.
#   * A stale session in ~/.maestro/sessions keeps a dead device server, and
#     every later run fails with "Device server died" while reporting
#     nothing. Dropping the session makes Maestro start a fresh one.
if ! adb -s "$DEVICE" shell pm list packages 2>/dev/null | grep -q "^package:dev.mobile.maestro$"; then
  echo "==> reinstalling Maestro's driver and server"
  DRIVER_DIR="$(mktemp -d)"
  ( cd "$DRIVER_DIR" && unzip -o -q "$HOME/.maestro/lib/maestro-client.jar" maestro-app.apk maestro-server.apk )
  adb -s "$DEVICE" install -r "$DRIVER_DIR/maestro-app.apk" >/dev/null
  adb -s "$DEVICE" install -r "$DRIVER_DIR/maestro-server.apk" >/dev/null
  rm -rf "$DRIVER_DIR"
fi
if [[ -f "$HOME/.maestro/sessions" ]]; then
  grep -v "$DEVICE" "$HOME/.maestro/sessions" > "$HOME/.maestro/sessions.tmp" \
    && mv "$HOME/.maestro/sessions.tmp" "$HOME/.maestro/sessions"
fi

run_maestro() {
  local -a args=(test)
  [[ -n "$DEVICE" ]] && args+=(--device "$DEVICE")
  for t in "${TAGS[@]+"${TAGS[@]}"}"; do args+=(--include-tags "$t"); done
  maestro "${args[@]}" "$@"
}

# ── 1. A clean install ─────────────────────────────────────────────────────
echo "==> clearing app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null

# The runtime permissions the app asks for. The flows still handle the system
# dialog if it appears, but granting them keeps the suite from depending on
# which prompt the ROM shows first.
for perm in android.permission.READ_SMS android.permission.POST_NOTIFICATIONS; do
  adb -s "$DEVICE" shell pm grant "$PKG" "$perm" >/dev/null 2>&1 || true
done

# ── 2. Onboarding ──────────────────────────────────────────────────────────
# It clears its own state, so it goes first and alone.
echo "==> onboarding"
if ! run_maestro .maestro/flows/00-onboarding.yaml; then
  echo "ONBOARDING FAILED"; exit 1
fi

# ── 3. The locale, then the demo data ──────────────────────────────────────
# After the onboarding, because that flow clears app state — and clearing app
# state clears the per-app locale with it.
echo "==> setting the app locale to $LOCALE"
adb -s "$DEVICE" shell cmd locale set-app-locales "$PKG" --locales "$LOCALE" >/dev/null
echo "    locale: $(adb -s "$DEVICE" shell cmd locale get-app-locales "$PKG" | tr -d '\r')"

if [[ $DO_SEED -eq 1 ]]; then
  # The seeder writes into the app's real database through an instrumentation
  # test, so the data survives and the app can be driven normally.
  echo "==> seeding demo data"
  ./gradlew :app:assembleDevDebug :app:assembleDevDebugAndroidTest --console=plain -q || {
    echo "could not build the instrumentation APK"; exit 1; }
  adb -s "$DEVICE" install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk >/dev/null
  adb -s "$DEVICE" install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk >/dev/null
  RESULT="$(adb -s "$DEVICE" shell am instrument -w -r \
    -e class "$SEEDER_CLASS" -e seedDemoData true \
    "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" 2>&1)"
  if ! grep -q "OK (1 test)" <<<"$RESULT"; then
    echo "seeding FAILED:"; sed 's/^/    /' <<<"$RESULT"; exit 1
  fi
  echo "    seeded"
  adb -s "$DEVICE" shell am force-stop "$PKG"
fi

# ── 4. The suite ───────────────────────────────────────────────────────────
FLOWS=()
if [[ ${#SELECT[@]} -gt 0 ]]; then
  for s in "${SELECT[@]}"; do FLOWS+=(".maestro/flows/${s}*.yaml"); done
else
  FLOWS=(".maestro/flows/0[1-9]*.yaml" ".maestro/flows/1[0-2]*.yaml" ".maestro/flows/13*.yaml")
fi

echo "==> running the suite"
FAILED=0
PASSED=()
for pattern in "${FLOWS[@]}"; do
  for f in $pattern; do
    [[ -f "$f" ]] || continue
    name="$(basename "$f" .yaml)"
    printf '\n--- %s ---\n' "$name"
    if run_maestro "$f"; then
      PASSED+=("$name")
    else
      FAILED=1
    fi
  done
done

# ── 5. Put the data back ───────────────────────────────────────────────────
# The delete-all-data flow wipes it, and the suite must be re-runnable.
if [[ $DO_SEED -eq 1 ]]; then
  echo
  echo "==> re-seeding demo data"
  adb -s "$DEVICE" shell am instrument -w -r \
    -e class "$SEEDER_CLASS" -e seedDemoData true \
    "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" >/dev/null 2>&1
  adb -s "$DEVICE" shell am force-stop "$PKG"
fi

echo
if [[ $FAILED -ne 0 ]]; then
  echo "SUITE FAILED — see ~/.maestro/tests for the artifacts."
  exit 1
fi
echo "SUITE PASSED — ${#PASSED[@]} flows"
