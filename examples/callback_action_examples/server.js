function classCallback() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some.*"
        },
        "httpClassCallback": {
            "callbackClass": "org.mockserver.examples.mockserver.CallbackActionExamples$TestExpectationCallback"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function objectCallback() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    var callback = function (request) {
        if (request.method === 'POST') {
            return {
                'statusCode': 201,
                'headers': {
                    "x-object-callback": ["test_object_callback_header"]
                },
                'body': "an_object_callback_response"
            };
        } else {
            return {
                'statusCode': 404
            };
        }
    };
    mockServerClient("localhost", 1080)
        .mockWithCallback(
            {
                'path': '/some/path'
            },
            callback
        )
        .then(
            function () {
                console.log("expectation created");
            },
            function (error) {
                console.log(error);
            }
        );
}

function createExpectationWithinObjectCallback() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    var callback = function (request) {
        if (request.method === 'POST') {
            mockServerClient("localhost", 1080)
                .mockAnyResponse({
                    "httpRequest": {
                        "path": "/some/otherPath"
                    },
                    "httpResponse": {
                        "body": request.body.string
                    }
                })
                .then(
                    function () {
                        console.log("chained expectation created");
                    },
                    function (error) {
                        console.log(error);
                    }
                );
            return {
                'statusCode': 202,
                'body': "request processed"
            };
        } else {
            return {
                'statusCode': 404
            };
        }
    };
    mockServerClient("localhost", 1080)
        .mockWithCallback(
            {
                'path': '/some/path'
            },
            callback
        )
        .then(
            function () {
                console.log("expectation created");
            },
            function (error) {
                console.log(error);
            }
        );
}

createExpectationWithinObjectCallback();