"""Validation helpers for MCP JSON-RPC protocol messages and tool schemas."""

from __future__ import annotations

from typing import Any

from .exceptions import McpProtocolError, McpToolError


REQUIRED_PARALLEL_TOOLS = {"web_search", "web_fetch"}


def validate_json_rpc_response(
    response: dict[str, Any] | None,
    expected_id: int | str | None = None,
) -> None:
    """Validate JSON-RPC 2.0 structure of a server response."""
    if response is None:
        raise McpProtocolError("Received empty response from server")

    if not isinstance(response, dict):
        raise McpProtocolError(f"Response must be a JSON object, got {type(response).__name__}")

    if response.get("jsonrpc") != "2.0":
        raise McpProtocolError(f"Invalid or missing 'jsonrpc' version: {response.get('jsonrpc')}")

    if "error" in response:
        err = response["error"]
        code = err.get("code", "unknown") if isinstance(err, dict) else "unknown"
        message = err.get("message", str(err)) if isinstance(err, dict) else str(err)
        raise McpProtocolError(f"JSON-RPC error [{code}]: {message}")

    if "result" not in response:
        raise McpProtocolError("JSON-RPC response must contain either 'result' or 'error'")

    if expected_id is not None and "id" in response:
        resp_id = response["id"]
        # Allow string or integer parity for jsonrpc id
        if str(resp_id) != str(expected_id):
            raise McpProtocolError(f"JSON-RPC ID mismatch: expected {expected_id}, got {resp_id}")


def validate_tools_list(tools: list[dict[str, Any]]) -> None:
    """Validate that the server advertises required Parallel Search MCP tools."""
    if not isinstance(tools, list):
        raise McpProtocolError(f"Expected tools list, got {type(tools).__name__}")

    tool_names = {t.get("name") for t in tools if isinstance(t, dict)}
    missing = REQUIRED_PARALLEL_TOOLS - tool_names
    if missing:
        raise McpToolError(
            f"Server is missing required Parallel Search MCP tool(s): {', '.join(sorted(missing))}"
        )


def validate_tool_result(tool_name: str, result: dict[str, Any]) -> None:
    """Validate tool call result structure and error flags."""
    if not isinstance(result, dict):
        raise McpToolError(f"Tool '{tool_name}' result must be a dict, got {type(result).__name__}")

    if result.get("isError"):
        content = result.get("content", [])
        msg = content[0].get("text") if content else "Unknown error"
        raise McpToolError(f"Tool '{tool_name}' returned error: {msg}")

    content = result.get("content")
    if not isinstance(content, list) or len(content) == 0:
        raise McpToolError(f"Tool '{tool_name}' result content is empty or invalid")
