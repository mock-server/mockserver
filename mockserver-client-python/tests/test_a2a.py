"""Tests for the A2A mock builder (parity with Java A2aMockBuilder).

These assert the serialized expectation JSON so the Python client produces the same
wire format / control-plane contract as the Java client.
"""

from mockserver.a2a import a2a_mock


def _dicts(builder):
    return [exp.to_dict() for exp in builder.build()]


def test_minimal_mock_has_four_expectations():
    # agent card + tasks/send + tasks/get + tasks/cancel
    expectations = _dicts(a2a_mock())
    assert len(expectations) == 4


def test_custom_path_applied_to_jsonrpc_requests():
    expectations = _dicts(a2a_mock("/custom/a2a"))
    # agent card is on the agent-card path; the JSON-RPC ones use the custom path
    jsonrpc = [e for e in expectations if e["httpRequest"].get("method") == "POST"]
    assert jsonrpc
    for e in jsonrpc:
        assert e["httpRequest"]["path"] == "/custom/a2a"


def test_agent_card_is_static_get_with_capabilities_and_skills():
    expectations = _dicts(
        a2a_mock()
        .with_agent_name("TestAgent")
        .with_agent_version("2.0.0")
        .with_skill("skill1")
        .with_name("Skill One")
        .with_description("First skill")
        .with_tag("test")
        .with_example("do a thing")
        .and_()
    )
    card = expectations[0]
    assert card["httpRequest"] == {"method": "GET", "path": "/.well-known/agent.json"}
    assert card["httpResponse"]["statusCode"] == 200
    assert card["httpResponse"]["headers"] == [
        {"name": "Content-Type", "values": ["application/json"]}
    ]
    body = card["httpResponse"]["body"]
    assert '"name": "TestAgent"' in body
    assert '"version": "2.0.0"' in body
    assert '"streaming": false' in body
    assert '"pushNotifications": false' in body
    assert '"stateTransitionHistory": false' in body
    assert '"id": "skill1"' in body
    assert '"name": "Skill One"' in body
    assert '"description": "First skill"' in body
    assert '"tags": ["test"]' in body
    assert '"examples": ["do a thing"]' in body


def test_skill_name_defaults_to_id():
    expectations = _dicts(a2a_mock().with_skill("translate").and_())
    body = expectations[0]["httpResponse"]["body"]
    assert '"id": "translate"' in body
    assert '"name": "translate"' in body


def test_custom_agent_url_used_when_set():
    expectations = _dicts(a2a_mock("/agent").with_agent_url("http://localhost:8080/agent"))
    body = expectations[0]["httpResponse"]["body"]
    assert '"url": "http://localhost:8080/agent"' in body


def test_default_agent_url_derived_from_path():
    expectations = _dicts(a2a_mock("/agent"))
    body = expectations[0]["httpResponse"]["body"]
    assert '"url": "http://localhost/agent"' in body


def test_custom_agent_card_path():
    expectations = _dicts(a2a_mock().with_agent_card_path("/agent-card"))
    assert expectations[0]["httpRequest"]["path"] == "/agent-card"


def test_tasks_send_uses_velocity_template_echoing_id():
    expectations = _dicts(a2a_mock())
    send = expectations[1]
    assert send["httpRequest"]["body"] == {"type": "JSON_RPC", "method": "tasks/send"}
    template = send["httpResponseTemplate"]
    assert template["templateType"] == "VELOCITY"
    assert "$!{request.jsonRpcRawId}" in template["template"]
    assert '"jsonrpc": "2.0"' in template["template"]


def test_tasks_get_and_cancel_present():
    expectations = _dicts(a2a_mock())
    methods = [
        e["httpRequest"]["body"]["method"]
        for e in expectations
        if isinstance(e["httpRequest"].get("body"), dict)
        and e["httpRequest"]["body"].get("type") == "JSON_RPC"
    ]
    assert "tasks/get" in methods
    assert "tasks/cancel" in methods
    cancel = expectations[3]
    assert '"state": "canceled"' in cancel["httpResponseTemplate"]["template"]


def test_default_task_response_in_template():
    expectations = _dicts(a2a_mock().with_default_task_response("Custom default response"))
    assert "Custom default response" in expectations[1]["httpResponseTemplate"]["template"]


