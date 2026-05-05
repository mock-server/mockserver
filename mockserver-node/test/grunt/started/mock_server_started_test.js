(function () {

    'use strict';

    var testCase = require('nodeunit').testCase;
    var http = require('http');
    var Q = require('q');

    function sendRequest(method, host, port, path, jsonBody) {
        var deferred = Q.defer();

        var body = (typeof jsonBody === "string" ? jsonBody : JSON.stringify(jsonBody || ""));
        var options = {
            method: method,
            host: host,
            path: path,
            port: port
        };

        var req = http.request(options);

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
    }

    exports.mock_server_started = {
        'mock server should have started': testCase({
            'should allows expectation to be setup': function (test) {
                test.expect(2);
                sendRequest("PUT", "localhost", 1080, "/expectation", {
                    'httpRequest': {
                        'path': '/somePath'
                    },
                    'httpResponse': {
                        'statusCode': 202,
                        'body': JSON.stringify({ name: 'first_body' })
                    }
                })
                    .then(function (response) {
                        test.equal(response.statusCode, 201, "allows expectation to be setup");
                    }, function () {
                        test.ok(false, "failed to setup expectation");
                    })
                    .then(function () {
                        sendRequest("GET", "localhost", 1080, "/somePath")
                            .then(function (response) {
                                test.equal(response.statusCode, 202, "expectation matched sucessfully");
                            }, function () {
                                test.ok(false, "failed to match expectation");
                            })
                            .then(function () {
                                // end
                                test.done();
                            });
                    });
            }
        })
    };

})();
