#!/usr/bin/env bash
# Rebuilds gopeed-cookie.patch from the chunked base64 parts in this folder,
# then applies it to the freshly cloned upstream tree.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$DIR/gopeed-cookie.patch}"
: > "$OUT"
for f in $(ls "$DIR"/c*.b64 | sort); do
  tr -d '\n\r\t ' < "$f" >> "$OUT"
done
base64 -d "$OUT" > "$OUT.bin"
mv "$OUT.bin" "$OUT"
echo "rebuilt $OUT ($(wc -c < "$OUT") bytes)"
