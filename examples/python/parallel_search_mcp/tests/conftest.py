"""Shared pytest fixtures for Parallel Search MCP example tests."""

from __future__ import annotations

import json
import re
import socket
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any, Generator

import pytest
from mockserver import Expectation, MockServerClient

from parallel_search_mcp.capture import CapturedSession
from parallel_search_mcp.config import ClientConfig
from parallel_search_mcp.fixtures_manager import get_default_fixture_path, load_fixture
from parallel_search_mcp.replay import build_replay_expectations


class LocalMockServerHandler(BaseHTTPRequestHandler):
    """In-process HTTP server simulating MockServer expectations engine."""

    expectations: list[dict[str, Any]] = []
    received_requests: list[dict[str, Any]] = []
    lock = threading.Lock()

    def log_message(self, format: str, *args: Any) -> None:
        # Suppress standard logging during tests
        pass

    def do_PUT(self) -> None:
        """Handle MockServer control plane endpoints."""
        content_length = int(self.headers.get("Content-Length", 0))
        raw_body = self.rfile.read(content_length) if content_length > 0 else b""
        body_str = raw_body.decode("utf-8", errors="replace")

        with self.lock:
            if self.path == "/mockserver/reset":
                self.expectations.clear()
                self.received_requests.clear()
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"")
                return

            if self.path == "/mockserver/expectation":
                try:
                    payload = json.loads(body_str)
                    if isinstance(payload, list):
                        self.expectations.extend(payload)
                    elif isinstance(payload, dict):
                        self.expectations.append(payload)
                except Exception:
                    pass
                self.send_response(201)
                self.end_headers()
                self.wfile.write(b"")
                return

            if self.path.startswith("/mockserver/retrieve"):
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps(self.expectations).encode("utf-8"))
                return

            self.send_response(404)
            self.end_headers()

    def do_POST(self) -> None:
        """Handle MCP traffic to /mcp, matching against registered expectations."""
        content_length = int(self.headers.get("Content-Length", 0))
        raw_body = self.rfile.read(content_length) if content_length > 0 else b""
        body_str = raw_body.decode("utf-8", errors="replace")

        try:
            req_json = json.loads(body_str) if body_str else {}
        except Exception:
            req_json = {}

        with self.lock:
            self.received_requests.append({
                "path": self.path,
                "headers": dict(self.headers),
                "body": req_json,
            })

            # Check for notification
            method = req_json.get("method")
            raw_id = req_json.get("id")

            if method == "notifications/initialized":
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b"{}")
                return

            # Match against registered expectations
            matched_exp = None
            for exp in self.expectations:
                req_pattern = exp.get("httpRequest", {})
                if req_pattern.get("path") and not self.path.startswith(req_pattern["path"].rstrip(".*")):
                    continue

                body_matcher = req_pattern.get("body", {})
                if not body_matcher:
                    matched_exp = exp
                    break

                matcher_type = body_matcher.get("type")

                if matcher_type == "JSON_RPC":
                    if body_matcher.get("method") == method:
                        matched_exp = exp
                        break
                elif matcher_type == "JSON_PATH":
                    jp = body_matcher.get("jsonPath", "")
                    if "tools/call" in jp:
                        tool_name = req_json.get("params", {}).get("name")
                        if tool_name and f"@.params.name == '{tool_name}'" in jp:
                            matched_exp = exp
                            break


            if matched_exp:
                template_obj = matched_exp.get("httpResponseTemplate")
                if template_obj and template_obj.get("template"):
                    template = template_obj["template"]
                    id_sub = str(raw_id) if raw_id is not None else "null"
                    rendered = template.replace("$!{request.jsonRpcRawId}", id_sub)
                    rendered = rendered.replace("${esc.d}", "$").replace("${esc.h}", "#")
                    try:
                        resp_data = json.loads(rendered)
                        status_code = resp_data.get("statusCode", 200)
                        body_out = resp_data.get("body", {})
                        if isinstance(body_out, (dict, list)):
                            body_bytes = json.dumps(body_out).encode("utf-8")
                        else:
                            body_bytes = str(body_out).encode("utf-8")
                        self.send_response(status_code)
                        self.send_header("Content-Type", "application/json")
                        self.end_headers()
                        self.wfile.write(body_bytes)
                        return
                    except Exception as err:
                        self.send_response(500)
                        self.end_headers()
                        self.wfile.write(str(err).encode("utf-8"))
                        return

                static_resp = matched_exp.get("httpResponse")
                if static_resp:
                    status_code = static_resp.get("statusCode", 200)
                    body_content = static_resp.get("body", "{}")
                    self.send_response(status_code)
                    self.send_header("Content-Type", "application/json")
                    self.end_headers()
                    self.wfile.write(body_content.encode("utf-8"))
                    return

            # No expectation matched
            self.send_response(404)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            err_resp = {
                "jsonrpc": "2.0",
                "id": raw_id,
                "error": {"code": -32601, "message": f"Method not found or no expectation: {method}"},
            }
            self.wfile.write(json.dumps(err_resp).encode("utf-8"))


def _get_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@pytest.fixture(scope="session")
def local_mockserver():
    """Start an in-process local MockServer simulation for fast offline testing."""
    port = _get_free_port()
    server = HTTPServer(("127.0.0.1", port), LocalMockServerHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()

    yield "127.0.0.1", port

    server.shutdown()
    server.server_close()


@pytest.fixture()
def mockserver_client(local_mockserver):
    host, port = local_mockserver
    with MockServerClient(host, port) as client:
        client.reset()
        yield client


@pytest.fixture()
def replay_client_config(local_mockserver):
    host, port = local_mockserver
    return ClientConfig.for_mockserver(host=host, port=port)
