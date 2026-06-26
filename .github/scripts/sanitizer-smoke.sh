#!/bin/sh
set -eu

ROOT=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
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
COUNTER_CHECK=${JAVAN_SANITIZER_COUNTER_CHECK:-false}
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

proof_counter() {
  if [ -f "${PROOF_COUNTERS:-}" ]; then
    sed -n "s/^$1=//p" "$PROOF_COUNTERS" | head -n 1
  fi
  return 0
}

configure_asan_options() {
  case "${ASAN_OPTIONS:-}" in
    *detect_leaks=0*)
      if [ "$SANITIZER_REQUIRED" = "true" ]; then
        printf '%s\n' "required sanitizer run cannot inherit ASAN_OPTIONS with detect_leaks=0" >&2
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
  reports=$FULL_PROJECT/.javan/reports
  mkdir -p "$reports"
  probe_status=pass
  if [ "$proof_status" = "skipped" ]; then
    probe_status=skipped
  fi
  probes='[{"name": "generated-app", "status": "'$probe_status'"}'
  if [ "$COUNTER_CHECK" = "true" ]; then
    probes=$probes', {"name": "heap-counters", "status": "'$probe_status'"}'
  fi
  probes=$probes']'
  cat >"$reports/sanitizer-proof.json" <<EOF
{
  "schemaVersion": 1,
  "status": $(json_string "$proof_status"),
  "kind": "app",
  "project": $(json_string "$PROJECT"),
  "cc": $(json_string "$CC"),
  "sanitizerFlags": $(json_string "$SANITIZER_FLAGS"),
  "sanitizerRequired": $SANITIZER_REQUIRED,
  "counterCheck": $COUNTER_CHECK,
  "compareJvm": $COMPARE_JVM,
  "expectedExit": $EXPECTED_EXIT,
  "actualExit": $actual_exit,
  "leakDetection": $(json_string "$leak_status"),
  "heapLimitBytes": $(json_number_or_null "${JAVAN_HEAP_LIMIT_BYTES:-}"),
  "gcStress": $(json_number_or_null "${JAVAN_GC_STRESS:-}"),
  "gcSafepointInterval": $(json_number_or_null "${JAVAN_GC_SAFEPOINT_INTERVAL:-}"),
  "actualLiveAllocations": $(json_number_or_null "$(proof_counter live_allocations)"),
  "actualLiveBytes": $(json_number_or_null "$(proof_counter live_bytes)"),
  "actualPeakLiveBytes": $(json_number_or_null "$(proof_counter peak_live_bytes)"),
  "actualTotalAllocations": $(json_number_or_null "$(proof_counter total_allocations)"),
  "actualGcCollections": $(json_number_or_null "$(proof_counter gc_collections)"),
  "actualGcCollectedAllocations": $(json_number_or_null "$(proof_counter gc_collected_allocations)"),
  "actualGcCollectedBytes": $(json_number_or_null "$(proof_counter gc_collected_bytes)"),
  "actualThreadObjects": $(json_number_or_null "$(proof_counter thread_objects)"),
  "actualStartedThreads": $(json_number_or_null "$(proof_counter started_threads)"),
  "actualCompletedThreads": $(json_number_or_null "$(proof_counter completed_threads)"),
  "actualActiveThreads": $(json_number_or_null "$(proof_counter active_threads)"),
  "actualThreadsWithTarget": $(json_number_or_null "$(proof_counter threads_with_target)"),
  "actualCurrentThreadRootPresent": $(json_number_or_null "$(proof_counter current_thread_root_present)"),
  "maxLiveAllocations": $(json_number_or_null "${JAVAN_SANITIZER_MAX_LIVE_ALLOCATIONS:-}"),
  "maxLiveBytes": $(json_number_or_null "${JAVAN_SANITIZER_MAX_LIVE_BYTES:-}"),
  "maxPeakLiveBytes": $(json_number_or_null "${JAVAN_SANITIZER_MAX_PEAK_LIVE_BYTES:-}"),
  "minTotalAllocations": $(json_number_or_null "${JAVAN_SANITIZER_MIN_TOTAL_ALLOCATIONS:-}"),
  "minGcCollections": $(json_number_or_null "${JAVAN_SANITIZER_MIN_GC_COLLECTIONS:-}"),
  "minGcCollectedAllocations": $(json_number_or_null "${JAVAN_SANITIZER_MIN_GC_COLLECTED_ALLOCATIONS:-}"),
  "minGcCollectedBytes": $(json_number_or_null "${JAVAN_SANITIZER_MIN_GC_COLLECTED_BYTES:-}"),
  "failureSignatures": $failure_signatures,
  "probes": $probes
}
EOF
  cat >"$reports/sanitizer-proof.md" <<EOF
