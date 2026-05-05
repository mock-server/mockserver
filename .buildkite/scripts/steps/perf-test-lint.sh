#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i python:3.12 \
  -w /build/mockserver-performance-test \
  -- bash -c 'python3 -m py_compile locustfile.py && bash -n scripts/runLocust.sh && bash -n runAll.sh'
