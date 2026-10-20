//! Unit tests for the LLM and MCP fluent builders — pure JSON building,
//! no running server required.

use mockserver_client::llm::{
    completion, conversation, default_error_body, embedding, header, llm_failover, llm_mock,
    tool_use, turn, usage, Provider, Role,
};
use mockserver_client::mcp::{
    escape_json, escape_json_path, escape_velocity, mcp_mock, mcp_mock_default,
    validate_and_serialize_json,
};
use serde_json::json;

// ---------------------------------------------------------------------------
// LlmMockBuilder
// ---------------------------------------------------------------------------

#[test]
fn llm_mock_completion_wire_shape() {
    let expectation = llm_mock("/v1/chat/completions")
        .with_provider(Provider::OPENAI)
        .with_model("gpt-4o")
        .responding_with(
            completion()
                .with_text("Hello!")
                .with_stop_reason("stop")
                .with_usage(usage().with_input_tokens(5).with_output_tokens(3)),
        )
        .build();

    assert_eq!(expectation["httpRequest"]["method"], "POST");
    assert_eq!(expectation["httpRequest"]["path"], "/v1/chat/completions");

    let llm = &expectation["httpLlmResponse"];
    assert_eq!(llm["provider"], "OPENAI");
    assert_eq!(llm["model"], "gpt-4o");
    assert_eq!(llm["completion"]["text"], "Hello!");
    assert_eq!(llm["completion"]["stopReason"], "stop");
    assert_eq!(llm["completion"]["usage"]["inputTokens"], 5);
    assert_eq!(llm["completion"]["usage"]["outputTokens"], 3);
    // embedding must be absent when responding with a completion
    assert!(llm.get("embedding").is_none());
}

#[test]
fn llm_mock_provider_is_uppercase() {
    let expectation = llm_mock("/x")
        .with_provider(Provider::ANTHROPIC)
        .responding_with(completion().with_text("hi"))
        .build();
    assert_eq!(expectation["httpLlmResponse"]["provider"], "ANTHROPIC");
}

#[test]
fn llm_mock_embedding_clears_completion() {
    let expectation = llm_mock("/v1/embeddings")
        .with_provider(Provider::OPENAI)
        .responding_with(completion().with_text("ignored"))
        .responding_with(embedding().with_dimensions(1536).with_deterministic_from_input(true))
        .build();

    let llm = &expectation["httpLlmResponse"];
    assert!(llm.get("completion").is_none());
    assert_eq!(llm["embedding"]["dimensions"], 1536);
    assert_eq!(llm["embedding"]["deterministicFromInput"], true);
}

