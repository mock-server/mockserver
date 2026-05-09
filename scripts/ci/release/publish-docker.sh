#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker

log_step "Publishing Docker images for $RELEASE_VERSION"

cd "$REPO_ROOT"

if is_ci; then
  log_info "Downloading shaded JAR artifact"
  buildkite-agent artifact download "mockserver/mockserver-netty/target/mockserver-netty-*-shaded.jar" .
fi

SHADED_JAR=$(find mockserver/mockserver-netty/target -name 'mockserver-netty-*-shaded.jar' -print -quit 2>/dev/null)
if [[ -z "$SHADED_JAR" ]]; then
  log_error "Shaded JAR not found. Run build-and-test.sh first."
  exit 1
fi

log_info "Using JAR: $SHADED_JAR"
cp "$SHADED_JAR" docker/local/mockserver-netty-jar-with-dependencies.jar

.buildkite/scripts/docker-login.sh

FULL_TAG="mockserver-$RELEASE_VERSION"
SHORT_TAG="$RELEASE_VERSION"

log_info "Building and pushing multi-arch images"
log_info "  Tags: mockserver/mockserver:$FULL_TAG, mockserver/mockserver:$SHORT_TAG, mockserver/mockserver:latest"

docker buildx create --use --name multiarch 2>/dev/null || docker buildx use multiarch
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --push \
  --tag "mockserver/mockserver:$FULL_TAG" \
  --tag "mockserver/mockserver:$SHORT_TAG" \
  --tag "mockserver/mockserver:latest" \
  docker/local

log_info "Docker images published for $RELEASE_VERSION"
