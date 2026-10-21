# MockServerClient for .NET

A hand-written, idiomatic .NET client for [MockServer](https://www.mock-server.com)'s control-plane REST API.

## Installation

The NuGet package is `MockServerClient` (the assembly and namespace remain `MockServer.Client`):

```bash
dotnet add package MockServerClient
```

Or via PackageReference in your `.csproj`:

```xml
<PackageReference Include="MockServerClient" Version="9.1.0" />
```

## Supported Targets

- .NET Standard 2.0 (for broad compatibility: .NET Framework 4.6.1+, .NET Core 2.0+, Mono 5.4+)
- .NET 8.0 (for modern APIs and best performance)

## Usage

```csharp
using MockServer.Client;
using MockServer.Client.Models;

// Create client
using var client = new MockServerClient("localhost", 1080);

// Create an expectation with fluent API
client.When(
    HttpRequest.Request()
        .WithMethod("GET")
        .WithPath("/hello")
        .WithQueryStringParameter("name", "world")
).Respond(
    HttpResponse.Response()
        .WithStatusCode(200)
        .WithHeader("Content-Type", "application/json")
        .WithBody("{\"message\":\"hello world\"}")
);

// Verify the request was received
client.Verify(
    HttpRequest.Request().WithPath("/hello"),
    VerificationTimes.AtLeastTimes(1)
);

// Reset all expectations
client.Reset();
```

### Forward Expectations

```csharp
client.When(
    HttpRequest.Request().WithPath("/proxy")
).Forward(
    HttpForward.Forward()
        .WithHost("backend.example.com")
        .WithPort(443)
        .WithScheme("HTTPS")
);
```

### Verify Sequence

```csharp
client.VerifySequence(
    HttpRequest.Request().WithPath("/first"),
    HttpRequest.Request().WithPath("/second")
);
```

### Retrieve Recorded Requests

```csharp
var requests = client.RetrieveRecordedRequests(
    HttpRequest.Request().WithPath("/api")
);
```

### Debugging Why a Request Did Not Match

```csharp
// Which expectation would serve a recorded request now, and why each one ahead of it lost
RequestExplanation explanation = client.Explain(correlationId);
foreach (var skipped in explanation.SkippedExpectations)
    Console.WriteLine($"{skipped.ExpectationId} {skipped.Reason}: {skipped.Explanation}");

// How a request compares, field by field, with every active expectation
MismatchDebugResult result = client.DebugMismatch(HttpRequest.Request().WithMethod("GET").WithPath("/api/users"));

// The closest expectations for recent unmatched requests, with suggested JSON patches
UnmatchedExplanation unmatched = client.ExplainUnmatched(limit: 5, suggestFixes: true);
```

### JWT Request Matcher

Match a bearer token's claims (exact or regex; a leading `!` negates), plus optional
issuer/audience/algorithm/header/scheme constraints. Unset properties are omitted from the wire.

```csharp
client.When(
    HttpRequest.Request()
        .WithPath("/secure")
        .WithJwt(new Jwt
        {
            Claims = new Dictionary<string, string>
            {
                ["sub"] = "user-123",
                ["role"] = "!admin",              // negated: any role except admin
                ["email"] = "^.+@example.com$"     // regex match
            },
            Issuer = "https://issuer.example.com",
            Audience = "my-api",
            Algorithm = "RS256"
        })
).Respond(
    HttpResponse.Response().WithStatusCode(200)
);
```

### ALL_OF Body Matcher

Require the request body to satisfy every sub-matcher. Combine any typed body matchers
(e.g. JSON path and regex) via `BodyMatcher`:

```csharp
client.When(
    HttpRequest.Request()
        .WithPath("/api")
        .WithAllOfBody(
            BodyMatcher.OfJsonPath("$.name"),
            BodyMatcher.OfRegex(".*active.*"))
).Respond(
    HttpResponse.Response().WithStatusCode(200)
);
```

### Conditional (if/then/else) and OpenAPI Request Matchers

`HttpRequest.RequestIf(if, then, else)` builds a conditional matcher: when `if` matches, `then`
must match too, otherwise `else` must (with no `else`, it matches whenever `if` does not). It is
sent as an `httpRequest` holding `if`/`then`/`else` (the `If`, `Then` and `Else` properties, or
`WithIf`/`WithThen`/`WithElse` on the builder). A branch may be an HTTP matcher, another
conditional, or an OpenAPI matcher from `HttpRequest.OpenApi(specUrlOrPayload, operationId)`
(`SpecUrlOrPayload`, `OperationId`, `ContextPathPrefix`). A load scenario `LoadStep` likewise takes
`Checks`, a list of `LoadCheck` assertions on each step's response. These need a client released
after 8.0.0. <!-- version-ref:keep -->

```csharp
client.When(
    HttpRequest.RequestIf(
        HttpRequest.Request().WithMethod("POST").WithHeader("content-type", "application/json"),
        HttpRequest.Request().WithJsonSchemaBody("{\"type\": \"object\", \"required\": [\"orderId\"]}"),
        HttpRequest.Request().WithMethod("GET"))
).Respond(
    HttpResponse.Response().WithStatusCode(200)
);
```

### Async API

All operations have async variants:

```csharp
await client.VerifyAsync(
    HttpRequest.Request().WithPath("/hello"),
    VerificationTimes.AtLeastTimes(1)
);
```

### Interactive Breakpoints

Register breakpoint matchers to pause forwarded/proxied traffic at REQUEST, RESPONSE, RESPONSE_STREAM, or INBOUND_STREAM phases. A callback WebSocket connection is opened automatically.

```csharp
using MockServer.Client.Models;
using System.Text.Json.Nodes;

// REQUEST-only breakpoint
var id = client.AddRequestBreakpoint(
    HttpRequest.Request().WithPath("/api/.*").Build(),
    request => request // continue with original
);

// REQUEST + RESPONSE breakpoint
var id2 = client.AddRequestResponseBreakpoint(
    HttpRequest.Request().WithPath("/api/.*").Build(),
    request => request,
    (request, response) => response
);

// Streaming breakpoint
var id3 = client.AddStreamBreakpoint(
    HttpRequest.Request().WithPath("/stream/.*").Build(),
    new[] { BreakpointPhase.ResponseStream },
    frame => StreamFrameDecision.Continue(frame.CorrelationId!)
);

// Manage matchers
var list = client.ListBreakpointMatchers();
client.RemoveBreakpointMatcher(id);
client.ClearBreakpointMatchers();
```

**Stream frame decisions:** `StreamFrameDecision.Continue`, `.Modify`, `.Drop`, `.Inject`, `.Close`.

When MockServer requires a control-plane JWT, configure it with `WithControlPlaneBearerToken(token)` (or a `Func<string>` supplier). The token is sent as `Authorization: Bearer <token>` on every control-plane request and on the breakpoint/callback WebSocket upgrade. A client certificate and CA certificate set with `MockServerClient.Builder(host, port).WithTls(...)` (or `WithClientCertificate` / `WithCaCertificate`) are used on that WebSocket too, so it passes control-plane mTLS; with your own `HttpClient`, the WebSocket gets no TLS settings from it.

## Mocking LLM providers

The `MockServer.Client.Llm` namespace provides fluent builders that mock LLM provider
APIs (Anthropic, OpenAI, Gemini, Bedrock, Azure OpenAI, Ollama). They build
expectations carrying an `httpLlmResponse` action; the produced wire JSON is
equivalent to the Java/Node/Python clients, so a mock scripted here behaves
identically to one scripted elsewhere.

### Single completion / embedding

```csharp
using MockServer.Client.Llm;

// A single chat completion mock.
LlmMockBuilder.LlmMock("/v1/chat/completions")
    .WithProvider(Provider.OPENAI)
    .WithModel("gpt-4o")
    .RespondingWith(Completion.Create()
        .WithText("Hello! How can I help?")
        .WithStopReason("stop")
        .WithUsage(Usage.Create().WithInputTokens(12).WithOutputTokens(8)))
    .ApplyTo(client);

// An embedding mock.
LlmMockBuilder.LlmMock("/v1/embeddings")
    .WithProvider(Provider.OPENAI)
    .RespondingWith(EmbeddingResponse.Create().WithDimensions(1536).WithDeterministicFromInput(true))
    .ApplyTo(client);
```

Completions also support tool calls (`WithToolCall(ToolUse.Of("name")...)`), structured
output (`WithOutputSchema`), and streaming physics (`Stream().WithStreamingPhysics(
StreamingPhysics.Create().WithTokensPerSecond(50).WithJitter(0.2))`).

### Multi-turn conversations

Conversation mocks advance MockServer scenario state turn-by-turn (`Started` -> `turn_1`
-> ... -> `__done`). Optionally isolate concurrent sessions by a request header,
query parameter, or cookie.

```csharp
LlmConversationBuilder.Conversation()
    .WithPath("/v1/chat/completions")
    .WithProvider(Provider.ANTHROPIC)
    .WithModel("claude-3-5-sonnet")
    .IsolateBy(IsolationSource.Header("x-session-id"))
    .Turn().RespondingWith(Completion.Create().WithText("Hi, I'm your assistant."))
    .Turn().WhenLatestMessageContains("weather")
        .RespondingWith(Completion.Create().WithText("It's sunny."))
    .Turn().RespondingWith(Completion.Create().WithText("Anything else?"))
    .ApplyTo(client);
```

### Failover / retry scenarios

Emit N failing responses (consumed first) followed by a success. Consecutive identical
failures are coalesced into a single limited-`times` expectation; each failure gets a
provider-shaped default error body unless you supply one.

```csharp
LlmFailoverBuilder.LlmFailover()
    .WithPath("/v1/chat/completions")
    .WithProvider(Provider.OPENAI)
    .WithModel("gpt-4o")
    .FailWith(429, count: 2)   // two rate-limit responses
    .FailWith(503)             // then one service-unavailable
    .ThenRespondWith(Completion.Create().WithText("recovered"))
    .ApplyTo(client);
```

## Mocking an MCP server

The `MockServer.Client.Mcp` namespace builds the expectation set needed to emulate a
Streamable-HTTP MCP (Model Context Protocol) server speaking JSON-RPC 2.0. It wires up
`initialize`, `ping`, `notifications/initialized`, and per-capability `*/list` and
`*/call` (or `*/read` / `*/get`) handlers.

```csharp
using MockServer.Client.Mcp;

McpMockBuilder.McpMock("/mcp")
    .WithServerName("WeatherServer")
    .WithTool("get_weather")
        .WithDescription("Get the weather for a city")
        .WithInputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}")
        .RespondingWith("72F and sunny")
    .And()
    .WithResource("file:///config.json")
        .WithName("config")
        .WithMimeType("application/json")
        .WithContent("{\"debug\":true}")
    .And()
    .WithPrompt("greeting")
        .WithArgument("name", "the name to greet", required: true)
        .RespondingWith("user", "Hello!")
    .And()
    .ApplyTo(client);
```

Each builder also exposes `Build()` returning the raw `List<Expectation>` if you want to
inspect or register them yourself.

## Mocking an A2A agent

The `MockServer.Client.A2a` namespace builds the expectation set needed to emulate an
A2A (Agent2Agent) server. It serves a GET agent-card document at `/.well-known/agent.json`
plus JSON-RPC 2.0 `tasks/send`, `tasks/get`, and `tasks/cancel` handlers, and can optionally
advertise and mock streaming (SSE) and push notifications.

```csharp
using MockServer.Client.A2a;

A2aMockBuilder.A2aMock("/a2a")
    .WithAgentName("WeatherAgent")
    .WithAgentDescription("Knows the weather")
    .WithSkill("get_weather")
        .WithName("Get Weather")
        .WithDescription("Returns the weather for a city")
        .WithTag("weather")
        .WithExample("What is the weather in London?")
    .And()
    .OnTaskSend()
        .MatchingMessage("hello.*")
        .RespondingWith("Hi there!")
    .And()
    .WithStreaming()
    .WithPushNotifications("http://localhost:1234/a2a/callback")
    .ApplyTo(client);
```

When streaming is enabled the agent card reports `capabilities.streaming: true` and the
streaming method (default `message/stream`) returns an SSE stream of `status-update` and
`artifact-update` events. When push notifications are configured the agent card reports
`capabilities.pushNotifications: true`, `tasks/pushNotificationConfig/set` echoes the
registered config, and each `tasks/send` both returns the JSON-RPC response to the caller
and POSTs the completed task to the configured webhook. As with the MCP builder, `Build()`
returns the raw `List<Expectation>` if you want to inspect or register them yourself.

## Start / Launch MockServer

The .NET client can download and launch a local MockServer instance directly -- no Java installation and no Docker required. The launcher downloads a self-contained platform bundle (`mockserver-<version>-<os>-<arch>`) from the GitHub Release, verifies its SHA-256, caches it per-user, and starts it.

### Quick start

```csharp
using MockServer.Client;

// Download (first run) and start MockServer on port 1080
using var launcher = MockServerBinaryLauncher.Start(port: 1080);
Console.WriteLine($"MockServer started, PID {launcher.Process?.Id}");

// ... use MockServer ...

launcher.Stop();
// or let the using statement dispose and stop it automatically
```

### Async API

```csharp
using var launcher = await MockServerBinaryLauncher.StartAsync(port: 1080);
```

### Just ensure the binary is present

```csharp
string launcherPath = await MockServerBinaryLauncher.EnsureBinaryAsync();
// or synchronously:
string launcherPath = MockServerBinaryLauncher.EnsureBinary();
```

### Specify a version

```csharp
using var launcher = MockServerBinaryLauncher.Start(port: 1080, version: "9.1.0");
```

### API reference

| Method / Class | Description |
|---|---|
| `MockServerBinaryLauncher.EnsureBinaryAsync(version?, options?)` | Download, verify, cache, and return the launcher path. Defaults to `DefaultVersion`. |
| `MockServerBinaryLauncher.EnsureBinary(version?, options?)` | Synchronous wrapper for `EnsureBinaryAsync`. |
| `MockServerBinaryLauncher.StartAsync(port, version?, options?)` | Ensure the binary and start MockServer. Returns a `MockServerBinaryLauncher` instance. |
| `MockServerBinaryLauncher.Start(port, version?, options?)` | Synchronous wrapper for `StartAsync`. |
| `MockServerBinaryLauncher` | Implements `IDisposable`. Properties: `Process`. Methods: `Stop()`, `Dispose()`. |
| `MockServerBinaryLauncher.DefaultVersion` | The default MockServer version, derived from the NuGet package version at runtime. |

### Supported platforms

| OS | Architecture |
|---|---|
| Linux | x86_64, aarch64 |
| macOS (darwin) | x86_64, aarch64 |
| Windows | x86_64, aarch64 |

### Environment variables

| Variable | Purpose |
|---|---|
| `MOCKSERVER_BINARY_BASE_URL` | Mirror host for the release assets (corporate / air-gapped networks) |
| `MOCKSERVER_BINARY_CACHE` | Override the cache directory (default: `~/.cache/mockserver/binaries` on Unix, `%LOCALAPPDATA%` on Windows) |
| `MOCKSERVER_SKIP_BINARY_DOWNLOAD` | Fail instead of downloading (use with a pre-seeded cache in CI) |

### Version

By default the launcher downloads the MockServer version matching this NuGet package (derived from the `<Version>` property in the `.csproj` at build time). Pass an explicit `version` argument to override.

## Building

```bash
cd mockserver-client-dotnet
dotnet build
dotnet test
```

## Using in tests (xUnit)

`MockServerFixture` is a reusable xUnit `IAsyncLifetime` fixture that creates a
`MockServerClient` and resets the server before and after each test, so recorded
requests, expectations and logs never leak between tests. Derive your test class
from it, or use it as an `IClassFixture` / collection fixture for a shared
instance:

```csharp
public class MyTests : MockServerFixture
{
    [SkippableFact]
    public void RecordsRequest()
    {
        SkipIfNoServer();
        // Client is reset before this test and again after it finishes.
        Client!.MockAnyResponse(/* ... */);
    }
}
```

The server URL is read from the `MOCKSERVER_URL` environment variable (for
example `http://localhost:1080`); when it is unset the fixture's lifecycle hooks
are no-ops and tests are skipped via `SkipIfNoServer()`.

## Requirements

- .NET SDK 8.0+ (for building)
- `System.Net.WebSockets.ClientWebSocket` (built-in, for breakpoint callback WebSocket)
- No external runtime dependencies beyond `System.Text.Json` (included via .NET or NuGet for netstandard2.0)

## License

Apache 2.0 - see [LICENSE](../LICENSE.md)
