"""Tests for Parallel Search MCP capture and replay example.

Distinguishes between:
  - Unit tests that run without infrastructure.
  - Integration tests that require a real MockServer daemon on localhost:1080.
  - Live upstream tests that verify connection to https://search.parallel.ai/mcp.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

import pytest
from mockserver import MockServerClient

from parallel_search_mcp import (
    DEFAULT_FIXTURE_PATH,
    McpClient,
    build_replay_expectations,
    load_fixture,
    send_mcp_request,
)


# ============================================================================
# Unit Tests (run without infrastructure)
# ============================================================================

def test_fixture_structure_and_no_credentials() -> None:
    """Verify fixture contains necessary MCP exchanges and no credentials or secrets."""
    assert DEFAULT_FIXTURE_PATH.is_file(), f"Fixture missing at {DEFAULT_FIXTURE_PATH}"
    data = load_fixture(DEFAULT_FIXTURE_PATH)

    # Required top-level sections
    assert "initialize" in data
    assert "tools" in data
    assert "search" in data
    assert "fetch" in data

    # Handshake info
    server_info = data["initialize"].get("serverInfo", {})
    assert "Parallel" in server_info.get("name", "")

    # Tools
    tool_names = [t.get("name") for t in data["tools"]]
    assert "web_search" in tool_names
    assert "web_fetch" in tool_names

    # Search results
    search_results = data["search"].get("structuredContent", {}).get("results", [])
    assert len(search_results) > 0

    # Fetch results
    fetch_results = data["fetch"].get("structuredContent", {}).get("results", [])
    assert len(fetch_results) > 0

    # Strict secret and credential scan
    raw_fixture_text = DEFAULT_FIXTURE_PATH.read_text(encoding="utf-8")
    sensitive_patterns = [
        r"sk-ant-[a-zA-Z0-9_\-]+",
        r"sk-proj-[a-zA-Z0-9_\-]+",
        r"bearer\s+[a-zA-Z0-9_\-\.]{20,}",
        r"\"authorization\"\s*:",
        r"\"x-api-key\"\s*:\s*\"[^\"]+\"",
    ]
    for pattern in sensitive_patterns:
        match = re.search(pattern, raw_fixture_text, re.IGNORECASE)
        assert match is None, f"Found sensitive data matching '{pattern}' in fixture: {match.group(0) if match else ''}"


def test_build_replay_expectations_structure() -> None:
    """Verify MockServer expectations use correct matchers and Velocity dynamic ID templates."""
    data = load_fixture(DEFAULT_FIXTURE_PATH)
    expectations = build_replay_expectations(data, path="/mcp")

    # Expect exactly 6 expectations: initialize, ping, notifications/initialized, tools/list, web_search, web_fetch
    assert len(expectations) == 6

    # 1. initialize expectation
    init_exp = expectations[0]
    assert init_exp.http_request.path == "/mcp"
    assert init_exp.http_request.body.method == "initialize"
    assert init_exp.http_response_template is not None
    assert "$!{request.jsonRpcRawId}" in init_exp.http_response_template.template
    assert "Parallel Web Search" in init_exp.http_response_template.template

    # 2. ping expectation
    ping_exp = expectations[1]
    assert ping_exp.http_request.body.method == "ping"
    assert "$!{request.jsonRpcRawId}" in ping_exp.http_response_template.template

    # 3. notifications/initialized (notification returns static 200)
    notif_exp = expectations[2]
    assert notif_exp.http_request.body.method == "notifications/initialized"
    assert notif_exp.http_response.status_code == 200

    # 4. tools/list expectation
    tools_exp = expectations[3]
    assert tools_exp.http_request.body.method == "tools/list"
    assert "$!{request.jsonRpcRawId}" in tools_exp.http_response_template.template
    assert "web_search" in tools_exp.http_response_template.template

    # 5. web_search expectation
    search_exp = expectations[4]
    assert search_exp.http_request.body.to_dict()["type"] == "JSON_PATH"
    assert "web_search" in search_exp.http_request.body.json_path
    assert "$!{request.jsonRpcRawId}" in search_exp.http_response_template.template

    # 6. web_fetch expectation
    fetch_exp = expectations[5]
    assert fetch_exp.http_request.body.to_dict()["type"] == "JSON_PATH"
    assert "web_fetch" in fetch_exp.http_request.body.json_path
    assert "$!{request.jsonRpcRawId}" in fetch_exp.http_response_template.template


def test_missing_fixture_raises_actionable_error(tmp_path: Path) -> None:
    """Verify load_fixture raises a clear FileNotFoundError when the file is missing."""
    missing = tmp_path / "nonexistent.json"
    with pytest.raises(FileNotFoundError, match="Fixture file not found"):
        load_fixture(missing)


# ============================================================================
# Integration Tests (run against real MockServer on localhost:1080)
# ============================================================================

def test_mockserver_replay_integration(mockserver_client: MockServerClient) -> None:
    """Verify replay against a real MockServer daemon with dynamic JSON-RPC IDs."""
    data = load_fixture(DEFAULT_FIXTURE_PATH)
    expectations = build_replay_expectations(data, path="/mcp")
    mockserver_client.upsert(*expectations)

    mcp_url = f"http://{mockserver_client.host}:{mockserver_client.port}/mcp"
    client = McpClient(mcp_url)

    # 1. Handshake
    init_res = client.handshake()
    server_info = init_res.get("serverInfo", {})
    assert "Parallel" in server_info.get("name", "")

    # 2. Tool discovery
    tools = client.list_tools()
    tool_names = [t.get("name") for t in tools]
    assert "web_search" in tool_names
    assert "web_fetch" in tool_names

    # 3. Dynamic search call with explicit ID 777
    search_res = client.search(
        objective="Find MockServer documentation",
        search_queries=["MockServer"],
        req_id=777,
    )
    results = search_res.get("structuredContent", {}).get("results", [])
    assert len(results) > 0

    # 4. Dynamic fetch call with explicit ID 888
    fetch_res = client.fetch(
        urls=["https://www.mock-server.com"],
        objective="Extract capabilities",
        req_id=888,
    )
    fetch_results = fetch_res.get("structuredContent", {}).get("results", [])
    assert len(fetch_results) > 0


# ============================================================================
# Upstream Tests (verify live connectivity when available)
# ============================================================================

def test_live_upstream_handshake() -> None:
    """Verify live Parallel Search MCP server is reachable and answers handshake."""
    client = McpClient("https://search.parallel.ai/mcp")
    try:
        init_res = client.handshake()
    except Exception as exc:
        pytest.skip(f"Live Parallel Search upstream is unavailable: {exc}")

    server_info = init_res.get("serverInfo", {})
    assert "Parallel Web Search MCP Server" == server_info.get("name")
