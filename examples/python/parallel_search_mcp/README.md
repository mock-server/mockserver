# Parallel Search MCP Example

## What it demonstrates

This example demonstrates the generic remote-MCP (Model Context Protocol) capture and replay pattern using MockServer:
- **Generic Remote-MCP Capture/Replay**: Proxying live JSON-RPC 2.0 traffic over the Streamable HTTP transport from a remote MCP server, recording requests and responses into a clean fixture, and replaying them deterministically in offline environments.
- **Concrete Upstream Service**: Connects to the live [Parallel Search MCP](https://search.parallel.ai/mcp) service, discovering the upstream `web_search` and `web_fetch` tools.
- **Dynamic Request ID Handling**: Using MockServer's Velocity template engine (`$!{request.jsonRpcRawId}`) and request matchers (`JsonRpcBody`, `JsonPathBody`) so replayed expectations match any incoming client request ID dynamically.
- **What a Successful Replay Proves**: Complete isolation from external network dependencies, deterministic execution in CI/CD without rate limits or service outages, and verification that no API keys or sensitive credentials leak into test fixtures.

## Prerequisites

- Python 3.9+
- `pip install mockserver-client` (or `pip install -e ../../mockserver-client-python`)
- MockServer running on `localhost:1080` (default for all examples):
  ```bash
  docker run --rm -p 1080:1080 mockserver/mockserver
  ```
- *(Optional)* An upstream API key (`export PARALLEL_API_KEY="sk-..."`) is only needed if capturing live interactions against an authenticated or rate-limited upstream endpoint. Offline replay does not require an API key or upstream connectivity.

## Run

```bash
# Offline replay via MockServer (default):
python parallel_search_mcp.py

# Or explicitly specify replay mode:
python parallel_search_mcp.py --mode replay

# Live capture through MockServer forward proxy into fixture:
python parallel_search_mcp.py --mode capture

# Direct live interaction against Parallel Search without MockServer:
python parallel_search_mcp.py --mode live
```

## Expected output

```
[Replay] Loading fixture from .../fixtures/parallel_search_fixtures.json...
[Replay] Connecting to MockServer on localhost:1080...
[Replay] Registered 6 MockServer expectations.

[Replay] Handshake with http://localhost:1080/mcp...
  Connected: 'Parallel Web Search MCP Server' v1.27.0
[Replay] Discovering tools...
  Available tools: ['web_search', 'web_fetch']

[Replay] Executing 'web_search' (JSON-RPC ID: 101)...
  Returned 10 results:
    1. GitHub - mock-server/mockserver
       URL: https://github.com/mock-server/mockserver
       Snippet: MockServer enables easy mocking of any system you integrate with via HTTP or HTTPS...
    2. GitHub - mock-server/mockserver-monorepo
       URL: https://github.com/mock-server/mockserver-monorepo
       Snippet: MockServer is an HTTP(S) mock server and proxy for testing...
    3. MockServer Documentation
       URL: https://www.mock-server.com
       Snippet: MockServer enables easy mocking of any system you integrate with via HTTP or HTTPS...

[Replay] Executing 'web_fetch' for https://github.com/mock-server/mockserver (JSON-RPC ID: 102)...
  Page fetch returned 1 extract result(s):
    URL: https://github.com/mock-server/mockserver
    Title: GitHub - mock-server/mockserver
    Excerpts count: 1

[Replay] Success! Deterministic offline replay completed without upstream network calls or credentials.
```
