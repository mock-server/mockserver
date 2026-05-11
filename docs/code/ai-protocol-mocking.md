# AI & RPC Protocol Mocking (SSE, WebSocket, JSON-RPC, MCP, A2A, gRPC)

## Overview

MockServer supports mocking AI protocol servers including MCP (Model Context Protocol) and A2A (Agent-to-Agent Protocol). This is distinct from MockServer's own MCP control plane (`/mockserver/mcp`) — these features enable mocking *other people's* MCP and A2A servers for testing.

## Architecture

### Core Building Blocks

Two primitive building blocks enable all AI protocol mocking:

1. **SSE Streaming Responses** (`HttpSseResponse`) — an `Action` type that streams Server-Sent Events to the client
2. **JSON-RPC Body Matching** (`JsonRpcBody`) — a `Body` matcher that validates JSON-RPC 2.0 method names and optionally validates `params` against a JSON Schema

### Higher-Level Builders

Built on top of the primitives:

- **`McpMockBuilder`** — generates a complete set of `Expectation[]` objects for a mock MCP server
- **`A2aMockBuilder`** — generates a complete set of `Expectation[]` objects for a mock A2A agent

### Layer Architecture

```mermaid
flowchart TB
    subgraph "Client API Layer"
        MCPBuilder["McpMockBuilder"]
        A2ABuilder["A2aMockBuilder"]
    end

    subgraph "Expectation Generation"
        JsonRpcBody["JsonRpcBody matcher"]
        JsonPathBody["JsonPathBody matcher"]
        VelocityTemplate["Velocity Templates"]
        SseResponse["HttpSseResponse action"]
    end

    subgraph "Core MockServer"
        Expectation["Expectation"]
        Matcher["HttpRequestPropertiesMatcher"]
        ActionHandler["HttpActionHandler"]
        SseHandler["HttpSseResponseActionHandler"]
        TemplateHandler["HttpResponseTemplateActionHandler"]
    end

    MCPBuilder --> JsonRpcBody
    MCPBuilder --> JsonPathBody
    MCPBuilder --> VelocityTemplate
    A2ABuilder --> JsonRpcBody
    A2ABuilder --> JsonPathBody
    A2ABuilder --> VelocityTemplate

    JsonRpcBody --> Matcher
    JsonPathBody --> Matcher
    VelocityTemplate --> TemplateHandler
    SseResponse --> SseHandler

    Matcher --> Expectation
    SseHandler --> ActionHandler
    TemplateHandler --> ActionHandler
```

## SSE Streaming Responses

### Model

- **`SseEvent`** (`mockserver-core/src/main/java/org/mockserver/model/SseEvent.java`) — a single SSE event with fields: `event`, `data`, `id`, `retry`, `delay`
- **`HttpSseResponse`** (`mockserver-core/src/main/java/org/mockserver/model/HttpSseResponse.java`) — action type extending `Action<HttpSseResponse>` with `statusCode`, `headers`, a list of `SseEvent` objects, and a `closeConnection` flag

### Action Type

`Action.Type.SSE_RESPONSE` was added to the `Action.Type` enum. `HttpActionHandler` routes requests matching an `HttpSseResponse` action to `HttpSseResponseActionHandler`.

### Handler

`HttpSseResponseActionHandler` (`mockserver-core/src/main/java/org/mockserver/mock/action/http/HttpSseResponseActionHandler.java`) writes the SSE stream directly via Netty's `ChannelHandlerContext`. It:

1. Writes HTTP response headers (`Content-Type: text/event-stream`, `Transfer-Encoding: chunked`, `Cache-Control: no-cache`, `Connection: keep-alive`) plus any custom headers from the action
2. Recursively schedules each event via `Scheduler`, using the per-event `Delay` if present or executing immediately if not
3. Formats each event per the SSE specification — multi-line `data` values are split into multiple `data:` lines; `id`, `event`, and `retry` fields are written when non-null
4. Writes `LastHttpContent.EMPTY_LAST_CONTENT` to terminate the chunked stream, then closes the channel if `closeConnection` is `true` (or null, which defaults to closing)

