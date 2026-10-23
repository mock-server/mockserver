/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */
var mockServerClient;

(function () {
    "use strict";

    // Every expectation action key the server recognises other than httpResponse itself, kept in
    // one place so it cannot drift out of step with the server's own action list. Derived from
    // Expectation.getAllActions() / the expectation.json schema. If any key is missing here an
    // empty httpResponse gets injected alongside the real action, and the server then rejects the
    // expectation with "exactly one must be marked as primary".
    var NON_HTTP_RESPONSE_ACTION_KEYS = [
        'httpResponseTemplate',
        'httpResponseClassCallback',
        'httpResponseObjectCallback',
        'httpForward',
        'httpForwardTemplate',
        'httpForwardClassCallback',
        'httpForwardObjectCallback',
        'httpOverrideForwardedRequest',
        'httpForwardValidateAction',
        'httpForwardWithFallback',
        'httpError',
        'httpSseResponse',
        'httpLlmResponse',
        'httpWebSocketResponse',
        'grpcStreamResponse',
        'grpcBidiResponse',
        'binaryResponse',
        'dnsResponse'
    ];

    // ------------------------------------------------------------------
    // Expectation filter: metadata, unmatched and paging query parameters
    // ------------------------------------------------------------------
    // Every rejection is thrown BEFORE a request is sent: a filter that silently selected
    // nothing would turn a filtered clear into a clear of every expectation.

    var EXPECTATION_FILTER_PROPERTIES = ['metadataKey', 'metadataValue', 'metadataJsonPath', 'unmatched', 'limit', 'offset'];

    // encodeURIComponent leaves ! ' ( ) * raw; encode them too so every value arrives literally
    var _encodeQueryValue = function (value) {
        return encodeURIComponent(value).replace(/[!'()*]/g, function (c) {
            return '%' + c.charCodeAt(0).toString(16).toUpperCase();
        });
    };

    var _isBlankString = function (value) {
        return typeof value !== 'string' || value.trim() === '';
    };

    // A string is sent as its text, any other value as its compact JSON.
    var _metadataValueText = function (value) {
        if (value === undefined || value === null) {
            throw new Error("the metadata value must not be null or undefined; select by the metadata key alone for any value, or pass the string 'null' to select a JSON null");
        }
        if (typeof value === 'string') {
            return value;
        }
        if (typeof value === 'number' && !isFinite(value)) {
            throw new Error("the metadata value must be a finite number but found: " + value);
        }
        var json;
        try {
            json = JSON.stringify(value);
        } catch (e) {
            throw new Error("the metadata value must be JSON serialisable: " + e.message);
        }
        if (json === undefined) {
            throw new Error("the metadata value must be JSON serialisable but found a " + typeof value);
        }
        return json;
    };

    /**
     * Build the query string suffix (starting with "&", or empty) for an expectation filter:
     * { metadataKey, metadataValue, metadataJsonPath, unmatched, limit, offset }.
     * For a clear the filter must select something and must not page.
     */
    var _expectationFilterQuery = function (filter, forClear) {
        if (filter === null || typeof filter !== 'object' || Array.isArray(filter)) {
            throw new Error("the expectation filter must be an object with any of: " + EXPECTATION_FILTER_PROPERTIES.join(', '));
        }
        var has = function (name) {
            return Object.prototype.hasOwnProperty.call(filter, name);
        };
        Object.keys(filter).forEach(function (name) {
            if (EXPECTATION_FILTER_PROPERTIES.indexOf(name) === -1) {
                throw new Error("\"" + name + "\" is not a supported expectation filter property, only " + EXPECTATION_FILTER_PROPERTIES.join(', ') + " are allowed");
            }
        });
        var params = [];
        if (has('metadataKey')) {
            if (_isBlankString(filter.metadataKey)) {
                throw new Error("the metadata key must be a non-blank string");
            }
            params.push('metadataKey=' + _encodeQueryValue(filter.metadataKey));
            if (has('metadataValue')) {
                params.push('metadataValue=' + _encodeQueryValue(_metadataValueText(filter.metadataValue)));
            }
        } else if (has('metadataValue')) {
            throw new Error("metadataValue requires metadataKey");
        }
        if (has('metadataJsonPath')) {
            if (_isBlankString(filter.metadataJsonPath)) {
                throw new Error("the metadata JSONPath must be a non-blank string");
            }
            params.push('metadataJsonPath=' + _encodeQueryValue(filter.metadataJsonPath));
        }
        var unmatched = false;
        if (filter.unmatched !== undefined && filter.unmatched !== null) {
            if (typeof filter.unmatched !== 'boolean') {
                throw new Error("unmatched must be a boolean but found: " + JSON.stringify(filter.unmatched));
            }
            unmatched = filter.unmatched;
            if (unmatched) {
                params.push('unmatched=true');
            }
        }
        ['limit', 'offset'].forEach(function (name) {
            var value = filter[name];
            if (value !== undefined && value !== null) {
                if (forClear) {
                    throw new Error("limit and offset are not supported for clear");
                }
                if (typeof value !== 'number' || !Number.isInteger(value) || value < 0 || value > 2147483647) {
                    throw new Error("the " + name + " must be an integer, zero or more, but found: " + JSON.stringify(value));
                }
                params.push(name + '=' + value);
            }
        });
        if (forClear && !has('metadataKey') && !has('metadataJsonPath') && !unmatched) {
            throw new Error("clear with a filter requires a metadataKey, metadataJsonPath or unmatched: true selection; use clear(pathOrRequestMatcher, 'EXPECTATIONS') to clear without one");
        }
        return params.length > 0 ? '&' + params.join('&') : '';
    };

    var RECORDED_EXPECTATIONS_OPTIONS = ['consolidate', 'parameterize', 'repeatsAsScenarios', 'captureHeaders', 'extractBodiesLargerThan'];

    /**
     * Build the query string suffix (starting with "&", or empty) for the recorded-expectation options:
     * { consolidate, parameterize, repeatsAsScenarios, captureHeaders, extractBodiesLargerThan }.
     * A boolean option is sent only when true; captureHeaders: [] is sent empty, which keeps no headers.
     */
    var _recordedExpectationsOptionsQuery = function (options) {
        if (options === null || typeof options !== 'object' || Array.isArray(options)) {
            throw new Error("the recorded expectations options must be an object with any of: " + RECORDED_EXPECTATIONS_OPTIONS.join(', '));
        }
        Object.keys(options).forEach(function (name) {
            if (RECORDED_EXPECTATIONS_OPTIONS.indexOf(name) === -1) {
                throw new Error("\"" + name + "\" is not a supported recorded expectations option, only " + RECORDED_EXPECTATIONS_OPTIONS.join(', ') + " are allowed");
            }
        });
        var params = [];
        ['consolidate', 'parameterize', 'repeatsAsScenarios'].forEach(function (name) {
            var value = options[name];
            if (value !== undefined && value !== null) {
                if (typeof value !== 'boolean') {
                    throw new Error(name + " must be a boolean but found: " + JSON.stringify(value));
                }
                if (value) {
                    params.push(name + '=true');
                }
            }
        });
        var captureHeaders = options.captureHeaders;
        if (captureHeaders !== undefined && captureHeaders !== null) {
            if (!Array.isArray(captureHeaders)) {
                throw new Error("captureHeaders must be an array of header names but found: " + JSON.stringify(captureHeaders));
            }
            captureHeaders.forEach(function (name) {
                if (_isBlankString(name) || name.indexOf(',') !== -1) {
                    throw new Error("each captureHeaders entry must be a non-blank header name without a comma but found: " + JSON.stringify(name));
                }
            });
            params.push('captureHeaders=' + _encodeQueryValue(captureHeaders.join(',')));
        }
        var threshold = options.extractBodiesLargerThan;
        if (threshold !== undefined && threshold !== null) {
            if (typeof threshold !== 'number' || !Number.isSafeInteger(threshold) || threshold < 0) {
                throw new Error("extractBodiesLargerThan must be an integer number of bytes, zero or more, but found: " + JSON.stringify(threshold));
            }
            params.push('extractBodiesLargerThan=' + threshold);
        }
        return params.length > 0 ? '&' + params.join('&') : '';
    };

    // Validate the metadata object of an expectation (string keys, any JSON value).
    var _requireMetadataObject = function (metadata) {
        if (typeof metadata !== 'object' || Array.isArray(metadata)) {
            throw new Error("metadata must be an object of string keys to JSON values but found: " + JSON.stringify(metadata));
        }
        return metadata;
    };

    var _withExpectationMetadata = function (expectation, metadata) {
        if (metadata) {
            expectation.metadata = metadata;
        }
        return expectation;
    };

    // ------------------------------------------------------------------
    // Inline breakpoint/callback message routing (browser-safe, no deps)
    // ------------------------------------------------------------------
    // These are pure functions duplicated from webSocketClient.js so they
    // are available in the browser path where require() does not exist.
    // The Node path (webSocketClient.js) keeps its own copy.
    //
    // Defined at IIFE scope (not inside the factory function) so they can
    // be exported for unit testing without changing runtime behaviour.

    /**
     * Extract the breakpoint id and correlation id from a headers map.
     * Headers may use canonical or lowercase names and values may be
     * arrays or plain strings.
     */
    var _extractBreakpointHeaders = function (headers) {
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
     * Pure function that routes a WebSocket breakpoint/callback message to
     * the appropriate handler and produces the reply envelope.
     *
     * @param {Object} payload  { type: string, value: string (JSON) }
     * @param {Object} handlers map of handler collections
     * @return {Object|null}  reply envelope or null
     */
    var _routeBreakpointMessage = function (payload, handlers) {
        handlers = handlers || {};
        var breakpointRequestHandlers = handlers.breakpointRequestHandlers || {};
        var breakpointResponseHandlers = handlers.breakpointResponseHandlers || {};
        var breakpointStreamFrameHandlers = handlers.breakpointStreamFrameHandlers || {};
        var requestHandler = handlers.requestHandler || null;
        var requestAndResponseHandler = handlers.requestAndResponseHandler || null;

        if (payload.type === "org.mockserver.model.HttpRequest") {
            var request = JSON.parse(payload.value);
            var reqHeaders = _extractBreakpointHeaders(request.headers);
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
            // breakpoint message with no matching handler — auto-continue
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
            var respHeaders = _extractBreakpointHeaders(
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
            // auto-continue with original response
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

    // the callback WebSockets the clients of each MockServer have opened in this process, by its address
    var callbackWebSockets = {};

    /**
     * Start the client communicating at the specified host and port
     *, for example:
     *
     *   var client = mockServerClient("localhost", 1080);
     *
     * @param host the host for the server to communicate with
     * @param port the port for the server to communicate with
     * @param contextPath the context path if server was deployed as a war i.e. '/myContextPath'
     * @param tls enable TLS (i.e. HTTPS) for communication to server
     * @param caCertPemFilePath provide custom CA Certificate (defaults to MockServer CA Certificate)
     * @param options optional control-plane auth + mTLS settings:
     *        {
     *            bearerToken,            // static control-plane JWT attached as `Authorization: Bearer <token>`
     *            bearerTokenSupplier,    // function() => string, evaluated per-request (overrides bearerToken)
     *            basicAuthUsername,      // control-plane HTTP Basic username, sent with basicAuthPassword (needs tls)
     *            basicAuthPassword,      // control-plane HTTP Basic password; a bearer token wins when both are set
     *            clientCertPemFilePath,  // PEM client certificate for mutual TLS
     *            clientKeyPemFilePath,   // PEM private key for mutual TLS
     *            callbackWebSocketTimeoutMillis // how long a callback or breakpoint waits for its WebSocket's client id (default 10000)
     *        }
     */
    mockServerClient = function (host, port, contextPath, tls, caCertPemFilePath, options) {

        var runningInNode = function () {
            return (typeof require !== 'undefined') && require('browser-or-node').isNode;
        };

        // LLM mocking builder factories (browser-safe): in Node use require,
        // in the browser fall back to the global set by llm.js.
        var _llm = (typeof require !== 'undefined') ? require('./llm') : (typeof window !== 'undefined' ? window.mockServerLlm : undefined);

        // MCP (Model Context Protocol) mock builder factory (browser-safe): in
        // Node use require, in the browser fall back to the global set by
        // mcpMockBuilder.js.
        var _mcpMock = (typeof require !== 'undefined') ? require('./mcpMockBuilder').mcpMock : (typeof window !== 'undefined' ? (window.mockServerMcp && window.mockServerMcp.mcpMock) : undefined);

        // A2A (Agent-to-Agent) mock builder factory (browser-safe): in Node use
        // require, in the browser fall back to the global set by a2aMockBuilder.js.
        var _a2aMock = (typeof require !== 'undefined') ? require('./a2aMockBuilder').a2aMock : (typeof window !== 'undefined' ? (window.mockServerA2a && window.mockServerA2a.a2aMock) : undefined);

        if (options && typeof options.basicAuthUsername === 'string' && options.basicAuthUsername !== '' && !tls && typeof console !== 'undefined') {
            console.warn('mockserver-client: control-plane Basic credentials are configured without tls, so the password can be read on the network, and MockServer refuses them unless controlPlaneBasicAuthenticationAllowInsecure is set (tests only)');
        }

        // Resolve the control-plane bearer token (static or supplier) for the
        // browser (XMLHttpRequest) transport path. The Node transport resolves
        // it per-request inside sendRequest.js.
        var _resolveBrowserBearerToken = function () {
            if (!options) {
                return null;
            }
            var token = null;
            if (typeof options.bearerTokenSupplier === 'function') {
                token = options.bearerTokenSupplier();
            } else if (options.bearerToken !== undefined && options.bearerToken !== null) {
                token = options.bearerToken;
            }
            if (token === undefined || token === null || token === '') {
                return null;
            }
            return String(token);
        };
        var _setBrowserAuthHeader = function (xmlhttp) {
            var token = _resolveBrowserBearerToken();
            if (token) {
                xmlhttp.setRequestHeader("Authorization", "Bearer " + token);
            } else if (options && typeof options.basicAuthUsername === 'string' && options.basicAuthUsername !== '' && options.basicAuthPassword !== undefined && options.basicAuthPassword !== null) {
                // UTF-8 bytes as a binary string, which is what btoa encodes
                xmlhttp.setRequestHeader("Authorization", "Basic " + window.btoa(unescape(encodeURIComponent(options.basicAuthUsername + ':' + String(options.basicAuthPassword)))));
            }
        };

        var _browserUrl = function (host, port, path) {
            return (tls ? 'https' : 'http') + '://' + host + ':' + port + (contextPath ? (contextPath.indexOf("/") === 0 ? contextPath : "/" + contextPath) : "") + path;
        };

        // The browser (XMLHttpRequest) transport, settling as the Node transport in sendRequest.js does: a 4xx or
        // 5xx rejects ("404 Not Found" for a 404, otherwise the response body) and never reaches the success
        // callback, unless resolveEveryStatus; and a request that gets no response rejects.
        var _browserRequest = function (method, host, port, path, contentType, body, resolveEveryStatus) {
            return {
                then: function (sucess, error) {
                    try {
                        var xmlhttp = new XMLHttpRequest();
                        xmlhttp.addEventListener("load", function () {
                            if (!resolveEveryStatus && this.status >= 400 && this.status < 600) {
                                if (error) {
                                    error(this.status === 404 ? "404 Not Found" : this.responseText);
                                }
                            } else if (sucess) {
                                sucess({
                                    statusCode: this.status,
                                    body: this.responseText
                                });
                            }
                        });
                        xmlhttp.addEventListener("error", function () {
                            if (error) {
                                error("Can't connect to MockServer running on host: \"" + host + "\" and port: \"" + port + "\"");
                            }
                        });
                        xmlhttp.open(method, _browserUrl(host, port, path));
                        if (contentType) {
                            xmlhttp.setRequestHeader("Content-Type", contentType);
                        }
                        _setBrowserAuthHeader(xmlhttp);
                        xmlhttp.send(body);
                    } catch (e) {
                        if (error) {
                            error(e);
                        }
                    }
                }
            };
        };

        var makeRequest = (runningInNode() ? require('./sendRequest').sendRequest(tls, caCertPemFilePath, options) : function (host, port, path, jsonBody) {
            var body = (typeof jsonBody === "string" ? jsonBody : JSON.stringify(jsonBody || ""));
            return _browserRequest('PUT', host, port, path, "application/json; charset=utf-8", body);
        });

        var makeGetRequest = (runningInNode() ? require('./sendRequest').sendGetRequest(tls, caCertPemFilePath, options) : function (host, port, path) {
            return _browserRequest('GET', host, port, path);
        });

        var makeBinaryRequest = (runningInNode() ? require('./sendRequest').sendBinaryRequest(tls, caCertPemFilePath, options) : function (host, port, path, bodyBuffer, contentType) {
            return _browserRequest('PUT', host, port, path, contentType || "application/octet-stream", bodyBuffer);
        });

        var makeDeleteRequest = (runningInNode() ? require('./sendRequest').sendDeleteRequest(tls, caCertPemFilePath, options) : function (host, port, path) {
            return _browserRequest('DELETE', host, port, path);
        });

        // PUT that resolves {statusCode, body} for EVERY status (never rejects on
        // 4xx). Used by control-plane helpers whose non-2xx responses are expected
        // outcomes carrying a meaningful body (files/retrieve 404, pact/verify 406).
        var makeRawRequest = (runningInNode() ? require('./sendRequest').sendRawRequest(tls, caCertPemFilePath, options) : function (host, port, path, body, contentType) {
            var requestBody = (typeof body === "string" ? body : JSON.stringify(body || ""));
            return _browserRequest('PUT', host, port, path, contentType || "application/json; charset=utf-8", requestBody, true);
        });

        var cleanedContextPath = (function (contextPath) {
            if (contextPath) {
                if (!contextPath.endsWith("/")) {
                    contextPath += "/";
                }
                if (!contextPath.startsWith("/")) {
                    contextPath = "/" + contextPath;
                }
                return contextPath;
            } else {
                return '';
            }
        })(contextPath);

        /**
         * The default headers added to to the mocked response when using mockSimpleResponse(...)
         */
        var defaultResponseHeaders;
        var defaultRequestHeaders;

        var headersUniqueConcatenate = function (arrayTarget, arraySource) {
            if (!arrayTarget) {
                arrayTarget = arraySource;
            } else if (Array.isArray(arrayTarget) && Array.isArray(arraySource)) {
                if (arraySource && arraySource.length) {
                    if (arrayTarget && arrayTarget.length) {
                        for (var i = 0; i < arraySource.length; i++) {
                            var arrayTargetAlreadyHasValue = false;
                            for (var j = 0; j < arrayTarget.length; j++) {
                                if (JSON.stringify(arraySource[i]) === JSON.stringify(arrayTarget[j])) {
                                    arrayTargetAlreadyHasValue = true;
                                }
                            }
                            if (!arrayTargetAlreadyHasValue) {
                                arrayTarget.push(arraySource[i]);
                            }
                        }
                    } else {
                        arrayTarget = arraySource;
                    }
                }
            } else if (!Array.isArray(arrayTarget) && Array.isArray(arraySource)) {
                arraySource.forEach(function (entry) {
                    arrayTarget[entry["name"]] = entry["values"];
                });
            } else if (Array.isArray(arrayTarget) && !Array.isArray(arraySource)) {
                for (var property in arraySource) {
                    if (arraySource.hasOwnProperty(property)) {
                        arrayTarget.push({"name": property, "values": arraySource[property]});
                    }
                }
            } else if (!Array.isArray(arrayTarget) && !Array.isArray(arraySource)) {
                arrayTarget = Object.assign(arrayTarget, arraySource);
            }
            return arrayTarget;
        };
        var createRequestMatcher = function (path) {
            return {
                method: "",
                path: path,
                body: "",
                headers: defaultRequestHeaders,
                cookies: [],
                queryStringParameters: []
            };

        };
        var createExpectation = function (path, responseBody, statusCode) {
            return {
                httpRequest: createRequestMatcher(path),
                httpResponse: {
                    statusCode: statusCode || 200,
                    body: JSON.stringify(responseBody),
                    cookies: [],
                    headers: defaultResponseHeaders,
                    delay: {
                        timeUnit: "MICROSECONDS",
                        value: 0
                    }
                },
                times: {
                    remainingTimes: 1,
                    unlimited: false
                }
            };
        };
        var createExpectationWithCallback = function (requestMatcher, clientId, times, priority, timeToLive, id, metadata) {
            var timesObject;
            if (typeof times === 'number') {
                timesObject = {
                    remainingTimes: times,
                    unlimited: false
                };
            } else if (typeof times === 'object') {
                timesObject = times;
            }
            requestMatcher.headers = headersUniqueConcatenate(requestMatcher.headers, defaultRequestHeaders);
            return _withExpectationMetadata({
                id: typeof id === 'string' ? id : undefined,
                priority: typeof priority === 'number' ? priority : undefined,
                httpRequest: requestMatcher,
                httpResponseObjectCallback: {
                    clientId: clientId
                },
                times: timesObject || {
                    remainingTimes: 1,
                    unlimited: false
                },
                timeToLive: typeof timeToLive === 'object' ? timeToLive : {
                    unlimited: true
                }
            }, metadata);
        };

        var createExpectationWithForwardCallback = function (requestMatcher, clientId, times, priority, timeToLive, id, metadata) {
            var timesObject;
            if (typeof times === 'number') {
                timesObject = {
                    remainingTimes: times,
                    unlimited: false
                };
            } else if (typeof times === 'object') {
                timesObject = times;
            }
            requestMatcher.headers = headersUniqueConcatenate(requestMatcher.headers, defaultRequestHeaders);
            return _withExpectationMetadata({
                id: typeof id === 'string' ? id : undefined,
                priority: typeof priority === 'number' ? priority : undefined,
                httpRequest: requestMatcher,
                httpForwardObjectCallback: {
                    clientId: clientId
                },
                times: timesObject || {
                    remainingTimes: 1,
                    unlimited: false
                },
                timeToLive: typeof timeToLive === 'object' ? timeToLive : {
                    unlimited: true
                }
            }, metadata);
        };
        var createExpectationWithForwardAndResponseCallback = function (requestMatcher, clientId, times, priority, timeToLive, id) {
            var timesObject;
            if (typeof times === 'number') {
                timesObject = {
                    remainingTimes: times,
                    unlimited: false
                };
            } else if (typeof times === 'object') {
                timesObject = times;
            }
            requestMatcher.headers = headersUniqueConcatenate(requestMatcher.headers, defaultRequestHeaders);
            return {
                id: typeof id === 'string' ? id : undefined,
                priority: typeof priority === 'number' ? priority : undefined,
                httpRequest: requestMatcher,
                httpForwardObjectCallback: {
                    clientId: clientId,
                    responseCallback: true
                },
                times: timesObject || {
                    remainingTimes: 1,
                    unlimited: false
                },
                timeToLive: typeof timeToLive === 'object' ? timeToLive : {
                    unlimited: true
                }
            };
        };

        // as the Node transport in webSocketClient.js: settles once MockServer has sent the client id
        var WebSocketClient = (runningInNode() ? require('./webSocketClient').webSocketClient(tls, caCertPemFilePath, options) : function (host, port, contextPath) {
            var clientId;
            var clientIdHandler;
            var requestHandler;
            var requestAndResponseHandler;
            var breakpointRequestHandlers = {};
            var breakpointResponseHandlers = {};
            var breakpointStreamFrameHandlers = {};
            var socket;

            return {
                then: function (sucess, error) {
                    var settled = false;
                    var registrationTimer = null;
                    var fail = function (reason) {
                        if (settled) {
                            return;
                        }
                        settled = true;
                        clearTimeout(registrationTimer);
                        if (socket && socket.readyState !== socket.CLOSING && socket.readyState !== socket.CLOSED) {
                            socket.close();
                        }
                        if (error) {
                            error(reason);
                        }
                    };
                    try {
                        var BrowserWebSocket = (typeof WebSocket !== "undefined") ? WebSocket :
                            ((typeof window !== "undefined" && window.MozWebSocket) ? window.MozWebSocket : null);
                        if (!BrowserWebSocket) {
                            fail("Your browser does not support web sockets.");
                            return;
                        }

                        var webSocketLocation = (tls ? "wss" : "ws") + "://" + host + ":" + port + contextPath + "/_mockserver_callback_websocket";
                        var timeoutMillis = (options && typeof options.callbackWebSocketTimeoutMillis === 'number' && isFinite(options.callbackWebSocketTimeoutMillis) && options.callbackWebSocketTimeoutMillis > 0) ?
                            options.callbackWebSocketTimeoutMillis : 10000;
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
                                if (breakpointId && handler) { breakpointRequestHandlers[breakpointId] = handler; }
                            },
                            setBreakpointResponseHandler: function (breakpointId, handler) {
                                if (breakpointId && handler) { breakpointResponseHandlers[breakpointId] = handler; }
                            },
                            setBreakpointStreamFrameHandler: function (breakpointId, handler) {
                                if (breakpointId && handler) { breakpointStreamFrameHandlers[breakpointId] = handler; }
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
                            // the browser's WebSocket does not reconnect
                            reconnectWhen: function () {
                            },
                            isClosed: function () {
                                return !socket || socket.readyState === socket.CLOSING || socket.readyState === socket.CLOSED;
                            },
                            close: function () {
                                if (!socket || socket.readyState === socket.CLOSED) {
                                    return Promise.resolve();
                                }
                                return new Promise(function (resolve) {
                                    socket.addEventListener('close', function () {
                                        resolve();
                                    });
                                    socket.close();
                                });
                            }
                        };

                        registrationTimer = setTimeout(function () {
                            fail("MockServer at " + webSocketLocation + " did not register the callback WebSocket with a client id within " + timeoutMillis + "ms");
                        }, timeoutMillis);
                        socket = new BrowserWebSocket(webSocketLocation);
                        socket.onmessage = function (event) {
                            var message = JSON.parse(event.data);

                            // Handle client-id registration directly
                            if (message.type === "org.mockserver.serialization.model.WebSocketClientIdDTO") {
                                var registration = JSON.parse(message.value);
                                if (registration.clientId) {
                                    clientId = registration.clientId;
                                    if (!settled) {
                                        settled = true;
                                        clearTimeout(registrationTimer);
                                        // the handle's clientIdCallback hands over this client id
                                        sucess(handle);
                                    } else if (clientIdHandler) {
                                        clientIdHandler(clientId);
                                    }
                                }
                                return;
                            }

                            // Route breakpoint / callback messages via the shared pure function
                            var reply = _routeBreakpointMessage(message, {
                                breakpointRequestHandlers: breakpointRequestHandlers,
                                breakpointResponseHandlers: breakpointResponseHandlers,
                                breakpointStreamFrameHandlers: breakpointStreamFrameHandlers,
                                requestHandler: requestHandler,
                                requestAndResponseHandler: requestAndResponseHandler
                            });
                            if (reply && socket.readyState === socket.OPEN) {
                                socket.send(JSON.stringify(reply));
                            }
                        };
                        // a browser reports no detail of a failed connection; the close that follows has the code
                        socket.onerror = function () {
                            fail("Can't connect to MockServer running on host: \"" + host + "\" and port: \"" + port + "\"");
                        };
                        socket.onclose = function (event) {
                            fail("MockServer at " + webSocketLocation + " closed the callback WebSocket before sending its client id" +
                                (event && event.code ? " (" + event.code + (event.reason ? " " + event.reason : "") + ")" : ""));
                        };
                    } catch (e) {
                        fail(e);
                    }
                }
            };
        });

        var callbackWebSocketKey = host + ":" + port + cleanedContextPath;

        /*
         * Opens a callback WebSocket and records it, so close() and reset() can close it. One that is
         * closed before it has opened is closed as it opens, and is not handed to the caller.
         */
        var openCallbackWebSocket = function () {
            var opening = new WebSocketClient(host, port, cleanedContextPath);
            var entry = {closed: false, handle: null, settled: null};
            (callbackWebSockets[callbackWebSocketKey] = callbackWebSockets[callbackWebSocketKey] || []).push(entry);
            return {
                isClosed: function () {
                    return entry.closed;
                },
                then: function (success, error) {
                    var settle;
                    entry.settled = new Promise(function (resolve) {
                        settle = resolve;
                    });
                    opening.then(function (handle) {
                        entry.handle = handle;
                        settle();
                        if (!entry.closed) {
                            success(handle);
                        } else if (error) {
                            error("the callback WebSocket was closed before it opened");
                        }
                    }, function (reason) {
                        settle();
                        // a WebSocket that never opened has nothing to close
                        var entries = callbackWebSockets[callbackWebSocketKey];
                        if (entries && entries.indexOf(entry) !== -1) {
                            entries.splice(entries.indexOf(entry), 1);
                        }
                        if (error) {
                            error(reason);
                        }
                    });
                }
            };
        };

        var closeCallbackWebSockets = function () {
            var entries = callbackWebSockets[callbackWebSocketKey] || [];
            delete callbackWebSockets[callbackWebSocketKey];
            return Promise.all(entries.map(function (entry) {
                entry.closed = true;
                var closeHandle = function () {
                    return entry.handle ? entry.handle.close() : undefined;
                };
                return Promise.resolve(entry.settled).then(closeHandle, closeHandle);
            })).then(function () {
            });
        };

        /*
         * Resolves with the active expectation that has this id, or null when MockServer does not hold it
         * (it answers 400 for an unknown id); rejects when MockServer cannot say.
         */
        var findActiveExpectation = function (expectationId) {
            return new Promise(function (resolve, reject) {
                makeRequest(host, port, "/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=JSON", {id: expectationId}).then(function (result) {
                    var expectations = result && result.body ? JSON.parse(result.body) : [];
                    resolve(expectations.filter(function (expectation) {
                        return expectation.id === expectationId;
                    })[0] || null);
                }, function (reason) {
                    if (String(reason).indexOf("No expectation found with id") !== -1) {
                        resolve(null);
                    } else {
                        reject(reason);
                    }
                });
            });
        };

        /*
         * Registers the expectation of a callback once its WebSocket has a client id. MockServer closes the
         * WebSocket when it removes the expectation (used up, cleared or reset) and when the connection drops.
         * The client reconnects, with the same client id, only while MockServer still holds the expectation
         * (or cannot say), and never registers it again: success runs once.
         */
        var registerCallbackExpectation = function (webSocketClient, createExpectation, sucess, error) {
            var registered = false;
            var expectationId = null;
            webSocketClient.reconnectWhen(function () {
                return expectationId === null || findActiveExpectation(expectationId).then(function (expectation) {
                    return expectation !== null;
                });
            });
            webSocketClient.clientIdCallback(function (clientId) {
                if (registered) {
                    return;
                }
                registered = true;
                makeRequest(host, port, "/mockserver/expectation", createExpectation(clientId)).then(function (result) {
                    try {
                        expectationId = JSON.parse(result.body)[0].id || null;
                    } catch (e) {
                        expectationId = null;
                    }
                    if (sucess) {
                        sucess(result);
                    }
                }, error);
            });
        };

        /**
         * Override:
         *
         * - default headers that are used to specify the response headers in mockSimpleResponse(...)
         *   (note: if you use mockAnyResponse(...) the default headers are not used)
         *
         * - headers added to every request matcher, this is particularly useful for running tests in parallel
         *
         *, for example:
         *
         *   client.setDefaultHeaders([
         *       {"name": "Content-Type", "values": ["application/json; charset=utf-8"]},
         *       {"name": "Cache-Control", "values": ["no-cache, no-store"]}
         *   ],[
         *       {"name": "sessionId", "values": ["786fcf9b-606e-605f-181d-c245b55e5eac"]}
         *   ])
         *
         * @param responseHeaders the default headers to be added to every response
         * @param requestHeaders the default headers to be added to every request matcher
         */
        var setDefaultHeaders = function (responseHeaders, requestHeaders) {
            if (responseHeaders) {
                defaultResponseHeaders = responseHeaders;
            }
            if (requestHeaders) {
                defaultRequestHeaders = requestHeaders;
            }
            return _this;
        };

        var addDefaultRequestMatcherHeaders = function (pathOrRequestMatcher) {
            var requestMatcher;
            if (typeof pathOrRequestMatcher === "string") {
                requestMatcher = {
                    path: pathOrRequestMatcher
                };
            } else if (typeof pathOrRequestMatcher === "object") {
                requestMatcher = pathOrRequestMatcher;
            } else {
                requestMatcher = {};
            }
            if (defaultRequestHeaders) {
                if (requestMatcher.httpRequest) {
                    requestMatcher.httpRequest.headers = headersUniqueConcatenate(requestMatcher.httpRequest.headers, defaultRequestHeaders);
                } else {
                    requestMatcher.headers = headersUniqueConcatenate(requestMatcher.headers, defaultRequestHeaders);
                }
            }
            return requestMatcher;
        };
        var addDefaultResponseMatcherHeaders = function (response) {
            var responseMatcher;
            if (typeof response === "object") {
                responseMatcher = response;
            } else {
                responseMatcher = {};
            }
            if (defaultResponseHeaders) {
                if (responseMatcher.httpResponse) {
                    responseMatcher.httpResponse.headers = headersUniqueConcatenate(responseMatcher.httpResponse.headers, defaultResponseHeaders);
                } else {
                    responseMatcher.headers = headersUniqueConcatenate(responseMatcher.headers, defaultResponseHeaders);
                }
            }
            return responseMatcher;
        };
        var hasNonHttpResponseAction = function (expectation) {
            return NON_HTTP_RESPONSE_ACTION_KEYS.some(function (key) {
                return Boolean(expectation[key]);
            });
        };
        var addDefaultExpectationHeaders = function (expectation) {
            if (!defaultRequestHeaders && !defaultResponseHeaders) {
                // nothing to add — returning early avoids materialising an empty httpResponse next to
                // a real action, which the server counts as a second action with no primary. Belt and
                // braces alongside NON_HTTP_RESPONSE_ACTION_KEYS: a future server action that nobody
                // adds to that list degrades to "default headers not applied" rather than a hard
                // IllegalArgumentException from the server.
                return expectation;
            }
            if (Array.isArray(expectation)) {
                for (var i = 0; i < expectation.length; i++) {
                    expectation[i].httpRequest = addDefaultRequestMatcherHeaders(expectation[i].httpRequest);
                    if (!hasNonHttpResponseAction(expectation[i])) {
                        expectation[i].httpResponse = addDefaultResponseMatcherHeaders(expectation[i].httpResponse);
                    }
                }
            } else {
                expectation.httpRequest = addDefaultRequestMatcherHeaders(expectation.httpRequest);
                if (!hasNonHttpResponseAction(expectation)) {
                    expectation.httpResponse = addDefaultResponseMatcherHeaders(expectation.httpResponse);
                }
            }
            return expectation;
        };
        /**
         * Setup an expectation by specifying an expectation object
         *, for example:
         *
         *   client.mockAnyResponse(
         *       {
         *           'httpRequest': {
         *               'path': '/somePath',
         *               'body': {
         *                   'type': "STRING",
         *                   'value': 'someBody'
         *               }
         *           },
         *           'httpResponse': {
         *               'statusCode': 200,
         *               'body': Base64.encode(JSON.stringify({ name: 'first_body' })),
         *               'delay': {
         *                   'timeUnit': 'MILLISECONDS',
         *                   'value': 250
         *               }
         *           },
         *           'times': {
         *               'remainingTimes': 1,
         *               'unlimited': false
         *           }
         *       }
         *   );
         *
         * @param expectation the expectation to setup on the MockServer
         */
        var mockAnyResponse = function (expectation) {
            return makeRequest(host, port, "/mockserver/expectation", addDefaultExpectationHeaders(expectation));
        };
        /**
         * Setup one or more LLM mock expectations. Accepts a single expectation
         * object, an array of expectations, or an LLM builder (the result of
         * client.llm.llmMock(...), .conversation(), or .llmFailover()); builders
         * are built via their .build() method. Equivalent to the Java client's
         * builder.applyTo(mockServerClient).
         *
         *, for example:
         *
         *   client.mockWithLLM(
         *       client.llm.llmMock("/v1/messages")
         *           .withProvider(client.llm.Provider.ANTHROPIC)
         *           .withModel("claude-sonnet-4")
         *           .respondingWith(
         *               client.llm.completion().withText("Paris.")
         *           )
         *   );
         *
         * @param expectationOrBuilder an expectation, array of expectations, or LLM builder
         */
        var mockWithLLM = function (expectationOrBuilder) {
            var expectation = (expectationOrBuilder && typeof expectationOrBuilder.build === "function")
                ? expectationOrBuilder.build()
                : expectationOrBuilder;
            return makeRequest(host, port, "/mockserver/expectation", addDefaultExpectationHeaders(expectation));
        };
        /**
         * Setup an expectation by specifying an OpenAPI expectation
         *, for example:
         *
         *   client.openAPIExpectation(
         *       {
         *           "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver-monorepo/master/mockserver/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json",
         *           "operationsAndResponses": {
         *               "showPetById": "200",
         *               "createPets": "500"
         *           }
         *       }
         *   );
         *
         * @param expectation the OpenAPI expectation to setup on the MockServer
         */
        var openAPIExpectation = function (expectation) {
            return makeRequest(host, port, "/mockserver/openapi", expectation);
        };
        /**
         * Setup an expectation by specifying a request matcher, and
         * a local request handler function.  The request handler function receives each
         * request (that matches the request matcher) and returns the response that will be returned for this expectation.
         *
         *, for example:
         *
         *    client.mockWithCallback(
         *            {
         *                path: '/somePath',
         *                body: 'some_request_body'
         *            },
         *            function (request) {
         *                var response = {
         *                    statusCode: 200,
         *                    body: 'some_response_body'
         *                };
         *                return response
         *            }
         *    ).then(
         *            function () {
         *                alert('expectation sent');
         *            },
         *            function (error) {
         *                alert('error');
         *            }
         *    );
         *
         * @param requestMatcher the request matcher for the expectation
         * @param requestHandler the function to be called back when the request is matched
         * @param times the number of times the requestMatcher should be matched
         * @param priority the priority with which this expectation is used to match requests compared to other expectations (high first)
         * @param timeToLive the time this expectation should be used to match requests
         * @param id the unique expectation id
         */
        var mockWithCallback = function (requestMatcher, requestHandler, times, priority, timeToLive, id, metadata) {
            return {
                then: function (sucess, error) {
                    try {
                        var webSocketClientPromise = openCallbackWebSocket();
                        webSocketClientPromise.then(function (webSocketClient) {
                            webSocketClient.requestCallback(function (request) {
                                var response = requestHandler(request);
                                response.headers = headersUniqueConcatenate(response.headers, [
                                    {
                                        "name": "WebSocketCorrelationId",
                                        "values": request.headers["WebSocketCorrelationId"] || request.headers["websocketcorrelationid"]
                                    }
                                ]);
                                return {
                                    type: "org.mockserver.model.HttpResponse",
                                    value: JSON.stringify(response)
                                };
                            });
                            registerCallbackExpectation(webSocketClient, function (clientId) {
                                return createExpectationWithCallback(requestMatcher, clientId, times, priority, timeToLive, id, metadata);
                            }, sucess, error);
                        }, error);
                    } catch (e) {
                        if (error) {
                            error(e);
                        }
                    }
                }
            };
        };
        var mockWithForwardCallback = function (requestMatcher, forwardHandler, times, priority, timeToLive, id, metadata) {
            return {
                then: function (sucess, error) {
                    try {
                        var webSocketClientPromise = openCallbackWebSocket();
                        webSocketClientPromise.then(function (webSocketClient) {
                            webSocketClient.requestCallback(function (request) {
                                var forwardRequest = forwardHandler(request);
                                forwardRequest.headers = headersUniqueConcatenate(forwardRequest.headers, [
                                    {
                                        "name": "WebSocketCorrelationId",
                                        "values": request.headers["WebSocketCorrelationId"] || request.headers["websocketcorrelationid"]
                                    }
                                ]);
                                return {
                                    type: "org.mockserver.model.HttpRequest",
                                    value: JSON.stringify(forwardRequest)
                                };
                            });
                            registerCallbackExpectation(webSocketClient, function (clientId) {
                                return createExpectationWithForwardCallback(requestMatcher, clientId, times, priority, timeToLive, id, metadata);
                            }, sucess, error);
                        }, error);
                    } catch (e) {
                        if (error) {
                            error(e);
                        }
                    }
                }
            };
        };
        var mockWithForwardAndResponseCallback = function (requestMatcher, forwardHandler, responseHandler, times, priority, timeToLive, id) {
            return {
                then: function (sucess, error) {
                    try {
                        var webSocketClientPromise = openCallbackWebSocket();
                        webSocketClientPromise.then(function (webSocketClient) {
                            webSocketClient.requestCallback(function (request) {
                                var forwardRequest = forwardHandler(request);
                                forwardRequest.headers = headersUniqueConcatenate(forwardRequest.headers, [
                                    {
                                        "name": "WebSocketCorrelationId",
                                        "values": request.headers["WebSocketCorrelationId"] || request.headers["websocketcorrelationid"]
                                    }
                                ]);
                                return {
                                    type: "org.mockserver.model.HttpRequest",
                                    value: JSON.stringify(forwardRequest)
                                };
                            });
                            webSocketClient.requestAndResponseCallback(function (requestAndResponse) {
                                var response = responseHandler(requestAndResponse.httpRequest, requestAndResponse.httpResponse);
                                var correlationId;
                                if (requestAndResponse.httpRequest && requestAndResponse.httpRequest.headers) {
                                    correlationId = requestAndResponse.httpRequest.headers["WebSocketCorrelationId"] || requestAndResponse.httpRequest.headers["websocketcorrelationid"];
                                }
                                if (correlationId) {
                                    response.headers = headersUniqueConcatenate(response.headers, [
                                        {
                                            "name": "WebSocketCorrelationId",
                                            "values": correlationId
                                        }
                                    ]);
                                }
                                return {
                                    type: "org.mockserver.model.HttpResponse",
                                    value: JSON.stringify(response)
                                };
                            });
                            registerCallbackExpectation(webSocketClient, function (clientId) {
                                return createExpectationWithForwardAndResponseCallback(requestMatcher, clientId, times, priority, timeToLive, id);
                            }, sucess, error);
                        }, error);
                    } catch (e) {
                        if (error) {
                            error(e);
                        }
                    }
                }
            };
        };
        /**
         * Normalise the class-callback argument accepted by
         * respondWithClassCallback / forwardWithClassCallback into the wire
         * action object { callbackClass, delay?, primary? }. The argument may be
         * a plain class-name string or a full HttpClassCallback object.
         */
        var createClassCallbackAction = function (callbackClassOrAction) {
            if (typeof callbackClassOrAction === "string") {
                return {callbackClass: callbackClassOrAction};
            }
            return callbackClassOrAction;
        };
        /**
         * Build the expectation object shared by all advanced response builders.
         * The requestMatcher may be a path string or a full request matcher object.
         * The supplied responseAction is set on the expectation under the given
         * top-level action property name (matching the MockServer JSON model).
         */
        var createAdvancedResponseExpectation = function (responseActionProperty, requestMatcher, responseAction, times, priority, timeToLive, id) {
            var request = (typeof requestMatcher === "string") ? createRequestMatcher(requestMatcher) : requestMatcher;
            var timesObject;
            if (typeof times === "number") {
                timesObject = {
                    remainingTimes: times,
                    unlimited: false
                };
            } else if (typeof times === "object" && times !== null) {
                timesObject = times;
            } else {
                timesObject = {
                    remainingTimes: 1,
                    unlimited: false
                };
            }
            var expectation = {
                id: typeof id === "string" ? id : undefined,
                priority: typeof priority === "number" ? priority : undefined,
                httpRequest: request,
                times: timesObject,
                timeToLive: (typeof timeToLive === "object" && timeToLive !== null) ? timeToLive : undefined
            };
            expectation[responseActionProperty] = responseAction;
            return expectation;
        };
        /**
         * Setup an expectation that delegates the response to a server-side
         * class implementing the MockServer ExpectationResponseCallback
         * interface (a "class callback"). This is a pure-JSON, REST-only
         * mechanism - no callback WebSocket is opened. The referenced class is
         * resolved and run inside the MockServer JVM, so it must be on the
         * server's classpath.
         *, for example:
         *
         *   client.respondWithClassCallback('/somePath', 'com.example.MyResponseCallback');
         *
         *   // or with delay / primary, pass the full action object:
         *   client.respondWithClassCallback('/somePath', {
         *       callbackClass: 'com.example.MyResponseCallback',
         *       delay: { timeUnit: 'MILLISECONDS', value: 100 }
         *   });
         *
         * @param requestMatcher the path to match (string) or a full request matcher object
         * @param callbackClass  the fully-qualified server-side callback class name, or a full
         *                       httpResponseClassCallback action object ({ callbackClass, delay?, primary? })
         * @param times the number of times the requestMatcher should be matched (optional)
         * @param priority the priority with which this expectation is used to match requests (optional)
         * @param timeToLive the time this expectation should be used to match requests (optional)
         * @param id the unique expectation id (optional)
         */
        var respondWithClassCallback = function (requestMatcher, callbackClass, times, priority, timeToLive, id) {
            return mockAnyResponse(createAdvancedResponseExpectation("httpResponseClassCallback", requestMatcher, createClassCallbackAction(callbackClass), times, priority, timeToLive, id));
        };
        /**
         * Setup an expectation that delegates request forwarding to a server-side
         * class implementing the MockServer ExpectationForwardCallback interface
         * (a forward "class callback"). Like respondWithClassCallback this is a
         * pure-JSON, REST-only mechanism and the referenced class must be on the
         * MockServer classpath.
         *, for example:
         *
         *   client.forwardWithClassCallback('/somePath', 'com.example.MyForwardCallback');
         *
         * @param requestMatcher the path to match (string) or a full request matcher object
         * @param callbackClass  the fully-qualified server-side callback class name, or a full
         *                       httpForwardClassCallback action object ({ callbackClass, delay?, primary? })
         * @param times the number of times the requestMatcher should be matched (optional)
         * @param priority the priority with which this expectation is used to match requests (optional)
         * @param timeToLive the time this expectation should be used to match requests (optional)
         * @param id the unique expectation id (optional)
         */
        var forwardWithClassCallback = function (requestMatcher, callbackClass, times, priority, timeToLive, id) {
            return mockAnyResponse(createAdvancedResponseExpectation("httpForwardClassCallback", requestMatcher, createClassCallbackAction(callbackClass), times, priority, timeToLive, id));
        };
        /**
         * Setup an expectation without having to specify the full expectation object
         *, for example:
         *
         *   client.mockSimpleResponse('/somePath', { name: 'value' }, 203);
         *
         * @param path the path to match requests against
         * @param responseBody the response body to return if a request matches
         * @param statusCode the response code to return if a request matches
         */
        var mockSimpleResponse = function (path, responseBody, statusCode) {
            return mockAnyResponse(createExpectation(path, responseBody, statusCode));
        };
        /**
         * Start building an expectation with a fluent, chainable API that mirrors
         * the Java client's `MockServerClient.when(...)` / `ForwardChainExpectation`.
         * Returns a chain object whose terminal methods (`.respond`, `.forward`,
         * `.error`, `.callback`, `.forwardCallback`) finish building the expectation
         * and send it to the MockServer using the same path as `mockAnyResponse`.
         *
         *, for example:
         *
         *   client.when({ path: '/somePath' })
         *       .respond({ statusCode: 200, body: 'some_response_body' });
         *
         *   // with times / timeToLive / priority (positional, like the Java client):
         *   client.when({ path: '/somePath' }, 2, { timeToLive: 60, timeUnit: 'SECONDS' }, 10)
         *       .respond({ statusCode: 201 });
         *
         *   // or build fluently before choosing the action:
         *   client.when({ path: '/somePath' })
         *       .withTimes(2)
         *       .withPriority(10)
         *       .withId('my-id')
         *       .forward({ host: 'localhost', port: 8081 });
         *
         * The returned chain exposes:
         *   .withTimes(times)            number, or a full { remainingTimes, unlimited } object
         *   .withTimeToLive(timeToLive)  a full timeToLive object
         *   .withPriority(priority)      number (higher matches first)
         *   .withId(id)                  the unique expectation id
         *   .respond(response)           an httpResponse object, a template, or a class-callback action
         *   .forward(forward)            an httpForward object, a template, or a class-callback action
         *   .error(error)                an httpError action
         *   .callback(requestHandler)    a local JS response callback (over the callback WebSocket)
         *   .forwardCallback(handler)    a local JS forward callback (over the callback WebSocket)
         *
         * Each terminal method returns the same promise-like result as the
         * existing builders, so it can be awaited or used with `.then(...)`.
         *
         * @param requestMatcher the path to match (string) or a full request matcher object
         * @param times the number of times the requestMatcher should be matched (optional)
         * @param timeToLive the time this expectation should be used to match requests (optional)
         * @param priority the priority with which this expectation is used to match requests, high first (optional)
         */
        var when = function (requestMatcher, times, timeToLive, priority) {
            var state = {
                requestMatcher: requestMatcher,
                times: times,
                timeToLive: timeToLive,
                priority: priority,
                id: undefined,
                metadata: undefined
            };
            // Detect which top-level action property a respond/forward action maps
            // to, mirroring the overloads of ForwardChainExpectation.respond/forward.
            // A plain object with a `callbackClass` is a class callback; one with a
            // `template`/`templateType` is a template; otherwise the natural action.
            var responseActionProperty = function (action) {
                if (action && typeof action === "object") {
                    if (typeof action.callbackClass === "string") {
                        return "httpResponseClassCallback";
                    }
                    if (typeof action.template === "string" || typeof action.templateType === "string") {
                        return "httpResponseTemplate";
                    }
                }
                return "httpResponse";
            };
            var forwardActionProperty = function (action) {
                if (action && typeof action === "object") {
                    if (typeof action.callbackClass === "string") {
                        return "httpForwardClassCallback";
                    }
                    if (typeof action.template === "string" || typeof action.templateType === "string") {
                        return "httpForwardTemplate";
                    }
                    if (typeof action.httpRequest === "object" || typeof action.requestOverride === "object" || typeof action.requestModifier === "object") {
                        return "httpOverrideForwardedRequest";
                    }
                }
                return "httpForward";
            };
            var send = function (actionProperty, action) {
                return mockAnyResponse(_withExpectationMetadata(createAdvancedResponseExpectation(actionProperty, state.requestMatcher, action, state.times, state.priority, state.timeToLive, state.id), state.metadata));
            };
            var chain = {
                withTimes: function (times) {
                    state.times = times;
                    return chain;
                },
                withTimeToLive: function (timeToLive) {
                    state.timeToLive = timeToLive;
                    return chain;
                },
                withPriority: function (priority) {
                    state.priority = priority;
                    return chain;
                },
                withId: function (id) {
                    state.id = id;
                    return chain;
                },
                /**
                 * withMetadata(key, value) adds (or replaces) one metadata entry; withMetadata(object)
                 * sets all metadata, replacing any already set, and null or {} removes it.
                 */
                withMetadata: function (keyOrMetadata, value) {
                    if (typeof keyOrMetadata === "string") {
                        if (_isBlankString(keyOrMetadata)) {
                            throw new Error("the metadata key must be a non-blank string");
                        }
                        if (value === undefined) {
                            throw new Error("the metadata value for \"" + keyOrMetadata + "\" must not be undefined, use null for a JSON null");
                        }
                        state.metadata = Object.assign({}, state.metadata);
                        state.metadata[keyOrMetadata] = value;
                    } else if (keyOrMetadata === null || keyOrMetadata === undefined) {
                        state.metadata = undefined;
                    } else {
                        var metadata = _requireMetadataObject(keyOrMetadata);
                        state.metadata = Object.keys(metadata).length > 0 ? Object.assign({}, metadata) : undefined;
                    }
                    return chain;
                },
                /**
                 * Finish the expectation with a response action and send it.
                 * The action property is auto-detected from the object shape:
                 * an object with a string `template`/`templateType` becomes an
                 * httpResponseTemplate, one with a string `callbackClass` becomes
                 * an httpResponseClassCallback, otherwise a plain httpResponse.
                 *
                 * @param response an httpResponse object, a response template, or a class-callback action
                 */
                respond: function (response) {
                    return send(responseActionProperty(response), response);
                },
                /**
                 * Finish the expectation with a forward action and send it. The
                 * action property is auto-detected from the object shape: a string
                 * `template`/`templateType` becomes an httpForwardTemplate, a
                 * `callbackClass` becomes an httpForwardClassCallback, an object
                 * carrying an `httpRequest`/`requestOverride`/`requestModifier`
                 * becomes an httpOverrideForwardedRequest, otherwise a plain
                 * httpForward.
                 *
                 * @param forward an httpForward object, a forward template, a class-callback, or an override-forwarded-request action
                 */
                forward: function (forward) {
                    return send(forwardActionProperty(forward), forward);
                },
                error: function (error) {
                    return send("httpError", error);
                },
                callback: function (requestHandler) {
                    return mockWithCallback(state.requestMatcher, requestHandler, state.times, state.priority, state.timeToLive, state.id, state.metadata);
                },
                forwardCallback: function (forwardHandler) {
                    return mockWithForwardCallback(state.requestMatcher, forwardHandler, state.times, state.priority, state.timeToLive, state.id, state.metadata);
                }
            };
            return chain;
        };
        /**
         * Setup an expectation that responds with a Server-Sent Events (SSE) stream
         *, for example:
         *
         *   client.respondWithSse('/events', {
         *       events: [
         *           { event: 'message', data: 'first' },
         *           { event: 'message', data: 'second' }
         *       ],
         *       closeConnection: true
         *   });
         *
         * @param requestMatcher the path to match (string) or a full request matcher object
         * @param sseResponse the SSE response action (httpSseResponse), with events/headers/statusCode/closeConnection
         * @param times the number of times the requestMatcher should be matched (optional)
         * @param priority the priority with which this expectation is used to match requests (optional)
         * @param timeToLive the time this expectation should be used to match requests (optional)
         * @param id the unique expectation id (optional)
         */
        var respondWithSse = function (requestMatcher, sseResponse, times, priority, timeToLive, id) {
            return mockAnyResponse(createAdvancedResponseExpectation("httpSseResponse", requestMatcher, sseResponse, times, priority, timeToLive, id));
        };
        /**
         * Setup an expectation that responds over a WebSocket connection
         *, for example:
         *
         *   client.respondWithWebSocket('/ws', {
         *       messages: [
         *           { text: 'hello' }
         *       ],
         *       closeConnection: false
         *   });
         *
         * @param requestMatcher the path to match (string) or a full request matcher object
         * @param webSocketResponse the WebSocket response action (httpWebSocketResponse), with messages/matchers/subprotocol/closeConnection
         * @param times the number of times the requestMatcher should be matched (optional)
         * @param priority the priority with which this expectation is used to match requests (optional)
         * @param timeToLive the time this expectation should be used to match requests (optional)
         * @param id the unique expectation id (optional)
         */
        var respondWithWebSocket = function (requestMatcher, webSocketResponse, times, priority, timeToLive, id) {
            return mockAnyResponse(createAdvancedResponseExpectation("httpWebSocketResponse", requestMatcher, webSocketResponse, times, priority, timeToLive, id));
        };
        /**
         * Setup an expectation that responds to a DNS query
         *, for example:
         *
         *   client.respondWithDns('example.com', {
         *       answerRecords: [
         *           { name: 'example.com', type: 'A', ttl: 300, value: '127.0.0.1' }
         *       ],
         *       responseCode: 'NOERROR'
         *   });
         *
         * @param requestMatcher the path to match (string) or a full request matcher object
         * @param dnsResponse the DNS response action (dnsResponse), with answerRecords/authorityRecords/additionalRecords/responseCode
         * @param times the number of times the requestMatcher should be matched (optional)
         * @param priority the priority with which this expectation is used to match requests (optional)
         * @param timeToLive the time this expectation should be used to match requests (optional)
         * @param id the unique expectation id (optional)
         */
        var respondWithDns = function (requestMatcher, dnsResponse, times, priority, timeToLive, id) {
            return mockAnyResponse(createAdvancedResponseExpectation("dnsResponse", requestMatcher, dnsResponse, times, priority, timeToLive, id));
        };
        /**
         * Setup an expectation that responds with raw binary data
         *, for example:
         *
         *   client.respondWithBinary('/binary', {
         *       binaryData: Buffer.from('hello').toString('base64')
         *   });
         *
         * @param requestMatcher the path to match (string) or a full request matcher object
         * @param binaryResponse the binary response action (binaryResponse), with binaryData (base64-encoded) and optional delay
         * @param times the number of times the requestMatcher should be matched (optional)
         * @param priority the priority with which this expectation is used to match requests (optional)
         * @param timeToLive the time this expectation should be used to match requests (optional)
         * @param id the unique expectation id (optional)
         */
        var respondWithBinary = function (requestMatcher, binaryResponse, times, priority, timeToLive, id) {
            return mockAnyResponse(createAdvancedResponseExpectation("binaryResponse", requestMatcher, binaryResponse, times, priority, timeToLive, id));
        };
        /**
         * Setup an expectation that responds with a gRPC server-streaming response
         *, for example:
         *
         *   client.respondWithGrpcStream('/my.Service/StreamItems', {
         *       statusName: 'OK',
         *       messages: [
         *           { json: '{"value":"first"}' },
         *           { json: '{"value":"second"}' }
         *       ],
         *       closeConnection: true
         *   });
         *
         * @param requestMatcher the path to match (string) or a full request matcher object
         * @param grpcStreamResponse the gRPC stream response action (grpcStreamResponse), with messages/statusName/statusMessage/headers/closeConnection
         * @param times the number of times the requestMatcher should be matched (optional)
         * @param priority the priority with which this expectation is used to match requests (optional)
         * @param timeToLive the time this expectation should be used to match requests (optional)
         * @param id the unique expectation id (optional)
         */
        var respondWithGrpcStream = function (requestMatcher, grpcStreamResponse, times, priority, timeToLive, id) {
            return mockAnyResponse(createAdvancedResponseExpectation("grpcStreamResponse", requestMatcher, grpcStreamResponse, times, priority, timeToLive, id));
        };
        var simplifyVerificationError = function (message) {
            if (typeof message === "string") {
                var idx = message.indexOf(", expected:<");
                if (idx !== -1) {
                    return message.substring(0, idx);
                }
            }
            return message;
        };
        /**
         * Issue a verification request and adapt the result to the callback /
         * promise contract shared by every verify* method.
         *
         * A FAILED verification MUST stay failed for every consumption style.
         * `makeRequest` rejects with the failure message, and that rejection is
         * handled here in one of two ways:
         *
         *  - an error callback was supplied (explicitly, or implicitly by
         *    `await`, which passes its own reject handler) -> hand the failure
         *    to it, exactly as before;
         *  - no error callback was supplied (e.g. a single-argument
         *    `.then(onSuccess)`) -> re-throw.
         *
         * Returning normally in the second case would convert a FAILED
         * verification into a passing one, silently turning a user's test suite
         * green while proving nothing.
         *
         * What the re-throw surfaces as depends on the transport. Under Node
         * `makeRequest` is a real (Q) promise, so it becomes a rejection. In the
         * browser `makeRequest` is a hand-rolled thenable whose `then` returns
         * `undefined` and whose handlers run synchronously from the XHR `load`
         * listener, so it becomes an uncaught exception there rather than a
         * catchable rejection. Loud in both cases, which is the point.
         */
        var verificationRequest = function (path, body, sucess, error) {
            return makeRequest(host, port, path, body).then(
                function () {
                    if (sucess) {
                        sucess();
                    }
                },
                function (result) {
                    var failure = simplifyVerificationError(result);
                    if (error) {
                        error(failure);
                        return;
                    }
                    throw failure;
                }
            );
        };
        /**
         * Verify a request has been sent, for example:
         *
         *   expect(client.verify({
         *       'httpRequest': {
         *           'method': 'POST',
         *           'path': '/somePath'
         *       }
         *   })).toBeTruthy();
         *
         * @param request the http request that must be matched for this verification to pass
         * @param atLeast the minimum number of times this request must be matched
         * @param atMost  the maximum number of times this request must be matched
         */
        var verify = function (request, atLeast, atMost) {
            if (atLeast === undefined && atMost === undefined) {
                atLeast = 1;
            }
            return {
                then: function (sucess, error) {
                    request.headers = headersUniqueConcatenate(request.headers, defaultRequestHeaders);
                    return verificationRequest("/mockserver/verify", {
                        "httpRequest": request,
                        "times": {
                            "atLeast": atLeast,
                            "atMost": atMost
                        }
                    }, sucess, error);
                }
            };
        };
        /**
         * Verify a request has been sent by expectation id, for example:
         *
         *   expect(client.verify({
         *           'id': '31e4ca35-66c6-4645-afeb-6e66c4ca0559'
         *       })).toBeTruthy();
         *
         * @param expectationId the expectation id that must be matched for this verification to pass
         * @param atLeast the minimum number of times this request must be matched
         * @param atMost  the maximum number of times this request must be matched
         */
        var verifyById = function (expectationId, atLeast, atMost) {
            if (atLeast === undefined && atMost === undefined) {
                atLeast = 1;
            }
            return {
                then: function (sucess, error) {
                    return verificationRequest("/mockserver/verify", {
                        "expectationId": expectationId,
                        "times": {
                            "atLeast": atLeast,
                            "atMost": atMost
                        }
                    }, sucess, error);
                }
            };
        };
        /**
         * Verify a sequence of requests has been sent, for example:
         *
         *   client.verifySequence(
         *       {
         *          'method': 'POST',
         *          'path': '/first_request'
         *       },
         *       {
         *          'method': 'POST',
         *          'path': '/second_request'
         *       },
         *       {
         *          'method': 'POST',
         *          'path': '/third_request'
         *       }
         *   );
         *
         * @param arguments the list of http requests that must be matched for this verification to pass
         */
        var verifySequence = function () {
            var requestSequence = [];
            for (var i = 0; i < arguments.length; i++) {
                var requestMatcher = arguments[i];
                requestMatcher.headers = headersUniqueConcatenate(requestMatcher.headers, defaultRequestHeaders);
                requestSequence.push(requestMatcher);
            }
            return {
                then: function (sucess, error) {
                    return verificationRequest("/mockserver/verifySequence", {
                        "httpRequests": requestSequence
                    }, sucess, error);
                }
            };
        };
        /**
         * Verify a sequence of requests has been sent by match the expectation id, for example:
         *
         *   client.verifySequenceById(
         *       {
         *           'id': '31e4ca35-66c6-4645-afeb-6e66c4ca0559'
         *       },
         *       {
         *           'id': '66c6ca35-ca35-66f5-8feb-5e6ac7ca0559'
         *       },
         *       {
         *           'id': 'ca3531e4-23c8-ff45-88f5-4ca0c7ca0559'
         *       }
         *   );
         *
         * @param arguments the list of expectation ids used to match requests for this verification to pass
         */
        var verifySequenceById = function () {
            /**
             * Convert to array, because otherwise it JSON-encoded as object
             */
            var expectationIds = Array.from(arguments);
            return {
                then: function (sucess, error) {
                    return verificationRequest("/mockserver/verifySequence", {
                        "expectationIds": expectationIds
                    }, sucess, error);
                }
            };
        };
        /**
         * Verify a response has been received, for example:
         *
         *   await client.verifyResponse({ 'statusCode': 200 }, 1, 1);
         *
         * @param responseMatcher the http response matcher that must be matched for this verification to pass
         * @param atLeast the minimum number of times this response must be matched
         * @param atMost  the maximum number of times this response must be matched
         */
        var verifyResponse = function (responseMatcher, atLeast, atMost) {
            if (atLeast === undefined && atMost === undefined) {
                atLeast = 1;
            }
            return {
                then: function (sucess, error) {
                    return verificationRequest("/mockserver/verify", {
                        "httpResponse": responseMatcher,
                        "times": {
                            "atLeast": atLeast,
                            "atMost": atMost
                        }
                    }, sucess, error);
                }
            };
        };
        /**
         * Verify a request and response pair has been exchanged, for example:
         *
         *   await client.verifyRequestAndResponse(
         *       { 'method': 'POST', 'path': '/somePath' },
         *       { 'statusCode': 200 },
         *       1, 1
         *   );
         *
         * @param requestMatcher the http request matcher that must be matched for this verification to pass
         * @param responseMatcher the http response matcher that must be matched for this verification to pass
         * @param atLeast the minimum number of times this request/response pair must be matched
         * @param atMost  the maximum number of times this request/response pair must be matched
         */
        var verifyRequestAndResponse = function (requestMatcher, responseMatcher, atLeast, atMost) {
            if (atLeast === undefined && atMost === undefined) {
                atLeast = 1;
            }
            return {
                then: function (sucess, error) {
                    requestMatcher.headers = headersUniqueConcatenate(requestMatcher.headers, defaultRequestHeaders);
                    return verificationRequest("/mockserver/verify", {
                        "httpRequest": requestMatcher,
                        "httpResponse": responseMatcher,
                        "times": {
                            "atLeast": atLeast,
                            "atMost": atMost
                        }
                    }, sucess, error);
                }
            };
        };
        /**
         * Verify a sequence of request and response pairs has been exchanged, for example:
         *
         *   await client.verifySequenceWithResponses([
         *       { request: { 'method': 'POST', 'path': '/first' }, response: { 'statusCode': 201 } },
         *       { request: { 'method': 'GET', 'path': '/second' }, response: { 'statusCode': 200 } }
         *   ]);
         *
         * @param requestsAndResponses array of {request, response} objects, index-aligned
         */
        var verifySequenceWithResponses = function (requestsAndResponses) {
            var requestSequence = [];
            var responseSequence = [];
            for (var i = 0; i < requestsAndResponses.length; i++) {
                var pair = requestsAndResponses[i];
                var requestMatcher = pair.request || {};
                requestMatcher.headers = headersUniqueConcatenate(requestMatcher.headers, defaultRequestHeaders);
                requestSequence.push(requestMatcher);
                responseSequence.push(pair.response || {});
            }
            return {
                then: function (sucess, error) {
                    return verificationRequest("/mockserver/verifySequence", {
                        "httpRequests": requestSequence,
                        "httpResponses": responseSequence
                    }, sucess, error);
                }
            };
        };
        /**
         * Verify that no requests have been received by the MockServer
         */
        var verifyZeroInteractions = function () {
            return verify({}, 0, 0);
        };
        /**
         * Close the callback WebSockets the clients of this MockServer have opened in this process (for
         * mockWithCallback, the callback and forwardCallback actions, and breakpoints), so they no longer
         * keep the process running. Sends nothing to MockServer, whose expectations and breakpoints that
         * use them stop working. A later callback or breakpoint opens a new WebSocket.
         *
         * @return a promise resolved once the WebSockets have closed
         */
        var close = function () {
            return closeCallbackWebSockets();
        };
        /**
         * Reset by clearing all recorded requests, expectations and breakpoints, and close the callback
         * WebSockets the clients of this MockServer have opened in this process, as close() does
         */
        var reset = function () {
            closeCallbackWebSockets().then(undefined, function () {
            });
            return makeRequest(host, port, "/mockserver/reset");
        };
        /**
         * Clear all recorded requests, expectations and logs that match the specified path
         *
         * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path to
         *                              decide what to clear, however if an object is passed
         *                              in the value will be treated as a full request matcher object
         * @param type                  the type to clear 'EXPECTATIONS', 'LOG' or 'ALL', defaults to 'ALL' if not specified,
         *                              or an expectation filter object ({ metadataKey, metadataValue, metadataJsonPath,
         *                              unmatched }) to clear only the expectations it selects, leaving the log untouched;
         *                              a filter that selects nothing, or pages, is rejected before anything is sent.
         *                              A null or undefined type clears everything, as it always has; to clear with a
         *                              filter use clearWithFilter, which refuses a missing filter
         */
        var clear = function (pathOrRequestMatcher, type) {
            if (type !== null && typeof type === "object") {
                var filterQuery = _expectationFilterQuery(type, true);
                return makeRequest(host, port, "/mockserver/clear?type=expectations" + filterQuery, addDefaultRequestMatcherHeaders(pathOrRequestMatcher));
            }
            if (type) {
                var typeEnum = ['EXPECTATIONS', 'LOG', 'ALL'];
                if (typeEnum.indexOf(type) === -1) {
                    throw new Error("\"" + (type || "undefined") + "\" is not a supported value for \"type\" parameter only " + typeEnum + " are allowed values");
                }
            }
            return makeRequest(host, port, "/mockserver/clear" + (type ? "?type=" + type : ""), addDefaultRequestMatcherHeaders(pathOrRequestMatcher));
        };
        /**
         * Clear only the expectations that match both the request matcher and the filter ({ metadataKey,
         * metadataValue, metadataJsonPath, unmatched }); the event log is left untouched. Unlike clear, a null,
         * undefined or non-object filter throws before anything is sent, so it can never become a clear of
         * everything.
         */
        var clearWithFilter = function (pathOrRequestMatcher, filter) {
            if (filter === null || typeof filter !== "object" || Array.isArray(filter)) {
                throw new Error("clearWithFilter requires an expectation filter object, but found: " + (filter === undefined ? "undefined" : JSON.stringify(filter)));
            }
            return clear(pathOrRequestMatcher, filter);
        };
        /**
         * Clear expectations, logs or both that match the expectation id
         *
         * @param expectationId         the expectation id that is used to clear expectations and logs
         * @param type                  the type to clear 'EXPECTATIONS', 'LOG' or 'ALL', defaults to 'ALL' if not specified
         */
        var clearById = function (expectationId, type) {
            if (type) {
                var typeEnum = ['EXPECTATIONS', 'LOG', 'ALL'];
                if (typeEnum.indexOf(type) === -1) {
                    throw new Error("\"" + (type || "undefined") + "\" is not a supported value for \"type\" parameter only " + typeEnum + " are allowed values");
                }
            }
            return makeRequest(host, port, "/mockserver/clear" + (type ? "?type=" + type : ""), { id: expectationId});
        };
        /**
         * Clear the expectations whose metadata has key with a value equal to value (a string is compared as
         * text, any other value as JSON). The event log is left untouched.
         */
        var clearByMetadata = function (key, value) {
            return clearWithFilter(undefined, {metadataKey: key, metadataValue: value});
        };
        /**
         * Clear the expectations whose metadata has key, whatever its value. The event log is left untouched.
         */
        var clearByMetadataKey = function (key) {
            return clearWithFilter(undefined, {metadataKey: key});
        };
        /**
         * Clear the expectations that have never matched a request. The event log is left untouched.
         */
        var clearUnmatchedExpectations = function () {
            return clearWithFilter(undefined, {unmatched: true});
        };
        /**
         * Add new ports the server is bound to and listening on
         *
         * @param ports array of ports to bind to, use 0 to bind to any free port
         */
        var bind = function (ports) {
            if (!Array.isArray(ports)) {
                throw new Error("ports parameter must be an array but found: " + JSON.stringify(ports));
            }
            return makeRequest(host, port, "/mockserver/bind", {ports: ports});
        };
        /**
         * Retrieve the recorded requests that match the parameter, as follows:
         * - use a string value to match on path,
         * - use a request matcher object to match on a full request,
         * - or use null to retrieve all requests
         *
         * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path, however
         *                              if an object is passed in the value will be treated as a full request
         *                              matcher object, if null is passed in it will be treated as match all
         */
        var retrieveRecordedRequests = function (pathOrRequestMatcher) {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/retrieve?type=REQUESTS&format=JSON", addDefaultRequestMatcherHeaders(pathOrRequestMatcher))
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve the recorded requests and their responses that match the parameter:
         * - use a string value to match on path,
         * - use a request matcher object to match on a full request,
         * - or use null to retrieve all requests
         *
         * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path, however
         *                              if an object is passed in the value will be treated as a full request
         *                              matcher object, if null is passed in it will be treated as match all
         */
        var retrieveRecordedRequestsAndResponses = function (pathOrRequestMatcher) {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/retrieve?type=REQUEST_RESPONSES&format=JSON", addDefaultRequestMatcherHeaders(pathOrRequestMatcher))
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        var retrieveRecordedRequestsAndResponsesAsHar = function (pathOrRequestMatcher) {
            return {
                then: function (success, error) {
                    makeRequest(host, port, "/mockserver/retrieve?type=REQUEST_RESPONSES&format=HAR", addDefaultRequestMatcherHeaders(pathOrRequestMatcher))
                        .then(function (result) {
                            success(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve the active expectations that match the parameter,
         * the expectations are retrieved by matching the parameter
         * on the expectations own request matcher, as follows:
         * - use a string value to match on path,
         * - use a request matcher object to match on a full request,
         * - or use null to retrieve all requests
         *
         * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path, however
         *                              if an object is passed in the value will be treated as a full request
         *                              matcher object, if null is passed in it will be treated as match all
         * @param filter                optional expectation filter object ({ metadataKey, metadataValue, metadataJsonPath,
         *                              unmatched, limit, offset }); an invalid filter is rejected before anything is sent
         */
        var retrieveActiveExpectations = function (pathOrRequestMatcher, filter) {
            var filterQuery = (filter === undefined || filter === null) ? "" : _expectationFilterQuery(filter, false);
            return {
                then: function (sucess, error) {
                    return makeRequest(host, port, "/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=JSON" + filterQuery, addDefaultRequestMatcherHeaders(pathOrRequestMatcher))
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve the request-response pairs as expectations that match the
         * parameter, expectations are retrieved by matching the parameter
         * on the expectations own request matcher, as follows:
         * - use a string value to match on path,
         * - use a request matcher object to match on a full request,
         * - or use null to retrieve all requests
         *
         * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path, however
         *                              if an object is passed in the value will be treated as a full request
         *                              matcher object, if null is passed in it will be treated as match all
         * @param offset                optional number of recorded expectations to skip, an integer zero or more,
         *                              or an options object ({ consolidate, parameterize, repeatsAsScenarios,
         *                              captureHeaders, extractBodiesLargerThan }) that shapes the returned
         *                              expectations; invalid options are rejected before anything is sent
         * @param limit                 optional maximum number to return, an integer zero or more
         */
        var retrieveRecordedExpectations = function (pathOrRequestMatcher, offset, limit) {
            var query;
            if (offset !== null && typeof offset === 'object') {
                if (limit !== undefined) {
                    throw new Error("limit cannot be combined with a recorded expectations options object");
                }
                query = _recordedExpectationsOptionsQuery(offset);
            } else {
                query = _expectationFilterQuery({offset: offset, limit: limit}, false);
            }
            return {
                then: function (sucess, error) {
                    return makeRequest(host, port, "/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON" + query, addDefaultRequestMatcherHeaders(pathOrRequestMatcher))
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve the active expectations whose metadata has key with a value equal to value (a string is
         * compared as text, any other value as JSON).
         */
        var retrieveActiveExpectationsByMetadata = function (key, value) {
            return retrieveActiveExpectations(undefined, {metadataKey: key, metadataValue: value});
        };
        /**
         * Retrieve the active expectations whose metadata has key, whatever its value.
         */
        var retrieveActiveExpectationsByMetadataKey = function (key) {
            return retrieveActiveExpectations(undefined, {metadataKey: key});
        };
        /**
         * Retrieve the active expectations that have never matched a request.
         */
        var retrieveUnmatchedExpectations = function () {
            return retrieveActiveExpectations(undefined, {unmatched: true});
        };
        /**
         * Retrieve the active expectations as MockServer SDK setup code (the
         * builder code that recreates the expectations) in the requested
         * language, instead of as JSON. The result is the generated code string.
         *
         * @param format                the code-generation language, one of "java",
         *                              "javascript", "python", "go", "csharp",
         *                              "ruby", "rust" or "php" (case-insensitive)
         * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path, however
         *                              if an object is passed in the value will be treated as a full request
         *                              matcher object, if null is passed in it will be treated as match all
         */
        var retrieveExpectationsAsCode = function (format, pathOrRequestMatcher) {
            return {
                then: function (sucess, error) {
                    return makeRequest(host, port, "/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=" + encodeURIComponent((format || "JAVA").toUpperCase()), addDefaultRequestMatcherHeaders(pathOrRequestMatcher))
                        .then(function (result) {
                            sucess(result.body);
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve the recorded (proxied) request-response pairs as MockServer
         * SDK setup code (the builder code that recreates the expectations) in
         * the requested language, instead of as JSON. The result is the
         * generated code string.
         *
         * @param format                the code-generation language, one of "java",
         *                              "javascript", "python", "go", "csharp",
         *                              "ruby", "rust" or "php" (case-insensitive)
         * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path, however
         *                              if an object is passed in the value will be treated as a full request
         *                              matcher object, if null is passed in it will be treated as match all
         */
        var retrieveRecordedExpectationsAsCode = function (format, pathOrRequestMatcher) {
            return {
                then: function (sucess, error) {
                    return makeRequest(host, port, "/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=" + encodeURIComponent((format || "JAVA").toUpperCase()), addDefaultRequestMatcherHeaders(pathOrRequestMatcher))
                        .then(function (result) {
                            sucess(result.body);
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve logs messages for expectation matching, verification, clearing, etc,
         * log messages are filtered by request matcher as follows:
         * - use a string value to match on path,
         * - use a request matcher object to match on a full request,
         * - or use null to retrieve all requests
         *
         * @param pathOrRequestMatcher  if a string is passed in the value will be treated as the path, however
         *                              if an object is passed in the value will be treated as a full request
         *                              matcher object, if null is passed in it will be treated as match all
         */
        var retrieveLogMessages = function (pathOrRequestMatcher) {
            return {
                then: function (sucess, error) {
                    return makeRequest(host, port, "/mockserver/retrieve?type=LOGS", addDefaultRequestMatcherHeaders(pathOrRequestMatcher))
                        .then(function (result) {
                            sucess(result.body && result.body.split("------------------------------------"));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };

        /**
         * Retrieve recorded requests, request-responses, expectations or log messages by
         * expectation id, instead of by request matcher.
         *
         * For type 'ACTIVE_EXPECTATIONS' only the expectation with that id is returned; for
         * every other type the entries matching that expectation's request are returned,
         * exactly as verify by expectation id matches them. An unknown id is rejected by
         * MockServer with a 400.
         *
         * @param typeAndFormat         the retrieve query string, e.g. "type=REQUESTS&format=JSON"
         * @param expectationId         the expectation id to retrieve for
         */
        var retrieveById = function (typeAndFormat, expectationId) {
            return makeRequest(host, port, "/mockserver/retrieve?" + typeAndFormat, {id: expectationId});
        };
        /**
         * Retrieve the recorded requests that match the request of the expectation with the given id
         *
         * @param expectationId         the expectation id whose request is matched against each recorded request
         */
        var retrieveRecordedRequestsById = function (expectationId) {
            return {
                then: function (sucess, error) {
                    return retrieveById("type=REQUESTS&format=JSON", expectationId)
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve the recorded requests and their responses that match the request of the
         * expectation with the given id
         *
         * @param expectationId         the expectation id whose request is matched against each recorded request
         */
        var retrieveRecordedRequestsAndResponsesById = function (expectationId) {
            return {
                then: function (sucess, error) {
                    return retrieveById("type=REQUEST_RESPONSES&format=JSON", expectationId)
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve the active expectation with the given id
         *
         * @param expectationId         the expectation id to retrieve
         */
        var retrieveActiveExpectationsById = function (expectationId) {
            return {
                then: function (sucess, error) {
                    return retrieveById("type=ACTIVE_EXPECTATIONS&format=JSON", expectationId)
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve, as expectations, the request-response pairs recorded for the request of the
         * expectation with the given id
         *
         * @param expectationId         the expectation id whose request is matched against each recorded expectation
         */
        var retrieveRecordedExpectationsById = function (expectationId) {
            return {
                then: function (sucess, error) {
                    return retrieveById("type=RECORDED_EXPECTATIONS&format=JSON", expectationId)
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve the log messages for the request of the expectation with the given id
         *
         * @param expectationId         the expectation id whose request is matched against each log message
         */
        var retrieveLogMessagesById = function (expectationId) {
            return {
                then: function (sucess, error) {
                    return retrieveById("type=LOGS", expectationId)
                        .then(function (result) {
                            sucess(result.body && result.body.split("------------------------------------"));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // Diagnostics (explain / debugMismatch / explainUnmatched)
        // -------------------------------------------------------------------
        // Resolves with the parsed 200 body; any other status, or a body that is
        // not JSON, rejects with failurePrefix + the server's {error} or the body.
        var diagnosticRequest = function (path, body, failurePrefix) {
            return {
                then: function (sucess, error) {
                    makeRawRequest(host, port, path, body)
                        .then(function (result) {
                            var parsed;
                            try {
                                parsed = result.body ? JSON.parse(result.body) : {};
                            } catch (e) {
                                parsed = null;
                            }
                            if (result.statusCode !== 200 || parsed === null) {
                                if (error) {
                                    error(failurePrefix + ((parsed && parsed.error) || result.body || ("status " + result.statusCode)));
                                }
                                return;
                            }
                            if (sucess) {
                                sucess(parsed);
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };
        /**
         * Explain how a recorded request would be served now (PUT /mockserver/explain):
         * re-checks it against the current expectations, in the order MockServer
         * evaluates them, and resolves with the RequestExplanation: the expectation
         * that would serve it, why each expectation ahead of that one was passed
         * over, and which lower-priority expectations it shadows. Read-only: it
         * consumes no Times and moves no scenario.
         *
         * Rejects with the server's reason when no request is recorded with this
         * correlation id (never received, cleared or evicted) or the recorded
         * request is not an HTTP request.
         *
         * @param correlationId the correlation id of the request's log entries
         * @return promise resolving to the parsed RequestExplanation
         */
        var explain = function (correlationId) {
            if (typeof correlationId !== "string" || !correlationId.trim()) {
                throw new Error("explain(correlationId) requires a non blank correlationId");
            }
            return diagnosticRequest("/mockserver/explain?correlationId=" + encodeURIComponent(correlationId), "",
                "explain failed for correlationId \"" + correlationId + "\": ");
        };
        /**
         * Analyse why a request does or does not match each active expectation
         * (PUT /mockserver/debugMismatch). Resolves with the per-expectation
         * results (field-level differences) and the closest match. Only an
         * HttpRequest definition is supported; an OpenAPI definition rejects.
         *
         * @param requestDefinition the request to check against the active expectations
         * @return promise resolving to the parsed DebugMismatchResult
         */
        var debugMismatch = function (requestDefinition) {
            if (requestDefinition === undefined || requestDefinition === null) {
                throw new Error("debugMismatch(requestDefinition) requires a non null requestDefinition");
            }
            return diagnosticRequest("/mockserver/debugMismatch", requestDefinition, "debugMismatch failed: ");
        };
        /**
         * Explain why recent requests matched no expectation
         * (PUT /mockserver/explainUnmatched). Resolves with each unmatched request
         * and its closest expectations, closest first, with per-field differences
         * and remediation hints.
         *
         * @param options optional {limit, suggestFixes}: limit bounds how many recent
         *        unmatched requests are analysed (server default 10); suggestFixes
         *        adds a suggestedFix JSON patch to each of the five closest
         *        non-matching expectations (nothing is applied)
         * @return promise resolving to the parsed ExplainUnmatchedResult
         */
        var explainUnmatched = function (options) {
            var body = {};
            if (options && options.limit !== undefined && options.limit !== null) {
                body.limit = options.limit;
            }
            if (options && options.suggestFixes !== undefined && options.suggestFixes !== null) {
                body.suggestFixes = options.suggestFixes;
            }
            return diagnosticRequest("/mockserver/explainUnmatched", Object.keys(body).length ? body : "", "explainUnmatched failed: ");
        };

        /**
         * Freeze the server clock at the given ISO-8601 instant.
         * If instant is omitted, the clock freezes at the current real time.
         *
         * @param instant  optional ISO-8601 instant string (e.g. "2025-01-15T09:30:00Z")
         */
        var freezeClock = function (instant) {
            var body = {action: "freeze"};
            if (instant) {
                body.instant = instant;
            }
            return makeRequest(host, port, "/mockserver/clock", body);
        };
        /**
         * Advance the frozen clock by the specified number of milliseconds.
         *
         * @param durationMillis  number of milliseconds to advance
         */
        var advanceClock = function (durationMillis) {
            return makeRequest(host, port, "/mockserver/clock", {action: "advance", durationMillis: durationMillis});
        };
        /**
         * Reset the server clock to real wall-clock time.
         */
        var resetClock = function () {
            return makeRequest(host, port, "/mockserver/clock", {action: "reset"});
        };
        /**
         * Query the current clock status.
         *
         * @return promise resolving to {statusCode, body} where body contains
         *         currentInstant, currentEpochMillis, and frozen
         */
        var clockStatus = function () {
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, "/mockserver/clock")
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // Metrics
        // -------------------------------------------------------------------
        /**
         * Retrieve the JSON counter snapshot (PUT /mockserver/retrieve?type=METRICS).
         * Resolves with a flat object mapping each metric name to its long value,
         * or {} when metrics are disabled (metricsEnabled=false).
         *
         * @return promise resolving to the parsed metrics object
         */
        var retrieveMetrics = function () {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/retrieve?type=METRICS", "")
                        .then(function (result) {
                            sucess(result.body ? JSON.parse(result.body) : {});
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Scrape the Prometheus exposition text (GET /mockserver/metrics). Distinct
         * from retrieveMetrics() (the JSON snapshot) — this returns the raw
         * Prometheus/OpenMetrics text. Rejects with "404 Not Found" when metrics
         * are disabled (metricsEnabled=false).
         *
         * @return promise resolving to the Prometheus exposition text string
         */
        var scrapeMetrics = function () {
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, "/mockserver/metrics")
                        .then(function (result) {
                            sucess(result.body);
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // Configuration
        // -------------------------------------------------------------------
        /**
         * Retrieve the effective live configuration (GET /mockserver/configuration).
         *
         * @return promise resolving to the parsed Configuration object
         */
        var retrieveConfiguration = function () {
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, "/mockserver/configuration")
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Update the live configuration (PUT /mockserver/configuration). Only the
         * fields present in the supplied document are applied (partial update).
         * Properties fixed at startup, such as forwardProxyBlockPrivateNetworks,
         * forwardProxyAllowedHosts and forwardProxyDeniedHosts, are ignored and the
         * server logs a warning.
         *
         * @param configuration  a ConfigurationDTO object (or JSON string)
         * @return promise resolving to the parsed updated Configuration object
         */
        var updateConfiguration = function (configuration) {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/configuration", configuration || "")
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // Drift detection
        // -------------------------------------------------------------------
        /**
         * Retrieve the recorded mock drift report (GET /mockserver/drift).
         * The report is of the form {count: <n>, drifts: [ ... ]}, where each
         * entry describes a difference detected between a mock's configured
         * response and the live upstream response for the same request.
         *
         * @return promise resolving to the parsed drift report object
         */
        var retrieveDrift = function () {
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, "/mockserver/drift")
                        .then(function (result) {
                            sucess(result.body ? JSON.parse(result.body) : {});
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Clear all recorded mock drift (PUT /mockserver/drift/clear).
         *
         * @return a promise that is resolved once the drift is cleared
         */
        var clearDrift = function () {
            return makeRequest(host, port, "/mockserver/drift/clear");
        };

        // -------------------------------------------------------------------
        // Pact (import / export / verify)
        // -------------------------------------------------------------------
        /**
         * Import a Pact v3 contract as expectations (PUT /mockserver/pact/import).
         *
         * @param pactJson  the Pact v3 contract (object or JSON string)
         * @return promise resolving to the array of upserted expectations
         */
        var pactImport = function (pactJson) {
            if (pactJson === undefined || pactJson === null || pactJson === "") {
                throw new Error("pactImport(pactJson) requires a non null pact contract");
            }
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/pact/import", pactJson)
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Export the active expectations as a Pact v3 consumer contract
         * (PUT /mockserver/pact?consumer=&provider=). Blank consumer/provider use
         * the server defaults.
         *
         * @param consumer  optional consumer name
         * @param provider  optional provider name
         * @return promise resolving to the parsed Pact v3 contract object
         */
        var pactExport = function (consumer, provider) {
            var query = [];
            if (consumer) {
                query.push("consumer=" + encodeURIComponent(consumer));
            }
            if (provider) {
                query.push("provider=" + encodeURIComponent(provider));
            }
            var path = "/mockserver/pact" + (query.length ? "?" + query.join("&") : "");
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, path, "")
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Verify a Pact v3 contract against the active expectations
         * (PUT /mockserver/pact/verify). The server replies 202 ACCEPTED when every
         * interaction matches and 406 NOT_ACCEPTABLE otherwise; the verification
         * report body is returned in BOTH cases. This method RESOLVES with the
         * parsed report for both pass and fail (mirroring the Java client, which
         * does not throw on a FAIL verdict) — inspect the report's `verified`
         * boolean to tell them apart. Any other status (400 for a malformed
         * request, 401/403, 500) or a report that is not JSON REJECTS, with the
         * server's `error` message or the response body.
         *
         * @param pactJson  the Pact v3 contract (object or JSON string)
         * @return promise resolving to the parsed verification report
         *         ({verified: boolean, interactions: [...]})
         */
        var pactVerify = function (pactJson) {
            if (pactJson === undefined || pactJson === null || pactJson === "") {
                throw new Error("pactVerify(pactJson) requires a non null pact contract");
            }
            return {
                then: function (sucess, error) {
                    makeRawRequest(host, port, "/mockserver/pact/verify", pactJson)
                        .then(function (result) {
                            var parsed;
                            try {
                                parsed = result.body ? JSON.parse(result.body) : {};
                            } catch (e) {
                                parsed = null;
                            }
                            // 2xx = pass, 406 = fail (both carry the report); any
                            // other status rejects with its {error} or its body.
                            var isReport = (result.statusCode >= 200 && result.statusCode < 300) || result.statusCode === 406;
                            if (!isReport || parsed === null) {
                                if (error) {
                                    error((parsed && parsed.error) || result.body || ("pactVerify failed with status " + result.statusCode));
                                }
                                return;
                            }
                            if (sucess) {
                                sucess(parsed);
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // File store (store / retrieve / list / delete)
        // -------------------------------------------------------------------
        /**
         * Store a UTF-8 text file in the in-memory file store
         * (PUT /mockserver/files/store).
         *
         * @param name     the file name/key (required)
         * @param content  the file content as a UTF-8 string (required)
         * @return promise resolving to {name, size}
         */
        var storeFile = function (name, content) {
            if (!name) {
                throw new Error("storeFile(name, content) requires a non null name");
            }
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/files/store", {name: name, content: content === undefined || content === null ? "" : content})
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Store a binary file in the in-memory file store
         * (PUT /mockserver/files/store). The bytes are base64-encoded and sent with
         * "base64": true so the server stores the raw bytes verbatim.
         *
         * @param name     the file name/key (required)
         * @param content  the file content as a Buffer, Uint8Array, ArrayBuffer or
         *                 base64 string
         * @return promise resolving to {name, size}
         */
        var storeBinaryFile = function (name, content) {
            if (!name) {
                throw new Error("storeBinaryFile(name, content) requires a non null name");
            }
            var base64;
            if (typeof content === "string") {
                base64 = content;
            } else if (typeof Buffer !== "undefined") {
                base64 = Buffer.from(content || []).toString("base64");
            } else {
                throw new Error("storeBinaryFile(name, content) requires a Buffer or base64 string content in this environment");
            }
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/files/store", {name: name, content: base64, base64: true})
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve a file's content from the in-memory file store
         * (PUT /mockserver/files/retrieve). Resolves with the raw file content
         * (a string) on success. An unknown file REJECTS with an Error carrying the
         * server's not-found message ("file not found: <name>"), so a missing file
         * is caught via try/catch rather than mistaken for success. This matches
         * the other MockServer clients (Java/Go/.NET/Ruby/Rust/Python/PHP), which
         * return the content directly and signal a missing file as an error.
         *
         * @param name  the file name/key (required)
         * @return promise resolving to the file content string (200); rejects with
         *         an Error on 404 (file not found)
         */
        var retrieveFile = function (name) {
            if (!name) {
                throw new Error("retrieveFile(name) requires a non null name");
            }
            return {
                then: function (sucess, error) {
                    makeRawRequest(host, port, "/mockserver/files/retrieve", {name: name})
                        .then(function (result) {
                            if (result.statusCode >= 400) {
                                if (error) {
                                    error(new Error(result.body || ("file not found: " + name)));
                                }
                                return;
                            }
                            if (sucess) {
                                sucess(result.body);
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };
        /**
         * List the names of all files in the in-memory file store
         * (PUT /mockserver/files/list).
         *
         * @return promise resolving to an array of file-name strings
         */
        var listFiles = function () {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/files/list", "")
                        .then(function (result) {
                            sucess(result.body ? JSON.parse(result.body) : []);
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Delete a file from the in-memory file store
         * (PUT /mockserver/files/delete). An unknown file rejects with the server's
         * 404 text body.
         *
         * @param name  the file name/key (required)
         * @return promise resolving to {statusCode, body}
         */
        var deleteFile = function (name) {
            if (!name) {
                throw new Error("deleteFile(name) requires a non null name");
            }
            return makeRequest(host, port, "/mockserver/files/delete", {name: name});
        };

        // -------------------------------------------------------------------
        // Import (HAR / Postman / Pact / WireMock / Mountebank / Mockoon / auto-detect)
        // -------------------------------------------------------------------
        // the migration importers (wiremock, mountebank, mockoon) reply {"expectations":[...],"warnings":[...]}
        var importedExpectations = function (body, format) {
            var reply = body ? JSON.parse(body) : [];
            if (Array.isArray(reply) || !reply || typeof reply !== 'object') {
                return reply;
            }
            if (!Array.isArray(reply.expectations)) {
                throw new Error("import response has no expectations array - server responded: " + body);
            }
            var warnings = Array.isArray(reply.warnings) ? reply.warnings : [];
            if (warnings.length > 0) {
                console.warn("import of " + (format || "auto-detected") + " document could not fully map " + warnings.length +
                    " construct(s), check the imported expectations:\n" + warnings.map(function (warning) {
                        return "  [" + warning.item + "] " + warning.construct + ": " + warning.detail;
                    }).join("\n"));
            }
            return reply.expectations;
        };
        /**
         * Import a HAR, Postman collection, Pact contract, WireMock stub mapping,
         * Mountebank imposter or Mockoon environment as expectations
         * (PUT /mockserver/import). When format is blank the server auto-detects
         * the format from the JSON shape.
         *
         * The WireMock, Mountebank and Mockoon importers also report each foreign
         * construct they could not map faithfully; those warnings are written with
         * console.warn. format "recording" is rejected: it loads recorded
         * request/response pairs into the request log rather than adding
         * expectations, so send PUT /mockserver/import?format=recording directly.
         *
         * @param json    the document (object or JSON string, required)
         * @param format  one of "har", "postman", "pact", "wiremock", "mountebank",
         *                "mockoon", or null/blank for auto-detect
         * @return promise resolving to the array of upserted expectations
         */
        var importDocument = function (json, format) {
            if (json === undefined || json === null || json === "") {
                throw new Error("importDocument(json, format) requires a non null document");
            }
            if (typeof format === "string" && format.trim().toLowerCase() === "recording") {
                throw new Error("importDocument(json, format) does not support format=recording, which loads recorded request/response pairs into the request log rather than adding expectations - send PUT /mockserver/import?format=recording directly");
            }
            var path = "/mockserver/import" + (format ? "?format=" + encodeURIComponent(format) : "");
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, path, json)
                        .then(function (result) {
                            var expectations;
                            try {
                                expectations = importedExpectations(result.body, format);
                            } catch (err) {
                                error(err);
                                return;
                            }
                            sucess(expectations);
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Import a HAR (HTTP Archive) document as expectations
         * (PUT /mockserver/import?format=har).
         *
         * @param harJson  the HAR document (object or JSON string)
         * @return promise resolving to the array of upserted expectations
         */
        var importHar = function (harJson) {
            return importDocument(harJson, "har");
        };
        /**
         * Import a Postman collection as expectations
         * (PUT /mockserver/import?format=postman).
         *
         * @param collectionJson  the Postman collection (object or JSON string)
         * @return promise resolving to the array of upserted expectations
         */
        var importPostmanCollection = function (collectionJson) {
            return importDocument(collectionJson, "postman");
        };

        // -------------------------------------------------------------------
        // Operating mode (SIMULATE / SPY / CAPTURE)
        // -------------------------------------------------------------------
        /**
         * Set the high-level operating mode (PUT /mockserver/mode?mode=<MODE>).
         * Setting the mode also flips attemptToProxyIfNoMatchingExpectation
         * server-side.
         *
         * @param mode  one of "SIMULATE", "SPY", "CAPTURE" (case-insensitive;
         *              MockMode.SIMULATE/SPY/CAPTURE constants are also exported)
         * @return promise resolving to {mode, proxyUnmatchedRequests}
         */
        var setMode = function (mode) {
            if (!mode) {
                throw new Error("setMode(mode) requires a non null mode (one of SIMULATE, SPY, CAPTURE)");
            }
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/mode?mode=" + encodeURIComponent(String(mode).toUpperCase()), "")
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Read the current high-level operating mode (GET /mockserver/mode).
         *
         * @return promise resolving to {mode, proxyUnmatchedRequests}
         */
        var retrieveMode = function () {
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, "/mockserver/mode")
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // WSDL -> expectations
        // -------------------------------------------------------------------
        /**
         * Generate and upsert expectations from a WSDL document
         * (PUT /mockserver/wsdl). The raw WSDL XML is sent as the request body with
         * an XML content type.
         *
         * @param wsdl  the WSDL document XML (required)
         * @return promise resolving to the array of generated (upserted) expectations
         */
        var wsdlExpectation = function (wsdl) {
            if (wsdl === undefined || wsdl === null || wsdl === "") {
                throw new Error("wsdlExpectation(wsdl) requires a non null WSDL document");
            }
            return {
                then: function (sucess, error) {
                    makeRawRequest(host, port, "/mockserver/wsdl", String(wsdl), "application/xml; charset=utf-8")
                        .then(function (result) {
                            if (result.statusCode >= 400) {
                                if (error) {
                                    error(result.body);
                                }
                                return;
                            }
                            if (sucess) {
                                sucess(result.body ? JSON.parse(result.body) : []);
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };

        /**
         * Register a service-scoped HTTP chaos profile for an upstream host. The
         * profile is applied to every matched forward expectation to that host that
         * does not define its own chaos. The host is matched case-insensitively,
         * ignoring any ":port".
         *
         * @param serviceHost  the upstream host to break
         * @param chaos        the HttpChaosProfile to apply to that host
         * @param ttlMillis    optional; if set, the chaos auto-reverts after this many ms
         */
        var setServiceChaos = function (serviceHost, chaos, ttlMillis) {
            var body = {host: serviceHost, chaos: chaos};
            if (ttlMillis && ttlMillis > 0) {
                body.ttlMillis = ttlMillis;
            }
            return makeRequest(host, port, "/mockserver/serviceChaos", body);
        };
        /**
         * Remove the service-scoped chaos profile registered for the given host.
         *
         * @param serviceHost  the upstream host whose service-scoped chaos to remove
         */
        var removeServiceChaos = function (serviceHost) {
            return makeRequest(host, port, "/mockserver/serviceChaos", {host: serviceHost, remove: true});
        };
        /**
         * Clear all service-scoped chaos profiles.
         */
        var clearServiceChaos = function () {
            return makeRequest(host, port, "/mockserver/serviceChaos", {clear: true});
        };
        /**
         * Query the current service-scoped chaos registrations.
         *
         * @return promise resolving to {services: {host: profile, ...}}
         */
        var serviceChaosStatus = function () {
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, "/mockserver/serviceChaos")
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Evaluate a set of service-level objectives (SLOs) over a window of the
         * recorded SLI samples (PUT /mockserver/verifySLO). The server encodes
         * the verdict in the HTTP status: 200 for PASS or INCONCLUSIVE, 406 for
         * FAIL, 403 when SLO tracking is disabled (sloTrackingEnabled=false) and
         * 400 for malformed criteria.
         *
         * The returned promise RESOLVES with the parsed SloVerdict object
         * (result PASS or INCONCLUSIVE) on 200, and REJECTS on FAIL (406) — the
         * rejection value is a string carrying the verdict body so callers can
         * inspect the per-objective results. A 403 rejects with a message
         * explaining how to enable SLO tracking; a 400 rejects naming the
         * criteria as invalid.
         *
         * @param criteria the SLO criteria ({name, window, minimumSampleCount,
         *        upstreamHosts, objectives:[{sli, comparator, threshold, scope}]})
         * @return promise resolving to the parsed SloVerdict
         *         ({name, result, sampleCount, objectiveResults, ...})
         */
        var verifySLO = function (criteria) {
            return {
                then: function (sucess, error) {
                    return makeRequest(host, port, "/mockserver/verifySLO", criteria)
                        .then(function (result) {
                            // 2xx -> PASS or INCONCLUSIVE verdict.
                            if (sucess) {
                                sucess(result.body ? JSON.parse(result.body) : result);
                            }
                        }, function (err) {
                            // The transport rejects on any >= 400 with the response
                            // body string. A FAIL verdict (406) carries a SloVerdict
                            // JSON body with result:"FAIL"; a disabled/invalid
                            // request (403 disabled / 400 malformed) carries an
                            // {"error": "..."} body naming which.
                            var body = (typeof err === "string") ? err : "";
                            var parsed = null;
                            try {
                                parsed = JSON.parse(body);
                            } catch (e) {
                                parsed = null;
                            }
                            if (parsed && (parsed.result === "PASS" || parsed.result === "INCONCLUSIVE")) {
                                // Defensive: some transports surface non-2xx verdicts
                                // through the error path; treat a PASS/INCONCLUSIVE
                                // verdict body as success.
                                if (sucess) {
                                    sucess(parsed);
                                }
                                return;
                            }
                            var failure;
                            if (parsed && parsed.result === "FAIL") {
                                failure = "SLO verdict FAIL: " + body;
                            } else if (parsed && parsed.error) {
                                // The server already distinguishes 403 (SLO tracking disabled) from
                                // 400 (malformed criteria) in its own error message, and this
                                // transport rejects with the body alone - no status is in scope here
                                // - so surface the server's message verbatim rather than guess.
                                failure = "verifySLO failed: " + parsed.error;
                            } else {
                                failure = "invalid SLO criteria: " + body;
                            }
                            // As with verify*, a FAILED verdict must stay failed: hand it
                            // to the error callback when one was supplied, otherwise
                            // re-throw so the returned promise rejects rather than
                            // silently resolving.
                            if (error) {
                                error(failure);
                                return;
                            }
                            throw failure;
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // Scheduled chaos experiments
        // -------------------------------------------------------------------

        /**
         * Start a scheduled multi-stage chaos experiment
         * (PUT /mockserver/chaosExperiment). The experiment is an ordered
         * sequence of stages, each applying service-scoped chaos profiles to one
         * or more hosts for a fixed duration; stages progress automatically.
         * Only one experiment may be active at a time; starting a new one stops
         * the previous one.
         *
         * @param experiment the experiment definition
         *        ({name, loop, stages:[{durationMillis, profiles:{host: profile}}]})
         * @return promise resolving to the parsed status JSON
         *         ({status: "started", name}), or the raw response when empty
         */
        var startChaosExperiment = function (experiment) {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/chaosExperiment", experiment)
                        .then(function (result) {
                            if (sucess) {
                                sucess(result.body ? JSON.parse(result.body) : result);
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // Load scenario (load injection) management
        // -------------------------------------------------------------------

        // Normalise a name-or-array argument into an array of scenario names.
        var toNamesArray = function (names) {
            if (names === undefined || names === null) {
                return [];
            }
            return Array.isArray(names) ? names : [names];
        };

        /**
         * Register (load) a load scenario in the MockServer's scenario registry.
         * This does NOT run the scenario - call startLoadScenarios(name) to run
         * it. Registration is allowed even when the server was started without
         * `loadGenerationEnabled=true` (only starting requires that flag).
         *
         * @param scenario the LoadScenario definition ({name, profile, steps, ...})
         * @return promise resolving to the parsed registration JSON ({name, state})
         */
        var loadScenario = function (scenario) {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/loadScenario", scenario)
                        .then(function (result) {
                            if (sucess) {
                                sucess(result.body ? JSON.parse(result.body) : result);
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };
        /**
         * List all registered load scenarios.
         *
         * @return promise resolving to the parsed JSON
         *         ({scenarios: [{name, state, definition, status?}, ...]})
         */
        var loadScenarios = function () {
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, "/mockserver/loadScenario")
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Retrieve a single registered load scenario by name.
         *
         * @param name the unique scenario name
         * @return promise resolving to the parsed scenario JSON
         *         ({name, state, definition, status?}), or rejecting 404 if absent
         */
        var getLoadScenario = function (name) {
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, "/mockserver/loadScenario/" + encodeURIComponent(name))
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };
        /**
         * Remove a single registered load scenario by name (stopping it first if
         * it is currently running).
         *
         * @param name the unique scenario name
         * @return promise resolving to the request response
         */
        var deleteLoadScenario = function (name) {
            return makeDeleteRequest(host, port, "/mockserver/loadScenario/" + encodeURIComponent(name));
        };
        /**
         * Clear (remove) all registered load scenarios.
         *
         * @return promise resolving to the request response
         */
        var clearLoadScenarios = function () {
            return makeDeleteRequest(host, port, "/mockserver/loadScenario");
        };
        /**
         * Start one or more registered load scenarios. Each scenario must already
         * be registered (404 otherwise). Each scenario's `startDelayMillis` is
         * honoured by the server.
         *
         * Requires the server to be started with `loadGenerationEnabled=true`;
         * otherwise the server responds 403 and this promise rejects with a clear
         * message explaining how to enable it.
         *
         * @param names a single scenario name or an array of scenario names
         * @return promise resolving to the parsed JSON
         *         ({started: [{name, state}, ...], status: "started"})
         */
        var startLoadScenarios = function (names) {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/loadScenario/start", {names: toNamesArray(names)})
                        .then(function (result) {
                            if (sucess) {
                                sucess(result.body ? JSON.parse(result.body) : result);
                            }
                        }, function (err) {
                            if (error) {
                                var message = (typeof err === "string") ? err : "";
                                if (message.toLowerCase().indexOf("load generation not enabled") !== -1) {
                                    error("load generation is not enabled on this MockServer - start it with loadGenerationEnabled=true to use load scenarios");
                                } else {
                                    error(err);
                                }
                            }
                        });
                }
            };
        };
        /**
         * Stop running load scenarios. Pass a single name, an array of names, or
         * nothing to stop all currently running scenarios.
         *
         * @param names a single scenario name, an array of names, or undefined
         *        (stop all running scenarios)
         * @return promise resolving to the parsed JSON
         *         ({stopped: [...], status: "stopped"})
         */
        var stopLoadScenarios = function (names) {
            var scenarioNames = toNamesArray(names);
            var body = scenarioNames.length > 0 ? {names: scenarioNames} : {};
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/loadScenario/stop", body)
                        .then(function (result) {
                            if (sucess) {
                                sucess(result.body ? JSON.parse(result.body) : result);
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };
        /**
         * Convenience: register a scenario then immediately start it.
         *
         * Requires the server to be started with `loadGenerationEnabled=true`.
         *
         * @param scenario the LoadScenario definition (must carry a unique `name`)
         * @return promise resolving to the parsed start JSON
         *         ({started: [{name, state}, ...], status: "started"})
         */
        var runLoadScenario = function (scenario) {
            return {
                then: function (sucess, error) {
                    loadScenario(scenario)
                        .then(function () {
                            startLoadScenarios(scenario.name).then(sucess, error);
                        }, error);
                }
            };
        };
        /**
         * Retrieve the end-of-run summary report for a load scenario run. The
         * default JSON report carries counts, latency percentiles, the threshold
         * verdict and per-threshold results; with `format="junit"` the server
         * returns a JUnit-XML <testsuite> document instead (resolved as a raw
         * string). Rejects 404 if the scenario has never run.
         *
         * @param name the unique scenario name
         * @param format optional report format - "junit" for the JUnit-XML form,
         *        omitted (or anything else) for the JSON report
         * @return promise resolving to the parsed JSON report, or the raw
         *         JUnit-XML string when format === "junit"
         */
        var getLoadScenarioReport = function (name, format) {
            var path = "/mockserver/loadScenario/" + encodeURIComponent(name) + "/report";
            if (format) {
                path += "?format=" + encodeURIComponent(format);
            }
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, path)
                        .then(function (result) {
                            if (sucess) {
                                if (format === "junit") {
                                    sucess(result.body);
                                } else {
                                    sucess(result.body ? JSON.parse(result.body) : result);
                                }
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };
        /**
         * Generate (and register) an editable load scenario from an OpenAPI
         * specification - one step per operation. Like loadScenario(...), this
         * registers the scenario in the LOADED state and generates no traffic, so
         * it is allowed even when the server was started without
         * `loadGenerationEnabled=true`.
         *
         * @param request {name, specUrlOrPayload, target?, profile?}
         * @return promise resolving to the parsed JSON
         *         ({status: "loaded", name, state, scenario})
         */
        var generateLoadScenarioFromOpenAPI = function (request) {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/loadScenario/generateFromOpenAPI", request)
                        .then(function (result) {
                            if (sucess) {
                                sucess(result.body ? JSON.parse(result.body) : result);
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };
        /**
         * Generate (and register) an editable load scenario from traffic
         * previously recorded by MockServer in proxy/recording mode. Like
         * loadScenario(...), this registers the scenario in the LOADED state and
         * generates no traffic, so it is allowed even when the server was started
         * without `loadGenerationEnabled=true`.
         *
         * @param request {name, mode?, requestFilter?, maxSteps?, target?, profile?}
         * @return promise resolving to the parsed JSON
         *         ({status: "loaded", name, state, scenario})
         */
        var generateLoadScenarioFromRecording = function (request) {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/loadScenario/generateFromRecording", request)
                        .then(function (result) {
                            if (sucess) {
                                sucess(result.body ? JSON.parse(result.body) : result);
                            }
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // Stateful scenario (state-machine) management
        // -------------------------------------------------------------------

        /**
         * Obtain a handle to a named stateful scenario, exposing typed helpers
         * over the /mockserver/scenario REST endpoints:
         *
         *   client.scenario("Deploy").set("Deploying", { transitionAfterMs: 5000, nextState: "Deployed" });
         *   client.scenario("Deploy").trigger("Failed");
         *   client.scenario("Deploy").state();
         *
         * @param name the scenario name
         * @return a handle with state(), set(state, options) and trigger(newState)
         */
        var scenario = function (name) {
            var scenarioPath = "/mockserver/scenario/" + encodeURIComponent(name);
            return {
                /**
                 * GET /mockserver/scenario/{name} — the scenario's current state.
                 *
                 * @return promise resolving to {scenarioName, currentState}
                 */
                state: function () {
                    return {
                        then: function (sucess, error) {
                            makeGetRequest(host, port, scenarioPath)
                                .then(function (result) {
                                    sucess(result.body && JSON.parse(result.body));
                                }, function (err) {
                                    error(err);
                                });
                        }
                    };
                },
                /**
                 * PUT /mockserver/scenario/{name} — set the scenario's state,
                 * optionally scheduling a timed transition.
                 *
                 * @param state    the state to set immediately
                 * @param options  optional {transitionAfterMs, nextState} to schedule a timed transition
                 * @return promise resolving to {scenarioName, currentState, nextState?, transitionAfterMs?}
                 */
                set: function (state, options) {
                    options = options || {};
                    var body = {state: state};
                    if (options.transitionAfterMs !== undefined && options.transitionAfterMs !== null) {
                        body.transitionAfterMs = options.transitionAfterMs;
                    }
                    if (options.nextState !== undefined && options.nextState !== null) {
                        body.nextState = options.nextState;
                    }
                    return {
                        then: function (sucess, error) {
                            makeRequest(host, port, scenarioPath, body)
                                .then(function (result) {
                                    sucess(result.body ? JSON.parse(result.body) : result);
                                }, function (err) {
                                    error(err);
                                });
                        }
                    };
                },
                /**
                 * PUT /mockserver/scenario/{name}/trigger — set the state to
                 * newState immediately.
                 *
                 * @param newState the state to transition to
                 * @return promise resolving to {scenarioName, currentState}
                 */
                trigger: function (newState) {
                    return {
                        then: function (sucess, error) {
                            makeRequest(host, port, scenarioPath + "/trigger", {newState: newState})
                                .then(function (result) {
                                    sucess(result.body ? JSON.parse(result.body) : result);
                                }, function (err) {
                                    error(err);
                                });
                        }
                    };
                }
            };
        };
        /**
         * List every known scenario and its current state.
         *
         * @return promise resolving to {scenarios: [{scenarioName, currentState}, ...]}
         */
        var scenarios = function () {
            return {
                then: function (sucess, error) {
                    makeGetRequest(host, port, "/mockserver/scenario")
                        .then(function (result) {
                            sucess(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };

        // -------------------------------------------------------------------
        // Breakpoint matcher management
        // -------------------------------------------------------------------

        var _breakpointWebSocketClient = null;
        var _breakpointWebSocketClientId = null;
        var _breakpointWebSocketOpening = null;

        /**
         * Ensure the breakpoint callback WebSocket is connected.
         * The WS connection is opened lazily on the first addBreakpoint call
         * and reused for all subsequent breakpoints.
         *
         * @return promise resolving to the webSocketClient
         */
        var ensureBreakpointWebSocket = function () {
            if ((_breakpointWebSocketOpening && _breakpointWebSocketOpening.isClosed()) || (_breakpointWebSocketClient && _breakpointWebSocketClient.isClosed())) {
                _breakpointWebSocketClient = null;
                _breakpointWebSocketClientId = null;
                _breakpointWebSocketOpening = null;
            }
            if (_breakpointWebSocketClient) {
                return {
                    then: function (success) {
                        success(_breakpointWebSocketClient);
                    }
                };
            }
            var webSocketClientPromise = openCallbackWebSocket();
            _breakpointWebSocketOpening = webSocketClientPromise;
            return {
                then: function (success, error) {
                    webSocketClientPromise.then(function (webSocketClient) {
                        // MockServer drops a client's breakpoints when its WebSocket closes, so a new one could
                        // restore none: the next breakpoint opens a new WebSocket instead
                        webSocketClient.reconnectWhen(function () {
                            return false;
                        });
                        webSocketClient.clientIdCallback(function (clientId) {
                            _breakpointWebSocketClient = webSocketClient;
                            _breakpointWebSocketClientId = clientId;
                            success(webSocketClient);
                        });
                    }, error);
                }
            };
        };

        /**
         * Register a breakpoint matcher with handlers for request, response, and/or
         * stream frame phases. Mirrors the Java client's addBreakpoint API.
         *
         * @param requestMatcher  the request definition to match (same as expectation matcher)
         * @param phases          array of phase strings: "REQUEST", "RESPONSE", "RESPONSE_STREAM", "INBOUND_STREAM"
         * @param requestHandler  function(request) => request|response for REQUEST phase (optional)
         * @param responseHandler function(request, response) => response for RESPONSE phase (optional)
         * @param streamFrameHandler function(pausedFrame) => {action, body?} for streaming phases (optional)
         */
        var addBreakpoint = function (requestMatcher, phases, requestHandler, responseHandler, streamFrameHandler) {
            if (!requestMatcher) {
                throw new Error("addBreakpoint requires a non-null requestMatcher");
            }
            if (!phases || !Array.isArray(phases) || phases.length === 0) {
                throw new Error("addBreakpoint requires a non-empty phases array");
            }
            return {
                then: function (success, error) {
                    try {
                        ensureBreakpointWebSocket().then(function (webSocketClient) {
                            var body = {
                                httpRequest: requestMatcher,
                                phases: phases,
                                clientId: _breakpointWebSocketClientId
                            };
                            makeRequest(host, port, "/mockserver/breakpoint/matcher", body)
                                .then(function (result) {
                                    var responseBody = typeof result === "string" ? result : (result && result.body ? result.body : "");
                                    var parsed = JSON.parse(responseBody);
                                    var breakpointId = parsed.id;
                                    if (!breakpointId) {
                                        if (error) {
                                            error("Server did not return a breakpoint id");
                                        }
                                        return;
                                    }
                                    // Install handlers keyed by breakpoint id
                                    if (requestHandler) {
                                        webSocketClient.setBreakpointRequestHandler(breakpointId, requestHandler);
                                    }
                                    if (responseHandler) {
                                        webSocketClient.setBreakpointResponseHandler(breakpointId, responseHandler);
                                    }
                                    if (streamFrameHandler) {
                                        webSocketClient.setBreakpointStreamFrameHandler(breakpointId, streamFrameHandler);
                                    }
                                    if (success) {
                                        success(breakpointId);
                                    }
                                }, error);
                        }, error);
                    } catch (e) {
                        if (error) {
                            error(e);
                        }
                    }
                }
            };
        };

        /**
         * Register a request-only breakpoint (convenience overload).
         *
         * @param requestMatcher  the request definition to match
         * @param requestHandler  function(request) => request|response
         */
        var addRequestBreakpoint = function (requestMatcher, requestHandler) {
            return addBreakpoint(requestMatcher, ["REQUEST"], requestHandler, null, null);
        };

        /**
         * Register a request+response breakpoint (convenience overload).
         *
         * @param requestMatcher   the request definition to match
         * @param requestHandler   function(request) => request|response
         * @param responseHandler  function(request, response) => response
         */
        var addRequestAndResponseBreakpoint = function (requestMatcher, requestHandler, responseHandler) {
            return addBreakpoint(requestMatcher, ["REQUEST", "RESPONSE"], requestHandler, responseHandler, null);
        };

        /**
         * List all registered breakpoint matchers.
         * Returns a promise resolving to {matchers: [{id, httpRequest, phases, clientId}, ...]}.
         */
        var listBreakpointMatchers = function () {
            return {
                then: function (success, error) {
                    makeGetRequest(host, port, "/mockserver/breakpoint/matchers")
                        .then(function (result) {
                            success(result.body && JSON.parse(result.body));
                        }, function (err) {
                            error(err);
                        });
                }
            };
        };

        /**
         * Remove a breakpoint matcher by id.
         *
         * @param breakpointId the id of the breakpoint matcher to remove
         */
        var removeBreakpointMatcher = function (breakpointId) {
            if (!breakpointId) {
                throw new Error("removeBreakpointMatcher requires a breakpointId");
            }
            var result = makeRequest(host, port, "/mockserver/breakpoint/matcher/remove", {id: breakpointId});
            // Remove client-side handlers
            if (_breakpointWebSocketClient) {
                _breakpointWebSocketClient.removeBreakpointHandlers(breakpointId);
            }
            return result;
        };

        /**
         * Clear all registered breakpoint matchers.
         */
        var clearBreakpointMatchers = function () {
            var result = makeRequest(host, port, "/mockserver/breakpoint/matcher/clear");
            // Clear client-side handlers
            if (_breakpointWebSocketClient) {
                _breakpointWebSocketClient.clearBreakpointHandlers();
            }
            return result;
        };

        /**
         * Upload a compiled gRPC proto descriptor set (a FileDescriptorSet, as
         * produced by `protoc --descriptor_set_out`).  Registered services then
         * become available for gRPC mocking and can be queried with
         * retrieveGrpcServices().
         *
         * @param descriptorSetBytes the raw bytes of the compiled descriptor
         *        set, as a Buffer, Uint8Array or ArrayBuffer
         * @returns a promise that is resolved once the descriptor set is loaded
         */
        var uploadGrpcDescriptor = function (descriptorSetBytes) {
            if (!descriptorSetBytes) {
                throw new Error("uploadGrpcDescriptor requires the descriptor set bytes");
            }
            var buffer;
            if (typeof Buffer !== 'undefined' && Buffer.isBuffer(descriptorSetBytes)) {
                buffer = descriptorSetBytes;
            } else if (typeof Buffer !== 'undefined') {
                buffer = Buffer.from(descriptorSetBytes);
            } else {
                buffer = descriptorSetBytes;
            }
            return makeBinaryRequest(host, port, "/mockserver/grpc/descriptors", buffer, "application/octet-stream");
        };

        /**
         * Retrieve the gRPC services registered from uploaded descriptor sets.
         *
         * @returns a promise resolved with the array of registered services,
         *          each with its name and methods (inputType, outputType,
         *          clientStreaming and serverStreaming flags)
         */
        var retrieveGrpcServices = function () {
            return {
                then: function (sucess, error) {
                    makeRequest(host, port, "/mockserver/grpc/services")
                        .then(function (response) {
                            sucess(JSON.parse((response && response.body) || "[]"));
                        }, function (err) {
                            if (error) {
                                error(err);
                            }
                        });
                }
            };
        };

        /**
         * Clear all registered gRPC descriptor sets and services.
         *
         * @returns a promise that is resolved once the descriptors are cleared
         */
        var clearGrpcDescriptors = function () {
            return makeRequest(host, port, "/mockserver/grpc/clear");
        };

        /**
         * Start building a mock MCP (Model Context Protocol) server that
         * speaks JSON-RPC 2.0 over the Streamable HTTP transport.  Returns a
         * fluent builder; call .applyTo() (with no arguments — this client is
         * used) to register the generated expectations, or .build() to obtain
         * the raw expectation array.  Mirrors the Java client McpMockBuilder.
         *
         * for example:
         *
         *   client.mcpMock("/mcp")
         *       .withServerName("MyServer")
         *       .withTool("get_weather")
         *           .withDescription("Get the weather for a city")
         *           .respondingWith("sunny")
         *       .and()
         *       .applyTo();
         *
         * @param path the HTTP path the MCP server is mounted on (default "/mcp")
         */
        var mcpMock = function (path) {
            var builder = _mcpMock(path);
            var applyTo = builder.applyTo;
            // Default applyTo() to this client when none is supplied.
            builder.applyTo = function (client) {
                return applyTo(client || _this);
            };
            return builder;
        };

        /**
         * Start building a mock A2A (Agent-to-Agent) agent: a static agent-card
         * document served over GET plus JSON-RPC 2.0 task methods (tasks/send,
         * tasks/get, tasks/cancel) over POST, with optional SSE streaming and
         * push-notification config + delivery. Returns a fluent builder; call
         * .applyTo() (with no arguments — this client is used) to register the
         * generated expectations, or .build() to obtain the raw expectation
         * array. Mirrors the Java client A2aMockBuilder.
         *
         * for example:
         *
         *   client.a2aMock("/a2a")
         *       .withAgentName("MyAgent")
         *       .withSkill("translate")
         *           .withName("Translation")
         *           .withExample("Translate hello to Spanish")
         *       .and()
         *       .onTaskSend()
         *           .matchingMessage("translate.*")
         *           .respondingWith("Hola")
         *       .and()
         *       .applyTo();
         *
         * @param path the HTTP path the A2A agent is mounted on (default "/a2a")
         */
        var a2aMock = function (path) {
            var builder = _a2aMock(path);
            var applyTo = builder.applyTo;
            // Default applyTo() to this client when none is supplied.
            builder.applyTo = function (client) {
                return applyTo(client || _this);
            };
            return builder;
        };

        /* jshint -W003 */
        var _this = {
            openAPIExpectation: openAPIExpectation,
            mockAnyResponse: mockAnyResponse,
            mockWithLLM: mockWithLLM,
            llm: _llm,
            mockWithCallback: mockWithCallback,
            mockWithForwardCallback: mockWithForwardCallback,
            mockWithForwardAndResponseCallback: mockWithForwardAndResponseCallback,
            respondWithClassCallback: respondWithClassCallback,
            forwardWithClassCallback: forwardWithClassCallback,
            mockSimpleResponse: mockSimpleResponse,
            when: when,
            respondWithSse: respondWithSse,
            respondWithWebSocket: respondWithWebSocket,
            respondWithDns: respondWithDns,
            respondWithBinary: respondWithBinary,
            respondWithGrpcStream: respondWithGrpcStream,
            setDefaultHeaders: setDefaultHeaders,
            verify: verify,
            verifyResponse: verifyResponse,
            verifyRequestAndResponse: verifyRequestAndResponse,
            verifyById: verifyById,
            verifySequence: verifySequence,
            verifySequenceWithResponses: verifySequenceWithResponses,
            verifySequenceById: verifySequenceById,
            verifyZeroInteractions: verifyZeroInteractions,
            reset: reset,
            close: close,
            clear: clear,
            clearWithFilter: clearWithFilter,
            clearById: clearById,
            clearByMetadata: clearByMetadata,
            clearByMetadataKey: clearByMetadataKey,
            clearUnmatchedExpectations: clearUnmatchedExpectations,
            freezeClock: freezeClock,
            advanceClock: advanceClock,
            resetClock: resetClock,
            clockStatus: clockStatus,
            retrieveMetrics: retrieveMetrics,
            scrapeMetrics: scrapeMetrics,
            retrieveConfiguration: retrieveConfiguration,
            updateConfiguration: updateConfiguration,
            retrieveDrift: retrieveDrift,
            clearDrift: clearDrift,
            pactImport: pactImport,
            pactExport: pactExport,
            pactVerify: pactVerify,
            storeFile: storeFile,
            storeBinaryFile: storeBinaryFile,
            retrieveFile: retrieveFile,
            listFiles: listFiles,
            deleteFile: deleteFile,
            importDocument: importDocument,
            importHar: importHar,
            importPostmanCollection: importPostmanCollection,
            setMode: setMode,
            retrieveMode: retrieveMode,
            wsdlExpectation: wsdlExpectation,
            setServiceChaos: setServiceChaos,
            removeServiceChaos: removeServiceChaos,
            clearServiceChaos: clearServiceChaos,
            serviceChaosStatus: serviceChaosStatus,
            verifySLO: verifySLO,
            startChaosExperiment: startChaosExperiment,
            loadScenario: loadScenario,
            scenario: scenario,
            scenarios: scenarios,
            loadScenarios: loadScenarios,
            getLoadScenario: getLoadScenario,
            deleteLoadScenario: deleteLoadScenario,
            clearLoadScenarios: clearLoadScenarios,
            startLoadScenarios: startLoadScenarios,
            stopLoadScenarios: stopLoadScenarios,
            runLoadScenario: runLoadScenario,
            getLoadScenarioReport: getLoadScenarioReport,
            generateLoadScenarioFromOpenAPI: generateLoadScenarioFromOpenAPI,
            generateLoadScenarioFromRecording: generateLoadScenarioFromRecording,
            bind: bind,
            retrieveRecordedRequests: retrieveRecordedRequests,
            retrieveRecordedRequestsAndResponses: retrieveRecordedRequestsAndResponses,
            retrieveRecordedRequestsAndResponsesAsHar: retrieveRecordedRequestsAndResponsesAsHar,
            retrieveActiveExpectations: retrieveActiveExpectations,
            retrieveRecordedExpectations: retrieveRecordedExpectations,
            retrieveActiveExpectationsByMetadata: retrieveActiveExpectationsByMetadata,
            retrieveActiveExpectationsByMetadataKey: retrieveActiveExpectationsByMetadataKey,
            retrieveUnmatchedExpectations: retrieveUnmatchedExpectations,
            retrieveExpectationsAsCode: retrieveExpectationsAsCode,
            retrieveRecordedExpectationsAsCode: retrieveRecordedExpectationsAsCode,
            retrieveLogMessages: retrieveLogMessages,
            retrieveRecordedRequestsById: retrieveRecordedRequestsById,
            retrieveRecordedRequestsAndResponsesById: retrieveRecordedRequestsAndResponsesById,
            retrieveActiveExpectationsById: retrieveActiveExpectationsById,
            retrieveRecordedExpectationsById: retrieveRecordedExpectationsById,
            retrieveLogMessagesById: retrieveLogMessagesById,
            explain: explain,
            debugMismatch: debugMismatch,
            explainUnmatched: explainUnmatched,
            addBreakpoint: addBreakpoint,
            addRequestBreakpoint: addRequestBreakpoint,
            addRequestAndResponseBreakpoint: addRequestAndResponseBreakpoint,
            listBreakpointMatchers: listBreakpointMatchers,
            removeBreakpointMatcher: removeBreakpointMatcher,
            clearBreakpointMatchers: clearBreakpointMatchers,
            uploadGrpcDescriptor: uploadGrpcDescriptor,
            retrieveGrpcServices: retrieveGrpcServices,
            clearGrpcDescriptors: clearGrpcDescriptors,
            mcpMock: mcpMock,
            a2aMock: a2aMock
        };
        // Explicit resource management support (TC39 `using`/`await using`).
        // Calling `await using client = mockServerClient(...)` will reset the
        // MockServer, and close the callback WebSockets, when the client goes
        // out of scope, so tests do not need a manual
        // `afterEach(() => client.reset())`. Symbols are guarded so the
        // client still works on runtimes that predate explicit resource
        // management.
        if (typeof Symbol !== 'undefined') {
            if (Symbol.asyncDispose) {
                _this[Symbol.asyncDispose] = function () {
                    var closing = closeCallbackWebSockets();
                    return Promise.all([makeRequest(host, port, "/mockserver/reset"), closing]).then(function (results) {
                        return results[0];
                    });
                };
            }
            if (Symbol.dispose) {
                _this[Symbol.dispose] = function () {
                    // Best-effort synchronous disposal: fire the reset request
                    // without awaiting it. Prefer `await using` (asyncDispose)
                    // when the reset must complete before the next test. The
                    // returned promise is swallowed so a connection/HTTP error
                    // during teardown does not surface as an unhandled rejection.
                    var p = reset();
                    if (p && typeof p.catch === 'function') {
                        p.catch(function () { });
                    }
                };
            }
        }
        return _this;
    };

    // The valid high-level operating modes (server enum MockMode). Exposed as
    // string constants so callers can write setMode(MockMode.SPY) instead of a
    // raw string literal. setMode also accepts the equivalent strings directly.
    var MockMode = Object.freeze({
        SIMULATE: "SIMULATE",
        SPY: "SPY",
        CAPTURE: "CAPTURE"
    });

    if (typeof module !== 'undefined') {
        var llmModule = require('./llm');
        var mcpMockFactory = require('./mcpMockBuilder').mcpMock;
        var a2aMockFactory = require('./a2aMockBuilder').a2aMock;
        // values are identifiers only: Node reads an ES module importer's names from this literal
        // and stops at the first value that is not one
        module.exports = {
            mockServerClient: mockServerClient,
            MockMode: MockMode,
            llm: llmModule,
            mcpMock: mcpMockFactory,
            a2aMock: a2aMockFactory,
            routeBreakpointMessage: _routeBreakpointMessage,
            extractBreakpointHeaders: _extractBreakpointHeaders,
            // exported so a test can ratchet it against the server's own expectation schema
            NON_HTTP_RESPONSE_ACTION_KEYS: NON_HTTP_RESPONSE_ACTION_KEYS
        };
    }
})();