'use strict';

/*
 * Pure unit tests for the A2A (Agent-to-Agent) mock builder.
 *
 * These assert the exact expectation JSON produced by a2aMock(...).build(),
 * proving the Node client emits the same wire contract as the Java client's
 * A2aMockBuilder:
 *   - a static agent-card GET (httpResponse with the agent JSON document),
 *   - JSON-RPC tasks/send, tasks/get and tasks/cancel over POST rendered with a
 *     VELOCITY httpResponseTemplate echoing the inbound id via
 *     $!{request.jsonRpcRawId},
 *   - custom tasks/send handlers matched by a JSON_PATH regex,
 *   - optional SSE streaming (httpSseResponse) and push-notification config +
 *     delivery (httpOverrideForwardedRequest).
 *
 * No live server is required — the builder is a pure function.
 */

var { describe, it } = require('node:test');
var assert = require('node:assert/strict');

// Require the builder both directly and via the index/client surface so the
// public wiring is covered too.
var a2aMock = require('../../a2aMockBuilder').a2aMock;
var indexA2aMock = require('../../index').a2aMock;
var mockServerClient = require('../../index').mockServerClient;

// ---------- helpers ----------

function findByJsonRpc(expectations, method) {
    return expectations.filter(function (e) {
        return e.httpRequest && e.httpRequest.body &&
            e.httpRequest.body.type === 'JSON_RPC' && e.httpRequest.body.method === method;
    });
}

function findByJsonPath(expectations) {
    return expectations.filter(function (e) {
        return e.httpRequest && e.httpRequest.body && e.httpRequest.body.type === 'JSON_PATH';
    });
}

function agentCard(expectations) {
    return expectations.filter(function (e) {
        return e.httpRequest && e.httpRequest.method === 'GET';
    })[0];
}

// =========================================================================
// public wiring
// =========================================================================

describe('a2aMock wiring', function () {
    it('is exported from the package index', function () {
        assert.equal(typeof a2aMock, 'function');
        assert.equal(typeof indexA2aMock, 'function');
    });

    it('is exposed as a method on a client instance', function () {
        var client = mockServerClient('localhost', 1080);
        assert.equal(typeof client.a2aMock, 'function');
        var builder = client.a2aMock('/a2a');
        assert.equal(typeof builder.withSkill, 'function');
        assert.equal(typeof builder.onTaskSend, 'function');
        assert.equal(typeof builder.build, 'function');
        assert.equal(typeof builder.applyTo, 'function');
    });

    it('defaults the agent path to /a2a and the card path to /.well-known/agent.json', function () {
        var expectations = a2aMock().build();
        var card = agentCard(expectations);
        assert.equal(card.httpRequest.path, '/.well-known/agent.json');
        // every JSON-RPC task method is mounted on /a2a
        findByJsonRpc(expectations, 'tasks/send').concat(
            findByJsonRpc(expectations, 'tasks/get'),
            findByJsonRpc(expectations, 'tasks/cancel')
        ).forEach(function (e) {
            assert.equal(e.httpRequest.path, '/a2a');
        });
    });
});

// =========================================================================
// expectation counts (mirror the Java A2aMockBuilderTest)
// =========================================================================

describe('a2aMock expectation set', function () {
    it('builds a minimal mock of 4 expectations (card + send + get + cancel)', function () {
        assert.equal(a2aMock().build().length, 4);
        assert.equal(a2aMock('/custom/a2a').build().length, 4);
    });

    it('places custom task handlers between the agent card and the default handlers', function () {
        var expectations = a2aMock()
            .onTaskSend().matchingMessage('custom.*').respondingWith('Custom response').and()
            .build();
        // card + custom + send + get + cancel
        assert.equal(expectations.length, 5);
        assert.ok(expectations[0].httpResponse, 'agent card is first');
        assert.equal(expectations[1].httpRequest.body.type, 'JSON_PATH');
        assert.ok(expectations[1].httpResponseTemplate.template.indexOf('Custom response') !== -1);
        assert.equal(expectations[2].httpRequest.body.method, 'tasks/send');
    });

    it('skills do not change the expectation count', function () {
        var expectations = a2aMock()
            .withSkill('translate').withName('Translation').withTag('i18n').withExample('Translate hello').and()
            .withSkill('summarize').withName('Summarization').and()
            .build();
        assert.equal(expectations.length, 4);
    });

    it('each custom task handler adds one expectation', function () {
        var expectations = a2aMock()
            .onTaskSend().matchingMessage('translate.*').respondingWith('Hola').and()
            .onTaskSend().matchingMessage('summarize.*').respondingWith('Brief').and()
            .build();
        // card + 2 handlers + send + get + cancel
        assert.equal(expectations.length, 6);
    });
});

