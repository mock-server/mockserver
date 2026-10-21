# MockServer PHP Client

PHP client for [MockServer](https://www.mock-server.com) - enables easy mocking of any system you integrate with via HTTP or HTTPS.

## Requirements

- PHP 8.1+
- Composer

## Installation

```bash
composer require mock-server/mockserver-client
```

## Quick Start

```php
<?php

use MockServer\MockServerClient;
use MockServer\HttpRequest;
use MockServer\HttpResponse;
use MockServer\VerificationTimes;

// Connect to MockServer
$client = new MockServerClient('localhost', 1080);

// Create an expectation
$client->when(
    HttpRequest::request()->method('GET')->path('/hello')
)->respond(
    HttpResponse::response()
        ->statusCode(200)
        ->header('Content-Type', 'application/json')
        ->body('{"message":"world"}')
);

// Verify the request was received
$client->verify(
    HttpRequest::request()->path('/hello'),
    VerificationTimes::atLeast(1)
);

// Reset all expectations
$client->reset();
```

## API

### Creating Expectations

```php
use MockServer\Times;
use MockServer\TimeToLive;
use MockServer\Delay;
use MockServer\HttpForward;

// Respond with a delay
$client->when(
    HttpRequest::request()->method('POST')->path('/api/data')
        ->header('Content-Type', 'application/json')
        ->jsonBody(['key' => 'value'])
)->respond(
    HttpResponse::response()
        ->statusCode(201)
        ->body('{"id": 1}')
        ->delay(Delay::milliseconds(500))
);

// Match only 3 times, with priority
$client->when(
    HttpRequest::request()->path('/limited'),
    Times::exactly(3),
    TimeToLive::exactly('SECONDS', 60),
    priority: 10
)->respond(
    HttpResponse::response()->statusCode(200)
);

// Forward to another server
$client->when(
    HttpRequest::request()->path('/proxy')
)->forward(
    HttpForward::forward()->host('backend.local')->port(8080)->scheme('HTTP')
);
```

### JWT Request Matcher

Match a request by inspecting a JSON Web Token carried on it (by default the
`authorization` header with a `Bearer` scheme). Each claim is matched by exact
string or regular expression; a leading `!` negates the match for that claim.

```php
use MockServer\Jwt;

$client->when(
    HttpRequest::request()->method('GET')->path('/secure')
        ->jwt(
            Jwt::jwt()
                ->claim('sub', 'user-123')          // exact match
                ->claim('role', '!admin')           // negated match
                ->claim('email', '^.+@example.com$') // regex match
                ->issuer('https://issuer.example.com')
                ->audience('my-api')
                ->algorithm('RS256')
                ->header('authorization')            // optional (default: authorization)
                ->scheme('Bearer')                   // optional
        )
)->respond(
    HttpResponse::response()->statusCode(200)
);
```

### ALL_OF Body Matcher

Require several body matchers to all match the same request body. Compose any
typed body matcher arrays — `HttpRequest::jsonPathBody()` and
`HttpRequest::regexBody()` are provided as convenient helpers.

```php
$client->when(
    HttpRequest::request()->method('POST')->path('/api/data')
        ->allOfBody([
            HttpRequest::jsonPathBody('$.name'),
            HttpRequest::regexBody('.*active.*'),
        ])
)->respond(
    HttpResponse::response()->statusCode(200)
);
```

### Date-Time Value Matchers

A header, query string parameter, path parameter or cookie value can be a
`DateTimeMatcher` instead of a plain string, added with `headerMatcher()`,
`queryStringParameterMatcher()`, `cookieMatcher()` or `pathParameter()`
(which takes strings or matchers). The request value must be a
date-time before, after or equal to the expected one. The expected value is
`now`, `now <offset>` (for example `now -1 hours`) or an ISO-8601, RFC-1123 or
epoch date-time. Options are `withActualFormat()`, `withTruncateExpected()`,
`withTruncateActual()`, `withApplyTruncationLast()`, `withZone()` and
`withNot()`; each returns a new matcher. A plain string such as
`'before:2026-01-01'` is still matched literally.

```php
use MockServer\DateTimeMatcher;

$client->when(
    HttpRequest::request()->method('GET')->path('/reports')
        ->headerMatcher('X-Issued-At', DateTimeMatcher::after('now -1 hours'))
        ->headerMatcher('If-Modified-Since', DateTimeMatcher::before('2026-10-21T00:00:00Z')->withActualFormat('rfc1123'))
        ->queryStringParameterMatcher('from', DateTimeMatcher::after('2026-01-01')->withActualFormat('dd/MM/yyyy'))
        ->cookieMatcher('seen', DateTimeMatcher::equalToDateTime('now')->withTruncateExpected('startOfDay')->withTruncateActual('startOfDay'))
)->respond(
    HttpResponse::response()->statusCode(200)
);
```

### Conditional and OpenAPI Request Matchers

`HttpRequest::conditional($if, $then, $else)` builds an if/then/else matcher,
written as an `httpRequest` holding `if`, `then` and `else`: when `$if` matches,
`$then` must match too, otherwise `$else` must (with no else, it matches
whenever `$if` does not). Each branch is an `HttpRequest`, including an OpenAPI
matcher (`HttpRequest::openAPI($specUrlOrPayload, $operationId)`) or another
conditional; `->not()` inverts any matcher. `HttpRequest::fromArray()` reads
these back, and `Expectation::fromArray()` exposes the typed request through
`getHttpRequest()`. These need a client released after 8.0.0. <!-- version-ref:keep -->

```php
$client->when(
    HttpRequest::conditional(
        HttpRequest::request()->method('POST')->header('content-type', 'application/json'),
        HttpRequest::request()->jsonSchemaBody('{"type": "object", "required": ["orderId"]}'),
        HttpRequest::request()->method('GET')
    )
)->respond(
    HttpResponse::response()->statusCode(200)
);
```

### Class Callbacks

A **class callback** references a server-side class (already on MockServer's
classpath) that implements one of MockServer's callback interfaces, and lets
MockServer compute the response — or the request to forward — dynamically. It is
pure JSON over the control plane, so it works fully from the PHP client.

```php
use MockServer\Expectation;
use MockServer\HttpClassCallback;
use MockServer\Delay;

// Respond using a server-side ExpectationResponseCallback class
$client->upsertExpectation(
    (new Expectation())
        ->httpRequest(HttpRequest::request()->method('GET')->path('/dynamic'))
        ->httpResponseClassCallback('com.example.MyResponseCallback')
);

// Optionally add a delay / mark the connection primary
$client->upsertExpectation(
    (new Expectation())
        ->httpRequest(HttpRequest::request()->path('/delayed'))
        ->httpResponseClassCallback(
            HttpClassCallback::callback('com.example.MyResponseCallback')
                ->delay(Delay::milliseconds(250))
                ->primary(true)
        )
);

// Forward using a server-side ExpectationForwardCallback class
$client->upsertExpectation(
    (new Expectation())
        ->httpRequest(HttpRequest::request()->path('/proxy'))
        ->httpForwardClassCallback('com.example.MyForwardCallback')
);
```

> **Object / closure callbacks are not available in PHP.** MockServer also
> supports *object* (closure) callbacks, where the callback runs in your own
> process: the client opens a callback **WebSocket**, the server hands it a
> `clientId`, and on each match it streams the request to your code, which
> returns the response. The PHP client is **REST-only and does not implement
> that callback WebSocket**, so object/closure callbacks cannot be used from
> PHP — use a **class callback** (a class on the MockServer classpath) instead.
> This is a transport limitation of the PHP client, not of MockServer.

See [`examples/php/callback`](../examples/php/callback) for a runnable example.

### Verification

```php
use MockServer\VerificationTimes;

// Verify at least once
$client->verify(
    HttpRequest::request()->path('/hello'),
    VerificationTimes::atLeast(1)
);

// Verify exactly 3 times
$client->verify(
    HttpRequest::request()->method('POST')->path('/api'),
    VerificationTimes::exactly(3)
);

// Verify sequence
$client->verifySequence(
    HttpRequest::request()->path('/first'),
    HttpRequest::request()->path('/second')
);
```

### Retrieving Recorded Data

```php
// Retrieve recorded requests
$requests = $client->retrieveRecordedRequests(
    HttpRequest::request()->path('/api')
);

// Retrieve active expectations
$expectations = $client->retrieveActiveExpectations();

// Retrieve log messages
$logs = $client->retrieveLogMessages();
```

### Debugging Why a Request Did Not Match

```php
// Which expectation would serve a recorded request now, and why each one ahead of it lost
$explanation = $client->explain($correlationId);
foreach ($explanation->skippedExpectations as $skipped) {
    echo "{$skipped->expectationId} {$skipped->reason}: {$skipped->explanation}\n";
}

// How a request compares, field by field, with every active expectation
$result = $client->debugMismatch(HttpRequest::request()->method('GET')->path('/api/users'));

// The closest expectations for recent unmatched requests, with suggested JSON patches
$unmatched = $client->explainUnmatched(5, true);
```

The results are typed objects in `MockServer\Diagnostics` (`RequestExplanation`,
`MismatchDebugResult`, `UnmatchedExplanation`).

### Control Operations

```php
// Clear specific expectations/logs
$client->clear(HttpRequest::request()->path('/old'));
$client->clear(null, 'EXPECTATIONS');  // type: EXPECTATIONS, LOG, or ALL
$client->clearById('my-expectation-id');

// Reset everything
$client->reset();

// Check server status
$status = $client->status();  // ['ports' => [1080]]

// Bind additional ports
$client->bind(1081, 1082);

// Check if server is running
if ($client->hasStarted()) {
    echo "MockServer is ready";
}
```

### SRE Control Plane

Methods for resilience verification — load generation, fault injection, SLO
verdicts, preemption (cordon/drain) and scheduled chaos experiments. Some are
gated behind a server start-up flag and raise `FeatureNotEnabledException`
(HTTP 403) until that flag is set.

```php
use MockServer\LoadCheck;
use MockServer\LoadScenario;
use MockServer\LoadProfile;
use MockServer\LoadStage;

// Load generation registry: register scenarios (always allowed), then start
// them (requires loadGenerationEnabled=true). Each scenario is an SLI producer.
$scenario = LoadScenario::scenario('checkout-load')
    ->maxRequests(5000)
    ->profile(LoadProfile::of(
        LoadStage::vuRamp(1, 10, 10000),   // warm up
        LoadStage::vuHold(10, 30000),      // steady state
        LoadStage::pause(5000),            // cool down
    ))
    ->addStep(
        HttpRequest::request()->method('GET')->path('/api/item/$iteration.index'),
        Delay::milliseconds(20),
    );

$client->loadScenario($scenario);            // PUT — register (does not run)
$client->startLoadScenarios('checkout-load');// PUT /start — drive load (name or array)
$client->loadScenarios();                    // GET — list all + state
$client->getLoadScenario('checkout-load');   // GET /{name}
$client->stopLoadScenarios();                // PUT /stop — stop all (or pass names)
$client->deleteLoadScenario('checkout-load');// DELETE /{name}
$client->clearLoadScenarios();               // DELETE — remove all

// Or register-and-start in one call:
$client->runLoadScenario($scenario);

// Per-step checks (LoadCheck) assert on each response; failures feed the
// CHECK_FAILURE_RATE threshold. LoadCheck::fromArray() reads them back.
$checked = LoadScenario::scenario('checked-scenario')
    ->profile(LoadProfile::of(LoadStage::vuHold(5, 60000)))
    ->addStep(
        HttpRequest::request()->method('GET')->path('/api/orders/123'),
        checks: [
            LoadCheck::status('EQUALS', '200'),
            LoadCheck::header('Content-Type', 'CONTAINS', 'application/json'),
            LoadCheck::bodyJsonPath('$.status', 'EQUALS', 'CONFIRMED'),
        ],
    );

// Service-scoped HTTP chaos for a downstream host (optional TTL dead-man's switch)
$client->setServiceChaos('payments.internal:8443', [
    'errorStatus' => 503,
    'errorProbability' => 0.3,
    'latency' => ['timeUnit' => 'MILLISECONDS', 'value' => 200],
], 60000);

// SLO verdict (requires sloTrackingEnabled=true). FAIL raises VerificationException;
// PASS / INCONCLUSIVE return the verdict array.
$verdict = $client->verifySlo([
    'name' => 'checkout-slo',
    'window' => ['type' => 'LOOKBACK', 'lookbackMillis' => 60000],
    'minimumSampleCount' => 20,
    'objectives' => [
        ['sli' => 'LATENCY_P95', 'comparator' => 'LESS_THAN', 'threshold' => 250.0],
        ['sli' => 'ERROR_RATE', 'comparator' => 'LESS_THAN_OR_EQUAL', 'threshold' => 0.01],
    ],
]);

// Preemption — cordon and drain the server (Kubernetes node drain / spot reclaim)
$client->setPreemption(['mode' => 'both', 'drainMillis' => 10000, 'ttlMillis' => 60000]);
$client->preemptionStatus();   // GET
$client->clearPreemption();    // DELETE — uncordon

// Scheduled multi-stage chaos experiment
$client->startChaosExperiment([
    'name' => 'gradual-degradation',
    'stages' => [
        ['durationMillis' => 10000, 'profiles' => ['api.example.com' => ['errorStatus' => 500, 'errorProbability' => 0.1]]],
        ['durationMillis' => 10000, 'profiles' => ['api.example.com' => ['errorStatus' => 500, 'errorProbability' => 0.5]]],
    ],
]);
```

## Mocking LLM APIs

Fluent builders under the `MockServer\Llm` namespace mock provider-agnostic LLM
completions, embeddings, multi-turn conversations and provider failover. The
wire JSON they produce is identical to the Java, Node and Python clients, so a
mock scripted from PHP behaves the same as one scripted anywhere else.

```php
<?php

use MockServer\Llm\Completion;
use MockServer\Llm\IsolationSource;
use MockServer\Llm\LlmConversationBuilder;
use MockServer\Llm\LlmFailoverBuilder;
use MockServer\Llm\LlmMockBuilder;
use MockServer\Llm\Provider;
use MockServer\Llm\Role;
use MockServer\Llm\Usage;

// Single completion mock
LlmMockBuilder::llmMock('/v1/chat/completions')
    ->withProvider(Provider::OPENAI)
    ->withModel('gpt-4o')
    ->respondingWith(
        Completion::completion()
            ->withText('Hello from a mocked model!')
            ->withStopReason('stop')
            ->withUsage(Usage::usage()->withInputTokens(12)->withOutputTokens(8))
    )
    ->applyTo($client);

// Multi-turn conversation (advances MockServer scenario state per turn),
// isolated per session by a request header
LlmConversationBuilder::conversation()
    ->withPath('/v1/chat/completions')
    ->withProvider(Provider::ANTHROPIC)
    ->isolateBy(IsolationSource::header('x-session-id'))
    ->turn()
        ->whenLatestMessageRole(Role::USER)
        ->respondingWith(Completion::completion()->withText('Hi! How can I help?'))
    ->turn()
        ->respondingWith(Completion::completion()->withText('Goodbye!'))
    ->applyTo($client);

// Provider failover: fail twice, then succeed (consecutive identical failures
// are coalesced; default JSON error bodies are supplied per status code)
LlmFailoverBuilder::llmFailover()
    ->withPath('/v1/chat/completions')
    ->withProvider(Provider::OPENAI)
    ->failWith(429)
    ->failWith(503, 2)
    ->thenRespondWith(Completion::completion()->withText('Recovered'))
    ->applyTo($client);
```

## Mocking MCP Servers

`MockServer\Mcp\McpMockBuilder` emulates a Streamable-HTTP MCP (Model Context
Protocol) server speaking JSON-RPC 2.0. It generates the full set of
expectations — `initialize`, `ping`, `notifications/initialized`, plus
`tools/list` + `tools/call`, `resources/list` + `resources/read` and
`prompts/list` + `prompts/get` for any tools, resources and prompts declared.

```php
<?php

use MockServer\Mcp\McpMockBuilder;
use MockServer\Llm\Role;

McpMockBuilder::mcpMock('/mcp')
    ->withServerName('WeatherServer')
    ->withTool('get_weather')
        ->withDescription('Get the weather for a city')
        ->withInputSchema('{"type":"object","properties":{"city":{"type":"string"}}}')
        ->respondingWith('72F and sunny')
    ->and()
    ->withResource('file:///config.json')
        ->withName('config')
        ->withMimeType('application/json')
        ->withContent('{"debug":true}')
    ->and()
    ->withPrompt('greeting')
        ->withArgument('name', 'Who to greet', true)
        ->respondingWith(Role::ASSISTANT, 'Hello there!')
    ->and()
    ->applyTo($client);
```

Every builder also exposes `build()` to obtain the raw `Expectation`
object(s) without registering them (a single `Expectation` for `llmMock`,
or an array of `Expectation` for conversations, failover and MCP).

## Mocking A2A Agents

`MockServer\A2a\A2aMockBuilder` emulates an A2A (Agent-to-Agent) agent speaking
JSON-RPC 2.0 over HTTP. It generates the agent card (`GET /.well-known/agent.json`
by default) plus the `tasks/send`, `tasks/get` and `tasks/cancel` JSON-RPC
methods, and optionally streaming (SSE) and push-notification delivery. A2A is
purely declarative (request matchers plus templated/literal responses), so it is
fully supported by the REST-only PHP client.

```php
<?php

use MockServer\A2a\A2aMockBuilder;

A2aMockBuilder::a2aMock('/a2a')
    ->withAgentName('WeatherAgent')
    ->withAgentDescription('Provides weather forecasts')
    ->withSkill('forecast')
        ->withName('Forecast')
        ->withDescription('Weather forecasting')
        ->withTag('weather')
        ->withExample('What is the weather in London?')
    ->and()
    ->onTaskSend()
        ->matchingMessage('.*weather.*')
        ->respondingWith('72F and sunny')
    ->and()
    ->withStreaming()                                  // advertise + mock message/stream (SSE)
    ->withPushNotifications('http://localhost:1234/a2a/callback')
    ->applyTo($client);
```

When `withPushNotifications(...)` is set, each `tasks/send` returns the JSON-RPC
task response to the caller **and** POSTs the completed task to the webhook URL,
and the agent card advertises `capabilities.pushNotifications: true`. Custom
`onTaskSend()` handlers are matched by a regular expression against
`params.message.parts[0].text`. Like the MCP builder, `build()` returns the raw
array of `Expectation` objects without registering them.

## Start / Launch MockServer

The PHP client does not include a binary launcher (PHP lacks the native WebSocket and subprocess management required for embedded launch). To start MockServer, use one of the following approaches:

- **Docker:** `docker run -d -p 1080:1080 mockserver/mockserver`
- **Executable JAR:** `java -jar mockserver-netty-no-dependencies-<version>.jar -serverPort 1080`
- **Homebrew:** `brew install mockserver && mockserver -serverPort 1080`
- **Another client's launcher:** The Node, Python, Ruby, Go, .NET, and Rust clients can each download and launch MockServer automatically without Java or Docker.

See the [Running MockServer](https://www.mock-server.com/mock_server/running_mock_server.html) documentation for all available options.

## Building

```bash
composer install
```

## Using in tests (PHPUnit)

`MockServer\Testing\MockServerTestTrait` provides a `MockServerClient` and resets
the server before and after each test, so recorded requests, expectations and
logs never leak between tests. Mix it into your PHPUnit test case and call the
lifecycle helpers from `setUp()` / `tearDown()`:

```php
use MockServer\Testing\MockServerTestTrait;
use PHPUnit\Framework\TestCase;

final class MyTest extends TestCase
{
    use MockServerTestTrait;

    protected function setUp(): void    { $this->setUpMockServer(); }
    protected function tearDown(): void { $this->tearDownMockServer(); }

    public function testSomething(): void
    {
        // $this->mockServer is a reset MockServerClient ready to use
        $this->mockServer->reset();
    }
}
```

The server URL is read from the `MOCKSERVER_URL` environment variable (for
example `http://localhost:1080`); when it is unset the test is skipped.

## Running Tests

Unit tests (no server required):

```bash
vendor/bin/phpunit --testsuite Unit
```

Integration tests (requires a running MockServer):

```bash
MOCKSERVER_URL=http://localhost:1080 vendor/bin/phpunit --testsuite Integration
```

## License

Apache 2.0 - see [LICENSE](../LICENSE.md)
