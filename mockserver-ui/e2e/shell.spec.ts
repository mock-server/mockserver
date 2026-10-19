import { test, expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { requireOrSkip } from './ci-guard';

// Real-browser e2e coverage of the dashboard SHELL against a live MockServer:
// the title bar (navigation menus, every icon control, the tools / clear menus,
// the operating-mode selector, theme and follow toggles, workspaces), the Metrics
// view against real traffic, the Get Started landing page, connection-loss
// handling, keyboard-only use, and narrow-window layout of every top-level view.
// Any console error or uncaught exception fails the test unless the test
// declares it (allowConsole) because it provokes the failure on purpose.

const HOST = process.env.E2E_MS_HOST || '127.0.0.1';
const PORT = process.env.E2E_MS_PORT || '1084';
const ORIGIN = `http://${HOST}:${PORT}`;
// The main server keeps metrics off (E2E-OBS-6 needs that); the e2e config starts
// the secondary MockServer (the library / verify upstream) with metrics on.
const METRICS_ORIGIN =
  process.env.E2E_METRICS_ORIGIN || `http://${process.env.E2E_UPSTREAM_HOST || HOST}:${process.env.E2E_UPSTREAM_PORT || '1114'}`;

// Every top-level view: its URL hash, owning nav group button, menu item label
// and the one-line description the shell shows under the nav (none for Get Started).
const VIEWS: { hash: string; group: string; item: string; description?: RegExp }[] = [
  { hash: 'get-started', group: 'Mock views', item: 'Get started view' },
  { hash: 'composer', group: 'Mock views', item: 'Mocks view', description: /^Create, edit, and manage mock expectations/ },
  { hash: 'scenarios', group: 'Mock views', item: 'Scenarios view', description: /^Manage scenario states/ },
  { hash: 'grpc', group: 'Mock views', item: 'gRPC services view', description: /^Mock gRPC services/ },
  { hash: 'async', group: 'Mock views', item: 'AsyncAPI broker mock view', description: /^Mock event-driven APIs/ },
  { hash: 'dashboard', group: 'Observe views', item: 'Dashboard view', description: /^Live view of incoming requests/ },
  { hash: 'traffic', group: 'Observe views', item: 'Traffic inspector view', description: /^Browse recorded request/ },
  { hash: 'sessions', group: 'Observe views', item: 'Trace inspector view', description: /^Trace related requests/ },
  { hash: 'metrics', group: 'Observe views', item: 'Metrics view', description: /^Prometheus metrics/ },
  { hash: 'verification', group: 'Verify views', item: 'Verification view', description: /^Assert which requests/ },
  { hash: 'contract', group: 'Verify views', item: 'Contract test view', description: /^Validate mocks and traffic/ },
  { hash: 'slo', group: 'Verify views', item: 'SLO verification view', description: /^Assert service-level objectives/ },
  { hash: 'drift', group: 'Verify views', item: 'Drift detection view', description: /^Detect when your mocks drift/ },
  { hash: 'chaos', group: 'Resilience views', item: 'Service chaos view', description: /^Inject latency, errors/ },
  { hash: 'performance', group: 'Resilience views', item: 'Performance testing view', description: /^Create, run, and monitor load/ },
  { hash: 'optimise', group: 'AI views', item: 'LLM Optimise view', description: /^Analyse captured LLM traffic/ },
  { hash: 'mcp-health', group: 'AI views', item: 'MCP server health view', description: /^See which MCP servers/ },
  { hash: 'breakpoints', group: 'Inspect views', item: 'Breakpoints view', description: /^Pause matching requests/ },
  { hash: 'audit', group: 'Inspect views', item: 'Audit trail view', description: /^Review recent control-plane changes/ },
  { hash: 'library', group: 'Inspect views', item: 'Library of captured content', description: /^Browse and reuse captured/ },
  { hash: 'cluster', group: 'Inspect views', item: 'Cluster status view', description: /^Monitor MockServer cluster/ },
];

// ---------------------------------------------------------------------------
// shared helpers + console capture
// ---------------------------------------------------------------------------

const consoleErrors = new WeakMap<Page, string[]>();
const allowedConsole = new WeakMap<Page, RegExp[]>();

function allowConsole(page: Page, ...patterns: RegExp[]): void {
  allowedConsole.set(page, [...(allowedConsole.get(page) ?? []), ...patterns]);
}

test.beforeEach(async ({ page, request }) => {
  const res = await request.put(`${ORIGIN}/mockserver/reset`);
  expect(res.ok(), `reset returned ${res.status()}`).toBeTruthy();
  const errors: string[] = [];
  consoleErrors.set(page, errors);
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(`${m.text()} @ ${m.location().url}`);
  });
  page.on('pageerror', (e) => errors.push(`uncaught: ${e.message}`));
});

// Several tests pass through the Metrics view on the main server, whose metrics
// are off. That view (only it) probes GET /mockserver/metrics and reads the 404
// as "disabled" to show its guidance; the browser logs that 404.
const METRICS_VIEW_PROBE_404 = new RegExp(`status of 404 .* @ ${ORIGIN.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}/mockserver/metrics$`);

test.afterEach(async ({ page }) => {
  const allowed = [METRICS_VIEW_PROBE_404, ...(allowedConsole.get(page) ?? [])];
  const unexpected = (consoleErrors.get(page) ?? []).filter((e) => !allowed.some((re) => re.test(e)));
  expect(unexpected, 'browser console errors / uncaught exceptions').toEqual([]);
});

async function metricsEnabled(request: APIRequestContext): Promise<boolean> {
  return (await request.get(`${METRICS_ORIGIN}/mockserver/metrics`).catch(() => null))?.status() === 200;
}

async function upsert(request: APIRequestContext, path: string, statusCode = 200, origin = ORIGIN): Promise<void> {
  const res = await request.put(`${origin}/mockserver/expectation`, {
    data: { httpRequest: { path }, httpResponse: { statusCode, body: 'shell-e2e' } },
  });
  expect(res.status(), await res.text()).toBe(201);
}

async function activeExpectationCount(request: APIRequestContext): Promise<number> {
  const res = await request.put(`${ORIGIN}/mockserver/retrieve?type=active_expectations`);
  return ((await res.json()) as unknown[]).length;
}

async function recordedRequestCount(request: APIRequestContext): Promise<number> {
  const res = await request.put(`${ORIGIN}/mockserver/retrieve?type=requests`);
  return ((await res.json()) as unknown[]).length;
}

function connectionChip(page: Page, status: string): Locator {
  return page.locator('header').getByText(status, { exact: true });
}

async function openDashboard(page: Page, hash = 'dashboard'): Promise<void> {
  await page.goto(`./#/${hash}`);
  await expect(connectionChip(page, 'connected')).toBeVisible();
}

