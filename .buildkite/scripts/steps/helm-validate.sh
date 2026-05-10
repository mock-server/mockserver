#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i alpine/helm:3.17.3 \
  -- sh -c '
    errors=0

    for chart in helm/mockserver helm/mockserver-config; do
      if [ -d "$chart" ]; then
        echo "--- Linting $chart"
        helm lint "$chart" || errors=$((errors + 1))

        echo "--- Rendering $chart"
        helm template test-release "$chart" > /dev/null || errors=$((errors + 1))
      fi
    done

    echo "--- Rendering mockserver with inline config enabled"
    helm template test-release helm/mockserver \
      --set app.config.enabled=true \
      --set app.config.properties="mockserver.initializationJsonPath=/config/initializerJson.json" \
      --set "app.config.initializerJson=[{\"httpRequest\":{\"path\":\"/example\"},\"httpResponse\":{\"body\":\"response\"}}]" \
      > /dev/null || errors=$((errors + 1))

    echo "--- Rendering mockserver with ingress enabled"
    helm template test-release helm/mockserver \
      --set ingress.enabled=true \
      > /dev/null || errors=$((errors + 1))

    if [ "$errors" -eq 0 ]; then
      echo "All Helm validations passed"
    else
      echo "FAILED: $errors validation(s) failed"
    fi
    exit $errors
  '
