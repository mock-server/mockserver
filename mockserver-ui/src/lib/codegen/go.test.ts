/**
 * Go emitter — typed-construction assertions.
 *
 * These lock the idiom the rewrite introduced: every expectation is built from
 * the Go client's own types — the fluent `mockserver.Request()` matcher builder,
 * the typed body constructors (StringBody / JSONMatchBody / XPathBody / AllOf /
 * …) and typed action / chaos / LLM / sequence struct literals on a
 * `mockserver.Expectation` — and registered with `client.Upsert`. No expectation
 * JSON blob is embedded and `json.Unmarshal`-ed (the byte-exact output of all 18
 * parity combos is separately pinned by extractParity.test.ts against goGolden).
 */
import { describe, it, expect } from 'vitest';
import { standardToGo } from './go';
import type { StandardMatcher, StandardActionPayload } from '../standardCodegen';

function m(o: Partial<StandardMatcher> = {}): StandardMatcher {
  return {
    id: '', method: 'GET', path: '/api', headers: '', queryString: '', cookies: '',
    pathParams: '', body: '', bodyBinary: false, bodyMatcherType: 'string',
    priority: 0, times: 0, ...o,
  };
}
const URL = 'http://localhost:1080';
const staticOk: StandardActionPayload = {
  type: 'static',
  static: { statusCode: 200, body: 'ok', contentType: 'text/plain', bodyFromFile: false, filePath: '', fileTemplateType: '' },
};

describe('standardToGo — scaffold', () => {
  it('imports only the typed client and registers via Upsert, never json.Unmarshal', () => {
    const code = standardToGo(m(), staticOk, URL);
    expect(code).toContain('package main');
    expect(code).toContain('mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"');
    expect(code).toContain('client := mockserver.New("localhost", 1080)');
    expect(code).toContain('expectation := mockserver.Expectation{');
    expect(code).toContain('if _, err := client.Upsert(expectation); err != nil {');
    expect(code).not.toContain('encoding/json');
    expect(code).not.toContain('json.Unmarshal(expectationJSON');
    expect(code).not.toContain('expectationJSON :=');
  });

  it('derives host and port from an https base URL', () => {
    const code = standardToGo(m(), staticOk, 'https://mock.example.com');
    expect(code).toContain('mockserver.New("mock.example.com", 443)');
  });
});

describe('standardToGo — request matcher via fluent builder', () => {
  it('uses Request() with typed method/path/header/query/cookie/pathParameter calls', () => {
    const code = standardToGo(
      m({ method: 'POST', path: '/u', headers: 'Accept: application/json', queryString: 'v=2', cookies: 'sid=1', pathParams: 'id=42' }),
      staticOk, URL,
    );
    expect(code).toContain('mockserver.Request().');
    expect(code).toContain('Method("POST").');
    expect(code).toContain('Path("/u").');
    expect(code).toContain('Header("Accept", "application/json").');
    expect(code).toContain('QueryStringParameter("v", "2").');
    expect(code).toContain('Cookie("sid", "1").');
    expect(code).toContain('PathParameter("id", "42").');
    expect(code).toContain('BuildPtr()');
  });

  it('builds a JWT matcher with the NewJwt() fluent builder', () => {
    const code = standardToGo(
      m({ jwt: { header: 'x-auth', scheme: 'Token', claims: 'sub=user1\nrole=admin', issuer: 'iss', audience: 'aud', algorithm: 'RS256' } }),
      staticOk, URL,
    );
    expect(code).toContain('Jwt(mockserver.NewJwt().WithHeader("x-auth").WithScheme("Token").Claim("sub", "user1").Claim("role", "admin").WithIssuer("iss").WithAudience("aud").WithAlgorithm("RS256"))');
  });

  it('sets secure via the typed .Secure(true) builder call', () => {
    const code = standardToGo(m({ secure: true }), staticOk, URL);
    expect(code).toContain('Secure(true)');
  });

  it('emits a typed HttpRequest struct with DnsName for a DNS matcher', () => {
    const code = standardToGo(
      m({ dns: { dnsName: 'example.com', dnsType: 'A', dnsClass: 'IN' } }),
      { type: 'dns_response', dnsResponse: { responseCode: 'NOERROR', answerRecords: '[{"name":"example.com","type":"A","ttl":300,"value":"1.2.3.4"}]' } },
      URL,
    );
    expect(code).toContain('HttpRequest: &mockserver.HttpRequest{');
    expect(code).toContain('DnsName: "example.com"');
    expect(code).toContain('DnsType: "A"');
    expect(code).toContain('DnsResponse: &mockserver.DnsResponse{');
    expect(code).toContain('AnswerRecords: []mockserver.DnsRecord{');
  });
});

