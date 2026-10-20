"""Tests for the MCP mock builder (WS1.3 parity with Java McpMockBuilder).

These assert the exact serialized expectation JSON so the Python client produces
the same wire format as the Java client.
"""

from mockserver.mcp import _escape_json_path, mcp_mock
from mockserver.models import JsonPathBody


def _dicts(builder):
    return [exp.to_dict() for exp in builder.build()]


def test_minimal_mock_has_three_expectations():
    expectations = _dicts(mcp_mock())
    assert len(expectations) == 3
    methods = [e["httpRequest"]["body"]["method"] for e in expectations]
    assert methods == ["initialize", "ping", "notifications/initialized"]


def test_custom_path_applied_to_all_requests():
    expectations = _dicts(mcp_mock("/custom/mcp"))
    assert len(expectations) == 3
    for e in expectations:
        assert e["httpRequest"]["path"] == "/custom/mcp"


def test_notifications_initialized_uses_static_response():
    expectations = _dicts(mcp_mock())
    notif = expectations[2]
    assert notif["httpRequest"]["body"] == {"type": "JSON_RPC", "method": "notifications/initialized"}
    assert notif["httpResponse"]["statusCode"] == 200
    assert notif["httpResponse"]["body"] == "{}"
    assert notif["httpResponse"]["headers"] == [
        {"name": "Content-Type", "values": ["application/json"]}
    ]


def test_initialize_uses_velocity_template_echoing_id():
    expectations = _dicts(mcp_mock())
    init = expectations[0]
    template = init["httpResponseTemplate"]
    assert template["templateType"] == "VELOCITY"
    assert "$!{request.jsonRpcRawId}" in template["template"]
    assert "statusCode" in template["template"]


def test_register_tool_with_response_exact_wire_json():
    expectations = _dicts(
        mcp_mock()
        .with_tool("get_weather")
        .with_description("Get weather for a city")
        .with_input_schema('{"type": "object", "properties": {"city": {"type": "string"}}}')
        .responding_with("72F and sunny")
        .and_()
    )

    # initialize, ping, notifications/initialized, tools/list, tools/call
    assert len(expectations) == 5

    # --- tools/list expectation (index 3) --------------------------------
    tools_list = expectations[3]
    assert tools_list["httpRequest"] == {
        "method": "POST",
        "path": "/mcp",
        "body": {"type": "JSON_RPC", "method": "tools/list"},
    }
    assert tools_list["httpResponseTemplate"] == {
        "templateType": "VELOCITY",
        "template": (
            '{"statusCode": 200, '
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], '
            '"body": {"jsonrpc": "2.0", "result": '
            '{"tools": [{"name": "get_weather", "description": "Get weather for a city", '
            '"inputSchema": {"type":"object","properties":{"city":{"type":"string"}}}}]}, '
            '"id": $!{request.jsonRpcRawId}}}'
        ),
    }

    # --- tools/call expectation (index 4) --------------------------------
    tools_call = expectations[4]
    assert tools_call["httpRequest"] == {
        "method": "POST",
        "path": "/mcp",
        "body": {
            "type": "JSON_PATH",
            "jsonPath": "$[?(@.method == 'tools/call' && @.params.name == 'get_weather')]",
        },
    }
    assert tools_call["httpResponseTemplate"] == {
        "templateType": "VELOCITY",
        "template": (
            '{"statusCode": 200, '
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], '
            '"body": {"jsonrpc": "2.0", "result": '
            '{"content": [{"type": "text", "text": "72F and sunny"}], "isError": false}, '
            '"id": $!{request.jsonRpcRawId}}}'
        ),
    }


