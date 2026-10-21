"""Expectation metadata, retrieve/clear by metadata and unmatched, and paging.

Wire-level tests assert the exact raw query string the client sends, because a
wrong or widened clear deletes other people's expectations.
"""
from __future__ import annotations

import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import pytest

from mockserver import JSON_NULL, ExpectationFilter
from mockserver.async_client import AsyncMockServerClient
from mockserver.client import MockServerClient
from mockserver.models import Expectation, HttpRequest, HttpResponse

FIXTURE = (
    Path(__file__).resolve().parents[2]
    / "test-fixtures"
    / "expectations"
    / "top_metadata.json"
)


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


class TestExpectationMetadataModel:
    def test_shared_fixture_round_trips_exactly(self):
        data = json.loads(FIXTURE.read_text())
        assert Expectation.from_dict(data).to_dict() == data

    def test_metadata_value_types_are_preserved(self):
        expectation = Expectation.from_dict(json.loads(FIXTURE.read_text()))
        assert expectation.metadata["ticket"] == 4211
        assert isinstance(expectation.metadata["ticket"], int)
        assert expectation.metadata["ratio"] == 0.5
        assert expectation.metadata["flaky"] is False
        assert "note" in expectation.metadata and expectation.metadata["note"] is None
        assert expectation.metadata["contact"]["oncall"] == ["ann", "bob"]

    def test_empty_metadata_is_omitted(self):
        assert "metadata" not in Expectation(metadata={}).to_dict()
        assert "metadata" not in Expectation().to_dict()
        assert Expectation.from_dict({"metadata": {}}).metadata is None

    def test_from_dict_does_not_alias_input(self):
        data = {"metadata": {"tags": ["a"]}}
        expectation = Expectation.from_dict(data)
        expectation.metadata["tags"].append("b")
        assert data["metadata"]["tags"] == ["a"]

    def test_fluent_with_metadata_is_sent_on_upsert(self, client):
        client.when(HttpRequest(path="/x")).with_metadata("owner", "team-a").with_metadata(
            "ticket", 42
        ).with_metadata("note", None).respond(HttpResponse(status_code=200))
        _, body = RecordingHandler.requests[-1]
        assert json.loads(body)[0]["metadata"] == {
            "owner": "team-a",
            "ticket": 42,
            "note": None,
        }

    def test_fluent_with_metadata_mapping_replaces_and_empty_removes(self, client):
        chain = client.when(HttpRequest(path="/x")).with_metadata("a", 1)
        chain.with_metadata({"b": [1, 2]})
        assert chain._async_chain._expectation.metadata == {"b": [1, 2]}
        chain.with_metadata({})
        assert chain._async_chain._expectation.metadata is None

    @pytest.mark.parametrize("key", ["", "   ", 5])
    def test_fluent_with_metadata_rejects_blank_key(self, client, key):
        chain = client.when(HttpRequest(path="/x"))
        with pytest.raises(ValueError):
            chain.with_metadata(key, "v")
        with pytest.raises(ValueError):
            chain.with_metadata({key: "v"})

    def test_fluent_with_metadata_key_requires_value(self, client):
        with pytest.raises(ValueError):
            client.when(HttpRequest(path="/x")).with_metadata("owner")


