import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OpenApiImportDialog from '../components/OpenApiImportDialog';

const connectionParams = { host: '127.0.0.1', port: '1080', secure: false };

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

const specWithNamedExamples = JSON.stringify({
  openapi: '3.0.0',
  info: { title: 'pets', version: '1.0.0' },
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
    },
  },
});

const loadProblems = [
  {
    specIndex: 0,
    kind: 'REF',
    kindLabel: 'unresolved or mistyped $ref',
    location: '#/paths/~1pets/get/responses/200/content/application~1json/schema/$ref',
    description: '$ref target #/components/schemas/Missing does not exist',
    hint: 'check that the $ref target exists',
  },
  {
    specIndex: 0,
    kind: 'MISSING',
    kindLabel: 'missing required field',
    location: '#/info',
    description: "required field 'version' is missing",
  },
];

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('OpenApiImportDialog named-example picker', () => {
  it('shows no picker until a JSON spec with named examples is pasted', async () => {
    const user = userEvent.setup();
    render(<OpenApiImportDialog open onClose={() => {}} connectionParams={connectionParams} />);

    expect(screen.queryByText('Named Examples')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('OpenAPI spec or URL'));
    await user.paste(specWithNamedExamples);

    await waitFor(() => expect(screen.getByText('Named Examples')).toBeInTheDocument());
    expect(screen.getByRole('combobox', { name: /listPets \(200\)/ })).toBeInTheDocument();
  });

  it('includes the chosen exampleName in the import request', async () => {
    const user = userEvent.setup();
    const calls = stubFetch(201, [{ id: 'openapi:pets:listPets' }]);
    render(<OpenApiImportDialog open onClose={() => {}} connectionParams={connectionParams} />);

    await user.click(screen.getByLabelText('OpenAPI spec or URL'));
    await user.paste(specWithNamedExamples);

    await waitFor(() => expect(screen.getByText('Named Examples')).toBeInTheDocument());
    await user.click(screen.getByRole('combobox', { name: /listPets \(200\)/ }));
    await user.click(screen.getByRole('option', { name: 'twoDogs' }));

    await user.click(screen.getByRole('button', { name: 'Import' }));

    await waitFor(() => expect(calls.length).toBe(1));
    const sent = JSON.parse(String(calls[0]?.init?.body)) as Array<Record<string, unknown>>;
    expect(sent[0]?.operationsAndResponses).toEqual({
      listPets: { statusCode: '200', exampleName: 'twoDogs' },
    });
  });

  it('still imports every operation when an example is pinned for one of several', async () => {
    const multiOpSpec = JSON.stringify({
      openapi: '3.0.0',
      info: { title: 'pets', version: '1.0.0' },
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
          post: { operationId: 'createPet', responses: { '201': {} } },
        },
        '/pets/{id}': {
          get: { operationId: 'getPet', responses: { '200': {} } },
        },
      },
    });
    const user = userEvent.setup();
    const calls = stubFetch(201, []);
    render(<OpenApiImportDialog open onClose={() => {}} connectionParams={connectionParams} />);

    await user.click(screen.getByLabelText('OpenAPI spec or URL'));
    await user.paste(multiOpSpec);

    await waitFor(() => expect(screen.getByText('Named Examples')).toBeInTheDocument());
    await user.click(screen.getByRole('combobox', { name: /listPets \(200\)/ }));
    await user.click(screen.getByRole('option', { name: 'twoDogs' }));

    await user.click(screen.getByRole('button', { name: 'Import' }));

    await waitFor(() => expect(calls.length).toBe(1));
    const sent = JSON.parse(String(calls[0]?.init?.body)) as Array<Record<string, unknown>>;
    const opsAndResponses = sent[0]?.operationsAndResponses as Record<string, unknown>;
    // the picked operation AND the others must all be present, or the server
    // (which treats this map as an operation filter) drops the unpinned ones
    expect(Object.keys(opsAndResponses).sort()).toEqual(
      ['createPet', 'getPet', 'listPets'].sort(),
    );
    expect(opsAndResponses.listPets).toEqual({ statusCode: '200', exampleName: 'twoDogs' });
    expect(opsAndResponses.createPet).toBe('');
    expect(opsAndResponses.getPet).toBe('');
  });

  it('omits operationsAndResponses when the picker is left on Default', async () => {
    const user = userEvent.setup();
    const calls = stubFetch(201, [{ id: 'openapi:pets:listPets' }]);
    render(<OpenApiImportDialog open onClose={() => {}} connectionParams={connectionParams} />);

    await user.click(screen.getByLabelText('OpenAPI spec or URL'));
    await user.paste(specWithNamedExamples);

    await waitFor(() => expect(screen.getByText('Named Examples')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Import' }));

    await waitFor(() => expect(calls.length).toBe(1));
    const sent = JSON.parse(String(calls[0]?.init?.body)) as Array<Record<string, unknown>>;
    expect(sent[0]).not.toHaveProperty('operationsAndResponses');
    expect(sent[0]?.specUrlOrPayload).toBe(specWithNamedExamples);
  });
});

