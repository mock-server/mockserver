/**
 * Shared fixture set for the per-language emitter byte-identity parity harness
 * ({@link ./extractParity.test.ts}) and the one-off golden generator.
 *
 * The combos are chosen to exercise the distinct buildExpectationJson branches
 * AND the per-language escaping paths (Go backtick break-out, Rust raw-string
 * hash escalation, C# verbatim quote doubling, Python literalisation of
 * booleans/null). The typed-construction Ruby emitter is exercised by its own
 * golden harness (./ruby.test.ts against ./__fixtures__/rubyGolden.ts).
 */
import {
  buildExpectationJson,
  standardToGo,
  type StandardMatcher,
  type StandardActionPayload,
} from '../standardCodegen.ts';

function baseMatcher(overrides?: Partial<StandardMatcher>): StandardMatcher {
  return {
    id: '', method: 'GET', path: '/api', headers: '', queryString: '', cookies: '',
    pathParams: '', body: '', bodyBinary: false, bodyMatcherType: 'string',
    priority: 0, times: 0, ...overrides,
  };
}

const BASE_URL = 'http://localhost:1080';
const HTTPS_URL = 'https://mock.example.com';

export interface Combo {
  name: string;
  matcher: StandardMatcher;
  action: StandardActionPayload;
  baseUrl: string;
}

