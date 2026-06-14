#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

JAVAN_GC_STRESS=${JAVAN_GC_STRESS:-64}
JAVAN_GC_SAFEPOINT_INTERVAL=${JAVAN_GC_SAFEPOINT_INTERVAL:-1}
export JAVAN_GC_STRESS
export JAVAN_GC_SAFEPOINT_INTERVAL

sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/memory-soak
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

JAVAN_HEAP_LIMIT_BYTES=2048 \
  sh .github/scripts/sanitizer-library-smoke.sh src/test/resources/projects/acceptance/native-library

JAVAN_HEAP_LIMIT_BYTES=4096 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/string-growth-limit

JAVAN_HEAP_LIMIT_BYTES=4096 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-container-live-roots

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-list-reclaim

JAVAN_HEAP_LIMIT_BYTES=12288 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-map-reclaim

JAVAN_HEAP_LIMIT_BYTES=4096 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-optional-reclaim

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-iterator-reclaim

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-stringbuilder-reclaim

JAVAN_HEAP_LIMIT_BYTES=8192 \
  sh .github/scripts/sanitizer-smoke.sh src/test/resources/projects/native-profile/runtime-list-of-array-gc

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
