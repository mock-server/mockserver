#!/usr/bin/env bash
# shellcheck disable=SC2155

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
TEST_CASE="${TEST_CASE:-SCRIPT_DIR}"
source "${SCRIPT_DIR}/../helm-deploy.sh"
source "${SCRIPT_DIR}/../logging.sh"

printMessage "Start: \"${SCRIPT_DIR/\//}\""

function integration_test() {
  start-up "--set image.repositoryNameAndTag=mockserver/mockserver:integration_testing --set app.serverPort=1081"
  TEST_EXIT_CODE=0
  sleep 3
  runCommand "curl -v -s -X PUT 'http://${MOCKSERVER_HOST}/mockserver/expectation' -d \"{
                'httpRequest' : {
                  'path' : '/some/path'
                },
                'httpResponse' : {
                  'body' : 'some_response_body'
                }
              }\"" || TEST_EXIT_CODE=1
  if [[ "${TEST_EXIT_CODE}" == "0" ]]; then
    RESPONSE_BODY=$(runCommand "curl -v -s -X PUT 'http://${MOCKSERVER_HOST}/some/path'")

    if [[ "${RESPONSE_BODY}" != "some_response_body" ]]; then
      printErrorMessage "Failed to retrieve response body for expectation matched by path, found: \"${RESPONSE_BODY}\""
      TEST_EXIT_CODE=1
    fi
  fi
  if [[ "${TEST_EXIT_CODE}" != "0" ]]; then
    printErrorMessage "Failed: ${TEST_CASE}"
    docker-compose logs
    printErrorMessage "Failed: ${TEST_CASE}"
  else
    printPassMessage "Passed: ${TEST_CASE}"
  fi
  tear-down
  return ${TEST_EXIT_CODE}
}

integration_test
