#!/usr/bin/env bash

set -euo pipefail

function printMessageWithColourAndBorders() {
  COLOUR="${2}"
  echo
  printf -v str "%-$((${#1}))s" ' '
  if [[ "${#str}" -gt "225" ]]; then
    printf "${COLOUR}%s\e[0m\n" "$(echo $1)"
  else
    printf "${COLOUR}%s\e[0m\n" "${str// /=}"
    printf "${COLOUR}%s\e[0m\n" "$(echo $1)"
    printf "${COLOUR}%s\e[0m\n" "${str// /=}"
  fi
  echo
}

function printMessageWithColour() {
  COLOUR="${2}"
  printf "${COLOUR}%s\e[0m\n" "${1}"
}

function printMessage() {
  printMessageWithColourAndBorders >&2 "${1}" "\e[0;33m"
}

function printPassMessage() {
  printMessageWithColourAndBorders >&2 "${1}" "\e[0;32m"
}

function printPlainPassMessage() {
  printMessageWithColour >&2 "${1}" "\e[0;32m"
}

function printFailureMessage() {
  printMessageWithColourAndBorders >&2 "${1}" "\e[0;31m"
}

function printPlainFailureMessage() {
  printMessageWithColour >&2 "${1}" "\e[0;31m"
}

function runCommand() {
  printMessageWithColourAndBorders >&2 "$1" "\e[0;33m"
  if ! eval "$(echo $1)"; then
    return 1
  fi
}

function retryCommand() {
  n=0
  until [ "$n" -ge "${2:-3}" ] || runCommand "${1}"; do
    n=$((n + 1))
    printMessage "${n} of ${2:-3} retries for command: ${1}"
    printMessage "sleeping ${3:-30}s before retry"
    sleep "${3:-30}"
  done
  if [ "$n" -ge "${2:-3}" ]; then
    printMessage "The command has failed after $n attempts."
    return 1
  fi
}

function logTestResult() {
  TEST_EXIT_CODE="${1}"
  TEST_CASE="${2}"
  if [[ "${TEST_EXIT_CODE}" != "0" ]]; then
    printFailureMessage "Failed: ${TEST_CASE}"
    if [ -n "${3:-}" ]; then
      container-logs "${3:-}"
    fi
    container-logs
    printFailureMessage "Failed: ${TEST_CASE}"
    printPlainFailureMessage "  - ${TEST_CASE}" >>${FAIL_LOG_FILE} 2>&1
  else
    printPassMessage "Passed: ${TEST_CASE}"
    printPlainPassMessage "  - ${TEST_CASE}" >>${PASS_LOG_FILE} 2>&1
  fi
}
