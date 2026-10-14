/**
 * Generate Stub and Capture as Mock show the x-mockserver-lint-warnings count from
 * PUT /mockserver/expectation as a badge that opens the Lint dialog.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import GenerateStubDialog from '../components/GenerateStubDialog';
import CaptureAsMockDialog from '../components/CaptureAsMockDialog';
import { useDashboardStore } from '../store';
import type { GenericParsed } from '../lib/llmTraffic';

const params = { host: '127.0.0.1', port: '1080', secure: false };

const lintReport = {
  expectationsChecked: 1,
  inactiveExpectationsSkipped: 0,
  issueCount: 1,
  errorCount: 0,
  warningCount: 1,
  infoCount: 0,
  issuesTruncated: false,
  shadowCheckTruncated: false,
  issues: [{ rule: 'PATH_MISSING_LEADING_SLASH', severity: 'WARNING', expectationId: 'e1', position: 1, summary: 'GET users', message: 'a request path always starts with /' }],
};

/** A server whose PUT /mockserver/expectation reports `lintWarnings` (no header when undefined). */
function stubServer(lintWarnings?: string) {
  const fetchMock = vi.fn(async (url: string) =>
    String(url).endsWith('/mockserver/lint')
      ? new Response(JSON.stringify(lintReport), { status: 200 })
      : new Response('[]', { status: 201, headers: lintWarnings === undefined ? {} : { 'x-mockserver-lint-warnings': lintWarnings } }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderGenerateStub() {
  const onClose = vi.fn();
  render(
    <ThemeProvider theme={buildTheme('dark')}>
      <GenerateStubDialog
        open
        onClose={onClose}
        suggestions={[{ httpRequest: { method: 'GET', path: 'users' }, httpResponse: { statusCode: 200 } }]}
        confidence={0.9}
        connectionParams={params}
      />
    </ThemeProvider>,
  );
  return { onClose };
}

const genericParsed: GenericParsed = { kind: 'generic', method: 'GET', path: '/api/health', statusCode: 200 };

function renderCapture() {
  const onClose = vi.fn();
  render(
    <ThemeProvider theme={buildTheme('dark')}>
      <CaptureAsMockDialog
        open
        onClose={onClose}
        parsed={genericParsed}
        path="/api/health"
        connectionParams={params}
        itemValue={{ httpRequest: { method: 'GET', path: '/api/health' }, httpResponse: { statusCode: 200 } }}
      />
    </ThemeProvider>,
  );
  return { onClose };
}

beforeEach(() => {
  useDashboardStore.setState({ notification: null });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('Generate Stub lint warnings badge', () => {
  it('shows the lint warning count on the registered suggestion and opens the Lint dialog', async () => {
    const fetchMock = stubServer('1');
    const user = userEvent.setup({ delay: null });
    renderGenerateStub();

    await user.click(screen.getByRole('button', { name: 'Register Now' }));

    const banner = (await screen.findByText(/Suggestion 1 registered successfully/)).closest('[role="alert"]') as HTMLElement;
    const badge = within(banner).getByRole('button', { name: '1 Lint Warning' });
    await user.click(badge);
    const dialog = await screen.findByRole('dialog', { name: 'Lint Expectations' });
    expect(await within(dialog).findByText('PATH_MISSING_LEADING_SLASH')).toBeInTheDocument();
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/mockserver/lint'))).toBe(true));
  });

  it('shows no badge when the server reports no lint warnings', async () => {
    stubServer();
    const user = userEvent.setup({ delay: null });
    renderGenerateStub();

    await user.click(screen.getByRole('button', { name: 'Register Now' }));

    await screen.findByText(/Suggestion 1 registered successfully/);
    expect(screen.queryByTestId('lint-warnings-badge')).not.toBeInTheDocument();
  });
});

describe('Capture as Mock lint warnings badge', () => {
  it('stays open after registering to show the lint warning count, and the badge opens the Lint dialog', async () => {
    stubServer('3');
    const user = userEvent.setup({ delay: null });
    const { onClose } = renderCapture();

    await user.click(screen.getByRole('button', { name: 'Register' }));

    const banner = await screen.findByTestId('capture-registered');
    expect(onClose).not.toHaveBeenCalled();
    expect(useDashboardStore.getState().notification).toBeNull();
    expect(screen.getByRole('button', { name: 'Registered' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Refine in Composer/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();
    expect(screen.getByLabelText('Path')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('Response Body')).toHaveAttribute('readonly');
    expect(screen.getByRole('button', { name: 'Exact precision' })).toBeDisabled();
    await user.click(within(banner).getByRole('button', { name: '3 Lint Warnings' }));
    expect(await screen.findByRole('dialog', { name: 'Lint Expectations' })).toBeInTheDocument();
  });

  it('closes with the success notification when the server reports no lint warnings', async () => {
    stubServer();
    const user = userEvent.setup({ delay: null });
    const { onClose } = renderCapture();

    await user.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
    expect(useDashboardStore.getState().notification).toEqual({ message: 'Mock expectation registered successfully', severity: 'success' });
    expect(screen.queryByTestId('lint-warnings-badge')).not.toBeInTheDocument();
  });
});