describe('standardToGo — typed body constructors', () => {
  it('JSON body with a match type → JSONMatchBody', () => {
    const code = standardToGo(m({ body: '{"ok":true}', bodyMatcherType: 'json', jsonMatchType: 'STRICT' }), staticOk, URL);
    expect(code).toContain('req.Body = mockserver.JSONMatchBody("{\\"ok\\":true}", "STRICT")');
  });

  it('subString body → SubStringBody', () => {
    const code = standardToGo(m({ body: 'frag', bodyMatcherType: 'string', bodySubString: true }), staticOk, URL);
    expect(code).toContain('req.Body = mockserver.SubStringBody("frag")');
  });

  it('xpath / regex bodies → XPathBody / RegexBody', () => {
    expect(standardToGo(m({ body: '/a/b', bodyMatcherType: 'xpath' }), staticOk, URL)).toContain('req.Body = mockserver.XPathBody("/a/b")');
    expect(standardToGo(m({ body: '.*x.*', bodyMatcherType: 'regex' }), staticOk, URL)).toContain('req.Body = mockserver.RegexBody(".*x.*")');
  });

  it('allOf body → AllOf(...) of typed sub-matchers', () => {
    const code = standardToGo(
      m({ method: 'POST', bodyMatcherType: 'allOf', bodyAllOf: [{ type: 'json', value: '{"k":1}' }, { type: 'xpath', value: '/a/b' }] }),
      staticOk, URL,
    );
    expect(code).toContain('req.Body = mockserver.AllOf(');
    expect(code).toContain('mockserver.JSONMatchBody("{\\"k\\":1}", "")');
    expect(code).toContain('mockserver.XPathBody("/a/b")');
  });

  it('a plain string body stays a bare .Body(str) builder call', () => {
    const code = standardToGo(m({ body: 'raw text', bodyMatcherType: 'string' }), staticOk, URL);
    expect(code).toContain('Body("raw text")');
    expect(code).not.toContain('mockserver.StringBody');
  });
});

