#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
. .github/scripts/timing-report.sh

OUTPUT=${1:-dist/javan}
VERSION=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1)
GENERATION=${JAVAN_BOOTSTRAP_GENERATION:-3}
case "$GENERATION" in
  2|3) ;;
  *)
    printf '%s\n' "JAVAN_BOOTSTRAP_GENERATION must be 2 or 3, got: $GENERATION" >&2
    exit 2
    ;;
esac

REUSE_TARGET=${JAVAN_BUILD_REUSE_TARGET:-false}
if [ "$REUSE_TARGET" = "true" ]; then
  if [ ! -f target/classes/javan/Main.class ]; then
    printf '%s\n' "Missing target/classes/javan/Main.class for JAVAN_BUILD_REUSE_TARGET=true." >&2
    exit 1
  fi
else
  ./mvnw -q -DskipTests clean package
fi
mkdir -p "$(dirname -- "$OUTPUT")"
JAR="target/javan-$VERSION.jar"
if [ -z "$VERSION" ]; then
  printf '%s\n' "Could not resolve the Maven project version." >&2
  exit 1
fi
if [ "$REUSE_TARGET" != "true" ] && [ ! -f "$JAR" ]; then
  printf '%s\n' "No packaged javan jar found in target/." >&2
  exit 1
fi
javan_timing_run bootstrap_jvm java -cp target/classes javan.Main build target/classes \
  --main javan.Main \
  --output javan-bootstrap-from-jvm

mkdir -p "$(dirname -- "$OUTPUT")"
javan_timing_run bootstrap_gen2 target/.javan/bin/javan-bootstrap-from-jvm build target/classes \
  --main javan.Main \
  --output javan-bootstrap-rebuilt
BUILT=target/.javan/bin/javan-bootstrap-rebuilt
if [ "$GENERATION" = "3" ]; then
  javan_timing_run bootstrap_gen3 target/.javan/bin/javan-bootstrap-rebuilt build target/classes \
    --main javan.Main \
    --output javan-bootstrap-verified
  BUILT=target/.javan/bin/javan-bootstrap-verified
fi
"$BUILT" --version >/dev/null
cp "$BUILT" "$OUTPUT"
if [ "$(uname -s)" = "Darwin" ] && command -v codesign >/dev/null 2>&1; then
  codesign --force --sign - "$OUTPUT" >/dev/null
fi

case "$OUTPUT" in
  /*) printf '%s\n' "Built $OUTPUT" ;;
  *) printf '%s\n' "Built $ROOT/$OUTPUT" ;;
esac
