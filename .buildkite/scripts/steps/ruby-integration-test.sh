#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NETWORK_NAME="mockserver-ruby-$$"
MOCKSERVER_NAME="mockserver-ruby-server-$$"

cleanup() {
  docker rm -f "$MOCKSERVER_NAME" 2>/dev/null || true
  docker network rm "$NETWORK_NAME" 2>/dev/null || true
}
trap cleanup EXIT

docker network create "$NETWORK_NAME"

docker run -d \
  --name "$MOCKSERVER_NAME" \
  --network "$NETWORK_NAME" \
  mockserver/mockserver:snapshot

"$SCRIPT_DIR/../run-in-docker.sh" \
  -i ruby:3.3 \
  -w /build/mockserver-client-ruby \
  -e "MOCKSERVER_HOST=$MOCKSERVER_NAME" \
  -e "MOCKSERVER_PORT=1080" \
  --network "$NETWORK_NAME" \
  -- bash -c 'bundle install && bundle exec rspec --tag integration -f documentation --format RspecJunitFormatter --out test-reports/integration.xml'
