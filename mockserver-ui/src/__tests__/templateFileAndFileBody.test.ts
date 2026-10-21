/**
 * Tests for the template-file and templated-file-body composer features:
 * - httpResponseTemplate / httpForwardTemplate `templateFile` (load a template from a file)
 * - httpResponse FILE body with `templateType` (render an external body file as a template)
 *
 * Each test asserts the emitted JSON shape matches the MockServer Java DTOs
 * (HttpTemplateDTO.templateFile, FileBodyDTO.templateType).
 */
import { describe, it, expect } from 'vitest';
import {
  buildExpectationJson,
  standardToJava,
  type StandardMatcher,
  type StandardActionPayload,
} from '../lib/standardCodegen';
import { standardToNode } from '../lib/codegen/node';
import { standardToPython } from '../lib/codegen/python';
import { standardToGo } from '../lib/codegen/go';
import { standardToCsharp } from '../lib/codegen/csharp';
import { standardToRuby } from '../lib/codegen/ruby';
import { standardToRust } from '../lib/codegen/rust';

function baseMatcher(overrides?: Partial<StandardMatcher>): StandardMatcher {
  return {
    id: '',
    method: 'GET',
    path: '/api/test',
    headers: '',
    queryString: '',
    cookies: '',
    pathParams: '',
    body: '',
    bodyBinary: false,
    bodyMatcherType: 'string',
    priority: 0,
    times: 0,
    ...overrides,
  };
}

describe('buildExpectationJson response template with templateFile', () => {
  it('emits templateFile and omits empty inline template', () => {
    const action: StandardActionPayload = {
      type: 'template',
      template: { templateType: 'MUSTACHE', template: '', templateFile: 'templates/r.mustache' },
    };
    const t = buildExpectationJson(baseMatcher(), action)['httpResponseTemplate'] as Record<string, unknown>;
    expect(t['templateType']).toBe('MUSTACHE');
    expect(t['templateFile']).toBe('templates/r.mustache');
    expect(t).not.toHaveProperty('template');
  });

  it('keeps the inline template when both are provided (inline wins)', () => {
    const action: StandardActionPayload = {
      type: 'template',
      template: { templateType: 'VELOCITY', template: '{ "statusCode": 200 }', templateFile: 'templates/r.vm' },
    };
    const t = buildExpectationJson(baseMatcher(), action)['httpResponseTemplate'] as Record<string, unknown>;
    expect(t['template']).toBe('{ "statusCode": 200 }');
    expect(t['templateFile']).toBe('templates/r.vm');
  });

  it('emits only inline template when no templateFile is set (unchanged behaviour)', () => {
    const action: StandardActionPayload = {
      type: 'template',
      template: { templateType: 'VELOCITY', template: '{ "statusCode": 200 }' },
    };
    const t = buildExpectationJson(baseMatcher(), action)['httpResponseTemplate'] as Record<string, unknown>;
    expect(t['template']).toBe('{ "statusCode": 200 }');
    expect(t).not.toHaveProperty('templateFile');
  });
});

describe('buildExpectationJson forward template with templateFile', () => {
  it('emits templateFile on httpForwardTemplate', () => {
    const action: StandardActionPayload = {
      type: 'forward_template',
      forwardTemplate: { templateType: 'MUSTACHE', template: '', templateFile: 'templates/f.mustache' },
    };
    const ft = buildExpectationJson(baseMatcher(), action)['httpForwardTemplate'] as Record<string, unknown>;
    expect(ft['templateFile']).toBe('templates/f.mustache');
    expect(ft).not.toHaveProperty('template');
  });
});

