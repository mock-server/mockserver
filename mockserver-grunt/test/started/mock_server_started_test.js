(function () {

    'use strict';

    var testCase = require('nodeunit').testCase,
        Q = require('q'),
        request = require('request'),
        sendRequest = function (method, url, jsonBody) {
            var deferred = Q.defer();
            var options = {
                method: method,
                url: url,
                json: jsonBody
            };
            request(options, function (error, response) {
                if (error) {
                    deferred.reject(new Error(error));
                } else {
                    deferred.resolve(response);
                }
            });
            return deferred.promise;
        };

    exports.mock_server_started = {
        'mock server should have started': testCase({
            'should allows expectation to be setup': function (test) {
                sendRequest('PUT', "http://localhost:1080/expectation", {
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
                        sendRequest('GET', "http://localhost:1080/somePath")
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
