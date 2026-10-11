"""Exceptions for the Parallel Search MCP client and MockServer capture/replay workflow."""

from __future__ import annotations


class McpError(Exception):
    """Base exception for all MCP-related errors."""


class McpConnectionError(McpError):
    """Raised when connecting to an MCP endpoint fails (e.g. host unreachable, timed out)."""


class McpProtocolError(McpError):
    """Raised when a JSON-RPC 2.0 protocol violation occurs (e.g. malformed body, invalid ID)."""


class McpToolError(McpError):
    """Raised when an MCP tool invocation returns an error or tool execution fails."""


class FixtureError(McpError):
    """Base exception for fixture loading and validation errors."""


class FixtureNotFoundError(FixtureError):
    """Raised when an expected fixture file does not exist."""


class FixtureValidationError(FixtureError):
    """Raised when a fixture file is corrupted, malformed, or missing required interactions."""
