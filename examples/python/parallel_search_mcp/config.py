"""Configuration management for Parallel Search MCP client."""

from __future__ import annotations

import os
import uuid
from dataclasses import dataclass, field


DEFAULT_PARALLEL_MCP_URL = "https://search.parallel.ai/mcp"
DEFAULT_TIMEOUT_SECONDS = 30.0
DEFAULT_MODEL_NAME = "mockserver-mcp-client/1.0"


@dataclass
class ClientConfig:
    """Configuration for connecting to an MCP server via Streamable HTTP."""

    base_url: str = DEFAULT_PARALLEL_MCP_URL
    api_key: str | None = None
    session_id: str = field(default_factory=lambda: uuid.uuid4().hex)
    model_name: str = DEFAULT_MODEL_NAME
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS
    extra_headers: dict[str, str] = field(default_factory=dict)

    @classmethod
    def from_env(
        cls,
        base_url: str | None = None,
        timeout_seconds: float | None = None,
    ) -> ClientConfig:
        """Create configuration reading from environment variables."""
        url = base_url or os.environ.get("PARALLEL_MCP_URL", DEFAULT_PARALLEL_MCP_URL)
        api_key = os.environ.get("PARALLEL_API_KEY")
        session_id = os.environ.get("PARALLEL_SESSION_ID") or uuid.uuid4().hex
        model_name = os.environ.get("PARALLEL_MODEL_NAME", DEFAULT_MODEL_NAME)
        timeout = (
            timeout_seconds
            if timeout_seconds is not None
            else float(os.environ.get("PARALLEL_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS))
        )
        return cls(
            base_url=url,
            api_key=api_key if api_key else None,
            session_id=session_id,
            model_name=model_name,
            timeout_seconds=timeout,
        )

    @classmethod
    def for_mockserver(
        cls,
        host: str = "localhost",
        port: int = 1080,
        path: str = "/mcp",
        timeout_seconds: float = 10.0,
    ) -> ClientConfig:
        """Create configuration targeting a local or remote MockServer instance."""
        url = f"http://{host}:{port}{path}"
        return cls(
            base_url=url,
            api_key=None,  # No credentials needed for local replay
            timeout_seconds=timeout_seconds,
        )
