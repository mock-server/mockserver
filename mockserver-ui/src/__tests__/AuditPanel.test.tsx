import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import AuditPanel from '../components/AuditPanel';
import type { AuditEntry } from '../lib/audit';

const params = { host: '127.0.0.1', port: '1080', secure: false };

function renderPanel() {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <AuditPanel connectionParams={params} />
    </ThemeProvider>,
  );
}

function fixture(): AuditEntry[] {
  return [
    {
      epochTimeMs: 1719950400123,
      method: 'PUT',
      path: '/mockserver/expectation',
      operation: 'expectation',
      sourceAddress: '127.0.0.1',
      principal: 'admin',
      principalSource: 'BASIC',
      outcome: 'AUTHORIZED',
      summary: 'control-plane expectation',
    },
    {
      epochTimeMs: 1719950300000,
      method: 'PUT',
      path: '/mockserver/clear',
      operation: 'clear',
      sourceAddress: '10.0.0.5',
      principal: null,
      principalSource: null,
      outcome: 'FORBIDDEN',
      summary: 'control-plane clear',
    },
  ];
}

interface ServerOptions {
  enabled?: boolean;
  reads?: boolean;
  entries?: AuditEntry[];
  /** When set, a PUT /mockserver/configuration fails with this status + body. */
  putFailure?: { status: number; body: string };
}

/**
 * Stub fetch, routing by URL: GET /mockserver/configuration returns the live
 * config (reflecting prior PUTs), PUT applies the partial to that live config,
 * and /mockserver/audit returns the entries array. Captures the PUT bodies and
 * per-endpoint call counts so tests can assert what the panel sent.
 */
