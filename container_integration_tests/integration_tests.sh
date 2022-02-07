#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
source "${SCRIPT_DIR}/logging.sh"

function build_docker() {
  runCommand "cd ${SCRIPT_DIR}"
  runCommand "cp ../mockserver-netty/target/mockserver-netty-*-SNAPSHOT-jar-with-dependencies.jar ../docker/mockserver-netty-jar-with-dependencies.jar     "
  runCommand "docker build --no-cache -t mockserver/mockserver:integration_testing --build-arg source=copy ../docker"
  runCommand "rm ../docker/mockserver-netty-jar-with-dependencies.jar"
}

function run_all_tests() {
  for testDirectory in */; do
    export TEST_CASE="${testDirectory/\//}"
    printMessage "Running Test: \"${TEST_CASE}\""
    runCommand "cd ${testDirectory}"
    runCommand "./integration_test.sh"
  done
}

build_docker
run_all_tests