#!/bin/sh
set -eu

javan_host_target() {
  case "$(uname -s)" in
    Darwin) os=macos ;;
    Linux) os=linux ;;
    MINGW*|MSYS*|CYGWIN*) os=windows ;;
    *) os=$(uname -s | tr '[:upper:]' '[:lower:]') ;;
  esac
  case "$(uname -m)" in
    x86_64|amd64) arch=x64 ;;
    arm64|aarch64) arch=aarch64 ;;
    *) arch=$(uname -m | tr '[:upper:]' '[:lower:]') ;;
  esac
  printf '%s-%s\n' "$os" "$arch"
}
