(function () {

    'use strict';

    var testCase = require('nodeunit').testCase;
    var mockserver = require(__dirname + '/../../..');

    exports.mock_server_start_failure = {
        'mock server fails to start': testCase({
            'if configuration missing': function (test) {

                test.expect(1);
                mockserver
                    .start_mockserver()
                    .then(
                        function () {
                            test.ok(false, "should fail to start");
                            test.done();
                        },
                        function (error) {
                            test.equal(error, 'Please specify "serverPort", for example: "start_mockserver({ serverPort: 1080 })"');
                            test.done();
                        }
                    );
            },
            'if port is missing': function (test) {

                test.expect(1);
                var options = {};
                mockserver
                    .start_mockserver(options)
                    .then(
                        function () {
                            test.ok(false, "should fail to start");
                            test.done();
                        },
                        function (error) {
                            test.equal(error, 'Please specify "serverPort", for example: "start_mockserver({ serverPort: 1080 })"');
                            test.done();
                        }
                    );
            },
            'if deprecated option "systemProperties" is given': function (test) {

                test.expect(1);
                mockserver
                    .start_mockserver({
                        serverPort: 1080,
                        systemProperties: '--foo'
                    })
                    .then(
                        function () {
                            test.ok(false, "should fail to start");
                            test.done();
                        },
                        function (error) {
                            test.equal(error, 'The option "systemProperties" was renamed to "jvmOptions" in 5.4.1. Please migrate to the new option name');
                            test.done();
                        }
                    );
            }
        })
    };

    exports.mock_server_stop_failure = {
        'mock server fails to stop': testCase({
            'if configuration is missing': function (test) {

                test.expect(1);
                mockserver
                    .stop_mockserver()
                    .then(
                        function () {
                            test.ok(false, "should fail to stop");
                            test.done();
                        },
                        function (error) {
                            test.equal(error, 'Please specify "serverPort", for example: "stop_mockserver({ serverPort: 1080 })"');
                            test.done();
                        }
                    );
            },
            'if port is missing': function (test) {

                test.expect(1);
                var options = {};

                mockserver
                    .stop_mockserver(options)
                    .then(
                        function () {
                            test.ok(false, "should fail to stop");
                            test.done();
                        },
                        function (error) {
                            test.equal(error, 'Please specify "serverPort", for example: "stop_mockserver({ serverPort: 1080 })"');
                            test.done();
                        }
                    );
            }
        })
    };

})();
