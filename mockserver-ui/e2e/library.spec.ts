import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { requireOrSkip } from './ci-guard';
import { readFile } from 'node:fs/promises';
import http from 'node:http';
import net from 'node:net';
import { fileURLToPath } from 'node:url';

// Library, Inspect menu, and proxy record & replay — driven in a real browser
// against a REAL MockServer (plus a second MockServer acting as the proxied
// upstream for the record/replay tests).
//
//   Library → Import   every format (Expectation JSON, OpenAPI inline/YAML/file/
//                      URL, WSDL, HAR, Postman) through the form, then the
//                      generated mock is served over the wire
//   Library → WASM / gRPC Descriptors   upload, inspect/dry-run, delete/clear
//   Library → Cassettes record forwarded traffic to a server file, load it back
//   Library → Export   every scope × format the dropdown offers is downloaded;
//                      JSON / HAR / Postman / OpenAPI downloads are re-imported
//                      through the Import tab and the mocks they recreate are hit
//   Inspect            Breakpoints (pause + continue a proxied request), Audit
//                      (enable, see a mutation, search), Library, Cluster
//   Record & replay    requests sent through MockServer as a forward proxy to the
//                      upstream are recorded, promoted to mocks from the Traffic
//                      view, and then replayed by MockServer with the upstream gone
//
// The upstream is the secondary server the e2e config boots, 127.0.0.1:1114
// (E2E_UPSTREAM_HOST / E2E_UPSTREAM_PORT). The tests that need it skip when it
// is not reachable, and fail instead in CI.

const HOST = process.env.E2E_MS_HOST || '127.0.0.1';
const PORT = process.env.E2E_MS_PORT || '1084';
const ORIGIN = `http://${HOST}:${PORT}`;
const UP_HOST = process.env.E2E_UPSTREAM_HOST || '127.0.0.1';
const UP_PORT = process.env.E2E_UPSTREAM_PORT || '1114';
const UP_ORIGIN = `http://${UP_HOST}:${UP_PORT}`;

// ---------------------------------------------------------------------------
// Fixtures (inline so the spec is self-contained in CI)
// ---------------------------------------------------------------------------

const OPENAPI_JSON = JSON.stringify({
  openapi: '3.0.0',
  info: { title: 'E2E Library Petstore', version: '1.0.0' },
  paths: {
    '/lib-oas/pets': {
      get: {
        operationId: 'listPets',
        responses: { '200': { description: 'ok', content: { 'application/json': { example: [{ id: 1, name: 'rex' }] } } } },
      },
    },
    '/lib-oas/pets/{petId}': {
      get: {
        operationId: 'getPet',
        parameters: [{ name: 'petId', in: 'path', required: true, schema: { type: 'string' } }],
        responses: { '200': { description: 'ok', content: { 'application/json': { example: { id: 7, name: 'tom' } } } } },
      },
    },
  },
}, null, 2);

const OPENAPI_YAML = `openapi: 3.0.0
info:
  title: E2E Library Yaml
  version: 1.0.0
paths:
  /lib-yaml/hello:
    get:
      operationId: hello
      responses:
        '200':
          description: ok
          content:
            application/json:
              example:
                greeting: hi
`;

const POSTMAN = JSON.stringify({
  info: { name: 'E2E Library Postman', schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json' },
  item: [{
    name: 'get widget',
    request: { method: 'GET', url: { raw: 'http://example.com/lib-postman/widget', host: ['example', 'com'], path: ['lib-postman', 'widget'] } },
    response: [{
      name: 'ok', code: 200, header: [{ key: 'Content-Type', value: 'application/json' }], body: '{"widget":"postman"}',
      originalRequest: { method: 'GET', url: { raw: 'http://example.com/lib-postman/widget' } },
    }],
  }],
});

function harFor(path: string, query: string, body: string): string {
  return JSON.stringify({
    log: {
      version: '1.2',
      creator: { name: 'e2e', version: '1' },
      entries: [{
        startedDateTime: '2026-10-10T00:00:00.000Z',
        time: 1,
        request: {
          method: 'GET', url: `http://example.com${path}?${query}`, httpVersion: 'HTTP/1.1', headers: [],
          queryString: query.split('&').map((kv) => ({ name: kv.split('=')[0], value: kv.split('=')[1] })),
          cookies: [], headersSize: -1, bodySize: 0,
        },
        response: {
          status: 200, statusText: 'OK', httpVersion: 'HTTP/1.1', headers: [{ name: 'Content-Type', value: 'application/json' }],
          cookies: [], content: { size: body.length, mimeType: 'application/json', text: body }, redirectURL: '', headersSize: -1, bodySize: body.length,
        },
        cache: {}, timings: { send: 0, wait: 1, receive: 0 },
      }],
    },
  });
}

const WSDL = `<?xml version="1.0" encoding="UTF-8"?>
<definitions name="LibCalc" targetNamespace="http://example.com/libcalc"
  xmlns="http://schemas.xmlsoap.org/wsdl/" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
  xmlns:tns="http://example.com/libcalc" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
  <types>
    <xsd:schema targetNamespace="http://example.com/libcalc">
      <xsd:element name="Add"><xsd:complexType><xsd:sequence><xsd:element name="a" type="xsd:int"/><xsd:element name="b" type="xsd:int"/></xsd:sequence></xsd:complexType></xsd:element>
      <xsd:element name="AddResponse"><xsd:complexType><xsd:sequence><xsd:element name="result" type="xsd:int"/></xsd:sequence></xsd:complexType></xsd:element>
    </xsd:schema>
  </types>
  <message name="AddRequest"><part name="parameters" element="tns:Add"/></message>
  <message name="AddResponse"><part name="parameters" element="tns:AddResponse"/></message>
  <portType name="LibCalcPortType"><operation name="Add"><input message="tns:AddRequest"/><output message="tns:AddResponse"/></operation></portType>
  <binding name="LibCalcBinding" type="tns:LibCalcPortType">
    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
    <operation name="Add"><soap:operation soapAction="http://example.com/libcalc/Add"/><input><soap:body use="literal"/></input><output><soap:body use="literal"/></output></operation>
  </binding>
  <service name="LibCalcService"><port name="LibCalcPort" binding="tns:LibCalcBinding"><soap:address location="http://example.com/lib-wsdl/calc"/></port></service>
</definitions>
`;

const EXPECTATIONS = JSON.stringify([
  { id: 'lib-json-1', httpRequest: { method: 'GET', path: '/lib-json/one' }, httpResponse: { statusCode: 200, body: 'one' } },
  { id: 'lib-json-2', httpRequest: { method: 'POST', path: '/lib-json/two' }, httpResponse: { statusCode: 201, body: { two: 2 } } },
], null, 2);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

type Json = Record<string, unknown>;

async function activeExpectations(request: APIRequestContext): Promise<Json[]> {
  const res = await request.put(`${ORIGIN}/mockserver/retrieve?type=active_expectations`);
  expect(res.ok(), `retrieve returned ${res.status()}`).toBeTruthy();
  return (await res.json()) as Json[];
}

async function put(request: APIRequestContext, path: string, data: unknown, origin = ORIGIN) {
  const res = await request.put(`${origin}${path}`, { data });
  expect(res.ok(), `PUT ${path} returned ${res.status()}: ${await res.text()}`).toBeTruthy();
  return res;
}

/**
 * Send a request THROUGH MockServer as an HTTP forward proxy (absolute-form
 * request target), the way a client configured with `-x`/`HTTP_PROXY` does.
 */
function viaProxy(
  path: string,
  opts: { method?: string; headers?: Record<string, string>; body?: string } = {},
): Promise<{ status: number; body: string }> {
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        host: HOST,
        port: Number(PORT),
        method: opts.method ?? 'GET',
        path: `${UP_ORIGIN}${path}`,
        headers: { Host: `${UP_HOST}:${UP_PORT}`, ...(opts.headers ?? {}) },
      },
      (res) => {
        let data = '';
        res.setEncoding('utf8');
        res.on('data', (c) => (data += c));
        res.on('end', () => resolve({ status: res.statusCode ?? 0, body: data }));
      },
    );
    req.on('error', reject);
    if (opts.body) req.write(opts.body);
    req.end();
  });
}

