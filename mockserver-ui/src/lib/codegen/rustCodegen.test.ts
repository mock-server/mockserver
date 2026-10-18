/**
 * Typed-Rust emitter tests.
 *
 * The Rust emitter was rewritten to construct typed `mockserver-client` objects
 * (`Expectation::new(HttpRequest…)`, `HttpResponse`, `Body`, `HttpForward`,
 * `HttpChaosProfile`, …) instead of deserialising an embedded JSON blob via
 * `serde_json::from_str`. This suite:
 *
 *   1. pins byte-exact output against {@link ./__fixtures__/rustGolden.ts};
 *   2. proves the output is genuinely TYPED — it never round-trips the whole
 *      expectation payload through `serde_json::from_str`, and uses type-specific
 *      builders / struct literals for the modelled fields;
 *   3. is backed by an out-of-band cargo equivalence proof (see the task report):
 *      each emitted program compiles against the in-repo crate and
 *      `serde_json::to_value` of the constructed value equals buildExpectationJson.
 */
import { describe, it, expect } from 'vitest';
import { standardToRust } from './rust';
import { rustCombos } from './rustCodegenCases';
import { keptFieldsEdit } from './extractParityCases';
import { rustGolden } from './__fixtures__/rustGolden';
import { withRustClientMajorPlaceholder } from './__fixtures__/rustClientMajorPlaceholder';

describe('standardToRust — typed construction', () => {
  describe('byte-exact golden', () => {
    for (const combo of rustCombos) {
      it(combo.name, () => {
        expect(withRustClientMajorPlaceholder(standardToRust(combo.matcher, combo.action, combo.baseUrl))).toBe(rustGolden[combo.name]);
      });
    }
  });

  describe('no whole-payload serde_json::from_str', () => {
    for (const combo of rustCombos) {
      it(combo.name, () => {
        const code = standardToRust(combo.matcher, combo.action, combo.baseUrl);
        // The old emitter round-tripped the entire expectation via
        // serde_json::from_str(r#"…"#). The typed emitter must never do that.
        expect(code).not.toContain('serde_json::from_str');
        expect(code).not.toContain('Expectation = serde_json');
      });
    }
  });

  it('constructs an Expectation via HttpRequest / HttpResponse builders', () => {
    const code = standardToRust(
      { id: '', method: 'GET', path: '/api', headers: '', queryString: '', cookies: '', pathParams: '', body: '', bodyBinary: false, bodyMatcherType: 'string', priority: 0, times: 0 },
      { type: 'static', static: { statusCode: 200, body: 'hello', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' } },
      'http://localhost:1080',
    );
    expect(code).toContain('use mockserver_client::*;');
    expect(code).toContain('Expectation::new(request)');
    expect(code).toContain('HttpRequest::new()');
    expect(code).toContain('.method("GET")');
    expect(code).toContain('.path("/api")');
    expect(code).toContain('.respond(HttpResponse::new()');
    expect(code).toContain('.status_code(200)');
    expect(code).toContain('.body("hello")');
    expect(code).toContain('client.upsert(&[expectation])');
  });

  it('uses a typed Body matcher for allOf composite bodies', () => {
    const code = standardToRust(
      {
        id: '', method: 'POST', path: '/api', headers: '', queryString: '', cookies: '', pathParams: '', body: '',
        bodyBinary: false, bodyMatcherType: 'allOf',
        bodyAllOf: [
          { type: 'json', value: '{"k":1}' },
          { type: 'xpath', value: '/a/b' },
          { type: 'regex', value: '.*foo.*' },
        ],
        priority: 0, times: 0,
      },
      { type: 'static', static: { statusCode: 200, body: 'ok', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' } },
      'http://localhost:1080',
    );
    expect(code).toContain('Body::all_of(vec![');
    expect(code).toContain('Body::xpath("/a/b")');
    expect(code).toContain('Body::regex(".*foo.*")');
  });

  it('builds a typed HttpForward for a forward action', () => {
    const code = standardToRust(
      { id: '', method: 'GET', path: '/api', headers: '', queryString: '', cookies: '', pathParams: '', body: '', bodyBinary: false, bodyMatcherType: 'string', priority: 0, times: 0 },
      { type: 'forward', forward: { scheme: 'HTTPS', host: 'upstream.example.com', port: 8443 } },
      'http://localhost:1080',
    );
    expect(code).toContain('.forward(HttpForward::new("upstream.example.com", 8443)');
    expect(code).toContain('.scheme("HTTPS")');
  });

  it('builds a typed HttpChaosProfile struct literal for a chaos profile', () => {
    const code = standardToRust(
      { id: '', method: 'GET', path: '/flaky', headers: '', queryString: '', cookies: '', pathParams: '', body: '', bodyBinary: false, bodyMatcherType: 'string', priority: 0, times: 0 },
      {
        type: 'static',
        static: { statusCode: 200, body: 'ok', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' },
        chaos: { errorStatus: 503, errorProbability: 0.25, latencyValue: 200, latencyUnit: 'MILLISECONDS' },
      },
      'http://localhost:1080',
    );
    expect(code).toContain('.chaos(HttpChaosProfile {');
    expect(code).toContain('error_status: Some(503)');
    expect(code).toContain('error_probability: Some(0.25)');
    expect(code).toContain('latency: Some(Delay::milliseconds(200))');
  });

  it('sets a JSON array response body with json_body, not the extra map', () => {
    const combo = keptFieldsEdit(
      'array-body',
      { type: 'static', static: { statusCode: 200, body: 'ok', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' } },
      'httpResponse',
      (wire) => ({ ...wire, body: [1, { a: 2 }] }),
    );
    const code = standardToRust(combo.matcher, combo.action, combo.baseUrl);
    expect(code).toContain('.json_body(serde_json::json!([');
    expect(code).not.toContain('.extra.insert("body"');
  });
});
