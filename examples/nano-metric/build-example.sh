#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVAN=${JAVAN:-"$ROOT/../../dist/javan"}
NANO_CLASSES=${NANO_CLASSES:-"$ROOT/../../../nano/target/classes"}

if [ ! -d "$NANO_CLASSES" ]; then
  echo "Nano classes not found. Set NANO_CLASSES=/path/to/nano/target/classes" >&2
  exit 2
fi

rm -rf "$ROOT/.javan"
"$JAVAN" build "$ROOT" --classpath "$NANO_CLASSES" --output nano-metric
"$ROOT/.javan/bin/nano-metric"
