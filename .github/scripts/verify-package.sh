#!/bin/sh
set -eu

ARCHIVE=${1:-}
if [ -z "$ARCHIVE" ] || [ ! -f "$ARCHIVE" ]; then
  printf '%s\n' "Usage: .github/scripts/verify-package.sh dist/release/javan-<version>-<os>-<arch>.tar.gz" >&2
  exit 2
fi

if [ ! -f "$ARCHIVE.sha256" ]; then
  printf '%s\n' "Missing checksum: $ARCHIVE.sha256" >&2
  exit 1
fi

ARCHIVE_DIR=$(CDPATH= cd -- "$(dirname -- "$ARCHIVE")" && pwd)
ARCHIVE_NAME=$(basename "$ARCHIVE")
if command -v shasum >/dev/null 2>&1; then
  (cd "$ARCHIVE_DIR" && shasum -a 256 -c "$ARCHIVE_NAME.sha256") >/dev/null
elif command -v sha256sum >/dev/null 2>&1; then
  (cd "$ARCHIVE_DIR" && sha256sum -c "$ARCHIVE_NAME.sha256") >/dev/null
else
  printf '%s\n' "No SHA-256 verifier found." >&2
  exit 1
fi

EXPECTED_ROOT=${ARCHIVE_NAME%.tar.gz}
case "$EXPECTED_ROOT" in
  *-windows-*) BIN_NAME=javan.exe ;;
  *) BIN_NAME=javan ;;
esac
LIST=$ARCHIVE_DIR/$EXPECTED_ROOT.contents
tar -tzf "$ARCHIVE" >"$LIST"
if grep -E '(^/|(^|/)\.\.($|/))' "$LIST" >/dev/null 2>&1; then
  printf '%s\n' "Archive contains unsafe paths." >&2
  exit 1
fi
if tar -tvzf "$ARCHIVE" | grep -E '^[lh]' >/dev/null 2>&1; then
  printf '%s\n' "Archive contains unsupported link entries." >&2
  exit 1
fi
if grep -v -E "^$EXPECTED_ROOT/$|^$EXPECTED_ROOT/bin/$|^$EXPECTED_ROOT/bin/$BIN_NAME$|^$EXPECTED_ROOT/README.md$|^$EXPECTED_ROOT/VERSION$|^$EXPECTED_ROOT/LICENSE$" "$LIST" >/dev/null 2>&1; then
  printf '%s\n' "Archive contains unexpected files." >&2
  cat "$LIST" >&2
  exit 1
fi
for required in "$EXPECTED_ROOT/" "$EXPECTED_ROOT/bin/" "$EXPECTED_ROOT/bin/$BIN_NAME" "$EXPECTED_ROOT/README.md" "$EXPECTED_ROOT/VERSION"; do
  if ! grep -Fx "$required" "$LIST" >/dev/null 2>&1; then
    printf '%s\n' "Archive is missing $required." >&2
    exit 1
  fi
done

TMP=${TMPDIR:-/tmp}/javan-package-$$
mkdir -p "$TMP"
trap 'rm -rf "$TMP" "$LIST"' EXIT HUP INT TERM

tar -xzf "$ARCHIVE" -C "$TMP"
ROOT="$TMP/$EXPECTED_ROOT"
if [ -z "$ROOT" ] || [ ! -x "$ROOT/bin/$BIN_NAME" ]; then
  printf '%s\n' "Archive does not contain an executable bin/$BIN_NAME." >&2
  exit 1
fi
if [ ! -f "$ROOT/VERSION" ]; then
  printf '%s\n' "Archive does not contain VERSION." >&2
  exit 1
fi

VERSION=$(cat "$ROOT/VERSION")
if ! printf '%s\n' "$VERSION" | grep -Eq '^[0-9]{4}[.]([1-9]|1[0-2])[.]([1-9]|[12][0-9]|3[01])(-SNAPSHOT)?$'; then
  printf '%s\n' "Package version must use YYYY.M.D or YYYY.M.D-SNAPSHOT without leading zeroes: $VERSION" >&2
  exit 1
fi

PACKAGE_BIN=$ROOT/bin/$BIN_NAME
ACTUAL_VERSION=$("$PACKAGE_BIN" --version)
if ! printf '%s\n' "$ACTUAL_VERSION" | grep -F "javan $VERSION" >/dev/null; then
  printf '%s\n' "Package executable version does not match VERSION: expected javan $VERSION, got $ACTUAL_VERSION" >&2
  exit 1
fi
ACTUAL_HELP=$("$PACKAGE_BIN" --help)
if ! printf '%s\n' "$ACTUAL_HELP" | grep -F "javan $VERSION" >/dev/null; then
  printf '%s\n' "Package help does not match VERSION: expected javan $VERSION." >&2
  exit 1
fi
"$PACKAGE_BIN" --help >/dev/null

if [ "$BIN_NAME" = "javan" ]; then
  FACADE_HOME=$TMP/facade-home
  HOME=$FACADE_HOME JAVAN_HOME=$FACADE_HOME/.javan "$PACKAGE_BIN" install >"$TMP/facade-install.out"
  JDK_HOME=$(sed -n 's/^  jdk home: //p' "$TMP/facade-install.out")
  if [ -z "$JDK_HOME" ] || [ ! -x "$JDK_HOME/bin/java" ] || [ ! -x "$JDK_HOME/bin/javac" ]; then
    printf '%s\n' "javan install did not publish an executable JDK facade." >&2
    cat "$TMP/facade-install.out" >&2
    exit 1
  fi
  "$JDK_HOME/bin/java" --version >"$TMP/facade-java.out" 2>"$TMP/facade-java.err"
  "$JDK_HOME/bin/java" jdk list >"$TMP/facade-jdk-list.out"
  grep -F "Javan facade" "$TMP/facade-java.out" >/dev/null
  grep -F "JDKs" "$TMP/facade-jdk-list.out" >/dev/null
fi

printf '%s\n' "Verified package $ARCHIVE"
