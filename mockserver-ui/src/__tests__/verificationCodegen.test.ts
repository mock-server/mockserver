/**
 * Tests for the verification code generators — Java, Node.js, Python, Go, C#,
 * Ruby, Rust, JSON, and curl. Covers the four key scenarios: request-only,
 * response-only, request+response, and sequence mode.
 *
 * Assertions check for the correct client API call and key structural elements
 * without over-asserting exact whitespace.
 */
import { describe, it, expect } from 'vitest';
import {
  verifyToJava,
  verifyToJson,
  verifyToCurl,
  verifyToNode,
  verifyToPython,
  verifyToGo,
  verifyToCsharp,
  verifyToRuby,
  verifyToRust,
  type VerificationCodegenInput,
} from '../lib/verificationCodegen';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const BASE_URL = 'http://localhost:1080';

function baseInput(overrides?: Partial<VerificationCodegenInput>): VerificationCodegenInput {
  return {
    mode: 'single',
    httpRequest: { method: 'GET', path: '/api/orders' },
    httpResponse: {},
    times: { mode: 'atLeast', count: 1 },
    httpRequests: [],
    httpResponses: [],
    baseUrl: BASE_URL,
    ...overrides,
  };
}

const REQUEST_ONLY = baseInput();

const RESPONSE_ONLY = baseInput({
  httpRequest: {},
  httpResponse: { statusCode: 200, body: '{"ok":true}' },
  times: { mode: 'atMost', count: 5 },
});

const BOTH = baseInput({
  httpRequest: { method: 'POST', path: '/api/submit', headers: { 'Content-Type': ['application/json'] } },
  httpResponse: { statusCode: 201, body: 'created' },
  times: { mode: 'exactly', count: 2 },
});

const BETWEEN = baseInput({
  httpRequest: { method: 'GET', path: '/api' },
  times: { mode: 'between', count: 1, atMost: 5 },
});

const SEQUENCE = baseInput({
  mode: 'sequence',
  httpRequests: [{ method: 'POST', path: '/a' }, { method: 'GET', path: '/b' }],
  httpResponses: [undefined, undefined],
});

const SEQUENCE_WITH_RESPONSES = baseInput({
  mode: 'sequence',
  httpRequests: [{ method: 'POST', path: '/a' }, { method: 'GET', path: '/b' }],
  httpResponses: [{ statusCode: 201 }, { statusCode: 200, body: 'ok' }],
});

// ---------------------------------------------------------------------------
// Java
// ---------------------------------------------------------------------------

describe('verifyToJava', () => {
  it('generates request-only verify with VerificationTimes', () => {
    const code = verifyToJava(REQUEST_ONLY);
    expect(code).toContain('import static org.mockserver.model.HttpRequest.request;');
    expect(code).toContain('import org.mockserver.verify.VerificationTimes;');
    expect(code).toContain('mockServerClient');
    expect(code).toContain('.verify(');
    expect(code).toContain('request()');
    expect(code).toContain('.withMethod("GET")');
    expect(code).toContain('.withPath("/api/orders")');
    expect(code).toContain('VerificationTimes.atLeast(1)');
    // Should NOT contain response()
    expect(code).not.toContain('response()');
  });

  it('generates response-only verify', () => {
    const code = verifyToJava(RESPONSE_ONLY);
    expect(code).toContain('response()');
    expect(code).toContain('.withStatusCode(200)');
    expect(code).toContain('VerificationTimes.atMost(5)');
    // Should NOT contain request()
    expect(code).not.toContain('request()');
  });

  it('generates request+response verify with exactly times', () => {
    const code = verifyToJava(BOTH);
    expect(code).toContain('request()');
    expect(code).toContain('response()');
    expect(code).toContain('.withStatusCode(201)');
    expect(code).toContain('VerificationTimes.exactly(2)');
  });

  it('generates between times', () => {
    const code = verifyToJava(BETWEEN);
    expect(code).toContain('VerificationTimes.between(1, 5)');
  });

  it('generates sequence verify with varargs requests', () => {
    const code = verifyToJava(SEQUENCE);
    expect(code).toContain('.verify(');
    expect(code).toContain('.withPath("/a")');
    expect(code).toContain('.withPath("/b")');
    // No VerificationTimes in sequence
    expect(code).not.toContain('VerificationTimes');
    // Request-only sequence should NOT use verificationSequence()
    expect(code).not.toContain('verificationSequence()');
  });

  it('generates sequence verify with verificationSequence when responses are present', () => {
    const code = verifyToJava(SEQUENCE_WITH_RESPONSES);
    expect(code).toContain('import static org.mockserver.model.HttpRequest.request;');
    expect(code).toContain('import static org.mockserver.model.HttpResponse.response;');
    expect(code).toContain('import static org.mockserver.verify.VerificationSequence.verificationSequence;');
    expect(code).toContain('verificationSequence()');
    expect(code).toContain('.withRequests(');
    expect(code).toContain('.withResponses(');
    expect(code).toContain('.withPath("/a")');
    expect(code).toContain('.withPath("/b")');
    expect(code).toContain('.withStatusCode(201)');
    expect(code).toContain('.withStatusCode(200)');
    // Should NOT contain the old "NOTE" comment
    expect(code).not.toContain('NOTE');
    expect(code).not.toContain('does not support');
  });
});

