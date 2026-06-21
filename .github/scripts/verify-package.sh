#!/bin/sh
set -eu

ARCHIVE=${1:-}
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
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
LIST=$ARCHIVE_DIR/$EXPECTED_ROOT.contents
tar -tzf "$ARCHIVE" >"$LIST"
if grep -E '(^/|(^|/)\.\.($|/))' "$LIST" >/dev/null 2>&1; then
  printf '%s\n' "Archive contains unsafe paths." >&2
  exit 1
fi
if grep -v -E "^$EXPECTED_ROOT/$|^$EXPECTED_ROOT/bin/$|^$EXPECTED_ROOT/bin/javan$|^$EXPECTED_ROOT/README.md$|^$EXPECTED_ROOT/VERSION$|^$EXPECTED_ROOT/LICENSE$" "$LIST" >/dev/null 2>&1; then
  printf '%s\n' "Archive contains unexpected files." >&2
  cat "$LIST" >&2
  exit 1
fi
for required in "$EXPECTED_ROOT/" "$EXPECTED_ROOT/bin/" "$EXPECTED_ROOT/bin/javan" "$EXPECTED_ROOT/README.md" "$EXPECTED_ROOT/VERSION"; do
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
if [ -z "$ROOT" ] || [ ! -x "$ROOT/bin/javan" ]; then
  printf '%s\n' "Archive does not contain an executable bin/javan." >&2
  exit 1
fi
if [ ! -f "$ROOT/VERSION" ]; then
  printf '%s\n' "Archive does not contain VERSION." >&2
  exit 1
fi

VERSION=$(cat "$ROOT/VERSION")
if ! printf '%s\n' "$VERSION" | grep -Eq '^[0-9]+([.][0-9]+){1,3}$'; then
  printf '%s\n' "Release version must be a final numeric version: $VERSION" >&2
  exit 1
fi

"$ROOT/bin/javan" --version | grep -F "javan $VERSION" >/dev/null
"$ROOT/bin/javan" --help | grep -F "javan $VERSION" >/dev/null
"$ROOT/bin/javan" --help >/dev/null
JAVAN_BIN=$ROOT/bin/javan sh "$REPO_ROOT/.github/scripts/verify-showcase.sh" >/dev/null

printf '%s\n' "Verified package $ARCHIVE"
