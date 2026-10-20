'use strict';

/*
 * Opening a callback WebSocket settles only once MockServer has registered it: it resolves when the
 * client id arrives, and rejects when the connection is refused, the handshake fails, MockServer
 * closes the connection before sending the id, or the id does not arrive in time. Each test runs
 * against a fake MockServer in this process, so none needs a real one.
 */

var { describe, it } = require('node:test');
var assert = require('node:assert/strict');
var childProcess = require('child_process');
var fs = require('fs');
var https = require('https');
var http = require('http');
var net = require('net');
var os = require('os');
var path = require('path');
var WebSocketServer = require('websocket').server;
var webSocketClient = require('../../webSocketClient').webSocketClient;
var mockServerClient = require('../../').mockServerClient;

var PACKAGE_ROOT = path.resolve(__dirname, '..', '..');
var CLIENT_ID_TYPE = 'org.mockserver.serialization.model.WebSocketClientIdDTO';
var SETTLE_DEADLINE_MILLIS = 5000;

/*
 * A fake MockServer on 127.0.0.1: `onWebSocket(request)` handles each callback WebSocket request,
 * and an expectation PUT is answered 201 with an expectation id.
 */
function startFakeMockServer(onWebSocket) {
    var sockets = new Set();
    var webSocketRequests = [];
    var expectationBodies = [];
    var httpServer = http.createServer(function (req, res) {
        var body = '';
        req.on('data', function (chunk) {
            body += chunk;
        });
        req.on('end', function () {
            if (req.url.indexOf('/mockserver/expectation') === 0) {
                expectationBodies.push(JSON.parse(body));
                res.writeHead(201, {'Content-Type': 'application/json'});
                res.end(JSON.stringify([{id: 'expectation-1'}]));
            } else {
                res.writeHead(404);
                res.end();
            }
        });
    });
    httpServer.on('connection', function (socket) {
        sockets.add(socket);
        socket.on('close', function () {
            sockets.delete(socket);
        });
    });
    var wsServer = new WebSocketServer({httpServer: httpServer, autoAcceptConnections: false});
    wsServer.on('request', function (request) {
        webSocketRequests.push(request.httpRequest.headers);
        onWebSocket(request);
    });
    return new Promise(function (resolve) {
        httpServer.listen(0, '127.0.0.1', function () {
            resolve({
                port: httpServer.address().port,
                webSocketRequests: webSocketRequests,
                expectationBodies: expectationBodies,
                close: function () {
                    wsServer.shutDown();
                    sockets.forEach(function (socket) {
                        socket.destroy();
                    });
                    return new Promise(function (done) {
                        httpServer.close(function () {
                            done();
                        });
                    });
                }
            });
        });
    });
}

function sendClientId(connection, clientId) {
    connection.sendUTF(JSON.stringify({type: CLIENT_ID_TYPE, value: JSON.stringify({clientId: clientId})}));
}

// a port nothing listens on
function refusedPort() {
    return new Promise(function (resolve) {
        var server = net.createServer();
        server.listen(0, '127.0.0.1', function () {
            var port = server.address().port;
            server.close(function () {
                resolve(port);
            });
        });
    });
}

// {resolved: value}, {rejected: reason} or {pending: true} when neither happens within the deadline
function settledWithin(thenable, deadlineMillis) {
    return new Promise(function (resolve) {
        var timer = setTimeout(resolve, deadlineMillis, {pending: true});
        thenable.then(function (value) {
            clearTimeout(timer);
            resolve({resolved: value});
        }, function (reason) {
            clearTimeout(timer);
            resolve({rejected: reason});
        });
    });
}

function openWebSocket(port, options) {
    return webSocketClient(false, undefined, options)('127.0.0.1', port, '');
}

function sleep(millis) {
    return new Promise(function (resolve) {
        setTimeout(resolve, millis);
    });
}

/*
 * Runs `body` in a child Node process, where `mockServer` is the package; resolves with the exit
 * code and output, or with `running: true` if it is still running after `deadlineMillis`.
 */
