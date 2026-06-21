#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
SHOWCASE=example
SHOWCASE_ROOT=$ROOT/$SHOWCASE
TMP=${TMPDIR:-/tmp}/javan-showcase-$$
mkdir -p "$TMP"
trap 'rm -rf "$TMP" "$SHOWCASE_ROOT/target"' EXIT HUP INT TERM

assert_contains() {
  file=$1
  expected=$2
  if ! grep -F "$expected" "$file" >/dev/null 2>&1; then
    printf '%s\n' "Missing expected showcase report content in $file: $expected" >&2
    cat "$file" >&2
    exit 1
  fi
}

JAVAN_BIN=${JAVAN_BIN:-${1:-}}
JAVAN_IMAGE=${JAVAN_IMAGE:-}

if [ -z "$JAVAN_BIN" ] && [ -z "$JAVAN_IMAGE" ]; then
  if [ -x "$ROOT/dist/javan" ]; then
    JAVAN_BIN=$ROOT/dist/javan
  else
    printf '%s\n' "Set JAVAN_BIN=/path/to/javan or JAVAN_IMAGE=image:tag." >&2
    exit 2
  fi
fi

if [ -n "$JAVAN_BIN" ]; then
  if [ ! -x "$JAVAN_BIN" ]; then
    printf '%s\n' "Missing executable javan binary: $JAVAN_BIN" >&2
    exit 2
  fi
  case "$JAVAN_BIN" in
    /*) ;;
    *) JAVAN_BIN=$(CDPATH= cd -- "$(dirname -- "$JAVAN_BIN")" && pwd)/$(basename -- "$JAVAN_BIN") ;;
  esac
fi

rm -rf "$SHOWCASE_ROOT/target"
mkdir -p "$SHOWCASE_ROOT/target/classes"
find "$SHOWCASE_ROOT/src/main/java" -name '*.java' | sort >"$TMP/sources.txt"
javac -d "$SHOWCASE_ROOT/target/classes" @"$TMP/sources.txt"

if [ -n "$JAVAN_IMAGE" ]; then
  docker run --rm \
    -v "$ROOT:/workspace" \
    -w /workspace \
    "$JAVAN_IMAGE" \
    build "$SHOWCASE/target/classes" --main com.acme.showcase.Main --output native-showcase

  docker run --rm \
    -v "$ROOT:/workspace" \
    -w /workspace \
    "$JAVAN_IMAGE" \
    report "$SHOWCASE/target" >/dev/null

  docker run --rm \
    --entrypoint "/workspace/$SHOWCASE/target/.javan/bin/native-showcase" \
    -v "$ROOT:/workspace" \
    -w /workspace \
    "$JAVAN_IMAGE" >"$TMP/showcase.out"
else
  "$JAVAN_BIN" build "$SHOWCASE_ROOT/target/classes" --main com.acme.showcase.Main --output native-showcase
  "$JAVAN_BIN" report "$SHOWCASE_ROOT/target" >/dev/null
  "$SHOWCASE_ROOT/target/.javan/bin/native-showcase" >"$TMP/showcase.out"
fi

SHOWCASE_REPORT=$SHOWCASE_ROOT/target/.javan/reports/report.json
if [ ! -f "$SHOWCASE_REPORT" ]; then
  printf '%s\n' "Missing native showcase unified report: $SHOWCASE_REPORT" >&2
  exit 1
fi
assert_contains "$SHOWCASE_REPORT" '"name": "diagnostics"'
assert_contains "$SHOWCASE_REPORT" '"errors": 0'
assert_contains "$SHOWCASE_REPORT" '"warnings": 0'
assert_contains "$SHOWCASE_REPORT" '"name": "runtime"'
assert_contains "$SHOWCASE_REPORT" '"artifactKind": "app"'

cat >"$TMP/showcase.expected" <<'EOF'
javan native showcase
metric requests -> 9
first request
iter first request
request second request
map 9
samples 3
copy 8
name-length 8
char e
same true
enum READY
static ready 1
caught boom
safe deterministic native build
EOF

if ! cmp "$TMP/showcase.expected" "$TMP/showcase.out" >/dev/null; then
  printf '%s\n' "Native showcase output mismatch" >&2
  printf '%s\n' "--- expected" >&2
  cat "$TMP/showcase.expected" >&2
  printf '%s\n' "--- actual" >&2
  cat "$TMP/showcase.out" >&2
  exit 1
fi

printf '%s\n' "Verified native showcase"
