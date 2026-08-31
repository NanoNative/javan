#!/bin/sh
set -eu

IMAGE=${1:-}
if [ -z "$IMAGE" ]; then
  printf '%s\n' "Usage: .github/scripts/verify-image.sh ghcr.io/nanonative/javan:<tag>" >&2
  exit 2
fi

if [ -z "${JAVAN_RELEASE_VERSION:-}" ]; then
  printf '%s\n' "Missing release proof input: JAVAN_RELEASE_VERSION" >&2
  exit 2
fi
if [ -z "${JAVAN_RELEASE_ARCHIVE_DIR:-}" ]; then
  printf '%s\n' "Missing release proof input: JAVAN_RELEASE_ARCHIVE_DIR" >&2
  exit 2
fi
if [ -z "${JAVAN_RELEASE_PROOF_DIR:-}" ]; then
  printf '%s\n' "Missing release proof input: JAVAN_RELEASE_PROOF_DIR" >&2
  exit 2
fi

archive_checksum() {
  target=$1
  archive="javan-${JAVAN_RELEASE_VERSION}-${target}.tar.gz"
  checksum_file="$JAVAN_RELEASE_ARCHIVE_DIR/$archive.sha256"

  if [ ! -f "$JAVAN_RELEASE_ARCHIVE_DIR/$archive" ] || [ ! -f "$checksum_file" ]; then
    printf '%s\n' "Missing release proof input: $archive or $archive.sha256" >&2
    exit 1
  fi
  if ! (cd "$JAVAN_RELEASE_ARCHIVE_DIR" && sha256sum -c "$archive.sha256") >&2; then
    printf '%s\n' "Release archive checksum did not verify: $archive" >&2
    exit 1
  fi

  checksum=$(awk 'NR == 1 { print $1 }' "$checksum_file")
  if ! printf '%s\n' "$checksum" | grep -Eq '^[0-9a-f]{64}$'; then
    printf '%s\n' "Invalid release archive checksum: $checksum_file" >&2
    exit 1
  fi
  printf '%s\n' "$checksum"
}

RAW=$(docker buildx imagetools inspect "$IMAGE" --raw)
has_linux_platform() {
  architecture=$1
  platform_pattern='"platform"[[:space:]]*:[[:space:]]*\{[^}]*("architecture"[[:space:]]*:[[:space:]]*"'"$architecture"'"[^}]*"os"[[:space:]]*:[[:space:]]*"linux"|"os"[[:space:]]*:[[:space:]]*"linux"[^}]*"architecture"[[:space:]]*:[[:space:]]*"'"$architecture"'")[^}]*\}'
  printf '%s\n' "$RAW" | tr '\n' ' ' | grep -Eq "$platform_pattern"
}

if ! has_linux_platform amd64; then
  printf '%s\n' "Image manifest is missing linux/amd64: $IMAGE" >&2
  exit 1
fi
if ! has_linux_platform arm64; then
  printf '%s\n' "Image manifest is missing linux/arm64: $IMAGE" >&2
  exit 1
fi

DIGEST=$(docker buildx imagetools inspect "$IMAGE" --format '{{.Manifest.Digest}}')
if ! printf '%s\n' "$DIGEST" | grep -Eq '^sha256:[0-9a-f]{64}$'; then
  printf '%s\n' "Image manifest has no immutable digest: $IMAGE" >&2
  exit 1
fi

x64_checksum=$(archive_checksum linux-x64)
arm64_checksum=$(archive_checksum linux-aarch64)
mkdir -p "$JAVAN_RELEASE_PROOF_DIR"
proof_name=$(printf '%s' "$IMAGE" | tr '/:@' '----')
proof_file="$JAVAN_RELEASE_PROOF_DIR/$proof_name.json"

cat >"$proof_file" <<EOF
{
  "image": "$IMAGE",
  "digest": "$DIGEST",
  "version": "$JAVAN_RELEASE_VERSION",
  "platforms": ["linux/amd64", "linux/arm64"],
  "archives": [
    {"target": "linux-x64", "sha256": "$x64_checksum"},
    {"target": "linux-aarch64", "sha256": "$arm64_checksum"}
  ]
}
EOF

printf '%s\n' "Verified image manifest $IMAGE ($DIGEST)"
printf '%s\n' "Recorded release proof $proof_file"
