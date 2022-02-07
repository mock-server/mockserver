#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
source "${SCRIPT_DIR}/../docker-compose.sh"
source "${SCRIPT_DIR}/../logging.sh"

printMessage "Start: \"${SCRIPT_DIR/\//}\""

function integration_test() {
  start-up
  TEST_EXIT_CODE=0
  sleep 3
  docker-exec-client "curl -v -X PUT 'http://mockserver:1234/mockserver/expectation' -d \\\"{
                        'httpRequest' : {
                          'path' : '/some/path'
                        },
                        'httpResponse' : {
                          'body' : 'some_response_body'
                        }
                      }\\\" 1> >(grep '.*' 1>&2) 2> >(grep '^[<>]' 1>&2)" || TEST_EXIT_CODE=1
  RESPONSE_BODY=$(docker-exec-client "curl -v -X PUT 'http://mockserver:1234/some/path' 2> >(grep '^[<>]' 1>&2)")

  if [[ "${RESPONSE_BODY}" != "some_response_body" ]]; then
    printErrorMessage "Failed to retrieve response body for expectation matched by path, found: \"${RESPONSE_BODY}\""
    TEST_EXIT_CODE=1
  fi
  if [[ "${TEST_EXIT_CODE}" != "0" ]]; then
    docker-compose logs
  fi
  tear-down
  return ${TEST_EXIT_CODE}
}

integration_test
