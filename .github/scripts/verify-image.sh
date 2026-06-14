#!/bin/sh
set -eu

IMAGE=${1:-}
if [ -z "$IMAGE" ]; then
  printf '%s\n' "Usage: .github/scripts/verify-image.sh ghcr.io/nanonative/javan:<tag>" >&2
  exit 2
fi

RAW=$(docker buildx imagetools inspect "$IMAGE" --raw)
if ! printf '%s\n' "$RAW" | grep -F '"architecture":"amd64"' >/dev/null; then
  printf '%s\n' "Image manifest is missing linux/amd64: $IMAGE" >&2
  exit 1
fi
if ! printf '%s\n' "$RAW" | grep -F '"architecture":"arm64"' >/dev/null; then
  printf '%s\n' "Image manifest is missing linux/arm64: $IMAGE" >&2
  exit 1
fi

printf '%s\n' "Verified image manifest $IMAGE"