// ---------------------------------------------------------------------------
// Node.js
// ---------------------------------------------------------------------------

describe('verifyToNode', () => {
  it('generates request-only verify', () => {
    const code = verifyToNode(REQUEST_ONLY);
    expect(code).toContain("require('mockserver-client')");
    expect(code).toContain('mockServerClient("localhost", 1080)');
    expect(code).toContain('.verify(');
    expect(code).toContain('"method": "GET"');
    expect(code).toContain('1, undefined'); // atLeast=1, atMost=undefined
  });

  it('generates response-only verify using verifyResponse', () => {
    const code = verifyToNode(RESPONSE_ONLY);
    expect(code).toContain('.verifyResponse(');
    expect(code).toContain('"statusCode": 200');
  });

  it('generates request+response verify using verifyRequestAndResponse', () => {
    const code = verifyToNode(BOTH);
    expect(code).toContain('.verifyRequestAndResponse(');
    expect(code).toContain('"method": "POST"');
    expect(code).toContain('"statusCode": 201');
  });

  it('generates sequence with verifySequenceWithResponses when responses present', () => {
    const code = verifyToNode(SEQUENCE_WITH_RESPONSES);
    expect(code).toContain('.verifySequenceWithResponses(');
    expect(code).toContain('"path": "/a"');
  });

  it('generates sequence with verifySequence when no responses', () => {
    const code = verifyToNode(SEQUENCE);
    expect(code).toContain('.verifySequence(');
    expect(code).not.toContain('verifySequenceWithResponses');
  });
});

// ---------------------------------------------------------------------------
// Python
// ---------------------------------------------------------------------------

describe('verifyToPython', () => {
  it('generates request-only verify', () => {
    const code = verifyToPython(REQUEST_ONLY);
    expect(code).toContain('from mockserver import');
    expect(code).toContain('client.verify(');
    expect(code).toContain('request=HttpRequest.from_dict(');
    expect(code).toContain('VerificationTimes.at_least(1)');
  });

  it('generates response-only verify with response= kwarg', () => {
    const code = verifyToPython(RESPONSE_ONLY);
    expect(code).toContain('response=HttpResponse.from_dict(');
    expect(code).toContain('VerificationTimes.at_most(5)');
    // No request= kwarg
    expect(code).not.toContain('request=');
  });

  it('generates request+response verify', () => {
    const code = verifyToPython(BOTH);
    expect(code).toContain('request=');
    expect(code).toContain('response=');
    expect(code).toContain('VerificationTimes.exactly(2)');
  });

  it('generates sequence with responses= kwarg', () => {
    const code = verifyToPython(SEQUENCE_WITH_RESPONSES);
    expect(code).toContain('client.verify_sequence(');
    expect(code).toContain('responses=[');
    expect(code).toContain('HttpResponse.from_dict(');
  });
});

// ---------------------------------------------------------------------------
// Go
// ---------------------------------------------------------------------------

