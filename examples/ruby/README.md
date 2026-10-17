# MockServer Ruby Examples

Runnable examples demonstrating the [MockServer Ruby client](../../mockserver-client-ruby/).

## Prerequisites

- **Ruby 3.0+**
- **MockServer running** on `localhost:1080` (e.g. `docker run -d -p 1080:1080 mockserver/mockserver`)
- **Install the client gem** -- either from the local source:

  ```bash
  cd ../../mockserver-client-ruby && gem build mockserver-client.gemspec && gem install mockserver-client-*.gem
  ```

  or from RubyGems:

  ```bash
  gem install mockserver-client
  ```

## Scenarios

| Folder | Description |
|--------|-------------|
| [response_action](response_action/) | Return a canned HTTP response (status, headers, body, delay) |
| [scenario](scenario/) | Stateful scenarios -- state machines, sequenced responses, timed/external/cross-protocol transitions |
| [load_scenario](load_scenario/) | Register, start, list, and stop a server-side load scenario (RATE ramp -> VU hold -> PAUSE) via the Load Scenario registry |
| [forward_action](forward_action/) | Forward requests to another host or override the forwarded request |
| [callback](callback/) | Register class callbacks (server-side class) and object/closure callbacks (response written in Ruby) |
| [request_matchers](request_matchers/) | Match requests by path, method, headers, query params, cookies, and body |
| [openapi_expectations](openapi_expectations/) | Create expectations from an OpenAPI specification |
| [verify_requests](verify_requests/) | Verify that MockServer received specific requests |
| [retrieve_recorded_requests](retrieve_recorded_requests/) | Retrieve requests that MockServer has recorded |
| [reverse_proxy](reverse_proxy/) | Use MockServer as a reverse proxy in front of real services |
| [breakpoint_modify_proxied](breakpoint_modify_proxied/) | Modify a proxied exchange with an interactive breakpoint (RESPONSE phase) |
| [parallel_search_mcp](parallel_search_mcp/) | Capture a remote MCP server (Parallel Search) through MockServer and replay it offline |
