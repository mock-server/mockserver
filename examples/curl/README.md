# MockServer Curl Examples

These runnable shell scripts were extracted from the original reference markdown at
`mockserver/mockserver-examples/curl_examples.md`. Each script is self-contained and executable.

## Prerequisites

- A running MockServer instance (e.g. `docker run -d -p 1080:1080 mockserver/mockserver`)
- `curl` installed
- Set the `MOCKSERVER_URL` environment variable if your MockServer is not at the default location:
  ```bash
  export MOCKSERVER_URL=http://localhost:1080
  ```
  If not set, scripts default to `http://localhost:1080`.

## Examples Index

### create_expectation/

| File | Description |
|------|-------------|
| `basic_expectation.sh` | Create a basic expectation with query params and cookies |
| `array_of_expectations.sh` | Create multiple expectations in a single request |
| `match_exactly_twice.sh` | Expectation that matches exactly twice then expires |
| `match_once_within_60s.sh` | Expectation that matches once and expires after 60 seconds |
| `update_by_id.sh` | Update an existing expectation by its ID |
| `match_all_requests.sh` | Match all incoming requests (no request matcher) |

### callback/

| File | Description |
|------|-------------|
| `class_callback.sh` | Expectation with a Java class callback action |

### matchers/path/

| File | Description |
|------|-------------|
| `match_by_path.sh` | Match requests by exact path |
| `match_by_regex_path.sh` | Match requests by regex path pattern |
| `match_by_not_matching_path.sh` | Match requests by negated path (NOT matching) |
| `match_by_path_parameter_regex.sh` | Match by path parameter with regex value |
| `match_by_path_parameter_json_schema.sh` | Match by path parameters with JSON Schema validation |

### matchers/method/

| File | Description |
|------|-------------|
| `match_by_method_regex.sh` | Match requests by method regex |
| `match_by_not_matching_method.sh` | Match requests by negated method (NOT GET) |

### matchers/query_parameters/

| File | Description |
|------|-------------|
| `match_by_query_param_regex_value.sh` | Match by query parameter regex value |
| `match_by_query_param_regex_name.sh` | Match by query parameter regex name |
| `match_by_optional_query_param.sh` | Match with optional query parameters (? prefix) |
| `match_by_query_param_json_schema.sh` | Match by query parameters with JSON Schema |
| `match_by_query_param_key_matching.sh` | Match using MATCHING_KEY key match style |

### matchers/headers/

| File | Description |
|------|-------------|
| `match_by_headers.sh` | Match by specific header names and values |
| `match_by_header_name_regex.sh` | Match by header name regex |
| `match_by_header_regex_name_and_value.sh` | Match by regex header name and value |
| `match_by_header_json_schema.sh` | Match by header values with JSON Schema |
| `match_by_either_or_optional_header.sh` | Match with either/or optional headers |
| `match_by_not_matching_header_value.sh` | Match by negated header value |
| `match_by_not_matching_headers.sh` | Match requests that do NOT have specific headers |
| `accept_negotiation.sh` | Match by Accept-header content negotiation (`accept:` directive) |

### matchers/conditional/

| File | Description |
|------|-------------|
| `conditional_request.sh` | Match using an if/then/else conditional request definition |

### matchers/cookies/

| File | Description |
|------|-------------|
| `match_by_cookies_and_query_params.sh` | Match by cookies and query string parameters |
| `match_by_cookies_json_schema.sh` | Match by cookies with JSON Schema validation |

### matchers/body/

| File | Description |
|------|-------------|
| `fuzzy_body.sh` | Match body by fuzzy (approximate) string similarity |
| `match_by_regex_body.sh` | Match by regex body pattern |
| `match_by_body_utf16.sh` | Match by body with UTF-16 content type |
| `match_by_form_submission.sh` | Match POST with form-urlencoded body |
| `match_by_xpath.sh` | Match XML body by XPath expression |
| `match_by_xml.sh` | Match by specific XML body |
| `match_by_xml_schema.sh` | Match body against an XML Schema |
| `match_by_json_exact.sh` | Match JSON body with STRICT matching |
| `match_by_json_ignoring_extra_fields.sh` | Match JSON body ignoring extra fields |
| `match_by_json_with_placeholders.sh` | Match JSON body with json-unit placeholders |
| `match_by_json_schema.sh` | Match body against a JSON Schema |
| `match_by_json_path.sh` | Match body by JsonPath expression |
| `match_by_not_matching_json_path.sh` | Match body NOT matching a JsonPath expression |