export const combos: Combo[] = [
  {
    name: 'simple-static',
    matcher: baseMatcher(),
    action: { type: 'static', static: { statusCode: 200, body: 'hello', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'static-file-body-full',
    matcher: baseMatcher({ method: 'POST', path: '/orders' }),
    action: {
      type: 'static',
      static: {
        statusCode: 201, body: '', contentType: 'application/json',
        bodyFromFile: true, filePath: 'responses/order.json', fileTemplateType: 'MUSTACHE',
        headers: 'X-Trace: abc\nX-Env: prod', cookies: 'session=xyz\ntheme=dark',
        reasonPhrase: 'Created', delayValue: 250, delayUnit: 'MILLISECONDS',
        connectionOptions: { keepAliveOverride: true, closeSocket: false, suppressContentLengthHeader: true },
      },
    },
    baseUrl: BASE_URL,
  },
  {
    name: 'json-body-jwt-secure',
    matcher: baseMatcher({
      method: 'PUT', path: '/account', secure: true,
      headers: 'Accept: application/json', queryString: 'v=2', cookies: 'sid=1',
      pathParams: 'id=42', body: '{"ok":true,"n":3}', bodyMatcherType: 'json', jsonMatchType: 'STRICT',
      jwt: { header: 'x-auth', scheme: 'Token', claims: 'sub=user1\nrole=admin', issuer: 'iss', audience: 'aud', algorithm: 'RS256' },
      priority: 5, times: 2, ttlSeconds: 60,
    }),
    action: { type: 'static', static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' } },
    baseUrl: HTTPS_URL,
  },
  {
    name: 'forward',
    matcher: baseMatcher(),
    action: { type: 'forward', forward: { scheme: 'HTTPS', host: 'upstream.example.com', port: 8443 } },
    baseUrl: BASE_URL,
  },
  {
    name: 'forward-override',
    matcher: baseMatcher(),
    action: {
      type: 'forward_override',
      forwardOverride: {
        overrideMethod: 'PATCH', overrideHost: 'rewrite.example.com', overrideScheme: 'HTTPS',
        overridePath: '/v2/api', overrideQueryString: 'debug=1', overrideHeaders: 'X-Fwd: yes', overrideBody: '{"patched":true}',
      },
    },
    baseUrl: BASE_URL,
  },
  {
    name: 'callback',
    matcher: baseMatcher(),
    action: { type: 'callback', callback: { callbackClass: 'com.example.MyCallback' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'template-velocity',
    matcher: baseMatcher(),
    action: { type: 'template', template: { templateType: 'VELOCITY', template: '$!request.body' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'error',
    matcher: baseMatcher(),
    action: { type: 'error', error: { dropConnection: true, responseBytesB64: 'AQIDBA==', delayValue: 5, delayUnit: 'SECONDS' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'forward-fallback',
    matcher: baseMatcher(),
    action: {
      type: 'forward_fallback',
      forwardFallback: {
        scheme: 'HTTP', host: 'primary.example.com', port: 80,
        fallbackStatusCode: 503, fallbackBody: 'unavailable', fallbackOnStatusCodes: '500,502,503', fallbackOnTimeout: true,
      },
    },
    baseUrl: BASE_URL,
  },
  {
    name: 'websocket',
    matcher: baseMatcher(),
    action: {
      type: 'websocket',
      websocket: {
        subprotocol: 'chat', messages: 'hello\nworld', closeConnection: false,
        matchers: [{ frameType: 'TEXT', textMatcher: 'ping', responses: 'pong\nack' }],
      },
    },
    baseUrl: BASE_URL,
  },
  {
    name: 'sse',
    matcher: baseMatcher(),
    action: {
      type: 'sse',
      sse: { statusCode: 200, headers: 'Cache-Control: no-cache', closeConnection: false, events: [{ event: 'msg', data: 'tick', id: '1', retry: '1000' }] },
    },
    baseUrl: BASE_URL,
  },
  {
    name: 'binary-response',
    matcher: baseMatcher(),
    action: { type: 'binary_response', binaryResponse: { binaryData: 'SGVsbG8=' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'binary-response-upstream',
    matcher: baseMatcher(),
    action: { type: 'binary_response', binaryResponse: { binaryData: 'VXBzdHJlYW0=', upstream: 'FORWARD_AND_REPLACE' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'dns',
    matcher: baseMatcher({ dns: { dnsName: 'example.com', dnsType: 'A', dnsClass: 'IN' } }),
    action: { type: 'dns_response', dnsResponse: { responseCode: 'NOERROR', answerRecords: '[{"name":"example.com","type":"A","ttl":300,"value":"1.2.3.4"}]' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'grpc-stream',
    matcher: baseMatcher(),
    action: {
      type: 'grpc_stream',
      grpcStream: { statusName: 'OK', statusMessage: '', headers: 'grpc-encoding: identity', messages: '{"a":1}\n{"b":2}', closeConnection: false },
    },
    baseUrl: BASE_URL,
  },
  {
    name: 'allOf-body',
    matcher: baseMatcher({
      method: 'POST', bodyMatcherType: 'allOf',
      bodyAllOf: [
        { type: 'json', value: '{"k":1}' },
        { type: 'xpath', value: '/a/b' },
        { type: 'regex', value: '.*foo.*' },
      ],
    }),
    action: { type: 'static', static: { statusCode: 200, body: 'ok', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'json-path-date-time-body',
    matcher: baseMatcher({
      method: 'POST', path: '/orders', bodyMatcherType: 'json-path', body: '$.order.created',
      bodyDateTime: '{"after":"now -1 days","actualFormat":"iso","truncateExpected":"startOfDay","zone":"Europe/London"}',
    }),
    action: { type: 'static', static: { statusCode: 200, body: 'recent order', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'xpath-date-time-body',
    matcher: baseMatcher({
      method: 'POST', path: '/orders', bodyMatcherType: 'xpath', body: '/order/@expires',
      bodyDateTime: '{"not":true,"before":"2026-01-01T00:00:00Z","actualFormat":"rfc1123","truncateActual":"first day of month","applyTruncationLast":true}',
    }),
    action: { type: 'static', static: { statusCode: 200, body: 'unexpired order', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'special-chars-escaping',
    matcher: baseMatcher({ path: '/a`b/c"#d', body: 'line1\nline2 "quoted" `tick`', bodyMatcherType: 'string' }),
    action: { type: 'static', static: { statusCode: 200, body: 'resp "with" `back`tick and #hash', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' } },
    baseUrl: BASE_URL,
  },
  {
    name: 'capture-scenario-sideEffects',
    matcher: baseMatcher({ method: 'POST', path: '/checkout' }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: 'ok', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      capture: [{ source: 'jsonPath', expression: '$.token', into: 'authToken' }],
      scenario: { name: 'login', requiredState: 'START', transitionTo: 'DONE' },
      scenarioModeled: true,
      sideEffects: [{
        position: 'after', method: 'POST', path: '/audit', host: 'audit.example.com', body: '{"logged":true}',
        delayValue: 0, delayUnit: 'MILLISECONDS', blocking: false, timeoutValue: 0, timeoutUnit: 'MILLISECONDS', failurePolicy: 'BEST_EFFORT',
      }],
    },
    baseUrl: BASE_URL,
  },
  {
    name: 'steps-pipeline',
    matcher: baseMatcher({ path: '/pipeline' }),
    action: {
      type: 'static',
      steps: [
        { actionType: 'httpResponse', responder: true, actionBody: '{"statusCode":200,"body":"step"}', blocking: false, delayValue: 0, delayUnit: 'MILLISECONDS', timeoutValue: 0, timeoutUnit: 'MILLISECONDS', failurePolicy: 'BEST_EFFORT' },
        { actionType: 'httpRequest', responder: false, actionBody: '{"path":"/hook"}', blocking: true, delayValue: 100, delayUnit: 'MILLISECONDS', timeoutValue: 2, timeoutUnit: 'SECONDS', failurePolicy: 'FAIL_FAST' },
      ],
    },
    baseUrl: BASE_URL,
  },

  // -------------------------------------------------------------------------
  // Edit-preserved action combos. These exercise the wire keys that the
  // standard Composer form CANNOT model but that an edit overlay carries
  // through verbatim (see standardCodegen.ts ACTION_FAMILY_KEYS + the merge in
  // mergeUnmodeledFields). ComposerView produces exactly this shape on the edit
  // path: a form-default action plus `editOriginal` (the retained server JSON)
  // and `editActionModeled: false` when the original action is unmodeled — so
  // buildExpectationJson preserves the original action-family key(s). Every
  // language emitter must render these TYPED (this was the regression: two
  // emitters dropped the preserved action entirely, others degraded it to a
  // JSON blob). `editActionModeled: true` is used where the preserved payload is
  // a NON-action sibling (rateLimit / crossProtocolScenarios / percentage /
  // timestamp / namespace) that passes through alongside a form-modeled action.
  // -------------------------------------------------------------------------
  {
    // Kitchen-sink LLM completion — mirrors test-fixtures/expectations/action_llm_completion.json.
    name: 'edit-llm-completion',
    matcher: baseMatcher({ method: 'POST', path: '/v1/messages' }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { method: 'POST', path: '/v1/messages' },
        httpLlmResponse: {
          provider: 'ANTHROPIC',
          model: 'claude-sonnet-4-20250514',
          delay: { timeUnit: 'MILLISECONDS', value: 15 },
          completion: {
            text: 'Hello, world!',
            toolCalls: [{ id: 'call_1', name: 'get_weather', arguments: '{"city":"SF"}' }],
            stopReason: 'end_turn',
            usage: { inputTokens: 10, outputTokens: 25, cachedInputTokens: 2, cacheCreationTokens: 3, reasoningTokens: 4 },
            streaming: true,
            outputSchema: '{"type":"object"}',
            enforceOutputSchema: true,
            toolChoice: 'auto',
            reasoningText: 'thinking',
            reasoningSignature: 'sig',
            model: 'claude-sonnet-4-20250514',
            streamingPhysics: { tokensPerSecond: 50, jitter: 0.2, seed: 7, subwordStreaming: true },
          },
          conversationPredicates: {
            turnIndex: 1,
            latestMessageContains: 'weather',
            latestMessageMatches: '.*weather.*',
            latestMessageRole: 'USER',
            containsToolResultFor: 'get_weather',
            semanticMatchAgainst: 'weather query',
            normalization: {
              collapseWhitespace: true, lowercase: true, sortJsonKeys: true,
              dropBuiltInVolatileFields: true, dropVolatileFields: ['id', 'created'],
            },
          },
          primary: true,
        },
      },
    },
    baseUrl: BASE_URL,
  },
  {
    // LLM embedding / rerank / moderation / contentFilter / llm-chaos — mirrors
    // test-fixtures/expectations/action_llm_embedding_rerank.json.
    name: 'edit-llm-embedding-rerank',
    matcher: baseMatcher({ method: 'POST', path: '/v1/embeddings' }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { method: 'POST', path: '/v1/embeddings' },
        httpLlmResponse: {
          provider: 'OPENAI',
          model: 'text-embedding-3-small',
          embedding: { dimensions: 1536, deterministicFromInput: true, seed: 42 },
          rerank: { topN: 3, deterministicFromInput: true, seed: 42 },
          moderation: { flaggedCategories: ['violence'], model: 'text-moderation-latest' },
          contentFilter: { hate: 'low', sexual: 'safe', violence: 'medium', selfHarm: 'safe' },
          chaos: {
            errorStatus: 429, retryAfter: '5', errorProbability: 0.1, truncateMode: 'MID_STREAM',
            truncateAtFraction: 0.5, malformedSse: true, seed: 1, quotaName: 'q1', quotaLimit: 100,
            quotaWindowMillis: 60000, quotaErrorStatus: 429, tokenQuotaLimit: 1000,
            tokenQuotaWindowMillis: 60000, contentFilterBlockProbability: 0.2, errorKind: 'OVERLOAD',
          },
        },
      },
    },
    baseUrl: BASE_URL,
  },
  {
    // Response sequence — httpResponses + responseMode + responseWeights + switchAfter.
    name: 'edit-response-sequence',
    matcher: baseMatcher({ method: 'GET', path: '/seq' }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { method: 'GET', path: '/seq' },
        httpResponses: [
          { statusCode: 200, body: 'first' },
          { statusCode: 201, body: 'second', headers: { 'content-type': ['text/plain'] } },
        ],
        responseMode: 'WEIGHTED',
        responseWeights: [3, 1],
        switchAfter: 5,
      },
    },
    baseUrl: BASE_URL,
  },
  {
    // Object callback (response) — httpResponseObjectCallback.
    name: 'edit-object-callback',
    matcher: baseMatcher({ method: 'GET', path: '/cb' }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { method: 'GET', path: '/cb' },
        httpResponseObjectCallback: { clientId: 'client-1', responseCallback: true },
      },
    },
    baseUrl: BASE_URL,
  },
  {
    // Forward validate action — httpForwardValidateAction.
    name: 'edit-forward-validate',
    matcher: baseMatcher({ method: 'POST', path: '/validate' }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { method: 'POST', path: '/validate' },
        httpForwardValidateAction: {
          specUrlOrPayload: 'https://example.com/openapi.json',
          host: 'upstream.example.com', port: 443, scheme: 'HTTPS',
          validateRequest: true, validateResponse: true, validationMode: 'STRICT',
        },
      },
    },
    baseUrl: BASE_URL,
  },
  {
    // gRPC bidirectional streaming — grpcBidiResponse (+ rules).
    name: 'edit-grpc-bidi',
    matcher: baseMatcher({ method: 'POST', path: '/grpc' }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { method: 'POST', path: '/grpc' },
        grpcBidiResponse: {
          statusName: 'OK',
          headers: { 'grpc-encoding': ['identity'] },
          messages: [{ json: '{"a":1}' }],
          rules: [{ matchJson: '{"cmd":"ping"}', responses: [{ json: '{"pong":true}' }] }],
          closeConnection: false,
        },
      },
    },
    baseUrl: BASE_URL,
  },
  {
    // Rate limit — a NON-action sibling preserved alongside a form-modeled
    // static action (editActionModeled: true). Also exercises `percentage`.
    name: 'edit-rate-limit',
    matcher: baseMatcher({ method: 'GET', path: '/rl' }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: 'ok', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: true,
      editOriginal: {
        httpRequest: { method: 'GET', path: '/rl' },
        httpResponse: { statusCode: 200, body: 'ok' },
        rateLimit: {
          name: 'api', algorithm: 'token_bucket', limit: 100, windowMillis: 60000,
          burst: 20, refillPerSecond: 5, errorStatus: 429, retryAfter: '1',
        },
        percentage: 50,
      },
    },
    baseUrl: BASE_URL,
  },
  {
    // Cross-protocol scenarios + namespace + timestamp + percentage siblings,
    // preserved alongside a form-modeled static action (editActionModeled: true).
    name: 'edit-cross-protocol',
    matcher: baseMatcher({ method: 'GET', path: '/xp' }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: 'ok', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: true,
      editOriginal: {
        httpRequest: { method: 'GET', path: '/xp' },
        httpResponse: { statusCode: 200, body: 'ok' },
        crossProtocolScenarios: [
          { trigger: 'DNS_QUERY', matchPattern: '*.example.com', scenarioName: 'dns-scn', targetState: 'RESOLVED' },
        ],
        namespace: 'team-a',
        timestamp: '2026-01-01T00:00:00Z',
        percentage: 25,
      },
    },
    baseUrl: BASE_URL,
  },
  // -------------------------------------------------------------------------
  // Edits whose original action carries fields the form does not show (delay,
  // primary, per-message delays). The saved JSON keeps them, so every emitter
  // must render them.
  // -------------------------------------------------------------------------
  keptFieldsEdit('kept-static', { type: 'static', static: { statusCode: 200, body: 'ok', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' } }, 'httpResponse',
    (w) => ({ ...w, delay: { timeUnit: 'HOURS', value: 1 }, primary: true })),
  keptFieldsEdit('kept-forward', { type: 'forward', forward: { scheme: 'HTTP', host: 'up.example.com', port: 8080 } }, 'httpForward',
    (w) => ({ ...w, delay: { timeUnit: 'SECONDS', value: 2 }, primary: true })),
  keptFieldsEdit('kept-error', { type: 'error', error: { dropConnection: true, responseBytesB64: '', delayValue: 0, delayUnit: 'MILLISECONDS' } }, 'httpError',
    (w) => ({ ...w, delay: { timeUnit: 'DAYS', value: 1 }, primary: true })),
  keptFieldsEdit('kept-callback', { type: 'callback', callback: { callbackClass: 'com.example.MyCallback' } }, 'httpResponseClassCallback',
    (w) => ({ ...w, delay: { timeUnit: 'SECONDS', value: 2 }, primary: true })),
  keptFieldsEdit('kept-template', { type: 'template', template: { templateType: 'MUSTACHE', template: '{"a":1}' } }, 'httpResponseTemplate',
    (w) => ({ ...w, delay: { timeUnit: 'SECONDS', value: 2 }, primary: true })),
  keptFieldsEdit('kept-forward-override', {
    type: 'forward_override',
    forwardOverride: { overrideMethod: '', overrideHost: '', overrideScheme: '', overridePath: '/v2', overrideQueryString: '', overrideHeaders: '', overrideBody: '' },
  }, 'httpOverrideForwardedRequest', (w) => ({ ...w, delay: { timeUnit: 'SECONDS', value: 2 }, primary: true })),
  keptFieldsEdit('kept-forward-fallback', {
    type: 'forward_fallback',
    forwardFallback: { scheme: 'HTTP', host: 'up.example.com', port: 80, fallbackStatusCode: 503, fallbackBody: '', fallbackOnStatusCodes: '', fallbackOnTimeout: true },
  }, 'httpForwardWithFallback', (w) => ({ ...w, delay: { timeUnit: 'SECONDS', value: 2 }, primary: true })),
  keptFieldsEdit('kept-websocket', {
    type: 'websocket',
    websocket: { subprotocol: '', messages: 'hello\nworld', closeConnection: true, matchers: [{ frameType: 'TEXT', textMatcher: 'ping', responses: 'pong' }] },
  }, 'httpWebSocketResponse', (w) => ({
    ...w,
    messages: (w['messages'] as Record<string, unknown>[]).map((m) => ({ ...m, delay: { timeUnit: 'MILLISECONDS', value: 100 } })),
    matchers: (w['matchers'] as Record<string, unknown>[]).map((m) => ({
      ...m,
      responses: (m['responses'] as Record<string, unknown>[]).map((r) => ({ ...r, delay: { timeUnit: 'MILLISECONDS', value: 50 } })),
    })),
    delay: { timeUnit: 'SECONDS', value: 1 },
    primary: true,
  })),
  keptFieldsEdit('kept-sse', {
    type: 'sse',
    sse: { statusCode: 200, headers: '', events: [{ event: 'tick', data: 'd', id: '', retry: '' }], closeConnection: true },
  }, 'httpSseResponse', (w) => ({
    ...w,
    events: (w['events'] as Record<string, unknown>[]).map((e) => ({ ...e, delay: { timeUnit: 'MILLISECONDS', value: 100 } })),
    delay: { timeUnit: 'SECONDS', value: 1 },
    primary: true,
  })),
  keptFieldsEdit('kept-grpc-stream', {
    type: 'grpc_stream',
    grpcStream: { statusName: 'OK', statusMessage: '', headers: '', messages: '{"a":1}', closeConnection: false },
  }, 'grpcStreamResponse', (w) => ({
    ...w,
    messages: (w['messages'] as Record<string, unknown>[]).map((m) => ({ ...m, delay: { timeUnit: 'MILLISECONDS', value: 100 }, templateType: 'MUSTACHE' })),
    delay: { timeUnit: 'SECONDS', value: 1 },
    primary: true,
  })),
];

/**
 * An edit of a form-modeled action whose original carries extra fields: `addKept`
 * turns the form's own output (the edit baseline) into the loaded original.
 */
export function keptFieldsEdit(
  name: string,
  form: StandardActionPayload,
  key: string,
  addKept: (wire: Record<string, unknown>) => Record<string, unknown>,
): Combo {
  const matcher = baseMatcher({ path: '/kept' });
  const baseline = buildExpectationJson(matcher, form)[key] as Record<string, unknown>;
  return {
    name,
    matcher,
    action: {
      ...form,
      editOriginal: { httpRequest: { method: 'GET', path: '/kept' }, [key]: addKept(structuredClone(baseline)) },
      editActionModeled: true,
      editActionBaseline: { [key]: baseline },
    },
    baseUrl: BASE_URL,
  };
}

// NOTE: `python` is intentionally NOT in this map. The Python emitter was
// rewritten to build typed client objects (see ../python.ts) rather than embed a
// JSON dict, so it no longer reproduces the byte-for-byte from_dict golden the
// other emitters share. It now has its own golden fixture (__fixtures__/pythonGolden.ts)
// and its own test (../../__tests__/pythonCodegen.test.ts) which additionally
// proves round-trip semantic equivalence by executing the generated code.
export const emitters: Record<string, (m: StandardMatcher, a: StandardActionPayload, u: string) => string> = {
  go: standardToGo,
};
