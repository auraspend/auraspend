#!/usr/bin/env bash
# Park the current deck as a variant, so the live screenshots/ folder is free
# for the next attempt.
#
#   ./variant.sh 02-iphone-mockup "iPhone bezel with the concept's iOS chrome"
#
# Copies the exported slides, the feature graphic, the deck state and the two
# source files that define the device frame into variants/<name>/. Nothing is
# deleted: the live deck keeps working afterwards.
set -euo pipefail
cd "$(dirname "$0")"

NAME="${1:?usage: ./variant.sh <name> [note]}"
NOTE="${2:-}"
DEST="variants/$NAME"

[[ -e "$DEST" ]] && { echo "variants/$NAME already exists"; exit 1; }
[[ -d screenshots ]] || { echo "no screenshots/ to park"; exit 1; }

mkdir -p "$DEST/screenshots" "$DEST/code"
cp screenshots/*.png "$DEST/screenshots/" 2>/dev/null || true
cp feature-1024x500.png "$DEST/" 2>/dev/null || true
cp app-store-screenshots.json "$DEST/" 2>/dev/null || true
cp feature-graphic.html "$DEST/" 2>/dev/null || true
# The frame lives in these two files; without them the deck state is only half
# the recipe, and this repo has no committed history for them yet.
cp src/components/editor/device-frames.tsx "$DEST/code/" 2>/dev/null || true
cp src/lib/constants.ts "$DEST/code/" 2>/dev/null || true

cat > "$DEST/NOTE.md" <<EOF
# Variant: $NAME

${NOTE:-(no note)}

Parked $(date '+%Y-%m-%d %H:%M') from \`docs/store/\` — 7 slides at 1080×1920 plus
the feature graphic, the deck state (\`app-store-screenshots.json\`) and the
frame source (\`code/\`) as it was at that moment.

Restore: copy \`screenshots/\` and \`feature-1024x500.png\` back if this becomes the
deck you ship, and \`code/\` over \`src/\` if the frame differed.
EOF

echo "parked -> $DEST"
ls "$DEST"