```mermaid
sequenceDiagram
    participant Client
    participant MockServer
    participant Handler as HttpSseResponseActionHandler
    participant Scheduler

    Client->>MockServer: GET /events
    MockServer->>Handler: SSE_RESPONSE action matched
    Handler->>Client: HTTP 200 headers (text/event-stream, chunked)
    loop For each SseEvent
        Handler->>Scheduler: schedule with per-event Delay
        Scheduler->>Handler: execute after delay
        Handler->>Client: SSE event chunk (id, event, retry, data lines)
    end
    Handler->>Client: LastHttpContent (terminates chunked transfer)
    Handler->>Client: close connection (if closeConnection true or null)
```

### Serialization

- **`SseEventDTO`** (`mockserver-core/.../serialization/model/SseEventDTO.java`) and **`HttpSseResponseDTO`** (`mockserver-core/.../serialization/model/HttpSseResponseDTO.java`) handle REST API serialization and deserialization
- **`ExpectationDTO`** includes an `httpSseResponse` field mapped to `HttpSseResponseDTO`
- `BodyDTODeserializer` and `StrictBodyDTODeserializer` handle the `JSON_RPC` body type
- `BodyDTO.createDTO()` maps `JsonRpcBody` to `JsonRpcBodyDTO`

## JSON-RPC Body Matching

### Model

**`JsonRpcBody`** (`mockserver-core/src/main/java/org/mockserver/model/JsonRpcBody.java`) extends `Body<String>` with `Body.Type.JSON_RPC`. It has two fields:

| Field | Required | Purpose |
|-------|----------|---------|
| `method` | Yes | Method name to match (exact string or Java regex) |
| `paramsSchema` | No | JSON Schema string; when present, `params` is validated against it |

### Matcher

**`JsonRpcMatcher`** (`mockserver-core/src/main/java/org/mockserver/matchers/JsonRpcMatcher.java`) validates:

1. `jsonrpc` field equals `"2.0"`
2. `method` field matches — first by exact equality, then by `String.matches()` (regex)
3. If `paramsSchema` is set, `params` is validated using `JsonSchemaValidator`; a missing `params` field fails validation
4. Batch requests (JSON arrays) — matches if **any** element in the array satisfies all the above conditions

### Integration Points

- `HttpRequestPropertiesMatcher.buildBodyMatcher()` — added the `JSON_RPC` case to route to `JsonRpcMatcher`
- `BodyDTODeserializer` and `StrictBodyDTODeserializer` — support two JSON representations:
  - Typed: `{"type": "JSON_RPC", "method": "tools/list"}`
  - Wrapped: `{"jsonRpc": {"method": "tools/list"}}`

### Template Object Enhancement

`HttpRequestTemplateObject` (`mockserver-core/.../templates/engine/model/HttpRequestTemplateObject.java`) was extended with three fields extracted from JSON-RPC request bodies:

| Field | Velocity variable | Value |
|-------|-------------------|-------|
| `jsonRpcId` | `$!{request.jsonRpcId}` | String representation of the ID (text nodes use text value; numeric/null use `toString()`) |
| `jsonRpcRawId` | `$!{request.jsonRpcRawId}` | Raw JSON representation — preserves `1` for numbers and `"abc"` for strings; used for embedding directly in JSON response bodies |
| `jsonRpcMethod` | `$!{request.jsonRpcMethod}` | The `method` field value |

Extraction is best-effort: any parse error is silently swallowed, leaving all three fields null.

## WebSocket Mocking

### Model

- **`WebSocketMessage`** (`mockserver-core/.../model/WebSocketMessage.java`) — Single WebSocket message with `text`, `binary`, and `delay` fields
- **`HttpWebSocketResponse`** (`mockserver-core/.../model/HttpWebSocketResponse.java`) — Action type extending `Action<HttpWebSocketResponse>` with `subprotocol`, `messages` list, and `closeConnection` flag

### Action Type

`Action.Type.WEBSOCKET_RESPONSE` was added to the enum. This triggers the `HttpWebSocketResponseActionHandler`.

### Handler

`HttpWebSocketResponseActionHandler` performs the WebSocket handshake using Netty's `WebSocketServerHandshakerFactory`, then sends configured messages as `TextWebSocketFrame` or `BinaryWebSocketFrame`. It:

1. Reconstructs a Netty `FullHttpRequest` from the MockServer `HttpRequest` (preserving headers including `Sec-WebSocket-Key`)
2. Performs the WebSocket handshake
3. Removes HTTP codecs from the pipeline
4. Sends each message with optional per-message delays
5. Optionally sends `CloseWebSocketFrame` and closes the connection

### Usage