function runProcess(body, deadlineMillis) {
    var script = [
        "var mockServer = require(" + JSON.stringify(PACKAGE_ROOT) + ");",
        "process.on('unhandledRejection', function (reason) { console.log('UNHANDLED ' + reason); process.exit(4); });",
        body
    ].join('\n');
    return new Promise(function (resolve) {
        var child = childProcess.spawn(process.execPath, ['-e', script], {cwd: PACKAGE_ROOT});
        var stdout = '';
        var stderr = '';
        child.stdout.on('data', function (chunk) {
            stdout += chunk;
        });
        child.stderr.on('data', function (chunk) {
            stderr += chunk;
        });
        var timer = setTimeout(function () {
            child.kill('SIGKILL');
        }, deadlineMillis);
        child.on('exit', function (code, signal) {
            clearTimeout(timer);
            resolve({code: code, running: signal === 'SIGKILL', stdout: stdout, stderr: stderr});
        });
    });
}

describe('opening a callback WebSocket', {timeout: 60000}, function () {
    it('rejects promptly when the connection is refused', async function () {
        var port = await refusedPort();
        var outcome = await settledWithin(openWebSocket(port), SETTLE_DEADLINE_MILLIS);
        assert.deepEqual(outcome, {rejected: "Can't connect to MockServer running on host: \"127.0.0.1\" and port: \"" + port + "\""});
    });

    it('rejects when the CA certificate it was given cannot be read', async function () {
        var missing = path.join(PACKAGE_ROOT, 'no-such-ca-' + process.pid + '.pem');
        var opening = webSocketClient(true, missing)('127.0.0.1', await refusedPort(), '');
        var outcome = await settledWithin(opening, SETTLE_DEADLINE_MILLIS);
        assert.ok(outcome.rejected, JSON.stringify(outcome));
        assert.match(String(outcome.rejected), /ENOENT/);
    });

    it('rejects when MockServer refuses the WebSocket handshake', async function () {
        var server = await startFakeMockServer(function (request) {
            request.reject(403, 'Forbidden');
        });
        try {
            var outcome = await settledWithin(openWebSocket(server.port), SETTLE_DEADLINE_MILLIS);
            assert.ok(outcome.rejected, JSON.stringify(outcome));
            assert.match(String(outcome.rejected), /403/);
        } finally {
            await server.close();
        }
    });

    it('rejects when MockServer closes the connection before sending the client id', async function () {
        var server = await startFakeMockServer(function (request) {
            request.accept(null, request.origin).close();
        });
        try {
            var outcome = await settledWithin(openWebSocket(server.port), SETTLE_DEADLINE_MILLIS);
            assert.ok(outcome.rejected, JSON.stringify(outcome));
            assert.match(String(outcome.rejected), /closed the callback WebSocket before sending its client id/);
            await sleep(2500);
            assert.equal(server.webSocketRequests.length, 1, 'reconnected after a failed registration');
        } finally {
            await server.close();
        }
    });

    it('rejects when the client id does not arrive in time', async function () {
        var server = await startFakeMockServer(function (request) {
            request.accept(null, request.origin);
        });
        try {
            var started = Date.now();
            var outcome = await settledWithin(openWebSocket(server.port, {callbackWebSocketTimeoutMillis: 500}), SETTLE_DEADLINE_MILLIS);
            var elapsed = Date.now() - started;
            assert.ok(outcome.rejected, JSON.stringify(outcome));
            assert.match(String(outcome.rejected), /client id within 500ms/);
            assert.ok(elapsed >= 450, 'rejected after ' + elapsed + 'ms, before its timeout');
        } finally {
            await server.close();
        }
    });

    it('resolves only once the client id has arrived', async function () {
        var server = await startFakeMockServer(function (request) {
            var connection = request.accept(null, request.origin);
            setTimeout(sendClientId, 400, connection, 'client-one');
        });
        try {
            var opening = openWebSocket(server.port);
            assert.deepEqual(await settledWithin(opening, 200), {pending: true});
            var outcome = await settledWithin(opening, SETTLE_DEADLINE_MILLIS);
            assert.ok(outcome.resolved, JSON.stringify(outcome));
            var clientIds = [];
            outcome.resolved.clientIdCallback(function (clientId) {
                clientIds.push(clientId);
            });
            assert.deepEqual(clientIds, ['client-one']);
            await outcome.resolved.close();
        } finally {
            await server.close();
        }
    });

    it('reconnects with the same client id after a registered connection drops, without rejecting', async function () {
        var connections = [];
        var server = await startFakeMockServer(function (request) {
            var connection = request.accept(null, request.origin);
            connections.push(connection);
            sendClientId(connection, 'client-one');
        });
        var unhandled = [];
        var onUnhandled = function (reason) {
            unhandled.push(reason);
        };
        process.on('unhandledRejection', onUnhandled);
        try {
            var rejections = [];
            var opening = openWebSocket(server.port);
            var handle = await new Promise(function (resolve, reject) {
                opening.then(resolve, function (reason) {
                    rejections.push(reason);
                    reject(reason);
                });
            });
            connections[0].drop();
            var deadline = Date.now() + 10000;
            while (server.webSocketRequests.length < 2 && Date.now() < deadline) {
                await sleep(100);
            }
            assert.equal(server.webSocketRequests.length, 2, 'did not reconnect');
            assert.equal(server.webSocketRequests[1]['x-client-registration-id'], 'client-one');
            assert.equal(handle.isClosed(), false);
            assert.deepEqual(rejections, []);
            assert.deepEqual(unhandled, []);
            await handle.close();
        } finally {
            process.removeListener('unhandledRejection', onUnhandled);
            await server.close();
        }
    });
});

