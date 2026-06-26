#!/bin/sh
set -eu

ROOT=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

JAVAN_GC_STRESS=${JAVAN_GC_STRESS:-64}
JAVAN_GC_SAFEPOINT_INTERVAL=${JAVAN_GC_SAFEPOINT_INTERVAL:-1}
export JAVAN_GC_STRESS
export JAVAN_GC_SAFEPOINT_INTERVAL

assert_contains() {
  file=$1
  expected=$2
  if ! grep -F "$expected" "$file" >/dev/null 2>&1; then
    printf '%s\n' "Missing sanitizer proof field in $file: $expected" >&2
    cat "$file" >&2
    exit 1
  fi
}

assert_sanitizer_proof_file() {
  file=$1
  if [ ! -f "$file" ]; then
    printf '%s\n' "Missing sanitizer proof report: $file" >&2
    exit 1
  fi
}

json_number_field() {
  file=$1
  name=$2
  sed -n "s/.*\"$name\": \([0-9][0-9]*\).*/\1/p" "$file" | head -n 1
}

assert_json_number_at_least() {
  file=$1
  name=$2
  minimum=$3
  value=$(json_number_field "$file" "$name")
  case "$value" in
    ''|*[!0123456789]*)
      printf '%s\n' "Missing sanitizer proof numeric field in $file: $name" >&2
      cat "$file" >&2
      exit 1
      ;;
  esac
  if [ "$value" -lt "$minimum" ]; then
    printf '%s\n' "Sanitizer proof numeric field too small in $file: $name $value < $minimum" >&2
    cat "$file" >&2
    exit 1
  fi
}

run_javan_report() {
  project=$1
  if [ -n "${JAVAN_BIN:-}" ]; then
    "$JAVAN_BIN" report "$project" >/dev/null
  elif [ -d "$ROOT/target/classes" ]; then
    java -cp "$ROOT/target/classes" javan.Main report "$project" >/dev/null
  elif [ -x "$ROOT/dist/javan" ]; then
    "$ROOT/dist/javan" report "$project" >/dev/null
  elif [ -x "$ROOT/target/.javan/bin/javan-verified" ]; then
    "$ROOT/target/.javan/bin/javan-verified" report "$project" >/dev/null
  else
    printf '%s\n' "Missing javan runtime for report proof: build target/classes or set JAVAN_BIN=/path/to/javan." >&2
    exit 2
  fi
}

assert_sanitizer_proof_summary() {
  project=$1
  kind=$2
  run_javan_report "$project"
  report=$ROOT/$project/.javan/reports/report.json
  assert_sanitizer_proof_file "$report"
  assert_contains "$report" '"name": "sanitizer-proof"'
  assert_contains "$report" '"status": "present"'
  assert_contains "$report" '"status": "pass"'
  assert_contains "$report" "\"kind\": \"$kind\""
  assert_contains "$report" '"counterCheck": "true"'
  assert_contains "$report" '"actualLiveAllocations": 0'
  assert_contains "$report" '"actualLiveBytes": 0'
  assert_contains "$report" '"failureSignatures": "false"'
}

assert_thread_inventory_summary() {
  file=$1
  assert_contains "$file" '"actualThreadObjects": 1'
  assert_contains "$file" '"actualStartedThreads": 1'
  assert_contains "$file" '"actualCompletedThreads": 0'
  assert_contains "$file" '"actualActiveThreads": 0'
  assert_contains "$file" '"actualThreadsWithTarget": 0'
  assert_contains "$file" '"actualCurrentThreadRootPresent": 1'
}

