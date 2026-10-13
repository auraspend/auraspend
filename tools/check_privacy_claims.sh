#!/usr/bin/env bash
# Fails when an absolute privacy claim appears without its exception stated in
# the same line.
#
# Why this exists: the app said "Messages are categorised on-device. Nothing
# leaves your phone." while a user-initiated Drive backup uploads the message
# queue — masked, but still message text — as plain JSON. The claim was written
# before the backup existed and nobody re-read it afterwards, which is exactly
# how a privacy promise drifts: not by a decision, but by an omission two
# features later.
#
# The rule is deliberately narrow: a small set of absolute phrases, and a small
# set of qualifiers that state the exception. It is a tripwire, not a grammar
# checker. Anything it flags is either reworded to be true or given a qualifier
# on the same line.
#
# Scope: the in-app strings (both locales), the website pages and the README.
# Not `memory/` or `docs/handoffs/` — those are historical records that quote the
# wording as it was, and rewriting a record to match today's copy would be the
# opposite of honest.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

FILES=(
  app/src/main/res/values/strings.xml
  app/src/main/res/values-hi/strings.xml
  docs/index.html
  docs/privacy.html
  docs/terms.html
  README.md
)

# Absolute claims: they read as "no data ever leaves", which is not true of a
# user-initiated backup.
ABSOLUTE=(
  "nothing leaves"
  "never leaves"
  "never uploads"
  "no data leaves"
  "nothing is uploaded"
  "nothing is ever uploaded"
  "stays on your phone"
  "stays on your device"
  "never sent"
)

# The exception, stated in the same line. "unless you" and "you start yourself"
# cover the Drive backup; the rest cover "only when you ask" phrasings.
QUALIFIER=(
  "unless you"
  "you start yourself"
  "drive backup"
  "the only upload"
  "only when you"
  "when you ask"
  "the only way"
  "until you ask"
)

fail=0
for file in "${FILES[@]}"; do
  [[ -f "$file" ]] || { echo "[FAIL] missing file: $file"; fail=1; continue; }
  while IFS= read -r line; do
    lower=$(printf '%s' "$line" | tr '[:upper:]' '[:lower:]')
    for claim in "${ABSOLUTE[@]}"; do
      [[ "$lower" == *"$claim"* ]] || continue
      qualified=0
      for q in "${QUALIFIER[@]}"; do
        [[ "$lower" == *"$q"* ]] && qualified=1 && break
      done
      if [[ $qualified -eq 0 ]]; then
        echo "[FAIL] $file: absolute privacy claim \"$claim\" with no exception on the line:"
        echo "       $(printf '%s' "$line" | sed 's/^[[:space:]]*//' | cut -c1-140)"
        fail=1
      fi
    done
  done < "$file"
done

if [[ $fail -ne 0 ]]; then
  echo
  echo "An absolute claim must state its exception (the Drive backup the user starts)."
  echo "Reword the copy, or add the exception to the same line."
  exit 1
fi

echo "Privacy claims: every absolute claim states its exception."
