#!/usr/bin/env python3
"""MockServer Python client -- capture and replay a remote MCP server.

Records an MCP (Model Context Protocol) client's JSON-RPC calls to the live
Parallel Search MCP server (https://search.parallel.ai/mcp) through MockServer,
then replays them offline from expectations that match on the JSON-RPC method
and echo the caller's JSON-RPC id with a Velocity template.

  python parallel_search_mcp.py                    # replay the shared fixture (offline)
  MCP_CAPTURE=true python parallel_search_mcp.py   # capture live, then replay the capture

Run against a MockServer on localhost:1080 (override with MOCKSERVER_HOST /
MOCKSERVER_PORT). An optional PARALLEL_API_KEY is sent upstream during capture.
"""

import json
import os
import urllib.request
from pathlib import Path

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
)

MOCK_HOST = os.environ.get("MOCKSERVER_HOST", "localhost")
MOCK_PORT = int(os.environ.get("MOCKSERVER_PORT", "1080"))
MCP_URL = f"http://{MOCK_HOST}:{MOCK_PORT}/mcp"
UPSTREAM_HOST = "search.parallel.ai"
FIXTURE = Path(__file__).resolve().parent.parent.parent / "json" / "parallel_search_mcp" / "replay_expectations.json"


def check(condition, message):
    if not condition:
        raise SystemExit(f"FAILED: {message}")


class McpClient:
    """Minimal MCP Streamable HTTP client: one JSON-RPC message per POST."""

    def __init__(self, url, api_key=None):
        self.url, self.api_key, self.next_id = url, api_key, 1

    def send(self, method, params=None, notification=False):
        message = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            message["params"] = params
        if not notification:
            message["id"] = self.next_id
            self.next_id += 1
        # an explicit User-Agent: Parallel's CDN rejects some library defaults (e.g. Python-urllib)
        headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream",
                   "User-Agent": "mockserver-example/1.0"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        request = urllib.request.Request(self.url, json.dumps(message).encode(), headers, method="POST")
        with urllib.request.urlopen(request, timeout=120) as response:
            body = response.read()
        if notification:
            return None
        reply = json.loads(body)
        check(reply.get("id") == message["id"], f"{method}: id {reply.get('id')} != {message['id']}")
        return reply["result"]

    def call_tool(self, name, arguments):
        result = self.send("tools/call", {"name": name, "arguments": arguments})
        return json.loads(result["content"][0]["text"])  # Parallel returns its payload as JSON text


def run_session(mcp):
    """The application under test: initialize, list tools, search, then fetch."""
    init = mcp.send("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                                   "clientInfo": {"name": "mockserver-example", "version": "1.0.0"}})
    mcp.send("notifications/initialized", notification=True)
    tools = [tool["name"] for tool in mcp.send("tools/list", {})["tools"]]
    search = mcp.call_tool("web_search", {"objective": "What is MockServer?",
                                          "search_queries": ["MockServer mock HTTP server"]})
    fetch = mcp.call_tool("web_fetch", {"urls": ["https://www.mock-server.com/"],
                                        "objective": "What is MockServer?"})
    return init["serverInfo"]["name"], tools, search["results"], fetch["results"]


def body_json(body):
    """MockServer records a JSON body as a string, or as {"json": ...} / {"string": ...}."""
    if isinstance(body, dict):
        body = body.get("json", body.get("string", body))
    return json.loads(body) if isinstance(body, str) else body


def replay_expectation(recorded):
    """Turn one recorded JSON-RPC exchange into a replay expectation that echoes the caller's id."""
    request_body = body_json(recorded["httpRequest"]["body"])
    status_code = recorded["httpResponse"].get("statusCode", 200)
    response_body = body_json(recorded["httpResponse"].get("body"))
    method = request_body["method"]
    if method == "tools/call":
        tool = request_body["params"]["name"]
        matcher = JsonPathBody(json_path=f"$[?(@.method == 'tools/call' && @.params.name == '{tool}')]")
    else:
        matcher = JsonRpcBody(method=method)
    request = HttpRequest(method="POST", path="/mcp", body=matcher)
    if not response_body:  # a notification: replay the bare status (202 Accepted)
        return Expectation(http_request=request, http_response=HttpResponse(status_code=status_code))
    key = "result" if "result" in response_body else "error"
    # escape Velocity's $, # and \ so recorded text (e.g. markdown "#") renders literally;
    # "\" goes last because the $ and # replacements contain none, while its own contains a "$"
    recorded = (json.dumps(response_body[key], separators=(",", ":"))
                .replace("$", "${esc.d}").replace("#", "${esc.h}").replace("\\", "${esc.b}"))
    template = ('{"statusCode": %d, "headers": [{"name": "Content-Type", "values": ["application/json"]}], '
                '"body": {"jsonrpc": "2.0", "id": $!{request.jsonRpcRawId}, "%s": %s}}') % (status_code, key, recorded)
    return Expectation(http_request=request, http_response_template=HttpTemplate(template_type="VELOCITY", template=template))


def capture(client):
    client.reset()
    # keep credentials (e.g. the forwarded Authorization header) out of MockServer's own log
    client.update_configuration('{"redactSecretsInLog": true}')
    client.when(HttpRequest(path="/mcp")).forward(HttpOverrideForwardedRequest(
        http_request=HttpRequest(socket_address=SocketAddress(host=UPSTREAM_HOST, port=443, scheme="HTTPS"))
        .with_header("Host", UPSTREAM_HOST)))
    print(f"capture: forwarding /mcp to https://{UPSTREAM_HOST}/mcp")
    server, tools, search, fetch = run_session(McpClient(MCP_URL, os.environ.get("PARALLEL_API_KEY")))
    print(f"capture: {server}, tools {tools}, {len(search)} search results, fetched {fetch[0]['url']}")
    # MockServer records every proxied exchange as an expectation; rewrite each one for replay
    recorded = client.retrieve_recorded_expectations(HttpRequest(path="/mcp"))
    expectations = [replay_expectation(r.to_dict()) for r in recorded]
    print(f"capture: recorded {len(recorded)} exchanges -> {len(expectations)} replay expectations")
    return expectations


def replay(client, expectations, source):
    client.reset()  # drops the forward expectation and the recorded log
    client.upsert(*expectations)
    print(f"replay: loaded {len(expectations)} expectations from {source}")
    server, tools, search, fetch = run_session(McpClient(MCP_URL))
    check({"web_search", "web_fetch"} <= set(tools), f"tools/list returned {tools}")
    check(search and all(result["url"] for result in search), "web_search returned no results")
    check(fetch and fetch[0]["excerpts"][0], "web_fetch returned no content")
    forwarded = client.retrieve_recorded_expectations(HttpRequest(path="/mcp"))  # only proxied traffic is recorded
    check(not forwarded, f"{len(forwarded)} requests were forwarded upstream during replay")
    print(f"replay: {server}, tools {tools}")
    print(f"replay: web_search -> {len(search)} results, first {search[0]['url']}")
    print(f"replay: web_fetch -> {fetch[0]['url']} ({len(fetch[0]['excerpts'][0])} chars)")
    print(f"replay: 0 requests forwarded to {UPSTREAM_HOST} -- answered from recorded fixtures")


def main():
    with MockServerClient(MOCK_HOST, MOCK_PORT) as client:
        if os.environ.get("MCP_CAPTURE") == "true":
            replay(client, capture(client), "the live capture")
        else:
            fixture = [Expectation.from_dict(e) for e in json.loads(FIXTURE.read_text())]
            replay(client, fixture, FIXTURE.name)


if __name__ == "__main__":
    main()
