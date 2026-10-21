'use strict';

/*
 * Single definition of how an expectation's properties split into ACTIONS and everything else,
 * plus the derivation of the action list from the server's own authoritative schema.
 *
 * This lived in four places — three test files and scripts/build_server_typescript.sh — each
 * carrying a comment asserting it was "kept identical to" one of the others. Comments were the
 * only thing holding them in sync. Divergence would have been fail-loud rather than silent
 * (the lists are compared against the same schema), but "fail loudly in a confusing way" is
 * still worse than "cannot diverge".
 *
 * Consumed from Node via require(), and from bash via `node -p` (see build_server_typescript.sh).
 */

var fs = require('fs');
var path = require('path');

// test/lib -> test -> mockserver-client-node -> <repo root>
var REPO_ROOT = path.resolve(__dirname, '..', '..', '..');

var SCHEMA_PATH = path.join(REPO_ROOT, 'mockserver', 'mockserver-core', 'src', 'main',
    'resources', 'org', 'mockserver', 'model', 'schema', 'expectation.json');

/*
 * Everything in expectation.json that is NOT an action: matchers, scenario/state plumbing,
 * lifecycle metadata.
 *
 * Listed explicitly rather than pattern-matched, deliberately: a newly added server key must
 * land in neither bucket by accident. A new key that is not named here is treated as an action,
 * and the gates then require it to be declared by the client — which is the safe default,
 * because the cost of wrongly treating a non-action as an action is a loud test failure, while
 * the cost of wrongly skipping a real action is an action nobody can create.
 */
var NON_ACTION_KEYS = [
    'id', 'priority', 'percentage', 'chaos', 'rateLimit',
    'httpRequest', 'httpResponse',
    'beforeActions', 'afterActions', 'capture',
    'namespace', 'scenarioName', 'scenarioState', 'newScenarioState',
    'httpResponses', 'responseMode', 'responseWeights', 'switchAfter',
    'crossProtocolScenarios',
    'times', 'timeToLive', 'steps', 'timestamp', 'metadata'
];

var NON_ACTION_KEY_SET = new Set(NON_ACTION_KEYS);

/* Every expectation property the server's authoritative schema declares. */
function schemaExpectationKeys(schemaPath) {
    var schema = JSON.parse(fs.readFileSync(schemaPath || SCHEMA_PATH, 'utf8'));
    return Object.keys(schema.properties).sort();
}

/* The subset of those that are actions. */
function serverActionKeys(schemaPath) {
    return schemaExpectationKeys(schemaPath)
        .filter(function (k) { return !NON_ACTION_KEY_SET.has(k); })
        .sort();
}

module.exports = {
    SCHEMA_PATH: SCHEMA_PATH,
    NON_ACTION_KEYS: NON_ACTION_KEYS,
    NON_ACTION_KEY_SET: NON_ACTION_KEY_SET,
    schemaExpectationKeys: schemaExpectationKeys,
    serverActionKeys: serverActionKeys
};
