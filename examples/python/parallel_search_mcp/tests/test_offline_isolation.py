"""Test proving offline isolation: no calls to search.parallel.ai occur during replay."""

from __future__ import annotations

import socket
import pytest
from mockserver import MockServerClient

from parallel_search_mcp.client import ParallelSearchMcpClient
from parallel_search_mcp.config import ClientConfig
from parallel_search_mcp.fixtures_manager import get_default_fixture_path
from parallel_search_mcp.replay import configure_replay


def test_replay_makes_zero_upstream_calls(
    monkeypatch: pytest.MonkeyPatch,
    mockserver_client: MockServerClient,
    replay_client_config: ClientConfig,
) -> None:
    """Prove that during replay, all network traffic is served locally and never reaches upstream."""
    orig_getaddrinfo = socket.getaddrinfo

    def guarded_getaddrinfo(host, port, *args, **kwargs):
        if host and "parallel.ai" in host.lower():
            raise AssertionError(f"Unexpected attempt to resolve live upstream host: {host}")
        return orig_getaddrinfo(host, port, *args, **kwargs)

    monkeypatch.setattr(socket, "getaddrinfo", guarded_getaddrinfo)

    # Configure replay expectations on local MockServer
    configure_replay(mockserver_client, fixture_path=get_default_fixture_path())

    client = ParallelSearchMcpClient(replay_client_config)

    # Execute entire suite of operations
    client.connect()
    tools = client.list_tools()
    assert len(tools) >= 2

    search_result = client.search_structured(
        objective="Offline test",
        search_queries=["MockServer offline"],
    )
    assert search_result.total_results > 0

    fetch_result = client.fetch_structured(
        urls=["https://www.mock-server.com"],
        objective="Offline fetch test",
    )
    assert fetch_result.total_results > 0

    # Ensure the client target remained localhost
    assert "127.0.0.1" in client.config.base_url or "localhost" in client.config.base_url
