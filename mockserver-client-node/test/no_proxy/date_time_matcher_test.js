'use strict';

/*
 * Date-time matcher object form (e.g. { after: 'now -1 hours' }) in header,
 * query-string parameter, path parameter and cookie values. The client has no
 * runtime model for these values, so the invariant is that every code path that
 * touches the header/cookie/parameter maps passes them to the server unchanged,
 * and that a plain string such as "before:2026-01-01" stays a plain string.
 *
 * The HTTP transport (sendRequest.js) is mocked, as in when_dsl_test.js; no
 * running server is needed.
 */

var { describe, it, beforeEach, afterEach } = require('node:test');
var assert = require('node:assert/strict');
var fs = require('fs');
var path = require('path');

var sendRequestModule = require('../../sendRequest');
var mockServerClient = require('../../').mockServerClient;

process.on('unhandledRejection', function () {});

// test/no_proxy -> test -> mockserver-client-node -> <repo root>
var FIXTURE_PATH = path.resolve(__dirname, '..', '..', '..', 'test-fixtures', 'expectations',
    'matcher_request_date_time.json');

function loadFixture() {
    return JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf8'));
}

var captured;

function makeStub(method) {
    return function () {
        return function (host, port, urlPath, jsonBody) {
            captured.push({
                method: method,
                path: urlPath,
                // serialised exactly as sendRequest.js would put it on the wire
                body: (typeof jsonBody === 'string' || jsonBody === undefined)
                    ? jsonBody
                    : JSON.parse(JSON.stringify(jsonBody))
            });
            return Promise.resolve({statusCode: 201, body: JSON.stringify({})});
        };
    };
}

var originalSend, originalGet, originalDelete, originalBinary;

beforeEach(function () {
    captured = [];
    originalSend = sendRequestModule.sendRequest;
    originalGet = sendRequestModule.sendGetRequest;
    originalDelete = sendRequestModule.sendDeleteRequest;
    originalBinary = sendRequestModule.sendBinaryRequest;
    sendRequestModule.sendRequest = makeStub('PUT');
    sendRequestModule.sendGetRequest = makeStub('GET');
    sendRequestModule.sendDeleteRequest = makeStub('DELETE');
    sendRequestModule.sendBinaryRequest = makeStub('PUT');
});

afterEach(function () {
    sendRequestModule.sendRequest = originalSend;
    sendRequestModule.sendGetRequest = originalGet;
    sendRequestModule.sendDeleteRequest = originalDelete;
    sendRequestModule.sendBinaryRequest = originalBinary;
});

describe('date-time matcher values', function () {

    it('mockAnyResponse sends the shared fixture unchanged', async function () {
        await mockServerClient('localhost', 1080).mockAnyResponse(loadFixture());

        assert.equal(captured.length, 1);
        assert.equal(captured[0].path, '/mockserver/expectation');
        assert.deepEqual(captured[0].body, loadFixture());
    });

    it('keeps a plain "before:..." string as a literal string', async function () {
        await mockServerClient('localhost', 1080).mockAnyResponse(loadFixture());

        assert.deepEqual(captured[0].body.httpRequest.headers['X-Plain'], ['before:2026-01-01']);
        assert.equal(typeof captured[0].body.httpRequest.headers['X-Plain'][0], 'string');
    });

    it('writes only the options that are set', async function () {
        await mockServerClient('localhost', 1080).mockAnyResponse(loadFixture());

        var headers = captured[0].body.httpRequest.headers;
        assert.deepEqual(Object.keys(headers['X-Issued-At'][0]), ['after']);
        assert.deepEqual(headers['If-Modified-Since'][0], {before: '2026-10-21T00:00:00Z', actualFormat: 'rfc1123'});
        assert.deepEqual(captured[0].body.httpRequest.cookies.seen, {
            not: true,
            equalToDateTime: 'now +1 days',
            truncateExpected: 'first day of month',
            truncateActual: 'startOfDay',
            applyTruncationLast: true
        });
    });

    it('keeps date-time values when default request headers are merged in', async function () {
        var client = mockServerClient('localhost', 1080);
        client.setDefaultHeaders(null, [{name: 'sessionId', values: ['abc']}]);

        await client.mockAnyResponse(loadFixture());

        var expected = loadFixture();
        expected.httpRequest.headers.sessionId = ['abc'];
        assert.deepEqual(captured[0].body.httpRequest, expected.httpRequest);
    });

    it('keeps date-time values in the array header form and in path parameters', async function () {
        await mockServerClient('localhost', 1080)
            .when({
                path: '/orders/{placed}',
                pathParameters: {placed: [{before: 'now', zone: 'Europe/London'}]},
                headers: [{name: 'X-Issued-At', values: [{after: 'now -1 hours'}, 'literal']}]
            })
            .respond({statusCode: 200});

        var request = captured[0].body.httpRequest;
        assert.deepEqual(request.pathParameters, {placed: [{before: 'now', zone: 'Europe/London'}]});
        assert.deepEqual(request.headers, [{name: 'X-Issued-At', values: [{after: 'now -1 hours'}, 'literal']}]);
    });

    it('verify sends date-time values unchanged', async function () {
        var request = loadFixture().httpRequest;

        await mockServerClient('localhost', 1080).verify(request, 1);

        assert.equal(captured.length, 1);
        assert.equal(captured[0].path, '/mockserver/verify');
        assert.deepEqual(captured[0].body.httpRequest, loadFixture().httpRequest);
    });
});
