#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# Pre-pull the container images a Docker-gated Testcontainers suite needs,
# with bounded retry + exponential backoff, BEFORE Maven runs.
#
# Testcontainers pulls lazily behind an Awaitility wait, so a slow or throttled
# registry kills the suite minutes in with an opaque ContainerFetchException
# (initializationError). Pulling here fails fast, names the image and registry,
# and warms the host daemon cache that Testcontainers reaches through the socket.
#
# Retrying a pull is safe; retrying Maven is NOT (it turns real failures green).
# Skips on PR builds, mirroring run-in-docker.sh's PR socket gate (and its
# ALLOW_PR_DOCKER_SOCKET override), so PRs are not coupled to registry uptime
# for a step that will not run.
#
# USAGE
#   pre-pull-images.sh <image> [<image> ...]
#
# Tunables (env): PRE_PULL_ATTEMPTS (default 4),
#                 PRE_PULL_BACKOFF_SECONDS (default 5, doubles each retry).
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

if [[ "$#" -eq 0 ]]; then
  echo "pre-pull-images.sh: no images given" >&2
  exit 2
fi

# Same gate run-in-docker.sh applies: no socket on PR builds => the step is
# skipped => there is nothing to pre-pull.
PR="${BUILDKITE_PULL_REQUEST:-false}"
if [[ "$PR" != "false" && "${ALLOW_PR_DOCKER_SOCKET:-false}" != "true" ]]; then
  echo "--- :information_source: Skipping image pre-pull on PR build #${PR} (Docker-socket step is skipped on PRs)"
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "^^^ +++"
  echo "pre-pull-images.sh: docker CLI not found on the agent — cannot pre-pull images" >&2
  exit 1
fi

ATTEMPTS="${PRE_PULL_ATTEMPTS:-4}"
BACKOFF_SECONDS="${PRE_PULL_BACKOFF_SECONDS:-5}"

prePullImage() {
  local image="$1"
  # Registry host is everything before the first '/', but only when it looks
  # like a host (contains a '.' or ':'); otherwise the image is on Docker Hub.
  local registry="${image%%/*}"
  if [[ "$image" != */* || ( "$registry" != *.* && "$registry" != *:* ) ]]; then
    registry="docker.io (Docker Hub)"
  fi

  # Already cached: skip the registry. Testcontainers pulls only if absent, so the
  # suite would not have fetched it either, and a registry outage must not fail a
  # step that can run off the cache. Tags are pinned, so a cached image is correct.
  if docker image inspect "$image" >/dev/null 2>&1; then
    echo "--- :docker: ${image} already cached; skipping pull"
    return 0
  fi

  local attempt=1
  local delay="$BACKOFF_SECONDS"
  while true; do
    echo "--- :docker: Pre-pulling ${image} (attempt ${attempt}/${ATTEMPTS})"
    if docker pull -q "$image"; then
      return 0
    fi
    if (( attempt >= ATTEMPTS )); then
      echo "^^^ +++"
      echo "pre-pull-images.sh: FAILED to pull '${image}' from '${registry}' after ${ATTEMPTS} attempts." >&2
      echo "The registry is unreachable, throttling, or the image/tag no longer exists." >&2
      echo "Failing this step now rather than letting Testcontainers time out mid-suite." >&2
      return 1
    fi
    echo "pull of '${image}' failed; retrying in ${delay}s..." >&2
    sleep "$delay"
    attempt=$(( attempt + 1 ))
    delay=$(( delay * 2 ))
  done
}

for image in "$@"; do
  prePullImage "$image"
done

echo "--- :white_check_mark: Pre-pulled $# image(s)"
