#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# Start a MockServer built from the current checkout on a private Docker
# network, then export the connection details for a client test step.
#
# Several client suites gate their integration tests on MOCKSERVER_URL and
# SKIP silently when it is unset. CI never set it, so those tests never ran
# while the step reported green. This helper makes "run against a real
# server" the cheap default so a client step cannot accidentally test
# nothing.
#
# Usage:
#   source .buildkite/scripts/with-mockserver.sh
#   # afterwards:
#   #   $MOCKSERVER_NETWORK — docker network the server is attached to
#   #   $MOCKSERVER_NAME    — container name / resolvable hostname
#   #   $MOCKSERVER_URL     — http://<name>:1080, as seen from a sibling
#   #                         container attached to $MOCKSERVER_NETWORK
#   #   $MOCKSERVER_DNS_PORT / $MOCKSERVER_DNS_ADDRESS — 1053 / <name>:1053, UDP DNS mocking
#
# The caller is expected to pass --network "$MOCKSERVER_NETWORK" and
# -e "MOCKSERVER_URL=$MOCKSERVER_URL" to run-in-docker.sh.
#
# Cleanup is registered via an EXIT trap in the calling shell.
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

_WMS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MOCKSERVER_NETWORK="mockserver-net-$$"
MOCKSERVER_NAME="mockserver-server-$$"

# Build (or reuse) an image from HEAD rather than the stale :snapshot tag on
# Docker Hub, so the client is verified against the server it ships with.
# shellcheck source=build-local-mockserver-image.sh
source "$_WMS_SCRIPT_DIR/build-local-mockserver-image.sh"

_wms_cleanup() {
  docker rm -f "$MOCKSERVER_NAME" >/dev/null 2>&1 || true
  docker network rm "$MOCKSERVER_NETWORK" >/dev/null 2>&1 || true
}
trap _wms_cleanup EXIT

docker network create "$MOCKSERVER_NETWORK" >/dev/null

MOCKSERVER_DNS_PORT=1053

docker run -d \
  --name "$MOCKSERVER_NAME" \
  --network "$MOCKSERVER_NETWORK" \
  -e "MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION=false" \
  -e "MOCKSERVER_DNS_ENABLED=true" \
  -e "MOCKSERVER_DNS_PORT=$MOCKSERVER_DNS_PORT" \
  "$MOCKSERVER_IMAGE" >/dev/null

MOCKSERVER_URL="http://${MOCKSERVER_NAME}:1080"
MOCKSERVER_DNS_ADDRESS="${MOCKSERVER_NAME}:${MOCKSERVER_DNS_PORT}"

# Wait for readiness via the image's Docker HEALTHCHECK — the image is
# distroless (no shell, no curl) so `docker exec ... curl` cannot work.
echo "--- :hourglass: Waiting for MockServer to become healthy..."
_wms_deadline=$((SECONDS + 120))
_wms_ready=false
_wms_status="unknown"
while [ $SECONDS -lt $_wms_deadline ]; do
  _wms_status=$(docker inspect -f '{{.State.Health.Status}}' "$MOCKSERVER_NAME" 2>/dev/null || echo "unknown")
  case "$_wms_status" in
    healthy)   _wms_ready=true; break ;;
    unhealthy) break ;;
  esac
  sleep 2
done

if [ "$_wms_ready" != "true" ]; then
  echo "+++ :bangbang: MockServer failed to become healthy (status: ${_wms_status})" >&2
  docker logs "$MOCKSERVER_NAME" >&2 || true
  exit 1
fi

echo "--- :white_check_mark: MockServer ready at ${MOCKSERVER_URL}"

export MOCKSERVER_NETWORK MOCKSERVER_NAME MOCKSERVER_URL MOCKSERVER_DNS_PORT MOCKSERVER_DNS_ADDRESS