// =========================================================================
// agent card
// =========================================================================

describe('a2aMock agent card', function () {
    it('serves a static 200 JSON document (not a template)', function () {
        var card = agentCard(a2aMock().withAgentName('TestAgent').withAgentVersion('2.0.0').build());
        assert.equal(card.httpResponseTemplate, undefined);
        assert.equal(card.httpResponse.statusCode, 200);
        assert.deepEqual(card.httpResponse.headers, [{ name: 'Content-Type', values: ['application/json'] }]);
        assert.ok(card.httpResponse.body.indexOf('"name": "TestAgent"') !== -1);
        assert.ok(card.httpResponse.body.indexOf('"version": "2.0.0"') !== -1);
    });

    it('defaults the agent url to http://localhost + path when not set', function () {
        var card = agentCard(a2aMock('/agent').build());
        assert.ok(card.httpResponse.body.indexOf('"url": "http://localhost/agent"') !== -1);
    });

    it('honours an explicit agent url', function () {
        var card = agentCard(a2aMock('/agent').withAgentUrl('http://localhost:8080/agent').build());
        assert.ok(card.httpResponse.body.indexOf('"url": "http://localhost:8080/agent"') !== -1);
    });

    it('supports a custom agent card path', function () {
        var card = agentCard(a2aMock().withAgentCardPath('/agent-card').build());
        assert.equal(card.httpRequest.path, '/agent-card');
    });

    it('includes the full skill object (id, name, description, tags, examples)', function () {
        var card = agentCard(a2aMock()
            .withSkill('translate')
            .withName('Translation')
            .withDescription('Translates text')
            .withTag('i18n')
            .withExample('Translate hello to French')
            .and()
            .build());
        var body = card.httpResponse.body;
        assert.ok(body.indexOf('"id": "translate"') !== -1);
        assert.ok(body.indexOf('"name": "Translation"') !== -1);
        assert.ok(body.indexOf('"description": "Translates text"') !== -1);
        assert.ok(body.indexOf('"tags": ["i18n"]') !== -1);
        assert.ok(body.indexOf('"examples": ["Translate hello to French"]') !== -1);
    });

    it('falls back the skill name to its id when withName is omitted', function () {
        var card = agentCard(a2aMock().withSkill('translate').and().build());
        assert.ok(card.httpResponse.body.indexOf('{"id": "translate", "name": "translate"}') !== -1);
    });

    it('advertises capabilities (streaming/pushNotifications) off by default', function () {
        var body = agentCard(a2aMock().build()).httpResponse.body;
        assert.ok(body.indexOf('"capabilities": {"streaming": false, "pushNotifications": false, "stateTransitionHistory": false}') !== -1);
    });
});

// =========================================================================
// task method templates
// =========================================================================

