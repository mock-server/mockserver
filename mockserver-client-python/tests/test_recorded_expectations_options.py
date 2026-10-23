"""Retrieving recorded expectations with RecordedExpectationsOptions.

Wire-level tests assert the exact raw query string the client sends, and that
every rejected option raises before anything is sent.
"""
from __future__ import annotations

import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from mockserver import RecordedExpectationsOptions
from mockserver.async_client import AsyncMockServerClient
from mockserver.client import MockServerClient
from mockserver.models import HttpRequest

RECORDED = "type=RECORDED_EXPECTATIONS&format=JSON"


class RecordingHandler(BaseHTTPRequestHandler):
    requests: list[tuple[str, str]] = []
    response_body = "[]"

    def do_PUT(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8") if length else ""
        RecordingHandler.requests.append((self.path, body))
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(RecordingHandler.response_body.encode("utf-8"))

    def log_message(self, format, *args):
        pass


@pytest.fixture
def server_port():
    RecordingHandler.requests = []
    RecordingHandler.response_body = "[]"
    server = HTTPServer(("127.0.0.1", 0), RecordingHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield server.server_address[1]
    server.shutdown()
    server.server_close()


@pytest.fixture
def client(server_port):
    with MockServerClient("127.0.0.1", server_port) as c:
        yield c


def _last_query() -> str:
    path, _ = RecordingHandler.requests[-1]
    return path.split("?", 1)[1]


class TestSyncClient:
    def test_sends_every_option_with_a_request_matcher(self, client):
        options = (
            RecordedExpectationsOptions()
            .with_consolidate()
            .with_parameterize()
            .with_repeats_as_scenarios()
            .with_capture_headers("X-Tenant", "Accept")
            .with_extract_bodies_larger_than(1024)
        )
        client.retrieve_recorded_expectations(HttpRequest(path="/jobs/.*"), options=options)
        assert _last_query() == (
            RECORDED
            + "&consolidate=true&parameterize=true&repeatsAsScenarios=true"
            + "&captureHeaders=X-Tenant%2CAccept&extractBodiesLargerThan=1024"
        )
        assert json.loads(RecordingHandler.requests[-1][1]) == {"path": "/jobs/.*"}

    def test_empty_capture_headers_keeps_none(self, client):
        client.retrieve_recorded_expectations(
            options=RecordedExpectationsOptions().with_capture_headers()
        )
        assert _last_query() == RECORDED + "&captureHeaders="

    def test_zero_threshold_is_sent(self, client):
        client.retrieve_recorded_expectations(
            options=RecordedExpectationsOptions().with_extract_bodies_larger_than(0)
        )
        assert _last_query() == RECORDED + "&extractBodiesLargerThan=0"

    def test_false_or_unset_options_send_nothing_extra(self, client):
        client.retrieve_recorded_expectations(
            options=RecordedExpectationsOptions().with_repeats_as_scenarios(False)
        )
        assert _last_query() == RECORDED
        client.retrieve_recorded_expectations(options=RecordedExpectationsOptions())
        assert _last_query() == RECORDED
        client.retrieve_recorded_expectations()
        assert _last_query() == RECORDED

    def test_combines_with_paging(self, client):
        client.retrieve_recorded_expectations(
            offset=5,
            limit=10,
            options=RecordedExpectationsOptions().with_repeats_as_scenarios(),
        )
        assert _last_query() == RECORDED + "&repeatsAsScenarios=true&limit=10&offset=5"

    def test_returns_the_sequenced_expectation(self, client):
        RecordingHandler.response_body = json.dumps([
            {
                "httpRequest": {"method": "GET", "path": "/jobs/1"},
                "httpResponses": [
                    {"statusCode": 200, "body": "pending"},
                    {"statusCode": 200, "body": "done"},
                ],
                "responseMode": "SWITCH",
                "times": {"unlimited": True},
            }
        ])
        expectations = client.retrieve_recorded_expectations(
            options=RecordedExpectationsOptions().with_repeats_as_scenarios()
        )
        assert len(expectations) == 1
        assert expectations[0].response_mode == "SWITCH"
        assert [r.body for r in expectations[0].http_responses] == ["pending", "done"]

    @pytest.mark.parametrize(
        "build",
        [
            lambda: RecordedExpectationsOptions().with_repeats_as_scenarios("true"),
            lambda: RecordedExpectationsOptions().with_consolidate(1),
            lambda: RecordedExpectationsOptions().with_capture_headers("X-Tenant", " "),
            lambda: RecordedExpectationsOptions().with_capture_headers("X-Tenant,Accept"),
            lambda: RecordedExpectationsOptions().with_capture_headers(["X-Tenant"]),
            lambda: RecordedExpectationsOptions().with_extract_bodies_larger_than(-1),
            lambda: RecordedExpectationsOptions().with_extract_bodies_larger_than("1024"),
            lambda: RecordedExpectationsOptions().with_extract_bodies_larger_than(True),
        ],
    )
    def test_invalid_option_is_rejected_when_set(self, build):
        with pytest.raises(ValueError):
            build()

    def test_invalid_attribute_set_directly_sends_nothing(self, client):
        options = RecordedExpectationsOptions()
        options.capture_headers = "X-Tenant"
        with pytest.raises(ValueError):
            client.retrieve_recorded_expectations(options=options)
        options = RecordedExpectationsOptions()
        options.extract_bodies_larger_than = -5
        with pytest.raises(ValueError):
            client.retrieve_recorded_expectations(options=options)
        with pytest.raises(ValueError):
            client.retrieve_recorded_expectations(options={"repeatsAsScenarios": True})
        assert RecordingHandler.requests == []


class TestAsyncClient:
    async def test_sends_options(self, server_port):
        client = AsyncMockServerClient("127.0.0.1", server_port)
        await client.retrieve_recorded_expectations(
            options=RecordedExpectationsOptions()
            .with_repeats_as_scenarios()
            .with_capture_headers("X-Tenant")
        )
        assert _last_query() == RECORDED + "&repeatsAsScenarios=true&captureHeaders=X-Tenant"

    async def test_rejection_sends_nothing(self, server_port):
        client = AsyncMockServerClient("127.0.0.1", server_port)
        options = RecordedExpectationsOptions()
        options.repeats_as_scenarios = "yes"
        with pytest.raises(ValueError):
            await client.retrieve_recorded_expectations(options=options)
        assert RecordingHandler.requests == []
