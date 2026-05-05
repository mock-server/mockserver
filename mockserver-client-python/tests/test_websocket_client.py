from __future__ import annotations

import asyncio
import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from mockserver.models import (
    HttpRequest,
    HttpRequestAndHttpResponse,
    HttpResponse,
    KeyToMultiValue,
)
from mockserver.websocket_client import (
    MAX_RECONNECT_ATTEMPTS,
    TYPE_CLIENT_ID_DTO,
    TYPE_ERROR_DTO,
    TYPE_HTTP_REQUEST,
    TYPE_HTTP_REQUEST_AND_RESPONSE,
    TYPE_HTTP_RESPONSE,
    WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
    WEBSOCKET_PATH,
    MockServerWebSocketClient,
    _add_correlation_id_header,
    _build_error_message,
    _build_ws_message,
    _clean_context_path,
    _extract_correlation_id,
)


class TestCleanContextPath:
    def test_empty_string(self):
        assert _clean_context_path("") == ""

    def test_with_leading_slash(self):
        assert _clean_context_path("/api") == "/api"

    def test_without_leading_slash(self):
        assert _clean_context_path("api") == "/api"

    def test_none_like_empty(self):
        assert _clean_context_path("") == ""


class TestExtractCorrelationId:
    def test_extracts_from_headers(self):
        request = HttpRequest(
            headers=[
                KeyToMultiValue(name="Content-Type", values=["application/json"]),
                KeyToMultiValue(
                    name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
                    values=["abc-123"],
                ),
            ]
        )
        assert _extract_correlation_id(request) == "abc-123"

    def test_returns_none_when_no_headers(self):
        request = HttpRequest()
        assert _extract_correlation_id(request) is None

    def test_returns_none_when_header_not_found(self):
        request = HttpRequest(
            headers=[KeyToMultiValue(name="Other", values=["val"])]
        )
        assert _extract_correlation_id(request) is None

    def test_returns_none_when_header_has_empty_values(self):
        request = HttpRequest(
            headers=[
                KeyToMultiValue(
                    name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME, values=[]
                )
            ]
        )
        assert _extract_correlation_id(request) is None


class TestAddCorrelationIdHeader:
    def test_adds_header_to_response_with_no_headers(self):
        response = HttpResponse()
        result = _add_correlation_id_header(response, "id-1")
        assert result.headers is not None
        assert len(result.headers) == 1
        assert result.headers[0].name == WEB_SOCKET_CORRELATION_ID_HEADER_NAME
        assert result.headers[0].values == ["id-1"]

    def test_adds_header_to_response_with_existing_headers(self):
        response = HttpResponse(
            headers=[KeyToMultiValue(name="X-Custom", values=["val"])]
        )
        result = _add_correlation_id_header(response, "id-2")
        assert len(result.headers) == 2

    def test_replaces_existing_correlation_header(self):
        response = HttpResponse(
            headers=[
                KeyToMultiValue(
                    name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
                    values=["old-id"],
                )
            ]
        )
        result = _add_correlation_id_header(response, "new-id")
        assert len(result.headers) == 1
        assert result.headers[0].values == ["new-id"]

    def test_works_with_http_request(self):
        request = HttpRequest()
        result = _add_correlation_id_header(request, "req-id")
        assert result.headers[0].name == WEB_SOCKET_CORRELATION_ID_HEADER_NAME
        assert result.headers[0].values == ["req-id"]


class TestBuildWsMessage:
    def test_builds_response_message(self):
        response = HttpResponse(status_code=200).to_dict()
        msg = _build_ws_message(TYPE_HTTP_RESPONSE, response)
        parsed = json.loads(msg)
        assert parsed["type"] == TYPE_HTTP_RESPONSE
        inner = json.loads(parsed["value"])
        assert inner["statusCode"] == 200

    def test_builds_request_message(self):
        request = HttpRequest(method="GET", path="/test").to_dict()
        msg = _build_ws_message(TYPE_HTTP_REQUEST, request)
        parsed = json.loads(msg)
        assert parsed["type"] == TYPE_HTTP_REQUEST
        inner = json.loads(parsed["value"])
        assert inner["method"] == "GET"
        assert inner["path"] == "/test"

    def test_value_is_double_encoded(self):
        msg = _build_ws_message(TYPE_HTTP_RESPONSE, {"statusCode": 404})
        parsed = json.loads(msg)
        assert isinstance(parsed["value"], str)
        inner = json.loads(parsed["value"])
        assert inner["statusCode"] == 404


class TestBuildErrorMessage:
    def test_builds_error_message(self):
        msg = _build_error_message("something went wrong", "corr-123")
        parsed = json.loads(msg)
        assert parsed["type"] == TYPE_ERROR_DTO
        inner = json.loads(parsed["value"])
        assert inner["message"] == "something went wrong"
        assert inner["webSocketCorrelationId"] == "corr-123"


