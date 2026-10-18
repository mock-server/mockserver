#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# Run the AsyncAPI live-broker integration suites (Kafka, Kafka+Avro, AMQP,
# MQTT 3.1.1, MQTT 5) against real brokers via Testcontainers.
#
# The main build collects these Failsafe ITs but has no Docker socket, so their
# Assume guard skips every test there while the job goes green. This step gives
# them the socket and fails closed via assert-suite-ran.sh below. It is separate
# (not `-s` on java-build.sh) for the same reason as java-cloud-store-test.sh.
#
# TESTCONTAINERS_RYUK_CONTAINER_PRIVILEGED=false: the agents' userns-remapped
# dockerd rejects Testcontainers' default privileged Ryuk. Keep that exact name;
# TESTCONTAINERS_RYUK_PRIVILEGED is read by nothing.
#
# -m 7g: mockserver/.mvn/jvm.config grants Maven -Xmx6144m, so a 4g cgroup
# OOM-kills the -am build before any test runs.
# Rationale and rejected alternatives: docs/infrastructure/ci-cd.md
# ("Gates That Must Fail Closed").
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MODULE="mockserver-async"

# Image names come from test-container-images.properties via test-images.sh, the
# same file the suites read. ecr-pull-through-login.sh may set
# MOCKSERVER_TEST_IMAGE_REGISTRY; unset, test_image resolves the public names.
#   cp-kafka           -> Kafka / KafkaAvro / KafkaSecurity / AsyncApiControlPlane
#   rabbitmq           -> Amqp
#   eclipse-mosquitto  -> Mqtt / Mqtt5 / MqttTls / MqttTlsHandshake
source "$SCRIPT_DIR/../lib/test-images.sh"
source "$SCRIPT_DIR/../lib/ecr-pull-through-login.sh"

# Pre-pull on the host daemon (with retry) before Maven runs, so a throttled
# registry fails fast here rather than minutes later as an opaque Testcontainers
# ContainerFetchException. The suites pull through the mounted socket, so they
# then hit the warm host cache.
"$SCRIPT_DIR/../lib/pre-pull-images.sh" \
  "$(test_image cp-kafka)" \
  "$(test_image rabbitmq)" \
  "$(test_image eclipse-mosquitto)"

# Pass the resolved registry into the container so the Testcontainers suites ask
# for the SAME image names the host just warmed (see TestContainerImages). Empty
# when the pull-through cache is not enabled — the suites then use public names.
IMAGE_REGISTRY_ENV=()
if [[ -n "${MOCKSERVER_TEST_IMAGE_REGISTRY:-}" ]]; then
  IMAGE_REGISTRY_ENV=(-e "MOCKSERVER_TEST_IMAGE_REGISTRY=${MOCKSERVER_TEST_IMAGE_REGISTRY}")
fi

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i mockserver/mockserver:maven \
  -w /build/mockserver \
  -m 7g \
  --cache maven \
  --docker-socket \
  -e TESTCONTAINERS_RYUK_CONTAINER_PRIVILEGED=false \
  "${IMAGE_REGISTRY_ENV[@]+"${IMAGE_REGISTRY_ENV[@]}"}" \
  -- bash -ec "
    # Build the module's dependencies without running their tests — the main
    # build step already covers those, and this step must stay scoped to the
    # live-broker suites.
    ./mvnw -pl ${MODULE} -am install \
      -DskipTests -DskipITs -Djacoco.skip=true -Dmaven.javadoc.skip=true \
      -Dmaven.gitcommitid.skip=true -P '!build-ui' \
      --batch-mode --no-transfer-progress

    # 'verify' (not 'test') — these are Failsafe ITs, so they only run in the
    # integration-test/verify phase.
    ./mvnw -pl ${MODULE} verify \
      -Djacoco.skip=true -Dmaven.gitcommitid.skip=true \
      --batch-mode --no-transfer-progress

    # Fail closed. The Assume guards mean a missing/broken Docker socket reports
    # these suites as SKIPPED with Maven still exiting 0 — exactly the false
    # positive this step exists to remove. Note the FAILSAFE report directory:
    # these are ITs, so they do not write to surefire-reports.
    /build/.buildkite/scripts/steps/assert-suite-ran.sh \
      'mockserver-async/target/failsafe-reports/TEST-*LiveBrokerIntegrationTest.xml'
  "
