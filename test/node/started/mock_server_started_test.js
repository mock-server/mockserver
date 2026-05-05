(function () {

    'use strict';

    var testCase = require('nodeunit').testCase;
    var mockserver = require(__dirname + '/../../..');
    var sendRequest = require(__dirname + '/../../sendRequest.js');
    const fs = require('fs')

    function checkFileExists(test, path) {
        try {
            if (fs.existsSync(path)) {
                test.ok(fs.existsSync(path), "check '" + file + "' exists");
            }
        } catch(err) {
            console.error(err);
            test.ok(false, "failed check if '" + file + "' exists");
        }
    }

    function stopMockServer(test, port) {
        mockserver
            .stop_mockserver({serverPort: port})
            .then(
                function() {
                    test.done();
                },
                function(error) {
                    test.ok(false, "failed to stop MockServer on port " + port + ": " + error);
                    test.done();
                }
            );
    }

    exports.mock_server_started = {
        'mock server should have started': testCase({
            'should allow expectation to be set up': function (test) {
                var port = 1081;

                test.expect(2);
                mockserver
                    .start_mockserver({serverPort: port})
                    .then(
                        function () {
                            sendRequest("PUT", "localhost", port, "/expectation", {
                                'httpRequest': {
                                    'path': '/somePath'
                                },
                                'httpResponse': {
                                    'statusCode': 202,
                                    'body': JSON.stringify({name: 'first_body'})
                                }
                            })
                                .then(
                                    function (response) {
                                        test.equal(response.statusCode, 201, "allows expectation to be setup");
                                    },
                                    function () {
                                        test.ok(false, "failed to setup expectation");
                                    }
                                )
                                .then(function () {
                                    sendRequest("GET", "localhost", port, "/somePath")
                                        .then(
                                            function (response) {
                                                test.equal(response.statusCode, 202, "expectation matched sucessfully");
                                            },
                                            function () {
                                                test.ok(false, "failed to match expectation");
                                            })
                                        .then(function () {
                                            test.done();
                                        });
                                });
                        },
                        function (error) {
                            test.ok(false, "should start without error: \"" + error + "\"");
                            test.done();
                        }
                    );
            },
            'allow multiple system properties to be specified in single string': function (test) {
                var port = 1082;

                test.expect(2);
                mockserver
                    .start_mockserver({
                        serverPort: port,
                        jvmOptions: '-Dmockserver.dynamicallyCreateCertificateAuthorityCertificate=true -Dmockserver.directoryToSaveDynamicSSLCertificate=./tmp/' + port
                    })
                    .then(
                        function () {
                            sendRequest("PUT", "localhost", port, "/expectation", {
                                'httpRequest': {
                                    'path': '/somePath'
                                },
                                'httpResponse': {
                                    'statusCode': 202,
                                    'body': JSON.stringify({name: 'first_body'})
                                }
                            }, "https")
                                .then(
                                    function (response) {
                                        test.equal(response.statusCode, 201, "allows expectation to be setup");
                                    },
                                    function (error) {
                                        test.ok(false, "failed to setup expectation");
                                    }
                                )
                                .then(function () {
                                    sendRequest("GET", "localhost", port, "/somePath", undefined, "https")
                                        .then(
                                            function (response) {
                                                test.equal(response.statusCode, 202, "expectation matched sucessfully");
                                            },
                                            function (error) {
                                                test.ok(false, "failed to match expectation");
                                            }
                                        )
                                        .then(function () {
                                            checkFileExists(test, '/tmp/' + port + '/PKCS8CertificateAuthorityPrivateKey.pem');
                                            stopMockServer(test, port);
                                        });
                                });
                        },
                        function (error) {
                            test.ok(false, "should start without error: \"" + error + "\"");
                            stopMockServer(test, port);
                        }
                    );
            },
            'allow multiple system properties to be specified as array': function (test) {
                var port = 1083;

                test.expect(2);
                mockserver
                    .start_mockserver({
                        serverPort: port,
                        jvmOptions: ['-Dmockserver.dynamicallyCreateCertificateAuthorityCertificate=true', '-Dmockserver.directoryToSaveDynamicSSLCertificate=./tmp/' + port]
                    })
                    .then(
                        function () {
                            sendRequest("PUT", "localhost", port, "/expectation", {
                                'httpRequest': {
                                    'path': '/somePath'
                                },
                                'httpResponse': {
                                    'statusCode': 202,
                                    'body': JSON.stringify({name: 'first_body'})
                                }
                            }, "https")
                                .then(
                                    function (response) {
                                        test.equal(response.statusCode, 201, "allows expectation to be setup");
                                    },
                                    function (error) {
                                        test.ok(false, "failed to setup expectation");
                                    }
                                )
                                .then(function () {
                                    sendRequest("GET", "localhost", port, "/somePath", undefined, "https")
                                        .then(
                                            function (response) {
                                                test.equal(response.statusCode, 202, "expectation matched sucessfully");
                                            },
                                            function (error) {
                                                test.ok(false, "failed to match expectation");
                                            }
                                        )
                                        .then(function () {
                                            checkFileExists(test, '/tmp/' + port + '/PKCS8CertificateAuthorityPrivateKey.pem');
                                            stopMockServer(test, port);
                                        });
                                });
                        },
                        function (error) {
                            test.ok(false, "should start without error: \"" + error + "\"");
                            stopMockServer(test, port);
                        }
                    );
            }
        })
    };

})();
