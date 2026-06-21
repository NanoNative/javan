#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

mvn -q clean verify
scripts/build.sh
ARCHIVE=$(.github/scripts/package-release.sh "${JAVAN_VERSION:-}")
.github/scripts/verify-package.sh "$ARCHIVE"

TMP=${TMPDIR:-/tmp}/javan-release-verify-$$
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
tar -xzf "$ARCHIVE" -C "$TMP"
PACKAGE_ROOT=$TMP/$(basename "$ARCHIVE" .tar.gz)
PACKAGE_BIN=$PACKAGE_ROOT/bin/javan

"$PACKAGE_BIN" doctor >/dev/null
"$PACKAGE_BIN" --help >/dev/null
"$PACKAGE_BIN" --version >/dev/null
JAVAN_BIN=$PACKAGE_BIN .github/scripts/acceptance.sh
JAVAN_BIN=$PACKAGE_BIN JAVAN_SANITIZER_REQUIRED=true sh .github/scripts/sanitizer-suite.sh
