#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
source "${SCRIPT_DIR}/../docker-compose.sh"
source "${SCRIPT_DIR}/../logging.sh"

printMessage "Start: \"${SCRIPT_DIR/\//}\""

function integration_test() {
  runCommand "rm -rf ${SCRIPT_DIR}/config"
  start-up
  TEST_EXIT_CODE=0
  sleep 3
  docker-exec-client "curl -v -s -X PUT 'http://mockserver:1080/mockserver/expectation' -d \\\"{
                        'httpRequest' : {
                          'path' : '/some/path'
                        },
                        'httpResponse' : {
                          'body' : 'some_response_body'
                        }
                      }\\\"" || TEST_EXIT_CODE=1
  if [[ "${TEST_EXIT_CODE}" == "0" ]]; then
    RESPONSE_BODY=$(docker-exec-client "curl -v -s -X PUT 'http://mockserver:1080/some/path'")

    if [[ "${RESPONSE_BODY}" != "some_response_body" ]]; then
      printFailureMessage "Failed to retrieve response body for expectation matched by path, found: \"${RESPONSE_BODY}\""
      TEST_EXIT_CODE=1
    fi

    if [[ ! -s "${SCRIPT_DIR}/config/persistedExpectations.json" ]]; then
      printFailureMessage "Expectations were not persisted to: \"${SCRIPT_DIR}/config/persistedExpectations.json\""
      TEST_EXIT_CODE=1
    else
      printMessage "Expectations were persisted to: \"${SCRIPT_DIR}/config/persistedExpectations.json\":"
      cat "${SCRIPT_DIR}/config/persistedExpectations.json"
      echo
    fi
  fi
  logTestResult "${TEST_EXIT_CODE}" "${TEST_CASE}"
#  tear-down
  return ${TEST_EXIT_CODE}
}

integration_test
