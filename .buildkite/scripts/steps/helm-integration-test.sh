#!/usr/bin/env bash
set -euo pipefail

JAR_DIR="mockserver/mockserver-netty/target"

echo "--- :buildkite: Downloading shaded JAR artifact"
if command -v buildkite-agent &>/dev/null && buildkite-agent artifact download "mockserver/mockserver-netty/target/mockserver-netty-*-shaded.jar" . 2>/dev/null; then
  SHADED_JAR=$(ls "${JAR_DIR}"/mockserver-netty-*-shaded.jar 2>/dev/null | head -1)
  if [ -z "$SHADED_JAR" ]; then
    echo "Error: shaded JAR not found after artifact download"
    exit 1
  fi
  JAR_NAME=$(basename "$SHADED_JAR" | sed 's/-shaded\.jar$/-jar-with-dependencies.jar/')
  cp "$SHADED_JAR" "$JAR_DIR/$JAR_NAME"
else
  echo "No artifact available — building JAR from source"
  echo "--- :maven: Building mockserver-netty shaded JAR"
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  "$SCRIPT_DIR/../run-in-docker.sh" \
    -i mockserver/mockserver:maven \
    -m 7g \
    -w /build/mockserver \
    -e "MAVEN_OPTS=-Xms2048m -Xmx6144m" \
    -- ./mvnw -pl mockserver-netty -am package -DskipTests -q
  JAR_NAME=$(ls "${JAR_DIR}"/mockserver-netty-*-jar-with-dependencies.jar 2>/dev/null | head -1 | xargs basename)
fi

if [ -z "$JAR_NAME" ] || [ ! -f "$JAR_DIR/$JAR_NAME" ]; then
  echo "Error: jar-with-dependencies JAR not found in $JAR_DIR"
  exit 1
fi

echo "--- :docker: Building MockServer Docker image for testing"
cp "$JAR_DIR/$JAR_NAME" docker/mockserver-netty-jar-with-dependencies.jar
docker build --no-cache -t mockserver/mockserver:integration_testing --build-arg source=copy docker
rm docker/mockserver-netty-jar-with-dependencies.jar

echo "--- :k8s: Installing k3d (if needed)"
K3D_VERSION="v5.7.5"
K3D_BIN="/usr/local/bin/k3d"
if ! command -v k3d &>/dev/null || [[ "$(k3d version 2>/dev/null | head -1)" != *"${K3D_VERSION#v}"* ]]; then
  ARCH=$(uname -m); case "$ARCH" in x86_64) ARCH=amd64;; aarch64) ARCH=arm64;; esac
  K3D_TMP=$(mktemp)
  curl -fsSL "https://github.com/k3d-io/k3d/releases/download/${K3D_VERSION}/k3d-linux-${ARCH}" -o "$K3D_TMP"
  chmod +x "$K3D_TMP"
  sudo mv "$K3D_TMP" "$K3D_BIN" 2>/dev/null || mv "$K3D_TMP" "$K3D_BIN"
  k3d version
fi

echo "--- :helm: Running Helm integration tests"
export SKIP_JAVA_BUILD=true
export SKIP_DOCKER_BUILD_MOCKSERVER=true
export SKIP_DOCKER_REBUILD_CLIENT=true
export SKIP_DOCKER_TESTS=true
export DELETE_CLUSTER=true

exec container_integration_tests/integration_tests.sh
