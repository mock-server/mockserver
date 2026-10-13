import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  importOpenApi,
  discoverNamedExamples,
  discoverAllOperationIds,
} from '../lib/openapiImport';

const params = { host: '127.0.0.1', port: '1080', secure: false };

interface FetchCall {
  url: string;
  init?: RequestInit;
}

function stubFetch(status: number, body: unknown): FetchCall[] {
  const calls: FetchCall[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return {
        ok: status >= 200 && status < 300,
        status,
        statusText: 'stub',
        json: async () => body,
        text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
      };
    }),
  );
  return calls;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('importOpenApi', () => {
  it('PUTs the spec wrapped as an OpenAPIExpectation and returns created expectations and load problems', async () => {
    const created = [{ id: 'openapi:foo:listPets' }, { id: 'openapi:foo:createPets' }];
    const loadProblems = [
      {
        specIndex: 0,
        kind: 'REF',
        kindLabel: 'unresolved or mistyped $ref',
        location: '#/paths/~1pets/get/responses/200/content/application~1json/schema/$ref',
        description: '$ref target #/components/schemas/Missing does not exist',
        hint: 'check that the $ref target exists',
      },
    ];
    const calls = stubFetch(201, { expectations: created, loadProblems });
    const result = await importOpenApi(params, '{"openapi":"3.0.0"}');
    expect(result).toEqual({ expectations: created, loadProblems });
    expect(calls[0]?.url).toBe('http://127.0.0.1:1080/mockserver/openapi?includeLoadProblems=true');
    expect(calls[0]?.init?.method).toBe('PUT');
    expect(String(calls[0]?.init?.body)).toContain('specUrlOrPayload');
  });

  it('reads a bare array of expectations from a server without load problems', async () => {
    const created = [{ id: 'openapi:foo:listPets' }];
    stubFetch(201, created);
    expect(await importOpenApi(params, '{"openapi":"3.0.0"}')).toEqual({ expectations: created, loadProblems: [] });
  });

  it('drops malformed load problems', async () => {
    stubFetch(201, { expectations: [], loadProblems: [{ kind: 'REF' }, 'text', { kindLabel: 'other problem', description: 'kept' }] });
    expect((await importOpenApi(params, '{"openapi":"3.0.0"}')).loadProblems).toEqual([
      { kindLabel: 'other problem', description: 'kept' },
    ]);
  });

  it('returns nothing when the response is neither an array nor an import result', async () => {
    stubFetch(201, 'unexpected');
    expect(await importOpenApi(params, 'https://example.com/spec.yaml')).toEqual({ expectations: [], loadProblems: [] });
  });

  it('throws the server message on an invalid spec', async () => {
    stubFetch(400, 'unable to load API spec');
    await expect(importOpenApi(params, 'not a spec')).rejects.toThrow('unable to load API spec');
  });

  it('sends operationsAndResponses with statusCode and exampleName when selections are given', async () => {
    const calls = stubFetch(201, []);
    await importOpenApi(params, '{"openapi":"3.0.0"}', {
      listPets: { statusCode: '200', exampleName: 'two' },
    });
    const sent = JSON.parse(String(calls[0]?.init?.body)) as Array<Record<string, unknown>>;
    expect(sent[0]?.operationsAndResponses).toEqual({
      listPets: { statusCode: '200', exampleName: 'two' },
    });
  });

  it('omits operationsAndResponses when no selections are given', async () => {
    const calls = stubFetch(201, []);
    await importOpenApi(params, '{"openapi":"3.0.0"}', {});
    const sent = JSON.parse(String(calls[0]?.init?.body)) as Array<Record<string, unknown>>;
    expect(sent[0]).not.toHaveProperty('operationsAndResponses');
  });

  // Regression: the server treats operationsAndResponses as an operation FILTER
  // (OpenAPIConverter only generates an expectation for operations present as a
  // key). Pinning a named example for ONE operation must NOT drop the others.
  it('includes every operation (pinned + default-preserving) when one example is pinned', async () => {
    const multiOpSpec = JSON.stringify({
      openapi: '3.0.0',
      paths: {
        '/pets': {
          get: {
            operationId: 'listPets',
            responses: {
              '200': {
                content: {
                  'application/json': {
                    examples: { oneCat: { value: [] }, twoDogs: { value: [] } },
                  },
                },
              },
            },
          },
          post: { operationId: 'createPet', responses: { '201': {} } },
        },
        '/pets/{id}': {
          get: { operationId: 'getPet', responses: { '200': {} } },
          delete: { operationId: 'deletePet', responses: { '204': {} } },
        },
      },
    });
    const calls = stubFetch(201, []);
    await importOpenApi(params, multiOpSpec, {
      listPets: { statusCode: '200', exampleName: 'twoDogs' },
    });
    const sent = JSON.parse(String(calls[0]?.init?.body)) as Array<Record<string, unknown>>;
    const opsAndResponses = sent[0]?.operationsAndResponses as Record<string, unknown>;
    // every operation in the spec must be present, or the server drops it
    expect(Object.keys(opsAndResponses).sort()).toEqual(
      ['createPet', 'deletePet', 'getPet', 'listPets'].sort(),
    );
    // the pinned operation carries its chosen example
    expect(opsAndResponses.listPets).toEqual({ statusCode: '200', exampleName: 'twoDogs' });
    // the others carry a default-preserving (blank) value, so the server applies
    // its own first-response/default-example behaviour for them
    expect(opsAndResponses.createPet).toBe('');
    expect(opsAndResponses.getPet).toBe('');
    expect(opsAndResponses.deletePet).toBe('');
  });

  // Data-loss regression: the server SYNTHESIZES an operationId of the form
  // "<METHOD> <path>" (OpenAPIParser.synthesizeOperationIds) for any operation
  // that declares no operationId, then OpenAPIConverter filters generation by
  // that synthesized key. So a spec mixing a pinned, id-bearing operation with
  // an id-LESS operation must still send the synthesized key (with a blank,
  // default-preserving value) — otherwise the id-less operation is dropped.
  it('keeps an id-less operation (via its synthesized key) when another operation pins an example', async () => {
    const mixedSpec = JSON.stringify({
      openapi: '3.0.0',
      paths: {
        '/pets': {
          get: {
            operationId: 'listPets',
            responses: {
              '200': {
                content: {
                  'application/json': {
                    examples: { oneCat: { value: [] }, twoDogs: { value: [] } },
                  },
                },
              },
            },
          },
        },
        // NOTE: no operationId — the server will synthesize "POST /pets/import"
        '/pets/import': {
          post: { responses: { '202': {} } },
        },
      },
    });
    const calls = stubFetch(201, []);
    await importOpenApi(params, mixedSpec, {
      listPets: { statusCode: '200', exampleName: 'twoDogs' },
    });
    const sent = JSON.parse(String(calls[0]?.init?.body)) as Array<Record<string, unknown>>;
    const opsAndResponses = sent[0]?.operationsAndResponses as Record<string, unknown>;
    // the pinned, id-bearing operation
    expect(opsAndResponses.listPets).toEqual({ statusCode: '200', exampleName: 'twoDogs' });
    // the id-less operation must survive under the server's synthesized key,
    // with a default-preserving blank value — not silently dropped
    expect(opsAndResponses).toHaveProperty('POST /pets/import', '');
    expect(Object.keys(opsAndResponses).sort()).toEqual(['POST /pets/import', 'listPets'].sort());
  });
});