describe('buildExpectationJson static response with templated FILE body', () => {
  it('emits a FILE body with templateType and contentType on the body (not as a header)', () => {
    const action: StandardActionPayload = {
      type: 'static',
      static: {
        statusCode: 200,
        body: '',
        contentType: 'application/json',
        bodyFromFile: true,
        filePath: 'responses/order.json',
        fileTemplateType: 'MUSTACHE',
      },
    };
    const r = buildExpectationJson(baseMatcher(), action)['httpResponse'] as Record<string, unknown>;
    const body = r['body'] as Record<string, unknown>;
    expect(body['type']).toBe('FILE');
    expect(body['filePath']).toBe('responses/order.json');
    expect(body['templateType']).toBe('MUSTACHE');
    expect(body['contentType']).toBe('application/json');
    // content type lives on the FILE body, so it must not also be emitted as a header
    expect(r).not.toHaveProperty('headers');
  });

  it('emits a plain FILE body (no templateType) when none is selected', () => {
    const action: StandardActionPayload = {
      type: 'static',
      static: {
        statusCode: 200,
        body: '',
        contentType: '',
        bodyFromFile: true,
        filePath: 'responses/static.json',
        fileTemplateType: '',
      },
    };
    const body = (buildExpectationJson(baseMatcher(), action)['httpResponse'] as Record<string, unknown>)['body'] as Record<string, unknown>;
    expect(body['type']).toBe('FILE');
    expect(body['filePath']).toBe('responses/static.json');
    expect(body).not.toHaveProperty('templateType');
  });

  it('falls back to the inline string body when bodyFromFile is false', () => {
    const action: StandardActionPayload = {
      type: 'static',
      static: {
        statusCode: 200,
        body: '{"ok":true}',
        contentType: 'application/json',
        bodyFromFile: false,
        filePath: '',
        fileTemplateType: '',
      },
    };
    const r = buildExpectationJson(baseMatcher(), action)['httpResponse'] as Record<string, unknown>;
    expect(r['body']).toBe('{"ok":true}');
  });
});

// ---------------------------------------------------------------------------
// Java code generation (standardToJava) — regression for the templateFile /
// FILE-body fields that were previously dropped from the Java preview.
// ---------------------------------------------------------------------------

describe('standardToJava response template with templateFile', () => {
  it('emits .withTemplateFile() and the no-template builder when only a file is set', () => {
    const java = standardToJava(baseMatcher(), {
      type: 'template',
      template: { templateType: 'VELOCITY', template: '', templateFile: 'templates/foo.vm' },
    });
    expect(java).toContain('template(TemplateType.VELOCITY)');
    expect(java).toContain('.withTemplateFile("templates/foo.vm")');
    // must NOT emit an empty inline template
    expect(java).not.toContain('template(TemplateType.VELOCITY, "")');
  });

  it('emits both inline template and .withTemplateFile() when both are set', () => {
    const java = standardToJava(baseMatcher(), {
      type: 'template',
      template: { templateType: 'MUSTACHE', template: '{ "statusCode": 200 }', templateFile: 'templates/foo.mustache' },
    });
    expect(java).toContain('template(TemplateType.MUSTACHE, "{ \\"statusCode\\": 200 }")');
    expect(java).toContain('.withTemplateFile("templates/foo.mustache")');
  });
});

describe('standardToJava indentation alignment', () => {
  it('aligns the action argument with the matcher inside .when(...)', () => {
    const java = standardToJava(baseMatcher(), {
      type: 'forward_template',
      forwardTemplate: { templateType: 'VELOCITY', template: '', templateFile: 'templets/foo.vl' },
    });
    // request() (matcher) and template() (action) must start at the same column
    expect(java).toContain('\n    request()');
    expect(java).toContain('\n    template(TemplateType.VELOCITY)');
    // fluent calls one level deeper must also align
    expect(java).toContain('\n        .withMethod(');
    expect(java).toContain('\n        .withTemplateFile("templets/foo.vl")');
  });
});

describe('standardToJava forward template with templateFile', () => {
  it('emits .forward(template(...).withTemplateFile(...))', () => {
    const java = standardToJava(baseMatcher(), {
      type: 'forward_template',
      forwardTemplate: { templateType: 'VELOCITY', template: '', templateFile: 'templates/fwd.vm' },
    });
    expect(java).toContain('.forward(');
    expect(java).toContain('template(TemplateType.VELOCITY)');
    expect(java).toContain('.withTemplateFile("templates/fwd.vm")');
    expect(java).not.toContain('template(TemplateType.VELOCITY, "")');
  });
});

