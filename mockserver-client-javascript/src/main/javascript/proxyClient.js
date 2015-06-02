var proxyClient = function (host, port) {
    "use strict";

    var xmlhttp = new XMLHttpRequest();
    var proxyUrl = "http://" + host + ":" + port;
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
            body: "",
            headers: headers,
            cookies: [],
            parameters: []
        }
    };
    /**
     * Retrieve the recorded requests that match the httpRequest parameter as a JSON array, use null for the parameter to retrieve all requests
     *
     * @param request the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return a JSON array of all expectations that have been recorded by the proxy
     */
    var retrieve = function (request) {
        xmlhttp.open("PUT", proxyUrl + "/retrieve", false);
        xmlhttp.setRequestHeader("Content-Type", "application/json; charset=utf-8");
        xmlhttp.send(JSON.stringify(request));
        return xmlhttp.responseText && JSON.parse(xmlhttp.responseText);
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
        xmlhttp.open("PUT", proxyUrl + "/verify", false);
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
        xmlhttp.open("PUT", proxyUrl + "/verifySequence", false);
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
     * Reset the proxy by clearing all recorded requests
     */
    var reset = function () {
        xmlhttp.open("PUT", proxyUrl + "/reset", false);
        xmlhttp.send("");
        return _this;
    };
    /**
     * Clear all recorded requests that match the specified path
     *
     * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path to
     *                              decide which recorded requests to cleared, however if an object is
     *                              passed in the value will be treated as a full request matcher object
     */
    var clear = function (pathOrRequestMatcher) {
        xmlhttp.open("PUT", proxyUrl + "/clear", false);
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
     * Pretty-print the json for all requests / responses that match the specified path
     * as Expectations to the log. They are printed into a dedicated log called mockserver_request.log
     *
     * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path to
     *                              decide which recorded requests to cleared, however if an object is
     *                              passed in the value will be treated as a full request matcher object
     */
    var dumpToLogs = function (pathOrRequestMatcher) {
        xmlhttp.open("PUT", proxyUrl + "/dumpToLog", false);
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

    var _this = {
        retrieve: retrieve,
        verify: verify,
        verifySequence: verifySequence,
        reset: reset,
        clear: clear,
        dumpToLogs: dumpToLogs
    };
    return  _this;
};