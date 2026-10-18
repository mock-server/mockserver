#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# Run the transparent-proxy original-destination end-to-end suites:
#
#   SoOriginalDstEndToEndIntegrationTest   (iptables REDIRECT + SO_ORIGINAL_DST, --cap-add=NET_ADMIN)
#   TproxyEndToEndIntegrationTest          (iptables TPROXY / IP_TRANSPARENT,    --cap-add=NET_ADMIN)
#   EbpfOriginalDestinationEndToEndIntegrationTest (pinned BPF map read path,    --privileged)
#
# OPT-IN (RUN_TRANSPARENT_PROXY_E2E=true): the suites shell out to the `docker`
# CLI, which the maven CI image lacks, and need NET_ADMIN/--privileged sibling
# containers, which the agents' userns-remapped dockerd rejects. On these agents
# they can only SKIP, so a fail-closed assert would be permanently red without
# testing anything; by default the step prints a loud notice instead. Set the
# flag only on an agent with the docker CLI, the socket and privilege support.
#
# -m 7g: mockserver/.mvn/jvm.config grants Maven -Xmx6144m, so a 4g cgroup
# OOM-kills the -am build. See docs/infrastructure/ci-cd.md ("Gates That Must
# Fail Closed") for both decisions.
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE="mockserver-netty"

if [[ "${RUN_TRANSPARENT_PROXY_E2E:-false}" != "true" ]]; then
  echo "+++ :warning: transparent-proxy end-to-end suites NOT executed on this agent"
  cat <<'EOF'
The three privileged transparent-proxy end-to-end suites

  SoOriginalDstEndToEndIntegrationTest
  TproxyEndToEndIntegrationTest
  EbpfOriginalDestinationEndToEndIntegrationTest

are COLLECTED by Failsafe but were NOT run on this agent, so their coverage is
NOT asserted here. They shell out to the `docker` CLI and need NET_ADMIN /
--privileged sibling containers:

  * the maven CI image has no `docker` CLI binary, and
  * the elastic-ci-stack agents run dockerd with user-namespace remapping, which
    rejects --privileged (the eBPF suite).

Running them under a fail-closed assertion on such an agent would go permanently
RED without testing anything. To run them for real, use an agent with the docker
CLI, the Docker socket, and NET_ADMIN/--privileged support, and set
RUN_TRANSPARENT_PROXY_E2E=true. See docs/infrastructure/ci-cd.md and
docs/infrastructure/service-mesh.md.
EOF
  exit 0
fi

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i mockserver/mockserver:maven \
  -w /build/mockserver \
  -m 7g \
  --cache maven \
  --docker-socket \
  -- bash -ec "
    # Build the module and its dependencies (produces the fat JAR the suites mount
    # into the sibling container via findFatJar); the main build covers unit tests.
    ./mvnw -pl ${MODULE} -am install \
      -DskipTests -DskipITs -Djacoco.skip=true -Dmaven.javadoc.skip=true \
      -Dmaven.gitcommitid.skip=true -P '!build-ui' \
      --batch-mode --no-transfer-progress

    # 'verify' (not 'test') — these are Failsafe ITs, so they only run in the
    # integration-test/verify phase. Scope to the three transparent-proxy suites.
    ./mvnw -pl ${MODULE} verify \
      -Dtest='ZzzNoSuchUnitTest' \
      -Dit.test='TproxyEndToEndIntegrationTest,SoOriginalDstEndToEndIntegrationTest,EbpfOriginalDestinationEndToEndIntegrationTest' \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Djacoco.skip=true -Dmaven.gitcommitid.skip=true \
      --batch-mode --no-transfer-progress

    # Fail closed. The Assume guards mean a missing docker CLI, an unusable socket,
    # a missing kernel module, or a daemon that refuses the privileged container
    # all report these suites as SKIPPED with Maven still exiting 0 — exactly the
    # false positive this step exists to remove. These are ITs, so read the
    # FAILSAFE report directory.
    /build/.buildkite/scripts/steps/assert-suite-ran.sh \
      'mockserver-netty/target/failsafe-reports/TEST-*SoOriginalDstEndToEndIntegrationTest.xml' \
      'mockserver-netty/target/failsafe-reports/TEST-*TproxyEndToEndIntegrationTest.xml' \
      'mockserver-netty/target/failsafe-reports/TEST-*EbpfOriginalDestinationEndToEndIntegrationTest.xml'
  "
