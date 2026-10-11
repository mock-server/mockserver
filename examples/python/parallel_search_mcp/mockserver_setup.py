"""MockServer setup helpers for configuring proxy, capture, and verification."""

from __future__ import annotations

from typing import Any

from mockserver import (
    HttpForward,
    HttpOverrideForwardedRequest,
    HttpRequest,
    MockServerClient,
    SocketAddress,
    Times,
)


DEFAULT_UPSTREAM_HOST = "search.parallel.ai"
DEFAULT_UPSTREAM_PORT = 443
DEFAULT_UPSTREAM_SCHEME = "HTTPS"
DEFAULT_MCP_PATH = "/mcp"


def configure_proxy(
    client: MockServerClient,
    upstream_host: str = DEFAULT_UPSTREAM_HOST,
    upstream_port: int = DEFAULT_UPSTREAM_PORT,
    upstream_scheme: str = DEFAULT_UPSTREAM_SCHEME,
    path: str = DEFAULT_MCP_PATH,
) -> None:
    """Configure MockServer to forward all matching requests to the live upstream server.

    Overrides the Host header to match upstream_host so virtual-host routing and
    TLS SNI on upstream services (e.g. Cloudflare) succeed.
    """
    client.when(
        HttpRequest(path=f"{path}.*"),
        times=Times(unlimited=True),
    ).forward(
        HttpOverrideForwardedRequest(
            request_override=HttpRequest().with_header("Host", upstream_host),
            socket_address=SocketAddress(
                host=upstream_host,
                port=upstream_port,
                scheme=upstream_scheme,
            ),
        )
    )


def reset_mockserver(client: MockServerClient) -> None:
    """Clear all active expectations and recorded logs/requests from MockServer."""
    client.reset()


def retrieve_proxied_requests(
    client: MockServerClient,
    path: str = DEFAULT_MCP_PATH,
) -> list[HttpRequest]:
    """Retrieve all requests recorded by MockServer while proxying."""
    return client.retrieve_recorded_requests(HttpRequest(path=f"{path}.*"))
