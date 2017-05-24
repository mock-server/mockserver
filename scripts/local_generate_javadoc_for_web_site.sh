#!/usr/bin/env bash

export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx1024m'

mvn javadoc:aggregate -DreportOutputDirectory='${basedir}/jekyll-www.mock-server.com'
