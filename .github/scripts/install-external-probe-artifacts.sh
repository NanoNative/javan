#!/bin/sh
set -eu

REPO=${1:-${JAVAN_MAVEN_REPO:-${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}}}
ARTIFACT_ROOT=${2:-src/test/resources/external-artifacts}
PROBE_ROOT=${3:-src/test/resources/external-probes}
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

mkdir -p "$REPO"

if [ ! -d "$ARTIFACT_ROOT" ]; then
  printf '%s\n' "Missing external-artifacts directory: $ARTIFACT_ROOT" >&2
  exit 1
fi

if [ ! -d "$PROBE_ROOT" ]; then
  printf '%s\n' "Missing external-probes directory: $PROBE_ROOT" >&2
  exit 1
fi

tmp=${TMPDIR:-/tmp}/javan-install-external-probes-$$
trap 'rm -rf "$tmp"' EXIT HUP INT TERM

"$SCRIPT_DIR/list-external-probe-artifacts.sh" "$PROBE_ROOT" | while IFS=: read -r group_id artifact_id version; do
  source_root=$ARTIFACT_ROOT/$artifact_id/$version/src/main/java
  if [ ! -d "$source_root" ]; then
    printf '%s\n' "Missing bundled sources for $group_id:$artifact_id:$version under $source_root" >&2
    exit 1
  fi

  work=$tmp/$artifact_id-$version
  classes=$work/classes
  mkdir -p "$classes"
  find "$source_root" -name '*.java' | sort >"$work/sources.txt"
  if [ ! -s "$work/sources.txt" ]; then
    printf '%s\n' "No Java sources found for $group_id:$artifact_id:$version under $source_root" >&2
    exit 1
  fi

  javac --release 17 -d "$classes" @"$work/sources.txt"

  jar_path=$REPO/$(printf '%s' "$group_id" | tr '.' '/')/$artifact_id/$version/$artifact_id-$version.jar
  mkdir -p "$(dirname "$jar_path")"
  jar --create --file "$jar_path" -C "$classes" .
done
