#!/bin/sh
set -eu

REPO=${1:-${JAVAN_MAVEN_REPO:-${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}}}
ARTIFACT_ROOT=${2:-src/test/resources/external-artifacts}
PROBE_ROOT=${3:-src/test/resources/external-probes}

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
coordinates=$tmp/coordinates
mkdir -p "$tmp"
: >"$coordinates"

find "$PROBE_ROOT" -mindepth 1 -maxdepth 1 -type d | sort | while IFS= read -r probe_dir; do
  properties_file=$probe_dir/probe.properties
  if [ ! -f "$properties_file" ]; then
    printf '%s\n' "Missing probe metadata: $properties_file" >&2
    exit 1
  fi

  group_id=$(sed -n 's/^groupId=//p' "$properties_file" | head -n 1)
  artifact_id=$(sed -n 's/^artifactId=//p' "$properties_file" | head -n 1)
  version=$(sed -n 's/^version=//p' "$properties_file" | head -n 1)
  if [ -z "$group_id" ] || [ -z "$artifact_id" ] || [ -z "$version" ]; then
    printf '%s\n' "Incomplete probe metadata in $properties_file" >&2
    exit 1
  fi
  printf '%s:%s:%s\n' "$group_id" "$artifact_id" "$version" >>"$coordinates"
done

sort -u "$coordinates" | while IFS=: read -r group_id artifact_id version; do
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