function stubServer(options: ServerOptions = {}) {
  const state: Record<string, unknown> = {
    controlPlaneAuditEnabled: options.enabled ?? false,
    controlPlaneAuditReads: options.reads ?? false,
  };
  const entries = options.entries ?? [];
  const puts: Record<string, unknown>[] = [];
  const auditUrls: string[] = [];

  const mock = vi.fn(async (url: string, init?: { method?: string; body?: string }) => {
    if (url.includes('/mockserver/configuration')) {
      if (init?.method === 'PUT') {
        const body = JSON.parse(String(init.body)) as Record<string, unknown>;
        puts.push(body);
        if (options.putFailure) {
          return {
            ok: false,
            status: options.putFailure.status,
            statusText: 'Error',
            text: async (): Promise<string> => options.putFailure!.body,
          };
        }
        Object.assign(state, body);
        return { ok: true, text: async (): Promise<string> => '' };
      }
      return { ok: true, json: async () => ({ ...state }) };
    }
    // audit endpoint
    auditUrls.push(url);
    return { ok: true, json: async () => entries };
  });
  vi.stubGlobal('fetch', mock);
  return { mock, puts, auditUrls };
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('AuditPanel', () => {
  it('fetches on mount and renders the audit entries', async () => {
    const { auditUrls } = stubServer({ enabled: true, entries: fixture() });
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('control-plane expectation')).toBeInTheDocument();
    });
    expect(screen.getByText('control-plane clear')).toBeInTheDocument();
    expect(screen.getByText('AUTHORIZED')).toBeInTheDocument();
    expect(screen.getByText('FORBIDDEN')).toBeInTheDocument();
    expect(screen.getByText('admin')).toBeInTheDocument();
    // Fetched from the audit endpoint exactly once on mount.
    expect(auditUrls).toHaveLength(1);
    expect(auditUrls[0]).toContain('/mockserver/audit');
  });

  it('shows the On status chip and no way to switch the audit trail off', async () => {
    stubServer({ enabled: true, entries: fixture() });
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('Audit Trail: On')).toBeInTheDocument();
    });
    // controlPlaneAuditEnabled is fixed at startup, so the panel offers no toggle for it
    expect(screen.queryByRole('switch', { name: 'Audit trail enabled' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enable Audit Trail' })).not.toBeInTheDocument();
    expect(screen.getByText(/The most recent control-plane changes since the server started appear here/i)).toBeInTheDocument();
  });

  it('shows the Off status chip and says the audit trail is set at startup when disabled', async () => {
    const { puts } = stubServer({ enabled: false, entries: [] });
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('Audit Trail: Off')).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: 'Enable Audit Trail' })).not.toBeInTheDocument();
    expect(screen.getByText(/It can only be switched on when MockServer starts/i)).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Also record reads' })).toBeDisabled();
    expect(puts).toHaveLength(0);
  });

  it('the reads checkbox PUTs controlPlaneAuditReads', async () => {
    const { puts } = stubServer({ enabled: true, reads: false, entries: [] });
    const user = userEvent.setup();
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('Audit Trail: On')).toBeInTheDocument();
    });

    await user.click(screen.getByRole('checkbox', { name: 'Also record reads' }));

    await waitFor(() => {
      expect(puts).toContainEqual({ controlPlaneAuditReads: true });
    });
  });

  it('surfaces a failed configuration update via the error alert', async () => {
    stubServer({
      enabled: true,
      reads: false,
      entries: [],
      putFailure: { status: 401, body: 'control-plane write forbidden' },
    });
    const user = userEvent.setup();
    renderPanel();

    await waitFor(() => {
      expect(screen.getByRole('checkbox', { name: 'Also record reads' })).toBeEnabled();
    });

    await user.click(screen.getByRole('checkbox', { name: 'Also record reads' }));

    await waitFor(() => {
      expect(screen.getByText('control-plane write forbidden')).toBeInTheDocument();
    });
    // The failed write must not have ticked the reads box.
    expect(screen.getByRole('checkbox', { name: 'Also record reads' })).not.toBeChecked();
  });

  it('refetches when Refresh is clicked', async () => {
    const { auditUrls } = stubServer({ enabled: true, entries: fixture() });
    const user = userEvent.setup();
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('control-plane expectation')).toBeInTheDocument();
    });
    expect(auditUrls).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: /refresh/i }));
    await waitFor(() => {
      expect(auditUrls).toHaveLength(2);
    });
  });

  it('filters entries by the search field', async () => {
    stubServer({ enabled: true, entries: fixture() });
    const user = userEvent.setup();
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('control-plane expectation')).toBeInTheDocument();
    });

    await user.type(screen.getByRole('textbox', { name: 'Search audit entries' }), 'clear');
    expect(screen.queryByText('control-plane expectation')).not.toBeInTheDocument();
    expect(screen.getByText('control-plane clear')).toBeInTheDocument();
  });

  it('explains the opt-in audit trail in the empty state when disabled with no entries', async () => {
    stubServer({ enabled: false, entries: [] });
    renderPanel();

    // The empty state must explain that the audit trail is off by default and
    // list the startup properties that switch it on.
    await waitFor(() => {
      expect(screen.getByText('No audit entries recorded.')).toBeInTheDocument();
    });
    expect(screen.getByText('controlPlaneAuditEnabled=true')).toBeInTheDocument();
    expect(screen.getByText('-Dmockserver.controlPlaneAuditEnabled=true')).toBeInTheDocument();
    expect(screen.getByText('MOCKSERVER_CONTROL_PLANE_AUDIT_ENABLED=true')).toBeInTheDocument();
  });

  it('surfaces the server error envelope when the audit list fails to load', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: async () => ({ error: 'audit store unavailable' }),
      })),
    );
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('Could not load audit trail')).toBeInTheDocument();
    });
    expect(screen.getByText('audit store unavailable')).toBeInTheDocument();
  });

  it('shows the not-available branch on a 404 (older server)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        json: async () => {
          throw new Error('no body');
        },
      })),
    );
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('Audit trail not available')).toBeInTheDocument();
    });
  });
});
