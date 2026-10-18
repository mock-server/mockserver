#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# Run the transparent-proxy original-destination end-to-end suites:
#
#   SoOriginalDstEndToEndIntegrationTest   (iptables REDIRECT + SO_ORIGINAL_DST, --cap-add=NET_ADMIN)
#   TproxyEndToEndIntegrationTest          (iptables TPROXY / IP_TRANSPARENT,    --cap-add=NET_ADMIN)
#   EbpfOriginalDestinationEndToEndIntegrationTest (pinned BPF map read path,    --privileged)
#
# The suites run the `docker` CLI against the HOST daemon and bind-mount the fat
# jar by absolute path, so the maven image gets a docker CLI copied from a
# digest-pinned docker:<ver>-cli image, and the checkout is also mounted at its
# host path with Maven run from there. Enabled by RUN_TRANSPARENT_PROXY_E2E=true.
# -m 7g: mockserver/.mvn/jvm.config grants Maven -Xmx6144m, so a 4g cgroup OOM-kills it.
# See docs/infrastructure/ci-cd.md ("Gates That Must Fail Closed").
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
MODULE="mockserver-netty"
DOCKER_CLI_IMAGE="docker:28.5.2-cli@sha256:625d9431a9f54c5a2bc90f24f0e1c3d55b1349fd857dd85035f98c2c9acbdd4d"
SUITE_BASE_IMAGE="eclipse-temurin:17-jre"

if [[ "${RUN_TRANSPARENT_PROXY_E2E:-false}" != "true" ]]; then
  echo "+++ :warning: transparent-proxy end-to-end suites NOT executed (RUN_TRANSPARENT_PROXY_E2E is not true)"
  echo "Set RUN_TRANSPARENT_PROXY_E2E=true to run SoOriginalDst, Tproxy and EbpfOriginalDestination"
  echo "EndToEndIntegrationTest. They need the Docker socket and a daemon that allows NET_ADMIN and"
  echo "--privileged --userns=host containers. See docs/infrastructure/ci-cd.md."
  exit 0
fi

PR="${BUILDKITE_PULL_REQUEST:-false}"
if [[ "$PR" != "false" && "${ALLOW_PR_DOCKER_SOCKET:-false}" != "true" ]]; then
  echo "+++ :lock: Skipping transparent-proxy e2e on PR build #${PR} (Docker socket is withheld from PR builds)"
  exit 0
fi

"$SCRIPT_DIR/../lib/pre-pull-images.sh" "$DOCKER_CLI_IMAGE" "$SUITE_BASE_IMAGE"

CLI_DIR="$(mktemp -d "${TMPDIR:-/tmp}/transparent-proxy-docker-cli.XXXXXX")"
CLI_CONTAINER=""
MAVEN_CONTAINER="tproxy-e2e-${BUILDKITE_JOB_ID:-$$}"
cleanup() {
  docker rm -f "$MAVEN_CONTAINER" >/dev/null 2>&1 || true
  [[ -n "$CLI_CONTAINER" ]] && docker rm -f "$CLI_CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$CLI_DIR"
}
trap cleanup EXIT

CLI_CONTAINER="$(docker create "$DOCKER_CLI_IMAGE")"
docker cp "$CLI_CONTAINER:/usr/local/bin/docker" "$CLI_DIR/docker"
docker cp "$CLI_CONTAINER:/usr/local/libexec/docker/cli-plugins/docker-buildx" "$CLI_DIR/docker-buildx"

"$SCRIPT_DIR/../run-in-docker.sh" \
  -i mockserver/mockserver:maven \
  --name "$MAVEN_CONTAINER" \
  -w "$REPO_ROOT/mockserver" \
  -m 7g \
  --cache maven \
  --docker-socket \
  -v "$REPO_ROOT:$REPO_ROOT" \
  -v "$CLI_DIR/docker:/usr/local/bin/docker:ro" \
  -v "$CLI_DIR/docker-buildx:/usr/local/libexec/docker/cli-plugins/docker-buildx:ro" \
  -- bash -ec "
    docker version --format 'docker CLI {{.Client.Version}} -> daemon {{.Server.Version}}'

    ./mvnw -pl ${MODULE} -am install \
      -DskipTests -DskipITs -Djacoco.skip=true -Dmaven.javadoc.skip=true \
      -Dmaven.gitcommitid.skip=true -P '!build-ui' \
      --batch-mode --no-transfer-progress

    ./mvnw -pl ${MODULE} verify \
      -Dtest='ZzzNoSuchUnitTest' \
      -Dit.test='TproxyEndToEndIntegrationTest,SoOriginalDstEndToEndIntegrationTest,EbpfOriginalDestinationEndToEndIntegrationTest' \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Djacoco.skip=true -Dmaven.javadoc.skip=true \
      -Dmaven.gitcommitid.skip=true -P '!build-ui' \
      --batch-mode --no-transfer-progress

    # The suites' Assume guards turn a missing capability into a SKIP with Maven exit 0.
    ${REPO_ROOT}/.buildkite/scripts/steps/assert-suite-ran.sh \
      'mockserver-netty/target/failsafe-reports/TEST-*SoOriginalDstEndToEndIntegrationTest.xml' \
      'mockserver-netty/target/failsafe-reports/TEST-*TproxyEndToEndIntegrationTest.xml' \
      'mockserver-netty/target/failsafe-reports/TEST-*EbpfOriginalDestinationEndToEndIntegrationTest.xml'
  "