```java
mockServerClient.when(
    request().withMethod("GET").withPath("/ws")
).respondWithWebSocket(
    HttpWebSocketResponse.webSocketResponse()
        .withMessage(WebSocketMessage.webSocketMessage("hello"))
        .withMessage(WebSocketMessage.webSocketMessage("world"))
        .withCloseConnection(true)
);
```

## MCP Mock Builder

### Purpose

`McpMockBuilder` generates a complete set of `Expectation[]` objects that make MockServer behave as a mock MCP server. This allows testing MCP clients against a predictable, configurable mock.

### Location

`mockserver-client-java/src/main/java/org/mockserver/client/McpMockBuilder.java`

### Defaults

| Property | Default |
|----------|---------|
| `path` | `/mcp` |
| `serverName` | `MockMCPServer` |
| `serverVersion` | `1.0.0` |
| `protocolVersion` | `2025-03-26` |

### Generated Expectations

| MCP Method | Request Matcher | Response Type |
|---|---|---|
| `initialize` | `POST {path}` + `JsonRpcBody("initialize")` | Velocity template — echoes `jsonRpcRawId`, returns server info and capabilities |
| `ping` | `POST {path}` + `JsonRpcBody("ping")` | Velocity template — echoes `jsonRpcRawId`, returns `{}` |
| `notifications/initialized` | `POST {path}` + `JsonRpcBody("notifications/initialized")` | Static `HttpResponse` 200 with empty JSON body |
| `tools/list` | `POST {path}` + `JsonRpcBody("tools/list")` | Velocity template — returns configured tools array |
| `tools/call` (per tool) | `POST {path}` + `JsonPathBody` matching `method == 'tools/call'` and `params.name == '{toolName}'` | Velocity template — returns text content and `isError` flag |
| `resources/list` | `POST {path}` + `JsonRpcBody("resources/list")` | Velocity template — returns configured resources array |
| `resources/read` (per resource) | `POST {path}` + `JsonPathBody` matching `method == 'resources/read'` and `params.uri == '{uri}'` | Velocity template — returns resource `text` and `mimeType` |
| `prompts/list` | `POST {path}` + `JsonRpcBody("prompts/list")` | Velocity template — returns configured prompts array |
| `prompts/get` (per prompt) | `POST {path}` + `JsonPathBody` matching `method == 'prompts/get'` and `params.name == '{promptName}'` | Velocity template — returns messages array |

The `tools/list`, `resources/list`, and `prompts/list` expectations are generated whenever tools, resources, or prompts are registered respectively, or when the corresponding capability flag (`withToolsCapability()`, etc.) is explicitly set.

### JSON-RPC ID Echoing

All Velocity templates embed `$!{request.jsonRpcRawId}` as the `id` field in the JSON-RPC response body. This preserves the original ID type (number or string) and ensures correct request-response correlation for MCP clients.

### Usage

```java
McpMockBuilder.mcpMock("/mcp")
    .withServerName("TestMCP")
    .withServerVersion("1.0.0")
    .withTool("get_weather")
        .withDescription("Get weather for a city")
        .respondingWith("72F and sunny")
        .and()
    .withResource("config://app")
        .withName("App Config")
        .withMimeType("application/json")
        .withContent("{\"debug\": true}")
        .and()
    .withPrompt("summarize")
        .withDescription("Summarize text")
        .withArgument("text", "Text to summarize", true)
        .respondingWith("assistant", "Here is your summary.")
        .and()
    .applyTo(mockServerClient);
```

`applyTo(MockServerClient)` calls `client.upsert(build())`. `build()` can also be called directly to obtain the `Expectation[]` array without applying it.

## A2A Mock Builder

### Purpose

`A2aMockBuilder` generates expectations for a mock A2A (Agent-to-Agent Protocol) agent. The A2A protocol uses JSON-RPC 2.0 over HTTP with an Agent Card discovery mechanism (`GET /.well-known/agent.json`).

### Location

`mockserver-client-java/src/main/java/org/mockserver/client/A2aMockBuilder.java`

### Defaults

| Property | Default |
|----------|---------|
| `path` | `/a2a` |
| `agentCardPath` | `/.well-known/agent.json` |
| `agentName` | `MockAgent` |
| `agentDescription` | `A mock A2A agent` |
| `agentVersion` | `1.0.0` |
| `agentUrl` | `http://localhost{path}` (derived) |
| `defaultTaskResponse` | `Task completed successfully` |

### Generated Expectations

