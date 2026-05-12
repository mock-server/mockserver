# Domain Model, Matchers & Serialization

## Domain Model Hierarchy

All domain objects descend from a common base providing reflection-based equality, JSON serialization, and a NOT operator:

```mermaid
classDiagram
    class ObjectWithReflectiveEqualsHashCodeToString {
        <<base>>
    }
    class ObjectWithJsonToString {
        +toString(): JSON
    }
    class Not {
        +not: Boolean
    }

    ObjectWithReflectiveEqualsHashCodeToString <|-- ObjectWithJsonToString
    ObjectWithJsonToString <|-- Not

    class RequestDefinition {
        <<abstract>>
        +logCorrelationId: String
    }
    class HttpRequest {
        +method: NottableString
        +path: NottableString
        +pathParameters: Parameters
        +queryStringParameters: Parameters
        +body: Body
        +headers: Headers
        +cookies: Cookies
        +keepAlive: Boolean
        +secure: Boolean
        +protocol: Protocol
        +socketAddress: SocketAddress
        +clientCertificateChain: List~X509Certificate~
    }
    class OpenAPIDefinition {
        +specUrlOrPayload: String
        +operationId: String
    }

    Not <|-- RequestDefinition
    RequestDefinition <|-- HttpRequest
    RequestDefinition <|-- OpenAPIDefinition
```

### Action Types

```mermaid
classDiagram
    class Action~T~ {
        <<abstract>>
        +type: Type
        +delay: Delay
        +expectationId: String
    }
    class Delay {
        +timeUnit: TimeUnit
        +value: long
        +distribution: DelayDistribution
    }
    class DelayDistribution {
        +type: UNIFORM/LOG_NORMAL/GAUSSIAN
        +min/max: Long
        +median/p99: Long
        +mean/stdDev: Long
    }
    Delay --> DelayDistribution : optional
    class HttpResponse {
        +statusCode: Integer
        +reasonPhrase: String
        +body: Body
        +headers: Headers
        +cookies: Cookies
        +connectionOptions: ConnectionOptions
    }
    class HttpForward {
        +host: String
        +port: Integer
        +scheme: Scheme
    }
    class HttpTemplate {
        +template: String
        +templateType: TemplateType
        +actionType: Type
    }
    class HttpClassCallback {
        +callbackClass: String
        +actionType: Type
    }
    class HttpObjectCallback {
        +clientId: String
        +responseCallback: Boolean
        +actionType: Type
    }
    class HttpOverrideForwardedRequest {
        +requestOverride: HttpRequest
        +requestModifier: HttpRequestModifier
        +responseOverride: HttpResponse
        +responseModifier: HttpResponseModifier
    }
    class HttpError {
        +dropConnection: Boolean
        +responseBytes: byte[]
    }

    Action <|-- HttpResponse
    Action <|-- HttpForward
    Action <|-- HttpTemplate
    Action <|-- HttpClassCallback
    Action <|-- HttpObjectCallback
    Action <|-- HttpOverrideForwardedRequest
    class GrpcStreamResponse {
        +statusCode: Integer
        +messages: List~GrpcStreamMessage~
    }
    class GrpcStreamMessage {
        +json: String
        +delay: Delay
    }
    GrpcStreamResponse --> GrpcStreamMessage : 0..*

    Action <|-- HttpError
    Action <|-- GrpcStreamResponse
```

### Body Types

```mermaid
classDiagram
    class Body~T~ {
        <<abstract>>
        +type: Type
        +optional: Boolean
        +getValue(): T
    }
    class BodyWithContentType~T~ {
        <<abstract>>
        +mediaType: MediaType
    }

    Body <|-- BodyWithContentType
    Body <|-- RegexBody
    Body <|-- JsonSchemaBody
    Body <|-- JsonPathBody
    Body <|-- XmlSchemaBody
    Body <|-- XPathBody
    Body <|-- ParameterBody
    Body <|-- GraphQLBody

    BodyWithContentType <|-- StringBody
    BodyWithContentType <|-- JsonBody
    BodyWithContentType <|-- XmlBody
    BodyWithContentType <|-- BinaryBody
```

