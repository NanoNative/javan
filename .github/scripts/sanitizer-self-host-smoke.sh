#!/bin/sh
set -eu

ROOT=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
TMP=${TMPDIR:-/tmp}/javan-self-host-sanitizer-$$
CC=${CC:-cc}
SANITIZER_FLAGS=${SANITIZER_FLAGS:-"-fsanitize=address,undefined -fno-omit-frame-pointer"}
SANITIZER_REQUIRED=${JAVAN_SANITIZER_REQUIRED:-false}
TARGET_PROJECT=${JAVAN_SELF_HOST_TARGET_PROJECT:-target}
TARGET_CLASSES=${JAVAN_SELF_HOST_CLASSES:-target/classes}
OUTPUT_NAME=${JAVAN_SELF_HOST_OUTPUT_NAME:-javan-selfhost-sanitizer-smoke}
PROBE_SCOPE=${JAVAN_SELF_HOST_PROBE_SCOPE:-full}
REUSE_GENERATED=${JAVAN_SELF_HOST_REUSE_GENERATED:-false}
REPORTS=$ROOT/$TARGET_PROJECT/.javan/reports

mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

json_string() {
  printf '"%s"' "$(json_escape "$1")"
}

json_number_or_null() {
  if [ -n "$1" ]; then
    printf '%s' "$1"
  else
    printf '%s' "null"
  fi
}

counter_value() {
  file=$1
  name=$2
  if [ -f "$file" ]; then
    sed -n "s/^$name=//p" "$file" | head -n 1
  fi
  return 0
}

required_counter_value() {
  file=$1
  name=$2
  value=$(counter_value "$file" "$name")
  case "$value" in
    ''|*[!0123456789]*)
      printf '%s\n' "self-host sanitizer counter check failed: missing numeric '$name' in $file" >&2
      if [ -f "$file" ]; then
        cat "$file" >&2
      else
        printf '%s\n' "counter file does not exist" >&2
      fi
      exit 1
      ;;
  esac
  printf '%s' "$value"
}

number_or_zero() {
  if [ -n "$1" ]; then
    printf '%s' "$1"
  else
    printf '%s' 0
  fi
}

max_number() {
  left=$(number_or_zero "$1")
  right=$(number_or_zero "$2")
  if [ "$right" -gt "$left" ]; then
    printf '%s' "$right"
  else
    printf '%s' "$left"
  fi
}

sum_number() {
  left=$(number_or_zero "$1")
  right=$(number_or_zero "$2")
  printf '%s' "$((left + right))"
}

assert_at_most() {
  label=$1
  actual=$(number_or_zero "$2")
  maximum=$3
  if [ -n "$maximum" ] && [ "$actual" -gt "$maximum" ]; then
    printf '%s\n' "self-host sanitizer counter check failed: $label $actual > $maximum" >&2
    exit 1
  fi
}

assert_at_least() {
  label=$1
  actual=$(number_or_zero "$2")
  minimum=$3
  if [ -n "$minimum" ] && [ "$actual" -lt "$minimum" ]; then
    printf '%s\n' "self-host sanitizer counter check failed: $label $actual < $minimum" >&2
    exit 1
  fi
}

configure_asan_options() {
  case "${ASAN_OPTIONS:-}" in
    *detect_leaks=0*)
      if [ "$SANITIZER_REQUIRED" = "true" ]; then
        printf '%s\n' "required self-host sanitizer run cannot inherit ASAN_OPTIONS with detect_leaks=0" >&2
        exit 1
      fi
      ;;
  esac
  if [ -n "${ASAN_OPTIONS:-}" ]; then
    case "$ASAN_OPTIONS" in
      *detect_leaks=*) ;;
      *) ASAN_OPTIONS=detect_leaks=1:$ASAN_OPTIONS ;;
    esac
  else
    ASAN_OPTIONS=detect_leaks=1:halt_on_error=1
  fi
}