// In-app navigation (no reload), the same path the store's hashchange listener takes.
async function goToView(page: Page, hash: string): Promise<void> {
  await page.evaluate((h) => {
    window.location.hash = `#/${h}`;
  }, hash);
}

// Presses Tab until `target` holds focus — proof it is reachable by keyboard alone.
async function tabTo(page: Page, target: Locator, maxPresses = 60): Promise<void> {
  for (let i = 0; i < maxPresses; i++) {
    if (await target.evaluate((el) => el === document.activeElement)) return;
    await page.keyboard.press('Tab');
  }
  await expect(target, `not reachable within ${maxPresses} Tab presses`).toBeFocused();
}

// The KPI hero card's big number on the Metrics view (an <h5> under its caption).
function heroValue(page: Page, label: string): Locator {
  return page.locator('.MuiCard-root').filter({ has: page.getByText(label, { exact: true }) }).locator('h5');
}

// The outlined Paper holding one Metrics chart, found by its caption.
function metricsPaper(page: Page, caption: string): Locator {
  return page.locator('.MuiPaper-outlined').filter({ has: page.getByText(caption, { exact: true }) });
}

function relativeLuminance(rgb: string): number {
  const [r, g, b] = (rgb.match(/\d+(\.\d+)?/g) ?? ['0', '0', '0']).slice(0, 3).map(Number);
  const f = (v: number) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r!) + 0.7152 * f(g!) + 0.0722 * f(b!);
}

// ---------------------------------------------------------------------------
// Metrics view
// ---------------------------------------------------------------------------

