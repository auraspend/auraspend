#!/usr/bin/env bash
# Check that AuraSpend's licensing is internally consistent and complete.
#
# The licence drifted across eleven files before this script existed — LICENSE,
# the README badge and section, both string resource locales, the splash footer,
# the website, and the legal pages all disagreed, and nothing noticed. An
# inaccurate licence claim is a real compliance problem rather than a cosmetic
# one, so it gets a mechanical check.
#
# It also checks the *other* direction: that every declared dependency has a
# third-party attribution. Adding a dependency is the moment an out-of-date
# attribution becomes possible, and nothing else in the build would notice.
#
# Usage: tools/check_license.sh
# Exit:  0 consistent, 1 at least one failure.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

failures=0
pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }

echo "Licence: AGPL-3.0-or-later"

# ── 1. LICENSE is the AGPL, and the in-app copy is byte-identical ────────────
if head -1 LICENSE | grep -q "GNU AFFERO GENERAL PUBLIC LICENSE"; then
    pass "LICENSE is the AGPL"
else
    fail "LICENSE is not the AGPL — first line reads: $(head -1 LICENSE)"
fi

RAW=app/src/main/res/raw/agpl_3_0.txt
if [ -f "$RAW" ]; then
    if cmp -s LICENSE "$RAW"; then
        pass "bundled in-app licence text is byte-identical to LICENSE"
    else
        fail "$RAW differs from LICENSE (AGPL-3.0 §4 requires a copy of the Licence to accompany the work)"
    fi
else
    fail "$RAW is missing — the app must ship the licence text (AGPL-3.0 §4)"
fi

# ── 2. Every place that states AuraSpend's own licence agrees ───────────────
# Each of these is a place the licence has previously been written by hand.
check_mentions() {
    local label="$1" file="$2" pattern="$3"
    if [ ! -f "$file" ]; then
        fail "$label: $file does not exist"
        return
    fi
    if grep -qE "$pattern" "$file"; then
        pass "$label"
    else
        fail "$label — $file does not mention the AGPL"
    fi
}

check_mentions "README badge states AGPL" \
    README.md 'img\.shields\.io/badge/License-AGPL'
check_mentions "README License section states AGPL" \
    README.md 'GNU Affero General Public License'
check_mentions "English About string states AGPL" \
    app/src/main/res/values/strings.xml 'settings_about_version.*AGPL-3\.0'
check_mentions "Hindi About string states AGPL" \
    app/src/main/res/values-hi/strings.xml 'settings_about_version.*AGPL-3\.0'
check_mentions "Splash footer states AGPL" \
    app/src/main/res/values-hi/strings.xml 'splash_open_source_license.*AGPL-3\.0'
check_mentions "CONTRIBUTING states AGPL and copyright assignment" \
    .github/CONTRIBUTING.md 'assign copyright'
check_mentions "PR template carries the assignment confirmation" \
    .github/PULL_REQUEST_TEMPLATE.md 'assign copyright in this contribution'
check_mentions "website hero states AGPL" \
    docs/index.html 'AGPL-3\.0'
check_mentions "website schema.org licence is AGPL" \
    docs/index.html '"license": "https://www\.gnu\.org/licenses/agpl'
check_mentions "terms of service state AGPL" \
    docs/terms.html 'agpl-3\.0'
check_mentions "third-party licence index exists" \
    docs/licenses/README.md 'AGPL-3\.0-or-later'

