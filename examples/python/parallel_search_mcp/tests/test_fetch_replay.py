"""Test suite for verifying Parallel Search MCP web_fetch workflow in offline replay mode."""

from __future__ import annotations

import pytest
from mockserver import MockServerClient

from parallel_search_mcp.client import ParallelSearchMcpClient
from parallel_search_mcp.config import ClientConfig
from parallel_search_mcp.fixtures_manager import get_default_fixture_path
from parallel_search_mcp.replay import configure_replay


def test_replayed_fetch_workflow(
    mockserver_client: MockServerClient,
    replay_client_config: ClientConfig,
) -> None:
    """Verify that MockServer serves recorded page fetch expectations offline."""
    fixture_path = get_default_fixture_path()
    configure_replay(mockserver_client, fixture_path=fixture_path)

    client = ParallelSearchMcpClient(replay_client_config)

    # Handshake
    client.connect()

    # Raw fetch call
    raw_fetch = client.fetch(
        urls=["https://www.mock-server.com"],
        objective="Extract MockServer core capabilities",
    )
    assert "content" in raw_fetch
    assert len(raw_fetch["content"]) > 0

    # Structured fetch call
    structured_fetch = client.fetch_structured(
        urls=["https://www.mock-server.com"],
        objective="Extract MockServer core capabilities",
    )
    assert structured_fetch.total_results > 0
    assert structured_fetch.urls == ["https://www.mock-server.com"]

    item = structured_fetch.results[0]
    assert item.url == "https://www.mock-server.com"
    assert len(item.excerpts) > 0
    combined_excerpts = " ".join(item.excerpts).lower()
    assert "mock" in combined_excerpts


def test_fetch_argument_validation(replay_client_config: ClientConfig) -> None:
    """Verify input validation for web_fetch tool inputs."""
    client = ParallelSearchMcpClient(replay_client_config)

    with pytest.raises(ValueError, match="urls list cannot be empty"):
        client.fetch(urls=[])

    with pytest.raises(ValueError, match="supports up to 20 URLs"):
        client.fetch(urls=[f"https://example.com/{i}" for i in range(25)])
