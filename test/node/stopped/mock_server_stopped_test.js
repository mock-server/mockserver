(function () {

    'use strict';

    var testCase = require('nodeunit').testCase;
    var mockserver = require(__dirname + '/../../..');
    var sendRequest = require(__dirname + '/../../sendRequest.js');

    exports.mock_server_stopped = {
        'mock server has stopped': testCase({
            'should fail when attempting to setup expectation': function (test) {

                var port = 1081;

                test.expect(2);
                mockserver
                    .stop_mockserver({serverPort: port})
                    .then(
                        function () {
                            sendRequest("PUT", "localhost", port, "/expectation", {
                                'httpRequest': {
                                    'path': '/somePath'
                                },
                                'httpResponse': {
                                    'statusCode': 201,
                                    'body': JSON.stringify({name: 'first_body'})
                                }
                            }).then(
                                function () {
                                    test.ok(false, "allowed expectation to be setup");
                                },
                                function () {
                                    test.ok(true, "did not allow expectation to be setup");
                                })
                                .then(function () {
                                    test.done();
                                });
                        },
                        function (error) {
                            test.ok(false, "should start without error: \"" + error + "\"");
                            test.done();
                        }
                    );
            }
        })
    };

})();