/**
 * The exact bytes `curl -x` sends for a proxied GET: no Connection header, a
 * Proxy-Connection header instead. Node's http client always adds Connection, so
 * write the request on a raw socket. Resolves the response status code.
 */
function curlStyleProxyGet(path: string): Promise<number> {
  return new Promise((resolve, reject) => {
    const socket = net.connect(Number(PORT), HOST, () => {
      socket.write(
        `GET ${UP_ORIGIN}${path} HTTP/1.1\r\nHost: ${UP_HOST}:${UP_PORT}\r\n` +
          'User-Agent: curl/8.7.1\r\nAccept: */*\r\nProxy-Connection: Keep-Alive\r\n\r\n',
      );
    });
    let head = '';
    socket.setEncoding('latin1');
    socket.on('data', (chunk: string) => {
      head += chunk;
      const m = /^HTTP\/1\.1 (\d{3})/.exec(head);
      if (m) {
        socket.destroy();
        resolve(Number(m[1]));
      }
    });
    socket.on('error', reject);
  });
}

async function upstreamReachable(request: APIRequestContext): Promise<boolean> {
  try {
    const res = await request.put(`${UP_ORIGIN}/mockserver/status`, { timeout: 3_000 });
    return res.ok();
  } catch {
    return false;
  }
}

// Console/uncaught-error capture. A test that deliberately provokes a server 4xx
// adds the browser's own "Failed to load resource" line to `allowedConsole`.
let consoleErrors: string[] = [];
let allowedConsole: RegExp[] = [];

async function openView(page: Page, view: string) {
  await page.goto(`./#/${view}`);
  await expect(page.getByText('connected', { exact: true })).toBeVisible();
}

async function openLibraryTab(page: Page, tab: 'Import' | 'Export' | 'Cassettes' | 'WASM Modules' | 'gRPC Descriptors') {
  await openView(page, 'library');
  await page.getByRole('tab', { name: tab, exact: true }).click();
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Pick the import format radio by its title (the label also carries a description). */
async function chooseImportFormat(page: Page, label: string) {
  await page.getByRole('radio', { name: new RegExp(`^${escapeRegex(label)}`) }).check();
}

async function importViaForm(page: Page, format: string, content: string) {
  await chooseImportFormat(page, format);
  await page.getByRole('radio', { name: 'Paste', exact: true }).check();
  await page.getByLabel(`${format} content`).fill(content);
  await page.getByRole('button', { name: 'Import', exact: true }).click();
}

async function importFileViaForm(page: Page, format: string, file: { name: string; mimeType: string; content: string }) {
  await chooseImportFormat(page, format);
  await page.getByRole('radio', { name: 'File', exact: true }).check();
  await page.getByTestId('import-file-input').setInputFiles({
    name: file.name,
    mimeType: file.mimeType,
    buffer: Buffer.from(file.content, 'utf8'),
  });
  // The chosen file is loaded into the text area before it is sent.
  await expect(page.getByLabel(`${format} content`)).not.toHaveValue('');
  await page.getByRole('button', { name: 'Import', exact: true }).click();
}

function snackbar(page: Page) {
  return page.locator('.MuiSnackbar-root');
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

test.beforeEach(async ({ page, request }) => {
  consoleErrors = [];
  allowedConsole = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      const where = msg.location()?.url;
      consoleErrors.push(where ? `${msg.text()} [${where}]` : msg.text());
    }
  });
  page.on('pageerror', (err) => consoleErrors.push(`pageerror: ${err.message}`));
  const res = await request.put(`${ORIGIN}/mockserver/reset`);
  expect(res.ok(), `reset returned ${res.status()}`).toBeTruthy();
});

test.afterEach(async () => {
  const unexpected = consoleErrors.filter((e) => !allowedConsole.some((re) => re.test(e)));
  expect(unexpected, 'browser console errors / uncaught exceptions').toEqual([]);
});

// ---------------------------------------------------------------------------
// Inspect menu
// ---------------------------------------------------------------------------

test('the Inspect menu opens every view under it', async ({ page }) => {
  await openView(page, 'library');
  const views: Array<[string, RegExp | string]> = [
    ['Breakpoints view', 'Register a New Breakpoint Matcher'],
    ['Audit trail view', 'Audit Trail'],
    ['Library of captured content', 'Import'],
    ['Cluster status view', 'Node ID'],
  ];
  for (const [ariaLabel, marker] of views) {
    await page.getByRole('button', { name: 'Inspect views' }).click();
    await page.getByRole('menuitem', { name: ariaLabel }).click();
    await expect(page.getByRole('menu')).toHaveCount(0);
    if (ariaLabel === 'Library of captured content') {
      await expect(page.getByRole('tab', { name: 'Import', exact: true })).toBeVisible();
      await expect(page).toHaveURL(/#\/library$/);
    } else {
      await expect(page.getByText(marker, { exact: true }).first()).toBeVisible();
    }
  }
  // Cluster: a standalone server reports itself as the single local member.
  await expect(page.getByText('Single node', { exact: true })).toBeVisible();
  await expect(page.getByText('1 member', { exact: true })).toBeVisible();
  await expect(page.getByRole('cell').getByText('Local', { exact: true })).toBeVisible();
});

test('Library sub-tabs each render their empty state', async ({ page }) => {
  await openView(page, 'library');
  await page.getByRole('tab', { name: 'Export', exact: true }).click();
  await expect(page.getByText('What to export')).toBeVisible();
  await page.getByRole('tab', { name: 'Cassettes', exact: true }).click();
  await expect(page.getByText('No cassettes tracked yet')).toBeVisible();
  // WASM is off by default (wasmEnabled=false): the list call is a 403 and the tab says how to turn it on.
  allowedConsole.push(/status of 403 \(Forbidden\) \[.*\/mockserver\/wasm\/modules\]/);
  await page.getByRole('tab', { name: 'WASM Modules', exact: true }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'WASM rules are turned off on this server' })).toBeVisible();
  await page.getByRole('tab', { name: 'gRPC Descriptors', exact: true }).click();
  await expect(page.getByText('No gRPC descriptors loaded.')).toBeVisible();
  await page.getByRole('tab', { name: 'Import', exact: true }).click();
  await expect(page.getByRole('radio', { name: /^Expectation JSON/ })).toBeChecked();
});

// ---------------------------------------------------------------------------
// Library → Import
// ---------------------------------------------------------------------------

test('imports Expectation JSON by paste and the mocks are served', async ({ page, request }) => {
  await openLibraryTab(page, 'Import');
  await importViaForm(page, 'Expectation JSON', EXPECTATIONS);
  await expect(snackbar(page)).toContainText('Imported 2 expectations');
  // The form clears for the next import.
  await expect(page.getByLabel('Expectation JSON content')).toHaveValue('');

  const ids = (await activeExpectations(request)).map((e) => e['id']).sort();
  expect(ids).toEqual(['lib-json-1', 'lib-json-2']);
  expect(await (await request.get(`${ORIGIN}/lib-json/one`)).text()).toBe('one');
  const two = await request.post(`${ORIGIN}/lib-json/two`);
  expect(two.status()).toBe(201);
  expect(await two.json()).toEqual({ two: 2 });
});

