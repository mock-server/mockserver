var proxyClient = function (host, port) {
    "use strict";

    var xmlhttp = new XMLHttpRequest(),
        proxyUrl = "http://" + host + ":" + port,
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
        butFoundAssertionErrorMessage = function () {
            xmlhttp.open("PUT", proxyUrl + "/retrieve", false);
            xmlhttp.send();
            return " but " + (xmlhttp.responseText ? "only found" + xmlhttp.responseText : "found no requests");
        },
        /**
         * Retrieve the recorded requests that match the httpRequest parameter as a JSON array, use null for the parameter to retrieve all requests
         *
         * @param request the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
         * @return a JSON array of all expectations that have been recorded by the proxy
         */
        retrieve = function (request) {
            xmlhttp.open("PUT", proxyUrl + "/retrieve", false);
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
            debugger;
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
         * Reset the proxy by clearing all recorded requests
         */
        reset = function () {
            xmlhttp.open("PUT", proxyUrl + "/reset", false);
            xmlhttp.send("");
            return _this;
        },
        /**
         * Clear all recorded requests that match the specified path
         *
         * @param path the path to decide which expectations to cleared
         */
        clear = function (path) {
            xmlhttp.open("PUT", proxyUrl + "/clear", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
            return _this;
        },
        /**
         * Pretty-print the json for all requests / responses that match the specified path
         * as Expectations to the log. They are printed into a dedicated log called mockserver_request.log
         *
         * @param path the path to decide which expectations to dump to the log
         */
        dumpToLogs = function (path) {
            xmlhttp.open("PUT", proxyUrl + "/dumpToLog", false);
            xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*", "")));
            return _this;
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