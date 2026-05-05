#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i node:22 \
  -w /build/mockserver-ui \
  -- bash -c 'npm ci && npm run lint && npm run typecheck'
