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
  javan_timing_cpu_seconds=${5:-unknown}
  javan_timing_max_rss_bytes=${6:-unknown}
  javan_timing_resource_source=${7:-unavailable}
  javan_timing_finished=$(javan_timing_now)
  javan_timing_seconds=$((javan_timing_finished - javan_timing_started))
  printf 'Timing: %s=%ss status=%s counted=%s cpu=%s max_rss_bytes=%s source=%s\n' \
    "$javan_timing_phase" "$javan_timing_seconds" "$javan_timing_phase_status" "$javan_timing_counted" \
    "$javan_timing_cpu_seconds" "$javan_timing_max_rss_bytes" "$javan_timing_resource_source"
  if [ -n "${JAVAN_TIMING_LOG:-}" ]; then
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$javan_timing_phase" "$javan_timing_seconds" "$javan_timing_phase_status" "$javan_timing_counted" \
      "$javan_timing_cpu_seconds" "$javan_timing_max_rss_bytes" "$javan_timing_resource_source" \
      >> "$JAVAN_TIMING_LOG"
  fi
}

javan_timing_run() {
  javan_timing_run_phase=$1
  shift
  javan_timing_run_started=$(javan_timing_now)
  if javan_timing_measure "$@"; then
    javan_timing_run_code=0
    javan_timing_run_status=pass
  else
    javan_timing_run_code=$?
    javan_timing_run_status=fail
  fi
  javan_timing_record "$javan_timing_run_phase" "$javan_timing_run_started" "$javan_timing_run_status" true \
    "$javan_timing_measure_cpu_seconds" "$javan_timing_measure_max_rss_bytes" "$javan_timing_measure_source"
  return "$javan_timing_run_code"
}

