var mockServerClient = function (mockServerUrl, proxyUrl) {
    "use strict";

    var xmlhttp = new XMLHttpRequest(),
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
                    body: JSON.stringify(responseBody),
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
        mockAnyResponse = function (expectation) {
            xmlhttp.open("PUT", mockServerUrl, false);
            xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
            xmlhttp.send(JSON.stringify(expectation));
        },
        mockSimpleResponse = function (path, responseBody, statusCode) {
            mockAnyResponse(createExpectation(path, responseBody, statusCode));
        },
        butFoundAssertionErrorMessage = function() {
            xmlhttp.open("PUT", proxyUrl + "/retrieve", false);
            xmlhttp.send();
            return " but " + (xmlhttp.responseText ? "only found" + xmlhttp.responseText: "found no requests");
        },
        verify = function (request, count, exact) {
            xmlhttp.open("PUT", proxyUrl + "/retrieve", false);
            xmlhttp.send(JSON.stringify(request));
            var expectations = xmlhttp.responseText && JSON.parse(xmlhttp.responseText);
            if (!expectations) {
                throw "Expected " + JSON.stringify(request) + butFoundAssertionErrorMessage();
            }
            if (exact) {
                if (expectations.length !== count) {
                    throw "Expected " + JSON.stringify(request) + butFoundAssertionErrorMessage();
                }
            } else {
                if (expectations.size < count) {
                    throw "Expected " + JSON.stringify(request) + butFoundAssertionErrorMessage();
                }
            }
        },
        resetMocks = function (path) {
            xmlhttp.open("PUT", mockServerUrl + "/reset", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
        },
        resetProxy = function (path) {
            xmlhttp.open("PUT", proxyUrl + "/reset", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
        },
        clearMocks = function (path) {
            xmlhttp.open("PUT", mockServerUrl + "/clear", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
        },
        clearProxy = function (path) {
            xmlhttp.open("PUT", proxyUrl + "/clear", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
        },
        dumpMocksToLog = function (path) {
            xmlhttp.open("PUT", mockServerUrl + "/dumpToLog", false);
            xmlhttp.send(JSON.stringify(createExpectation(path || ".*", "")));
        },
        dumpProxyToLog = function (path) {
            xmlhttp.open("PUT", proxyUrl + "/dumpToLog", false);
            xmlhttp.send(JSON.stringify(createExpectation(path || ".*", "")));
        },
        setDefaultHeaders = function (headers) {
            defaultResponseHeaders = headers;
        };

    return {
        mockAnyResponse: mockAnyResponse,
        mockSimpleResponse: mockSimpleResponse,
        verify: verify,
        resetMocks: resetMocks,
        resetProxy: resetProxy,
        clearMocks: clearMocks,
        clearProxy: clearProxy,
        dumpMocksToLog: dumpMocksToLog,
        dumpProxyToLog: dumpProxyToLog,
        setDefaultHeaders: setDefaultHeaders
    };
};