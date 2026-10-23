#!/usr/bin/env bash
# Simulate an error by resetting the connection (TCP RST), so the client sees "connection reset by peer".
# Assumes MOCKSERVER_URL is set (defaults to http://localhost:1080).

curl -X PUT "${MOCKSERVER_URL:-http://localhost:1080}/mockserver/expectation" \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpError": {
    "resetConnection": true
  }
}'
