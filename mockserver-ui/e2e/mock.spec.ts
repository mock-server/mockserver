import { test, expect, type APIRequestContext, type Page, type Locator } from '@playwright/test';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

// Real-browser end-to-end coverage of the dashboard's Mock menu — Get Started,
// the Mocks composer (Quick + Advanced, every expectation kind and response
// type), Scenarios, gRPC and Async — against a REAL MockServer over real REST
// and the real WebSocket. Every test resets the server first and asserts the
// SERVER's own state (PUT /mockserver/retrieve, the data plane), not just what
// the page shows. Any console error or uncaught exception fails the test.

const HOST = process.env.E2E_MS_HOST || '127.0.0.1';
const PORT = process.env.E2E_MS_PORT || '1084';
const ORIGIN = `http://${HOST}:${PORT}`;

type Json = Record<string, unknown>;

async function activeExpectations(request: APIRequestContext): Promise<Json[]> {
  const res = await request.put(`${ORIGIN}/mockserver/retrieve?type=active_expectations&format=json`);
  expect(res.ok(), `retrieve returned ${res.status()}`).toBeTruthy();
  return (await res.json()) as Json[];
}

async function onlyExpectation(request: APIRequestContext): Promise<Json> {
  let list: Json[] = [];
  await expect
    .poll(async () => {
      list = await activeExpectations(request);
      return list.length;
    })
    .toBe(1);
  return list[0]!;
}

async function upsert(request: APIRequestContext, expectations: Json | Json[]): Promise<void> {
  const res = await request.put(`${ORIGIN}/mockserver/expectation`, { data: expectations });
  expect(res.status(), await res.text()).toBe(201);
}

// Console errors / uncaught exceptions are collected per test and asserted empty
// at the end, so a view that throws while rendering fails loudly. A test that
// deliberately provokes a failed fetch (server 4xx) declares the expected text.
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

test.afterEach(async ({ page }) => {
  const allowed = allowedConsole.get(page) ?? [];
  const unexpected = (consoleErrors.get(page) ?? []).filter((e) => !allowed.some((re) => re.test(e)));
  expect(unexpected, 'browser console errors / uncaught exceptions').toEqual([]);
});

async function selectOption(scope: Page | Locator, label: string, option: string): Promise<void> {
  await scope.getByRole('combobox', { name: label, exact: true }).click();
  const page = 'page' in scope ? scope.page() : scope;
  await page.getByRole('option', { name: option, exact: true }).click();
}

async function gotoView(page: Page, view: string): Promise<void> {
  await page.goto(`./#/${view}`);
  await expect(page.getByText('connected', { exact: true })).toBeVisible();
}

async function openAdvanced(page: Page): Promise<void> {
  await gotoView(page, 'composer');
  await page.getByRole('button', { name: 'Advanced', exact: true }).click();
  await expect(page.getByText('Expectation kind')).toBeVisible();
}

async function chooseKind(page: Page, kind: string): Promise<void> {
  await page.getByRole('radio', { name: kind, exact: true }).check();
}

async function chooseAction(page: Page, label: string): Promise<void> {
  // Each action radio's accessible name is "<label><description>"; match on the label prefix.
  await page.getByRole('radio', { name: new RegExp(`^${label}`) }).check();
}

// The step-3 Paper ("3 · <action label>") that holds the chosen action's fields —
// scoping avoids clashes with same-named matcher fields (Path, Method, Headers...).
function actionPanel(page: Page): Locator {
  return page.locator('.MuiPaper-root').filter({ has: page.getByText(/^3 · /) });
}

function registerButton(page: Page): Locator {
  return page.getByRole('button', { name: /^(Register expectation|Update expectation)$/ });
}

async function register(page: Page): Promise<void> {
  await registerButton(page).click();
  await expect(page.getByTestId('register-success')).toBeVisible();
}

function action(expectation: Json, key: string): Json {
  const a = expectation[key];
  expect(a, `expectation has no ${key}: ${JSON.stringify(expectation)}`).toBeTruthy();
  return a as Json;
}

