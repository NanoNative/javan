#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

VERSION=${1:-${JAVAN_VERSION:-}}
REPOSITORY=${2:-${JAVAN_RELEASE_REPOSITORY:-${GITHUB_REPOSITORY:-}}}
RELEASE_DIR=${JAVAN_RELEASE_DIR:-dist/release}
OUTPUT=${JAVAN_HOMEBREW_FORMULA_OUTPUT:-$RELEASE_DIR/javan.rb}

if [ -z "$VERSION" ]; then
  printf '%s\n' "Usage: .github/scripts/generate-homebrew-formula.sh <version> <owner/repo>" >&2
  exit 1
fi

VERSION=${VERSION#v}
if ! printf '%s\n' "$VERSION" | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' >/dev/null 2>&1; then
  printf '%s\n' "Homebrew formula version must be a numeric triplet such as 2026.7.16: $VERSION" >&2
  exit 1
fi

if [ -z "$REPOSITORY" ]; then
  printf '%s\n' "Missing release repository owner/name." >&2
  exit 1
fi

TAG=v$VERSION

checksum_for() {
  archive_name=$1
  checksum_file=$RELEASE_DIR/$archive_name.sha256
  if [ ! -f "$checksum_file" ]; then
    printf '%s\n' "Missing checksum file: $checksum_file" >&2
    exit 2
  fi
  checksum=$(awk 'NR == 1 { print $1 }' "$checksum_file")
  if ! printf '%s\n' "$checksum" | grep -E '^[0-9a-fA-F]{64}$' >/dev/null 2>&1; then
    printf '%s\n' "Invalid checksum in $checksum_file" >&2
    exit 3
  fi
  printf '%s' "$checksum"
}

linux_x64_archive=javan-$VERSION-linux-x64.tar.gz
linux_aarch64_archive=javan-$VERSION-linux-aarch64.tar.gz
macos_aarch64_archive=javan-$VERSION-macos-aarch64.tar.gz

linux_x64_sha=$(checksum_for "$linux_x64_archive")
linux_aarch64_sha=$(checksum_for "$linux_aarch64_archive")
macos_aarch64_sha=$(checksum_for "$macos_aarch64_archive")

mkdir -p "$(dirname "$OUTPUT")"

cat >"$OUTPUT" <<EOF
class Javan < Formula
  desc "Native-first Java toolchain"
  homepage "https://github.com/$REPOSITORY"
  version "$VERSION"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/$REPOSITORY/releases/download/$TAG/$macos_aarch64_archive"
      sha256 "$macos_aarch64_sha"
    end
  end

  on_linux do
    if Hardware::CPU.arm?
      url "https://github.com/$REPOSITORY/releases/download/$TAG/$linux_aarch64_archive"
      sha256 "$linux_aarch64_sha"
    else
      url "https://github.com/$REPOSITORY/releases/download/$TAG/$linux_x64_archive"
      sha256 "$linux_x64_sha"
    end
  end

  def install
    bin.install "bin/javan"
    prefix.install "README.md"
    prefix.install "VERSION"
  end

  test do
    assert_match version.to_s, shell_output("#{bin}/javan --version")
    assert_match "javan home:", shell_output("#{bin}/javan doctor")
  end
end
EOF

printf '%s\n' "$ROOT/$OUTPUT"
