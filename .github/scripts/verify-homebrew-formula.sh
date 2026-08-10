#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

FORMULA=${1:-${JAVAN_HOMEBREW_FORMULA_OUTPUT:-dist/release/javan.rb}}
VERSION=${2:-${JAVAN_VERSION:-}}
REPOSITORY=${3:-${JAVAN_RELEASE_REPOSITORY:-${GITHUB_REPOSITORY:-}}}
RELEASE_DIR=${JAVAN_RELEASE_DIR:-dist/release}

if [ -z "$VERSION" ] || [ -z "$REPOSITORY" ]; then
  printf '%s\n' "Usage: .github/scripts/verify-homebrew-formula.sh <formula> <version> <owner/repo>" >&2
  exit 1
fi

TAG=$VERSION

if [ ! -f "$FORMULA" ]; then
  printf '%s\n' "Missing Homebrew formula: $FORMULA" >&2
  exit 2
fi

assert_contains() {
  expected=$1
  if ! grep -F "$expected" "$FORMULA" >/dev/null 2>&1; then
    printf '%s\n' "Missing expected Homebrew formula content in $FORMULA: $expected" >&2
    exit 3
  fi
}

checksum_for() {
  archive_name=$1
  checksum_file=$RELEASE_DIR/$archive_name.sha256
  if [ ! -f "$checksum_file" ]; then
    printf '%s\n' "Missing checksum file: $checksum_file" >&2
    exit 4
  fi
  awk 'NR == 1 { print $1 }' "$checksum_file"
}

linux_x64_archive=javan-$VERSION-linux-x64.tar.gz
linux_aarch64_archive=javan-$VERSION-linux-aarch64.tar.gz
assert_contains "class Javan < Formula"
assert_contains "homepage \"https://github.com/$REPOSITORY\""
assert_contains "version \"$VERSION\""
assert_contains "url \"https://github.com/$REPOSITORY/releases/download/$TAG/$linux_x64_archive\""
assert_contains "url \"https://github.com/$REPOSITORY/releases/download/$TAG/$linux_aarch64_archive\""
assert_contains "sha256 \"$(checksum_for "$linux_x64_archive")\""
assert_contains "sha256 \"$(checksum_for "$linux_aarch64_archive")\""
assert_contains "bin.install \"bin/javan\""
assert_contains "prefix.install \"README.md\""
assert_contains "prefix.install \"VERSION\""
assert_contains "assert_match version.to_s, shell_output(\"#{bin}/javan --version\")"
assert_contains "assert_match \"javan home:\", shell_output(\"#{bin}/javan doctor\")"

printf '%s\n' "Verified Homebrew formula $FORMULA"
