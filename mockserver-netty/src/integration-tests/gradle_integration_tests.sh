#!/usr/bin/env bash

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
source "${SCRIPT_DIR}/logging.sh"

function run_gradle_build() {
  printMessage "TEST: $1"
  runCommand "$SCRIPT_DIR/gradle/gradlew --refresh-dependencies -p $SCRIPT_DIR/$1 -PmockserverVersion=${2:-5.14.0} test"
}

run_gradle_build gradle-netty-shaded-dependencies $1
run_gradle_build gradle-netty-no-dependencies-dependencies $1
