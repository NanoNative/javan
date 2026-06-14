#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVAN=${JAVAN:-"$ROOT/../../dist/javan"}

rm -rf "$ROOT/lib-classes" "$ROOT/lib" "$ROOT/app/.javan"
mkdir -p "$ROOT/lib-classes" "$ROOT/lib"

javac -d "$ROOT/lib-classes" "$ROOT/lib-src/dep/MathLib.java"
jar --create --file "$ROOT/lib/mathlib.jar" -C "$ROOT/lib-classes" .

"$JAVAN" build "$ROOT/app" --classpath "$ROOT/lib/mathlib.jar" --output library-dependency
"$ROOT/app/.javan/bin/library-dependency"
