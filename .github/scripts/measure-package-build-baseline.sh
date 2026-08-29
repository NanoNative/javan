#!/bin/sh
# shellcheck disable=SC1091
set -eu

# Measure only the extracted package binary. Fixture preparation and execution prove comparable
# input and correctness, but are deliberately outside each measured interval.
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"
. .github/scripts/timing-report.sh

ARCHIVE=${1:-}
if [ -z "$ARCHIVE" ] || [ ! -f "$ARCHIVE" ]; then
  printf '%s\n' "Usage: .github/scripts/measure-package-build-baseline.sh <javan-package.tar.gz>" >&2
  exit 2
fi
ARCHIVE=$(CDPATH='' cd -- "$(dirname -- "$ARCHIVE")" && pwd)/$(basename -- "$ARCHIVE")

TMP=${TMPDIR:-/tmp}/javan-package-build-baseline-$$
RESULTS=$TMP/results.tsv
TAB=$(printf '\t')
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

json_string() {
  printf '"'
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
  printf '"'
}

first_line() {
  "$@" 2>&1 | sed -n '1p' || true
}

require_number() {
  value=$1
  label=$2
  case "$value" in
    ''|*[!0-9]*)
      printf '%s\n' "Expected numeric $label, got: $value" >&2
      exit 1
      ;;
  esac
}

count_decisions() {
  report=$1
  decision=$2
  awk -v decision="$decision" 'index($0, "\"decision\": \"" decision "\"") { count++ } END { print count + 0 }' "$report"
}

require_cache_state() {
  cache_state=$1
  rebuilt=$2
  reused=$3
  case "$cache_state" in
    cold)
      [ "$rebuilt" -gt 0 ] && [ "$reused" -eq 0 ] || {
        printf '%s\n' 'Cold build did not rebuild every generated object.' >&2
        exit 1
      }
      ;;
    warm)
      [ "$rebuilt" -eq 0 ] && [ "$reused" -gt 0 ] || {
        printf '%s\n' 'Warm build did not reuse every generated object.' >&2
        exit 1
      }
      ;;
    changed-source)
      [ "$rebuilt" -gt 0 ] && [ "$reused" -gt 0 ] || {
        printf '%s\n' 'Changed-source build did not prove both invalidation and reuse.' >&2
        exit 1
      }
      ;;
    *)
      printf '%s\n' "Unsupported cache state: $cache_state" >&2
      exit 2
      ;;
  esac
}

compile_fixture() {
  rm -rf "$FIXTURE/target/classes"
  mkdir -p "$FIXTURE/target/classes"
  find "$FIXTURE/src/main/java" -type f -name '*.java' | sort > "$TMP/sources.txt"
  "$JAVAC" -d "$FIXTURE/target/classes" @"$TMP/sources.txt"
}

measure_build() {
  result_id=$1
  cache_state=$2
  expected_output=$3
  output_name=package-build-baseline-$result_id
  started=$(javan_timing_now)
  if javan_timing_measure "$PACKAGE_BIN" build "$FIXTURE/target/classes" --main com.acme.showcase.Main --output "$output_name" \
    > "$TMP/$result_id.stdout" 2> "$TMP/$result_id.stderr"; then
    status=pass
  else
    status=fail
  fi
  finished=$(javan_timing_now)
  if [ "$status" != pass ]; then
    cat "$TMP/$result_id.stderr" >&2
    exit 1
  fi

  artifact=$FIXTURE/target/.javan/bin/$output_name
  if [ ! -x "$artifact" ]; then
    printf '%s\n' "Missing package-built artifact: $artifact" >&2
    exit 1
  fi
  "$artifact" > "$TMP/$result_id.program"
  if ! grep -Fx "$expected_output" "$TMP/$result_id.program" >/dev/null 2>&1; then
    printf '%s\n' "Package-built artifact did not contain expected output: $expected_output" >&2
    cat "$TMP/$result_id.program" >&2
    exit 1
  fi

  cache_report=$FIXTURE/target/.javan/reports/native-object-cache.json
  if [ ! -f "$cache_report" ]; then
    printf '%s\n' "Missing native object cache report: $cache_report" >&2
    exit 1
  fi
  rebuilt=$(count_decisions "$cache_report" rebuilt)
  reused=$(count_decisions "$cache_report" reused)
  require_cache_state "$cache_state" "$rebuilt" "$reused"
  artifact_bytes=$(wc -c < "$artifact" | tr -d '[:space:]')
  require_number "$artifact_bytes" artifact-bytes

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$result_id" "$cache_state" "$((finished - started))" "$javan_timing_measure_cpu_seconds" \
    "$javan_timing_measure_max_rss_bytes" "$javan_timing_measure_source" "$artifact_bytes" "$rebuilt:$reused" \
    >> "$RESULTS"
}

sh .github/scripts/verify-package.sh "$ARCHIVE"
tar -xzf "$ARCHIVE" -C "$TMP"
PACKAGE_HOME=$TMP/$(basename "$ARCHIVE" .tar.gz)
PACKAGE_BIN=$PACKAGE_HOME/bin/javan
if [ ! -x "$PACKAGE_BIN" ]; then
  printf '%s\n' "Package does not contain executable bin/javan: $ARCHIVE" >&2
  exit 1
fi
PACKAGE_VERSION=$(cat "$PACKAGE_HOME/VERSION")
PACKAGE_NAME=$(basename "$ARCHIVE" .tar.gz)
PACKAGE_PREFIX=javan-$PACKAGE_VERSION-
case "$PACKAGE_NAME" in
  "$PACKAGE_PREFIX"*) PACKAGE_TARGET=${PACKAGE_NAME#"$PACKAGE_PREFIX"} ;;
  *)
    printf '%s\n' "Package name does not match VERSION: $ARCHIVE" >&2
    exit 1
    ;;
