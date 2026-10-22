/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */
(function () {
    "use strict";

    if (module && require) {
        var Q = require('q');
        var fs = require('fs');

        var defer = function () {
            var promise = (global.protractor && global.protractor.promise.USE_PROMISE_MANAGER !== false) ? global.protractor.promise : Q;
            var deferred = promise.defer();

            if (deferred.fulfill && !deferred.resolve) {
                deferred.resolve = deferred.fulfill;
            }
            return deferred;
        };

        // ------------------------------------------------------------------
        // Control-plane auth + mTLS helpers
        // ------------------------------------------------------------------
        // `clientOptions` is the optional options object threaded through from
        // mockServerClient(host, port, contextPath, tls, caCertPath, options).
        // Recognised keys:
        //   bearerToken           static control-plane JWT (string)
        //   bearerTokenSupplier   function() => string, evaluated per-request
        //                         (so the token can be refreshed)
        //   basicAuthUsername     control-plane Basic username (with basicAuthPassword)
        //   basicAuthPassword     control-plane Basic password; a bearer token wins when both are set
        //   clientCertPemFilePath path to a PEM client certificate (mTLS)
        //   clientKeyPemFilePath  path to a PEM private key (mTLS)

        // Resolve the bearer token for a single request, preferring the supplier
        // (so a refreshed token is picked up) and falling back to the static
        // token. Returns null when neither is configured or the supplier yields
        // a blank value.
        var resolveBearerToken = function (clientOptions) {
            if (!clientOptions) {
                return null;
            }
            var token = null;
            if (typeof clientOptions.bearerTokenSupplier === 'function') {
                token = clientOptions.bearerTokenSupplier();
            } else if (clientOptions.bearerToken !== undefined && clientOptions.bearerToken !== null) {
                token = clientOptions.bearerToken;
            }
            if (token === undefined || token === null || token === '') {
                return null;
            }
            return String(token);
        };

        // The control-plane Authorization header value: the bearer token when one is configured, else the Basic
        // credentials, else null.
        var resolveAuthorization = function (clientOptions) {
            var token = resolveBearerToken(clientOptions);
            if (token) {
                return 'Bearer ' + token;
            }
            if (clientOptions && typeof clientOptions.basicAuthUsername === 'string' && clientOptions.basicAuthUsername !== '' && clientOptions.basicAuthPassword !== undefined && clientOptions.basicAuthPassword !== null) {
                return 'Basic ' + Buffer.from(clientOptions.basicAuthUsername + ':' + String(clientOptions.basicAuthPassword), 'utf8').toString('base64');
            }
            return null;
        };

        // The mTLS client cert/key TLS options ({cert, key}) for every connection
        // to MockServer, its HTTP requests and its callback WebSocket alike.
        var clientCertificateOptions = function (clientOptions) {
            var options = {};
            if (clientOptions) {
                if (clientOptions.clientCertPemFilePath) {
                    options.cert = fs.readFileSync(clientOptions.clientCertPemFilePath, {encoding: 'utf-8'});
                }
                if (clientOptions.clientKeyPemFilePath) {
                    options.key = fs.readFileSync(clientOptions.clientKeyPemFilePath, {encoding: 'utf-8'});
                }
            }
            return options;
        };

        // Attach the control-plane Authorization header and mTLS client cert/key
        // to a Node http(s) request options object. Mutates and returns options.
        var applyControlPlaneAuth = function (options, clientOptions) {
            var authorization = resolveAuthorization(clientOptions);
            if (authorization) {
                if (!options.headers) {
                    options.headers = {};
                }
                options.headers['Authorization'] = authorization;
            }
            return Object.assign(options, clientCertificateOptions(clientOptions));
        };

        var downloadCACert = function (tls, caCertPath, callback) {
            // https://raw.githubusercontent.com/mock-server/mockserver-monorepo/master/mockserver/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem

            var dest = "CertificateAuthorityCertificate.pem";
            if (tls && !caCertPath && !fs.existsSync('./' + dest)) {
                var options = {
                    protocol: 'https:',
                    method: 'GET',
                    host: "raw.githubusercontent.com",
                    path: "/mock-server/mockserver-monorepo/master/mockserver/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem",
                    port: 443,
                };
                var req = require('https').request(options);

                req.once('error', function (error) {
                    console.error('Fetching ' + JSON.stringify(options, null, 2) + ' failed with error ' + error);
                });

                req.once('response', function (res) {
                    if (res.statusCode < 200 || res.statusCode >= 300) {
                        console.error('Fetching ' + JSON.stringify(options, null, 2) + ' failed with HTTP status code ' + res.statusCode);
                    } else {
                        var writeStream = fs.createWriteStream(dest);
                        res.pipe(writeStream);

                        writeStream.on('error', function (error) {
                            console.error('Saving ' + dest + ' failed with error ' + error);
                        });
                        writeStream.on('close', function () {
                            console.log('Saved ' + dest + ' from ' + JSON.stringify(options, null, 2));
                            callback(tls ? [fs.readFileSync(caCertPath || "./" + dest, {encoding: 'utf-8'})] : []);
                        });
                    }
                });

                req.end();
            } else {
                callback(tls ? [fs.readFileSync(caCertPath || "./" + dest, {encoding: 'utf-8'})] : []);
            }
        };

        var sendRequest = function (tls, caCertPath, clientOptions) {
            var http = tls ? require('https') : require('http');
            return function (host, port, path, jsonBody, resolveCallback) {
                var deferred = defer();
                downloadCACert(tls, caCertPath, function (ca) {

                    var body = (typeof jsonBody === "string" ? jsonBody : JSON.stringify(jsonBody || ""));
                    var options = applyControlPlaneAuth({
                        protocol: tls ? 'https:' : 'http:',
                        method: 'PUT',
                        host: host,
                        path: path,
                        port: port,
                        ca: ca,
                        headers: {
                            'Content-Type': "application/json; charset=utf-8"
                        },
                    }, clientOptions);

                    var req = http.request(options);

                    req.once('response', function (response) {
                        var data = '';

                        response.on('data', function (chunk) {
                            data += chunk;
                        });

                        response.on('end', function () {
                            if (resolveCallback) {
                                deferred.resolve(resolveCallback(data));
                            } else {
                                if (response.statusCode >= 400 && response.statusCode < 600) {
                                    if (response.statusCode === 404) {
                                        deferred.reject("404 Not Found");
                                    } else {
                                        deferred.reject(data);
                                    }
                                } else {
                                    deferred.resolve({
                                        statusCode: response.statusCode,
                                        body: data
                                    });
                                }
                            }
                        });
                    });

                    req.once('error', function (error) {
                        if (error.code && error.code === "ECONNREFUSED") {
                            deferred.reject("Can't connect to MockServer running on host: \"" + host + "\" and port: \"" + port + "\"");
                        } else {
                            // reject with the error message (not JSON.stringify, which
                            // serialises an Error to "{}" since message/stack are
                            // non-enumerable) to preserve diagnostic information while
                            // keeping the string rejection contract used elsewhere
                            deferred.reject(error.message || String(error));
                        }
                    });

                    req.write(body);
                    req.end();

                });
                return deferred.promise;
            };
        };

        var sendBinaryRequest = function (tls, caCertPath, clientOptions) {
            var http = tls ? require('https') : require('http');
            return function (host, port, path, bodyBuffer, contentType) {
                var deferred = defer();
                downloadCACert(tls, caCertPath, function (ca) {

                    var body = Buffer.isBuffer(bodyBuffer) ? bodyBuffer : Buffer.from(bodyBuffer || []);
                    var options = applyControlPlaneAuth({
                        protocol: tls ? 'https:' : 'http:',
                        method: 'PUT',
                        host: host,
                        path: path,
                        port: port,
                        ca: ca,
                        headers: {
                            'Content-Type': contentType || "application/octet-stream",
                            'Content-Length': body.length
                        },
                    }, clientOptions);

                    var req = http.request(options);

                    req.once('response', function (response) {
                        var data = '';

                        response.on('data', function (chunk) {
                            data += chunk;
                        });

                        response.on('end', function () {
                            if (response.statusCode >= 400 && response.statusCode < 600) {
                                if (response.statusCode === 404) {
                                    deferred.reject("404 Not Found");
                                } else {
                                    deferred.reject(data);
                                }
                            } else {
                                deferred.resolve({
                                    statusCode: response.statusCode,
                                    body: data
                                });
                            }
                        });
                    });

                    req.once('error', function (error) {
                        if (error.code && error.code === "ECONNREFUSED") {
                            deferred.reject("Can't connect to MockServer running on host: \"" + host + "\" and port: \"" + port + "\"");
                        } else {
                            // reject with the error message (not JSON.stringify, which
                            // serialises an Error to "{}" since message/stack are
                            // non-enumerable) to preserve diagnostic information while
                            // keeping the string rejection contract used elsewhere
                            deferred.reject(error.message || String(error));
                        }
                    });

                    req.write(body);
                    req.end();

                });
                return deferred.promise;
            };
        };

        var sendGetRequest = function (tls, caCertPath, clientOptions) {
            var http = tls ? require('https') : require('http');
            return function (host, port, path) {
                var deferred = defer();
                downloadCACert(tls, caCertPath, function (ca) {

                    var options = applyControlPlaneAuth({
                        protocol: tls ? 'https:' : 'http:',
                        method: 'GET',
                        host: host,
                        path: path,
                        port: port,
                        ca: ca,
                    }, clientOptions);

                    var req = http.request(options);

                    req.once('response', function (response) {
                        var data = '';

                        response.on('data', function (chunk) {
                            data += chunk;
                        });

                        response.on('end', function () {
                            if (response.statusCode >= 400 && response.statusCode < 600) {
                                if (response.statusCode === 404) {
                                    deferred.reject("404 Not Found");
                                } else {
                                    deferred.reject(data);
                                }
                            } else {
                                deferred.resolve({
                                    statusCode: response.statusCode,
                                    body: data
                                });
                            }
                        });
                    });

                    req.once('error', function (error) {
                        if (error.code && error.code === "ECONNREFUSED") {
                            deferred.reject("Can't connect to MockServer running on host: \"" + host + "\" and port: \"" + port + "\"");
                        } else {
                            // reject with the error message (not JSON.stringify, which
                            // serialises an Error to "{}" since message/stack are
                            // non-enumerable) to preserve diagnostic information while
                            // keeping the string rejection contract used elsewhere
                            deferred.reject(error.message || String(error));
                        }
                    });

                    req.end();

                });
                return deferred.promise;
            };
        };

        var sendDeleteRequest = function (tls, caCertPath, clientOptions) {
            var http = tls ? require('https') : require('http');
            return function (host, port, path) {
                var deferred = defer();
                downloadCACert(tls, caCertPath, function (ca) {

                    var options = applyControlPlaneAuth({
                        protocol: tls ? 'https:' : 'http:',
                        method: 'DELETE',
                        host: host,
                        path: path,
                        port: port,
                        ca: ca,
                    }, clientOptions);

                    var req = http.request(options);

                    req.once('response', function (response) {
                        var data = '';

                        response.on('data', function (chunk) {
                            data += chunk;
                        });

                        response.on('end', function () {
                            if (response.statusCode >= 400 && response.statusCode < 600) {
                                if (response.statusCode === 404) {
                                    deferred.reject("404 Not Found");
                                } else {
                                    deferred.reject(data);
                                }
                            } else {
                                deferred.resolve({
                                    statusCode: response.statusCode,
                                    body: data
                                });
                            }
                        });
                    });

                    req.once('error', function (error) {
                        if (error.code && error.code === "ECONNREFUSED") {
                            deferred.reject("Can't connect to MockServer running on host: \"" + host + "\" and port: \"" + port + "\"");
                        } else {
                            // reject with the error message (not JSON.stringify, which
                            // serialises an Error to "{}" since message/stack are
                            // non-enumerable) to preserve diagnostic information while
                            // keeping the string rejection contract used elsewhere
                            deferred.reject(error.message || String(error));
                        }
                    });

                    req.end();

                });
                return deferred.promise;
            };
        };

        // Like sendRequest, but NEVER rejects on a >= 400 status — it always
        // resolves with {statusCode, body} for every response (including 404 and
        // 406). This is required by control-plane helpers whose non-2xx responses
        // are expected outcomes carrying a meaningful body, e.g. files/retrieve
        // (404 NOT_FOUND with a text body) and pact/verify (406 NOT_ACCEPTABLE
        // with the verification report). The caller inspects statusCode itself.
        // Only a transport-level failure (connection refused, etc.) rejects.
        var sendRawRequest = function (tls, caCertPath, clientOptions) {
            var http = tls ? require('https') : require('http');
            return function (host, port, path, body, contentType) {
                var deferred = defer();
                downloadCACert(tls, caCertPath, function (ca) {

                    var requestBody = (typeof body === "string" ? body : JSON.stringify(body || ""));
                    var options = applyControlPlaneAuth({
                        protocol: tls ? 'https:' : 'http:',
                        method: 'PUT',
                        host: host,
                        path: path,
                        port: port,
                        ca: ca,
                        headers: {
                            'Content-Type': contentType || "application/json; charset=utf-8"
                        },
                    }, clientOptions);

                    var req = http.request(options);

                    req.once('response', function (response) {
                        var data = '';

                        response.on('data', function (chunk) {
                            data += chunk;
                        });

                        response.on('end', function () {
                            // Resolve for every status — the caller interprets it.
                            deferred.resolve({
                                statusCode: response.statusCode,
                                body: data
                            });
                        });
                    });

                    req.once('error', function (error) {
                        if (error.code && error.code === "ECONNREFUSED") {
                            deferred.reject("Can't connect to MockServer running on host: \"" + host + "\" and port: \"" + port + "\"");
                        } else {
                            deferred.reject(error.message || String(error));
                        }
                    });

                    req.write(requestBody);
                    req.end();

                });
                return deferred.promise;
            };
        };

        module.exports = {
            sendRequest: sendRequest,
            sendGetRequest: sendGetRequest,
            sendBinaryRequest: sendBinaryRequest,
            sendDeleteRequest: sendDeleteRequest,
            sendRawRequest: sendRawRequest,
            resolveBearerToken: resolveBearerToken,
            resolveAuthorization: resolveAuthorization,
            clientCertificateOptions: clientCertificateOptions
        };
    }
})();