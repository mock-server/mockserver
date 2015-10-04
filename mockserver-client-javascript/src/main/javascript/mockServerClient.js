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

    var xmlhttp = new XMLHttpRequest();
    var mockServerUrl = "http://" + host + ":" + port;
    /**
     * The default headers added to to the mocked response when using mockSimpleResponse(...)
     */
    var defaultResponseHeaders = [
        {"name": "Content-Type", "values": ["application/json; charset=utf-8"]},
        {"name": "Cache-Control", "values": ["no-cache, no-store"]}
    ];
    var createResponseMatcher = function (path) {
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
            queryStringParameters: [],
            body: "",
            headers: headers,
            cookies: []
        }
    };
    var createExpectation = function (path, responseBody, statusCode) {
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
    };
    /**
     * Setup an expectation in the MockServer by specifying an expectation object
     * for example:
     *
     *   mockServerClient("localhost", 1080).mockAnyResponse(
     *       {
     *           'httpRequest': {
     *               'path': '/somePath',
     *               'body': {
     *                   'type': "STRING",
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
    var mockAnyResponse = function (expectation) {
        xmlhttp.open("PUT", mockServerUrl + "/expectation", false);
        xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
        xmlhttp.send(JSON.stringify(expectation));
        return _this;
    };
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
    var mockSimpleResponse = function (path, responseBody, statusCode) {
        return mockAnyResponse(createExpectation(path, responseBody, statusCode));
    };
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
    var setDefaultHeaders = function (headers) {
        defaultResponseHeaders = headers;
    };
    /**
     * Verify a request has been sent for example:
     *
     *   client.verify({
     *      'method': 'POST',
     *      'path': '/somePath'
     *   });
     *
     * @param request the http request that must be matched for this verification to pass
     * @param count   the number of times this request must be matched
     * @param exact   true if the count is matched as "equal to" or false if the count is matched as "greater than or equal to"
     */
    var verify = function (request, count, exact) {
        if (count === undefined) {
            count = 1;
        }
        xmlhttp.open("PUT", mockServerUrl + "/verify", false);
        xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
        xmlhttp.send(JSON.stringify({
            "httpRequest": request,
            "times": {
                "count": count,
                "exact": exact
            }
        }));
        if (xmlhttp.status !== 202) {
            console && console.error && console.error(xmlhttp.responseText);
            throw xmlhttp.responseText;
        }
        return _this;
    };
    /**
     * Verify a sequence of requests has been sent for example:
     *
     *   client.verifySequence(
     *       {
     *          'method': 'POST',
     *          'path': '/first_request'
     *       },
     *       {
     *          'method': 'POST',
     *          'path': '/second_request'
     *       },
     *       {
     *          'method': 'POST',
     *          'path': '/third_request'
     *       }
     *   );
     *
     * @param arguments the list of http requests that must be matched for this verification to pass
     */
    var verifySequence = function () {
        xmlhttp.open("PUT", mockServerUrl + "/verifySequence", false);
        xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
        var requestSequence = [];
        for (var i = 0; i < arguments.length; i++) {
            requestSequence.push(arguments[i]);
        }
        xmlhttp.send(JSON.stringify({
            "httpRequests": requestSequence
        }));
        if (xmlhttp.status !== 202) {
            console && console.error && console.error(xmlhttp.responseText);
            throw xmlhttp.responseText;
        }
        return _this;
    };
    /**
     * Reset MockServer by clearing all expectations
     */
    var reset = function () {
        xmlhttp.open("PUT", mockServerUrl + "/reset", false);
        xmlhttp.send("");
        return _this;
    };
    /**
     * Clear all expectations that match the specified path
     *
     * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path to
     *                              decide which expectations to cleared, however if an object is passed
     *                              in the value will be treated as a full request matcher object
     */
    var clear = function (pathOrRequestMatcher) {
        xmlhttp.open("PUT", mockServerUrl + "/clear", false);
        xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
        if (typeof pathOrRequestMatcher === "string") {
            xmlhttp.send(JSON.stringify(createResponseMatcher(pathOrRequestMatcher)));
        } else if (pathOrRequestMatcher) {
            xmlhttp.send(JSON.stringify(pathOrRequestMatcher));
        } else {
            xmlhttp.send(JSON.stringify(createResponseMatcher(".*")));
        }
        return _this;
    };
    /**
     * Retrieve the recorded requests that match the parameter, as follows:
     * - use a string value to match on path,
     * - use a request matcher object to match on a full request,
     * - or use null to retrieve all requests
     *
     * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path, however
     *                              if an object is passed in the value will be treated as a full request
     *                              matcher object, if null is passed in it will be treated as match all
     */
    var retrieveRequests = function (pathOrRequestMatcher) {
        xmlhttp.open("PUT", mockServerUrl + "/retrieve", false);
        xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
        if (typeof pathOrRequestMatcher === "string") {
            xmlhttp.send(JSON.stringify(createResponseMatcher(pathOrRequestMatcher)));
        } else if (pathOrRequestMatcher) {
            xmlhttp.send(JSON.stringify(pathOrRequestMatcher));
        } else {
            xmlhttp.send(JSON.stringify(createResponseMatcher(".*")));
        }
        return JSON.parse(xmlhttp.responseText);
    };
    /**
     * Retrieve the setup expectations that match the parameter,
     * the expectation is retrieved by matching the parameter
     * on the expectations own request matcher, as follows:
     * - use a string value to match on path,
     * - use a request matcher object to match on a full request,
     * - or use null to retrieve all requests
     *
     * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path, however
     *                              if an object is passed in the value will be treated as a full request
     *                              matcher object, if null is passed in it will be treated as match all
     */
    var retrieveExpectations = function (pathOrRequestMatcher) {
        xmlhttp.open("PUT", mockServerUrl + "/retrieve?type=expectation", false);
        xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
        if (typeof pathOrRequestMatcher === "string") {
            xmlhttp.send(JSON.stringify(createResponseMatcher(pathOrRequestMatcher)));
        } else if (pathOrRequestMatcher) {
            xmlhttp.send(JSON.stringify(pathOrRequestMatcher));
        } else {
            xmlhttp.send(JSON.stringify(createResponseMatcher(".*")));
        }
        return JSON.parse(xmlhttp.responseText);
    };

    var _this = {
        mockAnyResponse: mockAnyResponse,
        mockSimpleResponse: mockSimpleResponse,
        setDefaultHeaders: setDefaultHeaders,
        verify: verify,
        verifySequence: verifySequence,
        reset: reset,
        clear: clear,
        retrieveRequests: retrieveRequests,
        retrieveExpectations: retrieveExpectations
    };
    return _this;
};