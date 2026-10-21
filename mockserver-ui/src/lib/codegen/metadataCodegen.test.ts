/// <reference types="vite/client" />
/**
 * Expectation `metadata` in every code tab: an edited expectation carrying the shared
 * fixture's metadata (every JSON value kind, including null and nested values) must be
 * rendered in each client's native form, never dropped.
 */
import { describe, it, expect } from 'vitest';
import { buildExpectationJson, standardToJava, type StandardMatcher, type StandardActionPayload } from '../standardCodegen';
import { standardToNode } from './node';
import { standardToPython } from './python';
import { standardToGo } from './go';
import { standardToCsharp } from './csharp';
import { standardToRuby } from './ruby';
import { standardToRust } from './rust';

const fixture = Object.values(
  import.meta.glob('../../../../test-fixtures/expectations/top_metadata.json', { eager: true, import: 'default' }),
)[0] as Record<string, unknown>;

const matcher: StandardMatcher = {
  id: 'metadata-1', method: '', path: '/metadata', headers: '', queryString: '', cookies: '', pathParams: '',
  body: '', bodyBinary: false, bodyMatcherType: 'string', priority: 0, times: 0,
};
const action: StandardActionPayload = {
  type: 'static',
  static: { statusCode: 200, body: 'ok', contentType: '' },
  editOriginal: fixture,
  editActionModeled: true,
};
const BASE_URL = 'http://localhost:1080';

describe('expectation metadata codegen', () => {
  it('the saved JSON (JSON / curl / Node tabs) carries the metadata verbatim', () => {
    expect(fixture['metadata']).toBeDefined();
    expect(buildExpectationJson(matcher, action)['metadata']).toEqual(fixture['metadata']);
    const node = standardToNode(matcher, action, BASE_URL);
    expect(node).toContain('"metadata": {');
    expect(node).toContain('"note": null');
  });

  it('Java: scalar entries as .withMetadata literals, objects and arrays as JSON text', () => {
    const java = standardToJava(matcher, action);
    expect(java).not.toContain('import org.mockserver.mock.MetadataJson');
    expect(java).toContain('.withMetadata("owner", "team-a")');
    expect(java).toContain('.withMetadata("ticket", 4211)');
    expect(java).toContain('.withMetadata("ratio", 0.5)');
    expect(java).toContain('.withMetadata("flaky", false)');
    expect(java).toContain('.withMetadata("note", (Object) null)');
    expect(java).toContain('.withMetadataJson("tags", "[\\"smoke\\",\\"checkout\\"]")');
    expect(java).toContain('.withMetadataJson("contact", "{\\"team\\":\\"checkout\\",\\"oncall\\":[\\"ann\\",\\"bob\\"]}")');
    expect(java).toContain('.withMetadata("literal", "!not?optional 100%+a b")');
    // modifiers sit on the ForwardChainExpectation, before the terminal action
    expect(java.indexOf('.withMetadata(')).toBeLessThan(java.indexOf('.respond('));
  });

  it('Java: integers outside int range are long literals', () => {
    const big: StandardActionPayload = { ...action, editOriginal: { ...fixture, metadata: { big: 4294967296 } } };
    const java = standardToJava(matcher, big);
    expect(java).toContain('.withMetadata("big", 4294967296L)');
    expect(java).not.toContain('MetadataJson');
  });

  it('Python: metadata= dict literal', () => {
    const py = standardToPython(matcher, action, BASE_URL);
    expect(py).toContain('metadata={');
    expect(py).toContain('"note": None');
    expect(py).toContain('"flaky": False');
  });

  it('Go: Metadata: mockserver.Metadata map literal', () => {
    const go = standardToGo(matcher, action, BASE_URL);
    expect(go).toContain('Metadata: mockserver.Metadata{');
    expect(go).toContain('"note": nil,');
    expect(go).not.toContain('NOTE: wire key "metadata"');
  });

  it('C#: Metadata dictionary with JsonElement for objects and arrays', () => {
    const cs = standardToCsharp(matcher, action, BASE_URL);
    expect(cs).toContain('Metadata = new Dictionary<string, object?>');
    expect(cs).toContain('["ticket"] = 4211');
    expect(cs).toContain('["note"] = null');
    expect(cs).toContain('["tags"] = JsonSerializer.Deserialize<JsonElement>(@"[""smoke"",""checkout""]")');
    expect(cs).toContain('using System.Text.Json;');
  });

  it('Ruby: metadata: hash literal', () => {
    const rb = standardToRuby(matcher, action, BASE_URL);
    expect(rb).toContain('metadata: {');
    expect(rb).toContain('"note" => nil');
  });

  it('Rust: one typed .metadata_entry per key, not the raw extra map', () => {
    const rs = standardToRust(matcher, action, BASE_URL);
    expect(rs).toContain('.metadata_entry("owner", serde_json::json!("team-a"))');
    expect(rs).toContain('.metadata_entry("note", serde_json::json!(null))');
    expect(rs).toContain('serde_json = "1"');
    expect(rs).not.toContain('extra.insert("metadata"');
  });
});
