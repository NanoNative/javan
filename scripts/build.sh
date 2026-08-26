#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
. .github/scripts/timing-report.sh
. .github/scripts/generated-sources.sh

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    BOOTSTRAP_SUFFIX=.exe
    ;;
  *)
    BOOTSTRAP_SUFFIX=
    ;;
esac

OUTPUT=${1:-dist/javan$BOOTSTRAP_SUFFIX}
VERSION=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1)
GENERATION=${JAVAN_BOOTSTRAP_GENERATION:-3}
SOURCE=${JAVAN_BOOTSTRAP_SOURCE:-}
case "$GENERATION" in
  2|3) ;;
  *)
    printf '%s\n' "JAVAN_BOOTSTRAP_GENERATION must be 2 or 3, got: $GENERATION" >&2
    exit 2
    ;;
esac
if [ -n "$SOURCE" ] && [ "$GENERATION" != "3" ]; then
  printf '%s\n' "JAVAN_BOOTSTRAP_SOURCE requires generation 3." >&2
  exit 2
fi
if [ -n "$SOURCE" ]; then
  for file in javan_program.h javan_program.sources javan_runtime.c javan_runtime.h; do
    if [ ! -f "$SOURCE/$file" ]; then
      printf '%s\n' "Missing generated bootstrap source: $SOURCE/$file" >&2
      exit 1
    fi
  done
  javan_generated_sources "$SOURCE" >/dev/null
fi

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
mkdir -p "$(dirname -- "$OUTPUT")"
if [ -n "$SOURCE" ]; then
  BUILT=target/.javan/bin/javan-bootstrap-verified$BOOTSTRAP_SUFFIX
  GENERATED=target/.javan/generated
  mkdir -p "$(dirname -- "$BUILT")" "$GENERATED"
  if [ "$SOURCE" != "$GENERATED" ]; then
    javan_copy_generated_sources "$SOURCE" "$GENERATED"
  fi
  GENERATED_SOURCES=$(javan_generated_sources "$GENERATED")
  CC=${CC:-cc}
  # Generated source paths are manifest-validated and cannot contain shell separators or whitespace.
  # shellcheck disable=SC2086
  javan_timing_run bootstrap_gen3 sh -c \
    'cd "$1" && shift && exec "$1" -pthread -Wno-parentheses -I . $2 javan_runtime.c -o "$3"' \
    sh "$GENERATED" "$CC" "$GENERATED_SOURCES" "$ROOT/$BUILT"
else
  javan_timing_run bootstrap_jvm java -cp target/classes javan.Main build target/classes \
    --main javan.Main \
    --output javan-bootstrap-from-jvm
  javan_timing_run bootstrap_gen2 target/.javan/bin/javan-bootstrap-from-jvm build target/classes \
    --main javan.Main \
    --output javan-bootstrap-rebuilt
  BUILT=target/.javan/bin/javan-bootstrap-rebuilt$BOOTSTRAP_SUFFIX
  if [ "$GENERATION" = "3" ]; then
    GEN2_SOURCES=target/.javan/generated-gen2-proof
    javan_copy_generated_sources target/.javan/generated "$GEN2_SOURCES"
    javan_timing_run bootstrap_gen3 target/.javan/bin/javan-bootstrap-rebuilt build target/classes \
      --main javan.Main \
      --output javan-bootstrap-verified
    javan_compare_generated_sources "$GEN2_SOURCES" target/.javan/generated
    BUILT=target/.javan/bin/javan-bootstrap-verified$BOOTSTRAP_SUFFIX
  fi
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