### response_action/

| File | Description |
|------|-------------|
| `response_with_plain_text.sh` | Return a response with plain text Content-Type |
| `response_with_cookie.sh` | Return a response with a Set-Cookie header |
| `response_with_status_code_and_reason.sh` | Return a custom status code and reason phrase |
| `response_with_delay.sh` | Return a response after a 10-second delay |
| `response_suppress_headers.sh` | Suppress Content-Length and Connection headers |
| `response_override_headers.sh` | Override Content-Length and keep-alive |
| `response_close_socket.sh` | Return a response then close the socket |
| `weighted_response.sh` | Pick one of several responses at random by weight |
| `switch_hit_count.sh` | Switch responses after a hit-count threshold |
| `generate_from_schema.sh` | Generate the response body from an inline JSON Schema |

### error_action/

| File | Description |
|------|-------------|
| `random_bytes_error.sh` | Drop connection and return random bytes |
| `drop_connection.sh` | Drop the connection without response |
| `reset_connection.sh` | Reset the connection with a TCP reset (connection reset by peer) |

### forward_action/

| File | Description |
|------|-------------|
| `forward_http.sh` | Forward requests to a target host over HTTP |
| `forward_https.sh` | Forward requests to a target host over HTTPS |
| `forward_override_request.sh` | Forward with overridden path and Host header |
| `forward_override_request_and_response.sh` | Forward with overridden request and response |
| `forward_override_change_host_port.sh` | Forward with overridden host, port, and scheme |
| `forward_override_with_delay.sh` | Forward with a 20-second delay |

### template_forward/

| File | Description |
|------|-------------|
| `javascript_template.sh` | Forward using a JavaScript template |
| `velocity_template.sh` | Forward using a Velocity template |

### template_response/

| File | Description |
|------|-------------|
| `javascript_template.sh` | Generate response with a JavaScript template |
| `javascript_template_with_delay.sh` | JavaScript template response with 2-minute delay |
| `velocity_template.sh` | Generate response with a Velocity template |

### verify/

| File | Description |
|------|-------------|
| `verify_exact.sh` | Verify a request was received exactly twice |
| `verify_at_least.sh` | Verify a request was received at least twice |
| `verify_at_most.sh` | Verify a request was received at most twice |
| `verify_sequence.sh` | Verify a sequence of requests in order |
| `verify_by_openapi.sh` | Verify requests matching an OpenAPI operation |
| `verify_all.sh` | Soft/collecting verification of several requests |
| `verify_response.sh` | Verify a recorded response (forwarded or mocked) |

### retrieve/

| File | Description |
|------|-------------|
| `retrieve_recorded_requests.sh` | Retrieve recorded requests filtered by matcher |
| `retrieve_all_recorded_requests.sh` | Retrieve all recorded requests |
| `retrieve_log_messages.sh` | Retrieve log messages filtered by matcher |
| `retrieve_active_expectations.sh` | Retrieve all active expectations |
| `retrieve_active_expectations_filtered.sh` | Retrieve active expectations filtered by matcher |
| `retrieve_recorded_expectations.sh` | Retrieve all recorded expectations (proxy mode) |
| `retrieve_request_responses.sh` | Retrieve all recorded request-response pairs |

### clear/

| File | Description |
|------|-------------|
| `clear_by_request_matcher.sh` | Clear expectations/logs by request path |
| `clear_by_openapi.sh` | Clear by OpenAPI operation |
| `clear_by_expectation_id.sh` | Clear a specific expectation by ID |
| `clear_logs_only.sh` | Clear only request logs, keep expectations |

### reset/

| File | Description |
|------|-------------|
| `reset.sh` | Reset MockServer (clear all state) |

### forward_fallback/

| File | Description |
|------|-------------|
| `forward_with_fallback.sh` | Forward with fallback response on specific status codes or timeout |
| `forward_with_fallback_default_codes.sh` | Forward with fallback using default status code matching (500-599) |