describe('control-plane bearer token on the callback WebSocket upgrade', {timeout: 60000}, function () {
    async function upgradeHeadersFor(options, dropOnce) {
        var connections = [];
        var server = await startFakeMockServer(function (request) {
            var connection = request.accept(null, request.origin);
            connections.push(connection);
            sendClientId(connection, 'client-one');
        });
        try {
            var handle = await openWebSocket(server.port, options);
            if (dropOnce) {
                connections[0].drop();
                var deadline = Date.now() + 10000;
                while (server.webSocketRequests.length < 2 && Date.now() < deadline) {
                    await sleep(100);
                }
            }
            await handle.close();
            return server.webSocketRequests;
        } finally {
            await server.close();
        }
    }

    it('sends Authorization: Bearer with the configured bearerToken', async function () {
        var requests = await upgradeHeadersFor({bearerToken: 'static-token'});
        assert.equal(requests[0].authorization, 'Bearer static-token');
    });

    it('sends no Authorization header when no token is configured', async function () {
        var requests = await upgradeHeadersFor(undefined);
        assert.equal(requests[0].authorization, undefined);
        requests = await upgradeHeadersFor({bearerToken: ''});
        assert.equal(requests[0].authorization, undefined);
    });

    it('asks bearerTokenSupplier again on reconnect, keeping the client id', async function () {
        var issued = 0;
        var requests = await upgradeHeadersFor({
            bearerToken: 'ignored',
            bearerTokenSupplier: function () {
                issued += 1;
                return 'token-' + issued;
            }
        }, true);
        assert.equal(requests.length, 2, 'did not reconnect');
        assert.equal(requests[0].authorization, 'Bearer token-1');
        assert.equal(requests[1].authorization, 'Bearer token-2');
        assert.equal(requests[1]['x-client-registration-id'], 'client-one');
    });

    it('retries the reconnect when bearerTokenSupplier throws', async function () {
        var calls = 0;
        var requests = await upgradeHeadersFor({
            bearerTokenSupplier: function () {
                calls += 1;
                if (calls === 2) {
                    throw new Error('token endpoint unavailable');
                }
                return 'token-' + calls;
            }
        }, true);
        assert.equal(requests.length, 2, 'did not retry the reconnect');
        assert.equal(calls, 3);
        assert.equal(requests[1].authorization, 'Bearer token-3');
        assert.equal(requests[1]['x-client-registration-id'], 'client-one');
    });
});

/*
 * A TLS client with no CA path downloads MockServer's CA certificate into the working directory. Each test
 * sends that download to a local HTTP server, from an empty working directory of its own; `body` handles it.
 */
