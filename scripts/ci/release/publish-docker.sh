#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker

log_step "Publishing Docker images for $RELEASE_VERSION"

cd "$REPO_ROOT"

if is_ci; then
  log_info "Downloading shaded JAR artifact"
  buildkite-agent artifact download "mockserver/mockserver-netty/target/mockserver-netty-*-shaded.jar" . 2>/dev/null || true
fi

SHADED_JAR=$(find mockserver/mockserver-netty/target -name 'mockserver-netty-*-shaded.jar' -print -quit 2>/dev/null)
if [[ -z "$SHADED_JAR" ]]; then
  log_info "JAR not found in build artifacts — downloading from Maven Central"
  mkdir -p mockserver/mockserver-netty/target
  SHADED_JAR="mockserver/mockserver-netty/target/mockserver-netty-${RELEASE_VERSION}-shaded.jar"
  curl -fsSL --max-time 300 --connect-timeout 30 --retry 3 --retry-delay 5 -o "$SHADED_JAR" \
    "https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/${RELEASE_VERSION}/mockserver-netty-${RELEASE_VERSION}-shaded.jar"
fi

log_info "Using JAR: $SHADED_JAR"
cp "$SHADED_JAR" docker/local/mockserver-netty-jar-with-dependencies.jar

.buildkite/scripts/docker-login.sh
.buildkite/scripts/ecr-login.sh

FULL_TAG="mockserver-$RELEASE_VERSION"
SHORT_TAG="$RELEASE_VERSION"
ECR_REPO="public.ecr.aws/mockserver/mockserver"

log_info "Building and pushing multi-arch images"
log_info "  Tags: mockserver/mockserver:$FULL_TAG, mockserver/mockserver:$SHORT_TAG, mockserver/mockserver:latest"
log_info "  ECR:  ${ECR_REPO}:$FULL_TAG, ${ECR_REPO}:$SHORT_TAG, ${ECR_REPO}:latest"

docker buildx create --use --name multiarch 2>/dev/null || docker buildx use multiarch
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --push \
  --tag "mockserver/mockserver:$FULL_TAG" \
  --tag "mockserver/mockserver:$SHORT_TAG" \
  --tag "mockserver/mockserver:latest" \
  --tag "${ECR_REPO}:$FULL_TAG" \
  --tag "${ECR_REPO}:$SHORT_TAG" \
  --tag "${ECR_REPO}:latest" \
  docker/local

log_info "Building and pushing GraalJS variant"
cp docker/local/mockserver-netty-jar-with-dependencies.jar docker/graaljs/mockserver-netty-jar-with-dependencies.jar
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --push \
  --build-arg source=copy \
  --tag "mockserver/mockserver:$FULL_TAG-graaljs" \
  --tag "mockserver/mockserver:$SHORT_TAG-graaljs" \
  --tag "mockserver/mockserver:latest-graaljs" \
  --tag "${ECR_REPO}:$FULL_TAG-graaljs" \
  --tag "${ECR_REPO}:$SHORT_TAG-graaljs" \
  --tag "${ECR_REPO}:latest-graaljs" \
  docker/graaljs

log_info "Docker images published for $RELEASE_VERSION"
