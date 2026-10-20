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
        var WebSocketClient = require('websocket').client;
        var Q = require('q');
        var resolveBearerToken = require('./sendRequest').resolveBearerToken;
        var clientCertificateOptions = require('./sendRequest').clientCertificateOptions;
        var fs = require('fs');

        var defer = function () {
            var promise = (global.protractor && global.protractor.promise.USE_PROMISE_MANAGER !== false)
                ? global.protractor.promise
                : Q;
            var deferred = promise.defer();

            if (deferred.fulfill && !deferred.resolve) {
                deferred.resolve = deferred.fulfill;
            }
            return deferred;
        };

        // calls back once, with the CA certificates or with (null, reason) when they cannot be had; returns the
        // download request, if one was made, so it can be abandoned
        var downloadCACert = function (tls, caCertPath, callback) {
            // https://raw.githubusercontent.com/mock-server/mockserver-monorepo/master/mockserver/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem

            var dest = "CertificateAuthorityCertificate.pem";
            var done = false;
            var failed = function (reason) {
                if (!done) {
                    done = true;
                    callback(null, reason);
                }
            };
            var readCACert = function (certPath) {
                var ca;
                try {
                    ca = tls ? [fs.readFileSync(certPath, {encoding: 'utf-8'})] : [];
                } catch (e) {
                    failed(e.message || String(e));
                    return;
                }
                done = true;
                callback(ca);
            };

            if (!(tls && !caCertPath && !fs.existsSync('./' + dest))) {
                readCACert(caCertPath || "./" + dest);
                return null;
            }

            var options = {
                protocol: 'https:',
                method: 'GET',
                host: "raw.githubusercontent.com",
                path: "/mock-server/mockserver-monorepo/master/mockserver/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem",
                port: 443,
            };
            var req = require('https').request(options);

            req.once('error', function (error) {
                failed('Fetching ' + JSON.stringify(options, null, 2) + ' failed with error ' + error);
            });

            req.once('response', function (res) {
                if (res.statusCode < 200 || res.statusCode >= 300) {
                    res.resume();
                    failed('Fetching ' + JSON.stringify(options, null, 2) + ' failed with HTTP status code ' + res.statusCode);
                } else {
                    // written beside its destination and renamed into place only when complete, so a download
                    // that is cut off or abandoned never leaves a truncated certificate for later runs to trust
                    var tempPath = dest.replace(/\.pem$/, '') + '.' + process.pid + '.' + Date.now() + '.tmp.pem';
                    var writeStream = fs.createWriteStream(tempPath);
                    var discard = function (reason) {
                        writeStream.destroy();
                        fs.unlink(tempPath, function () {
                        });
                        failed(reason);
                    };
                    res.once('aborted', function () {
                        discard('Fetching ' + JSON.stringify(options, null, 2) + ' was cut off');
                    });
                    res.once('error', function (error) {
                        discard('Fetching ' + JSON.stringify(options, null, 2) + ' failed with error ' + error);
                    });
                    writeStream.once('error', function (error) {
                        discard('Saving ' + dest + ' failed with error ' + error);
                    });
                    // 'finish' follows only the end of a complete response
                    writeStream.once('finish', function () {
                        fs.rename(tempPath, dest, function (error) {
                            if (error) {
                                discard('Saving ' + dest + ' failed with error ' + error);
                            } else if (!done) {
                                console.log('Saved ' + dest + ' from ' + JSON.stringify(options, null, 2));
                                readCACert("./" + dest);
                            }
                        });
                    });
                    res.pipe(writeStream);
                }
            });

            req.end();
            return req;
        };

        var MAX_RECONNECT_ATTEMPTS = 3;

        /**
         * Extract the breakpoint id and correlation id from a headers map.
         * Headers may use canonical or lowercase names and values may be
         * arrays or plain strings.
         *
         * @param {Object} headers  the headers map
         * @return {{breakpointId: string|null, correlationId: string|null}}
         */
        var extractBreakpointHeaders = function (headers) {
            var breakpointId = null;
            var correlationId = null;
            if (headers) {
                for (var hk in headers) {
                    if (headers.hasOwnProperty(hk)) {
                        if (hk === "X-MockServer-BreakpointId" || hk === "x-mockserver-breakpointid") {
                            breakpointId = Array.isArray(headers[hk]) ? headers[hk][0] : headers[hk];
                        }
                        if (hk === "WebSocketCorrelationId" || hk === "websocketcorrelationid") {
                            correlationId = Array.isArray(headers[hk]) ? headers[hk][0] : headers[hk];
                        }
                    }
                }
            }
            return { breakpointId: breakpointId, correlationId: correlationId };
        };

        /**
         * Pure function that routes a WebSocket breakpoint message to the
         * appropriate handler and produces the reply envelope.
         *
         * The function mirrors the dispatch that was previously inline inside
         * the WebSocket onmessage handler. Extracting it enables unit testing
         * without a live WebSocket connection.
         *
         * @param {Object} payload  the parsed message: { type: string, value: string (JSON) }
         * @param {Object} handlers {
         *   breakpointRequestHandlers:      { [breakpointId]: function(request) => request|response|null },
         *   breakpointResponseHandlers:     { [breakpointId]: function(request, response) => response|null },
         *   breakpointStreamFrameHandlers:  { [breakpointId]: function(pausedFrame) => {action, ...}|null },
         *   requestHandler:                 function(request) => envelope|null (legacy callback),
         *   requestAndResponseHandler:      function(requestAndResponse) => envelope|null (legacy callback)
         * }
         * @return {Object|null}  { type: string, value: string } reply envelope, or null
         *                        when the message is a client-id registration or has no handler.
         */
        var routeBreakpointMessage = function (payload, handlers) {
            handlers = handlers || {};
            var breakpointRequestHandlers = handlers.breakpointRequestHandlers || {};
            var breakpointResponseHandlers = handlers.breakpointResponseHandlers || {};
            var breakpointStreamFrameHandlers = handlers.breakpointStreamFrameHandlers || {};
            var requestHandler = handlers.requestHandler || null;
            var requestAndResponseHandler = handlers.requestAndResponseHandler || null;

            if (payload.type === "org.mockserver.model.HttpRequest") {
                var request = JSON.parse(payload.value);
                var reqHeaders = extractBreakpointHeaders(request.headers);
                var breakpointId = reqHeaders.breakpointId;
                var correlationId = reqHeaders.correlationId;

                var bpReqHandler = breakpointId ? breakpointRequestHandlers[breakpointId] : null;
                if (bpReqHandler) {
                    try {
                        var bpResult = bpReqHandler(request);
                        if (bpResult === null || bpResult === undefined) {
                            bpResult = request; // auto-continue
                        }
                        if (correlationId) {
                            if (!bpResult.headers) { bpResult.headers = {}; }
                            bpResult.headers["WebSocketCorrelationId"] = Array.isArray(correlationId) ? correlationId : [correlationId];
                        }
                        var bpResultType = bpResult.statusCode !== undefined
                            ? "org.mockserver.model.HttpResponse"
                            : "org.mockserver.model.HttpRequest";
                        return {
                            type: bpResultType,
                            value: JSON.stringify(bpResult)
                        };
                    } catch (e) {
                        // auto-continue on error
                        if (!request.headers) { request.headers = {}; }
                        if (correlationId) {
                            request.headers["WebSocketCorrelationId"] = Array.isArray(correlationId) ? correlationId : [correlationId];
                        }
                        return {
                            type: "org.mockserver.model.HttpRequest",
                            value: JSON.stringify(request)
                        };
                    }
                } else if (requestHandler) {
                    return requestHandler(request);
                }
                // breakpoint message with no matching handler — auto-continue with the
                // original request so the server-side exchange is not left to time out
                // (consistent with the stream-frame phase and the other language clients)
                if (correlationId) {
                    if (!request.headers) { request.headers = {}; }
                    request.headers["WebSocketCorrelationId"] = Array.isArray(correlationId) ? correlationId : [correlationId];
                    return {
                        type: "org.mockserver.model.HttpRequest",
                        value: JSON.stringify(request)
                    };
                }
                return null;
            } else if (payload.type === "org.mockserver.model.HttpRequestAndHttpResponse") {
                var requestAndResponse = JSON.parse(payload.value);
                var respHeaders = extractBreakpointHeaders(
                    requestAndResponse.httpRequest ? requestAndResponse.httpRequest.headers : null
                );
                var bpId2 = respHeaders.breakpointId;
                var corrId2 = respHeaders.correlationId;

                var bpRespHandler = bpId2 ? breakpointResponseHandlers[bpId2] : null;
                if (bpRespHandler) {
                    try {
                        var bpResp = bpRespHandler(requestAndResponse.httpRequest, requestAndResponse.httpResponse);
                        if (bpResp === null || bpResp === undefined) {
                            bpResp = requestAndResponse.httpResponse; // auto-continue
                        }
                        if (corrId2) {
                            if (!bpResp.headers) { bpResp.headers = {}; }
                            bpResp.headers["WebSocketCorrelationId"] = Array.isArray(corrId2) ? corrId2 : [corrId2];
                        }
                        return {
                            type: "org.mockserver.model.HttpResponse",
                            value: JSON.stringify(bpResp)
                        };
                    } catch (e) {
                        // auto-continue on error
                        var origResp = requestAndResponse.httpResponse || {};
                        if (corrId2) {
                            if (!origResp.headers) { origResp.headers = {}; }
                            origResp.headers["WebSocketCorrelationId"] = Array.isArray(corrId2) ? corrId2 : [corrId2];
                        }
                        return {
                            type: "org.mockserver.model.HttpResponse",
                            value: JSON.stringify(origResp)
                        };
                    }
                } else if (requestAndResponseHandler) {
                    return requestAndResponseHandler(requestAndResponse);
                }
                // breakpoint message with no matching handler — auto-continue with the
                // original response (consistent with the other phases and language clients)
                if (corrId2) {
                    var origResp2 = requestAndResponse.httpResponse || {};
                    if (!origResp2.headers) { origResp2.headers = {}; }
                    origResp2.headers["WebSocketCorrelationId"] = Array.isArray(corrId2) ? corrId2 : [corrId2];
                    return {
                        type: "org.mockserver.model.HttpResponse",
                        value: JSON.stringify(origResp2)
                    };
                }
                return null;
            } else if (payload.type === "org.mockserver.serialization.model.PausedStreamFrameDTO") {
                var pausedFrame = JSON.parse(payload.value);
                var sfBpId = pausedFrame.breakpointId;
                var sfHandler = sfBpId ? breakpointStreamFrameHandlers[sfBpId] : null;
                var decision;
                if (sfHandler) {
                    try {
                        decision = sfHandler(pausedFrame);
                        if (decision === null || decision === undefined) {
                            decision = { correlationId: pausedFrame.correlationId, action: "CONTINUE" };
                        } else {
                            decision.correlationId = pausedFrame.correlationId; // ensure echoed
                        }
                    } catch (e) {
                        decision = { correlationId: pausedFrame.correlationId, action: "CONTINUE" };
                    }
                } else {
                    // auto-continue for unknown breakpoint id
                    decision = { correlationId: pausedFrame.correlationId, action: "CONTINUE" };
                }
                return {
                    type: "org.mockserver.serialization.model.StreamFrameDecisionDTO",
                    value: JSON.stringify(decision)
                };
            }
            // WebSocketClientIdDTO or unknown types — not routed
            return null;
        };

        var DEFAULT_REGISTRATION_TIMEOUT_MILLIS = 10000;

        // resolved per connection attempt, so a reconnect picks up a refreshed bearer token
        var upgradeHeaders = function (clientOptions, clientId) {
            var headers = {};
            var token = resolveBearerToken(clientOptions);
            if (token) {
                headers['Authorization'] = 'Bearer ' + token;
            }
            if (clientId) {
                headers['X-CLIENT-REGISTRATION-ID'] = clientId;
            }
            return Object.keys(headers).length ? headers : undefined;
        };

        var registrationTimeoutMillis = function (clientOptions) {
            var millis = clientOptions && clientOptions.callbackWebSocketTimeoutMillis;
            return (typeof millis === 'number' && isFinite(millis) && millis > 0) ? millis : DEFAULT_REGISTRATION_TIMEOUT_MILLIS;
        };

        var connectionFailure = function (error, host, port) {
            if (error && error.code === "ECONNREFUSED") {
                return "Can't connect to MockServer running on host: \"" + host + "\" and port: \"" + port + "\"";
            }
            // the message, not JSON.stringify (which serialises an Error to "{}"), keeping the string rejection contract
            return (error && error.message) || String(error);
        };

        /*
         * Opens a callback WebSocket. The promise resolves once MockServer has sent the WebSocket's client id,
         * and rejects if the first connection is refused or fails its handshake, if MockServer closes it before
         * sending the client id, or if the client id has not arrived within the registration timeout. After the
         * client id has arrived a dropped connection reconnects, with the same client id, as before.
         */
        var webSocketClient = function (tls, caCertPath, clientOptions) {
            return function (host, port, contextPath) {
                var deferred = defer();
                var timeoutMillis = registrationTimeoutMillis(clientOptions);
                var webSocketLocation = (tls ? "wss" : "ws") + "://" + host + ":" + port + contextPath + "/_mockserver_callback_websocket";

                var clientId;
                var clientIdHandler;
                var requestHandler;
                var requestAndResponseHandler;
                // Per-breakpoint-id handlers for matcher-driven breakpoints
                var breakpointRequestHandlers = {};
                var breakpointResponseHandlers = {};
                var breakpointStreamFrameHandlers = {};
                var registered = false;
                var reconnectAttempts = 0;
                var closed = false;
                var client = null;
                var caDownload = null;
                var currentConnection = null;
                var reconnectTimer = null;
                var reconnectWanted = null;
                var registrationTimer = null;

                // before the client id has arrived: stop for good, releasing the socket, and reject
                var failRegistration = function (reason) {
                    if (registered || closed) {
                        return;
                    }
                    closed = true;
                    clearTimeout(registrationTimer);
                    if (caDownload) {
                        caDownload.destroy();
                    }
                    if (client) {
                        client.abort();
                    }
                    if (currentConnection && currentConnection.state !== 'closed') {
                        currentConnection.drop(1000, 'callback WebSocket registration failed', true);
                    }
                    deferred.reject(reason);
                };

                registrationTimer = setTimeout(function () {
                    failRegistration("MockServer at " + webSocketLocation + " did not register the callback WebSocket with a client id within " + timeoutMillis + "ms");
                }, timeoutMillis);

                var handle = {
                    requestCallback: function requestCallback(callback) {
                        requestHandler = callback;
                    },
                    requestAndResponseCallback: function requestAndResponseCallback(callback) {
                        requestAndResponseHandler = callback;
                    },
                    clientIdCallback: function clientIdCallback(callback) {
                        clientIdHandler = callback;
                        if (clientId) {
                            clientIdHandler(clientId);
                        }
                    },
                    setBreakpointRequestHandler: function (breakpointId, handler) {
                        if (breakpointId && handler) {
                            breakpointRequestHandlers[breakpointId] = handler;
                        }
                    },
                    setBreakpointResponseHandler: function (breakpointId, handler) {
                        if (breakpointId && handler) {
                            breakpointResponseHandlers[breakpointId] = handler;
                        }
                    },
                    setBreakpointStreamFrameHandler: function (breakpointId, handler) {
                        if (breakpointId && handler) {
                            breakpointStreamFrameHandlers[breakpointId] = handler;
                        }
                    },
                    removeBreakpointHandlers: function (breakpointId) {
                        if (breakpointId) {
                            delete breakpointRequestHandlers[breakpointId];
                            delete breakpointResponseHandlers[breakpointId];
                            delete breakpointStreamFrameHandlers[breakpointId];
                        }
                    },
                    clearBreakpointHandlers: function () {
                        breakpointRequestHandlers = {};
                        breakpointResponseHandlers = {};
                        breakpointStreamFrameHandlers = {};
                    },
                    // asked, when MockServer drops the connection, whether to reconnect: a promise of false (or false) stops for good
                    reconnectWhen: function (predicate) {
                        reconnectWanted = predicate;
                    },
                    isClosed: function () {
                        return closed;
                    },
                    // closes the connection for good: no reconnect follows; resolves once it has closed
                    close: function () {
                        closed = true;
                        clearTimeout(registrationTimer);
                        if (reconnectTimer) {
                            clearTimeout(reconnectTimer);
                            reconnectTimer = null;
                        }
                        if (client) {
                            client.abort();
                        }
                        var connection = currentConnection;
                        if (!connection || connection.state === 'closed') {
                            return Promise.resolve();
                        }
                        return new Promise(function (resolve) {
                            connection.once('close', function () {
                                resolve();
                            });
                            connection.close();
                        });
                    }
                };

                caDownload = downloadCACert(tls, caCertPath, function (ca, caFailure) {
                    caDownload = null;
                    if (closed) {
                        return;
                    }
                    if (!ca) {
                        failRegistration(caFailure);
                        return;
                    }

                    var tlsOptions;
                    try {
                        tlsOptions = Object.assign({ca: ca, port: port}, clientCertificateOptions(clientOptions));
                    } catch (e) {
                        failRegistration("Can't read the mTLS client certificate or key: " + (e.message || String(e)));
                        return;
                    }
                    client = new WebSocketClient({
                        maxReceivedFrameSize: 64 * 1024 * 1024,   // 64MiB
                        maxReceivedMessageSize: 64 * 1024 * 1024, // 64MiB
                        fragmentOutgoingMessages: false,
                        tlsOptions: tlsOptions
                    });

                    var scheduleReconnect = function () {
                        reconnectAttempts += 1;
                        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
                            console.error('Max reconnect attempts reached, giving up');
                            return;
                        }
                        var delayMs = Math.min(Math.pow(2, reconnectAttempts), 8) * 1000;
                        console.warn('WebSocket disconnected, reconnecting (attempt ' + reconnectAttempts + '/' + MAX_RECONNECT_ATTEMPTS + ') in ' + (delayMs / 1000) + 's');
                        reconnectTimer = setTimeout(function () {
                            reconnectTimer = null;
                            // the same client id, so an expectation MockServer still holds keeps reaching this client
                            try {
                                client.connect(webSocketLocation, [], undefined, upgradeHeaders(clientOptions, clientId));
                            } catch (e) {
                                console.error('WebSocket reconnect failed: ' + (e.message || String(e)));
                                scheduleReconnect();
                            }
                        }, delayMs);
                    };

                    client.on('connectFailed', function (error) {
                        if (closed) {
                            return;
                        }
                        if (!registered) {
                            failRegistration(connectionFailure(error, host, port));
                        } else {
                            scheduleReconnect();
                        }
                    });

                    client.on('connect', function (connection) {
                        if (closed) {
                            connection.close();
                            return;
                        }
                        reconnectAttempts = 0;
                        currentConnection = connection;
                        connection.on('error', function (error) {
                            // before registration this fails the registration; after it, the close that follows reconnects
                            failRegistration(connectionFailure(error, host, port));
                        });
                        connection.on('close', function (reasonCode, description) {
                            if (closed) {
                                return;
                            }
                            if (!registered) {
                                failRegistration("MockServer at " + webSocketLocation + " closed the callback WebSocket before sending its client id" +
                                    (reasonCode ? " (" + reasonCode + (description ? " " + description : "") + ")" : ""));
                                return;
                            }
                            if (!reconnectWanted) {
                                scheduleReconnect();
                                return;
                            }
                            // false stops for good; true, or a failure to answer, reconnects
                            Promise.resolve().then(reconnectWanted).then(function (wanted) {
                                if (wanted === false) {
                                    closed = true;
                                } else if (!closed) {
                                    scheduleReconnect();
                                }
                            }, function () {
                                if (!closed) {
                                    scheduleReconnect();
                                }
                            });
                        });
                        connection.on('message', function (message) {
                            if (message.type === 'utf8') {
                                var payload = JSON.parse(message.utf8Data);

                                // Handle client-id registration directly (not routed)
                                if (payload.type === "org.mockserver.serialization.model.WebSocketClientIdDTO") {
                                    var registration = JSON.parse(payload.value);
                                    if (registration.clientId) {
                                        clientId = registration.clientId;
                                        if (!registered) {
                                            registered = true;
                                            clearTimeout(registrationTimer);
                                            // the handle's clientIdCallback hands over this client id
                                            deferred.resolve(handle);
                                        } else if (clientIdHandler) {
                                            clientIdHandler(clientId);
                                        }
                                    }
                                    return;
                                }

                                // Route breakpoint / callback messages via the pure function
                                var reply = routeBreakpointMessage(payload, {
                                    breakpointRequestHandlers: breakpointRequestHandlers,
                                    breakpointResponseHandlers: breakpointResponseHandlers,
                                    breakpointStreamFrameHandlers: breakpointStreamFrameHandlers,
                                    requestHandler: requestHandler,
                                    requestAndResponseHandler: requestAndResponseHandler
                                });
                                if (reply) {
                                    connection.sendUTF(JSON.stringify(reply));
                                }
                            } else {
                                console.log('Incorrect message format: ' + JSON.parse(message));
                            }
                        });
                    });

                    try {
                        client.connect(webSocketLocation, [], undefined, upgradeHeaders(clientOptions));
                    } catch (e) {
                        failRegistration(e.message || String(e));
                    }
                });
                return deferred.promise;
            };
        };

        module.exports = {
            webSocketClient: webSocketClient,
            routeBreakpointMessage: routeBreakpointMessage,
            extractBreakpointHeaders: extractBreakpointHeaders
        };
    }
})();