#!/usr/bin/env bash

set -euo pipefail

function docker-exec() {
  if [[ -z "${TEST_CASE:-}" ]]; then
    runCommand "docker-compose exec -T ${1} /bin/bash -c \"${2}\""
  else
    runCommand "docker-compose -p ${TEST_CASE} exec -T ${1} /bin/bash -c \"${2}\""
  fi
}

function docker-exec-client() {
  docker-exec "client" "${1}"
}

function tear-down() {
  runCommand "docker-compose -p ${TEST_CASE} down --remove-orphans || true"
}

function start-up() {
  runCommand "docker-compose -p ${TEST_CASE} up --build -d"
}

function container-logs() {
  printMessage "mockserver logs"
  docker-compose logs
}
