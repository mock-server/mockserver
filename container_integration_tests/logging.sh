#!/usr/bin/env bash

set -euo pipefail

function printMessageWithColour() {
    COLOUR="${2}"
    echo
    printf -v str "%-$((${#1}))s" ' '
    if [[ "${#str}" -gt "275" ]]; then
        printf -v str "%-275s" ' '
    fi
    printf "${COLOUR}%s\e[0m\n" "${str// /=}"
    printf "${COLOUR}%s\e[0m\n" "$(echo $1 | sed 's/@@.*@@/***/g')"
    printf "${COLOUR}%s\e[0m\n" "${str// /=}"
    echo
}

function runCommand() {
    printMessageWithColour >&2 "$1" "\e[0;33m"
    if ! eval "$(echo $1)"; then
        return 1
    fi
}

function printMessage() {
    printMessageWithColour "${1}" "\e[0;32m"
}

function printErrorMessage() {
    printMessageWithColour "${1}" "\e[0;31m"
}
