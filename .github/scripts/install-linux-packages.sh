#!/bin/sh
set -eu

if [ "$#" -eq 0 ]; then
  printf '%s\n' 'Usage: install-linux-packages.sh package [package ...]' >&2
  exit 2
fi

package_available() {
  case "$1" in
    build-essential) command -v cc >/dev/null 2>&1 ;;
    mingw-w64) command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1 ;;
    *)
      command -v dpkg-query >/dev/null 2>&1 \
        && dpkg-query -W -f='${db:Status-Status}' "$1" 2>/dev/null | grep -qx installed
      ;;
  esac
}

missing_packages=''
for package in "$@"; do
  if ! package_available "$package"; then
    missing_packages="$missing_packages $package"
  fi
done
if [ -z "$missing_packages" ]; then
  exit 0
fi

mirrors=/etc/apt/apt-mirrors.txt
if [ -f "$mirrors" ]; then
  official_mirror=$(grep -E '^https?://(archive|ports)\.ubuntu\.com' "$mirrors" | head -n 1 || true)
  if [ -n "$official_mirror" ]; then
    printf '%s\n' "$official_mirror" | sudo tee "$mirrors" >/dev/null
  fi
fi

for attempt in 1 2 3; do
  if timeout -k 15s 4m sudo apt-get \
    -o Acquire::ForceIPv4=true \
    -o Acquire::http::Timeout=20 \
    -o Acquire::https::Timeout=20 \
    -o Acquire::Retries=2 \
    update \
    && timeout -k 15s 4m sudo apt-get \
      -o Acquire::ForceIPv4=true \
      -o Acquire::http::Timeout=20 \
      -o Acquire::https::Timeout=20 \
      -o Acquire::Retries=2 \
      install -y --fix-missing "$@"; then
    exit 0
  fi
  if [ "$attempt" -eq 3 ]; then
    printf '%s\n' 'Linux package installation failed after 3 attempts.' >&2
    exit 1
  fi
  sleep 15
done
