#!/usr/bin/env bash
set -euo pipefail

touch docker_build/maven/corporate-root-ca.pem

DOCKER_CMD="docker buildx build --platform linux/amd64 --load --tag mockserver/mockserver:maven docker_build/maven"

echo "┌──────────────────────────────────────────────────────────────────"
echo "│ 🐳 Docker Command (copy to reproduce locally):"
echo "│"
echo "│   $DOCKER_CMD"
echo "│"
echo "└──────────────────────────────────────────────────────────────────"
echo ""

docker buildx create --use --name builder 2>/dev/null || docker buildx use builder
exec docker buildx build \
  --platform linux/amd64 \
  --load \
  --tag mockserver/mockserver:maven \
  docker_build/maven
