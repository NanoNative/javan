#!/bin/sh
set -eu

ROOT=${1:-src/test/resources/projects/real-probes}

if [ ! -d "$ROOT" ]; then
  printf '%s\n' "Missing real-probes directory: $ROOT" >&2
  exit 1
fi

tmp=${TMPDIR:-/tmp}/javan-real-probe-artifacts-$$
trap 'rm -f "$tmp"' EXIT HUP INT TERM
: >"$tmp"

for probe_dir in $(find "$ROOT" -mindepth 1 -maxdepth 1 -type d | sort); do
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

  printf '%s:%s:%s\n' "$group_id" "$artifact_id" "$version" >>"$tmp"
done

sort -u "$tmp"
