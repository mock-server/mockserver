#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# Run the mockserver-testcontainers integration suite
# (MockServerContainerIntegrationTest) against a real MockServer container
# started via Testcontainers.
#
# The main build has no Docker socket, so the suite's Assume guard would skip it
# there while the job stays green. This step supplies the socket and fails closed
# via assert-suite-ran.sh. Separate step, Ryuk flag and -m 7g: same reasons as
# java-async-broker-test.sh (docs/infrastructure/ci-cd.md, "Gates That Must Fail
# Closed").
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MODULE="mockserver-testcontainers"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i mockserver/mockserver:maven \
  -w /build/mockserver \
  -m 7g \
  --cache maven \
  --docker-socket \
  -e TESTCONTAINERS_RYUK_CONTAINER_PRIVILEGED=false \
  -- bash -ec "
    # Build the module's dependencies without running their tests — the main
    # build step already covers those, and this step must stay scoped to the
    # container integration suite.
    ./mvnw -pl ${MODULE} -am install \
      -DskipTests -DskipITs -Djacoco.skip=true -Dmaven.javadoc.skip=true \
      -Dmaven.gitcommitid.skip=true -P '!build-ui' \
      --batch-mode --no-transfer-progress

    # 'verify' (not 'test') — MockServerContainerIntegrationTest is a Failsafe IT,
    # so it only runs in the integration-test/verify phase.
    ./mvnw -pl ${MODULE} verify \
      -Djacoco.skip=true -Dmaven.gitcommitid.skip=true \
      --batch-mode --no-transfer-progress

    # Fail closed. The Assume guard means a missing/broken Docker socket reports
    # this suite as SKIPPED with Maven still exiting 0 — exactly the false positive
    # this step exists to remove. Note the FAILSAFE report directory: this is an IT,
    # so it does not write to surefire-reports.
    /build/.buildkite/scripts/steps/assert-suite-ran.sh \
      'mockserver-testcontainers/target/failsafe-reports/TEST-*MockServerContainerIntegrationTest.xml'
  "
