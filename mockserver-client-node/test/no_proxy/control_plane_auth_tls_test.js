'use strict';

/*
 * Unit tests for the control-plane auth + mTLS client options (no running server).
 *
 * The MockServer Node client binds its HTTP transport at construction time via
 *   require('./sendRequest').sendRequest(tls, caCertPath, options)
 * and sendRequest.js issues the request through the core `http`/`https`
 * modules. To assert exactly what reaches the wire — the Authorization header
 * and the mTLS cert/key in the request options — we stub the `http` and
 * `https` modules' `request` function and capture the options object the
 * client passes in.
 *
 * These tests intercept the transport at the lowest layer (the http(s)
 * request), so they exercise the real sendRequest.js auth/mTLS wiring rather
 * than a re-implementation.
 */

var { describe, it, beforeEach, afterEach } = require('node:test');
var assert = require('node:assert/strict');
var http = require('node:http');
var https = require('node:https');
var fs = require('node:fs');
var os = require('node:os');
var path = require('node:path');

var mockServerClient = require('../../').mockServerClient;

// Suppress benign unhandled rejections from internal Q promises.
process.on('unhandledRejection', function () {});

// Every captured request: the options object passed to http(s).request(...)
var capturedOptions;

var originalHttpRequest, originalHttpsRequest;

// A fake request object that immediately fires a successful empty response,
// so the client's promise resolves and the test can await it.
function fakeRequest() {
    var responseHandler = null;
    return {
        once: function (event, handler) {
            if (event === 'response') {
                responseHandler = handler;
            }
            return this;
        },
        write: function () {},
        end: function () {
            if (responseHandler) {
                // Minimal fake response: emits 'end' with a 200 and empty body.
                var dataHandlers = {};
                var fakeResponse = {
                    statusCode: 200,
                    on: function (event, handler) {
                        dataHandlers[event] = handler;
                        return this;
                    }
                };
                // Deliver asynchronously to mirror real socket behaviour.
                setImmediate(function () {
                    responseHandler(fakeResponse);
                    if (dataHandlers.end) {
                        dataHandlers.end();
                    }
                });
            }
        }
    };
}

function captureRequest(options) {
    capturedOptions.push(options);
    return fakeRequest();
}

beforeEach(function () {
    capturedOptions = [];
    originalHttpRequest = http.request;
    originalHttpsRequest = https.request;
    http.request = captureRequest;
    https.request = captureRequest;
});

afterEach(function () {
    http.request = originalHttpRequest;
    https.request = originalHttpsRequest;
});

// ---------------------------------------------------------------------------
// Bearer token
// ---------------------------------------------------------------------------

describe('control-plane bearer token', function () {

    it('attaches Authorization: Bearer <token> when a static bearerToken is configured', async function () {
        var client = mockServerClient('localhost', 1080, undefined, false, undefined, {
            bearerToken: 'static-jwt-123'
        });
        await client.reset();

        assert.equal(capturedOptions.length, 1);
        assert.equal(capturedOptions[0].headers['Authorization'], 'Bearer static-jwt-123');
    });

    it('resolves the token from bearerTokenSupplier per request (refreshable)', async function () {
        var tokens = ['token-A', 'token-B'];
        var i = 0;
        var client = mockServerClient('localhost', 1080, undefined, false, undefined, {
            bearerTokenSupplier: function () {
                return tokens[i++];
            }
        });

        await client.reset();
        await client.reset();

        assert.equal(capturedOptions.length, 2);
        assert.equal(capturedOptions[0].headers['Authorization'], 'Bearer token-A');
        assert.equal(capturedOptions[1].headers['Authorization'], 'Bearer token-B');
    });

    it('prefers bearerTokenSupplier over a static bearerToken', async function () {
        var client = mockServerClient('localhost', 1080, undefined, false, undefined, {
            bearerToken: 'static',
            bearerTokenSupplier: function () { return 'supplied'; }
        });
        await client.reset();

        assert.equal(capturedOptions[0].headers['Authorization'], 'Bearer supplied');
    });

    it('does NOT attach an Authorization header when no token is configured', async function () {
        var client = mockServerClient('localhost', 1080);
        await client.reset();

        assert.equal(capturedOptions.length, 1);
        assert.equal(capturedOptions[0].headers && capturedOptions[0].headers['Authorization'], undefined);
    });

    it('does NOT attach an Authorization header when the supplier yields a blank value', async function () {
        var client = mockServerClient('localhost', 1080, undefined, false, undefined, {
            bearerTokenSupplier: function () { return ''; }
        });
        await client.reset();

        assert.equal(capturedOptions[0].headers && capturedOptions[0].headers['Authorization'], undefined);
    });

    it('attaches the bearer header on a GET control-plane request too', async function () {
        var client = mockServerClient('localhost', 1080, undefined, false, undefined, {
            bearerToken: 'get-jwt'
        });
        await client.clockStatus();

        assert.equal(capturedOptions.length, 1);
        assert.equal(capturedOptions[0].method, 'GET');
        assert.equal(capturedOptions[0].headers['Authorization'], 'Bearer get-jwt');
    });
});

