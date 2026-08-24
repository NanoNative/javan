#!/bin/sh
set -eu

ROOT=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

SANITIZER_SCOPE=${JAVAN_SANITIZER_SCOPE:-full}
case "$SANITIZER_SCOPE" in
  full|platform_smoke|baseline|self_host|gc_roots|gc_values|runtime_containers|temporary_roots|failure_exceptions|failure_limits) ;;
  *)
    printf '%s\n' "Unsupported sanitizer scope: $SANITIZER_SCOPE" >&2
    exit 2
    ;;
esac

runs_scope() {
  [ "$SANITIZER_SCOPE" = "full" ] || [ "$SANITIZER_SCOPE" = "$1" ]
}

run_smoke() {
  sh .github/scripts/sanitizer-smoke.sh "src/test/resources/projects/native-profile/$1"
}

run_heap_smoke() (
  JAVAN_HEAP_LIMIT_BYTES=$1 run_smoke "$2"
)

run_gc_smoke() (
  JAVAN_HEAP_LIMIT_BYTES=$1 JAVAN_GC_STRESS=1 JAVAN_GC_SAFEPOINT_INTERVAL=1 run_smoke "$2"
)

run_stress_smoke() (
  JAVAN_HEAP_LIMIT_BYTES=$1 JAVAN_GC_STRESS=1 run_smoke "$2"
)

run_failure() (
  JAVAN_SANITIZER_COMPARE_JVM=false \
  JAVAN_SANITIZER_EXPECTED_EXIT=$1 \
  JAVAN_SANITIZER_EXPECTED_STDERR_CONTAINS=$2 \
    run_smoke "$3"
)

run_allocation_failure() (
  JAVAN_MAX_ALLOCATION_BYTES=$1 run_failure 1 "out of memory" "$2"
)

run_gc_failure() (
  JAVAN_HEAP_LIMIT_BYTES=$1 JAVAN_GC_STRESS=1 JAVAN_GC_SAFEPOINT_INTERVAL=1 \
    run_failure 1 "$2" "$3"
)

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
  assert_contains "$file" '"actualThreadObjects": 0'
  assert_contains "$file" '"actualStartedThreads": 0'
  assert_contains "$file" '"actualCompletedThreads": 0'
  assert_contains "$file" '"actualActiveThreads": 0'
  assert_contains "$file" '"actualThreadsWithTarget": 0'
  assert_contains "$file" '"actualCurrentThreadRootPresent": 0'
}

assert_thread_sanitizer_summary() {
  project=$1
  proof=$project/.javan/reports/sanitizer-proof.json
  assert_sanitizer_proof_file "$proof"
  assert_contains "$proof" '"status": "pass"'
  assert_contains "$proof" '"kind": "app"'
  assert_contains "$proof" '"counterCheck": true'
  assert_contains "$proof" '"actualLiveAllocations": 0'
  assert_contains "$proof" '"actualLiveBytes": 0'
  assert_contains "$proof" '"failureSignatures": false'
  assert_thread_inventory_summary "$proof"
  assert_sanitizer_proof_summary "$project" app
  report=$project/.javan/reports/report.json
  assert_thread_inventory_summary "$report"
}

assert_library_sanitizer_summary() {
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
}

if runs_scope baseline || [ "$SANITIZER_SCOPE" = "platform_smoke" ]; then
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
assert_thread_sanitizer_summary src/test/resources/projects/native-profile/thread-current-inventory

JAVAN_HEAP_LIMIT_BYTES=65536 \
JAVAN_SANITIZER_COUNTER_CHECK=true \
JAVAN_SANITIZER_MAX_LIVE_ALLOCATIONS=0 \
JAVAN_SANITIZER_MAX_LIVE_BYTES=0 \
JAVAN_SANITIZER_MIN_TOTAL_ALLOCATIONS=4000 \
JAVAN_SANITIZER_MIN_GC_COLLECTIONS=1 \
JAVAN_SANITIZER_MIN_GC_COLLECTED_ALLOCATIONS=4000 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/concurrent-object-return-handoff
CONCURRENT_HANDOFF_PROOF=src/test/resources/projects/native-profile/concurrent-object-return-handoff/.javan/reports/sanitizer-proof.json
assert_thread_sanitizer_summary src/test/resources/projects/native-profile/concurrent-object-return-handoff
assert_contains "$CONCURRENT_HANDOFF_PROOF" '"maxLiveAllocations": 0'
assert_contains "$CONCURRENT_HANDOFF_PROOF" '"maxLiveBytes": 0'
assert_contains "$CONCURRENT_HANDOFF_PROOF" '"minTotalAllocations": 4000'
assert_contains "$CONCURRENT_HANDOFF_PROOF" '"minGcCollections": 1'
assert_contains "$CONCURRENT_HANDOFF_PROOF" '"minGcCollectedAllocations": 4000'
assert_json_number_at_least "$CONCURRENT_HANDOFF_PROOF" actualTotalAllocations 4000
assert_json_number_at_least "$CONCURRENT_HANDOFF_PROOF" actualGcCollections 1
assert_json_number_at_least "$CONCURRENT_HANDOFF_PROOF" actualGcCollectedAllocations 4000
CONCURRENT_HANDOFF_REPORT=src/test/resources/projects/native-profile/concurrent-object-return-handoff/.javan/reports/report.json
assert_json_number_at_least "$CONCURRENT_HANDOFF_REPORT" actualTotalAllocations 4000
assert_json_number_at_least "$CONCURRENT_HANDOFF_REPORT" actualGcCollections 1
assert_json_number_at_least "$CONCURRENT_HANDOFF_REPORT" actualGcCollectedAllocations 4000