test('imports an inline OpenAPI spec (JSON) and serves an example per operation', async ({ page, request }) => {
  await openLibraryTab(page, 'Import');
  await importViaForm(page, 'OpenAPI', OPENAPI_JSON);
  await expect(snackbar(page)).toContainText('Imported 2 expectations');
  const pets = await request.get(`${ORIGIN}/lib-oas/pets`);
  expect(pets.status()).toBe(200);
  expect(await pets.json()).toEqual([{ id: 1, name: 'rex' }]);
  const pet = await request.get(`${ORIGIN}/lib-oas/pets/42`);
  expect(await pet.json()).toEqual({ id: 7, name: 'tom' });
});

test('imports an OpenAPI spec from a YAML file chosen with the file picker', async ({ page, request }) => {
  await openLibraryTab(page, 'Import');
  await importFileViaForm(page, 'OpenAPI', { name: 'spec.yaml', mimeType: 'application/yaml', content: OPENAPI_YAML });
  await expect(snackbar(page)).toContainText('Imported 1 expectation');
  expect(await (await request.get(`${ORIGIN}/lib-yaml/hello`)).json()).toEqual({ greeting: 'hi' });
});

test('imports a WSDL file and serves the SOAP response', async ({ page, request }) => {
  await openLibraryTab(page, 'Import');
  await importFileViaForm(page, 'WSDL / SOAP', { name: 'calc.wsdl', mimeType: 'text/xml', content: WSDL });
  await expect(snackbar(page)).toContainText('Imported 1 expectation');
  const res = await request.post(`${ORIGIN}/lib-wsdl/calc`, {
    headers: { SOAPAction: '"http://example.com/libcalc/Add"', 'Content-Type': 'text/xml' },
    data: '<Envelope/>',
  });
  expect(res.status()).toBe(200);
  expect(await res.text()).toContain('AddResponse');
});

test('imports a HAR file and a Postman collection', async ({ page, request }) => {
  await openLibraryTab(page, 'Import');
  await importFileViaForm(page, 'HAR (HTTP Archive)', { name: 'capture.har', mimeType: 'application/json', content: harFor('/lib-har/thing', 'x=1', '{"har":"thing"}') });
  await expect(snackbar(page)).toContainText('Imported 1 expectation');
  expect(await (await request.get(`${ORIGIN}/lib-har/thing?x=1`)).json()).toEqual({ har: 'thing' });

  await importViaForm(page, 'Postman collection', POSTMAN);
  await expect(snackbar(page)).toContainText('Imported 1 expectation');
  expect(await (await request.get(`${ORIGIN}/lib-postman/widget`)).json()).toEqual({ widget: 'postman' });
});

test('E2E-LIB-14 importing an OpenAPI spec URL served by the same MockServer never hangs', async ({ request }) => {
  test.setTimeout(120_000);
  await put(request, '/mockserver/expectation', {
    httpRequest: { path: '/lib-self-spec/.*' },
    httpResponse: { statusCode: 200, headers: { 'Content-Type': ['application/json'] }, body: OPENAPI_JSON },
  });
  for (let i = 0; i < 12; i++) {
    const res = await request.put(`${ORIGIN}/mockserver/openapi`, {
      data: [{ specUrlOrPayload: `http://localhost:${PORT}/lib-self-spec/${Date.now()}-${i}/openapi.json` }],
      // A fresh connection per import (as curl / most scripts do) lands on each event loop in turn.
      headers: { Connection: 'close' },
      timeout: 10_000,
    });
    expect(res.status(), `import ${i}`).toBe(201);
  }
});

test('E2E-LIB-2 importing a second, different HAR file keeps the mocks from the first', async ({ page, request }) => {
  await openLibraryTab(page, 'Import');
  await importViaForm(page, 'HAR (HTTP Archive)', harFor('/lib-har/first', 'a=1', '{"n":1}'));
  await expect(snackbar(page)).toContainText('Imported 1 expectation');
  await importViaForm(page, 'HAR (HTTP Archive)', harFor('/lib-har/second', 'b=2', '{"n":2}'));
  await expect.poll(async () => (await activeExpectations(request)).length).toBe(2);
  expect((await request.get(`${ORIGIN}/lib-har/first?a=1`)).status()).toBe(200);
  expect((await request.get(`${ORIGIN}/lib-har/second?b=2`)).status()).toBe(200);
});

test('import error paths: invalid input is reported and nothing is created', async ({ page, request }) => {
  allowedConsole.push(/Failed to load resource: the server responded with a status of 400/);
  await openLibraryTab(page, 'Import');

  // The Import button stays disabled until there is content.
  await expect(page.getByRole('button', { name: 'Import', exact: true })).toBeDisabled();

  // Malformed JSON never reaches the server — the browser-side parse fails.
  await importViaForm(page, 'Expectation JSON', '[{ "httpRequest": ');
  await expect(page.getByRole('alert')).toBeVisible();

  // A syntactically valid but invalid expectation is rejected by the server (400).
  await importViaForm(page, 'Expectation JSON', JSON.stringify({ httpRequest: { path: 1234 }, httpResponse: { statusCode: 'not-a-number' } }));
  await expect(page.getByRole('alert')).toBeVisible();

  // Not a HAR at all.
  await importViaForm(page, 'HAR (HTTP Archive)', '{"not":"a har"}');
  await expect(page.getByRole('alert')).toBeVisible();

  // Not a WSDL.
  await importViaForm(page, 'WSDL / SOAP', '<nope/>');
  await expect(page.getByRole('alert')).toBeVisible();

  // Not an OpenAPI spec. The server accepts it with no operations and reports
  // its load problems, so the problems alert is the only report, an error
  // rather than a success snackbar.
  await importViaForm(page, 'OpenAPI', '{"openapi":"3.0.0"}');
  const loadProblems = page.getByTestId('openapi-load-problems');
  await expect(loadProblems).toContainText(/No Expectations Created: Spec Has \d+ Problems?/);
  await expect(loadProblems).toContainText('missing required field');
  await expect(page.getByRole('alert')).toHaveCount(1);
  await expect(snackbar(page)).toHaveCount(0);

  expect(await activeExpectations(request)).toEqual([]);
});

test('import is keyboard operable: choose a format, paste and submit without the mouse', async ({ page, request }) => {
  await openLibraryTab(page, 'Import');
  await page.getByRole('radio', { name: /^Expectation JSON/ }).focus();
  // Arrow keys move within the radio group (Expectation JSON → OpenAPI → WSDL → HAR → Postman).
  await page.keyboard.press('ArrowDown');
  await expect(page.getByRole('radio', { name: /^OpenAPI/ })).toBeChecked();
  await page.keyboard.press('ArrowUp');
  await expect(page.getByRole('radio', { name: /^Expectation JSON/ })).toBeChecked();
  await page.getByLabel('Expectation JSON content').focus();
  await page.keyboard.insertText(JSON.stringify({ httpRequest: { path: '/lib-kbd' }, httpResponse: { statusCode: 202 } }));
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Import', exact: true })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(snackbar(page)).toContainText('Imported 1 expectation');
  expect((await request.get(`${ORIGIN}/lib-kbd`)).status()).toBe(202);
});

test('E2E-LIB-3 the WASM Modules tab explains that WASM is disabled instead of a bare HTTP 403', async ({ page }) => {
  allowedConsole.push(/\/mockserver\/wasm\/modules/);
  await openLibraryTab(page, 'WASM Modules');
  const guidance = page.getByRole('alert').filter({ hasText: 'WASM rules are turned off on this server' });
  await expect(guidance).toBeVisible();
  await expect(guidance).toContainText('-Dmockserver.wasmEnabled=true');
  await expect(page.getByText(/HTTP 403/)).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Upload', exact: true })).toBeDisabled();
});

