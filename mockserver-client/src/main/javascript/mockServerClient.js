var mockServer = function (baseUrl) {
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
        mockResponse = function (path, responseBody, statusCode) {
            var expectedResponse = createExpectation(path, responseBody, statusCode);
            xmlhttp.open("PUT", baseUrl, false);
            xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
            xmlhttp.send(JSON.stringify(expectedResponse));
        },
        clearMock = function () {
            xmlhttp.open("PUT", baseUrl + "/clear", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(".*")));
        },
        dumpToLog = function () {
            xmlhttp.open("PUT", baseUrl + "/dumpToLog", false);
            xmlhttp.send(JSON.stringify(createExpectation(".*", "")));
        },
        setDefaultHeaders = function (headers) {
            defaultResponseHeaders = headers;
        };

    return {
        mockResponse: mockResponse,
        clearMock: clearMock,
        dumpToLog: dumpToLog,
        setDefaultHeaders: setDefaultHeaders
    };
};