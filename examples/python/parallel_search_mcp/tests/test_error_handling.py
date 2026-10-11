"""Test suite for verifying robust error handling (missing fixtures, network errors, malformed responses)."""

from __future__ import annotations

import json
from pathlib import Path
import pytest
from mockserver import Expectation, HttpRequest, HttpResponse, MockServerClient

from parallel_search_mcp.capture import CapturedSession
from parallel_search_mcp.client import ParallelSearchMcpClient
from parallel_search_mcp.config import ClientConfig
from parallel_search_mcp.exceptions import (
    FixtureNotFoundError,
    FixtureValidationError,
    McpConnectionError,
    McpProtocolError,
    McpToolError,
)
from parallel_search_mcp.fixtures_manager import (
    load_fixture,
    validate_fixture_file,
    validate_fixture_integrity,
)


def test_missing_fixture_raises_not_found(tmp_path: Path) -> None:
    """Loading a non-existent fixture path must fail clearly with FixtureNotFoundError."""
    missing_file = tmp_path / "does_not_exist.json"
    with pytest.raises(FixtureNotFoundError, match="Fixture file does not exist"):
        load_fixture(missing_file)


def test_corrupt_json_fixture_raises_validation_error(tmp_path: Path) -> None:
    """A fixture with syntax errors must raise FixtureValidationError."""
    corrupt_file = tmp_path / "corrupt.json"
    corrupt_file.write_text("{ this is not valid json", encoding="utf-8")

    with pytest.raises(FixtureValidationError, match="Invalid JSON"):
        validate_fixture_file(corrupt_file)


def test_incomplete_fixture_raises_validation_error() -> None:
    """A fixture missing required tools or interactions must fail integrity validation."""
    # Missing initialize
    with pytest.raises(FixtureValidationError, match="missing 'initialize'"):
        validate_fixture_integrity(CapturedSession())

    # Missing tools
    with pytest.raises(FixtureValidationError, match="missing 'tools'"):
        validate_fixture_integrity(CapturedSession(initialize={"ok": True}))

    # Missing required tool name
    session = CapturedSession(
        initialize={"ok": True},
        tools=[{"name": "some_other_tool"}],
    )
    with pytest.raises(FixtureValidationError, match="missing required tools"):
        validate_fixture_integrity(session)

    # Missing search content
    session_with_tools = CapturedSession(
        initialize={"ok": True},
        tools=[{"name": "web_search"}, {"name": "web_fetch"}],
        search={},
    )
    with pytest.raises(FixtureValidationError, match="missing valid 'web_search'"):
        validate_fixture_integrity(session_with_tools)


def test_unreachable_endpoint_raises_connection_error() -> None:
    """Connecting to an unreachable port raises McpConnectionError with diagnostic details."""
    config = ClientConfig(base_url="http://127.0.0.1:1/mcp", timeout_seconds=1.0)
    client = ParallelSearchMcpClient(config)

    with pytest.raises(McpConnectionError, match="Failed to connect"):
        client.connect()


def test_server_error_status_code_raises_http_error(
    mockserver_client: MockServerClient,
    replay_client_config: ClientConfig,
) -> None:
    """A 500 error from the endpoint must raise an error indicating the HTTP code."""
    # Register an expectation returning 500
    mockserver_client.when(
        HttpRequest(path="/mcp")
    ).respond(
        HttpResponse(status_code=500, body="Internal Server Error")
    )

    client = ParallelSearchMcpClient(replay_client_config)
    with pytest.raises(McpConnectionError, match="HTTP 500"):
        client.connect()
