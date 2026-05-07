#!/usr/bin/env bash
set -euo pipefail

echo "--- :buildkite: Downloading shaded JAR artifact"
buildkite-agent artifact download "mockserver/mockserver-netty/target/mockserver-netty-*-shaded.jar" .

SHADED_JAR=$(ls mockserver/mockserver-netty/target/mockserver-netty-*-shaded.jar 2>/dev/null | head -1)
if [ -z "$SHADED_JAR" ]; then
  echo "Error: shaded JAR not found after artifact download"
  exit 1
fi

echo "--- :package: Copying shaded JAR as jar-with-dependencies"
JAR_DIR="mockserver/mockserver-netty/target"
JAR_NAME=$(basename "$SHADED_JAR" | sed 's/-shaded\.jar$/-jar-with-dependencies.jar/')
cp "$SHADED_JAR" "$JAR_DIR/$JAR_NAME"

echo "--- :docker: Running container integration tests (Docker Compose only)"
export SKIP_JAVA_BUILD=true
export SKIP_HELM_TESTS=true
export SKIP_DOCKER_REBUILD_CLIENT=false

exec container_integration_tests/integration_tests.sh
