# MockServer Go Examples

Runnable examples demonstrating the [MockServer Go client](../../mockserver-client-go/).

## Prerequisites

- **Go 1.21+**
- **MockServer running** on `localhost:1080` (e.g. `docker run -d -p 1080:1080 mockserver/mockserver`)
- **Install the client** -- from the local source tree the `go.mod` `replace` directive
  already points to the local client, so no extra install step is needed. Or use
  the published module:

  ```bash
  go get github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9
  ```

## Examples

| Folder | Description |
|--------|-------------|
| [create_expectation](create_expectation/) | Create an expectation, send a test request, and verify it was received. |
| [modify_proxied_response](modify_proxied_response/) | Register a RESPONSE breakpoint that modifies a proxied response in-flight. |
| [scenario](scenario/) | Run the five canonical stateful-scenario flows (state machine, sequential cycling, timed transition, external trigger, cross-protocol) and assert each. |
| [load_scenario](load_scenario/) | Register, start, list, and stop a server-side load scenario (RATE ramp → VU hold → PAUSE) via the Load Scenario registry. |

Each folder contains a runnable Go program and its own `README.md` with
instructions.
