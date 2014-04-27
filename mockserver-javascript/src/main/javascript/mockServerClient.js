var mockServerClient = function (mockServerUrl) {
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
        mockAnyResponse = function (expectation) {
            xmlhttp.open("PUT", mockServerUrl + "/expectation", false);
            xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
            xmlhttp.send(JSON.stringify(expectation));
        },
        mockSimpleResponse = function (path, responseBody, statusCode) {
            mockAnyResponse(createExpectation(path, responseBody, statusCode));
        },
        setDefaultHeaders = function (headers) {
            defaultResponseHeaders = headers;
        },
        reset = function (path) {
            xmlhttp.open("PUT", mockServerUrl + "/reset", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
        },
        clear = function (path) {
            xmlhttp.open("PUT", mockServerUrl + "/clear", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
        },
        dumpToLogs = function (path) {
            xmlhttp.open("PUT", mockServerUrl + "/dumpToLog", false);
            xmlhttp.send(JSON.stringify(createExpectation(path || ".*", "")));
        };

    return {
        mockAnyResponse: mockAnyResponse,
        mockSimpleResponse: mockSimpleResponse,
        setDefaultHeaders: setDefaultHeaders,
        reset: reset,
        clear: clear,
        dumpToLogs: dumpToLogs
    };
};