### websocket/

| File | Description |
|------|-------------|
| `websocket_response.sh` | WebSocket expectation with subprotocol and text messages |
| `websocket_with_matchers.sh` | WebSocket expectation with a message matcher (ping/pong) |

### scenario/

| File | Description |
|------|-------------|
| `state_machine.sh` | Stateful scenario: login flow with `scenarioName` / `scenarioState` / `newScenarioState` |
| `sequential_cycling.sh` | Multiple responses served sequentially (`httpResponses` array, `SEQUENTIAL` mode) |
| `timed_transition.sh` | Auto-advance a scenario state after a delay (`PUT /mockserver/scenario/{name}`) |
| `external_trigger.sh` | Force a scenario state change from a test harness (`PUT /mockserver/scenario/{name}/trigger`) |
| `cross_protocol.sh` | `crossProtocolScenarios`: an HTTP_REQUEST event advances a second expectation's gating state |

### load_scenario/

The **Load Scenario registry**: register a named, server-side traffic generator, then
start/stop it by name (requires the server started with `-Dmockserver.loadGenerationEnabled=true`).

| File | Description |
|------|-------------|
| `register_start_list_stop.sh` | Core lifecycle: register (`PUT /mockserver/loadScenario`) → start → list (expect `RUNNING`) → stop, for a RATE ramp → VU hold → PAUSE profile |
| `multi_stage_profile.sh` | A realistic multi-stage `checkout-load` (RATE ramp 5→50 req/s, VU hold, PAUSE; two templated steps); polls live status, stops all, then clears the registry |

### sse/

| File | Description |
|------|-------------|
| `sse_response.sh` | Server-Sent Events response with multiple events |

### binary_response/

| File | Description |
|------|-------------|
| `binary_response.sh` | Return raw binary data (base64-encoded) |

### dns_response/

| File | Description |
|------|-------------|
| `dns_a_record.sh` | DNS response with an A record |
| `dns_nxdomain.sh` | DNS NXDOMAIN response (domain not found) |

### forward_class_callback/

| File | Description |
|------|-------------|
| `forward_class_callback.sh` | Forward requests using a Java class callback |

### grpc_stream/

| File | Description |
|------|-------------|
| `grpc_stream_response.sh` | gRPC server-streaming response with delayed messages |
| `grpc_stream_error.sh` | gRPC error response (UNAVAILABLE) with connection close |

### openapi/

| File | Description |
|------|-------------|
| `expectations_from_url.sh` | Create expectations from an OpenAPI spec URL |
| `expectation_for_operation.sh` | Create expectation for a specific OpenAPI operation |
| `expectation_from_classpath.sh` | Create expectation from a classpath OpenAPI spec |

### contract/

| File | Description |
|------|-------------|
| `contract_test.sh` | Run an OpenAPI spec as a contract test against a live service (`PUT /mockserver/contractTest`) |

### pact/

| File | Description |
|------|-------------|
| `import_pact_with_provider_state.sh` | Import a Pact v3 contract gated by a provider state, then activate the state so it matches |

### llm/

| File | Description |
|------|-------------|
| `chat_completion.sh` | Mock an OpenAI-style chat completion via `httpLlmResponse` |
| `embeddings.sh` | Mock an embeddings response (deterministic from input) |
| `rerank.sh` | Mock a rerank response returning the top N candidates |

### mcp/

| File | Description |
|------|-------------|
| `mcp_tool.sh` | Mock an MCP server tool (`tools/list` and `tools/call`) over JSON-RPC |

### parallel_search_mcp/

| File | Description |
|------|-------------|
| `parallel_search_mcp.sh` | Capture a remote MCP server (Parallel Search) through MockServer and replay it offline |

### a2a/

| File | Description |
|------|-------------|
| `agent_card.sh` | Serve an A2A agent card from `/.well-known/agent.json` |
| `task_response.sh` | Return a completed A2A task for a `tasks/send` JSON-RPC call |

### graphql/

| File | Description |
|------|-------------|
| `import_sdl.sh` | Import a GraphQL SDL via `PUT /mockserver/graphql` |
| `match_by_graphql_query.sh` | Match a request by a `GRAPHQL` body matcher (query AST) |
