#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVAN=${JAVAN:-"$ROOT/../../dist/javan"}
MAVEN_REPO=${JAVAN_MAVEN_REPO:-${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}}
TYPEMAP_JAR=${TYPEMAP_JAR:-"$MAVEN_REPO/berlin/yuna/type-map/2025.06.1521025/type-map-2025.06.1521025.jar"}

if [ -z "$TYPEMAP_JAR" ] || [ ! -f "$TYPEMAP_JAR" ]; then
  echo "TypeMap jar not found. Set TYPEMAP_JAR=/path/to/type-map.jar" >&2
  exit 3
fi

rm -rf "$ROOT/.javan"
"$JAVAN" build "$ROOT" --classpath "$TYPEMAP_JAR" --output typemap-pair >/dev/null
"$ROOT/.javan/bin/typemap-pair"
