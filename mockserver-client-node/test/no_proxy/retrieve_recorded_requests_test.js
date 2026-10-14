'use strict';

// retrieveRecordedRequests against a local HTTP server standing in for
// MockServer, so the filter sent and the recorded requests returned are both
// under the test's control.

var { describe, it } = require('node:test');
var assert = require('node:assert/strict');
var http = require('http');
var mockServerClient = require('../../').mockServerClient;

function startStubServer(body) {
    return new Promise(function (resolve) {
        var received = [];
        var server = http.createServer(function (req, res) {
            var data = '';
            req.on('data', function (chunk) {
                data += chunk;
            });
            req.on('end', function () {
                received.push({method: req.method, url: req.url, body: data});
                res.writeHead(200, {'Content-Type': 'application/json', 'Connection': 'close'});
                res.end(body);
            });
        });
        server.listen(0, '127.0.0.1', function () {
            resolve({server: server, port: server.address().port, received: received});
        });
    });
}

describe('retrieveRecordedRequests', function () {
    it('sends a DNS filter and returns the recorded DNS queries', async function () {
        var stub = await startStubServer(JSON.stringify([
            {dnsName: 'api.example.com', dnsType: 'A', dnsClass: 'IN'}
        ]));
        try {
            var requests = await mockServerClient('127.0.0.1', stub.port)
                .retrieveRecordedRequests({dnsName: 'api.example.com', dnsType: 'A'});

            assert.equal(stub.received.length, 1);
            assert.equal(stub.received[0].method, 'PUT');
            assert.equal(stub.received[0].url, '/mockserver/retrieve?type=REQUESTS&format=JSON');
            assert.deepEqual(JSON.parse(stub.received[0].body), {dnsName: 'api.example.com', dnsType: 'A'});
            assert.equal(requests.length, 1);
            assert.equal(requests[0].dnsName, 'api.example.com');
            assert.equal(requests[0].dnsType, 'A');
            assert.equal(requests[0].dnsClass, 'IN');
        } finally {
            stub.server.close();
        }
    });
});
