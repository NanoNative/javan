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
if find target -maxdepth 1 -name 'javan-*SNAPSHOT*.jar' | grep . >/dev/null 2>&1; then
  printf '%s\n' "Refusing to build native image from a SNAPSHOT artifact." >&2
  exit 1
fi

native-image \
  --no-fallback \
  -o "$OUTPUT" \
  -jar "$JAR"

case "$OUTPUT" in
  /*) printf '%s\n' "Built $OUTPUT" ;;
  *) printf '%s\n' "Built $ROOT/$OUTPUT" ;;
esac
