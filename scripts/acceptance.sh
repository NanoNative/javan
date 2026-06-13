#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVAN_BIN=${JAVAN_BIN:-"$ROOT/dist/javan"}

if [ ! -x "$JAVAN_BIN" ]; then
  printf '%s\n' "Missing executable javan binary: $JAVAN_BIN" >&2
  printf '%s\n' "Run scripts/build-javan-native.sh first, or set JAVAN_BIN=/path/to/javan." >&2
  exit 2
fi

TMP=${TMPDIR:-/tmp}/javan-acceptance-$$
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

PASS_COUNT=0

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  printf 'ok %s - %s\n' "$PASS_COUNT" "$1"
}

fail() {
  printf 'not ok - %s\n' "$1" >&2
  exit 1
}

run_cmd() {
  command_name=$1
  shift
  command_out="$TMP/$command_name.out"
  command_err="$TMP/$command_name.err"
  if "$@" >"$command_out" 2>"$command_err"; then
    return 0
  fi
  printf '%s\n' "Command failed: $*" >&2
  printf '%s\n' "--- stdout" >&2
  cat "$command_out" >&2
  printf '%s\n' "--- stderr" >&2
  cat "$command_err" >&2
  return 1
}

assert_contains() {
  file=$1
  text=$2
  if ! grep -F "$text" "$file" >/dev/null 2>&1; then
    printf '%s\n' "Expected to find '$text' in $file" >&2
    cat "$file" >&2
    exit 1
  fi
}

accepts_jvm_equivalent_app() {
  project=$1
  main_class=${2:-com.acme.Main}
  name=$(basename "$project")
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  java -cp "$full_project/.javan/classes" "$main_class" >"$TMP/$build_name.jvm" 2>"$TMP/$build_name.jvm.err" \
    || fail "$project JVM reference run"
  "$full_project/.javan/bin/$name" >"$TMP/$build_name.native" 2>"$TMP/$build_name.native.err" \
    || fail "$project native run"
  cmp "$TMP/$build_name.jvm" "$TMP/$build_name.native" >/dev/null \
    || fail "$project native output differs from JVM"
  pass "$project matches JVM output"
}

rejects_check() {
  project=$1
  expected=$2
  name=$(printf '%s' "$project" | tr '/-' '__')
  full_project="$ROOT/$project"

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  if "$JAVAN_BIN" check "$full_project" >"$TMP/$name.out" 2>"$TMP/$name.err"; then
    fail "$project unexpectedly passed"
  fi
  assert_contains "$TMP/$name.err" "$expected"
  pass "$project rejected clearly"
}

accepts_native_library() {
  project=examples/native-library
  full_project="$ROOT/$project"
  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "native-library-build" "$JAVAN_BIN" build "$full_project" --kind staticlib --export com.acme.Math.add --bindings c,rust,go,python
  cc "$full_project/caller.c" "$full_project/.javan/dist/libnative-library.a" -o "$TMP/native-library-caller" \
    >"$TMP/native-library-cc.out" 2>"$TMP/native-library-cc.err" || fail "$project C caller compile"
  "$TMP/native-library-caller" >"$TMP/native-library.out" 2>"$TMP/native-library.err" \
    || fail "$project C caller run"
  assert_contains "$TMP/native-library.out" "10"
  pass "$project C ABI smoke"
}

accepts_optional_typemap_probe() {
  if [ -z "${TYPEMAP_JAR:-}" ] && ! ls "$ROOT"/../TypeMap/target/*.jar >/dev/null 2>&1; then
    pass "examples/typemap-pair skipped without TYPEMAP_JAR"
    return 0
  fi
  (cd "$ROOT/examples/typemap-pair" && JAVAN="$JAVAN_BIN" ./build-example.sh) \
    >"$TMP/typemap.out" 2>"$TMP/typemap.err" || fail "examples/typemap-pair"
  assert_contains "$TMP/typemap.out" "value"
  pass "examples/typemap-pair native probe"
}

accepts_optional_nano_probe() {
  if [ -z "${NANO_CLASSES:-}" ] && [ ! -d "$ROOT/../nano/target/classes" ]; then
    pass "examples/nano-metric skipped without NANO_CLASSES"
    return 0
  fi
  (cd "$ROOT/examples/nano-metric" && JAVAN="$JAVAN_BIN" ./build-example.sh) \
    >"$TMP/nano.out" 2>"$TMP/nano.err" || fail "examples/nano-metric"
  assert_contains "$TMP/nano.out" "requests"
  pass "examples/nano-metric native probe"
}

cd "$ROOT"

accepts_jvm_equivalent_app examples/hello
accepts_jvm_equivalent_app examples/primitive-int
accepts_jvm_equivalent_app examples/boolean-basic
accepts_jvm_equivalent_app examples/int-array
accepts_jvm_equivalent_app examples/long-basic
accepts_jvm_equivalent_app examples/long-array
accepts_jvm_equivalent_app examples/float-double
accepts_jvm_equivalent_app examples/primitive-arrays
accepts_jvm_equivalent_app examples/static-fields
accepts_jvm_equivalent_app examples/string-intrinsics
accepts_jvm_equivalent_app examples/string-concat
accepts_jvm_equivalent_app examples/enum-basic
accepts_jvm_equivalent_app examples/interface-dispatch
accepts_jvm_equivalent_app examples/polymorphic-virtual
accepts_jvm_equivalent_app examples/interface-polymorphic
accepts_jvm_equivalent_app examples/try-catch
accepts_jvm_equivalent_app examples/object-fields
accepts_jvm_equivalent_app examples/jdk-intrinsics

accepts_native_library

rejects_check examples/no-main "no main class found"
rejects_check examples/multiple-main "multiple main classes"
rejects_check examples/unsupported-reflection "Class.forName"

accepts_optional_typemap_probe
accepts_optional_nano_probe

printf '%s\n' "Acceptance passed: $PASS_COUNT checks"
