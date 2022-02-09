function createExpectation() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .mockAnyResponse({
            "httpRequest": {
                "method": "GET",
                "path": "/view/cart",
                "queryStringParameters": {
                    "cartId": ["055CA455-1DF7-45BB-8535-4F83E7266092"]
                },
                "cookies": {
                    "session": "4930456C-C718-476F-971F-CB8E047AB349"
                }
            },
            "httpResponse": {
                "body": "some_response_body"
            }
        })
        .then(
            function () {
                console.log("expectation created");
            },
            function (error) {
                console.log(error);
            }
        );
}

function largeNumber() {
Number.prototype.toFixedSpecial = function(n) {
    var str = this.toFixed(n);
    if (str.indexOf('e+') === -1)
        return str;

    // if number is in scientific notation, pick (b)ase and (p)ower
    str = str.replace('.', '').split('e+').reduce(function(b, p) {
        return b + Array(p - b.length + 2).join(0);
    });

    if (n > 0)
        str += '.' + Array(n + 1).join(0);

    return str;
};

var mockServerClient = require('mockserver-client').mockServerClient;
let largeNumber = 1000000000000100000001;
let expectation = {
    "httpRequest": {
        "method": "GET",
        "path": "/test"
    },
    "httpResponse": {
        "statusCode": 200,
        "reasonPhrase": "OK",
        "headers": [
            {
                "name": "Content-Type",
                "values": [
                    "application/json"
                ]
            }
        ],
        "body": {
            "value": largeNumber.toFixedSpecial(0)
        }
    }
};
mockServerClient("localhost", 1080)
    .mockAnyResponse(expectation)
    .then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

largeNumber();

function createExpectationOverTLS() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080, undefined, true)
        .mockAnyResponse({
            "httpRequest": {
                "method": "GET",
                "path": "/view/cart",
                "queryStringParameters": {
                    "cartId": ["055CA455-1DF7-45BB-8535-4F83E7266092"]
                },
                "cookies": {
                    "session": "4930456C-C718-476F-971F-CB8E047AB349"
                }
            },
            "httpResponse": {
                "body": "some_response_body"
            }
        })
        .then(
            function () {
                console.log("expectation created");
            },
            function (error) {
                console.log(error);
            }
        );
}

function verifyRequestsExact() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .verify(
            {
                'path': '/some/path'
            }, 2, 2)
        .then(
            function () {
                console.log("request found exactly 2 times");
            },
            function (error) {
                console.log(error);
            }
        );
}

function verifyRequestsReceiveAtLeastTwice() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .verify(
            {
                'path': '/some/path'
            }, 2)
        .then(
            function () {
                console.log("request found exactly 2 times");
            },
            function (error) {
                console.log(error);
            }
        );
}

function verifyRequestsReceiveAtMostTwice() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .verify(
            {
                'path': '/some/path'
            }, 0, 2)
        .then(
            function () {
                console.log("request found exactly 2 times");
            },
            function (error) {
                console.log(error);
            }
        );
}

function verifyRequestsReceiveExactlyTwice() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .verify(
            {
                'path': '/some/path'
            }, 2, 2)
        .then(
            function () {
                console.log("request found exactly 2 times");
            },
            function (error) {
                console.log(error);
            }
        );
}

function verifyRequestsReceiveAtLeastTwiceByOpenAPI() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .verify(
            {
                'specUrlOrPayload': 'https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json'
            }, 2)
        .then(
            function () {
                console.log("request found exactly 2 times");
            },
            function (error) {
                console.log(error);
            }
        );
}

function verifyRequestsReceiveExactlyOnceByOpenAPIWithOperation() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .verify(
            {
                'specUrlOrPayload': 'org/mockserver/mock/openapi_petstore_example.json',
                'operationId': 'showPetById'
            }, 1, 1)
        .then(
            function () {
                console.log("request found exactly 2 times");
            },
            function (error) {
                console.log(error);
            }
        );
}

function verifyRequestSequence() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .verifySequence(
            {
                'path': '/some/path/one'
            },
            {
                'path': '/some/path/two'
            },
            {
                'path': '/some/path/three'
            }
        )
        .then(
            function () {
                console.log("request sequence found in the order specified");
            },
            function (error) {
                console.log(error);
            }
        );
}


function verifyRequestSequenceUsingOpenAPI() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .verifySequence(
            {
                'path': '/status'
            },
            {
                'specUrlOrPayload': 'org/mockserver/mock/openapi_petstore_example.json',
                'operationId': 'listPets'
            },
            {
                'specUrlOrPayload': 'org/mockserver/mock/openapi_petstore_example.json',
                'operationId': 'showPetById'
            }
        )
        .then(
            function () {
                console.log("request sequence found in the order specified");
            },
            function (error) {
                console.log(error);
            }
        );
}

function retrieveRecordedRequests() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .retrieveRecordedRequests({
            "path": "/some/path",
            "method": "POST"
        })
        .then(
            function (recordedRequests) {
                console.log(JSON.stringify(recordedRequests, null, "  "));
            },
            function (error) {
                console.log(error);
            }
        );
}

function retrieveRecordedLogMessages() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .retrieveLogMessages({
            "path": "/some/path",
            "method": "POST"
        })
        .then(
            function (logMessages) {
                // logMessages is a String[]
                console.log(logMessages);
            },
            function (error) {
                console.log(error);
            }
        );
}

function clearWithRequestPropertiesMatcher() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .clear({
            'path': '/some/path'
        })
        .then(
            function () {
                console.log("cleared state that matches request matcher");
            },
            function (error) {
                console.log(error);
            }
        );
}

function clearWithOpenAPIRequestMatcher() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .clear({
            "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
            "operationId": "showPetById"
        })
        .then(
            function () {
                console.log("cleared state that matches request matcher");
            },
            function (error) {
                console.log(error);
            }
        );
}

function clearRequestsAndLogsWithRequestPropertiesMatcher() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .clear({
            'path': '/some/path'
        }, 'LOG')
        .then(
            function () {
                console.log("cleared state that matches request matcher");
            },
            function (error) {
                console.log(error);
            }
        );
}

function clearRequestAndLogsWithOpenAPIRequestMatcher() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .clear({
            "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
            "operationId": "showPetById"
        }, 'LOG')
        .then(
            function () {
                console.log("cleared state that matches request matcher");
            },
            function (error) {
                console.log(error);
            }
        );
}

function reset() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .reset()
        .then(
            function () {
                console.log("reset all state");
            },
            function (error) {
                console.log(error);
            }
        );
}