esac
if [ -n "${JAVAN_PACKAGE_TARGET:-}" ] && [ "$PACKAGE_TARGET" != "$JAVAN_PACKAGE_TARGET" ]; then
  printf '%s\n' "Package target $PACKAGE_TARGET does not match expected $JAVAN_PACKAGE_TARGET." >&2
  exit 1
fi

FIXTURE=$TMP/example
cp -R "$ROOT/example" "$FIXTURE"
JAVAC=${JAVAC:-javac}
if ! command -v "$JAVAC" >/dev/null 2>&1; then
  printf '%s\n' "Missing javac required to prepare the versioned example fixture: $JAVAC" >&2
  exit 1
fi
compile_fixture
: > "$RESULTS"

for iteration in 1 2 3; do
  rm -rf "$FIXTURE/target/.javan"
  measure_build "cold-$iteration" cold 'safe deterministic native build'
done
for iteration in 1 2 3; do
  measure_build "warm-$iteration" warm 'safe deterministic native build'
done

SOURCE=$FIXTURE/src/main/java/com/acme/showcase/Main.java
CHANGED_SOURCE=$TMP/Main.java
sed 's/safe deterministic native build/safe controlled source rebuild/' "$SOURCE" > "$CHANGED_SOURCE"
if cmp -s "$SOURCE" "$CHANGED_SOURCE"; then
  printf '%s\n' 'The controlled source-change fixture was not applied.' >&2
  exit 1
fi
mv "$CHANGED_SOURCE" "$SOURCE"
compile_fixture
measure_build changed-source changed-source 'safe controlled source rebuild'

COMMIT_SHA=${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || printf unknown)}
JDK=$(first_line java --version)
C_TOOLCHAIN=$(first_line "${CC:-cc}" --version)
RESULTS_DIR=${JAVAN_BASELINE_OUTPUT_DIR:-$ROOT/dist/release}
JSON=$RESULTS_DIR/javan-$PACKAGE_TARGET-package-build-baseline.json
MARKDOWN=$RESULTS_DIR/javan-$PACKAGE_TARGET-package-build-baseline.md
mkdir -p "$RESULTS_DIR"

{
  printf '{\n'
  printf '  "schemaVersion": 1,\n'
  printf '  "kind": "package-build-baseline",\n'
  printf '  "regressionPolicy": "none; results are comparative evidence, not universal thresholds",\n'
  printf '  "commitSha": '; json_string "$COMMIT_SHA"; printf ',\n'
  printf '  "target": '; json_string "$PACKAGE_TARGET"; printf ',\n'
  printf '  "packageVersion": '; json_string "$PACKAGE_VERSION"; printf ',\n'
  printf '  "fixture": {"path": "example", "mainClass": "com.acme.showcase.Main", "sourceChange": "Main.java: safe deterministic native build -> safe controlled source rebuild"},\n'
  printf '  "command": "bin/javan build target/classes --main com.acme.showcase.Main --output <name>",\n'
  printf '  "results": [\n'
  separator=''
  while IFS=$TAB read -r result_id cache_state wall_seconds cpu_seconds max_rss_bytes resource_source artifact_bytes cache_counts; do
    rebuilt=${cache_counts%%:*}
    reused=${cache_counts#*:}
    printf '%b    {"id": ' "$separator"; json_string "$result_id"
    printf ', "cacheState": '; json_string "$cache_state"
    printf ', "target": '; json_string "$PACKAGE_TARGET"
    printf ', "jdk": '; json_string "$JDK"
    printf ', "cToolchain": '; json_string "$C_TOOLCHAIN"
    printf ', "wallSeconds": %s, "cpuSeconds": ' "$wall_seconds"; json_string "$cpu_seconds"
    printf ', "peakRssBytes": '; json_string "$max_rss_bytes"
    printf ', "resourceSource": '; json_string "$resource_source"
    printf ', "artifactBytes": %s, "rebuiltObjects": %s, "reusedObjects": %s}' "$artifact_bytes" "$rebuilt" "$reused"
    separator=',\n'
  done < "$RESULTS"
  printf '\n  ]\n}\n'
} > "$JSON"

{
  printf '# Package-Backed Build Baseline\n\n'
  printf -- '- Commit: `%s`\n' "$COMMIT_SHA"
  printf -- '- Target: `%s`\n' "$PACKAGE_TARGET"
  printf -- '- Package version: `%s`\n' "$PACKAGE_VERSION"
  printf -- '- JDK: `%s`\n' "$JDK"
  printf -- '- C toolchain: `%s`\n' "$C_TOOLCHAIN"
  printf -- '- Fixture: `example` (`com.acme.showcase.Main`)\n'
  printf -- '- Measured command: `bin/javan build target/classes --main com.acme.showcase.Main --output <name>`\n'
  printf -- '- Regression policy: none; these measurements are comparative evidence, not universal thresholds.\n\n'
  printf '| Result | Cache state | Wall seconds | CPU seconds | Peak RSS bytes | Artifact bytes | Rebuilt | Reused |\n'
  printf '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |\n'
  while IFS=$TAB read -r result_id cache_state wall_seconds cpu_seconds max_rss_bytes resource_source artifact_bytes cache_counts; do
    rebuilt=${cache_counts%%:*}
    reused=${cache_counts#*:}
    printf '| `%s` | `%s` | %s | %s | %s | %s | %s | %s |\n' \
      "$result_id" "$cache_state" "$wall_seconds" "$cpu_seconds" "$max_rss_bytes" "$artifact_bytes" "$rebuilt" "$reused"
  done < "$RESULTS"
} > "$MARKDOWN"

printf 'Package build baseline: %s\n' "$JSON"