describe('a2aMock task method responses', function () {
    it('renders tasks/send as the exact VELOCITY JSON-RPC template', function () {
        var send = findByJsonRpc(a2aMock().build(), 'tasks/send')[0];
        assert.deepEqual(send.httpRequest, {
            method: 'POST',
            path: '/a2a',
            body: { type: 'JSON_RPC', method: 'tasks/send' }
        });
        assert.equal(send.httpResponseTemplate.templateType, 'VELOCITY');
        assert.equal(
            send.httpResponseTemplate.template,
            '{"statusCode": 200, ' +
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], ' +
            '"body": {"jsonrpc": "2.0", "result": ' +
            '{"id": "mock-task-id", "status": {"state": "completed"}, ' +
            '"artifacts": [{"parts": [{"type": "text", "text": "Task completed successfully"}]}]}' +
            ', "id": $!{request.jsonRpcRawId}}}'
        );
    });

    it('renders tasks/cancel with a canceled task state', function () {
        var cancel = findByJsonRpc(a2aMock().build(), 'tasks/cancel')[0];
        assert.ok(cancel.httpResponseTemplate.template.indexOf('"status": {"state": "canceled"}') !== -1);
    });

    it('uses the configured default task response', function () {
        var send = findByJsonRpc(a2aMock().withDefaultTaskResponse('Custom default response').build(), 'tasks/send')[0];
        assert.ok(send.httpResponseTemplate.template.indexOf('Custom default response') !== -1);
    });

    it('produces the exact JSON_PATH matcher and template for a custom handler', function () {
        var handler = findByJsonPath(a2aMock()
            .onTaskSend().matchingMessage('translate.*').respondingWith('Hola').and()
            .build())[0];
        assert.deepEqual(handler.httpRequest, {
            method: 'POST',
            path: '/a2a',
            body: {
                type: 'JSON_PATH',
                jsonPath: "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /translate.*/)]"
            }
        });
        assert.ok(handler.httpResponseTemplate.template.indexOf('"text": "Hola"') !== -1);
    });

    it('flags an error (failed) task when respondingWith(text, true) is used', function () {
        var handler = findByJsonPath(a2aMock()
            .onTaskSend().matchingMessage('bad.*').respondingWith('Error occurred', true).and()
            .build())[0];
        assert.ok(handler.httpResponseTemplate.template.indexOf('"status": {"state": "failed"}') !== -1);
    });
});

// =========================================================================
// escaping
// =========================================================================