// A throwaway HTTP upstream for the forward actions: echoes method + path (+ an
// X-Seen-Host header) so a test can prove the request really went through it.
async function startUpstream(): Promise<{ port: number; seen: string[]; close: () => Promise<void> }> {
  const seen: string[] = [];
  const server = http.createServer((req, res) => {
    seen.push(`${req.method} ${req.url}`);
    res.writeHead(200, { 'Content-Type': 'text/plain', 'X-Seen-Host': String(req.headers.host ?? '') });
    res.end(`upstream saw ${req.method} ${req.url}`);
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  return {
    port: (server.address() as AddressInfo).port,
    seen,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

// ---------------------------------------------------------------------------
// Navigation + Get Started
// ---------------------------------------------------------------------------

test('Mock menu lists its five views and each one opens', async ({ page }) => {
  await gotoView(page, 'dashboard');
  const views: Array<[string, string, RegExp]> = [
    ['Get started view', 'get-started', /Welcome to MockServer/],
    ['Mocks view', 'composer', /Quick mock/],
    ['Scenarios view', 'scenarios', /Scenario State Machine/],
    ['gRPC services view', 'grpc', /gRPC Services/],
    ['AsyncAPI broker mock view', 'async', /AsyncAPI Broker Mock/],
  ];
  for (const [menuItem, hash, marker] of views) {
    await page.getByRole('button', { name: 'Mock views' }).click();
    const menu = page.getByRole('menu');
    await expect(menu.getByRole('menuitem')).toHaveCount(5);
    await menu.getByRole('menuitem', { name: menuItem }).click();
    await expect(page).toHaveURL(new RegExp(`#/${hash}$`));
    await expect(page.getByText(marker).first()).toBeVisible();
  }
});

test('Get Started routes each card and link to its view, and Import OpenAPI opens the import dialog', async ({ page }) => {
  await gotoView(page, 'get-started');
  await expect(page.getByRole('heading', { name: 'Welcome to MockServer' })).toBeVisible();
  // No server state yet → no returning-user banner.
  await expect(page.getByRole('button', { name: 'Open Dashboard' })).toHaveCount(0);

  const cards: Array<[string, string]> = [
    ['Create Mock', 'composer'],
    ['View Traffic', 'traffic'],
    ['Open Breakpoints', 'breakpoints'],
    ['Open Chaos', 'chaos'],
    ['Open Performance', 'performance'],
    ['Open LLM Optimise', 'optimise'],
  ];
  for (const [button, hash] of cards) {
    await gotoView(page, 'get-started');
    await page.getByRole('button', { name: button, exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`#/${hash}$`));
  }

  await gotoView(page, 'get-started');
  await page.getByRole('button', { name: 'Import OpenAPI', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();

  // "More in the tabs above" links.
  for (const [link, hash] of [['Async', 'async'], ['Library', 'library'], ['Verification', 'verification']] as const) {
    await gotoView(page, 'get-started');
    await page.getByRole('button', { name: link, exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`#/${hash}$`));
  }
});

test('Get Started offers a jump to the Dashboard when the server already has mocks', async ({ page, request }) => {
  await upsert(request, { httpRequest: { path: '/gs/existing' }, httpResponse: { statusCode: 200 } });
  await gotoView(page, 'get-started');
  const banner = page.getByRole('alert').filter({ hasText: 'This server has 1 active mock' });
  await expect(banner).toBeVisible();
  await banner.getByRole('button', { name: 'Open Dashboard' }).click();
  await expect(page).toHaveURL(/#\/dashboard$/);
});

// ---------------------------------------------------------------------------
// Mocks composer — Quick mock
// ---------------------------------------------------------------------------

test('Quick mock: the primary action works keyboard-only and the mock is served', async ({ page, request }) => {
  const path = `/quick/kbd-${Date.now()}`;
  await gotoView(page, 'composer');
  const quick = page.getByTestId('quick-mock-form');
  // Monaco lazy-loads; let it mount before driving focus through it.
  await expect(quick.locator('.monaco-editor .view-lines')).toBeVisible();
  await quick.getByLabel('Path', { exact: true }).focus();
  await page.keyboard.type(path);
  await page.keyboard.press('Tab');
  await expect(quick.getByLabel('Status code', { exact: true })).toBeFocused();
  await page.keyboard.press('ControlOrMeta+a');
  await page.keyboard.type('418');
  await page.keyboard.press('Tab');
  await expect(quick.getByLabel('Content-Type', { exact: true })).toBeFocused();
  // Next stop is the Monaco body editor; Tab moves on out of it like any field.
  await page.keyboard.press('Tab');
  await expect(quick.locator('.monaco-editor')).toHaveClass(/focused/);
  await page.keyboard.press('Tab');
  const registerMock = page.getByRole('button', { name: 'Register mock' });
  await expect(registerMock).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByTestId('register-success')).toContainText(path);

  const served = await request.get(`${ORIGIN}${path}`);
  expect(served.status()).toBe(418);
});

test('E2E-MOCK-2: Tab moves focus out of the Quick mock response-body editor (no keyboard trap), which is announced as "Response body"', async ({ page }) => {
  await gotoView(page, 'composer');
  const quick = page.getByTestId('quick-mock-form');
  await expect(quick.locator('.monaco-editor .view-lines')).toBeVisible();
  // a path enables Register, so it can take focus
  await quick.getByLabel('Path', { exact: true }).fill('/kbd/trap');
  await quick.getByLabel('Content-Type', { exact: true }).focus();
  await page.keyboard.press('Tab');
  // The focused element inside the editor carries the field's name...
  await expect(page.getByRole('textbox', { name: 'Response body' })).toBeFocused();
  // ...and the next Tab leaves the editor for the Register button,
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Register mock' })).toBeFocused();
  // and Shift+Tab goes back in and out the other way.
  await page.keyboard.press('Shift+Tab');
  await expect(page.getByRole('textbox', { name: 'Response body' })).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(quick.getByLabel('Content-Type', { exact: true })).toBeFocused();

  // Ctrl+M (the 'Desktop Chrome' device has a Windows user agent) makes Tab indent inside
  // this editor, and pressing it again restores Tab-moves-focus.
  await page.keyboard.press('Tab');
  await expect(page.getByRole('textbox', { name: 'Response body' })).toBeFocused();
  await page.keyboard.press('Control+M');
  // twice: the first Tab edits the body and re-renders the form, the toggle must survive that
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('textbox', { name: 'Response body' })).toBeFocused();
  await page.keyboard.press('Control+M');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Register mock' })).toBeFocused();
});

test('E2E-MOCK-2: Ctrl+M switches only the focused editor to Tab-indents when the form has several', async ({ page }) => {
  await openAdvanced(page);
  await selectOption(page, 'Body type', 'JSON');
  const matcher = page.getByRole('textbox', { name: 'JSON body matcher' });
  const responseBody = page.getByRole('textbox', { name: 'Response body' });
  await expect(matcher).toBeVisible();
  await expect(responseBody).toBeVisible();

  // the later-mounted response-body editor toggles itself...
  await responseBody.focus();
  await page.keyboard.press('Control+M');
  await page.keyboard.press('Tab');
  await expect(responseBody).toBeFocused();

  // ...while the body-matcher editor keeps Tab-moves-focus,
  await matcher.focus();
  await expect(matcher).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(matcher).not.toBeFocused();

  // and toggles itself independently when its own chord is pressed.
  await matcher.focus();
  await page.keyboard.press('Control+M');
  await page.keyboard.press('Tab');
  await expect(matcher).toBeFocused();
});

test('Quick mock: Register is disabled until a path is entered, and switching to Advanced keeps the draft', async ({ page }) => {
  await gotoView(page, 'composer');
  const quick = page.getByTestId('quick-mock-form');
  const registerMock = page.getByRole('button', { name: 'Register mock' });
  await expect(registerMock).toBeDisabled();
  await quick.getByLabel('Path', { exact: true }).fill('/quick/draft');
  await quick.getByLabel('Status code', { exact: true }).fill('204');
  await expect(registerMock).toBeEnabled();

  await quick.getByRole('button', { name: 'Switch to Advanced' }).click();
  await expect(page.getByText('Expectation kind')).toBeVisible();
  await expect(page.getByLabel('Path', { exact: true })).toHaveValue('/quick/draft');
  await expect(page.getByLabel('Status code', { exact: true })).toHaveValue('204');
});

// ---------------------------------------------------------------------------
// Mocks composer — Advanced HTTP, every "Respond with" type
// ---------------------------------------------------------------------------

test('Advanced HTTP static response: matcher headers + query, response headers, served with the right status, header and body', async ({ page, request }) => {
  const path = `/adv/static-${Date.now()}`;
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill(path);
  await page.getByLabel('Headers (Name: value per line)').first().fill('X-Tenant: acme');
  await page.getByLabel('Query params (key=value per line)').fill('debug=true');
  await page.getByLabel('Status code', { exact: true }).fill('202');
  await page.getByLabel('Response headers (one per line, Name: value)').fill('X-Mock: yes');
  await register(page);

  const exp = await onlyExpectation(request);
  expect((exp['httpRequest'] as Json)['path']).toBe(path);
  expect(action(exp, 'httpResponse')['statusCode']).toBe(202);

  const miss = await request.get(`${ORIGIN}${path}?debug=true`);
  expect(miss.status(), 'header matcher should exclude a request without X-Tenant').toBe(404);
  const hit = await request.get(`${ORIGIN}${path}?debug=true`, { headers: { 'X-Tenant': 'acme' } });
  expect(hit.status()).toBe(202);
  expect(hit.headers()['x-mock']).toBe('yes');
});

test('Advanced HTTP forward: proxies the matched request to the upstream', async ({ page, request }) => {
  const upstream = await startUpstream();
  try {
    const path = `/adv/fwd-${Date.now()}`;
    await openAdvanced(page);
    await page.getByLabel('Path', { exact: true }).fill(path);
    await chooseAction(page, 'Forward to upstream');
    await selectOption(page, 'Scheme', 'HTTP');
    await page.getByLabel('Host', { exact: true }).fill('127.0.0.1');
    await page.getByLabel('Port', { exact: true }).fill(String(upstream.port));
    await register(page);

    const exp = await onlyExpectation(request);
    expect(action(exp, 'httpForward')).toMatchObject({ host: '127.0.0.1', port: upstream.port });
    const res = await request.get(`${ORIGIN}${path}`);
    expect(res.status()).toBe(200);
    expect(await res.text()).toBe(`upstream saw GET ${path}`);
  } finally {
    await upstream.close();
  }
});

test('Advanced HTTP forward with override: rewrites path + Host header and the upstream sees the rewritten request', async ({ page, request }) => {
  const upstream = await startUpstream();
  try {
    const path = `/adv/override-${Date.now()}`;
    await openAdvanced(page);
    await page.getByLabel('Path', { exact: true }).fill(path);
    await chooseAction(page, 'Forward with override');
    const panel = actionPanel(page);
    await panel.getByLabel('Host header').fill(`127.0.0.1:${upstream.port}`);
    await panel.getByLabel('Path', { exact: true }).fill('/rewritten');
    await register(page);

    const exp = await onlyExpectation(request);
    expect(JSON.stringify(action(exp, 'httpOverrideForwardedRequest'))).toContain('/rewritten');
    const res = await request.get(`${ORIGIN}${path}`);
    expect(res.status()).toBe(200);
    expect(await res.text()).toBe('upstream saw GET /rewritten');
  } finally {
    await upstream.close();
  }
});

test('Advanced HTTP forward with fallback: an unreachable upstream serves the fallback response', async ({ page, request }) => {
  const dead = await startUpstream();
  const deadPort = dead.port;
  await dead.close(); // nothing listens on this port any more
  const path = `/adv/fallback-${Date.now()}`;
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill(path);
  await chooseAction(page, 'Forward with fallback');
  const panel = actionPanel(page);
  await selectOption(panel, 'Scheme', 'HTTP');
  await panel.getByLabel('Host', { exact: true }).fill('127.0.0.1');
  await panel.getByLabel('Port', { exact: true }).fill(String(deadPort));
  await panel.getByLabel('Status code', { exact: true }).fill('503');
  await panel.getByLabel('Fallback body').fill('fallback-served');
  await panel.getByLabel('Fallback on timeout / connection error').check();
  await register(page);

  await onlyExpectation(request);
  const res = await request.get(`${ORIGIN}${path}`);
  expect(res.status()).toBe(503);
  expect(await res.text()).toContain('fallback-served');
});

test('Advanced HTTP class callback and forward class callback register the class names', async ({ page, request }) => {
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill('/adv/callback');
  await chooseAction(page, 'Class callback');
  await actionPanel(page).getByLabel('Callback class (fully-qualified name)').fill('com.example.ResponseCallback');
  await register(page);
  expect(action(await onlyExpectation(request), 'httpResponseClassCallback')['callbackClass']).toBe('com.example.ResponseCallback');

  await request.put(`${ORIGIN}/mockserver/reset`);
  await page.getByRole('button', { name: 'Add another' }).click();
  await page.getByLabel('Path', { exact: true }).fill('/adv/fwd-callback');
  await chooseAction(page, 'Forward class callback');
  await actionPanel(page).getByLabel('Callback class (fully-qualified name)').fill('com.example.ForwardCallback');
  await register(page);
  expect(action(await onlyExpectation(request), 'httpForwardClassCallback')['callbackClass']).toBe('com.example.ForwardCallback');
});

test('Advanced HTTP response template (Velocity) renders the request into the served response', async ({ page, request }) => {
  const path = `/adv/template-${Date.now()}`;
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill(path);
  await chooseAction(page, 'Response template');
  await actionPanel(page).getByLabel('Template body').fill('{ "statusCode": 200, "body": "echo $!request.path" }');
  await register(page);

  expect(action(await onlyExpectation(request), 'httpResponseTemplate')['templateType']).toBe('VELOCITY');
  const res = await request.get(`${ORIGIN}${path}`);
  expect(res.status()).toBe(200);
  expect(await res.text()).toBe(`echo ${path}`);
});

test('Advanced HTTP forward template rewrites the forwarded request to the upstream', async ({ page, request }) => {
  const upstream = await startUpstream();
  try {
    const path = `/adv/fwd-template-${Date.now()}`;
    await openAdvanced(page);
    await page.getByLabel('Path', { exact: true }).fill(path);
    await chooseAction(page, 'Forward template');
    await actionPanel(page)
      .getByLabel('Template body')
      .fill(`{ "method": "GET", "path": "/via-template", "headers": { "Host": ["127.0.0.1:${upstream.port}"] } }`);
    await register(page);

    expect(action(await onlyExpectation(request), 'httpForwardTemplate')['templateType']).toBe('VELOCITY');
    const res = await request.get(`${ORIGIN}${path}`);
    expect(await res.text()).toBe('upstream saw GET /via-template');
  } finally {
    await upstream.close();
  }
});

test('Advanced HTTP error / fault injection: drop connection really drops the socket', async ({ page, request }) => {
  const path = `/adv/error-${Date.now()}`;
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill(path);
  await chooseAction(page, 'Error / fault injection');
  const drop = actionPanel(page).getByLabel('Drop connection (clean close)');
  await expect(drop).toBeChecked(); // the default
  await drop.uncheck();
  await expect(registerButton(page)).toBeDisabled(); // needs drop or bytes
  await drop.check();
  await register(page);

  expect(action(await onlyExpectation(request), 'httpError')['dropConnection']).toBe(true);
  await expect(request.get(`${ORIGIN}${path}`)).rejects.toThrow();
});

test('Advanced HTTP WebSocket response: a browser WebSocket receives the initial message', async ({ page, request }) => {
  const path = `/adv/ws-${Date.now()}`;
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill(path);
  await chooseAction(page, 'WebSocket response');
  await actionPanel(page).getByLabel('Message 1', { exact: true }).fill('hello-from-ws');
  await register(page);

  await onlyExpectation(request);
  const received = await page.evaluate(
    (url) =>
      new Promise<string>((resolve, reject) => {
        const ws = new WebSocket(url);
        ws.onmessage = (e) => {
          resolve(String(e.data));
          ws.close();
        };
        ws.onerror = () => reject(new Error('websocket error'));
        setTimeout(() => reject(new Error('no websocket message within 10s')), 10_000);
      }),
    `ws://${HOST}:${PORT}${path}`,
  );
  expect(received).toBe('hello-from-ws');
});

test('Advanced HTTP SSE response streams the configured event', async ({ page, request }) => {
  const path = `/adv/sse-${Date.now()}`;
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill(path);
  await chooseAction(page, 'SSE response');
  await expect(registerButton(page)).toBeDisabled(); // needs an event
  const panel = actionPanel(page);
  await panel.getByLabel('Event type').fill('tick');
  await panel.getByLabel('Data').fill('{"n":1}');
  await panel.getByLabel('Close connection after events').check();
  await register(page);

  expect(JSON.stringify(action(await onlyExpectation(request), 'httpSseResponse'))).toContain('tick');
  const res = await request.get(`${ORIGIN}${path}`, { headers: { Accept: 'text/event-stream' } });
  expect(res.headers()['content-type']).toContain('text/event-stream');
  const body = await res.text();
  expect(body).toContain('event: tick');
  expect(body).toContain('data: {"n":1}');
});

test('Advanced HTTP binary response: invalid base64 blocks Register; valid base64 is stored', async ({ page, request }) => {
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill('/adv/binary');
  await chooseAction(page, 'Binary response');
  const data = actionPanel(page).getByLabel('Binary data (base64)');
  await data.fill('not base64 !!');
  await expect(registerButton(page)).toBeDisabled();
  await data.fill('SGVsbG8=');
  await register(page);
  expect(JSON.stringify(action(await onlyExpectation(request), 'binaryResponse'))).toContain('SGVsbG8=');
});

test('Advanced cross-cutting options: chaos, a steps pipeline, capture rules and an after-action all take effect', async ({ page, request }) => {
  const upstream = await startUpstream();
  try {
    const stamp = Date.now();
    // Chaos: a guaranteed 503.
    await openAdvanced(page);
    await page.getByLabel('Path', { exact: true }).fill(`/x/chaos-${stamp}`);
    await page.getByLabel('Inject fault / chaos (optional)').check();
    await page.getByRole('spinbutton', { name: 'Error status' }).fill('503');
    await page.getByRole('spinbutton', { name: 'Error prob (0-1)' }).fill('1');
    await register(page);
    expect((await request.get(`${ORIGIN}/x/chaos-${stamp}`)).status()).toBe(503);

    // Steps: one responder step serves the response.
    await page.getByRole('button', { name: 'Add another' }).click();
    await page.getByLabel('Path', { exact: true }).fill(`/x/steps-${stamp}`);
    await page.getByLabel('Steps pipeline (advanced, optional)').check();
    await page.getByTestId('add-step').click();
    await expect(registerButton(page)).toBeDisabled(); // no responder yet
    const step = page.getByTestId('step-row');
    await step.getByRole('radio', { name: 'Responder', exact: true }).check();
    await step.getByLabel('Static HTTP response payload (JSON)').fill('{"statusCode": 226, "body": "via-steps"}');
    await register(page);
    const viaSteps = await request.get(`${ORIGIN}/x/steps-${stamp}`);
    expect(viaSteps.status()).toBe(226);
    expect(await viaSteps.text()).toBe('via-steps');

    // Capture rule + an after-action webhook to the upstream.
    await page.getByRole('button', { name: 'Add another' }).click();
    await page.getByLabel('Path', { exact: true }).fill(`/x/side-${stamp}`);
    await page.getByLabel('Capture into scenario state (optional)').check();
    const capture = page.getByTestId('capture-row').or(page.getByTestId('capture-rule-row'));
    await capture.getByLabel('Expression').fill('$.orderId');
    await capture.getByLabel('Into (state key)').fill('orderId');
    await page.getByLabel('Before & after actions (optional)').check();
    await page.getByTestId('add-side-effect').click();
    const sideEffect = page.getByTestId('side-effect-row');
    await selectOption(sideEffect, 'Position', 'After');
    await sideEffect.getByLabel('Path', { exact: true }).fill('/hook');
    await sideEffect.getByLabel('Host (optional)').fill(`127.0.0.1:${upstream.port}`);
    await register(page);

    const side = (await activeExpectations(request)).find((e) => JSON.stringify(e).includes(`/x/side-${stamp}`))!;
    expect(JSON.stringify(side)).toContain('orderId');
    expect((await request.get(`${ORIGIN}/x/side-${stamp}`)).status()).toBe(200);
    await expect.poll(() => upstream.seen.join(',')).toContain('/hook');
  } finally {
    await upstream.close();
  }
});

// ---------------------------------------------------------------------------
// Mocks composer — the other expectation kinds
// ---------------------------------------------------------------------------

test('gRPC kind pre-shapes the matcher and registers a gRPC stream response; leaving gRPC undoes the pre-shaping', async ({ page, request }) => {
  await openAdvanced(page);
  await chooseKind(page, 'gRPC');
  await expect(page.getByLabel('Path', { exact: true })).toHaveValue('/package.Service/Method');
  await expect(page.getByRole('combobox', { name: 'Method', exact: true })).toHaveText('POST');
  // gRPC offers exactly two actions.
  await expect(page.getByRole('radiogroup').nth(1).getByRole('radio')).toHaveCount(2);
  await page.getByLabel('Path', { exact: true }).fill('/greet.Greeter/SayHello');
  await chooseAction(page, 'gRPC stream response');
  await register(page);
  const exp = await onlyExpectation(request);
  expect((exp['httpRequest'] as Json)['method']).toBe('POST');
  expect(action(exp, 'grpcStreamResponse')).toBeTruthy();

  await page.getByRole('button', { name: 'Add another' }).click();
  await chooseKind(page, 'gRPC');
  await chooseKind(page, 'HTTP');
  await expect(page.getByLabel('Path', { exact: true })).toHaveValue('');
});

test('DNS kind registers a DNS expectation; invalid answer-records JSON blocks Register', async ({ page, request }) => {
  await openAdvanced(page);
  await chooseKind(page, 'DNS');
  await expect(registerButton(page)).toBeDisabled(); // DNS name required
  await page.getByRole('textbox', { name: 'DNS name' }).fill('api.e2e.test');
  const records = actionPanel(page).getByLabel('Answer records (JSON array)');
  await records.fill('{not json');
  await expect(registerButton(page)).toBeDisabled();
  await records.fill('[{"name":"api.e2e.test","type":"A","ttl":60,"value":"10.0.0.7"}]');
  await register(page);

  const exp = await onlyExpectation(request);
  expect(JSON.stringify(exp)).toContain('api.e2e.test');
  expect(JSON.stringify(exp)).toContain('10.0.0.7');
  // The existing-mocks list for DNS now shows it.
  await page.getByRole('button', { name: 'Add another' }).click();
  await chooseKind(page, 'DNS');
  await expect(page.getByTestId('existing-mocks-list')).toContainText('api.e2e.test');
});

test('MCP kind shows the tool derived from a registered HTTP response mock', async ({ page, request }) => {
  await openAdvanced(page);
  await chooseKind(page, 'MCP');
  await page.getByLabel('Path', { exact: true }).fill('/mcp/weather');
  await register(page);
  await onlyExpectation(request);
  // The MCP Tools panel (fed by the server's list_mock_tools MCP tool) lists it.
  await page.getByRole('button', { name: 'Add another' }).click();
  await chooseKind(page, 'MCP');
  await expect(page.getByRole('cell', { name: 'GET /mcp/weather', exact: true })).toBeVisible();
});

test('LLM Conversation kind registers a scripted conversation that answers like the provider, and lists it for editing', async ({ page, request }) => {
  await openAdvanced(page);
  await chooseKind(page, 'LLM Conversation');
  await expect(page.getByText('No LLM mocks yet')).toBeVisible();
  await page.getByLabel('Text', { exact: true }).fill('Hello from the e2e mock');
  await page.getByRole('button', { name: 'Register on server' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Conversation registered.' })).toBeVisible();

  await expect.poll(async () => (await activeExpectations(request)).length).toBeGreaterThan(0);
  const res = await request.post(`${ORIGIN}/v1/messages`, {
    data: { model: 'claude-test', max_tokens: 64, messages: [{ role: 'user', content: 'hi' }] },
  });
  expect(res.status()).toBe(200);
  expect(JSON.stringify(await res.json())).toContain('Hello from the e2e mock');

  // The scenario now appears in "Existing LLM scenarios"; selecting it loads it for editing.
  const list = page.getByTestId('existing-mocks-list');
  await expect(list.getByRole('button', { name: /\(1 turn\)/ })).toBeVisible();
  await list.getByRole('button', { name: /\(1 turn\)/ }).click();
  await expect(page.getByRole('button', { name: 'Update 1 expectation' })).toBeVisible();
  await expect(page.getByLabel('Text', { exact: true })).toHaveValue('Hello from the e2e mock');

  // Update in place: same number of expectations, new answer.
  const before = (await activeExpectations(request)).map((e) => e['id']).sort();
  await page.getByLabel('Text', { exact: true }).fill('Edited answer');
  await page.getByRole('button', { name: 'Update 1 expectation' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Conversation registered.' })).toBeVisible();
  await expect
    .poll(async () => {
      const r = await request.post(`${ORIGIN}/v1/messages`, {
        data: { model: 'claude-test', max_tokens: 64, messages: [{ role: 'user', content: 'hi' }] },
      });
      return JSON.stringify(await r.json());
    })
    .toContain('Edited answer');
  expect((await activeExpectations(request)).map((e) => e['id']).sort()).toEqual(before);
});

test('Import kind loads an expectation-JSON array, and a schema-invalid import shows the server error', async ({ page, request }) => {
  await openAdvanced(page);
  await chooseKind(page, 'Import');
  const content = page.getByLabel('Expectation JSON content');
  await content.fill(JSON.stringify([
    { httpRequest: { path: '/import/a' }, httpResponse: { statusCode: 201 } },
    { httpRequest: { path: '/import/b' }, httpResponse: { statusCode: 202 } },
  ]));
  await page.getByRole('button', { name: 'Import', exact: true }).click();
  await expect.poll(async () => (await activeExpectations(request)).length).toBe(2);
  expect((await request.get(`${ORIGIN}/import/b`)).status()).toBe(202);

  // Server-side validation failure (400) is surfaced, not swallowed.
  allowConsole(page, /status of 400/);
  await content.fill('{"httpRequest":{"path":"/import/bad"},"httpResponse":{"statusCode":"not-a-number"}}');
  await page.getByRole('button', { name: 'Import', exact: true }).click();
  await expect(page.getByRole('alert').filter({ hasText: /incorrect|invalid|schema|failed/i }).first()).toBeVisible();
  expect(await activeExpectations(request)).toHaveLength(2);
});

// ---------------------------------------------------------------------------
// Existing mocks: select → edit → save → delete
// ---------------------------------------------------------------------------

test('Existing mock: select from the list, edit in place (diff shown, unmodelled fields kept), then delete it', async ({ page, request }) => {
  const id = `e2e-edit-${Date.now()}`;
  const path = `/edit/${Date.now()}`;
  await upsert(request, {
    id,
    httpRequest: { method: 'GET', path },
    httpResponse: { statusCode: 200, body: 'v1', reasonPhrase: 'Fine' },
    priority: 7,
  });
  await openAdvanced(page);
  const list = page.getByTestId('existing-mocks-list');
  await expect(list).toContainText('Existing HTTP mocks');
  await list.getByRole('button', { name: new RegExp(path) }).click();

  // Prefilled from the server's copy.
  await expect(page.getByLabel('Expectation ID (optional)')).toHaveValue(id);
  await expect(page.getByLabel('Path', { exact: true })).toHaveValue(path);
  await expect(page.getByLabel('Status code', { exact: true })).toHaveValue('200');
  await expect(list.getByText(/^Editing /)).toBeVisible();
  await expect(registerButton(page)).toHaveText('Update expectation');

  await page.getByLabel('Status code', { exact: true }).fill('299');
  // The before → after diff of the pending update is rendered.
  await expect(page.getByTestId('standard-review-diff')).toBeVisible();
  await register(page);

  await expect
    .poll(async () => {
      const all = await activeExpectations(request);
      return all.length === 1 && all[0]!['id'] === id ? action(all[0]!, 'httpResponse')['statusCode'] : JSON.stringify(all);
    })
    .toBe(299);
  const [updated] = await activeExpectations(request);
  expect(updated!['priority']).toBe(7);
  expect(action(updated!, 'httpResponse')['reasonPhrase']).toBe('Fine');
  expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(299);

  // "New / clear" drops the selection and empties the form.
  await page.getByRole('button', { name: 'New / clear' }).click();
  await expect(page.getByLabel('Expectation ID (optional)')).toHaveValue('');
  await expect(registerButton(page)).toHaveText('Register expectation');

  // Delete — from the Active Expectations panel, with a confirmation.
  await page.evaluate(() => {
    window.location.hash = '#/dashboard';
  });
  const row = page.getByRole('button', { name: 'Delete expectation' }).filter({ visible: true });
  await expect(row).toHaveCount(1);
  await row.click();
  const confirm = page.getByRole('dialog', { name: 'Delete this expectation?' });
  await expect(confirm).toContainText(id);
  await confirm.getByRole('button', { name: 'Delete', exact: true }).click();
  await expect.poll(async () => (await activeExpectations(request)).length).toBe(0);
  expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(404);
});

test('E2E-MOCK-4: leaving an edit (New / clear, or navigating away) does not throw from the diff viewer', async ({ page, request }) => {
  await upsert(request, { id: 'e2e-diff', httpRequest: { path: '/diff/one' }, httpResponse: { statusCode: 200 } });
  await openAdvanced(page);
  const list = page.getByTestId('existing-mocks-list');
  await list.getByRole('button', { name: /\/diff\/one/ }).click();
  await expect(page.getByTestId('standard-review-diff').locator('.monaco-diff-editor')).toBeVisible();
  await page.getByRole('button', { name: 'New / clear' }).click();
  await expect(page.getByTestId('standard-review-diff')).toHaveCount(0);

  // the same, by navigating away mid-edit
  await list.getByRole('button', { name: /\/diff\/one/ }).click();
  await expect(page.getByTestId('standard-review-diff').locator('.monaco-diff-editor')).toBeVisible();
  await page.evaluate(() => {
    window.location.hash = '#/dashboard';
  });
  await expect(page.getByTestId('standard-review-diff')).toHaveCount(0);
  // the model disposal is deferred a tick; give a late throw the chance to surface
  await page.evaluate(() => new Promise((r) => setTimeout(r, 250)));
  // afterEach fails the test on an uncaught
  // "TextModel got disposed before DiffEditorWidget model got reset".
});

test('Existing mock: Edit from Active Expectations opens it in the composer', async ({ page, request }) => {
  const id = `e2e-edit-row-${Date.now()}`;
  await upsert(request, { id, httpRequest: { path: '/edit/from-row' }, httpResponse: { statusCode: 201 } });
  await gotoView(page, 'dashboard');
  await page.getByRole('button', { name: 'Edit expectation' }).filter({ visible: true }).click();
  await expect(page).toHaveURL(/#\/composer$/);
  await expect(page.getByLabel('Path', { exact: true })).toHaveValue('/edit/from-row');
  await expect(page.getByLabel('Status code', { exact: true })).toHaveValue('201');
});

test('Existing mock: a Quick "Update mock" on a forward expectation keeps the forward (no silent overwrite), and Duplicate creates a new id', async ({ page, request }) => {
  const id = `e2e-quick-fwd-${Date.now()}`;
  await upsert(request, { id, httpRequest: { path: '/quick/keep-forward' }, httpForward: { host: 'upstream.invalid', port: 8080, scheme: 'HTTP' } });
  await gotoView(page, 'dashboard');
  await page.getByRole('button', { name: 'Edit expectation' }).filter({ visible: true }).click();
  await expect(page).toHaveURL(/#\/composer$/);
  await page.getByRole('button', { name: 'Quick mock', exact: true }).click();
  const quick = page.getByTestId('quick-mock-form');
  await expect(quick.getByTestId('quick-preserved-fields-info')).toContainText('httpForward');
  await quick.getByLabel('Path', { exact: true }).fill('/quick/keep-forward-v2');
  await page.getByRole('button', { name: 'Update mock' }).click();
  await expect(page.getByTestId('register-success')).toBeVisible();
  await expect
    .poll(async () => {
      const all = await activeExpectations(request);
      return all.length === 1 ? `${all[0]!['id']} ${(all[0]!['httpRequest'] as Json)['path']} ${'httpForward' in all[0]!} ${'httpResponse' in all[0]!}` : all.length;
    })
    .toBe(`${id} /quick/keep-forward-v2 true false`);

  // Duplicate → composer with a blank id → a second, independent expectation.
  await page.evaluate(() => {
    window.location.hash = '#/dashboard';
  });
  await page.getByRole('button', { name: 'Duplicate expectation' }).filter({ visible: true }).click();
  await expect(page).toHaveURL(/#\/composer$/);
  await expect(page.getByLabel('Expectation ID (optional)')).toHaveValue('');
  await page.getByRole('button', { name: /^(Register expectation|Register mock)$/ }).click();
  await expect.poll(async () => (await activeExpectations(request)).length).toBe(2);
});

test('Existing mock: hostile and unicode paths render as text and load into the form verbatim', async ({ page, request }) => {
  const hostile = '/xss/<img src=x onerror="window.__e2eXss=1">';
  const unicode = `/unicode/ünï-cödé-✓-${'x'.repeat(200)}`;
  await upsert(request, [
    { httpRequest: { path: hostile }, httpResponse: { statusCode: 200, body: '<script>window.__e2eXss=2</script>' } },
    { httpRequest: { path: unicode }, httpResponse: { statusCode: 200 } },
  ]);
  await openAdvanced(page);
  const list = page.getByTestId('existing-mocks-list');
  await list.getByRole('button', { name: /onerror/ }).click();
  await expect(page.getByLabel('Path', { exact: true })).toHaveValue(hostile);
  await page.getByRole('button', { name: 'New / clear' }).click();
  await list.getByRole('button', { name: /ünï-cödé/ }).click();
  await expect(page.getByLabel('Path', { exact: true })).toHaveValue(unicode);
  expect(await page.evaluate(() => (window as unknown as { __e2eXss?: number }).__e2eXss)).toBeUndefined();
});

test('E2E-MOCK-7: the composer\'s existing-mocks list reaches every mock (or says it is truncated) past 100', async ({ page, request }) => {
  await upsert(
    request,
    Array.from({ length: 120 }, (_, i) => ({
      id: `many-${String(i).padStart(3, '0')}`,
      httpRequest: { path: `/many/${String(i).padStart(3, '0')}` },
      httpResponse: { statusCode: 200 + i },
    })),
  );
  await openAdvanced(page);
  const list = page.getByTestId('existing-mocks-list');
  // The live window holds 100; the list says so instead of silently stopping.
  await expect(list.getByRole('button', { name: /\/many\// })).toHaveCount(100);
  await expect(list.getByTestId('existing-mocks-window-notice')).toHaveText(
    /This list holds the 100 expectations the dashboard keeps live, of 120 on the server\./,
  );
  // Every mock is reachable by search: each one outside the window is found on the server and loads.
  const shown = new Set(
    (await list.getByRole('button', { name: /\/many\// }).allTextContents()).map((t) => t.match(/\/many\/(\d{3})/)![1]),
  );
  const hidden = Array.from({ length: 120 }, (_, i) => String(i).padStart(3, '0')).filter((n) => !shown.has(n));
  expect(hidden).toHaveLength(20);
  const target = hidden[hidden.length - 1]!;
  await list.getByLabel('Search by path or id').fill(`/many/${target}`);
  await expect(list.getByRole('button', { name: /\/many\// })).toHaveCount(1);
  await list.getByRole('button', { name: new RegExp(`/many/${target}`) }).click();
  await expect(page.getByLabel('Expectation ID (optional)')).toHaveValue(`many-${target}`);
  await expect(page.getByLabel('Path', { exact: true })).toHaveValue(`/many/${target}`);
  await expect(page.getByLabel('Status code', { exact: true })).toHaveValue(String(200 + Number(target)));
});

test('Quick mock: a 256 KiB response body pasted into the real editor round-trips to the server', async ({ page, request }) => {
  const path = `/quick/large-${Date.now()}`;
  const payload = JSON.stringify({ blob: 'x'.repeat(256 * 1024) });
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write'], { origin: ORIGIN });
  await gotoView(page, 'composer');
  const quick = page.getByTestId('quick-mock-form');
  await quick.getByLabel('Path', { exact: true }).fill(path);
  await expect(quick.locator('.monaco-editor .view-lines')).toBeVisible();
  // Paste, as a user would: keyboard.insertText of this size stalls Monaco's
  // EditContext input path in headless Chrome (a harness limit, not a UI one).
  await page.evaluate((t) => navigator.clipboard.writeText(t), payload);
  await quick.locator('.monaco-editor').click();
  await page.keyboard.press('ControlOrMeta+v');
  await page.getByRole('button', { name: 'Register mock' }).click();
  await expect(page.getByTestId('register-success')).toBeVisible();
  const served = await request.get(`${ORIGIN}${path}`);
  expect((await served.text()).length).toBe(payload.length);
});

// ---------------------------------------------------------------------------
// Test Matcher / Try It / code tabs
// ---------------------------------------------------------------------------

test('Test Matcher dry-runs the draft matcher in the browser: WOULD MATCH vs WOULD NOT MATCH', async ({ page }) => {
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill('/tm/orders');
  await page.getByRole('button', { name: 'Test Matcher' }).click();
  const dialog = page.getByRole('dialog', { name: 'Matcher Test Playground' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel('Candidate expectation JSON')).toHaveValue(/\/tm\/orders/);

  await dialog.getByLabel('Sample method').fill('GET');
  await dialog.getByLabel('Sample path').fill('/tm/orders');
  await dialog.getByRole('button', { name: 'Test request' }).click();
  await expect(dialog.getByText('WOULD MATCH', { exact: true })).toBeVisible();

  await dialog.getByLabel('Sample path').fill('/tm/other');
  await dialog.getByRole('button', { name: 'Test request' }).click();
  await expect(dialog.getByText('WOULD NOT MATCH', { exact: true })).toBeVisible();

  // Malformed candidate JSON is flagged, not thrown.
  await dialog.getByLabel('Candidate expectation JSON').fill('{ nope');
  await expect(dialog.getByText(/JSON|parse|Unexpected/i).first()).toBeVisible();
  await dialog.getByRole('button', { name: 'Close' }).last().click();
  await expect(dialog).toBeHidden();
});

test('Try It fires the draft request at the live server and shows the real response', async ({ page, request }) => {
  const path = `/tryit/${Date.now()}`;
  await upsert(request, { httpRequest: { path }, httpResponse: { statusCode: 207, body: 'live-body' } });
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill(path);
  await page.getByRole('button', { name: 'Try It' }).click();
  const widget = page.getByTestId('live-response-widget');
  await expect(widget.getByLabel('Live request path')).toHaveValue(path);
  await widget.getByRole('button', { name: 'Send request' }).click();
  const result = widget.getByTestId('try-it-result');
  await expect(result).toContainText('207');
  await expect(result).toContainText('live-body');
  await page.getByRole('button', { name: 'Hide Try It' }).click();
  await expect(widget).toBeHidden();
});

test('Code tabs: every language renders code for the draft, and the JSON tab is exactly what gets registered', async ({ page, request }) => {
  const path = `/codegen/${Date.now()}`;
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill(path);
  await page.getByLabel('Status code', { exact: true }).fill('203');
  const tabs = ['Java', 'Node.js', 'Python', 'Go', 'C#', 'Ruby', 'Rust', 'JSON', 'curl'];
  const review = page.locator('.MuiPaper-root').filter({ has: page.getByText('4 · Review & register') });
  const code = review.locator('pre').last();
  for (const tab of tabs) {
    await review.getByRole('tab', { name: tab, exact: true }).click();
    await expect(review.getByRole('tab', { name: tab, exact: true })).toHaveAttribute('aria-selected', 'true');
    await expect(code, `${tab} tab should mention the path`).toContainText(path);
    await expect(code, `${tab} tab should mention the status`).toContainText('203');
  }
  await review.getByRole('tab', { name: 'curl', exact: true }).click();
  await expect(code).toContainText(`${ORIGIN}/mockserver/expectation`);

  await review.getByRole('tab', { name: 'JSON', exact: true }).click();
  const shown = JSON.parse((await code.textContent()) ?? '') as Json | Json[];
  // Register the JSON tab's content ourselves and compare to what the UI registers.
  await register(page);
  const viaUi = await onlyExpectation(request);
  const fromTab = (Array.isArray(shown) ? shown[0] : shown) as Json;
  expect(fromTab['httpRequest']).toEqual(viaUi['httpRequest']);
  expect(fromTab['httpResponse']).toEqual(viaUi['httpResponse']);
});

// ---------------------------------------------------------------------------
// Validation + error paths
// ---------------------------------------------------------------------------

test('Advanced: malformed JSON body matcher blocks Register with the reason', async ({ page }) => {
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill('/validate/json');
  await selectOption(page, 'Body type', 'JSON');
  const bodyEditor = page.locator('.MuiPaper-root').filter({ has: page.getByText('1 · Match a request') }).locator('.monaco-editor').first();
  await bodyEditor.click();
  await page.keyboard.insertText('{"a": ');
  await expect(registerButton(page)).toBeDisabled();
  await registerButton(page).hover({ force: true });
  await expect(page.getByRole('tooltip')).toHaveText('JSON body matcher is not valid JSON');
  await page.keyboard.insertText('1}');
  await expect(registerButton(page)).toBeEnabled();
});

test('E2E-MOCK-3: an empty or out-of-range response status code is rejected before registering', async ({ page, request }) => {
  await gotoView(page, 'composer');
  const quick = page.getByTestId('quick-mock-form');
  const status = quick.getByLabel('Status code', { exact: true });
  const registerMock = page.getByRole('button', { name: 'Register mock' });
  await quick.getByLabel('Path', { exact: true }).fill('/validate/status');
  await expect(registerMock).toBeEnabled();
  for (const bad of ['', '0', '-5', '1000']) {
    await status.fill(bad);
    await expect(status).toHaveValue(bad);
    await expect(quick.getByText('Status code must be a whole number from 100 to 999')).toBeVisible();
    await expect(registerMock).toBeDisabled();
  }
  await status.fill('999');
  await expect(registerMock).toBeEnabled();

  // The server refuses one too, so no client can register a mock answering "HTTP/1.1 0".
  for (const statusCode of [0, -5, 1000]) {
    const res = await request.put(`${ORIGIN}/mockserver/expectation`, {
      data: { httpRequest: { path: '/validate/status' }, httpResponse: { statusCode } },
    });
    expect(res.status()).toBe(400);
    expect(await res.text()).toContain('$.httpResponse.statusCode');
  }
  expect(await activeExpectations(request)).toEqual([]);
});

// ---------------------------------------------------------------------------
// Scenarios (state machines)
// ---------------------------------------------------------------------------

async function scenarioState(request: APIRequestContext, name: string): Promise<string> {
  const res = await request.get(`${ORIGIN}/mockserver/scenario/${encodeURIComponent(name)}`);
  expect(res.ok()).toBeTruthy();
  return ((await res.json()) as Json)['currentState'] as string;
}

async function seedCheckoutScenario(request: APIRequestContext, name: string): Promise<void> {
  await upsert(request, [
    { httpRequest: { path: `/${name}/pay` }, httpResponse: { statusCode: 200, body: 'paid' }, scenarioName: name, scenarioState: 'Started', newScenarioState: 'Paid' },
    { httpRequest: { path: `/${name}/pay` }, httpResponse: { statusCode: 409, body: 'already paid' }, scenarioName: name, scenarioState: 'Paid' },
  ]);
}

test('Scenarios: lists a scenario with its bound mocks, follows live traffic, and Set / Trigger drive the server state machine', async ({ page, request }) => {
  const name = `checkout${Date.now()}`;
  await seedCheckoutScenario(request, name);
  await gotoView(page, 'scenarios');
  await expect(page.getByText('Scenario Details')).toBeVisible();
  await expect(page.getByText('2 mocks')).toBeVisible();

  // Real traffic advances the state machine; List picks it up.
  expect((await request.get(`${ORIGIN}/${name}/pay`)).status()).toBe(200);
  await page.getByRole('button', { name: 'List', exact: true }).click();
  // The chip's accessible name comes from its tooltip: "<name> → <state>".
  const chip = page.getByRole('button', { name: `${name} → Paid` });
  await expect(chip).toBeVisible();
  await chip.click();
  await expect(page.getByPlaceholder('Scenario name')).toHaveValue(name);
  await expect(page.getByTestId('scenario-state-graph-svg')).toBeVisible();

  // Set State → back to Started; the mock answers 200 again.
  await page.getByPlaceholder('State', { exact: true }).fill('Started');
  await page.getByRole('button', { name: 'Set', exact: true }).click();
  await expect.poll(() => scenarioState(request, name)).toBe('Started');
  expect((await request.get(`${ORIGIN}/${name}/pay`)).status()).toBe(200);
  await expect.poll(() => scenarioState(request, name)).toBe('Paid');

  // Trigger → confirm dialog → forced transition.
  await page.getByPlaceholder('New state').fill('Started');
  await page.getByRole('button', { name: 'Trigger', exact: true }).click();
  const confirm = page.getByRole('dialog', { name: 'Trigger scenario transition?' });
  await expect(confirm).toContainText(name);
  await confirm.getByRole('button', { name: 'Trigger transition' }).click();
  await expect.poll(() => scenarioState(request, name)).toBe('Started');
  await page.getByRole('button', { name: 'Refresh', exact: true }).click();
  await expect(page.getByText('Current state:').locator('..')).toContainText('Started');
});

test('Scenarios: a timed transition shows a countdown and the server advances on its own', async ({ page, request }) => {
  const name = `timed${Date.now()}`;
  await gotoView(page, 'scenarios');
  await page.getByPlaceholder('Scenario name').fill(name);
  await page.getByPlaceholder('State', { exact: true }).fill('Waiting');
  await page.getByPlaceholder('Delay (ms)').fill('2000');
  await page.getByPlaceholder('Next state').fill('Done');
  await page.getByRole('button', { name: 'Set', exact: true }).click();
  await expect(page.getByText(/-> Done$/)).toBeVisible();
  expect(await scenarioState(request, name)).toBe('Waiting');
  await expect.poll(() => scenarioState(request, name), { timeout: 10_000 }).toBe('Done');
});

test('Scenarios: the composer binds a new mock to a scenario, and "Edit scenario" opens it in the composer', async ({ page, request }) => {
  const name = `bound${Date.now()}`;
  await openAdvanced(page);
  await page.getByLabel('Path', { exact: true }).fill(`/${name}`);
  const section = page.getByTestId('scenario-section');
  await section.getByLabel('Scenario Name').fill(name);
  await section.getByLabel('Required State').fill('Started');
  await section.getByLabel('Transition To').fill('Next');
  await register(page);
  const exp = await onlyExpectation(request);
  expect(exp).toMatchObject({ scenarioName: name, scenarioState: 'Started', newScenarioState: 'Next' });

  // The Mocks view's own Scenarios tab shows the same panel.
  await page.getByRole('tab', { name: 'Scenarios' }).click();
  await expect(page.getByText('Scenario State Machine', { exact: true })).toBeVisible();
  await expect(page.getByText('1 mock', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Edit scenario' }).click();
  await expect(page.getByRole('tab', { name: 'Compose' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByLabel('Path', { exact: true })).toHaveValue(`/${name}`);
  await expect(page.getByTestId('scenario-section').getByLabel('Scenario Name')).toHaveValue(name);
});

// ---------------------------------------------------------------------------
// gRPC services view
// ---------------------------------------------------------------------------

test.describe('gRPC view', () => {
  // Descriptors survive /mockserver/reset, so clear them explicitly around each test.
  test.beforeEach(async ({ request }) => {
    expect((await request.put(`${ORIGIN}/mockserver/grpc/clear`)).ok()).toBeTruthy();
  });
  test.afterEach(async ({ request }) => {
    await request.put(`${ORIGIN}/mockserver/grpc/clear`);
  });

  test('shows the empty state, then the services, methods and streaming kinds of a loaded descriptor set', async ({ page, request }) => {
    await gotoView(page, 'grpc');
    await expect(page.getByText('0 service(s)')).toBeVisible();
    await expect(page.getByText(/No gRPC services loaded/)).toBeVisible();

    const descriptor = readFileSync(resolve(REPO_ROOT, 'mockserver/mockserver-core/src/test/resources/grpc/greeting.dsc'));
    const loaded = await request.put(`${ORIGIN}/mockserver/grpc/descriptors`, {
      headers: { 'Content-Type': 'application/octet-stream' },
      data: descriptor,
    });
    expect(loaded.status()).toBe(201);

    await page.getByRole('button', { name: 'Refresh gRPC services' }).click();
    await expect(page.getByText('1 service(s)')).toBeVisible();
    await expect(page.getByText('4 method(s)').first()).toBeVisible();
    await expect(page.getByText('com.example.grpc.GreetingService')).toBeVisible();
    const kinds: Array<[string, string]> = [
      ['Greeting', 'unary'],
      ['ListGreetings', 'server stream'],
      ['CollectGreetings', 'client stream'],
      ['Chat', 'bidi stream'],
    ];
    for (const [method, kind] of kinds) {
      const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: method, exact: true }) });
      await expect(row).toContainText(kind);
      await expect(row).toContainText('com.example.grpc.HelloRequest');
    }
    await expect(page.getByText('server SERVING')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Async (AsyncAPI broker mock) — everything that works without a live broker
// ---------------------------------------------------------------------------

const ORDERS_SPEC = [
  'asyncapi: 3.0.0',
  'info:',
  '  title: Orders',
  '  version: 1.0.0',
  'channels:',
  '  orders:',
  '    address: orders',
  '    messages:',
  '      orderCreated:',
  '        payload:',
  '          type: object',
  '          properties:',
  '            id:',
  '              type: string',
  '        examples:',
  '          - payload:',
  '              id: "abc"',
].join('\n');

async function openAsyncDialog(page: Page): Promise<Locator> {
  await page.getByRole('button', { name: 'Import / export tools' }).click();
  await page.getByRole('menuitem', { name: /AsyncAPI Broker Mock/ }).click();
  const dialog = page.getByRole('dialog', { name: 'AsyncAPI Broker Mock' });
  await expect(dialog).toBeVisible();
  return dialog;
}

test('Async: empty state, then a spec loaded from the Tools dialog shows its channels in the Async view', async ({ page }) => {
  await gotoView(page, 'async');
  await expect(page.getByText('no spec loaded')).toBeVisible();
  await expect(page.getByText(/No channels loaded/)).toBeVisible();
  await expect(page.getByText(/Load an AsyncAPI spec with consumer subscriptions/)).toBeVisible();

  const dialog = await openAsyncDialog(page);
  await expect(dialog.getByRole('button', { name: 'Load spec' })).toBeEnabled();
  await dialog.getByLabel('AsyncAPI spec (JSON / YAML)').fill(ORDERS_SPEC);
  await dialog.getByRole('button', { name: 'Load spec' }).click();
  await expect(dialog.getByRole('alert').filter({ hasText: 'AsyncAPI spec loaded.' })).toBeVisible();
  await dialog.getByRole('button', { name: 'Close' }).click();
  await expect(dialog).toBeHidden();

  await page.getByRole('button', { name: 'Refresh async status' }).click();
  await expect(page.getByText(/^Orders · AsyncAPI /)).toBeVisible();
  const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'orders', exact: true }) });
  await expect(row).toBeVisible();
  await expect(row).toContainText('1'); // one example message
  await expect(page.getByText('0 publisher(s)')).toBeVisible();
  await expect(page.getByText('0 subscriber(s)')).toBeVisible();
  await expect(page.getByText('0 recorded message(s)')).toBeVisible();
});

test('Async: "Generate HTTP expectations" turns channel examples into served HTTP mocks', async ({ page, request }) => {
  await gotoView(page, 'async');
  const dialog = await openAsyncDialog(page);
  await dialog.getByLabel('Generate HTTP expectations').check();
  await dialog.getByLabel('AsyncAPI spec (JSON / YAML)').fill(ORDERS_SPEC);
  await dialog.getByRole('button', { name: 'Generate expectations' }).click();
  await expect(dialog.getByRole('alert').filter({ hasText: 'Generated 1 HTTP expectation.' })).toBeVisible();
  const served = await request.get(`${ORIGIN}/orders`);
  expect(served.status()).toBe(200);
  expect(await served.json()).toEqual({ id: 'abc' });
});

test('Async: verify messages reports pass / fail from the server, and a broken spec is refused with an error', async ({ page }) => {
  await gotoView(page, 'async');
  const dialog = await openAsyncDialog(page);
  await dialog.getByLabel('AsyncAPI spec (JSON / YAML)').fill(ORDERS_SPEC);
  await dialog.getByRole('button', { name: 'Load spec' }).click();
  await expect(dialog.getByRole('alert').filter({ hasText: 'AsyncAPI spec loaded.' })).toBeVisible();

  allowConsole(page, /status of 406/, /status of 400/);
  const verifyBody = dialog.getByLabel('Verification request (JSON)');
  await verifyBody.fill('{"channel":"orders","count":{"atLeast":1}}');
  await dialog.getByRole('button', { name: 'Verify messages' }).click();
  await expect(dialog.getByRole('alert').filter({ hasText: "expected at least 1 message(s) matching channel 'orders' but found 0" })).toBeVisible();

  await verifyBody.fill('{"channel":"orders","count":{"atMost":0}}');
  await dialog.getByRole('button', { name: 'Verify messages' }).click();
  await expect(dialog.getByRole('alert').filter({ hasText: 'Verified — the observed messages satisfy the request.' })).toBeVisible();

  // A broken spec is refused with an error alert (its wording: see E2E-MOCK-6).
  await dialog.getByLabel('AsyncAPI spec (JSON / YAML)').fill('garbage: [');
  await dialog.getByRole('button', { name: 'Load spec' }).click();
  await expect(dialog.getByRole('alert').filter({ hasText: /failed to load AsyncAPI spec/ })).toBeVisible();
});

test('E2E-MOCK-6: a broken AsyncAPI spec shows the server\'s parse error, not just "HTTP 400 Bad Request"', async ({ page }) => {
  allowConsole(page, /status of 400/);
  await gotoView(page, 'async');
  const dialog = await openAsyncDialog(page);
  await dialog.getByLabel('AsyncAPI spec (JSON / YAML)').fill('garbage: [');
  await dialog.getByRole('button', { name: 'Load spec' }).click();
  // The server's 400 body is JSON whose message keeps the parser's position and the bad line.
  const alert = dialog.getByRole('alert').filter({ hasText: /failed to load AsyncAPI spec/ });
  await expect(alert).toBeVisible();
  await expect(alert).toContainText('garbage: [');
  await expect(alert).not.toContainText('HTTP 400');
});

test('E2E-MOCK-1: the Verify Messages placeholder shape is honoured (top-level atMost is not silently ignored)', async ({ page }) => {
  await gotoView(page, 'async');
  const dialog = await openAsyncDialog(page);
  await dialog.getByLabel('AsyncAPI spec (JSON / YAML)').fill(ORDERS_SPEC);
  await dialog.getByRole('button', { name: 'Load spec' }).click();
  await expect(dialog.getByRole('alert').filter({ hasText: 'AsyncAPI spec loaded.' })).toBeVisible();
  allowConsole(page, /status of 400/);
  const verifyBody = dialog.getByLabel('Verification request (JSON)');
  // The placeholder teaches the shape the server reads: count constraints under "count".
  await expect(verifyBody).toHaveAttribute('placeholder', /"count": \{ "atLeast": 1 \}/);
  // A count field at the top level used to be ignored silently (checked as "at least 1"); now it is refused.
  await verifyBody.fill('{"channel":"orders","atMost":0}');
  await dialog.getByRole('button', { name: 'Verify messages' }).click();
  await expect(dialog.getByRole('alert').filter({ hasText: `'atMost' must be inside "count"` })).toBeVisible();
  // The placeholder's shape, with atMost, is honoured.
  await verifyBody.fill('{"channel":"orders","count":{"atMost":0}}');
  await dialog.getByRole('button', { name: 'Verify messages' }).click();
  await expect(dialog.getByRole('alert').filter({ hasText: 'Verified' })).toBeVisible();
});

test('E2E-MOCK-5/8: the Async header does not claim "connected" with no broker, and labels the AsyncAPI version', async ({ page, request }) => {
  // a Buffer, so the YAML goes as is (a string `data` would be sent JSON-encoded)
  const res = await request.put(`${ORIGIN}/mockserver/asyncapi`, { data: Buffer.from(ORDERS_SPEC), headers: { 'Content-Type': 'application/yaml' } });
  expect(res.status()).toBe(201);
  await gotoView(page, 'async');
  await expect(page.getByText('0 publisher(s)')).toBeVisible();
  await expect(page.getByText('connected', { exact: true }).filter({ visible: true })).toHaveCount(1); // only the AppBar's
  await expect(page.getByText('spec loaded, no broker', { exact: true })).toBeVisible();
  // "3.0.0" is the AsyncAPI document version, not the Orders API's own version (1.0.0)
  await expect(page.getByText('Orders · AsyncAPI 3.0.0', { exact: true })).toBeVisible();
});

// ---------------------------------------------------------------------------
// Light theme + a narrow window
// ---------------------------------------------------------------------------

test.describe('narrow window, light theme', () => {
  test.use({ viewport: { width: 1024, height: 700 } });

  async function noHorizontalPageScroll(page: Page): Promise<void> {
    const overflow = await page.evaluate(() => {
      const el = document.scrollingElement ?? document.documentElement;
      return el.scrollWidth - el.clientWidth;
    });
    expect(overflow, 'page scrolls horizontally').toBeLessThanOrEqual(0);
  }

  test('the Mock views work from the hamburger menu in light mode at 1024×700', async ({ page, request }) => {
    await gotoView(page, 'get-started');
    await page.getByRole('button', { name: 'Switch to light mode' }).click();
    await expect(page.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible();
    const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(bg, 'light theme body background').not.toBe('rgb(18, 18, 18)');

    // Below the lg breakpoint the grouped nav collapses into one hamburger menu.
    await page.getByRole('button', { name: 'Open navigation menu' }).click();
    await page.getByRole('menuitem', { name: 'Mocks view' }).click();
    await expect(page).toHaveURL(/#\/composer$/);
    await noHorizontalPageScroll(page);
    await page.screenshot({ path: test.info().outputPath('composer-quick-light-1024.png') });

    const path = `/narrow/${Date.now()}`;
    const quick = page.getByTestId('quick-mock-form');
    await quick.getByLabel('Path', { exact: true }).fill(path);
    await page.getByRole('button', { name: 'Register mock' }).click();
    await expect(page.getByTestId('register-success')).toBeVisible();
    expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(200);

    await page.getByRole('button', { name: 'Advanced', exact: true }).click();
    await expect(page.getByText('Expectation kind')).toBeVisible();
    await noHorizontalPageScroll(page);

    for (const view of ['Scenarios view', 'gRPC services view', 'AsyncAPI broker mock view', 'Get started view']) {
      await page.getByRole('button', { name: 'Open navigation menu' }).click();
      await page.getByRole('menuitem', { name: view }).click();
      await noHorizontalPageScroll(page);
    }
  });
});
