from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, HTTPServer
import threading
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from mockserver.async_client import AsyncMockServerClient
from mockserver.exceptions import (
    MockServerConnectionError,
    MockServerError,
    MockServerVerificationError,
)
from mockserver.fluent import ForwardChainExpectation
from mockserver.models import (
    Expectation,
    HttpForward,
    HttpObjectCallback,
    HttpRequest,
    HttpRequestAndHttpResponse,
    HttpResponse,
    OpenAPIExpectation,
    Ports,
    TimeToLive,
    Times,
    VerificationTimes,
)


class TestAsyncClientInit:
    def test_http_base_url(self):
        client = AsyncMockServerClient("localhost", 1080)
        assert client._base_url == "http://localhost:1080"

    def test_https_base_url(self):
        client = AsyncMockServerClient("localhost", 1080, secure=True)
        assert client._base_url == "https://localhost:1080"

    def test_with_context_path(self):
        client = AsyncMockServerClient("localhost", 1080, context_path="api")
        assert client._base_url == "http://localhost:1080/api"

    def test_with_context_path_leading_slash(self):
        client = AsyncMockServerClient("localhost", 1080, context_path="/api")
        assert client._base_url == "http://localhost:1080/api"

    def test_stores_connection_params(self):
        client = AsyncMockServerClient(
            "myhost", 9090, context_path="ctx", secure=True
        )
        assert client._host == "myhost"
        assert client._port == 9090
        assert client._context_path == "ctx"
        assert client._secure is True
        assert client._ca_cert_path is None

    def test_stores_ca_cert_path(self):
        client = AsyncMockServerClient(
            "myhost", 9090, secure=False, ca_cert_path="/certs/ca.pem"
        )
        assert client._ca_cert_path == "/certs/ca.pem"

    def test_secure_with_invalid_ca_cert_raises(self):
        import pytest as pt
        with pt.raises(FileNotFoundError):
            AsyncMockServerClient(
                "myhost", 9090, secure=True, ca_cert_path="/nonexistent/ca.pem"
            )

    def test_empty_websocket_clients_list(self):
        client = AsyncMockServerClient("localhost", 1080)
        assert client._websocket_clients == []


class TestAsyncClientContextManager:
    @pytest.mark.asyncio
    async def test_aenter_returns_self(self):
        client = AsyncMockServerClient("localhost", 1080)
        async with client as c:
            assert c is client

    @pytest.mark.asyncio
    async def test_aexit_calls_close(self):
        client = AsyncMockServerClient("localhost", 1080)
        client.close = AsyncMock()
        async with client:
            pass
        client.close.assert_called_once()


class TestWhen:
    def test_returns_forward_chain_expectation(self):
        client = AsyncMockServerClient("localhost", 1080)
        chain = client.when(HttpRequest(method="GET", path="/test"))
        assert isinstance(chain, ForwardChainExpectation)
        assert chain._expectation.http_request.method == "GET"
        assert chain._expectation.http_request.path == "/test"

    def test_with_times_and_ttl(self):
        client = AsyncMockServerClient("localhost", 1080)
        times = Times(remaining_times=3, unlimited=False)
        ttl = TimeToLive(time_unit="SECONDS", time_to_live=60, unlimited=False)
        chain = client.when(
            HttpRequest(path="/test"), times=times, time_to_live=ttl, priority=5
        )
        assert chain._expectation.times is times
        assert chain._expectation.time_to_live is ttl
        assert chain._expectation.priority == 5


