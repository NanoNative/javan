#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

VERSION=${1:-}
POM_VERSION=$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -n 1)
if [ -z "$VERSION" ]; then
  VERSION=$POM_VERSION
fi
VERSION=${VERSION#v}
if [ -z "$VERSION" ]; then
  printf '%s\n' "Could not resolve project version." >&2
  exit 1
fi
if [ "$VERSION" != "$POM_VERSION" ]; then
  printf '%s\n' "Requested version $VERSION does not match pom.xml version $POM_VERSION." >&2
  exit 1
fi
if ! printf '%s\n' "$VERSION" | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' >/dev/null 2>&1; then
  printf '%s\n' "Release version must be a numeric triplet such as 2026.6.14: $VERSION" >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin) OS=macos ;;
  Linux) OS=linux ;;
  MINGW*|MSYS*|CYGWIN*) OS=windows ;;
  *) OS=$(uname -s | tr '[:upper:]' '[:lower:]') ;;
esac

case "$(uname -m)" in
  x86_64|amd64) ARCH=x64 ;;
  arm64|aarch64) ARCH=aarch64 ;;
  *) ARCH=$(uname -m | tr '[:upper:]' '[:lower:]') ;;
esac
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
