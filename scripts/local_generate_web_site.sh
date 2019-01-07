#!/usr/bin/env bash

rm -rf jekyll-www.mock-server.com/_site || true

cd jekyll-www.mock-server.com
bundle exec jekyll build
cd _site
cp mock_server/mockserver_clients.html .
cp mock_server/running_mock_server.html .
cp mock_server/debugging_issues.html .
cp mock_server/creating_expectations.html .
open .