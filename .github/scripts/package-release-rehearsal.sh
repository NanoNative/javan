#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
ARCHIVE=${1:-}
TARGET=${2:-}
COMMIT=${3:-$(git -C "$ROOT" rev-parse HEAD)}

usage() {
  printf '%s\n' "Usage: .github/scripts/package-release-rehearsal.sh <javan-package.tar.gz> <target> [commit]" >&2
  exit 2
}

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

if [ ! -d "$ROOT/target/classes/javan" ]; then
  printf '%s\n' "Missing compiled Javan classes under target/classes; run Maven compile first." >&2
  exit 2
fi
if ! command -v javac >/dev/null 2>&1; then
  printf '%s\n' "Missing javac required to package compiled rehearsal fixtures." >&2
  exit 2
fi

WORK=${TMPDIR:-/tmp}/javan-rehearsal-package-$$
BUNDLE_NAME=$PACKAGE_NAME-rehearsal
BUNDLE_ROOT=$WORK/$BUNDLE_NAME
BUNDLE=$ARCHIVE_DIR/$BUNDLE_NAME.tar.gz
mkdir -p "$BUNDLE_ROOT"
trap 'rm -rf "$WORK"' EXIT HUP INT TERM

compile_fixture() {
  source_root=$1
  classes=$2
  sources=$WORK/$(basename "$classes").sources
  mkdir -p "$classes"
  find "$source_root" -name '*.java' -type f | sort >"$sources"
  if [ ! -s "$sources" ]; then
    printf '%s\n' "Missing Java sources for rehearsal fixture: $source_root" >&2
    exit 1
  fi
  javac -d "$classes" @"$sources"
}

mkdir -p "$BUNDLE_ROOT/self-host"
cp -R "$ROOT/target/classes" "$BUNDLE_ROOT/self-host/classes"
compile_fixture "$ROOT/src/test/resources/projects/acceptance/hello/src/main/java" "$BUNDLE_ROOT/acceptance/classes"
compile_fixture "$ROOT/src/test/resources/projects/acceptance/native-library/src/main/java" "$BUNDLE_ROOT/abi/classes"
mkdir -p "$BUNDLE_ROOT/abi"
cp "$ROOT/src/test/resources/projects/acceptance/native-library/caller.c" "$BUNDLE_ROOT/abi/caller.c"
mkdir -p "$BUNDLE_ROOT/src/test/resources/projects/native-profile/memory-soak"
cp -R "$ROOT/src/test/resources/projects/native-profile/memory-soak/src" "$BUNDLE_ROOT/src/test/resources/projects/native-profile/memory-soak/src"

mkdir -p "$BUNDLE_ROOT/.github/scripts"
for script in \
  rehearse-release-artifact.sh \
  verify-package.sh \
  platform-target.sh \
  sanitizer-smoke.sh \
  sanitizer-common.sh \
  generated-sources.sh; do
  cp "$ROOT/.github/scripts/$script" "$BUNDLE_ROOT/.github/scripts/$script"
done
chmod +x "$BUNDLE_ROOT/.github/scripts/rehearse-release-artifact.sh" "$BUNDLE_ROOT/.github/scripts/verify-package.sh"
printf '%s\n' "$COMMIT" >"$BUNDLE_ROOT/COMMIT"
printf '%s\n' "$TARGET" >"$BUNDLE_ROOT/TARGET"
printf '%s\n' 'macos-x64,windows-x64,windows-aarch64,TLS,arbitrary external-library support' >"$BUNDLE_ROOT/UNSUPPORTED"

rm -f "$BUNDLE" "$BUNDLE.sha256"
tar -C "$WORK" -czf "$BUNDLE" "$BUNDLE_NAME"
if command -v shasum >/dev/null 2>&1; then
  (cd "$ARCHIVE_DIR" && shasum -a 256 "$BUNDLE_NAME.tar.gz" >"$BUNDLE_NAME.tar.gz.sha256")
elif command -v sha256sum >/dev/null 2>&1; then
  (cd "$ARCHIVE_DIR" && sha256sum "$BUNDLE_NAME.tar.gz" >"$BUNDLE_NAME.tar.gz.sha256")
else
  printf '%s\n' "No SHA-256 verifier found." >&2
  exit 1
fi

printf '%s\n' "$BUNDLE"
