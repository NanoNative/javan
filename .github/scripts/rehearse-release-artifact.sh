#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/platform-target.sh"

ARCHIVE=
TARGET=

usage() {
  printf '%s\n' 'Usage: rehearse-release-artifact.sh --archive <javan-package.tar.gz> --target <target>' >&2
  exit 2
}

reject_publication_option() {
  printf '%s\n' "Artifact rehearsal does not accept publication control: $1" >&2
  exit 2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --archive)
      [ "$#" -ge 2 ] || usage
      ARCHIVE=$2
      shift 2
      ;;
    --target)
      [ "$#" -ge 2 ] || usage
      TARGET=$2
      shift 2
      ;;
    --upload|--tag|--release|--credential|--token)
      reject_publication_option "$1"
      ;;
    *) usage ;;
  esac
done

case "$TARGET" in
  linux-x64|linux-aarch64|macos-aarch64) ;;
  *)
    printf '%s\n' "Unsupported first-release rehearsal target: $TARGET" >&2
    exit 2
    ;;
esac
if [ -z "$ARCHIVE" ] || [ ! -f "$ARCHIVE" ]; then
  usage
fi

verify_checksum() {
  file=$1
  checksum=$file.sha256
  if [ ! -f "$checksum" ]; then
    printf '%s\n' "Missing checksum: $checksum" >&2
    exit 1
  fi
  directory=$(CDPATH= cd -- "$(dirname -- "$file")" && pwd)
  name=$(basename "$file")
  if command -v shasum >/dev/null 2>&1; then
    (cd "$directory" && shasum -a 256 -c "$name.sha256") >/dev/null
  elif command -v sha256sum >/dev/null 2>&1; then
    (cd "$directory" && sha256sum -c "$name.sha256") >/dev/null
  else
    printf '%s\n' 'No SHA-256 verifier found.' >&2
    exit 1
  fi
}

extract_safe() {
  archive=$1
  destination=$2
  listing=$destination.contents
  tar -tzf "$archive" >"$listing"
  if grep -E '(^/|(^|/)\.\.($|/))' "$listing" >/dev/null 2>&1; then
    printf '%s\n' "Archive contains unsafe paths: $archive" >&2
    exit 1
  fi
  if tar -tvzf "$archive" | grep -E '^[lh]' >/dev/null 2>&1; then
    printf '%s\n' "Archive contains unsupported link entries: $archive" >&2
    exit 1
  fi
  tar -xzf "$archive" -C "$destination"
}

json_string() {
  value=$(printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g')
  printf '"%s"' "$value"
}

ARCHIVE_DIR=$(CDPATH= cd -- "$(dirname -- "$ARCHIVE")" && pwd)
ARCHIVE_NAME=$(basename "$ARCHIVE")
PACKAGE_NAME=${ARCHIVE_NAME%.tar.gz}
case "$ARCHIVE_NAME" in
  *-"$TARGET".tar.gz) ;;
  *)
    printf '%s\n' "Archive $ARCHIVE_NAME does not match rehearsal target $TARGET." >&2
    exit 1
    ;;
esac
HOST_TARGET=$(javan_host_target)
if [ "$HOST_TARGET" != "$TARGET" ]; then
  printf '%s\n' "Host target $HOST_TARGET does not match requested rehearsal target $TARGET." >&2
  exit 1
fi

BUNDLE_NAME=$PACKAGE_NAME-rehearsal
BUNDLE=$ARCHIVE_DIR/$BUNDLE_NAME.tar.gz
if [ ! -f "$BUNDLE" ]; then
  printf '%s\n' "Missing matching rehearsal sidecar: $BUNDLE" >&2
  exit 1
fi

TMP=${TMPDIR:-/tmp}/javan-release-rehearsal-$$
PASSED=$TMP/passed
REPORT=$ARCHIVE_DIR/$PACKAGE_NAME.rehearsal.json
MARKDOWN=$ARCHIVE_DIR/$PACKAGE_NAME.rehearsal.md
BUNDLE_ROOT=
mkdir -p "$TMP"
: >"$PASSED"
status=fail

