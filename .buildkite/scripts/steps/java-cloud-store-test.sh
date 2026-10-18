#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# Run the cloud blob-store contract suites (S3/s3mock, GCS/fake-gcs,
# Azure/Azurite) against real backing services via Testcontainers.
#
# A separate step, not `-s` on java-build.sh: run-in-docker.sh exits 0 for a
# socket step on PR builds, which would silently skip the whole reactor.
# The suites' Assume guard makes a missing socket a SKIP, so the step fails
# closed via assert-suite-ran.sh below.
#
# TESTCONTAINERS_RYUK_CONTAINER_PRIVILEGED=false: the agents' userns-remapped
# dockerd rejects Testcontainers' default privileged Ryuk. Keep that exact name;
# TESTCONTAINERS_RYUK_PRIVILEGED is read by nothing. Do not swap in
# RYUK_DISABLED (leaks containers on JVM death).
#
# -m 7g: mockserver/.mvn/jvm.config grants Maven -Xmx6144m, so a 4g cgroup
# OOM-kills the -am build before any test runs. Do not cap the heap instead.
# Rationale and rejected alternatives: docs/infrastructure/ci-cd.md
# ("Gates That Must Fail Closed").
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MODULES="mockserver-blob-s3,mockserver-blob-gcs,mockserver-blob-azure"

# Image names come from test-container-images.properties via test-images.sh, the
# same file the suites read. ecr-pull-through-login.sh may set
# MOCKSERVER_TEST_IMAGE_REGISTRY; unset, test_image resolves the public names.
#   s3mock           -> S3BlobStoreContractTest / S3ExpectationPersistenceReloadTest
#   fake-gcs-server  -> GcsBlobStoreContractTest / GcsBlobStoreRegistrarConfigWiringTest
#   azurite          -> AzureBlobStoreContractTest / AzureBlobStoreRegistrarConfigWiringTest
source "$SCRIPT_DIR/../lib/test-images.sh"
source "$SCRIPT_DIR/../lib/ecr-pull-through-login.sh"

# Pre-pull on the host daemon (with retry) before Maven runs, so a throttled
# registry fails fast here rather than minutes later as an opaque Testcontainers
# ContainerFetchException. The suites pull through the mounted socket, so they
# then hit the warm host cache.
"$SCRIPT_DIR/../lib/pre-pull-images.sh" \
  "$(test_image s3mock)" \
  "$(test_image fake-gcs-server)" \
  "$(test_image azurite)"

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
    # Build the modules' dependencies without running their tests — the main
    # build step already covers those, and this step must stay scoped to the
    # cloud contract suites.
    ./mvnw -q -pl ${MODULES} -am install \
      -DskipTests -DskipITs -Djacoco.skip=true -Dmaven.javadoc.skip=true \
      -Dmaven.gitcommitid.skip=true -P '!build-ui' \
      --batch-mode --no-transfer-progress

    ./mvnw -pl ${MODULES} test \
      -Djacoco.skip=true -Dmaven.gitcommitid.skip=true \
      --batch-mode --no-transfer-progress

    # Fail closed. The Assume guards mean a missing/broken Docker socket
    # reports these suites as SKIPPED with Maven still exiting 0 — exactly
    # the false positive this step exists to remove.
    /build/.buildkite/scripts/steps/assert-suite-ran.sh \
      'mockserver-blob-s3/target/surefire-reports/TEST-*BlobStoreContractTest.xml' \
      'mockserver-blob-s3/target/surefire-reports/TEST-*S3ExpectationPersistenceReloadTest.xml' \
      'mockserver-blob-gcs/target/surefire-reports/TEST-*BlobStoreContractTest.xml' \
      'mockserver-blob-gcs/target/surefire-reports/TEST-*RegistrarConfigWiringTest.xml' \
      'mockserver-blob-azure/target/surefire-reports/TEST-*BlobStoreContractTest.xml' \
      'mockserver-blob-azure/target/surefire-reports/TEST-*RegistrarConfigWiringTest.xml'
  "
