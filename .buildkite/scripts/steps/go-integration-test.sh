#!/usr/bin/env bash
# Go client integration tests against a live MockServer built from HEAD.
#
# integration_test.go's skipIfNoServer() skips every test when MOCKSERVER_URL
# is unset. go-unit-test.sh never set it, so the whole integration suite
# skipped on every CI build while the step reported green.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../with-mockserver.sh
source "$SCRIPT_DIR/../with-mockserver.sh"

# NOT `exec`: exec replaces this shell and would discard the EXIT trap that
# with-mockserver.sh registered, orphaning the server container and network.
"$SCRIPT_DIR/../run-in-docker.sh" \
  -i golang:1.23 \
  -w /build/mockserver-client-go \
  --network "$MOCKSERVER_NETWORK" \
  -e "MOCKSERVER_URL=$MOCKSERVER_URL" \
  -e "MOCKSERVER_DNS_ADDRESS=$MOCKSERVER_DNS_ADDRESS" \
  -e "MOCKSERVER_REQUIRE_SERVER=true" \
  -- bash -c "go test ./... -v -count=1 -run 'TestIntegration'"
