#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TMP=${TMPDIR:-/tmp}/javan-sanitizer-$$
PROJECT=${1:-src/test/resources/projects/native-profile/memory-soak}
FULL_PROJECT=$ROOT/$PROJECT
CC=${CC:-cc}
SANITIZER_FLAGS=${SANITIZER_FLAGS:-"-fsanitize=address,undefined -fno-omit-frame-pointer"}
EXPECTED_EXIT=${JAVAN_SANITIZER_EXPECTED_EXIT:-0}
COMPARE_JVM=${JAVAN_SANITIZER_COMPARE_JVM:-true}
EXPECTED_STDOUT=${JAVAN_SANITIZER_EXPECTED_STDOUT:-}
EXPECTED_STDERR_CONTAINS=${JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS:-}
SANITIZER_REQUIRED=${JAVAN_SANITIZER_REQUIRED:-false}
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

if [ -n "${JAVAN_BIN:-}" ]; then
  if [ ! -x "$JAVAN_BIN" ]; then
    printf '%s\n' "Missing executable javan binary: $JAVAN_BIN" >&2
    exit 2
  fi
elif [ -f "$ROOT/target/classes/javan/Main.class" ]; then
  JAVAN_BIN=$TMP/javan
  JAVAN_SANITIZER_CLASSES=$ROOT/target/classes
  export JAVAN_SANITIZER_CLASSES
  {
    printf '%s\n' '#!/bin/sh'
    printf '%s\n' 'exec java -cp "$JAVAN_SANITIZER_CLASSES" javan.Main "$@"'
  } >"$JAVAN_BIN"
  chmod +x "$JAVAN_BIN"
elif [ -x "$ROOT/dist/javan" ]; then
  JAVAN_BIN=$ROOT/dist/javan
elif [ -x "$ROOT/target/.javan/bin/javan-verified" ]; then
  JAVAN_BIN=$ROOT/target/.javan/bin/javan-verified
else
  printf '%s\n' "Missing javan runtime: build target/classes or set JAVAN_BIN=/path/to/javan." >&2
  exit 2
fi

JAVAN_MAX_ALLOCATION_BYTES= \
JAVAN_HEAP_LIMIT_BYTES= \
JAVAN_GC_STRESS= \
JAVAN_GC_SAFEPOINT_INTERVAL= \
  "$JAVAN_BIN" clean "$FULL_PROJECT" >/dev/null 2>&1 || true
JAVAN_MAX_ALLOCATION_BYTES= \
JAVAN_HEAP_LIMIT_BYTES= \
JAVAN_GC_STRESS= \
JAVAN_GC_SAFEPOINT_INTERVAL= \
  "$JAVAN_BIN" build "$FULL_PROJECT" >/dev/null

if [ "$COMPARE_JVM" = "true" ]; then
  JAVAN_MAX_ALLOCATION_BYTES= \
  JAVAN_HEAP_LIMIT_BYTES= \
  JAVAN_GC_STRESS= \
  JAVAN_GC_SAFEPOINT_INTERVAL= \
    java -cp "$FULL_PROJECT/.javan/classes" com.acme.Main >"$TMP/expected.out"
else
  printf '%s' "$EXPECTED_STDOUT" >"$TMP/expected.out"
fi

printf '%s\n' 'int main(void) { return 0; }' >"$TMP/sanitizer-support.c"
set +e
# shellcheck disable=SC2086
"$CC" $SANITIZER_FLAGS \
  "$TMP/sanitizer-support.c" \
  -o "$TMP/sanitizer-support" \
  >"$TMP/support-cc.out" 2>"$TMP/support-cc.err"
support_compile_code=$?
set -e

if [ "$support_compile_code" -ne 0 ]; then
  if [ "$SANITIZER_REQUIRED" = "true" ]; then
    printf '%s\n' "sanitizer compiler flags unavailable for required run: $CC" >&2
    cat "$TMP/support-cc.err" >&2
    exit 1
  fi
  printf '%s\n' "skip - sanitizer compiler flags unavailable for $CC"
  cat "$TMP/support-cc.err"
  exit 0
fi

set +e
# shellcheck disable=SC2086
"$CC" $SANITIZER_FLAGS \
  -I "$FULL_PROJECT/.javan/generated" \
  "$FULL_PROJECT/.javan/generated/main.c" \
  "$FULL_PROJECT/.javan/generated/javan_runtime.c" \
  -o "$TMP/javan-sanitizer-probe" \
  >"$TMP/cc.out" 2>"$TMP/cc.err"
compile_code=$?
set -e

if [ "$compile_code" -ne 0 ]; then
  printf '%s\n' "sanitizer generated runtime compile failed" >&2
  cat "$TMP/cc.err"
  exit 1
fi

ASAN_OPTIONS=${ASAN_OPTIONS:-detect_leaks=1:halt_on_error=1}
UBSAN_OPTIONS=${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}
export ASAN_OPTIONS UBSAN_OPTIONS

