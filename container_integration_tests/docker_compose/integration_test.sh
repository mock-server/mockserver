#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
source "${SCRIPT_DIR}/../docker-compose.sh"
source "${SCRIPT_DIR}/../logging.sh"

printMessage "Start: \"${SCRIPT_DIR/\//}\""

function integration_test() {
  docker-exec-client "curl -v -X PUT \"http://mockServer:1234/mockserver/expectation\" -d '{ \"httpRequest\" : { \"path\" : \"/some/path\" }, \"httpResponse\" : { \"body\" : \"some_response_body\" } }'"
}

start-up

integration_test

tear-down