async function withCaDownload(body, test) {
    var sockets = new Set();
    var server = http.createServer(body);
    server.on('connection', function (socket) {
        sockets.add(socket);
    });
    await new Promise(function (resolve) {
        server.listen(0, '127.0.0.1', resolve);
    });
    var originalRequest = https.request;
    // only the download: the WebSocket client's own wss request that follows goes out as usual
    https.request = function (options) {
        https.request = originalRequest;
        return http.request({host: '127.0.0.1', port: server.address().port, method: options.method, path: options.path});
    };
    var originalDirectory = process.cwd();
    var directory = fs.mkdtempSync(path.join(os.tmpdir(), 'mockserver-ca-download-'));
    process.chdir(directory);
    try {
        await test(directory);
    } finally {
        process.chdir(originalDirectory);
        https.request = originalRequest;
        sockets.forEach(function (socket) {
            socket.destroy();
        });
        await new Promise(function (resolve) {
            server.close(resolve);
        });
        fs.rmSync(directory, {recursive: true, force: true});
    }
}

var CA_PEM = '-----BEGIN CERTIFICATE-----\n' + 'A'.repeat(1000) + '\n-----END CERTIFICATE-----\n';

// the files left in the directory once any pending unlink has run
async function filesLeftIn(directory) {
    await sleep(200);
    return fs.readdirSync(directory).sort();
}

describe('downloading the CA certificate', {timeout: 60000}, function () {
    it('saves a complete download as CertificateAuthorityCertificate.pem and uses it', async function () {
        await withCaDownload(function (req, res) {
            res.writeHead(200, {'Content-Length': Buffer.byteLength(CA_PEM)});
            res.end(CA_PEM);
        }, async function (directory) {
            var port = await refusedPort();
            var outcome = await settledWithin(webSocketClient(true)('127.0.0.1', port, ''), SETTLE_DEADLINE_MILLIS);
            // the certificate was read, so the client went on to connect
            assert.deepEqual(outcome, {rejected: "Can't connect to MockServer running on host: \"127.0.0.1\" and port: \"" + port + "\""});
            assert.deepEqual(await filesLeftIn(directory), ['CertificateAuthorityCertificate.pem']);
            assert.equal(fs.readFileSync(path.join(directory, 'CertificateAuthorityCertificate.pem'), 'utf-8'), CA_PEM);
        });
    });

    it('rejects, and leaves no certificate file, when the download is cut off', async function () {
        await withCaDownload(function (req, res) {
            res.writeHead(200, {'Content-Length': Buffer.byteLength(CA_PEM)});
            res.write(CA_PEM.substring(0, 100), function () {
                setTimeout(function () {
                    res.socket.destroy();
                }, 50);
            });
        }, async function (directory) {
            var outcome = await settledWithin(webSocketClient(true)('127.0.0.1', await refusedPort(), ''), SETTLE_DEADLINE_MILLIS);
            assert.ok(outcome.rejected, JSON.stringify(outcome));
            assert.match(String(outcome.rejected), /^Fetching /);
            assert.deepEqual(await filesLeftIn(directory), []);
        });
    });

    it('rejects, and leaves no certificate file, when the registration times out part-way through the download', async function () {
        await withCaDownload(function (req, res) {
            res.writeHead(200, {'Content-Length': Buffer.byteLength(CA_PEM)});
            res.write(CA_PEM.substring(0, 100));
        }, async function (directory) {
            var opening = webSocketClient(true, undefined, {callbackWebSocketTimeoutMillis: 300})('127.0.0.1', await refusedPort(), '');
            var outcome = await settledWithin(opening, SETTLE_DEADLINE_MILLIS);
            assert.ok(outcome.rejected, JSON.stringify(outcome));
            assert.match(String(outcome.rejected), /client id within 300ms/);
            assert.deepEqual(await filesLeftIn(directory), []);
        });
    });
});

