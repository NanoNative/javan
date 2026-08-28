#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"
. .github/scripts/platform-target.sh

VERSION=${1:-}
if [ -z "$VERSION" ]; then
  VERSION=${JAVAN_VERSION:-}
fi
if [ -z "$VERSION" ]; then
  VERSION=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1)
fi
if [ -z "$VERSION" ]; then
  printf '%s\n' "Could not resolve project version." >&2
  exit 1
fi
if ! printf '%s\n' "$VERSION" | grep -E '^[0-9]{4}\.([1-9]|1[0-2])\.([1-9]|[12][0-9]|3[01])(-SNAPSHOT)?$' >/dev/null 2>&1; then
  printf '%s\n' "Package version must use YYYY.M.D or YYYY.M.D-SNAPSHOT without leading zeroes: $VERSION" >&2
  exit 1
fi

HOST_TARGET=$(javan_host_target)
OS=${HOST_TARGET%-*}
ARCH=${HOST_TARGET#*-}
if [ -n "${JAVAN_PACKAGE_TARGET:-}" ] && [ "$OS-$ARCH" != "$JAVAN_PACKAGE_TARGET" ]; then
  printf '%s\n' "Host target $OS-$ARCH does not match expected package target $JAVAN_PACKAGE_TARGET." >&2
  exit 1
fi

BIN=dist/javan
BIN_NAME=javan
if [ "$OS" = "windows" ]; then
  BIN=dist/javan.exe
  BIN_NAME=javan.exe
fi

if [ ! -x "$BIN" ]; then
  printf '%s\n' "Missing built native executable: $BIN" >&2
  printf '%s\n' "Run scripts/build.sh first." >&2
  exit 2
fi

PACKAGE="javan-$VERSION-$OS-$ARCH"
PACKAGE_DIR="dist/release/$PACKAGE"
ARCHIVE="dist/release/$PACKAGE.tar.gz"
ARCHIVE_NAME="$PACKAGE.tar.gz"

rm -rf dist/release
mkdir -p "$PACKAGE_DIR/bin"
cp "$BIN" "$PACKAGE_DIR/bin/$BIN_NAME"
cp README.md "$PACKAGE_DIR/README.md"
printf '%s\n' "$VERSION" >"$PACKAGE_DIR/VERSION"

if [ -f LICENSE ]; then
  cp LICENSE "$PACKAGE_DIR/LICENSE"
fi

tar -C dist/release -czf "$ARCHIVE" "$PACKAGE"
if command -v shasum >/dev/null 2>&1; then
  (cd dist/release && shasum -a 256 "$ARCHIVE_NAME" >"$ARCHIVE_NAME.sha256")
elif command -v sha256sum >/dev/null 2>&1; then
  (cd dist/release && sha256sum "$ARCHIVE_NAME" >"$ARCHIVE_NAME.sha256")
fi

printf '%s\n' "$ROOT/$ARCHIVE"
