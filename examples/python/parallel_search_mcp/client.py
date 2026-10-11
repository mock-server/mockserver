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
