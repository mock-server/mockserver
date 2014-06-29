/**
 * Start the client communicating to a MockServer at the specified host and port
 * for example:
 *
 *   var client = mockServerClient("localhost", 1080);
 *
 * @param host the host for the MockServer to communicate with
 * @param port the port for the MockServer to communicate with
 */
var mockServerClient = function (host, port) {
    "use strict";

    var xmlhttp = new XMLHttpRequest(),
        mockServerUrl = "http://" + host + ":" + port,
        /**
         * The default headers added to to the mocked response when using mockSimpleResponse(...)
         */
        defaultResponseHeaders = [
            {"name": "Content-Type", "values": ["application/json; charset=utf-8"]},
            {"name": "Cache-Control", "values": ["no-cache, no-store"]}
        ],
        createResponseMatcher = function (path) {
            var headers = [];
            if (window.location.href.match(/testId\=.*/g)) {
                headers = [
                    {
                        "name": "Referer",
                        "values": [".*" + window.location.href.match(/testId\=.*/g)[0] + ".*"]
                    }
                ];
            }
            return {
                method: "",
                path: path,
                body: "",
                headers: headers,
                cookies: [],
                parameters: []
            }
        },
        createExpectation = function (path, responseBody, statusCode) {
            var headers = [];
            return {
                httpRequest: createResponseMatcher(path),
                httpResponse: {
                    statusCode: statusCode || 200,
                    body: Base64.encode(JSON.stringify(responseBody)),
                    cookies: [],
                    headers: defaultResponseHeaders,
                    delay: {
                        timeUnit: "MICROSECONDS",
                        value: 0
                    }
                },
                times: {
                    remainingTimes: 1,
                    unlimited: false
                }
            };
        },
        /**
         * Setup an expectation in the MockServer by specifying an expectation object
         * for example:
         *
         *   mockServerClient("localhost", 1080).mockAnyResponse(
         *       {
         *           'httpRequest': {
         *               'path': '/somePath',
         *               'body': {
         *                   'type': "EXACT",
         *                   'value': 'someBody'
         *               }
         *           },
         *           'httpResponse': {
         *               'statusCode': 200,
         *               'body': Base64.encode(JSON.stringify({ name: 'first_body' })),
         *               'delay': {
         *                   'timeUnit': 'MILLISECONDS',
         *                   'value': 250
         *               }
         *           },
         *           'times': {
         *               'remainingTimes': 1,
         *               'unlimited': false
         *           }
         *       }
         *   );
         *
         * @param expectation the expectation to setup on the MockServer
         */
        mockAnyResponse = function (expectation) {
            xmlhttp.open("PUT", mockServerUrl + "/expectation", false);
            xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
            xmlhttp.send(JSON.stringify(expectation));
            return _this;
        },
        /**
         * Setup an expectation in the MockServer without having to specify the full expectation object
         * for example:
         *
         *   mockServerClient("localhost", 1080).mockSimpleResponse('/somePath', { name: 'value' }, 203);
         *
         * @param path the path to match requests against
         * @param responseBody the response body to return if a request matches
         * @param statusCode the response code to return if a request matches
         */
        mockSimpleResponse = function (path, responseBody, statusCode) {
            return mockAnyResponse(createExpectation(path, responseBody, statusCode));
        },
        /**
         * Override the default headers that are used to specify the response headers in mockSimpleResponse(...)
         * (note: if you use mockAnyResponse(...) the default headers are not used)
         * for example:
         *
         *   mockServerClient("localhost", 1080).setDefaultHeaders([
         *       {"name": "Content-Type", "values": ["application/json; charset=utf-8"]},
         *       {"name": "Cache-Control", "values": ["no-cache, no-store"]}
         *   ])
         *
         * @param headers the path to match requests against
         */
        setDefaultHeaders = function (headers) {
            defaultResponseHeaders = headers;
        },
        butFoundAssertionErrorMessage = function () {
            xmlhttp.open("PUT", mockServerUrl + "/retrieve", false);
            xmlhttp.send();
            return " but " + (xmlhttp.responseText ? "only found " + xmlhttp.responseText : "found no requests");
        },
        retrieve = function (request) {
            xmlhttp.open("PUT", mockServerUrl + "/retrieve", false);
            xmlhttp.send(JSON.stringify(request));
            return xmlhttp.responseText && JSON.parse(xmlhttp.responseText);
        },
        /**
         * Verify a request has been sent for example:
         *
         *   expect(client.verify({
         *       'httpRequest': {
         *           'method': 'POST',
         *           'path': '/somePath'
         *       }
         *   })).toBeTruthy();
         *
         * @param request the http request that must be matched for this verification to pass
         * @param count   the number of times this request must be matched
         * @param exact   true if the count is matched as "equal to" or false if the count is matched as "greater than or equal to"
         */
        verify = function (request, count, exact) {
            var expectations = retrieve(request);
            if (!expectations) {
                throw "Expected " + JSON.stringify(request) + butFoundAssertionErrorMessage();
            }
            if (exact) {
                if (expectations.length !== count) {
                    throw "Expected " + JSON.stringify(request) + butFoundAssertionErrorMessage();
                }
            } else {
                if (expectations.length < count) {
                    throw "Expected " + JSON.stringify(request) + butFoundAssertionErrorMessage();
                }
            }
            return _this;
        },
        /**
         * Reset MockServer by clearing all expectations
         */
        reset = function () {
            xmlhttp.open("PUT", mockServerUrl + "/reset", false);
            xmlhttp.send("");
            return _this;
        },
        /**
         * Clear all expectations that match the specified path
         *
         * @param path the path to decide which expectations to cleared
         */
        clear = function (path) {
            xmlhttp.open("PUT", mockServerUrl + "/clear", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
            return _this;
        },
        /**
         * Pretty-print the json for all expectations for the specified path.
         * This is particularly helpful when debugging expectations. The expectation
         * are printed into a dedicated log called mockserver_request.log
         *
         * @param path the path to decide which expectations to dump to the log
         */
        dumpToLogs = function (path) {
            xmlhttp.open("PUT", mockServerUrl + "/dumpToLog", false);
            xmlhttp.send(JSON.stringify(createExpectation(path || ".*", "")));
            return _this;
        };

    var _this = {
        mockAnyResponse: mockAnyResponse,
        mockSimpleResponse: mockSimpleResponse,
        setDefaultHeaders: setDefaultHeaders,
        verify: verify,
        reset: reset,
        clear: clear,
        dumpToLogs: dumpToLogs
    };
    return  _this;
};