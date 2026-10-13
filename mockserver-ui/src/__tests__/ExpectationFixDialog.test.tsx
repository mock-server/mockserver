import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import ExplainUnmatchedDialog from '../components/ExplainUnmatchedDialog';
import { explainUnmatched } from '../lib/explainUnmatched';
import { fetchExpectation } from '../lib/expectations';
import { registerExpectation } from '../lib/generateStub';

vi.mock('../lib/explainUnmatched', () => ({ explainUnmatched: vi.fn() }));
vi.mock('../lib/expectations', () => ({ fetchExpectation: vi.fn() }));
vi.mock('../lib/generateStub', () => ({ registerExpectation: vi.fn() }));

const connectionParams = { host: 'localhost', port: '1080', secure: false };
const mockExplain = vi.mocked(explainUnmatched);
const mockFetchExpectation = vi.mocked(fetchExpectation);
const mockRegister = vi.mocked(registerExpectation);

const registered = {
  id: 'exp-1',
  httpRequest: { method: 'GET', path: '/api/order' },
  httpResponse: { statusCode: 200 },
};

function explanation(matchesAfterPatch = true) {
  return {
    correlationId: 'c', timestamp: 't', unmatchedRequestCount: 1, truncated: false,
    unmatchedRequests: [{
      method: 'GET', path: '/api/orders', totalExpectationsEvaluated: 1,
      closestExpectations: [{
        expectationId: 'exp-1', expectationMethod: 'GET', expectationPath: '/api/order', matches: false,
        matchedFieldCount: 17, totalFieldCount: 18, differingFieldCount: 1,
        differences: { path: ['expected /api/order but was /api/orders'] },
        suggestedFix: {
          patch: [
            { op: 'test' as const, path: '/httpRequest/path', value: '/api/order' },
            { op: 'replace' as const, path: '/httpRequest/path', value: '/api/orders' },
          ],
          patchedFields: ['path'],
          unpatchedFields: matchesAfterPatch ? [] : [{ field: 'jwt', reason: 'edit it by hand' }],
          matchesAfterPatch,
        },
      }],
    }],
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockFetchExpectation.mockResolvedValue(registered);
  mockRegister.mockResolvedValue(undefined);
});

async function openPreview() {
  render(<ExplainUnmatchedDialog open onClose={() => {}} connectionParams={connectionParams} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Preview fix for expectation exp-1' }));
  return screen.findByRole('dialog', { name: 'Preview Fix' });
}

describe('Explain Unmatched fix preview and apply', () => {
  it('asks the server for suggested fixes', async () => {
    mockExplain.mockResolvedValue(explanation());
    render(<ExplainUnmatchedDialog open onClose={() => {}} connectionParams={connectionParams} />);
    await screen.findByRole('button', { name: 'Preview fix for expectation exp-1' });
    expect(mockExplain).toHaveBeenCalledWith(connectionParams, 10, undefined, true);
  });

  it('previews the change and writes nothing until Apply Fix is selected', async () => {
    mockExplain.mockResolvedValue(explanation());

    await openPreview();

    expect(screen.getByTestId('expectation-fix-changes')).toHaveTextContent('path: "/api/order" → "/api/orders"');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Apply Fix' })).toBeEnabled());
    expect(mockFetchExpectation).toHaveBeenCalledWith(connectionParams, 'exp-1', expect.anything());
    expect(screen.getByTestId('expectation-fix-before')).toHaveTextContent('"path": "/api/order"');
    expect(screen.getByTestId('expectation-fix-after')).toHaveTextContent('"path": "/api/orders"');
    expect(mockRegister).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Apply Fix' }));

    await screen.findByText('Expectation updated.');
    expect(mockRegister).toHaveBeenCalledTimes(1);
    expect(mockRegister).toHaveBeenCalledWith(connectionParams, { ...registered, httpRequest: { method: 'GET', path: '/api/orders' } });
    await waitFor(() => expect(mockExplain).toHaveBeenCalledTimes(2));
  });

  it('does not apply when Cancel is selected', async () => {
    mockExplain.mockResolvedValue(explanation());

    await openPreview();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Apply Fix' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Preview Fix' })).not.toBeInTheDocument());
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it('says when the fix alone will not make the expectation match', async () => {
    mockExplain.mockResolvedValue(explanation(false));

    await openPreview();

    expect(screen.getByText(/This fix alone does not make the expectation match/)).toBeInTheDocument();
    expect(screen.getByText(/edit it by hand/)).toBeInTheDocument();
  });

  it('refuses to apply when the expectation changed since the explanation', async () => {
    mockExplain.mockResolvedValue(explanation());
    mockFetchExpectation.mockResolvedValue({ ...registered, httpRequest: { method: 'GET', path: '/edited' } });

    await openPreview();

    expect(await screen.findByText(/has changed since the explanation was computed/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Apply Fix' })).toBeDisabled();
    expect(mockRegister).not.toHaveBeenCalled();
  });
});
