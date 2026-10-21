/**
 * Template `parameters` in the composer and its code tabs: the JSON object the composer saves on
 * httpResponseTemplate / httpForwardTemplate, what each language tab writes for it, and that a loaded
 * expectation's parameters survive an edit.
 */
import { describe, it, expect } from 'vitest';
import {
  buildExpectationJson,
  javaJsonValue,
  parseTemplateParameters,
  standardToJava,
  templateParametersError,
  templateParametersText,
  type StandardMatcher,
  type StandardActionPayload,
} from '../lib/standardCodegen';
import { standardToNode } from '../lib/codegen/node';
import { standardToPython } from '../lib/codegen/python';
import { standardToGo } from '../lib/codegen/go';
import { standardToCsharp } from '../lib/codegen/csharp';
import { standardToRuby } from '../lib/codegen/ruby';
import { standardToRust } from '../lib/codegen/rust';

const MATCHER: StandardMatcher = {
  id: '',
  method: 'GET',
  path: '/greet',
  headers: '',
  queryString: '',
  cookies: '',
  pathParams: '',
  body: '',
  bodyBinary: false,
  bodyMatcherType: 'string',
  priority: 0,
  times: 0,
};

const PARAMETERS = '{ "greeting": "hello", "names": ["ann", null], "limits": { "max": 5 } }';

const responseTemplate = (parameters?: string): StandardActionPayload => ({
  type: 'template',
  template: { templateType: 'MUSTACHE', template: '{{ parameters.greeting }}', templateFile: '', parameters },
});

describe('template parameters JSON', () => {
  it('saves the parameters object on a response and a forward template', () => {
    const response = buildExpectationJson(MATCHER, responseTemplate(PARAMETERS))['httpResponseTemplate'] as Record<string, unknown>;
    expect(response['parameters']).toEqual({ greeting: 'hello', names: ['ann', null], limits: { max: 5 } });
    const forward = buildExpectationJson(MATCHER, {
      type: 'forward_template',
      forwardTemplate: { templateType: 'VELOCITY', template: 'x', templateFile: '', parameters: '{"target":"up"}' },
    })['httpForwardTemplate'] as Record<string, unknown>;
    expect(forward['parameters']).toEqual({ target: 'up' });
  });

  it('leaves parameters out when blank, empty, or not a JSON object', () => {
    for (const text of [undefined, '', '  ', '{}', '[1]', '"x"', '{ broken']) {
      const t = buildExpectationJson(MATCHER, responseTemplate(text))['httpResponseTemplate'] as Record<string, unknown>;
      expect(t, String(text)).not.toHaveProperty('parameters');
    }
    expect(parseTemplateParameters('{"a":1}')).toEqual({ a: 1 });
  });

  it('explains parameters text that cannot be saved', () => {
    expect(templateParametersError('')).toBeNull();
    expect(templateParametersError('{"a":1}')).toBeNull();
    expect(templateParametersError('[1]')).toMatch(/must be a JSON object/);
    expect(templateParametersError('{ broken')).toMatch(/must be valid JSON/);
  });

  it('shows a loaded template\'s parameters as JSON text', () => {
    expect(templateParametersText({ a: 1 })).toBe('{\n  "a": 1\n}');
    expect(templateParametersText(undefined)).toBe('');
    expect(templateParametersText({})).toBe('');
    expect(templateParametersText([1])).toBe('');
  });

  it('keeps a loaded template\'s parameters when the form does not show them', () => {
    const original = {
      httpRequest: { method: 'GET', path: '/greet' },
      httpResponseTemplate: { templateType: 'MUSTACHE', template: '{{ parameters.greeting }}', parameters: { greeting: 'hi' } },
    };
    const formWithoutParameters = responseTemplate(undefined);
    const baseline = buildExpectationJson(MATCHER, formWithoutParameters);
    const saved = buildExpectationJson(MATCHER, {
      ...formWithoutParameters,
      editOriginal: original,
      editActionModeled: true,
      editActionBaseline: { httpResponseTemplate: baseline['httpResponseTemplate'] },
    });
    expect((saved['httpResponseTemplate'] as Record<string, unknown>)['parameters']).toEqual({ greeting: 'hi' });
  });
});

describe('template parameters in the code tabs', () => {
  const action = responseTemplate(PARAMETERS);

  it('Java sets each parameter, writing nulls so the code runs', () => {
    const java = standardToJava(MATCHER, action);
    expect(java).toContain('.withParameter("greeting", "hello")');
    expect(java).toContain('.withParameter("names", java.util.Arrays.<Object>asList("ann", (Object) null))');
    expect(java).toContain('.withParameter("limits", java.util.Map.of("max", 5))');
    expect(java).not.toContain('NOTE');
    expect(javaJsonValue([null])).toBe('java.util.Arrays.<Object>asList((Object) null)');
    expect(javaJsonValue({ a: null })).toBe('new java.util.LinkedHashMap<String, Object>() {{ put("a", null); }}');
    expect(javaJsonValue(3000000000)).toBe('3000000000L');
  });

  it('every client tab carries the parameters', () => {
    expect(standardToNode(MATCHER, action, 'http://localhost:1080')).toContain('"greeting": "hello"');
    expect(standardToPython(MATCHER, action, 'http://localhost:1080')).toMatch(/parameters=\{\s+"greeting": "hello"/);
    expect(standardToRuby(MATCHER, action, 'http://localhost:1080')).toMatch(/parameters: \{\s+"greeting" => "hello"/);
    expect(standardToGo(MATCHER, action, 'http://localhost:1080')).toMatch(/Parameters: map\[string\]interface\{\}\{\s+"greeting": "hello"/);
    const csharp = standardToCsharp(MATCHER, action, 'http://localhost:1080');
    expect(csharp).toContain('.WithParameter("greeting", "hello")');
    expect(csharp).toContain('.WithParameter("limits", JsonSerializer.Deserialize<JsonElement>(');
    const rust = standardToRust(MATCHER, action, 'http://localhost:1080');
    expect(rust).toContain('.respond_template_with_parameters(');
    expect(rust).toContain('"greeting": "hello"');
    expect(rust).not.toContain('NOTE');
  });
});
