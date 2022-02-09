#!/usr/bin/env bash

curl -v -s -X PUT "http://localhost:1080/mockserver/reset"

for counter in $(seq 1 1 5000); do
    # shellcheck disable=SC2059
    printf "\nnumber of requests sent: $counter"

    curl -s -X PUT 'http://localhost:1080/mockserver/expectation' -d "{
                        'httpRequest' : {
                        },
                        'httpOverrideForwardedRequest' : {
                          'responseModifier': {
                            'headers': {
                              'add': {
                                'x-proxy': ['mock-server']
                              }
                            }
                          },
                          'requestOverride': {
                            'socketAddress': {
                              'host' : 'mockserver_target',
                              'port' : 4567,
                              'scheme' : 'HTTPS'
                            }
                          },
                          'responseModifier': {
                            'headers': {
                              'add': {
                                'x-proxy': ['mock-server']
                              }
                            }
                          }
                        }
                      }" > /dev/null
    curl -s -X PUT 'http://localhost:1080/some/path' > /dev/null

    COUNT=$(curl -s -X PUT "http://localhost:1080/mockserver/retrieve?type=REQUESTS" | grep "method" | wc -l)
    printf " number of requests in log: $COUNT"
done