#!/bin/sh

# Shared phase timing for native package proofs. The caller owns the log lifetime.
javan_timing_now() {
  date +%s
}

javan_timing_record() {
  phase=$1
  started=$2
  status=${3:-pass}
  counted=${4:-true}
  finished=$(javan_timing_now)
  seconds=$((finished - started))
  printf 'Timing: %s=%ss status=%s counted=%s\n' "$phase" "$seconds" "$status" "$counted"
  if [ -n "${JAVAN_TIMING_LOG:-}" ]; then
    printf '%s\t%s\t%s\t%s\n' "$phase" "$seconds" "$status" "$counted" >> "$JAVAN_TIMING_LOG"
  fi
}

javan_timing_run() {
  phase=$1
  shift
  started=$(javan_timing_now)
  if "$@"; then
    code=0
    status=pass
  else
    code=$?
    status=fail
  fi
  javan_timing_record "$phase" "$started" "$status" true
  return "$code"
}

javan_timing_write_reports() {
  target=$1
  generation=$2
  scope=$3
  json=$4
  markdown=$5
  status=$6
  exit_code=$7
  total=0
  separator=''
  commit_sha=${GITHUB_SHA:-unknown}
  version=${JAVAN_VERSION:-unknown}
  runner_os=${RUNNER_OS:-$(uname -s)}
  runner_arch=${RUNNER_ARCH:-$(uname -m)}
  sanitizer_scope=${JAVAN_PACKAGE_SANITIZER_SCOPE:-unknown}
  heap_limit_bytes=${JAVAN_HEAP_LIMIT_BYTES:-unknown}

  mkdir -p "$(dirname "$json")" "$(dirname "$markdown")"
  {
    printf '{\n'
    printf '  "schemaVersion": 1,\n'
    printf '  "commitSha": "%s",\n' "$commit_sha"
    printf '  "version": "%s",\n' "$version"
    printf '  "runnerOs": "%s",\n' "$runner_os"
    printf '  "runnerArch": "%s",\n' "$runner_arch"
    printf '  "target": "%s",\n' "$target"
    printf '  "bootstrapGeneration": %s,\n' "$generation"
    printf '  "proofScope": "%s",\n' "$scope"
    printf '  "sanitizerScope": "%s",\n' "$sanitizer_scope"
    printf '  "heapLimitBytes": "%s",\n' "$heap_limit_bytes"
    printf '  "status": "%s",\n' "$status"
    printf '  "exitCode": %s,\n' "$exit_code"
    printf '  "phases": [\n'
    while IFS="	" read -r phase seconds phase_status counted; do
      [ -n "$phase" ] || continue
      printf '%b    {"name": "%s", "seconds": %s, "status": "%s", "countedInTotal": %s}' \
        "$separator" "$phase" "$seconds" "$phase_status" "$counted"
      separator=',\n'
      if [ "$counted" = "true" ]; then
        total=$((total + seconds))
      fi
    done < "$JAVAN_TIMING_LOG"
    printf '\n  ],\n'
    printf '  "totalSeconds": %s\n' "$total"
    printf '}\n'
  } > "$json"

  {
    printf '# Native package timings\n\n'
    printf -- '- Target: `%s`\n' "$target"
    printf -- '- Bootstrap generation: `%s`\n' "$generation"
    printf -- '- Proof scope: `%s`\n\n' "$scope"
    printf -- '- Status: `%s` (exit `%s`)\n\n' "$status" "$exit_code"
    printf '| Phase | Seconds | Status | Total |\n'
    printf '| --- | ---: | --- | --- |\n'
    while IFS="	" read -r phase seconds phase_status counted; do
      [ -n "$phase" ] || continue
      printf '| `%s` | %s | %s | %s |\n' "$phase" "$seconds" "$phase_status" "$counted"
    done < "$JAVAN_TIMING_LOG"
    printf '| **Total measured** | **%s** | **%s** | |\n' "$total" "$status"
  } > "$markdown"

  printf 'Timing report: %s\n' "$json"
  cat "$markdown"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    cat "$markdown" >> "$GITHUB_STEP_SUMMARY"
  fi
}
