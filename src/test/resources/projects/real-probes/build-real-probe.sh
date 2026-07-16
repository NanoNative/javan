#!/bin/sh
set -eu

ROOT=${1:-$(pwd)}
ROOT=$(CDPATH= cd -- "$ROOT" && pwd)
PROBE_FILE=$ROOT/probe.properties
TMP_ROOT=${TMPDIR:-/tmp}/javan-real-probe-$$
WORKDIR=$TMP_ROOT/project

mkdir -p "$TMP_ROOT"
mkdir -p "$WORKDIR"
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

if [ ! -f "$PROBE_FILE" ]; then
  printf '%s\n' "Missing probe metadata: $PROBE_FILE" >&2
  exit 2
fi

if [ -z "${JAVAN:-}" ]; then
  if [ -x "$ROOT/../../dist/javan" ]; then
    JAVAN=$ROOT/../../dist/javan
  else
    printf '%s\n' "Missing javan binary. Set JAVAN=/path/to/javan." >&2
    exit 2
  fi
fi

project=$(sed -n 's/^project=//p' "$PROBE_FILE" | head -n 1)
group_id=$(sed -n 's/^groupId=//p' "$PROBE_FILE" | head -n 1)
artifact_id=$(sed -n 's/^artifactId=//p' "$PROBE_FILE" | head -n 1)
version=$(sed -n 's/^version=//p' "$PROBE_FILE" | head -n 1)

if [ -z "$project" ] || [ -z "$group_id" ] || [ -z "$artifact_id" ] || [ -z "$version" ]; then
  printf '%s\n' "Incomplete probe metadata in $PROBE_FILE" >&2
  exit 2
fi

classpath=${JAVAN_PROBE_CLASSPATH:-}

if [ -z "$classpath" ] && [ -n "${JAVAN_PROBE_ARTIFACT:-}" ] && [ -f "$JAVAN_PROBE_ARTIFACT" ]; then
  classpath=$JAVAN_PROBE_ARTIFACT
fi

if [ -z "$classpath" ] && [ -n "${JAVAN_PROBE_CLASSES:-}" ] && [ -d "$JAVAN_PROBE_CLASSES" ]; then
  classpath=$JAVAN_PROBE_CLASSES
fi

if [ -z "$classpath" ]; then
  maven_repo=${JAVAN_MAVEN_REPO:-${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}}
  artifact_path=$maven_repo/$(printf '%s' "$group_id" | tr '.' '/')/$artifact_id/$version/$artifact_id-$version.jar
  if [ -f "$artifact_path" ]; then
    classpath=$artifact_path
  fi
fi

if [ -z "$classpath" ]; then
  printf '%s\n' "Probe dependency not found. Set JAVAN_PROBE_CLASSPATH, JAVAN_PROBE_ARTIFACT, JAVAN_PROBE_CLASSES, or JAVAN_MAVEN_REPO for $group_id:$artifact_id:$version." >&2
  exit 3
fi

cp -R "$ROOT/." "$WORKDIR"
rm -rf "$WORKDIR/.javan" "$WORKDIR/target" "$WORKDIR/build" "$WORKDIR/out"
"$JAVAN" build "$WORKDIR" --classpath "$classpath" --output "$project" >/dev/null
"$WORKDIR/.javan/bin/$project"
