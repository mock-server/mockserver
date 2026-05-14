#!/usr/bin/env bash
# Build and push the MockServer Docker images (linux/amd64 + linux/arm64) to
# Docker Hub and AWS ECR Public.
#
# Dry-run: docker buildx build (local, no --push), skip ECR login.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/_lib.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --execute) DRY_RUN=false; shift ;;
    -h|--help) echo "Usage: $0 [--dry-run|--execute]"; exit 0 ;;
    *) log_error "Unknown arg: $1"; exit 2 ;;
  esac
done

require_cmd docker
require_cmd curl
require_release_inputs
skip_unless_release_type "docker" full,post-maven,docker-only

log_step "Publish Docker images $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

# ---- Locate or fetch shaded JAR -------------------------------------------
cd "$REPO_ROOT"
SHADED_JAR=$(find mockserver/mockserver-netty/target -name 'mockserver-netty-*-shaded.jar' -print -quit 2>/dev/null || true)
if [[ -z "$SHADED_JAR" ]]; then
  log_info "Local shaded JAR not found — downloading from Maven Central"
  mkdir -p mockserver/mockserver-netty/target
  SHADED_JAR="mockserver/mockserver-netty/target/mockserver-netty-${RELEASE_VERSION}-shaded.jar"
  if is_dry_run && ! curl -sf -I "https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/${RELEASE_VERSION}/mockserver-netty-${RELEASE_VERSION}-shaded.jar" >/dev/null 2>&1; then
    log_dry "skip: download $RELEASE_VERSION JAR (not yet on Maven Central — would normally wait)"
    # Use the current SNAPSHOT shaded jar as a stand-in for local docker build test.
    SHADED_JAR=$(find mockserver/mockserver-netty/target -name 'mockserver-netty-*-shaded.jar' -print -quit 2>/dev/null || true)
    if [[ -z "$SHADED_JAR" ]]; then
      log_dry "no local JAR available — running 'mvn package' to produce one"
      in_docker "$MAVEN_IMAGE" -w /build/mockserver -v mockserver-m2-cache:/root/.m2 \
        -- mvn -DskipTests -pl mockserver-netty -am package
      SHADED_JAR=$(find mockserver/mockserver-netty/target -name 'mockserver-netty-*-shaded.jar' -print -quit 2>/dev/null || true)
    fi
  else
    curl -fsSL --max-time 300 --connect-timeout 30 --retry 3 --retry-delay 5 \
      -o "$SHADED_JAR" \
      "https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/${RELEASE_VERSION}/mockserver-netty-${RELEASE_VERSION}-shaded.jar"
  fi
fi
[[ -n "$SHADED_JAR" && -f "$SHADED_JAR" ]] || { log_error "No shaded JAR available"; exit 1; }
log_info "Using JAR: $SHADED_JAR"
cp "$SHADED_JAR" docker/local/mockserver-netty-jar-with-dependencies.jar
cp "$SHADED_JAR" docker/graaljs/mockserver-netty-jar-with-dependencies.jar

# ---- Auth (skipped in dry-run) --------------------------------------------
if ! is_dry_run; then
  log_info "Login to Docker Hub + ECR Public"
  "$REPO_ROOT/.buildkite/scripts/docker-login.sh"
  "$REPO_ROOT/.buildkite/scripts/ecr-login.sh"
fi

FULL_TAG="mockserver-$RELEASE_VERSION"
SHORT_TAG="$RELEASE_VERSION"
ECR_REPO="public.ecr.aws/mockserver/mockserver"

PUSH_FLAG=$(is_dry_run && echo "--load" || echo "--push")
# --load can't be used with multi-arch builds, fall back to local single-arch
# for dry-run.
PLATFORMS=$(is_dry_run && echo "linux/amd64" || echo "linux/amd64,linux/arm64")

log_info "Build (platforms=$PLATFORMS, mode=$PUSH_FLAG)"
docker buildx create --use --name multiarch 2>/dev/null || docker buildx use multiarch

docker buildx build \
  --platform "$PLATFORMS" \
  $PUSH_FLAG \
  --tag "mockserver/mockserver:$FULL_TAG" \
  --tag "mockserver/mockserver:$SHORT_TAG" \
  --tag "mockserver/mockserver:latest" \
  --tag "${ECR_REPO}:$FULL_TAG" \
  --tag "${ECR_REPO}:$SHORT_TAG" \
  --tag "${ECR_REPO}:latest" \
  docker/local

log_info "Build GraalJS variant"
docker buildx build \
  --platform "$PLATFORMS" \
  $PUSH_FLAG \
  --build-arg source=copy \
  --tag "mockserver/mockserver:$FULL_TAG-graaljs" \
  --tag "mockserver/mockserver:$SHORT_TAG-graaljs" \
  --tag "mockserver/mockserver:latest-graaljs" \
  --tag "${ECR_REPO}:$FULL_TAG-graaljs" \
  --tag "${ECR_REPO}:$SHORT_TAG-graaljs" \
  --tag "${ECR_REPO}:latest-graaljs" \
  docker/graaljs

if is_dry_run; then
  log_dry "skip: push to Docker Hub + ECR (used --load instead of --push)"
fi

log_info "Docker publish complete"
