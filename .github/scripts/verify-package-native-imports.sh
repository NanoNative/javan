#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
. "$ROOT/.github/scripts/platform-target.sh"

ARCHIVE=${1:-}

usage() {
  printf '%s\n' 'Usage: .github/scripts/verify-package-native-imports.sh <javan-package.tar.gz>' >&2
  exit 2
}

verify_checksum() {
  checksum=$1
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 -c "$checksum"
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c "$checksum"
  else
    printf '%s\n' 'No SHA-256 verifier found.' >&2
    exit 2
  fi
}

compile_fixture() {
  project=$1
  classes=$project/target/classes
  sources=$project/target/sources.txt
  mkdir -p "$project/target"
  find "$project/src/main/java" -name '*.java' -type f | sort >"$sources"
  javac -d "$classes" @"$sources"
  rm -f "$sources"
}

case "$ARCHIVE" in
  '') usage ;;
esac

TARGET=$(javan_host_target)
case "$TARGET" in
  linux-x64|linux-aarch64|macos-aarch64) ;;
  *)
    printf '%s\n' "Unsupported first-release package target: $TARGET" >&2
    exit 2
    ;;
esac

if [ ! -f "$ARCHIVE" ] || [ ! -f "$ARCHIVE.sha256" ]; then
  usage
fi

ARCHIVE_NAME=$(basename "$ARCHIVE")
ARCHIVE_DIR=$(CDPATH= cd -- "$(dirname -- "$ARCHIVE")" && pwd)
PACKAGE_NAME=${ARCHIVE_NAME%.tar.gz}
case "$ARCHIVE_NAME" in
  *-"$TARGET".tar.gz) ;;
  *)
    printf '%s\n' "Archive $ARCHIVE_NAME does not match host target $TARGET." >&2
    exit 2
    ;;
esac

(cd "$ARCHIVE_DIR" && verify_checksum "$ARCHIVE_NAME.sha256") >/dev/null
sh "$ROOT/.github/scripts/verify-package.sh" "$ARCHIVE"

WORK=${TMPDIR:-/tmp}/javan-package-native-imports-$$
trap 'rm -rf "$WORK"' EXIT HUP INT TERM
mkdir -p "$WORK"
tar -xzf "$ARCHIVE" -C "$WORK"
PACKAGE_BIN=$WORK/$PACKAGE_NAME/bin/javan
FIXTURES=$ROOT/src/test/resources/projects/acceptance

SUCCESS=$WORK/native-imports
cp -R "$FIXTURES/native-imports" "$SUCCESS"
compile_fixture "$SUCCESS"
"$PACKAGE_BIN" build "$SUCCESS" --main com.acme.Main --output native-imports >/dev/null
"$SUCCESS/.javan/bin/native-imports" >"$WORK/native-imports.out"
grep -Fx '28:3:3:11:5' "$WORK/native-imports.out" >/dev/null

INVALID=$WORK/native-import-invalid
cp -R "$FIXTURES/native-import-invalid" "$INVALID"
compile_fixture "$INVALID"
if "$PACKAGE_BIN" check "$INVALID" --main com.acme.Main >"$WORK/native-import-invalid.out" 2>"$WORK/native-import-invalid.err"; then
  printf '%s\n' 'Reachable invalid native import unexpectedly passed package check.' >&2
  exit 1
fi
grep -F 'error[JAVAN013]: native import ABI is not supported' "$WORK/native-import-invalid.err" >/dev/null
grep -F 'Use only the supported native import ABI.' "$WORK/native-import-invalid.err" >/dev/null

printf '%s\n' "Verified package-native imports with $PACKAGE_BIN for $TARGET"