describe('standardToJava static response with templated FILE body', () => {
  it('emits file(path, MediaType, TemplateType) and the right imports', () => {
    const java = standardToJava(baseMatcher(), {
      type: 'static',
      static: {
        statusCode: 200,
        body: '',
        contentType: 'application/json',
        bodyFromFile: true,
        filePath: 'responses/order.json',
        fileTemplateType: 'MUSTACHE',
      },
    });
    expect(java).toContain('.withBody(file("responses/order.json", MediaType.parse("application/json"), TemplateType.MUSTACHE))');
    expect(java).toContain('import static org.mockserver.model.FileBody.file;');
    expect(java).toContain('import org.mockserver.model.MediaType;');
    expect(java).toContain('import org.mockserver.model.HttpTemplate.TemplateType;');
    // content type is on the FILE body, not a header
    expect(java).not.toContain('.withHeader("Content-Type"');
  });

  it('emits file(path, TemplateType) with no MediaType/null when a template engine is set but no content type', () => {
    const java = standardToJava(baseMatcher(), {
      type: 'static',
      static: {
        statusCode: 200,
        body: '',
        contentType: '',
        bodyFromFile: true,
        filePath: 'responses/order.json',
        fileTemplateType: 'VELOCITY',
      },
    });
    expect(java).toContain('.withBody(file("responses/order.json", TemplateType.VELOCITY))');
    expect(java).toContain('import org.mockserver.model.HttpTemplate.TemplateType;');
    expect(java).not.toContain('MediaType');
    expect(java).not.toContain('null');
  });

  it('emits a plain file(path) body with no templateType and no MediaType', () => {
    const java = standardToJava(baseMatcher(), {
      type: 'static',
      static: {
        statusCode: 200,
        body: '',
        contentType: '',
        bodyFromFile: true,
        filePath: 'responses/static.json',
        fileTemplateType: '',
      },
    });
    expect(java).toContain('.withBody(file("responses/static.json"))');
    expect(java).not.toContain('MediaType');
  });
});

// ---------------------------------------------------------------------------
// FILE body with a templated path (FileBodyDTO.templatedFilePath)
// ---------------------------------------------------------------------------

describe('a FILE body whose path is templated from the request', () => {
  const URL = 'http://localhost:1080';
  const templatedPath = 'responses/{{ request.pathParameters.name.0 }}.json';
  const fileAction = (overrides: Record<string, unknown>): StandardActionPayload => ({
    type: 'static',
    static: {
      statusCode: 200,
      body: '',
      contentType: 'application/json',
      bodyFromFile: true,
      filePath: templatedPath,
      fileTemplateType: 'MUSTACHE',
      ...overrides,
    },
  });
  const carried = fileAction({ templatedFilePath: true });

  it('is saved with templatedFilePath: true', () => {
    const body = (buildExpectationJson(baseMatcher(), carried)['httpResponse'] as Record<string, unknown>)['body'];
    expect(body).toEqual({
      type: 'FILE', filePath: templatedPath, templateType: 'MUSTACHE', templatedFilePath: true, contentType: 'application/json',
    });
  });

  it('omits the flag when unset, false, or when no template engine is selected', () => {
    for (const overrides of [{}, { templatedFilePath: false }, { templatedFilePath: true, fileTemplateType: '' }]) {
      const body = (buildExpectationJson(baseMatcher(), fileAction(overrides))['httpResponse'] as Record<string, unknown>)['body'];
      expect(body).not.toHaveProperty('templatedFilePath');
    }
    expect(standardToJava(baseMatcher(), fileAction({}))).not.toContain('withTemplatedFilePath');
  });

  it('Java chains withTemplatedFilePath(true) onto the FILE body', () => {
    expect(standardToJava(baseMatcher(), carried)).toContain(
      `.withBody(file("${templatedPath}", MediaType.parse("application/json"), TemplateType.MUSTACHE).withTemplatedFilePath(true))`,
    );
    expect(standardToJava(baseMatcher(), fileAction({ templatedFilePath: true, contentType: '' }))).toContain(
      `.withBody(file("${templatedPath}", TemplateType.MUSTACHE).withTemplatedFilePath(true))`,
    );
  });

  it('every typed client emitter carries the flag', () => {
    expect(standardToNode(baseMatcher(), carried, URL)).toContain('"templatedFilePath": true');
    expect(standardToPython(baseMatcher(), carried, URL)).toContain('templated_file_path=True');
    expect(standardToGo(baseMatcher(), carried, URL)).toMatch(/TemplatedFilePath:\s+true,/);
    expect(standardToCsharp(baseMatcher(), carried, URL)).toContain('TemplatedFilePath = true');
    expect(standardToRuby(baseMatcher(), carried, URL)).toContain('templated_file_path: true');
    expect(standardToRust(baseMatcher(), carried, URL)).toContain('"templatedFilePath": true');
  });
});
