#!/usr/bin/env bash
set -euo pipefail

SHADED_JAR=$(ls mockserver/mockserver-netty/target/mockserver-netty-*-shaded.jar 2>/dev/null | head -1)
if [ -z "$SHADED_JAR" ]; then
  echo "Error: shaded JAR not found in mockserver/mockserver-netty/target/"
  exit 1
fi

echo "--- :package: Found JAR: $SHADED_JAR"
cp "$SHADED_JAR" docker/local/mockserver-netty-jar-with-dependencies.jar

.buildkite/scripts/docker-login.sh

echo "--- :docker: Building and pushing mockserver/mockserver:latest"

DOCKER_CMD="docker buildx build --platform linux/amd64,linux/arm64 --push --tag mockserver/mockserver:latest docker/local"

echo "┌──────────────────────────────────────────────────────────────────"
echo "│ 🐳 Docker Command (copy to reproduce locally):"
echo "│"
echo "│   $DOCKER_CMD"
echo "│"
echo "└──────────────────────────────────────────────────────────────────"
echo ""

docker buildx create --use --name builder 2>/dev/null || docker buildx use builder
exec docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --push \
  --tag mockserver/mockserver:latest \
  docker/local
