#!/usr/bin/env bash
# Check that the website agrees with the app about which version it is.
#
# Why this exists: the site carried a hardcoded "Download 0.1.91" on two
# pages and "0.1.91" in three meta descriptions, while the app was at 0.1.93
# and the site's own changelog said 0.1.93. A version number written into a
# page is a number that is wrong the moment the next release is cut, and the
# fix is not to bump it — it is to stop writing versions where they do not
# belong. The only place one is genuinely required is the structured data,
# where `softwareVersion` is a fact about the build, and that one is checked
# here so it cannot drift.
#
# Usage: tools/check_site_version.sh
# Exit:  0 consistent, 1 a mismatch.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

failures=0
pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }

# The version the app is about to be, or is, published as.
APP_VERSION="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
if [[ -z "$APP_VERSION" ]]; then
  echo "could not read versionName from app/build.gradle.kts"
  exit 1
fi

# 1. The structured data's softwareVersion must be the app's version.
if [[ ! -f site/index.html ]]; then
  # An old branch, or a PR based on one, may not have the site at all. There is
  # nothing to disagree with, so this is not a failure.
  echo "  skip  there is no site/index.html — nothing to check"
  echo
  echo "The site agrees with the app."
  exit 0
fi

SITE_VERSION="$(sed -n 's/.*"softwareVersion": "\([^"]*\)".*/\1/p' site/index.html | head -1)"
if [[ "$SITE_VERSION" == "$APP_VERSION" ]]; then
  pass "the site's softwareVersion is $APP_VERSION"
else
  fail "the site's softwareVersion is '${SITE_VERSION:-none}' and the app is '$APP_VERSION'"
fi

# 2. Nothing else on the site may name *the current release*. The patterns
#    are narrow on purpose: a version in a download button, in a page's meta
#    description, or in the name of the artifact, is a claim about what is
#    current, and it is wrong the moment the next release is cut. A version
#    that is a historical fact — the licence history, the release that
#    introduced the encrypted database — is not flagged, because it does not go
#    stale.
STALE="$(grep -rnoE '(Download|AuraSpend|AuraSpend-V)[ -]?0\.1\.[0-9]+' site/ --include='*.html' \
  | grep -v 'site/changelog.html' || true)"
if [[ -z "$STALE" ]]; then
  pass "no page outside the changelog claims to be a particular release"
else
  fail "a page claims to be a particular release, and it will be wrong after the next one:"
  printf '%s\n' "$STALE" | sed 's/^/          /'
fi

echo
if [[ $failures -eq 0 ]]; then
  echo "The site agrees with the app."
  exit 0
fi
echo "$failures check(s) failed."
exit 1
