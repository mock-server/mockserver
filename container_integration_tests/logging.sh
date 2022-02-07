#!/usr/bin/env bash

set -euo pipefail

function printMessageWithColour() {
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

function printMessage() {
  printMessageWithColour "${1}" "\e[0;32m"
}

function printErrorMessage() {
  printMessageWithColour "${1}" "\e[0;31m"
}

function runCommand() {
  printMessageWithColour >&2 "$1" "\e[0;33m"
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
