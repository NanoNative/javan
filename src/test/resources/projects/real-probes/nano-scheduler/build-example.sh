#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVAN=${JAVAN:-"$ROOT/../../dist/javan"}
MAVEN_REPO=${JAVAN_MAVEN_REPO:-${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}}
NANO_JAR=${NANO_JAR:-"$MAVEN_REPO/org/nanonative/nano/2025.11.3131219/nano-2025.11.3131219.jar"}

if [ ! -f "$NANO_JAR" ]; then
  echo "Nano jar not found. Set NANO_JAR=/path/to/nano.jar" >&2
  exit 3
fi

rm -rf "$ROOT/.javan"
"$JAVAN" build "$ROOT" --classpath "$NANO_JAR" --output nano-scheduler >/dev/null
"$ROOT/.javan/bin/nano-scheduler"
