# MockServer JSON Examples

These JSON expectation/request payloads were extracted from the original reference markdown at
`mockserver/mockserver-examples/json_examples.md`. Each file contains valid JSON that can be used
as the request body for MockServer's REST API (e.g. `PUT /mockserver/expectation`).

## Prerequisites

- A running MockServer instance
- These JSON files can be used with curl:
  ```bash
  curl -X PUT "${MOCKSERVER_URL:-http://localhost:1080}/mockserver/expectation" \
    -d @examples/json/create_expectation/basic_expectation.json
  ```

## Examples Index

### create_expectation/

| File | Description |
|------|-------------|
| `basic_expectation.json` | Basic expectation with query params and cookies |
| `array_of_expectations.json` | Multiple expectations in a single JSON array |
| `match_exactly_twice.json` | Expectation that matches exactly twice |
| `match_once_within_60s.json` | Expectation that matches once, expires after 60s |
| `update_by_id.json` | Update an existing expectation by its ID |
| `match_all_requests.json` | Match all requests (no request matcher) |

### callback/

| File | Description |
|------|-------------|
| `class_callback.json` | Expectation with a Java class callback action |

### matchers/path/

| File | Description |
|------|-------------|
| `match_by_path.json` | Match by exact path |
| `match_by_regex_path.json` | Match by regex path pattern |
| `match_by_not_matching_path.json` | Match by negated path (NOT matching) |
| `match_by_path_parameter_regex.json` | Match by path parameter with regex value |
| `match_by_path_parameter_json_schema.json` | Match by path parameters with JSON Schema |

### matchers/method/

| File | Description |
|------|-------------|
| `match_by_method_regex.json` | Match by method regex |
| `match_by_not_matching_method.json` | Match by negated method (NOT GET) |

### matchers/query_parameters/

| File | Description |
|------|-------------|
| `match_by_query_param_regex_value.json` | Match by query parameter regex value |
| `match_by_optional_query_param.json` | Match with optional query parameters (? prefix) |
| `match_by_query_param_json_schema.json` | Match by query parameters with JSON Schema |
| `match_by_query_param_key_matching.json` | Match using MATCHING_KEY key match style |

### matchers/headers/

| File | Description |
|------|-------------|
| `match_by_headers.json` | Match by specific header names and values |
| `match_by_header_name_regex.json` | Match by header name regex |
| `match_by_header_regex_name_and_value.json` | Match by regex header name and value |
| `match_by_either_or_optional_header.json` | Match with either/or optional headers |
| `match_by_not_matching_header_value.json` | Match by negated header value |
| `match_by_not_matching_headers.json` | Match requests missing specific headers |
| `match_by_header_key_matching.json` | Match using MATCHING_KEY key match style |
| `match_by_accept_media_type.json` | Match by Accept content negotiation (`accept:<media-type>`) |

### matchers/cookies/

| File | Description |
|------|-------------|
| `match_by_cookies_and_query_params.json` | Match by cookies and query parameters |
| `match_by_cookies_json_schema.json` | Match by cookies with JSON Schema validation |

### matchers/body/

| File | Description |
|------|-------------|
| `match_by_regex_body.json` | Match by regex body pattern |
| `match_by_form_submission.json` | Match POST with form-urlencoded body |
| `match_by_xpath.json` | Match XML body by XPath expression |
| `match_by_xml.json` | Match by specific XML body |
| `match_by_json_exact.json` | Match JSON body with STRICT matching |
| `match_by_json_ignoring_extra_fields.json` | Match JSON body ignoring extra fields |
| `match_by_json_with_placeholders.json` | Match JSON body with json-unit placeholders |
| `match_by_json_schema.json` | Match body against a JSON Schema |
| `match_by_json_path.json` | Match body by JsonPath expression |
| `match_by_not_matching_json_path.json` | Match body NOT matching JsonPath |
| `match_by_fuzzy_body.json` | Match a text body by fuzzy (edit-distance) similarity |

### matchers/conditional/

| File | Description |
|------|-------------|
| `match_by_conditional_request.json` | Match using an if/then/else request definition |

### response_action/

| File | Description |
|------|-------------|
| `response_with_plain_text.json` | Response with plain text Content-Type |
| `response_with_cookie.json` | Response with a Set-Cookie header |
| `response_with_status_code_and_reason.json` | Custom status code and reason phrase |
| `response_with_delay.json` | Response after a 10-second delay |
| `response_suppress_headers.json` | Suppress Content-Length and Connection headers |
| `response_override_headers.json` | Override Content-Length and keep-alive |
| `response_close_socket.json` | Response then close the socket |
| `weighted_response.json` | Pick one of several responses by weight (WEIGHTED mode) |
| `switch_hit_count.json` | Switch responses after a hit count (SWITCH mode) |
| `generate_from_schema.json` | Generate a response body from an inline JSON Schema |

### error_action/

| File | Description |
|------|-------------|
| `random_bytes_error.json` | Drop connection and return random bytes |
| `drop_connection.json` | Drop the connection without response |
| `reset_connection.json` | Reset the connection with a TCP reset (connection reset by peer) |

### forward_action/

| File | Description |
|------|-------------|
| `forward_http.json` | Forward to a target host over HTTP |
| `forward_https.json` | Forward to a target host over HTTPS |
| `forward_override_request.json` | Forward with overridden path and Host |
| `forward_override_request_and_response.json` | Forward with overridden request and response |
| `forward_override_change_host_port.json` | Forward with overridden host, port, and scheme |
| `forward_override_with_delay.json` | Forward with a 20-second delay |

### template_forward/

