#!/bin/sh
set -eu

IMAGE=${1:-}
if [ -z "$IMAGE" ]; then
  printf '%s\n' "Usage: .github/scripts/verify-image.sh ghcr.io/nanonative/javan:<tag>" >&2
  exit 2
fi

RAW=$(docker buildx imagetools inspect "$IMAGE" --raw)
if ! printf '%s\n' "$RAW" | grep -Eq '"architecture"[[:space:]]*:[[:space:]]*"amd64"'; then
  printf '%s\n' "Image manifest is missing linux/amd64: $IMAGE" >&2
  exit 1
fi
if ! printf '%s\n' "$RAW" | grep -Eq '"architecture"[[:space:]]*:[[:space:]]*"arm64"'; then
  printf '%s\n' "Image manifest is missing linux/arm64: $IMAGE" >&2
  exit 1
fi

printf '%s\n' "Verified image manifest $IMAGE"
