#!/usr/bin/env bash
set -euo pipefail

# Fires every example in the generated Postman collection at a LIVE MockServer and fails if any
# example body is rejected as malformed (400/415/500).
#
# collections-validate.sh only proves the committed collections match the generator. A required
# requestBody with no `example` generates a bodyless request that regenerates identically and passes
# there, yet 400s for every user; this step catches that by running the examples for real.
#
# MockServer is started HERE, on the agent, not by test_collections.py via `docker run`: that would
# need the Docker socket, which run-in-docker.sh withholds on PR builds, silently skipping the check.
# Both containers join a user-defined bridge network (name resolution, and it works on the agents'
# userns-remapped daemon, which rejects --network=host). The host port publish exists only for the
# readiness curl below.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

IMAGE="${MOCKSERVER_IMAGE:-mockserver/mockserver:snapshot}"
PORT="${MOCKSERVER_PORT:-11080}"
NET="collections-net-$$"
CONTAINER_NAME="mockserver-live-$$"

# Install the cleanup trap BEFORE the fallible `docker run` (and right after the network exists), not
# after: under `set -e` a failing `docker run` — image pull failure, host port already bound, a daemon
# rejection, exactly the infra-failure class this step keeps hitting — aborts the script on the
# assignment below, so a trap installed afterwards would never run and the freshly-created bridge
# network would leak. Leaked user-defined bridges exhaust Docker's address pool and eventually brick
# the agent's daemon. CONTAINER_ID starts empty and the cleanup guards on it so the trap is safe under
# `set -u` even when it fires before the container is created.
echo "=== creating bridge network ($NET) ==="
docker network create "$NET" >/dev/null

CONTAINER_ID=""
cleanup() {
  if [ -n "$CONTAINER_ID" ]; then
    echo ""
    echo "=== MockServer logs (tail) ==="
    docker logs --tail 50 "$CONTAINER_ID" 2>&1 || true
    docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true
  fi
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "=== starting MockServer ($IMAGE) as $CONTAINER_NAME on $NET, host port $PORT ==="
CONTAINER_ID="$(docker run --rm -d --name "$CONTAINER_NAME" --network "$NET" -p "${PORT}:1080" "$IMAGE")"

# Wait for readiness rather than sleeping a fixed interval: /mockserver/status answers as soon as the
# port binds, which is what we need before firing requests at it.
echo "=== waiting for MockServer to accept requests ==="
ready=0
for _ in $(seq 1 60); do
  if curl -fsS -X PUT "http://localhost:${PORT}/mockserver/status" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  echo "FAIL: MockServer did not become ready within 60s — this is an infrastructure failure, NOT a"
  echo "      collection failure. Do not interpret it as the examples being valid or invalid."
  exit 1
fi

echo ""
echo "=== firing every generated example at the live server ==="
# Deliberately NOT `exec`: exec replaces this shell, which discards the `trap cleanup EXIT` above, so
# the MockServer container and host port would leak and the failure-log dump would never print. Run it
# as a child instead — `set -e` propagates a non-zero result (firing the trap on the way out), and a
# success falls through to the end of the script (trap still fires).
"$SCRIPT_DIR/../run-in-docker.sh" \
  -i python:3.12 \
  --network "$NET" \
  -- bash -c "
    set -eu
    python3 scripts/collections/test_collections.py --base-url 'http://${CONTAINER_NAME}:1080'
  "
