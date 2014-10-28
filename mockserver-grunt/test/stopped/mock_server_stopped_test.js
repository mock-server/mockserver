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

    exports.mock_server_stopped = {
        'mock server has stopped': testCase({
            'should fail when attempting to setup expectation': function (test) {
                sendRequest('PUT', "http://localhost:1080/expectation", {
                    'httpRequest': {
                        'path': '/somePath'
                    },
                    'httpResponse': {
                        'statusCode': 201,
                        'body': JSON.stringify({ name: 'first_body' })
                    }
                }).then(function (response) {
                    test.ok(false, "allowed expectation to be setup");
                }, function (response) {
                    test.ok(true, "did not allow expectation to be setup");
                }).then(function (){
                    // end
                    test.done();
                });
            }
        })
    };

})();
