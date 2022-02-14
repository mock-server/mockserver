#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
source "${SCRIPT_DIR}/logging.sh"
source "${SCRIPT_DIR}/helm-deploy.sh"

# SKIP_JAVA_BUILD=true ./integration_tests.sh

function build_docker() {
  runCommand "cd ${SCRIPT_DIR}"
  if [[ "${SKIP_JAVA_BUILD:-}" != "true" ]]; then
    runCommand "cd ..; ./mvnw -DskipTests=true package; cd -"
  fi
  runCommand "cp ../mockserver-netty/target/mockserver-netty-*-SNAPSHOT-shaded.jar ../docker/mockserver-netty-shaded.jar"
  runCommand "docker build --no-cache -t mockserver/mockserver:integration_testing --build-arg source=copy ../docker"
  runCommand "rm ../docker/mockserver-netty-shaded.jar"
}

function test() {
  export TEST_CASE="${1}"
  printMessage "Running Test: \"${TEST_CASE}\""
  runCommand "cd ${TEST_CASE}"
  runCommand "./integration_test.sh" || return 1
  runCommand "cd ${SCRIPT_DIR}"
}

function run_all_tests() {
  if [[ "${SKIP_ALL_TESTS:-}" != "true" ]]; then
    # docker compose test
    test "docker_compose_forward_with_override"
    test "docker_compose_remote_host_and_port_by_environment_variable"
    test "docker_compose_server_port_by_command"
    test "docker_compose_server_port_by_environment_variable_long_name"
    test "docker_compose_server_port_by_environment_variable_short_name"
    test "docker_compose_without_server_port"
    # helm test
    start-up-k8s
    test "helm_default_config"
    test "helm_local_docker_container"
    tear-down-k8s
  fi
}

build_docker
run_all_tests