JAVAN_HEAP_LIMIT_BYTES=32768 \
JAVAN_SANITIZER_COUNTER_CHECK=true \
JAVAN_SANITIZER_MAX_LIVE_ALLOCATIONS=0 \
JAVAN_SANITIZER_MAX_LIVE_BYTES=0 \
JAVAN_SANITIZER_MAX_PEAK_LIVE_BYTES=32768 \
JAVAN_SANITIZER_MIN_TOTAL_ALLOCATIONS=5000 \
JAVAN_SANITIZER_MIN_GC_COLLECTIONS=1 \
JAVAN_SANITIZER_MIN_GC_COLLECTED_ALLOCATIONS=5000 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/memory-soak
MEMORY_SOAK_PROOF=src/test/resources/projects/native-profile/memory-soak/.javan/reports/sanitizer-proof.json
assert_sanitizer_proof_file "$MEMORY_SOAK_PROOF"
assert_contains "$MEMORY_SOAK_PROOF" '"status": "pass"'
assert_contains "$MEMORY_SOAK_PROOF" '"kind": "app"'
assert_contains "$MEMORY_SOAK_PROOF" '"counterCheck": true'
assert_contains "$MEMORY_SOAK_PROOF" '"actualLiveAllocations": 0'
assert_contains "$MEMORY_SOAK_PROOF" '"actualLiveBytes": 0'
assert_contains "$MEMORY_SOAK_PROOF" '"maxLiveAllocations": 0'
assert_contains "$MEMORY_SOAK_PROOF" '"maxLiveBytes": 0'
assert_contains "$MEMORY_SOAK_PROOF" '"maxPeakLiveBytes": 32768'
assert_contains "$MEMORY_SOAK_PROOF" '"minTotalAllocations": 5000'
assert_contains "$MEMORY_SOAK_PROOF" '"minGcCollections": 1'
assert_contains "$MEMORY_SOAK_PROOF" '"minGcCollectedAllocations": 5000'
assert_contains "$MEMORY_SOAK_PROOF" '"failureSignatures": false'
assert_json_number_at_least "$MEMORY_SOAK_PROOF" actualTotalAllocations 5000
assert_json_number_at_least "$MEMORY_SOAK_PROOF" actualGcCollections 1
assert_json_number_at_least "$MEMORY_SOAK_PROOF" actualGcCollectedAllocations 5000
assert_sanitizer_proof_summary src/test/resources/projects/native-profile/memory-soak app
MEMORY_SOAK_REPORT=src/test/resources/projects/native-profile/memory-soak/.javan/reports/report.json
assert_json_number_at_least "$MEMORY_SOAK_REPORT" actualTotalAllocations 5000
assert_json_number_at_least "$MEMORY_SOAK_REPORT" actualGcCollections 1
assert_json_number_at_least "$MEMORY_SOAK_REPORT" actualGcCollectedAllocations 5000

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_SANITIZER_COUNTER_CHECK=true \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/thread-current-inventory
THREAD_CURRENT_PROOF=src/test/resources/projects/native-profile/thread-current-inventory/.javan/reports/sanitizer-proof.json
assert_sanitizer_proof_file "$THREAD_CURRENT_PROOF"
assert_contains "$THREAD_CURRENT_PROOF" '"status": "pass"'
assert_contains "$THREAD_CURRENT_PROOF" '"kind": "app"'
assert_contains "$THREAD_CURRENT_PROOF" '"counterCheck": true'
assert_contains "$THREAD_CURRENT_PROOF" '"failureSignatures": false'
assert_thread_inventory_summary "$THREAD_CURRENT_PROOF"
run_javan_report src/test/resources/projects/native-profile/thread-current-inventory
THREAD_CURRENT_REPORT=src/test/resources/projects/native-profile/thread-current-inventory/.javan/reports/report.json
assert_sanitizer_proof_file "$THREAD_CURRENT_REPORT"
assert_contains "$THREAD_CURRENT_REPORT" '"name": "sanitizer-proof"'
assert_contains "$THREAD_CURRENT_REPORT" '"status": "present"'
assert_contains "$THREAD_CURRENT_REPORT" '"status": "pass"'
assert_contains "$THREAD_CURRENT_REPORT" '"kind": "app"'
assert_contains "$THREAD_CURRENT_REPORT" '"counterCheck": "true"'
assert_contains "$THREAD_CURRENT_REPORT" '"failureSignatures": "false"'
assert_thread_inventory_summary "$THREAD_CURRENT_REPORT"

JAVAN_SANITIZER_SELF_HOST_MAX_LIVE_ALLOCATIONS=0 \
JAVAN_SANITIZER_SELF_HOST_MAX_LIVE_BYTES=0 \
JAVAN_SANITIZER_SELF_HOST_MAX_ROOT_FRAME_DEPTH=0 \
JAVAN_SANITIZER_SELF_HOST_MAX_FRAME_ROOT_COUNT=0 \
JAVAN_SANITIZER_SELF_HOST_MIN_TOTAL_ALLOCATIONS=1 \
JAVAN_SANITIZER_SELF_HOST_MIN_GC_COLLECTIONS=1 \
JAVAN_GC_STRESS= \
JAVAN_GC_SAFEPOINT_INTERVAL= \
  sh .github/scripts/sanitizer-self-host-smoke.sh