describe('a2aMock escaping', function () {
    it('Velocity-escapes $ and # in the default task response', function () {
        var send = findByJsonRpc(a2aMock().withDefaultTaskResponse('$100 off #sale').build(), 'tasks/send')[0];
        assert.ok(send.httpResponseTemplate.template.indexOf('${esc.d}100 off ${esc.h}sale') !== -1);
    });

    it('Velocity-escapes a backslash before # in the default task response', function () {
        var send = findByJsonRpc(a2aMock().withDefaultTaskResponse('a\\#b').build(), 'tasks/send')[0];
        assert.ok(send.httpResponseTemplate.template.indexOf('a${esc.b}${esc.b}${esc.h}b') !== -1);
    });

    it('Velocity-escapes very long backslash runs in the default task response', function () {
        var run = '\\'.repeat(100000);
        var plain = findByJsonRpc(a2aMock().withDefaultTaskResponse(run + 'x').build(), 'tasks/send')[0];
        assert.ok(plain.httpResponseTemplate.template.indexOf(run + run + 'x') !== -1);
        var meta = findByJsonRpc(a2aMock().withDefaultTaskResponse(run + '$').build(), 'tasks/send')[0];
        assert.ok(meta.httpResponseTemplate.template.indexOf('${esc.b}'.repeat(200000) + '${esc.d}') !== -1);
    });

    it('Velocity-escapes $ and # in a custom handler response', function () {
        var handler = findByJsonPath(a2aMock()
            .onTaskSend().matchingMessage('test.*').respondingWith('Price is $50 #discount').and()
            .build())[0];
        assert.ok(handler.httpResponseTemplate.template.indexOf('${esc.d}50 ${esc.h}discount') !== -1);
    });

    it('escapes forward slashes in the message-pattern regex', function () {
        var handler = findByJsonPath(a2aMock()
            .onTaskSend().matchingMessage('path/to/resource').respondingWith('found').and()
            .build())[0];
        assert.ok(handler.httpRequest.body.jsonPath.indexOf('path\\/to\\/resource') !== -1);
    });

    it('escapes newlines and carriage returns, and strips null bytes, in the pattern', function () {
        var handler = findByJsonPath(a2aMock()
            .onTaskSend().matchingMessage('line1\nline2\rmid\0end\\d+').respondingWith('found').and()
            .build())[0];
        var jsonPath = handler.httpRequest.body.jsonPath;
        assert.ok(jsonPath.indexOf('line1\\nline2\\rmidend\\d+') !== -1);
        assert.ok(jsonPath.indexOf('\0') === -1);
    });

    it('preserves an existing regex escape sequence (\\d+ stays \\d+, not doubled)', function () {
        // '\\d+' in a JS string literal is a single backslash followed by 'd+'.
        var handler = findByJsonPath(a2aMock()
            .onTaskSend().matchingMessage('\\d+').respondingWith('found').and()
            .build())[0];
        var jsonPath = handler.httpRequest.body.jsonPath;
        // The single-backslash escape must survive verbatim, NOT become '\\\\d+'.
        assert.ok(jsonPath.indexOf('=~ /\\d+/)]') !== -1, jsonPath);
    });

    it('preserves an already-escaped forward slash (\\/ stays \\/, not double-escaped)', function () {
        // 'a\\/b' is a, backslash, forward-slash, b.
        var handler = findByJsonPath(a2aMock()
            .onTaskSend().matchingMessage('a\\/b').respondingWith('found').and()
            .build())[0];
        var jsonPath = handler.httpRequest.body.jsonPath;
        // Must stay 'a\/b' and NOT be turned into 'a\\/b' (which would escape
        // the backslash and re-expose the bare '/' delimiter).
        assert.ok(jsonPath.indexOf('=~ /a\\/b/)]') !== -1, jsonPath);
    });

    it('neutralizes a trailing lone backslash so it cannot escape the closing delimiter (security)', function () {
        // 'trail\\' is the 5 chars t,r,a,i,l followed by ONE real backslash.
        var handler = findByJsonPath(a2aMock()
            .onTaskSend().matchingMessage('trail\\').respondingWith('found').and()
            .build())[0];
        var jsonPath = handler.httpRequest.body.jsonPath;
        // The lone trailing backslash must be doubled to a literal backslash:
        // 'trail\' -> 'trail\\' (two real backslashes in the output).
        assert.ok(jsonPath.indexOf('=~ /trail\\\\/)]') !== -1, jsonPath);
        // Security assertion: the jsonPath still terminates with the closing
        // '/)]' delimiter+suffix — the backslash did NOT break out and consume
        // the delimiter. The full literal is '...=~ /trail\\/)]' where the two
        // backslashes are an escaped-backslash, leaving the final '/' as the
        // delimiter.
        assert.ok(/\/\)]$/.test(jsonPath), 'must end with /)]: ' + jsonPath);
        // And the breakout signature ('trail\/)]' with a SINGLE backslash, i.e.
        // backslash directly escaping the delimiter) must NOT be present.
        assert.ok(jsonPath.indexOf('trail\\/)]') === -1, 'lone backslash escaped the delimiter: ' + jsonPath);
    });

    it('still escapes a plain forward slash in a normal pattern', function () {
        var handler = findByJsonPath(a2aMock()
            .onTaskSend().matchingMessage('path/to/resource').respondingWith('found').and()
            .build())[0];
        assert.ok(handler.httpRequest.body.jsonPath.indexOf('path\\/to\\/resource') !== -1);
    });
});

// =========================================================================
// streaming
// =========================================================================

describe('a2aMock streaming', function () {
    it('advertises the streaming capability and adds an SSE expectation', function () {
        var expectations = a2aMock().withStreaming().withDefaultTaskResponse('streamed result').build();
        // card + tasks/send + streaming + tasks/get + tasks/cancel
        assert.equal(expectations.length, 5);

        var body = agentCard(expectations).httpResponse.body;
        assert.ok(body.indexOf('"streaming": true') !== -1);
        assert.ok(body.indexOf('"pushNotifications": false') !== -1);

        var streaming = expectations.filter(function (e) { return e.httpSseResponse; })[0];
        assert.ok(streaming, 'expected an SSE expectation');
        assert.equal(streaming.httpSseResponse.statusCode, 200);
        assert.equal(streaming.httpSseResponse.closeConnection, true);
        assert.equal(streaming.httpSseResponse.events.length, 3);
        var allData = streaming.httpSseResponse.events.map(function (e) { return e.data; }).join('');
        assert.ok(allData.indexOf('status-update') !== -1);
        assert.ok(allData.indexOf('artifact-update') !== -1);
        assert.ok(allData.indexOf('"state": "working"') !== -1);
        assert.ok(allData.indexOf('"state": "completed"') !== -1);
        assert.ok(allData.indexOf('"final": true') !== -1);
        assert.ok(allData.indexOf('streamed result') !== -1);
        // the streaming request matches the default message/stream method
        assert.equal(streaming.httpRequest.body.method, 'message/stream');
    });

    it('supports a custom streaming method (implies streaming)', function () {
        var expectations = a2aMock().withStreamingMethod('tasks/sendSubscribe').build();
        var streaming = expectations.filter(function (e) { return e.httpSseResponse; })[0];
        assert.ok(streaming, 'expected an SSE expectation');
        assert.equal(streaming.httpRequest.body.method, 'tasks/sendSubscribe');
        assert.ok(agentCard(expectations).httpResponse.body.indexOf('"streaming": true') !== -1);
    });
});

