#!/usr/bin/env python3
"""MockServer Python client -- Parallel Search MCP capture/replay example.

Demonstrates:
  1. Capturing live MCP (Model Context Protocol) interactions from a remote server
     (Parallel Search at https://search.parallel.ai/mcp) through MockServer's forward proxy.
  2. Recording Streamable HTTP exchanges (initialize, tools/list, web_search, web_fetch).
  3. Replaying the captured session offline against a real MockServer daemon.
  4. Echoing dynamic JSON-RPC request IDs using MockServer's Velocity template engine.

Usage:
  # Offline replay against real MockServer (default, requires MockServer on localhost:1080):
  python parallel_search_mcp.py

  # Or explicitly:
  python parallel_search_mcp.py --mode replay

  # Capture live upstream interactions through MockServer forward proxy:
  python parallel_search_mcp.py --mode capture

  # Direct live interaction with Parallel Search MCP without MockServer:
  python parallel_search_mcp.py --mode live
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

# Ensure mockserver-client-python takes precedence on sys.path
_current_dir = Path(__file__).resolve().parent
_python_examples_dir = _current_dir.parent
_examples_dir = _python_examples_dir.parent
_repo_root = _examples_dir.parent
_client_pkg = _repo_root / "mockserver-client-python"

for _p in (_client_pkg, _python_examples_dir, _current_dir):
    if _p.is_dir() and str(_p) not in sys.path:
        sys.path.insert(0, str(_p))

from mockserver import (
    Expectation,
    HttpOverrideForwardedRequest,
    HttpRequest,
    HttpResponse,
    HttpTemplate,
    JsonPathBody,
    JsonRpcBody,
    MockServerClient,
    SocketAddress,
    Times,
)

DEFAULT_HOST = os.environ.get("MOCKSERVER_HOST", "localhost")
DEFAULT_PORT = int(os.environ.get("MOCKSERVER_PORT", "1080"))
UPSTREAM_URL = "https://search.parallel.ai/mcp"
UPSTREAM_HOST = "search.parallel.ai"
UPSTREAM_PORT = 443

DEFAULT_FIXTURE_PATH = _current_dir / "fixtures" / "parallel_search_fixtures.json"


def is_mockserver_reachable(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT) -> bool:
    """Check if MockServer is listening on host:port."""
    try:
        with socket.create_connection((host, port), timeout=1.0):
            return True
    except (OSError, ConnectionRefusedError):
        return False


def _escape_velocity(val: str) -> str:
    """Escape Velocity template metacharacters ($ and #)."""
    return val.replace("$", "${esc.d}").replace("#", "${esc.h}")


def _velocity_response(result_json: str) -> HttpTemplate:
    """Build a Velocity HTTP template that dynamically echoes the caller's JSON-RPC id."""
    template = (
        '{"statusCode": 200, '
        '"headers": [{"name": "Content-Type", "values": ["application/json"]}], '
        '"body": {"jsonrpc": "2.0", "result": ' + result_json + ', '
        '"id": $!{request.jsonRpcRawId}}}'
    )
    return HttpTemplate(template_type="VELOCITY", template=template)


def send_mcp_request(
    url: str,
    method: str,
    params: dict[str, Any] | None = None,
    req_id: int | str | None = None,
    api_key: str | None = None,
    timeout: float = 20.0,
) -> dict[str, Any]:
    """Send a JSON-RPC 2.0 request over HTTP to an MCP endpoint."""
    payload: dict[str, Any] = {"jsonrpc": "2.0", "method": method}
    if req_id is not None:
        payload["id"] = req_id
    if params is not None:
        payload["params"] = params

    headers: dict[str, str] = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
        "User-Agent": "MockServer-MCP-Client/1.0",
    }
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
        headers["x-api-key"] = api_key

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")

    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            if not raw.strip():
                return {}
            return json.loads(raw)
    except urllib.error.HTTPError as exc:
        err_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} from {url}: {err_body}") from exc


