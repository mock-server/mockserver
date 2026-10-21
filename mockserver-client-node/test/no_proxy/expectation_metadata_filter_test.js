'use strict';

// Expectation metadata, retrieve / clear by metadata and unmatched, and paging, against a local
// HTTP server standing in for MockServer so the exact query string and body sent are asserted.

var { describe, it, before, after, beforeEach } = require('node:test');
var assert = require('node:assert/strict');
var fs = require('fs');
var http = require('http');
var path = require('path');
var mockServerClient = require('../../').mockServerClient;

var FIXTURE_PATH = path.resolve(__dirname, '..', '..', '..', 'test-fixtures', 'expectations', 'top_metadata.json');

describe('expectation metadata and filters', function () {
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

    var RETRIEVE = '/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=JSON';
    var CLEAR = '/mockserver/clear?type=expectations';

    describe('metadataValue encoding', function () {
        [
            ['!team-a', '%21team-a'],
            ['?x', '%3Fx'],
            ['100%', '100%25'],
            ['a+b', 'a%2Bb'],
            ['a b', 'a%20b'],
            ['x&y#z=1', 'x%26y%23z%3D1'],
            ["(it's)*", '%28it%27s%29%2A'],
            ['!not?optional 100%+a b', '%21not%3Foptional%20100%25%2Ba%20b']
        ].forEach(function (testCase) {
            it('retrieve sends ' + JSON.stringify(testCase[0]) + ' literally', async function () {
                await client().retrieveActiveExpectationsByMetadata('owner', testCase[0]);

                assert.equal(received.length, 1);
                assert.equal(received[0].method, 'PUT');
                assert.equal(received[0].url, RETRIEVE + '&metadataKey=owner&metadataValue=' + testCase[1]);
            });

            it('clear sends ' + JSON.stringify(testCase[0]) + ' literally', async function () {
                await client().clearByMetadata('owner', testCase[0]);

                assert.equal(received.length, 1);
                assert.equal(received[0].url, CLEAR + '&metadataKey=owner&metadataValue=' + testCase[1]);
            });
        });

        it('encodes the metadata key too', async function () {
            await client().retrieveActiveExpectationsByMetadataKey('!a b&c');

            assert.equal(received[0].url, RETRIEVE + '&metadataKey=%21a%20b%26c');
        });

        [
            [42, '42'],
            [0.5, '0.5'],
            [true, 'true'],
            [false, 'false'],
            [['a', 'b'], '%5B%22a%22%2C%22b%22%5D'],
            [{team: 'checkout'}, '%7B%22team%22%3A%22checkout%22%7D'],
            ['null', 'null']
        ].forEach(function (testCase) {
            it('sends ' + JSON.stringify(testCase[0]) + ' (' + typeof testCase[0] + ') as ' + testCase[1], async function () {
                await client().retrieveActiveExpectations(undefined, {metadataKey: 'k', metadataValue: testCase[0]});

                assert.equal(received[0].url, RETRIEVE + '&metadataKey=k&metadataValue=' + testCase[1]);
            });
        });
    });

    describe('retrieve', function () {
        it('by metadata key sends no metadataValue', async function () {
            await client().retrieveActiveExpectationsByMetadataKey('owner');

            assert.equal(received[0].url, RETRIEVE + '&metadataKey=owner');
            assert.deepEqual(JSON.parse(received[0].body), {});
        });

        it('unmatched', async function () {
            await client().retrieveUnmatchedExpectations();

            assert.equal(received[0].url, RETRIEVE + '&unmatched=true');
        });

        it('with every filter option and a request matcher', async function () {
            await client().retrieveActiveExpectations('/some/path', {
                metadataKey: 'owner',
                metadataValue: 'team-a',
                metadataJsonPath: '$.tags[?(@ == "smoke")]',
                unmatched: true,
                limit: 10,
                offset: 0
            });

            assert.equal(received[0].url, RETRIEVE +
                '&metadataKey=owner&metadataValue=team-a' +
                '&metadataJsonPath=%24.tags%5B%3F%28%40%20%3D%3D%20%22smoke%22%29%5D' +
                '&unmatched=true&limit=10&offset=0');
            assert.deepEqual(JSON.parse(received[0].body), {path: '/some/path'});
        });

        it('unmatched: false and absent paging add nothing', async function () {
            await client().retrieveActiveExpectations(undefined, {unmatched: false, limit: undefined, offset: null});

            assert.equal(received[0].url, RETRIEVE);
        });

        it('recorded expectations page', async function () {
            await client().retrieveRecordedExpectations(undefined, 5, 10);

            assert.equal(received[0].url, '/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON&limit=10&offset=5');
        });

        it('returns metadata exactly as the server sent it', async function () {
            var fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf8'));
            responseBody = JSON.stringify([fixture]);

            var expectations = await client().retrieveActiveExpectationsByMetadata('owner', 'team-a');

            assert.deepEqual(expectations, [fixture]);
            assert.strictEqual(expectations[0].metadata.note, null);
            assert.strictEqual(expectations[0].metadata.ticket, 4211);
        });

        it('leaves the existing retrieve methods unchanged', async function () {
            await client().retrieveActiveExpectations('/x');
            await client().retrieveRecordedExpectations('/x');

            assert.equal(received[0].url, RETRIEVE);
            assert.equal(received[1].url, '/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON');
            assert.deepEqual(JSON.parse(received[1].body), {path: '/x'});
        });
    });

    describe('clear', function () {
        it('by metadata key sends no metadataValue', async function () {
            await client().clearByMetadataKey('owner');

            assert.equal(received[0].url, CLEAR + '&metadataKey=owner');
        });

        it('unmatched', async function () {
            await client().clearUnmatchedExpectations();

            assert.equal(received[0].url, CLEAR + '&unmatched=true');
        });

        it('by JSONPath with a request matcher', async function () {
            await client().clear('/some/path', {metadataJsonPath: '$.owner'});

            assert.equal(received[0].url, CLEAR + '&metadataJsonPath=%24.owner');
            assert.deepEqual(JSON.parse(received[0].body), {path: '/some/path'});
        });

        it('with an explicit filter through clearWithFilter', async function () {
            await client().clearWithFilter('/some/path', {metadataKey: 'owner', metadataValue: '!team-a'});

            assert.equal(received[0].url, CLEAR + '&metadataKey=owner&metadataValue=%21team-a');
            assert.deepEqual(JSON.parse(received[0].body), {path: '/some/path'});
        });

        it('leaves the existing clear unchanged', async function () {
            await client().clear('/x', 'EXPECTATIONS');
            await client().clear('/x');

            assert.equal(received[0].url, '/mockserver/clear?type=EXPECTATIONS');
            assert.equal(received[1].url, '/mockserver/clear');
        });
    });

    describe('rejects before sending anything', function () {
        var rejections = {
            'a blank metadata key on retrieve': function (c) {
                return c.retrieveActiveExpectationsByMetadataKey('  ');
            },
            'an undefined filter on clearWithFilter': function (c) {
                return c.clearWithFilter('/x', undefined);
            },
            'a null filter on clearWithFilter': function (c) {
                return c.clearWithFilter('/x', null);
            },
            'a string type on clearWithFilter': function (c) {
                return c.clearWithFilter('/x', 'EXPECTATIONS');
            },
            'an empty filter on clearWithFilter': function (c) {
                return c.clearWithFilter('/x', {});
            },
            'an empty metadata key on clear': function (c) {
                return c.clearByMetadataKey('');
            },
            'a null metadata key on clear': function (c) {
                return c.clearByMetadataKey(null);
            },
            'an undefined metadata key on clear by value': function (c) {
                return c.clearByMetadata(undefined, 'team-a');
            },
            'a null metadata value on clear': function (c) {
                return c.clearByMetadata('owner', null);
            },
            'a missing metadata value on clear': function (c) {
                return c.clearByMetadata('owner');
            },
            'a null metadata value on retrieve': function (c) {
                return c.retrieveActiveExpectationsByMetadata('owner', null);
            },
            'an explicitly undefined metadataValue in a clear filter': function (c) {
                return c.clear(undefined, {metadataKey: 'owner', metadataValue: undefined});
            },
            'a NaN metadata value': function (c) {
                return c.clearByMetadata('owner', NaN);
            },
            'a function metadata value': function (c) {
                return c.clearByMetadata('owner', function () {
                });
            },
            'a metadataValue without a metadataKey': function (c) {
                return c.retrieveActiveExpectations(undefined, {metadataValue: 'team-a'});
            },
            'a blank JSONPath': function (c) {
                return c.clear(undefined, {metadataJsonPath: ' '});
            },
            'a null JSONPath': function (c) {
                return c.retrieveActiveExpectations(undefined, {metadataJsonPath: null});
            },
            'a clear filter that selects nothing': function (c) {
                return c.clear(undefined, {});
            },
            'a clear filter with unmatched: false only': function (c) {
                return c.clear(undefined, {unmatched: false});
            },
            'a clear with a limit': function (c) {
                return c.clear(undefined, {unmatched: true, limit: 10});
            },
            'a clear with offset 0': function (c) {
                return c.clear(undefined, {metadataKey: 'owner', offset: 0});
            },
            'a negative offset': function (c) {
                return c.retrieveActiveExpectations(undefined, {offset: -1});
            },
            'a negative limit on recorded expectations': function (c) {
                return c.retrieveRecordedExpectations(undefined, 0, -1);
            },
            'a fractional limit': function (c) {
                return c.retrieveActiveExpectations(undefined, {limit: 1.5});
            },
            'a string offset': function (c) {
                return c.retrieveRecordedExpectations(undefined, '5', 10);
            },
            'a non-boolean unmatched': function (c) {
                return c.clear(undefined, {unmatched: 'true'});
            },
            'an unknown filter property': function (c) {
                return c.clear(undefined, {metadataKey: 'owner', unmatch: true});
            },
            'an array filter': function (c) {
                return c.clear(undefined, ['owner']);
            }
        };
        Object.keys(rejections).forEach(function (name) {
            it(name, function () {
                var c = client();
                assert.throws(function () {
                    rejections[name](c);
                }, Error);
                assert.equal(received.length, 0);
            });
        });
    });

    describe('creating expectations with metadata', function () {
        it('sends a fixture with metadata verbatim', async function () {
            var fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf8'));

            await client().mockAnyResponse(JSON.parse(JSON.stringify(fixture)));

            assert.equal(received[0].url, '/mockserver/expectation');
            assert.deepEqual(JSON.parse(received[0].body).metadata, fixture.metadata);
        });

        it('when(...).withMetadata(...) adds entries and an object', async function () {
            await client().when('/some/path')
                .withMetadata({ticket: 4211, note: null})
                .withMetadata('owner', 'team-a')
                .withMetadata('tags', ['smoke'])
                .respond({statusCode: 200});

            assert.deepEqual(JSON.parse(received[0].body).metadata, {ticket: 4211, note: null, owner: 'team-a', tags: ['smoke']});
        });

        it('when(...).withMetadata(null) removes metadata', async function () {
            await client().when('/some/path')
                .withMetadata('owner', 'team-a')
                .withMetadata(null)
                .respond({statusCode: 200});

            assert.equal('metadata' in JSON.parse(received[0].body), false);
        });

        it('when(...) without metadata sends no metadata field', async function () {
            await client().when('/some/path').respond({statusCode: 200});

            assert.equal('metadata' in JSON.parse(received[0].body), false);
        });

        it('when(...).withMetadata rejects a blank key, an undefined value and an array', function () {
            var chain = client().when('/some/path');
            assert.throws(function () {
                chain.withMetadata(' ', 'x');
            }, Error);
            assert.throws(function () {
                chain.withMetadata('owner', undefined);
            }, Error);
            assert.throws(function () {
                chain.withMetadata(['owner']);
            }, Error);
            assert.equal(received.length, 0);
        });
    });
});