describe('OpenApiImportDialog load problems', () => {
  it('lists the problems of a spec that loaded, grouped by kind with location and hint', async () => {
    const user = userEvent.setup();
    stubFetch(201, { expectations: [{ id: 'openapi:pets:listPets' }], loadProblems });
    render(<OpenApiImportDialog open onClose={() => {}} connectionParams={connectionParams} />);

    await user.click(screen.getByLabelText('OpenAPI spec or URL'));
    await user.paste('openapi: 3.0.0');
    await user.click(screen.getByRole('button', { name: 'Import' }));

    const alert = await screen.findByTestId('openapi-load-problems');
    expect(screen.getByText('Imported 1 expectation.')).toBeInTheDocument();
    expect(within(alert).getByText('Spec Loaded With 2 Problems')).toBeInTheDocument();
    expect(within(alert).getByText('unresolved or mistyped $ref (1)')).toBeInTheDocument();
    expect(within(alert).getByText('missing required field (1)')).toBeInTheDocument();
    expect(alert).toHaveTextContent(
      '#/paths/~1pets/get/responses/200/content/application~1json/schema/$ref: $ref target #/components/schemas/Missing does not exist',
    );
    expect(alert).toHaveTextContent("#/info: required field 'version' is missing");
    expect(within(alert).getByText('Hint: check that the $ref target exists')).toBeInTheDocument();
  });

  it('shows no problems for a spec without any', async () => {
    const user = userEvent.setup();
    stubFetch(201, { expectations: [{ id: 'openapi:pets:listPets' }], loadProblems: [] });
    render(<OpenApiImportDialog open onClose={() => {}} connectionParams={connectionParams} />);

    await user.click(screen.getByLabelText('OpenAPI spec or URL'));
    await user.paste('openapi: 3.0.0');
    await user.click(screen.getByRole('button', { name: 'Import' }));

    await screen.findByText('Imported 1 expectation.');
    expect(screen.queryByTestId('openapi-load-problems')).not.toBeInTheDocument();
  });

  it('says how many problems are not shown when there are more than fit', async () => {
    const user = userEvent.setup();
    const many = Array.from({ length: 53 }, (_, index) => ({
      kind: 'OTHER',
      kindLabel: 'other problem',
      location: `#/paths/~1p${index}`,
      description: `problem ${index}`,
    }));
    stubFetch(201, { expectations: [], loadProblems: many });
    render(<OpenApiImportDialog open onClose={() => {}} connectionParams={connectionParams} />);

    await user.click(screen.getByLabelText('OpenAPI spec or URL'));
    await user.paste('openapi: 3.0.0');
    await user.click(screen.getByRole('button', { name: 'Import' }));

    const alert = await screen.findByTestId('openapi-load-problems');
    expect(within(alert).getByText('Spec Loaded With 53 Problems')).toBeInTheDocument();
    expect(within(alert).getAllByRole('listitem')).toHaveLength(50);
    expect(within(alert).getByText('3 more not shown; PUT /mockserver/openapi?includeLoadProblems=true returns every problem.')).toBeInTheDocument();
  });
});
