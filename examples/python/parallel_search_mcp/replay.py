"""Replay workflow: configure MockServer expectations to serve recorded MCP responses."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from mockserver import (
    Expectation,
    HttpRequest,
    HttpResponse,
    HttpTemplate,
    JsonPathBody,
    JsonRpcBody,
    MockServerClient,
)

from .capture import CapturedSession
from .fixtures_manager import validate_fixture_file


def _velocity_escape(text: str) -> str:
    """Escape Velocity template characters ($ and #) so they survive rendering."""
    return text.replace("$", "${esc.d}").replace("#", "${esc.h}")


def _velocity_json_rpc_template(result_json: str) -> HttpTemplate:
    """Generate an HttpTemplate echoing back the caller's JSON-RPC ID."""
    template_str = (
        '{"statusCode": 200, '
        '"headers": [{"name": "Content-Type", "values": ["application/json"]}], '
        '"body": {"jsonrpc": "2.0", "result": ' + result_json + ', '
        '"id": $!{request.jsonRpcRawId}}}'
    )
    return HttpTemplate(template_type="VELOCITY", template=template_str)


def build_replay_expectations(
    session: CapturedSession,
    path: str = "/mcp",
) -> list[Expectation]:
    """Generate MockServer expectations that replay a CapturedSession with dynamic ID echoing."""
    expectations: list[Expectation] = []

    # 1. initialize expectation
    init_json = _velocity_escape(json.dumps(session.initialize))
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonRpcBody(method="initialize"),
            ),
            http_response_template=_velocity_json_rpc_template(init_json),
        )
    )

    # 2. ping expectation
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonRpcBody(method="ping"),
            ),
            http_response_template=_velocity_json_rpc_template("{}"),
        )
    )

    # 3. notifications/initialized expectation
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonRpcBody(method="notifications/initialized"),
            ),
            http_response=HttpResponse(
                status_code=200,
                body="{}",
            ).with_header("Content-Type", "application/json"),
        )
    )

    # 4. tools/list expectation
    tools_json = _velocity_escape(json.dumps({"tools": session.tools}))
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonRpcBody(method="tools/list"),
            ),
            http_response_template=_velocity_json_rpc_template(tools_json),
        )
    )

    # 5. web_search tools/call expectation
    search_json = _velocity_escape(json.dumps(session.search))
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonPathBody(
                    json_path="$[?(@.method == 'tools/call' && @.params.name == 'web_search')]"
                ),
            ),
            http_response_template=_velocity_json_rpc_template(search_json),
        )
    )

    # 6. web_fetch tools/call expectation
    fetch_json = _velocity_escape(json.dumps(session.fetch))
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonPathBody(
                    json_path="$[?(@.method == 'tools/call' && @.params.name == 'web_fetch')]"
                ),
            ),
            http_response_template=_velocity_json_rpc_template(fetch_json),
        )
    )

    return expectations


def configure_replay(
    client: MockServerClient,
    fixture_path: Path | str | None = None,
    path: str = "/mcp",
) -> list[Expectation]:
    """Load a fixture and register all replay expectations into MockServer."""
    session = validate_fixture_file(fixture_path)
    expectations = build_replay_expectations(session, path=path)
    client.upsert(*expectations)
    return expectations
