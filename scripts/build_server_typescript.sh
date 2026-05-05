#!/usr/bin/env bash

# Ran successfully with Node.js 16.  Experienced issues with Node.js 12
npx swagger-typescript-api -n mockServer.d.ts \
   -p https://app.swaggerhub.com/apiproxy/schema/file/apis/jamesdbloom/mock-server-openapi/5.15.x?format=yaml \
   -r true \
   --no-client \
   --extract-request-params \
   --extract-request-body

LICENCE="/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */
"

echo "${LICENCE}" | cat - mockServer.d.ts > temp && mv temp mockServer.d.ts