describe('verifyToGo', () => {
  it('generates request-only Verify call with fluent builder', () => {
    const code = verifyToGo(REQUEST_ONLY);
    expect(code).toContain('mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"');
    expect(code).toContain('client := mockserver.New("localhost", 1080)');
    expect(code).toContain('req := mockserver.Request()');
    expect(code).toContain('.Method("GET")');
    expect(code).toContain('.Path("/api/orders")');
    expect(code).toContain('client.Verify(req, mockserver.AtLeast(1))');
  });

  it('generates response-only VerifyResponse with nil request', () => {
    const code = verifyToGo(RESPONSE_ONLY);
    expect(code).toContain('resp := mockserver.Response()');
    expect(code).toContain('.StatusCode(200)');
    expect(code).toContain('client.VerifyResponse(nil, resp, mockserver.AtMost(5))');
  });

  it('generates request+response VerifyResponse', () => {
    const code = verifyToGo(BOTH);
    expect(code).toContain('req := mockserver.Request()');
    expect(code).toContain('resp := mockserver.Response()');
    expect(code).toContain('client.VerifyResponse(req, resp, mockserver.ExactlyTimes(2))');
  });

  it('generates sequence with VerifyResponseSequence when responses present', () => {
    const code = verifyToGo(SEQUENCE_WITH_RESPONSES);
    expect(code).toContain('client.VerifyResponseSequence(');
    expect(code).toContain('req0 := mockserver.Request()');
    expect(code).toContain('req1 := mockserver.Request()');
    expect(code).toContain('resp0 := mockserver.Response()');
    expect(code).toContain('resp1 := mockserver.Response()');
    expect(code).toContain('[]*mockserver.RequestBuilder{req0, req1}');
    expect(code).toContain('[]*mockserver.ResponseBuilder{resp0, resp1}');
  });

  it('generates sequence with VerifySequence when no responses', () => {
    const code = verifyToGo(SEQUENCE);
    expect(code).toContain('client.VerifySequence(');
    expect(code).toContain('req0 := mockserver.Request()');
    expect(code).toContain('req1 := mockserver.Request()');
    expect(code).not.toContain('VerifyResponseSequence');
  });

  it('does NOT emit the old builder methods or encoding/json import', () => {
    // Verify all key scenarios do not contain the old buggy API
    for (const input of [REQUEST_ONLY, RESPONSE_ONLY, BOTH, SEQUENCE, SEQUENCE_WITH_RESPONSES]) {
      const code = verifyToGo(input);
      expect(code).not.toContain('NewRequestBuilder');
      expect(code).not.toContain('FromJSON');
      expect(code).not.toContain('json.Unmarshal');
      expect(code).not.toContain('"encoding/json"');
    }
  });
});

// ---------------------------------------------------------------------------
// C# / .NET
// ---------------------------------------------------------------------------

describe('verifyToCsharp', () => {
  it('generates request-only Verify call', () => {
    const code = verifyToCsharp(REQUEST_ONLY);
    expect(code).toContain('new MockServerClient("localhost", 1080)');
    expect(code).toContain('client.Verify(request, VerificationTimes.AtLeastTimes(1))');
  });

  it('generates response-only Verify with null request', () => {
    const code = verifyToCsharp(RESPONSE_ONLY);
    expect(code).toContain('client.Verify(null, response, VerificationTimes.AtMostTimes(5))');
  });

  it('generates request+response Verify', () => {
    const code = verifyToCsharp(BOTH);
    expect(code).toContain('client.Verify(request, response, VerificationTimes.ExactlyTimes(2))');
  });

  it('generates between via new VerificationTimes { AtLeast, AtMost }', () => {
    const code = verifyToCsharp(BETWEEN);
    expect(code).toContain('new VerificationTimes { AtLeast = 1, AtMost = 5 }');
  });

  it('generates sequence with VerifySequence', () => {
    const code = verifyToCsharp(SEQUENCE);
    expect(code).toContain('client.VerifySequence(');
  });

  it('generates sequence with response lists', () => {
    const code = verifyToCsharp(SEQUENCE_WITH_RESPONSES);
    expect(code).toContain('new List<HttpRequest>');
    expect(code).toContain('new List<HttpResponse>');
  });
});

// ---------------------------------------------------------------------------
// Ruby
// ---------------------------------------------------------------------------