// protoc --descriptor_set_out of: package e2elib; service LibGreeter { rpc SayHello
// (HelloRequest) returns (HelloReply); rpc StreamHello (HelloRequest) returns (stream HelloReply); }
const LIB_GREETER_DESC_B64 = 'Cu0BChBsaWJncmVldGVyLnByb3RvEgZlMmVsaWIiIgoMSGVsbG9SZXF1ZXN0EhIKBG5hbWUYASABKAlSBG5hbWUiJgoKSGVsbG9SZXBseRIYCgdtZXNzYWdlGAEgASgJUgdtZXNzYWdlMn0KCkxpYkdyZWV0ZXISNAoIU2F5SGVsbG8SFC5lMmVsaWIuSGVsbG9SZXF1ZXN0GhIuZTJlbGliLkhlbGxvUmVwbHkSOQoLU3RyZWFtSGVsbG8SFC5lMmVsaWIuSGVsbG9SZXF1ZXN0GhIuZTJlbGliLkhlbGxvUmVwbHkwAWIGcHJvdG8z';

// The prebuilt example module: matches POST /orders with header X-Tenant: acme.
const WASM_MODULE_PATH = fileURLToPath(new URL('../../examples/wasm/rust-request/match-request.wasm', import.meta.url));

test('WASM modules: upload, dry-run test, and delete a module', async ({ page, request }) => {
  const name = `lib-wasm-${Date.now()}`;
  try {
    // WASM is off by default; it is a runtime-configurable property.
    await put(request, '/mockserver/configuration', { wasmEnabled: true });
    await openLibraryTab(page, 'WASM Modules');
    await expect(page.getByText('No WASM modules loaded.')).toBeVisible();

    // Validation: a name is required.
    await page.getByRole('button', { name: 'Upload', exact: true }).click();
    await expect(page.getByText('Module name is required')).toBeVisible();

    await page.getByLabel('Module name').fill(name);
    await page.locator('input[type="file"][accept=".wasm"]').setInputFiles(WASM_MODULE_PATH);
    await page.getByRole('button', { name: 'Upload', exact: true }).click();
    await expect(page.getByRole('cell', { name, exact: true })).toBeVisible();
    expect(await (await request.get(`${ORIGIN}/mockserver/wasm/modules`)).json()).toContain(name);

    await page.getByRole('button', { name: `Test WASM module ${name}` }).click();
    const dialog = page.getByRole('dialog', { name: new RegExp(`Test WASM Module`) });
    const sample = dialog.getByLabel('Sample request (JSON)');
    await sample.fill(JSON.stringify({ method: 'POST', path: '/orders', headers: { 'X-Tenant': ['acme'] } }));
    await dialog.getByRole('button', { name: 'Run test' }).click();
    await expect(dialog.getByText('Matched — the module accepted this request.')).toBeVisible();
    await sample.fill(JSON.stringify({ method: 'GET', path: '/orders' }));
    await dialog.getByRole('button', { name: 'Run test' }).click();
    await expect(dialog.getByText('Not matched — the module rejected this request.')).toBeVisible();
    await sample.fill('{ not json');
    await dialog.getByRole('button', { name: 'Run test' }).click();
    await expect(dialog.getByText('Sample request must be valid JSON.')).toBeVisible();
    await dialog.getByRole('button', { name: 'Close' }).click();

    await page.getByRole('button', { name: `Delete WASM module ${name}` }).click();
    await page.getByRole('button', { name: 'Delete module' }).click();
    await expect(page.getByText('No WASM modules loaded.')).toBeVisible();
    expect(await (await request.get(`${ORIGIN}/mockserver/wasm/modules`)).json()).not.toContain(name);
  } finally {
    await request.delete(`${ORIGIN}/mockserver/wasm/modules?name=${encodeURIComponent(name)}`).catch(() => undefined);
    await request.put(`${ORIGIN}/mockserver/configuration`, { data: { wasmEnabled: false } });
  }
});

test('gRPC descriptors: upload a descriptor set, list its services, clear all', async ({ page, request }) => {
  try {
    await openLibraryTab(page, 'gRPC Descriptors');
    await expect(page.getByText('No gRPC descriptors loaded.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Clear all' })).toBeDisabled();
    await page.getByRole('button', { name: 'Upload', exact: true }).click();
    await expect(page.getByText(/Select a compiled descriptor set/)).toBeVisible();

    await page.locator('input[type="file"][accept^=".desc"]').setInputFiles({
      name: 'libgreeter.desc', mimeType: 'application/octet-stream', buffer: Buffer.from(LIB_GREETER_DESC_B64, 'base64'),
    });
    await page.getByRole('button', { name: 'Upload', exact: true }).click();
    await expect(page.getByText('e2elib.LibGreeter', { exact: true })).toBeVisible();
    const sayHello = page.getByRole('row').filter({ hasText: 'SayHello' });
    await expect(sayHello).toContainText('e2elib.HelloRequest → e2elib.HelloReply');
    await expect(sayHello).toContainText('unary');
    await expect(page.getByRole('row').filter({ hasText: 'StreamHello' })).toContainText('server');

    await page.getByRole('button', { name: 'Clear all' }).click();
    await page.getByRole('button', { name: 'Clear descriptors' }).click();
    await expect(page.getByText('No gRPC descriptors loaded.')).toBeVisible();
  } finally {
    await request.put(`${ORIGIN}/mockserver/grpc/clear`).catch(() => undefined);
  }
});

// ---------------------------------------------------------------------------
// Library → Export
// ---------------------------------------------------------------------------

const SCOPES = ['Recorded requests', 'Active expectations', 'Recorded expectations', 'Server logs'] as const;

async function seedExportState(request: APIRequestContext) {
  await put(request, '/mockserver/expectation', JSON.parse(EXPECTATIONS));
  expect((await request.get(`${ORIGIN}/lib-json/one`)).status()).toBe(200);
  expect((await request.post(`${ORIGIN}/lib-json/two`, { data: '{"a":1}' })).status()).toBe(201);
}

async function chooseExport(page: Page, scope: string, format: string) {
  await page.getByRole('radio', { name: scope, exact: true }).check();
  await page.getByRole('combobox', { name: 'Format' }).click();
  await page.getByRole('option', { name: format, exact: true }).click();
  await expect(page.getByRole('listbox')).toHaveCount(0);
}

async function downloadExport(page: Page): Promise<{ filename: string; content: Buffer }> {
  const pending = page.waitForEvent('download');
  await page.getByRole('button', { name: /^Download / }).click();
  const download = await pending;
  const path = await download.path();
  return { filename: download.suggestedFilename(), content: await readFile(path) };
}

test('exports every scope × format the dropdown offers as a non-empty download', async ({ page, request }) => {
  test.setTimeout(120_000);
  await seedExportState(request);
  await openLibraryTab(page, 'Export');

  const seen: string[] = [];
  for (const scope of SCOPES) {
    await page.getByRole('radio', { name: scope, exact: true }).check();
    await page.getByRole('combobox', { name: 'Format' }).click();
    const formats = await page.getByRole('option').allTextContents();
    await page.keyboard.press('Escape');
    expect(formats.length, `${scope} offers formats`).toBeGreaterThan(0);
    for (const format of formats) {
      await chooseExport(page, scope, format);
      const button = page.getByRole('button', { name: /^Download / });
      const expectedName = ((await button.textContent()) ?? '').replace(/^Download /, '');
      const { filename, content } = await downloadExport(page);
      const where = `${scope} / ${format}`;
      expect(filename, where).toBe(expectedName);
      if (format.startsWith('Bruno')) {
        expect(content.subarray(0, 2).toString('latin1'), `${where} is a zip`).toBe('PK');
      } else {
        const text = content.toString('utf8');
        // An empty recorded-expectations scope legitimately exports an empty list.
        if (scope !== 'Recorded expectations') {
          expect(text.length, `${where} is non-empty`).toBeGreaterThan(2);
        }
        if (/JSON|HAR|OpenAPI|Postman/.test(format)) {
          expect(() => JSON.parse(text), `${where} parses as JSON`).not.toThrow();
        }
        if (scope === 'Active expectations' && !/Log entries/.test(format)) {
          expect(text, `${where} mentions the exported path`).toContain('lib-json');
        }
      }
      seen.push(where);
    }
  }
  // The four scopes together offer every format at least once.
  expect(seen.length).toBeGreaterThanOrEqual(40);
  await expect(page.getByRole('alert').filter({ hasText: /error|failed/i })).toHaveCount(0);
});