Body `Type` enum: `BINARY`, `JSON`, `JSON_SCHEMA`, `JSON_PATH`, `PARAMETERS`, `REGEX`, `STRING`, `XML`, `XML_SCHEMA`, `XPATH`, `JSON_RPC`, `GRAPHQL`, `LOG_EVENT`

### ConnectionOptions

`ConnectionOptions` on `HttpResponse` provides low-level control over the HTTP connection:

| Field | Type | Description |
|-------|------|-------------|
| `suppressContentLengthHeader` | Boolean | Prevent `Content-Length` header from being added |
| `contentLengthHeaderOverride` | Integer | Override `Content-Length` with a specific value |
| `suppressConnectionHeader` | Boolean | Prevent `Connection` header from being added |
| `chunkSize` | Integer | If positive, response is sent with `Transfer-Encoding: chunked` in chunks of this size |
| `chunkDelay` | Delay | Delay between each chunk when `chunkSize` is set. Uses Netty `EventLoop.schedule()` (non-blocking). Supports all delay distributions (fixed, uniform, lognormal, Gaussian). First chunk (headers) is written immediately; subsequent chunks are scheduled with cumulative delays |
| `keepAliveOverride` | Boolean | If true, `Connection: keep-alive`; if false, `Connection: close` |
| `closeSocket` | Boolean | Force close (true) or keep open (false) the socket after responding |
| `closeSocketDelay` | Delay | Delay before closing the socket (ignored if socket is not being closed) |

### Request & Response Modifiers

Used by `HttpOverrideForwardedRequest` to modify forwarded requests and responses:

**`HttpRequestModifier`** fields:

| Field | Type | Description |
|-------|------|-------------|
| `path` | `PathModifier` | Regex-based path rewriting (`regex` + `substitution`) |
| `queryStringParameters` | `QueryParametersModifier` | Add, replace, or remove query parameters |
| `headers` | `HeadersModifier` | Add, replace, or remove headers |
| `cookies` | `CookiesModifier` | Add, replace, or remove cookies |

**`HttpResponseModifier`** fields:

| Field | Type | Description |
|-------|------|-------------|
| `headers` | `HeadersModifier` | Add, replace, or remove response headers |
| `cookies` | `CookiesModifier` | Add, replace, or remove response cookies |

Each modifier type (`HeadersModifier`, `CookiesModifier`, `QueryParametersModifier`) supports three operations: `add`, `replace`, and `remove`.

### NottableString

The fundamental primitive throughout the model. A string value that can be negated (`!value`) or made optional (`?value`):

| Variant | Class | Purpose |
|---------|-------|---------|
| Standard | `NottableString` | Exact or regex string matching with NOT operator |
| Optional | `NottableOptionalString` | Matches if present; absence also matches |
| Schema | `NottableSchemaString` | Validates against a JSON Schema |

### Expectation

The `Expectation` class binds a `RequestDefinition` (matcher) to an `Action`, with `Times` and `TimeToLive` constraints:

```java
Expectation.when(request)      // RequestDefinition
    .thenRespond(response)     // Action (only one allowed)
    .withTimes(Times.exactly(3))
    .withTimeToLive(TimeToLive.exactly(TimeUnit.MINUTES, 5))
    .withPriority(10)
    .withId("unique-id")
    .withScenarioName("MyScenario")
    .withScenarioState("Started")
    .withNewScenarioState("Step2")
```

Scenario fields are optional. When `scenarioName` and `scenarioState` are set, the expectation only matches when the named scenario is in the required state. After matching, the scenario transitions to `newScenarioState` (if set). All scenarios start in the `"Started"` state. State is managed by `ScenarioManager` in `RequestMatchers`.

#### Sequential/Cycling Responses

