"""Test verifying that replay mode works in strict isolation without Parallel credentials."""

from __future__ import annotations

import os
import pytest
from mockserver import MockServerClient

from parallel_search_mcp.client import ParallelSearchMcpClient
from parallel_search_mcp.config import ClientConfig
from parallel_search_mcp.fixtures_manager import get_default_fixture_path
from parallel_search_mcp.replay import configure_replay
from parallel_search_mcp.tests.conftest import LocalMockServerHandler


def test_replay_operates_without_api_key(
    monkeypatch: pytest.MonkeyPatch,
    mockserver_client: MockServerClient,
    replay_client_config: ClientConfig,
) -> None:
    """Prove that replay mode functions completely without PARALLEL_API_KEY set."""
    # Explicitly clear any environment API keys
    monkeypatch.delenv("PARALLEL_API_KEY", raising=False)
    assert os.environ.get("PARALLEL_API_KEY") is None

    # Verify config has no API key
    assert replay_client_config.api_key is None

    # Configure replay from fixture
    configure_replay(mockserver_client, fixture_path=get_default_fixture_path())

    client = ParallelSearchMcpClient(replay_client_config)

    # 1. Connect
    client.connect()

    # 2. Tools
    tools = client.list_tools()
    assert len(tools) >= 2

    # 3. Search
    search_res = client.search_structured(
        objective="Verify search works without credentials",
        search_queries=["MockServer"],
    )
    assert search_res.total_results > 0

    # 4. Fetch
    fetch_res = client.fetch_structured(
        urls=["https://www.mock-server.com"],
        objective="Verify fetch works without credentials",
    )
    assert fetch_res.total_results > 0

    # 5. Verify no Authorization header was ever sent to the replay server
    with LocalMockServerHandler.lock:
        for recorded in LocalMockServerHandler.received_requests:
            headers = recorded.get("headers", {})
            assert "authorization" not in {k.lower(): v for k, v in headers.items()}
