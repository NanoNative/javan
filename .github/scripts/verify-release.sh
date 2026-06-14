#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

mvn -q verify
scripts/build.sh
dist/javan doctor >/dev/null
dist/javan --help >/dev/null
dist/javan --version >/dev/null
JAVAN_BIN=$ROOT/dist/javan .github/scripts/acceptance.sh
JAVAN_BIN=$ROOT/dist/javan JAVAN_SANITIZER_REQUIRED=true sh .github/scripts/sanitizer-suite.sh
ARCHIVE=$(.github/scripts/package-release.sh "${JAVAN_VERSION:-}")
.github/scripts/verify-package.sh "$ARCHIVE"
