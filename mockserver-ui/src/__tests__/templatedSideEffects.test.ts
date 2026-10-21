import { describe, it, expect } from 'vitest';
import {
  buildExpectationJson,
  sideEffectsFromExpectation,
  standardToJava,
  type StandardMatcher,
  type StandardActionPayload,
  type StandardSideEffectAction,
} from '../lib/standardCodegen';
import { standardToNode } from '../lib/codegen/node';
import { standardToPython } from '../lib/codegen/python';
import { standardToGo } from '../lib/codegen/go';
import { standardToCsharp } from '../lib/codegen/csharp';
import { standardToRuby } from '../lib/codegen/ruby';
import { standardToRust } from '../lib/codegen/rust';

const TEMPLATE = '{ "method": "POST", "path": "/notify/$!esc.url($request.headers[\'x-id\'][0])" }';

function matcher(): StandardMatcher {
  return {
    id: '',
    method: 'POST',
    path: '/order',
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
}

function templated(overrides?: Partial<StandardSideEffectAction>): StandardSideEffectAction {
  return {
    position: 'after',
    templateType: 'VELOCITY',
    template: TEMPLATE,
    method: '',
    path: '',
    host: '',
    body: '',
    delayValue: 5,
    delayUnit: 'MILLISECONDS',
    blocking: true,
    timeoutValue: 0,
    timeoutUnit: 'SECONDS',
    failurePolicy: 'BEST_EFFORT',
    ...overrides,
  };
}

function action(sideEffects: StandardSideEffectAction[]): StandardActionPayload {
  return { type: 'static', static: { statusCode: 201, body: 'ok', contentType: 'text/plain' }, sideEffects };
}

describe('templated side-effect webhooks', () => {
  it('emits httpRequestTemplate in place of httpRequest', () => {
    const json = buildExpectationJson(matcher(), action([templated()]));
    expect(json['afterActions']).toEqual([
      { httpRequestTemplate: { templateType: 'VELOCITY', template: TEMPLATE }, delay: { timeUnit: 'MILLISECONDS', value: 5 } },
    ]);
  });

  it('round-trips a templated before-action back into the composer', () => {
    const json = buildExpectationJson(matcher(), action([templated({ position: 'before', templateType: 'MUSTACHE', failurePolicy: 'FAIL_FAST' })]));
    const parsed = sideEffectsFromExpectation(json);
    expect(parsed).toHaveLength(1);
    expect(parsed![0]).toMatchObject({ position: 'before', templateType: 'MUSTACHE', template: TEMPLATE, failurePolicy: 'FAIL_FAST' });
    expect(buildExpectationJson(matcher(), action(parsed!))).toEqual(json);
  });

  it('leaves plain webhooks unchanged and skips a templated row with no template', () => {
    const plain = templated({ templateType: '', template: undefined, path: '/audit', delayValue: 0 });
    const json = buildExpectationJson(matcher(), action([plain, templated({ template: '  ' })]));
    expect(json['afterActions']).toEqual([{ httpRequest: { path: '/audit' } }]);
  });

  it('keeps a file-backed template and other unedited fields when an expectation is loaded and saved', () => {
    const loaded = {
      httpRequest: { path: '/order' },
      httpResponse: { statusCode: 201 },
      beforeActions: [
        {
          httpRequestTemplate: { templateType: 'MUSTACHE', templateFile: 'hooks/before.mustache', delay: { timeUnit: 'SECONDS', value: 1 } },
          failurePolicy: 'FAIL_FAST',
        },
      ],
      afterActions: [
        {
          httpRequest: { method: 'POST', path: '/audit', queryStringParameters: { source: ['mock'] }, body: { type: 'JSON', json: '{"a":1}' } },
          delay: { timeUnit: 'MILLISECONDS', value: 5 },
        },
      ],
    };
    const parsed = sideEffectsFromExpectation(loaded)!;
    expect(parsed).toHaveLength(2);
    expect(parsed[0]).toMatchObject({ templateType: 'MUSTACHE', template: '' });

    const saved = buildExpectationJson(matcher(), action(parsed));
    expect(saved['beforeActions']).toEqual(loaded.beforeActions);
    expect(saved['afterActions']).toEqual(loaded.afterActions);
    expect(standardToJava(matcher(), action(parsed))).toContain('.withTemplateFile("hooks/before.mustache")');
  });

  it('renders the template through every client model', () => {
    const a = action([templated({ position: 'before', failurePolicy: 'FAIL_FAST' })]);
    const base = 'http://localhost:1080';
    expect(standardToJava(matcher(), a)).toContain('.withHttpRequestTemplate(template(TemplateType.VELOCITY, ');
    expect(standardToJava(matcher(), a)).toContain('import static org.mockserver.model.HttpTemplate.template;');
    expect(standardToNode(matcher(), a, base)).toContain('"httpRequestTemplate"');
    expect(standardToPython(matcher(), a, base)).toContain('http_request_template=');
    expect(standardToRuby(matcher(), a, base)).toContain('http_request_template:');
    expect(standardToGo(matcher(), a, base)).toContain('HttpRequestTemplate:');
    expect(standardToCsharp(matcher(), a, base)).toContain('HttpRequestTemplate =');
    const rust = standardToRust(matcher(), a, base);
    expect(rust).toContain('..ExpectationAction::request_template(HttpTemplate::new("VELOCITY", ');
    expect(rust).toContain('failure_policy: Some("FAIL_FAST".to_string()),');
  });
});
