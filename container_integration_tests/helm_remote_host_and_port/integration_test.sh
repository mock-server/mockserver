#!/usr/bin/env bash
# shellcheck disable=SC2155

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
TEST_CASE="${TEST_CASE:-SCRIPT_DIR}"
source "${SCRIPT_DIR}/../helm-deploy.sh"
source "${SCRIPT_DIR}/../logging.sh"

printMessage "Start: \"${SCRIPT_DIR/\//}\""

function integration_test() {
  start-up "--set image.repositoryNameAndTag=mockserver/mockserver:integration_testing --set service.port=1090" "proxy-target"
  PROXY_TARGET="${MOCKSERVER_HOST}"
  sleep 3
  start-up "--set image.repositoryNameAndTag=mockserver/mockserver:integration_testing --set app.proxyRemoteHost=proxy-target.proxy-target.svc.cluster.local --set app.proxyRemotePort=1090"
  TEST_EXIT_CODE=0
  sleep 3
  runCommand "curl -v -s -X PUT 'http://${PROXY_TARGET}/mockserver/expectation' -d \"{
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
      printFailureMessage "Failed to retrieve response body for expectation matched by path, found: \"${RESPONSE_BODY}\""
      TEST_EXIT_CODE=1
    fi
  fi
  logTestResult "${TEST_EXIT_CODE}" "${TEST_CASE}" "proxy-target"
#  tear-down "proxy-target"
#  tear-down
  return ${TEST_EXIT_CODE}
}

integration_test
