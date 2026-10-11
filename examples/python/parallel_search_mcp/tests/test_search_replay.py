"""Test suite for verifying Parallel Search MCP search workflow in offline replay mode."""

from __future__ import annotations

import pytest
from mockserver import MockServerClient

from parallel_search_mcp.client import ParallelSearchMcpClient
from parallel_search_mcp.config import ClientConfig
from parallel_search_mcp.fixtures_manager import get_default_fixture_path
from parallel_search_mcp.replay import configure_replay


def test_replayed_search_workflow(
    mockserver_client: MockServerClient,
    replay_client_config: ClientConfig,
) -> None:
    """Verify that MockServer serves recorded search expectations offline."""
    fixture_path = get_default_fixture_path()
    expectations = configure_replay(mockserver_client, fixture_path=fixture_path)
    assert len(expectations) >= 5

    client = ParallelSearchMcpClient(replay_client_config)

    # 1. Verify handshake
    handshake = client.connect()
    assert handshake.get("protocolVersion") == "2024-11-05"
    server_info = handshake.get("serverInfo", {})
    assert "Parallel" in server_info.get("name", "")

    # 2. Verify tool discovery
    tools = client.list_tools()
    tool_names = [t.get("name") for t in tools]
    assert "web_search" in tool_names
    assert "web_fetch" in tool_names

    # 3. Verify search execution
    raw_search = client.search(
        objective="Find MockServer documentation and repository",
        search_queries=["MockServer HTTP mock testing"],
    )
    assert "content" in raw_search
    assert len(raw_search["content"]) > 0

    # 4. Verify structured search results
    structured_search = client.search_structured(
        objective="Find MockServer documentation and repository",
        search_queries=["MockServer HTTP mock testing"],
    )
    assert structured_search.total_results > 0
    assert any("mock" in url.lower() for url in structured_search.urls)

    first_item = structured_search.results[0]
    assert first_item.url.startswith("http")
    assert len(first_item.excerpts) > 0


def test_replayed_search_dynamic_json_rpc_id(
    mockserver_client: MockServerClient,
    replay_client_config: ClientConfig,
) -> None:
    """Verify that MockServer's Velocity template correctly echoes dynamic JSON-RPC IDs."""
    fixture_path = get_default_fixture_path()
    configure_replay(mockserver_client, fixture_path=fixture_path)

    client = ParallelSearchMcpClient(replay_client_config)

    # Send search with explicit custom integer and string request IDs
    res1 = client.transport.send_json_rpc(
        "tools/call",
        params={
            "name": "web_search",
            "arguments": {
                "objective": "Find MockServer documentation",
                "search_queries": ["MockServer testing"],
            },
        },
        request_id=98765,
    )
    assert res1 is not None
    assert res1.get("id") == 98765
    assert "result" in res1
    assert "content" in res1["result"]
