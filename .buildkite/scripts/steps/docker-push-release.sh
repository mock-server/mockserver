#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${RELEASE_TAG:-}" ]]; then
  RELEASE_TAG="${BUILDKITE_TAG:-}"
fi

if [[ -z "$RELEASE_TAG" ]]; then
  echo "Error: RELEASE_TAG environment variable is required (e.g. mockserver-5.15.0)"
  echo "Set it via Buildkite build environment or trigger the build from a git tag."
  exit 1
fi

FULL_TAG="$RELEASE_TAG"
SHORT_TAG="${RELEASE_TAG#mockserver-}"

if [[ "$FULL_TAG" == "$SHORT_TAG" ]]; then
  echo "Error: RELEASE_TAG must start with 'mockserver-' (e.g. mockserver-5.15.0)"
  exit 1
fi

echo "--- :info: Building release image"
echo "Full tag:  mockserver/mockserver:${FULL_TAG}"
echo "Short tag: mockserver/mockserver:${SHORT_TAG}"

.buildkite/scripts/docker-login.sh
.buildkite/scripts/ecr-login.sh

ECR_REPO="public.ecr.aws/mockserver/mockserver"

DOCKER_CMD="docker buildx build --platform linux/amd64,linux/arm64 --push --tag mockserver/mockserver:${FULL_TAG} --tag mockserver/mockserver:${SHORT_TAG} --tag ${ECR_REPO}:${FULL_TAG} --tag ${ECR_REPO}:${SHORT_TAG} --file docker/Dockerfile ."

echo "┌──────────────────────────────────────────────────────────────────"
echo "│ Docker Command (copy to reproduce locally):"
echo "│"
echo "│   $DOCKER_CMD"
echo "│"
echo "└──────────────────────────────────────────────────────────────────"
echo ""

docker buildx create --use --name multiarch 2>/dev/null || docker buildx use multiarch
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --push \
  --tag "mockserver/mockserver:${FULL_TAG}" \
  --tag "mockserver/mockserver:${SHORT_TAG}" \
  --tag "${ECR_REPO}:${FULL_TAG}" \
  --tag "${ECR_REPO}:${SHORT_TAG}" \
  --file docker/Dockerfile \
  .

echo "--- :docker: Building and pushing GraalJS variant"
exec docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --push \
  --tag "mockserver/mockserver:${FULL_TAG}-graaljs" \
  --tag "mockserver/mockserver:${SHORT_TAG}-graaljs" \
  --tag "${ECR_REPO}:${FULL_TAG}-graaljs" \
  --tag "${ECR_REPO}:${SHORT_TAG}-graaljs" \
  --file docker/graaljs/Dockerfile \
  .