An expectation can return multiple responses by setting `httpResponses` (a `List<HttpResponse>`) instead of `httpResponse`. Each match returns the next response, cycling back to the first after the last. The `responseMode` field (`ResponseMode.SEQUENTIAL` or `ResponseMode.RANDOM`) controls selection. Sequential mode uses `(matchCount - 1) % size` because `matchCount` is incremented in `consumeMatch()` before `getPrimaryAction()` is called.

#### Match Count

Each `Expectation` tracks how many times it has been matched via `matchCount` (an `AtomicInteger`). This is incremented in `consumeMatch()` and exposed via `getMatchCount()`. The match count is `@JsonIgnore` — it is runtime-only state, not serialized.

## Request Matching

### Matcher Hierarchy

```mermaid
classDiagram
    class Matcher~T~ {
        <<interface>>
        +matches(MatchDifference, T): boolean
        +isBlank(): boolean
    }
    class NotMatcher~T~ {
        <<abstract>>
        +not: boolean
    }
    class BodyMatcher~T~ {
        <<abstract>>
    }
    class AbstractHttpRequestMatcher {
        <<abstract>>
    }

    Matcher <|.. NotMatcher
    NotMatcher <|-- BodyMatcher
    NotMatcher <|-- AbstractHttpRequestMatcher

    BodyMatcher <|-- ExactStringMatcher
    BodyMatcher <|-- SubStringMatcher
    BodyMatcher <|-- RegexStringMatcher
    BodyMatcher <|-- JsonStringMatcher
    BodyMatcher <|-- JsonSchemaMatcher
    BodyMatcher <|-- JsonPathMatcher
    BodyMatcher <|-- XmlStringMatcher
    BodyMatcher <|-- XmlSchemaMatcher
    BodyMatcher <|-- XPathMatcher
    BodyMatcher <|-- BinaryMatcher
    BodyMatcher <|-- ParameterStringMatcher
    BodyMatcher <|-- GraphQLMatcher
    BodyMatcher <|-- MultiValueMapMatcher
    BodyMatcher <|-- HashMapMatcher
    BodyMatcher <|-- BooleanMatcher

    AbstractHttpRequestMatcher <|-- HttpRequestPropertiesMatcher
    AbstractHttpRequestMatcher <|-- HttpRequestsPropertiesMatcher
```

### HttpRequestPropertiesMatcher

The primary matcher decomposes an `HttpRequest` into individual property matchers using a fail-fast strategy:

```mermaid
flowchart TD
    REQ([Incoming HttpRequest]) --> M[method match?]
    M -->|fail| FAIL([No match])
    M -->|pass| P[path match?]
    P -->|fail| FAIL
    P -->|pass| PP[path parameters match?]
    PP -->|fail| FAIL
    PP -->|pass| QP[query parameters match?]
    QP -->|fail| FAIL
    QP -->|pass| H[headers match?]
    H -->|fail| FAIL
    H -->|pass| C[cookies match?]
    C -->|fail| FAIL
    C -->|pass| B[body match?]
    B -->|fail| FAIL
    B -->|pass| KA[keepAlive match?]
    KA -->|fail| FAIL
    KA -->|pass| S[secure/SSL match?]
    S -->|fail| FAIL
    S -->|pass| PR[protocol match?]
    PR -->|fail| FAIL
    PR -->|pass| MATCH([Match!])
```

Each field uses the appropriate body matcher type:

| Field | Matcher Type |
|-------|-------------|
| Method | `RegexStringMatcher` |
| Path | `RegexStringMatcher` |
| Path parameters | `MultiValueMapMatcher` |
| Query parameters | `MultiValueMapMatcher` |
| Headers | `MultiValueMapMatcher` |
| Cookies | `HashMapMatcher` |
| Body (by type) | `JsonStringMatcher`, `XmlStringMatcher`, `RegexStringMatcher`, `BinaryMatcher`, etc. |
| keepAlive, secure | `BooleanMatcher` |

### HttpRequestsPropertiesMatcher (OpenAPI)

