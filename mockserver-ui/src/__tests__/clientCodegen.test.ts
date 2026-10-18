/**
 * Tests for the Node / Python / Go / C# client-library code generators.
 *
 * All four hydrate the same expectation JSON the JSON tab shows, via each
 * client's native facility, then register through the native client. The tests
 * assert the client wrapper and that the full payload (including templateFile /
 * FILE-body fields) round-trips into the generated snippet.
 */
import { describe, it, expect } from 'vitest';
import {
  standardToNode,
  standardToPython,
  standardToGo,
  standardToCsharp,
  standardToRuby,
  standardToRust,
  type StandardMatcher,
  type StandardActionPayload,
} from '../lib/standardCodegen';

function baseMatcher(overrides?: Partial<StandardMatcher>): StandardMatcher {
  return {
    id: '', method: 'GET', path: '/api', headers: '', queryString: '', cookies: '',
    pathParams: '', body: '', bodyBinary: false, bodyMatcherType: 'string',
    priority: 0, times: 0, ...overrides,
  };
}

const BASE_URL = 'http://localhost:1080';

const templateFileAction: StandardActionPayload = {
  type: 'forward_template',
  forwardTemplate: { templateType: 'VELOCITY', template: '', templateFile: 'templates/foo.vm' },
};

const fileBodyAction: StandardActionPayload = {
  type: 'static',
  static: {
    statusCode: 200, body: '', contentType: 'application/json',
    bodyFromFile: true, filePath: 'responses/order.json', fileTemplateType: 'MUSTACHE',
  },
};

describe('standardToNode', () => {
  it('wraps the expectation JSON in mockAnyResponse with host/port', () => {
    const code = standardToNode(baseMatcher(), templateFileAction, BASE_URL);
    expect(code).toContain("require('mockserver-client')");
    expect(code).toContain('mockServerClient("localhost", 1080)');
    expect(code).toContain('.mockAnyResponse(');
    // full fidelity — the templateFile survives into the generated object
    expect(code).toContain('"httpForwardTemplate"');
    expect(code).toContain('"templateFile": "templates/foo.vm"');
  });

  it('carries a templated FILE body through', () => {
    const code = standardToNode(baseMatcher(), fileBodyAction, BASE_URL);
    expect(code).toContain('"type": "FILE"');
    expect(code).toContain('"templateType": "MUSTACHE"');
  });
});

describe('standardToPython', () => {
  it('builds typed client objects and registers via upsert (no from_dict blob)', () => {
    const code = standardToPython(baseMatcher(), templateFileAction, BASE_URL);
    expect(code).toMatch(/^from mockserver import /m);
    expect(code).toContain('MockServerClient("localhost", 1080).upsert(');
    expect(code).toContain('Expectation(');
    // the forward template action maps onto a typed HttpTemplate, carrying templateFile
    expect(code).toContain('http_forward_template=HttpTemplate(');
    expect(code).toContain('template_file="templates/foo.vm"');
    expect(code).not.toContain('from_dict');
  });

  it('renders a JSON body matcher as a typed Body with Python literals', () => {
    const code = standardToPython(baseMatcher({ secure: true, bodyMatcherType: 'json', body: '{"ok":true,"n":null}' }), {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
    }, BASE_URL);
    expect(code).toContain('body=Body(');
    expect(code).toContain('"ok": True');
    expect(code).toContain('"n": None');
    expect(code).toContain('secure=True');
    expect(code).not.toContain('from_dict');
    expect(code).not.toContain(': true');
  });
});

describe('standardToGo', () => {
  it('constructs a typed mockserver.Expectation and Upserts it (no embedded JSON)', () => {
    const code = standardToGo(baseMatcher(), templateFileAction, BASE_URL);
    expect(code).toContain('mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"');
    expect(code).toContain('mockserver.New("localhost", 1080)');
    // typed construction — the fluent request builder and a typed action struct
    expect(code).toContain('mockserver.Request().');
    expect(code).toContain('expectation := mockserver.Expectation{');
    expect(code).toContain('HttpForwardTemplate: &mockserver.HttpTemplate{');
    expect(code).toContain('TemplateFile: "templates/foo.vm"');
    expect(code).toContain('client.Upsert(expectation)');
    // the whole point of the rewrite: no JSON blob is unmarshalled
    expect(code).not.toContain('json.Unmarshal(expectationJSON');
    expect(code).not.toContain('encoding/json');
  });

  it('uses a typed FILE body constructor for a templated file response', () => {
    const code = standardToGo(baseMatcher(), fileBodyAction, BASE_URL);
    expect(code).toContain('Body: mockserver.FileBody{');
    expect(code).toContain('Type: "FILE"');
    expect(code).toContain('TemplateType: "MUSTACHE"');
  });

  it('needs no backtick break-out because Go strings are double-quoted', () => {
    const code = standardToGo(baseMatcher({ path: '/a`b' }), {
      type: 'static',
      static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
    }, BASE_URL);
    // the path is a normal Go double-quoted string literal (a backtick is legal inside it)
    expect(code).toContain('Path("/a`b")');
    // no raw-string backtick break-out remains from the old JSON-embedding emitter
    expect(code).not.toContain('` + "`" + `');
  });
});

