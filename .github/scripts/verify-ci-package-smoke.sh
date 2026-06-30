#!/bin/sh
set -eu

ROOT=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

assert_contains() {
  file=$1
  expected=$2
  if ! grep -F "$expected" "$file" >/dev/null 2>&1; then
    printf '%s\n' "Missing expected CI package proof content in $file: $expected" >&2
    cat "$file" >&2
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
      printf '%s\n' "Missing numeric field in $file: $name" >&2
      cat "$file" >&2
      exit 1
      ;;
  esac
  if [ "$value" -lt "$minimum" ]; then
    printf '%s\n' "Numeric field in $file is too small: $name $value < $minimum" >&2
    cat "$file" >&2
    exit 1
  fi
}

JAVAN_BUILD_REUSE_TARGET=true scripts/build.sh
ARCHIVE=$(.github/scripts/package-release.sh "${JAVAN_VERSION:-}")
.github/scripts/verify-package.sh "$ARCHIVE"

TMP=${TMPDIR:-/tmp}/javan-ci-package-$$
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

tar -xzf "$ARCHIVE" -C "$TMP"
PACKAGE_ROOT=$TMP/$(basename "$ARCHIVE" .tar.gz)
PACKAGE_BIN=$PACKAGE_ROOT/bin/javan
PACKAGE_VERSION=$(cat "$PACKAGE_ROOT/VERSION")

"$PACKAGE_BIN" doctor >/dev/null
"$PACKAGE_BIN" --version >/dev/null
rm -rf target/.javan
"$PACKAGE_BIN" check target/classes --main javan.Main >/dev/null
"$PACKAGE_BIN" report target >/dev/null

DIAGNOSTICS=target/.javan/reports/diagnostics.json
if [ ! -f "$DIAGNOSTICS" ]; then
  printf '%s\n' "Missing packaged self-check diagnostics report: $DIAGNOSTICS" >&2
  exit 1
fi
for expected in '"diagnostics": 0' '"errors": 0' '"warnings": 0'; do
  if ! grep -q "$expected" "$DIAGNOSTICS"; then
    printf '%s\n' "Packaged self-check did not prove $expected" >&2
    cat "$DIAGNOSTICS" >&2
    exit 1
  fi
done

REPORT=target/.javan/reports/report.json
if [ ! -f "$REPORT" ]; then
  printf '%s\n' "Missing packaged self-check unified report: $REPORT" >&2
  exit 1
fi
assert_contains "$REPORT" '"name": "diagnostics"'
assert_contains "$REPORT" '"diagnostics": 0'
assert_contains "$REPORT" '"errors": 0'
assert_contains "$REPORT" '"warnings": 0'
assert_contains "$REPORT" '"name": "reachability"'
assert_contains "$REPORT" '"reachableMethods":'

"$PACKAGE_BIN" build target/classes --main javan.Main --jar --output javan-package-selfhost-jar >/dev/null
SELFHOST_JAR=$ROOT/target/.javan/dist/javan-package-selfhost-jar.jar
if [ ! -f "$SELFHOST_JAR" ]; then
  printf '%s\n' "Missing package-built self-host jar: $SELFHOST_JAR" >&2
  exit 1
fi
jar tf "$SELFHOST_JAR" >"$TMP/selfhost-jar-entries.txt"
assert_contains "$TMP/selfhost-jar-entries.txt" "META-INF/MANIFEST.MF"
assert_contains "$TMP/selfhost-jar-entries.txt" "javan/Main.class"
SELFHOST_JAR_EXTRACT=$TMP/selfhost-jar-extract
mkdir -p "$SELFHOST_JAR_EXTRACT"
(cd "$SELFHOST_JAR_EXTRACT" && jar xf "$SELFHOST_JAR" META-INF/MANIFEST.MF)
assert_contains "$SELFHOST_JAR_EXTRACT/META-INF/MANIFEST.MF" "Main-Class: javan.Main"

"$PACKAGE_BIN" build target/classes --main javan.Main --output javan-package-selfhost-smoke >/dev/null
SELFHOST_BIN=target/.javan/bin/javan-package-selfhost-smoke
if [ ! -x "$SELFHOST_BIN" ]; then
  printf '%s\n' "Missing package-built self-host smoke binary: $SELFHOST_BIN" >&2
  exit 1
fi
"$SELFHOST_BIN" --version | grep -F "javan $PACKAGE_VERSION" >/dev/null

JAVAN_BIN=$PACKAGE_BIN JAVAN_SANITIZER_REQUIRED=true sh .github/scripts/sanitizer-self-host-smoke.sh
SANITIZER_PROOF=target/.javan/reports/sanitizer-proof.json
if [ ! -f "$SANITIZER_PROOF" ]; then
  printf '%s\n' "Missing package-backed self-host sanitizer proof: $SANITIZER_PROOF" >&2
  exit 1
fi
assert_contains "$SANITIZER_PROOF" '"status": "pass"'
assert_contains "$SANITIZER_PROOF" '"kind": "self-host"'
assert_contains "$SANITIZER_PROOF" '"counterCheck": true'
assert_contains "$SANITIZER_PROOF" '"actualLiveAllocations": 0'
assert_contains "$SANITIZER_PROOF" '"actualLiveBytes": 0'
assert_contains "$SANITIZER_PROOF" '"actualRootFrameDepth": 0'
assert_contains "$SANITIZER_PROOF" '"actualFrameRootCount": 0'
assert_contains "$SANITIZER_PROOF" '"minTotalAllocations": 1'
assert_contains "$SANITIZER_PROOF" '"minGcCollections": 1'
assert_contains "$SANITIZER_PROOF" '"failureSignatures": false'
assert_json_number_at_least "$SANITIZER_PROOF" actualTotalAllocations 1
assert_json_number_at_least "$SANITIZER_PROOF" actualGcCollections 1

"$PACKAGE_BIN" report target >/dev/null
REPORT=target/.javan/reports/report.json
assert_contains "$REPORT" '"name": "sanitizer-proof"'
assert_contains "$REPORT" '"kind": "self-host"'
assert_contains "$REPORT" '"counterCheck": "true"'
assert_contains "$REPORT" '"actualLiveAllocations": 0'
assert_contains "$REPORT" '"actualLiveBytes": 0'
assert_contains "$REPORT" '"failureSignatures": "false"'
assert_json_number_at_least "$REPORT" actualTotalAllocations 1
assert_json_number_at_least "$REPORT" actualGcCollections 1

printf '%s\n' "Verified CI package smoke with $PACKAGE_BIN"
