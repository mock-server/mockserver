#!/usr/bin/env bash

# ./standalone_performance_test_memory_size.sh 2>&1 > standalone_performance_test_memory_size.log

export JAVA_HOME=`/usr/libexec/java_home -v 13`

ulimit -S -n 49152

curl -v -k -X PUT http://localhost:1080/mockserver/stop || true
sleep 2

export TYPE=memory

for outterLoop in 100 200 500 1000 2000 4000
do
  java -Xmx${outterLoop}m -Dmockserver.logLevel=WARN \
    -Dmockserver.disableSystemOut=true \
    -jar ~/.m2/repository/org/mock-server/mockserver-netty/5.11.1-SNAPSHOT/mockserver-netty-5.11.1-SNAPSHOT-jar-with-dependencies.jar \
    -serverPort 1080 &
  sleep 5
  echo "+++ Create Expectation"
  curl -v -s -X PUT http://127.0.0.1:1080/expectation -d '[
      {
          "httpRequest": {
              "path": "/not_simple"
          },
          "httpResponse": {
              "statusCode": 200,
              "body": "some not simple response"
          },
          "times": {
              "unlimited": true
          }
      },
      {
          "httpRequest": {
              "method": "POST",
              "path": "/simple"
          },
          "httpResponse": {
              "statusCode": 200,
              "body": "some simple POST response"
          },
          "times": {
              "unlimited": true
          }
      },
      {
          "httpRequest": {
              "path": "/forward"
          },
          "httpOverrideForwardedRequest": {
              "httpRequest": {
                  "headers": {
                      "host": [ "127.0.0.1:1080" ]
                  },
                  "path": "/simple"
              }
          },
          "times": {
              "unlimited": true
          }
      },
      {
          "httpRequest": {
              "path": "/simple"
          },
          "httpResponse": {
              "statusCode": 200,
              "body": "some simple response"
          },
          "times": {
              "unlimited": true
          }
      }
  ]'
  for count in 500 1000 2000
  do
      export NAME=${outterLoop}_${count}_${TYPE}
      locust --loglevel=DEBUG --headless --only-summary --csv="${NAME}" -u $count -r 15 -t 120 --host=http://127.0.0.1:1080 2>&1
      curl -v -k -X PUT http://localhost:1080/mockserver/reset
  done
  curl -v -k -X PUT http://localhost:1080/mockserver/stop || true
  sleep 20
done
