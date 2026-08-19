#!/bin/sh
set -eu

if [ "$#" -eq 0 ]; then
  printf '%s\n' 'Usage: install-linux-packages.sh package [package ...]' >&2
  exit 2
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
