#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i python:3.12 \
  -w /build/mockserver-client-python \
  -s \
  -- bash -c 'apt-get update -qq && apt-get install -y -qq docker.io >/dev/null 2>&1 && pip install -e ".[dev]" && pytest -m integration -v'
