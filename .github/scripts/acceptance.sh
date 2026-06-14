#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TMP=${TMPDIR:-/tmp}/javan-acceptance-$$
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

if [ -n "${JAVAN_BIN:-}" ]; then
  if [ ! -x "$JAVAN_BIN" ]; then
    printf '%s\n' "Missing executable javan binary: $JAVAN_BIN" >&2
    exit 2
  fi
  case "$JAVAN_BIN" in
    /*) ;;
    *) JAVAN_BIN=$(CDPATH= cd -- "$(dirname -- "$JAVAN_BIN")" && pwd)/$(basename -- "$JAVAN_BIN") ;;
  esac
elif [ -f "$ROOT/target/classes/javan/Main.class" ]; then
  JAVAN_BIN=$TMP/javan
  JAVAN_ACCEPTANCE_CLASSES=$ROOT/target/classes
  export JAVAN_ACCEPTANCE_CLASSES
  {
    printf '%s\n' '#!/bin/sh'
    printf '%s\n' 'exec java -cp "$JAVAN_ACCEPTANCE_CLASSES" javan.Main "$@"'
  } >"$JAVAN_BIN"
  chmod +x "$JAVAN_BIN"
elif [ -x "$ROOT/dist/javan" ]; then
  JAVAN_BIN=$ROOT/dist/javan
else
  printf '%s\n' "Missing javan runtime: build target/classes or set JAVAN_BIN=/path/to/javan." >&2
  printf '%s\n' "Run mvn -q package first." >&2
  exit 2
fi

PASS_COUNT=0
NATIVE_PROFILE_PROJECTS=src/test/resources/projects/native-profile
NEGATIVE_PROJECTS=src/test/resources/projects/negative

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

assert_empty() {
  file=$1
  if [ -s "$file" ]; then
    printf '%s\n' "Expected $file to be empty" >&2
    cat "$file" >&2
    exit 1
  fi
}

assert_exact() {
  file=$1
  text=$2
  expected="$TMP/expected-$$"
  printf '%s\n' "$text" >"$expected"
  cmp "$expected" "$file" >/dev/null || {
    printf '%s\n' "Expected exact content in $file" >&2
    printf '%s\n' "--- expected" >&2
    cat "$expected" >&2
    printf '%s\n' "--- actual" >&2
    cat "$file" >&2
    exit 1
  }
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

accepts_jvm_equivalent_app_args() {
  project=$1
  main_class=$2
  shift 2
  name=$(basename "$project")
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  java -cp "$full_project/.javan/classes" "$main_class" "$@" >"$TMP/$build_name.jvm" 2>"$TMP/$build_name.jvm.err" \
    || fail "$project JVM reference run"
  JAVAN_GC_SAFEPOINT_INTERVAL=1 "$full_project/.javan/bin/$name" "$@" >"$TMP/$build_name.native" 2>"$TMP/$build_name.native.err" \
    || fail "$project native args GC stress run"
  cmp "$TMP/$build_name.jvm" "$TMP/$build_name.native" >/dev/null \
    || fail "$project native args GC stress output differs from JVM"
  pass "$project matches JVM output with args under GC safe-point stress"
}

accepts_jvm_equivalent_app_gc_stress() {
  project=$1
  main_class=${2:-com.acme.Main}
  name=$(basename "$project")
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  java -cp "$full_project/.javan/classes" "$main_class" >"$TMP/$build_name.jvm" 2>"$TMP/$build_name.jvm.err" \
    || fail "$project JVM reference run"
  JAVAN_GC_SAFEPOINT_INTERVAL=1 "$full_project/.javan/bin/$name" >"$TMP/$build_name.native" 2>"$TMP/$build_name.native.err" \
    || fail "$project native GC stress run"
  cmp "$TMP/$build_name.jvm" "$TMP/$build_name.native" >/dev/null \
    || fail "$project native GC stress output differs from JVM"
  pass "$project matches JVM output under GC safe-point stress"
}

accepts_jvm_equivalent_app_env() {
  project=$1
  env_name=$2
  env_value=$3
  main_class=${4:-com.acme.Main}
  name=$(basename "$project")
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  java -cp "$full_project/.javan/classes" "$main_class" >"$TMP/$build_name.jvm" 2>"$TMP/$build_name.jvm.err" \
    || fail "$project JVM reference run"
  env "$env_name=$env_value" "$full_project/.javan/bin/$name" >"$TMP/$build_name.native" 2>"$TMP/$build_name.native.err" \
    || fail "$project native env run"
  cmp "$TMP/$build_name.jvm" "$TMP/$build_name.native" >/dev/null \
    || fail "$project native env output differs from JVM"
  pass "$project matches JVM output with $env_name"
}

accepts_jvm_equivalent_app_envs() {
  project=$1
  main_class=$2
  shift 2
  name=$(basename "$project")
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  java -cp "$full_project/.javan/classes" "$main_class" >"$TMP/$build_name.jvm" 2>"$TMP/$build_name.jvm.err" \
    || fail "$project JVM reference run"
  env "$@" "$full_project/.javan/bin/$name" >"$TMP/$build_name.native" 2>"$TMP/$build_name.native.err" \
    || fail "$project native env run"
  cmp "$TMP/$build_name.jvm" "$TMP/$build_name.native" >/dev/null \
    || fail "$project native env output differs from JVM"
  pass "$project matches JVM output with env set"
}

accepts_native_panic() {
  project=$1
  expected=$2
  name=$(basename "$project")
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  set +e
  "$full_project/.javan/bin/$name" >"$TMP/$build_name.native" 2>"$TMP/$build_name.native.err"
  exit_code=$?
  set -e
  if [ "$exit_code" -eq 0 ]; then
    fail "$project native panic unexpectedly succeeded"
  fi
  if [ "$exit_code" -ne 1 ]; then
    printf '%s\n' "Expected $project native panic exit 1, got $exit_code" >&2
    exit 1
  fi
  assert_empty "$TMP/$build_name.native"
  assert_exact "$TMP/$build_name.native.err" "$expected"
  pass "$project native panic is deterministic"
}

accepts_native_panic_env() {
  project=$1
  expected=$2
  env_name=$3
  env_value=$4
  name=$(basename "$project")
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  set +e
  env "$env_name=$env_value" "$full_project/.javan/bin/$name" >"$TMP/$build_name.native" 2>"$TMP/$build_name.native.err"
  exit_code=$?
  set -e
  if [ "$exit_code" -eq 0 ]; then
    fail "$project native panic unexpectedly succeeded"
  fi
  if [ "$exit_code" -ne 1 ]; then
    printf '%s\n' "Expected $project native panic exit 1, got $exit_code" >&2
    exit 1
  fi
  assert_empty "$TMP/$build_name.native"
  assert_exact "$TMP/$build_name.native.err" "$expected"
  pass "$project native panic is deterministic with $env_name"
}

accepts_native_panic_envs() {
  project=$1
  expected=$2
  shift 2
  name=$(basename "$project")
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  set +e
  env "$@" "$full_project/.javan/bin/$name" >"$TMP/$build_name.native" 2>"$TMP/$build_name.native.err"
  exit_code=$?
  set -e
  if [ "$exit_code" -eq 0 ]; then
    fail "$project native panic unexpectedly succeeded"
  fi
  if [ "$exit_code" -ne 1 ]; then
    printf '%s\n' "Expected $project native panic exit 1, got $exit_code" >&2
    exit 1
  fi
  assert_empty "$TMP/$build_name.native"
  assert_exact "$TMP/$build_name.native.err" "$expected"
  pass "$project native panic is deterministic with env set"
}

accepts_native_exit_code() {
  project=$1
  expected=$2
  name=$(basename "$project")
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  set +e
  "$full_project/.javan/bin/$name" >"$TMP/$build_name.native" 2>"$TMP/$build_name.native.err"
  exit_code=$?
  set -e
  if [ "$exit_code" -ne "$expected" ]; then
    printf '%s\n' "Expected $project native exit $expected, got $exit_code" >&2
    printf '%s\n' "--- stdout" >&2
    cat "$TMP/$build_name.native" >&2
    printf '%s\n' "--- stderr" >&2
    cat "$TMP/$build_name.native.err" >&2
    exit 1
  fi
  assert_empty "$TMP/$build_name.native"
  assert_empty "$TMP/$build_name.native.err"
  pass "$project native exit code is deterministic"
}

accepts_native_resource_distribution() {
  project=$1
  full_project="$ROOT/$project"
  build_name=$(printf '%s' "$project" | tr '/-' '__')

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "$build_name-build" "$JAVAN_BIN" build "$full_project"
  assert_exact "$full_project/.javan/resources/assets/logo.txt" "logo"
  assert_exact "$full_project/.javan/dist/resources/assets/logo.txt" "logo"
  assert_contains "$full_project/.javan/reports/resources.json" "\"resourceCount\": 1"
  assert_contains "$full_project/.javan/reports/resources.json" "\"path\": \"assets/logo.txt\""
  pass "$project resources copied and reported"
}

accepts_runtime_contract_report() {
  project=$1
  full_project="$ROOT/$project"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"runtimePackaging\": \"monolithic-c-runtime\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"memoryModel\": \"tracked-c-heap-safe-point-partial-gc\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"allocator\": \"tracked-calloc-free-at-shutdown\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"javaAllocationOwnership\": \"javan-owned-generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage-gc-eligible\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"ffiAllocationOwnership\": \"caller-frees-javan-owned-results-with-javan_free\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"temporaryAllocationOwnership\": \"javan-owned-explicit-free\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"heapMetadata\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"heapMetadataStrategy\": \"allocation-ledger-kind-typeid-runtimekind-mark-collectible\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"heapAccounting\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"heapReclamation\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"heapReclamationScope\": \"generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"typeDescriptors\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"objectFieldDescriptors\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"frameRootInventory\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"managedHeap\": false"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"gc\": \"partial-mark-sweep\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"gcStrategy\": \"single-threaded-entry-statement-and-return-safe-point-generated-object-object-array-primitive-array-runtime-string-runtime-container-and-owned-container-storage-mark-sweep\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"gcStress\": \"metadata-verify-and-safe-point-collection\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"gcExcludedAllocationKinds\": [\"explicit-runtime-temporaries\", \"ffi-exports\"]"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"runtimeContainerTraversal\": \"precise-rooted-runtime-container-mark-sweep\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"operandCallTemporaryRoots\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"operandCallTemporaryRootModel\": \"generated-expression-root-frame\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"operandCallTemporaryRootScope\": [\"object-call-arguments\", \"nested-object-call-results\", \"field-store-receiver\", \"field-store-value\", \"array-store-array\", \"array-store-value\", \"return-operand\", \"object-print-operands\"]"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"operandCallTemporaryRootLifetime\": \"until-enclosing-generated-statement-or-return-completes\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"allocationPathCollection\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"allocationPathCollectionModel\": \"allocator-gc-retry-before-out-of-memory\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"allocationPathCollectionScope\": \"generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"allocationFailureMode\": \"deterministic-native-panic\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"statementSafePoints\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"statementSafePointScope\": \"generated-label-and-non-terminal-statement-boundaries\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"returnValueRoots\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"protectedObjectReturns\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"protectedObjectReturnScope\": \"single-threaded-static-return-root-through-callee-safe-point-and-frame-pop\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"staticRootInventory\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"localRootInventory\": true"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"rootScanning\": false"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"rootModel\": \"generated-static-frame-return-and-expression-root-inventory-no-heap-scan\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"threadRoots\": false"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"javaHeapAllocationsManaged\": false"
  assert_contains "$full_project/.javan/reports/runtime.json" "\"exceptions\": \"panic-and-limited-same-method-catch\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"threads\": \"none\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"sanitizerInstrumentation\": \"not-built\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"sanitizers\": \"not-enabled\""
  assert_contains "$full_project/.javan/reports/runtime.md" "Runtime Contract"
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"hostTarget\""
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"actualTarget\""
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"crossCompilation\": false"
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"name\": \"system-linked\""
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"status\": \"verified-host\""
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"name\": \"self-contained\""
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"status\": \"not-implemented\""
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"target\": \"linux-x64\""
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"target\": \"linux-aarch64\""
  assert_contains "$full_project/.javan/reports/runtime-footprint.json" "\"target\": \"macos-aarch64\""
  assert_contains "$full_project/.javan/reports/runtime-footprint.md" "Runtime Footprint"
  pass "$project runtime contract reported"
}

accepts_jar_output() {
  project="$TMP/jar-output-project"
  source="$project/src/main/java/com/acme/Library.java"
  mkdir -p "$(dirname "$source")"
  {
    printf '%s\n' 'package com.acme;'
    printf '%s\n' ''
    printf '%s\n' 'public final class Library {'
    printf '%s\n' '    private Library() {'
    printf '%s\n' '    }'
    printf '%s\n' ''
    printf '%s\n' '    public static int add(final int left, final int right) {'
    printf '%s\n' '        return left + right;'
    printf '%s\n' '    }'
    printf '%s\n' '}'
  } >"$source"

  run_cmd "jar-output-build" "$JAVAN_BIN" build "$project" --jar
  jar_file="$project/.javan/dist/jar-output-project.jar"
  if [ ! -f "$jar_file" ]; then
    fail "jar output was not created"
  fi
  jar --list --file "$jar_file" >"$TMP/jar-output.list" 2>"$TMP/jar-output.err" \
    || fail "jar output listing"
  assert_contains "$TMP/jar-output.list" "com/acme/Library.class"
  pass "jar output under rebuilt binary"
}

accepts_unified_report() {
  project="$TMP/report-project"
  source="$project/src/main/java/com/acme/Main.java"
  mkdir -p "$(dirname "$source")"
  {
    printf '%s\n' 'package com.acme;'
    printf '%s\n' ''
    printf '%s\n' 'public final class Main {'
    printf '%s\n' '    private Main() {'
    printf '%s\n' '    }'
    printf '%s\n' ''
    printf '%s\n' '    public static void main(final String[] args) {'
    printf '%s\n' '        System.out.println("report");'
    printf '%s\n' '    }'
    printf '%s\n' '}'
  } >"$source"

  run_cmd "report-project-build" "$JAVAN_BIN" build "$project"
  run_cmd "report-project-report" "$JAVAN_BIN" report "$project"
  assert_contains "$project/.javan/reports/report.md" "Known report families:"
  assert_contains "$project/.javan/reports/report.json" "\"reportsDirectory\""
  pass "unified report under rebuilt binary"
}

rejects_check() {
  project=$1
  expected=$2
  name=$(printf '%s' "$project" | tr '/-' '__')
  full_project="$ROOT/$project"

  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  set +e
  "$JAVAN_BIN" check "$full_project" >"$TMP/$name.out" 2>"$TMP/$name.err"
  exit_code=$?
  set -e
  if [ "$exit_code" -eq 0 ]; then
    fail "$project unexpectedly passed"
  fi
  if [ "$exit_code" -ne 2 ]; then
    printf '%s\n' "Expected $project check exit 2, got $exit_code" >&2
    exit 1
  fi
  assert_contains "$TMP/$name.err" "$expected"
  pass "$project rejected clearly"
}

accepts_native_library() {
  project=src/test/resources/projects/acceptance/native-library
  full_project="$ROOT/$project"
  "$JAVAN_BIN" clean "$full_project" >/dev/null 2>&1 || true
  run_cmd "native-library-build" "$JAVAN_BIN" build "$full_project" --library --format static --export com.acme.Math.add --export com.acme.Text.greet --export com.acme.Bytes.duplicate --bindings c,rust,go,python
  assert_contains "$full_project/.javan/reports/runtime.json" "\"artifactKind\": \"library\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"linkage\": \"static-archive\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"javan_export_com_acme_Math_add_int_int\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"javan_export_com_acme_Text_greet_string\""
  assert_contains "$full_project/.javan/reports/runtime.json" "\"javan_export_com_acme_Bytes_duplicate_bytes\""
  cc "$full_project/caller.c" "$full_project/.javan/dist/libnative-library.a" -o "$TMP/native-library-caller" \
    >"$TMP/native-library-cc.out" 2>"$TMP/native-library-cc.err" || fail "$project C caller compile"
  JAVAN_HEAP_LIMIT_BYTES=2048 "$TMP/native-library-caller" >"$TMP/native-library.out" 2>"$TMP/native-library.err" \
    || fail "$project C caller run"
  assert_contains "$TMP/native-library.out" "10"
  assert_contains "$TMP/native-library.out" "Hi Yuna"
  assert_contains "$TMP/native-library.out" "4:3:4"
  pass "$project C ABI smoke"
}

accepts_optional_typemap_probe() {
  if [ -z "${TYPEMAP_JAR:-}" ] && ls "$ROOT"/../../TypeMap/target/type-map-*.jar >/dev/null 2>&1; then
    TYPEMAP_JAR=$(find "$ROOT"/../../TypeMap/target -maxdepth 1 -name 'type-map-*.jar' | sort | tail -1)
    export TYPEMAP_JAR
  fi
  if [ -z "${TYPEMAP_JAR:-}" ]; then
    pass "src/test/resources/projects/real-probes/typemap-pair skipped without TYPEMAP_JAR"
    return 0
  fi
  (cd "$ROOT/src/test/resources/projects/real-probes/typemap-pair" && JAVAN="$JAVAN_BIN" ./build-example.sh) \
    >"$TMP/typemap.out" 2>"$TMP/typemap.err" || fail "src/test/resources/projects/real-probes/typemap-pair"
  assert_contains "$TMP/typemap.out" "value"
  pass "src/test/resources/projects/real-probes/typemap-pair native probe"
}

accepts_optional_nano_probe() {
  if [ -z "${NANO_CLASSES:-}" ] && [ -d "$ROOT/../../nano/target/classes" ]; then
    NANO_CLASSES=$ROOT/../../nano/target/classes
    export NANO_CLASSES
  fi
  if [ -z "${NANO_CLASSES:-}" ]; then
    pass "src/test/resources/projects/real-probes/nano-metric skipped without NANO_CLASSES"
    return 0
  fi
  (cd "$ROOT/src/test/resources/projects/real-probes/nano-metric" && JAVAN="$JAVAN_BIN" ./build-example.sh) \
    >"$TMP/nano.out" 2>"$TMP/nano.err" || fail "src/test/resources/projects/real-probes/nano-metric"
  assert_contains "$TMP/nano.out" "requests"
  pass "src/test/resources/projects/real-probes/nano-metric native probe"
}

accepts_optional_nano_duration_probe() {
  if [ -z "${NANO_JAR:-}" ] && [ -f "$HOME/.m2/repository/org/nanonative/nano/2025.11.3131219/nano-2025.11.3131219.jar" ]; then
    NANO_JAR=$HOME/.m2/repository/org/nanonative/nano/2025.11.3131219/nano-2025.11.3131219.jar
    export NANO_JAR
  fi
  if [ -z "${NANO_JAR:-}" ]; then
    pass "src/test/resources/projects/real-probes/nano-duration skipped without NANO_JAR"
    return 0
  fi
  (cd "$ROOT/src/test/resources/projects/real-probes/nano-duration" && JAVAN="$JAVAN_BIN" ./build-example.sh) \
    >"$TMP/nano-duration.out" 2>"$TMP/nano-duration.err" || fail "src/test/resources/projects/real-probes/nano-duration"
  assert_contains "$TMP/nano-duration.out" "1m 5s"
  pass "src/test/resources/projects/real-probes/nano-duration native probe"
}

cd "$ROOT"

accepts_jvm_equivalent_app src/test/resources/projects/acceptance/hello
accepts_runtime_contract_report src/test/resources/projects/acceptance/hello
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/primitive-int"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/boolean-basic"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/helper-call"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/if-else"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/while-loop"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/int-array"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/int-bitwise-and"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/object-array-clone"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/int-array-clone"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/long-basic"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/long-array"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/float-double"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/primitive-arrays"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/static-fields"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/static-root-inventory"
accepts_jvm_equivalent_app_gc_stress "$NATIVE_PROFILE_PROJECTS/string-static-root"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/root-frame-stack"
accepts_jvm_equivalent_app_gc_stress "$NATIVE_PROFILE_PROJECTS/gc-generated-object-graph"
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/object-registry-gc" JAVAN_HEAP_LIMIT_BYTES 3072
accepts_jvm_equivalent_app_gc_stress "$NATIVE_PROFILE_PROJECTS/protected-object-return"
accepts_jvm_equivalent_app_gc_stress "$NATIVE_PROFILE_PROJECTS/operand-call-temporary-roots"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/string-intrinsics"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/string-concat"
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/string-growth-limit" JAVAN_HEAP_LIMIT_BYTES 4096
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/enum-basic"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/interface-dispatch"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/polymorphic-virtual"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/interface-polymorphic"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/try-catch"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/typed-catch-specific-miss"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/typed-catch-runtime-superclass"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/typed-catch-io-superclass"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/typed-catch-util-runtime-superclass"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/typed-catch-error-not-exception"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/exception-default-message-null"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/object-fields"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/object-list"
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-container-live-roots" JAVAN_HEAP_LIMIT_BYTES 4096
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-list-reclaim" JAVAN_HEAP_LIMIT_BYTES 8192
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-map-reclaim" JAVAN_HEAP_LIMIT_BYTES 12288
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-optional-reclaim" JAVAN_HEAP_LIMIT_BYTES 4096
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-iterator-reclaim" JAVAN_HEAP_LIMIT_BYTES 8192
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-stringbuilder-reclaim" JAVAN_HEAP_LIMIT_BYTES 8192
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-list-of-array-gc" JAVAN_HEAP_LIMIT_BYTES 8192
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-list-copy-gc" JAVAN_HEAP_LIMIT_BYTES 8192
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-map-copy-gc" JAVAN_HEAP_LIMIT_BYTES 12288
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/runtime-map-values-gc" JAVAN_HEAP_LIMIT_BYTES 12288
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/runtime-realloc-growth-fit" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=300 \
  JAVAN_GC_STRESS=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/operand-call-receiver-temporary-root" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/operand-array-load-temporary-root" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/runtime-string-temporary-root" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/runtime-string-substring-source-root" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/runtime-string-replace-source-root" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/runtime-string-from-chars-source-root" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/runtime-string-char-array-copy-gc" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/runtime-stringbuilder-append-source-root" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/runtime-nested-container-reclaim" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=16384 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/runtime-directory-stream-source-root" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app_envs "$NATIVE_PROFILE_PROJECTS/exception-catch-heap-pressure" com.acme.Main \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/memory-soak"
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/large-arrays"
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/primitive-array-gc" JAVAN_HEAP_LIMIT_BYTES 8192
accepts_jvm_equivalent_app_env "$NATIVE_PROFILE_PROJECTS/allocation-path-gc" JAVAN_HEAP_LIMIT_BYTES 4096
accepts_jvm_equivalent_app "$NATIVE_PROFILE_PROJECTS/jdk-intrinsics"
accepts_native_panic "$NATIVE_PROFILE_PROJECTS/exception-panic" "boom"
accepts_native_panic "$NATIVE_PROFILE_PROJECTS/exception-default-panic" "javan panic"
accepts_native_panic_envs "$NATIVE_PROFILE_PROJECTS/panic-string-concat-temporary-root" "left-right" \
  JAVAN_HEAP_LIMIT_BYTES=4096 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_native_panic "$NATIVE_PROFILE_PROJECTS/negative-array-length" "negative array length"
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 64
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/string-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 64
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/exception-catch-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 64
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/runtime-list-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 48
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/runtime-map-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 96
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/runtime-path-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 128
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/runtime-read-string-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 1024
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/runtime-read-all-bytes-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 512
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/runtime-directory-stream-child-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 256
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/runtime-process-run-output-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 512
accepts_native_panic_env "$NATIVE_PROFILE_PROJECTS/array-copy-allocation-limit-panic" "out of memory" JAVAN_MAX_ALLOCATION_BYTES 128
accepts_native_panic_envs "$NATIVE_PROFILE_PROJECTS/heap-limit-live-root-panic" "out of memory" \
  JAVAN_HEAP_LIMIT_BYTES=2048 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1
accepts_native_exit_code "$NATIVE_PROFILE_PROJECTS/system-exit" 7
accepts_jvm_equivalent_app_args "$NATIVE_PROFILE_PROJECTS/main-args" com.acme.Main left right
accepts_native_resource_distribution "$NATIVE_PROFILE_PROJECTS/native-resources"
accepts_jar_output
accepts_unified_report

accepts_native_library

rejects_check "$NEGATIVE_PROJECTS/no-main" "no main class found"
rejects_check "$NEGATIVE_PROJECTS/multiple-main" "multiple main classes"
rejects_check "$NEGATIVE_PROJECTS/unsupported-reflection" "Class.forName"
rejects_check "$NEGATIVE_PROJECTS/exception-cause-constructor" "error[JAVAN014]"
rejects_check "$NEGATIVE_PROJECTS/enum-value-of" "error[JAVAN015]"

accepts_optional_typemap_probe
accepts_optional_nano_probe
accepts_optional_nano_duration_probe

printf '%s\n' "Acceptance passed: $PASS_COUNT checks"
