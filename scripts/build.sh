#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

OUTPUT=${1:-dist/javan}

mvn -q -DskipTests clean package
mkdir -p "$(dirname -- "$OUTPUT")"
VERSION=$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -n 1)
JAR="target/javan-$VERSION.jar"
if [ -z "$VERSION" ] || [ ! -f "$JAR" ]; then
  printf '%s\n' "No packaged javan jar found in target/." >&2
  exit 1
fi
java -cp target/classes javan.Main build target/classes \
  --main javan.Main \
  --output javan-bootstrap-from-jvm

mkdir -p "$(dirname -- "$OUTPUT")"
target/.javan/bin/javan-bootstrap-from-jvm build target/classes \
  --main javan.Main \
  --output javan-bootstrap-rebuilt
target/.javan/bin/javan-bootstrap-rebuilt build target/classes \
  --main javan.Main \
  --output javan-bootstrap-verified
target/.javan/bin/javan-bootstrap-verified --version >/dev/null
cp target/.javan/bin/javan-bootstrap-verified "$OUTPUT"
if [ "$(uname -s)" = "Darwin" ] && command -v codesign >/dev/null 2>&1; then
  codesign --force --sign - "$OUTPUT" >/dev/null
fi

case "$OUTPUT" in
  /*) printf '%s\n' "Built $OUTPUT" ;;
  *) printf '%s\n' "Built $ROOT/$OUTPUT" ;;
esac
