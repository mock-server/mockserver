const http = require("http");
module.exports = (function () {

    var http = require('http');
    var https = require('https');
    var Q = require('q');

    return function (method, host, port, path, jsonBody, protocol) {
        var deferred = Q.defer();

        var body = (typeof jsonBody === "string" ? jsonBody : JSON.stringify(jsonBody || ""));
        var options = {
            method: method,
            host: host,
            path: path,
            port: port,
            checkServerIdentity: function (host, cert) {
                console.log('checkServerIdentity called for host: "' + host + '"');
            }
        };

        process.env['NODE_TLS_REJECT_UNAUTHORIZED'] = 0;
        var req = protocol && protocol === "https" ? https.request(options) : http.request(options);

        req.once('response', function (response) {
            var data = '';

            if (response.statusCode === 400 || response.statusCode === 404) {
                deferred.reject(response.statusCode);
            }

            response.on('data', function (chunk) {
                data += chunk;
            });

            response.on('end', function () {
                deferred.resolve({
                    statusCode: response.statusCode,
                    body: data
                });
            });
        });

        req.once('error', function (error) {
            deferred.reject(error);
        });

        req.write(body);
        req.end();

        return deferred.promise;
    };
})();