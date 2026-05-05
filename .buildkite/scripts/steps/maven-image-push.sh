#!/usr/bin/env bash
set -euo pipefail

.buildkite/scripts/docker-login.sh

DOCKER_CMD="docker push mockserver/mockserver:maven"

echo "┌──────────────────────────────────────────────────────────────────"
echo "│ 🐳 Docker Command (copy to reproduce locally):"
echo "│"
echo "│   $DOCKER_CMD"
echo "│"
echo "└──────────────────────────────────────────────────────────────────"
echo ""

exec docker push mockserver/mockserver:maven
