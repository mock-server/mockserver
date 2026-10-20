"""Fluent builder for mocking an MCP (Model Context Protocol) server.

Mirrors the Java ``org.mockserver.client.McpMockBuilder``. It produces the same
wire-level expectation JSON: a set of HTTP expectations that emulate a Streamable
HTTP MCP server speaking JSON-RPC 2.0.

Each generated expectation matches a JSON-RPC method (``initialize``, ``ping``,
``notifications/initialized``, ``tools/list``, ``tools/call``, ``resources/list``,
``resources/read``, ``prompts/list``, ``prompts/get``) on ``POST <path>`` and
responds with a Velocity template that echoes back the incoming JSON-RPC id via
``$!{request.jsonRpcRawId}``.

Example::

    from mockserver import MockServerClient
    from mockserver.mcp import mcp_mock

    mcp_mock() \
        .with_tool("get_weather") \
            .with_description("Get weather for a city") \
            .with_input_schema('{"type": "object"}') \
            .responding_with("72F and sunny") \
            .and_() \
        .apply_to(client)
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field

from mockserver.models import (
    Expectation,
    HttpRequest,
    HttpResponse,
    HttpTemplate,
    JsonPathBody,
    JsonRpcBody,
)


def _escape_json(value: str | None) -> str:
    """Quote a string the same way Jackson ``writeValueAsString`` does, then
    strip the surrounding quotes — yielding only the escaped inner content."""
    if value is None:
        return ""
    quoted = json.dumps(value)
    return quoted[1:-1]


# group 2 consumes the metacharacter after a backslash run, is empty at the end
# and unset (empty alternative) otherwise, so no lookaround is needed
_VELOCITY_METACHARACTERS = re.compile(r"(\\+)(?:([$#]|\Z)|)|[$#]")
_VELOCITY_METACHARACTER_ESCAPES = {"$": "${esc.d}", "#": "${esc.h}", "": ""}


def _velocity_escape_match(match: re.Match) -> str:
    run, following = match.group(1), match.group(2)
    if run is None:
        return _VELOCITY_METACHARACTER_ESCAPES[match.group(0)]
    if following is None:
        return run
    return "${esc.b}" * len(run) + _VELOCITY_METACHARACTER_ESCAPES[following]


def _escape_velocity(value: str | None) -> str | None:
    """Escape Velocity metacharacters so literal ``$`` / ``#`` survive rendering.

    Velocity consumes backslashes written before a reference or directive, so a
    run of backslashes followed by ``$``, ``#`` or the end of the value is
    escaped too; other backslashes are literal to Velocity and left as-is.
    """
    if value is None:
        return None
    return _VELOCITY_METACHARACTERS.sub(_velocity_escape_match, value)


def _escape_json_path(value: str | None) -> str:
    if value is None:
        return ""
    return value.replace("\\", "\\\\").replace("'", "\\'")


def _validate_and_serialize_json(raw: str) -> str:
    """Parse + re-serialize JSON (compact) to validate it, mirroring the Java
    ``validateAndSerializeJson``. Raises ``ValueError`` for invalid JSON."""
    try:
        parsed = json.loads(raw)
    except (ValueError, TypeError) as exc:
        raise ValueError(f"Invalid JSON for inputSchema: {exc}") from exc
    return json.dumps(parsed, separators=(",", ":"))


def _velocity_json_rpc_response(result_json: str) -> str:
    return (
        '{"statusCode": 200, '
        '"headers": [{"name": "Content-Type", "values": ["application/json"]}], '
        '"body": {"jsonrpc": "2.0", "result": ' + result_json
        + ', "id": $!{request.jsonRpcRawId}}}'
    )


def _template(velocity: str) -> HttpTemplate:
    return HttpTemplate(template_type="VELOCITY", template=velocity)


@dataclass
class _McpToolDefinition:
    name: str
    description: str | None = None
    input_schema: str | None = None
    response_content: str | None = None
    response_is_error: bool = False


@dataclass
class _McpResourceDefinition:
    uri: str
    name: str | None = None
    description: str | None = None
    mime_type: str = "application/json"
    content: str | None = None


@dataclass
class _McpPromptArgument:
    name: str
    description: str | None
    required: bool


@dataclass
class _McpPromptMessage:
    role: str
    text: str


@dataclass
class _McpPromptDefinition:
    name: str
    description: str | None = None
    arguments: list[_McpPromptArgument] = field(default_factory=list)
    messages: list[_McpPromptMessage] = field(default_factory=list)


class McpMockBuilder:
    def __init__(self, path: str = "/mcp") -> None:
        self._path = path
        self._server_name = "MockMCPServer"
        self._server_version = "1.0.0"
        self._protocol_version = "2025-03-26"
        self._tools_capability = False
        self._resources_capability = False
        self._prompts_capability = False
        self._tools: list[_McpToolDefinition] = []
        self._resources: list[_McpResourceDefinition] = []
        self._prompts: list[_McpPromptDefinition] = []

    # --- top-level configuration ---------------------------------------

    def with_server_name(self, name: str) -> McpMockBuilder:
        self._server_name = name
        return self

    def with_server_version(self, version: str) -> McpMockBuilder:
        self._server_version = version
        return self

    def with_protocol_version(self, version: str) -> McpMockBuilder:
        self._protocol_version = version
        return self

    def with_tools_capability(self) -> McpMockBuilder:
        self._tools_capability = True
        return self

    def with_resources_capability(self) -> McpMockBuilder:
        self._resources_capability = True
        return self

    def with_prompts_capability(self) -> McpMockBuilder:
        self._prompts_capability = True
        return self

    def with_tool(self, name: str) -> McpToolBuilder:
        return McpToolBuilder(self, name)

    def with_resource(self, uri: str) -> McpResourceBuilder:
        return McpResourceBuilder(self, uri)

    def with_prompt(self, name: str) -> McpPromptBuilder:
        return McpPromptBuilder(self, name)

    # --- terminal operations -------------------------------------------

    def apply_to(self, client) -> list[Expectation]:
        return client.upsert(*self.build())

    def build(self) -> list[Expectation]:
        expectations: list[Expectation] = [
            self._build_initialize_expectation(),
            self._build_ping_expectation(),
            self._build_notifications_initialized_expectation(),
        ]

        if self._tools_capability or self._tools:
            expectations.append(self._build_tools_list_expectation())
        for tool in self._tools:
            expectations.append(self._build_tools_call_expectation(tool))

        if self._resources_capability or self._resources:
            expectations.append(self._build_resources_list_expectation())
        for resource in self._resources:
            expectations.append(self._build_resources_read_expectation(resource))

        if self._prompts_capability or self._prompts:
            expectations.append(self._build_prompts_list_expectation())
        for prompt in self._prompts:
            expectations.append(self._build_prompts_get_expectation(prompt))

        return expectations

    # --- expectation builders ------------------------------------------

    def _json_rpc_request(self, method: str) -> HttpRequest:
        return HttpRequest(method="POST", path=self._path, body=JsonRpcBody(method=method))

    def _json_path_request(self, json_path: str) -> HttpRequest:
        return HttpRequest(method="POST", path=self._path, body=JsonPathBody(json_path=json_path))

    def _build_initialize_expectation(self) -> Expectation:
        caps_parts: list[str] = []
        if self._tools_capability or self._tools:
            caps_parts.append('"tools": {"listChanged": false}')
        if self._resources_capability or self._resources:
            caps_parts.append('"resources": {"subscribe": false, "listChanged": false}')
        if self._prompts_capability or self._prompts:
            caps_parts.append('"prompts": {"listChanged": false}')
        caps = "{" + ", ".join(caps_parts) + "}"

        result_json = (
            '{"protocolVersion": "' + _escape_velocity(_escape_json(self._protocol_version)) + '", '
            '"capabilities": ' + caps + ', '
            '"serverInfo": {"name": "' + _escape_velocity(_escape_json(self._server_name))
            + '", "version": "' + _escape_velocity(_escape_json(self._server_version)) + '"}}'
        )

        return Expectation(
            http_request=self._json_rpc_request("initialize"),
            http_response_template=_template(_velocity_json_rpc_response(result_json)),
        )

    def _build_ping_expectation(self) -> Expectation:
        return Expectation(
            http_request=self._json_rpc_request("ping"),
            http_response_template=_template(_velocity_json_rpc_response("{}")),
        )

    def _build_notifications_initialized_expectation(self) -> Expectation:
        return Expectation(
            http_request=self._json_rpc_request("notifications/initialized"),
            http_response=HttpResponse(
                status_code=200,
                body="{}",
            ).with_header("Content-Type", "application/json"),
        )

    def _build_tools_list_expectation(self) -> Expectation:
        items: list[str] = []
        for tool in self._tools:
            parts = ['{"name": "' + _escape_velocity(_escape_json(tool.name)) + '"']
            if tool.description is not None:
                parts.append(', "description": "' + _escape_velocity(_escape_json(tool.description)) + '"')
            if tool.input_schema is not None:
                parts.append(', "inputSchema": ' + _escape_velocity(_validate_and_serialize_json(tool.input_schema)))
            parts.append("}")
            items.append("".join(parts))
        tools_json = "[" + ", ".join(items) + "]"

        return Expectation(
            http_request=self._json_rpc_request("tools/list"),
            http_response_template=_template(
                _velocity_json_rpc_response('{"tools": ' + tools_json + "}")
            ),
        )

    def _build_tools_call_expectation(self, tool: _McpToolDefinition) -> Expectation:
        json_path = "$[?(@.method == 'tools/call' && @.params.name == '" + _escape_json_path(tool.name) + "')]"
        content = _escape_velocity(_escape_json(tool.response_content)) if tool.response_content is not None else ""
        is_error = "true" if tool.response_is_error else "false"
        result_json = '{"content": [{"type": "text", "text": "' + content + '"}], "isError": ' + is_error + "}"

        return Expectation(
            http_request=self._json_path_request(json_path),
            http_response_template=_template(_velocity_json_rpc_response(result_json)),
        )

    def _build_resources_list_expectation(self) -> Expectation:
        items: list[str] = []
        for resource in self._resources:
            parts = ['{"uri": "' + _escape_velocity(_escape_json(resource.uri)) + '"']
            if resource.name is not None:
                parts.append(', "name": "' + _escape_velocity(_escape_json(resource.name)) + '"')
            if resource.description is not None:
                parts.append(', "description": "' + _escape_velocity(_escape_json(resource.description)) + '"')
            if resource.mime_type is not None:
                parts.append(', "mimeType": "' + _escape_velocity(_escape_json(resource.mime_type)) + '"')
            parts.append("}")
            items.append("".join(parts))
        resources_json = "[" + ", ".join(items) + "]"

        return Expectation(
            http_request=self._json_rpc_request("resources/list"),
            http_response_template=_template(
                _velocity_json_rpc_response('{"resources": ' + resources_json + "}")
            ),
        )

    def _build_resources_read_expectation(self, resource: _McpResourceDefinition) -> Expectation:
        json_path = "$[?(@.method == 'resources/read' && @.params.uri == '" + _escape_json_path(resource.uri) + "')]"
        content = _escape_velocity(_escape_json(resource.content)) if resource.content is not None else ""
        mime_type = resource.mime_type if resource.mime_type is not None else "application/json"
        result_json = (
            '{"contents": [{"uri": "' + _escape_velocity(_escape_json(resource.uri)) + '", '
            '"mimeType": "' + _escape_velocity(_escape_json(mime_type)) + '", '
            '"text": "' + content + '"}]}'
        )

        return Expectation(
            http_request=self._json_path_request(json_path),
            http_response_template=_template(_velocity_json_rpc_response(result_json)),
        )

    def _build_prompts_list_expectation(self) -> Expectation:
        items: list[str] = []
        for prompt in self._prompts:
            parts = ['{"name": "' + _escape_velocity(_escape_json(prompt.name)) + '"']
            if prompt.description is not None:
                parts.append(', "description": "' + _escape_velocity(_escape_json(prompt.description)) + '"')
            if prompt.arguments:
                arg_items: list[str] = []
                for arg in prompt.arguments:
                    arg_parts = ['{"name": "' + _escape_velocity(_escape_json(arg.name)) + '"']
                    if arg.description is not None:
                        arg_parts.append(', "description": "' + _escape_velocity(_escape_json(arg.description)) + '"')
                    arg_parts.append(', "required": ' + ("true" if arg.required else "false"))
                    arg_parts.append("}")
                    arg_items.append("".join(arg_parts))
                parts.append(', "arguments": [' + ", ".join(arg_items) + "]")
            parts.append("}")
            items.append("".join(parts))
        prompts_json = "[" + ", ".join(items) + "]"

        return Expectation(
            http_request=self._json_rpc_request("prompts/list"),
            http_response_template=_template(
                _velocity_json_rpc_response('{"prompts": ' + prompts_json + "}")
            ),
        )

    def _build_prompts_get_expectation(self, prompt: _McpPromptDefinition) -> Expectation:
        json_path = "$[?(@.method == 'prompts/get' && @.params.name == '" + _escape_json_path(prompt.name) + "')]"
        msg_items: list[str] = []
        for msg in prompt.messages:
            msg_items.append(
                '{"role": "' + _escape_velocity(_escape_json(msg.role)) + '", '
                '"content": {"type": "text", "text": "' + _escape_velocity(_escape_json(msg.text)) + '"}}'
            )
        messages_json = "[" + ", ".join(msg_items) + "]"
        result_json = '{"messages": ' + messages_json + "}"

        return Expectation(
            http_request=self._json_path_request(json_path),
            http_response_template=_template(_velocity_json_rpc_response(result_json)),
        )


class McpToolBuilder:
    def __init__(self, parent: McpMockBuilder, name: str) -> None:
        self._parent = parent
        self._tool = _McpToolDefinition(name=name)

    def with_description(self, description: str) -> McpToolBuilder:
        self._tool.description = description
        return self

    def with_input_schema(self, json_schema: str) -> McpToolBuilder:
        self._tool.input_schema = json_schema
        return self

    def responding_with(self, text_content: str, is_error: bool = False) -> McpToolBuilder:
        self._tool.response_content = text_content
        self._tool.response_is_error = is_error
        return self

    def and_(self) -> McpMockBuilder:
        self._parent._tools.append(self._tool)
        self._parent._tools_capability = True
        return self._parent


class McpResourceBuilder:
    def __init__(self, parent: McpMockBuilder, uri: str) -> None:
        self._parent = parent
        self._resource = _McpResourceDefinition(uri=uri)

    def with_name(self, name: str) -> McpResourceBuilder:
        self._resource.name = name
        return self

    def with_description(self, description: str) -> McpResourceBuilder:
        self._resource.description = description
        return self

    def with_mime_type(self, mime_type: str) -> McpResourceBuilder:
        self._resource.mime_type = mime_type
        return self

    def with_content(self, content: str) -> McpResourceBuilder:
        self._resource.content = content
        return self

    def and_(self) -> McpMockBuilder:
        self._parent._resources.append(self._resource)
        self._parent._resources_capability = True
        return self._parent


class McpPromptBuilder:
    def __init__(self, parent: McpMockBuilder, name: str) -> None:
        self._parent = parent
        self._prompt = _McpPromptDefinition(name=name)

    def with_description(self, description: str) -> McpPromptBuilder:
        self._prompt.description = description
        return self

    def with_argument(self, name: str, description: str | None, required: bool) -> McpPromptBuilder:
        self._prompt.arguments.append(_McpPromptArgument(name=name, description=description, required=required))
        return self

    def responding_with(self, role: str, text_content: str) -> McpPromptBuilder:
        self._prompt.messages.append(_McpPromptMessage(role=role, text=text_content))
        return self

    def and_(self) -> McpMockBuilder:
        self._parent._prompts.append(self._prompt)
        self._parent._prompts_capability = True
        return self._parent


def mcp_mock(path: str = "/mcp") -> McpMockBuilder:
    """Create a new MCP mock builder. ``path`` defaults to ``/mcp``."""
    return McpMockBuilder(path)
