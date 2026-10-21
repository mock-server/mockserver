/**
 * Node client-library emitter tests — split out of the shared per-language
 * byte-identity harness (./extractParity.test.ts) so the Node emitter can carry
 * a stronger, Node-specific guarantee than the other languages: not only that
 * its output is byte-stable, but that the object literal it embeds is a valid
 * client `Expectation`.
 *
 * Three layers:
 *  1. Byte-identity — standardToNode over the shared `combos` reproduces the
 *     committed golden (./__fixtures__/nodeGolden.ts) character-for-character.
 *  2. Idiom — the emitted snippet uses the website's typed idiom
 *     (`mockServerClient(host, port).mockAnyResponse({ ...literal... }).then(...)`).
 *  3. TYPE PROOF — every emitted object literal (the argument to mockAnyResponse)
 *     typechecks against the client's `Expectation` type
 *     (mockserver-client-node/mockServer.d.ts) via a real `tsc` run, with a
 *     bogus-key negative control proving the check actually rejects bad output.
 *
 * The type proof's fs/tsc mechanics live in a plain-JS helper
 * (../../../scripts/typecheck-node-codegen.mjs), imported dynamically, so this
 * test uses no node built-ins and stays clean under the app tsconfig (which has
 * no @types/node). See that script's header for the full rationale — it mirrors
 * how scripts/emit-java-codegen-samples.mjs javac's the Java tab.
 */
import { describe, it, expect, afterAll } from 'vitest';
import {
  standardToNode,
  type StandardMatcher,
  type StandardActionPayload,
} from '../standardCodegen';
import { combos } from './extractParityCases';
import { nodeGolden } from './__fixtures__/nodeGolden';
import { typecheckExpectationLiterals, cleanupTypecheckScratch } from '../../../scripts/typecheck-node-codegen.mjs';

const firstCombo = combos[0];
if (!firstCombo) throw new Error('extractParityCases combos fixture is empty');

/** Pull the object literal argument out of an emitted `.mockAnyResponse(...)` call. */
function extractLiteral(src: string): string {
  const marker = '.mockAnyResponse(';
  const start = src.indexOf(marker);
  expect(start, 'emitted snippet must call .mockAnyResponse').toBeGreaterThanOrEqual(0);
  const from = start + marker.length;
  const end = src.indexOf(')\n  .then(', from);
  expect(end, 'emitted snippet must chain .then(...) after mockAnyResponse').toBeGreaterThan(from);
  return src.slice(from, end);
}

function baseMatcher(overrides?: Partial<StandardMatcher>): StandardMatcher {
  return {
    id: '', method: 'GET', path: '/api', headers: '', queryString: '', cookies: '',
    pathParams: '', body: '', bodyBinary: false, bodyMatcherType: 'string',
    priority: 0, times: 0, ...overrides,
  };
}

/**
 * Node-only type-proof cases for buildExpectationJson features the SHARED combos
 * do not exercise, so the `Expectation` type is proven complete for them too:
 *  - chaos: the modeled top-level chaos profile.
 *  - LLM + multi-response sequence: carried verbatim through the edit-overlay
 *    passthrough (the form does not model them), proving the client type accepts
 *    the shapes the composer preserves.
 */
const extraTypeCases: { name: string; matcher: StandardMatcher; action: StandardActionPayload; baseUrl: string }[] = [
  {
    // mirrors test-fixtures/expectations/matcher_request_date_time.json
    name: 'date-time-matchers',
    matcher: baseMatcher({
      headers: 'X-Issued-At: {"after":"now -1 hours"}\nIf-Modified-Since: {"before":"2026-10-21T00:00:00Z","actualFormat":"rfc1123"}\nX-Plain: before:2026-01-01',
      queryString: 'from={"after":"2026-01-01","actualFormat":"dd/MM/yyyy","zone":"Europe/London"}',
      cookies: 'seen={"not":true,"equalToDateTime":"now +1 days","truncateExpected":"first day of month","truncateActual":"startOfDay","applyTruncationLast":true}',
      pathParams: 'id={"before":"now"}',
    }),
    action: {
      type: 'static',
      static: { statusCode: 200, body: 'dated', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
    },
    baseUrl: 'http://localhost:1080',
  },
  {
    name: 'chaos-profile',
    matcher: baseMatcher(),
    action: {
      type: 'static',
      static: { statusCode: 200, body: 'ok', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      chaos: {
        errorStatus: 503, errorProbability: 0.5, retryAfter: '10',
        latencyValue: 100, latencyUnit: 'MILLISECONDS', seed: 42, succeedFirst: 1, failRequestCount: 3,
      },
    },
    baseUrl: 'http://localhost:1080',
  },
  {
    name: 'llm-passthrough',
    matcher: baseMatcher(),
    action: {
      type: 'static',
      static: { statusCode: 0, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { path: '/api', method: 'GET' },
        httpLlmResponse: { provider: 'OPENAI', model: 'gpt-4o', completion: { text: 'hi', stopReason: 'stop' } },
      },
    },
    baseUrl: 'http://localhost:1080',
  },
  {
    name: 'multi-response-sequence',
    matcher: baseMatcher(),
    action: {
      type: 'static',
      static: { statusCode: 0, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { path: '/api', method: 'GET' },
        httpResponses: [{ statusCode: 200, body: 'a' }, { statusCode: 201, body: 'b' }],
        responseMode: 'SEQUENTIAL',
      },
    },
    baseUrl: 'http://localhost:1080',
  },
];

describe('standardToNode byte-identity', () => {
  for (const combo of combos) {
    it(combo.name, () => {
      expect(standardToNode(combo.matcher, combo.action, combo.baseUrl)).toBe(nodeGolden[combo.name]);
    });
  }
});

describe('standardToNode idiom', () => {
  it('emits the website typed mockAnyResponse idiom', () => {
    const code = standardToNode(firstCombo.matcher, firstCombo.action, firstCombo.baseUrl);
    expect(code).toContain("require('mockserver-client')");
    expect(code).toContain('mockServerClient("localhost", 1080)');
    expect(code).toContain('.mockAnyResponse({');
    expect(code).toContain('.then(');
    // The argument is a JSON object literal (typed idiom), not a builder chain.
    expect(extractLiteral(code).trimStart().startsWith('{')).toBe(true);
  });
});

describe('standardToNode Expectation type proof', () => {
  it('every emitted literal typechecks as a client Expectation', () => {
    const cases = [...combos, ...extraTypeCases];
    const literals = cases.map((c) => extractLiteral(standardToNode(c.matcher, c.action, c.baseUrl)));
    const { ok, output } = typecheckExpectationLiterals('samples.ts', literals);
    expect(output, 'tsc must report no errors for generated Node literals').toBe('');
    expect(ok).toBe(true);
  });

  it('negative control: a bogus key makes the literal fail the type check', () => {
    const good = extractLiteral(standardToNode(firstCombo.matcher, firstCombo.action, firstCombo.baseUrl));
    // Inject an excess property the Expectation type does not declare, right
    // after the literal's opening brace. Built by concatenation (not replace)
    // so the single-insertion intent is explicit.
    expect(good.startsWith('{')).toBe(true);
    const bad = '{\n  "__definitelyNotAnExpectationField__": true,' + good.slice(1);
    const { ok, output } = typecheckExpectationLiterals('negative.ts', [bad]);
    expect(ok, 'tsc must reject a literal with an undeclared Expectation field').toBe(false);
    expect(output).toContain('__definitelyNotAnExpectationField__');
  });

  afterAll(() => {
    cleanupTypecheckScratch();
  });
});