class TestExpectationFilterEncoding:
    @pytest.mark.parametrize(
        "value,encoded",
        [
            ("!team-a", "%21team-a"),
            ("?x", "%3Fx"),
            ("100%", "100%25"),
            ("a+b", "a%2Bb"),
            ("a b", "a+b"),
            ("a&b#c=d", "a%26b%23c%3Dd"),
        ],
    )
    def test_metadata_value_is_sent_literally(self, client, value, encoded):
        client.retrieve_active_expectations_by_metadata("owner", value)
        assert _last_query() == (
            "type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=owner&metadataValue="
            + encoded
        )

    @pytest.mark.parametrize(
        "value,encoded",
        [
            (42, "42"),
            (True, "true"),
            (["a", "b"], "%5B%22a%22%2C%22b%22%5D"),
            ({"k": 1}, "%7B%22k%22%3A1%7D"),
            (JSON_NULL, "null"),
        ],
    )
    def test_non_string_values_are_sent_as_compact_json(self, client, value, encoded):
        client.retrieve_active_expectations_by_metadata("owner", value)
        assert _last_query().endswith("&metadataValue=" + encoded)

    def test_metadata_key_is_sent_literally(self, client):
        client.retrieve_active_expectations_by_metadata_key("!own er")
        assert _last_query() == (
            "type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=%21own+er"
        )

    def test_retrieve_unmatched(self, client):
        RecordingHandler.response_body = json.dumps(
            [{"id": "u1", "metadata": {"owner": "team-a"}}]
        )
        result = client.retrieve_unmatched_expectations()
        assert _last_query() == "type=ACTIVE_EXPECTATIONS&format=JSON&unmatched=true"
        assert result[0].id == "u1"
        assert result[0].metadata == {"owner": "team-a"}

    def test_retrieve_with_full_filter_and_request(self, client):
        client.retrieve_active_expectations(
            HttpRequest(path="/p"),
            ExpectationFilter()
            .with_metadata_json_path("$[?(@.owner == 'team-a')]")
            .with_unmatched()
            .with_offset(5)
            .with_limit(10),
        )
        assert _last_query() == (
            "type=ACTIVE_EXPECTATIONS&format=JSON"
            "&metadataJsonPath=%24%5B%3F%28%40.owner+%3D%3D+%27team-a%27%29%5D"
            "&unmatched=true&limit=10&offset=5"
        )
        assert json.loads(RecordingHandler.requests[-1][1]) == {"path": "/p"}

    def test_retrieve_without_filter_is_unchanged(self, client):
        client.retrieve_active_expectations()
        assert _last_query() == "type=ACTIVE_EXPECTATIONS&format=JSON"
        assert RecordingHandler.requests[-1][1] == ""

    def test_retrieve_recorded_page(self, client):
        client.retrieve_recorded_expectations(offset=0, limit=3)
        assert _last_query() == "type=RECORDED_EXPECTATIONS&format=JSON&limit=3&offset=0"
        client.retrieve_recorded_expectations()
        assert _last_query() == "type=RECORDED_EXPECTATIONS&format=JSON"

    def test_clear_by_metadata(self, client):
        client.clear_by_metadata("owner", "!team-a")
        path, body = RecordingHandler.requests[-1]
        assert path == (
            "/mockserver/clear?type=expectations&metadataKey=owner&metadataValue=%21team-a"
        )
        assert body == ""

    def test_clear_by_metadata_key(self, client):
        client.clear_by_metadata_key("owner")
        assert _last_query() == "type=expectations&metadataKey=owner"

    def test_clear_unmatched(self, client):
        client.clear_unmatched_expectations()
        assert _last_query() == "type=expectations&unmatched=true"

    def test_clear_with_filter_and_request(self, client):
        client.clear(
            HttpRequest(path="/p"),
            expectation_filter=ExpectationFilter().with_metadata_json_path("$.tags"),
        )
        path, body = RecordingHandler.requests[-1]
        assert path == "/mockserver/clear?type=expectations&metadataJsonPath=%24.tags"
        assert json.loads(body) == {"path": "/p"}

    def test_with_metadata_key_drops_previous_value(self):
        f = ExpectationFilter().with_metadata("owner", "a").with_metadata_key("owner")
        assert f.to_query_params() == {"metadataKey": "owner"}


