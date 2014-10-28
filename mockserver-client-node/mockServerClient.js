/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */

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

        var Q = require('q'),
            request = require('request'),
            mockServerUrl = "http://" + host + ":" + port,
            /**
             * The default headers added to to the mocked response when using mockSimpleResponse(...)
             */
            defaultResponseHeaders = [
                {"name": "Content-Type", "values": ["application/json; charset=utf-8"]},
                {"name": "Cache-Control", "values": ["no-cache, no-store"]}
            ],
            createResponseMatcher = function (path) {
                return {
                    method: "",
                    path: path,
                    body: "",
                    headers: [],
                    cookies: [],
                    parameters: []
                };
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
            sendRequest = function (url, jsonBody, chainable) {
                var deferred = Q.defer();
                var options = {
                    method: 'PUT',
                    url: url,
                    json: jsonBody
                };
                request(options, function (error, response, body) {
                    if (error) {
                        deferred.reject(new Error(error));
                    } else {
                        if (chainable) {
                            deferred.resolve(_this);
                        } else {
                            deferred.resolve(body && JSON.parse(body));
                        }
                    }
                });
                return deferred.promise;
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
            mockAnyResponse = function (expectation) {
                return sendRequest(mockServerUrl + "/expectation", expectation, true);
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
            butFoundAssertionErrorMessage = function (expectedMessage) {
                sendRequest(mockServerUrl + "/retrieve").then(function (expectations) {
                    throw expectedMessage + " but " + (expectations ? "only found " + expectations : "found no requests");
                });
            },
            retrieve = function (request) {
                return sendRequest(mockServerUrl + "/retrieve", request);
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
                return retrieve(request)
                    .then(function (expectations) {
                        if (!expectations) {
                            butFoundAssertionErrorMessage("Expected " + JSON.stringify(request));
                        }
                        if (exact) {
                            if (expectations.length !== count) {
                                butFoundAssertionErrorMessage("Expected " + JSON.stringify(request));
                            }
                        } else {
                            if (expectations.length < count) {
                                butFoundAssertionErrorMessage("Expected " + JSON.stringify(request));
                            }
                        }
                        return _this;
                    });
            },
            /**
             * Reset MockServer by clearing all expectations
             */
            reset = function () {
                return sendRequest(mockServerUrl + "/reset", null, true);
            },
            /**
             * Clear all expectations that match the specified path
             *
             * @param path the path to decide which expectations to cleared
             */
            clear = function (path) {
                return sendRequest(mockServerUrl + "/clear", createResponseMatcher(path || ".*"), true);
            },
            /**
             * Pretty-print the json for all expectations for the specified path.
             * This is particularly helpful when debugging expectations. The expectation
             * are printed into a dedicated log called mockserver_request.log
             *
             * @param path the path to decide which expectations to dump to the log
             */
            dumpToLogs = function (path) {
                return sendRequest(mockServerUrl + "/dumpToLog", createResponseMatcher(path || ".*"), true);
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
    },
    /**
     * Start the client communicating to a MockServer proxy at the specified host and port
     * for example:
     *
     *   var client = proxyClient("localhost", 1080);
     *
     * @param host the host for the proxy to communicate with
     * @param port the port for the proxy to communicate with
     */
    proxyClient = function (host, port) {
        "use strict";

        var Q = require('q'),
            request = require('request'),
            proxyUrl = "http://" + host + ":" + port,
            createResponseMatcher = function (path) {
                return {
                    method: "",
                    path: path,
                    body: "",
                    headers: [],
                    cookies: [],
                    parameters: []
                };
            },
            sendRequest = function (url, jsonBody, chainable) {
                var deferred = Q.defer();
                var options = {
                    method: 'PUT',
                    url: url,
                    json: jsonBody
                };
                request(options, function (error, response, body) {
                    if (error) {
                        deferred.reject(new Error(error));
                    } else {
                        if (chainable) {
                            deferred.resolve(_this);
                        } else {
                            deferred.resolve(body && JSON.parse(body));
                        }
                    }
                });
                return deferred.promise;
            },
            butFoundAssertionErrorMessage = function (expectedMessage) {
                sendRequest(proxyUrl + "/retrieve").then(function (requests) {
                    throw expectedMessage + " but " + (requests ? "only found " + requests : "found no requests");
                });
            },
            /**
             * Retrieve the recorded requests that match the httpRequest parameter as a JSON array, use null for the parameter to retrieve all requests
             *
             * @param request the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
             * @return a JSON array of all expectations that have been recorded by the proxy
             */
            retrieve = function (request) {
                return sendRequest(proxyUrl + "/retrieve", request);
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
                return retrieve(request)
                    .then(function (requests) {
                        if (!requests) {
                            butFoundAssertionErrorMessage("Expected " + JSON.stringify(request));
                        }
                        if (exact) {
                            if (requests.length !== count) {
                                butFoundAssertionErrorMessage("Expected " + JSON.stringify(request));
                            }
                        } else {
                            if (requests.length < count) {
                                butFoundAssertionErrorMessage("Expected " + JSON.stringify(request));
                            }
                        }
                        return _this;
                    });
            },
            /**
             * Reset the proxy by clearing all recorded requests
             */
            reset = function () {
                return sendRequest(proxyUrl + "/reset", null, true);
            },
            /**
             * Clear all recorded requests that match the specified path
             *
             * @param path the path to decide which expectations to cleared
             */
            clear = function (path) {
                return sendRequest(proxyUrl + "/clear", createResponseMatcher(path || ".*"), true);
            },
            /**
             * Pretty-print the json for all requests / responses that match the specified path
             * as Expectations to the log. They are printed into a dedicated log called mockserver_request.log
             *
             * @param path the path to decide which expectations to dump to the log
             */
            dumpToLogs = function (path) {
                return sendRequest(proxyUrl + "/dumpToLog", createResponseMatcher(path || ".*", ""), true);
            };

        var _this = {
            retrieve: retrieve,
            verify: verify,
            reset: reset,
            clear: clear,
            dumpToLogs: dumpToLogs
        };
        return  _this;
    };

module.exports = {
    mockServerClient: mockServerClient,
    proxyClient: proxyClient
};