test('the format dropdown is filtered by scope and explains lossy formats', async ({ page }) => {
  await openLibraryTab(page, 'Export');
  await page.getByRole('radio', { name: 'Server logs', exact: true }).check();
  await page.getByRole('combobox', { name: 'Format' }).click();
  await expect(page.getByRole('option')).toHaveText(['Log entries (JSON)']);
  await page.keyboard.press('Escape');

  await chooseExport(page, 'Active expectations', 'Postman collection v2.1');
  await expect(page.getByText(/Best-effort export/)).toBeVisible();
  await chooseExport(page, 'Active expectations', 'MockServer JSON');
  await expect(page.getByText(/Best-effort export/)).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Download mockserver-expectations.json' })).toBeVisible();
  // Bruno is a binary zip, so there is nothing to copy as code.
  await chooseExport(page, 'Active expectations', 'Bruno collection (.zip)');
  await expect(page.getByRole('button', { name: 'Copy as code' })).toHaveCount(0);
});

test('Copy as code puts the generated client code on the clipboard', async ({ page, context, request }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: ORIGIN });
  await seedExportState(request);
  await openLibraryTab(page, 'Export');
  await chooseExport(page, 'Active expectations', 'MockServer Java DSL');
  await page.getByRole('button', { name: 'Copy as code' }).click();
  await expect(page.getByRole('button', { name: 'Copied!' })).toBeVisible();
  const clip = await page.evaluate(() => navigator.clipboard.readText());
  expect(clip).toContain('/lib-json/one');
  expect(clip).toMatch(/request\(\)|HttpRequest/);
});

