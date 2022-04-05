#!/usr/bin/env bash

cp /Users/jamesbloom/.m2/repository/org/mock-server/mockserver-netty/5.13.3-SNAPSHOT/mockserver-netty-5.13.3-SNAPSHOT-shaded.jar ./mockserver-netty-shaded.jar
docker build --no-cache -t mockserver/mockserver:local-snapshot .