finish() {
  code=$?
  trap - EXIT HUP INT TERM
  if [ "$code" -eq 0 ]; then
    status=pass
  fi
  checks=$(paste -sd, "$PASSED" 2>/dev/null || true)
  commit=$(cat "$BUNDLE_ROOT/COMMIT" 2>/dev/null || printf unknown)
  unsupported=$(cat "$BUNDLE_ROOT/UNSUPPORTED" 2>/dev/null || printf unknown)
  toolchain=$(cc --version 2>/dev/null | sed -n '1p' || printf unknown)
  cat >"$REPORT" <<EOF
{
  "schemaVersion": 1,
  "status": $(json_string "$status"),
  "commit": $(json_string "$commit"),
  "target": $(json_string "$TARGET"),
  "archive": $(json_string "$ARCHIVE_NAME"),
  "sidecar": $(json_string "$(basename "$BUNDLE")"),
  "toolchain": $(json_string "$toolchain"),
  "passedChecks": $(json_string "$checks"),
  "skips": $(json_string "$unsupported"),
  "publication": "disabled"
}
EOF
  cat >"$MARKDOWN" <<EOF
# Javan Release Artifact Rehearsal

- status: \`$status\`
- commit: \`$commit\`
- target: \`$TARGET\`
- archive: \`$ARCHIVE_NAME\`
- sidecar: \`$(basename "$BUNDLE")\`
- toolchain: \`$toolchain\`
- passed checks: \`$checks\`
- skips: \`$unsupported\`
- publication: \`disabled\`
EOF
  rm -rf "$TMP"
  exit "$code"
}
trap finish EXIT
trap 'exit 130' HUP INT TERM

verify_checksum "$ARCHIVE"
verify_checksum "$BUNDLE"
extract_safe "$ARCHIVE" "$TMP"
extract_safe "$BUNDLE" "$TMP"
PACKAGE_ROOT=$TMP/$PACKAGE_NAME
BUNDLE_ROOT=$TMP/$BUNDLE_NAME
if [ ! -d "$PACKAGE_ROOT" ] || [ ! -d "$BUNDLE_ROOT" ]; then
  printf '%s\n' 'Archive roots do not match their file names.' >&2
  exit 1
fi
if [ "$(cat "$BUNDLE_ROOT/TARGET")" != "$TARGET" ]; then
  printf '%s\n' "Sidecar target does not match requested rehearsal target $TARGET." >&2
  exit 1
fi

"$BUNDLE_ROOT/.github/scripts/verify-package.sh" "$ARCHIVE"
printf '%s\n' package >>"$PASSED"
PACKAGE_BIN=$PACKAGE_ROOT/bin/javan
VERSION=$(cat "$PACKAGE_ROOT/VERSION")

SELF_HOST=$BUNDLE_ROOT/self-host
SELF_HOST_BIN=$SELF_HOST/.javan/bin/javan-rehearsal-selfhost
if ! "$PACKAGE_BIN" build "$SELF_HOST/classes" --main javan.Main --output javan-rehearsal-selfhost \
  >"$TMP/self-host-build.out" 2>"$TMP/self-host-build.err"; then
  cat "$TMP/self-host-build.out" >&2
  cat "$TMP/self-host-build.err" >&2
  exit 1
fi
if [ ! -x "$SELF_HOST_BIN" ]; then
  printf '%s\n' "Self-host build did not create $SELF_HOST_BIN" >&2
  cat "$TMP/self-host-build.out" >&2
  exit 1
fi
if ! "$SELF_HOST_BIN" --version >"$TMP/self-host-version.out" 2>"$TMP/self-host-version.err"; then
  cat "$TMP/self-host-version.out" >&2
  cat "$TMP/self-host-version.err" >&2
  exit 1
fi
if ! grep -F "javan $VERSION" "$TMP/self-host-version.out" >/dev/null; then
  printf '%s\n' "Self-host executable version does not match package VERSION: expected javan $VERSION" >&2
  cat "$TMP/self-host-version.out" >&2
  exit 1
fi
printf '%s\n' self-host >>"$PASSED"

ACCEPTANCE=$BUNDLE_ROOT/acceptance
"$PACKAGE_BIN" build "$ACCEPTANCE/classes" --main com.acme.Main --output javan-rehearsal-acceptance >/dev/null
"$ACCEPTANCE/.javan/bin/javan-rehearsal-acceptance" >"$TMP/acceptance.out"
grep -Fx 'Hello from javan' "$TMP/acceptance.out" >/dev/null
printf '%s\n' acceptance >>"$PASSED"

ABI=$BUNDLE_ROOT/abi
"$PACKAGE_BIN" build "$ABI/classes" --library --format static \
  --output native-library \
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
cc "$ABI/caller.c" "$ABI/.javan/dist/libnative-library.a" -o "$TMP/abi-caller"
JAVAN_HEAP_LIMIT_BYTES=2048 "$TMP/abi-caller" >"$TMP/abi.out"
for expected in '10' 'try-add:1:10' 'Hi Yuna' 'retained-bytes:3:1:4' 'byte-result-error:negative byte array length'; do
  grep -Fx "$expected" "$TMP/abi.out" >/dev/null
done
printf '%s\n' abi >>"$PASSED"

SANITIZER_PROJECT=src/test/resources/projects/native-profile/memory-soak
if ! (
  cd "$BUNDLE_ROOT"
  JAVAN_BIN=$PACKAGE_BIN \
  JAVAN_SANITIZER_REQUIRED=true \
  JAVAN_SANITIZER_COUNTER_CHECK=true \
  JAVAN_SANITIZER_MAX_LIVE_ALLOCATIONS=0 \
  JAVAN_SANITIZER_MAX_LIVE_BYTES=0 \
  JAVAN_SANITIZER_MIN_TOTAL_ALLOCATIONS=5000 \
  JAVAN_SANITIZER_MIN_GC_COLLECTIONS=1 \
  JAVAN_SANITIZER_MIN_GC_COLLECTED_ALLOCATIONS=5000 \
  JAVAN_GC_STRESS=1 \
  JAVAN_GC_SAFEPOINT_INTERVAL=1 \
    sh .github/scripts/sanitizer-smoke.sh "$SANITIZER_PROJECT"
) >"$TMP/sanitizer.out" 2>"$TMP/sanitizer.err"; then
  cat "$TMP/sanitizer.out" >&2
  cat "$TMP/sanitizer.err" >&2
  exit 1
fi
SANITIZER_PROOF=$BUNDLE_ROOT/$SANITIZER_PROJECT/.javan/reports/sanitizer-proof.json
grep -F '"status": "pass"' "$SANITIZER_PROOF" >/dev/null
grep -F '"actualLiveAllocations": 0' "$SANITIZER_PROOF" >/dev/null
grep -F '"actualLiveBytes": 0' "$SANITIZER_PROOF" >/dev/null
printf '%s\n' sanitizer >>"$PASSED"

printf '%s\n' "Rehearsed release archive $ARCHIVE_NAME for $TARGET"
