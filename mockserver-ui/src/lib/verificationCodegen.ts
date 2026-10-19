/**
 * Codegen for MockServer verification calls — emits snippets for Java, Node.js,
 * Python, Go, C#, Ruby, Rust, plus the raw JSON wire body and a curl command.
 *
 * Used by the Verification panel's code-preview tabs so the user can copy/paste
 * client-library code that reproduces the verification outside the UI.
 *
 * The generated calls match the verify APIs actually shipped in each client
 * library. Request rendering reuses helpers from standardCodegen.ts so the
 * snippets are stylistically consistent with the Composer's code generation.
 */

import {
  escapeJava,
  clientHostPort,
  toPythonLiteral,
  rustRawString,
  indentAfterFirst,
  rustClientVersionRequirement,
} from './standardCodegen';
import {
  betweenUpperBound,
  buildVerifyBody,
  buildVerifySequenceBody,
  type VerificationTimesSpec,
} from './verification';

// ---------------------------------------------------------------------------
// Public types — mirror VerificationView's form state
// ---------------------------------------------------------------------------

export interface VerificationCodegenInput {
  mode: 'single' | 'sequence';
  /** Single-mode request matcher (empty object = no request matcher). */
  httpRequest: Record<string, unknown>;
  /** Single-mode response matcher (empty object = no response matcher). */
  httpResponse: Record<string, unknown>;
  /** Single-mode times spec. */
  times: VerificationTimesSpec;
  /** Sequence-mode request matchers. */
  httpRequests: Record<string, unknown>[];
  /** Sequence-mode response matchers (index-aligned; undefined = no response matcher at that position). */
  httpResponses: (Record<string, unknown> | undefined)[];
  /** MockServer base URL for client connection and curl commands. */
  baseUrl: string;
}

// ---------------------------------------------------------------------------
// Internal helpers — request/response rendering per language
// ---------------------------------------------------------------------------

const hasKeys = (o: Record<string, unknown> | undefined): boolean =>
  !!o && Object.keys(o).length > 0;

type BodyMatcherWire = { type?: string; string?: string; subString?: boolean; json?: string };

/** A request/response matcher's body as built by `bodyMatcher`, or undefined. */
function typedBody(m: Record<string, unknown>): BodyMatcherWire | undefined {
  const b = m['body'];
  return b && typeof b === 'object' ? (b as BodyMatcherWire) : undefined;
}

/** Java expression for a body matcher: json("…") or subString("…"). */
function bodyToJava(body: BodyMatcherWire): string {
  return body.type === 'JSON'
    ? `json("${escapeJava(body.json ?? '')}")`
    : `subString("${escapeJava(body.string ?? '')}")`;
}

/** Static imports the Java snippet needs for the typed bodies it renders. */
function javaBodyImports(matchers: (Record<string, unknown> | undefined)[], imports: Set<string>): void {
  for (const m of matchers) {
    const body = m ? typedBody(m) : undefined;
    if (!body) continue;
    imports.add(body.type === 'JSON'
      ? 'import static org.mockserver.model.JsonBody.json;'
      : 'import static org.mockserver.model.StringBody.subString;');
  }
}

/** Render request matcher fields as Java builder calls (request().withMethod(...)...). */
function requestToJava(req: Record<string, unknown>): string {
  const lines: string[] = ['request()'];
  if (req['method']) lines.push(`    .withMethod("${escapeJava(String(req['method']))}")`);
  if (req['path']) lines.push(`    .withPath("${escapeJava(String(req['path']))}")`);
  const headers = req['headers'] as Record<string, string[]> | undefined;
  if (headers) {
    for (const [k, vs] of Object.entries(headers)) {
      for (const v of vs) {
        lines.push(`    .withHeader("${escapeJava(k)}", "${escapeJava(v)}")`);
      }
    }
  }
  const query = req['queryStringParameters'] as Record<string, string[]> | undefined;
  if (query) {
    for (const [k, vs] of Object.entries(query)) {
      const values = vs.map((v) => `"${escapeJava(v)}"`).join(', ');
      lines.push(`    .withQueryStringParameter("${escapeJava(k)}", ${values})`);
    }
  }
  const body = typedBody(req);
  if (body) lines.push(`    .withBody(${bodyToJava(body)})`);
  return lines.join('\n');
}