SELF_HOST_PROOF=target/.javan/reports/sanitizer-proof.json
assert_sanitizer_proof_file "$SELF_HOST_PROOF"
assert_contains "$SELF_HOST_PROOF" '"status": "pass"'
assert_contains "$SELF_HOST_PROOF" '"kind": "self-host"'
assert_contains "$SELF_HOST_PROOF" '"counterCheck": true'
assert_contains "$SELF_HOST_PROOF" '"actualLiveAllocations": 0'
assert_contains "$SELF_HOST_PROOF" '"actualLiveBytes": 0'
assert_contains "$SELF_HOST_PROOF" '"actualRootFrameDepth": 0'
assert_contains "$SELF_HOST_PROOF" '"actualFrameRootCount": 0'
assert_contains "$SELF_HOST_PROOF" '"minTotalAllocations": 1'
assert_contains "$SELF_HOST_PROOF" '"minGcCollections": 1'
assert_contains "$SELF_HOST_PROOF" '"failureSignatures": false'
assert_json_number_at_least "$SELF_HOST_PROOF" actualTotalAllocations 1
assert_json_number_at_least "$SELF_HOST_PROOF" actualGcCollections 1
assert_sanitizer_proof_summary target self-host
SELF_HOST_REPORT=target/.javan/reports/report.json
assert_json_number_at_least "$SELF_HOST_REPORT" actualTotalAllocations 1
assert_json_number_at_least "$SELF_HOST_REPORT" actualGcCollections 1
sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/static-root-inventory
JAVAN_HEAP_LIMIT_BYTES=4096 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/string-static-root
sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/root-frame-stack
sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/gc-generated-object-graph
JAVAN_HEAP_LIMIT_BYTES=3072 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/object-registry-gc
sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/protected-object-return
sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/operand-call-temporary-roots
sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/large-arrays

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/primitive-array-gc

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/boxed-integer-gc

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/boxed-boolean-gc

JAVAN_HEAP_LIMIT_BYTES=8192 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-filetime-gc

JAVAN_HEAP_LIMIT_BYTES=8192 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-duration-millis-gc

JAVAN_HEAP_LIMIT_BYTES=8192 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-duration-seconds-gc

JAVAN_HEAP_LIMIT_BYTES=8192 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/boxed-long-gc

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/boxed-float-gc

JAVAN_HEAP_LIMIT_BYTES=8192 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/boxed-double-gc

JAVAN_HEAP_LIMIT_BYTES=6000 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/local-root-liveness-gc

JAVAN_HEAP_LIMIT_BYTES=6000 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/cfg-local-root-liveness-gc

JAVAN_HEAP_LIMIT_BYTES=2048 \
  sh .github/scripts/sanitizer-library-smoke.sh src/test/resources/projects/acceptance/native-library
NATIVE_LIBRARY_PROOF=src/test/resources/projects/acceptance/native-library/.javan/reports/sanitizer-proof.json
assert_sanitizer_proof_file "$NATIVE_LIBRARY_PROOF"
assert_contains "$NATIVE_LIBRARY_PROOF" '"status": "pass"'
assert_contains "$NATIVE_LIBRARY_PROOF" '"kind": "library"'
assert_contains "$NATIVE_LIBRARY_PROOF" '"counterCheck": true'
assert_contains "$NATIVE_LIBRARY_PROOF" '"actualLiveAllocations": 0'
assert_contains "$NATIVE_LIBRARY_PROOF" '"actualLiveBytes": 0'
assert_contains "$NATIVE_LIBRARY_PROOF" '"actualRootFrameDepth": 0'
assert_contains "$NATIVE_LIBRARY_PROOF" '"actualFrameRootCount": 0'
assert_contains "$NATIVE_LIBRARY_PROOF" '"maxLiveAllocations": 0'
assert_contains "$NATIVE_LIBRARY_PROOF" '"maxLiveBytes": 0'
assert_contains "$NATIVE_LIBRARY_PROOF" '"maxRootFrameDepth": 0'
assert_contains "$NATIVE_LIBRARY_PROOF" '"maxFrameRootCount": 0'
assert_contains "$NATIVE_LIBRARY_PROOF" '"minTotalAllocations": 2000'
assert_contains "$NATIVE_LIBRARY_PROOF" '"minGcCollections": 1'
assert_contains "$NATIVE_LIBRARY_PROOF" '"minGcCollectedAllocations": 1000'
assert_contains "$NATIVE_LIBRARY_PROOF" '"failureSignatures": false'
assert_json_number_at_least "$NATIVE_LIBRARY_PROOF" actualTotalAllocations 2000
assert_json_number_at_least "$NATIVE_LIBRARY_PROOF" actualGcCollections 1
assert_json_number_at_least "$NATIVE_LIBRARY_PROOF" actualGcCollectedAllocations 1000
assert_sanitizer_proof_summary src/test/resources/projects/acceptance/native-library library
NATIVE_LIBRARY_REPORT=src/test/resources/projects/acceptance/native-library/.javan/reports/report.json
assert_json_number_at_least "$NATIVE_LIBRARY_REPORT" actualTotalAllocations 2000
assert_json_number_at_least "$NATIVE_LIBRARY_REPORT" actualGcCollections 1
assert_json_number_at_least "$NATIVE_LIBRARY_REPORT" actualGcCollectedAllocations 1000

