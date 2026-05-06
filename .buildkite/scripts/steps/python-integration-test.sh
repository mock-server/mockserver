#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NETWORK_NAME="mockserver-python-$$"
MOCKSERVER_NAME="mockserver-python-server-$$"

cleanup() {
  docker rm -f "$MOCKSERVER_NAME" 2>/dev/null || true
  docker network rm "$NETWORK_NAME" 2>/dev/null || true
}
trap cleanup EXIT

docker network create "$NETWORK_NAME"

docker run -d \
  --name "$MOCKSERVER_NAME" \
  --network "$NETWORK_NAME" \
  mockserver/mockserver:latest

"$SCRIPT_DIR/../run-in-docker.sh" \
  -i python:3.12 \
  -w /build/mockserver-client-python \
  -e "MOCKSERVER_HOST=$MOCKSERVER_NAME" \
  -e "MOCKSERVER_PORT=1080" \
  --network "$NETWORK_NAME" \
  -- bash -c 'pip install -e ".[dev]" && pytest -m integration -v --junitxml=test-reports/integration.xml'
