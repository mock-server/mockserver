#!/usr/bin/env sh

# set -e

runCommand() {
    echo
    echo "$1"
    echo
    sleep 3
    eval "$1"
    echo
}
finish() {
    runCommand "curl -v -s -X PUT http://$MOCKSERVER_HOST/mockserver/stop"
}
trap finish INT TERM QUIT EXIT

sleep 5

echo "+++ Create Expectation"
curl -v -s -X PUT http://$MOCKSERVER_HOST/expectation -d '[
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

echo "+++ JVM warm up"
runCommand "locust --loglevel=ERROR --headless --only-summary -u 6000 -r 3 -t 20 --host=http://$MOCKSERVER_HOST"
runCommand "curl -v -k -X PUT http://$MOCKSERVER_HOST/mockserver/reset"

echo "+++ HTTP"
for count in 10 100 200 300 400 500 #600 700 800 900 1000 1100 1200 1250
do
    sleep 5
    runCommand "locust --loglevel=INFO --headless --only-summary --csv=nonTLS_$count -u $count -r 15 -t 30 --host=http://$MOCKSERVER_HOST"
    runCommand "curl -v -k -X PUT http://$MOCKSERVER_HOST/mockserver/reset"
done

echo "+++ HTTPS"
for count in 10 100 200 300 400 500 #600 700 800 900 1000 1100 1200 1250
do
    sleep 5
    runCommand "locust --loglevel=INFO --headless --only-summary --csv=TLS_$count -u $count -r 15 -t 30 --host=https://$MOCKSERVER_HOST"
    runCommand "curl -v -k -X PUT http://$MOCKSERVER_HOST/mockserver/reset"
done