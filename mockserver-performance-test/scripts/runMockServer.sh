#!/usr/bin/env bash

docker stop mockserver || true
docker run --env MOCKSERVER_LOG_LEVEL=ERROR --env MOCKSERVER_DISABLE_SYSTEM_OUT=true -d --rm --name mockserver -p 1080:1080 mockserver/mockserver:mockserver-snapshot -serverPort 1080