def test_register_resource_exact_wire_json():
    expectations = _dicts(
        mcp_mock()
        .with_resource("config://app")
        .with_name("App Config")
        .with_description("Application configuration")
        .with_mime_type("application/json")
        .with_content('{"debug": true}')
        .and_()
    )

    # initialize, ping, notifications/initialized, resources/list, resources/read
    assert len(expectations) == 5

    # --- resources/list expectation (index 3) ----------------------------
    res_list = expectations[3]
    assert res_list["httpRequest"]["body"] == {"type": "JSON_RPC", "method": "resources/list"}
    assert res_list["httpResponseTemplate"] == {
        "templateType": "VELOCITY",
        "template": (
            '{"statusCode": 200, '
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], '
            '"body": {"jsonrpc": "2.0", "result": '
            '{"resources": [{"uri": "config://app", "name": "App Config", '
            '"description": "Application configuration", "mimeType": "application/json"}]}, '
            '"id": $!{request.jsonRpcRawId}}}'
        ),
    }

    # --- resources/read expectation (index 4) ----------------------------
    res_read = expectations[4]
    assert res_read["httpRequest"]["body"] == {
        "type": "JSON_PATH",
        "jsonPath": "$[?(@.method == 'resources/read' && @.params.uri == 'config://app')]",
    }
    assert res_read["httpResponseTemplate"] == {
        "templateType": "VELOCITY",
        "template": (
            '{"statusCode": 200, '
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], '
            '"body": {"jsonrpc": "2.0", "result": '
            '{"contents": [{"uri": "config://app", "mimeType": "application/json", '
            '"text": "{\\"debug\\": true}"}]}, '
            '"id": $!{request.jsonRpcRawId}}}'
        ),
    }


def test_tool_error_response_flag():
    expectations = _dicts(
        mcp_mock()
        .with_tool("failing_tool")
        .responding_with("Something went wrong", is_error=True)
        .and_()
    )
    tools_call = expectations[-1]
    assert '"isError": true' in tools_call["httpResponseTemplate"]["template"]


def test_velocity_metacharacters_escaped_in_tool():
    expectations = _dicts(
        mcp_mock()
        .with_tool("get_$price")
        .with_description("Gets price with $currency #formatting")
        .responding_with("$100 #result")
        .and_()
    )
    tools_list_template = expectations[3]["httpResponseTemplate"]["template"]
    assert "get_${esc.d}price" in tools_list_template
    assert "${esc.d}currency ${esc.h}formatting" in tools_list_template

    tools_call_template = expectations[4]["httpResponseTemplate"]["template"]
    assert "${esc.d}100 ${esc.h}result" in tools_call_template


def test_backslash_escaped_for_velocity_in_tool():
    expectations = _dicts(mcp_mock().with_tool("escapes").responding_with("a\\$b a\\#b end\\").and_())
    tools_call_template = expectations[4]["httpResponseTemplate"]["template"]
    # JSON-escaped; backslash runs before $ / # / end of value are escaped for Velocity
    assert "a${esc.b}${esc.b}${esc.d}b a${esc.b}${esc.b}${esc.h}b end${esc.b}${esc.b}" in tools_call_template


def test_escape_velocity_handles_trailing_mixed_and_very_long_backslash_runs():
    from mockserver.mcp import _escape_velocity

    from mockserver.a2a import _escape_velocity as _a2a_escape_velocity

    run = "\\" * 100_000
    cases = {
        "end\\\\": r"end${esc.b}${esc.b}",
        r"a\b\$c#": r"a\b${esc.b}${esc.d}c${esc.h}",
        "a\\\n": "a\\\n",
        run + "x": run + "x",
        run + "$": "${esc.b}" * 100_000 + "${esc.d}",
        run: "${esc.b}" * 100_000,
    }
    for value, expected in cases.items():
        assert _escape_velocity(value) == expected, value[:20]
        assert _a2a_escape_velocity(value) == expected, value[:20]


def test_escape_velocity_only_escapes_backslashes_before_metacharacters():
    from mockserver.mcp import _escape_velocity

    from mockserver.a2a import _escape_velocity as _a2a_escape_velocity

    # shared cross-client table, asserted identically in all eight client builders
    cases = {
        r"a\$b": r"a${esc.b}${esc.d}b",
        r"a\#b": r"a${esc.b}${esc.h}b",
        "end\\": r"end${esc.b}",
        r"\\$": r"${esc.b}${esc.b}${esc.d}",
        r"${esc.b}": r"${esc.d}{esc.b}",
        r"é\$ü": r"é${esc.b}${esc.d}ü",
        r"a\nb \"q\"": r"a\nb \"q\"",
        r"\\x": r"\\x",
        "$#": "${esc.d}${esc.h}",
        "": "",
    }
    for value, expected in cases.items():
        assert _escape_velocity(value) == expected, value
        assert _a2a_escape_velocity(value) == expected, value


