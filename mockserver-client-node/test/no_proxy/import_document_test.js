'use strict';

/*
 * importDocument resolves the upserted expectations for both reply shapes of
 * PUT /mockserver/import: the plain array (har, postman, pact) and the
 * {"expectations":[...],"warnings":[...]} object the wiremock, mountebank and
 * mockoon importers reply with, whose warnings it writes with console.warn.
 * The HTTP transport (sendRequest.js) is stubbed.
 */

var { describe, it, beforeEach, afterEach } = require('node:test');
var assert = require('node:assert/strict');

var sendRequestModule = require('../../sendRequest');
var mockServerClient = require('../../').mockServerClient;

var captured;
var warned;
var originalSend;
var originalWarn;

function replyWith(responseBody) {
    return function () {
        return function (host, port, path) {
            captured.push(path);
            return Promise.resolve({ statusCode: 201, body: responseBody });
        };
    };
}

function clientReplying(responseBody) {
    sendRequestModule.sendRequest = replyWith(responseBody);
    return mockServerClient('localhost', 1080);
}

beforeEach(function () {
    captured = [];
    warned = [];
    originalSend = sendRequestModule.sendRequest;
    originalWarn = console.warn;
    console.warn = function (message) {
        warned.push(message);
    };
});

afterEach(function () {
    sendRequestModule.sendRequest = originalSend;
    console.warn = originalWarn;
});

describe('client.importDocument', function () {
    var expectation = { httpRequest: { path: '/api/xml' }, httpResponse: { statusCode: 200, body: 'xml-ok' } };

    it('resolves the expectations of a reply that carries warnings, and warns about each warning', async function () {
        var client = clientReplying(JSON.stringify({
            expectations: [expectation],
            warnings: [{ item: 'GET /api/xml', construct: 'response.transformers', detail: 'response templating is not translated' }]
        }));

        var result = await client.importDocument({ mappings: [] }, 'wiremock');

        assert.deepEqual(result, [expectation]);
        assert.equal(captured[0], '/mockserver/import?format=wiremock');
        assert.equal(warned.length, 1);
        assert.match(warned[0], /wiremock/);
        assert.match(warned[0], /\[GET \/api\/xml\] response\.transformers: response templating is not translated/);
    });

    it('resolves the expectations of a reply with no warnings without warning', async function () {
        var client = clientReplying(JSON.stringify({ expectations: [expectation], warnings: [] }));

        var result = await client.importDocument({ routes: [] }, 'mockoon');

        assert.deepEqual(result, [expectation]);
        assert.equal(warned.length, 0);
    });

    it('rejects the recording format without sending a request', function () {
        var client = clientReplying('[]');

        ['recording', 'Recording', ' RECORDING '].forEach(function (format) {
            assert.throws(function () {
                client.importDocument('{"httpRequest":{}}', format);
            }, /PUT \/mockserver\/import\?format=recording/);
        });
        assert.equal(captured.length, 0);
    });

    it('rejects with the reply when an object reply has no expectations array', async function () {
        var client = clientReplying('{"error":"upstream unavailable"}');

        await assert.rejects(Promise.resolve(client.importDocument({ mappings: [] }, 'wiremock')), function (err) {
            assert.match(String(err && err.message), /\{"error":"upstream unavailable"\}/);
            return true;
        });
    });

    it('resolves the expectations of a reply that is an array', async function () {
        var client = clientReplying(JSON.stringify([expectation]));

        var result = await client.importHar({ log: { entries: [] } });

        assert.deepEqual(result, [expectation]);
        assert.equal(captured[0], '/mockserver/import?format=har');
        assert.equal(warned.length, 0);
    });
});
