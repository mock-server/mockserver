'use strict';

/*
 * DRIFT GATE for mockServer.d.ts — the Node client's server-model type declarations.
 *
 * mockServer.d.ts is hand-maintained and authoritative. Do NOT gate it by regenerating with
 * scripts/build_server_typescript.sh and byte-diffing: swagger-typescript-api produces a
 * structurally different artefact (renamed members, ~1000 more lines), and adopting it would
 * break the type-level fidelity gate in test/roundtrip_fidelity_types.ts.
 *
 * So this file gates the property that actually matters, against the artefact that is
 * actually authoritative — the server's own expectation.json schema, the same source the
 * server validates incoming expectations with:
 *
 *   1. mockServer.d.ts declares every expectation action the server accepts.
 *      (Node type rot — the thing a stale generator would cause.)
 *   2. The two committed copies of the OpenAPI spec stay identical.
 *   3. The OpenAPI spec's own action gap is PINNED to an explicit list, ratcheting in both
 *      directions: adding a new gap fails, and closing one fails until the list is updated.
 *      That list is the finding, made mechanical so it cannot silently widen.
 *
 * Needs no MockServer instance — pure file comparison. It lives under test/no_proxy/ because
 * that is the directory CI globs (`npm test` and the Buildkite step discover test files).
 */

var { describe, it } = require('node:test');
var assert = require('node:assert/strict');
var fs = require('fs');
var path = require('path');
var yaml = require('js-yaml');

// The action/non-action split and its derivation from the server schema live in one place
// (test/lib/action_keys.js) so the three test files and build_server_typescript.sh cannot drift.
var actionKeys = require('../lib/action_keys');
var serverActionKeys = actionKeys.serverActionKeys;
var schemaExpectationKeys = actionKeys.schemaExpectationKeys;

// test/no_proxy -> test -> mockserver-client-node -> <repo root>
var REPO_ROOT = path.resolve(__dirname, '..', '..', '..');
var CLIENT_DIR = path.resolve(__dirname, '..', '..');

var DTS_PATH = path.join(CLIENT_DIR, 'mockServer.d.ts');
var SPEC_CORE_PATH = path.join(REPO_ROOT, 'mockserver', 'mockserver-core', 'src', 'main',
    'resources', 'org', 'mockserver', 'openapi', 'mock-server-openapi-embedded-model.yaml');
var SPEC_SITE_PATH = path.join(REPO_ROOT, 'jekyll-www.mock-server.com', 'mockserver-openapi.yaml');

/*
 * Expectation members mockServer.d.ts declares that the server's schema does NOT.
 *
 * expectation.json sets `additionalProperties: false`, so anything here is a property a
 * TypeScript user can write, typecheck cleanly, and then have the server reject at runtime
 * with 400 "incorrect expectation json format". That is the opposite failure to a missing
 * action, and it needs its own assertion — a one-directional gate would never see it.
 *
 * Empty, and it should stay that way. It exists armed rather than absent so that the day a
 * genuine transitional exception is needed, the mechanism and this explanation are already
 * here rather than being invented under time pressure.
 */
var DTS_KNOWN_EXTRA_KEYS = [];

/*
 * The OpenAPI spec's KNOWN GAP against the server schema, pinned exactly.
 *
 * Each entry would be an expectation action the SERVER accepts and the spec does not declare.
 * The gap is now CLOSED — the spec declares every action the server accepts (the 7 that were
 * pinned here, binary/dns/grpcBidi/grpcStream/httpForwardValidate/httpForwardWithFallback/
 * httpLlm, are all declared as of the control-plane spec reconciliation), so this list is empty.
 *
 * It stays here, asserted for equality (not a subset), as a forward ratchet:
 *   - a new action added to the server but not the spec FAILS (the gap widened);
 *   - the list may only ever be [] or shrinking toward it, and must never regain an entry
 *     except to record a genuine, reviewed, transitional server/spec gap.
 *
 * The comment above once said reaching [] would let this file be replaced by a
 * regenerate-and-byte-diff gate. It cannot: swagger-typescript-api emits a structurally
 * different artefact from the hand-maintained mockServer.d.ts (see the file header), so the
 * action-completeness assertion in the 'mockServer.d.ts drift' suite is the real gate and this
 * remains the spec-side forward ratchet.
 */
var SPEC_KNOWN_MISSING_ACTIONS = [];

/* Parse the member names out of `export type Expectation = { ... };` in the .d.ts. */
function declaredExpectationKeys() {
    var dts = fs.readFileSync(DTS_PATH, 'utf8');
    var block = /export type Expectation = \{([\s\S]*?)^\};/m.exec(dts);
    assert.ok(block, 'could not locate "export type Expectation" in mockServer.d.ts');
    var members = block[1].match(/^\s{2}(\w+)\??:/gm) || [];
    return members.map(function (l) { return /^\s{2}(\w+)/.exec(l)[1]; }).sort();
}

