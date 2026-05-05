from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, HTTPServer
import threading

import pytest

from mockserver.client import MockServerClient, SyncForwardChainExpectation
from mockserver.exceptions import MockServerError, MockServerVerificationError
from mockserver.models import (
    Delay,
    Expectation,
    HttpError,
    HttpForward,
    HttpRequest,
    HttpResponse,
    OpenAPIExpectation,
    Times,
    VerificationTimes,
)


class SyncMockHandler(BaseHTTPRequestHandler):
    response_status = 200
    response_body = "[]"
    last_request_body = None
    last_path = None
    request_count = 0

    def do_PUT(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8") if content_length > 0 else ""
        SyncMockHandler.last_request_body = body
        SyncMockHandler.last_path = self.path
        SyncMockHandler.request_count += 1

        self.send_response(SyncMockHandler.response_status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(SyncMockHandler.response_body.encode("utf-8"))

    def log_message(self, format, *args):
        pass


@pytest.fixture
def sync_mock_server():
    server = HTTPServer(("127.0.0.1", 0), SyncMockHandler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever)
    thread.daemon = True
    thread.start()
    SyncMockHandler.response_status = 200
    SyncMockHandler.response_body = "[]"
    SyncMockHandler.last_request_body = None
    SyncMockHandler.last_path = None
    SyncMockHandler.request_count = 0
    yield port
    server.shutdown()


class TestSyncClientInit:
    def test_creates_async_client(self, sync_mock_server):
        client = MockServerClient("127.0.0.1", sync_mock_server)
        try:
            assert client._async_client._host == "127.0.0.1"
            assert client._async_client._port == sync_mock_server
        finally:
            client.close()


class TestSyncContextManager:
    def test_context_manager(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            assert client is not None


class TestSyncUpsert:
    def test_upsert(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "id": "sync-exp",
            "httpRequest": {"path": "/sync"},
            "httpResponse": {"statusCode": 200},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.upsert(
                Expectation(
                    http_request=HttpRequest(path="/sync"),
                    http_response=HttpResponse(status_code=200),
                )
            )
            assert len(result) == 1
            assert result[0].id == "sync-exp"


class TestSyncClear:
    def test_clear(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.clear(HttpRequest(path="/test"))
            assert "/mockserver/clear" in SyncMockHandler.last_path

    def test_clear_by_id(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.clear_by_id("exp-abc")
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["id"] == "exp-abc"


class TestSyncReset:
    def test_reset(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.reset()
            assert SyncMockHandler.last_path == "/mockserver/reset"


class TestSyncVerify:
    def test_verify_success(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.verify(HttpRequest(path="/test"))
            assert "/mockserver/verify" in SyncMockHandler.last_path

    def test_verify_failure(self, sync_mock_server):
        SyncMockHandler.response_status = 406
        SyncMockHandler.response_body = "Verification failed"
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerVerificationError):
                client.verify(HttpRequest(path="/test"))

    def test_verify_sequence(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.verify_sequence(
                HttpRequest(path="/a"),
                HttpRequest(path="/b"),
            )
            assert "/mockserver/verifySequence" in SyncMockHandler.last_path

    def test_verify_zero_interactions(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.verify_zero_interactions()
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["times"]["atMost"] == 0


class TestSyncRetrieve:
    def test_retrieve_recorded_requests(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"method": "GET", "path": "/recorded"},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_recorded_requests()
            assert len(result) == 1
            assert result[0].path == "/recorded"

    def test_retrieve_active_expectations(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"id": "ae1", "httpRequest": {"path": "/active"}},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_active_expectations()
            assert len(result) == 1
            assert result[0].id == "ae1"

    def test_retrieve_recorded_expectations(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"id": "re1"},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_recorded_expectations()
            assert len(result) == 1

    def test_retrieve_log_messages(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(["msg1", "msg2"])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_log_messages()
            assert result == ["msg1", "msg2"]


class TestSyncBind:
    def test_bind(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"ports": [9090]})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.bind(9090)
            assert result == [9090]


class TestSyncHasStarted:
    def test_has_started_true(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            assert client.has_started(attempts=1) is True

    def test_has_started_false(self):
        client = MockServerClient("127.0.0.1", 19998)
        try:
            assert client.has_started(attempts=1, timeout=0.01) is False
        finally:
            client.close()


class TestSyncStop:
    def test_stop(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.stop()
            assert SyncMockHandler.last_path == "/mockserver/stop"


class TestSyncWhen:
    def test_when_returns_sync_chain(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            chain = client.when(HttpRequest(path="/test"))
            assert isinstance(chain, SyncForwardChainExpectation)

    def test_when_respond(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "id": "when-resp",
            "httpRequest": {"path": "/test"},
            "httpResponse": {"statusCode": 200},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = (
                client.when(HttpRequest(path="/test"))
                .with_id("when-resp")
                .respond(HttpResponse(status_code=200))
            )
            assert len(result) == 1

    def test_when_forward(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "httpRequest": {"path": "/test"},
            "httpForward": {"host": "example.com"},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.when(HttpRequest(path="/test")).forward(
                HttpForward(host="example.com")
            )
            assert len(result) == 1

    def test_when_error(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "httpRequest": {"path": "/test"},
            "httpError": {"dropConnection": True},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.when(HttpRequest(path="/test")).error(
                HttpError(drop_connection=True)
            )
            assert len(result) == 1

    def test_when_respond_with_delay(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "httpRequest": {"path": "/test"},
            "httpResponse": {"statusCode": 200, "delay": {"timeUnit": "SECONDS", "value": 1}},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.when(HttpRequest(path="/test")).respond_with_delay(
                HttpResponse(status_code=200),
                Delay(time_unit="SECONDS", value=1),
            )
            assert len(result) == 1


class TestSyncForwardChainExpectation:
    def test_with_id(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{"id": "chained"}])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            chain = client.when(HttpRequest(path="/test"))
            result = chain.with_id("chained")
            assert result is chain
            assert chain._async_chain._expectation.id == "chained"

    def test_with_priority(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            chain = client.when(HttpRequest(path="/test"))
            result = chain.with_priority(10)
            assert result is chain
            assert chain._async_chain._expectation.priority == 10

    def test_forward_with_delay(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{}])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.when(HttpRequest(path="/test")).forward_with_delay(
                HttpForward(host="example.com"),
                Delay(time_unit="MILLISECONDS", value=500),
            )
            assert len(result) == 1


class TestSyncOpenApiExpectation:
    def test_open_api_expectation(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.open_api_expectation(
                OpenAPIExpectation(spec_url_or_payload="https://example.com/spec.json")
            )
            assert "/mockserver/openapi" in SyncMockHandler.last_path
