#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVAN=${JAVAN:-"$ROOT/../../dist/javan"}
TYPEMAP_TARGET=${TYPEMAP_TARGET:-"$ROOT/../../../TypeMap/target"}
TYPEMAP_JAR=${TYPEMAP_JAR:-$(find "$TYPEMAP_TARGET" -maxdepth 1 -name 'type-map-*.jar' | sort | tail -1)}

if [ -z "$TYPEMAP_JAR" ] || [ ! -f "$TYPEMAP_JAR" ]; then
  echo "TypeMap jar not found. Set TYPEMAP_JAR=/path/to/type-map.jar" >&2
  exit 2
fi

rm -rf "$ROOT/.javan"
"$JAVAN" build "$ROOT" --classpath "$TYPEMAP_JAR" --output typemap-pair
"$ROOT/.javan/bin/typemap-pair"