JAVAN_HEAP_LIMIT_BYTES=4096 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/string-growth-limit

JAVAN_HEAP_LIMIT_BYTES=4096 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-container-live-roots

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-list-reclaim

JAVAN_HEAP_LIMIT_BYTES=12288 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-map-reclaim

JAVAN_HEAP_LIMIT_BYTES=8192 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-map-realloc-gc

JAVAN_HEAP_LIMIT_BYTES=4096 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-optional-reclaim

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-iterator-reclaim

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-stringbuilder-reclaim

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-list-of-array-gc

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-list-of-varargs-gc

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-list-copy-gc

JAVAN_HEAP_LIMIT_BYTES=12288 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-map-copy-gc

JAVAN_HEAP_LIMIT_BYTES=12288 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-map-values-gc

JAVAN_HEAP_LIMIT_BYTES=300 \
JAVAN_GC_STRESS=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-realloc-growth-fit

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/operand-call-receiver-temporary-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/operand-array-load-temporary-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/operand-object-compare-temporary-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/operand-field-load-temporary-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/operand-chained-field-load-temporary-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/operand-chained-call-receiver-temporary-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-string-temporary-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-string-substring-source-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-string-replace-source-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-string-from-chars-source-root

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-string-char-array-copy-gc

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-stringbuilder-append-source-root

JAVAN_HEAP_LIMIT_BYTES=16384 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-nested-container-reclaim

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-directory-stream-source-root

JAVAN_MAX_ALLOCATION_BYTES=24 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-directory-stream-result-allocation-limit-panic

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/exception-catch-heap-pressure

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/typed-catch-specific-miss

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/typed-catch-runtime-superclass

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/typed-catch-io-superclass

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/typed-catch-util-runtime-superclass

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/typed-catch-error-not-exception

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/exception-default-message-null

JAVAN_HEAP_LIMIT_BYTES=4096 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/allocation-path-gc

JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS=boom \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/exception-panic

JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="javan panic" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/exception-default-panic

JAVAN_HEAP_LIMIT_BYTES=4096 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="left-right" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/panic-string-concat-temporary-root

JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="negative array length" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/negative-array-length

JAVAN_MAX_ALLOCATION_BYTES=64 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/allocation-limit-panic

JAVAN_MAX_ALLOCATION_BYTES=64 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/string-allocation-limit-panic

JAVAN_MAX_ALLOCATION_BYTES=64 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/exception-catch-allocation-limit-panic

JAVAN_MAX_ALLOCATION_BYTES=48 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-list-allocation-limit-panic

JAVAN_MAX_ALLOCATION_BYTES=96 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-map-allocation-limit-panic

JAVAN_MAX_ALLOCATION_BYTES=128 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-path-allocation-limit-panic

JAVAN_MAX_ALLOCATION_BYTES=1024 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-read-string-allocation-limit-panic

JAVAN_MAX_ALLOCATION_BYTES=512 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-read-all-bytes-allocation-limit-panic

JAVAN_MAX_ALLOCATION_BYTES=256 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-directory-stream-child-allocation-limit-panic

JAVAN_MAX_ALLOCATION_BYTES=512 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-process-run-output-allocation-limit-panic

JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="string builder length overflow" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-stringbuilder-setlength-overflow-panic

JAVAN_MAX_ALLOCATION_BYTES=128 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/array-copy-allocation-limit-panic

JAVAN_HEAP_LIMIT_BYTES=2048 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=1 \
JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS="out of memory" \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/heap-limit-live-root-panic

JAVAN_SANITIZER_COMPARE_JVM=false \
JAVAN_SANITIZER_EXPECTED_EXIT=7 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/system-exit