class McpClient:
    """Streamable HTTP client for the Model Context Protocol (MCP)."""

    def __init__(self, endpoint_url: str, api_key: str | None = None) -> None:
        self.endpoint_url = endpoint_url
        self.api_key = api_key
        self._next_id = 1

    def _next_request_id(self) -> int:
        req_id = self._next_id
        self._next_id += 1
        return req_id

    def handshake(self) -> dict[str, Any]:
        """Perform MCP initialize handshake and send notifications/initialized."""
        init_resp = send_mcp_request(
            url=self.endpoint_url,
            method="initialize",
            params={
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {"name": "MockServer-MCP-Example", "version": "1.0.0"},
            },
            req_id=self._next_request_id(),
            api_key=self.api_key,
        )
        # Send initialized notification (no id)
        send_mcp_request(
            url=self.endpoint_url,
            method="notifications/initialized",
            req_id=None,
            api_key=self.api_key,
        )
        return init_resp.get("result", {})

    def list_tools(self) -> list[dict[str, Any]]:
        """Query available MCP tools via tools/list."""
        resp = send_mcp_request(
            url=self.endpoint_url,
            method="tools/list",
            params={},
            req_id=self._next_request_id(),
            api_key=self.api_key,
        )
        return resp.get("result", {}).get("tools", [])

    def search(
        self,
        objective: str = "Find MockServer documentation and repository",
        search_queries: list[str] | None = None,
        req_id: int | str | None = None,
    ) -> dict[str, Any]:
        """Invoke web_search tool via tools/call."""
        queries = search_queries or ["MockServer HTTP mock testing"]
        call_id = req_id if req_id is not None else self._next_request_id()
        resp = send_mcp_request(
            url=self.endpoint_url,
            method="tools/call",
            params={
                "name": "web_search",
                "arguments": {
                    "objective": objective,
                    "search_queries": queries,
                },
            },
            req_id=call_id,
            api_key=self.api_key,
        )
        return resp.get("result", {})

    def fetch(
        self,
        urls: list[str],
        objective: str = "Extract MockServer core capabilities",
        req_id: int | str | None = None,
    ) -> dict[str, Any]:
        """Invoke web_fetch tool via tools/call."""
        call_id = req_id if req_id is not None else self._next_request_id()
        resp = send_mcp_request(
            url=self.endpoint_url,
            method="tools/call",
            params={
                "name": "web_fetch",
                "arguments": {
                    "urls": urls,
                    "objective": objective,
                },
            },
            req_id=call_id,
            api_key=self.api_key,
        )
        return resp.get("result", {})


def load_fixture(path: Path) -> dict[str, Any]:
    """Load and validate an MCP session fixture JSON file."""
    if not path.is_file():
        raise FileNotFoundError(
            f"Fixture file not found: {path}\n"
            f"Run with '--mode capture' to record live upstream interactions, "
            f"or ensure the fixture file exists."
        )
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    for required_key in ("initialize", "tools", "search", "fetch"):
        if required_key not in data:
            raise ValueError(f"Invalid fixture format: missing '{required_key}' key in {path}")
    return data


