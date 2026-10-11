# MockServer Python Examples

Runnable examples demonstrating the MockServer Python client.

## Prerequisites

- **Python 3.9+**
- **MockServer running** on `localhost:1080` (default for all examples).
  Start one quickly with Docker:
  ```bash
  docker run -d --rm -p 1080:1080 mockserver/mockserver
  ```
- **Install the client** -- either from PyPI:
  ```bash
  pip install mockserver-client
  ```
  or from the local source tree:
  ```bash
  pip install -e ../../mockserver-client-python
  ```

## Examples

| Folder | Description |
|--------|-------------|
| [response_action](response_action/) | Return canned HTTP responses (status codes, headers, cookies, JSON bodies, delays). |
| [scenario](scenario/) | Stateful, sequential, timed, and cross-protocol mocking with the typed scenario API. |
| [load_scenario](load_scenario/) | Register, start, list, and stop a server-side load scenario (RATE ramp → VU hold → PAUSE) via the Load Scenario registry. |
| [forward_action](forward_action/) | Forward requests to another host, with optional request/response overrides. |
| [request_matchers](request_matchers/) | Match requests by path, query parameters, headers, cookies, and body content. |
| [openapi_expectations](openapi_expectations/) | Generate expectations automatically from an OpenAPI specification. |
| [verify_requests](verify_requests/) | Verify that MockServer received specific requests a certain number of times. |
| [retrieve_recorded_requests](retrieve_recorded_requests/) | Retrieve requests that MockServer recorded while proxying. |
| [reverse_proxy](reverse_proxy/) | Use MockServer as a reverse proxy, forwarding traffic to different backends. |
| [breakpoint_modify_proxied](breakpoint_modify_proxied/) | Modify a proxied exchange with an interactive breakpoint (RESPONSE phase). |
| [llm_mock](llm_mock/) | Mock an OpenAI-style chat completion with the LLM mock builder. |
| [mcp_mock](mcp_mock/) | Mock an MCP server tool with the MCP mock builder. |
| [parallel_search_mcp](parallel_search_mcp/) | Parallel Search MCP client with Streamable HTTP, MockServer proxy capture, and deterministic offline replay. |

Each folder contains a runnable Python script and its own `README.md` with
instructions.

