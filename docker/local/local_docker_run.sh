#!/usr/bin/env bash
# shellcheck disable=SC2046

docker stop mockserver_local_snapshot || true
docker run \
  --rm \
  --env MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV=true \
  --env MOCKSERVER_MEMORY_USAGE_DIRECTORY=/logs \
  -v $(pwd):/logs \
  --name mockserver_local_snapshot \
  -p 1080:1080 \
  mockserver/mockserver:local-snapshot -logLevel INFO -serverPort 1080