describe('control-plane basic credentials', function () {

    it('attaches Authorization: Basic <base64 username:password> when basic credentials are configured', async function () {
        var client = mockServerClient('localhost', 1080, undefined, false, undefined, {
            basicAuthUsername: 'admin',
            basicAuthPassword: 'pä:ss'
        });
        await client.reset();

        assert.equal(capturedOptions.length, 1);
        assert.equal(capturedOptions[0].headers['Authorization'], 'Basic ' + Buffer.from('admin:pä:ss', 'utf8').toString('base64'));
    });

    it('sends the bearer token instead when both are configured', async function () {
        var client = mockServerClient('localhost', 1080, undefined, false, undefined, {
            bearerToken: 'jwt',
            basicAuthUsername: 'admin',
            basicAuthPassword: 's3cret'
        });
        await client.reset();

        assert.equal(capturedOptions[0].headers['Authorization'], 'Bearer jwt');
    });

    it('warns once that basic credentials without tls can be read on the network', async function () {
        var originalWarn = console.warn;
        var warnings = [];
        console.warn = function (message) { warnings.push(message); };
        try {
            var client = mockServerClient('localhost', 1080, undefined, false, undefined, {basicAuthUsername: 'admin', basicAuthPassword: 's3cret'});
            await client.reset();
            await client.reset();
            mockServerClient('localhost', 1080, undefined, true, '/no/download/needed.pem', {basicAuthUsername: 'admin', basicAuthPassword: 's3cret'});
        } finally {
            console.warn = originalWarn;
        }

        assert.equal(warnings.length, 1);
        assert.match(warnings[0], /without tls/);
        assert.doesNotMatch(warnings[0], /s3cret/);
    });

    it('does NOT put the credentials in the error of a failed request', async function () {
        var failing = function (options) {
            capturedOptions.push(options);
            var responseHandler = null;
            return {
                once: function (event, handler) { if (event === 'response') { responseHandler = handler; } return this; },
                write: function () {},
                end: function () {
                    var handlers = {};
                    setImmediate(function () {
                        responseHandler({statusCode: 500, on: function (event, handler) { handlers[event] = handler; return this; }});
                        if (handlers.data) { handlers.data(Buffer.from('failed')); }
                        if (handlers.end) { handlers.end(); }
                    });
                }
            };
        };
        http.request = failing;
        https.request = failing;
        var client = mockServerClient('localhost', 1080, undefined, false, undefined, {basicAuthUsername: 'admin', basicAuthPassword: 'pa55-near-miss'});

        var failure = await client.reset().then(function () { return null; }, function (error) { return error; });

        assert.ok(failure !== null);
        assert.doesNotMatch(String(failure), /pa55-near-miss/);
        assert.doesNotMatch(String(failure), new RegExp(Buffer.from('admin:pa55-near-miss').toString('base64')));
    });

    it('does NOT attach an Authorization header for a username without a password', async function () {
        var client = mockServerClient('localhost', 1080, undefined, false, undefined, {
            basicAuthUsername: 'admin'
        });
        await client.reset();

        assert.equal(capturedOptions[0].headers && capturedOptions[0].headers['Authorization'], undefined);
    });
});

// ---------------------------------------------------------------------------
// mTLS client certificate / key
// ---------------------------------------------------------------------------

describe('mTLS client certificate', function () {

    var tmpDir, certPath, keyPath, caPath;

    beforeEach(function () {
        tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ms-mtls-'));
        certPath = path.join(tmpDir, 'client.pem');
        keyPath = path.join(tmpDir, 'client-key.pem');
        // A real CA file path is supplied so sendRequest.js skips the network
        // download branch (which would otherwise hit our stubbed https.request).
        caPath = path.join(tmpDir, 'ca.pem');
        fs.writeFileSync(certPath, '-----BEGIN CERTIFICATE-----\nFAKECERT\n-----END CERTIFICATE-----\n');
        fs.writeFileSync(keyPath, '-----BEGIN PRIVATE KEY-----\nFAKEKEY\n-----END PRIVATE KEY-----\n');
        fs.writeFileSync(caPath, '-----BEGIN CERTIFICATE-----\nFAKECA\n-----END CERTIFICATE-----\n');
    });

    afterEach(function () {
        fs.rmSync(tmpDir, { recursive: true, force: true });
    });

    it('passes cert and key into the https request options when configured', async function () {
        var client = mockServerClient('localhost', 1080, undefined, true, caPath, {
            clientCertPemFilePath: certPath,
            clientKeyPemFilePath: keyPath
        });
        await client.reset();

        assert.equal(capturedOptions.length, 1);
        assert.match(capturedOptions[0].cert, /FAKECERT/);
        assert.match(capturedOptions[0].key, /FAKEKEY/);
    });

    it('does NOT set cert/key when no client certificate is configured', async function () {
        var client = mockServerClient('localhost', 1080, undefined, true, caPath);
        await client.reset();

        assert.equal(capturedOptions[0].cert, undefined);
        assert.equal(capturedOptions[0].key, undefined);
    });

    it('combines mTLS cert/key and bearer token on the same request', async function () {
        var client = mockServerClient('localhost', 1080, undefined, true, caPath, {
            bearerToken: 'mtls-jwt',
            clientCertPemFilePath: certPath,
            clientKeyPemFilePath: keyPath
        });
        await client.reset();

        assert.equal(capturedOptions[0].headers['Authorization'], 'Bearer mtls-jwt');
        assert.match(capturedOptions[0].cert, /FAKECERT/);
        assert.match(capturedOptions[0].key, /FAKEKEY/);
    });
});