#[test]
fn llm_mock_tool_calls_serialize() {
    let expectation = llm_mock("/v1/chat/completions")
        .with_provider(Provider::ANTHROPIC)
        .responding_with(completion().with_tool_call(
            tool_use("get_weather")
                .with_id("call_1")
                .with_arguments(r#"{"city":"London"}"#),
        ))
        .build();

    let calls = expectation["httpLlmResponse"]["completion"]["toolCalls"]
        .as_array()
        .unwrap();
    assert_eq!(calls.len(), 1);
    assert_eq!(calls[0]["id"], "call_1");
    assert_eq!(calls[0]["name"], "get_weather");
    assert_eq!(calls[0]["arguments"], r#"{"city":"London"}"#);
}

#[test]
fn llm_mock_streaming_physics_wire_shape() {
    let expectation = llm_mock("/v1/chat/completions")
        .with_provider(Provider::OPENAI)
        .responding_with(
            completion()
                .with_text("streamed")
                .streaming()
                .with_streaming_physics(
                    mockserver_client::llm::streaming_physics()
                        .with_tokens_per_second(40)
                        .with_jitter(0.2)
                        .with_time_to_first_token(120, "MILLISECONDS"),
                ),
        )
        .build();

    let c = &expectation["httpLlmResponse"]["completion"];
    assert_eq!(c["streaming"], true);
    assert_eq!(c["streamingPhysics"]["tokensPerSecond"], 40);
    assert_eq!(c["streamingPhysics"]["jitter"], 0.2);
    assert_eq!(c["streamingPhysics"]["timeToFirstToken"]["timeUnit"], "MILLISECONDS");
    assert_eq!(c["streamingPhysics"]["timeToFirstToken"]["value"], 120);
}

// ---------------------------------------------------------------------------
// Conversation builder + scenario advancement
// ---------------------------------------------------------------------------

#[test]
fn conversation_scenario_states_advance() {
    let expectations = conversation()
        .with_path("/v1/chat/completions")
        .with_provider(Provider::OPENAI)
        .with_model("gpt-4o")
        .turn(turn().responding_with(completion().with_text("one")))
        .turn(
            turn()
                .when_latest_message_contains("again")
                .responding_with(completion().with_text("two")),
        )
        .turn(turn().responding_with(completion().with_text("three")))
        .build();

    assert_eq!(expectations.len(), 3);

    // turn 0
    assert_eq!(expectations[0]["scenarioState"], "Started");
    assert_eq!(expectations[0]["newScenarioState"], "turn_1");
    // turn 1
    assert_eq!(expectations[1]["scenarioState"], "turn_1");
    assert_eq!(expectations[1]["newScenarioState"], "turn_2");
    // turn 2 (last)
    assert_eq!(expectations[2]["scenarioState"], "turn_2");
    assert_eq!(expectations[2]["newScenarioState"], "__done");

    // scenario name shared and prefixed
    let name = expectations[0]["scenarioName"].as_str().unwrap();
    assert!(name.starts_with("__llm_conv_"), "got {name}");
    assert_eq!(expectations[1]["scenarioName"], name);
    assert_eq!(expectations[2]["scenarioName"], name);

    // predicate present only where set; absent otherwise
    assert!(expectations[0]["httpLlmResponse"]
        .get("conversationPredicates")
        .is_none());
    assert_eq!(
        expectations[1]["httpLlmResponse"]["conversationPredicates"]["latestMessageContains"],
        "again"
    );
    assert!(expectations[2]["httpLlmResponse"]
        .get("conversationPredicates")
        .is_none());
}

#[test]
fn conversation_isolation_suffix_encoded_in_scenario_name() {
    let expectations = conversation()
        .with_path("/c")
        .with_provider(Provider::OPENAI)
        .isolate_by(header("x-session-id"))
        .turn(turn().responding_with(completion().with_text("hi")))
        .build_with_id("fixed-uuid");

    assert_eq!(
        expectations[0]["scenarioName"],
        "__llm_conv_fixed-uuid__iso=header:x-session-id"
    );
}

#[test]
fn conversation_turn_role_predicate_uses_role_const() {
    let expectations = conversation()
        .with_path("/c")
        .with_provider(Provider::OPENAI)
        .turn(
            turn()
                .when_latest_message_role(Role::USER)
                .when_turn_index(0)
                .responding_with(completion().with_text("hi")),
        )
        .build();
    let preds = &expectations[0]["httpLlmResponse"]["conversationPredicates"];
    assert_eq!(preds["latestMessageRole"], "USER");
    assert_eq!(preds["turnIndex"], 0);
}

// ---------------------------------------------------------------------------
// Failover builder — coalescing + default error bodies
// ---------------------------------------------------------------------------

#[test]
fn failover_coalesces_consecutive_identical_failures() {
    let expectations = llm_failover()
        .with_path("/v1/chat/completions")
        .with_provider(Provider::OPENAI)
        .fail_with_count(429, 2)
        .fail_with(500)
        .then_respond_with(completion().with_text("recovered"))
        .build();

    // [429 x2 coalesced], [500 x1], [success]
    assert_eq!(expectations.len(), 3);

    assert_eq!(expectations[0]["httpResponse"]["statusCode"], 429);
    assert_eq!(expectations[0]["times"]["remainingTimes"], 2);
    assert_eq!(expectations[0]["times"]["unlimited"], false);
    assert_eq!(expectations[0]["timeToLive"]["unlimited"], true);
    assert_eq!(
        expectations[0]["httpResponse"]["headers"][0]["name"],
        "Content-Type"
    );

    assert_eq!(expectations[1]["httpResponse"]["statusCode"], 500);
    assert_eq!(expectations[1]["times"]["remainingTimes"], 1);

    // success expectation, unlimited
    assert_eq!(expectations[2]["times"]["remainingTimes"], 0);
    assert_eq!(expectations[2]["times"]["unlimited"], true);
    assert_eq!(
        expectations[2]["httpLlmResponse"]["completion"]["text"],
        "recovered"
    );
    assert_eq!(expectations[2]["httpLlmResponse"]["provider"], "OPENAI");
}

#[test]
fn failover_default_error_bodies() {
    assert_eq!(
        default_error_body(429),
        r#"{"error":{"type":"rate_limit_error","message":"Rate limit exceeded. Please retry after a brief wait."}}"#
    );
    assert_eq!(
        default_error_body(500),
        r#"{"error":{"type":"internal_server_error","message":"An internal error occurred. Please retry your request."}}"#
    );
    assert_eq!(
        default_error_body(502),
        r#"{"error":{"type":"bad_gateway","message":"Bad gateway. The upstream server returned an invalid response."}}"#
    );
    assert_eq!(
        default_error_body(503),
        r#"{"error":{"type":"service_unavailable","message":"The service is temporarily overloaded. Please retry later."}}"#
    );
    assert_eq!(
        default_error_body(418),
        r#"{"error":{"type":"error","message":"Request failed with status 418"}}"#
    );
}

#[test]
fn failover_failure_default_body_used_when_none_supplied() {
    let expectations = llm_failover()
        .with_path("/x")
        .with_provider(Provider::ANTHROPIC)
        .fail_with(429)
        .then_respond_with(completion().with_text("ok"))
        .build();
    assert_eq!(expectations[0]["httpResponse"]["body"], default_error_body(429));
}

#[test]
fn failover_custom_body_overrides_default() {
    let expectations = llm_failover()
        .with_path("/x")
        .with_provider(Provider::ANTHROPIC)
        .fail_with_body(503, r#"{"error":"custom"}"#)
        .then_respond_with(completion().with_text("ok"))
        .build();
    assert_eq!(expectations[0]["httpResponse"]["body"], r#"{"error":"custom"}"#);
}

// ---------------------------------------------------------------------------
// MCP builder
// ---------------------------------------------------------------------------

#[test]
fn mcp_initialize_velocity_template_present() {
    let expectations = mcp_mock_default().build();
    // initialize, ping, notifications/initialized
    assert_eq!(expectations.len(), 3);

    let initialize = &expectations[0];
    assert_eq!(initialize["httpRequest"]["body"]["type"], "JSON_RPC");
    assert_eq!(initialize["httpRequest"]["body"]["method"], "initialize");
    assert_eq!(
        initialize["httpResponseTemplate"]["templateType"],
        "VELOCITY"
    );
    let template = initialize["httpResponseTemplate"]["template"]
        .as_str()
        .unwrap();
    assert!(template.contains("$!{request.jsonRpcRawId}"));
    assert!(template.contains("\"protocolVersion\": \"2025-03-26\""));
    assert!(template.contains("MockMCPServer"));

    // ping
    assert_eq!(expectations[1]["httpRequest"]["body"]["method"], "ping");
    // notifications/initialized is a plain 200 response, not a template
    assert_eq!(
        expectations[2]["httpRequest"]["body"]["method"],
        "notifications/initialized"
    );
    assert_eq!(expectations[2]["httpResponse"]["statusCode"], 200);
    assert_eq!(expectations[2]["httpResponse"]["body"], "{}");
}

#[test]
fn mcp_tool_generates_list_and_call_jsonpath() {
    let expectations = mcp_mock("/mcp")
        .with_tool("get_weather")
        .with_description("Get the weather")
        .with_input_schema(r#"{ "type" : "object" }"#)
        .responding_with("sunny", false)
        .and()
        .build();

    // initialize, ping, notifications/initialized, tools/list, tools/call
    assert_eq!(expectations.len(), 5);

    // capabilities advertise tools
    let init_template = expectations[0]["httpResponseTemplate"]["template"]
        .as_str()
        .unwrap();
    assert!(init_template.contains("\"tools\": {\"listChanged\": false}"));

    // tools/list contains validated + compacted input schema
    let tools_list = expectations[3]["httpResponseTemplate"]["template"]
        .as_str()
        .unwrap();
    assert_eq!(expectations[3]["httpRequest"]["body"]["method"], "tools/list");
    assert!(tools_list.contains("get_weather"));
    assert!(tools_list.contains("Get the weather"));
    // compacted JSON schema, no spaces
    assert!(tools_list.contains(r#"{"type":"object"}"#));

    // tools/call uses a JSON_PATH matcher
    let call = &expectations[4];
    assert_eq!(call["httpRequest"]["body"]["type"], "JSON_PATH");
    assert_eq!(
        call["httpRequest"]["body"]["jsonPath"],
        "$[?(@.method == 'tools/call' && @.params.name == 'get_weather')]"
    );
    let call_template = call["httpResponseTemplate"]["template"].as_str().unwrap();
    assert!(call_template.contains("\"text\": \"sunny\""));
    assert!(call_template.contains("\"isError\": false"));
}

#[test]
fn mcp_resource_and_prompt_expectations() {
    let expectations = mcp_mock("/mcp")
        .with_resource("file:///config.json")
        .with_name("config")
        .with_content(r#"{"debug":true}"#)
        .and()
        .with_prompt("greeting")
        .with_description("A greeting prompt")
        .with_argument("name", Some("who to greet".to_string()), true)
        .responding_with(Role::ASSISTANT, "Hello there")
        .and()
        .build();

    // initialize, ping, notifications, resources/list, resources/read, prompts/list, prompts/get
    assert_eq!(expectations.len(), 7);

    let resources_read = &expectations[4];
    assert_eq!(resources_read["httpRequest"]["body"]["type"], "JSON_PATH");
    assert_eq!(
        resources_read["httpRequest"]["body"]["jsonPath"],
        "$[?(@.method == 'resources/read' && @.params.uri == 'file:///config.json')]"
    );

    let prompts_list = expectations[5]["httpResponseTemplate"]["template"]
        .as_str()
        .unwrap();
    assert!(prompts_list.contains("\"required\": true"));
    assert!(prompts_list.contains("who to greet"));

    let prompts_get = &expectations[6];
    assert_eq!(
        prompts_get["httpRequest"]["body"]["jsonPath"],
        "$[?(@.method == 'prompts/get' && @.params.name == 'greeting')]"
    );
    let get_template = prompts_get["httpResponseTemplate"]["template"]
        .as_str()
        .unwrap();
    assert!(get_template.contains("\"role\": \"ASSISTANT\""));
    assert!(get_template.contains("Hello there"));
}

#[test]
fn mcp_velocity_and_json_escaping() {
    // escapeJson strips surrounding quotes and escapes inner quotes
    assert_eq!(escape_json("a\"b"), "a\\\"b");
    // escapeVelocity rewrites $ and #
    assert_eq!(escape_velocity("$x #y"), "${esc.d}x ${esc.h}y");
    // escapeJsonPath escapes single quotes
    assert_eq!(escape_json_path("o'brien"), "o\\'brien");
    // backslashes are escaped before quotes, so a trailing backslash cannot escape the closing quote
    assert_eq!(escape_json_path("x\\"), "x\\\\");
    assert_eq!(escape_json_path("a\\'b"), "a\\\\\\'b");
    // validate+serialize compacts
    assert_eq!(
        validate_and_serialize_json(r#"{ "a" :  1 }"#).unwrap(),
        r#"{"a":1}"#
    );
    assert!(validate_and_serialize_json("not json").is_err());
}

#[test]
fn mcp_content_with_velocity_metachars_is_escaped() {
    let expectations = mcp_mock("/mcp")
        .with_tool("echo")
        .responding_with("price is $5 #1", false)
        .and()
        .build();
    let call_template = expectations[4]["httpResponseTemplate"]["template"]
        .as_str()
        .unwrap();
    assert!(call_template.contains("price is ${esc.d}5 ${esc.h}1"));
}

#[test]
fn mcp_velocity_escapes_backslashes_before_metachars() {
    // Shared cross-client table: backslash runs before $, # or the end of the
    // value are escaped; other backslashes are left as-is.
    let cases = [
        (r"a\$b", r"a${esc.b}${esc.d}b"),
        (r"a\#b", r"a${esc.b}${esc.h}b"),
        (r"end\", r"end${esc.b}"),
        (r"\\$", r"${esc.b}${esc.b}${esc.d}"),
        (r"${esc.b}", r"${esc.d}{esc.b}"),
        (r"é\$ü", r"é${esc.b}${esc.d}ü"),
        (r#"a\nb \"q\""#, r#"a\nb \"q\""#),
        (r"\\x", r"\\x"),
        ("$#", "${esc.d}${esc.h}"),
        ("", ""),
    ];
    for (input, expected) in cases {
        assert_eq!(escape_velocity(input), expected, "input: {input}");
    }

    let expectations = mcp_mock("/mcp")
        .with_tool("echo")
        .responding_with(r"a\$b", false)
        .and()
        .build();
    let call_template = expectations[4]["httpResponseTemplate"]["template"]
        .as_str()
        .unwrap();
    // JSON-escaped "a\\$b": both backslashes precede '$', so all are escaped
    assert!(
        call_template.contains("a${esc.b}${esc.b}${esc.d}b"),
        "{call_template}"
    );
}

#[test]
fn mcp_capabilities_only_for_present_features() {
    // tools capability flag with no tools still advertises tools/list
    let expectations = mcp_mock("/mcp").with_tools_capability().build();
    // initialize, ping, notifications, tools/list
    assert_eq!(expectations.len(), 4);
    assert_eq!(expectations[3]["httpRequest"]["body"]["method"], "tools/list");

    let init_template = expectations[0]["httpResponseTemplate"]["template"]
        .as_str()
        .unwrap();
    assert!(init_template.contains("\"tools\""));
    assert!(!init_template.contains("\"resources\""));
    assert!(!init_template.contains("\"prompts\""));
}

#[test]
fn mcp_round_trips_through_serde() {
    // every generated expectation must be valid JSON objects
    let expectations = mcp_mock_default()
        .with_tool("a")
        .responding_with("x", false)
        .and()
        .build();
    let value = json!(expectations);
    assert!(value.is_array());
    assert!(value.as_array().unwrap().iter().all(|e| e.is_object()));
}
