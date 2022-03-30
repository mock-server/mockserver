#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
cd "${SCRIPT_DIR}/../jekyll-www.mock-server.com"

$(rbenv which bundle) exec jekyll server &
JEKYLL_PID=$!
sleep 2
open http://127.0.0.1:4000/

cleanup() {
  if [[ -z ${JEKYLL_PID+x} ]]; then
    kill JEKYLL_PID
  fi
}

trap cleanup EXIT
wait $JEKYLL_PID