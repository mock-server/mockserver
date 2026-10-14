# Directory Listing Copy — AlternativeTo / StackShare / Slant

## TL;DR

Three listings that require a human account to create. All are UI-only (no API). The copy below is
factually grounded in the repo — do not expand it with features that are not confirmed shipped.

---

## AlternativeTo

**URL to create:** `https://alternativeto.net` → Add Software

**Category:** Development Tools → Mocking / Testing

**Name:** MockServer

**Tagline (≤120 chars):**
```
HTTP(S), gRPC and LLM mock server and proxy — test, record, verify and inject chaos
```

**Description (≤2000 chars):**
```
MockServer is an open-source HTTP(S) mock server and proxy for testing and chaos engineering.

Mock any system your application depends on — HTTP/1.1, HTTPS, HTTP/2, HTTP/3, gRPC, WebSockets,
raw TCP, Kafka, MQTT, and AI/LLM chat-completion APIs (OpenAI, Anthropic, Gemini, Azure OpenAI,
Bedrock, Ollama). Match requests on method, path, query, headers, cookies and body (JSON, XML,
JSONPath, XPath, regex, OpenAPI spec).

Key features:
- Mock & proxy — port forwarding, web proxy, HTTPS tunnelling (CONNECT), SOCKS
- Response templating — Velocity, Mustache, JavaScript, webhooks, class callbacks
- OpenAPI — generate expectations directly from an OpenAPI/Swagger spec
- Verification — assert which requests were received, in what order, how many times
- Chaos testing — inject latency, dropped connections, and errors to test resilience
- LLM/AI mocking — mock streaming and non-streaming chat-completion endpoints
- Live dashboard — watch requests, expectations and logs in real time
- Clients — Java, JavaScript/Node, Python, Ruby, Go, .NET, Rust

Runs as a Docker image, Helm chart, executable JAR, WAR, or embedded in tests via JUnit 4/5
extensions, Spring TestExecutionListener, or Testcontainers. Licensed under Apache 2.0.
```

**Website:** `https://www.mock-server.com`

**Source:** `https://github.com/mock-server/mockserver-monorepo`

**License:** Apache 2.0 (open source)

**Platform:** Cross-platform (Docker, macOS via Homebrew, Linux, Windows via JAR)

**Alternatives to list (makes MockServer discoverable when users search these):**
- WireMock
- Mockoon
- Postman Mock Server
- Hoverfly
- Nock (Node.js)
- MockWebServer (OkHttp)

---

## StackShare

**URL to create:** `https://stackshare.io` → Add a Tool

**Category:** Application Utilities → Testing

**Name:** MockServer

**Tagline (≤90 chars):**
```
Mock & proxy HTTP, gRPC, WebSocket and LLM APIs for integration and chaos testing
```

**Description:**
```
MockServer is an open-source mock server and HTTP proxy for testing. It mocks HTTP/1.1, HTTPS,
HTTP/2, HTTP/3, gRPC, WebSockets, Kafka, MQTT, and AI/LLM chat-completion APIs (OpenAI, Anthropic,
Gemini). Verify requests, record and replay traffic, and inject chaos — latency, dropped connections,
errors — to test how your application handles dependency failures.

Clients: Java, JavaScript/Node, Python, Ruby, Go, .NET, Rust. Runs as Docker, Helm, JAR, or WAR.
```

**Website:** `https://www.mock-server.com`

**GitHub:** `https://github.com/mock-server/mockserver-monorepo`

**Documentation:** `https://www.mock-server.com`

**Pricing:** Free / Open Source (Apache 2.0)

**Features to tick (where StackShare offers checkboxes):**
- REST API mocking
- gRPC mocking
- Request verification
- Traffic recording
- Docker support
- Kubernetes/Helm support
- Multi-language clients

---

## Slant

**URL:** `https://www.slant.co`

**Target question to answer / add MockServer to:**
- "What are the best service mocking tools?" (search for this question or the closest equivalent)
- "What are the best tools for API mocking?"
- "What are the best WireMock alternatives?"

**Pros to add (one at a time — Slant uses a pro/con format):**

```
Supports HTTP, HTTP/2, HTTP/3, gRPC, WebSockets, Kafka, MQTT, and LLM/AI chat-completion APIs in a single server
```

```
First-class chaos testing — inject latency, dropped connections, and error responses to test resilience
```

```
Multi-language clients: Java, JavaScript, Python, Ruby, Go, .NET, Rust
```

```
Runs as Docker, Helm/Kubernetes, executable JAR, or embedded in JUnit 4/5 / Spring tests via Testcontainers
```

```
OpenAPI / Swagger spec import generates expectations automatically
```

**Cons to add (honest — Slant reviewers reject biased entries):**

```
Java-based server; requires a JVM or Docker — no single native binary for all platforms without Docker
```

```
Control-plane API is REST (JSON); no native SDK for every language (community clients exist for some)
```

**Description / "What is it?" field:**
```
MockServer is an open-source HTTP(S) mock server and proxy. Mock any API your application depends
on to develop and test against systems that are unavailable or hard to reproduce. Proxy real
traffic to record, inspect and modify requests in flight. Inject chaos — latency, errors, dropped
connections — to test resilience. Licensed Apache 2.0. Homepage: https://www.mock-server.com
```

---

## Notes for all listings

- All three are account-gated and UI-only. A human must create or log in to an account.
- AlternativeTo and StackShare display the listing immediately after creation (no review queue).
- Slant entries go through a community review before appearing prominently.
- Keep descriptions factual — do not list features that are not confirmed in the current release.
  The copy above reflects what is shipped in MockServer 8.0.0 (June 2026). <!-- version-ref:keep -->
- Link back to `https://www.mock-server.com` as the canonical homepage in all listings.
