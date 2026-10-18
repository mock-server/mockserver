'use strict';

/*
 * END-TO-END acceptance gate for the Node client's expectation action keys.
 *
 * This is the companion to action_key_coverage_test.js, and it exists because that
 * test — by design — stops one step short of the thing that actually matters.
 *
 *   action_key_coverage_test.js asserts the BYTES THE CLIENT EMITS, captured off the
 *   wire by a throwaway HTTP listener that replies 201 to anything. It proves the
 *   client sends exactly one action. It cannot prove the SERVER ACCEPTS the result,
 *   because the listener is not a server — it accepts payloads a real MockServer
 *   rejects outright. (Its per-action payload `{some: 'action'}` is one such: a real
 *   server 400s it with "is not defined in the schema and the schema does not allow
 *   additional properties".)
 *
 * So this file closes the loop against a REAL MockServer: for every action key the
 * server's own expectation.json schema declares, create an expectation THROUGH THE
 * NODE CLIENT and assert the server both accepted it and stored it with that action.
 * That is the assertion that matters — "gRPC bidi is uncreatable from
 * Node" is a claim about the server's response, not about the client's JSON.
 *
 * Why default headers are set on every case: addDefaultExpectationHeaders returns
 * early when there are no default headers to add, short-circuiting before the action
 * key list is ever consulted. Without setDefaultHeaders these cases cannot observe a
 * missing key and would pass against the unfixed client. (Confirmed by mutation —
 * see the mutation table in the accompanying report.)
 *
 * Requires a running MockServer (supplied by test/run_with_local_server.js, or by
 * MOCKSERVER_HOST/MOCKSERVER_PORT in CI's external mode).
 */

var { describe, it, before } = require('node:test');
var assert = require('node:assert/strict');

var mockServerClient = require('../../').mockServerClient;
// The action/non-action split and its derivation from the server schema live in one place
// (test/lib/action_keys.js) so the three test files and build_server_typescript.sh cannot drift.
var schemaActionKeys = require('../lib/action_keys').serverActionKeys;

process.on('unhandledRejection', function (reason) {
    var msg = String(reason || '');
    if (/ECONNREFUSED/.test(msg) ||
        /Can't connect to MockServer/.test(msg) ||
        /Max reconnect attempts/.test(msg) ||
        /WebSocket/.test(msg)) {
        return;
    }
    console.error('[unhandledRejection — NOT suppressed]', reason);
});

var mockServerHost = process.env.MOCKSERVER_HOST || 'localhost';
var mockServerPort = parseInt(process.env.MOCKSERVER_PORT, 10) || 1080;

// Minimal SCHEMA-VALID payload per action. Most actions accept `{}`; the three that
// declare required fields are spelled out. These were established empirically against
// a real 7.4.1-SNAPSHOT server — an action whose payload is wrong fails loudly here
// rather than silently degrading into a weaker assertion.
var MINIMAL_VALID_ACTION_PAYLOAD = {
    binaryResponse: {},
    dnsResponse: {},
    grpcBidiResponse: {},
    grpcStreamResponse: {},
    httpError: {},
    httpForward: {},
    httpForwardClassCallback: {},
    httpForwardObjectCallback: {},
    httpForwardTemplate: {},
    httpForwardValidateAction: { host: 'upstream.example.com', specUrlOrPayload: 'spec.yaml' },
    httpForwardWithFallback: {
        httpForward: { host: 'upstream.example.com' },
        fallbackResponse: { statusCode: 200 }
    },
    httpLlmResponse: {},
    httpOverrideForwardedRequest: { httpRequest: { path: '/overridden' } },
    httpResponseClassCallback: {},
    httpResponseObjectCallback: {},
    httpResponseTemplate: {},
    httpSseResponse: {},
    httpWebSocketResponse: {}
};

/*
 * Two long-lived clients, created once.
 *
 *   writer — has default headers configured. This is MANDATORY: addDefaultExpectationHeaders
 *            returns early when there are no default headers, short-circuiting before the
 *            action key list is ever consulted, so a writer without them could not observe a
 *            missing key at all.
 *   reader — has NO default headers, because retrieveActiveExpectations() runs its argument
 *            through addDefaultRequestMatcherHeaders too. A reader with default headers would
 *            search for expectations carrying X-Default-Request, match nothing, and every case
 *            would fail for a reason that has nothing to do with action keys.
 *
 * Each case uses a UNIQUE request path, so cases cannot see each other's expectations and no
 * per-case reset() is needed. An earlier draft reset in beforeEach and was both flaky (the
 * first case intermittently failed in the hook) and contaminating (a mutation aimed at one
 * action turned its alphabetical neighbour red too, hiding which action was really broken).
 */
