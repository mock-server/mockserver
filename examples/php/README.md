# MockServer PHP Examples

Runnable examples demonstrating the [MockServer PHP client](../../mockserver-client-php/).

## Prerequisites

- **PHP 8.1+** with Composer
- **MockServer running** on `localhost:1080` (e.g. `docker run -d -p 1080:1080 mockserver/mockserver`)
- **Install the client** -- from the local source tree:

  ```bash
  cd ../../mockserver-client-php && composer install
  ```

  or via Packagist:

  ```bash
  composer require mock-server/mockserver-client
  ```

## Examples

| Folder | Description |
|--------|-------------|
| [create_expectation](create_expectation/) | Create an expectation, send a test request, and verify it was received. |
| [forward_override](forward_override/) | Use `httpOverrideForwardedRequest` to statically modify proxied requests (PHP has no WebSocket breakpoints). |
| [scenario](scenario/) | Stateful scenarios: state-machine login flow, sequential response cycling, timed and external transitions, and cross-protocol triggers. |
| [load_scenario](load_scenario/) | Register, start, list, and stop a server-side load scenario (RATE ramp → VU hold → PAUSE) via the Load Scenario registry. |
| [parallel_search_mcp](parallel_search_mcp/) | Capture a remote MCP server (Parallel Search) through MockServer and replay it offline. |

Each folder contains a runnable PHP script and its own `README.md` with
instructions.

> **Note:** The PHP client communicates via REST only and does not include
> WebSocket support. Interactive breakpoints (the "modify proxied exchanges"
> feature available in Go, .NET, Rust, Node, Python, and Ruby) are not available
> from PHP. The `forward_override` example shows the static alternative.