def test_custom_task_handler_uses_json_path_regex():
    expectations = _dicts(
        a2a_mock()
        .on_task_send()
        .matching_message("translate.*")
        .responding_with("Hola")
        .and_()
    )
    # agent card + custom handler + tasks/send + tasks/get + tasks/cancel
    assert len(expectations) == 5
    handler = expectations[1]
    body = handler["httpRequest"]["body"]
    assert body["type"] == "JSON_PATH"
    assert (
        body["jsonPath"]
        == "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /translate.*/)]"
    )
    assert "Hola" in handler["httpResponseTemplate"]["template"]


def test_custom_handlers_placed_before_default_handlers():
    expectations = _dicts(
        a2a_mock()
        .on_task_send()
        .matching_message("custom.*")
        .responding_with("Custom response")
        .and_()
    )
    assert len(expectations) == 5
    assert "httpResponse" in expectations[0]  # agent card
    assert expectations[1]["httpRequest"]["body"]["type"] == "JSON_PATH"
    assert expectations[2]["httpRequest"]["body"]["method"] == "tasks/send"


def test_error_task_handler_state_failed():
    expectations = _dicts(
        a2a_mock()
        .on_task_send()
        .matching_message("bad.*")
        .responding_with("Error occurred", is_error=True)
        .and_()
    )
    template = expectations[1]["httpResponseTemplate"]["template"]
    assert '"state": "failed"' in template


def test_velocity_metacharacters_escaped_in_default_response():
    expectations = _dicts(a2a_mock().with_default_task_response("$100 off #sale"))
    template = expectations[1]["httpResponseTemplate"]["template"]
    assert "${esc.d}100 off ${esc.h}sale" in template


def test_backslash_escaped_for_velocity_in_default_response():
    expectations = _dicts(a2a_mock().with_default_task_response("a\\#b"))
    template = expectations[1]["httpResponseTemplate"]["template"]
    assert "a${esc.b}${esc.b}${esc.h}b" in template


def test_velocity_metacharacters_escaped_in_custom_handler():
    expectations = _dicts(
        a2a_mock()
        .on_task_send()
        .matching_message("test.*")
        .responding_with("Price is $50 #discount")
        .and_()
    )
    template = expectations[1]["httpResponseTemplate"]["template"]
    assert "${esc.d}50 ${esc.h}discount" in template


def test_slash_escaped_in_message_pattern():
    expectations = _dicts(
        a2a_mock()
        .on_task_send()
        .matching_message("path/to/resource")
        .responding_with("found")
        .and_()
    )
    json_path = expectations[1]["httpRequest"]["body"]["jsonPath"]
    assert "path\\/to\\/resource" in json_path


def test_newline_and_carriage_return_escaped_and_null_stripped():
    expectations = _dicts(
        a2a_mock()
        .on_task_send()
        .matching_message("line1\nline2\rmore\0null")
        .responding_with("found")
        .and_()
    )
    json_path = expectations[1]["httpRequest"]["body"]["jsonPath"]
    assert "line1\\nline2\\rmore" in json_path
    assert "null" in json_path
    assert "\0" not in json_path
    assert "\n" not in json_path


def _pattern_json_path(pattern):
    expectations = _dicts(
        a2a_mock()
        .on_task_send()
        .matching_message(pattern)
        .responding_with("found")
        .and_()
    )
    return expectations[1]["httpRequest"]["body"]["jsonPath"]


def test_existing_regex_escape_sequence_preserved():
    # "\d+" must stay "\d+" — a single backslash, NOT doubled.
    json_path = _pattern_json_path("\\d+")
    assert "/\\d+/" in json_path
    # exactly one backslash before the d (no over-escaping)
    assert "\\\\d" not in json_path


def test_already_escaped_slash_preserved():
    # "a\/b" must stay "a\/b" — the existing escape is preserved, not doubled to "a\\/b".
    json_path = _pattern_json_path("a\\/b")
    assert "/a\\/b/" in json_path
    assert "a\\\\/b" not in json_path


def test_trailing_lone_backslash_cannot_break_out_of_regex_literal():
    # One real trailing backslash ("trail\") must become two ("trail\\") so it
    # escapes itself rather than the closing "/" delimiter.
    json_path = _pattern_json_path("trail\\")
    # SECURITY: the regex literal still closes with an UNESCAPED "/)]" — no breakout.
    assert json_path.endswith("/trail\\\\/)]")
    assert "trail\\\\/)]" in json_path


def test_special_json_characters_escaped_in_response():
    expectations = _dicts(a2a_mock().with_default_task_response("line1\nline2\ttab"))
    template = expectations[1]["httpResponseTemplate"]["template"]
    assert "line1\\nline2\\ttab" in template


