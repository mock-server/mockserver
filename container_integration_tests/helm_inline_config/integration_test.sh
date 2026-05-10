#!/usr/bin/env bash
# shellcheck disable=SC2155

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
TEST_CASE="${TEST_CASE:-SCRIPT_DIR}"
source "${SCRIPT_DIR}/../helm-deploy.sh"
source "${SCRIPT_DIR}/../logging.sh"

printMessage "Start: \"${SCRIPT_DIR/\//}\""

function integration_test() {
  start-up "--set image.repositoryNameAndTag=mockserver/mockserver:integration_testing --set app.config.enabled=true --set app.config.properties=mockserver.initializationJsonPath=/config/initializerJson.json --set app.config.initializerJson=[{\"httpRequest\":{\"path\":\"/preset\"},\"httpResponse\":{\"body\":\"preset_response\"}}]"
  TEST_EXIT_CODE=0
  sleep 3
  run-helm-test || TEST_EXIT_CODE=1
  RESPONSE_BODY=$(runCommand "curl -v -s -X PUT 'http://${MOCKSERVER_HOST}/preset'") || TEST_EXIT_CODE=1
  if [[ "${TEST_EXIT_CODE}" == "0" ]]; then
    if [[ "${RESPONSE_BODY}" != "preset_response" ]]; then
      printFailureMessage "Failed to retrieve response body for pre-loaded expectation, found: \"${RESPONSE_BODY}\""
      TEST_EXIT_CODE=1
    fi
  fi
  logTestResult "${TEST_EXIT_CODE}" "${TEST_CASE}"
  tear-down
  return ${TEST_EXIT_CODE}
}

integration_test