describe('standardToGo — typed action struct literals', () => {
  it('static response → &mockserver.HttpResponse{...}', () => {
    const code = standardToGo(m(), staticOk, URL);
    expect(code).toContain('HttpResponse: &mockserver.HttpResponse{');
    expect(code).toContain('StatusCode: 200');
    expect(code).toContain('Body: "ok"');
    expect(code).toContain('Headers: map[string][]string{');
  });

  it('forward → &mockserver.HttpForward{...}', () => {
    const code = standardToGo(m(), { type: 'forward', forward: { scheme: 'HTTPS', host: 'up.example.com', port: 8443 } }, URL);
    expect(code).toContain('HttpForward: &mockserver.HttpForward{');
    expect(code).toContain('Host: "up.example.com"');
    expect(code).toContain('Port: 8443');
    expect(code).toContain('Scheme: "HTTPS"');
  });

  it('forward-override → typed HttpOverrideForwardedRequest with a builder RequestOverride', () => {
    const code = standardToGo(m(), {
      type: 'forward_override',
      forwardOverride: { overrideMethod: 'PATCH', overrideHost: 'rw.example.com', overrideScheme: 'HTTPS', overridePath: '/v2', overrideQueryString: 'd=1', overrideHeaders: '', overrideBody: '{"p":1}' },
    }, URL);
    expect(code).toContain('HttpOverrideForwardedRequest: &mockserver.HttpOverrideForwardedRequest{');
    expect(code).toContain('RequestOverride: mockserver.Request().');
  });

  it('error → &mockserver.HttpError{ DropConnection: ptr(true), ... }', () => {
    const code = standardToGo(m(), { type: 'error', error: { dropConnection: true, responseBytesB64: 'AQID', delayValue: 5, delayUnit: 'SECONDS' } }, URL);
    expect(code).toContain('HttpError: &mockserver.HttpError{');
    expect(code).toContain('DropConnection: ptr(true)');
    expect(code).toContain('ResponseBytes: "AQID"');
    expect(code).toContain('Delay: &mockserver.Delay{');
  });

  it('forward-fallback → &mockserver.HttpForwardWithFallback{...}', () => {
    const code = standardToGo(m(), {
      type: 'forward_fallback',
      forwardFallback: { scheme: 'HTTP', host: 'p.example.com', port: 80, fallbackStatusCode: 503, fallbackBody: 'x', fallbackOnStatusCodes: '500,502', fallbackOnTimeout: true },
    }, URL);
    expect(code).toContain('HttpForwardWithFallback: &mockserver.HttpForwardWithFallback{');
    expect(code).toContain('FallbackResponse: &mockserver.HttpResponse{');
    expect(code).toContain('FallbackOnStatusCodes: []int{500, 502}');
    expect(code).toContain('FallbackOnTimeout: ptr(true)');
  });

  it('websocket / sse / grpc / binary responses use their typed structs', () => {
    expect(standardToGo(m(), { type: 'websocket', websocket: { subprotocol: 'chat', messages: 'hi', closeConnection: false, matchers: [] } }, URL))
      .toContain('HttpWebSocketResponse: &mockserver.HttpWebSocketResponse{');
    expect(standardToGo(m(), { type: 'sse', sse: { statusCode: 200, headers: '', closeConnection: false, events: [{ event: 'e', data: 'd', id: '1', retry: '' }] } }, URL))
      .toContain('HttpSseResponse: &mockserver.HttpSseResponse{');
    expect(standardToGo(m(), { type: 'grpc_stream', grpcStream: { statusName: 'OK', statusMessage: '', headers: '', messages: '{"a":1}', closeConnection: false } }, URL))
      .toContain('GrpcStreamResponse: &mockserver.GrpcStreamResponse{');
    expect(standardToGo(m(), { type: 'binary_response', binaryResponse: { binaryData: 'SGk=' } }, URL))
      .toContain('BinaryResponse: &mockserver.BinaryResponse{');
  });

  it('class callback and forward template use their typed structs', () => {
    expect(standardToGo(m(), { type: 'callback', callback: { callbackClass: 'com.x.Y' } }, URL))
      .toContain('HttpResponseClassCallback: &mockserver.HttpClassCallback{');
    expect(standardToGo(m(), { type: 'forward_class_callback', forwardClassCallback: { callbackClass: 'com.x.Z' } }, URL))
      .toContain('HttpForwardClassCallback: &mockserver.HttpClassCallback{');
    expect(standardToGo(m(), { type: 'template', template: { templateType: 'VELOCITY', template: '$!x' } }, URL))
      .toContain('HttpResponseTemplate: &mockserver.HttpTemplate{');
  });
});

