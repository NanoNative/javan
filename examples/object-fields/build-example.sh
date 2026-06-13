#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVAN=${JAVAN:-"$ROOT/../../dist/javan"}

rm -rf "$ROOT/.javan"
"$JAVAN" build "$ROOT" --output object-fields
"$ROOT/.javan/bin/object-fields"