def test_single_quotes_escaped_in_json_path():
    expectations = _dicts(
        mcp_mock()
        .with_tool("tool'name")
        .responding_with("result")
        .and_()
    )
    body = expectations[4]["httpRequest"]["body"]
    assert body["type"] == "JSON_PATH"
    assert "tool\\'name" in body["jsonPath"]


def test_backslashes_escaped_before_quotes_in_json_path():
    assert _escape_json_path("x\\") == "x\\\\"
    assert _escape_json_path("a\\b") == "a\\\\b"
    assert _escape_json_path("a\\'b") == "a\\\\\\'b"
    assert _escape_json_path("o'brien") == "o\\'brien"


def test_trailing_backslash_escaped_in_json_path():
    expectations = _dicts(
        mcp_mock()
        .with_tool("tool\\")
        .responding_with("result")
        .and_()
    )
    body = expectations[4]["httpRequest"]["body"]
    assert body["jsonPath"] == "$[?(@.method == 'tools/call' && @.params.name == 'tool\\\\')]"


def test_invalid_input_schema_rejected():
    import pytest

    with pytest.raises(ValueError):
        mcp_mock().with_tool("bad_tool").with_input_schema("not valid json {{{").responding_with(
            "result"
        ).and_().build()


def test_special_json_characters_escaped_in_content():
    expectations = _dicts(
        mcp_mock()
        .with_tool("test_tool")
        .responding_with("line1\nline2\ttab\b\fspecial")
        .and_()
    )
    template = expectations[4]["httpResponseTemplate"]["template"]
    assert "line1\\nline2\\ttab\\b\\fspecial" in template


def test_prompt_registration_structure():
    expectations = _dicts(
        mcp_mock()
        .with_prompt("code_review")
        .with_description("Review code changes")
        .with_argument("language", "Programming language", True)
        .responding_with("user", "Please review this code")
        .responding_with("assistant", "I'll review the code now.")
        .and_()
    )
    assert len(expectations) == 5
    prompts_list = expectations[3]["httpResponseTemplate"]["template"]
    assert '"name": "code_review"' in prompts_list
    assert '"required": true' in prompts_list

    prompt_get = expectations[4]
    assert prompt_get["httpRequest"]["body"] == {
        "type": "JSON_PATH",
        "jsonPath": "$[?(@.method == 'prompts/get' && @.params.name == 'code_review')]",
    }
    get_template = prompt_get["httpResponseTemplate"]["template"]
    assert '"role": "user"' in get_template
    assert '"role": "assistant"' in get_template


def test_capability_without_definitions_adds_list_expectation():
    expectations = _dicts(mcp_mock().with_tools_capability())
    assert len(expectations) == 4
    assert expectations[3]["httpRequest"]["body"]["method"] == "tools/list"


def test_all_capabilities_count():
    expectations = _dicts(
        mcp_mock("/mcp")
        .with_server_name("TestServer")
        .with_server_version("2.0.0")
        .with_protocol_version("2025-03-26")
        .with_tool("tool1").with_description("First tool").responding_with("tool1 result").and_()
        .with_tool("tool2").with_description("Second tool").responding_with("tool2 result", True).and_()
        .with_resource("res://data").with_name("Data").with_content("some data").and_()
        .with_prompt("prompt1").with_description("A prompt").responding_with("user", "hello").and_()
    )
    assert len(expectations) == 10


def test_json_path_body_roundtrip():
    body = JsonPathBody(json_path="$.foo")
    assert body.to_dict() == {"type": "JSON_PATH", "jsonPath": "$.foo"}