/** Export the active expectations in `format`, reset the server, re-import the file through the Import tab. */
async function exportResetReimport(page: Page, request: APIRequestContext, exportFormat: string, importFormat: string) {
  await seedExportState(request);
  await openLibraryTab(page, 'Export');
  await chooseExport(page, 'Active expectations', exportFormat);
  const { filename, content } = await downloadExport(page);

  expect((await request.put(`${ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
  expect((await request.get(`${ORIGIN}/lib-json/one`)).status()).toBe(404);

  await page.getByRole('tab', { name: 'Import', exact: true }).click();
  await importFileViaForm(page, importFormat, { name: filename, mimeType: 'application/json', content: content.toString('utf8') });
  await expect(snackbar(page)).toContainText('Imported 2 expectations');
}

test('round-trip: MockServer JSON export re-imports to identical behaviour', async ({ page, request }) => {
  await exportResetReimport(page, request, 'MockServer JSON', 'Expectation JSON');
  expect((await activeExpectations(request)).map((e) => e['id']).sort()).toEqual(['lib-json-1', 'lib-json-2']);
  expect(await (await request.get(`${ORIGIN}/lib-json/one`)).text()).toBe('one');
  const two = await request.post(`${ORIGIN}/lib-json/two`);
  expect(two.status()).toBe(201);
  expect(await two.json()).toEqual({ two: 2 });
});

for (const [exportFormat, importFormat] of [
  ['HAR (HTTP Archive)', 'HAR (HTTP Archive)'],
  ['Postman collection v2.1', 'Postman collection'],
] as const) {
  test(`round-trip: ${exportFormat} export re-imports and serves the same status and body`, async ({ page, request }) => {
    await exportResetReimport(page, request, exportFormat, importFormat);
    const one = await request.get(`${ORIGIN}/lib-json/one`);
    expect(one.status()).toBe(200);
    expect(await one.text()).toBe('one');
    const two = await request.post(`${ORIGIN}/lib-json/two`);
    expect(two.status()).toBe(201);
    expect(JSON.parse(await two.text())).toEqual({ two: 2 });
  });
}

test('round-trip: OpenAPI export re-imports a plain-text example unchanged', async ({ page, request }) => {
  await exportResetReimport(page, request, 'OpenAPI 3 spec', 'OpenAPI');
  const one = await request.get(`${ORIGIN}/lib-json/one`);
  expect(one.status()).toBe(200);
  expect(await one.text()).toBe('one');
  expect((await request.post(`${ORIGIN}/lib-json/two`)).status()).toBe(201);
});

test('E2E-LIB-4 round-trip: OpenAPI export keeps a JSON response body as JSON, not a JSON-encoded string', async ({ page, request }) => {
  await exportResetReimport(page, request, 'OpenAPI 3 spec', 'OpenAPI');
  const two = await request.post(`${ORIGIN}/lib-json/two`);
  expect(await two.json()).toEqual({ two: 2 });
});

for (const [exportFormat, importFormat] of [
  ['HAR (HTTP Archive)', 'HAR (HTTP Archive)'],
  ['Postman collection v2.1', 'Postman collection'],
] as const) {
  test(`E2E-LIB-6 round-trip: ${exportFormat} export keeps the response Content-Type`, async ({ page, request }) => {
    await exportResetReimport(page, request, exportFormat, importFormat);
    const two = await request.post(`${ORIGIN}/lib-json/two`);
    expect(two.headers()['content-type'] ?? '').toContain('application/json');
  });
}

test('the Library Export tab works in light theme at a narrow 1024×700 window', async ({ page, request }) => {
  await page.setViewportSize({ width: 1024, height: 700 });
  await seedExportState(request);
  await openLibraryTab(page, 'Export');
  const toLight = page.getByRole('button', { name: 'Switch to light mode' });
  if (await toLight.count()) await toLight.click();
  await expect(page.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible();
  const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  const [r, g, b] = (bg.match(/\d+/g) ?? []).map(Number);
  expect(r! + g! + b!, `light body background, got ${bg}`).toBeGreaterThan(600);

  await chooseExport(page, 'Active expectations', 'MockServer JSON');
  const { content } = await downloadExport(page);
  expect(JSON.parse(content.toString('utf8'))).toHaveLength(2);
  // No horizontal page scroll at this width.
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await page.screenshot({ path: test.info().outputPath('export-light-1024.png') });
});

// ---------------------------------------------------------------------------
// Library → Cassettes (recording needs forwarded traffic — see the proxy block)
// ---------------------------------------------------------------------------

test('cassette load reports a missing server file as an error', async ({ page }) => {
  await openLibraryTab(page, 'Cassettes');
  await page.getByRole('tab', { name: 'Load', exact: true }).click();
  await page.getByLabel('File path (required)').fill(`does-not-exist-${Date.now()}.json`);
  await page.getByRole('button', { name: 'Load Expectations' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'File does not exist' })).toBeVisible();
  await page.getByRole('tab', { name: 'List', exact: true }).click();
  await expect(page.getByText('No cassettes tracked yet')).toBeVisible();
});

test('E2E-LIB-10 recording a cassette with no forwarded traffic is not reported as a success', async ({ page }) => {
  await openLibraryTab(page, 'Cassettes');
  await page.getByRole('tab', { name: 'Record', exact: true }).click();
  await page.getByLabel('File path (required)').fill(`.tmp/e2e-lib-empty-${Date.now()}.json`);
  await page.getByRole('button', { name: 'Record', exact: true }).click();
  await expect(page.getByText(/No recorded traffic found/)).toBeVisible();
  await expect(page.locator('.MuiAlert-colorSuccess')).toHaveCount(0);
  await page.getByRole('tab', { name: 'List', exact: true }).click();
  await expect(page.getByText('No cassettes tracked yet')).toBeVisible();
});

// ---------------------------------------------------------------------------
// Inspect → Audit
// ---------------------------------------------------------------------------

test('audit: enable from the UI, see a control-plane mutation, search it, disable again', async ({ page, request }) => {
  const stamp = Date.now();
  try {
    await openView(page, 'audit');
    await expect(page.getByText('Audit Trail: Off')).toBeVisible();
    await expect(page.getByText('No audit entries recorded.')).toBeVisible();
    await page.getByRole('button', { name: 'Enable Audit Trail' }).click();
    await expect(page.getByText('Audit Trail: On')).toBeVisible();

    await put(request, '/mockserver/expectation', { id: `lib-audit-${stamp}`, httpRequest: { path: `/lib-audit/${stamp}` }, httpResponse: { statusCode: 204 } });
    await page.getByRole('button', { name: 'Refresh', exact: true }).click();
    const rows = page.getByRole('row').filter({ hasText: '/mockserver/expectation' });
    await expect(rows.first()).toBeVisible();

    await page.getByRole('textbox', { name: 'Search audit entries' }).fill('no-such-audit-entry-xyz');
    await expect(page.getByText('No entries match your search.')).toBeVisible();
    await page.getByRole('textbox', { name: 'Search audit entries' }).fill('/mockserver/expectation');
    await expect(rows.first()).toBeVisible();

    await page.getByRole('switch', { name: 'Audit trail enabled' }).click();
    await expect(page.getByText('Audit Trail: Off')).toBeVisible();
  } finally {
    await request.put(`${ORIGIN}/mockserver/configuration`, { data: { controlPlaneAuditEnabled: false, controlPlaneAuditReads: false } });
  }
});

test('E2E-LIB-8 audit rows carry a summary of the change', async ({ page, request }) => {
  try {
    await put(request, '/mockserver/configuration', { controlPlaneAuditEnabled: true });
    await put(request, '/mockserver/expectation', { httpRequest: { path: '/lib-audit-summary' }, httpResponse: { statusCode: 204 } });
    await openView(page, 'audit');
    const row = page.getByRole('row').filter({ hasText: '/mockserver/expectation' });
    await expect(row.getByRole('cell').last()).not.toHaveText('');
  } finally {
    await request.put(`${ORIGIN}/mockserver/configuration`, { data: { controlPlaneAuditEnabled: false } });
  }
});

// A reset empties the audit trail (as documented) but keeps the record of the reset itself.
test('E2E-LIB-9 a reset empties the audit trail but records who reset it', async ({ page, request }) => {
  try {
    await put(request, '/mockserver/configuration', { controlPlaneAuditEnabled: true });
    await put(request, '/mockserver/expectation', { httpRequest: { path: '/lib-audit-reset' }, httpResponse: { statusCode: 204 } });
    await put(request, '/mockserver/reset', '');
    await openView(page, 'audit');
    const resetRow = page.getByRole('row').filter({ hasText: '/mockserver/reset' });
    await expect(resetRow).toBeVisible();
    await expect(resetRow).toContainText('Reset all expectations, recorded requests and state');
    await expect(page.getByRole('row').filter({ hasText: '/mockserver/expectation' })).toHaveCount(0);
  } finally {
    await request.put(`${ORIGIN}/mockserver/configuration`, { data: { controlPlaneAuditEnabled: false } });
  }
});

// ---------------------------------------------------------------------------
// Proxy record & replay, Inspect → Breakpoints  (need the upstream MockServer)
// ---------------------------------------------------------------------------

test.describe('with the upstream MockServer', () => {
  test.beforeEach(async ({ request }) => {
    requireOrSkip(await upstreamReachable(request), `upstream MockServer not reachable at ${UP_ORIGIN}`);
    expect((await request.put(`${UP_ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
  });

  test.afterEach(async ({ request }) => {
    await request.put(`${ORIGIN}/mockserver/breakpoint/matcher/clear`).catch(() => undefined);
  });

  function trafficRow(page: Page, path: string) {
    return page
      .locator('[data-vrow]')
      .filter({ has: page.locator(`[data-testid="traffic-row-path"][aria-label="${UP_HOST}:${UP_PORT}${path}"]`) });
  }

  test('records proxied traffic, promotes it to mocks from Traffic, and replays it with the upstream gone', async ({ page, request }) => {
    const stamp = Date.now();
    const base = `/lib-rec/${stamp}`;
    await put(request, '/mockserver/expectation', {
      httpRequest: { method: 'GET', path: `${base}/items` },
      httpResponse: { statusCode: 200, headers: { 'Content-Type': ['application/json'] }, body: { items: [1, 2, 3] } },
    }, UP_ORIGIN);
    await put(request, '/mockserver/expectation', {
      httpRequest: { method: 'POST', path: `${base}/orders` },
      httpResponse: { statusCode: 201, body: 'order-created' },
    }, UP_ORIGIN);

    await openView(page, 'traffic');
    const promote = page.getByRole('button', { name: /^(Promote to )?Mocks$/ });
    await expect(promote).toBeDisabled();

    // --- RECORD: real requests through MockServer as a forward proxy -------------
    const items = await viaProxy(`${base}/items`);
    expect(items.status).toBe(200);
    expect(JSON.parse(items.body)).toEqual({ items: [1, 2, 3] });
    const order = await viaProxy(`${base}/orders`, { method: 'POST', body: 'x', headers: { 'Content-Type': 'text/plain' } });
    expect(order.status).toBe(201);

    await expect(trafficRow(page, `${base}/items`).filter({ has: page.getByText('200', { exact: true }) })).toBeVisible();
    await expect(trafficRow(page, `${base}/orders`).filter({ has: page.getByText('201', { exact: true }) })).toBeVisible();
    // Recorded expectations exist server-side.
    const recorded = await (await request.put(`${ORIGIN}/mockserver/retrieve?type=RECORDED_EXPECTATIONS`)).json() as Json[];
    expect(recorded.length).toBe(2);

    // --- PROMOTE: Traffic → Promote to Mocks, scoped by a path filter ------------
    await expect(promote).toBeEnabled();
    await promote.click();
    const dialog = page.getByRole('dialog', { name: 'Promote Recordings to Mocks' });
    await expect(dialog).toBeVisible();
    await dialog.getByRole('textbox', { name: 'Path filter' }).fill(`${base}/.*`);
    await dialog.getByRole('button', { name: 'Run' }).click();
    await expect(dialog.getByText('Created 2 expectations from recorded traffic.')).toBeVisible();
    const active = await activeExpectations(request);
    expect(active.map((e) => (e['httpRequest'] as Json)['path']).sort()).toEqual([`${base}/items`, `${base}/orders`]);
    await dialog.getByRole('button', { name: 'View Expectations' }).click();
    await expect(page).toHaveURL(/#\/dashboard$/);

    // --- REPLAY: the upstream forgets everything; MockServer still answers --------
    expect((await request.put(`${UP_ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
    const replayedViaProxy = await viaProxy(`${base}/items`);
    expect(replayedViaProxy.status).toBe(200);
    expect(JSON.parse(replayedViaProxy.body)).toEqual({ items: [1, 2, 3] });
    const replayedDirect = await request.get(`${ORIGIN}${base}/items`);
    expect(replayedDirect.status()).toBe(200);
    expect(await replayedDirect.json()).toEqual({ items: [1, 2, 3] });
    const orderDirect = await request.post(`${ORIGIN}${base}/orders`, { data: 'x', headers: { 'Content-Type': 'text/plain' } });
    expect(orderDirect.status()).toBe(201);
    expect(await orderDirect.text()).toBe('order-created');
  });

  test('promote with a filter that matches nothing explains why no mocks were created', async ({ page, request }) => {
    const stamp = Date.now();
    await put(request, '/mockserver/expectation', { httpRequest: { path: `/lib-rec0/${stamp}` }, httpResponse: { statusCode: 200, body: 'z' } }, UP_ORIGIN);
    await openView(page, 'traffic');
    expect((await viaProxy(`/lib-rec0/${stamp}`)).status).toBe(200);
    const promote = page.getByRole('button', { name: /^(Promote to )?Mocks$/ });
    await expect(promote).toBeEnabled();
    await promote.click();
    const dialog = page.getByRole('dialog', { name: 'Promote Recordings to Mocks' });
    await dialog.getByRole('textbox', { name: 'Path filter' }).fill('/nothing-recorded-here/.*');
    await dialog.getByRole('button', { name: 'Run' }).click();
    await expect(dialog.getByText(/No expectations were created/)).toBeVisible();
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(dialog).toHaveCount(0);
    expect(await activeExpectations(request)).toEqual([]);
  });

  test('E2E-LIB-1 a mock promoted from curl-style proxied traffic also matches a direct request', async ({ request }) => {
    const stamp = Date.now();
    await put(request, '/mockserver/expectation', { httpRequest: { path: `/lib-rec1/${stamp}` }, httpResponse: { statusCode: 200, body: 'p' } }, UP_ORIGIN);
    // curl -x and other proxy-aware clients send Proxy-Connection on proxied requests.
    expect(await curlStyleProxyGet(`/lib-rec1/${stamp}`)).toBe(200);
    expect((await request.put(`${ORIGIN}/mockserver/recordings/promote`)).status()).toBe(201);
    expect((await request.get(`${ORIGIN}/lib-rec1/${stamp}`)).status()).toBe(200);
    // A verbatim promote keeps the client's own headers but must not pin the upstream's Host: the direct
    // request sends Host ${HOST}:${PORT}, not the recorded ${UP_HOST}:${UP_PORT}.
    expect((await request.put(`${ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
    expect(await curlStyleProxyGet(`/lib-rec1/${stamp}`)).toBe(200);
    expect((await request.put(`${ORIGIN}/mockserver/recordings/promote?consolidate=false`)).status()).toBe(201);
    const direct = await request.get(`${ORIGIN}/lib-rec1/${stamp}`, { headers: { 'User-Agent': 'curl/8.7.1', Accept: '*/*' } });
    expect(direct.status()).toBe(200);
  });

  test('cassettes: record forwarded traffic to a server file, reset, load it back and replay it', async ({ page, request }) => {
    const stamp = Date.now();
    const path = `/lib-cas/${stamp}`;
    // Relative to the SERVER's working directory; .tmp/ is git-ignored in this repo.
    const file = `${process.env.E2E_SERVER_SCRATCH_DIR ?? '.tmp'}/e2e-lib-cassette-${stamp}.json`;
    await put(request, '/mockserver/expectation', { httpRequest: { path }, httpResponse: { statusCode: 200, body: `cassette-${stamp}` } }, UP_ORIGIN);
    expect((await viaProxy(path)).status).toBe(200);

    await openLibraryTab(page, 'Cassettes');
    await page.getByRole('tab', { name: 'Record', exact: true }).click();
    await page.getByLabel('File path (required)').fill(file);
    await page.getByLabel('Request path filter (optional)').fill(path);
    await page.getByRole('button', { name: 'Record', exact: true }).click();
    await expect(page.getByRole('alert').filter({ hasText: /^Wrote 1 expectation\(s\) to / })).toBeVisible();
    await expect(page.getByRole('alert').filter({ hasText: /No recorded traffic/ })).toHaveCount(0);

    // Forget everything — server and upstream — then replay from the cassette.
    expect((await request.put(`${ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
    expect((await request.put(`${UP_ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
    expect((await request.get(`${ORIGIN}${path}`)).status()).toBe(404);

    await page.getByRole('tab', { name: 'Load', exact: true }).click();
    await page.getByLabel('File path (required)').fill(file);
    await page.getByRole('button', { name: 'Load Expectations' }).click();
    await expect(page.getByRole('alert').filter({ hasText: /^Loaded 1 expectation\(s\) from / })).toBeVisible();
    // Replay the way it was recorded: the same client, still pointed at the upstream
    // through MockServer — now answered from the cassette (the upstream was reset).
    const replayed = await viaProxy(path);
    expect(replayed.status).toBe(200);
    expect(replayed.body).toBe(`cassette-${stamp}`);

    await page.getByRole('tab', { name: 'List', exact: true }).click();
    const name = file.split('/').pop()!;
    const rows = page.getByRole('row').filter({ has: page.getByRole('cell', { name, exact: true }) });
    await expect(rows.first()).toBeVisible();
    await expect(rows).toHaveCount(1);
    await expect(rows.first().getByRole('cell').nth(2)).toHaveText('1');
    await rows.first().getByRole('button', { name: `Remove ${name}` }).click();
    await expect(page.getByText('No cassettes tracked yet')).toBeVisible();
  });

  test('E2E-LIB-12 a recorded-then-loaded cassette is listed once', async ({ page, request }) => {
    const stamp = Date.now();
    const path = `/lib-cas3/${stamp}`;
    const file = `${process.env.E2E_SERVER_SCRATCH_DIR ?? '.tmp'}/e2e-lib-cassette-${stamp}.json`;
    await put(request, '/mockserver/expectation', { httpRequest: { path }, httpResponse: { statusCode: 200, body: 'c' } }, UP_ORIGIN);
    expect((await viaProxy(path)).status).toBe(200);
    await openLibraryTab(page, 'Cassettes');
    await page.getByRole('tab', { name: 'Record', exact: true }).click();
    await page.getByLabel('File path (required)').fill(file);
    await page.getByRole('button', { name: 'Record', exact: true }).click();
    await expect(page.getByRole('alert').filter({ hasText: /^Wrote 1 expectation\(s\) to / })).toBeVisible();
    await page.getByRole('tab', { name: 'Load', exact: true }).click();
    await page.getByLabel('File path (required)').fill(file);
    await page.getByRole('button', { name: 'Load Expectations' }).click();
    await expect(page.getByRole('alert').filter({ hasText: /^Loaded 1 expectation\(s\) from / })).toBeVisible();
    await page.getByRole('tab', { name: 'List', exact: true }).click();
    await expect(page.getByRole('cell', { name: file.split('/').pop()!, exact: true })).toHaveCount(1);
  });

  test('E2E-LIB-11 a replayed cassette matches a client that does not send the recorded hop-by-hop headers', async ({ page, request }) => {
    const stamp = Date.now();
    const path = `/lib-cas2/${stamp}`;
    const file = `${process.env.E2E_SERVER_SCRATCH_DIR ?? '.tmp'}/e2e-lib-cassette-${stamp}.json`;
    await put(request, '/mockserver/expectation', { httpRequest: { path }, httpResponse: { statusCode: 200, body: 'c' } }, UP_ORIGIN);
    expect((await viaProxy(path)).status).toBe(200);
    await openLibraryTab(page, 'Cassettes');
    await page.getByRole('tab', { name: 'Record', exact: true }).click();
    await page.getByLabel('File path (required)').fill(file);
    await page.getByRole('button', { name: 'Record', exact: true }).click();
    await expect(page.getByRole('alert').filter({ hasText: /^Wrote 1 expectation\(s\) to / })).toBeVisible();
    expect((await request.put(`${ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
    expect((await request.put(`${UP_ORIGIN}/mockserver/reset`)).ok()).toBeTruthy();
    await page.getByRole('tab', { name: 'Load', exact: true }).click();
    await page.getByLabel('File path (required)').fill(file);
    await page.getByRole('button', { name: 'Load Expectations' }).click();
    await expect(page.getByRole('alert').filter({ hasText: /^Loaded 1 expectation\(s\) from / })).toBeVisible();
    // Control: the recording client's own request shape is replayed from the cassette...
    expect((await viaProxy(path)).status).toBe(200);
    // ...but the same request from curl -x (Proxy-Connection, no Connection header) is not.
    expect(await curlStyleProxyGet(path)).toBe(200);
  });

  test('E2E-LIB-5 Traffic lists each proxied request once, in time order', async ({ page, request }) => {
    const stamp = Date.now();
    await put(request, '/mockserver/expectation', { httpRequest: { path: `/lib-dup/${stamp}/.*` }, httpResponse: { statusCode: 200 } }, UP_ORIGIN);
    await openView(page, 'traffic');
    expect((await viaProxy(`/lib-dup/${stamp}/a`)).status).toBe(200);
    expect((await viaProxy(`/lib-dup/${stamp}/b`)).status).toBe(200);
    await expect(trafficRow(page, `/lib-dup/${stamp}/b`).filter({ has: page.getByText('200', { exact: true }) })).toBeVisible();
    await expect(page.locator('[data-vrow]')).toHaveCount(2);
    // Console order: oldest first.
    const paths = await page.locator('[data-vrow] [data-testid="traffic-row-path"]').evaluateAll((els) => els.map((el) => el.getAttribute('aria-label')));
    expect(paths).toEqual([`${UP_HOST}:${UP_PORT}/lib-dup/${stamp}/a`, `${UP_HOST}:${UP_PORT}/lib-dup/${stamp}/b`]);
  });

  test('imports an OpenAPI spec from a URL (hosted by the upstream)', async ({ page, request }) => {
    const stamp = Date.now();
    await put(request, '/mockserver/expectation', {
      httpRequest: { method: 'GET', path: `/lib-spec/${stamp}/openapi.json` },
      httpResponse: { statusCode: 200, headers: { 'Content-Type': ['application/json'] }, body: OPENAPI_JSON },
    }, UP_ORIGIN);
    await openLibraryTab(page, 'Import');
    await chooseImportFormat(page, 'OpenAPI');
    await page.getByRole('radio', { name: 'URL', exact: true }).check();
    await expect(page.getByRole('button', { name: 'Import', exact: true })).toBeDisabled();
    await page.getByLabel('Spec URL').fill(`${UP_ORIGIN}/lib-spec/${stamp}/openapi.json`);
    await page.getByRole('button', { name: 'Import', exact: true }).click();
    await expect(snackbar(page)).toContainText('Imported 2 expectations');
    expect((await request.get(`${ORIGIN}/lib-oas/pets`)).status()).toBe(200);
  });

  test('E2E-LIB-13 imports an OpenAPI spec from a URL that does not end in .json / .yaml', async ({ page, request }) => {
    await put(request, '/mockserver/expectation', {
      httpRequest: { method: 'GET', path: '/lib-spec/v3/api-docs' },
      httpResponse: { statusCode: 200, headers: { 'Content-Type': ['application/json'] }, body: OPENAPI_JSON },
    }, UP_ORIGIN);
    await openLibraryTab(page, 'Import');
    await chooseImportFormat(page, 'OpenAPI');
    await page.getByRole('radio', { name: 'URL', exact: true }).check();
    await page.getByLabel('Spec URL').fill(`${UP_ORIGIN}/lib-spec/v3/api-docs`);
    await page.getByRole('button', { name: 'Import', exact: true }).click();
    await expect(snackbar(page)).toContainText('Imported 2 expectations');
  });

  test('Traffic → Replay re-issues a proxied request to the upstream and shows the live response', async ({ page, request }) => {
    const stamp = Date.now();
    const path = `/lib-replay/${stamp}`;
    await put(request, '/mockserver/expectation', { httpRequest: { path }, httpResponse: { statusCode: 200, body: 'first' } }, UP_ORIGIN);
    await openView(page, 'traffic');
    expect((await viaProxy(path)).body).toBe('first');
    // The upstream now answers differently — Replay must show the LIVE answer.
    await put(request, '/mockserver/expectation', { httpRequest: { path }, httpResponse: { statusCode: 200, body: `second-${stamp}` }, priority: 10 }, UP_ORIGIN);

    await trafficRow(page, path).filter({ has: page.getByText('200', { exact: true }) }).click();
    await page.getByRole('button', { name: 'Replay', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Replay Request' });
    await expect(dialog.getByText(/real outbound HTTP request/)).toBeVisible();
    await dialog.getByRole('button', { name: 'Replay', exact: true }).click();
    await expect(dialog.getByText('Upstream Response')).toBeVisible();
    await expect(dialog).toContainText(`second-${stamp}`);
    await expect(dialog.getByRole('button', { name: 'Replay Again' })).toBeVisible();
    await dialog.getByRole('button', { name: 'Close' }).click();
  });

  test('breakpoints: register a matcher in the UI, pause a proxied request, continue it, remove the matcher', async ({ page, request }) => {
    const stamp = Date.now();
    const path = `/lib-bp/${stamp}`;
    await put(request, '/mockserver/expectation', { httpRequest: { path }, httpResponse: { statusCode: 200, body: 'released' } }, UP_ORIGIN);

    await openView(page, 'breakpoints');
    await expect(page.getByText('No matchers registered yet.', { exact: false })).toBeVisible();
    await page.getByLabel('Path (regex)').fill(`/lib-bp/${stamp}`);
    await page.getByRole('checkbox', { name: 'Response', exact: true }).uncheck();
    await page.getByRole('button', { name: 'Register Matcher' }).click();
    await expect(page.getByText('Registered Matchers (1)')).toBeVisible();

    // Fire the proxied request — it pauses at the breakpoint instead of completing.
    let settled = false;
    const inFlight = viaProxy(path).then((r) => { settled = true; return r; });

    await page.getByRole('tab', { name: /Live Exchanges/ }).click();
    const pausedRow = page.getByRole('row').filter({ hasText: path });
    await expect(pausedRow).toBeVisible();
    expect(settled, 'the request is held while paused').toBe(false);
    await pausedRow.getByRole('button', { name: /^Continue / }).click();
    const released = await inFlight;
    expect(released.status).toBe(200);
    expect(released.body).toBe('released');
    await expect(pausedRow).toHaveCount(0);

    await page.getByRole('tab', { name: 'Matchers', exact: true }).click();
    await page.getByRole('button', { name: /^Remove / }).click();
    await expect(page.getByText('Registered Matchers (0)')).toBeVisible();
    const left = await (await request.get(`${ORIGIN}/mockserver/breakpoint/matchers`)).json() as { matchers: unknown[] };
    expect(left.matchers).toEqual([]);
  });

  test('breakpoints: abort a paused proxied request so it never reaches the upstream', async ({ page, request }) => {
    const stamp = Date.now();
    const path = `/lib-bpa/${stamp}`;
    await put(request, '/mockserver/expectation', { httpRequest: { path }, httpResponse: { statusCode: 200, body: 'should-not-arrive' } }, UP_ORIGIN);
    await openView(page, 'breakpoints');
    await page.getByLabel('Path (regex)').fill(path);
    await page.getByRole('checkbox', { name: 'Response', exact: true }).uncheck();
    await page.getByRole('button', { name: 'Register Matcher' }).click();
    await expect(page.getByText('Registered Matchers (1)')).toBeVisible();

    const inFlight = viaProxy(path);
    await page.getByRole('tab', { name: /Live Exchanges/ }).click();
    const pausedRow = page.getByRole('row').filter({ hasText: path });
    await pausedRow.getByRole('button', { name: /^Abort / }).click();
    const aborted = await inFlight;
    expect(aborted.status).toBe(503);
    expect(aborted.body).not.toContain('should-not-arrive');
    const upstreamSaw = await (await request.put(`${UP_ORIGIN}/mockserver/retrieve?type=REQUESTS`, { data: { path } })).json() as unknown[];
    expect(upstreamSaw).toEqual([]);
  });
});
