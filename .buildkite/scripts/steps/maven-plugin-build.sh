#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i mockserver/mockserver:maven \
  -m 7g \
  -- bash -c 'cd mockserver && ./mvnw clean install -DskipTests && ./mvnw -f ../mockserver-maven-plugin/pom.xml clean verify'
