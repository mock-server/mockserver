"""Pytest fixtures for Parallel Search MCP example tests.

Requires a real MockServer daemon for integration testing.
Simulated MockServer HTTP handlers (LocalMockServerHandler) have been removed.
"""

from __future__ import annotations

import os
import socket
import sys
from pathlib import Path
from typing import Generator

# Ensure mockserver-client-python and examples/python take precedence on sys.path
_tests_dir = Path(__file__).resolve().parent
_pkg_dir = _tests_dir.parent
_python_examples_dir = _pkg_dir.parent
_examples_dir = _python_examples_dir.parent
_repo_root = _examples_dir.parent
_client_pkg = _repo_root / "mockserver-client-python"

for _p in (_client_pkg, _python_examples_dir, _pkg_dir):
    if _p.is_dir() and str(_p) not in sys.path:
        sys.path.insert(0, str(_p))

import pytest
from mockserver import MockServerClient

MOCK_HOST = os.environ.get("MOCKSERVER_HOST", "localhost")
MOCK_PORT = int(os.environ.get("MOCKSERVER_PORT", "1080"))


def is_mockserver_reachable(host: str = MOCK_HOST, port: int = MOCK_PORT) -> bool:
    """Check if a real MockServer daemon is listening on host:port."""
    try:
        with socket.create_connection((host, port), timeout=1.0):
            return True
    except (OSError, ConnectionRefusedError):
        return False


@pytest.fixture()
def mockserver_client() -> Generator[MockServerClient, None, None]:
    """Yield a connected MockServerClient against a real MockServer daemon.

    If MockServer is not reachable on localhost:1080, skips the test with
    actionable startup instructions.
    """
    if not is_mockserver_reachable():
        pytest.skip(
            f"Real MockServer is not reachable on {MOCK_HOST}:{MOCK_PORT}. "
            "Start one with: docker run --rm -p 1080:1080 mockserver/mockserver"
        )

    with MockServerClient(MOCK_HOST, MOCK_PORT) as client:
        client.reset()
        yield client