describe('verifyToRuby', () => {
  it('generates request-only verify', () => {
    const code = verifyToRuby(REQUEST_ONLY);
    expect(code).toContain("require 'mockserver-client'");
    expect(code).toContain('client.verify(request, times:');
    expect(code).toContain('VerificationTimes.at_least(1)');
  });

  it('generates response-only verify', () => {
    const code = verifyToRuby(RESPONSE_ONLY);
    expect(code).toContain('client.verify(times:');
    expect(code).toContain('response: response');
    expect(code).toContain('VerificationTimes.at_most(5)');
  });

  it('generates request+response verify', () => {
    const code = verifyToRuby(BOTH);
    expect(code).toContain('client.verify(request, times:');
    expect(code).toContain('response: response');
  });

  it('generates sequence with responses:', () => {
    const code = verifyToRuby(SEQUENCE_WITH_RESPONSES);
    expect(code).toContain('client.verify_sequence(');
    expect(code).toContain('responses:');
  });
});

// ---------------------------------------------------------------------------
// Rust
// ---------------------------------------------------------------------------

describe('verifyToRust', () => {
  it('generates request-only verify', () => {
    const code = verifyToRust(REQUEST_ONLY);
    expect(code).toContain('ClientBuilder::new("localhost", 1080)');
    expect(code).toContain('client.verify(request, VerificationTimes::at_least(1))');
  });

  it('generates response-only verify_response', () => {
    const code = verifyToRust(RESPONSE_ONLY);
    expect(code).toContain('client.verify_response(response, VerificationTimes::at_most(5))');
  });

  it('generates request+response verify_request_and_response', () => {
    const code = verifyToRust(BOTH);
    expect(code).toContain('client.verify_request_and_response(request, response, VerificationTimes::exactly(2))');
  });

  it('generates sequence with verify_sequence_with_responses when responses present', () => {
    const code = verifyToRust(SEQUENCE_WITH_RESPONSES);
    expect(code).toContain('client.verify_sequence_with_responses(');
  });

  it('generates sequence with verify_sequence when no responses', () => {
    const code = verifyToRust(SEQUENCE);
    expect(code).toContain('client.verify_sequence(');
    expect(code).not.toContain('verify_sequence_with_responses');
  });
});

// ---------------------------------------------------------------------------
// JSON
// ---------------------------------------------------------------------------

describe('verifyToJson', () => {
  it('produces the same body the panel would post to /verify for request-only', () => {
    const json = verifyToJson(REQUEST_ONLY);
    const parsed = JSON.parse(json);
    expect(parsed.httpRequest).toEqual({ method: 'GET', path: '/api/orders' });
    expect(parsed.times).toEqual({ atLeast: 1 });
    expect(parsed).not.toHaveProperty('httpResponse');
  });

  it('produces the body for response-only verify', () => {
    const json = verifyToJson(RESPONSE_ONLY);
    const parsed = JSON.parse(json);
    expect(parsed).not.toHaveProperty('httpRequest');
    expect(parsed.httpResponse).toEqual({ statusCode: 200, body: '{"ok":true}' });
    expect(parsed.times).toEqual({ atMost: 5 });
  });

  it('produces the body for request+response verify', () => {
    const json = verifyToJson(BOTH);
    const parsed = JSON.parse(json);
    expect(parsed.httpRequest).toBeDefined();
    expect(parsed.httpResponse).toBeDefined();
    expect(parsed.times).toEqual({ atLeast: 2, atMost: 2 });
  });

  it('produces the body for verifySequence', () => {
    const json = verifyToJson(SEQUENCE);
    const parsed = JSON.parse(json);
    expect(parsed.httpRequests).toBeInstanceOf(Array);
    expect(parsed.httpRequests).toHaveLength(2);
    expect(parsed).not.toHaveProperty('httpResponses');
  });

  it('produces the body for verifySequence with responses', () => {
    const json = verifyToJson(SEQUENCE_WITH_RESPONSES);
    const parsed = JSON.parse(json);
    expect(parsed.httpRequests).toHaveLength(2);
    expect(parsed.httpResponses).toHaveLength(2);
    expect(parsed.httpResponses[0]).toEqual({ statusCode: 201 });
  });
});

// ---------------------------------------------------------------------------
// curl
// ---------------------------------------------------------------------------

describe('verifyToCurl', () => {
  it('produces a PUT to /mockserver/verify for single mode', () => {
    const curl = verifyToCurl(REQUEST_ONLY);
    expect(curl).toContain('-X PUT');
    expect(curl).toContain("'http://localhost:1080/mockserver/verify'");
    expect(curl).toContain("Content-Type: application/json");
    expect(curl).toContain('-d');
  });

  it('produces a PUT to /mockserver/verifySequence for sequence mode', () => {
    const curl = verifyToCurl(SEQUENCE);
    expect(curl).toContain("'http://localhost:1080/mockserver/verifySequence'");
  });

  it('includes -v flag', () => {
    const curl = verifyToCurl(REQUEST_ONLY);
    expect(curl).toContain('-v');
  });
});

