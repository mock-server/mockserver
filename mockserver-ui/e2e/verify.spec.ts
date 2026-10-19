import { test, expect, type APIRequestContext, type Page, type Locator } from '@playwright/test';
import { requireOrSkip } from './ci-guard';

// Real-browser e2e coverage of the dashboard's Verify group — Verify (request
// verification), Contract (OpenAPI contract test / recorded-traffic validation),
// SLO and Drift — against a REAL MockServer over real REST.
//
// Two servers are involved:
//   • the server under test (E2E_MS_PORT) — serves the dashboard and holds state;
//   • an "upstream" standing in for the real API (E2E_UPSTREAM_PORT, default
//     1114) that forwarded traffic, the contract test and drift run against.
// Every upstream path is distinct from the paths the server under test is called
// on (forwards rewrite the path), so E2E_UPSTREAM_PORT may point at the SAME
// server when a second JVM is not available — the suite then still works.
//
// The SLO view needs the server under test started with
// -Dmockserver.sloTrackingEnabled=true.

const HOST = process.env.E2E_MS_HOST || '127.0.0.1';
const PORT = process.env.E2E_MS_PORT || '1084';
const ORIGIN = `http://${HOST}:${PORT}`;
const UPSTREAM_HOST = process.env.E2E_UPSTREAM_HOST || HOST;
const UPSTREAM_PORT = process.env.E2E_UPSTREAM_PORT || '1114';
const UPSTREAM = `http://${UPSTREAM_HOST}:${UPSTREAM_PORT}`;
const UPSTREAM_IS_SEPARATE = UPSTREAM !== ORIGIN;

const SUCCESS_TEXT = 'Verified — MockServer saw matching traffic the expected number of times.';

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

async function reset(request: APIRequestContext): Promise<void> {
  const res = await request.put(`${ORIGIN}/mockserver/reset`);
  expect(res.ok(), `reset returned ${res.status()}`).toBeTruthy();
  if (UPSTREAM_IS_SEPARATE) {
    const up = await request.put(`${UPSTREAM}/mockserver/reset`);
    expect(up.ok(), `upstream reset returned ${up.status()} — is the upstream MockServer running on ${UPSTREAM}?`).toBeTruthy();
  }
}

async function putExpectations(request: APIRequestContext, base: string, expectations: unknown[]): Promise<void> {
  const res = await request.put(`${base}/mockserver/expectation`, { data: expectations });
  expect(res.status(), `expectation PUT to ${base} returned ${res.status()}: ${await res.text()}`).toBe(201);
}

/**
 * Registers `upstreamPath` on the upstream (serving `upstreamResponse`) and, on
 * the server under test, a forward of `path` to it that rewrites the path (so the
 * upstream may be the same server without looping). Optional `stub` adds a
 * lower-priority response expectation for `path` — the shape drift is compared to.
 */
async function forwardToUpstream(
  request: APIRequestContext,
  opts: { path: string; upstreamPath: string; upstreamResponse: Record<string, unknown>; stub?: Record<string, unknown> },
): Promise<void> {
  await putExpectations(request, UPSTREAM, [{ httpRequest: { path: opts.upstreamPath }, httpResponse: opts.upstreamResponse }]);
  const ours: unknown[] = [
    {
      id: `forward-${opts.path}`,
      priority: 10,
      httpRequest: { path: opts.path },
      httpOverrideForwardedRequest: {
        requestOverride: { path: opts.upstreamPath, headers: { Host: [`${UPSTREAM_HOST}:${UPSTREAM_PORT}`] } },
      },
    },
  ];
  if (opts.stub) ours.push(opts.stub);
  await putExpectations(request, ORIGIN, ours);
}

/** Browser console errors / uncaught exceptions, minus the ones a test declares expected. */
function watchConsole(page: Page): { errors: string[]; allow: (re: RegExp) => void } {
  const errors: string[] = [];
  // Matched against "<message> @ <source url>".
  const allowed: RegExp[] = [];
  page.on('console', (m) => {
    if (m.type() !== 'error') return;
    const text = `${m.text()} @ ${m.location().url}`;
    if (!allowed.some((re) => re.test(text))) errors.push(`console.error: ${text}`);
  });
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
  return { errors, allow: (re) => allowed.push(re) };
}

// The browser itself logs "Failed to load resource: … status of 406" for every
// 4xx fetch. A failed verification is a 406 BY DESIGN, so tests that expect one
// allow exactly that line — anything else still fails the test.
const FAILED_VERIFY_406 = /Failed to load resource: the server responded with a status of 406 .* @ .*\/mockserver\/verify(Sequence)?$/;

let consoleWatch: ReturnType<typeof watchConsole>;

test.beforeEach(async ({ page, request }) => {
  await reset(request);
  consoleWatch = watchConsole(page);
});

test.afterEach(async () => {
  expect(consoleWatch.errors, 'browser console errors / uncaught exceptions').toEqual([]);
});