# Sanitizer Proof

- status: \`$proof_status\`
- kind: \`app\`
- project: \`$PROJECT\`
- sanitizer required: \`$SANITIZER_REQUIRED\`
- counter check: \`$COUNTER_CHECK\`
- expected exit: \`$EXPECTED_EXIT\`
- actual exit: \`$actual_exit\`
- leak detection: \`$leak_status\`
- actual live allocations: \`$(proof_counter live_allocations)\`
- actual live bytes: \`$(proof_counter live_bytes)\`
- actual peak live bytes: \`$(proof_counter peak_live_bytes)\`
- actual GC collections: \`$(proof_counter gc_collections)\`
- actual thread objects: \`$(proof_counter thread_objects)\`
- actual started threads: \`$(proof_counter started_threads)\`
- actual completed threads: \`$(proof_counter completed_threads)\`
- actual active threads: \`$(proof_counter active_threads)\`
- actual threads with target: \`$(proof_counter threads_with_target)\`
- current thread root present: \`$(proof_counter current_thread_root_present)\`
- failure signatures: \`$failure_signatures\`

This report is written by the sanitizer smoke script after successful validation.
EOF
}

configure_asan_options

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
  write_sanitizer_proof "skipped" -1 "sanitizer compiler flags unavailable" "false"
  printf '%s\n' "skip - sanitizer compiler flags unavailable for $CC"
  cat "$TMP/support-cc.err"
  exit 0
fi

if [ "$COUNTER_CHECK" = "true" ]; then
  cat >"$TMP/counter-wrapper.c" <<'EOF'
#include "javan_runtime.h"

#include <limits.h>
#include <stdio.h>
#include <stdlib.h>

#define main javan_generated_main
#include "main.c"
#undef main

static unsigned long javan_sanitizer_counter_limit(const char* name, unsigned long fallback) {
    const char* value = getenv(name);
    if (value == NULL || value[0] == '\0') {
        return fallback;
    }
    char* end = NULL;
    unsigned long parsed = strtoul(value, &end, 10);
    return end == value ? fallback : parsed;
}

static int javan_sanitizer_expect_at_most(
    const char* label,
    unsigned long actual,
    unsigned long maximum
) {
    if (actual <= maximum) {
        return 0;
    }
    fprintf(
        stderr,
        "javan heap counter check failed: %s %lu > %lu\n",
        label,
        actual,
        maximum
    );
    return 1;
}

static int javan_sanitizer_expect_at_least(
    const char* label,
    unsigned long actual,
    unsigned long minimum
) {
    if (actual >= minimum) {
        return 0;
    }
    fprintf(
        stderr,
        "javan heap counter check failed: %s %lu < %lu\n",
        label,
        actual,
        minimum
    );
    return 1;
}

static void javan_sanitizer_write_proof_counters(void) {
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
    fprintf(out, "thread_objects=%lu\n", javan_heap_thread_objects());
    fprintf(out, "started_threads=%lu\n", javan_heap_started_threads());
    fprintf(out, "completed_threads=%lu\n", javan_heap_completed_threads());
    fprintf(out, "active_threads=%lu\n", javan_heap_active_threads());
    fprintf(out, "threads_with_target=%lu\n", javan_heap_threads_with_target());
    fprintf(out, "current_thread_root_present=%d\n", javan_heap_current_thread_root_present());
    fclose(out);
}

int main(int argc, char** argv) {
    int code = javan_generated_main(argc, argv);
    javan_gc_collect();
    javan_validate_heap_metadata();
    javan_sanitizer_write_proof_counters();
    if (code != 0) {
        return code;
    }
    if (javan_sanitizer_expect_at_most(
            "live allocations",
            javan_heap_live_allocations(),
            javan_sanitizer_counter_limit("JAVAN_SANITIZER_MAX_LIVE_ALLOCATIONS", ULONG_MAX)
        ) != 0) {
        return 1;
    }
    if (javan_sanitizer_expect_at_most(
            "live bytes",
            javan_heap_live_bytes(),
            javan_sanitizer_counter_limit("JAVAN_SANITIZER_MAX_LIVE_BYTES", ULONG_MAX)
        ) != 0) {
        return 1;
    }
    if (javan_sanitizer_expect_at_least(
            "total allocations",
            javan_heap_total_allocations(),
            javan_sanitizer_counter_limit("JAVAN_SANITIZER_MIN_TOTAL_ALLOCATIONS", 0)
        ) != 0) {
        return 1;
    }
    if (javan_sanitizer_expect_at_most(
            "peak live bytes",
            javan_heap_peak_live_bytes(),
            javan_sanitizer_counter_limit("JAVAN_SANITIZER_MAX_PEAK_LIVE_BYTES", ULONG_MAX)
        ) != 0) {
        return 1;
    }
    if (javan_sanitizer_expect_at_least(
            "gc collections",
            javan_heap_gc_collections(),
            javan_sanitizer_counter_limit("JAVAN_SANITIZER_MIN_GC_COLLECTIONS", 0)
        ) != 0) {
        return 1;
    }
    if (javan_sanitizer_expect_at_least(
            "gc collected allocations",
            javan_heap_gc_collected_allocations(),
            javan_sanitizer_counter_limit("JAVAN_SANITIZER_MIN_GC_COLLECTED_ALLOCATIONS", 0)
        ) != 0) {
        return 1;
    }
    if (javan_sanitizer_expect_at_least(
            "gc collected bytes",
            javan_heap_gc_collected_bytes(),
            javan_sanitizer_counter_limit("JAVAN_SANITIZER_MIN_GC_COLLECTED_BYTES", 0)
        ) != 0) {
        return 1;
    }
    return 0;
}
EOF
  set +e
  # shellcheck disable=SC2086
  "$CC" $SANITIZER_FLAGS \
    -I "$FULL_PROJECT/.javan/generated" \
    "$TMP/counter-wrapper.c" \
    "$FULL_PROJECT/.javan/generated/javan_runtime.c" \
    -o "$TMP/javan-sanitizer-probe" \
    >"$TMP/cc.out" 2>"$TMP/cc.err"
  compile_code=$?
  set -e
else
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
fi

if [ "$compile_code" -ne 0 ]; then
  printf '%s\n' "sanitizer generated runtime compile failed" >&2
  cat "$TMP/cc.err"
  exit 1
fi

UBSAN_OPTIONS=${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}
export ASAN_OPTIONS UBSAN_OPTIONS
PROOF_COUNTERS=$TMP/proof-counters.env
export PROOF_COUNTERS

run_probe() {
  : >"$TMP/run-shell.err"
  JAVAN_SANITIZER_PROOF_COUNTERS=$PROOF_COUNTERS \
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

if [ "${LEAK_SANITIZER_STATUS:-}" = "leak detection unsupported on this platform" ] && [ "$SANITIZER_REQUIRED" = "true" ]; then
  printf '%s\n' "required sanitizer run did not prove leak safety: LeakSanitizer unsupported and no fallback passed" >&2
  exit 1
fi

write_sanitizer_proof "pass" "$run_code" "${LEAK_SANITIZER_STATUS:-AddressSanitizer leak detection enabled}" "false"

if [ -n "${LEAK_SANITIZER_STATUS:-}" ]; then
  printf '%s\n' "ok - sanitizer smoke passed for $PROJECT ($LEAK_SANITIZER_STATUS)"
else
  printf '%s\n' "ok - sanitizer smoke passed for $PROJECT"
fi