describe('discoverAllOperationIds', () => {
  it('returns every operationId declared by an inline JSON spec, in document order', () => {
    expect(discoverAllOperationIds(specWithNamedExamples)).toEqual(['listPets', 'createPet']);
  });

  it('returns an empty list for a URL or YAML spec', () => {
    expect(discoverAllOperationIds('https://example.com/spec.yaml')).toEqual([]);
    expect(discoverAllOperationIds('openapi: 3.0.0\npaths: {}')).toEqual([]);
  });

  it('returns an empty list for unparseable JSON or a spec without paths', () => {
    expect(discoverAllOperationIds('{ not valid json')).toEqual([]);
    expect(discoverAllOperationIds('{"openapi":"3.0.0"}')).toEqual([]);
  });

  // The server synthesizes "<UPPERCASE_METHOD> <path>" for an id-less operation
  // (OpenAPIParser.synthesizeOperationIds). We must emit the same key so it is a
  // key of operationsAndResponses (and therefore not filtered out by the server).
  it('synthesizes "<METHOD> <path>" for operations without an explicit operationId', () => {
    const spec = JSON.stringify({
      openapi: '3.0.0',
      paths: {
        '/pets': {
          get: { operationId: 'listPets', responses: { '200': {} } },
          post: { responses: { '201': {} } },
        },
        '/pets/{id}': {
          delete: { responses: { '204': {} } },
        },
      },
    });
    expect(discoverAllOperationIds(spec)).toEqual([
      'listPets',
      'POST /pets',
      'DELETE /pets/{id}',
    ]);
  });

  it('treats a blank operationId the same as a missing one (synthesizes a key)', () => {
    const spec = JSON.stringify({
      openapi: '3.0.0',
      paths: { '/ping': { get: { operationId: '   ', responses: { '200': {} } } } },
    });
    expect(discoverAllOperationIds(spec)).toEqual(['GET /ping']);
  });
});

