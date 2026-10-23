'use strict';

/*
 * Date-time comparison on a JSON_PATH or XPATH body ({ type: 'JSON_PATH', jsonPath,
 * dateTime: { after: 'now -1 days' } }). The client has no runtime body model, so the
 * invariant is that the body reaches the server unchanged.
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
var FIXTURES_DIR = path.resolve(__dirname, '..', '..', '..', 'test-fixtures', 'expectations');

function loadFixture(name) {
    return JSON.parse(fs.readFileSync(path.join(FIXTURES_DIR, name), 'utf8'));
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

describe('body date-time comparisons', function () {

    ['matcher_body_json_path_date_time.json', 'matcher_body_xpath_date_time.json'].forEach(function (name) {
        it('mockAnyResponse sends ' + name + ' unchanged', async function () {
            await mockServerClient('localhost', 1080).mockAnyResponse(loadFixture(name));

            assert.equal(captured.length, 1);
            assert.deepEqual(captured[0].body, loadFixture(name));
        });

        it('verify sends the ' + name + ' body unchanged', async function () {
            await mockServerClient('localhost', 1080).verify(loadFixture(name).httpRequest, 1);

            assert.deepEqual(captured[0].body.httpRequest.body, loadFixture(name).httpRequest.body);
        });
    });

    it('when(...).respond(...) keeps a body dateTime', async function () {
        var body = {type: 'JSON_PATH', jsonPath: '$.order.created', dateTime: {after: 'now -1 days'}};

        await mockServerClient('localhost', 1080).when({path: '/orders', body: body}).respond({statusCode: 200});

        assert.deepEqual(captured[0].body.httpRequest.body, body);
    });
});
