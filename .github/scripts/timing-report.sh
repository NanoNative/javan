#!/bin/sh

# Shared phase timing for native package proofs. The caller owns the log lifetime.
javan_timing_now() {
  date +%s
}

javan_timing_record() {
  javan_timing_phase=$1
  javan_timing_started=$2
  javan_timing_phase_status=${3:-pass}
  javan_timing_counted=${4:-true}
  javan_timing_finished=$(javan_timing_now)
  javan_timing_seconds=$((javan_timing_finished - javan_timing_started))
  printf 'Timing: %s=%ss status=%s counted=%s\n' \
    "$javan_timing_phase" "$javan_timing_seconds" "$javan_timing_phase_status" "$javan_timing_counted"
  if [ -n "${JAVAN_TIMING_LOG:-}" ]; then
    printf '%s\t%s\t%s\t%s\n' \
      "$javan_timing_phase" "$javan_timing_seconds" "$javan_timing_phase_status" "$javan_timing_counted" \
      >> "$JAVAN_TIMING_LOG"
  fi
}

javan_timing_run() {
  javan_timing_run_phase=$1
  shift
  javan_timing_run_started=$(javan_timing_now)
  if "$@"; then
    javan_timing_run_code=0
    javan_timing_run_status=pass
  else
    javan_timing_run_code=$?
    javan_timing_run_status=fail
  fi
  javan_timing_record "$javan_timing_run_phase" "$javan_timing_run_started" "$javan_timing_run_status" true
  return "$javan_timing_run_code"
}

javan_timing_write_reports() {
  javan_timing_target=$1
  javan_timing_generation=$2
  javan_timing_scope=$3
  javan_timing_json=$4
  javan_timing_markdown=$5
  javan_timing_report_status=$6
  javan_timing_exit_code=$7
  javan_timing_total=0
  javan_timing_separator=''
  javan_timing_commit_sha=${GITHUB_SHA:-unknown}
  javan_timing_version=${JAVAN_VERSION:-unknown}
  javan_timing_runner_os=${RUNNER_OS:-$(uname -s)}
  javan_timing_runner_arch=${RUNNER_ARCH:-$(uname -m)}
  javan_timing_sanitizer_scope=${JAVAN_PACKAGE_SANITIZER_SCOPE:-unknown}
  javan_timing_heap_limit_bytes=${JAVAN_HEAP_LIMIT_BYTES:-unknown}

  mkdir -p "$(dirname "$javan_timing_json")" "$(dirname "$javan_timing_markdown")"
  {
    printf '{\n'
    printf '  "schemaVersion": 1,\n'
    printf '  "commitSha": "%s",\n' "$javan_timing_commit_sha"
    printf '  "version": "%s",\n' "$javan_timing_version"
    printf '  "runnerOs": "%s",\n' "$javan_timing_runner_os"
    printf '  "runnerArch": "%s",\n' "$javan_timing_runner_arch"
    printf '  "target": "%s",\n' "$javan_timing_target"
    printf '  "bootstrapGeneration": %s,\n' "$javan_timing_generation"
    printf '  "proofScope": "%s",\n' "$javan_timing_scope"
    printf '  "sanitizerScope": "%s",\n' "$javan_timing_sanitizer_scope"
    printf '  "heapLimitBytes": "%s",\n' "$javan_timing_heap_limit_bytes"
    printf '  "status": "%s",\n' "$javan_timing_report_status"
    printf '  "exitCode": %s,\n' "$javan_timing_exit_code"
    printf '  "phases": [\n'
    while IFS="	" read -r javan_timing_row_phase javan_timing_row_seconds javan_timing_row_status javan_timing_row_counted; do
      [ -n "$javan_timing_row_phase" ] || continue
      printf '%b    {"name": "%s", "seconds": %s, "status": "%s", "countedInTotal": %s}' \
        "$javan_timing_separator" "$javan_timing_row_phase" "$javan_timing_row_seconds" \
        "$javan_timing_row_status" "$javan_timing_row_counted"
      javan_timing_separator=',\n'
      if [ "$javan_timing_row_counted" = "true" ]; then
        javan_timing_total=$((javan_timing_total + javan_timing_row_seconds))
      fi
    done < "$JAVAN_TIMING_LOG"
    printf '\n  ],\n'
    printf '  "totalSeconds": %s\n' "$javan_timing_total"
    printf '}\n'
  } > "$javan_timing_json"

  {
    printf '# Native package timings\n\n'
    printf -- '- Target: `%s`\n' "$javan_timing_target"
    printf -- '- Bootstrap generation: `%s`\n' "$javan_timing_generation"
    printf -- '- Proof scope: `%s`\n\n' "$javan_timing_scope"
    printf -- '- Status: `%s` (exit `%s`)\n\n' "$javan_timing_report_status" "$javan_timing_exit_code"
    printf '| Phase | Seconds | Status | Total |\n'
    printf '| --- | ---: | --- | --- |\n'
    while IFS="	" read -r javan_timing_row_phase javan_timing_row_seconds javan_timing_row_status javan_timing_row_counted; do
      [ -n "$javan_timing_row_phase" ] || continue
      printf '| `%s` | %s | %s | %s |\n' \
        "$javan_timing_row_phase" "$javan_timing_row_seconds" "$javan_timing_row_status" "$javan_timing_row_counted"
    done < "$JAVAN_TIMING_LOG"
    printf '| **Total measured** | **%s** | **%s** | |\n' "$javan_timing_total" "$javan_timing_report_status"
  } > "$javan_timing_markdown"

  printf 'Timing report: %s\n' "$javan_timing_json"
  cat "$javan_timing_markdown"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    cat "$javan_timing_markdown" >> "$GITHUB_STEP_SUMMARY"
  fi
}
