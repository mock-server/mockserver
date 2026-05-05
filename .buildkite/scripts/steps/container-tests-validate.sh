#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i bash:latest \
  -- bash -c '
    errors=0
    for script in container_integration_tests/*.sh; do
      if [ -f "$script" ]; then
        echo "Checking $script..."
        bash -n "$script" || errors=$((errors + 1))
      fi
    done
    exit $errors
  '