// =========================================================================
// push notifications
// =========================================================================

describe('a2aMock push notifications', function () {
    it('advertises push notifications, echoes the config, and forwards delivery to the webhook', function () {
        var expectations = a2aMock().withPushNotifications('http://localhost:1234/callback').build();
        // card + pushConfig + tasks/send delivery + tasks/get + tasks/cancel
        assert.equal(expectations.length, 5);

        assert.ok(agentCard(expectations).httpResponse.body.indexOf('"pushNotifications": true') !== -1);

        // config echo
        var configEcho = findByJsonRpc(expectations, 'tasks/pushNotificationConfig/set')[0];
        assert.ok(configEcho, 'expected a push-notification config echo');
        assert.ok(configEcho.httpResponseTemplate.template.indexOf('http://localhost:1234/callback') !== -1);

        // delivery: a tasks/send that forwards to the webhook
        var delivery = expectations.filter(function (e) {
            return e.httpOverrideForwardedRequest && e.httpRequest.body && e.httpRequest.body.method === 'tasks/send';
        })[0];
        assert.ok(delivery, 'expected a tasks/send forward-delivery expectation');
        var override = delivery.httpOverrideForwardedRequest;
        assert.equal(override.requestOverride.method, 'POST');
        assert.equal(override.requestOverride.path, '/callback');
        assert.deepEqual(override.requestOverride.socketAddress, { host: 'localhost', port: 1234, scheme: 'HTTP' });
        // caller response is a VELOCITY template echoing the JSON-RPC request id
        assert.equal(override.responseTemplate.templateType, 'VELOCITY');
        assert.ok(override.responseTemplate.template.indexOf('$!{request.jsonRpcRawId}') !== -1);
    });

    it('does NOT Velocity-escape the literal webhook push body', function () {
        var expectations = a2aMock()
            .withPushNotifications('http://localhost:1234/callback')
            .withDefaultTaskResponse('$100 off #sale')
            .build();
        var delivery = expectations.filter(function (e) { return e.httpOverrideForwardedRequest; })[0];
        var pushBody = delivery.httpOverrideForwardedRequest.requestOverride.body;
        assert.ok(pushBody.indexOf('$100 off #sale') !== -1);
        assert.ok(pushBody.indexOf('esc.d') === -1);
        assert.ok(pushBody.indexOf('esc.h') === -1);
    });

    it('parses an https webhook with the default port 443', function () {
        var expectations = a2aMock().withPushNotifications('https://example.com/a2a/push').build();
        var delivery = expectations.filter(function (e) { return e.httpOverrideForwardedRequest; })[0];
        var override = delivery.httpOverrideForwardedRequest.requestOverride;
        assert.deepEqual(override.socketAddress, { host: 'example.com', port: 443, scheme: 'HTTPS' });
        assert.equal(override.secure, true);
        assert.equal(override.path, '/a2a/push');
    });
});

// =========================================================================
// applyTo wiring
// =========================================================================

describe('a2aMock applyTo', function () {
    it('delegates to client.mockAnyResponse with the built expectation array', function () {
        var captured = null;
        var fakeClient = { mockAnyResponse: function (e) { captured = e; return 'APPLIED'; } };
        var result = a2aMock('/a2a')
            .onTaskSend().matchingMessage('x.*').respondingWith('y').and()
            .applyTo(fakeClient);
        assert.equal(result, 'APPLIED');
        assert.ok(Array.isArray(captured));
        assert.ok(captured.length >= 4);
    });
});