class TestMockServerWebSocketClientInit:
    def test_initial_state(self):
        client = MockServerWebSocketClient()
        assert client.is_connected is False
        assert client.client_id is None
        assert client._response_callback is None
        assert client._forward_callback is None
        assert client._forward_response_callback is None

    def test_register_response_callback(self):
        client = MockServerWebSocketClient()
        fn = lambda req: HttpResponse(status_code=200)
        client.register_response_callback(fn)
        assert client._response_callback is fn

    def test_register_forward_callback(self):
        client = MockServerWebSocketClient()
        fwd_fn = lambda req: req
        resp_fn = lambda req, resp: resp
        client.register_forward_callback(fwd_fn, resp_fn)
        assert client._forward_callback is fwd_fn
        assert client._forward_response_callback is resp_fn

    def test_register_forward_callback_without_response(self):
        client = MockServerWebSocketClient()
        fwd_fn = lambda req: req
        client.register_forward_callback(fwd_fn)
        assert client._forward_callback is fwd_fn
        assert client._forward_response_callback is None


class TestHandleMessage:
    @pytest.fixture
    def client(self):
        c = MockServerWebSocketClient()
        c._ws = AsyncMock()
        c._ws.open = True
        return c

    @pytest.mark.asyncio
    async def test_handles_client_id_dto(self, client):
        msg = json.dumps({
            "type": TYPE_CLIENT_ID_DTO,
            "value": json.dumps({"clientId": "test-client-id"}),
        })
        await client._handle_message(msg)
        assert client._client_id == "test-client-id"

    @pytest.mark.asyncio
    async def test_handles_response_callback(self, client):
        def response_callback(request):
            return HttpResponse(status_code=200, body="hello")

        client.register_response_callback(response_callback)

        request_dict = HttpRequest(
            method="GET",
            path="/test",
            headers=[
                KeyToMultiValue(
                    name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
                    values=["corr-1"],
                )
            ],
        ).to_dict()

        msg = json.dumps({
            "type": TYPE_HTTP_REQUEST,
            "value": json.dumps(request_dict),
        })

        await client._handle_message(msg)

        client._ws.send.assert_called_once()
        sent_data = json.loads(client._ws.send.call_args[0][0])
        assert sent_data["type"] == TYPE_HTTP_RESPONSE
        inner = json.loads(sent_data["value"])
        assert inner["statusCode"] == 200
        found_correlation = False
        for h in inner.get("headers", []):
            if h["name"] == WEB_SOCKET_CORRELATION_ID_HEADER_NAME:
                assert h["values"] == ["corr-1"]
                found_correlation = True
        assert found_correlation

    @pytest.mark.asyncio
    async def test_handles_async_response_callback(self, client):
        async def response_callback(request):
            return HttpResponse(status_code=201)

        client.register_response_callback(response_callback)

        request_dict = HttpRequest(
            method="POST",
            path="/async-test",
            headers=[
                KeyToMultiValue(
                    name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
                    values=["corr-async"],
                )
            ],
        ).to_dict()

        msg = json.dumps({
            "type": TYPE_HTTP_REQUEST,
            "value": json.dumps(request_dict),
        })

        await client._handle_message(msg)

        client._ws.send.assert_called_once()
        sent_data = json.loads(client._ws.send.call_args[0][0])
        inner = json.loads(sent_data["value"])
        assert inner["statusCode"] == 201

    @pytest.mark.asyncio
    async def test_handles_forward_callback(self, client):
        def forward_callback(request):
            request.path = "/forwarded"
            return request

        client.register_forward_callback(forward_callback)

        request_dict = HttpRequest(
            method="GET",
            path="/original",
            headers=[
                KeyToMultiValue(
                    name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
                    values=["corr-fwd"],
                )
            ],
        ).to_dict()

        msg = json.dumps({
            "type": TYPE_HTTP_REQUEST,
            "value": json.dumps(request_dict),
        })

        await client._handle_message(msg)

        client._ws.send.assert_called_once()
        sent_data = json.loads(client._ws.send.call_args[0][0])
        assert sent_data["type"] == TYPE_HTTP_REQUEST
        inner = json.loads(sent_data["value"])
        assert inner["path"] == "/forwarded"

    @pytest.mark.asyncio
    async def test_handles_forward_response_callback(self, client):
        def forward_response_callback(request, response):
            return HttpResponse(status_code=202, body="modified")

        client._forward_response_callback = forward_response_callback

        req_and_resp = HttpRequestAndHttpResponse(
            http_request=HttpRequest(
                method="GET",
                path="/test",
                headers=[
                    KeyToMultiValue(
                        name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
                        values=["corr-fr"],
                    )
                ],
            ),
            http_response=HttpResponse(status_code=200),
        ).to_dict()

        msg = json.dumps({
            "type": TYPE_HTTP_REQUEST_AND_RESPONSE,
            "value": json.dumps(req_and_resp),
        })

        await client._handle_message(msg)

        client._ws.send.assert_called_once()
        sent_data = json.loads(client._ws.send.call_args[0][0])
        assert sent_data["type"] == TYPE_HTTP_RESPONSE
        inner = json.loads(sent_data["value"])
        assert inner["statusCode"] == 202

    @pytest.mark.asyncio
    async def test_sends_error_on_callback_exception(self, client):
        def bad_callback(request):
            raise ValueError("callback failed")

        client.register_response_callback(bad_callback)

        request_dict = HttpRequest(
            headers=[
                KeyToMultiValue(
                    name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
                    values=["corr-err"],
                )
            ],
        ).to_dict()

        msg = json.dumps({
            "type": TYPE_HTTP_REQUEST,
            "value": json.dumps(request_dict),
        })

        await client._handle_message(msg)

        client._ws.send.assert_called_once()
        sent_data = json.loads(client._ws.send.call_args[0][0])
        assert sent_data["type"] == TYPE_ERROR_DTO
        inner = json.loads(sent_data["value"])
        assert "callback failed" in inner["message"]
        assert inner["webSocketCorrelationId"] == "corr-err"

    @pytest.mark.asyncio
    async def test_sends_error_on_forward_callback_exception(self, client):
        def bad_forward(request):
            raise RuntimeError("forward failed")

        client.register_forward_callback(bad_forward)

        request_dict = HttpRequest(
            headers=[
                KeyToMultiValue(
                    name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME,
                    values=["corr-fwd-err"],
                )
            ],
        ).to_dict()

        msg = json.dumps({
            "type": TYPE_HTTP_REQUEST,
            "value": json.dumps(request_dict),
        })

        await client._handle_message(msg)

        sent_data = json.loads(client._ws.send.call_args[0][0])
        assert sent_data["type"] == TYPE_ERROR_DTO
        inner = json.loads(sent_data["value"])
        assert "forward failed" in inner["message"]

    @pytest.mark.asyncio
    async def test_no_callback_registered_for_request(self, client):
        request_dict = HttpRequest(method="GET", path="/test").to_dict()
        msg = json.dumps({
            "type": TYPE_HTTP_REQUEST,
            "value": json.dumps(request_dict),
        })
        await client._handle_message(msg)
        client._ws.send.assert_not_called()

    @pytest.mark.asyncio
    async def test_no_callback_registered_for_request_and_response(self, client):
        req_and_resp = HttpRequestAndHttpResponse(
            http_request=HttpRequest(method="GET"),
            http_response=HttpResponse(status_code=200),
        ).to_dict()
        msg = json.dumps({
            "type": TYPE_HTTP_REQUEST_AND_RESPONSE,
            "value": json.dumps(req_and_resp),
        })
        await client._handle_message(msg)
        client._ws.send.assert_not_called()


