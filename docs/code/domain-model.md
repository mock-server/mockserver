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
    Action <|-- HttpError
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

    BodyWithContentType <|-- StringBody
    BodyWithContentType <|-- JsonBody
    BodyWithContentType <|-- XmlBody
    BodyWithContentType <|-- BinaryBody
```

Body `Type` enum: `BINARY`, `JSON`, `JSON_SCHEMA`, `JSON_PATH`, `PARAMETERS`, `REGEX`, `STRING`, `XML`, `XML_SCHEMA`, `XPATH`

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
```

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

`ExpandedParameterDecoder` handles 14 OpenAPI parameter serialization styles: `SIMPLE`, `SIMPLE_EXPLODED`, `LABEL`, `LABEL_EXPLODED`, `MATRIX`, `MATRIX_EXPLODED`, `FORM`, `FORM_EXPLODED`, `SPACE_DELIMITED`, `SPACE_DELIMITED_EXPLODED`, `PIPE_DELIMITED`, `PIPE_DELIMITED_EXPLODED`, `DEEP_OBJECT`.

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
    SPEC[OpenAPI Spec<br/><i>URL, file, or inline</i>] --> PARSER[OpenAPIParser<br/><i>Swagger Parser + LRU cache</i>]
    PARSER --> CONV[OpenAPIConverter<br/><i>Spec → Expectations</i>]
    CONV --> EXP[Expectation[]]
    
    CONV --> EB[ExampleBuilder<br/><i>Schema → example values</i>]
    EB --> RESP[Example HttpResponse]
```

`OpenAPIConverter` creates one `Expectation` per operation, with an `OpenAPIDefinition` matcher and an example `HttpResponse` built from the spec's response schemas, headers, and examples.

## Configuration

Two complementary configuration mechanisms:

| Class | Scope | Source |
|-------|-------|--------|
| `Configuration` | Instance (runtime POJO) | Programmatic, ~1900 lines |
| `ConfigurationProperties` | Static (system properties) | `mockserver.properties` file + JVM system properties, ~1850 lines |
| `ClientConfiguration` | Client subset | Timeout, TLS, JWT settings |

Configuration properties cover: logging, memory usage, scalability, socket settings, HTTP parsing, CORS, template restrictions, initialization/persistence, verification, proxy settings, TLS (forward, control plane), ring buffer sizing.
