#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVAN=${JAVAN:-"$ROOT/../../dist/javan"}
MAVEN_REPO=${JAVAN_MAVEN_REPO:-${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}}
NANO_JAR=${NANO_JAR:-"$MAVEN_REPO/org/nanonative/nano/2025.11.3131219/nano-2025.11.3131219.jar"}
NANO_CLASSPATH=${NANO_CLASSPATH:-}

if [ -z "$NANO_CLASSPATH" ] && [ -f "$NANO_JAR" ]; then
  NANO_CLASSPATH=$NANO_JAR
fi

if [ -z "$NANO_CLASSPATH" ] && [ -n "${NANO_CLASSES:-}" ] && [ -d "$NANO_CLASSES" ]; then
  NANO_CLASSPATH=$NANO_CLASSES
fi

if [ -z "$NANO_CLASSPATH" ]; then
  echo "Nano dependency not found. Set NANO_JAR=/path/to/nano.jar, NANO_CLASSPATH=/path/to/dependency, or NANO_CLASSES=/path/to/compiled/classes" >&2
  exit 3
fi

rm -rf "$ROOT/.javan"
"$JAVAN" build "$ROOT" --classpath "$NANO_CLASSPATH" --output nano-metric >/dev/null
"$ROOT/.javan/bin/nano-metric"