const specWithNamedExamples = JSON.stringify({
  openapi: '3.0.0',
  paths: {
    '/pets': {
      get: {
        operationId: 'listPets',
        responses: {
          '200': {
            content: {
              'application/json': {
                examples: {
                  oneCat: { value: [{ name: 'cat' }] },
                  twoDogs: { value: [{ name: 'dog' }, { name: 'rex' }] },
                },
              },
            },
          },
        },
      },
      post: {
        operationId: 'createPet',
        responses: {
          '201': {
            content: {
              'application/json': {
                // only one named example — nothing to choose
                examples: { created: { value: { name: 'cat' } } },
              },
            },
          },
        },
      },
    },
  },
});

describe('discoverNamedExamples', () => {
  it('returns operations that declare two or more named examples', () => {
    const result = discoverNamedExamples(specWithNamedExamples);
    expect(result).toEqual([
      { operationId: 'listPets', statusCode: '200', exampleNames: ['oneCat', 'twoDogs'] },
    ]);
  });

  it('returns an empty list for a URL spec', () => {
    expect(discoverNamedExamples('https://example.com/spec.yaml')).toEqual([]);
  });

  it('returns an empty list for YAML or unparseable input', () => {
    expect(discoverNamedExamples('openapi: 3.0.0\npaths: {}')).toEqual([]);
    expect(discoverNamedExamples('{ not valid json')).toEqual([]);
  });

  it('returns an empty list when no operation has named examples', () => {
    const spec = JSON.stringify({
      openapi: '3.0.0',
      paths: { '/ping': { get: { operationId: 'ping', responses: { '200': {} } } } },
    });
    expect(discoverNamedExamples(spec)).toEqual([]);
  });

  it('only inspects the first media type, mirroring the server content selection', () => {
    // The server uses the first content entry, so a named example on a second
    // media type cannot be honoured and must not be offered in the picker.
    const spec = JSON.stringify({
      openapi: '3.0.0',
      paths: {
        '/pets': {
          get: {
            operationId: 'listPets',
            responses: {
              '200': {
                content: {
                  'application/json': { examples: { a: { value: 1 }, b: { value: 2 } } },
                  'application/xml': { examples: { xmlOnly: { value: '<x/>' } } },
                },
              },
            },
          },
        },
      },
    });
    expect(discoverNamedExamples(spec)).toEqual([
      { operationId: 'listPets', statusCode: '200', exampleNames: ['a', 'b'] },
    ]);
  });

  it('does not resolve $ref responses (no picker for $ref-only examples)', () => {
    const spec = JSON.stringify({
      openapi: '3.0.0',
      paths: {
        '/pets': {
          get: {
            operationId: 'listPets',
            responses: { '200': { $ref: '#/components/responses/PetList' } },
          },
        },
      },
      components: {
        responses: {
          PetList: {
            content: { 'application/json': { examples: { a: { value: 1 }, b: { value: 2 } } } },
          },
        },
      },
    });
    expect(discoverNamedExamples(spec)).toEqual([]);
  });
});