# Runs the command once while preserving its output and gathering native process resource use when available.
javan_timing_measure() {
  javan_timing_measure_cpu_seconds=unknown
  javan_timing_measure_max_rss_bytes=unknown
  javan_timing_measure_source=unavailable
  javan_timing_measure_file=$(mktemp "${TMPDIR:-/tmp}/javan-timing.XXXXXX") || {
    "$@"
    return $?
  }

  case "$(uname -s)" in
    Linux)
      if [ -x /usr/bin/time ]; then
        if /usr/bin/time -f '%U %S %M' -o "$javan_timing_measure_file" "$@"; then
          javan_timing_measure_code=0
        else
          javan_timing_measure_code=$?
        fi
        IFS=' ' read -r javan_timing_measure_user javan_timing_measure_system javan_timing_measure_rss \
          < "$javan_timing_measure_file" || true
        javan_timing_measure_user=$(printf '%s' "$javan_timing_measure_user" | tr ',' '.')
        javan_timing_measure_system=$(printf '%s' "$javan_timing_measure_system" | tr ',' '.')
        case "$javan_timing_measure_user:$javan_timing_measure_system" in
          *[!0-9.:]*|:|*:*:*) ;;
          *) javan_timing_measure_cpu_seconds=$(awk -v user="$javan_timing_measure_user" -v system="$javan_timing_measure_system" \
            'BEGIN { printf "%.6f", user + system }') ;;
        esac
        case "$javan_timing_measure_rss" in
          ''|*[!0-9]*) ;;
          *) javan_timing_measure_max_rss_bytes=$(awk -v rss="$javan_timing_measure_rss" 'BEGIN { printf "%.0f", rss * 1024 }') ;;
        esac
        javan_timing_measure_source=gnu-time
        rm -f "$javan_timing_measure_file"
        return "$javan_timing_measure_code"
      fi
      ;;
    Darwin)
      if /usr/bin/time -l sh -c 'exec "$@" 2>&3' javan-timing "$@" 3>&2 2> "$javan_timing_measure_file"; then
        javan_timing_measure_code=0
      else
        javan_timing_measure_code=$?
      fi
      javan_timing_measure_user=$(awk '$2 == "real" && $4 == "user" && $6 == "sys" { print $3; exit }' "$javan_timing_measure_file")
      javan_timing_measure_system=$(awk '$2 == "real" && $4 == "user" && $6 == "sys" { print $5; exit }' "$javan_timing_measure_file")
      javan_timing_measure_rss=$(awk '$2 == "maximum" && $3 == "resident" && $4 == "set" && $5 == "size" { print $1; exit }' "$javan_timing_measure_file")
      case "$javan_timing_measure_user:$javan_timing_measure_system" in
        *[!0-9.:]*|:|*:*:*) ;;
        *) javan_timing_measure_cpu_seconds=$(awk -v user="$javan_timing_measure_user" -v system="$javan_timing_measure_system" \
          'BEGIN { printf "%.6f", user + system }') ;;
      esac
      case "$javan_timing_measure_rss" in
        ''|*[!0-9]*) ;;
        *) javan_timing_measure_max_rss_bytes=$javan_timing_measure_rss ;;
      esac
      javan_timing_measure_source=bsd-time
      rm -f "$javan_timing_measure_file"
      return "$javan_timing_measure_code"
      ;;
  esac

  rm -f "$javan_timing_measure_file"
  "$@"
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
  javan_timing_host_os=$(uname -s)
  case "$javan_timing_host_os" in
    Darwin)
      javan_timing_cpu_count=$(sysctl -n hw.ncpu 2>/dev/null || printf unknown)
      javan_timing_physical_memory_bytes=$(sysctl -n hw.memsize 2>/dev/null || printf unknown)
      ;;
    Linux)
      javan_timing_cpu_count=$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf unknown)
      javan_timing_physical_memory_bytes=$(awk '/MemTotal:/ { printf "%.0f", $2 * 1024; exit }' /proc/meminfo 2>/dev/null || printf unknown)
      ;;
    *)
      javan_timing_cpu_count=unknown
      javan_timing_physical_memory_bytes=unknown
      ;;
  esac

  mkdir -p "$(dirname "$javan_timing_json")" "$(dirname "$javan_timing_markdown")"
  {
    printf '{\n'
    printf '  "schemaVersion": 2,\n'
    printf '  "commitSha": "%s",\n' "$javan_timing_commit_sha"
    printf '  "version": "%s",\n' "$javan_timing_version"
    printf '  "runnerOs": "%s",\n' "$javan_timing_runner_os"
    printf '  "runnerArch": "%s",\n' "$javan_timing_runner_arch"
    printf '  "target": "%s",\n' "$javan_timing_target"
    printf '  "bootstrapGeneration": %s,\n' "$javan_timing_generation"
    printf '  "proofScope": "%s",\n' "$javan_timing_scope"
    printf '  "sanitizerScope": "%s",\n' "$javan_timing_sanitizer_scope"
    printf '  "heapLimitBytes": "%s",\n' "$javan_timing_heap_limit_bytes"
    printf '  "availableProcessors": "%s",\n' "$javan_timing_cpu_count"
    printf '  "physicalMemoryBytes": "%s",\n' "$javan_timing_physical_memory_bytes"
    printf '  "status": "%s",\n' "$javan_timing_report_status"
    printf '  "exitCode": %s,\n' "$javan_timing_exit_code"
    printf '  "phases": [\n'
    while IFS="	" read -r javan_timing_row_phase javan_timing_row_seconds javan_timing_row_status javan_timing_row_counted javan_timing_row_cpu_seconds javan_timing_row_max_rss_bytes javan_timing_row_resource_source; do
      [ -n "$javan_timing_row_phase" ] || continue
      javan_timing_row_cpu_seconds=${javan_timing_row_cpu_seconds:-unknown}
      javan_timing_row_max_rss_bytes=${javan_timing_row_max_rss_bytes:-unknown}
      javan_timing_row_resource_source=${javan_timing_row_resource_source:-unavailable}
      printf '%b    {"name": "%s", "seconds": %s, "status": "%s", "countedInTotal": %s, "cpuSeconds": "%s", "maxRssBytes": "%s", "resourceSource": "%s"}' \
        "$javan_timing_separator" "$javan_timing_row_phase" "$javan_timing_row_seconds" \
        "$javan_timing_row_status" "$javan_timing_row_counted" "$javan_timing_row_cpu_seconds" \
        "$javan_timing_row_max_rss_bytes" "$javan_timing_row_resource_source"
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
    printf '| Phase | Seconds | CPU seconds | Peak RSS bytes | Resource source | Status | Total |\n'
    printf '| --- | ---: | ---: | ---: | --- | --- | --- |\n'
    while IFS="	" read -r javan_timing_row_phase javan_timing_row_seconds javan_timing_row_status javan_timing_row_counted javan_timing_row_cpu_seconds javan_timing_row_max_rss_bytes javan_timing_row_resource_source; do
      [ -n "$javan_timing_row_phase" ] || continue
      javan_timing_row_cpu_seconds=${javan_timing_row_cpu_seconds:-unknown}
      javan_timing_row_max_rss_bytes=${javan_timing_row_max_rss_bytes:-unknown}
      javan_timing_row_resource_source=${javan_timing_row_resource_source:-unavailable}
      printf '| `%s` | %s | %s | %s | %s | %s | %s |\n' \
        "$javan_timing_row_phase" "$javan_timing_row_seconds" "$javan_timing_row_cpu_seconds" \
        "$javan_timing_row_max_rss_bytes" "$javan_timing_row_resource_source" "$javan_timing_row_status" \
        "$javan_timing_row_counted"
    done < "$JAVAN_TIMING_LOG"
    printf '| **Total measured** | **%s** | | | | **%s** | |\n' "$javan_timing_total" "$javan_timing_report_status"
  } > "$javan_timing_markdown"

  printf 'Timing report: %s\n' "$javan_timing_json"
  cat "$javan_timing_markdown"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    cat "$javan_timing_markdown" >> "$GITHUB_STEP_SUMMARY"
  fi
}
