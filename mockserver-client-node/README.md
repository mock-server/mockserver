# mockserver-client-node 

> Communicate with a [MockServer](https://mock-server.com/) from any node or grunt build

[![Build status](https://badge.buildkite.com/368c3b69e959f29725d8ab582f8d75dedddceee196d39b6d28.svg?style=square&theme=slack)](https://buildkite.com/mockserver/mockserver-client-node)

[![NPM](https://nodei.co/npm/mockserver-client.png?downloads=true&stars=true)](https://nodei.co/npm/mockserver-client/) 

# Community

* Roadmap:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://github.com/orgs/mock-server/projects/1"><img height="20px" src="https://mock-server.com/images/GitHub_Logo-md.png" alt="GitHub Project"></a>
* Feature Requests:&nbsp;&nbsp;&nbsp;<a href="https://github.com/mock-server/mockserver-monorepo/issues"><img height="20px" src="https://mock-server.com/images/GitHub_Logo-md.png" alt="Github Issues"></a>
* Issues / Bugs:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://github.com/mock-server/mockserver-monorepo/issues"><img height="20px" src="https://mock-server.com/images/GitHub_Logo-md.png" alt="Github Issues"></a>
* Discussions:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://github.com/mock-server/mockserver-monorepo/discussions"><img height="20px" src="https://mock-server.com/images/GitHub_Logo-md.png" alt="GitHub Discussions"></a>

## Getting Started

[MockServer](https://mock-server.com/) allows you to mock any system you integrate with via HTTP or HTTPS (i.e. (REST) services, web sites, etc). Please note that it is a third party project that needs java.

This npm module allows any grunt or node project to easily communicate with a running [MockServer](https://mock-server.com/) instance.

As an addition to this module for communicating with a running MockServer there is a second project that can be used to start and stop a MockServer called [mockserver-node](https://www.npmjs.org/package/mockserver-node).

The MockServer client can be created as follows:

```js
var mockServer = require('mockserver-client'),
    mockServerClient = mockServer.mockServerClient // MockServer and proxy client
```
**Note:** this assumes you have an instance of MockServer running on port 1080.
For more information on how to do so check [mockserver-node](https://www.npmjs.org/package/mockserver-node).

The package includes TypeScript typings. It is a CommonJS module. From CommonJS, or TypeScript compiled to it, `import { mockServerClient, llm } from 'mockserver-client'` works with or without `esModuleInterop`. The LLM builders can also be imported on their own: `import llm = require('mockserver-client/llm')`, `import * as llm from 'mockserver-client/llm'`, or by name, `import { llmMock, completion } from 'mockserver-client/llm'`; `import llm from 'mockserver-client/llm'` needs `esModuleInterop`. That module has no `default` property.

From an ES module (`.mjs`, `.mts`, or a package with `"type": "module"`) named imports work too, `import { mockServerClient, llm } from 'mockserver-client'`, as do `import * as` and the default import, `import mockServer from 'mockserver-client'`. A module other than the package itself can be imported with or without its extension: `import { llmMock, completion } from 'mockserver-client/llm'`.

The package's `exports` map names every path that can be imported or required: the package itself, each of its modules with and without `.js` (`llm`, `setupMockServer`, `mockServerClient`, `mcpMockBuilder`, `a2aMockBuilder`, `index`, `sendRequest`, `webSocketClient`), the types-only `llmTypes` and `mockServer` (for `import type`), and `package.json`. Any other path inside the package, such as a `.d.ts` file or a test file, fails with `ERR_PACKAGE_PATH_NOT_EXPORTED`. Each path with typings names them under a `types` condition, so TypeScript finds them under the `node16`, `nodenext` and `bundler` module resolutions.

## Setup Expectation

A simple expectation can be set up as follows:

```js
mockServerClient("localhost", 1080)
    .mockSimpleResponse('/somePath', { name: 'value' }, 203)
    .then(
        function(result) {
            // do something next
        }, 
        function(error) {
            // handle error
        }
    );
```

A more complex expectation can be set up like this:

```js
mockServerClient("localhost", 1080)
    .mockAnyResponse(
        {
            'httpRequest': {
                'method': 'POST',
                'path': '/somePath',
                'queryStringParameters': [
                    {
                        'name': 'test',
                        'values': [ 'true' ]
                    }
                ],
                'body': {
                    'type': "STRING",
                    'value': 'someBody'
                }
            },
            'httpResponse': {
                'statusCode': 200,
                'body': JSON.stringify({ name: 'value' }),
                'delay': {
                    'timeUnit': 'MILLISECONDS',
                    'value': 250
                }
            },
            'times': {
                'remainingTimes': 1,
                'unlimited': false
            }
        }
    )
    .then(
        function(result) {
            // do something next
        }, 
        function(error) {
            // handle error
        }
    );
```

A header, query-string parameter, path parameter or cookie value can be a plain string, a `{ not, value }` or `{ schema }` object, or a date-time matcher. A date-time matcher has exactly one of `before`, `after` or `equalToDateTime`, which takes `now`, `now` with an offset such as `now -1 days`, or a date-time. Optional fields are `actualFormat`, `truncateExpected`, `truncateActual`, `applyTruncationLast`, `zone` and `not`. TypeScript users can type it as `DateTimeMatcher`:

```js
mockServerClient("localhost", 1080)
    .when({
        path: '/somePath',
        headers: {
            'X-Issued-At': [{ after: 'now -1 hours' }],
            'If-Modified-Since': [{ before: 'now', actualFormat: 'rfc1123' }]
        },
        cookies: { seen: { equalToDateTime: 'now', truncateActual: 'startOfDay', truncateExpected: 'startOfDay' } }
    })
    .respond({ statusCode: 200 });
```

A plain string such as `'before:2026-01-01'` is matched literally, not as a date-time.

For the full documentation see [MockServer - Creating Expectations](https://mock-server.com/mock_server/creating_expectations.html).

### Fluent `when().respond()` API

The same expectation can also be built with a chainable API that mirrors the Java
client's `when(...)` / `ForwardChainExpectation`:

```js
mockServerClient("localhost", 1080)
    .when({ path: '/somePath' })
    .respond({ statusCode: 200, body: 'some_response_body' });
```

To set the number of matches, time-to-live, priority or expectation id, use the
chainable `.withTimes()` / `.withTimeToLive()` / `.withPriority()` / `.withId()`
builders before the terminal action — this is the **recommended** style because it
is unambiguous and order-independent:

```js
mockServerClient("localhost", 1080)
    .when({ path: '/somePath' })
    .withTimes(2)
    .withTimeToLive({ unlimited: false, timeToLive: 60, timeUnit: 'SECONDS' })
    .withPriority(10)
    .withId('my-id')
    .forward({ host: 'localhost', port: 8081 });
```

> **⚠️ Positional argument order differs from the procedural methods.** `when(...)`
> takes its optional arguments as `when(request, times, timeToLive, priority)` to
> match the **Java** client. This is **not** the order used by the Node procedural
> methods (`mockSimpleResponse`, `respondWith*`, etc.), which take
> `(…, times, priority, timeToLive, id)`. In particular `when(req, 2, 5)` sets
> `timeToLive = 5` (which expects an object, so it is ignored) and leaves the
> priority unset — it does **not** set the priority to `5`. To avoid this footgun,
> prefer the `.withTimes()` / `.withTimeToLive()` / `.withPriority()` builders shown
> above rather than positional arguments.

If you do pass them positionally, the order is `(request, times, timeToLive, priority)`:

```js
mockServerClient("localhost", 1080)
    .when({ path: '/somePath' }, 2, { unlimited: false, timeToLive: 60, timeUnit: 'SECONDS' }, 10)
    .respond({ statusCode: 201 });
```

The chain returned by `when(...)` exposes the builders `withTimes`, `withTimeToLive`,
`withPriority`, `withId` and the terminal actions `respond` (httpResponse, a template,
or a class-callback), `forward` (httpForward, a template, a class-callback, or an
override-forwarded-request), `error` (httpError), `callback` (a local JS response
callback over the callback WebSocket) and `forwardCallback` (a local JS forward
callback). The action is auto-detected from the object shape: a plain object with a
string `template`/`templateType` becomes a template action, one with a `callbackClass`
becomes a server-side class-callback, otherwise it is the natural response/forward
action. Each terminal method returns the same promise as the procedural builders, so
it can be awaited or used with `.then(...)`. A template can carry `parameters`, a JSON
object it reads as `parameters.*`, so one template can be reused with different values:
`respond({ templateType: 'MUSTACHE', template: '{"statusCode": 200, "body": "{{ parameters.greeting }}"}', parameters: { greeting: 'hello' } })`.

### Advanced Response Builders

For non-HTTP and streaming protocols there are dedicated builders that take a path (or full request matcher)
plus the response action object, with optional `times`, `priority`, `timeToLive`, and `id` arguments:

```javascript
// Server-Sent Events (SSE)
client.respondWithSse('/events', {
    events: [
        { event: 'message', data: 'first' },
        { event: 'message', data: 'second' }
    ],
    closeConnection: true
});

// WebSocket
client.respondWithWebSocket('/ws', {
    messages: [ { text: 'hello' } ],
    closeConnection: false
});

// DNS
client.respondWithDns('example.com', {
    answerRecords: [
        { name: 'example.com', type: 'A', ttl: 300, value: '127.0.0.1' }
    ],
    responseCode: 'NOERROR'
});

// Raw binary
client.respondWithBinary('/binary', {
    binaryData: Buffer.from('hello').toString('base64')
});

// gRPC server-streaming
client.respondWithGrpcStream('/my.Service/StreamItems', {
    statusName: 'OK',
    messages: [
        { json: '{"value":"first"}' },
        { json: '{"value":"second"}' }
    ],
    closeConnection: true
});
```

### AI Agent Mock Builders (MCP & A2A)

Declarative builders generate the full set of expectations needed to mock an AI
agent endpoint. `client.mcpMock(path)` mocks an [MCP](https://modelcontextprotocol.io)
(Model Context Protocol) server, and `client.a2aMock(path)` mocks an
[A2A](https://a2a-protocol.org) (Agent-to-Agent) agent. Both return a fluent
builder; call `.applyTo()` to register the generated expectations, or `.build()`
to obtain the raw expectation array. They are also exported as standalone
factories (`require('mockserver-client').a2aMock`).

```javascript
// Mock an A2A agent: a static agent-card document over GET plus JSON-RPC 2.0
// tasks/send, tasks/get and tasks/cancel over POST.
client.a2aMock('/a2a')
    .withAgentName('TranslatorAgent')
    .withAgentDescription('Translates text between languages')
    .withSkill('translate')
        .withName('Translation')
        .withDescription('Translates text')
        .withTag('i18n')
        .withExample('Translate hello to Spanish')
    .and()
    // optional SSE streaming (advertises capabilities.streaming and streams
    // status-update / artifact-update events):
    .withStreaming()
    // optional push notifications (echoes the config and POSTs each completed
    // task to the webhook while still replying to the caller):
    .withPushNotifications('http://localhost:1234/callback')
    // custom handler matched by a regex over the inbound message text:
    .onTaskSend()
        .matchingMessage('translate.*')
        .respondingWith('Hola')
    .and()
    .applyTo();   // returns a promise; omit the client arg to use this client
```

### SRE Helpers (SLO verdicts & chaos experiments)

`verifySLO(criteria)` evaluates service-level objectives over the recorded SLI
samples and resolves the verdict (PASS / INCONCLUSIVE); it rejects on a FAIL
verdict, and on a disabled/invalid request (start MockServer with
`sloTrackingEnabled=true`). `startChaosExperiment(experiment)` runs a scheduled
multi-stage chaos experiment (only one experiment is active at a time).

```javascript
// Evaluate SLOs over the last 60s of forwarded traffic
client.verifySLO({
    name: 'checkout-slo',
    window: { type: 'LOOKBACK', lookbackMillis: 60000 },
    minimumSampleCount: 10,
    objectives: [
        { sli: 'LATENCY_P95', comparator: 'LESS_THAN', threshold: 250 },
        { sli: 'ERROR_RATE', comparator: 'LESS_THAN_OR_EQUAL', threshold: 0.01 }
    ]
}).then(
    function (verdict) { /* verdict.result === 'PASS' | 'INCONCLUSIVE' */ },
    function (failure) { /* FAIL verdict body, or enablement guidance */ }
);

// Run a two-stage chaos experiment against an upstream host
client.startChaosExperiment({
    name: 'rolling-brownout',
    stages: [
        { durationMillis: 30000, profiles: { 'api.example.com': { errorStatus: 503, errorProbability: 0.25 } } },
        { durationMillis: 30000, profiles: { 'api.example.com': { latency: { value: 500, timeUnit: 'MILLISECONDS' } } } }
    ]
});
```

### Client IP matcher

Match requests by the client IP address or CIDR range, IPv4 or IPv6, with `clientIp` in the request matcher. The client address is the TCP peer of the connection, not `X-Forwarded-For`; prefix with `!` to match every other address.

```js
mockServerClient("localhost", 1080).mockAnyResponse({
    httpRequest: { path: "/internal", clientIp: "10.0.0.0/8" },
    httpResponse: { body: "internal client" }
});
```

### Diagnose Matching

`explain(correlationId)` tells you which expectation would serve a recorded request now and why
each expectation ahead of it was passed over; `debugMismatch(request)` compares a request with
every active expectation; `explainUnmatched({limit, suggestFixes})` explains recent requests that
matched nothing. Each resolves with a typed result (`RequestExplanation`, `DebugMismatchResult`,
`ExplainUnmatchedResult`) and rejects with the server's reason on failure.

```js
const explanation = await client.explain("abc-123-def-456");
for (const skipped of explanation.skippedExpectations || []) {
    console.log(skipped.expectationId, skipped.reason, skipped.explanation);
}
```

## Verify Requests

It is also possible to verify that request were made:

```js
mockServerClient("localhost", 1080)
    .verify(
        {
            'method': 'POST',
            'path': '/somePath',
            'body': 'someBody'
        }, 
        1, true
    )
    .then(
        function() {
            // do something next
        }, 
        function(failure) {
            // handle verification failure
        }
    );
```
It is furthermore possible to verify that sequences of requests were made in a specific order:

```js
mockServerClient("localhost", 1080)
    .verifySequence(
        {
            'method': 'POST',
            'path': '/somePathOne',
            'body': 'someBody'
        },
        {
            'method': 'GET',
            'path': '/somePathTwo'
        },
        {
            'method': 'GET',
            'path': '/somePathThree'
        }
    )
    .then(
        function() {
            // do something next
        }, 
        function(failure) {
            // handle verification failure
        }
    );
```

For the full documentation see [MockServer - Verifying Requests](https://mock-server.com/mock_server/verification.html).

## Interactive Breakpoints

The client supports matcher-driven interactive breakpoints over the callback WebSocket. Register a breakpoint matcher to pause forwarded/proxied exchanges at specific phases and inspect/modify/continue them via callback handlers.

### Register a breakpoint

```js
// REQUEST phase only
client.addRequestBreakpoint(
    { path: '/api/.*' },
    function (request) {
        // inspect or modify the request; return a request to continue or a response to abort
        request.path = '/api/modified';
        return request;
    }
).then(function (breakpointId) {
    console.log('Breakpoint registered:', breakpointId);
});

// REQUEST + RESPONSE phases
client.addRequestAndResponseBreakpoint(
    { path: '/api/.*' },
    function (request) { return request; },           // REQUEST handler
    function (request, response) { return response; } // RESPONSE handler
).then(function (breakpointId) {
    console.log('Breakpoint registered:', breakpointId);
});

// All phases with stream frame handler
client.addBreakpoint(
    { path: '/stream/.*' },
    ['REQUEST', 'RESPONSE', 'RESPONSE_STREAM', 'INBOUND_STREAM'],
    function (request) { return request; },
    function (request, response) { return response; },
    function (pausedFrame) {
        // pausedFrame has: correlationId, streamId, sequenceNumber, direction, phase, body (base64)
        return { action: 'CONTINUE' };
        // Other actions: MODIFY (with body), DROP, INJECT (with body), CLOSE
    }
).then(function (breakpointId) {
    console.log('Breakpoint registered:', breakpointId);
});
```

### Manage breakpoints

```js
// List all matchers
client.listBreakpointMatchers().then(function (result) {
    console.log(result.matchers);
});

// Remove a specific matcher
client.removeBreakpointMatcher(breakpointId);

// Clear all matchers
client.clearBreakpointMatchers();
```

### Control-plane authentication

When MockServer requires a control-plane JWT, pass `bearerToken` or `bearerTokenSupplier` in the client options. In Node the client sends `Authorization: Bearer <token>` on every control-plane request and on the callback WebSocket used by callbacks and breakpoints; a supplier is asked again on each reconnect.

```js
var client = mockServerClient("localhost", 1080, undefined, false, undefined, { bearerToken: process.env.MOCKSERVER_JWT });
```

When MockServer requires mutual TLS for the control plane, pass `clientCertPemFilePath` and `clientKeyPemFilePath` (with `tls` true and, for a private CA, the CA certificate path). In Node the client presents that certificate, and trusts that CA, on every control-plane request and on the callback WebSocket.

```js
var client = mockServerClient("localhost", 1080, undefined, true, "ca.pem", { clientCertPemFilePath: "client-cert.pem", clientKeyPemFilePath: "client-key.pem" });
```

A browser cannot set headers on a WebSocket, and MockServer only accepts the token as an `Authorization` header, so in a browser callbacks and breakpoints need mutual TLS (client certificates) rather than a JWT.

## Using in tests

The client supports TC39 explicit resource management, so a `using` / `await using`
binding resets the server automatically when it goes out of scope — no manual
`afterEach(() => client.reset())` needed:

```js
const { mockServerClient } = require('mockserver-client');

it('records the request', async () => {
    await using client = mockServerClient('localhost', 1080);
    // ... register expectations, make requests ...
    // the server is reset when `client` goes out of scope
});
```

If your runtime/transpiler does not yet support `await using`, reset explicitly
in a Jest/Mocha hook:

```js
const { mockServerClient } = require('mockserver-client');
const client = mockServerClient('localhost', 1080);

afterEach(() => client.reset());
```

A callback (`mockWithCallback`, the `callback` and `forwardCallback` actions) or a
breakpoint opens a WebSocket to MockServer, and an open WebSocket keeps the Node
process running. `reset()` and disposing the client close every such WebSocket the
clients of that MockServer opened in this process, so the process can exit when the
tests are done. To close them without resetting MockServer, call `close()`, which
resolves once they have closed; the expectations and breakpoints that used them stop
working, and a later callback or breakpoint opens a new WebSocket:

```js
after(() => client.close());
```

MockServer also closes a callback's WebSocket itself when it removes the callback's
expectation: when its `times` are used up, or when it is cleared or reset, whether by
this client, another process, the REST API or the dashboard. The client then leaves
it closed and never registers that expectation again, so a reset stays reset. If
the connection drops while MockServer still holds the expectation, the client
reconnects with the same client id and the callback carries on with its remaining
`times`. A breakpoint's WebSocket is not reopened (MockServer drops a client's
breakpoints when its WebSocket closes); the next breakpoint opens a new one.

Registering a callback or a breakpoint settles only once its WebSocket is open and
MockServer has given it a client id. It rejects, and leaves nothing open to keep the
process running, if the connection is refused, the WebSocket handshake fails,
MockServer closes the WebSocket before sending the client id, or the client id has
not arrived within 10 seconds. Change that limit with the
`callbackWebSocketTimeoutMillis` option:

```js
const client = mockServerClient('localhost', 1080, undefined, false, undefined, {
    callbackWebSocketTimeoutMillis: 30000
});
```

## Start / Launch MockServer

This package (`mockserver-client`) is the REST/WebSocket client for communicating with a running MockServer. To **download and launch** a local MockServer instance (no Java or Docker required), use the companion [`mockserver-node`](https://www.npmjs.org/package/mockserver-node) package:

```shell
npx -p mockserver-node mockserver run -p 1080
```

`mockserver-node` downloads a self-contained platform bundle (`mockserver-<version>-<os>-<arch>`) from the GitHub Release, verifies its SHA-256, caches it per-user, and starts it. See the [mockserver-node README](https://www.npmjs.org/package/mockserver-node) for full details including environment variables (`MOCKSERVER_BINARY_BASE_URL`, `MOCKSERVER_BINARY_CACHE`, `MOCKSERVER_SKIP_BINARY_DOWNLOAD`) and supported platforms (linux/darwin/windows on x86_64/aarch64).

## Contributing
In lieu of a formal styleguide, take care to maintain the existing coding style. Add unit tests for any new or changed functionality. Lint and test your code using [Grunt](http://gruntjs.com/).

## Changelog

All notable and significant changes are detailed in the [MockServer changelog](https://github.com/mock-server/mockserver-monorepo/blob/master/changelog.md) 

---

Task submitted by [James D Bloom](https://blog.jamesdbloom.com)

## AI Assistant Integration

MockServer includes a built-in [MCP](https://modelcontextprotocol.io) (Model Context Protocol) server that enables AI coding assistants to create expectations, verify requests, and debug HTTP traffic programmatically.

- **MCP Endpoint:** `http://localhost:1080/mockserver/mcp`
- **AI Documentation:** [llms.txt](https://www.mock-server.com/llms.txt)
- **Setup Guide:** [AI Integration](https://www.mock-server.com/mock_server/ai_mcp_setup.html)
