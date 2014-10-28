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

        'should verify exact number of requests have been sent': function (test) {
            // given
            var client = proxyClient("localhost", 1090);
            mockServerClient("localhost", 1080).mockSimpleResponse('/somePath', { name: 'value' }, 203);
            mockServerClient("localhost", 1080).mockSimpleResponse('/somePath', { name: 'value' }, 203);
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
                }, 2, true);
        },

        'should verify at least a number of requests have been sent': function (test) {
            // given
            var client = proxyClient("localhost", 1090);
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
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
        },


        'should fail when no requests have been sent': function (test) {
            // given
            var client = proxyClient("localhost", 1090);
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // when
            test.throws(function () {
                client.verify(
                    {
                        'path': '/someOtherPath'
                    }, 1);
            });
        },

        'should fail when not enough exact requests have been sent': function (test) {
            // given
            var client = proxyClient("localhost", 1090);
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // when
            test.throws(function () {
                client.verify(
                    {
                        'method': 'POST',
                        'path': '/somePath',
                        'body': 'someBody'
                    }, 2, true);
            });
        },

        'should fail when not enough at least requests have been sent': function (test) {
            // given
            var client = proxyClient("localhost", 1090);
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // when
            test.throws(function () {
                client.verify(
                    {
                        'method': 'POST',
                        'path': '/somePath',
                        'body': 'someBody'
                    }, 2);
            });
        },

        'should clear proxy': function (test) {
            // given
            var client = proxyClient("localhost", 1090);
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // then
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1);

            // when
            client.clear('/somePath');

            // then
            test.throws(function () {
                client.verify(
                    {
                        'method': 'POST',
                        'path': '/somePath',
                        'body': 'someBody'
                    }, 1);
            });
        },

        'should reset proxy': function (test) {
            // given
            var client = proxyClient("localhost", 1090);
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });
            sendRequest("POST", "http://localhost:1080/somePath", "someBody")
                .then(function (response) {
                    test.equal(response.statusCode, 404);
                }, function (error) {
                    console.log(error);
                });

            // then
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1);

            // when
            client.reset();

            // then
            test.throws(function () {
                client.verify(
                    {
                        'method': 'POST',
                        'path': '/somePath',
                        'body': 'someBody'
                    }, 1);
            });
        }
    };

})();