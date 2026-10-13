/**
 * The composer's "Mock Registered" banner shows the x-mockserver-lint-warnings
 * count from PUT /mockserver/expectation as a badge that opens the Lint dialog.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import ComposerView from '../components/ComposerView';
import { useDashboardStore } from '../store';

vi.mock('../lib/mcpClient', () => ({
  buildBaseUrl: () => 'http://127.0.0.1:1080',
  callMcpTool: vi.fn().mockResolvedValue({ ok: true, result: { tools: [], count: 0 } }),
}));

vi.mock('../lib/conversationCodegen', () => ({
  listConversationScenarios: () => [],
}));

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

function renderComposer() {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <ComposerView connectionParams={params} />
    </ThemeProvider>,
  );
}

async function register(path: string) {
  const user = userEvent.setup({ delay: null });
  renderComposer();
  await user.type(screen.getByLabelText('Path'), path);
  await user.click(screen.getByRole('button', { name: /Register mock/ }));
  return { user, banner: await screen.findByTestId('register-success') };
}

beforeEach(() => {
  useDashboardStore.setState({ activeExpectations: [], pendingEditExpectation: null, view: 'composer' });
  try { globalThis.sessionStorage?.clear(); } catch { /* noop */ }
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('Composer lint warnings badge', () => {
  it('shows the lint warning count and opens the Lint dialog', async () => {
    const fetchMock = vi.fn(async (url: string) =>
      String(url).includes('/mockserver/lint')
        ? new Response(JSON.stringify(lintReport), { status: 200 })
        : new Response('[]', { status: 201, headers: { 'x-mockserver-lint-warnings': '1' } }));
    vi.stubGlobal('fetch', fetchMock);

    const { user, banner } = await register('users');

    const badge = within(banner).getByRole('button', { name: '1 Lint Warning' });
    expect(badge).toHaveAttribute('data-testid', 'lint-warnings-badge');
    await user.click(badge);

    const dialog = await screen.findByRole('dialog', { name: 'Lint Expectations' });
    expect(await within(dialog).findByText('PATH_MISSING_LEADING_SLASH')).toBeInTheDocument();
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/mockserver/lint'))).toBe(true));
  });

  it('shows no badge when the server reports no lint warnings', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('[]', { status: 201 })));
    const { banner } = await register('/fine');
    expect(within(banner).queryByTestId('lint-warnings-badge')).not.toBeInTheDocument();
  });
});