For `OpenAPIDefinition` request definitions, this matcher parses an OpenAPI spec and creates multiple `HttpRequestPropertiesMatcher` instances (one per operation + content-type combination). A request matches if it matches any of the generated matchers.

### MatchDifference

Collects per-field match failure details for debugging. Fields: `METHOD`, `PATH`, `PATH_PARAMETERS`, `QUERY_PARAMETERS`, `COOKIES`, `HEADERS`, `BODY`, `SECURE`, `PROTOCOL`, `KEEP_ALIVE`, `OPERATION`, `OPENAPI`.

## Codec Layer

The codec package bridges Netty's HTTP objects and MockServer's domain model:

```mermaid
graph LR
    subgraph "Server-side (inbound)"
        NR[Netty FullHttpRequest] -->|decode| MR[MockServer HttpRequest]
        MResp[MockServer HttpResponse] -->|encode| NResp[Netty FullHttpResponse]
    end

    subgraph "Client-side (forwarding)"
        MR2[MockServer HttpRequest] -->|encode| NR2[Netty FullHttpRequest]
        NResp2[Netty FullHttpResponse] -->|decode| MResp2[MockServer HttpResponse]
    end
```

| Codec | Direction | Conversion |
|-------|-----------|------------|
| `MockServerHttpServerCodec` | Server pipeline | Combines request decoder + response encoder |
| `MockServerHttpClientCodec` | Client pipeline | Combines response decoder + request encoder |
| `MockServerBinaryClientCodec` | Binary proxy | Binary message encode/decode |
| `BodyDecoderEncoder` | Both | Body ↔ ByteBuf conversion |
| `ExpandedParameterDecoder` | Inbound | Query/form parameter parsing (OpenAPI styles) |
| `PathParametersDecoder` | Inbound | URL path parameter extraction |

### OpenAPI Parameter Styles

`ExpandedParameterDecoder` handles 13 OpenAPI parameter serialization styles: `SIMPLE`, `SIMPLE_EXPLODED`, `LABEL`, `LABEL_EXPLODED`, `MATRIX`, `MATRIX_EXPLODED`, `FORM`, `FORM_EXPLODED`, `SPACE_DELIMITED`, `SPACE_DELIMITED_EXPLODED`, `PIPE_DELIMITED`, `PIPE_DELIMITED_EXPLODED`, `DEEP_OBJECT`.

## Serialization

### Architecture

Three serialization layers:

1. **Top-level serializers**: Public API for JSON (de)serialization (`ExpectationSerializer`, `HttpRequestSerializer`, etc.)
2. **DTO layer** (`serialization/model/`): Data Transfer Objects mirroring domain objects, each with a `buildObject()` method
3. **Custom Jackson modules** (`serialization/serializers/`, `serialization/deserializers/`): Type-specific JSON handling

### ObjectMapperFactory

Central registry configuring Jackson `ObjectMapper` with all custom serializers, deserializers, and modules. Used by all serialization operations.

### Java Code Serializers

`serialization/java/` package generates Java client API code from domain objects (e.g., `ExpectationToJavaSerializer` produces Java code that recreates an expectation programmatically).

## OpenAPI

### Processing Pipeline

```mermaid
flowchart LR
    SPEC["OpenAPI Spec
URL, file, or inline"] --> PARSER["OpenAPIParser
Swagger Parser + LRU cache"]
    PARSER --> CONV["OpenAPIConverter
Spec → Expectations"]
    CONV --> EXP[Expectation[]]
    
    CONV --> EB["ExampleBuilder
Schema → example values"]
    EB --> RESP[Example HttpResponse]
```

`OpenAPIConverter` creates one `Expectation` per operation, with an `OpenAPIDefinition` matcher and an example `HttpResponse` built from the spec's response schemas, headers, and examples.

## Configuration

Two complementary configuration mechanisms:

