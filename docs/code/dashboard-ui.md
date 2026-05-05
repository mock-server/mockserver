# Dashboard UI

## Architecture Overview

The MockServer dashboard is a React single-page application (SPA) that receives real-time updates via WebSocket. The frontend is built with Vite and served as static resources from the Java classpath. During the Maven build, the `build-ui` profile in `mockserver-netty` uses `frontend-maven-plugin` to install Node, run `npm ci` and `npm run build`, then copies the output to the classpath.

```mermaid
graph TB
    subgraph "Browser"
        REACT["React 18 SPA
Zustand store"]
        WS_C[WebSocket Client]
    end

    subgraph "MockServer (Netty)"
        DH["DashboardHandler
Static file serving"]
        DWSH["DashboardWebSocketHandler
Real-time data push"]
        EL["MockServerEventLog
Disruptor ring buffer"]
        RM["RequestMatchers
Active expectations"]
    end

    REACT -->|GET /mockserver/dashboard/*| DH
    DH -->|index.html, JS| REACT

    REACT -->|"WebSocket upgrade
/_mockserver_ui_websocket"| DWSH
    WS_C <-->|JSON messages| DWSH

    EL -->|MockServerLogListener| DWSH
    RM -->|MockServerMatcherListener| DWSH
```

## Request Flow

### 1. Initial Page Load

```mermaid
sequenceDiagram
    participant B as Browser
    participant HRH as HttpRequestHandler
    participant DH as DashboardHandler

    B->>HRH: GET /mockserver/dashboard
    HRH->>DH: renderDashboard(ctx, request)
    DH->>DH: Load /org/mockserver/dashboard/index.html from classpath
    DH-->>B: index.html
    B->>HRH: GET /mockserver/dashboard/assets/index-*.js
    DH-->>B: JavaScript bundle
```

### 2. WebSocket Connection

```mermaid
sequenceDiagram
    participant B as Browser (React/Zustand)
    participant PU as PortUnificationHandler
    participant DWSH as DashboardWebSocketHandler
    participant EL as MockServerEventLog
    participant RM as RequestMatchers

    B->>PU: GET /_mockserver_ui_websocket (Upgrade: websocket)
    PU->>DWSH: channelRead() detects upgrade URI

    DWSH->>DWSH: upgradeChannel()
    Note over DWSH: 1. WebSocket handshake 2. Register in clientRegistry 3. Register as log listener 4. Register as matcher listener 5. Start throttle scheduler (1/sec)

    DWSH-->>B: WebSocket handshake OK

    Note over DWSH: After 100ms delay...
    DWSH->>DWSH: sendUpdate()
    DWSH->>EL: retrieveLogEntriesInReverseForUI()
    DWSH->>RM: retrieveRequestMatchers()
    DWSH-->>B: JSON {logMessages, activeExpectations, recordedRequests, proxiedRequests}

    Note over B,DWSH: Ongoing: listener callbacks trigger sendUpdate()

    B->>DWSH: TextFrame: {"method": "GET", "path": "/api/.*"}
    Note over DWSH: Client sends filter as HttpRequest JSON
    DWSH->>DWSH: Store filter, trigger sendUpdate()
    DWSH-->>B: Filtered JSON data
```

### 3. Real-Time Updates

The `DashboardWebSocketHandler` implements both `MockServerLogListener` and `MockServerMatcherListener`. When either fires, `sendUpdate()` assembles and pushes the current state to all connected clients.

**Throttling**: A `Semaphore(1)` with a scheduled release every 1 second limits updates to at most one per second per client, preventing UI flooding during high-traffic scenarios.

## Frontend Application

### Technology Stack

| Component | Technology |
|-----------|-----------|
| Framework | React 18 |
| State management | Zustand |
| Build tool | Vite |
| UI library | MUI v6 |
| Language | TypeScript |
| Testing | Vitest + React Testing Library |

### Zustand Store

```typescript
{
  logMessages: [],           // Log entries (grouped by correlationId)
  activeExpectations: [],    // Currently active expectations
  recordedRequests: [],      // All received requests
  proxiedRequests: [],       // Forwarded request+response pairs
  connectionStatus: 'disconnected',
  error: null,
  filterEnabled: false,
  filterExpanded: false,
  autoScroll: true,
  logSearch: '',
  expectationSearch: '',
  receivedSearch: '',
  proxiedSearch: '',
}
```

