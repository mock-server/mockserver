import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import LintDialog from '../components/LintDialog';
import { useDashboardStore } from '../store';

const params = { host: '127.0.0.1', port: '1080', secure: false };

const shadower = { id: 'catch-all', priority: 0, httpRequest: { path: '/orders.*' }, httpResponse: { statusCode: 200 } };

const report = {
  source: 'stored',
  expectationsChecked: 3,
  inactiveExpectationsSkipped: 0,
  issueCount: 2,
  errorCount: 0,
  warningCount: 2,
  infoCount: 0,
  issuesTruncated: false,
  shadowCheckTruncated: false,
  issues: [
    {
      rule: 'SHADOWED',
      severity: 'WARNING',
      expectationId: 'specific',
      position: 2,
      summary: 'GET /orders/1',
      message: 'expectation #1 is evaluated earlier and always matches first, so this one is never served',
      relatedExpectationId: 'catch-all',
      relatedPosition: 1,
      relatedSummary: 'any method /orders.*',
    },
    {
      rule: 'PATH_MISSING_LEADING_SLASH',
      severity: 'WARNING',
      expectationId: 'no-slash',
      position: 3,
      summary: 'GET users',
      field: 'path',
      message: 'a request path always starts with /',
    },
  ],
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function renderDialog() {
  const onClose = vi.fn();
  render(
    <ThemeProvider theme={buildTheme('dark')}>
      <LintDialog open onClose={onClose} connectionParams={params} />
    </ThemeProvider>,
  );
  return { onClose };
}

beforeEach(() => {
  useDashboardStore.setState({ pendingEditExpectation: null, view: 'dashboard' });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('LintDialog', () => {
  it('lints the active expectations and lists each finding with rule, severity and message', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(report));
    vi.stubGlobal('fetch', fetchMock);
    renderDialog();

    const issues = await screen.findAllByTestId('lint-issue');
    expect(fetchMock.mock.calls[0]![0]).toBe('http://127.0.0.1:1080/mockserver/lint');
    expect(fetchMock.mock.calls[0]![1]).toMatchObject({ method: 'PUT' });
    expect(issues).toHaveLength(2);

    const shadowed = issues[0]!;
    expect(within(shadowed).getByText('SHADOWED')).toBeInTheDocument();
    expect(within(shadowed).getByText('Warning')).toBeInTheDocument();
    expect(within(shadowed).getByText(/always matches first/)).toBeInTheDocument();
    const related = within(shadowed).getByTestId('lint-related');
    expect(related).toHaveTextContent('Shadowed by #1');
    expect(within(related).getByRole('button', { name: 'Open expectation catch-all' })).toBeInTheDocument();

    expect(within(issues[1]!).getByText('PATH_MISSING_LEADING_SLASH')).toBeInTheDocument();
    expect(within(issues[1]!).queryByTestId('lint-related')).not.toBeInTheDocument();

    const summary = screen.getByTestId('lint-summary');
    expect(within(summary).getByText('3 Checked')).toBeInTheDocument();
    expect(within(summary).getByText('2 Warnings')).toBeInTheDocument();
  });

  it('the shadowing expectation link opens that whole expectation in the composer', async () => {
    const fetchMock = vi.fn(async (url: string) =>
      String(url).includes('/mockserver/lint') ? json(report) : json([shadower]));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const { onClose } = renderDialog();

    const related = (await screen.findAllByTestId('lint-related'))[0]!;
    await user.click(within(related).getByRole('button', { name: 'Open expectation catch-all' }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    const retrieve = fetchMock.mock.calls.find(([url]) => String(url).includes('/mockserver/retrieve'));
    expect(retrieve).toBeDefined();
    expect(JSON.parse(String((retrieve as unknown as [string, RequestInit])[1].body))).toEqual({ id: 'catch-all' });
    expect(useDashboardStore.getState().pendingEditExpectation).toEqual(shadower);
    expect(useDashboardStore.getState().view).toBe('composer');
  });

  it('keeps the dialog open and says why when the expectation is gone', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) =>
      String(url).includes('/mockserver/lint') ? json(report) : json([])));
    const user = userEvent.setup();
    const { onClose } = renderDialog();

    await user.click((await screen.findAllByRole('button', { name: 'Open expectation catch-all' }))[0]!);

    expect(await screen.findByText(/no longer registered/)).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
    expect(useDashboardStore.getState().view).toBe('dashboard');
  });

  it('says so when there is nothing to report', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ ...report, issueCount: 0, warningCount: 0, issues: [] })));
    renderDialog();
    expect(await screen.findByTestId('lint-clean')).toHaveTextContent('No problems found.');
  });

  it('warns when the shadowing check or the issue list was cut short', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ ...report, shadowCheckTruncated: true, issuesTruncated: true })));
    renderDialog();
    expect(await screen.findByText(/stopped at its comparison budget/)).toBeInTheDocument();
    expect(screen.getByText('Only the first 2 findings are listed.')).toBeInTheDocument();
  });

  it('shows a humanised error when the server cannot lint', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 404 })));
    renderDialog();
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByTestId('lint-issue')).not.toBeInTheDocument();
  });

  it('Refresh lints again', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => json(report));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();
    await screen.findAllByTestId('lint-issue');
    await user.click(screen.getByRole('button', { name: 'Refresh' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(await screen.findAllByTestId('lint-issue')).toHaveLength(2);
  });
});