JAVAN_HEAP_LIMIT_BYTES=65536 \
JAVAN_SANITIZER_COUNTER_CHECK=true \
JAVAN_SANITIZER_MAX_LIVE_ALLOCATIONS=0 \
JAVAN_SANITIZER_MAX_LIVE_BYTES=0 \
JAVAN_GC_STRESS=1 \
JAVAN_GC_SAFEPOINT_INTERVAL=1 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/http-server-loopback
assert_thread_sanitizer_summary src/test/resources/projects/native-profile/http-server-loopback

assert_library_sanitizer_summary
fi

if [ "$SANITIZER_SCOPE" = "platform_smoke" ] || [ "$SANITIZER_SCOPE" = "baseline" ]; then
  exit 0
fi

if runs_scope self_host; then
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
fi

if runs_scope gc_roots; then
  run_smoke static-root-inventory
  run_heap_smoke 4096 string-static-root
  run_smoke root-frame-stack
  run_smoke gc-generated-object-graph
  run_heap_smoke 3072 object-registry-gc
  run_smoke protected-object-return
  run_smoke operand-call-temporary-roots
  run_smoke large-arrays
  run_heap_smoke 8192 primitive-array-gc
fi

if runs_scope gc_values; then
  run_gc_smoke 4096 boxed-integer-gc
  run_gc_smoke 4096 boxed-boolean-gc
  run_gc_smoke 8192 runtime-filetime-gc
  run_gc_smoke 8192 runtime-duration-millis-gc
  run_gc_smoke 8192 runtime-duration-seconds-gc
  run_gc_smoke 8192 boxed-long-gc
  run_gc_smoke 4096 boxed-float-gc
  run_gc_smoke 8192 boxed-double-gc
  run_gc_smoke 6000 local-root-liveness-gc
  run_gc_smoke 6000 cfg-local-root-liveness-gc
fi

if runs_scope runtime_containers; then
  run_heap_smoke 4096 string-growth-limit
  run_heap_smoke 4096 runtime-container-live-roots
  run_heap_smoke 8192 runtime-list-reclaim
  run_heap_smoke 12288 runtime-map-reclaim
  run_gc_smoke 8192 runtime-map-realloc-gc
  run_heap_smoke 4096 runtime-optional-reclaim
  run_heap_smoke 8192 runtime-iterator-reclaim
  run_heap_smoke 8192 runtime-stringbuilder-reclaim
  run_heap_smoke 8192 runtime-list-of-array-gc
  run_gc_smoke 4096 runtime-list-of-varargs-gc
  run_heap_smoke 8192 runtime-list-copy-gc
  run_heap_smoke 12288 runtime-map-copy-gc
  run_heap_smoke 12288 runtime-map-values-gc
  run_stress_smoke 512 runtime-realloc-growth-fit
fi

if runs_scope temporary_roots; then
  run_gc_smoke 4096 operand-call-receiver-temporary-root
  run_gc_smoke 4096 operand-array-load-temporary-root
  run_gc_smoke 4096 operand-object-compare-temporary-root
  run_gc_smoke 4096 operand-field-load-temporary-root
  run_gc_smoke 4096 operand-chained-field-load-temporary-root
  run_gc_smoke 4096 operand-chained-call-receiver-temporary-root
  run_gc_smoke 4096 runtime-string-temporary-root
  run_gc_smoke 4096 runtime-string-substring-source-root
  run_gc_smoke 4096 runtime-string-replace-source-root
  run_gc_smoke 4096 runtime-string-from-chars-source-root
  run_gc_smoke 4096 runtime-string-char-array-copy-gc
  run_gc_smoke 4096 runtime-stringbuilder-append-source-root
  run_gc_smoke 16384 runtime-nested-container-reclaim
  run_gc_smoke 4096 runtime-directory-stream-source-root
fi

if runs_scope failure_limits; then
  run_allocation_failure 24 runtime-directory-stream-result-allocation-limit-panic
fi

if runs_scope failure_exceptions; then
  run_gc_smoke 4096 exception-catch-heap-pressure
  run_gc_smoke 4096 typed-catch-specific-miss
  run_gc_smoke 4096 typed-catch-runtime-superclass
  run_gc_smoke 4096 typed-catch-io-superclass
  run_gc_smoke 4096 typed-catch-util-runtime-superclass
  run_gc_smoke 4096 typed-catch-error-not-exception
  run_gc_smoke 4096 exception-default-message-null
  run_gc_smoke 8192 application-exception
  run_gc_smoke 8192 try-finally-exception
  run_heap_smoke 4096 allocation-path-gc
  run_failure 1 boom exception-panic
  run_failure 1 "javan panic" exception-default-panic
  run_gc_failure 4096 left-right panic-string-concat-temporary-root
  run_failure 1 "negative array length" negative-array-length
fi

if runs_scope failure_limits; then
  run_allocation_failure 64 allocation-limit-panic
  run_allocation_failure 64 string-allocation-limit-panic
  run_allocation_failure 64 exception-catch-allocation-limit-panic
  run_allocation_failure 48 runtime-list-allocation-limit-panic
  run_allocation_failure 96 runtime-map-allocation-limit-panic
  run_allocation_failure 128 runtime-path-allocation-limit-panic
  run_allocation_failure 1024 runtime-read-string-allocation-limit-panic
  run_allocation_failure 512 runtime-read-all-bytes-allocation-limit-panic
  run_allocation_failure 256 runtime-directory-stream-child-allocation-limit-panic
  run_allocation_failure 512 runtime-process-run-output-allocation-limit-panic
  run_failure 1 "string builder length overflow" runtime-stringbuilder-setlength-overflow-panic
  run_allocation_failure 128 array-copy-allocation-limit-panic
  run_gc_failure 2048 "out of memory" heap-limit-live-root-panic
fi

if runs_scope failure_exceptions; then
  run_failure 7 "" system-exit
fi
