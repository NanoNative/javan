#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

mvn -q verify
scripts/build-javan-native.sh
dist/javan doctor >/dev/null
dist/javan --help >/dev/null
dist/javan --version >/dev/null
scripts/acceptance.sh
ARCHIVE=$(scripts/package-release.sh "${JAVAN_VERSION:-}")
scripts/verify-package.sh "$ARCHIVE"