run_probe() {
  : >"$TMP/run-shell.err"
  /bin/sh -c '"$1" >"$2" 2>"$3"' sh "$TMP/javan-sanitizer-probe" "$TMP/native.out" "$TMP/native.err" 2>"$TMP/run-shell.err"
  probe_code=$?
  if [ -s "$TMP/run-shell.err" ]; then
    cat "$TMP/run-shell.err" >>"$TMP/native.err"
  fi
  return "$probe_code"
}

set +e
run_probe
run_code=$?
set -e

if grep -F "detect_leaks is not supported" "$TMP/native.err" >/dev/null 2>&1; then
  ASAN_OPTIONS=detect_leaks=0:halt_on_error=1
  export ASAN_OPTIONS
  set +e
  run_probe
  run_code=$?
  set -e
  if [ "$run_code" -eq "$EXPECTED_EXIT" ]; then
    LEAK_SANITIZER_STATUS="leak detection unsupported on this platform"
  fi
fi

if [ "$run_code" -ne "$EXPECTED_EXIT" ]; then
  printf '%s\n' "sanitizer probe exited with $run_code, expected $EXPECTED_EXIT" >&2
  printf '%s\n' "--- native stdout" >&2
  cat "$TMP/native.out" >&2
  printf '%s\n' "--- sanitizer stderr" >&2
  cat "$TMP/native.err" >&2
  exit 1
fi

if grep -E "ERROR: (AddressSanitizer|LeakSanitizer|UndefinedBehaviorSanitizer)|runtime error:|SUMMARY: (AddressSanitizer|LeakSanitizer|UndefinedBehaviorSanitizer)" "$TMP/native.err" >/dev/null 2>&1; then
  printf '%s\n' "sanitizer reported a failure signature" >&2
  printf '%s\n' "--- sanitizer stderr" >&2
  cat "$TMP/native.err" >&2
  exit 1
fi

if ! cmp "$TMP/expected.out" "$TMP/native.out" >/dev/null; then
  printf '%s\n' "sanitizer native output differed from JVM" >&2
  printf '%s\n' "--- expected" >&2
  cat "$TMP/expected.out" >&2
  printf '%s\n' "--- native" >&2
  cat "$TMP/native.out" >&2
  printf '%s\n' "--- sanitizer stderr" >&2
  cat "$TMP/native.err" >&2
  exit 1
fi

if [ -n "$EXPECTED_STDERR_CONTAINS" ]; then
  if ! grep -F "$EXPECTED_STDERR_CONTAINS" "$TMP/native.err" >/dev/null 2>&1; then
    printf '%s\n' "sanitizer stderr did not contain expected text: $EXPECTED_STDERR_CONTAINS" >&2
    printf '%s\n' "--- sanitizer stderr" >&2
    cat "$TMP/native.err" >&2
    exit 1
  fi
elif [ -s "$TMP/native.err" ]; then
  printf '%s\n' "sanitizer reported stderr" >&2
  cat "$TMP/native.err" >&2
  exit 1
fi

if [ "${LEAK_SANITIZER_STATUS:-}" = "leak detection unsupported on this platform" ] && command -v leaks >/dev/null 2>&1; then
  set +e
  "$CC" \
    -I "$FULL_PROJECT/.javan/generated" \
    "$FULL_PROJECT/.javan/generated/main.c" \
    "$FULL_PROJECT/.javan/generated/javan_runtime.c" \
    -o "$TMP/javan-leak-probe" \
    >"$TMP/leak-cc.out" 2>"$TMP/leak-cc.err"
  leak_compile_code=$?
  set -e
  if [ "$leak_compile_code" -eq 0 ]; then
    set +e
    leaks --atExit -- "$TMP/javan-leak-probe" >"$TMP/leaks.out" 2>"$TMP/leaks.err"
    leaks_code=$?
    set -e
    if [ "$leaks_code" -ne 0 ] && ! grep -F "0 leaks for 0 total leaked bytes" "$TMP/leaks.out" >/dev/null 2>&1; then
      printf '%s\n' "macOS leaks probe failed with exit code $leaks_code" >&2
      printf '%s\n' "--- leaks stdout" >&2
      cat "$TMP/leaks.out" >&2
      printf '%s\n' "--- leaks stderr" >&2
      cat "$TMP/leaks.err" >&2
      exit "$leaks_code"
    fi
    LEAK_SANITIZER_STATUS="AddressSanitizer leak detection unsupported; macOS leaks passed"
  else
    if [ "$SANITIZER_REQUIRED" = "true" ]; then
      printf '%s\n' "macOS leaks fallback compile failed for required run" >&2
      cat "$TMP/leak-cc.err" >&2
      exit 1
    fi
    printf '%s\n' "warning - macOS leaks fallback compile failed" >&2
    cat "$TMP/leak-cc.err" >&2
  fi
fi

if [ -n "${LEAK_SANITIZER_STATUS:-}" ]; then
  printf '%s\n' "ok - sanitizer smoke passed for $PROJECT ($LEAK_SANITIZER_STATUS)"
else
  printf '%s\n' "ok - sanitizer smoke passed for $PROJECT"
fi
