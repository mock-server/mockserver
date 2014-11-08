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
        xmlhttp.send(JSON.stringify(request));
        return xmlhttp.responseText && JSON.parse(xmlhttp.responseText);
    };
    var verify = function (request, count, exact) {
        if (count === undefined) {
            count = 1;
        }
        xmlhttp.open("PUT", proxyUrl + "/verify", false);
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
     * @param path the path to decide which expectations to cleared
     */
    var clear = function (path) {
        xmlhttp.open("PUT", proxyUrl + "/clear", false);
        xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
        return _this;
    };
    /**
     * Pretty-print the json for all requests / responses that match the specified path
     * as Expectations to the log. They are printed into a dedicated log called mockserver_request.log
     *
     * @param path the path to decide which expectations to dump to the log
     */
    var dumpToLogs = function (path) {
        xmlhttp.open("PUT", proxyUrl + "/dumpToLog", false);
        xmlhttp.send(JSON.stringify(createResponseMatcher(path || ".*")));
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