function specExpectationKeys(specPath) {
    var spec = yaml.load(fs.readFileSync(specPath, 'utf8'));
    var schemas = (spec.components && spec.components.schemas) || {};
    assert.ok(schemas.Expectation, 'no components.schemas.Expectation in ' + specPath);
    return Object.keys(schemas.Expectation.properties || {}).sort();
}

describe('mockServer.d.ts drift', function () {

    it('discovers a non-trivial set of server actions', function () {
        // Without this, a refactor that makes serverActionKeys() return [] would turn every
        // assertion below into a vacuous truth and this whole file into a no-op.
        assert.ok(serverActionKeys().length >= 15,
            'expected at least 15 server actions, got ' + serverActionKeys().length);
    });

    it('declares every expectation action the server accepts', function () {
        var declared = declaredExpectationKeys();
        var missing = serverActionKeys().filter(function (a) {
            return declared.indexOf(a) === -1;
        });

        assert.deepEqual(missing, [],
            'mockServer.d.ts is missing action(s) the server accepts: ' + missing.join(', ') +
            '. Node users get no type for them, and the fidelity gate in ' +
            'test/roundtrip_fidelity_types.ts can no longer see them. Add them to ' +
            'mockServer.d.ts (and to the OpenAPI spec, so generation can produce them).');
    });

    /*
     * The reverse direction. Types that are AHEAD of the server are not harmlessly generous:
     * expectation.json sets `additionalProperties: false`, so any member here is one a user
     * can write, typecheck green, and watch the server reject with 400.
     *
     * This caught a real instance — `openAPIDefinition` was declared as a top-level Expectation
     * member, but it is a REQUEST-level concept, declared only by requestDefinition.json and
     * conditionalRequestDefinition.json and reachable correctly through `httpRequest`
     * (RequestDefinition is a union that already includes OpenAPIDefinition). Sending it at the
     * top level returns 400 "$.openAPIDefinition: is not defined in the schema and the schema
     * does not allow additional properties".
     */
    it('declares no expectation member the server would reject', function () {
        var serverKeys = schemaExpectationKeys();
        var extra = declaredExpectationKeys().filter(function (k) {
            return serverKeys.indexOf(k) === -1 && DTS_KNOWN_EXTRA_KEYS.indexOf(k) === -1;
        }).sort();

        assert.deepEqual(extra, [],
            'mockServer.d.ts declares expectation member(s) absent from the server schema: ' +
            extra.join(', ') + '. expectation.json sets additionalProperties:false, so these ' +
            'typecheck green and then fail at runtime with 400 "incorrect expectation json ' +
            'format". Remove them, or pin them in DTS_KNOWN_EXTRA_KEYS with a reason.');
    });
});

describe('OpenAPI spec drift', function () {

    it('keeps the two committed copies of the spec identical', function () {
        // The core resource is what the server serves and what the generator reads; the
        // jekyll copy is what the website publishes. They are the same document and drift
        // between them means one audience is being told something untrue.
        var core = fs.readFileSync(SPEC_CORE_PATH);
        var site = fs.readFileSync(SPEC_SITE_PATH);
        assert.ok(core.equals(site),
            'mock-server-openapi-embedded-model.yaml and ' +
            'jekyll-www.mock-server.com/mockserver-openapi.yaml have diverged; they must be ' +
            'byte-identical copies of the same spec');
    });

    it('has exactly the pinned set of actions missing relative to the server schema', function () {
        var specKeys = specExpectationKeys(SPEC_CORE_PATH);
        var actuallyMissing = serverActionKeys().filter(function (a) {
            return specKeys.indexOf(a) === -1;
        }).sort();

        assert.deepEqual(actuallyMissing, SPEC_KNOWN_MISSING_ACTIONS,
            'the OpenAPI spec\'s gap against the server schema changed.\n' +
            '  pinned:   ' + JSON.stringify(SPEC_KNOWN_MISSING_ACTIONS) + '\n' +
            '  actual:   ' + JSON.stringify(actuallyMissing) + '\n' +
            'If the gap GREW, an action was added to the server without being added to the ' +
            'spec — add it to the spec (both committed copies) rather than to this list. If ' +
            'the gap SHRANK below the pinned set, remove the now-declared action from ' +
            'SPEC_KNOWN_MISSING_ACTIONS. The pinned set is now [] and should stay that way; ' +
            'it remains the spec-side forward ratchet (see the comment on ' +
            'SPEC_KNOWN_MISSING_ACTIONS for why a regenerate-and-byte-diff gate is not viable).');
    });
});