test.describe('Metrics view', () => {
  test.use({ baseURL: `${METRICS_ORIGIN}/mockserver/dashboard/` });
  test.beforeEach(async ({ request }) => {
    requireOrSkip(await metricsEnabled(request), `no MockServer with -Dmockserver.metricsEnabled=true at ${METRICS_ORIGIN}`);
    expect((await request.put(`${METRICS_ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
  });

  test('hero counters, latency and charts reflect real matched and unmatched traffic', async ({ page, request }) => {
    const stamp = Date.now();
    const hit = `/shell/metrics-hit-${stamp}`;

    await openDashboard(page, 'metrics');
    await expect(page.getByText('live', { exact: true })).toBeVisible();
    // reset zeroes the counters, and the dashboard's own control-plane calls never match or miss.
    await expect(heroValue(page, 'Matched')).toHaveText('0');
    await expect(heroValue(page, 'Not matched')).toHaveText('0');

    await upsert(request, hit, 200, METRICS_ORIGIN);
    for (let i = 0; i < 4; i++) expect((await request.get(`${METRICS_ORIGIN}${hit}`)).status()).toBe(200);
    for (let i = 0; i < 2; i++) expect((await request.get(`${METRICS_ORIGIN}/shell/metrics-miss-${stamp}-${i}`)).status()).toBe(404);

    await page.getByRole('button', { name: 'Refresh metrics' }).click();
    await expect(heroValue(page, 'Matched')).toHaveText('4');
    await expect(heroValue(page, 'Not matched')).toHaveText('2');
    await expect(heroValue(page, 'Forwarded')).toHaveText('0');
    const received = Number((await heroValue(page, 'All requests received').innerText()).replace(/,/g, ''));
    expect(received, 'requests received counts at least our six data-plane requests').toBeGreaterThanOrEqual(6);

    // Latency quantiles come from the server's duration histogram.
    for (const q of ['p50', 'p95', 'p99']) {
      await expect(heroValue(page, `latency ${q}`)).toHaveText(/^\d+(\.\d+)? ms$/);
    }
    await expect(metricsPaper(page, 'Throughput (derived, all requests)')).toContainText(/\d+\.\d req\/s/);

    // Charts draw once two scrapes exist (3 s poll), replacing the "collecting…" placeholder.
    const activity = metricsPaper(page, 'HTTP request activity (cumulative)');
    await expect(activity.getByText('collecting…')).toHaveCount(0, { timeout: 20_000 });
    await expect(activity.locator('.MuiLineChart-line')).toHaveCount(4);
    for (const series of ['All requests received', 'Matched', 'Not matched', 'Forwarded']) {
      await expect(activity.getByText(series, { exact: true })).toBeVisible();
    }
    // The registered expectation shows up in the by-type gauge.
    await expect(metricsPaper(page, 'Expectations by type').getByText('collecting…')).toHaveCount(0);
    await expect(page.getByText(/updated .* · every 3s/)).toBeVisible();
  });

  test('explains how to enable metrics when the server has them off', async ({ page }) => {
    // The disabled state is driven by a 404 from /mockserver/metrics — simulate it so
    // this runs whatever flags the server under test was started with.
    await page.route('**/mockserver/metrics', (route) => route.fulfill({ status: 404, body: '' }));
    allowConsole(page, /status of 404/);
    await openDashboard(page, 'metrics');
    const alert = page.getByRole('alert').filter({ hasText: 'Metrics are disabled' });
    await expect(alert).toBeVisible();
    await expect(alert).toContainText('-Dmockserver.metricsEnabled=true');
    await expect(alert).toContainText('MOCKSERVER_METRICS_ENABLED=true');
    await expect(page.getByText('disabled', { exact: true })).toBeVisible();
  });

  test('shows a retryable error when scraping fails, and recovers on Retry', async ({ page, request }) => {
    await page.route('**/mockserver/metrics', (route) => route.fulfill({ status: 500, body: 'boom' }));
    allowConsole(page, /status of 500/);
    await openDashboard(page, 'metrics');
    const alert = page.getByRole('alert').filter({ hasText: 'Could not load metrics' });
    await expect(alert).toBeVisible();

    await page.unroute('**/mockserver/metrics');
    await alert.getByRole('button', { name: 'Retry' }).click();
    await expect(alert).toHaveCount(0);
    await expect(page.getByText('live', { exact: true })).toBeVisible();
    await expect(heroValue(page, 'Matched')).toHaveText(/^\d+$/);
  });

  // E2E-SHELL-7 (fixed): the y-axis had a fixed width, so formatted ticks were
  // ellipsised ("47.…" for 47.7 MB, "100…" for 100,000, the last time tick "0…"),
  // every time tick read the same HH:MM, and count charts drew 0, 1, 1.
  test('E2E-SHELL-7: chart axis tick labels are shown in full, not ellipsised or repeated', async ({ page, request }) => {
    await openDashboard(page, 'metrics');
    // Charts draw their axes once two scrapes exist (3 s poll).
    await expect(page.locator('.MuiChartsAxis-tickLabel').first()).toBeVisible({ timeout: 20_000 });
    // Let a few more scrapes land so the time axis has several samples.
    await expect.poll(async () => (await metricsPaper(page, 'HTTP request activity (cumulative)').locator('.MuiChartsAxis-directionX .MuiChartsAxis-tickLabel').allTextContents()).length, { timeout: 20_000 }).toBeGreaterThanOrEqual(3);
    // The throughput chart has one sample fewer than the scrapes; it still gets a time axis.
    await expect(metricsPaper(page, 'Throughput (derived, all requests)').locator('.MuiChartsAxis-directionX .MuiChartsAxis-tickLabel').first()).toBeVisible();
    const ticks = await page.locator('.MuiChartsAxis-tickLabel').allTextContents();
    expect(ticks.length).toBeGreaterThan(0);
    expect(ticks.filter((t) => t.endsWith('…')), 'truncated tick labels').toEqual([]);
    for (const paper of await page.locator('.MuiPaper-outlined').filter({ has: page.locator('.MuiChartsAxis-tickLabel') }).all()) {
      const xs = await paper.locator('.MuiChartsAxis-directionX .MuiChartsAxis-tickLabel').allTextContents();
      expect(new Set(xs).size, `repeated time ticks ${xs.join(',')}`).toBe(xs.length);
      const ys = await paper.locator('.MuiChartsAxis-directionY .MuiChartsAxis-tickLabel').allTextContents();
      expect(new Set(ys).size, `repeated value ticks ${ys.join(',')}`).toBe(ys.length);
    }
  });

  // E2E-SHELL-11 (fixed): the received counter and throughput count control-plane
  // calls too (the view's own 3 s scrape), so their labels now say so.
  test('E2E-SHELL-11: the Metrics request counter and throughput say they count all requests', async ({ page }) => {
    await openDashboard(page, 'metrics');
    await expect(page.getByText('All requests received', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Throughput (derived, all requests)', { exact: true })).toBeVisible();
    await expect(page.getByText('Requests received', { exact: true })).toHaveCount(0);
  });
});

// ---------------------------------------------------------------------------
// Title bar controls
// ---------------------------------------------------------------------------

test.describe('title bar', () => {
  test('every icon control has an accessible name, opens its dialog, and Escape closes it back to the trigger', async ({ page }) => {
    await openDashboard(page);
    const header = page.locator('header');
    const dialogs: [string, string | RegExp][] = [
      ['Keyboard shortcuts', 'Keyboard Shortcuts'],
      ['Server clock', 'Server Clock'],
      // E2E-SHELL-9 (fixed): the Refresh button is no longer part of this dialog's name.
      ['Explain unmatched requests', /^Explain Unmatched Requests$/],
      ['Matcher test playground', 'Matcher Test Playground'],
      ['Server configuration', 'Server Configuration'],
    ];
    for (const [button, title] of dialogs) {
      const trigger = header.getByRole('button', { name: button, exact: true });
      await trigger.click();
      const dialog = page.getByRole('dialog', { name: title });
      await expect(dialog, `${button} opens its dialog`).toBeVisible();
      await page.keyboard.press('Escape');
      await expect(dialog).toHaveCount(0);
      await expect(trigger, `focus returns to ${button}`).toBeFocused();
    }
    // The remaining title-bar controls are present and named.
    for (const name of ['New workspace', 'Stop following new entries', 'Switch to light mode', 'Import / export tools', 'Clear logs, expectations, or reset server']) {
      await expect(header.getByRole('button', { name, exact: true })).toBeVisible();
    }
    await expect(header.getByRole('combobox', { name: 'Operating mode' })).toBeVisible();
  });

  test('Server clock freezes, advances and resets the real server clock', async ({ page, request }) => {
    type Clock = { currentEpochMillis: number; frozen: boolean };
    const clock = async () => (await (await request.get(`${ORIGIN}/mockserver/clock`)).json()) as Clock;
    try {
      await openDashboard(page);
      await page.getByRole('button', { name: 'Server clock' }).click();
      const dialog = page.getByRole('dialog', { name: 'Server Clock' });
      await expect(dialog.getByText('live', { exact: true })).toBeVisible();

      await dialog.getByRole('button', { name: 'Freeze now' }).click();
      await expect(dialog.getByText('frozen', { exact: true })).toBeVisible();
      const frozenAt = await clock();
      expect(frozenAt.frozen).toBe(true);

      // Advance by 1 hour.
      await dialog.getByRole('spinbutton').fill('1');
      await dialog.getByRole('combobox').click();
      await page.getByRole('option', { name: 'hr' }).click();
      await dialog.getByRole('button', { name: 'Advance' }).click();
      await expect.poll(async () => (await clock()).currentEpochMillis - frozenAt.currentEpochMillis).toBe(3_600_000);

      await dialog.getByRole('button', { name: 'Reset to live' }).click();
      await expect(dialog.getByText('live', { exact: true })).toBeVisible();
      expect((await clock()).frozen).toBe(false);
      await dialog.getByRole('button', { name: 'Close' }).click();
      await expect(dialog).toHaveCount(0);
    } finally {
      await request.put(`${ORIGIN}/mockserver/clock`, { data: { action: 'reset' } });
    }
  });

  test('Explain unmatched requests lists a real unmatched request with its closest expectation', async ({ page, request }) => {
    const stamp = Date.now();
    await openDashboard(page);
    await page.getByRole('button', { name: 'Explain unmatched requests' }).click();
    const dialog = page.getByRole('dialog', { name: /^Explain Unmatched Requests/ });
    await expect(dialog.getByText(/^No unmatched requests/)).toBeVisible();

    await upsert(request, `/shell/explain-${stamp}`);
    expect((await request.get(`${ORIGIN}/shell/explain-${stamp}-other`)).status()).toBe(404);
    await dialog.getByRole('button', { name: 'Refresh' }).click();
    await expect(dialog.getByText(`GET /shell/explain-${stamp}-other`)).toBeVisible();
    await expect(dialog.getByText('Closest of 1 expectations:')).toBeVisible();
    await expect(dialog.getByText(/^differs on \d+ fields?$/)).toBeVisible();
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(dialog).toHaveCount(0);
  });

  test('the shortcuts dialog lists the documented shortcuts and closes with its Close button', async ({ page }) => {
    await openDashboard(page);
    await page.getByRole('button', { name: 'Keyboard shortcuts' }).click();
    const dialog = page.getByRole('dialog', { name: 'Keyboard Shortcuts' });
    for (const action of [
      'Show this keyboard shortcuts help',
      'Focus the log search field',
      'Clear server logs and recorded requests (asks for confirmation)',
      'Show / hide the request filter panel',
    ]) {
      await expect(dialog.getByText(action, { exact: true })).toBeVisible();
    }
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(dialog).toHaveCount(0);
  });

  test('the tools menu lists all twelve tools and each opens its named dialog', async ({ page }) => {
    await openDashboard(page);
    const tools: [string, string][] = [
      ['Import OpenAPI…', 'Import OpenAPI'],
      ['Import WSDL…', 'Import WSDL'],
      ['Import GraphQL Schema…', 'Import GraphQL Schema'],
      ['Pact Contract (export / verify)…', 'Pact Contract'],
      ['Mock OIDC Provider…', 'Mock OIDC Provider'],
      ['Mock SAML Provider…', 'Mock SAML Provider'],
      ['Mock SCIM Provider…', 'Mock SCIM Provider'],
      ['AsyncAPI Broker Mock…', 'AsyncAPI Broker Mock'],
      ['Register CRUD Resource…', 'Register CRUD Resource'],
      ['Mock File Store…', 'File Store'],
      ['Diff Two Requests…', 'Diff Two Requests'],
      ['Compare Against Baseline…', 'Compare Against Baseline'],
    ];
    const trigger = page.getByRole('button', { name: 'Import / export tools' });
    await trigger.click();
    await expect(page.getByRole('menuitem')).toHaveText(tools.map(([item]) => item));
    await page.keyboard.press('Escape');
    await expect(page.getByRole('menu')).toHaveCount(0);

    for (const [item, title] of tools) {
      await trigger.click();
      await page.getByRole('menuitem', { name: item, exact: true }).click();
      const dialog = page.getByRole('dialog', { name: title });
      await expect(dialog, `${item} opens "${title}"`).toBeVisible();
      await page.keyboard.press('Escape');
      await expect(dialog).toHaveCount(0);
    }
  });

  test('the operating-mode selector switches the server mode, by mouse and by keyboard', async ({ page, request }) => {
    const original = ((await (await request.get(`${ORIGIN}/mockserver/mode`)).json()) as { mode: string }).mode;
    try {
      await openDashboard(page);
      const select = page.getByRole('combobox', { name: 'Operating mode' });
      await expect(select).toHaveText(original);

      await select.click();
      await expect(page.getByRole('option')).toHaveText(['SIMULATE', 'SPY', 'CAPTURE']);
      await page.getByRole('option', { name: 'CAPTURE' }).click();
      await expect(page.getByRole('alert').filter({ hasText: 'Operating mode set to CAPTURE' })).toBeVisible();
      await expect
        .poll(async () => ((await (await request.get(`${ORIGIN}/mockserver/mode`)).json()) as { mode: string }).mode)
        .toBe('CAPTURE');

      // Keyboard only: open the list, move to SIMULATE (first option), choose it.
      await select.focus();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('listbox')).toBeVisible();
      await page.keyboard.press('Home');
      await expect(page.getByRole('option', { name: 'SIMULATE' })).toBeFocused();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('listbox')).toHaveCount(0);
      await expect(select).toHaveText('SIMULATE');
      await expect
        .poll(async () => ((await (await request.get(`${ORIGIN}/mockserver/mode`)).json()) as { mode: string }).mode)
        .toBe('SIMULATE');
    } finally {
      await request.put(`${ORIGIN}/mockserver/mode?mode=${original}`);
    }
  });

  test('the theme toggle defaults to dark, switches to light, and the choice survives a reload', async ({ page }) => {
    await openDashboard(page);
    const bodyLuminance = async () =>
      relativeLuminance(await page.evaluate(() => getComputedStyle(document.body).backgroundColor));
    expect(await bodyLuminance(), 'dark by default').toBeLessThan(0.1);

    await page.getByRole('button', { name: 'Switch to light mode' }).click();
    await expect(page.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible();
    await expect.poll(bodyLuminance).toBeGreaterThan(0.8);
    expect(await page.evaluate(() => localStorage.getItem('mockserver-theme'))).toBe('light');

    await page.reload();
    await expect(connectionChip(page, 'connected')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible();
    expect(await bodyLuminance()).toBeGreaterThan(0.8);

    await page.getByRole('button', { name: 'Switch to dark mode' }).click();
    await expect.poll(bodyLuminance).toBeLessThan(0.1);
  });

  test('the master Follow switch flips every live panel between Following and Follow', async ({ page }) => {
    await openDashboard(page);
    // DashboardGrid renders two responsive layouts; count only the visible copy.
    const chips = (label: string) => page.getByText(label, { exact: true }).filter({ visible: true });
    await expect(chips('Following')).toHaveCount(3);

    await page.getByRole('button', { name: 'Stop following new entries' }).click();
    await expect(chips('Following')).toHaveCount(0);
    await expect(chips('Follow')).toHaveCount(3);

    await page.getByRole('button', { name: 'Follow new entries', exact: true }).click();
    await expect(chips('Following')).toHaveCount(3);
    await expect(page.getByRole('button', { name: 'Stop following new entries' })).toBeVisible();
  });

  test('New workspace adds a workspace tab row, each workspace keeps its own view, and closing one removes the row', async ({ page }) => {
    await openDashboard(page);
    const bar = page.getByRole('group', { name: 'Workspaces' });
    await expect(bar).toHaveCount(0);

    await page.locator('header').getByRole('button', { name: 'New workspace' }).click();
    await expect(bar).toBeVisible();
    const tabs = bar.getByRole('button', { name: /^Workspace / });
    await expect(tabs).toHaveCount(2);

    // The new workspace is active; navigate it somewhere else, then switch back.
    await page.getByRole('button', { name: 'Observe views' }).click();
    await page.getByRole('menuitem', { name: 'Metrics view' }).click();
    await expect(page).toHaveURL(/#\/metrics$/);
    await tabs.first().click();
    await expect(page).toHaveURL(/#\/dashboard$/);
    await tabs.nth(1).click();
    await expect(page).toHaveURL(/#\/metrics$/);

    const secondName = ((await tabs.nth(1).getAttribute('aria-label')) ?? '').replace(/^Workspace /, '');
    await bar.getByRole('button', { name: `Close workspace ${secondName}` }).click();
    await expect(bar).toHaveCount(0);
  });

  test('a workspace can target another MockServer: it shows that server’s data, and its deletes go there only', async ({ page, request }) => {
    const secondary = METRICS_ORIGIN;
    const secondaryPort = new URL(secondary).port;
    const suffix = Date.now();
    const mainPath = `/shell/ws-main-${suffix}`;
    const secondaryPath = `/shell/ws-secondary-${suffix}`;
    await upsert(request, mainPath);
    await upsert(request, secondaryPath, 200, secondary);
    const onSecondary = async () => {
      const res = await request.put(`${secondary}/mockserver/retrieve?type=active_expectations`, { data: { path: secondaryPath } });
      return ((await res.json()) as unknown[]).length;
    };
    try {
      await openDashboard(page);
      await page.locator('header').getByRole('button', { name: 'New workspace' }).click();
      const bar = page.getByRole('group', { name: 'Workspaces' });
      const tabs = bar.getByRole('button', { name: /^Workspace / });
      const secondName = ((await tabs.nth(1).getAttribute('aria-label')) ?? '').replace(/^Workspace /, '');

      await bar.getByRole('button', { name: `Set MockServer for workspace ${secondName}` }).click();
      const dialog = page.getByRole('dialog', { name: `MockServer for ${secondName}` });
      await dialog.getByLabel('Host').fill(new URL(secondary).hostname);
      await dialog.getByLabel('Port').fill(secondaryPort);
      await dialog.getByRole('button', { name: 'Save' }).click();
      await expect(dialog).toHaveCount(0);

      await expect(bar.getByText(`:${secondaryPort}`)).toBeVisible();
      await expect(connectionChip(page, 'connected')).toBeVisible();
      await expect(page.getByText(secondaryPath).first()).toBeVisible();
      await expect(page.getByText(mainPath)).toHaveCount(0);

      await tabs.first().click();
      await expect(page.getByText(mainPath).first()).toBeVisible();
      await expect(page.getByText(secondaryPath)).toHaveCount(0);

      // A destructive action in the retargeted workspace reaches only its own server.
      await tabs.nth(1).click();
      await expect(page.getByText(secondaryPath).first()).toBeVisible();
      await page.locator('#active-expectations-search').fill(secondaryPath);
      const rows = page.getByRole('button', { name: 'Delete expectation' }).filter({ visible: true });
      await expect(rows).toHaveCount(1);
      await rows.click();
      await page.getByRole('dialog', { name: 'Delete this expectation?' }).getByRole('button', { name: 'Delete', exact: true }).click();
      await expect.poll(onSecondary).toBe(0);
      expect(await activeExpectationCount(request)).toBe(1);
    } finally {
      await request.put(`${secondary}/mockserver/clear?type=expectations`, { data: { path: secondaryPath } });
    }
  });
});

// ---------------------------------------------------------------------------
// Clear / reset menu
// ---------------------------------------------------------------------------

test.describe('clear menu', () => {
  test('Reset Server (all) is cancellable, then wipes expectations and recorded requests and returns to Get Started', async ({ page, request }) => {
    const path = `/shell/reset-${Date.now()}`;
    await upsert(request, path);
    await request.get(`${ORIGIN}${path}`);
    await openDashboard(page);
    const clear = page.getByRole('button', { name: 'Clear logs, expectations, or reset server' });

    await clear.click();
    await expect(page.getByRole('menuitem')).toHaveText(['Clear Server Logs', 'Clear Server Expectations', 'Reset Server (all)']);
    await page.getByRole('menuitem', { name: 'Reset Server (all)' }).click();
    const confirm = page.getByRole('dialog', { name: 'Reset the entire server?' });
    await confirm.getByRole('button', { name: 'Cancel' }).click();
    await expect(confirm).toHaveCount(0);
    expect(await activeExpectationCount(request), 'Cancel leaves the server untouched').toBe(1);

    await clear.click();
    await page.getByRole('menuitem', { name: 'Reset Server (all)' }).click();
    await confirm.getByRole('button', { name: 'Reset server' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Server reset' })).toBeVisible();
    await expect.poll(() => activeExpectationCount(request)).toBe(0);
    await expect.poll(() => recordedRequestCount(request)).toBe(0);
    // By design a full reset also returns the dashboard to Get Started (store clearUI).
    await expect(page).toHaveURL(/#\/get-started$/);
    await expect(page.getByRole('heading', { name: 'Welcome to MockServer' })).toBeVisible();
    await expect(page.getByRole('alert').filter({ hasText: /This server has \d+ active mock/ })).toHaveCount(0);
  });

  test('Clear Server Logs empties the Log Messages panel and keeps expectations', async ({ page, request }) => {
    await openDashboard(page);
    await upsert(request, `/shell/clear-logs-${Date.now()}`);
    const log = page.getByRole('log').filter({ visible: true });
    await expect(log.getByText('CREATED_EXPECTATION').first()).toBeVisible();

    await page.getByRole('button', { name: 'Clear logs, expectations, or reset server' }).click();
    await page.getByRole('menuitem', { name: 'Clear Server Logs' }).click();
    await page.getByRole('dialog', { name: 'Clear server logs?' }).getByRole('button', { name: 'Clear logs' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Server logs and recorded requests cleared' })).toBeVisible();
    await expect(log.getByText('CREATED_EXPECTATION')).toHaveCount(0);
    expect(await activeExpectationCount(request)).toBe(1);
  });

  // E2E-SHELL-1 (fixed): PUT /mockserver/clear?type=log also removes recorded
  // requests (they are entries in the same server event log), but the prompt said
  // they were kept and the Received Requests panel went on showing them. The
  // prompt now says what happens, and the panel empties straight away.
  test('E2E-SHELL-1: Clear Server Logs says it removes recorded requests, and the Received Requests panel empties at once', async ({ page, request }) => {
    const stamp = Date.now();
    await openDashboard(page);
    await upsert(request, `/shell/kept-mock-${stamp}`);
    for (const p of ['a', 'b']) await request.get(`${ORIGIN}/shell/cleared-${stamp}-${p}`);
    await expect.poll(() => recordedRequestCount(request)).toBe(2);
    const shown = page.getByText(`/shell/cleared-${stamp}-a`).filter({ visible: true });
    await expect(shown.first()).toBeVisible();

    await page.getByRole('button', { name: 'Clear logs, expectations, or reset server' }).click();
    await page.getByRole('menuitem', { name: 'Clear Server Logs' }).click();
    const confirm = page.getByRole('dialog', { name: 'Clear server logs?' });
    await expect(confirm).toContainText('every recorded and proxied request');
    await expect(confirm).toContainText('Expectations are kept.');
    await expect(confirm).not.toContainText(/recorded requests are kept/i);
    await confirm.getByRole('button', { name: 'Clear logs' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Server logs and recorded requests cleared' })).toBeVisible();

    await expect(shown).toHaveCount(0);
    expect(await recordedRequestCount(request), 'the server removed the recorded requests').toBe(0);
    expect(await activeExpectationCount(request), 'expectations are kept').toBe(1);
  });
});

// ---------------------------------------------------------------------------
// Keyboard shortcuts and keyboard-only use
// ---------------------------------------------------------------------------

test.describe('keyboard', () => {
  test('? opens the help, Ctrl+Shift+L asks to clear logs, Ctrl+Shift+F toggles the request filter; none fire while typing', async ({ page }) => {
    await openDashboard(page);
    await page.locator('body').press('Shift+Slash');
    const help = page.getByRole('dialog', { name: 'Keyboard Shortcuts' });
    await expect(help).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(help).toHaveCount(0);

    await page.locator('body').press('Control+Shift+L');
    const confirm = page.getByRole('dialog', { name: 'Clear server logs?' });
    await expect(confirm).toBeVisible();
    await confirm.getByRole('button', { name: 'Cancel' }).click();
    await expect(confirm).toHaveCount(0);

    const filterBody = page
      .locator('.MuiCard-root')
      .filter({ has: page.getByText('Request Filter', { exact: true }) })
      .locator('.MuiCollapse-root');
    await expect(filterBody).toHaveClass(/MuiCollapse-hidden/);
    await page.locator('body').press('Control+Shift+F');
    await expect(filterBody).toHaveClass(/MuiCollapse-entered/);
    await page.locator('body').press('Control+Shift+F');
    await expect(filterBody).toHaveClass(/MuiCollapse-hidden/);

    // Typing "?" into a search field types it rather than opening the help.
    const logSearch = page.getByPlaceholder('Search — try /regex/').filter({ visible: true });
    await logSearch.click();
    await page.keyboard.type('?');
    await expect(logSearch).toHaveValue('?');
    await expect(help).toHaveCount(0);
  });

  // E2E-SHELL-2 (fixed): Ctrl/Cmd+K used to focus a ref that no field was attached to.
  test('E2E-SHELL-2: Ctrl+K focuses the Log Messages search field, switching to the Dashboard if needed', async ({ page }) => {
    await openDashboard(page);
    const logSearch = page.getByPlaceholder('Search — try /regex/').filter({ visible: true });
    await page.locator('body').press('Control+k');
    await expect(logSearch).toBeFocused();

    await goToView(page, 'metrics');
    await expect(page.getByTestId('view-description')).toHaveText(/^Prometheus metrics/);
    await page.locator('body').press('Control+k');
    await expect(page).toHaveURL(/#\/dashboard$/);
    await expect(logSearch).toBeFocused();
  });

  test('the grouped navigation works with the keyboard alone: Enter opens a group, arrows move, Enter selects, Escape closes', async ({ page }) => {
    await openDashboard(page);
    const observe = page.getByRole('button', { name: 'Observe views' });
    await tabTo(page, observe);
    await page.keyboard.press('Enter');
    const menu = page.getByRole('menu');
    await expect(menu).toBeVisible();
    // The open (modal) menu hides the rest of the page from the a11y tree, so read the attribute directly.
    await expect(page.locator('button[aria-label="Observe views"]')).toHaveAttribute('aria-expanded', 'true');
    await page.keyboard.press('Escape');
    await expect(menu).toHaveCount(0);
    await expect(observe).toBeFocused();

    await page.keyboard.press('Enter');
    // Dashboard, Traffic, Trace, Metrics — focus starts on the selected item (Dashboard).
    await expect(page.getByRole('menuitem', { name: 'Dashboard view' })).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('ArrowDown');
    await expect(page.getByRole('menuitem', { name: 'Metrics view' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/#\/metrics$/);
    await expect(page.getByTestId('view-description')).toHaveText(/^Prometheus metrics/);

    // The view's primary action is reachable and operable by keyboard too.
    const refresh = page.getByRole('button', { name: 'Refresh metrics' });
    await tabTo(page, refresh);
    await page.keyboard.press('Enter');
  });

  test('the collapsed (hamburger) navigation at 1024px works with the keyboard alone', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 700 });
    await openDashboard(page);
    const burger = page.getByRole('button', { name: 'Open navigation menu' });
    await tabTo(page, burger);
    await page.keyboard.press('Enter');
    await expect(page.getByRole('menuitem')).toHaveCount(22); // 21 views, Trace listed under Observe and AI
    await page.keyboard.press('Escape');
    await expect(page.getByRole('menu')).toHaveCount(0);
    await expect(burger).toBeFocused();

    await page.keyboard.press('Enter');
    await page.getByRole('menuitem', { name: 'Service chaos view' }).focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/#\/chaos$/);
    await expect(page.locator('header')).toContainText('Chaos');
  });

  test('every title-bar dialog opens from the keyboard and Escape returns focus', async ({ page }) => {
    await openDashboard(page);
    const clear = page.getByRole('button', { name: 'Clear logs, expectations, or reset server' });
    await tabTo(page, clear);
    await page.keyboard.press('Enter');
    await expect(page.getByRole('menuitem', { name: 'Clear Server Logs' })).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    const confirm = page.getByRole('dialog', { name: 'Clear all expectations?' });
    await expect(confirm).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(confirm).toHaveCount(0);

    const config = page.getByRole('button', { name: 'Server configuration' });
    await tabTo(page, config);
    await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog', { name: 'Server Configuration' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(config).toBeFocused();
  });

  test('every top-level view has keyboard-reachable content after the title bar', async ({ page }) => {
    test.setTimeout(120_000);
    await openDashboard(page, 'get-started');
    const clear = page.getByRole('button', { name: 'Clear logs, expectations, or reset server' });
    // MCP Health is read-only and has no controls until MCP traffic is captured.
    for (const view of VIEWS.filter((v) => v.hash !== 'mcp-health')) {
      await goToView(page, view.hash);
      if (view.description) await expect(page.getByTestId('view-description')).toHaveText(view.description);
      else await expect(page.getByRole('heading', { name: 'Welcome to MockServer' })).toBeVisible();
      // Lazy views mount after the description bar, so retry until the content is in.
      await expect(async () => {
        await clear.focus();
        await page.keyboard.press('Tab');
        const where = await page.evaluate(() => {
          const a = document.activeElement as HTMLElement | null;
          if (!a || a === document.body || a.closest('header')) return '';
          return `${a.tagName} ${a.getAttribute('aria-label') ?? a.textContent?.trim().slice(0, 30) ?? ''}`;
        });
        expect(where, `${view.hash}: the first Tab after the title bar lands in the view`).not.toBe('');
      }).toPass({ timeout: 10_000 });
    }
  });

  // E2E-SHELL-4 (fixed): the Request Filter header was a clickable <div> that
  // Tab skipped, so the filter could only be opened with the mouse.
  test('E2E-SHELL-4: the Request Filter header is a keyboard-operable disclosure button', async ({ page }) => {
    await openDashboard(page);
    const toggle = page.getByRole('button', { name: /Request Filter/ });
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await tabTo(page, toggle);
    await page.keyboard.press('Enter');
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  });
});

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

test('every view is reachable from its nav group, deep links load it, and Back returns to the previous view', async ({ page }) => {
  test.setTimeout(120_000);
  await openDashboard(page, 'get-started');
  for (const view of VIEWS) {
    await page.getByRole('button', { name: view.group }).click();
    await page.getByRole('menuitem', { name: view.item, exact: true }).click();
    await expect(page.getByRole('menu')).toHaveCount(0);
    await expect(page).toHaveURL(new RegExp(`#/${view.hash}$`));
    if (view.description) await expect(page.getByTestId('view-description')).toHaveText(view.description);
    else await expect(page.getByRole('heading', { name: 'Welcome to MockServer' })).toBeVisible();
  }

  await page.goBack();
  await expect(page).toHaveURL(/#\/library$/);
  await expect(page.getByTestId('view-description')).toHaveText(/^Browse and reuse captured/);

  // A deep link opens straight into its view.
  await page.goto('./#/slo');
  await expect(page.getByTestId('view-description')).toHaveText(/^Assert service-level objectives/);
  // An unknown hash falls back to a valid view rather than a blank page.
  await page.goto('./#/no-such-view');
  await expect(connectionChip(page, 'connected')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Observe views' })).toBeVisible();
});

// ---------------------------------------------------------------------------
// Get Started landing page
// ---------------------------------------------------------------------------

test.describe('Get Started', () => {
  test('each feature tile navigates to its view and Import OpenAPI opens the import dialog', async ({ page }) => {
    await openDashboard(page, 'get-started');
    await expect(page.getByRole('heading', { name: 'Welcome to MockServer' })).toBeVisible();
    const tiles: [string, string][] = [
      ['Create Mock', 'composer'],
      ['View Traffic', 'traffic'],
      ['Open Breakpoints', 'breakpoints'],
      ['Open Chaos', 'chaos'],
      ['Open Performance', 'performance'],
      ['Open LLM Optimise', 'optimise'],
    ];
    for (const [button, hash] of tiles) {
      await goToView(page, 'get-started');
      await page.getByRole('button', { name: button, exact: true }).click();
      await expect(page, `${button} → #/${hash}`).toHaveURL(new RegExp(`#/${hash}$`));
    }

    await goToView(page, 'get-started');
    await page.getByRole('button', { name: 'Import OpenAPI', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Import OpenAPI', exact: true });
    await expect(dialog).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
  });

  test('the "More in the tabs above" links navigate and the UI docs link opens the docs in a new tab', async ({ page }) => {
    await openDashboard(page, 'get-started');
    const more: [string, string][] = [
      ['Dashboard', 'dashboard'],
      ['Library', 'library'],
      ['Verification', 'verification'],
      ['Drift', 'drift'],
      ['Async', 'async'],
      ['Metrics', 'metrics'],
    ];
    for (const [label, hash] of more) {
      await goToView(page, 'get-started');
      await page.getByRole('button', { name: label, exact: true }).click();
      await expect(page, `${label} → #/${hash}`).toHaveURL(new RegExp(`#/${hash}$`));
    }
    await goToView(page, 'get-started');
    const docs = page.getByRole('link', { name: 'UI docs' });
    await expect(docs).toHaveAttribute('href', 'https://www.mock-server.com/mock_server/mockserver_ui.html');
    await expect(docs).toHaveAttribute('target', '_blank');
    await expect(docs).toHaveAttribute('rel', /noopener/);
  });

  test('offers a jump to the Dashboard when the server already holds mocks', async ({ page, request }) => {
    await upsert(request, `/shell/existing-${Date.now()}`);
    await openDashboard(page, 'get-started');
    const notice = page.getByRole('alert').filter({ hasText: 'This server has 1 active mock' });
    await expect(notice).toBeVisible();
    await notice.getByRole('button', { name: 'Open Dashboard' }).click();
    await expect(page).toHaveURL(/#\/dashboard$/);
  });

  test('is keyboard operable: Tab reaches Create Mock and Enter opens the composer', async ({ page }) => {
    await openDashboard(page, 'get-started');
    const create = page.getByRole('button', { name: 'Create Mock', exact: true });
    await tabTo(page, create);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/#\/composer$/);
  });

  test('collapses the tiles into a list of links on a narrow window, and the links still navigate', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 900 });
    await openDashboard(page, 'get-started');
    await expect(page.getByRole('button', { name: 'Create Mock', exact: true })).toBeHidden();
    await page.getByRole('button', { name: 'Chaos Testing', exact: true }).click();
    await expect(page).toHaveURL(/#\/chaos$/);
  });

  // E2E-SHELL-5 (fixed): the landing panel centred its content with
  // justify-content:center inside an overflow:auto box, so when it was taller
  // than the window the heading was pushed above the scroll origin.
  test('E2E-SHELL-5: the Welcome heading is fully visible below the title bar at 1024x700', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 700 });
    await openDashboard(page, 'get-started');
    const heading = page.getByRole('heading', { name: 'Welcome to MockServer' });
    await expect(heading).toBeVisible();
    const headerBottom = (await page.locator('header').boundingBox())!.y + (await page.locator('header').boundingBox())!.height;
    const headingTop = (await heading.boundingBox())!.y;
    expect(headingTop, 'heading starts below the title bar').toBeGreaterThanOrEqual(headerBottom);
  });
});

// ---------------------------------------------------------------------------
// Connection status
// ---------------------------------------------------------------------------

// The dashboard's live feed is cut and reconnects are made to fail by taking the
// browser offline (Playwright routes the socket so it can close the live one);
// the server itself keeps running so the suite can share it.
async function cutConnection(page: Page): Promise<() => Promise<void>> {
  allowConsole(page, /ERR_INTERNET_DISCONNECTED/, /WebSocket connection to .* failed/, /Failed to load resource/);
  const sockets: { close: () => Promise<void> }[] = [];
  await page.routeWebSocket(/_mockserver_ui_websocket/, (ws) => {
    sockets.push(ws.connectToServer());
  });
  return async () => {
    await page.context().setOffline(true);
    for (const s of sockets) await s.close();
  };
}

test.describe('connection status', () => {
  test('goes disconnected with an actionable message when the live connection drops, then recovers and streams live again', async ({ page, request }) => {
    test.setTimeout(90_000);
    const goDown = await cutConnection(page);
    await openDashboard(page);

    await goDown();
    await expect(connectionChip(page, 'disconnected')).toBeVisible();
    await expect(page.getByRole('alert').filter({ hasText: `Connection lost to ${HOST}:${PORT}` })).toBeVisible();

    await page.context().setOffline(false);
    // Reconnect back-off is capped at 15 s.
    await expect(connectionChip(page, 'connected')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole('alert').filter({ hasText: 'Connection lost to' })).toHaveCount(0);
    await expect(page.getByTestId('connection-loss-banner')).toHaveCount(0);

    // The re-opened feed is live: a request made now streams into the dashboard.
    const path = `/shell/after-reconnect-${Date.now()}`;
    await request.get(`${ORIGIN}${path}`);
    await expect(page.getByText(path).filter({ visible: true }).first()).toBeVisible();
  });

  // E2E-SHELL-3 (fixed): every reconnect attempt passes through "connecting", which
  // used to restart the 8 s banner timer and re-arm a dismissed banner, so it first
  // showed after ~17 s, vanished on each retry, and came back after being closed.
  test('E2E-SHELL-3: the connection-loss banner appears after ~8 s, stays up through retries, and stays dismissed', async ({ page }) => {
    test.setTimeout(120_000);
    const goDown = await cutConnection(page);
    await openDashboard(page);
    await goDown();

    const banner = page.getByTestId('connection-loss-banner');
    await expect(banner).toBeVisible({ timeout: 12_000 });
    // Through the next retries (back-off 3/6/9/12 s) it must not flicker away.
    const until = Date.now() + 15_000;
    while (Date.now() < until) {
      expect(await banner.count(), 'banner stays visible while still disconnected').toBe(1);
      await page.waitForTimeout(250);
    }
    await banner.getByRole('button', { name: 'Close' }).click();
    const until2 = Date.now() + 20_000;
    while (Date.now() < until2) {
      expect(await banner.count(), 'a dismissed banner stays dismissed for this outage').toBe(0);
      await page.waitForTimeout(250);
    }
    await page.context().setOffline(false);
  });
});

// ---------------------------------------------------------------------------
// Narrow windows and themes across every view
// ---------------------------------------------------------------------------

for (const { width, height, theme } of [
  { width: 1024, height: 700, theme: 'light' },
  { width: 768, height: 900, theme: 'dark' },
] as const) {
  test(`at ${width}x${height} (${theme}) no view scrolls the page sideways and the nav and clear controls stay on screen`, async ({ page }) => {
    test.setTimeout(120_000);
    await page.addInitScript((t) => localStorage.setItem('mockserver-theme', t), theme);
    await page.setViewportSize({ width, height });
    await openDashboard(page, 'get-started');
    await expect(page.getByRole('button', { name: theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode' })).toBeVisible();
    const burger = page.getByRole('button', { name: 'Open navigation menu' });
    const clear = page.getByRole('button', { name: 'Clear logs, expectations, or reset server' });
    for (const view of VIEWS) {
      await goToView(page, view.hash);
      if (view.description) await expect(page.getByTestId('view-description')).toHaveText(view.description);
      else await expect(page.getByRole('heading', { name: 'Welcome to MockServer' })).toBeVisible();
      const overflow = await page.evaluate(() => {
        const se = document.scrollingElement!;
        return se.scrollWidth - se.clientWidth;
      });
      expect(overflow, `${view.hash}: page scrolls horizontally by ${overflow}px`).toBeLessThanOrEqual(0);
      await expect(burger, `${view.hash}: nav menu on screen`).toBeInViewport();
      await expect(clear, `${view.hash}: clear control on screen`).toBeInViewport();
    }
  });
}

// E2E-SHELL-6 (fixed): log rows took the server's style.color, tuned for a dark
// background, so in light mode several types fell far below WCAG AA 4.5:1.
test('E2E-SHELL-6: in light mode, log-row type labels meet WCAG AA contrast', async ({ page, request }) => {
  await page.addInitScript(() => localStorage.setItem('mockserver-theme', 'light'));
  await openDashboard(page);
  const path = `/shell/contrast-${Date.now()}`;
  await upsert(request, path);
  await request.get(`${ORIGIN}${path}`);
  await request.get(`${ORIGIN}${path}-miss`);
  const log = page.getByRole('log').filter({ visible: true });
  for (const type of ['CREATED_EXPECTATION', 'EXPECTATION_RESPONSE', 'NO_MATCH_RESPONSE']) {
    const label = log.getByText(new RegExp(`\\b${type}\\b`)).first();
    await expect(label).toBeVisible();
    const fg = await label.evaluate((el) => getComputedStyle(el).color);
    const ratio = (1 + 0.05) / (relativeLuminance(fg) + 0.05); // against the white panel
    expect(ratio, `${type} (${fg}) contrast on white`).toBeGreaterThanOrEqual(4.5);
  }
});

// E2E-SHELL-8 (fixed): in light mode the title bar's status chip and active nav
// group, and primary-coloured text on the page, were below WCAG AA 4.5:1.
test('E2E-SHELL-8: in light mode the title bar and primary-coloured text meet WCAG AA contrast', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('mockserver-theme', 'light'));
  await openDashboard(page);
  const header = page.locator('header');
  const barColour = await header.evaluate((el) => getComputedStyle(el).backgroundColor);
  const ratio = (fg: string, bg: string) => {
    const [hi, lo] = [relativeLuminance(fg), relativeLuminance(bg)].sort((a, b) => b - a) as [number, number];
    return (hi + 0.05) / (lo + 0.05);
  };
  // The chip colour is opaque, so it can be compared with the bar directly.
  const chip = await connectionChip(page, 'connected').evaluate((el) => getComputedStyle(el).color);
  expect(ratio(chip, barColour), `connected chip ${chip} on ${barColour}`).toBeGreaterThanOrEqual(4.5);
  // The active group's highlight is a translucent overlay; blend it over the bar.
  const activeBg = await page.getByRole('button', { name: 'Observe views' }).evaluate((el) => getComputedStyle(el).backgroundColor);
  const blend = (fg: string, bg: string) => {
    const [r, g, b, a = 1] = (fg.match(/[\d.]+/g) ?? []).map(Number) as number[];
    const [R, G, B] = (bg.match(/[\d.]+/g) ?? []).map(Number) as number[];
    const mix = (x: number, y: number) => Math.round(x * a + y * (1 - a));
    return `rgb(${mix(r!, R!)}, ${mix(g!, G!)}, ${mix(b!, B!)})`;
  };
  expect(ratio('rgb(255, 255, 255)', blend(activeBg, barColour)), `active group on ${activeBg}`).toBeGreaterThanOrEqual(4.5);
  // Primary-coloured text on the light page (the bar itself uses the primary colour).
  expect(ratio(barColour, 'rgb(250, 250, 250)'), `primary ${barColour} on the page`).toBeGreaterThanOrEqual(4.5);
});

// E2E-SHELL-10 (fixed): the current view was shown only visually.
test('E2E-SHELL-10: the active nav group and the current view are exposed to assistive technology', async ({ page }) => {
  await openDashboard(page, 'metrics');
  await expect(page.getByRole('button', { name: 'Observe views' })).toHaveAttribute('aria-current', 'true');
  await expect(page.getByRole('button', { name: 'Mock views' })).not.toHaveAttribute('aria-current', /.*/);
  await page.getByRole('button', { name: 'Observe views' }).click();
  await expect(page.getByRole('menuitem', { name: 'Metrics view' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByRole('menuitem', { name: 'Dashboard view' })).not.toHaveAttribute('aria-current', /.*/);
  await page.keyboard.press('Escape');
});
