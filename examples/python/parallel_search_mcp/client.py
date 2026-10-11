"""High-level MCP client for Parallel Search MCP server."""

from __future__ import annotations

from typing import Any

from .config import ClientConfig
from .transport import StreamableHttpTransport


class ParallelSearchMcpClient:
    """Client for discovering and executing Parallel Search MCP tools."""

    def __init__(self, config: ClientConfig | None = None) -> None:
        self.config = config or ClientConfig()
        self.transport = StreamableHttpTransport(self.config)
        self.server_info: dict[str, Any] | None = None
        self.protocol_version: str | None = None
        self.capabilities: dict[str, Any] | None = None

    def initialize(self, protocol_version: str = "2024-11-05") -> dict[str, Any]:
        """Perform the MCP initialize handshake call."""
        params = {
            "protocolVersion": protocol_version,
            "capabilities": {},
            "clientInfo": {
                "name": "mockserver-parallel-mcp-client",
                "version": "1.0.0",
            },
        }
        response = self.transport.send_json_rpc("initialize", params=params)
        if not response or "result" not in response:
            error = response.get("error") if response else "Empty response"
            raise RuntimeError(f"MCP initialization failed: {error}")

        result = response["result"]
        self.protocol_version = result.get("protocolVersion")
        self.server_info = result.get("serverInfo", {})
        self.capabilities = result.get("capabilities", {})
        return result

    def send_initialized_notification(self) -> None:
        """Send the post-initialization notification to confirm readiness."""
        self.transport.send_json_rpc(
            "notifications/initialized",
            params=None,
            is_notification=True,
        )

    def connect(self, protocol_version: str = "2024-11-05") -> dict[str, Any]:
        """Convenience method to execute full initialize handshake and notification."""
        result = self.initialize(protocol_version=protocol_version)
        self.send_initialized_notification()
        return result

    def list_tools(self) -> list[dict[str, Any]]:
        """Query available tools from the server via tools/list."""
        response = self.transport.send_json_rpc("tools/list", params={})
        if not response or "result" not in response:
            error = response.get("error") if response else "Empty response"
            raise RuntimeError(f"Failed to list tools: {error}")

        return response["result"].get("tools", [])

    def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        """Invoke a tool by name with arguments via tools/call."""
        params = {
            "name": name,
            "arguments": arguments,
        }
        response = self.transport.send_json_rpc("tools/call", params=params)
        if not response or "result" not in response:
            error = response.get("error") if response else "Empty response"
            raise RuntimeError(f"Tool invocation '{name}' failed: {error}")

        result = response["result"]
        if result.get("isError"):
            content = result.get("content", [])
            msg = content[0].get("text") if content else "Tool reported error"
            raise RuntimeError(f"Tool '{name}' execution error: {msg}")

        return result

    def search(
        self,
        objective: str,
        search_queries: list[str],
        session_id: str | None = None,
        model_name: str | None = None,
    ) -> dict[str, Any]:
        """Execute a web search using the Parallel Search MCP 'web_search' tool."""
        if not search_queries:
            raise ValueError("search_queries must contain at least one query")

        args: dict[str, Any] = {
            "objective": objective,
            "search_queries": search_queries,
            "session_id": session_id or self.config.session_id,
        }
        m_name = model_name or self.config.model_name
        if m_name:
            args["model_name"] = m_name

        return self.call_tool("web_search", arguments=args)

    def fetch(
        self,
        urls: list[str],
        objective: str | None = None,
        search_queries: list[str] | None = None,
        full_content: bool = False,
        session_id: str | None = None,
        model_name: str | None = None,
    ) -> dict[str, Any]:
        """Execute a web page extraction using the Parallel Search MCP 'web_fetch' tool."""
        if not urls:
            raise ValueError("urls list cannot be empty")
        if len(urls) > 20:
            raise ValueError("web_fetch supports up to 20 URLs per request")

        args: dict[str, Any] = {
            "urls": urls,
            "full_content": full_content,
            "session_id": session_id or self.config.session_id,
        }
        if objective is not None:
            args["objective"] = objective
        if search_queries is not None:
            args["search_queries"] = search_queries
        m_name = model_name or self.config.model_name
        if m_name:
            args["model_name"] = m_name

        return self.call_tool("web_fetch", arguments=args)