# --- streaming -------------------------------------------------------------


def test_streaming_advertised_in_agent_card():
    expectations = _dicts(a2a_mock().with_streaming())
    body = expectations[0]["httpResponse"]["body"]
    assert '"streaming": true' in body
    assert '"pushNotifications": false' in body


def test_streaming_generates_sse_expectation():
    expectations = _dicts(a2a_mock().with_streaming().with_default_task_response("streamed result"))
    # agent card + streaming SSE + tasks/send + tasks/get + tasks/cancel
    assert len(expectations) == 5

    streaming = next((e for e in expectations if "httpSseResponse" in e), None)
    assert streaming is not None
    sse = streaming["httpSseResponse"]
    assert sse["statusCode"] == 200
    assert sse["closeConnection"] is True
    assert len(sse["events"]) == 3

    all_data = "".join(ev["data"] for ev in sse["events"])
    assert "status-update" in all_data
    assert "artifact-update" in all_data
    assert '"state": "working"' in all_data
    assert '"state": "completed"' in all_data
    assert '"final": true' in all_data
    assert "streamed result" in all_data

    assert streaming["httpRequest"]["body"] == {"type": "JSON_RPC", "method": "message/stream"}


def test_custom_streaming_method():
    expectations = _dicts(a2a_mock().with_streaming_method("tasks/sendSubscribe"))
    streaming = next((e for e in expectations if "httpSseResponse" in e), None)
    assert streaming is not None
    assert streaming["httpRequest"]["body"]["method"] == "tasks/sendSubscribe"


# --- push notifications ----------------------------------------------------


def test_push_notifications_advertised_and_config_and_delivery_generated():
    expectations = _dicts(a2a_mock().with_push_notifications("http://localhost:1234/callback"))
    # agent card + pushConfig + tasks/send delivery + tasks/get + tasks/cancel
    assert len(expectations) == 5

    body = expectations[0]["httpResponse"]["body"]
    assert '"pushNotifications": true' in body

    has_config_echo = False
    has_forward_delivery = False
    for e in expectations:
        req_body = e["httpRequest"].get("body")
        method = req_body.get("method") if isinstance(req_body, dict) else None
        if method == "tasks/pushNotificationConfig/set":
            has_config_echo = True
        if method == "tasks/send" and "httpOverrideForwardedRequest" in e:
            has_forward_delivery = True
            override = e["httpOverrideForwardedRequest"]
            webhook = override["requestOverride"]
            assert webhook["method"] == "POST"
            assert webhook["path"] == "/callback"
            assert webhook["socketAddress"]["host"] == "localhost"
            assert webhook["socketAddress"]["port"] == 1234
            assert webhook["socketAddress"]["scheme"] == "HTTP"
            response_template = override["responseTemplate"]
            assert response_template["templateType"] == "VELOCITY"
            assert "$!{request.jsonRpcRawId}" in response_template["template"]

    assert has_config_echo
    assert has_forward_delivery


def test_literal_webhook_push_body_not_velocity_escaped():
    expectations = _dicts(
        a2a_mock()
        .with_push_notifications("http://localhost:1234/callback")
        .with_default_task_response("$100 off #sale")
    )
    webhook = None
    for e in expectations:
        if "httpOverrideForwardedRequest" in e:
            webhook = e["httpOverrideForwardedRequest"]["requestOverride"]
    assert webhook is not None
    push_body = webhook["body"]
    if isinstance(push_body, dict):
        push_body = push_body.get("string", "")
    assert "$100 off #sale" in push_body
    assert "esc.d" not in push_body
    assert "esc.h" not in push_body


def test_https_webhook_default_port_and_scheme():
    expectations = _dicts(a2a_mock().with_push_notifications("https://example.com/a2a/push"))
    webhook = None
    for e in expectations:
        if "httpOverrideForwardedRequest" in e:
            webhook = e["httpOverrideForwardedRequest"]["requestOverride"]
    assert webhook is not None
    assert webhook["socketAddress"]["host"] == "example.com"
    assert webhook["socketAddress"]["port"] == 443
    assert webhook["socketAddress"]["scheme"] == "HTTPS"
    assert webhook["secure"] is True
    assert webhook["path"] == "/a2a/push"


def test_apply_to_calls_upsert():
    calls = {}

    class FakeClient:
        def upsert(self, *expectations):
            calls["count"] = len(expectations)
            return list(expectations)

    result = a2a_mock().apply_to(FakeClient())
    assert calls["count"] == 4
    assert len(result) == 4
