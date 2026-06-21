#!/bin/sh
set -eu

ROOT=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
TMP=${TMPDIR:-/tmp}/javan-sanitizer-library-$$
PROJECT=${1:-src/test/resources/projects/acceptance/native-library}
FULL_PROJECT=$ROOT/$PROJECT
LIBRARY_NAME=$(basename "$FULL_PROJECT")
SAFE_PACKAGE=$(printf '%s' "$LIBRARY_NAME" | tr -c 'A-Za-z0-9_' '_')
case "$SAFE_PACKAGE" in
  [0-9]*) SAFE_PACKAGE=javan_$SAFE_PACKAGE ;;
esac
case "$(uname -s 2>/dev/null || printf unknown)" in
  Darwin*) SHARED_ARTIFACT=$FULL_PROJECT/.javan/dist/lib$LIBRARY_NAME.dylib ;;
  MINGW*|MSYS*|CYGWIN*) SHARED_ARTIFACT=$FULL_PROJECT/.javan/dist/$LIBRARY_NAME.dll ;;
  *) SHARED_ARTIFACT=$FULL_PROJECT/.javan/dist/lib$LIBRARY_NAME.so ;;
esac
CC=${CC:-cc}
SANITIZER_FLAGS=${SANITIZER_FLAGS:-"-fsanitize=address,undefined -fno-omit-frame-pointer"}
SANITIZER_REQUIRED=${JAVAN_SANITIZER_REQUIRED:-false}
BINDING_TOOLCHAINS_REQUIRED=$SANITIZER_REQUIRED
PYTHON_BINDING_STATUS=not-run
RUST_BINDING_STATUS=not-run
GO_BINDING_STATUS=not-run
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
        printf '%s\n' "required sanitizer library run cannot inherit ASAN_OPTIONS with detect_leaks=0" >&2
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
    PYTHON_BINDING_STATUS=skipped
    RUST_BINDING_STATUS=skipped
    GO_BINDING_STATUS=skipped
  fi
  cat >"$reports/sanitizer-proof.json" <<EOF
{
  "schemaVersion": 1,
  "status": $(json_string "$proof_status"),
  "kind": "library",
  "project": $(json_string "$PROJECT"),
  "cc": $(json_string "$CC"),
  "sanitizerFlags": $(json_string "$SANITIZER_FLAGS"),
  "sanitizerRequired": $SANITIZER_REQUIRED,
  "counterCheck": true,
  "bindingToolchainsRequired": $BINDING_TOOLCHAINS_REQUIRED,
  "expectedExit": 0,
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
  "actualRootFrameDepth": $(json_number_or_null "$(proof_counter root_frame_depth)"),
  "actualFrameRootCount": $(json_number_or_null "$(proof_counter frame_root_count)"),
  "maxLiveAllocations": $(json_number_or_null "${JAVAN_SANITIZER_LIBRARY_MAX_LIVE_ALLOCATIONS:-0}"),
  "maxLiveBytes": $(json_number_or_null "${JAVAN_SANITIZER_LIBRARY_MAX_LIVE_BYTES:-0}"),
  "maxPeakLiveBytes": $(json_number_or_null "${JAVAN_SANITIZER_LIBRARY_MAX_PEAK_LIVE_BYTES:-2048}"),
  "maxRootFrameDepth": $(json_number_or_null "${JAVAN_SANITIZER_LIBRARY_MAX_ROOT_FRAME_DEPTH:-0}"),
  "maxFrameRootCount": $(json_number_or_null "${JAVAN_SANITIZER_LIBRARY_MAX_FRAME_ROOT_COUNT:-0}"),
  "minTotalAllocations": $(json_number_or_null "${JAVAN_SANITIZER_LIBRARY_MIN_TOTAL_ALLOCATIONS:-2000}"),
  "minGcCollections": $(json_number_or_null "${JAVAN_SANITIZER_LIBRARY_MIN_GC_COLLECTIONS:-1}"),
  "minGcCollectedAllocations": $(json_number_or_null "${JAVAN_SANITIZER_LIBRARY_MIN_GC_COLLECTED_ALLOCATIONS:-1000}"),
  "failureSignatures": $failure_signatures,
  "probes": [
    {"name": "library-output", "status": "$probe_status"},
    {"name": "heap-counters", "status": "$probe_status"},
    {"name": "byte-input-panic-cleanup", "status": "$probe_status"},
    {"name": "python-binding-ownership", "status": $(json_string "$PYTHON_BINDING_STATUS")},
    {"name": "rust-binding-ownership", "status": $(json_string "$RUST_BINDING_STATUS")},
    {"name": "go-binding-ownership", "status": $(json_string "$GO_BINDING_STATUS")}
  ]
}
EOF
  cat >"$reports/sanitizer-proof.md" <<EOF
# Sanitizer Proof