class MockHandler(BaseHTTPRequestHandler):
    response_status = 200
    response_body = "[]"
    last_request_body = None
    last_path = None
    last_method = None

    def do_PUT(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8") if content_length > 0 else ""
        MockHandler.last_request_body = body
        MockHandler.last_path = self.path
        MockHandler.last_method = "PUT"

        self.send_response(MockHandler.response_status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(MockHandler.response_body.encode("utf-8"))

    def log_message(self, format, *args):
        pass


@pytest.fixture
def mock_server():
    server = HTTPServer(("127.0.0.1", 0), MockHandler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever)
    thread.daemon = True
    thread.start()
    MockHandler.response_status = 200
    MockHandler.response_body = "[]"
    MockHandler.last_request_body = None
    MockHandler.last_path = None
    MockHandler.last_method = None
    yield port
    server.shutdown()


class TestUpsert:
    @pytest.mark.asyncio
    async def test_upsert_single_expectation(self, mock_server):
        MockHandler.response_body = json.dumps([{
            "id": "exp-1",
            "httpRequest": {"method": "GET", "path": "/test"},
            "httpResponse": {"statusCode": 200},
        }])
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        expectation = Expectation(
            http_request=HttpRequest(method="GET", path="/test"),
            http_response=HttpResponse(status_code=200),
        )
        result = await client.upsert(expectation)
        assert len(result) == 1
        assert result[0].id == "exp-1"
        assert MockHandler.last_path == "/mockserver/expectation"
        sent = json.loads(MockHandler.last_request_body)
        assert isinstance(sent, list)
        assert sent[0]["httpRequest"]["method"] == "GET"

    @pytest.mark.asyncio
    async def test_upsert_error(self, mock_server):
        MockHandler.response_status = 400
        MockHandler.response_body = "Bad Request"
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        with pytest.raises(MockServerError, match="Invalid expectation"):
            await client.upsert(Expectation())


class TestClear:
    @pytest.mark.asyncio
    async def test_clear_with_request(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.clear(HttpRequest(path="/test"))
        assert "/mockserver/clear" in MockHandler.last_path
        sent = json.loads(MockHandler.last_request_body)
        assert sent["path"] == "/test"

    @pytest.mark.asyncio
    async def test_clear_with_type(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.clear(clear_type="LOG")
        assert "type=LOG" in MockHandler.last_path

    @pytest.mark.asyncio
    async def test_clear_by_id(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.clear_by_id("exp-123")
        sent = json.loads(MockHandler.last_request_body)
        assert sent["id"] == "exp-123"


class TestReset:
    @pytest.mark.asyncio
    async def test_reset(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.reset()
        assert MockHandler.last_path == "/mockserver/reset"


class TestVerify:
    @pytest.mark.asyncio
    async def test_verify_success(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.verify(HttpRequest(path="/test"))
        assert "/mockserver/verify" in MockHandler.last_path

    @pytest.mark.asyncio
    async def test_verify_with_times(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.verify(
            HttpRequest(path="/test"),
            VerificationTimes(at_least=2, at_most=5),
        )
        sent = json.loads(MockHandler.last_request_body)
        assert sent["times"]["atLeast"] == 2
        assert sent["times"]["atMost"] == 5

    @pytest.mark.asyncio
    async def test_verify_failure_raises(self, mock_server):
        MockHandler.response_status = 406
        MockHandler.response_body = "Request not found"
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        with pytest.raises(MockServerVerificationError, match="Request not found"):
            await client.verify(HttpRequest(path="/test"))


class TestVerifySequence:
    @pytest.mark.asyncio
    async def test_verify_sequence_success(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.verify_sequence(
            HttpRequest(path="/first"),
            HttpRequest(path="/second"),
        )
        assert "/mockserver/verifySequence" in MockHandler.last_path
        sent = json.loads(MockHandler.last_request_body)
        assert len(sent["httpRequests"]) == 2
        assert sent["httpRequests"][0]["path"] == "/first"
        assert sent["httpRequests"][1]["path"] == "/second"

    @pytest.mark.asyncio
    async def test_verify_sequence_failure(self, mock_server):
        MockHandler.response_status = 406
        MockHandler.response_body = "Sequence not found"
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        with pytest.raises(MockServerVerificationError, match="Sequence not found"):
            await client.verify_sequence(HttpRequest(path="/a"))


class TestRetrieve:
    @pytest.mark.asyncio
    async def test_retrieve_recorded_requests(self, mock_server):
        MockHandler.response_body = json.dumps([
            {"method": "GET", "path": "/recorded1"},
            {"method": "POST", "path": "/recorded2"},
        ])
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        result = await client.retrieve_recorded_requests()
        assert len(result) == 2
        assert result[0].method == "GET"
        assert result[0].path == "/recorded1"
        assert "type=REQUESTS" in MockHandler.last_path
        assert "format=JSON" in MockHandler.last_path

    @pytest.mark.asyncio
    async def test_retrieve_active_expectations(self, mock_server):
        MockHandler.response_body = json.dumps([
            {"id": "e1", "httpRequest": {"path": "/active"}},
        ])
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        result = await client.retrieve_active_expectations()
        assert len(result) == 1
        assert result[0].id == "e1"
        assert "type=ACTIVE_EXPECTATIONS" in MockHandler.last_path

    @pytest.mark.asyncio
    async def test_retrieve_recorded_expectations(self, mock_server):
        MockHandler.response_body = json.dumps([
            {"id": "re1", "httpRequest": {"path": "/rec"}},
        ])
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        result = await client.retrieve_recorded_expectations()
        assert len(result) == 1
        assert "type=RECORDED_EXPECTATIONS" in MockHandler.last_path

    @pytest.mark.asyncio
    async def test_retrieve_recorded_requests_and_responses(self, mock_server):
        MockHandler.response_body = json.dumps([
            {
                "httpRequest": {"method": "GET", "path": "/rr"},
                "httpResponse": {"statusCode": 200},
            }
        ])
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        result = await client.retrieve_recorded_requests_and_responses()
        assert len(result) == 1
        assert result[0].http_request.path == "/rr"
        assert result[0].http_response.status_code == 200

    @pytest.mark.asyncio
    async def test_retrieve_log_messages(self, mock_server):
        MockHandler.response_body = json.dumps(["log line 1", "log line 2"])
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        result = await client.retrieve_log_messages()
        assert len(result) == 2
        assert result[0] == "log line 1"

    @pytest.mark.asyncio
    async def test_retrieve_empty_response(self, mock_server):
        MockHandler.response_body = ""
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        result = await client.retrieve_recorded_requests()
        assert result == []


class TestBind:
    @pytest.mark.asyncio
    async def test_bind_ports(self, mock_server):
        MockHandler.response_body = json.dumps({"ports": [1080, 1081]})
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        result = await client.bind(1080, 1081)
        assert result == [1080, 1081]
        sent = json.loads(MockHandler.last_request_body)
        assert sent["ports"] == [1080, 1081]


class TestHasStarted:
    @pytest.mark.asyncio
    async def test_has_started_returns_true(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        result = await client.has_started(attempts=1)
        assert result is True

    @pytest.mark.asyncio
    async def test_has_started_returns_false_on_connection_error(self):
        client = AsyncMockServerClient("127.0.0.1", 19999)
        result = await client.has_started(attempts=1, timeout=0.01)
        assert result is False


class TestStop:
    @pytest.mark.asyncio
    async def test_stop(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.stop()
        assert MockHandler.last_path == "/mockserver/stop"

    @pytest.mark.asyncio
    async def test_stop_ignores_connection_error(self):
        client = AsyncMockServerClient("127.0.0.1", 19999)
        await client.stop()


class TestOpenApiExpectation:
    @pytest.mark.asyncio
    async def test_open_api_expectation(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.open_api_expectation(
            OpenAPIExpectation(spec_url_or_payload="https://example.com/openapi.json")
        )
        assert "/mockserver/openapi" in MockHandler.last_path
        sent = json.loads(MockHandler.last_request_body)
        assert sent["specUrlOrPayload"] == "https://example.com/openapi.json"


class TestVerifyZeroInteractions:
    @pytest.mark.asyncio
    async def test_verify_zero_interactions(self, mock_server):
        client = AsyncMockServerClient("127.0.0.1", mock_server)
        await client.verify_zero_interactions()
        sent = json.loads(MockHandler.last_request_body)
        assert sent["times"]["atMost"] == 0


class TestRegisterWebSocketCallback:
    @pytest.mark.asyncio
    async def test_register_websocket_callback(self):
        client = AsyncMockServerClient("localhost", 1080)
        with patch("mockserver.async_client.MockServerWebSocketClient") as MockWSClient:
            mock_ws = MagicMock()
            mock_ws.connect = AsyncMock(return_value="test-ws-id")
            mock_ws.listen = AsyncMock()
            MockWSClient.return_value = mock_ws

            callback = lambda req: HttpResponse(status_code=200)
            client_id = await client._register_websocket_callback("response", callback)

            assert client_id == "test-ws-id"
            mock_ws.connect.assert_called_once_with(
                "localhost", 1080, "", False, None, tls_verify=True
            )
            mock_ws.register_response_callback.assert_called_once_with(callback)
            assert mock_ws in client._websocket_clients

    @pytest.mark.asyncio
    async def test_register_forward_callback(self):
        client = AsyncMockServerClient("localhost", 1080)
        with patch("mockserver.async_client.MockServerWebSocketClient") as MockWSClient:
            mock_ws = MagicMock()
            mock_ws.connect = AsyncMock(return_value="fwd-ws-id")
            mock_ws.listen = AsyncMock()
            MockWSClient.return_value = mock_ws

            fwd_fn = lambda req: req
            resp_fn = lambda req, resp: resp
            client_id = await client._register_websocket_callback(
                "forward", fwd_fn, resp_fn
            )

            assert client_id == "fwd-ws-id"
            mock_ws.register_forward_callback.assert_called_once_with(fwd_fn, resp_fn)


class TestClose:
    @pytest.mark.asyncio
    async def test_close_cleans_up_websockets(self):
        client = AsyncMockServerClient("localhost", 1080)
        ws1 = AsyncMock()
        ws2 = AsyncMock()
        client._websocket_clients = [ws1, ws2]
        await client.close()
        ws1.close.assert_called_once()
        ws2.close.assert_called_once()
        assert client._websocket_clients == []