write_sanitizer_proof() {
  proof_status=$1
  actual_exit=$2
  leak_status=$3
  failure_signatures=$4
  mkdir -p "$REPORTS"
  probe_status=pass
  if [ "$proof_status" = "skipped" ]; then
    probe_status=skipped
  fi
  self_check_status=$probe_status
  self_report_status=$probe_status
  if [ "$PROBE_SCOPE" = "package-smoke" ]; then
    self_check_status=skipped
    self_report_status=skipped
  fi
  cat >"$REPORTS/sanitizer-proof.json" <<EOF
{
  "schemaVersion": 1,
  "status": $(json_string "$proof_status"),
  "kind": "self-host",
  "probeScope": $(json_string "$PROBE_SCOPE"),
  "project": $(json_string "$TARGET_CLASSES"),
  "cc": $(json_string "$CC"),
  "sanitizerFlags": $(json_string "$SANITIZER_FLAGS"),
  "sanitizerRequired": $SANITIZER_REQUIRED,
  "counterCheck": true,
  "expectedExit": 0,
  "actualExit": $actual_exit,
  "leakDetection": $(json_string "$leak_status"),
  "actualLiveAllocations": $(json_number_or_null "$AGG_LIVE_ALLOCATIONS"),
  "actualLiveBytes": $(json_number_or_null "$AGG_LIVE_BYTES"),
  "actualPeakLiveBytes": $(json_number_or_null "$AGG_PEAK_LIVE_BYTES"),
  "actualTotalAllocations": $(json_number_or_null "$AGG_TOTAL_ALLOCATIONS"),
  "actualGcCollections": $(json_number_or_null "$AGG_GC_COLLECTIONS"),
  "actualGcCollectedAllocations": $(json_number_or_null "$AGG_GC_COLLECTED_ALLOCATIONS"),
  "actualGcCollectedBytes": $(json_number_or_null "$AGG_GC_COLLECTED_BYTES"),
  "actualRootFrameDepth": $(json_number_or_null "$AGG_ROOT_FRAME_DEPTH"),
  "actualFrameRootCount": $(json_number_or_null "$AGG_FRAME_ROOT_COUNT"),
  "maxLiveAllocations": $(json_number_or_null "${JAVAN_SANITIZER_SELF_HOST_MAX_LIVE_ALLOCATIONS:-0}"),
  "maxLiveBytes": $(json_number_or_null "${JAVAN_SANITIZER_SELF_HOST_MAX_LIVE_BYTES:-0}"),
  "maxPeakLiveBytes": $(json_number_or_null "${JAVAN_SANITIZER_SELF_HOST_MAX_PEAK_LIVE_BYTES:-}"),
  "maxRootFrameDepth": $(json_number_or_null "${JAVAN_SANITIZER_SELF_HOST_MAX_ROOT_FRAME_DEPTH:-0}"),
  "maxFrameRootCount": $(json_number_or_null "${JAVAN_SANITIZER_SELF_HOST_MAX_FRAME_ROOT_COUNT:-0}"),
  "minTotalAllocations": $(json_number_or_null "${JAVAN_SANITIZER_SELF_HOST_MIN_TOTAL_ALLOCATIONS:-1}"),
  "minGcCollections": $(json_number_or_null "${JAVAN_SANITIZER_SELF_HOST_MIN_GC_COLLECTIONS:-1}"),
  "minGcCollectedAllocations": $(json_number_or_null "${JAVAN_SANITIZER_SELF_HOST_MIN_GC_COLLECTED_ALLOCATIONS:-0}"),
  "minGcCollectedBytes": $(json_number_or_null "${JAVAN_SANITIZER_SELF_HOST_MIN_GC_COLLECTED_BYTES:-0}"),
  "failureSignatures": $failure_signatures,
  "probes": [
    {"name": "version", "status": "$probe_status"},
    {"name": "self-check", "status": "$self_check_status"},
    {"name": "self-report", "status": "$self_report_status"},
    {"name": "tiny-check", "status": "$probe_status"},
    {"name": "tiny-build", "status": "$probe_status"}
  ]
}
EOF
  cat >"$REPORTS/sanitizer-proof.md" <<EOF
# Sanitizer Proof

- status: \`$proof_status\`
- kind: \`self-host\`
- probe scope: \`$PROBE_SCOPE\`
- project: \`$TARGET_CLASSES\`
- sanitizer required: \`$SANITIZER_REQUIRED\`
- counter check: \`true\`
- expected exit: \`0\`
- actual exit: \`$actual_exit\`
- leak detection: \`$leak_status\`
- actual live allocations: \`$AGG_LIVE_ALLOCATIONS\`
- actual live bytes: \`$AGG_LIVE_BYTES\`
- actual peak live bytes: \`$AGG_PEAK_LIVE_BYTES\`
- actual total allocations: \`$AGG_TOTAL_ALLOCATIONS\`
- actual GC collections: \`$AGG_GC_COLLECTIONS\`
- actual root frame depth: \`$AGG_ROOT_FRAME_DEPTH\`
- actual frame root count: \`$AGG_FRAME_ROOT_COUNT\`
- failure signatures: \`$failure_signatures\`

This report is written after the generated native Javan binary runs version,
self-check, self-report, and a tiny build/check loop under sanitizer instrumentation.
EOF
}

resolve_javan_bin() {
  if [ -n "${JAVAN_BIN:-}" ]; then
    if [ ! -x "$JAVAN_BIN" ]; then
      printf '%s\n' "Missing executable javan binary: $JAVAN_BIN" >&2
      exit 2
    fi
    printf '%s' "$JAVAN_BIN"
    return
  fi
  if [ -f "$ROOT/target/classes/javan/Main.class" ]; then
    wrapper=$TMP/javan
    {
      printf '%s\n' '#!/bin/sh'
      printf '%s\n' "exec java -cp '$ROOT/target/classes' javan.Main \"\$@\""
    } >"$wrapper"
    chmod +x "$wrapper"
    printf '%s' "$wrapper"
    return
  fi
  printf '%s\n' "Missing javan runtime: build target/classes or set JAVAN_BIN=/path/to/javan." >&2
  exit 2
}

reset_aggregate() {
  AGG_LIVE_ALLOCATIONS=0
  AGG_LIVE_BYTES=0
  AGG_PEAK_LIVE_BYTES=0
  AGG_TOTAL_ALLOCATIONS=0
  AGG_GC_COLLECTIONS=0
  AGG_GC_COLLECTED_ALLOCATIONS=0
  AGG_GC_COLLECTED_BYTES=0
  AGG_ROOT_FRAME_DEPTH=0
  AGG_FRAME_ROOT_COUNT=0
}

record_counters() {
  counters=$1
  live_allocations=$(required_counter_value "$counters" live_allocations)
  live_bytes=$(required_counter_value "$counters" live_bytes)
  peak_live_bytes=$(required_counter_value "$counters" peak_live_bytes)
  total_allocations=$(required_counter_value "$counters" total_allocations)
  gc_collections=$(required_counter_value "$counters" gc_collections)
  gc_collected_allocations=$(required_counter_value "$counters" gc_collected_allocations)
  gc_collected_bytes=$(required_counter_value "$counters" gc_collected_bytes)
  root_frame_depth=$(required_counter_value "$counters" root_frame_depth)
  frame_root_count=$(required_counter_value "$counters" frame_root_count)

  AGG_LIVE_ALLOCATIONS=$(max_number "$AGG_LIVE_ALLOCATIONS" "$live_allocations")
  AGG_LIVE_BYTES=$(max_number "$AGG_LIVE_BYTES" "$live_bytes")
  AGG_PEAK_LIVE_BYTES=$(max_number "$AGG_PEAK_LIVE_BYTES" "$peak_live_bytes")
  AGG_TOTAL_ALLOCATIONS=$(sum_number "$AGG_TOTAL_ALLOCATIONS" "$total_allocations")
  AGG_GC_COLLECTIONS=$(sum_number "$AGG_GC_COLLECTIONS" "$gc_collections")
  AGG_GC_COLLECTED_ALLOCATIONS=$(sum_number "$AGG_GC_COLLECTED_ALLOCATIONS" "$gc_collected_allocations")
  AGG_GC_COLLECTED_BYTES=$(sum_number "$AGG_GC_COLLECTED_BYTES" "$gc_collected_bytes")
  AGG_ROOT_FRAME_DEPTH=$(max_number "$AGG_ROOT_FRAME_DEPTH" "$root_frame_depth")
  AGG_FRAME_ROOT_COUNT=$(max_number "$AGG_FRAME_ROOT_COUNT" "$frame_root_count")
}

make_tiny_project() {
  TINY_PROJECT=$TMP/tiny
  mkdir -p "$TINY_PROJECT/src/main/java/com/acme"
  cat >"$TINY_PROJECT/src/main/java/com/acme/Main.java" <<'EOF'
package com.acme;

public final class Main {
    public static void main(final String[] args) {
        final int left = 21;
        final int right = 21;
        System.out.println(left + right);
    }
}
EOF
}

run_probe() {
  name=$1
  shift
  printf '%s\n' "self-host probe: $name"
  counters=$TMP/counters-$name.env
  out=$TMP/$name.out
  err=$TMP/$name.err
  rm -f "$counters"
  set +e
  JAVAN_SANITIZER_PROOF_COUNTERS=$counters "$PROBE" "$@" >"$out" 2>"$err"
  code=$?
  cat "$err" >>"$TMP/all.err"
  if [ "$code" -ne 0 ]; then
    if grep -F "detect_leaks is not supported" "$err" >/dev/null 2>&1; then
      return 88
    fi
    printf '%s\n' "self-host sanitizer probe '$name' exited with $code" >&2
    printf '%s\n' "--- stdout" >&2
    cat "$out" >&2
    printf '%s\n' "--- stderr" >&2
    cat "$err" >&2
    return "$code"
  fi
  set -e
  record_counters "$counters"
}

run_all_probes() {
  : >"$TMP/all.err"
  reset_aggregate
  run_probe version --version || return $?
  case "$PROBE_SCOPE" in
    full)
      run_probe self-check check "$TARGET_CLASSES" --main javan.Main || return $?
      run_probe self-report report "$TARGET_PROJECT" || return $?
      ;;
    package-smoke) ;;
    *)
      printf '%s\n' "Unsupported self-host sanitizer probe scope: $PROBE_SCOPE" >&2
      return 2
      ;;
  esac
  run_probe tiny-check check "$TINY_PROJECT" || return $?
  run_probe tiny-build build "$TINY_PROJECT" --output selfhost-tiny || return $?
}

run_leaks_probe() {
  name=$1
  shift
  out=$TMP/leaks-$name.out
  err=$TMP/leaks-$name.err
  set +e
  leaks --atExit -- "${LEAK_PROBE:-$PROBE}" "$@" >"$out" 2>"$err"
  code=$?
  set -e
  if [ "$code" -ne 0 ] && ! grep -F "0 leaks for 0 total leaked bytes" "$out" >/dev/null 2>&1; then
    printf '%s\n' "macOS leaks self-host probe '$name' failed with $code" >&2
    printf '%s\n' "--- leaks stdout" >&2
    cat "$out" >&2
    printf '%s\n' "--- leaks stderr" >&2
    cat "$err" >&2
    exit "$code"
  fi
}

run_all_leaks_probes() {
  run_leaks_probe version --version
  case "$PROBE_SCOPE" in
    full)
      run_leaks_probe self-check check "$TARGET_CLASSES" --main javan.Main
      run_leaks_probe self-report report "$TARGET_PROJECT"
      ;;
    package-smoke) ;;
    *)
      printf '%s\n' "Unsupported self-host sanitizer probe scope: $PROBE_SCOPE" >&2
      exit 2
      ;;
  esac
  run_leaks_probe tiny-check check "$TINY_PROJECT"
  run_leaks_probe tiny-build build "$TINY_PROJECT" --output selfhost-tiny
}

configure_asan_options

JAVAN=$(resolve_javan_bin)
cd "$ROOT"

printf '%s\n' 'int main(void) { return 0; }' >"$TMP/sanitizer-support.c"
set +e
# shellcheck disable=SC2086
"$CC" $SANITIZER_FLAGS "$TMP/sanitizer-support.c" -o "$TMP/sanitizer-support" >"$TMP/support-cc.out" 2>"$TMP/support-cc.err"
support_compile_code=$?
set -e

reset_aggregate
if [ "$support_compile_code" -ne 0 ]; then
  if [ "$SANITIZER_REQUIRED" = "true" ]; then
    printf '%s\n' "sanitizer compiler flags unavailable for required self-host run: $CC" >&2
    cat "$TMP/support-cc.err" >&2
    exit 1
  fi
  write_sanitizer_proof "skipped" -1 "sanitizer compiler flags unavailable" "false"
  printf '%s\n' "skip - sanitizer compiler flags unavailable for self-host: $CC"
  exit 0
fi

GENERATED=$ROOT/$TARGET_PROJECT/.javan/generated
if [ "$REUSE_GENERATED" = "true" ]; then
  printf '%s\n' "Reusing generated self-host output from $GENERATED"
else
  printf '%s\n' "Building self-host generated output for $TARGET_CLASSES"
  "$JAVAN" build "$TARGET_CLASSES" --main javan.Main --output "$OUTPUT_NAME" >/dev/null
fi
if [ ! -f "$GENERATED/main.c" ] || [ ! -f "$GENERATED/javan_runtime.c" ]; then
  printf '%s\n' "Missing generated self-host C output in $GENERATED" >&2
  exit 1
fi

cat >"$TMP/self-host-counter-wrapper.c" <<'EOF'
#include "javan_runtime.h"

#include <limits.h>
#include <stdio.h>
#include <stdlib.h>

#define main javan_generated_main
#include "main.c"
#undef main

static unsigned long javan_self_host_counter_limit(const char* name, unsigned long fallback) {
    const char* value = getenv(name);
    if (value == NULL || value[0] == '\0') {
        return fallback;
    }
    char* end = NULL;
    unsigned long parsed = strtoul(value, &end, 10);
    return end == value ? fallback : parsed;
}

static int javan_self_host_expect_at_most(
    const char* label,
    unsigned long actual,
    unsigned long maximum
) {
    if (actual <= maximum) {
        return 0;
    }
    fprintf(stderr, "javan self-host heap counter check failed: %s %lu > %lu\n", label, actual, maximum);
    return 1;
}

static void javan_self_host_write_proof_counters(void) {
    const char* path = getenv("JAVAN_SANITIZER_PROOF_COUNTERS");
    if (path == NULL || path[0] == '\0') {
        return;
    }
    FILE* out = fopen(path, "w");
    if (out == NULL) {
        return;
    }
    fprintf(out, "live_allocations=%lu\n", javan_heap_live_allocations());
    fprintf(out, "live_bytes=%lu\n", javan_heap_live_bytes());
    fprintf(out, "peak_live_bytes=%lu\n", javan_heap_peak_live_bytes());
    fprintf(out, "total_allocations=%lu\n", javan_heap_total_allocations());
    fprintf(out, "gc_collections=%lu\n", javan_heap_gc_collections());
    fprintf(out, "gc_collected_allocations=%lu\n", javan_heap_gc_collected_allocations());
    fprintf(out, "gc_collected_bytes=%lu\n", javan_heap_gc_collected_bytes());
    fprintf(out, "root_frame_depth=%d\n", javan_heap_root_frame_depth());
    fprintf(out, "frame_root_count=%d\n", javan_heap_frame_root_count());
    fclose(out);
}

static void javan_self_host_write_exit_proof_counters(void) {
    javan_gc_collect();
    javan_validate_heap_metadata();
    javan_self_host_write_proof_counters();
}

int main(int argc, char** argv) {
    if (atexit(javan_self_host_write_exit_proof_counters) != 0) {
        fputs("unable to register self-host sanitizer proof writer\n", stderr);
        return 1;
    }
    int code = javan_generated_main(argc, argv);
    javan_gc_collect();
    javan_validate_heap_metadata();
    javan_self_host_write_proof_counters();
    if (code != 0) {
        return code;
    }
    if (javan_self_host_expect_at_most(
            "live allocations",
            javan_heap_live_allocations(),
            javan_self_host_counter_limit("JAVAN_SANITIZER_SELF_HOST_MAX_LIVE_ALLOCATIONS", 0)
        ) != 0) {
        return 1;
    }
    if (javan_self_host_expect_at_most(
            "live bytes",
            javan_heap_live_bytes(),
            javan_self_host_counter_limit("JAVAN_SANITIZER_SELF_HOST_MAX_LIVE_BYTES", 0)
        ) != 0) {
        return 1;
    }
    if (javan_self_host_expect_at_most(
            "root frame depth",
            (unsigned long) javan_heap_root_frame_depth(),
            javan_self_host_counter_limit("JAVAN_SANITIZER_SELF_HOST_MAX_ROOT_FRAME_DEPTH", 0)
        ) != 0) {
        return 1;
    }
    if (javan_self_host_expect_at_most(
            "frame root count",
            (unsigned long) javan_heap_frame_root_count(),
            javan_self_host_counter_limit("JAVAN_SANITIZER_SELF_HOST_MAX_FRAME_ROOT_COUNT", 0)
        ) != 0) {
        return 1;
    }
    return 0;
}
EOF

set +e
# shellcheck disable=SC2086
"$CC" $SANITIZER_FLAGS \
  -I "$GENERATED" \
  "$TMP/self-host-counter-wrapper.c" \
  "$GENERATED/javan_runtime.c" \
  -o "$TMP/javan-self-host-sanitizer-probe" \
  >"$TMP/cc.out" 2>"$TMP/cc.err"
compile_code=$?
set -e

if [ "$compile_code" -ne 0 ]; then
  printf '%s\n' "sanitizer self-host runtime compile failed" >&2
  cat "$TMP/cc.err" >&2
  exit 1
fi

PROBE=$TMP/javan-self-host-sanitizer-probe
make_tiny_project

UBSAN_OPTIONS=${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}
export ASAN_OPTIONS UBSAN_OPTIONS

set +e
run_all_probes
probe_run_code=$?
set -e
LEAK_STATUS="AddressSanitizer leak detection enabled"
if [ "$probe_run_code" -eq 88 ] || grep -F "detect_leaks is not supported" "$TMP/all.err" >/dev/null 2>&1; then
  ASAN_OPTIONS=detect_leaks=0:halt_on_error=1
  export ASAN_OPTIONS
  set +e
  run_all_probes
  probe_run_code=$?
  set -e
  if [ "$probe_run_code" -ne 0 ]; then
    exit "$probe_run_code"
  fi
  LEAK_STATUS="leak detection unsupported on this platform"
  if command -v leaks >/dev/null 2>&1; then
    set +e
    "$CC" \
      -I "$GENERATED" \
      "$TMP/self-host-counter-wrapper.c" \
      "$GENERATED/javan_runtime.c" \
      -o "$TMP/javan-self-host-leak-probe" \
      >"$TMP/leak-cc.out" 2>"$TMP/leak-cc.err"
    leak_compile_code=$?
    set -e
    if [ "$leak_compile_code" -ne 0 ]; then
      if [ "$SANITIZER_REQUIRED" = "true" ]; then
        printf '%s\n' "macOS self-host leaks fallback compile failed for required run" >&2
        cat "$TMP/leak-cc.err" >&2
        exit 1
      fi
      printf '%s\n' "warning - macOS self-host leaks fallback compile failed" >&2
      cat "$TMP/leak-cc.err" >&2
    else
      LEAK_PROBE=$TMP/javan-self-host-leak-probe
      export LEAK_PROBE
      run_all_leaks_probes
      LEAK_STATUS="AddressSanitizer leak detection unsupported; macOS leaks passed"
    fi
  elif [ "$SANITIZER_REQUIRED" = "true" ]; then
    printf '%s\n' "required self-host sanitizer run did not prove leak safety: LeakSanitizer unsupported and no fallback passed" >&2
    exit 1
  fi
fi
if [ "$probe_run_code" -ne 0 ]; then
  exit "$probe_run_code"
fi

if grep -E "ERROR: (AddressSanitizer|LeakSanitizer|UndefinedBehaviorSanitizer)|runtime error:|SUMMARY: (AddressSanitizer|LeakSanitizer|UndefinedBehaviorSanitizer)" "$TMP/all.err" >/dev/null 2>&1; then
  printf '%s\n' "self-host sanitizer reported a failure signature" >&2
  cat "$TMP/all.err" >&2
  exit 1
fi

assert_at_most "live allocations" "$AGG_LIVE_ALLOCATIONS" "${JAVAN_SANITIZER_SELF_HOST_MAX_LIVE_ALLOCATIONS:-0}"
assert_at_most "live bytes" "$AGG_LIVE_BYTES" "${JAVAN_SANITIZER_SELF_HOST_MAX_LIVE_BYTES:-0}"
assert_at_most "peak live bytes" "$AGG_PEAK_LIVE_BYTES" "${JAVAN_SANITIZER_SELF_HOST_MAX_PEAK_LIVE_BYTES:-}"
assert_at_most "root frame depth" "$AGG_ROOT_FRAME_DEPTH" "${JAVAN_SANITIZER_SELF_HOST_MAX_ROOT_FRAME_DEPTH:-0}"
assert_at_most "frame root count" "$AGG_FRAME_ROOT_COUNT" "${JAVAN_SANITIZER_SELF_HOST_MAX_FRAME_ROOT_COUNT:-0}"
assert_at_least "total allocations" "$AGG_TOTAL_ALLOCATIONS" "${JAVAN_SANITIZER_SELF_HOST_MIN_TOTAL_ALLOCATIONS:-1}"
assert_at_least "GC collections" "$AGG_GC_COLLECTIONS" "${JAVAN_SANITIZER_SELF_HOST_MIN_GC_COLLECTIONS:-1}"
assert_at_least "GC collected allocations" "$AGG_GC_COLLECTED_ALLOCATIONS" "${JAVAN_SANITIZER_SELF_HOST_MIN_GC_COLLECTED_ALLOCATIONS:-0}"
assert_at_least "GC collected bytes" "$AGG_GC_COLLECTED_BYTES" "${JAVAN_SANITIZER_SELF_HOST_MIN_GC_COLLECTED_BYTES:-0}"

write_sanitizer_proof "pass" 0 "$LEAK_STATUS" "false"
"$JAVAN" report "$TARGET_PROJECT" >/dev/null
printf '%s\n' "ok - self-host sanitizer smoke passed for $TARGET_CLASSES ($LEAK_STATUS)"