describe('registering a callback through the client', {timeout: 60000}, function () {
    var registrations = {
        'mockWithCallback': function (client) {
            return client.mockWithCallback({path: '/some'}, function () {
                return {statusCode: 200};
            });
        },
        'mockWithForwardCallback': function (client) {
            return client.mockWithForwardCallback({path: '/some'}, function (request) {
                return request;
            });
        },
        'mockWithForwardAndResponseCallback': function (client) {
            return client.mockWithForwardAndResponseCallback({path: '/some'}, function (request) {
                return request;
            }, function (request, response) {
                return response;
            });
        },
        'addRequestBreakpoint': function (client) {
            return client.addRequestBreakpoint({path: '/some'}, function (request) {
                return request;
            });
        }
    };

    Object.keys(registrations).forEach(function (name) {
        it(name + ' rejects when the WebSocket connection is refused', async function () {
            var port = await refusedPort();
            var client = mockServerClient('127.0.0.1', port);
            var outcome = await settledWithin(registrations[name](client), SETTLE_DEADLINE_MILLIS);
            assert.deepEqual(outcome, {rejected: "Can't connect to MockServer running on host: \"127.0.0.1\" and port: \"" + port + "\""});
        });

        it(name + ' rejects when the client id does not arrive in time', async function () {
            var server = await startFakeMockServer(function (request) {
                request.accept(null, request.origin);
            });
            try {
                var client = mockServerClient('127.0.0.1', server.port, undefined, false, undefined, {callbackWebSocketTimeoutMillis: 300});
                var outcome = await settledWithin(registrations[name](client), SETTLE_DEADLINE_MILLIS);
                assert.ok(outcome.rejected, JSON.stringify(outcome));
                assert.match(String(outcome.rejected), /client id within 300ms/);
                assert.deepEqual(server.expectationBodies, []);
            } finally {
                await server.close();
            }
        });
    });

    it('mockWithCallback registers its expectation with the client id MockServer sent', async function () {
        var server = await startFakeMockServer(function (request) {
            sendClientId(request.accept(null, request.origin), 'client-one');
        });
        var client = mockServerClient('127.0.0.1', server.port);
        try {
            var outcome = await settledWithin(registrations.mockWithCallback(client), SETTLE_DEADLINE_MILLIS);
            assert.ok(outcome.resolved, JSON.stringify(outcome));
            assert.equal(server.expectationBodies.length, 1);
            assert.equal(server.expectationBodies[0].httpResponseObjectCallback.clientId, 'client-one');
        } finally {
            await client.close();
            await server.close();
        }
    });
});

describe('a process whose callback registration failed', {timeout: 60000}, function () {
    var EXIT_DEADLINE_MILLIS = 15000;
    var REPORT = ".then(function () { console.log('resolved'); process.exit(2); }, function (reason) { console.log('rejected ' + reason); });";

    it('exits after the connection is refused', async function () {
        var port = await refusedPort();
        var result = await runProcess(
            "mockServer.mockServerClient('127.0.0.1', " + port + ").mockWithCallback({path: '/some'}, function () { return {statusCode: 200}; })" + REPORT,
            EXIT_DEADLINE_MILLIS);
        assert.equal(result.running, false, 'still running\n' + result.stdout + result.stderr);
        assert.equal(result.code, 0, result.stdout + result.stderr);
        assert.match(result.stdout, /^rejected Can't connect/m);
    });

    it('exits after the client id does not arrive in time, with MockServer still holding the connection', async function () {
        var server = await startFakeMockServer(function (request) {
            request.accept(null, request.origin);
        });
        try {
            var result = await runProcess(
                "mockServer.mockServerClient('127.0.0.1', " + server.port + ", undefined, false, undefined, {callbackWebSocketTimeoutMillis: 500})" +
                ".addRequestBreakpoint({path: '/some'}, function (request) { return request; })" + REPORT,
                EXIT_DEADLINE_MILLIS);
            assert.equal(result.running, false, 'still running\n' + result.stdout + result.stderr);
            assert.equal(result.code, 0, result.stdout + result.stderr);
            assert.match(result.stdout, /^rejected .*client id within 500ms/m);
        } finally {
            await server.close();
        }
    });

    it('exits after MockServer closes the connection before sending the client id', async function () {
        var server = await startFakeMockServer(function (request) {
            request.accept(null, request.origin).close();
        });
        try {
            var result = await runProcess(
                "mockServer.mockServerClient('127.0.0.1', " + server.port + ").mockWithForwardCallback({path: '/some'}, function (request) { return request; })" + REPORT,
                EXIT_DEADLINE_MILLIS);
            assert.equal(result.running, false, 'still running\n' + result.stdout + result.stderr);
            assert.equal(result.code, 0, result.stdout + result.stderr);
            assert.match(result.stdout, /^rejected .*closed the callback WebSocket before sending its client id/m);
        } finally {
            await server.close();
        }
    });
});
