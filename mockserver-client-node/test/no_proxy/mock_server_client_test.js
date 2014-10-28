(function () {

    'use strict';

    var mockServer = require('../../mockServerClient'),
        mockServerClient = mockServer.mockServerClient,
        proxyClient = mockServer.proxyClient,
        Q = require('q'),
        request = require('request'),
        sendRequest = function (method, url, body) {
            var deferred = Q.defer();
            var options = {
                method: method,
                url: url,
                body: body
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
        setUp: function (callback) {
            mockServerClient("localhost", 1080).reset();
            proxyClient("localhost", 1090).reset();
            callback();
        },

        'should create full expectation with string body': function (test) {
            // when
            mockServerClient("localhost", 1080).mockAnyResponse(
                {
                    'httpRequest': {
                        'method': 'POST',
                        'path': '/somePath',
                        'queryString': 'test=true',
                        'parameters': [
                            {
                                'name': 'test',
                                'values': [ 'true' ]
                            }
                        ],
                        'body': {
                            'type': "STRING",
                            'value': 'someBody'
                        }
                    },
                    'httpResponse': {
                        'statusCode': 200,
                        'body': JSON.stringify({ name: 'value' }),
                        'delay': {
                            'timeUnit': 'MILLISECONDS',
                            'value': 250
                        }
                    },
                    'times': {
                        'remainingTimes': 1,
                        'unlimited': false
                    }
                }
            );

            // then - non matching request
            sendRequest("GET", "http://localhost:1080/otherPath")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // then - matching request
            sendRequest("POST", "http://localhost:1080/somePath?test=true", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 200);
                    test.equal(response.body, '{"name":"value"}');
                }, function (error) {
                    console.log(error);
                });

            // then - matching request, but no times remaining
            sendRequest("POST", "http://localhost:1080/somePath?test=true", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // end
            test.done();
        },
        'should match on body only': function (test) {
            // when
            var client = mockServerClient("localhost", 1080);
            client.mockAnyResponse(
                {
                    'httpRequest': {
                        'path': '/somePath',
                        'body': {
                            'type': "STRING",
                            'value': 'someBody'
                        }
                    },
                    'httpResponse': {
                        'statusCode': 200,
                        'body': JSON.stringify({ name: 'first_body' }),
                        'delay': {
                            'timeUnit': 'MILLISECONDS',
                            'value': 250
                        }
                    },
                    'times': {
                        'remainingTimes': 1,
                        'unlimited': false
                    }
                }
            );
            client.mockAnyResponse(
                {
                    'httpRequest': {
                        'path': '/somePath',
                        'body': {
                            'type': "REGEX",
                            'value': 'someOtherBody'
                        }
                    },
                    'httpResponse': {
                        'statusCode': 200,
                        'body': JSON.stringify({ name: 'second_body' }),
                        'delay': {
                            'timeUnit': 'MILLISECONDS',
                            'value': 250
                        }
                    },
                    'times': {
                        'remainingTimes': 1,
                        'unlimited': false
                    }
                }
            );

            // then - non matching request
            sendRequest("POST", "http://localhost:1080/otherPath", "someIncorrectBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // then - matching request
            sendRequest("POST", "http://localhost:1080/otherPath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 200);
                    test.equal(response.body, '{"name":"first_body"}');
                }, function (error) {
                    console.log(error);
                });

            // then - matches second expectation as body different
            sendRequest("POST", "http://localhost:1080/otherPath", "someOtherBody")
                .then(function (response) {
                    test.equal(response.statusCode, 200);
                    test.equal(response.body, '{"name":"second_body"}');
                }, function (error) {
                    console.log(error);
                });

            // end
            test.done();
        },

        'should create simple response expectation': function (test) {
            // when
            mockServerClient("localhost", 1080).mockSimpleResponse('/somePath', { name: 'value' }, 203);

            // then - non matching request
            sendRequest("POST", "http://localhost:1080/otherPath")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // then - matching request
            sendRequest("POST", "http://localhost:1080/somePath?test=true", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 200);
                    test.equal(response.body, '{"name":"value"}');
                }, function (error) {
                    console.log(error);
                });

            // then - matching request, but no times remaining
            sendRequest("POST", "http://localhost:1080/somePath?test=true", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // end
            test.done();
        },

        'should update default headers for simple response expectation': function (test) {
            // when
            var client = mockServerClient("localhost", 1080);
            client.setDefaultHeaders([
                {"name": "Content-Type", "values": ["application/json; charset=utf-8"]},
                {"name": "X-Test", "values": ["test-value"]}
            ]);
            client.mockSimpleResponse('/somePath', { name: 'value' }, 203);

            // then - matching request
            sendRequest("POST", "http://localhost:1080/somePath?test=true", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 203);
                    test.equal(response.body, '{"name":"value"}');
                    test.equal(response.headers, '{"X-Test":"test-value"}');
                }, function (error) {
                    console.log(error);
                });

            // end
            test.done();
        },

        'should verify exact number of requests have been sent': function (test) {
            // given
            var client = mockServerClient("localhost", 1080);
            client.mockSimpleResponse('/somePath', { name: 'value' }, 203);
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 203);
                }, function (error) {
                    console.log(error);
                });

            // when
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1, true);

            // end
            test.done();
        },

        'should verify at least a number of requests have been sent': function (test) {
            // given
            var client = mockServerClient("localhost", 1080);
            client.mockSimpleResponse('/somePath', { name: 'value' }, 203);
            client.mockSimpleResponse('/somePath', { name: 'value' }, 203);
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 203);
                }, function (error) {
                    console.log(error);
                });
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 203);
                }, function (error) {
                    console.log(error);
                });

            // when
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1);

            // end
            test.done();
        },