| Endpoint | Request Matcher | Response Type |
|---|---|---|
| Agent Card | `GET {agentCardPath}` | Static `HttpResponse` — JSON agent card with name, description, version, url, capabilities, and skills |
| `tasks/send` | `POST {path}` + `JsonRpcBody("tasks/send")` | Velocity template — completed task with default response text |
| `tasks/get` | `POST {path}` + `JsonRpcBody("tasks/get")` | Velocity template — completed task with default response text |
| `tasks/cancel` | `POST {path}` + `JsonRpcBody("tasks/cancel")` | Velocity template — canceled task with `status.state: "canceled"` |
| Custom task handlers (per handler) | `POST {path}` + `JsonPathBody` matching `method == 'tasks/send'` and `params.message.parts[0].text =~ /{pattern}/` | Velocity template — completed or failed task with custom response text |

Custom task handlers are evaluated in registration order. Because MockServer matches expectations in priority/registration order, more specific handlers should be registered before the generic `tasks/send` catch-all.

### Usage

```java
A2aMockBuilder.a2aMock("/agent")
    .withAgentName("TranslationAgent")
    .withAgentDescription("Translates text between languages")
    .withSkill("translate")
        .withName("Translation")
        .withDescription("Translates text")
        .withTag("nlp")
        .and()
    .onTaskSend()
        .matchingMessage("translate.*")
        .respondingWith("Bonjour")
        .and()
    .applyTo(mockServerClient);
```

## gRPC Mocking

MockServer supports mocking gRPC services without requiring grpc-java as a dependency. Instead, it uses a pure Netty pipeline approach: gRPC requests are decoded from HTTP/2 + protobuf framing into JSON, routed through the existing matching engine as `POST /<service>/<method>`, and responses are re-encoded back to gRPC framing. This means all existing JSON/JSONPath/JSONSchema matchers work with gRPC automatically.

### Architecture

```mermaid
flowchart LR
    Client["gRPC Client"] -->|HTTP/2 + protobuf| GRH["GrpcToHttpRequestHandler\n(decode protobuf→JSON)"]
    GRH -->|POST /service/method\nJSON body| MH["HttpRequestHandler\n(standard matching)"]
    MH -->|JSON response| GWH["GrpcToHttpResponseHandler\n(encode JSON→protobuf)"]
    GWH -->|HTTP/2 + protobuf\n+ grpc-status trailers| Client
```

### Proto Descriptor Infrastructure

gRPC mocking requires proto descriptors so MockServer can convert between protobuf binary and JSON. Three loading mechanisms are supported:

| Mechanism | Config Property | Description |
|-----------|----------------|-------------|
| Descriptor files (`.dsc`/`.desc`) | `grpcDescriptorDirectory` | Directory of pre-compiled descriptor set files |
| Proto source files (`.proto`) | `grpcProtoDirectory` | Directory of `.proto` files compiled at startup via `protoc` |
| Runtime REST API upload | `PUT /mockserver/grpc/descriptors` | Upload descriptor bytes at runtime via client API |

Core classes:

| Class | Module | Purpose |
|-------|--------|---------|
| `GrpcProtoDescriptorStore` | core | Registry of loaded service/method descriptors, provides converters |
| `GrpcProtoFileCompiler` | core | Compiles `.proto` files to descriptors via `protoc` |
| `GrpcJsonMessageConverter` | core | Converts protobuf binary ↔ JSON using `com.google.protobuf.util.JsonFormat` |
| `GrpcFrameCodec` | core | Encodes/decodes the 5-byte gRPC length-prefixed framing |
| `GrpcStatusMapper` | core | Maps between gRPC status codes and names |

### Netty Pipeline Integration

gRPC handlers are conditionally inserted into both h2c (HTTP/2 cleartext) and TLS-negotiated HTTP/2 pipelines when the descriptor store has loaded services:

```mermaid
graph LR
    H2C["HTTP/2 Connection Handler"] --> CB[CallbackWebSocketServerHandler]
    CB --> DASH[DashboardWebSocketHandler]
    DASH --> CODEC[MockServerHttpServerCodec]
    CODEC --> GRPC_RESP["GrpcToHttpResponseHandler"]
    GRPC_RESP --> GRPC_REQ["GrpcToHttpRequestHandler"]
    GRPC_REQ --> HANDLER[HttpRequestHandler]
```