var writer = mockServerClient(mockServerHost, mockServerPort);
writer.setDefaultHeaders(
    [{ name: 'X-Default-Response', values: ['yes'] }],
    [{ name: 'X-Default-Request', values: ['yes'] }]
);
var reader = mockServerClient(mockServerHost, mockServerPort);

describe('every server action key is creatable through the Node client', function () {
    var actionKeys = schemaActionKeys();

    before(function () {
        return reader.reset();
    });

    it('has a minimal valid payload registered for every schema action key', function () {
        var registered = Object.keys(MINIMAL_VALID_ACTION_PAYLOAD).sort();
        var missing = actionKeys.filter(function (k) { return registered.indexOf(k) === -1; });
        var extra = registered.filter(function (k) { return actionKeys.indexOf(k) === -1; });

        assert.deepEqual(missing, [],
            'the server declares action keys with no payload registered here, so they are not ' +
            'covered by the acceptance cases below: ' + missing.join(', '));
        assert.deepEqual(extra, [],
            'payloads registered for actions the server schema does not declare: ' + extra.join(', '));
    });

    it('discovers a non-trivial set of action keys', function () {
        // Guards against a refactor that makes schemaActionKeys() return [] — which would
        // silently turn every case below into zero cases and this whole file into a no-op.
        assert.ok(actionKeys.length >= 15,
            'expected at least 15 action keys from the server schema, got ' + actionKeys.length);
    });

    actionKeys.forEach(function (actionKey) {
        it(actionKey + ' is accepted and stored by a real MockServer', async function () {
            var requestPath = '/acceptance/' + actionKey;

            var expectation = { httpRequest: { path: requestPath } };
            expectation[actionKey] = MINIMAL_VALID_ACTION_PAYLOAD[actionKey];

            // Rejects if the server responds 400 — e.g. with "when multiple action types
            // are configured, exactly one must be marked as primary", which is exactly how
            // an action missing from NON_HTTP_RESPONSE_ACTION_KEYS fails.
            await writer.mockAnyResponse(expectation);

            var active = await reader.retrieveActiveExpectations({ path: requestPath });

            assert.equal(active.length, 1,
                'expected exactly one active expectation for ' + actionKey);
            assert.ok(
                Object.prototype.hasOwnProperty.call(active[0], actionKey),
                'the server stored the expectation without the ' + actionKey + ' action; ' +
                'stored keys were: ' + Object.keys(active[0]).join(', ')
            );
            assert.ok(
                !Object.prototype.hasOwnProperty.call(active[0], 'httpResponse'),
                'an empty httpResponse rode along with ' + actionKey + ', which is the ' +
                'Finding 22 failure mode: the server counts two actions with no primary'
            );
        });
    });
});

/*
 * The rejection path, asserted WITHOUT `await`.
 *
 * This client has already shipped a bug where a single-argument `.then()` swallowed a
 * failure: the rejection handler was `undefined`, the handler "returned" undefined, and
 * the promise FULFILLED — turning a failed verification into a pass. `await` masks that
 * class of bug because `await` supplies its own reject path, which is why the repo's own
 * tests never caught it. So this case drives the two-argument `.then(onFulfilled,
 * onRejected)` form directly and fails if the fulfil branch is ever taken.
 */
describe('a server rejection surfaces as a promise rejection, not a silent pass', function () {
    it('rejects a two-action expectation via .then(onFulfilled, onRejected)', function () {
        // Two actions and no primary — the server 400s this with "when multiple action
        // types are configured, exactly one must be marked as primary".
        var twoActions = {
            httpRequest: { path: '/rejection/two-actions' },
            grpcBidiResponse: {},
            httpForward: { host: 'upstream.example.com' }
        };

        return writer.mockAnyResponse(twoActions).then(
            function () {
                throw new Error(
                    'mockAnyResponse FULFILLED for an expectation the server rejects with 400. ' +
                    'Either the client is swallowing the failure (the single-arg .then() bug) ' +
                    'or the server stopped rejecting two actions with no primary.'
                );
            },
            function (reason) {
                var msg = typeof reason === 'string' ? reason : JSON.stringify(reason);
                assert.match(
                    msg, /exactly one must be marked as primary/,
                    'expected the server\'s multiple-action rejection, got: ' + msg
                );
            }
        );
    });
});