### WebSocket Hook

The `useWebSocket` hook manages the WebSocket lifecycle:

```typescript
const url = `${protocol}://${host}:${port}/_mockserver_ui_websocket`;
const ws = new WebSocket(url);
```

- `onopen`: Sends the current filter (serialized `HttpRequest`), resets reconnect counter
- `onmessage`: Parses JSON, calls `applyMessage()` to update all four entity arrays
- `onclose`: Triggers reconnection with exponential backoff (max 10 retries)
- `onerror`: Sets error status in store

### UI Components

The dashboard displays four data panels in a 2x2 grid:

| Panel | Component | Data Source | Content |
|-------|-----------|------------|---------|
| Log Messages | `LogPanel` | `logMessages` | Grouped log entries with color-coded types |
| Active Expectations | `ExpectationPanel` | `activeExpectations` | Currently registered expectations with matchers and actions |
| Received Requests | `RequestPanel` | `recordedRequests` | All received HTTP requests |
| Proxied Requests | `RequestPanel` | `proxiedRequests` | Forwarded requests with their responses |

Additional components: `AppBar` (connection status, theme toggle, clear/reset), `FilterPanel` (request filter with debounce), `Panel` (shared panel wrapper with search, badge count, auto-scroll).

### Filtering

Users can filter all panels by sending an `HttpRequest` JSON object as a text WebSocket frame. The server stores the filter per client and uses it when assembling data:

- **Active expectations**: Filtered by `requestMatchers.retrieveRequestMatchers(filter)`
- **Log entries**: Filtered by matching `LogEntry.httpRequests` against the filter
- **Recorded requests**: Filtered by type `RECEIVED_REQUEST` + request match
- **Proxied requests**: Filtered by type `FORWARDED_REQUEST` + request match

## Server-Side Data Assembly

### sendUpdate() Method

For each connected client, assembles four data categories (limited to 100 items each):

```mermaid
flowchart TD
    SU[sendUpdate] --> AE["Active Expectations
From RequestMatchers"]
    SU --> LM["Log Messages
From EventLog, reverse order,
grouped by correlationId"]
    SU --> RR["Recorded Requests
RECEIVED_REQUEST entries"]
    SU --> PR["Proxied Requests
FORWARDED_REQUEST entries
with request + response"]

    AE --> JSON["Serialize to JSON
Custom dashboard ObjectMapper"]
    LM --> JSON
    RR --> JSON
    PR --> JSON

    JSON --> SEND["Send via TextWebSocketFrame
Throttled: max 1/sec"]
```

### Dashboard Model Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `DashboardLogEntryDTO` | `o.m.dashboard.model` | Simplified log entry for UI display with description, style, and HTTP request/response data |
| `DashboardLogEntryDTOGroup` | `o.m.dashboard.model` | Groups related log entries by correlation ID (e.g., a request and its matching response) |
| `Description` | `o.m.dashboard.serializers` | Truncated request description (method + path) for UI column display |

### Custom Serializers

The dashboard uses specialized Jackson serializers for UI-friendly output:

| Serializer | Purpose |
|------------|---------|
| `DashboardLogEntryDTOSerializer` | Color-coded log entries with message parts |
| `DashboardLogEntryDTOGroupSerializer` | Groups related entries by correlation ID |
| `DescriptionSerializer` | Truncated request/log descriptions |
| `ThrowableSerializer` | Exception stack traces as string arrays |

### Log Entry Color Coding

| Log Type | Color | RGB |
|----------|-------|-----|
| RECEIVED_REQUEST | Blue | `rgb(114,160,193)` |
| EXPECTATION_RESPONSE | Light blue | `rgb(161,208,231)` |
| EXPECTATION_MATCHED | Teal | `rgb(117,185,186)` |
| EXPECTATION_NOT_MATCHED | Muted pink | `rgb(204,165,163)` |
| FORWARDED_REQUEST | Sky blue | `rgb(152,208,255)` |
| VERIFICATION | Purple | `rgb(178,148,187)` |
| VERIFICATION_FAILED | Red | `rgb(234,67,106)` |
| WARN | Coral | `rgb(245,95,105)` |
| ERROR | Dark pink | `rgb(179,97,122)` |
| EXCEPTION | Bright red | `rgb(211,33,45)` |
| INFO | Green | `rgb(59,122,87)` |
| TEMPLATE_GENERATED | Gold | `rgb(241,186,27)` |
| CREATED_EXPECTATION | (default) | Uses log level colour |
| UPDATED_EXPECTATION | (default) | Uses log level colour |
| REMOVED_EXPECTATION | (default) | Uses log level colour |
| CLEARED | (default) | Uses log level colour |
| RETRIEVED | (default) | Uses log level colour |
| VERIFICATION_PASSED | (default) | Uses log level colour |
| NO_MATCH_RESPONSE | (default) | Uses log level colour |
| SERVER_CONFIGURATION | (default) | Uses log level colour |
| AUTHENTICATION_FAILED | (default) | Uses log level colour |
| DEBUG | (default) | Uses log level colour |
| TRACE | (default) | Uses log level colour |
| RUNNABLE | (hidden) | Internal — not displayed in UI |

### JSON Message Structure

```json
{
  "logMessages": [
    {
      "key": "<id>_log",
      "value": {
        "description": "2024-01-15 10:30:45.123 RECEIVED_REQUEST",
        "style": { "paddingTop": "4px", "color": "rgb(114,160,193)" },
        "messageParts": [
          { "key": "<id>_0msg", "value": "received request:" },
          { "key": "<id>_0arg", "json": true, "argument": true, "value": { "method": "GET", "path": "/api/users" } }
        ]
      }
    }
  ],
  "activeExpectations": [ ... ],
  "recordedRequests": [ ... ],
  "proxiedRequests": [ ... ]
}
```

## Dashboard vs Callback WebSockets

MockServer has two distinct WebSocket systems:

| Feature | Dashboard WebSocket | Callback WebSocket |
|---------|--------------------|--------------------|
| URI | `/_mockserver_ui_websocket` | `/_mockserver_callback_websocket` |
| Handler | `DashboardWebSocketHandler` | `CallbackWebSocketServerHandler` |
| Purpose | Real-time UI data push | Closure callback execution |
| Direction | Server → Client (push) | Bidirectional (request/response) |
| Client | Browser (React SPA) | Java `WebSocketClient` |
| Pipeline impact | Keeps all handlers | Removes downstream handlers |
| Max clients | 100 (CircularHashMap) | Bounded by configuration |

## Build Integration

The UI is built from source during the Maven build via the `build-ui` profile in `mockserver-netty/pom.xml`:

| Step | Plugin | Phase | Action |
|------|--------|-------|--------|
| Install Node | `frontend-maven-plugin` | `generate-resources` | Downloads Node v22.14.0 |
| Install dependencies | `frontend-maven-plugin` | `generate-resources` | Runs `npm ci` |
| Build UI | `frontend-maven-plugin` | `generate-resources` | Runs `npm run build` (tsc + vite) |
| Copy to classpath | `maven-resources-plugin` | `process-resources` | Copies `mockserver-ui/build/` to `target/classes/org/mockserver/dashboard/` |

The profile auto-activates when `../../mockserver-ui/package.json` exists. To skip the UI build: `./mvnw ... -P!build-ui`.

### Static Resources

All frontend files are bundled in the JAR at `/org/mockserver/dashboard/`:

| File | Type | Purpose |
|------|------|---------|
| `index.html` | HTML | SPA entry point |
| `assets/index-*.js` | JS | Application bundle (React, MUI, Zustand, all components) |
| `apple-touch-icon.png` | PNG | Touch icon |
| `favicon.ico` | ICO | Browser favicon |

## Opening the Dashboard

From client code:

```java
MockServerClient client = new MockServerClient("localhost", 1080);
client.openUI();  // Opens http://localhost:1080/mockserver/dashboard in browser
```

Or directly in a browser: `http://localhost:1080/mockserver/dashboard`