class TestConstants:
    def test_websocket_path(self):
        assert WEBSOCKET_PATH == "/_mockserver_callback_websocket"

    def test_max_reconnect_attempts(self):
        assert MAX_RECONNECT_ATTEMPTS == 3

    def test_type_constants(self):
        assert TYPE_HTTP_REQUEST == "org.mockserver.model.HttpRequest"
        assert TYPE_HTTP_RESPONSE == "org.mockserver.model.HttpResponse"
        assert TYPE_HTTP_REQUEST_AND_RESPONSE == "org.mockserver.model.HttpRequestAndHttpResponse"
        assert TYPE_CLIENT_ID_DTO == "org.mockserver.serialization.model.WebSocketClientIdDTO"
        assert TYPE_ERROR_DTO == "org.mockserver.serialization.model.WebSocketErrorDTO"


class TestCloseMethod:
    @pytest.mark.asyncio
    async def test_close_sets_stopped(self):
        client = MockServerWebSocketClient()
        client._ws = AsyncMock()
        client._ws.open = True
        await client.close()
        assert client._stopped is True
        assert client._ws is None

    @pytest.mark.asyncio
    async def test_close_when_not_connected(self):
        client = MockServerWebSocketClient()
        await client.close()
        assert client._stopped is True
        assert client._ws is None

    @pytest.mark.asyncio
    async def test_close_cancels_listen_task(self):
        client = MockServerWebSocketClient()
        client._ws = AsyncMock()
        client._ws.open = True

        async def fake_listen():
            await asyncio.sleep(100)

        client._listen_task = asyncio.create_task(fake_listen())
        await client.close()
        assert client._listen_task.cancelled() or client._listen_task.done()
