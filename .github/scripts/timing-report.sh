#!/bin/sh

# Shared phase timing for native package proofs. The caller owns the log lifetime.
javan_timing_now() {
  date +%s
}

javan_timing_record() {
  phase=$1
  started=$2
  finished=$(javan_timing_now)
  seconds=$((finished - started))
  printf 'Timing: %s=%ss\n' "$phase" "$seconds"
  if [ -n "${JAVAN_TIMING_LOG:-}" ]; then
    printf '%s\t%s\n' "$phase" "$seconds" >> "$JAVAN_TIMING_LOG"
  fi
}

javan_timing_run() {
  phase=$1
  shift
  started=$(javan_timing_now)
  "$@"
  javan_timing_record "$phase" "$started"
}

javan_timing_write_reports() {
  target=$1
  generation=$2
  scope=$3
  json=$4
  markdown=$5
  total=0
  separator=''

  mkdir -p "$(dirname -- "$json")" "$(dirname -- "$markdown")"
  {
    printf '{\n'
    printf '  "target": "%s",\n' "$target"
    printf '  "bootstrapGeneration": %s,\n' "$generation"
    printf '  "proofScope": "%s",\n' "$scope"
    printf '  "phases": [\n'
    while IFS="	" read -r phase seconds; do
      [ -n "$phase" ] || continue
      printf '%b    {"name": "%s", "seconds": %s}' "$separator" "$phase" "$seconds"
      separator=',\n'
      total=$((total + seconds))
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
    printf '| Phase | Seconds |\n'
    printf '| --- | ---: |\n'
    while IFS="	" read -r phase seconds; do
      [ -n "$phase" ] || continue
      printf '| `%s` | %s |\n' "$phase" "$seconds"
    done < "$JAVAN_TIMING_LOG"
    printf '| **Total measured** | **%s** |\n' "$total"
  } > "$markdown"

  printf 'Timing report: %s\n' "$json"
  cat "$markdown"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    cat "$markdown" >> "$GITHUB_STEP_SUMMARY"
  fi
}
