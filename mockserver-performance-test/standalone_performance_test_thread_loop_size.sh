#!/usr/bin/env bash

# ./standalone_performance_test_thread_loop_size.sh 2>&1 > standalone_performance_test_thread_loop_size.log

export JAVA_HOME=`/usr/libexec/java_home -v 13`

ulimit -S -n 49152

curl -v -k -X PUT http://localhost:1080/mockserver/stop || true
sleep 2

export TYPE=threads

for outterLoop in 200 100 50 40 30 25 20 15 10 9 8 7 6 5 4 3 2 1
do
  java -Xmx1g -Dmockserver.logLevel=WARN \
    -Dmockserver.disableSystemOut=true \
    -Dmockserver.actionHandlerThreadCount=${outterLoop} \
    -jar ~/.m2/repository/org/mock-server/mockserver-netty/5.11.1-SNAPSHOT/mockserver-netty-5.11.1-SNAPSHOT-jar-with-dependencies.jar \
    -serverPort 1080 &
  sleep 5
  echo "+++ Create Expectation"
  curl -v -s -X PUT http://localhost:1080/expectation -d '[
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
                      "host": [ "localhost:1080" ]
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
  for count in 100 1000 3000
  do
      locust --loglevel=DEBUG --headless --only-summary --csv="${outterLoop}_${count}_${TYPE}" -u $count -r 15 -t 120 --host=http://localhost:1080
      curl -v -k -X PUT http://localhost:1080/mockserver/reset
  done
  curl -v -k -X PUT http://localhost:1080/mockserver/stop || true
  sleep 20
done