describe('standardToGo — chaos / lifecycle / capture / scenario / steps', () => {
  it('chaos profile → &mockserver.HttpChaosProfile{...} with typed pointer fields', () => {
    const code = standardToGo(m(), {
      ...staticOk,
      chaos: { errorStatus: 503, errorProbability: 0.3, retryAfter: '5', latencyValue: 100, latencyUnit: 'MILLISECONDS', seed: 42, succeedFirst: 2, failRequestCount: 1 },
    }, URL);
    expect(code).toContain('Chaos: &mockserver.HttpChaosProfile{');
    expect(code).toContain('ErrorStatus: 503');
    expect(code).toContain('ErrorProbability: ptr(0.3)');
    expect(code).toContain('Seed: ptr(int64(42))');
    expect(code).toContain('Latency: &mockserver.Delay{');
  });

  it('priority / times / ttl → typed fields and Times/TimeToLive structs', () => {
    const code = standardToGo(m({ priority: 5, times: 2, ttlSeconds: 60 }), staticOk, URL);
    expect(code).toContain('Priority: 5');
    expect(code).toContain('Times: &mockserver.Times{');
    expect(code).toContain('RemainingTimes: 2');
    expect(code).toContain('TimeToLive: &mockserver.TimeToLive{');
  });

  it('capture / scenario / after-actions → typed CaptureRule / scenario fields / AfterAction', () => {
    const code = standardToGo(m({ method: 'POST', path: '/c' }), {
      ...staticOk,
      capture: [{ source: 'jsonPath', expression: '$.t', into: 'tok' }],
      scenario: { name: 'login', requiredState: 'START', transitionTo: 'DONE' },
      scenarioModeled: true,
      sideEffects: [{ position: 'after', method: 'POST', path: '/a', host: 'a.example.com', body: '{"l":1}', delayValue: 0, delayUnit: 'MILLISECONDS', blocking: false, timeoutValue: 0, timeoutUnit: 'MILLISECONDS', failurePolicy: 'BEST_EFFORT' }],
    }, URL);
    expect(code).toContain('Capture: []mockserver.CaptureRule{');
    expect(code).toContain('Source: "jsonPath"');
    expect(code).toContain('ScenarioName: "login"');
    expect(code).toContain('NewScenarioState: "DONE"');
    expect(code).toContain('AfterActions: []mockserver.AfterAction{');
  });

  it('steps pipeline → []mockserver.ExpectationStep with typed step actions', () => {
    const code = standardToGo(m({ path: '/p' }), {
      type: 'static',
      steps: [
        { actionType: 'httpResponse', responder: true, actionBody: '{"statusCode":200,"body":"s"}', blocking: false, delayValue: 0, delayUnit: 'MILLISECONDS', timeoutValue: 0, timeoutUnit: 'MILLISECONDS', failurePolicy: 'BEST_EFFORT' },
        { actionType: 'httpRequest', responder: false, actionBody: '{"path":"/h"}', blocking: true, delayValue: 100, delayUnit: 'MILLISECONDS', timeoutValue: 2, timeoutUnit: 'SECONDS', failurePolicy: 'FAIL_FAST' },
      ],
    }, URL);
    expect(code).toContain('Steps: []mockserver.ExpectationStep{');
    expect(code).toContain('HttpResponse: &mockserver.HttpResponse{');
    expect(code).toContain('Responder: ptr(true)');
    expect(code).toContain('FailurePolicy: "FAIL_FAST"');
  });
});

describe('standardToGo — preserved LLM and response sequence (edit overlay)', () => {
  it('preserved httpLlmResponse → &mockserver.HttpLlmResponse{ Completion: &mockserver.Completion{...} }', () => {
    const code = standardToGo(m({ method: 'POST', path: '/v1/messages' }), {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { method: 'POST', path: '/v1/messages' },
        httpLlmResponse: { provider: 'ANTHROPIC', model: 'claude-3', completion: { text: 'hi', usage: { inputTokens: 5, outputTokens: 10 }, streaming: true } },
      },
    } as StandardActionPayload, URL);
    expect(code).toContain('HttpLlmResponse: &mockserver.HttpLlmResponse{');
    expect(code).toContain('Provider: "ANTHROPIC"');
    expect(code).toContain('Completion: &mockserver.Completion{');
    expect(code).toContain('Usage: &mockserver.Usage{');
    expect(code).toContain('InputTokens: ptr(5)');
    expect(code).toContain('Streaming: ptr(true)');
    expect(code).not.toContain('json.Unmarshal(expectationJSON');
  });

  it('preserved httpResponses sequence → []*mockserver.HttpResponse with mode/weights', () => {
    const code = standardToGo(m({ path: '/s' }), {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
      editActionModeled: false,
      editOriginal: {
        httpRequest: { path: '/s' },
        httpResponses: [{ statusCode: 200, body: 'a' }, { statusCode: 500, body: 'b' }],
        responseMode: 'WEIGHTED', responseWeights: [3, 1], switchAfter: 2,
      },
    } as StandardActionPayload, URL);
    expect(code).toContain('HttpResponses: []*mockserver.HttpResponse{');
    expect(code).toContain('ResponseMode: "WEIGHTED"');
    expect(code).toContain('ResponseWeights: []int{3, 1}');
    expect(code).toContain('SwitchAfter: ptr(2)');
  });
});

describe('standardToGo — ptr helper', () => {
  it('appends the generic ptr helper only when a pointer field is emitted', () => {
    const withPtr = standardToGo(m({ secure: false }), { type: 'error', error: { dropConnection: true, responseBytesB64: '', delayValue: 0, delayUnit: 'MILLISECONDS' } }, URL);
    expect(withPtr).toContain('func ptr[T any](v T) *T { return &v }');
    const noPtr = standardToGo(m(), staticOk, URL);
    expect(noPtr).not.toContain('func ptr[T any]');
  });
});
