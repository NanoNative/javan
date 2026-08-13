#!/bin/sh

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

json_string() {
  printf '"%s"' "$(json_escape "$1")"
}

json_number_or_null() {
  if [ -n "$1" ]; then
    printf '%s' "$1"
  else
    printf '%s' null
  fi
}

configure_asan_options() {
  sanitizer_required=$1
  sanitizer_description=$2
  case "${ASAN_OPTIONS:-}" in
    *detect_leaks=0*)
      if [ "$sanitizer_required" = "true" ]; then
        printf '%s\n' "$sanitizer_description cannot inherit ASAN_OPTIONS with detect_leaks=0" >&2
        exit 1
      fi
      ;;
  esac
  if [ -n "${ASAN_OPTIONS:-}" ]; then
    case "$ASAN_OPTIONS" in
      *detect_leaks=*) ;;
      *) ASAN_OPTIONS=detect_leaks=1:$ASAN_OPTIONS ;;
    esac
  else
    ASAN_OPTIONS=detect_leaks=1:halt_on_error=1
  fi
  unset sanitizer_required sanitizer_description
}
