#!/usr/bin/env bash

export MAVEN_OPTS='-XX:MaxPermSize=1024m -Xmx1024m'

rm -rf jekyll-www.mock-server.com/_site
rm -rf jekyll-www.mock-server.com/apidocs

mvn javadoc:aggregate -DreportOutputDirectory='${basedir}/jekyll-www.mock-server.com'

cd jekyll-www.mock-server.com
bundle exec jekyll build
cd _site
cp mock_server/mockserver_clients.html .
cp mock_server/running_mock_server.html .
cp mock_server/debugging_issues.html .
cp mock_server/creating_expectations_request_matchers.html .
cp mock_server/creating_expectations.html .
cp mock_server/creating_expectations_java_example_code.html .
#zip -r mockserver_site.zip .