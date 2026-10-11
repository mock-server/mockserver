#!/usr/bin/env python3
"""MockServer Python client -- Parallel Search MCP capture/replay example.

Demonstrates:
  1. Streamable HTTP connection to Parallel Search MCP (https://search.parallel.ai/mcp).
  2. Discovering 'web_search' and 'web_fetch' tools.
  3. Executing a web search and retrieving structured results.
  4. Executing a page fetch and extracting markdown excerpts.
  5. Capturing live interactions through MockServer's forward proxy.
  6. Replaying captured interactions through MockServer for deterministic offline testing.

Usage:
  # Default: offline replay via MockServer (requires MockServer on localhost:1080)
  python parallel_search_mcp.py --mode=replay

  # Capture real upstream interactions through MockServer proxy into fixture:
  python parallel_search_mcp.py --mode=capture

  # Direct live interaction with Parallel Search MCP:
  python parallel_search_mcp.py --mode=live
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

# Ensure mockserver-client-python and examples/python take precedence on sys.path
_current_dir = Path(__file__).resolve().parent
_examples_dir = _current_dir.parent
_repo_root = _examples_dir.parent.parent
_client_pkg_dir = _repo_root / "mockserver-client-python"

if _client_pkg_dir.is_dir() and str(_client_pkg_dir) not in sys.path:
    sys.path.insert(0, str(_client_pkg_dir))
if str(_examples_dir) not in sys.path:
    sys.path.insert(0, str(_examples_dir))
if str(_current_dir) not in sys.path:
    sys.path.insert(0, str(_current_dir))

from mockserver import MockServerClient

try:
    from .capture import capture_session
    from .client import ParallelSearchMcpClient
    from .config import ClientConfig, DEFAULT_PARALLEL_MCP_URL
    from .fixtures_manager import get_default_fixture_path, save_fixture
    from .mockserver_setup import configure_proxy
    from .replay import configure_replay
except ImportError:
    from parallel_search_mcp.capture import capture_session
    from parallel_search_mcp.client import ParallelSearchMcpClient
    from parallel_search_mcp.config import ClientConfig, DEFAULT_PARALLEL_MCP_URL
    from parallel_search_mcp.fixtures_manager import get_default_fixture_path, save_fixture
    from parallel_search_mcp.mockserver_setup import configure_proxy
    from parallel_search_mcp.replay import configure_replay



DEFAULT_HOST = os.environ.get("MOCKSERVER_HOST", "localhost")
DEFAULT_PORT = int(os.environ.get("MOCKSERVER_PORT", "1080"))


def run_replay(
    host: str = DEFAULT_HOST,
    port: int = DEFAULT_PORT,
    fixture_path: Path | None = None,
) -> int:
    """Configure MockServer with recorded expectations and execute offline replay."""
    fixture = fixture_path or get_default_fixture_path()
    print(f"[Replay] Connecting to MockServer on {host}:{port}...")

    with MockServerClient(host, port) as mock_client:
        mock_client.reset()
        print(f"[Replay] Loading fixture from {fixture}...")
        expectations = configure_replay(mock_client, fixture_path=fixture)
        print(f"[Replay] Registered {len(expectations)} MockServer expectations.")

        # Connect MCP client to local MockServer
        config = ClientConfig.for_mockserver(host=host, port=port)
        mcp_client = ParallelSearchMcpClient(config)

        print("[Replay] Connecting to MCP endpoint via Streamable HTTP...")
        server_info = mcp_client.connect()
        name = server_info.get("serverInfo", {}).get("name", "Unknown")
        version = server_info.get("serverInfo", {}).get("version", "Unknown")
        print(f"[Replay] Handshake successful: Server '{name}' v{version}")

        print("[Replay] Discovering available tools...")
        tools = mcp_client.list_tools()
        print(f"[Replay] Found {len(tools)} tools: {[t.get('name') for t in tools]}")

        # Execute replayed search
        print("\n[Replay] Executing web_search...")
        search_res = mcp_client.search_structured(
            objective="Find MockServer documentation and repository",
            search_queries=["MockServer HTTP mock testing"],
        )
        print(f"[Replay] Search returned {search_res.total_results} results:")
        for idx, item in enumerate(search_res.results[:3], start=1):
            print(f"  {idx}. {item.title or 'No title'}")
            print(f"     URL: {item.url}")
            if item.excerpts:
                snippet = item.excerpts[0][:100].replace("\n", " ")
                print(f"     Snippet: {snippet}...")

        # Execute replayed fetch
        target_url = search_res.urls[0] if search_res.urls else "https://www.mock-server.com"
        print(f"\n[Replay] Executing web_fetch for '{target_url}'...")
        fetch_res = mcp_client.fetch_structured(
            urls=[target_url],
            objective="Extract MockServer core capabilities",
        )
        print(f"[Replay] Page fetch returned {fetch_res.total_results} results:")
        for item in fetch_res.results:
            print(f"  URL: {item.url}")
            print(f"  Title: {item.title or 'N/A'}")
            print(f"  Excerpts count: {len(item.excerpts)}")

        print("\n[Replay] Offline replay completed successfully! No upstream calls made.")
    return 0


def run_capture(
    host: str = DEFAULT_HOST,
    port: int = DEFAULT_PORT,
    output_fixture: Path | None = None,
) -> int:
    """Proxy traffic through MockServer to record live Parallel Search MCP interactions."""
    target_fixture = output_fixture or get_default_fixture_path()
    print(f"[Capture] Setting up MockServer proxy on {host}:{port} -> search.parallel.ai:443...")

    with MockServerClient(host, port) as mock_client:
        mock_client.reset()
        configure_proxy(mock_client)
        print("[Capture] Forward proxy rule registered.")

        # Direct MCP client through MockServer proxy
        config = ClientConfig.for_mockserver(host=host, port=port)
        mcp_client = ParallelSearchMcpClient(config)

        print("[Capture] Executing live interactions through MockServer proxy...")
        session = capture_session(
            mcp_client=mcp_client,
            mockserver_client=mock_client,
        )

        print(f"[Capture] Saving captured session to {target_fixture}...")
        save_fixture(session, target_fixture)
        print(f"[Capture] Fixture successfully recorded with {len(session.tools)} tools.")
    return 0


def run_live() -> int:
    """Execute live search and page fetch directly against https://search.parallel.ai/mcp."""
    print(f"[Live] Connecting directly to {DEFAULT_PARALLEL_MCP_URL}...")
    config = ClientConfig.from_env()
    client = ParallelSearchMcpClient(config)

    server_info = client.connect()
    name = server_info.get("serverInfo", {}).get("name", "Unknown")
    version = server_info.get("serverInfo", {}).get("version", "Unknown")
    print(f"[Live] Connected to: {name} v{version}")

    tools = client.list_tools()
    print(f"[Live] Tools: {[t.get('name') for t in tools]}")

    print("\n[Live] Running search...")
    search_res = client.search_structured(
        objective="Find MockServer repository",
        search_queries=["mockserver monorepo"],
    )
    print(f"[Live] Results count: {search_res.total_results}")
    if search_res.results:
        print(f"[Live] First result: {search_res.results[0].title} - {search_res.results[0].url}")

    print("\n[Live] Running fetch...")
    fetch_res = client.fetch_structured(
        urls=["https://www.mock-server.com"],
        objective="Overview of MockServer",
    )
    print(f"[Live] Fetch results count: {fetch_res.total_results}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Parallel Search MCP capture and replay example with MockServer."
    )
    parser.add_argument(
        "--mode",
        choices=["replay", "capture", "live"],
        default="replay",
        help="Operating mode: 'replay' (offline MockServer), 'capture' (record proxy), 'live' (direct)",
    )
    parser.add_argument(
        "--host",
        default=DEFAULT_HOST,
        help=f"MockServer host (default: {DEFAULT_HOST})",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=DEFAULT_PORT,
        help=f"MockServer port (default: {DEFAULT_PORT})",
    )
    parser.add_argument(
        "--fixture",
        type=Path,
        default=None,
        help="Path to fixture file (defaults to bundled parallel_search_fixtures.json)",
    )

    args = parser.parse_args()

    try:
        if args.mode == "replay":
            return run_replay(host=args.host, port=args.port, fixture_path=args.fixture)
        elif args.mode == "capture":
            return run_capture(host=args.host, port=args.port, output_fixture=args.fixture)
        elif args.mode == "live":
            return run_live()
    except KeyboardInterrupt:
        print("\n[Aborted] Operation cancelled by user.")
        return 130
    except Exception as err:
        print(f"\n[Error] {type(err).__name__}: {err}", file=sys.stderr)
        if "Connection refused" in str(err) or "Failed to connect" in str(err):
            print(
                "\nTip: Ensure MockServer is running on the target port.\n"
                "Start MockServer quickly using Docker:\n"
                "  docker run -d --rm -p 1080:1080 mockserver/mockserver\n",
                file=sys.stderr,
            )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