class TestFailClosed:
    @pytest.mark.parametrize(
        "action",
        [
            lambda c: c.clear(expectation_filter=ExpectationFilter()),
            lambda c: c.clear(expectation_filter=ExpectationFilter().with_unmatched(False)),
            lambda c: c.clear(
                expectation_filter=ExpectationFilter().with_unmatched().with_limit(1)
            ),
            lambda c: c.clear(
                expectation_filter=ExpectationFilter().with_unmatched().with_offset(0)
            ),
            lambda c: c.clear(
                clear_type="log", expectation_filter=ExpectationFilter().with_unmatched()
            ),
            lambda c: c.clear(expectation_filter={"metadataKey": "owner"}),
            # an explicit None (a helper that returned nothing) must not become a full clear
            lambda c: c.clear(expectation_filter=None),
            lambda c: c.clear(None, None, None),
            lambda c: c.clear(None, "expectations", None),
            lambda c: c.clear_by_metadata("owner", None),
            lambda c: c.clear_by_metadata("", "a"),
            lambda c: c.clear_by_metadata("  ", "a"),
            lambda c: c.clear_by_metadata(None, "a"),
            lambda c: c.clear_by_metadata_key(""),
            lambda c: c.clear_by_metadata_key(None),
            lambda c: c.clear_by_metadata("owner", float("nan")),
            lambda c: c.clear_by_metadata("owner", object()),
            lambda c: c.retrieve_active_expectations_by_metadata("owner", None),
            lambda c: c.retrieve_active_expectations_by_metadata_key("   "),
            lambda c: c.retrieve_recorded_expectations(offset=-1),
            lambda c: c.retrieve_recorded_expectations(limit=-1),
            lambda c: c.retrieve_recorded_expectations(limit=True),
            lambda c: c.retrieve_recorded_expectations(limit="5"),
            lambda c: c.retrieve_active_expectations(
                expectation_filter=ExpectationFilter().with_metadata_json_path(" ")
            ),
        ],
    )
    def test_rejected_before_sending(self, client, action):
        with pytest.raises(ValueError):
            action(client)
        assert RecordingHandler.requests == []

    def test_directly_set_blank_key_is_rejected_before_sending(self, client):
        f = ExpectationFilter()
        f.metadata_key = "  "
        with pytest.raises(ValueError):
            client.clear(expectation_filter=f)
        f = ExpectationFilter()
        f.metadata_value = "team-a"
        with pytest.raises(ValueError):
            client.retrieve_active_expectations(expectation_filter=f)
        assert RecordingHandler.requests == []

    def test_with_unmatched_rejects_non_bool(self):
        with pytest.raises(ValueError):
            ExpectationFilter().with_unmatched("true")


class TestAsyncClient:
    async def test_async_methods_send_exact_query(self, server_port):
        client = AsyncMockServerClient("127.0.0.1", server_port)
        await client.retrieve_active_expectations_by_metadata("owner", "a b")
        assert _last_query() == (
            "type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=owner&metadataValue=a+b"
        )
        await client.retrieve_active_expectations_by_metadata_key("owner")
        assert _last_query() == "type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=owner"
        await client.retrieve_unmatched_expectations()
        assert _last_query() == "type=ACTIVE_EXPECTATIONS&format=JSON&unmatched=true"
        await client.retrieve_recorded_expectations(None, 2, 4)
        assert _last_query() == "type=RECORDED_EXPECTATIONS&format=JSON&limit=4&offset=2"
        await client.clear_by_metadata("ticket", 4211)
        assert _last_query() == "type=expectations&metadataKey=ticket&metadataValue=4211"
        await client.clear_by_metadata_key("owner")
        assert _last_query() == "type=expectations&metadataKey=owner"
        await client.clear_unmatched_expectations()
        assert _last_query() == "type=expectations&unmatched=true"

    async def test_async_rejections_send_nothing(self, server_port):
        client = AsyncMockServerClient("127.0.0.1", server_port)
        with pytest.raises(ValueError):
            await client.clear(expectation_filter=ExpectationFilter())
        with pytest.raises(ValueError):
            await client.clear(expectation_filter=None)
        with pytest.raises(ValueError):
            await client.clear_by_metadata("owner", None)
        with pytest.raises(ValueError):
            await client.retrieve_recorded_expectations(offset=-1)
        assert RecordingHandler.requests == []
