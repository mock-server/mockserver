"""Capture workflow for recording Parallel Search MCP interactions via MockServer."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any

from mockserver import HttpRequest, MockServerClient

from .client import ParallelSearchMcpClient
from .config import ClientConfig
from .mockserver_setup import configure_proxy, reset_mockserver


DEFAULT_SEARCH_OBJECTIVE = "Find MockServer documentation and repository"
DEFAULT_SEARCH_QUERIES = ["MockServer HTTP mock testing"]
DEFAULT_FETCH_URLS = ["https://www.mock-server.com"]
DEFAULT_FETCH_OBJECTIVE = "Extract MockServer core capabilities"


@dataclass
class CapturedInteraction:
    """A single captured MCP exchange (method, request params, and result)."""

    method: str
    params: dict[str, Any] | None = None
    result: dict[str, Any] | None = None
    tool_name: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "method": self.method,
            "params": self.params,
            "result": self.result,
            "tool_name": self.tool_name,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> CapturedInteraction:
        return cls(
            method=data["method"],
            params=data.get("params"),
            result=data.get("result"),
            tool_name=data.get("tool_name"),
        )


@dataclass
class CapturedSession:
    """Complete collection of captured interactions from an MCP workflow session."""

    initialize: dict[str, Any] = field(default_factory=dict)
    tools: list[dict[str, Any]] = field(default_factory=list)
    search: dict[str, Any] = field(default_factory=dict)
    fetch: dict[str, Any] = field(default_factory=dict)
    raw_expectations: list[dict[str, Any]] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "version": "1.0",
            "initialize": self.initialize,
            "tools": self.tools,
            "search": self.search,
            "fetch": self.fetch,
            "raw_expectations": self.raw_expectations,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> CapturedSession:
        return cls(
            initialize=data.get("initialize", {}),
            tools=data.get("tools", []),
            search=data.get("search", {}),
            fetch=data.get("fetch", {}),
            raw_expectations=data.get("raw_expectations", []),
        )


def capture_session(
    mcp_client: ParallelSearchMcpClient,
    mockserver_client: MockServerClient | None = None,
    search_objective: str = DEFAULT_SEARCH_OBJECTIVE,
    search_queries: list[str] | None = None,
    fetch_urls: list[str] | None = None,
    fetch_objective: str | None = DEFAULT_FETCH_OBJECTIVE,
) -> CapturedSession:
    """Execute live MCP operations and capture all exchanged requests and responses.

    If mockserver_client is provided, also retrieves the recorded expectations
    from MockServer's internal recording log.
    """
    queries = search_queries or DEFAULT_SEARCH_QUERIES
    urls = fetch_urls or DEFAULT_FETCH_URLS

    # 1. Handshake
    init_result = mcp_client.connect()

    # 2. Tool discovery
    tools_list = mcp_client.list_tools(validate_required=True)

    # 3. Web Search
    search_result = mcp_client.search(
        objective=search_objective,
        search_queries=queries,
    )

    # 4. Web Fetch
    fetch_result = mcp_client.fetch(
        urls=urls,
        objective=fetch_objective,
    )

    raw_exps: list[dict[str, Any]] = []
    if mockserver_client is not None:
        try:
            recorded = mockserver_client.retrieve_recorded_expectations(HttpRequest(path="/mcp.*"))
            raw_exps = [exp.to_dict() for exp in recorded]
        except Exception:
            # Fallback if recorded expectations format differs
            pass

    return CapturedSession(
        initialize=init_result,
        tools=tools_list,
        search=search_result,
        fetch=fetch_result,
        raw_expectations=raw_exps,
    )