async function openVerification(page: Page): Promise<void> {
  await page.goto('./#/verification');
  await expect(page.getByText('connected', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Verification', exact: true })).toBeVisible();
}

function timesModeSelect(page: Page): Locator {
  return page.getByRole('combobox', { name: 'Times mode' });
}

function methodSelect(page: Page): Locator {
  return page.getByRole('combobox', { name: 'Method' });
}

async function chooseOption(page: Page, combobox: Locator, option: string): Promise<void> {
  await combobox.click();
  await page.getByRole('option', { name: option, exact: true }).click();
  await expect(page.getByRole('listbox')).toHaveCount(0);
}

async function setTimes(page: Page, mode: string, count: number, max?: number): Promise<void> {
  await chooseOption(page, timesModeSelect(page), mode);
  await page.getByRole('spinbutton', { name: mode === 'between' ? 'min' : 'times' }).fill(String(count));
  if (max !== undefined) await page.getByRole('spinbutton', { name: 'max' }).fill(String(max));
}

function resultAlert(page: Page): Locator {
  return page.getByRole('alert').filter({ hasText: /Verified —|Verification failed/ });
}

async function clickVerifyAndExpect(page: Page, button: string, outcome: 'pass' | RegExp): Promise<void> {
  await page.getByRole('button', { name: button, exact: true }).click();
  const alert = resultAlert(page);
  if (outcome === 'pass') {
    await expect(alert).toHaveText(SUCCESS_TEXT);
  } else {
    await expect(alert).toContainText('Verification failed');
    await expect(alert).toContainText(outcome);
  }
}

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

test('the Verify menu lists Verify, Contract, SLO and Drift and opens each view', async ({ page }) => {
  await page.goto('./#/dashboard');
  await expect(page.getByText('connected', { exact: true })).toBeVisible();

  const views: Array<[string, string]> = [
    ['Verification view', 'Verification'],
    ['Contract test view', 'Contract Test'],
    ['SLO verification view', 'SLO Verification'],
    ['Drift detection view', 'Drift Detection'],
  ];
  for (const [menuItem, heading] of views) {
    await page.getByRole('button', { name: 'Verify views' }).click();
    const menu = page.getByRole('menu');
    await expect(menu.getByRole('menuitem')).toHaveCount(4);
    await menu.getByRole('menuitem', { name: menuItem, exact: true }).click();
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
  }
  await expect(page).toHaveURL(/#\/drift$/);
});

test('keyboard only: opens Verify from the nav menu, types a path and runs a passing verification', async ({ page, request }) => {
  const path = `/e2e/verify/kbd-${Date.now()}`;
  expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(404);

  await page.goto('./#/dashboard');
  await expect(page.getByText('connected', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Verify views' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('menu')).toBeVisible();
  // MUI focuses the first item; the Verification view is the first entry.
  await expect(page.getByRole('menuitem', { name: 'Verification view', exact: true })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Verification', exact: true })).toBeVisible();

  await page.getByRole('textbox', { name: 'Path' }).focus();
  await page.keyboard.type(path);

  // Tab forward to the Verify button (bounded) and activate it with Enter.
  const verifyButton = page.getByRole('button', { name: 'Verify', exact: true });
  for (let i = 0; i < 25 && !(await verifyButton.evaluate((el) => el === document.activeElement)); i++) {
    await page.keyboard.press('Tab');
  }
  await expect(verifyButton).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(resultAlert(page)).toHaveText(SUCCESS_TEXT);
});

// ---------------------------------------------------------------------------
// Single-request verification
// ---------------------------------------------------------------------------

test('single request: at least / at most / exactly / between / never pass and fail against real traffic', async ({ page, request }) => {
  consoleWatch.allow(FAILED_VERIFY_406);
  const path = `/e2e/verify/times-${Date.now()}`;
  for (let i = 0; i < 2; i++) expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(404);

  await openVerification(page);
  await page.getByRole('textbox', { name: 'Path' }).fill(path);

  // The request was received exactly twice.
  const cases: Array<{ mode: string; count: number; max?: number; outcome: 'pass' | RegExp }> = [
    { mode: 'at least', count: 2, outcome: 'pass' },
    { mode: 'at least', count: 3, outcome: /Request not found at least 3 times/ },
    { mode: 'at most', count: 2, outcome: 'pass' },
    // An exceeded upper bound says the request WAS found, too often (E2E-VERIFY-9).
    { mode: 'at most', count: 1, outcome: /Request found 2 times but should have been found at most once/ },
    { mode: 'exactly', count: 2, outcome: 'pass' },
    { mode: 'exactly', count: 1, outcome: /Request found 2 times but should have been found exactly once/ },
    { mode: 'exactly', count: 3, outcome: /Request not found exactly 3 times/ },
    { mode: 'between', count: 1, max: 3, outcome: 'pass' },
    { mode: 'between', count: 3, max: 5, outcome: /Request not found between 3 and 5 times/ },
    // "never" is expressed as exactly 0.
    { mode: 'exactly', count: 0, outcome: /Request found 2 times but should have been found exactly 0 times/ },
  ];
  for (const c of cases) {
    await setTimes(page, c.mode, c.count, c.max);
    await clickVerifyAndExpect(page, 'Verify', c.outcome);
  }

  // "never" passes for a path that was never requested.
  await page.getByRole('textbox', { name: 'Path' }).fill(`${path}-never-sent`);
  await setTimes(page, 'exactly', 0);
  await clickVerifyAndExpect(page, 'Verify', 'pass');

  // The generated code previews the same wire body the panel posts.
  await page.getByRole('tab', { name: 'JSON' }).click();
  const code = page.locator('pre').filter({ hasText: '"times"' });
  await expect(code).toContainText(`"path": "${path}-never-sent"`);
  await expect(code).toContainText('"atLeast": 0');
  await expect(code).toContainText('"atMost": 0');
});

test('a failing verification explains itself: expected matcher, what was received, and the closest-match diff', async ({ page, request }) => {
  consoleWatch.allow(FAILED_VERIFY_406);
  const stamp = Date.now();
  const received = `/e2e/verify/explain-${stamp}/orders`;
  expect((await request.get(`${ORIGIN}${received}`)).status()).toBe(404);

  await openVerification(page);
  await page.getByRole('textbox', { name: 'Path' }).fill(`/e2e/verify/explain-${stamp}/order`);
  await clickVerifyAndExpect(page, 'Verify', /Request not found at least once/);

  const alert = resultAlert(page);
  await expect(alert).toContainText(`"path" : "${received}"`);
  await expect(alert).toContainText('closest match diff');
  await expect(alert).toContainText('string or regex match failed');
});

test('single request: method, header, query and body all constrain the match', async ({ page, request }) => {
  consoleWatch.allow(FAILED_VERIFY_406);
  const path = `/e2e/verify/fields-${Date.now()}`;
  const sent = await request.post(`${ORIGIN}${path}?page=2`, {
    headers: { 'X-Trace': 'abc123', 'Content-Type': 'application/json' },
    data: { order: 'widget', qty: 3 },
  });
  expect(sent.status()).toBe(404);

  await openVerification(page);
  await chooseOption(page, methodSelect(page), 'POST');
  await page.getByRole('textbox', { name: 'Path' }).fill(path);
  await page.getByRole('textbox', { name: 'Headers (Name: value per line)' }).fill('X-Trace: abc123');
  await page.getByRole('textbox', { name: 'Query (key=value per line)' }).fill('page=2');
  await page.getByRole('textbox', { name: 'Body (substring/JSON match)' }).fill('{"order":"widget","qty":3}');
  await setTimes(page, 'exactly', 1);
  await clickVerifyAndExpect(page, 'Verify', 'pass');

  // Each field narrows the match: a wrong header value fails…
  await page.getByRole('textbox', { name: 'Headers (Name: value per line)' }).fill('X-Trace: other');
  await clickVerifyAndExpect(page, 'Verify', /Request not found exactly once/);
  await page.getByRole('textbox', { name: 'Headers (Name: value per line)' }).fill('X-Trace: abc123');

  // …a wrong query value fails…
  await page.getByRole('textbox', { name: 'Query (key=value per line)' }).fill('page=3');
  await clickVerifyAndExpect(page, 'Verify', /Request not found exactly once/);
  await page.getByRole('textbox', { name: 'Query (key=value per line)' }).fill('page=2');

  // …a wrong method fails.
  await chooseOption(page, methodSelect(page), 'GET');
  await clickVerifyAndExpect(page, 'Verify', /Request not found exactly once/);
});

test('quick scope: "method:POST path:/…/*" fills the matcher fields, which then verify', async ({ page, request }) => {
  const base = `/e2e/verify/scope-${Date.now()}`;
  expect((await request.post(`${ORIGIN}${base}/a`, { data: 'x' })).status()).toBe(404);

  await openVerification(page);
  const applyScope = page.getByRole('button', { name: 'Apply scope' });
  await expect(applyScope).toBeDisabled();
  await page.getByRole('textbox', { name: 'Quick scope' }).fill(`method:POST path:${base}/*`);
  await expect(applyScope).toBeEnabled();
  await applyScope.click();

  await expect(methodSelect(page)).toHaveText('POST');
  const pathValue = await page.getByRole('textbox', { name: 'Path' }).inputValue();
  expect(pathValue.startsWith(base), `path filled from the glob, got ${pathValue}`).toBeTruthy();
  await clickVerifyAndExpect(page, 'Verify', 'pass');
});

test('response matcher verifies the responses recorded for forwarded traffic', async ({ page, request }) => {
  consoleWatch.allow(FAILED_VERIFY_406);
  const stamp = Date.now();
  const path = `/e2e/verify/fwd-${stamp}`;
  await forwardToUpstream(request, {
    path,
    upstreamPath: `/upstream/verify/fwd-${stamp}`,
    upstreamResponse: { statusCode: 202, body: 'from-upstream' },
  });
  const fwd = await request.get(`${ORIGIN}${path}`);
  expect(fwd.status(), 'forward reached the upstream').toBe(202);

  await openVerification(page);
  await page.getByRole('textbox', { name: 'Path' }).fill(path);
  await page.getByRole('button', { name: 'Expand Response matcher (optional)' }).first().click();
  await page.getByRole('textbox', { name: 'Status code' }).fill('202');
  await setTimes(page, 'exactly', 1);
  await clickVerifyAndExpect(page, 'Verify', 'pass');

  await page.getByRole('textbox', { name: 'Status code' }).fill('500');
  await clickVerifyAndExpect(page, 'Verify', /not found exactly once/);
});

test('quick scope: "status:>=500" fills the response status range, which the server applies', async ({ page, request }) => {
  consoleWatch.allow(FAILED_VERIFY_406);
  const path = `/e2e/verify/status-${Date.now()}`;
  await putExpectations(request, ORIGIN, [{ httpRequest: { path }, httpResponse: { statusCode: 503 } }]);
  expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(503);

  await openVerification(page);
  const scope = page.getByRole('textbox', { name: 'Quick scope' });
  const applyScope = page.getByRole('button', { name: 'Apply scope' });
  await scope.fill(`path:${path} status:>=500`);
  await applyScope.click();
  await expect(page.getByRole('textbox', { name: 'Status range' })).toHaveValue('>= 500');
  await setTimes(page, 'exactly', 1);
  await clickVerifyAndExpect(page, 'Verify', 'pass');

  await scope.fill('status:>=400 status:<500');
  await applyScope.click();
  await expect(page.getByRole('textbox', { name: 'Status range' })).toHaveValue('4XX');
  await clickVerifyAndExpect(page, 'Verify', /not found exactly once/);

  await scope.fill('status:>=400 status:<450');
  await expect(page.getByText(/needs two comparisons/)).toBeVisible();
  await expect(applyScope).toBeDisabled();
});

// ---------------------------------------------------------------------------
// Sequence verification
// ---------------------------------------------------------------------------

test('ordered sequence: in-order passes, out-of-order fails, steps can be added and removed', async ({ page, request }) => {
  consoleWatch.allow(FAILED_VERIFY_406);
  const base = `/e2e/verify/seq-${Date.now()}`;
  for (const step of ['login', 'cart', 'checkout']) {
    expect((await request.get(`${ORIGIN}${base}/${step}`)).status()).toBe(404);
  }

  await openVerification(page);
  await page.getByRole('button', { name: 'Ordered sequence' }).click();
  const paths = page.getByRole('textbox', { name: 'Path' });
  await expect(paths).toHaveCount(2);

  await paths.nth(0).fill(`${base}/login`);
  await paths.nth(1).fill(`${base}/checkout`);
  await clickVerifyAndExpect(page, 'Verify sequence', 'pass');

  // A third step out of order fails and the failure lists the expected sequence.
  await page.getByRole('button', { name: 'Add step' }).click();
  await expect(paths).toHaveCount(3);
  await paths.nth(2).fill(`${base}/cart`);
  await clickVerifyAndExpect(page, 'Verify sequence', /Request sequence not found/);
  await expect(resultAlert(page)).toContainText(`${base}/cart`);

  // Removing the out-of-order step makes it pass again.
  await page.getByRole('button', { name: 'Remove step' }).nth(2).click();
  await expect(paths).toHaveCount(2);
  await clickVerifyAndExpect(page, 'Verify sequence', 'pass');

  // Switching mode clears the stale result.
  await page.getByRole('button', { name: 'Single request' }).click();
  await expect(resultAlert(page)).toHaveCount(0);
});

test('a verification the browser cannot deliver shows an error, not a pass/fail verdict', async ({ page }) => {
  consoleWatch.allow(/ERR_CONNECTION_REFUSED/);
  await openVerification(page);
  // Simulate the server going away mid-session (the only fault injected in this file).
  await page.route('**/mockserver/verify', (route) => route.abort('connectionrefused'));
  await page.getByRole('textbox', { name: 'Path' }).fill('/e2e/verify/unreachable');
  await page.getByRole('button', { name: 'Verify', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(resultAlert(page)).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Verify', exact: true })).toBeEnabled();
});

// ---------------------------------------------------------------------------
// "Verify This Request" hand-off from a captured flow
// ---------------------------------------------------------------------------

test('"Verify This Request" from a captured flow prefills the Verify view with its method and path', async ({ page, request }) => {
  const path = `/e2e/verify/handoff-${Date.now()}`;
  expect((await request.delete(`${ORIGIN}${path}`)).status()).toBe(404);

  await page.goto('./#/traffic');
  await expect(page.getByText('connected', { exact: true })).toBeVisible();
  await page.getByText(path).first().click();
  await page.getByRole('button', { name: 'Create from this request…' }).click();
  await page.getByRole('menuitem', { name: 'Verify This Request' }).click();

  await expect(page.getByRole('heading', { name: 'Verification', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/#\/verification$/);
  await expect(page.getByRole('textbox', { name: 'Path' })).toHaveValue(path);
  await expect(methodSelect(page)).toHaveText('DELETE');
  await clickVerifyAndExpect(page, 'Verify', 'pass');
});

// ---------------------------------------------------------------------------
// Layout: light theme + narrow window
// ---------------------------------------------------------------------------

test('Verify and Drift views in the light theme at 1024×700: no horizontal scroll, primary action reachable', async ({ page, request }) => {
  await page.addInitScript(() => {
    try { window.localStorage.setItem('mockserver-theme', 'light'); } catch { /* ignore */ }
  });
  await page.setViewportSize({ width: 1024, height: 700 });
  const path = `/e2e/verify/narrow-${Date.now()}`;
  expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(404);

  await openVerification(page);
  await expect(page.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible();
  const bodyBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  expect(bodyBg, 'light theme background').not.toBe('rgb(18, 18, 18)');

  const noHorizontalScroll = () =>
    page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth);
  expect(await noHorizontalScroll(), 'Verify view overflows horizontally at 1024px').toBeTruthy();

  await page.getByRole('textbox', { name: 'Path' }).fill(path);
  const verify = page.getByRole('button', { name: 'Verify', exact: true });
  await verify.scrollIntoViewIfNeeded();
  await expect(verify).toBeInViewport();
  await clickVerifyAndExpect(page, 'Verify', 'pass');
  await page.screenshot({ path: test.info().outputPath('verify-light-1024.png'), fullPage: true });

  await page.goto('./#/drift');
  await expect(page.getByRole('heading', { name: 'Drift Detection' })).toBeVisible();
  expect(await noHorizontalScroll(), 'Drift view overflows horizontally at 1024px').toBeTruthy();
});

// ---------------------------------------------------------------------------
// Contract
// ---------------------------------------------------------------------------

function petsSpec(prefix: string): string {
  const pet = { type: 'object', required: ['id', 'name'], properties: { id: { type: 'integer' }, name: { type: 'string' } } };
  const ok = (schema: unknown) => ({ '200': { description: 'ok', content: { 'application/json': { schema } } } });
  return JSON.stringify({
    openapi: '3.0.0',
    info: { title: 'Pets', version: '1' },
    paths: {
      [`${prefix}/pets`]: { get: { operationId: 'listPets', responses: ok({ type: 'array', items: pet }) } },
      [`${prefix}/pets/1`]: { get: { operationId: 'getPet', responses: ok(pet) } },
    },
  });
}

test('contract: a live contract test against the upstream reports per-operation pass / fail with validation errors', async ({ page, request }) => {
  const prefix = `/upstream/contract-${Date.now()}`;
  const json = { 'Content-Type': ['application/json'] };
  await putExpectations(request, UPSTREAM, [
    { httpRequest: { path: `${prefix}/pets` }, httpResponse: { statusCode: 200, headers: json, body: { type: 'JSON', json: [{ id: 1, name: 'Rex' }] } } },
    // Breaks the contract: id is a string and name is missing.
    { httpRequest: { path: `${prefix}/pets/1` }, httpResponse: { statusCode: 200, headers: json, body: { type: 'JSON', json: { id: 'one' } } } },
  ]);

  await page.goto('./#/contract');
  await expect(page.getByRole('heading', { name: 'Contract Test' })).toBeVisible();
  const run = page.getByRole('button', { name: 'Run contract test' });
  await expect(run).toBeDisabled();

  await page.getByRole('textbox', { name: 'OpenAPI spec (URL or inline)' }).fill(petsSpec(prefix));
  await expect(run, 'a base URL is still required').toBeDisabled();
  await page.getByRole('textbox', { name: 'Target base URL' }).fill(UPSTREAM);
  await run.click();

  await expect(page.getByText('1 failed').first()).toBeVisible();
  const rows = page.getByRole('row');
  const listRow = rows.filter({ hasText: 'listPets' });
  const getRow = rows.filter({ hasText: 'getPet' });
  await expect(listRow).toContainText('PASS');
  await expect(getRow).toContainText('FAIL');
  await expect(getRow).toContainText('$.id: string found, integer expected');
  await expect(getRow).toContainText('$.name: is missing but it is required');
  // A multi-line validation error keeps its line breaks (E2E-VERIFY-8).
  const multiLine = getRow.locator('li').filter({ hasText: '$.name: is missing' });
  await expect(multiLine).toHaveCSS('white-space', 'pre-wrap');
  await expect(page.getByText('2 operations')).toBeVisible();

  // operationId narrows the run to one (passing) operation.
  await page.getByRole('textbox', { name: 'operationId (optional)' }).fill('listPets');
  await run.click();
  await expect(page.getByText('All passed')).toBeVisible();
  await expect(page.getByText('1 operation', { exact: true })).toBeVisible();
  await expect(rows.filter({ hasText: 'getPet' })).toHaveCount(0);
});

test('contract: an unparseable spec shows the server error instead of a report', async ({ page }) => {
  consoleWatch.allow(/status of 400 .* @ .*\/mockserver\/contractTest$/);
  await page.goto('./#/contract');
  await page.getByRole('textbox', { name: 'OpenAPI spec (URL or inline)' }).fill('this is not an openapi document');
  await page.getByRole('textbox', { name: 'Target base URL' }).fill(UPSTREAM);
  await page.getByRole('button', { name: 'Run contract test' }).click();
  await expect(page.getByRole('alert').filter({ hasText: /Unable to load API spec/ })).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
});

test('contract: Validate Recorded Traffic shows the empty state, then validates forwarded traffic against the spec', async ({ page, request }) => {
  const stamp = Date.now();
  const prefix = `/e2e/contract-${stamp}`;

  await page.goto('./#/contract');
  await page.getByRole('button', { name: 'Validate Recorded Traffic' }).click();
  await expect(page.getByRole('textbox', { name: 'Target base URL' })).toHaveCount(0);
  const validate = page.getByRole('button', { name: 'Validate Traffic' });
  await expect(validate).toBeDisabled();
  await page.getByRole('textbox', { name: 'OpenAPI spec (URL or inline)' }).fill(petsSpec(prefix));
  await validate.click();
  await expect(page.getByRole('alert').filter({ hasText: 'No recorded traffic to validate.' })).toBeVisible();

  // Record one conforming and one violating exchange by forwarding to the upstream.
  const json = { 'Content-Type': ['application/json'] };
  await forwardToUpstream(request, {
    path: `${prefix}/pets`,
    upstreamPath: `/upstream/contract-${stamp}/pets`,
    upstreamResponse: { statusCode: 200, headers: json, body: { type: 'JSON', json: [{ id: 1, name: 'Rex' }] } },
  });
  await forwardToUpstream(request, {
    path: `${prefix}/pets/1`,
    upstreamPath: `/upstream/contract-${stamp}/pets/1`,
    upstreamResponse: { statusCode: 200, headers: json, body: { type: 'JSON', json: { id: 'one' } } },
  });
  expect((await request.get(`${ORIGIN}${prefix}/pets`)).status()).toBe(200);
  expect((await request.get(`${ORIGIN}${prefix}/pets/1`)).status()).toBe(200);

  await validate.click();
  if (UPSTREAM_IS_SEPARATE) {
    await expect(page.getByText('1 invalid')).toBeVisible();
    await expect(page.getByText('2 requests')).toBeVisible();
  } else {
    // The upstream hits are recorded too (and match no operation): 4 requests, 3 invalid.
    await expect(page.getByText('3 invalid')).toBeVisible();
  }
  const rows = page.getByRole('row');
  await expect(rows.filter({ hasText: `${prefix}/pets/1` })).toContainText('FAIL');
  await expect(rows.filter({ hasText: `${prefix}/pets/1` })).toContainText('$.id: string found, integer expected');
  // The list row: "/pets" not followed by "/1".
  const listRow = rows.filter({ hasText: new RegExp(`${prefix}/pets(?!/1)`) });
  await expect(listRow).toContainText('PASS');
  await expect(listRow).toContainText(`GET ${prefix}/pets`);
});

// ---------------------------------------------------------------------------
// SLO
// ---------------------------------------------------------------------------

/** A row of the verdict table (the objectives editor rows never carry a result). */
function verdictRow(page: Page, indicator: string): Locator {
  return page.getByRole('row').filter({ hasText: indicator }).filter({ hasText: /PASS|FAIL|INCONCLUSIVE/ });
}

async function fillObjective(page: Page, index: number, threshold: string): Promise<void> {
  await page.getByRole('spinbutton', { name: `Objective ${index} threshold` }).fill(threshold);
}

test('SLO: forwarded traffic yields PASS, a breached threshold yields FAIL, too few samples is INCONCLUSIVE', async ({ page, request }) => {
  // A FAIL verdict is a 406 by design.
  consoleWatch.allow(/status of 406 .* @ .*\/mockserver\/verifySLO$/);
  const stamp = Date.now();
  const path = `/e2e/slo-${stamp}`;
  await forwardToUpstream(request, { path, upstreamPath: `/upstream/slo-${stamp}`, upstreamResponse: { statusCode: 200, body: 'ok' } });
  for (let i = 0; i < 3; i++) expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(200);

  await page.goto('./#/slo');
  await expect(page.getByRole('heading', { name: 'SLO Verification' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Name' }).fill(`slo-${stamp}`);
  await fillObjective(page, 1, '60000');
  const verify = page.getByRole('button', { name: 'Verify SLO' });

  await verify.click();
  const verdict = page.getByRole('alert').filter({ hasText: `slo-${stamp}:` });
  await expect(verdict).toContainText(`slo-${stamp}: PASS`);
  await expect(verdict).toContainText(/Evaluated \d+ samples? over the window/);
  await expect(verdictRow(page, 'Latency p95')).toContainText('PASS');

  // A latency objective nobody can meet (< 0 ms) breaches.
  await fillObjective(page, 1, '0');
  await verify.click();
  await expect(verdict).toContainText(`slo-${stamp}: FAIL`);
  await expect(verdictRow(page, 'Latency p95')).toContainText('FAIL');

  // A minimum sample count far above the traffic sent cannot reach a verdict.
  await fillObjective(page, 1, '60000');
  await page.getByRole('spinbutton', { name: 'Minimum sample count' }).fill('100000');
  await verify.click();
  await expect(verdict).toContainText(`slo-${stamp}: INCONCLUSIVE`);
  await expect(verdict).toContainText('Not enough samples to draw a verdict');
});

test('SLO: objectives can be added and removed, and Verify is disabled for an invalid form', async ({ page }) => {
  await page.goto('./#/slo');
  const verify = page.getByRole('button', { name: 'Verify SLO' });
  await expect(verify).toBeEnabled();

  await page.getByRole('button', { name: 'Add objective' }).click();
  await expect(page.getByRole('spinbutton', { name: 'Objective 3 threshold' })).toHaveValue('500');
  await page.getByRole('button', { name: 'Remove objective 3' }).click();
  await expect(page.getByRole('spinbutton', { name: 'Objective 3 threshold' })).toHaveCount(0);

  await fillObjective(page, 1, '');
  await expect(verify, 'blank threshold').toBeDisabled();
  await fillObjective(page, 1, '250');
  await page.getByRole('spinbutton', { name: 'Lookback window in seconds' }).fill('0');
  await expect(verify, 'zero lookback').toBeDisabled();
  await page.getByRole('spinbutton', { name: 'Lookback window in seconds' }).fill('60');
  await expect(verify).toBeEnabled();

  // The last remaining objective cannot be removed.
  await page.getByRole('button', { name: 'Remove objective 2' }).click();
  await expect(page.getByRole('button', { name: 'Remove objective 1' })).toBeDisabled();
});

test('SLO: a server without sloTrackingEnabled explains why it cannot verify', async ({ browser }) => {
  requireOrSkip(UPSTREAM_IS_SEPARATE, 'needs a second server started without sloTrackingEnabled');
  const page = await browser.newPage();
  const watch = watchConsole(page);
  watch.allow(/status of 403 .* @ .*\/mockserver\/verifySLO$/);
  try {
    await page.goto(`${UPSTREAM}/mockserver/dashboard/#/slo`);
    await page.getByRole('button', { name: 'Verify SLO' }).click();
    const error = page.getByRole('alert').filter({ hasText: 'Could not verify SLO' });
    await expect(error).toContainText('SLO tracking not enabled');
    await expect(page.getByRole('table')).toHaveCount(1); // only the objectives editor, no verdict table
    expect(watch.errors).toEqual([]);
  } finally {
    await page.close();
  }
});

// ---------------------------------------------------------------------------
// Drift
// ---------------------------------------------------------------------------

async function driftCount(request: APIRequestContext, expectationId?: string): Promise<number> {
  const qs = expectationId ? `?expectationId=${encodeURIComponent(expectationId)}` : '';
  const res = await request.get(`${ORIGIN}/mockserver/drift${qs}`);
  expect(res.ok()).toBeTruthy();
  return ((await res.json()) as { count: number }).count;
}

test('drift: a forwarded response that diverges from its stub is listed, filtered and cleared', async ({ page, request }) => {
  const stamp = Date.now();
  const path = `/e2e/drift-${stamp}`;
  const stubId = `drift-stub-${stamp}`;

  await page.goto('./#/drift');
  await expect(page.getByRole('heading', { name: 'Drift Detection' })).toBeVisible();
  await expect(page.getByText('0 detected', { exact: true })).toBeVisible();
  await expect(page.getByText(/^No drift detected\./)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Clear', exact: true })).toBeDisabled();

  // The real API (upstream) has evolved away from the stub: different status,
  // id changed type, age removed, email added, a new header.
  await forwardToUpstream(request, {
    path,
    upstreamPath: `/upstream/drift-${stamp}`,
    upstreamResponse: {
      statusCode: 200,
      headers: { 'Content-Type': ['application/json'], 'X-Upstream-Version': ['2'] },
      body: { type: 'JSON', json: { id: '7', name: 'Ann', email: 'ann@example.com' } },
    },
    stub: {
      id: stubId,
      priority: 0,
      httpRequest: { path },
      httpResponse: { statusCode: 201, headers: { 'Content-Type': ['application/json'] }, body: { type: 'JSON', json: { id: 7, name: 'Ann', age: 30 } } },
    },
  });
  expect((await request.get(`${ORIGIN}${path}`)).status(), 'served by the upstream, not the stub').toBe(200);
  // Drift analysis is asynchronous; wait for the server to record it.
  await expect.poll(() => driftCount(request, stubId)).toBe(5);

  await page.getByRole('button', { name: 'Refresh drift' }).click();
  await expect(page.getByText('5 detected', { exact: true })).toBeVisible();
  const rows = page.getByRole('row').filter({ hasText: stubId });
  await expect(rows).toHaveCount(5);
  await expect(rows.filter({ hasText: 'STATUS' })).toContainText('statusCode');
  await expect(rows.filter({ hasText: 'STATUS' })).toContainText('201');
  await expect(rows.filter({ hasText: 'STATUS' })).toContainText('200');
  await expect(rows.filter({ hasText: 'STATUS' })).toContainText('100%');
  await expect(rows.filter({ hasText: 'SCHEMA_TYPE_CHANGED' })).toContainText('$.id');
  await expect(rows.filter({ hasText: 'SCHEMA_FIELD_REMOVED' })).toContainText('$.age');
  await expect(rows.filter({ hasText: 'SCHEMA_FIELD_ADDED' })).toContainText('$.email');
  await expect(rows.filter({ hasText: 'HEADER_ADDED' })).toContainText('header.x-upstream-version');

  // Filter by expectation id.
  const filter = page.getByRole('textbox', { name: 'Filter by expectation' });
  await filter.fill(stubId.slice(-8));
  await expect(rows).toHaveCount(5);
  await filter.fill('no-such-expectation');
  await expect(rows).toHaveCount(0);
  await filter.fill('');
  await expect(rows).toHaveCount(5);

  // Clear asks for confirmation; cancelling keeps the records.
  await page.getByRole('button', { name: 'Clear', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Clear all drift records?' });
  await expect(dialog).toContainText('This removes all 5 detected drift records.');
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(dialog).toHaveCount(0);
  expect(await driftCount(request)).toBe(5);

  await page.getByRole('button', { name: 'Clear', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Clear drift records' }).click();
  await expect(page.getByText('0 detected', { exact: true })).toBeVisible();
  // The closing dialog keeps the count it opened with (E2E-VERIFY-10).
  await expect(page.getByText(/This removes all 0 detected/)).toHaveCount(0);
  await expect(page.getByText(/^No drift detected\./)).toBeVisible();
  expect(await driftCount(request)).toBe(0);
});

test('drift: the view picks up new drift on its own (auto-refresh) without a reload', async ({ page, request }) => {
  const stamp = Date.now();
  const path = `/e2e/drift-auto-${stamp}`;
  await page.goto('./#/drift');
  await expect(page.getByText('0 detected', { exact: true })).toBeVisible();

  await forwardToUpstream(request, {
    path,
    upstreamPath: `/upstream/drift-auto-${stamp}`,
    upstreamResponse: { statusCode: 503 },
    stub: { id: `auto-${stamp}`, httpRequest: { path }, httpResponse: { statusCode: 200 } },
  });
  expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(503);
  // The panel polls every 5 s; allow two cycles.
  await expect(page.getByRole('row').filter({ hasText: `auto-${stamp}` }).filter({ hasText: 'STATUS' })).toBeVisible({ timeout: 12_000 });
});

// ---------------------------------------------------------------------------
// Regression tests for defects found by the e2e sweep (E2E-VERIFY-n)
// ---------------------------------------------------------------------------

test('E2E-VERIFY-1: the "Body (substring/JSON match)" field matches a substring or a partial JSON body', async ({ page, request }) => {
  consoleWatch.allow(FAILED_VERIFY_406);
  const path = `/e2e/verify/body-substring-${Date.now()}`;
  await request.post(`${ORIGIN}${path}`, { headers: { 'Content-Type': 'application/json' }, data: { order: 'widget', qty: 3 } });
  await openVerification(page);
  await page.getByRole('textbox', { name: 'Path' }).fill(path);
  const body = page.getByRole('textbox', { name: 'Body (substring/JSON match)' });
  await body.fill('widget');
  await clickVerifyAndExpect(page, 'Verify', 'pass');
  await body.fill('{"order":"widget"}');
  await clickVerifyAndExpect(page, 'Verify', 'pass');
  // …and still narrows the match.
  await body.fill('gadget');
  await clickVerifyAndExpect(page, 'Verify', /Request not found at least once/);
  await body.fill('{"order":"gadget"}');
  await clickVerifyAndExpect(page, 'Verify', /Request not found at least once/);
});

test('E2E-VERIFY-1: the response body field matches a substring of a forwarded response', async ({ page, request }) => {
  consoleWatch.allow(FAILED_VERIFY_406);
  const stamp = Date.now();
  const path = `/e2e/verify/resp-body-${stamp}`;
  await forwardToUpstream(request, {
    path,
    upstreamPath: `/upstream/verify/resp-body-${stamp}`,
    upstreamResponse: { statusCode: 200, headers: { 'Content-Type': ['application/json'] }, body: { type: 'JSON', json: { status: 'shipped', id: 9 } } },
  });
  expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(200);

  await openVerification(page);
  await page.getByRole('textbox', { name: 'Path' }).fill(path);
  await page.getByRole('button', { name: 'Expand Response matcher (optional)' }).first().click();
  const body = page.getByRole('textbox', { name: 'Response body (substring/JSON match)' });
  await body.fill('shipped');
  await clickVerifyAndExpect(page, 'Verify', 'pass');
  await body.fill('{"status":"shipped"}');
  await clickVerifyAndExpect(page, 'Verify', 'pass');
  await body.fill('{"status":"lost"}');
  await clickVerifyAndExpect(page, 'Verify', /Response not found at least once/);
});

test('E2E-VERIFY-2: a header or query line without its separator is rejected inline, never dropped into a false PASS', async ({ page, request }) => {
  const path = `/e2e/verify/bad-header-${Date.now()}`;
  await request.get(`${ORIGIN}${path}`);
  await openVerification(page);
  await page.getByRole('textbox', { name: 'Path' }).fill(path);
  const headers = page.getByRole('textbox', { name: 'Headers (Name: value per line)' });
  const verify = page.getByRole('button', { name: 'Verify', exact: true });
  await headers.fill('X-Required-Header abc');
  await expect(page.getByText('"X-Required-Header abc" is not Name: value')).toBeVisible();
  await expect(page.getByText('Fix the fields marked in red to verify.')).toBeVisible();
  await expect(verify).toBeDisabled();

  await headers.fill('');
  await page.getByRole('textbox', { name: 'Query (key=value per line)' }).fill('page');
  await expect(page.getByText('"page" is not key=value')).toBeVisible();
  await expect(verify).toBeDisabled();

  await page.getByRole('textbox', { name: 'Query (key=value per line)' }).fill('');
  await expect(verify).toBeEnabled();
  await clickVerifyAndExpect(page, 'Verify', 'pass');
});

test('E2E-VERIFY-3: selects and inputs have accessible names and section headers are single buttons', async ({ page }) => {
  await openVerification(page);
  await expect(methodSelect(page)).toHaveText('Any method');
  await expect(timesModeSelect(page)).toHaveText('at least');
  await expect(page.getByRole('textbox', { name: 'Quick scope' })).toBeVisible();
  for (const name of ['Collapse Request matcher (optional)', 'Expand Response matcher (optional)']) {
    const header = page.getByRole('button', { name, exact: true });
    await expect(header).toHaveCount(1);
    await expect(header.locator('button, [role="button"]')).toHaveCount(0);
  }
  // The header toggles from the keyboard.
  await page.getByRole('button', { name: 'Expand Response matcher (optional)' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('textbox', { name: 'Status code' })).toBeVisible();
});

test('E2E-VERIFY-4: an out-of-range count is rejected before sending, and a 400 reads as an error, not "Verification failed"', async ({ page, request }) => {
  const path = `/e2e/verify/overflow-${Date.now()}`;
  await request.get(`${ORIGIN}${path}`);
  await openVerification(page);
  await page.getByRole('textbox', { name: 'Path' }).fill(path);
  const verify = page.getByRole('button', { name: 'Verify', exact: true });
  await page.getByRole('spinbutton', { name: 'times' }).fill('3000000000');
  await expect(page.getByText('At most 2147483647')).toBeVisible();
  await expect(verify).toBeDisabled();
  await page.getByRole('spinbutton', { name: 'times' }).fill('1');
  await expect(verify).toBeEnabled();

  // Any other non-verdict status is an error too; the real server's 400 is simulated here.
  consoleWatch.allow(/status of 400 .* @ .*\/mockserver\/verify$/);
  await page.route('**/mockserver/verify', (route) => route.fulfill({ status: 400, contentType: 'text/plain', body: 'incorrect verification json format' }));
  await verify.click();
  await expect(page.getByRole('alert').filter({ hasText: 'The request was rejected as invalid.' })).toBeVisible();
  await expect(page.getByText('Verification failed')).toHaveCount(0);
});

test('E2E-VERIFY-5: "between 3 and 1" is flagged instead of silently becoming "exactly 3"', async ({ page }) => {
  await openVerification(page);
  await page.getByRole('textbox', { name: 'Path' }).fill('/e2e/verify/between');
  await setTimes(page, 'between', 3, 1);
  await expect(page.getByText('Must not be less than min')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Verify', exact: true })).toBeDisabled();
  await page.getByRole('tab', { name: 'JSON' }).click();
  const code = page.locator('pre').filter({ hasText: '"times"' });
  await expect(code).toContainText('"atMost": 1');
  await expect(code).not.toContainText('"atMost": 3');
});

test('E2E-VERIFY-6: a drift filter that hides every record says so, not "No drift detected"', async ({ page, request }) => {
  const stamp = Date.now();
  const path = `/e2e/drift-filter-${stamp}`;
  await forwardToUpstream(request, {
    path,
    upstreamPath: `/upstream/drift-filter-${stamp}`,
    upstreamResponse: { statusCode: 500 },
    stub: { id: `filter-${stamp}`, httpRequest: { path }, httpResponse: { statusCode: 200 } },
  });
  await request.get(`${ORIGIN}${path}`);
  await expect.poll(() => driftCount(request)).toBeGreaterThan(0);
  await page.goto('./#/drift');
  await page.getByRole('button', { name: 'Refresh drift' }).click();
  await expect(page.getByRole('row').filter({ hasText: `filter-${stamp}` })).toHaveCount(1);
  await page.getByRole('textbox', { name: 'Filter by expectation' }).fill('no-such-expectation');
  await expect(page.getByText('No drift records match the filter “no-such-expectation”.')).toBeVisible();
  await expect(page.getByText(/^No drift detected\./)).toHaveCount(0);
});

test('E2E-VERIFY-7: schema drift rows show the expected and actual JSON types', async ({ page, request }) => {
  const stamp = Date.now();
  const path = `/e2e/drift-type-${stamp}`;
  const json = { 'Content-Type': ['application/json'] };
  await forwardToUpstream(request, {
    path,
    upstreamPath: `/upstream/drift-type-${stamp}`,
    upstreamResponse: { statusCode: 200, headers: json, body: { type: 'JSON', json: { id: '7', tag: 'new' } } },
    stub: { id: `type-${stamp}`, httpRequest: { path }, httpResponse: { statusCode: 200, headers: json, body: { type: 'JSON', json: { id: 7, old: true } } } },
  });
  await request.get(`${ORIGIN}${path}`);
  await expect.poll(() => driftCount(request, `type-${stamp}`)).toBe(3);
  await page.goto('./#/drift');
  await page.getByRole('button', { name: 'Refresh drift' }).click();
  const cells = (driftType: string) => page.getByRole('row').filter({ hasText: driftType }).getByRole('cell');
  // Columns: Expectation, Drift Type, Field, Expected, Actual, …
  await expect(cells('SCHEMA_TYPE_CHANGED').nth(3)).toHaveText('integer');
  await expect(cells('SCHEMA_TYPE_CHANGED').nth(4)).toHaveText('string');
  await expect(cells('SCHEMA_FIELD_REMOVED').nth(3)).toHaveText('boolean');
  await expect(cells('SCHEMA_FIELD_ADDED').nth(4)).toHaveText('string');
});
