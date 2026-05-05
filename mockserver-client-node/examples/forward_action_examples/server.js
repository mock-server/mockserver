function forwardRequestInHTTP() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpForward": {
            "host": "mock-server.com",
            "port": 80,
            "scheme": "HTTP"
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

function forwardRequestInHTTPS() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpForward": {
            "host": "mock-server.com",
            "port": 443,
            "scheme": "HTTPS"
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

function forwardOverriddenRequest() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpOverrideForwardedRequest": {
            "httpRequest": {
                "path": "/some/other/path",
                "headers": {
                    "Host": ["target.host.com"]
                }
            }
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

function forwardOverriddenRequestAndChangeReponse() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpOverrideForwardedRequest": {
            "httpRequest": {
                "path": "/some/other/path",
                "headers": {
                    "Host": ["target.host.com"]
                }
            },
            "httpResponse": {
                "body": "some_overridden_body"
            }
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

function forwardOverriddenRequestAndChangeHostAndPort() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpOverrideForwardedRequest": {
            "httpRequest": {
                "path": "/some/other/path",
                "headers": {
                    "Host": ["any.host.com"]
                },
                "socketAddress": {
                    "host": "target.host.com",
                    "port": 1234,
                    "scheme": "HTTPS"
                }
            }
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

function forwardOverriddenRequestWithDelay() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpOverrideForwardedRequest": {
            "httpRequest": {
                "path": "/some/other/path",
                "headers": {
                    "Host": ["target.host.com"]
                }
            },
            "delay": {"timeUnit": "SECONDS", "value": 20}
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

function javascriptTemplatedForward() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpForwardTemplate": {
            "template": "return {\n" +
                "    'path' : \"/somePath\",\n" +
                "    'queryStringParameters' : {\n" +
                "        'userId' : request.queryStringParameters && request.queryStringParameters['userId']\n" +
                "    },\n" +
                "    'headers' : {\n" +
                "        'Host' : [ \"localhost:1081\" ]\n" +
                "    },\n" +
                "    'body': JSON.stringify({'name': 'value'})\n" +
                "};",
            "templateType": "JAVASCRIPT"
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

function javascriptTemplatedForwardWithDelay() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpForwardTemplate": {
            "template": "return {\n" +
                "    'path' : \"/somePath\",\n" +
                "    'cookies' : {\n" +
                "        'SessionId' : request.cookies && request.cookies['SessionId']\n" +
                "    },\n" +
                "    'headers' : {\n" +
                "        'Host' : [ \"localhost:1081\" ]\n" +
                "    },\n" +
                "    'keepAlive' : true,\n" +
                "    'secure' : true,\n" +
                "    'body' : \"some_body\"\n" +
                "};",
            "templateType": "JAVASCRIPT",
            "delay": {"timeUnit": "SECONDS", "value": 20}
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

function velocityTemplatedForward() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpForwardTemplate": {
            "template": "{\n" +
                "    'path' : \"/somePath\",\n" +
                "    'queryStringParameters' : {\n" +
                "        'userId' : [ \"$!request.queryStringParameters['userId'][0]\" ]\n" +
                "    },\n" +
                "    'cookies' : {\n" +
                "        'SessionId' : \"$!request.cookies['SessionId']\"\n" +
                "    },\n" +
                "    'headers' : {\n" +
                "        'Host' : [ \"localhost:1081\" ]\n" +
                "    },\n" +
                "    'body': \"{'name': 'value'}\"\n" +
                "}",
            "templateType": "VELOCITY"
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