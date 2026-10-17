# MockServer .NET Examples

Runnable examples demonstrating the [MockServer .NET client](../../mockserver-client-dotnet/).

## Prerequisites

- **.NET SDK 8.0+**
- **MockServer running** on `localhost:1080` (e.g. `docker run -d -p 1080:1080 mockserver/mockserver`)
- Each example project references the local client via a `ProjectReference`, so
  no NuGet install is needed. Or use the published package:

  ```bash
  dotnet add package MockServerClient
  ```

## Examples

| Folder | Description |
|--------|-------------|
| [create_expectation](create_expectation/) | Create an expectation, send a test request, and verify it was received. |
| [modify_proxied_response](modify_proxied_response/) | Register a RESPONSE breakpoint that modifies a proxied response in-flight. |
| [scenario](scenario/) | Run the 5 canonical stateful-scenario flows (state machine, cycling responses, timed transition, external trigger, cross-protocol). |
| [load_scenario](load_scenario/) | Register, start, list, and stop a server-side load scenario (RATE ramp → VU hold → PAUSE) via the Load Scenario registry. |
| [parallel_search_mcp](parallel_search_mcp/) | Capture a remote MCP server (Parallel Search) through MockServer and replay it offline. |

Each folder contains a runnable .NET console application and its own `README.md`
with instructions.
