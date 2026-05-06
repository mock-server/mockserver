#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i python:3.12 \
  -w /build/mockserver-client-python \
  -- bash -c "pip install -e '.[dev]' && pytest -m 'not integration' --junitxml=test-reports/unit.xml"