/** Render response matcher fields as Java builder calls (response().withStatusCode(...)...). */
function responseToJava(resp: Record<string, unknown>): string {
  const lines: string[] = ['response()'];
  if (resp['statusCode'] != null) lines.push(`    .withStatusCode(${resp['statusCode']})`);
  if (typeof resp['statusCodeRange'] === 'string') lines.push(`    .withStatusCodeRange("${escapeJava(resp['statusCodeRange'])}")`);
  const headers = resp['headers'] as Record<string, string[]> | undefined;
  if (headers) {
    for (const [k, vs] of Object.entries(headers)) {
      for (const v of vs) {
        lines.push(`    .withHeader("${escapeJava(k)}", "${escapeJava(v)}")`);
      }
    }
  }
  const body = typedBody(resp);
  if (body) lines.push(`    .withBody(${bodyToJava(body)})`);
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Java times rendering — VerificationTimes.atLeast / atMost / exactly / between
// ---------------------------------------------------------------------------

function timesToJava(times: VerificationTimesSpec): string {
  const count = Math.max(0, Math.floor(times.count));
  switch (times.mode) {
    case 'atLeast': return `VerificationTimes.atLeast(${count})`;
    case 'atMost': return `VerificationTimes.atMost(${count})`;
    case 'exactly': return `VerificationTimes.exactly(${count})`;
    case 'between': {
      const upper = betweenUpperBound(times);
      return `VerificationTimes.between(${count}, ${upper})`;
    }
  }
}

// ---------------------------------------------------------------------------
// JSON output — reuses the body builders from verification.ts
// ---------------------------------------------------------------------------

export function verifyToJson(input: VerificationCodegenInput): string {
  if (input.mode === 'sequence') {
    return JSON.stringify(
      buildVerifySequenceBody(
        input.httpRequests,
        input.httpResponses.some((r) => r && Object.keys(r).length > 0)
          ? input.httpResponses
          : undefined,
      ),
      null,
      2,
    );
  }
  return JSON.stringify(
    buildVerifyBody(
      input.httpRequest,
      input.times,
      hasKeys(input.httpResponse) ? input.httpResponse : undefined,
    ),
    null,
    2,
  );
}

// ---------------------------------------------------------------------------
// curl output
// ---------------------------------------------------------------------------

export function verifyToCurl(input: VerificationCodegenInput): string {
  const endpoint = input.mode === 'sequence'
    ? '/mockserver/verifySequence'
    : '/mockserver/verify';
  const json = input.mode === 'sequence'
    ? JSON.stringify(buildVerifySequenceBody(
        input.httpRequests,
        input.httpResponses.some((r) => r && Object.keys(r).length > 0)
          ? input.httpResponses
          : undefined,
      ))
    : JSON.stringify(buildVerifyBody(
        input.httpRequest,
        input.times,
        hasKeys(input.httpResponse) ? input.httpResponse : undefined,
      ));
  const safe = json.replace(/'/g, `'\\''`);
  return `curl -v -X PUT '${input.baseUrl}${endpoint}' \\\n  -H 'Content-Type: application/json' \\\n  -d '${safe}'`;
}

// ---------------------------------------------------------------------------
// Java client codegen
// ---------------------------------------------------------------------------

export function verifyToJava(input: VerificationCodegenInput): string {
  const hasReq = hasKeys(input.httpRequest);
  const hasResp = hasKeys(input.httpResponse);
  const imports = new Set<string>();

  if (input.mode === 'sequence') {
    const hasAnyResp = input.httpResponses.some((r) => r && Object.keys(r).length > 0);
    imports.add('import static org.mockserver.model.HttpRequest.request;');
    javaBodyImports([...input.httpRequests, ...(hasAnyResp ? input.httpResponses : [])], imports);

    if (hasAnyResp) {
      // Response-paired sequence: verify(verificationSequence().withRequests(...).withResponses(...))
      imports.add('import static org.mockserver.model.HttpResponse.response;');
      imports.add('import static org.mockserver.verify.VerificationSequence.verificationSequence;');

      const lines: string[] = [];
      for (const imp of Array.from(imports).sort()) lines.push(imp);
      lines.push('');
      lines.push('mockServerClient');
      lines.push('  .verify(');
      lines.push('    verificationSequence()');
      const reqSnippets = input.httpRequests.map((req) => {
        if (Object.keys(req).length === 0) return '        request()';
        return '        ' + requestToJava(req).split('\n').join('\n        ');
      });
      lines.push('      .withRequests(');
      lines.push(reqSnippets.join(',\n'));
      lines.push('      )');
      const respSnippets = input.httpResponses.map((resp) => {
        if (!resp || Object.keys(resp).length === 0) return '        response()';
        return '        ' + responseToJava(resp).split('\n').join('\n        ');
      });
      lines.push('      .withResponses(');
      lines.push(respSnippets.join(',\n'));
      lines.push('      )');
      lines.push('  );');
      return lines.join('\n');
    }

    // Request-only sequence: verify(RequestDefinition... requestDefinitions)
    const lines: string[] = [];
    for (const imp of Array.from(imports).sort()) lines.push(imp);
    lines.push('');
    lines.push('mockServerClient');
    lines.push('  .verify(');
    const reqSnippets = input.httpRequests.map((req) => {
      if (Object.keys(req).length === 0) return '    request()';
      return '    ' + requestToJava(req).split('\n').join('\n    ');
    });
    lines.push(reqSnippets.join(',\n'));
    lines.push('  );');
    return lines.join('\n');
  }

  // Single verify
  if (hasReq) imports.add('import static org.mockserver.model.HttpRequest.request;');
  if (hasResp) imports.add('import static org.mockserver.model.HttpResponse.response;');
  imports.add('import org.mockserver.verify.VerificationTimes;');
  javaBodyImports([hasReq ? input.httpRequest : undefined, hasResp ? input.httpResponse : undefined], imports);

  const lines: string[] = [];
  for (const imp of Array.from(imports).sort()) lines.push(imp);
  lines.push('');
  lines.push('mockServerClient');

  const timesJava = timesToJava(input.times);

  if (hasReq && hasResp) {
    // verify(request, response, times)
    lines.push('  .verify(');
    lines.push('    ' + requestToJava(input.httpRequest).split('\n').join('\n    ') + ',');
    lines.push('    ' + responseToJava(input.httpResponse).split('\n').join('\n    ') + ',');
    lines.push(`    ${timesJava}`);
    lines.push('  );');
  } else if (hasResp) {
    // response-only: verify(response, times)
    lines.push('  .verify(');
    lines.push('    ' + responseToJava(input.httpResponse).split('\n').join('\n    ') + ',');
    lines.push(`    ${timesJava}`);
    lines.push('  );');
  } else {
    // request-only: verify(request, times)
    lines.push('  .verify(');
    lines.push('    ' + requestToJava(input.httpRequest).split('\n').join('\n    ') + ',');
    lines.push(`    ${timesJava}`);
    lines.push('  );');
  }

  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Node.js client codegen
// ---------------------------------------------------------------------------

function nodeTimesArgs(times: VerificationTimesSpec): string {
  const count = Math.max(0, Math.floor(times.count));
  switch (times.mode) {
    case 'atLeast': return `${count}, undefined`;
    case 'atMost': return `undefined, ${count}`;
    case 'exactly': return `${count}, ${count}`;
    case 'between': {
      const upper = betweenUpperBound(times);
      return `${count}, ${upper}`;
    }
  }
}

export function verifyToNode(input: VerificationCodegenInput): string {
  const { host, port } = clientHostPort(input.baseUrl);
  const hasReq = hasKeys(input.httpRequest);
  const hasResp = hasKeys(input.httpResponse);

  const lines: string[] = [
    "const { mockServerClient } = require('mockserver-client');",
    '',
    `mockServerClient("${host}", ${port})`,
  ];

  if (input.mode === 'sequence') {
    const hasAnyResp = input.httpResponses.some((r) => r && Object.keys(r).length > 0);
    if (hasAnyResp) {
      // verifySequenceWithResponses([{request, response}, ...])
      const pairs = input.httpRequests.map((req, i) => {
        const resp = input.httpResponses[i];
        const reqJson = JSON.stringify(Object.keys(req).length > 0 ? req : {}, null, 4);
        const respJson = resp && Object.keys(resp).length > 0
          ? JSON.stringify(resp, null, 4)
          : '{}';
        return `    { request: ${indentAfterFirst(reqJson, 6)}, response: ${indentAfterFirst(respJson, 6)} }`;
      });
      lines.push(`  .verifySequenceWithResponses([`);
      lines.push(pairs.join(',\n'));
      lines.push('  ])');
    } else {
      // verifySequence(...matchers)
      const reqSnippets = input.httpRequests.map((req) => {
        const j = JSON.stringify(Object.keys(req).length > 0 ? req : {}, null, 4);
        return `    ${indentAfterFirst(j, 4)}`;
      });
      lines.push('  .verifySequence(');
      lines.push(reqSnippets.join(',\n'));
      lines.push('  )');
    }
  } else {
    // Single mode
    if (hasReq && hasResp) {
      const reqJson = JSON.stringify(input.httpRequest, null, 4);
      const respJson = JSON.stringify(input.httpResponse, null, 4);
      lines.push(`  .verifyRequestAndResponse(${indentAfterFirst(reqJson, 2)}, ${indentAfterFirst(respJson, 2)}, ${nodeTimesArgs(input.times)})`);
    } else if (hasResp) {
      const respJson = JSON.stringify(input.httpResponse, null, 4);
      lines.push(`  .verifyResponse(${indentAfterFirst(respJson, 2)}, ${nodeTimesArgs(input.times)})`);
    } else {
      const reqJson = JSON.stringify(hasReq ? input.httpRequest : {}, null, 4);
      lines.push(`  .verify(${indentAfterFirst(reqJson, 2)}, ${nodeTimesArgs(input.times)})`);
    }
  }

  lines.push('  .then(');
  lines.push('    () => console.log("verification passed"),');
  lines.push('    (error) => console.error(error)');
  lines.push('  );');
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Python client codegen
// ---------------------------------------------------------------------------

function pythonTimesExpr(times: VerificationTimesSpec): string {
  const count = Math.max(0, Math.floor(times.count));
  switch (times.mode) {
    case 'atLeast': return `VerificationTimes.at_least(${count})`;
    case 'atMost': return `VerificationTimes.at_most(${count})`;
    case 'exactly': return `VerificationTimes.exactly(${count})`;
    case 'between': {
      const upper = betweenUpperBound(times);
      return `VerificationTimes.between(${count}, ${upper})`;
    }
  }
}

export function verifyToPython(input: VerificationCodegenInput): string {
  const { host, port } = clientHostPort(input.baseUrl);
  const hasReq = hasKeys(input.httpRequest);
  const hasResp = hasKeys(input.httpResponse);

  const lines: string[] = [
    'from mockserver import MockServerClient, HttpRequest, HttpResponse, VerificationTimes',
    '',
    `client = MockServerClient("${host}", ${port})`,
    '',
  ];

  if (input.mode === 'sequence') {
    const hasAnyResp = input.httpResponses.some((r) => r && Object.keys(r).length > 0);
    const reqArgs = input.httpRequests.map((req) => {
      const dict = toPythonLiteral(Object.keys(req).length > 0 ? req : {}, 4);
      return `    HttpRequest.from_dict(${dict})`;
    });
    if (hasAnyResp) {
      const respArgs = input.httpResponses.map((resp) => {
        const dict = toPythonLiteral(resp && Object.keys(resp).length > 0 ? resp : {}, 8);
        return `        HttpResponse.from_dict(${dict})`;
      });
      lines.push('client.verify_sequence(');
      lines.push(reqArgs.join(',\n') + ',');
      lines.push('    responses=[');
      lines.push(respArgs.join(',\n') + ',');
      lines.push('    ],');
      lines.push(')');
    } else {
      lines.push('client.verify_sequence(');
      lines.push(reqArgs.join(',\n') + ',');
      lines.push(')');
    }
  } else {
    // Single mode
    const args: string[] = [];
    if (hasReq) {
      const dict = toPythonLiteral(input.httpRequest, 4);
      args.push(`    request=HttpRequest.from_dict(${dict})`);
    }
    args.push(`    times=${pythonTimesExpr(input.times)}`);
    if (hasResp) {
      const dict = toPythonLiteral(input.httpResponse, 4);
      args.push(`    response=HttpResponse.from_dict(${dict})`);
    }
    lines.push('client.verify(');
    lines.push(args.join(',\n') + ',');
    lines.push(')');
  }

  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Go client codegen
// ---------------------------------------------------------------------------

function goTimesExpr(times: VerificationTimesSpec): string {
  const count = Math.max(0, Math.floor(times.count));
  switch (times.mode) {
    case 'atLeast': return `mockserver.AtLeast(${count})`;
    case 'atMost': return `mockserver.AtMost(${count})`;
    case 'exactly': return `mockserver.ExactlyTimes(${count})`;
    case 'between': {
      const upper = betweenUpperBound(times);
      return `mockserver.Between(${count}, ${upper})`;
    }
  }
}

/** Render a request matcher as a Go fluent RequestBuilder chain (mockserver.Request().Method(...)...). */
function requestToGo(req: Record<string, unknown>): string {
  let s = 'mockserver.Request()';
  if (req['method']) s += `.Method("${escapeJava(String(req['method']))}")`;
  if (req['path']) s += `.Path("${escapeJava(String(req['path']))}")`;
  const headers = req['headers'] as Record<string, string[]> | undefined;
  if (headers) {
    for (const [k, vs] of Object.entries(headers)) {
      s += `.Header("${escapeJava(k)}"${vs.map((v) => `, "${escapeJava(v)}"`).join('')})`;
    }
  }
  const query = req['queryStringParameters'] as Record<string, string[]> | undefined;
  if (query) {
    for (const [k, vs] of Object.entries(query)) {
      s += `.QueryStringParameter("${escapeJava(k)}"${vs.map((v) => `, "${escapeJava(v)}"`).join('')})`;
    }
  }
  const body = typedBody(req);
  if (body?.type === 'JSON') s += `.JSONBody("${escapeJava(body.json ?? '')}")`;
  // The RequestBuilder has no plain typed-body setter; a one-entry ALL_OF carries the substring matcher.
  else if (body) s += `.AllOfBody(mockserver.SubStringBody("${escapeJava(body.string ?? '')}"))`;
  return s;
}

/** Render a response matcher as a Go fluent ResponseBuilder chain (mockserver.Response().StatusCode(...)...). */
function responseToGo(resp: Record<string, unknown>): string {
  let s = 'mockserver.Response()';
  if (resp['statusCode'] != null) s += `.StatusCode(${resp['statusCode']})`;
  const headers = resp['headers'] as Record<string, string[]> | undefined;
  if (headers) {
    for (const [k, vs] of Object.entries(headers)) {
      s += `.Header("${escapeJava(k)}"${vs.map((v) => `, "${escapeJava(v)}"`).join('')})`;
    }
  }
  // The Go ResponseBuilder can only set a plain (exact-match) body, which the caller flags.
  const body = typedBody(resp);
  if (body) s += `.Body("${escapeJava(body.type === 'JSON' ? body.json ?? '' : body.string ?? '')}")`;
  return s;
}

/**
 * The Go client's ResponseBuilder cannot set `statusCodeRange`, so a verification with a status
 * range is sent with net/http instead of being emitted as a looser client call.
 */
function verifyToGoHttp(input: VerificationCodegenInput): string {
  const endpoint = input.mode === 'sequence' ? '/mockserver/verifySequence' : '/mockserver/verify';
  const json = JSON.stringify(JSON.parse(verifyToJson(input)));
  return [
    'package main',
    '',
    'import (',
    '\t"fmt"',
    '\t"io"',
    '\t"net/http"',
    '\t"strings"',
    ')',
    '',
    'func main() {',
    '\t// The Go client cannot express a status range, so this sends the verification directly.',
    `\tbody := ${JSON.stringify(json)}`,
    `\treq, err := http.NewRequest(http.MethodPut, ${JSON.stringify(`${input.baseUrl}${endpoint}`)}, strings.NewReader(body))`,
    '\tif err != nil {',
    '\t\tpanic(err)',
    '\t}',
    '\treq.Header.Set("Content-Type", "application/json")',
    '\tresp, err := http.DefaultClient.Do(req)',
    '\tif err != nil {',
    '\t\tpanic(err)',
    '\t}',
    '\tdefer resp.Body.Close()',
    '\tif resp.StatusCode != http.StatusAccepted {',
    '\t\tmessage, _ := io.ReadAll(resp.Body)',
    '\t\tpanic(fmt.Sprintf("verification failed (%d): %s", resp.StatusCode, message))',
    '\t}',
    '}',
  ].join('\n');
}

export function verifyToGo(input: VerificationCodegenInput): string {
  const ranged = (input.mode === 'sequence' ? input.httpResponses : [input.httpResponse])
    .some((r) => r && typeof r['statusCodeRange'] === 'string');
  if (ranged) return verifyToGoHttp(input);
  const { host, port } = clientHostPort(input.baseUrl);
  const hasReq = hasKeys(input.httpRequest);
  const hasResp = hasKeys(input.httpResponse);

  const lines: string[] = [
    'package main',
    '',
    'import (',
    '\tmockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"',
    ')',
    '',
    'func main() {',
    `\tclient := mockserver.New("${host}", ${port})`,
    '',
  ];

  const responseMatchers = input.mode === 'sequence' ? input.httpResponses : [hasResp ? input.httpResponse : undefined];
  if (responseMatchers.some((r) => r && typedBody(r))) {
    lines.push('\t// The Go ResponseBuilder sets an exact body: use the JSON or curl tab for a substring/JSON body match.');
  }

  if (input.mode === 'sequence') {
    const hasAnyResp = input.httpResponses.some((r) => r && Object.keys(r).length > 0);

    // Request builders (an empty Request() matches any request at that position)
    input.httpRequests.forEach((req, i) => {
      lines.push(`\treq${i} := ${requestToGo(Object.keys(req).length > 0 ? req : {})}`);
    });

    if (hasAnyResp) {
      input.httpResponses.forEach((resp, i) => {
        lines.push(`\tresp${i} := ${responseToGo(resp && Object.keys(resp).length > 0 ? resp : {})}`);
      });
      lines.push('');
      const reqList = input.httpRequests.map((_, i) => `req${i}`).join(', ');
      const respList = input.httpResponses.map((_, i) => `resp${i}`).join(', ');
      lines.push(`\tif err := client.VerifyResponseSequence([]*mockserver.RequestBuilder{${reqList}}, []*mockserver.ResponseBuilder{${respList}}); err != nil {`);
    } else {
      lines.push('');
      const reqList = input.httpRequests.map((_, i) => `req${i}`).join(', ');
      lines.push(`\tif err := client.VerifySequence(${reqList}); err != nil {`);
    }
    lines.push('\t\tpanic(err)');
    lines.push('\t}');
  } else {
    // Single mode
    if (hasReq) lines.push(`\treq := ${requestToGo(input.httpRequest)}`);
    if (hasResp) lines.push(`\tresp := ${responseToGo(input.httpResponse)}`);
    lines.push('');

    const timesExpr = goTimesExpr(input.times);

    if (hasReq && hasResp) {
      lines.push(`\tif err := client.VerifyResponse(req, resp, ${timesExpr}); err != nil {`);
    } else if (hasResp) {
      lines.push(`\tif err := client.VerifyResponse(nil, resp, ${timesExpr}); err != nil {`);
    } else {
      lines.push(`\tif err := client.Verify(req, ${timesExpr}); err != nil {`);
    }
    lines.push('\t\tpanic(err)');
    lines.push('\t}');
  }

  lines.push('}');
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// C# / .NET client codegen
// ---------------------------------------------------------------------------

function csharpTimesExpr(times: VerificationTimesSpec): string {
  const count = Math.max(0, Math.floor(times.count));
  switch (times.mode) {
    case 'atLeast': return `VerificationTimes.AtLeastTimes(${count})`;
    case 'atMost': return `VerificationTimes.AtMostTimes(${count})`;
    case 'exactly': return `VerificationTimes.ExactlyTimes(${count})`;
    case 'between': {
      // C# client has no built-in Between factory; construct manually
      const upper = betweenUpperBound(times);
      return `new VerificationTimes { AtLeast = ${count}, AtMost = ${upper} }`;
    }
  }
}

export function verifyToCsharp(input: VerificationCodegenInput): string {
  const { host, port } = clientHostPort(input.baseUrl);
  const hasReq = hasKeys(input.httpRequest);
  const hasResp = hasKeys(input.httpResponse);

  const lines: string[] = [
    'using System.Text.Json;',
    'using MockServer.Client;',
    'using MockServer.Client.Models;',
    '',
    `using var client = new MockServerClient("${host}", ${port});`,
    '',
  ];

  if (input.mode === 'sequence') {
    const hasAnyResp = input.httpResponses.some((r) => r && Object.keys(r).length > 0);
    const reqJsons = input.httpRequests.map((req) =>
      JSON.stringify(Object.keys(req).length > 0 ? req : {}).replace(/"/g, '""'),
    );

    reqJsons.forEach((verbatim, i) => {
      lines.push(`var request${i} = JsonSerializer.Deserialize<HttpRequest>(@"${verbatim}")!;`);
    });

    if (hasAnyResp) {
      const respJsons = input.httpResponses.map((resp) =>
        JSON.stringify(resp && Object.keys(resp).length > 0 ? resp : {}).replace(/"/g, '""'),
      );
      respJsons.forEach((verbatim, i) => {
        lines.push(`var response${i} = JsonSerializer.Deserialize<HttpResponse>(@"${verbatim}")!;`);
      });
      lines.push('');
      const reqList = reqJsons.map((_, i) => `request${i}`).join(', ');
      const respList = respJsons.map((_, i) => `response${i}`).join(', ');
      lines.push(`client.VerifySequence(new List<HttpRequest> { ${reqList} }, new List<HttpResponse> { ${respList} });`);
    } else {
      lines.push('');
      const reqList = reqJsons.map((_, i) => `request${i}`).join(', ');
      lines.push(`client.VerifySequence(${reqList});`);
    }
  } else {
    const timesExpr = csharpTimesExpr(input.times);

    if (hasReq) {
      const reqVerbatim = JSON.stringify(input.httpRequest).replace(/"/g, '""');
      lines.push(`var request = JsonSerializer.Deserialize<HttpRequest>(@"${reqVerbatim}")!;`);
    }
    if (hasResp) {
      const respVerbatim = JSON.stringify(input.httpResponse).replace(/"/g, '""');
      lines.push(`var response = JsonSerializer.Deserialize<HttpResponse>(@"${respVerbatim}")!;`);
    }
    lines.push('');

    if (hasReq && hasResp) {
      lines.push(`client.Verify(request, response, ${timesExpr});`);
    } else if (hasResp) {
      lines.push(`client.Verify(null, response, ${timesExpr});`);
    } else {
      lines.push(`client.Verify(request, ${timesExpr});`);
    }
  }

  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Ruby client codegen
// ---------------------------------------------------------------------------

function rubyTimesExpr(times: VerificationTimesSpec): string {
  const count = Math.max(0, Math.floor(times.count));
  switch (times.mode) {
    case 'atLeast': return `VerificationTimes.at_least(${count})`;
    case 'atMost': return `VerificationTimes.at_most(${count})`;
    case 'exactly': return `VerificationTimes.exactly(${count})`;
    case 'between': {
      const upper = betweenUpperBound(times);
      return `VerificationTimes.between(${count}, ${upper})`;
    }
  }
}

export function verifyToRuby(input: VerificationCodegenInput): string {
  const { host, port } = clientHostPort(input.baseUrl);
  const hasReq = hasKeys(input.httpRequest);
  const hasResp = hasKeys(input.httpResponse);

  const lines: string[] = [
    "require 'json'",
    "require 'mockserver-client'",
    '',
    `client = MockServer::Client.new('${host}', ${port})`,
    '',
  ];

  if (input.mode === 'sequence') {
    const hasAnyResp = input.httpResponses.some((r) => r && Object.keys(r).length > 0);

    const reqHeredocs = input.httpRequests.map((req, i) => {
      const json = JSON.stringify(Object.keys(req).length > 0 ? req : {}, null, 2);
      const indented = json.split('\n').map((l) => '  ' + l).join('\n');
      return [
        `request_${i}_json = <<~'JSON'`,
        indented,
        'JSON',
        `request_${i} = MockServer::HttpRequest.from_hash(JSON.parse(request_${i}_json))`,
      ].join('\n');
    });
    lines.push(reqHeredocs.join('\n\n'));

    if (hasAnyResp) {
      const respHeredocs = input.httpResponses.map((resp, i) => {
        const json = JSON.stringify(resp && Object.keys(resp).length > 0 ? resp : {}, null, 2);
        const indented = json.split('\n').map((l) => '  ' + l).join('\n');
        return [
          `response_${i}_json = <<~'JSON'`,
          indented,
          'JSON',
          `response_${i} = MockServer::HttpResponse.from_hash(JSON.parse(response_${i}_json))`,
        ].join('\n');
      });
      lines.push('');
      lines.push(respHeredocs.join('\n\n'));

      const reqList = input.httpRequests.map((_, i) => `request_${i}`).join(', ');
      const respList = input.httpResponses.map((_, i) => `response_${i}`).join(', ');
      lines.push('');
      lines.push(`client.verify_sequence(${reqList}, responses: [${respList}])`);
    } else {
      const reqList = input.httpRequests.map((_, i) => `request_${i}`).join(', ');
      lines.push('');
      lines.push(`client.verify_sequence(${reqList})`);
    }
  } else {
    if (hasReq) {
      const json = JSON.stringify(input.httpRequest, null, 2);
      const indented = json.split('\n').map((l) => '  ' + l).join('\n');
      lines.push("request_json = <<~'JSON'");
      lines.push(indented);
      lines.push('JSON');
      lines.push('request = MockServer::HttpRequest.from_hash(JSON.parse(request_json))');
    }
    if (hasResp) {
      const json = JSON.stringify(input.httpResponse, null, 2);
      const indented = json.split('\n').map((l) => '  ' + l).join('\n');
      lines.push('');
      lines.push("response_json = <<~'JSON'");
      lines.push(indented);
      lines.push('JSON');
      lines.push('response = MockServer::HttpResponse.from_hash(JSON.parse(response_json))');
    }
    lines.push('');

    const timesExpr = rubyTimesExpr(input.times);

    if (hasReq && hasResp) {
      lines.push(`client.verify(request, times: ${timesExpr}, response: response)`);
    } else if (hasResp) {
      lines.push(`client.verify(times: ${timesExpr}, response: response)`);
    } else {
      lines.push(`client.verify(request, times: ${timesExpr})`);
    }
  }

  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Rust client codegen
// ---------------------------------------------------------------------------

function rustTimesExpr(times: VerificationTimesSpec): string {
  const count = Math.max(0, Math.floor(times.count));
  switch (times.mode) {
    case 'atLeast': return `VerificationTimes::at_least(${count})`;
    case 'atMost': return `VerificationTimes::at_most(${count})`;
    case 'exactly': return `VerificationTimes::exactly(${count})`;
    case 'between': {
      const upper = betweenUpperBound(times);
      return `VerificationTimes::between(${count}, ${upper})`;
    }
  }
}

export function verifyToRust(input: VerificationCodegenInput): string {
  const { host, port } = clientHostPort(input.baseUrl);
  const hasReq = hasKeys(input.httpRequest);
  const hasResp = hasKeys(input.httpResponse);

  const lines: string[] = [
    `// Cargo.toml: mockserver-client = "${rustClientVersionRequirement()}" and serde_json = "1"`,
    'use mockserver_client::{ClientBuilder, HttpRequest, HttpResponse, VerificationTimes};',
    '',
    'fn main() -> mockserver_client::Result<()> {',
    `    let client = ClientBuilder::new("${host}", ${port}).build()?;`,
    '',
  ];

  if (input.mode === 'sequence') {
    const hasAnyResp = input.httpResponses.some((r) => r && Object.keys(r).length > 0);

    input.httpRequests.forEach((req, i) => {
      const json = JSON.stringify(Object.keys(req).length > 0 ? req : {}, null, 2);
      lines.push(`    let request_${i}: HttpRequest = serde_json::from_str(${rustRawString(json)})?;`);
    });

    if (hasAnyResp) {
      input.httpResponses.forEach((resp, i) => {
        const json = JSON.stringify(resp && Object.keys(resp).length > 0 ? resp : {}, null, 2);
        lines.push(`    let response_${i}: HttpResponse = serde_json::from_str(${rustRawString(json)})?;`);
      });
      lines.push('');
      const reqVec = input.httpRequests.map((_, i) => `request_${i}`).join(', ');
      const respVec = input.httpResponses.map((_, i) => `response_${i}`).join(', ');
      lines.push(`    client.verify_sequence_with_responses(vec![${reqVec}], vec![${respVec}])?;`);
    } else {
      lines.push('');
      const reqVec = input.httpRequests.map((_, i) => `request_${i}`).join(', ');
      lines.push(`    client.verify_sequence(vec![${reqVec}])?;`);
    }
  } else {
    const timesExpr = rustTimesExpr(input.times);

    if (hasReq) {
      const reqJson = JSON.stringify(input.httpRequest, null, 2);
      lines.push(`    let request: HttpRequest = serde_json::from_str(${rustRawString(reqJson)})?;`);
    }
    if (hasResp) {
      const respJson = JSON.stringify(input.httpResponse, null, 2);
      lines.push(`    let response: HttpResponse = serde_json::from_str(${rustRawString(respJson)})?;`);
    }
    lines.push('');

    if (hasReq && hasResp) {
      lines.push(`    client.verify_request_and_response(request, response, ${timesExpr})?;`);
    } else if (hasResp) {
      lines.push(`    client.verify_response(response, ${timesExpr})?;`);
    } else {
      lines.push(`    client.verify(request, ${timesExpr})?;`);
    }
  }

  lines.push('    Ok(())');
  lines.push('}');
  return lines.join('\n');
}
