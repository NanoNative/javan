#!/bin/sh

javan_generated_sources() {
  generated=$1
  manifest=$generated/javan_program.sources
  if [ ! -f "$manifest" ]; then
    printf '%s\n' "Missing generated source manifest: $manifest" >&2
    return 1
  fi

  exec 3<"$manifest"
  IFS= read -r version <&3 || true
  if [ "$version" != "javan-generated-sources-v1" ]; then
    exec 3<&-
    printf '%s\n' "Unsupported generated source manifest: $manifest" >&2
    return 1
  fi

  sources=
  relative_sources=
  while IFS= read -r source <&3 || [ -n "$source" ]; do
    case "$source" in
      ''|-*|/*|./*|../*|*/../*|*/..|*/./*|*//*|*[!A-Za-z0-9_./-]*)
        exec 3<&-
        printf '%s\n' "Invalid generated source entry: $source" >&2
        return 1
        ;;
    esac
    case "$source" in
      *.c) ;;
      *)
        exec 3<&-
        printf '%s\n' "Generated source entry is not C: $source" >&2
        return 1
        ;;
    esac
    case " $relative_sources " in
      *" $source "*)
        exec 3<&-
        printf '%s\n' "Duplicate generated source entry: $source" >&2
        return 1
        ;;
    esac
    if [ ! -f "$generated/$source" ]; then
      exec 3<&-
      printf '%s\n' "Missing generated C source: $generated/$source" >&2
      return 1
    fi
    relative_sources="$relative_sources $source"
    sources="$sources $source"
  done
  exec 3<&-

  case "$relative_sources" in
    ' main.c'|' main.c '*) ;;
    *)
      printf '%s\n' "Generated source manifest must start with main.c: $manifest" >&2
      return 1
      ;;
  esac
  printf '%s\n' "${sources# }"
}

javan_copy_generated_sources() {
  source_root=$1
  target_root=$2
  source_files=$(javan_generated_sources "$source_root")
  if [ -L "$target_root" ]; then
    printf '%s\n' "Generated source destination must not be a symbolic link: $target_root" >&2
    return 1
  fi
  mkdir -p "$target_root"
  rm -rf "$target_root/units"
  for source_file in $source_files; do
    mkdir -p "$(dirname "$target_root/$source_file")"
    rm -f "$target_root/$source_file"
    cp "$source_root/$source_file" "$target_root/$source_file"
  done
  for source_file in javan_program.h javan_runtime.c javan_runtime.h; do
    if [ ! -f "$source_root/$source_file" ]; then
      printf '%s\n' "Missing generated source dependency: $source_root/$source_file" >&2
      return 1
    fi
    rm -f "$target_root/$source_file"
    cp "$source_root/$source_file" "$target_root/$source_file"
  done
  rm -f "$target_root/javan_program.sources"
  cp "$source_root/javan_program.sources" "$target_root/javan_program.sources"
}

javan_compare_generated_sources() {
  expected_root=$1
  actual_root=$2
  expected_sources=$(javan_generated_sources "$expected_root")
  actual_sources=$(javan_generated_sources "$actual_root")
  if [ "$expected_sources" != "$actual_sources" ]; then
    printf '%s\n' "Generated source manifests differ" >&2
    return 1
  fi
  for source_file in javan_program.h javan_program.sources javan_runtime.c javan_runtime.h $expected_sources; do
    if ! cmp "$expected_root/$source_file" "$actual_root/$source_file" >/dev/null; then
      printf '%s\n' "Generated source differs: $source_file" >&2
      return 1
    fi
  done
}