| File | Description |
|------|-------------|
| `javascript_template.json` | Forward using a JavaScript template |
| `velocity_template.json` | Forward using a Velocity template |

### template_response/

| File | Description |
|------|-------------|
| `javascript_template.json` | Response via JavaScript template |
| `javascript_template_with_delay.json` | JavaScript template response with delay |
| `velocity_template.json` | Response via Velocity template |

### verify/

| File | Description |
|------|-------------|
| `verify_exact.json` | Verify request received exactly twice |
| `verify_at_least.json` | Verify request received at least twice |
| `verify_at_most.json` | Verify request received at most twice |
| `verify_sequence.json` | Verify a sequence of requests in order |
| `verify_by_openapi.json` | Verify requests matching an OpenAPI operation |
| `verify_all.json` | Per-verification body used by the client `verifyAll(...)` (soft verification) |
| `verify_response.json` | Verify a received response by matching on `httpResponse` |

### forward_fallback/

| File | Description |
|------|-------------|
| `forward_with_fallback.json` | Forward with fallback response on specific status codes or timeout |
| `forward_with_fallback_default_codes.json` | Forward with fallback using default status code matching (500-599) |

### websocket/

| File | Description |
|------|-------------|
| `websocket_response.json` | WebSocket expectation with subprotocol and text messages |
| `websocket_with_matchers.json` | WebSocket expectation with a message matcher (ping/pong) |

### sse/

| File | Description |
|------|-------------|
| `sse_response.json` | Server-Sent Events response with multiple events |

### binary_response/

| File | Description |
|------|-------------|
| `binary_response.json` | Return raw binary data (base64-encoded) |

### dns_response/

| File | Description |
|------|-------------|
| `dns_a_record.json` | DNS response with an A record |
| `dns_nxdomain.json` | DNS NXDOMAIN response (domain not found) |

### forward_class_callback/

| File | Description |
|------|-------------|
| `forward_class_callback.json` | Forward requests using a Java class callback |

### grpc_stream/

| File | Description |
|------|-------------|
| `grpc_stream_response.json` | gRPC server-streaming response with delayed messages |
| `grpc_stream_error.json` | gRPC error response (UNAVAILABLE) with connection close |

### openapi/

| File | Description |
|------|-------------|
| `expectations_from_url.json` | Create expectations from an OpenAPI spec URL |
| `expectation_for_operation.json` | Create expectation for a specific OpenAPI operation |
| `expectation_from_classpath.json` | Create expectation from a classpath OpenAPI spec |

### contract/

| File | Description |
|------|-------------|
| `contract_test.json` | Request body for running an OpenAPI spec as a contract test against a live service (`PUT /mockserver/contractTest`) |

### pact/

| File | Description |
|------|-------------|
| `pact_with_provider_state.json` | A Pact v3 contract with a provider-state-gated interaction (`PUT /mockserver/pact/import`) |

### scenario/

| File | Description |
|------|-------------|
| `state_machine.json` | Array of expectations for a login state machine (`scenarioName` / `scenarioState` / `newScenarioState`) |
| `sequential_cycling.json` | Single expectation with an `httpResponses` array in `SEQUENTIAL` mode |
| `cross_protocol.json` | Array of two expectations using `crossProtocolScenarios` for cross-protocol state correlation |
| `timed_transition.json` | Request body for `PUT /mockserver/scenario/DeployFlow` — set state with timed auto-transition |
| `external_trigger.json` | Request body for `PUT /mockserver/scenario/HealthFlow/trigger` — force a state change externally |

### load_scenario/

**Load Scenario registry** request bodies — register a named, server-side traffic generator
(`PUT /mockserver/loadScenario`), then start/stop it by name (starting requires the server
started with `-Dmockserver.loadGenerationEnabled=true`).

| File | Description |
|------|-------------|
| `checkout_load.json` | A realistic multi-stage `LoadScenario` (RATE ramp 5→50 req/s capped at 50 VUs, VU hold, PAUSE; two Velocity-templated steps) |
| `demo_soak.json` | A simpler single-step soak `LoadScenario` (RATE ramp → VU hold → PAUSE) |
| `start_single.json` | Start one scenario by name — `{"name":"checkout-load"}` for `PUT .../start` |
| `start_names.json` | Start several scenarios — `{"names":[...]}` for `PUT .../start` |
| `stop_all.json` | Stop all running scenarios — `{"all":true}` body for `PUT .../stop` |

### llm/

| File | Description |
|------|-------------|
| `chat_completion.json` | Mock an LLM chat/text completion (`httpLlmResponse`) |
| `embeddings.json` | Mock an LLM embeddings response |
| `rerank.json` | Mock an LLM rerank response |

### mcp/

| File | Description |
|------|-------------|
| `mcp_tools_list.json` | Answer the MCP `tools/list` discovery call |
| `mcp_tool_call.json` | Answer an MCP `tools/call` invocation |

### parallel_search_mcp/

| File | Description |
|------|-------------|
| `capture_forward.json` | Forward `/mcp` to the live Parallel Search MCP server so MockServer records it |
| `replay_expectations.json` | Replay a recorded Parallel Search MCP session offline, echoing each JSON-RPC id |

### a2a/

| File | Description |
|------|-------------|
| `a2a_agent_card.json` | Serve an A2A agent card from `/.well-known/agent.json` |
| `a2a_task_response.json` | Answer an A2A `tasks/send` JSON-RPC call |

### graphql/

| File | Description |
|------|-------------|
| `match_by_query.json` | Match a GraphQL request by its query |
| `match_by_operation_and_variables_schema.json` | Match by operation name and validate variables against a JSON Schema |