- status: \`$proof_status\`
- kind: \`library\`
- project: \`$PROJECT\`
- sanitizer required: \`$SANITIZER_REQUIRED\`
- binding toolchains required: \`$BINDING_TOOLCHAINS_REQUIRED\`
- expected exit: \`0\`
- actual exit: \`$actual_exit\`
- leak detection: \`$leak_status\`
- actual live allocations: \`$(proof_counter live_allocations)\`
- actual live bytes: \`$(proof_counter live_bytes)\`
- actual root frame depth: \`$(proof_counter root_frame_depth)\`
- actual frame root count: \`$(proof_counter frame_root_count)\`
- failure signatures: \`$failure_signatures\`
- python binding: \`$PYTHON_BINDING_STATUS\`
- rust binding: \`$RUST_BINDING_STATUS\`
- go binding: \`$GO_BINDING_STATUS\`

This report is written by the sanitizer library smoke script after successful validation.
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
  "$JAVAN_BIN" build "$FULL_PROJECT" \
    --library \
    --format both \
    --export com.acme.Math.add \
    --export com.acme.Text.greet \
    --export com.acme.Bytes.duplicate \
    --export com.acme.Bytes.merge \
    --export com.acme.Store.rememberString \
    --export com.acme.Store.lastString \
    --export com.acme.Store.rememberBytes \
    --export com.acme.Store.lastBytes \
    --export com.acme.Store.clear \
    --export com.acme.Failures.failInt \
    --bindings c,rust,go,python >/dev/null

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

set +e
# shellcheck disable=SC2086
"$CC" $SANITIZER_FLAGS \
  -I "$FULL_PROJECT/.javan/generated" \
  "$FULL_PROJECT/caller.c" \
  "$FULL_PROJECT/.javan/generated/library.c" \
  "$FULL_PROJECT/.javan/generated/javan_runtime.c" \
  -o "$TMP/javan-library-sanitizer-probe" \
  >"$TMP/cc.out" 2>"$TMP/cc.err"
compile_code=$?
set -e

if [ "$compile_code" -ne 0 ]; then
  printf '%s\n' "sanitizer generated library runtime compile failed" >&2
  cat "$TMP/cc.err"
  exit 1
fi

UBSAN_OPTIONS=${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}
JAVAN_HEAP_LIMIT_BYTES=${JAVAN_HEAP_LIMIT_BYTES:-2048}
JAVAN_GC_STRESS=${JAVAN_GC_STRESS:-1}
JAVAN_GC_SAFEPOINT_INTERVAL=${JAVAN_GC_SAFEPOINT_INTERVAL:-1}
export ASAN_OPTIONS UBSAN_OPTIONS JAVAN_HEAP_LIMIT_BYTES JAVAN_GC_STRESS JAVAN_GC_SAFEPOINT_INTERVAL

run_probe() {
  : >"$TMP/run-shell.err"
  /bin/sh -c '"$1" >"$2" 2>"$3"' sh "$TMP/javan-library-sanitizer-probe" "$TMP/native.out" "$TMP/native.err" 2>"$TMP/run-shell.err"
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
  if [ "$run_code" -eq 0 ]; then
    LEAK_SANITIZER_STATUS="leak detection unsupported on this platform"
  fi
fi

if [ "$run_code" -ne 0 ]; then
  printf '%s\n' "sanitizer library probe exited with $run_code" >&2
  printf '%s\n' "--- native stdout" >&2
  cat "$TMP/native.out" >&2
  printf '%s\n' "--- sanitizer stderr" >&2
  cat "$TMP/native.err" >&2
  exit 1
fi

{
  printf '%s\n' '10'
  printf '%s\n' 'try-add:1:10'
  printf '%s\n' 'Hi Yuna'
  printf '%s\n' 'null-greeting:Hi null'
  printf '%s\n' '4:3:4'
  printf '%s\n' 'empty-bytes:0:clean'
  printf '%s\n' 'merged-bytes:4:3:1'
  printf '%s\n' 'retained:Yuna'
  printf '%s\n' 'retained-bytes:3:1:4'
  printf '%s\n' 'last-error:0:negative array length'
  printf '%s\n' 'result-error:negative array length'
  printf '%s\n' 'byte-error:negative byte array length'
  printf '%s\n' 'byte-result-error:negative byte array length'
} >"$TMP/expected.out"

if ! cmp "$TMP/expected.out" "$TMP/native.out" >/dev/null; then
  printf '%s\n' "sanitizer library output differed" >&2
  printf '%s\n' "--- expected" >&2
  cat "$TMP/expected.out" >&2
  printf '%s\n' "--- native" >&2
  cat "$TMP/native.out" >&2
  printf '%s\n' "--- sanitizer stderr" >&2
  cat "$TMP/native.err" >&2
  exit 1
fi

if [ -s "$TMP/native.err" ]; then
  printf '%s\n' "sanitizer library reported stderr" >&2
  cat "$TMP/native.err" >&2
  exit 1
fi

cat >"$TMP/library-counter-probe.c" <<'EOF'
#include "javan_runtime.h"

#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int javan_export_com_acme_Math_add_int_int(int arg0, int arg1);
JavanResult javan_try_com_acme_Math_add_int_int(int arg0, int arg1, int* out);
char* javan_export_com_acme_Text_greet_string(const char* arg0);
JavanByteArray javan_export_com_acme_Bytes_duplicate_bytes(JavanByteArray arg0);
JavanByteArray javan_export_com_acme_Bytes_merge_bytes_bytes(JavanByteArray arg0, JavanByteArray arg1);
void javan_export_com_acme_Store_rememberString_string(const char* arg0);
char* javan_export_com_acme_Store_lastString_void(void);
void javan_export_com_acme_Store_rememberBytes_bytes(JavanByteArray arg0);
JavanByteArray javan_export_com_acme_Store_lastBytes_void(void);
void javan_export_com_acme_Store_clear_void(void);
int javan_export_com_acme_Failures_failInt_void(void);
JavanResult javan_try_com_acme_Failures_failInt_void(int* out);

static unsigned long javan_library_counter_limit(const char* name, unsigned long fallback) {
    const char* value = getenv(name);
    if (value == NULL || value[0] == '\0') {
        return fallback;
    }
    char* end = NULL;
    unsigned long parsed = strtoul(value, &end, 10);
    return end == value ? fallback : parsed;
}

static int javan_library_expect_at_most(
    const char* label,
    unsigned long actual,
    unsigned long maximum
) {
    if (actual <= maximum) {
        return 0;
    }
    fprintf(
        stderr,
        "javan library heap counter check failed: %s %lu > %lu\n",
        label,
        actual,
        maximum
    );
    return 1;
}

static int javan_library_expect_at_least(
    const char* label,
    unsigned long actual,
    unsigned long minimum
) {
    if (actual >= minimum) {
        return 0;
    }
    fprintf(
        stderr,
        "javan library heap counter check failed: %s %lu < %lu\n",
        label,
        actual,
        minimum
    );
    return 1;
}

static void javan_library_write_proof_counters(void) {
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
    fprintf(out, "root_frame_depth=%lu\n", (unsigned long) javan_heap_root_frame_depth());
    fprintf(out, "frame_root_count=%lu\n", (unsigned long) javan_heap_frame_root_count());
    fclose(out);
}

int main(void) {
    int8_t data[8] = {3, 1, 4, 1, 5, 9, 2, 6};
    JavanByteArray input = {data, 8};
    int checksum = javan_export_com_acme_Math_add_int_int(4, 6);
    for (int index = 0; index < 512; index++) {
        int try_value = 0;
        JavanResult try_result = javan_try_com_acme_Math_add_int_int(4, 6, &try_value);
        if (try_result.ok != 1 || try_value != 10 || try_result.message != NULL) {
            fputs("invalid try add export\n", stderr);
            return 1;
        }
        javan_result_free(&try_result);

        char* greeting = javan_export_com_acme_Text_greet_string("Loop");
        if (greeting == NULL) {
            fputs("missing greeting\n", stderr);
            return 1;
        }
        checksum += greeting[0];
        javan_free(greeting);

        JavanByteArray output = javan_export_com_acme_Bytes_duplicate_bytes(input);
        if (output.data == NULL || output.length != 8 || output.data[0] != 3 || output.data[7] != 6) {
            fputs("invalid byte export\n", stderr);
            return 1;
        }
        checksum += output.data[2];
        javan_free(output.data);
    }
    if (checksum != 38922) {
        fprintf(stderr, "unexpected checksum %d\n", checksum);
        return 1;
    }
    char* null_greeting = javan_export_com_acme_Text_greet_string(NULL);
    if (null_greeting == NULL || strcmp(null_greeting, "Hi null") != 0 || javan_last_error() != NULL) {
        fputs("null string input contract failed\n", stderr);
        return 1;
    }
    javan_free(null_greeting);

    JavanByteArray empty = {NULL, 0};
    JavanByteArray empty_output = javan_export_com_acme_Bytes_duplicate_bytes(empty);
    if (empty_output.data != NULL || empty_output.length != 0 || javan_last_error() != NULL) {
        fputs("empty byte-array input contract failed\n", stderr);
        return 1;
    }
    javan_free(empty_output.data);
    JavanByteArray merged_empty = javan_export_com_acme_Bytes_merge_bytes_bytes(empty, input);
    if (merged_empty.data == NULL || merged_empty.length != 8 || merged_empty.data[0] != 3 || merged_empty.data[7] != 6) {
        fputs("empty byte-array merge contract failed\n", stderr);
        return 1;
    }
    javan_free(merged_empty.data);

    char retained_name[5] = {'Y', 'u', 'n', 'a', 0};
    javan_export_com_acme_Store_rememberString_string(retained_name);
    retained_name[0] = 'L';
    char* retained_string = javan_export_com_acme_Store_lastString_void();
    if (retained_string == NULL || retained_string[0] != 'Y' || retained_string[1] != 'u') {
        fputs("retained string input was not copied\n", stderr);
        return 1;
    }
    javan_free(retained_string);

    int8_t retained_data[3] = {1, 2, 3};
    JavanByteArray retained_input = {retained_data, 3};
    javan_export_com_acme_Store_rememberBytes_bytes(retained_input);
    retained_data[1] = 9;
    JavanByteArray retained_output = javan_export_com_acme_Store_lastBytes_void();
    if (retained_output.data == NULL
            || retained_output.length != 3
            || retained_output.data[0] != 1
            || retained_output.data[1] != 2
            || retained_output.data[2] != 3) {
        fputs("retained byte array input was not copied\n", stderr);
        return 1;
    }
    javan_free(retained_output.data);

    javan_export_com_acme_Store_clear_void();
    javan_clear_error();
    int failed = javan_export_com_acme_Failures_failInt_void();
    const char* error = javan_last_error();
    if (failed != 0 || error == NULL || strstr(error, "negative array length") == NULL) {
        fputs("missing recoverable library error\n", stderr);
        return 1;
    }
    if (strcmp(javan_last_error_code(), "JAVAN-RUNTIME-PANIC") != 0
            || strcmp(javan_last_error_summary(), "runtime helper failure") != 0
            || strcmp(javan_last_error_class(), "com.acme.Failures") != 0
            || strstr(javan_last_error_method(), "failInt()I") == NULL
            || strcmp(javan_last_error_file(), "Failures.java") != 0
            || javan_last_error_line() != 8
            || javan_last_error_bytecode_offset() < 0
            || strstr(javan_last_error_source_line(), "new int[-1]") == NULL
            || strstr(javan_last_error_detail(), "negative array length") == NULL) {
        fputs("missing structured library error\n", stderr);
        return 1;
    }
    javan_clear_error();
    if (javan_last_error() != NULL) {
        fputs("library error did not clear\n", stderr);
        return 1;
    }
    if (javan_last_error_code() != NULL || javan_last_error_line() != -1 || javan_last_error_bytecode_offset() != -1) {
        fputs("structured library error did not clear\n", stderr);
        return 1;
    }
    int try_failed = 99;
    JavanResult fail_result = javan_try_com_acme_Failures_failInt_void(&try_failed);
    if (fail_result.ok != 0
            || try_failed != 0
            || fail_result.code == NULL
            || strcmp(fail_result.code, "JAVAN-RUNTIME-PANIC") != 0
            || fail_result.detail == NULL
            || strstr(fail_result.detail, "negative array length") == NULL
            || fail_result.line != 8
            || fail_result.bytecode_offset < 0) {
        fputs("missing owned result error\n", stderr);
        return 1;
    }
    javan_clear_error();
    if (strstr(fail_result.detail, "negative array length") == NULL) {
        fputs("owned result did not survive borrowed error clear\n", stderr);
        return 1;
    }
    javan_result_free(&fail_result);
    if (fail_result.detail != NULL || fail_result.message != NULL) {
        fputs("owned result did not clear after free\n", stderr);
        return 1;
    }
    javan_gc_collect();
    javan_validate_heap_metadata();
    javan_library_write_proof_counters();
    if (javan_library_expect_at_most(
            "live allocations",
            javan_heap_live_allocations(),
            javan_library_counter_limit("JAVAN_SANITIZER_LIBRARY_MAX_LIVE_ALLOCATIONS", 0)
        ) != 0) {
        return 1;
    }
    if (javan_library_expect_at_most(
            "live bytes",
            javan_heap_live_bytes(),
            javan_library_counter_limit("JAVAN_SANITIZER_LIBRARY_MAX_LIVE_BYTES", 0)
        ) != 0) {
        return 1;
    }
    if (javan_library_expect_at_most(
            "root frame depth",
            (unsigned long) javan_heap_root_frame_depth(),
            javan_library_counter_limit("JAVAN_SANITIZER_LIBRARY_MAX_ROOT_FRAME_DEPTH", 0)
        ) != 0) {
        return 1;
    }
    if (javan_library_expect_at_most(
            "frame root count",
            (unsigned long) javan_heap_frame_root_count(),
            javan_library_counter_limit("JAVAN_SANITIZER_LIBRARY_MAX_FRAME_ROOT_COUNT", 0)
        ) != 0) {
        return 1;
    }
    if (javan_library_expect_at_most(
            "peak live bytes",
            javan_heap_peak_live_bytes(),
            javan_library_counter_limit("JAVAN_SANITIZER_LIBRARY_MAX_PEAK_LIVE_BYTES", 2048)
        ) != 0) {
        return 1;
    }
    if (javan_library_expect_at_least(
            "total allocations",
            javan_heap_total_allocations(),
            javan_library_counter_limit("JAVAN_SANITIZER_LIBRARY_MIN_TOTAL_ALLOCATIONS", 2000)
        ) != 0) {
        return 1;
    }
    if (javan_library_expect_at_least(
            "gc collections",
            javan_heap_gc_collections(),
            javan_library_counter_limit("JAVAN_SANITIZER_LIBRARY_MIN_GC_COLLECTIONS", 1)
        ) != 0) {
        return 1;
    }
    if (javan_library_expect_at_least(
            "gc collected allocations",
            javan_heap_gc_collected_allocations(),
            javan_library_counter_limit("JAVAN_SANITIZER_LIBRARY_MIN_GC_COLLECTED_ALLOCATIONS", 1000)
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
  "$TMP/library-counter-probe.c" \
  "$FULL_PROJECT/.javan/generated/library.c" \
  "$FULL_PROJECT/.javan/generated/javan_runtime.c" \
  -o "$TMP/javan-library-counter-probe" \
  >"$TMP/counter-cc.out" 2>"$TMP/counter-cc.err"
counter_compile_code=$?
set -e

if [ "$counter_compile_code" -ne 0 ]; then
  printf '%s\n' "sanitizer generated library counter probe compile failed" >&2
  cat "$TMP/counter-cc.err" >&2
  exit 1
fi

: >"$TMP/counter.err"
PROOF_COUNTERS=$TMP/library-proof-counters.env
export PROOF_COUNTERS
set +e
JAVAN_SANITIZER_PROOF_COUNTERS=$PROOF_COUNTERS \
  /bin/sh -c '"$1" >"$2" 2>"$3"' sh "$TMP/javan-library-counter-probe" "$TMP/counter.out" "$TMP/counter.err" 2>"$TMP/counter-shell.err"
counter_code=$?
set -e
if [ -s "$TMP/counter-shell.err" ]; then
  cat "$TMP/counter-shell.err" >>"$TMP/counter.err"
fi
if [ "$counter_code" -ne 0 ]; then
  printf '%s\n' "sanitizer library counter probe exited with $counter_code" >&2
  printf '%s\n' "--- counter stdout" >&2
  cat "$TMP/counter.out" >&2
  printf '%s\n' "--- counter stderr" >&2
  cat "$TMP/counter.err" >&2
  exit 1
fi
if grep -E "ERROR: (AddressSanitizer|LeakSanitizer|UndefinedBehaviorSanitizer)|runtime error:|SUMMARY: (AddressSanitizer|LeakSanitizer|UndefinedBehaviorSanitizer)" "$TMP/counter.err" >/dev/null 2>&1; then
  printf '%s\n' "sanitizer library counter probe reported a failure signature" >&2
  printf '%s\n' "--- counter stderr" >&2
  cat "$TMP/counter.err" >&2
  exit 1
fi
if [ -s "$TMP/counter.out" ] || [ -s "$TMP/counter.err" ]; then
  printf '%s\n' "sanitizer library counter probe reported output" >&2
  printf '%s\n' "--- counter stdout" >&2
  cat "$TMP/counter.out" >&2
  printf '%s\n' "--- counter stderr" >&2
  cat "$TMP/counter.err" >&2
  exit 1
fi

cat >"$TMP/library-byte-input-panic-probe.c" <<'EOF'
#include "javan_runtime.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

JavanByteArray javan_export_com_acme_Bytes_merge_bytes_bytes(JavanByteArray arg0, JavanByteArray arg1);

int main(void) {
    int8_t data[4] = {2, 4, 6, 8};
    JavanByteArray valid = {data, 4};
    JavanByteArray invalid = {NULL, 1};
    JavanByteArray output = javan_export_com_acme_Bytes_merge_bytes_bytes(valid, invalid);
    const char* error = javan_last_error();
    if (output.data != NULL || output.length != 0 || error == NULL) {
        fputs("missing byte-array input error\n", stderr);
        return 1;
    }
    if (error[0] != 'n' || error[1] != 'u') {
        fputs("wrong byte-array input error\n", stderr);
        return 1;
    }
    if (strcmp(javan_last_error_code(), "JAVAN-RUNTIME-PANIC") != 0
            || strcmp(javan_last_error_summary(), "native runtime panic") != 0
            || javan_last_error_class() != NULL
            || javan_last_error_line() != -1
            || strcmp(javan_last_error_detail(), "null byte array input") != 0) {
        fputs("wrong structured byte-array input error\n", stderr);
        return 1;
    }
    javan_clear_error();
    JavanByteArray negative = {data, -1};
    output = javan_export_com_acme_Bytes_merge_bytes_bytes(valid, negative);
    error = javan_last_error();
    if (output.data != NULL || output.length != 0 || error == NULL || strstr(error, "negative byte array length") == NULL) {
        fputs("missing negative byte-array input error\n", stderr);
        return 1;
    }
    if (strcmp(javan_last_error_code(), "JAVAN-RUNTIME-PANIC") != 0
            || strcmp(javan_last_error_summary(), "native runtime panic") != 0
            || javan_last_error_class() != NULL
            || javan_last_error_line() != -1
            || strcmp(javan_last_error_detail(), "negative byte array length") != 0) {
        fputs("wrong structured negative byte-array input error\n", stderr);
        return 1;
    }
    javan_clear_error();
    javan_gc_collect();
    javan_validate_heap_metadata();
    if (javan_heap_live_allocations() != 0 || javan_heap_live_bytes() != 0) {
        fputs("byte-array input panic leaked heap allocations\n", stderr);
        return 1;
    }
    return 0;
}
EOF

set +e
# shellcheck disable=SC2086
"$CC" $SANITIZER_FLAGS \
  -I "$FULL_PROJECT/.javan/generated" \
  "$TMP/library-byte-input-panic-probe.c" \
  "$FULL_PROJECT/.javan/generated/library.c" \
  "$FULL_PROJECT/.javan/generated/javan_runtime.c" \
  -o "$TMP/javan-library-byte-input-panic-probe" \
  >"$TMP/byte-panic-cc.out" 2>"$TMP/byte-panic-cc.err"
byte_panic_compile_code=$?
set -e

if [ "$byte_panic_compile_code" -ne 0 ]; then
  printf '%s\n' "sanitizer generated library byte-input panic probe compile failed" >&2
  cat "$TMP/byte-panic-cc.err" >&2
  exit 1
fi

: >"$TMP/byte-panic.out"
: >"$TMP/byte-panic.err"
set +e
JAVAN_HEAP_LIMIT_BYTES=4096 /bin/sh -c '"$1" >"$2" 2>"$3"' sh "$TMP/javan-library-byte-input-panic-probe" "$TMP/byte-panic.out" "$TMP/byte-panic.err" 2>"$TMP/byte-panic-shell.err"
byte_panic_code=$?
set -e
if [ -s "$TMP/byte-panic-shell.err" ]; then
  cat "$TMP/byte-panic-shell.err" >>"$TMP/byte-panic.err"
fi
if [ "$byte_panic_code" -ne 0 ]; then
  printf '%s\n' "sanitizer library byte-input panic probe exited with $byte_panic_code" >&2
  printf '%s\n' "--- byte panic stdout" >&2
  cat "$TMP/byte-panic.out" >&2
  printf '%s\n' "--- byte panic stderr" >&2
  cat "$TMP/byte-panic.err" >&2
  exit 1
fi
if grep -E "ERROR: (AddressSanitizer|LeakSanitizer|UndefinedBehaviorSanitizer)|runtime error:|SUMMARY: (AddressSanitizer|LeakSanitizer|UndefinedBehaviorSanitizer)" "$TMP/byte-panic.err" >/dev/null 2>&1; then
  printf '%s\n' "sanitizer library byte-input panic probe reported a failure signature" >&2
  printf '%s\n' "--- byte panic stderr" >&2
  cat "$TMP/byte-panic.err" >&2
  exit 1
fi
if [ -s "$TMP/byte-panic.out" ]; then
  printf '%s\n' "sanitizer library byte-input panic probe reported stdout" >&2
  cat "$TMP/byte-panic.out" >&2
  exit 1
fi
if [ -s "$TMP/byte-panic.err" ]; then
  printf '%s\n' "sanitizer library byte-input panic probe reported stderr" >&2
  cat "$TMP/byte-panic.err" >&2
  exit 1
fi

if command -v python3 >/dev/null 2>&1; then
  if [ ! -f "$SHARED_ARTIFACT" ]; then
    printf '%s\n' "python binding smoke missing shared library: $SHARED_ARTIFACT" >&2
    exit 1
  fi
  cat >"$TMP/python-binding-smoke.py" <<'EOF'
import ctypes
import sys
from pathlib import Path

package_dir = Path(sys.argv[1])
library = Path(sys.argv[2])
sys.path.insert(0, str(package_dir))

import native_library as binding

lib = binding.load(library)
lib.javan_gc_collect.argtypes = []
lib.javan_gc_collect.restype = None
lib.javan_validate_heap_metadata.argtypes = []
lib.javan_validate_heap_metadata.restype = None
lib.javan_heap_live_allocations.argtypes = []
lib.javan_heap_live_allocations.restype = ctypes.c_ulong
lib.javan_heap_live_bytes.argtypes = []
lib.javan_heap_live_bytes.restype = ctypes.c_ulong

payload = (ctypes.c_int8 * 8)(3, 1, 4, 1, 5, 9, 2, 6)
input_bytes = binding.JavanByteArray(payload, 8)
checksum = binding.try_javan_export_com_acme_Math_add_int_int(lib, 4, 6)
for _ in range(128):
    greeting = binding.try_javan_export_com_acme_Text_greet_string(lib, b"Loop")
    if greeting is None:
        raise SystemExit("missing greeting")
    checksum += greeting.encode("utf-8")[0]

    output = binding.try_javan_export_com_acme_Bytes_duplicate_bytes(lib, input_bytes)
    if len(output) != 8 or output[0] != 3 or output[7] != 6:
        raise SystemExit("invalid byte export")
    checksum += output[0] + output[7]

try:
    binding.try_javan_export_com_acme_Failures_failInt_void(lib)
except binding.JavanError as error:
    if error.code != "JAVAN-RUNTIME-PANIC" or "negative array length" not in (error.detail or ""):
        raise SystemExit("invalid wrapped failure")
else:
    raise SystemExit("missing wrapped failure")

if checksum != 10378:
    raise SystemExit(f"unexpected checksum {checksum}")

lib.javan_gc_collect()
lib.javan_validate_heap_metadata()
if lib.javan_heap_live_allocations() != 0:
    raise SystemExit(f"live allocations {lib.javan_heap_live_allocations()}")
if lib.javan_heap_live_bytes() != 0:
    raise SystemExit(f"live bytes {lib.javan_heap_live_bytes()}")
EOF
  : >"$TMP/python-binding.out"
  : >"$TMP/python-binding.err"
  set +e
  JAVAN_HEAP_LIMIT_BYTES=2048 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1 \
    python3 "$TMP/python-binding-smoke.py" \
      "$FULL_PROJECT/.javan/dist/lib/$LIBRARY_NAME/python" \
      "$SHARED_ARTIFACT" \
      >"$TMP/python-binding.out" 2>"$TMP/python-binding.err"
  python_binding_code=$?
  set -e
  if [ "$python_binding_code" -ne 0 ]; then
    printf '%s\n' "python binding ownership smoke exited with $python_binding_code" >&2
    printf '%s\n' "--- python stdout" >&2
    cat "$TMP/python-binding.out" >&2
    printf '%s\n' "--- python stderr" >&2
    cat "$TMP/python-binding.err" >&2
    exit 1
  fi
  if [ -s "$TMP/python-binding.out" ] || [ -s "$TMP/python-binding.err" ]; then
    printf '%s\n' "python binding ownership smoke reported output" >&2
    printf '%s\n' "--- python stdout" >&2
    cat "$TMP/python-binding.out" >&2
    printf '%s\n' "--- python stderr" >&2
    cat "$TMP/python-binding.err" >&2
    exit 1
  fi
  PYTHON_BINDING_STATUS=pass
else
  if [ "$BINDING_TOOLCHAINS_REQUIRED" = "true" ]; then
    printf '%s\n' "python3 unavailable for required binding ownership smoke" >&2
    exit 1
  fi
  PYTHON_BINDING_STATUS=skipped
  printf '%s\n' "skip - python3 unavailable for binding ownership smoke"
fi

if command -v rustc >/dev/null 2>&1; then
  RUST_PACKAGE=$FULL_PROJECT/.javan/dist/lib/$LIBRARY_NAME/rust
  cat >"$TMP/rust-binding-smoke.rs" <<'EOF'
extern crate native_library;

use native_library::{
    try_javan_export_com_acme_Bytes_duplicate_bytes,
    try_javan_export_com_acme_Failures_failInt_void,
    try_javan_export_com_acme_Math_add_int_int,
    try_javan_export_com_acme_Text_greet_string,
    JavanByteArray,
};
use std::ffi::{c_char, CString};

unsafe extern "C" {
    fn javan_gc_collect();
    fn javan_validate_heap_metadata();
    fn javan_heap_live_allocations() -> usize;
    fn javan_heap_live_bytes() -> usize;
}

fn main() {
    let name = CString::new("Loop").expect("valid input");
    let mut data: [i8; 8] = [3, 1, 4, 1, 5, 9, 2, 6];
    let input = JavanByteArray { data: data.as_mut_ptr(), length: 8 };
    let mut checksum = unsafe { try_javan_export_com_acme_Math_add_int_int(4, 6).expect("add") };
    for _ in 0..128 {
        let greeting = unsafe { try_javan_export_com_acme_Text_greet_string(name.as_ptr() as *const c_char).expect("greet") };
        let Some(greeting) = greeting else {
            eprintln!("missing greeting");
            std::process::exit(1);
        };
        checksum += greeting.as_bytes()[0] as i32;

        let output = unsafe { try_javan_export_com_acme_Bytes_duplicate_bytes(input).expect("duplicate") };
        if output.len() != 8 {
            eprintln!("invalid byte export");
            std::process::exit(1);
        }
        checksum += output[0] as i32 + output[7] as i32;
    }
    let error = unsafe { try_javan_export_com_acme_Failures_failInt_void() }.expect_err("expected failure");
    if error.code.as_deref() != Some("JAVAN-RUNTIME-PANIC")
            || !error.detail.as_deref().unwrap_or("").contains("negative array length") {
        eprintln!("invalid wrapped failure");
        std::process::exit(1);
    }
    if checksum != 10378 {
        eprintln!("unexpected checksum {checksum}");
        std::process::exit(1);
    }
    unsafe {
        javan_gc_collect();
        javan_validate_heap_metadata();
        if javan_heap_live_allocations() != 0 {
            eprintln!("live allocations {}", javan_heap_live_allocations());
            std::process::exit(1);
        }
        if javan_heap_live_bytes() != 0 {
            eprintln!("live bytes {}", javan_heap_live_bytes());
            std::process::exit(1);
        }
    }
}
EOF
  set +e
  rustc \
    --crate-type rlib \
    --crate-name native_library \
    "$RUST_PACKAGE/lib.rs" \
    -L native="$RUST_PACKAGE" \
    -o "$TMP/libnative_library.rlib" \
    >"$TMP/rust-lib.out" 2>"$TMP/rust-lib.err"
  rust_lib_code=$?
  set -e
  if [ "$rust_lib_code" -ne 0 ]; then
    printf '%s\n' "rust binding library compile failed" >&2
    cat "$TMP/rust-lib.err" >&2
    exit 1
  fi
  set +e
  rustc \
    "$TMP/rust-binding-smoke.rs" \
    -L "$TMP" \
    -L native="$RUST_PACKAGE" \
    --extern native_library="$TMP/libnative_library.rlib" \
    -l static=$LIBRARY_NAME \
    -o "$TMP/rust-binding-smoke" \
    >"$TMP/rust-main.out" 2>"$TMP/rust-main.err"
  rust_main_code=$?
  set -e
  if [ "$rust_main_code" -ne 0 ]; then
    printf '%s\n' "rust binding smoke compile failed" >&2
    cat "$TMP/rust-main.err" >&2
    exit 1
  fi
  : >"$TMP/rust-binding.out"
  : >"$TMP/rust-binding.err"
  set +e
  JAVAN_HEAP_LIMIT_BYTES=2048 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1 \
    "$TMP/rust-binding-smoke" >"$TMP/rust-binding.out" 2>"$TMP/rust-binding.err"
  rust_binding_code=$?
  set -e
  if [ "$rust_binding_code" -ne 0 ]; then
    printf '%s\n' "rust binding ownership smoke exited with $rust_binding_code" >&2
    printf '%s\n' "--- rust stdout" >&2
    cat "$TMP/rust-binding.out" >&2
    printf '%s\n' "--- rust stderr" >&2
    cat "$TMP/rust-binding.err" >&2
    exit 1
  fi
  if [ -s "$TMP/rust-binding.out" ] || [ -s "$TMP/rust-binding.err" ]; then
    printf '%s\n' "rust binding ownership smoke reported output" >&2
    printf '%s\n' "--- rust stdout" >&2
    cat "$TMP/rust-binding.out" >&2
    printf '%s\n' "--- rust stderr" >&2
    cat "$TMP/rust-binding.err" >&2
    exit 1
  fi
  RUST_BINDING_STATUS=pass
else
  if [ "$BINDING_TOOLCHAINS_REQUIRED" = "true" ]; then
    printf '%s\n' "rustc unavailable for required binding ownership smoke" >&2
    exit 1
  fi
  RUST_BINDING_STATUS=skipped
  printf '%s\n' "skip - rustc unavailable for binding ownership smoke"
fi

if command -v go >/dev/null 2>&1; then
  GO_PACKAGE=$FULL_PROJECT/.javan/dist/lib/$LIBRARY_NAME/go
  cat >"$GO_PACKAGE/go.mod" <<EOF
module javan_binding_smoke

go 1.22
EOF
  cat >"$GO_PACKAGE/${SAFE_PACKAGE}_ownership_cgo.go" <<'EOF'
package native_library

/*
#include <stdlib.h>
#include "native-library.h"
void javan_gc_collect(void);
void javan_validate_heap_metadata(void);
unsigned long javan_heap_live_allocations(void);
unsigned long javan_heap_live_bytes(void);
*/
import "C"
import "unsafe"

func javanTestCString(value string) *C.char {
    return C.CString(value)
}

func javanTestCStringFree(value *C.char) {
    C.free(unsafe.Pointer(value))
}

func javanTestByteArray(values []int8) JavanByteArray {
    if len(values) == 0 {
        return JavanByteArray{}
    }
    return JavanByteArray{data: (*C.int8_t)(unsafe.Pointer(&values[0])), length: C.int(len(values))}
}

func javanTestCollectAndValidate() (uint64, uint64) {
    C.javan_gc_collect()
    C.javan_validate_heap_metadata()
    return uint64(C.javan_heap_live_allocations()), uint64(C.javan_heap_live_bytes())
}
EOF
  cat >"$GO_PACKAGE/${SAFE_PACKAGE}_ownership_test.go" <<'EOF'
package native_library

import "testing"

func TestJavanBindingOwnership(t *testing.T) {
    name := javanTestCString("Loop")
    defer javanTestCStringFree(name)
    data := []int8{3, 1, 4, 1, 5, 9, 2, 6}
    input := javanTestByteArray(data)
    add, err := TryJavanExportComAcmeMathAddIntInt(4, 6)
    if err != nil {
        t.Fatalf("add failed: %v", err)
    }
    checksum := int(add)
    for index := 0; index < 128; index++ {
        greeting, err := TryJavanExportComAcmeTextGreetString(name)
        if err != nil {
            t.Fatalf("greet failed: %v", err)
        }
        if greeting == nil {
            t.Fatal("missing greeting")
        }
        checksum += int((*greeting)[0])

        output, err := TryJavanExportComAcmeBytesDuplicateBytes(input)
        if err != nil {
            t.Fatalf("duplicate failed: %v", err)
        }
        if len(output) != 8 || output[0] != 3 || output[7] != 6 {
            t.Fatal("invalid byte export")
        }
        checksum += int(output[0]) + int(output[7])
    }
    _, err = TryJavanExportComAcmeFailuresFailIntVoid()
    if err == nil {
        t.Fatal("missing wrapped failure")
    }
    wrapped, ok := err.(JavanError)
    if !ok || wrapped.Code != "JAVAN-RUNTIME-PANIC" || wrapped.Detail == "" {
        t.Fatalf("invalid wrapped failure %#v", err)
    }
    if checksum != 10378 {
        t.Fatalf("unexpected checksum %d", checksum)
    }
    liveAllocations, liveBytes := javanTestCollectAndValidate()
    if liveAllocations != 0 {
        t.Fatalf("live allocations %d", liveAllocations)
    }
    if liveBytes != 0 {
        t.Fatalf("live bytes %d", liveBytes)
    }
}
EOF
  : >"$TMP/go-binding.out"
  : >"$TMP/go-binding.err"
  set +e
  (
    cd "$GO_PACKAGE"
    LD_LIBRARY_PATH="$GO_PACKAGE${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
    DYLD_LIBRARY_PATH="$GO_PACKAGE${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
    export LD_LIBRARY_PATH DYLD_LIBRARY_PATH
    JAVAN_HEAP_LIMIT_BYTES=2048 \
    JAVAN_GC_STRESS=1 \
    JAVAN_GC_SAFEPOINT_INTERVAL=1 \
      go test ./...
  ) >"$TMP/go-binding.out" 2>"$TMP/go-binding.err"
  go_binding_code=$?
  set -e
  rm -f "$GO_PACKAGE/go.mod" "$GO_PACKAGE/${SAFE_PACKAGE}_ownership_cgo.go" "$GO_PACKAGE/${SAFE_PACKAGE}_ownership_test.go"
  if [ "$go_binding_code" -ne 0 ]; then
    printf '%s\n' "go binding ownership smoke exited with $go_binding_code" >&2
    printf '%s\n' "--- go stdout" >&2
    cat "$TMP/go-binding.out" >&2
    printf '%s\n' "--- go stderr" >&2
    cat "$TMP/go-binding.err" >&2
    exit 1
  fi
  GO_BINDING_STATUS=pass
else
  if [ "$BINDING_TOOLCHAINS_REQUIRED" = "true" ]; then
    printf '%s\n' "go unavailable for required binding ownership smoke" >&2
    exit 1
  fi
  GO_BINDING_STATUS=skipped
  printf '%s\n' "skip - go unavailable for binding ownership smoke"
fi

if [ "${LEAK_SANITIZER_STATUS:-}" = "leak detection unsupported on this platform" ] && command -v leaks >/dev/null 2>&1; then
  set +e
  "$CC" \
    -I "$FULL_PROJECT/.javan/generated" \
    "$FULL_PROJECT/caller.c" \
    "$FULL_PROJECT/.javan/generated/library.c" \
    "$FULL_PROJECT/.javan/generated/javan_runtime.c" \
    -o "$TMP/javan-library-leak-probe" \
    >"$TMP/leak-cc.out" 2>"$TMP/leak-cc.err"
  leak_compile_code=$?
  set -e
  if [ "$leak_compile_code" -eq 0 ]; then
    set +e
    leaks --atExit -- "$TMP/javan-library-leak-probe" >"$TMP/leaks.out" 2>"$TMP/leaks.err"
    leaks_code=$?
    set -e
    if [ "$leaks_code" -ne 0 ] && ! grep -F "0 leaks for 0 total leaked bytes" "$TMP/leaks.out" >/dev/null 2>&1; then
      printf '%s\n' "macOS leaks library probe failed with exit code $leaks_code" >&2
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
  printf '%s\n' "required sanitizer library run did not prove leak safety: LeakSanitizer unsupported and no fallback passed" >&2
  exit 1
fi

write_sanitizer_proof "pass" 0 "${LEAK_SANITIZER_STATUS:-AddressSanitizer leak detection enabled}" "false"

if [ -n "${LEAK_SANITIZER_STATUS:-}" ]; then
  printf '%s\n' "ok - sanitizer library smoke passed for $PROJECT ($LEAK_SANITIZER_STATUS)"
else
  printf '%s\n' "ok - sanitizer library smoke passed for $PROJECT"
fi