# ── 3. No stale claim that AuraSpend itself is Apache-licensed ───────────────
# An earlier version of this check grepped whole files for "Apache" and failed on
# two lines that were both correct: the sentence describing the *model's* licence
# (a third party, permissively licensed, and required to be stated accurately) and
# the historical note that AuraSpend *was* Apache-2.0 up to 0.1.0 (which the terms
# of service have to record). So the check is line-scoped, and a line is exempt
# when it carries a marker explaining why an Apache mention is legitimate.
# Failures print the offending line, because "it failed" is not actionable.
exempt='0\.1\.0|0\.1\.1|relicens|was Apache|rather than|was licensed under|third.party|this project relicens'
for f in README.md docs/index.html docs/terms.html \
         app/src/main/res/values/strings.xml \
         app/src/main/res/values-hi/strings.xml \
         .github/CONTRIBUTING.md; do
    if [ ! -f "$f" ]; then
        fail "$f does not exist"
        continue
    fi
    # Section-aware: any section whose heading announces a licence comparison is
    # exempt as a unit. Comparing the two licences is the entire point of such a
    # section, and a sentence can wrap across lines so that its marker ("rather
    # than Apache-2.0") sits on a different line from its "Apache" mention —
    # which is exactly what a line-scoped keyword list cannot see.
    # The comparison exemption is level-aware: a "### Why AGPL rather than"
    # section ends at the next heading of the same or higher level, so content
    # after it is checked again. Without the level check, anything appended after
    # the last heading would be silently exempt.
    # Two traps this had to be written around, both found by testing that it
    # could still FAIL and not just pass:
    #
    #   * `/re/i` is not a case-insensitive flag in POSIX awk. It parses as
    #     division — ($0 ~ /re/) / i — and since `i` is 0, 1/0 is truthy, so
    #     EVERY line matched. tolower() is used instead.
    #   * `/^#+[[:space:]]/` also matches a `#` comment inside a fenced code
    #     block, so shell comments in the build examples were treated as
    #     headings. Fence state is tracked explicitly.
    offenders="$(awk '
        BEGIN { inc = 0; level = 9; fence = 0 }
        /^[[:space:]]*```/ { fence = !fence; next }
        !fence && match($0, /^#+[[:space:]]/) {
            h = $0
            sub(/[^#].*$/, "", h)
            lv = length(h)
            if (lv <= level) inc = 0
            level = lv
            if (tolower($0) ~ /rather than|relicens|licen[cs]e (change|history)/) {
                inc = 1
                level = lv
            }
            next
        }
        inc { next }
        /Apache/ { print NR ":" $0 }
    ' "$f" | grep -Eiv "$exempt" || true)"
    # A line naming a third-party component is not a claim about AuraSpend.
    offenders="$(printf '%s\n' "$offenders" \
        | grep -Eiv 'all-MiniLM|model|encoder|Plus Jakarta|ONNX|AndroidX|Google|OkHttp|Guava|SmolLM2|Gemma|dependenc|bundled' || true)"
    if [ -n "$offenders" ]; then
        fail "$f mentions Apache without saying why:"
        printf '%s\n' "$offenders" | sed 's/^/          /'
    else
        pass "$f makes no unexplained Apache claim about AuraSpend"
    fi
done

# ── 4. The splash footer is localized, not hardcoded English ────────────────
if grep -qE '"Open source · AGPL-3\.0"' \
        app/src/main/java/com/awbuilds/auraspend/ui/splash/SplashScreen.kt; then
    fail "SplashScreen.kt hardcodes the licence string — use a string resource (AGENTS.md i18n rule)"
else
    pass "splash licence string comes from a resource"
fi

# ── 5. EN/hi parity for the licence strings ─────────────────────────────────
python3 - <<'PY' || exit 1
import re, sys
def keys(p):
    return set(re.findall(r'<string name="([^"]+)"', open(p, encoding="utf-8").read()))
en = keys("app/src/main/res/values/strings.xml")
hi = keys("app/src/main/res/values-hi/strings.xml")
missing = sorted(en - hi)
if missing:
    print(f"  FAIL  strings missing a Hindi translation: {', '.join(missing)}")
    sys.exit(1)
print("  ok    every English string has a Hindi twin")
PY
[ $? -ne 0 ] && failures=$((failures + 1))

# ── 6. Every declared dependency has a third-party attribution ──────────────
# The mapping is explicit on purpose: adding a dependency forces a decision about
# how it is attributed, rather than letting the attribution table silently rot.
# Keys are grep patterns matched against the app's THIRD_PARTY list and the
# docs/licenses index.
check_attribution() {
    local label="$1" pattern="$2"
    if grep -qE "$pattern" \
            app/src/main/java/com/awbuilds/auraspend/ui/settings/LicenseScreen.kt; then
        pass "attributed in-app: $label"
    else
        fail "no in-app attribution for: $label — add a THIRD_PARTY row in LicenseScreen.kt"
    fi
    if grep -qiE "$pattern" docs/licenses/README.md; then
        pass "attributed in docs/licenses: $label"
    else
        fail "no docs/licenses/README.md entry for: $label"
    fi
}

check_attribution "Plus Jakarta Sans" 'Plus Jakarta Sans'
check_attribution "ONNX Runtime (MIT)" 'ONNX Runtime'
check_attribution "SQLCipher" 'SQLCipher'
check_attribution "AndroidX" 'AndroidX'
check_attribution "Google API Client / Drive / play-services-auth" 'Google API Client'
check_attribution "Google Play In-App Updates" 'Google Play In-App Updates'
check_attribution "OkHttp" 'OkHttp'
check_attribution "Guava" 'Guava'
check_attribution "all-MiniLM-L6-v2 (the runtime model)" 'all-MiniLM-L6-v2'

# Every permissive licence the graph actually contains must have its text shipped.
for lic in OFL-PlusJakartaSans.txt MIT-ONNX-Runtime.txt Apache-2.0.txt; do
    if [ -s "docs/licenses/$lic" ]; then
        pass "licence text shipped: $lic"
    else
        fail "docs/licenses/$lic is missing or empty"
    fi
done

echo
if [ "$failures" -eq 0 ]; then
    echo "Licence metadata is consistent."
    exit 0
fi
echo "$failures check(s) failed."
exit 1
