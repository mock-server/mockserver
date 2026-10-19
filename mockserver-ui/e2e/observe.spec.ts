import { test, expect, type APIRequestContext, type Page, type Locator } from '@playwright/test';
import { requireOrSkip } from './ci-guard';
import { spawn, type ChildProcess } from 'node:child_process';
import { existsSync, mkdtempSync, readdirSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Observe and AI views against a real MockServer: the live Dashboard panels,
// Traffic (list, detail, bodies over 64 KiB and every action that must act on
// the WHOLE body), LLM rows in Traffic and Trace, LLM Optimise, MCP Health, the
// LLM Provider filter, the log-pressure banner and the incomplete-log
// verification message, the GraphQL badge, and the narrow Traffic pane.
//
// Assumes the server logs at INFO (the log-panel assertions read received-request
// and verification entries). The log-pressure test boots its own extra server
// (metrics on, maxLogEntries=50) from the runnable jar, or uses
// E2E_MS_PRESSURE_ORIGIN when that server is provided externally.

const HOST = process.env.E2E_MS_HOST || '127.0.0.1';
const PORT = process.env.E2E_MS_PORT || '1084';
const ORIGIN = `http://${HOST}:${PORT}`;
const PRESSURE_PORT = process.env.E2E_MS_PRESSURE_PORT || '1116';

const KIB = 1024;

// ---------------------------------------------------------------------------
// Console guard: any console error or uncaught exception fails the test.
// ---------------------------------------------------------------------------

// Nothing is ignored: the dashboard no longer probes /mockserver/metrics on a
// server whose configuration says metrics are off (E2E-OBS-6).
const IGNORED_CONSOLE: RegExp[] = [];

let consoleProblems: string[] = [];

test.beforeEach(async ({ page, request }) => {
  consoleProblems = [];
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    const where = msg.location().url ?? '';
    if (IGNORED_CONSOLE.some((re) => re.test(where))) return;
    consoleProblems.push(`console.error: ${msg.text()} @ ${where}`);
  });
  page.on('pageerror', (err) => consoleProblems.push(`pageerror: ${err.message}`));
  expect((await request.put(`${ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
});

test.afterEach(async ({ request }) => {
  await request.put(`${ORIGIN}/mockserver/reset`).catch(() => undefined);
  expect(consoleProblems, 'no console errors or uncaught exceptions').toEqual([]);
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function putExpectations(request: APIRequestContext, expectations: unknown[], origin = ORIGIN) {
  const res = await request.put(`${origin}/mockserver/expectation`, { data: expectations });
  expect(res.status(), await res.text()).toBe(201);
}

function stamp(): string {
  return `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
}

/** A JSON body over 64 KiB whose head and tail carry distinct markers. */
function bigJsonBody(tag: string, kib = 70): string {
  return JSON.stringify({ marker: `${tag}-start`, filler: 'x'.repeat(kib * KIB), tail: `${tag}-tail` });
}

function chatBody(content: string): string {
  return JSON.stringify({ model: 'gpt-4o', messages: [{ role: 'system', content: 'be brief' }, { role: 'user', content }] });
}

const OPENAI_EXPECTATION = (id: string) => ({
  id,
  httpRequest: { method: 'POST', path: '/v1/chat/completions' },
  httpLlmResponse: {
    provider: 'OPENAI',
    model: 'gpt-4o',
    completion: { text: 'mocked completion', stopReason: 'stop', usage: { inputTokens: 12, outputTokens: 9 } },
  },
});

async function gotoView(page: Page, view: string, origin = ORIGIN) {
  await page.goto(`${origin}/mockserver/dashboard/#/${view}`);
  await expect(page.getByText('connected', { exact: true })).toBeVisible();
}

function trafficRow(page: Page, pathText: string): Locator {
  return page.locator('[data-vrow]').filter({ has: page.getByTestId('traffic-row-path').filter({ hasText: pathText }) });
}

/** Open a Traffic row. */
async function openTrafficRow(page: Page, pathText: string) {
  await page.mouse.move(0, 0);
  const row = trafficRow(page, pathText).first();
  await expect(row).toBeVisible();
  await row.click({ position: { x: 20, y: 10 } });
}

// Locator for a dashboard grid panel by its title (the grid renders two
// responsive layouts, one hidden by CSS — scope to the visible one).
function panel(page: Page, title: string): Locator {
  return page
    .locator('.MuiPaper-root')
    .filter({ has: page.locator('h6, .MuiTypography-subtitle2').getByText(title, { exact: true }) })
    .filter({ visible: true })
    .first();
}

async function panelHeaderChips(p: Locator): Promise<string[]> {
  return p.evaluate((paper) => {
    const header = paper.firstElementChild as HTMLElement | null;
    return Array.from(header?.querySelectorAll('.MuiChip-root') ?? []).map((c) => (c.textContent ?? '').trim());
  });
}

/** Expand the log row (a group or a single entry) whose text matches. */
async function expandLogRow(log: Locator, text: string | RegExp) {
  const row = log.locator('[data-vrow]').filter({ hasText: text }).first();
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Expand', exact: true }).first().click();
  return row;
}

// ---------------------------------------------------------------------------
// Dashboard: live panels, Follow, removed counts
// ---------------------------------------------------------------------------

test.describe('Dashboard (OBS)', () => {
  test('live panels stream a new request, and Follow toggles off and back on', async ({ page, request }) => {
    const path = `/obs/live-${stamp()}`;
    await putExpectations(request, [{ httpRequest: { path }, httpResponse: { statusCode: 202 } }]);
    await gotoView(page, 'dashboard');

    for (const title of ['Log Messages', 'Active Expectations', 'Received Requests', 'Proxied Requests']) {
      await expect(panel(page, title), `${title} panel`).toBeVisible();
    }
    await expect(panel(page, 'Active Expectations')).toContainText(path);

    expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(202);
    await expect(panel(page, 'Received Requests')).toContainText(path);

    const received = panel(page, 'Received Requests');
    const follow = received.locator('.MuiChip-root').filter({ hasText: /^Follow(ing)?$/ });
    await expect(follow).toHaveText('Following');
    await follow.click();
    await expect(follow).toHaveText('Follow');
    await follow.click();
    await expect(follow).toHaveText('Following');
  });

  // Unreleased changelog: "Dashboard counts removed; Active Expectations now
  // shows the true server count." The window holds at most 100 rows, so a count
  // derived from it pinned at 100.
  test('removed counts: request and log panels carry no count, Active Expectations shows the true server total over 100', async ({ page, request }) => {
    const tag = stamp();
    const expectations = Array.from({ length: 120 }, (_, i) => ({
      id: `obs-count-${tag}-${i}`,
      httpRequest: { path: `/obs/count/${i}` },
      httpResponse: { statusCode: 200 },
    }));
    await putExpectations(request, expectations);
    for (let i = 0; i < 5; i++) await request.get(`${ORIGIN}/obs/count/${i}`);

    await gotoView(page, 'dashboard');
    await expect(panel(page, 'Received Requests')).toContainText('/obs/count/4');

    // The true total, not the 100-row window.
    await expect.poll(() => panelHeaderChips(panel(page, 'Active Expectations'))).toContain('120');
    for (const title of ['Received Requests', 'Log Messages', 'Proxied Requests']) {
      const chips = await panelHeaderChips(panel(page, title));
      expect(chips.filter((c) => /^\d/.test(c)), `${title} header shows no count`).toEqual([]);
    }

    // Traffic: the unmatched badge and host list carry no number either.
    await request.get(`${ORIGIN}/obs/unmatched-${tag}`);
    await request.get(`http://localhost:${PORT}/obs/other-host-${tag}`);
    await gotoView(page, 'traffic');
    const toolbar = page.getByTestId('traffic-toolbar');
    await expect(toolbar.getByText('unmatched', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: /Filter traffic by host/ }).first()).toBeVisible();
    const hostLabels = await page.getByRole('button', { name: /Filter traffic by host/ }).allInnerTexts();
    expect(hostLabels.length).toBeGreaterThanOrEqual(2);
    for (const label of hostLabels) expect(label.trim(), 'host entry is just host:port, no count').toMatch(/^[\w.-]+:\d+$/);

    // MCP Health aggregates the same capped window: no Calls column at all (E2E-OBS-8).
    await request.post(`${ORIGIN}/mcp`, { data: { jsonrpc: '2.0', id: 1, method: 'tools/list' } });
    await gotoView(page, 'mcp-health');
    const mcpTable = page.getByTestId('mcp-health-table');
    await expect(mcpTable.locator('tbody tr').first()).toBeVisible();
    await expect(mcpTable.getByRole('columnheader')).toHaveText(['Server', 'Errors', 'Median', 'p95', 'Max', 'Slowest method']);
  });

  test('E2E-OBS-1: ~150 small expectations no longer shrink the live window, and the banner names the log messages', async ({ page, request }) => {
    const tag = stamp();
    // The banner needs every did-not-match entry: at the default limit (50 per request) the log stays under the frame budget.
    const previousLimit = (await (await request.get(`${ORIGIN}/mockserver/configuration`)).json()).maxLoggedMatchFailures;
    expect((await request.put(`${ORIGIN}/mockserver/configuration`, { data: { maxLoggedMatchFailures: -1 } })).ok()).toBeTruthy();
    try {
      await putExpectations(request, Array.from({ length: 150 }, (_, i) => ({ id: `obs-frame-${tag}-${i}`, httpRequest: { path: `/obs/frame-exp/${i}` }, httpResponse: { statusCode: 200 } })));
      for (let i = 0; i < 50; i++) await request.get(`${ORIGIN}/obs/frame/${i}`);
    } finally {
      await request.put(`${ORIGIN}/mockserver/configuration`, { data: { maxLoggedMatchFailures: previousLimit } });
    }
    await gotoView(page, 'traffic');
    await expect(trafficRow(page, '/obs/frame/49')).toBeVisible();
    // 50 requests with no body are far under the 100-row window and the 16 MB update.
    // The list is windowed, so search for the oldest rather than scrolling to it.
    await page.locator('#traffic-inspector-search').fill('/obs/frame/0');
    await expect(trafficRow(page, '/obs/frame/0')).toBeVisible();
    await page.locator('#traffic-inspector-search').fill('');
    await expect(page.getByTestId('frame-limit-banner')).toHaveCount(0);
    // The Dashboard shows log messages, and says it is they that were cut, not large bodies.
    await gotoView(page, 'dashboard');
    const banner = page.getByTestId('frame-limit-banner');
    await expect(banner).toContainText('Older log messages are not shown');
    await expect(banner).not.toContainText('large bodies');
  });

  test('E2E-OBS-2: the Traffic header shows no count of the capped live window', async ({ page, request }) => {
    for (let i = 0; i < 120; i++) await request.get(`${ORIGIN}/obs/window/${i}`);
    await gotoView(page, 'traffic');
    await expect(trafficRow(page, '/obs/window/119')).toBeVisible();
    const chips = await page.getByTestId('traffic-toolbar').locator('.MuiChip-root').allInnerTexts();
    // Either no count (like the other panels) or the true total — never the window size.
    expect(chips).not.toContain('100');
  });

  test('the Dashboard log shows a shortened body notice as its own block, not inside the sentence', async ({ page, request }) => {
    const tag = `obs-log-${stamp()}`;
    await request.post(`${ORIGIN}/obs/log-big`, { data: bigJsonBody(tag), headers: { 'content-type': 'application/json' } });
    await gotoView(page, 'dashboard');
    const log = panel(page, 'Log Messages');
    await expect(log).toContainText('NO_MATCH_RESPONSE');
    const row = await expandLogRow(log, 'NO_MATCH_RESPONSE');
    // Opening an entry stops that panel following.
    await expect(log.locator('.MuiChip-root').filter({ hasText: /^Follow(ing)?$/ })).toHaveText('Follow');
    const notice = row.getByTestId('truncated-body-notice').first();
    await expect(notice).toBeVisible();
    await expect(notice).toContainText('Body shortened: showing the first 64 KiB of 70 KiB.');
    // Its own block: not nested in the inline JSON argument box, and it spans a
    // line of its own (nothing from the sentence sits beside it).
    const inInlineBox = await notice.evaluate((el) => {
      for (let p = el.parentElement; p && !p.matches('[data-vrow]'); p = p.parentElement) {
        if (getComputedStyle(p).display === 'inline-block') return true;
      }
      return false;
    });
    expect(inInlineBox, 'notice is not inside the inline JSON argument').toBe(false);
    expect(await notice.evaluate((el) => getComputedStyle(el).display)).not.toMatch(/^inline/);
    const sentence = row.getByText('no expectation for:').first();
    const s = (await sentence.boundingBox())!;
    const n = (await notice.boundingBox())!;
    expect(n.y, 'notice sits on a line below the message text').toBeGreaterThanOrEqual(s.y + s.height - 1);

    const notices = await row.getByTestId('truncated-body-notice').count();
    await notice.getByRole('button', { name: 'Load Full Body' }).click();
    await expect(row.getByTestId('truncated-body-notice')).toHaveCount(notices - 1);
  });
});

// ---------------------------------------------------------------------------
// Traffic: list + detail, and bodies over 64 KiB
// ---------------------------------------------------------------------------

test.describe('Traffic (OBS)', () => {
  test('list + detail: Request, Response and Raw JSON tabs show the exchange', async ({ page, request }) => {
    const path = `/obs/detail-${stamp()}`;
    await putExpectations(request, [{ httpRequest: { path }, httpResponse: { statusCode: 201, body: 'detail-response-body' } }]);
    expect((await request.get(`${ORIGIN}${path}?q=1`, { headers: { 'x-obs': 'yes' } })).status()).toBe(201);

    await gotoView(page, 'traffic');
    await openTrafficRow(page, path);
    const tabs = page.getByRole('tab');
    await expect(tabs).toHaveText(['Request', 'Response', 'Raw JSON'], { ignoreCase: true });

    await expect(page.getByRole('tab', { name: 'Request' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('x-obs', { exact: true })).toBeVisible();
    await page.getByRole('tab', { name: 'Response' }).click();
    await expect(page.getByText('detail-response-body')).toBeVisible();
    await page.getByRole('tab', { name: 'Raw JSON' }).click();
    await expect(page.getByText('"statusCode"')).toBeVisible();
    await expect(page.getByText('"detail-response-body"')).toBeVisible();
  });

  test('a body over 64 KiB is shortened with a notice, and Load Full Body shows the rest', async ({ page, request }) => {
    const tag = `obs-big-${stamp()}`;
    await request.post(`${ORIGIN}/obs/big-${tag}`, { data: bigJsonBody(tag), headers: { 'content-type': 'application/json' } });
    await gotoView(page, 'traffic');
    await openTrafficRow(page, `/obs/big-${tag}`);
    const notice = page.getByTestId('truncated-body-notice');
    await expect(notice).toHaveText(/Body shortened: showing the first 64 KiB of 70 KiB\./);
    // The shortened prefix ends inside the filler, so the body is shown as text and
    // its last field is absent; loaded whole it parses, and the "tail" field appears.
    await expect(page.getByText('"tail"', { exact: true })).toHaveCount(0);
    await notice.getByRole('button', { name: 'Load Full Body' }).click();
    await expect(notice).toHaveCount(0);
    await expect(page.getByText('"tail"', { exact: true })).toBeVisible();
  });

  test('Why Didn\'t This Match? analyses the WHOLE shortened request, so a matcher on text past 64 KiB matches', async ({ page, request }) => {
    const tag = `obs-why-${stamp()}`;
    const path = `/obs/why-${tag}`;
    expect((await request.post(`${ORIGIN}${path}`, { data: bigJsonBody(tag), headers: { 'content-type': 'application/json' } })).status()).toBe(404);
    // Registered AFTER the request: it matches only on a field past the 64 KiB cut.
    await putExpectations(request, [{ id: `${tag}-tail`, httpRequest: { method: 'POST', path, body: { type: 'JSON', json: { tail: `${tag}-tail` } } }, httpResponse: { statusCode: 200 } }]);
    await gotoView(page, 'traffic');
    await openTrafficRow(page, path);
    await page.getByRole('button', { name: "Why Didn't This Match?" }).click();
    const dialog = page.getByRole('dialog').filter({ hasText: "Why Didn't This Match?" });
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('1 expectation');
    await expect(dialog.getByText('matches', { exact: true })).toBeVisible();
    await dialog.getByRole('button', { name: 'Close' }).first().click();
    await expect(dialog).toHaveCount(0);
  });

  test('Generate Stub on a shortened request registers a mock the same request then matches', async ({ page, request }) => {
    const tag = `obs-stub-${stamp()}`;
    const path = `/obs/stub-${tag}`;
    const body = bigJsonBody(tag);
    expect((await request.post(`${ORIGIN}${path}`, { data: body, headers: { 'content-type': 'application/json' } })).status()).toBe(404);
    await gotoView(page, 'traffic');
    await openTrafficRow(page, path);
    await page.getByRole('button', { name: 'Generate Stub' }).click();
    const dialog = page.getByRole('dialog', { name: /Generated Expectation/ });
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Register Now' }).click();
    await expect(dialog.getByText(/registered successfully/)).toBeVisible();
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    const replay = await request.post(`${ORIGIN}${path}`, { data: body, headers: { 'content-type': 'application/json' } });
    expect(replay.status(), 'the generated stub matches the original request').toBe(201);
  });

  test('Capture as mock fills the request body with the WHOLE body, not the 64 KiB prefix', async ({ page, request }) => {
    const tag = `obs-cap-${stamp()}`;
    const path = `/obs/cap-${tag}`;
    await putExpectations(request, [{ httpRequest: { path }, httpResponse: { statusCode: 200, body: 'captured-response' } }]);
    await request.post(`${ORIGIN}${path}`, { data: bigJsonBody(tag), headers: { 'content-type': 'application/json' } });
    await gotoView(page, 'traffic');
    await openTrafficRow(page, path);
    await page.getByRole('button', { name: 'Capture as mock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Capture as Mock' });
    await expect(dialog).toBeVisible();
    const reqBody = dialog.getByLabel('Request Body');
    await expect(reqBody).toHaveValue(new RegExp(`${tag}-tail`));
    expect((await reqBody.inputValue()).length).toBeGreaterThan(70 * KIB);
    await dialog.getByRole('button', { name: 'Cancel' }).click();
  });

  // The Preview Diff is often the first editor a page opens; it must start
  // Monaco's diff worker itself (the console guard catches a failed worker load).
  test('Capture as mock Preview Diff computes the diff when it is the first editor opened', async ({ page, request }) => {
    const path = `/obs/cap-diff-first-${stamp()}`;
    await request.get(`${ORIGIN}${path}`);
    await gotoView(page, 'traffic');
    await openTrafficRow(page, path);
    await page.getByRole('button', { name: 'Capture as mock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Capture as Mock' });
    await dialog.getByRole('tab', { name: 'Preview Diff' }).click();
    const diff = dialog.getByTestId('json-diff-viewer');
    await expect(diff.locator('.monaco-diff-editor')).toBeVisible();
    await expect(diff.locator('.line-insert').first(), 'the added lines are highlighted').toBeVisible();
    await dialog.getByRole('button', { name: 'Cancel' }).click();
  });

  test('Add to Diff Pool + Diff Selected compares two shortened requests using their whole bodies', async ({ page, request }) => {
    const tag = `obs-diff-${stamp()}`;
    await request.post(`${ORIGIN}/obs/diff-a-${tag}`, { data: bigJsonBody(`${tag}a`), headers: { 'content-type': 'application/json' } });
    await request.post(`${ORIGIN}/obs/diff-b-${tag}`, { data: bigJsonBody(`${tag}b`), headers: { 'content-type': 'application/json' } });
    await gotoView(page, 'traffic');

    for (const which of ['a', 'b']) {
      await openTrafficRow(page, `/obs/diff-${which}-${tag}`);
      await page.getByRole('button', { name: 'Add to Diff Pool' }).click();
      await expect(page.getByRole('button', { name: 'In Diff Pool' })).toBeDisabled();
    }
    const poolChip = page.getByRole('button', { name: 'Diff Pool (2)' });
    await expect(poolChip).toBeVisible();
    await poolChip.click();
    const pool = page.locator('.MuiPopover-paper');
    await expect(pool).toContainText('Diff Pool (2)');
    await pool.getByRole('checkbox').nth(0).check();
    await pool.getByRole('checkbox').nth(1).check();
    await pool.getByRole('button', { name: 'Diff Selected' }).click();

    const dialog = page.getByRole('dialog', { name: 'Diff Two Requests' });
    await expect(dialog).toBeVisible();
    const both = (await dialog.getByLabel('Expected request (JSON)').inputValue()) + (await dialog.getByLabel('Actual request (JSON)').inputValue());
    expect(both).toContain(`${tag}a-tail`);
    expect(both).toContain(`${tag}b-tail`);
    await dialog.getByRole('button', { name: 'Close' }).click();
  });

  test('search warns that shortened bodies are searched only in part, and Search Full Bodies finds text past 64 KiB', async ({ page, request }) => {
    const tag = `obs-search-${stamp()}`;
    await request.post(`${ORIGIN}/obs/search-${tag}`, { data: bigJsonBody(tag), headers: { 'content-type': 'application/json' } });
    await gotoView(page, 'traffic');
    await expect(trafficRow(page, `/obs/search-${tag}`)).toBeVisible();

    await page.locator('#traffic-inspector-search').fill(`${tag}-tail`);
    const note = page.getByRole('alert').filter({ hasText: 'the search covers only the part shown' });
    await expect(note).toContainText('1 request has a body longer than shown');
    await expect(trafficRow(page, `/obs/search-${tag}`)).toHaveCount(0);
    await note.getByRole('button', { name: 'Search Full Bodies' }).click();
    await expect(trafficRow(page, `/obs/search-${tag}`)).toBeVisible();
    await expect(note).toHaveCount(0);
  });

  test('keyboard: the Follow control and the search are reachable with Tab and work with Enter', async ({ page, request }) => {
    await request.get(`${ORIGIN}/obs/kbd-follow`);
    await gotoView(page, 'traffic');
    await expect(trafficRow(page, '/obs/kbd-follow')).toBeVisible();
    const follow = page.getByTestId('traffic-toolbar').locator('.MuiChip-root').filter({ hasText: /^Follow(ing)?$/ });
    let focused = false;
    for (let i = 0; i < 40 && !focused; i++) {
      await page.keyboard.press('Tab');
      focused = await follow.evaluate((el) => el === document.activeElement);
    }
    expect(focused, 'Follow is reachable with Tab').toBe(true);
    await page.keyboard.press('Enter');
    await expect(follow).toHaveText('Follow');
    await page.keyboard.press('Enter');
    await expect(follow).toHaveText('Following');
    // The next stops include the search field; type into it from the keyboard.
    for (let i = 0; i < 5; i++) {
      await page.keyboard.press('Tab');
      if (await page.locator('#traffic-inspector-search').evaluate((el) => el === document.activeElement)) break;
    }
    await page.keyboard.type('kbd-follow');
    await expect(trafficRow(page, '/obs/kbd-follow')).toBeVisible();
    await page.keyboard.type('-nothing');
    await expect(trafficRow(page, '/obs/kbd-follow')).toHaveCount(0);
  });

  test('E2E-OBS-5: the Request Filter header is reachable with Tab and opens with Enter', async ({ page }) => {
    await gotoView(page, 'traffic');
    let reached = false;
    for (let i = 0; i < 40 && !reached; i++) {
      await page.keyboard.press('Tab');
      reached = await page.evaluate(() => (document.activeElement?.textContent ?? '').trim() === 'Request Filter');
    }
    expect(reached, 'the Request Filter toggle takes focus').toBe(true);
    const header = page.getByRole('button', { name: 'Request Filter' });
    await expect(header).toHaveAttribute('aria-expanded', 'false');
    await page.keyboard.press('Enter');
    await expect(header).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByLabel('Enabled')).toBeVisible();
  });

  test('E2E-OBS-3: a Traffic row can be opened from the keyboard, and the arrow keys move between rows', async ({ page, request }) => {
    await request.get(`${ORIGIN}/obs/kbd-older`);
    await request.get(`${ORIGIN}/obs/kbd-newer`);
    await gotoView(page, 'traffic');
    await expect(trafficRow(page, '/obs/kbd-newer')).toBeVisible();
    await expect(trafficRow(page, '/obs/kbd-older')).toBeVisible();
    // Tab through the page until a traffic row has focus, then open it with Enter.
    let reached = false;
    for (let i = 0; i < 60 && !reached; i++) {
      await page.keyboard.press('Tab');
      reached = await page.evaluate(() => !!document.activeElement?.closest('[data-vrow]'));
    }
    expect(reached, 'a traffic row is reachable with Tab').toBe(true);
    // The tab stop is the newest row; ArrowUp moves to the one before it.
    await expect(page.locator(':focus')).toContainText('/obs/kbd-newer');
    await page.keyboard.press('ArrowUp');
    await expect(page.locator(':focus')).toContainText('/obs/kbd-older');
    await page.keyboard.press('Enter');
    await expect(page.getByRole('tab', { name: 'Request' })).toBeVisible();
    await expect(page.locator('[role="button"][aria-pressed="true"]')).toContainText('/obs/kbd-older');
  });

  test('E2E-OBS-4: a row path tooltip cannot take the pointer from the row below', async ({ page, request }) => {
    await request.get(`${ORIGIN}/obs/tip-upper`);
    await request.get(`${ORIGIN}/obs/tip-lower`);
    await gotoView(page, 'traffic');
    await trafficRow(page, '/obs/tip-upper').getByTestId('traffic-row-path').hover();
    const tooltip = page.getByRole('tooltip').filter({ hasText: '/obs/tip-upper' });
    await expect(tooltip).toBeVisible();
    await expect(page.locator('.MuiTooltip-popper').filter({ hasText: '/obs/tip-upper' })).toHaveCSS('pointer-events', 'none');
    // Clicked without parking the pointer first: the click must reach the row.
    await trafficRow(page, '/obs/tip-lower').getByTestId('traffic-row-path').click({ timeout: 5_000 });
    await expect(page.locator('[role="button"][aria-pressed="true"]')).toContainText('/obs/tip-lower');
  });

  test('E2E-OBS-6: a server without metrics is never asked for them', async ({ page, request }) => {
    const config = await (await request.get(`${ORIGIN}/mockserver/configuration`)).json();
    expect(config.metricsEnabled, 'this test needs a server with metrics off (the default)').toBe(false);
    const metricsRequests: string[] = [];
    page.on('request', (r) => { if (r.url().includes('/mockserver/metrics')) metricsRequests.push(r.url()); });
    await request.get(`${ORIGIN}/obs/no-metrics`);
    await gotoView(page, 'dashboard');
    await expect(panel(page, 'Received Requests')).toContainText('/obs/no-metrics');
    await gotoView(page, 'traffic');
    await expect(trafficRow(page, '/obs/no-metrics')).toBeVisible();
    expect(metricsRequests).toEqual([]);
  });

  test('E2E-OBS-7: at 1600 px the Raw JSON tab of an unmatched request is shown whole', async ({ page, request }) => {
    expect((await request.get(`${ORIGIN}/obs/unmatched-tabs-${stamp()}`)).status()).toBe(404);
    await gotoView(page, 'traffic');
    await openTrafficRow(page, '/obs/unmatched-tabs-');
    await expect(page.getByRole('button', { name: /Why Didn.t This Match/i })).toBeVisible();
    const tab = page.getByRole('tab', { name: 'Raw JSON' });
    await expect(tab).toBeVisible();
    // Whole: the tab ends inside the visible part of the tab strip, which needs no scrolling.
    await expect.poll(() => tab.evaluate((el) => {
      const scroller = el.closest('.MuiTabs-scroller') as HTMLElement;
      const t = el.getBoundingClientRect();
      const v = scroller.getBoundingClientRect();
      return t.right <= v.right + 0.5 && scroller.scrollWidth <= scroller.clientWidth;
    })).toBe(true);
  });

  test('E2E-OBS-9: above INFO, Traffic explains why an unmatched request has no status', async ({ page, request }) => {
    expect((await request.put(`${ORIGIN}/mockserver/configuration`, { data: { logLevel: 'WARN' } })).ok()).toBeTruthy();
    try {
      expect((await request.get(`${ORIGIN}/obs/warn-unmatched`)).status()).toBe(404);
      await gotoView(page, 'traffic');
      await expect(trafficRow(page, '/obs/warn-unmatched')).toBeVisible();
      const hint = page.getByTestId('traffic-log-level-hint');
      await expect(hint).toContainText('MockServer is logging at WARN');
      await hint.getByRole('button', { name: /close/i }).click();
      await expect(hint).toHaveCount(0);
    } finally {
      await request.put(`${ORIGIN}/mockserver/configuration`, { data: { logLevel: 'INFO' } });
    }
  });
});

// ---------------------------------------------------------------------------
// GraphQL badge
// ---------------------------------------------------------------------------

test.describe('GraphQL badge (OBS)', () => {
  test('a real GraphQL POST is badged; plain {"data":…} JSON and a shortened non-GraphQL body are not', async ({ page, request }) => {
    const tag = stamp();
    await request.post(`${ORIGIN}/obs/gql-real-${tag}`, { data: { query: 'query GetUser { user { id } }' } });
    await request.post(`${ORIGIN}/obs/gql-data-${tag}`, { data: { data: { user: { id: 1, name: 'query' } } } });
    await request.post(`${ORIGIN}/obs/gql-big-${tag}`, {
      data: JSON.stringify({ data: { note: 'query mutation subscription', filler: 'q'.repeat(70 * KIB) } }),
      headers: { 'content-type': 'application/json' },
    });

    await gotoView(page, 'traffic');
    await expect(trafficRow(page, `/obs/gql-real-${tag}`)).toContainText('GQL GetUser');
    await expect(trafficRow(page, `/obs/gql-data-${tag}`)).toBeVisible();
    await expect(trafficRow(page, `/obs/gql-data-${tag}`)).not.toContainText('GQL');
    await expect(trafficRow(page, `/obs/gql-big-${tag}`)).toBeVisible();
    await expect(trafficRow(page, `/obs/gql-big-${tag}`)).not.toContainText('GQL');

    // The Dashboard log uses the same badge.
    await gotoView(page, 'dashboard');
    const log = panel(page, 'Log Messages');
    const groups = log.locator('[data-vrow]').filter({ hasText: 'NO_MATCH_RESPONSE' });
    await expect(groups).toHaveCount(3);
    for (let i = 0; i < 3; i++) await groups.nth(i).getByRole('button', { name: 'Expand', exact: true }).first().click();
    await expect(log.getByText('GQL GetUser').first()).toBeVisible();
    // Exactly one of the three requests (the GraphQL one) is badged, and only as GetUser.
    const perGroup: number[] = [];
    for (let i = 0; i < 3; i++) perGroup.push(await groups.nth(i).getByText(/^GQL /).count());
    expect(perGroup.filter((n) => n > 0), `GQL badges per request group: ${perGroup}`).toHaveLength(1);
    expect(await log.getByText(/^GQL /).allInnerTexts()).toEqual(expect.arrayContaining(['GQL GetUser']));
    expect(new Set(await log.getByText(/^GQL /).allInnerTexts())).toEqual(new Set(['GQL GetUser']));
  });
});

// ---------------------------------------------------------------------------
// LLM rows in Traffic and Trace
// ---------------------------------------------------------------------------

test.describe('LLM traffic (OBS)', () => {
  test('a 70 KB OpenAI chat request auto-loads in Traffic and in Trace', async ({ page, request }) => {
    const tag = `obs-llm70-${stamp()}`;
    await putExpectations(request, [OPENAI_EXPECTATION(`${tag}-exp`)]);
    const res = await request.post(`${ORIGIN}/v1/chat/completions`, {
      data: chatBody(`${tag}-head ${'a'.repeat(70 * KIB)} ${tag}-tail`),
      headers: { 'content-type': 'application/json' },
    });
    expect(res.status()).toBe(200);

    await gotoView(page, 'traffic');
    await openTrafficRow(page, '/v1/chat/completions');
    await expect(page.getByRole('tab', { name: 'Messages' })).toBeVisible();
    // Parsed from the whole body: a 64 KiB prefix is not valid JSON and yields no messages.
    await expect(page.getByText('Messages (2)')).toBeVisible();
    await expect(page.getByTestId('truncated-body-notice')).toHaveCount(0);

    await gotoView(page, 'sessions');
    await page.getByText('[0] POST /v1/chat/completions').first().click();
    await expect(page.getByText('mocked completion').first()).toBeVisible();
    await expect(page.getByTestId('trace-request-shortened')).toHaveCount(0);
    await expect(page.getByText(new RegExp(`${tag}-head`)).first()).toBeVisible();
  });

  test('a chat request over 4 Mi characters is NOT auto-loaded: Traffic and Trace both show the shortened notice', async ({ page, request }) => {
    const tag = `obs-llmhuge-${stamp()}`;
    await putExpectations(request, [OPENAI_EXPECTATION(`${tag}-exp`)]);
    const loads: string[] = [];
    page.on('request', (r) => { if (r.url().includes('/mockserver/logEntryBody')) loads.push(r.url()); });
    const res = await request.post(`${ORIGIN}/v1/chat/completions`, {
      data: chatBody(`${tag}-head ${'b'.repeat(4400 * KIB)} ${tag}-tail`),
      headers: { 'content-type': 'application/json' },
    });
    expect(res.status()).toBe(200);

    await gotoView(page, 'traffic');
    await openTrafficRow(page, '/v1/chat/completions');
    const notice = page.getByTestId('truncated-body-notice');
    await expect(notice).toContainText(/Body shortened: showing the first 64 KiB of 4\.\d MiB\./);
    await expect(notice.getByRole('button', { name: 'Load Full Body' })).toBeVisible();

    await gotoView(page, 'sessions');
    await page.getByText('[0] POST /v1/chat/completions').first().click();
    const shortened = page.getByTestId('trace-request-shortened');
    await expect(shortened).toBeVisible();
    await expect(shortened.getByTestId('truncated-body-notice')).toContainText('Body shortened');
    await expect(shortened).toContainText('The conversation is shown once the full body is loaded.');
    expect(loads, 'nothing auto-loaded the 4 Mi-character body').toEqual([]);
  });

  test('LLM Optimise analyses captured chat traffic', async ({ page, request }) => {
    await putExpectations(request, [OPENAI_EXPECTATION(`obs-opt-${stamp()}`)]);
    for (let i = 0; i < 2; i++) {
      await request.post(`${ORIGIN}/v1/chat/completions`, { data: chatBody(`turn ${i}`), headers: { 'content-type': 'application/json' } });
    }
    await gotoView(page, 'optimise');
    await expect(page.getByText('LLM Optimise', { exact: true })).toBeVisible();
    const hero = page.getByTestId('optimise-hero');
    await expect(hero).toBeVisible();
    await expect(hero).toContainText('Calls');
    await expect(hero).toContainText('2');
    await expect(page.getByTestId('optimise-verdict').first()).toBeVisible();
    await expect(page.getByTestId('optimise-call-table')).toBeVisible();
    await page.getByRole('button', { name: 'Refresh report' }).click();
    await expect(hero).toContainText('2');
  });

  test('MCP Health lists each MCP server and flags one that errors', async ({ page, request }) => {
    await gotoView(page, 'mcp-health');
    await expect(page.getByTestId('mcp-health-empty')).toBeVisible();

    await putExpectations(request, [
      { httpRequest: { method: 'POST', path: '/mcp', body: { type: 'JSON', json: { method: 'tools/call' } } }, httpResponse: { statusCode: 200, body: { jsonrpc: '2.0', id: 2, error: { code: -32000, message: 'boom' } } } },
      { httpRequest: { method: 'POST', path: '/mcp' }, httpResponse: { statusCode: 200, body: { jsonrpc: '2.0', id: 1, result: { tools: [] } } } },
    ]);
    await request.post(`${ORIGIN}/mcp`, { data: { jsonrpc: '2.0', id: 1, method: 'tools/list' } });
    await request.post(`${ORIGIN}/mcp`, { data: { jsonrpc: '2.0', id: 2, method: 'tools/call', params: { name: 'x' } } });

    const table = page.getByTestId('mcp-health-table');
    await expect(table).toBeVisible();
    const row = table.locator('tbody tr').first();
    await expect(row).toContainText(`${HOST}:${PORT}`);
    await expect(row.getByText('errors', { exact: true })).toBeVisible();
    // Server, Errors, … (the Calls column was removed: E2E-OBS-8).
    await expect(row.locator('td').nth(1)).toHaveText('50%');
  });

  test('the LLM Provider filter appears with an httpLlmResponse expectation and filters Active Expectations by provider', async ({ page, request }) => {
    const tag = stamp();
    await putExpectations(request, [
      OPENAI_EXPECTATION(`obs-prov-openai-${tag}`),
      { id: `obs-prov-anthropic-${tag}`, httpRequest: { method: 'POST', path: '/v1/messages' }, httpLlmResponse: { provider: 'ANTHROPIC', model: 'claude-test', completion: { text: 'hi', stopReason: 'end_turn' } } },
      { id: `obs-prov-plain-${tag}`, httpRequest: { path: '/obs/plain' }, httpResponse: { statusCode: 200 } },
    ]);
    await gotoView(page, 'dashboard');
    const expectations = panel(page, 'Active Expectations');
    for (const id of ['openai', 'anthropic', 'plain']) await expect(expectations).toContainText(`obs-prov-${id}-${tag}`);

    await page.getByText('Request Filter', { exact: true }).click();
    await expect(page.getByText('LLM Provider (expectations only)')).toBeVisible();
    await page.getByRole('button', { name: 'OpenAI', exact: true }).click();

    await expect(expectations).toContainText(`obs-prov-openai-${tag}`);
    await expect(expectations).not.toContainText(`obs-prov-anthropic-${tag}`);
    await expect(expectations).not.toContainText(`obs-prov-plain-${tag}`);

    await page.getByRole('button', { name: 'Anthropic', exact: true }).click();
    await expect(expectations).toContainText(`obs-prov-anthropic-${tag}`);
    await page.getByRole('button', { name: 'OpenAI', exact: true }).click();
    await expect(expectations).not.toContainText(`obs-prov-openai-${tag}`);
    await expect(expectations).toContainText(`obs-prov-anthropic-${tag}`);
  });
});

// ---------------------------------------------------------------------------
// Narrow Traffic pane + light theme
// ---------------------------------------------------------------------------

test.describe('Narrow Traffic pane (OBS)', () => {
  test.use({ viewport: { width: 1024, height: 700 } });

  test('in light theme at 1024x700 with a request open: toolbar reachable, Diff Pool does not scroll the list, rows single-line', async ({ page, request }) => {
    const tag = stamp();
    await putExpectations(request, [OPENAI_EXPECTATION(`obs-narrow-${tag}`)]);
    await request.post(`${ORIGIN}/v1/chat/completions`, { data: chatBody('narrow'), headers: { 'content-type': 'application/json' } });
    for (let i = 0; i < 4; i++) await request.get(`${ORIGIN}/obs/narrow/a-rather-long-path-segment-${i}/${tag}`);

    await page.addInitScript(() => localStorage.setItem('mockserver-theme', 'light'));
    await gotoView(page, 'traffic');
    await expect(page.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible();

    await openTrafficRow(page, `/obs/narrow/a-rather-long-path-segment-0/${tag}`);
    await page.getByRole('button', { name: 'Add to Diff Pool' }).click();

    const toolbar = page.getByTestId('traffic-toolbar');
    const tb = (await toolbar.boundingBox())!;
    const controls = [
      toolbar.locator('.MuiChip-root').filter({ hasText: /^Follow(ing)?$/ }),
      toolbar.locator('#traffic-inspector-search'),
      toolbar.getByRole('button', { name: 'Compare requests' }),
      toolbar.getByRole('button', { name: 'Diff Pool (1)' }),
      toolbar.getByRole('button', { name: 'Select requests' }),
      toolbar.getByRole('button', { name: /Mocks/ }),
    ];
    for (const c of controls) {
      await expect(c).toBeVisible();
      const b = (await c.boundingBox())!;
      expect(b.x, 'control starts inside the toolbar').toBeGreaterThanOrEqual(tb.x - 1);
      expect(b.x + b.width, 'control ends inside the toolbar').toBeLessThanOrEqual(tb.x + tb.width + 1);
    }

    const scroller = page.getByTestId('traffic-scroll-region');
    await toolbar.getByRole('button', { name: 'Diff Pool (1)' }).click();
    await expect(page.locator('.MuiPopover-paper')).toContainText('Diff Pool (1)');
    const pane = scroller.locator('xpath=..');
    expect(await pane.evaluate((el) => el.scrollLeft), 'list pane not scrolled sideways').toBe(0);
    expect(await scroller.evaluate((el) => el.scrollLeft)).toBe(0);
    await page.keyboard.press('Escape');

    // Each row on one line: every child shares the row's single line box.
    const rows = page.locator('[data-vrow]');
    const n = await rows.count();
    expect(n).toBeGreaterThanOrEqual(5);
    for (let i = 0; i < n; i++) {
      const lines = await rows.nth(i).evaluate((vrow) => {
        const row = vrow.firstElementChild as HTMLElement;
        const tops = Array.from(row.children).map((c) => Math.round((c as HTMLElement).getBoundingClientRect().top + (c as HTMLElement).getBoundingClientRect().height / 2));
        return Math.max(...tops) - Math.min(...tops);
      });
      expect(lines, `row ${i} is a single line`).toBeLessThan(10);
    }
    const first = rows.first();
    const time = (await first.boundingBox())!;
    const paneBox = (await pane.boundingBox())!;
    expect(time.x, 'rows start at the pane edge (time column visible)').toBeGreaterThanOrEqual(paneBox.x - 1);
  });
});

// ---------------------------------------------------------------------------
// Log pressure banner + incomplete-log verification (second server)
// ---------------------------------------------------------------------------

function findJar(): string | null {
  if (process.env.E2E_MS_JAR && existsSync(process.env.E2E_MS_JAR)) return process.env.E2E_MS_JAR;
  const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
  const dirs: Array<[string, RegExp]> = [
    [join(repoRoot, 'mockserver', 'mockserver-netty-no-dependencies', 'target'), /^mockserver-netty-no-dependencies-.*\.jar$/],
    [join(repoRoot, 'mockserver', 'mockserver-netty', 'target'), /^mockserver-netty-.*-jar-with-dependencies\.jar$/],
  ];
  for (const [dir, pattern] of dirs) {
    if (!existsSync(dir)) continue;
    const jars = readdirSync(dir)
      .filter((f) => pattern.test(f) && !/sources|javadoc|^original-/.test(f))
      .map((f) => join(dir, f))
      .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs);
    if (jars.length > 0) return jars[0]!;
  }
  return null;
}

test.describe('Log pressure (OBS)', () => {
  let child: ChildProcess | null = null;
  let pressureOrigin = process.env.E2E_MS_PRESSURE_ORIGIN ?? '';

  test.beforeAll(async ({ playwright }) => {
    test.setTimeout(120_000);
    if (pressureOrigin) return;
    const jar = findJar();
    requireOrSkip(jar !== null, 'no runnable MockServer jar to boot the log-pressure server, and E2E_MS_PRESSURE_ORIGIN is unset');
    pressureOrigin = `http://127.0.0.1:${PRESSURE_PORT}`;
    child = spawn(
      process.env.E2E_JAVA || 'java',
      ['-Xmx256m', '-Dmockserver.metricsEnabled=true', '-Dmockserver.maxLogEntries=50', '-jar', jar!, '-serverPort', PRESSURE_PORT, '-logLevel', 'INFO'],
      { stdio: 'ignore', cwd: mkdtempSync(join(tmpdir(), 'mockserver-e2e-obs-')) },
    );
    const api = await playwright.request.newContext();
    try {
      await expect
        .poll(async () => (await api.put(`${pressureOrigin}/mockserver/status`).catch(() => null))?.status() ?? 0, { timeout: 90_000 })
        .toBe(200);
    } finally {
      await api.dispose();
    }
  });

  test.afterAll(() => {
    child?.kill('SIGTERM');
    child = null;
  });

  test('evicted log entries raise the log-pressure banner, and an atMost verification says the log was incomplete', async ({ page, request }) => {
    expect((await request.put(`${pressureOrigin}/mockserver/reset`)).ok()).toBeTruthy();
    const path = `/obs/pressure-${stamp()}`;
    for (let i = 0; i < 40; i++) await request.get(`${pressureOrigin}${path}/${i}`);

    const verify = await request.put(`${pressureOrigin}/mockserver/verify`, {
      data: { httpRequest: { path: `${path}/0` }, times: { atMost: 0 } },
    });
    expect(verify.status()).toBe(406);
    const reason = await verify.text();
    expect(reason).toMatch(/evicted/i);
    expect(reason).toContain('maxLogEntries');

    await gotoView(page, 'dashboard', pressureOrigin);
    const banner = page.getByTestId('log-pressure-banner');
    await expect(banner).toBeVisible({ timeout: 30_000 });
    await expect(banner).toContainText('Log Events Evicted');
    await expect(banner.getByTestId('log-pressure-evicted')).toContainText('maxLogEntries');

    // The failed verification's log entry explains the loss inline.
    const log = panel(page, 'Log Messages');
    await expect(log.getByText('VERIFICATION_FAILED').first()).toBeVisible();
    const entry = await expandLogRow(log, 'VERIFICATION_FAILED');
    const loss = entry.getByTestId('event-log-loss').first();
    await expect(loss).toBeVisible();
    await expect(loss).toContainText('Absence cannot be proven');
    await expect(loss.getByTestId('event-log-loss-max-log-entries')).toContainText('maxLogEntries');

    // Dismissing hides the banner until more is lost.
    await banner.getByRole('button', { name: 'Close' }).click();
    await expect(banner).toHaveCount(0);
  });
});
