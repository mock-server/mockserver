#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

IMAGE=""
DOCKER_ARGS=()
COMMAND_ARGS=()
WORKDIR="/build"
MEMORY=""
NETWORK=""
DOCKER_SOCKET=false
ENV_VARS=()
VOLUMES=()

usage() {
  cat <<EOF
Usage: run-in-docker.sh [OPTIONS] -- COMMAND [ARGS...]

Runs a command inside a Docker container with the repo mounted at /build.
Logs the full docker run command at the start for easy local reproduction.

Options:
  -i, --image IMAGE        Docker image to use (required)
  -w, --workdir DIR        Working directory inside container (default: /build)
  -m, --memory SIZE        Memory limit (e.g. 14g)
  -s, --docker-socket      Mount Docker socket into container
  -e, --env KEY=VALUE      Pass environment variable to container
  -v, --volume SRC:DST     Additional volume mount
  --network NAME           Docker network to connect to
  -h, --help               Show this help

Examples:
  .buildkite/scripts/run-in-docker.sh -i node:22 -w /build/mockserver-ui -- npm ci && npm test
  .buildkite/scripts/run-in-docker.sh -i python:3.12 -s -- bash -c 'cd mockserver-client-python && pytest'
EOF
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -i|--image)   IMAGE="$2"; shift 2 ;;
    -w|--workdir) WORKDIR="$2"; shift 2 ;;
    -m|--memory)  MEMORY="$2"; shift 2 ;;
    -s|--docker-socket) DOCKER_SOCKET=true; shift ;;
    -e|--env)     ENV_VARS+=("$2"); shift 2 ;;
    -v|--volume)  VOLUMES+=("$2"); shift 2 ;;
    --network)    NETWORK="$2"; shift 2 ;;
    -h|--help)    usage ;;
    --)           shift; COMMAND_ARGS=("$@"); break ;;
    *)            COMMAND_ARGS=("$@"); break ;;
  esac
done

if [[ -z "$IMAGE" ]]; then
  echo "Error: --image is required"
  exit 1
fi

if [[ ${#COMMAND_ARGS[@]} -eq 0 ]]; then
  echo "Error: no command specified after --"
  exit 1
fi

DOCKER_ARGS+=(--rm)
DOCKER_ARGS+=(-v "$REPO_ROOT:/build")
DOCKER_ARGS+=(-w "$WORKDIR")

if [[ -n "$MEMORY" ]]; then
  DOCKER_ARGS+=(--memory="$MEMORY" --memory-swap="$MEMORY")
fi

if [[ -n "$NETWORK" ]]; then
  DOCKER_ARGS+=(--network "$NETWORK")
fi

if [[ "$DOCKER_SOCKET" == "true" ]]; then
  DOCKER_ARGS+=(-v /var/run/docker.sock:/var/run/docker.sock)
fi

for env_var in "${ENV_VARS[@]+"${ENV_VARS[@]}"}"; do
  DOCKER_ARGS+=(-e "$env_var")
done

for vol in "${VOLUMES[@]+"${VOLUMES[@]}"}"; do
  DOCKER_ARGS+=(-v "$vol")
done

quote_arg() {
  if [[ "$1" =~ [[:space:]\&\|\;\$\(\)\{\}\<\>\`\\] ]]; then
    local escaped="${1//\\/\\\\}"
    escaped="${escaped//\"/\\\"}"
    escaped="${escaped//\$/\\\$}"
    escaped="${escaped//\`/\\\`}"
    printf '"%s"' "$escaped"
  else
    printf '%s' "$1"
  fi
}

DISPLAY_ARGS=()
for arg in "${DOCKER_ARGS[@]}"; do
  DISPLAY_ARGS+=("$(quote_arg "$arg")")
done
DISPLAY_CMD_ARGS=()
for arg in "${COMMAND_ARGS[@]}"; do
  DISPLAY_CMD_ARGS+=("$(quote_arg "$arg")")
done

FULL_CMD="docker run ${DISPLAY_ARGS[*]} $IMAGE ${DISPLAY_CMD_ARGS[*]}"

echo "┌──────────────────────────────────────────────────────────────────"
echo "│ Docker Command (copy to reproduce locally):"
echo "│"
echo "│   $FULL_CMD"
echo "│"
echo "│ Or from repo root:"
echo "│   cd $(pwd) && $FULL_CMD"
echo "│"
echo "└──────────────────────────────────────────────────────────────────"
echo ""

exec docker run "${DOCKER_ARGS[@]}" "$IMAGE" "${COMMAND_ARGS[@]}"