describe('typed body matchers (E2E-VERIFY-1)', () => {
  const SUBSTRING = { type: 'STRING', string: 'widget', subString: true };
  const PARTIAL_JSON = { type: 'JSON', json: '{"order":"widget"}' };
  const TYPED = baseInput({
    httpRequest: { path: '/x', body: SUBSTRING },
    httpResponse: { statusCode: 200, body: PARTIAL_JSON },
  });

  it('renders Java subString(...) and json(...) with their static imports', () => {
    const code = verifyToJava(TYPED);
    expect(code).toContain('.withBody(subString("widget"))');
    expect(code).toContain('.withBody(json("{\\"order\\":\\"widget\\"}"))');
    expect(code).toContain('import static org.mockserver.model.StringBody.subString;');
    expect(code).toContain('import static org.mockserver.model.JsonBody.json;');
  });

  it('imports the body helpers for sequence steps too', () => {
    const code = verifyToJava(baseInput({ mode: 'sequence', httpRequests: [{ body: PARTIAL_JSON }, { path: '/b' }] }));
    expect(code).toContain('.withBody(json(');
    expect(code).toContain('import static org.mockserver.model.JsonBody.json;');
  });

  it('renders the Go request matcher with JSONBody / a substring ALL_OF, and flags the response-body limit', () => {
    const json = verifyToGo(baseInput({ httpRequest: { path: '/x', body: PARTIAL_JSON } }));
    expect(json).toContain('.JSONBody("{\\"order\\":\\"widget\\"}")');
    const code = verifyToGo(TYPED);
    expect(code).toContain('.AllOfBody(mockserver.SubStringBody("widget"))');
    expect(code).toContain('// The Go ResponseBuilder sets an exact body');
  });

  it('passes the typed bodies through unchanged in the JSON wire body', () => {
    const parsed = JSON.parse(verifyToJson(TYPED));
    expect(parsed.httpRequest.body).toEqual(SUBSTRING);
    expect(parsed.httpResponse.body).toEqual(PARTIAL_JSON);
  });
});

describe('between upper bound (E2E-VERIFY-5)', () => {
  it('is rendered as entered, never raised to the lower bound', () => {
    const input = baseInput({ times: { mode: 'between', count: 3, atMost: 1 } });
    expect(verifyToJava(input)).toContain('VerificationTimes.between(3, 1)');
    expect(JSON.parse(verifyToJson(input)).times).toEqual({ atLeast: 3, atMost: 1 });
  });
});

describe('response status range', () => {
  const RANGE = baseInput({ httpRequest: {}, httpResponse: { statusCodeRange: '>= 400' } });

  it('Java sets it with withStatusCodeRange', () => {
    expect(verifyToJava(RANGE)).toContain('.withStatusCodeRange(">= 400")');
  });

  it('the JSON-carrying clients keep it as statusCodeRange', () => {
    for (const code of [verifyToJson(RANGE), verifyToNode(RANGE), verifyToCsharp(RANGE), verifyToRuby(RANGE), verifyToRust(RANGE)]) {
      expect(code).toMatch(/statusCodeRange/);
    }
    expect(verifyToPython(RANGE)).toContain('"statusCodeRange": ">= 400"');
  });

  it('Go sends it with net/http, since its ResponseBuilder cannot set a range', () => {
    const go = verifyToGo(RANGE);
    expect(go).toContain('http.NewRequest(http.MethodPut, "http://localhost:1080/mockserver/verify"');
    expect(go).toContain('\\"statusCodeRange\\":\\">= 400\\"');
    expect(go).toContain('http.StatusAccepted');
    expect(go).not.toContain('mockserver.Response()');
    const seq = verifyToGo(baseInput({ mode: 'sequence', httpRequests: [{ path: '/a' }], httpResponses: [{ statusCodeRange: '5XX' }] }));
    expect(seq).toContain('/mockserver/verifySequence');
  });

  it('Go keeps the client builders when there is no range', () => {
    expect(verifyToGo(RESPONSE_ONLY)).toContain('mockserver.Response()');
  });
});