def save_fixture(data: dict[str, Any], path: Path) -> None:
    """Save an MCP session fixture, creating parent directories if needed."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def build_replay_expectations(fixture: dict[str, Any], path: str = "/mcp") -> list[Expectation]:
    """Build MockServer expectations matching MCP JSON-RPC requests with dynamic ID echoing."""
    expectations: list[Expectation] = []

    # 1. initialize handshake
    init_json = _escape_velocity(json.dumps(fixture["initialize"]))
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonRpcBody(method="initialize"),
            ),
            http_response_template=_velocity_response(init_json),
        )
    )

    # 2. ping
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonRpcBody(method="ping"),
            ),
            http_response_template=_velocity_response("{}"),
        )
    )

    # 3. notifications/initialized (JSON-RPC notification returning 200 OK)
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonRpcBody(method="notifications/initialized"),
            ),
            http_response=HttpResponse(
                status_code=200,
                body="{}",
            ).with_header("Content-Type", "application/json"),
        )
    )

    # 4. tools/list
    tools_json = _escape_velocity(json.dumps({"tools": fixture["tools"]}))
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonRpcBody(method="tools/list"),
            ),
            http_response_template=_velocity_response(tools_json),
        )
    )

    # 5. tools/call: web_search
    search_json = _escape_velocity(json.dumps(fixture["search"]))
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonPathBody(
                    json_path="$[?(@.method == 'tools/call' && @.params.name == 'web_search')]"
                ),
            ),
            http_response_template=_velocity_response(search_json),
        )
    )

    # 6. tools/call: web_fetch
    fetch_json = _escape_velocity(json.dumps(fixture["fetch"]))
    expectations.append(
        Expectation(
            http_request=HttpRequest(
                method="POST",
                path=path,
                body=JsonPathBody(
                    json_path="$[?(@.method == 'tools/call' && @.params.name == 'web_fetch')]"
                ),
            ),
            http_response_template=_velocity_response(fetch_json),
        )
    )

    return expectations


def configure_mockserver_proxy(client: MockServerClient, path: str = "/mcp") -> None:
    """Configure MockServer forward proxy to forward /mcp requests to Parallel Search."""
    client.when(
        HttpRequest(path=f"{path}.*"),
        times=Times(unlimited=True),
    ).forward(
        HttpOverrideForwardedRequest(
            http_request=HttpRequest(
                socket_address=SocketAddress(
                    host=UPSTREAM_HOST,
                    port=UPSTREAM_PORT,
                    scheme="HTTPS",
                ),
            ).with_header("Host", UPSTREAM_HOST),
        )
    )


def run_capture(
    host: str = DEFAULT_HOST,
    port: int = DEFAULT_PORT,
    fixture_path: Path | None = None,
    api_key: str | None = None,
    direct: bool = False,
) -> int:
    """Capture live MCP interactions from Parallel Search and save as a fixture."""
    target_fixture = fixture_path or DEFAULT_FIXTURE_PATH
    key = api_key or os.environ.get("PARALLEL_API_KEY")

    if direct:
        target_url = UPSTREAM_URL
        print(f"[Capture] Connecting directly to live upstream at {target_url}...")
    else:
        if not is_mockserver_reachable(host, port):
            print(
                f"[Error] Real MockServer is not reachable on {host}:{port}.\n"
                f"Start MockServer with:\n"
                f"  docker run --rm -p 1080:1080 mockserver/mockserver\n"
                f"Or pass '--direct' to capture directly from upstream without proxying.",
                file=sys.stderr,
            )
            return 1

        print(f"[Capture] Configuring MockServer forward proxy on {host}:{port} -> {UPSTREAM_HOST}...")
        with MockServerClient(host, port) as client:
            client.reset()
            configure_mockserver_proxy(client)
        target_url = f"http://{host}:{port}/mcp"
        print(f"[Capture] Forward proxy active at {target_url}")

    client = McpClient(target_url, api_key=key)

    # 1. Handshake
    print("[Capture] Performing MCP initialize handshake...")
    init_result = client.handshake()
    server_info = init_result.get("serverInfo", {})
    print(f"  Server: {server_info.get('name')} v{server_info.get('version')}")

    # 2. Tools list
    print("[Capture] Listing available MCP tools...")
    tools = client.list_tools()
    tool_names = [t.get("name") for t in tools]
    print(f"  Discovered tools: {tool_names}")

    # 3. Search
    print("[Capture] Invoking 'web_search' tool...")
    search_result = client.search(
        objective="Find MockServer documentation and repository",
        search_queries=["MockServer HTTP mock testing"],
    )
    print("  Search completed.")

    # Extract target URL from search results for fetch
    target_fetch_url = "https://www.mock-server.com"
    structured = search_result.get("structuredContent", {})
    results_list = structured.get("results", [])
    if results_list and results_list[0].get("url"):
        target_fetch_url = results_list[0]["url"]

    # 4. Fetch
    print(f"[Capture] Invoking 'web_fetch' tool for {target_fetch_url}...")
    fetch_result = client.fetch(
        urls=[target_fetch_url],
        objective="Extract MockServer core capabilities",
    )
    print("  Fetch completed.")

    # Build sanitized fixture (guaranteeing no auth headers or API keys are stored)
    fixture_data = {
        "version": "1.0",
        "initialize": init_result,
        "tools": tools,
        "search": search_result,
        "fetch": fetch_result,
    }

    save_fixture(fixture_data, target_fixture)
    print(f"\n[Capture] Fixture successfully recorded and saved to: {target_fixture}")
    return 0


def run_replay(
    host: str = DEFAULT_HOST,
    port: int = DEFAULT_PORT,
    fixture_path: Path | None = None,
) -> int:
    """Configure MockServer with recorded expectations and run offline replay."""
    target_fixture = fixture_path or DEFAULT_FIXTURE_PATH
    print(f"[Replay] Loading fixture from {target_fixture}...")
    fixture_data = load_fixture(target_fixture)

    if not is_mockserver_reachable(host, port):
        print(
            f"[Error] Real MockServer is not reachable on {host}:{port}.\n"
            f"Start MockServer with:\n"
            f"  docker run --rm -p 1080:1080 mockserver/mockserver",
            file=sys.stderr,
        )
        return 1

    print(f"[Replay] Connecting to MockServer on {host}:{port}...")
    with MockServerClient(host, port) as client:
        client.reset()
        expectations = build_replay_expectations(fixture_data)
        client.upsert(*expectations)
        print(f"[Replay] Registered {len(expectations)} MockServer expectations.")

    local_mcp_url = f"http://{host}:{port}/mcp"
    mcp_client = McpClient(local_mcp_url)

    # 1. Handshake
    print(f"\n[Replay] Handshake with {local_mcp_url}...")
    init_res = mcp_client.handshake()
    server_info = init_res.get("serverInfo", {})
    print(f"  Connected: '{server_info.get('name')}' v{server_info.get('version')}")

    # 2. Discover tools
    print("[Replay] Discovering tools...")
    tools = mcp_client.list_tools()
    tool_names = [t.get("name") for t in tools]
    print(f"  Available tools: {tool_names}")

    # 3. Execute search with a dynamic request ID
    dynamic_search_id = 101
    print(f"\n[Replay] Executing 'web_search' (JSON-RPC ID: {dynamic_search_id})...")
    search_resp = mcp_client.search(
        objective="Find MockServer documentation and repository",
        search_queries=["MockServer HTTP mock testing"],
        req_id=dynamic_search_id,
    )

    results = search_resp.get("structuredContent", {}).get("results", [])
    print(f"  Returned {len(results)} results:")
    for idx, item in enumerate(results[:3], start=1):
        title = item.get("title", "No title")
        url = item.get("url", "No URL")
        snippet = ""
        if item.get("excerpts"):
            snippet = item["excerpts"][0][:120].replace("\n", " ") + "..."
        print(f"    {idx}. {title}")
        print(f"       URL: {url}")
        if snippet:
            print(f"       Snippet: {snippet}")

    # 4. Execute fetch with another dynamic request ID
    dynamic_fetch_id = 102
    target_url = results[0]["url"] if results else "https://www.mock-server.com"
    print(f"\n[Replay] Executing 'web_fetch' for {target_url} (JSON-RPC ID: {dynamic_fetch_id})...")
    fetch_resp = mcp_client.fetch(
        urls=[target_url],
        objective="Extract MockServer core capabilities",
        req_id=dynamic_fetch_id,
    )

    fetch_results = fetch_resp.get("structuredContent", {}).get("results", [])
    print(f"  Page fetch returned {len(fetch_results)} extract result(s):")
    for item in fetch_results:
        print(f"    URL: {item.get('url')}")
        print(f"    Title: {item.get('title') or 'N/A'}")
        print(f"    Excerpts count: {len(item.get('excerpts', []))}")

    print("\n[Replay] Success! Deterministic offline replay completed without upstream network calls or credentials.")
    return 0


def run_live(api_key: str | None = None) -> int:
    """Execute live search and fetch directly against Parallel Search MCP."""
    key = api_key or os.environ.get("PARALLEL_API_KEY")
    print(f"[Live] Connecting directly to {UPSTREAM_URL}...")
    client = McpClient(UPSTREAM_URL, api_key=key)

    init_res = client.handshake()
    server_info = init_res.get("serverInfo", {})
    print(f"  Handshake: {server_info.get('name')} v{server_info.get('version')}")

    tools = client.list_tools()
    print(f"  Tools: {[t.get('name') for t in tools]}")

    print("\n[Live] Invoking 'web_search'...")
    search_res = client.search(
        objective="Find MockServer monorepo",
        search_queries=["mockserver monorepo"],
    )
    results = search_res.get("structuredContent", {}).get("results", [])
    print(f"  Search returned {len(results)} results.")
    if results:
        print(f"  Top result: {results[0].get('title')} ({results[0].get('url')})")

    fetch_url = results[0]["url"] if results else "https://www.mock-server.com"
    print(f"\n[Live] Invoking 'web_fetch' for {fetch_url}...")
    fetch_res = client.fetch(urls=[fetch_url], objective="MockServer overview")
    fetch_results = fetch_res.get("structuredContent", {}).get("results", [])
    print(f"  Fetch returned {len(fetch_results)} result(s).")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Parallel Search MCP capture and replay example with MockServer."
    )
    parser.add_argument(
        "--mode",
        choices=["replay", "capture", "live"],
        default="replay",
        help="Execution mode: 'replay' (offline via MockServer, default), 'capture' (proxy & record), or 'live' (direct).",
    )
    parser.add_argument(
        "--host",
        default=DEFAULT_HOST,
        help=f"MockServer host (default: {DEFAULT_HOST}).",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=DEFAULT_PORT,
        help=f"MockServer port (default: {DEFAULT_PORT}).",
    )
    parser.add_argument(
        "--fixture",
        type=Path,
        default=DEFAULT_FIXTURE_PATH,
        help=f"Fixture file path (default: {DEFAULT_FIXTURE_PATH}).",
    )
    parser.add_argument(
        "--direct",
        action="store_true",
        help="In capture mode, capture directly from upstream without MockServer proxy.",
    )
    parser.add_argument(
        "--api-key",
        default=None,
        help="Optional Parallel API key (defaults to PARALLEL_API_KEY environment variable).",
    )

    args = parser.parse_args()

    try:
        if args.mode == "replay":
            return run_replay(host=args.host, port=args.port, fixture_path=args.fixture)
        elif args.mode == "capture":
            return run_capture(
                host=args.host,
                port=args.port,
                fixture_path=args.fixture,
                api_key=args.api_key,
                direct=args.direct,
            )
        elif args.mode == "live":
            return run_live(api_key=args.api_key)
    except KeyboardInterrupt:
        print("\n[Aborted] Operation cancelled by user.")
        return 130
    except Exception as err:
        print(f"\n[Error] {type(err).__name__}: {err}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
