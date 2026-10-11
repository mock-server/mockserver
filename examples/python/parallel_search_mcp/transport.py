"""Streamable HTTP transport for Model Context Protocol (MCP) JSON-RPC 2.0 communication."""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from typing import Any

from .config import ClientConfig


class TransportError(Exception):
    """Base exception for transport failures."""


class ConnectionError(TransportError):
    """Raised when the connection to the MCP endpoint fails."""


class HttpError(TransportError):
    """Raised when the HTTP response contains an error status code."""

    def __init__(self, status_code: int, message: str, body: str | None = None) -> None:
        super().__init__(f"HTTP {status_code}: {message}")
        self.status_code = status_code
        self.body = body


class StreamableHttpTransport:
    """Implements the MCP Streamable HTTP transport over HTTP POST."""

    def __init__(self, config: ClientConfig) -> None:
        self.config = config
        self._request_id_counter = 0

    def _next_id(self) -> int:
        self._request_id_counter += 1
        return self._request_id_counter

    def send_json_rpc(
        self,
        method: str,
        params: dict[str, Any] | None = None,
        is_notification: bool = False,
        request_id: int | str | None = None,
    ) -> dict[str, Any] | None:
        """Send a JSON-RPC 2.0 request or notification and return the parsed response."""
        payload: dict[str, Any] = {
            "jsonrpc": "2.0",
            "method": method,
        }

        if not is_notification:
            payload["id"] = request_id if request_id is not None else self._next_id()

        if params is not None:
            payload["params"] = params

        data = json.dumps(payload).encode("utf-8")

        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
            "User-Agent": "mockserver-mcp-client/1.0",
        }

        if self.config.api_key:
            headers["Authorization"] = f"Bearer {self.config.api_key}"

        if self.config.extra_headers:
            headers.update(self.config.extra_headers)

        req = urllib.request.Request(
            url=self.config.base_url,
            data=data,
            headers=headers,
            method="POST",
        )

        try:
            with urllib.request.urlopen(req, timeout=self.config.timeout_seconds) as response:
                status = response.status
                raw_body = response.read().decode("utf-8")

                if is_notification:
                    return None

                if status in (202, 204) and not raw_body.strip():
                    return None

                if not raw_body.strip():
                    return {}

                try:
                    return json.loads(raw_body)
                except json.JSONDecodeError as err:
                    raise TransportError(f"Malformed JSON in server response: {err}") from err

        except urllib.error.HTTPError as err:
            err_body = None
            try:
                err_body = err.read().decode("utf-8")
            except Exception:
                pass
            raise HttpError(err.code, err.reason, body=err_body) from err

        except urllib.error.URLError as err:
            raise ConnectionError(f"Failed to connect to {self.config.base_url}: {err.reason}") from err

        except TimeoutError as err:
            raise ConnectionError(f"Connection timed out after {self.config.timeout_seconds}s") from err