The handlers are placed after `MockServerHttpServerCodec` so they operate on MockServer model objects. `GrpcToHttpRequestHandler` intercepts inbound `HttpRequest` objects with `content-type: application/grpc`, extracts the service and method from the path, decodes the protobuf body to JSON, and forwards with `x-grpc-service`, `x-grpc-method` headers.

`GrpcToHttpResponseHandler` is an outbound encoder that intercepts `HttpResponse` objects with `x-grpc-service` header, encodes the JSON body back to protobuf binary with gRPC framing, and appends `grpc-status` / `grpc-message` trailers.

### h2c Detection

`PortUnificationHandler.decode()` includes `isH2cPreface()` which detects the HTTP/2 connection preface (`PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n`) on cleartext connections. When detected, `switchToH2c()` assembles the HTTP/2 pipeline with gRPC handlers, enabling gRPC over plaintext HTTP/2.

### Streaming Support

`GrpcStreamResponse` is an action type for gRPC server streaming (and as a building block for other streaming patterns). It follows the same recursive scheduling pattern as `HttpSseResponse`:

| Class | Module | Purpose |
|-------|--------|---------|
| `GrpcStreamMessage` | core (model) | A single message in a stream: JSON body + optional per-message `Delay` |
| `GrpcStreamResponse` | core (model) | Action containing a list of `GrpcStreamMessage` objects and a `statusCode` |
| `GrpcStreamResponseActionHandler` | core (action) | Recursively schedules messages via `Scheduler`, encodes each to gRPC-framed protobuf, writes `grpc-status` trailers after last message |

```mermaid
sequenceDiagram
    participant Client
    participant MockServer
    participant Handler as GrpcStreamResponseActionHandler
    participant Scheduler

    Client->>MockServer: gRPC request (e.g., ListGreetings)
    MockServer->>Handler: GRPC_STREAM_RESPONSE action matched
    Handler->>Client: HTTP/2 200 headers (content-type: application/grpc)
    loop For each GrpcStreamMessage
        Handler->>Scheduler: schedule with per-message Delay
        Scheduler->>Handler: execute after delay
        Handler->>Client: gRPC-framed protobuf message
    end
    Handler->>Client: grpc-status trailers (OK)
```

### Serialization

- **`GrpcStreamMessageDTO`** and **`GrpcStreamResponseDTO`** handle REST API serialization
- **`ExpectationDTO`** includes a `grpcStreamResponse` field mapped to `GrpcStreamResponseDTO`
- **`grpcStreamResponse.json`** JSON schema is registered in `JsonSchemaExpectationValidator`

### Control Plane REST API

| Endpoint | Action |
|----------|--------|
| `PUT /mockserver/grpc/descriptors` | Upload a compiled proto descriptor set (binary body) |
| `PUT /mockserver/grpc/services` | List all loaded gRPC services and their methods |
| `PUT /mockserver/grpc/clear` | Clear all loaded descriptors and reset the store |

### Limitations

- **gRPC forwarding/proxy** is deferred — requires HTTP/2 + gRPC-framing client changes to `NettyHttpClient`
- **True client streaming and bidirectional streaming** require migration from `InboundHttp2ToHttpAdapter` (which aggregates full messages) to `Http2MultiplexHandler` with per-stream child channels
- **WAR deployment** returns 501 for `GRPC_STREAM_RESPONSE` actions (no `ChannelHandlerContext` available)
- **Proto reflection** is not yet supported — descriptors must be provided via files or API upload

## Module Boundaries

| Component | Module | Package |
|---|---|---|
| `SseEvent`, `HttpSseResponse`, `JsonRpcBody` | `mockserver-core` | `org.mockserver.model` |
| `JsonRpcMatcher` | `mockserver-core` | `org.mockserver.matchers` |
| `HttpSseResponseActionHandler` | `mockserver-core` | `org.mockserver.mock.action.http` |
| `SseEventDTO`, `HttpSseResponseDTO`, `JsonRpcBodyDTO` | `mockserver-core` | `org.mockserver.serialization.model` |
| `HttpRequestTemplateObject` (jsonRpc fields) | `mockserver-core` | `org.mockserver.templates.engine.model` |
| `GrpcStreamMessage`, `GrpcStreamResponse` | `mockserver-core` | `org.mockserver.model` |
| `GrpcFrameCodec`, `GrpcJsonMessageConverter`, `GrpcProtoDescriptorStore`, `GrpcProtoFileCompiler`, `GrpcStatusMapper`, `GrpcException` | `mockserver-core` | `org.mockserver.grpc` |
| `GrpcStreamResponseActionHandler` | `mockserver-core` | `org.mockserver.mock.action.http` |
| `GrpcStreamMessageDTO`, `GrpcStreamResponseDTO` | `mockserver-core` | `org.mockserver.serialization.model` |
| `GrpcToHttpRequestHandler`, `GrpcToHttpResponseHandler` | `mockserver-netty` | `org.mockserver.netty.grpc` |
| `McpMockBuilder`, `A2aMockBuilder` | `mockserver-client-java` | `org.mockserver.client` |

