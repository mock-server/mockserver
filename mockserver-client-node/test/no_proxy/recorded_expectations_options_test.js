'use strict';

// retrieveRecordedExpectations with recorded-expectation options, against a local HTTP server standing in for
// MockServer so the exact query string sent is asserted, and every rejection is proved to send nothing.

var { describe, it, before, after, beforeEach } = require('node:test');
var assert = require('node:assert/strict');
var http = require('http');
var mockServerClient = require('../../').mockServerClient;

var RETRIEVE_RECORDED = '/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON';

describe('recorded expectations options', function () {
    var server;
    var port;
    var received = [];
    var responseBody = '[]';

    before(function () {
        return new Promise(function (resolve) {
            server = http.createServer(function (req, res) {
                var data = '';
                req.on('data', function (chunk) {
                    data += chunk;
                });
                req.on('end', function () {
                    received.push({method: req.method, url: req.url, body: data});
                    res.writeHead(200, {'Content-Type': 'application/json', 'Connection': 'close'});
                    res.end(responseBody);
                });
            });
            server.listen(0, '127.0.0.1', function () {
                port = server.address().port;
                resolve();
            });
        });
    });

    after(function () {
        server.close();
    });

    beforeEach(function () {
        received.length = 0;
        responseBody = '[]';
    });

    var client = function () {
        return mockServerClient('127.0.0.1', port);
    };

    it('sends every option, with a request matcher', async function () {
        await client().retrieveRecordedExpectations('/jobs/.*', {
            consolidate: true,
            parameterize: true,
            repeatsAsScenarios: true,
            captureHeaders: ['X-Tenant', 'Accept'],
            extractBodiesLargerThan: 1024
        });

        assert.equal(received.length, 1);
        assert.equal(received[0].method, 'PUT');
        assert.equal(received[0].url, RETRIEVE_RECORDED +
            '&consolidate=true&parameterize=true&repeatsAsScenarios=true&captureHeaders=X-Tenant%2CAccept&extractBodiesLargerThan=1024');
        assert.deepEqual(JSON.parse(received[0].body), {path: '/jobs/.*'});
    });

    it('sends an empty captureHeaders, which keeps no headers', async function () {
        await client().retrieveRecordedExpectations(undefined, {captureHeaders: []});

        assert.equal(received[0].url, RETRIEVE_RECORDED + '&captureHeaders=');
    });

    it('sends extractBodiesLargerThan of zero', async function () {
        await client().retrieveRecordedExpectations(undefined, {extractBodiesLargerThan: 0});

        assert.equal(received[0].url, RETRIEVE_RECORDED + '&extractBodiesLargerThan=0');
    });

    it('sends nothing extra for false or absent options', async function () {
        await client().retrieveRecordedExpectations(undefined, {repeatsAsScenarios: false, consolidate: false});
        await client().retrieveRecordedExpectations(undefined, {});

        assert.equal(received[0].url, RETRIEVE_RECORDED);
        assert.equal(received[1].url, RETRIEVE_RECORDED);
    });

    it('returns the expectations the server sent', async function () {
        var expectation = {
            httpRequest: {method: 'GET', path: '/jobs/1'},
            httpResponses: [{statusCode: 200, body: 'pending'}, {statusCode: 200, body: 'done'}],
            responseMode: 'SWITCH',
            times: {unlimited: true}
        };
        responseBody = JSON.stringify([expectation]);

        var expectations = await client().retrieveRecordedExpectations(undefined, {repeatsAsScenarios: true});

        assert.deepEqual(expectations, [expectation]);
    });

    it('leaves paging unchanged', async function () {
        await client().retrieveRecordedExpectations(undefined, 5, 10);

        assert.equal(received[0].url, RETRIEVE_RECORDED + '&limit=10&offset=5');
    });

    describe('rejects, sending nothing', function () {
        var rejections = {
            'a non-boolean repeatsAsScenarios': {repeatsAsScenarios: 'true'},
            'a non-boolean consolidate': {consolidate: 1},
            'a string captureHeaders': {captureHeaders: 'X-Tenant'},
            'a blank captureHeaders entry': {captureHeaders: ['X-Tenant', ' ']},
            'a captureHeaders entry with a comma': {captureHeaders: ['X-Tenant,Accept']},
            'a non-string captureHeaders entry': {captureHeaders: [42]},
            'a negative extractBodiesLargerThan': {extractBodiesLargerThan: -1},
            'a fractional extractBodiesLargerThan': {extractBodiesLargerThan: 1.5},
            'a string extractBodiesLargerThan': {extractBodiesLargerThan: '1024'},
            'an unknown option': {repeatAsScenarios: true}
        };
        Object.keys(rejections).forEach(function (name) {
            it(name, function () {
                var c = client();
                assert.throws(function () {
                    c.retrieveRecordedExpectations(undefined, rejections[name]);
                }, Error);
                assert.equal(received.length, 0);
            });
        });

        it('a limit with an options object', function () {
            var c = client();
            assert.throws(function () {
                c.retrieveRecordedExpectations(undefined, {repeatsAsScenarios: true}, 10);
            }, Error);
            assert.equal(received.length, 0);
        });
    });
});
