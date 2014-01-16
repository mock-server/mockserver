var proxyClient = function (proxyUrl) {
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
        butFoundAssertionErrorMessage = function() {
            xmlhttp.open("PUT", proxyUrl + "/retrieve", false);
            xmlhttp.send();
            return " but " + (xmlhttp.responseText ? "only found" + xmlhttp.responseText: "found no requests");
        },
        retrieve = function(request) {
            xmlhttp.open("PUT", proxyUrl + "/retrieve", false);
            xmlhttp.send(JSON.stringify(request));
            return xmlhttp.responseText && JSON.parse(xmlhttp.responseText);
        },
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
                if (expectations.size < count) {
                    throw "Expected " + JSON.stringify(request) + butFoundAssertionErrorMessage();
                }
            }
        },
        reset = function (path) {
            xmlhttp.open("PUT", proxyUrl + "/reset", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
        },
        clear = function (path) {
            xmlhttp.open("PUT", proxyUrl + "/clear", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
        },
        dumpToLogs = function (path) {
            xmlhttp.open("PUT", proxyUrl + "/dumpToLog", false);
            xmlhttp.send(JSON.stringify(createExpectation(path || ".*", "")));
        };

    return {
        retrieve: retrieve,
        verify: verify,
        reset: reset,
        clear: clear,
        dumpToLogs: dumpToLogs
    };
};