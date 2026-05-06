# MockServer Python Client

Python client for [MockServer](https://www.mock-server.com) with full WebSocket callback support.

## Features

- **Full REST API**: Create expectations, verify requests, clear/reset, retrieve recorded data
- **Response Callbacks**: Register Python functions that dynamically generate responses via WebSocket
- **Forward Callbacks**: Modify requests before they are forwarded to the real server
- **Forward+Response Callbacks**: Modify both the forwarded request and the response
- **Fluent API**: `client.when(request).respond(callback)` — mirrors the Java client
- **Async + Sync**: Native asyncio API with a synchronous wrapper for non-async code
- **Minimal Dependencies**: Only `websockets` (for callback support)

## Installation

```bash
pip install mockserver-client
```

## Quick Start

### Synchronous API

```python
from mockserver import MockServerClient, HttpRequest, HttpResponse

client = MockServerClient("localhost", 1080)

# Static expectation
client.when(
    HttpRequest.request("/api/users").with_method("GET")
).respond(
    HttpResponse.response('{"users": []}', status_code=200)
)

# Verify
client.verify(
    HttpRequest.request("/api/users").with_method("GET"),
    VerificationTimes.at_least(1)
)

# Clean up
client.reset()
client.close()
```

### Context Manager

```python
with MockServerClient("localhost", 1080) as client:
    client.when(
        HttpRequest.request("/hello")
    ).respond(
        HttpResponse.response("world")
    )
```

### Async API

```python
import asyncio
from mockserver import AsyncMockServerClient, HttpRequest, HttpResponse

async def main():
    async with AsyncMockServerClient("localhost", 1080) as client:
        await client.when(
            HttpRequest.request("/api/data")
        ).respond(
            HttpResponse.response('{"key": "value"}')
        )

asyncio.run(main())
```

## Response Callbacks

Register a Python function that generates responses dynamically when matching requests arrive:

```python
from mockserver import MockServerClient, HttpRequest, HttpResponse

def handle_request(request):
    if request.method == "POST":
        return HttpResponse.response("created", status_code=201)
    return HttpResponse.not_found_response()

client = MockServerClient("localhost", 1080)
client.mock_with_callback(
    HttpRequest.request("/api/callback"),
    handle_request
)
```

Or with the fluent API:

```python
client.when(
    HttpRequest.request("/api/callback")
).respond(handle_request)
```

## Forward Callbacks

Modify requests before they are forwarded to the real server:

```python
def modify_request(request):
    return request.with_header("X-Forwarded", "true").with_path("/modified" + request.path)

client.mock_with_forward_callback(
    HttpRequest.request("/proxy/.*"),
    modify_request
)
```

## Forward+Response Callbacks

Modify both the forwarded request and the response:

```python
def modify_request(request):
    return request.with_header("X-Proxied", "true")

def modify_response(request, response):
    return response.with_header("X-Modified", "true")

client.mock_with_forward_callback(
    HttpRequest.request("/proxy/.*"),
    modify_request,
    modify_response
)
```

## Verification

```python
from mockserver import VerificationTimes

# Verify a request was received at least once
client.verify(
    HttpRequest.request("/api/users").with_method("GET"),
    VerificationTimes.at_least(1)
)

# Verify exact count
client.verify(
    HttpRequest.request("/api/users"),
    VerificationTimes.exactly(3)
)

# Verify request sequence (order matters)
client.verify_sequence(
    HttpRequest.request("/first"),
    HttpRequest.request("/second"),
    HttpRequest.request("/third"),
)

# Verify no interactions
client.verify_zero_interactions()
```

## Retrieval

```python
# Get recorded requests
requests = client.retrieve_recorded_requests(
    HttpRequest.request("/api/.*")
)

# Get active expectations
expectations = client.retrieve_active_expectations()

# Get log messages
logs = client.retrieve_log_messages()
```

## Control

```python
# Clear specific expectations
client.clear(HttpRequest.request("/api/users"))

# Clear by type
client.clear(HttpRequest.request("/api/users"), clear_type="LOG")

# Reset everything
client.reset()

# Bind additional ports
client.bind(1081, 1082)

# Check if running
if client.has_started():
    print("MockServer is running")

# Stop
client.stop()
```

## TLS Support

```python
# Uses system trust store (default — verifies certificates)
client = MockServerClient("localhost", 1080, secure=True)

# Custom CA certificate
client = MockServerClient(
    "localhost", 1080,
    secure=True,
    ca_cert_path="/path/to/ca.pem"
)

# Disable certificate verification (testing only — NOT recommended for production)
client = MockServerClient(
    "localhost", 1080,
    secure=True,
    tls_verify=False
)
```

## Domain Model

All domain model classes support builder-style chaining:

```python
request = (
    HttpRequest.request("/api/users")
    .with_method("POST")
    .with_header("Content-Type", "application/json")
    .with_header("Authorization", "Bearer token")
    .with_body('{"name": "test"}')
    .with_query_param("page", "1")
    .with_secure(True)
)

response = (
    HttpResponse.response()
    .with_status_code(201)
    .with_header("Location", "/api/users/1")
    .with_body('{"id": 1, "name": "test"}')
    .with_delay(Delay(time_unit="SECONDS", value=1))
)
```

## Requirements

- Python 3.9+
- `websockets` >= 12.0 (for callback support)

## License

Apache 2.0

## AI Assistant Integration

MockServer includes a built-in [MCP](https://modelcontextprotocol.io) (Model Context Protocol) server that enables AI coding assistants to create expectations, verify requests, and debug HTTP traffic programmatically.

- **MCP Endpoint:** `http://localhost:1080/mockserver/mcp`
- **AI Documentation:** [llms.txt](https://www.mock-server.com/llms.txt)
- **Setup Guide:** [AI Integration](https://www.mock-server.com/mock_server/ai_mcp_setup.html)