| Class | Scope | Source |
|-------|-------|--------|
| `Configuration` | Instance (runtime POJO) | Programmatic, ~1900 lines |
| `ConfigurationProperties` | Static (system properties) | `mockserver.properties` or `mockserver.json` file + JVM system properties, ~1900 lines |
| `ClientConfiguration` | Client subset | Timeout, TLS, JWT settings |
| `ConfigurationDTO` | Serialization DTO | JSON API and JSON config file format, ~1100 lines |
| `ConfigurationSerializer` | JSON codec | Serialize/deserialize `Configuration` via `ConfigurationDTO` |

Configuration properties cover: logging, memory usage, scalability, socket settings, HTTP parsing, CORS, template restrictions, initialization/persistence, verification, proxy settings, TLS (forward, control plane), ring buffer sizing, MCP.

### ConfigurationDTO

`ConfigurationDTO` (`serialization/model/ConfigurationDTO.java`) is a Jackson-annotated DTO covering all ~85 configuration properties. It serves dual purpose:
- **API response/request**: JSON schema for `GET/PUT /mockserver/configuration` endpoints
- **JSON config file format**: The JSON produced by serializing a `Configuration` can be saved as a `mockserver.json` config file and loaded at startup

Key methods:
- `ConfigurationDTO(Configuration)`: Constructs DTO from live configuration (reads all getters including fallback defaults)
- `buildObject()`: Creates a new `Configuration` from DTO values
- `applyTo(Configuration target)`: Merges only non-null DTO fields into an existing `Configuration` (used by `PUT /mockserver/configuration`)

### JSON Configuration File

`ConfigurationProperties.readPropertyFile()` detects `.json` file extension and parses it using Jackson. JSON property names use camelCase without the `mockserver.` prefix (e.g., `logLevel` not `mockserver.logLevel`). The parsed values are converted to a `Properties` object with `mockserver.` prefixed keys, occupying the same precedence slot as `.properties` files.

### Configuration API

Runtime configuration is exposed via REST endpoints in `HttpRequestHandler`:
- `GET /mockserver/configuration`: Returns current configuration as JSON
- `PUT /mockserver/configuration`: Updates configuration at runtime (only non-null fields are applied)

Client methods: `MockServerClient.retrieveConfiguration()`, `MockServerClient.updateConfiguration(String)`

### Metrics Retrieval

Metrics can be retrieved via the existing `PUT /mockserver/retrieve` endpoint with `type=METRICS`, returning a JSON map of metric names to counts. Client method: `MockServerClient.retrieveMetrics()`.

### MCP Configuration

The Model Context Protocol endpoint is controlled by a single property:

| Property | Type | Default | Source |
|----------|------|---------|--------|
| `mcpEnabled` | `boolean` | `true` | `Configuration` / `ConfigurationProperties` / system property `mockserver.mcpEnabled` |

When `mcpEnabled` is `true` (the default), MockServer registers the `McpStreamableHttpHandler` in the Netty pipeline to serve MCP requests at `/mockserver/mcp`. When `false`, no MCP handler is registered and requests to that path are handled normally by `HttpRequestHandler`.

### gRPC Configuration

| Property | Type | Default | Source |
|----------|------|---------|--------|
| `grpcEnabled` | `boolean` | `true` | `Configuration` / `ConfigurationProperties` / system property `mockserver.grpcEnabled` |
| `grpcDescriptorDirectory` | `String` | `null` | Directory of pre-compiled `.dsc`/`.desc` proto descriptor files |
| `grpcProtoDirectory` | `String` | `null` | Directory of `.proto` files to compile at startup |
| `grpcProtocPath` | `String` | `"protoc"` | Path to the `protoc` compiler binary |

When `grpcEnabled` is `true` (the default) and descriptors are loaded (via directory config or runtime API upload), MockServer inserts `GrpcToHttpRequestHandler` and `GrpcToHttpResponseHandler` into the HTTP/2 pipeline to intercept and convert gRPC requests. The `GrpcProtoDescriptorStore` is initialized in `HttpState` and provides method descriptors for protobuf↔JSON conversion.