//        'should fail when no requests have been sent': function (test) {
//            // given
//            var client = mockServerClient("localhost", 1080);
//            client.mockSimpleResponse('/somePath', { name: 'value' }, 203);
//            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
//                .then(function (response) {
//                    test.equal(response.statusCode, 203);
//                }, function (error) {
//                    console.log(error);
//                });
//
//            // when
//            test.throws(function () {
//                client.verify(
//                    {
//                        'path': '/someOtherPath'
//                    }, 1);
//            });
//
//            // end
//            test.done();
//        },

//        'should fail when not enough exact requests have been sent': function (test) {
//            // given
//            var client = mockServerClient("localhost", 1080);
//            client.mockSimpleResponse('/somePath', { name: 'value' }, 203);
//            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
//                .then(function (response) {
//                    test.equal(response.statusCode, 203);
//                }, function (error) {
//                    console.log(error);
//                });
//
//            // when
//            test.throws(function () {
//                client.verify(
//                    {
//                        'method': 'POST',
//                        'path': '/somePath',
//                        'body': 'someBody'
//                    }, 2, true);
//            });
//
//            // end
//            test.done();
//        },

//        'should fail when not enough at least requests have been sent': function (test) {
//            // given
//            var client = mockServerClient("localhost", 1080);
//            client.mockSimpleResponse('/somePath', { name: 'value' }, 203);
//            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
//                .then(function (response) {
//                    test.equal(response.statusCode, 203);
//                }, function (error) {
//                    console.log(error);
//                });
//
//            // when
//            test.throws(function () {
//                client.verify(
//                    {
//                        'method': 'POST',
//                        'path': '/somePath',
//                        'body': 'someBody'
//                    }, 2);
//            });
//
//            // end
//            test.done();
//        },

        'should clear expectations': function (test) {
            // when
            var client = mockServerClient("localhost", 1080);
            client.mockSimpleResponse('/somePathOne', { name: 'value' }, 200);
            client.mockSimpleResponse('/somePathOne', { name: 'value' }, 200);
            client.mockSimpleResponse('/somePathTwo', { name: 'value' }, 200);

            // then - matching request
            sendRequest("GET", "http://localhost:1080/somePathOne")
                .then(function (response) {
                    test.equal(response.statusCode, 200);
                    test.equal(response.body, '{"name":"value"}');
                }, function (error) {
                    console.log(error);
                });

            // when
            client.clear('/somePathOne');

            // then - matching request but cleared
            sendRequest("GET", "http://localhost:1080/somePathOne")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // then - matching request and not cleared
            sendRequest("GET", "http://localhost:1080/somePathTwo")
                .then(function (response) {
                    test.equal(response.statusCode, 200);
                    test.equal(response.body, '{"name":"value"}');
                }, function (error) {
                    console.log(error);
                });

            // end
            test.done();
        },

        'should reset expectations': function (test) {
            // when
            var client = mockServerClient("localhost", 1080);
            client.mockSimpleResponse('/somePathOne', { name: 'value' }, 200);
            client.mockSimpleResponse('/somePathOne', { name: 'value' }, 200);
            client.mockSimpleResponse('/somePathTwo', { name: 'value' }, 200);

            // then - matching request
            sendRequest("GET", "http://localhost:1080/somePathOne")
                .then(function (response) {
                    test.equal(response.statusCode, 200);
                    test.equal(response.body, '{"name":"value"}');
                }, function (error) {
                    console.log(error);
                });

            // when
            client.reset();

            // then - matching request but cleared
            sendRequest("GET", "http://localhost:1080/somePathOne")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // then - matching request but also cleared
            sendRequest("GET", "http://localhost:1080/somePathTwo")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // end
            test.done();
        }
    };

})();