## Test Coverage

| Test Class | Module | Tests | Type |
|---|---|---|---|
| `SseEventTest` | core | 19 | Unit |
| `HttpSseResponseTest` | core | 22 | Unit |
| `JsonRpcBodyTest` | core | 21 | Unit |
| `JsonRpcMatcherTest` | core | 12 | Unit |
| `HttpSseResponseDTOTest` | core | 5 | Unit |
| `JsonRpcBodyDTOTest` | core | 5 | Unit |
| `ExpectationWithSseAndJsonRpcSerializationTest` | core | 4 | Unit |
| `HttpRequestTemplateObjectJsonRpcTest` | core | 11 | Unit |
| `McpMockBuilderTest` | client-java | 12 | Unit |
| `A2aMockBuilderTest` | client-java | 11 | Unit |
| `SseStreamingIntegrationTest` | netty | 9 | Integration |
| `McpMockBuilderIntegrationTest` | netty | 12 | Integration |
| `A2aMockBuilderIntegrationTest` | netty | 7 | Integration |
| `WebSocketMessageTest` | core | 14 | Unit |
| `HttpWebSocketResponseTest` | core | 19 | Unit |
| `WebSocketMessageModelDTOTest` | core | 5 | Unit |
| `HttpWebSocketResponseDTOTest` | core | 5 | Unit |
| `ForwardChainExpectationTest` | client-java | 10 | Unit |
| `WebSocketMockingIntegrationTest` | netty | 6 | Integration |
| `GrpcFrameCodecTest` | core | 6 | Unit |
| `GrpcJsonMessageConverterTest` | core | 7 | Unit |
| `GrpcProtoDescriptorStoreTest` | core | 7 | Unit |
| `GrpcStatusMapperTest` | core | 7 | Unit |
| `GrpcStreamResponseDTOTest` | core | 3 | Unit |
| `GrpcIntegrationTest` | netty | 11 | Integration |

## Client Library Support

All four client libraries support the new action types and body matchers:

| Feature | Java | Node.js | Python | Ruby |
|---|---|---|---|---|
| SSE Response (`httpSseResponse`) | `respondWithSse()` | `Expectation.httpSseResponse` | `respond_with_sse()` | `respond_with_sse` |
| WebSocket Response (`httpWebSocketResponse`) | `respondWithWebSocket()` | `Expectation.httpWebSocketResponse` | `respond_with_websocket()` | `respond_with_websocket` |
| JSON-RPC Body (`JSON_RPC`) | `jsonRpc("method")` | `{ type: 'JSON_RPC', method: '...' }` | `Body.json_rpc("method")` | `Body.json_rpc("method")` |
| MCP Mock Builder | `McpMockBuilder.mcpMock()` | N/A (use REST API) | N/A (use REST API) | N/A (use REST API) |
| A2A Mock Builder | `A2aMockBuilder.a2aMock()` | N/A (use REST API) | N/A (use REST API) | N/A (use REST API) |
| gRPC Stream Response (`grpcStreamResponse`) | `respondWithGrpcStream()` | N/A (use REST API) | N/A (use REST API) | N/A (use REST API) |
| gRPC Descriptor Upload | `uploadGrpcDescriptor()` | N/A (use REST API) | N/A (use REST API) | N/A (use REST API) |
| gRPC Services List | `retrieveGrpcServices()` | N/A (use REST API) | N/A (use REST API) | N/A (use REST API) |
| gRPC Descriptors Clear | `clearGrpcDescriptors()` | N/A (use REST API) | N/A (use REST API) | N/A (use REST API) |
| Callback Support | Full (WebSocket) | Full (WebSocket) | Full (WebSocket) | Full (WebSocket) |

## Related GitHub Issues

- #2143 — SSE Streaming Support
- #2168 — WebSocket Mocking
- #2115 — Streaming Response Support
- #1936 — gRPC Protocol Support (under #2173 Protocol Extensions)
