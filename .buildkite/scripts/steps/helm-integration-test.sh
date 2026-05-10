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

echo "--- :docker: Building MockServer Docker image for testing"
cp "$JAR_DIR/$JAR_NAME" docker/mockserver-netty-jar-with-dependencies.jar
docker build --no-cache -t mockserver/mockserver:integration_testing --build-arg source=copy docker
rm docker/mockserver-netty-jar-with-dependencies.jar

echo "--- :k8s: Installing k3d (if needed)"
K3D_VERSION="v5.7.5"
if ! command -v k3d &>/dev/null || [[ "$(k3d version 2>/dev/null | head -1)" != *"${K3D_VERSION#v}"* ]]; then
  ARCH=$(uname -m); case "$ARCH" in x86_64) ARCH=amd64;; aarch64) ARCH=arm64;; esac
  curl -fsSL "https://github.com/k3d-io/k3d/releases/download/${K3D_VERSION}/k3d-linux-${ARCH}" -o /usr/local/bin/k3d
  chmod +x /usr/local/bin/k3d
  k3d version
fi

echo "--- :helm: Running Helm integration tests"
export SKIP_JAVA_BUILD=true
export SKIP_DOCKER_BUILD_MOCKSERVER=true
export SKIP_DOCKER_REBUILD_CLIENT=true
export SKIP_DOCKER_TESTS=true
export DELETE_CLUSTER=true

exec container_integration_tests/integration_tests.sh