describe('standardToRuby', () => {
  it('builds a typed Expectation from model constructors and upserts it', () => {
    const code = standardToRuby(baseMatcher(), templateFileAction, BASE_URL);
    expect(code).toContain("require 'mockserver-client'");
    expect(code).toContain("MockServer::Client.new('localhost', 1080)");
    expect(code).toContain('client.upsert(');
    expect(code).toContain('MockServer::Expectation.new(');
    // typed forward-template action, not an embedded JSON payload
    expect(code).toContain('MockServer::HttpTemplate.new(');
    expect(code).toContain('template_file: "templates/foo.vm"');
    // no whole-payload from_hash / heredoc round-trip anymore
    expect(code).not.toContain('from_hash');
    expect(code).not.toContain("<<~'JSON'");
  });

  it('carries a templated FILE body through as a typed Body', () => {
    const code = standardToRuby(baseMatcher(), fileBodyAction, BASE_URL);
    expect(code).toContain('MockServer::Body.new(');
    expect(code).toContain('type: "FILE"');
    expect(code).toContain('template_type: "MUSTACHE"');
  });
});

describe('standardToRust', () => {
  it('constructs typed client objects and upserts them (no from_str of the payload)', () => {
    const code = standardToRust(baseMatcher(), fileBodyAction, BASE_URL);
    expect(code).toContain('use mockserver_client::*;');
    expect(code).toContain('ClientBuilder::new("localhost", 1080).build()?');
    expect(code).toContain('Expectation::new(request)');
    expect(code).toContain('HttpRequest::new()');
    expect(code).toContain('HttpResponse::new()');
    expect(code).toContain('.status_code(200)');
    expect(code).toContain('client.upsert(&[expectation])?');
    // The whole expectation is never round-tripped through serde_json::from_str.
    expect(code).not.toContain('serde_json::from_str');
  });

  it('builds a FILE/typed-object response body with body_value, not the String body field or extra', () => {
    // HttpResponse.body is Option<String>; an object body goes through the typed
    // `.body_value(Body::object(..))` builder rather than the `extra` map.
    const code = standardToRust(baseMatcher(), fileBodyAction, BASE_URL);
    expect(code).toContain('.body_value(Body::object(');
    expect(code).not.toContain('.extra.insert("body"');
    expect(code).toContain('"type": "FILE"');
    expect(code).toContain('"templateType": "MUSTACHE"');
  });

  it('escapes body strings as normal Rust string literals (backtick and hash are literal)', () => {
    const code = standardToRust(baseMatcher({ path: '/a`b/c"#d' }), {
      type: 'static',
      static: { statusCode: 200, body: 'a"#b', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' },
    }, BASE_URL);
    // No raw-string wrapper anywhere; the quote is backslash-escaped, backtick/# are literal.
    expect(code).not.toContain('r#"');
    expect(code).not.toContain('r##"');
    expect(code).toContain('.path("/a`b/c\\"#d")');
    expect(code).toContain('.body("a\\"#b")');
  });
});

describe('clientHostPort (via generated snippets)', () => {
  it('defaults the port to 443 for https and 1080 on parse failure', () => {
    const https = standardToNode(baseMatcher(), { type: 'static', static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' } }, 'https://mock.example.com');
    expect(https).toContain('mockServerClient("mock.example.com", 443)');
    const garbage = standardToNode(baseMatcher(), { type: 'static', static: { statusCode: 200, body: '', contentType: '', bodyFromFile: false, filePath: '', fileTemplateType: '' } }, 'not a url');
    expect(garbage).toContain('mockServerClient("localhost", 1080)');
  });
});

describe('standardToCsharp', () => {
  it('constructs a typed Expectation with a typed FILE body and Upserts it', () => {
    const code = standardToCsharp(baseMatcher(), fileBodyAction, BASE_URL);
    expect(code).toContain('using MockServer.Client;');
    expect(code).toContain('using MockServer.Client.Models;');
    expect(code).toContain('new MockServerClient("localhost", 1080)');
    expect(code).toContain('client.Upsert(new Expectation');
    // typed construction — a FILE body is a typed FileBody, not a JSON blob
    expect(code).toContain('Body = new FileBody');
    expect(code).toContain('FilePath = "responses/order.json"');
    expect(code).toContain('TemplateType = FileTemplateType.MUSTACHE');
    // the whole-payload Deserialize<Expectation> approach is gone
    expect(code).not.toContain('JsonSerializer.Deserialize<Expectation>');
  });
});
