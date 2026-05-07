from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from mockserver.fluent import ForwardChainExpectation
from mockserver.models import (
    Delay,
    Expectation,
    HttpError,
    HttpForward,
    HttpObjectCallback,
    HttpOverrideForwardedRequest,
    HttpRequest,
    HttpResponse,
    HttpSseResponse,
    HttpTemplate,
    HttpWebSocketResponse,
    Times,
)


@pytest.fixture
def mock_client():
    client = AsyncMock()
    client.upsert = AsyncMock(return_value=[Expectation(id="test-id")])
    client._register_websocket_callback = AsyncMock(return_value="ws-client-id")
    return client


@pytest.fixture
def chain(mock_client):
    expectation = Expectation(
        http_request=HttpRequest(method="GET", path="/test"),
        times=Times(remaining_times=5, unlimited=False),
    )
    return ForwardChainExpectation(mock_client, expectation)


class TestWithId:
    def test_sets_id(self, chain):
        result = chain.with_id("my-id")
        assert result is chain
        assert chain._expectation.id == "my-id"


class TestWithPriority:
    def test_sets_priority(self, chain):
        result = chain.with_priority(10)
        assert result is chain
        assert chain._expectation.priority == 10


class TestRespond:
    @pytest.mark.asyncio
    async def test_with_http_response(self, chain, mock_client):
        response = HttpResponse(status_code=200, body="hello")
        result = await chain.respond(response)
        assert chain._expectation.http_response is response
        mock_client.upsert.assert_called_once_with(chain._expectation)

    @pytest.mark.asyncio
    async def test_with_http_template(self, chain, mock_client):
        template = HttpTemplate(template_type="JAVASCRIPT", template="return {};")
        result = await chain.respond(template)
        assert chain._expectation.http_response_template is template
        mock_client.upsert.assert_called_once()

    @pytest.mark.asyncio
    async def test_with_callable(self, chain, mock_client):
        callback = lambda req: HttpResponse(status_code=200)
        result = await chain.respond(callback)
        mock_client._register_websocket_callback.assert_called_once_with(
            "response", callback
        )
        assert chain._expectation.http_response_object_callback.client_id == "ws-client-id"
        mock_client.upsert.assert_called_once()


class TestRespondWithDelay:
    @pytest.mark.asyncio
    async def test_sets_delay_on_response(self, chain, mock_client):
        response = HttpResponse(status_code=200)
        delay = Delay(time_unit="SECONDS", value=5)
        result = await chain.respond_with_delay(response, delay)
        assert response.delay is delay
        assert chain._expectation.http_response is response
        mock_client.upsert.assert_called_once()


class TestForward:
    @pytest.mark.asyncio
    async def test_with_http_forward(self, chain, mock_client):
        forward = HttpForward(host="example.com", port=8080)
        result = await chain.forward(forward)
        assert chain._expectation.http_forward is forward
        mock_client.upsert.assert_called_once()

    @pytest.mark.asyncio
    async def test_with_http_override(self, chain, mock_client):
        override = HttpOverrideForwardedRequest(
            http_request=HttpRequest(path="/override")
        )
        result = await chain.forward(override)
        assert chain._expectation.http_override_forwarded_request is override
        mock_client.upsert.assert_called_once()

    @pytest.mark.asyncio
    async def test_with_http_template(self, chain, mock_client):
        template = HttpTemplate(template_type="VELOCITY", template="$req.path")
        result = await chain.forward(template)
        assert chain._expectation.http_forward_template is template
        mock_client.upsert.assert_called_once()

    @pytest.mark.asyncio
    async def test_with_callable(self, chain, mock_client):
        callback = lambda req: req
        result = await chain.forward(callback)
        mock_client._register_websocket_callback.assert_called_once_with(
            "forward", callback, None
        )
        assert chain._expectation.http_forward_object_callback.client_id == "ws-client-id"
        assert chain._expectation.http_forward_object_callback.response_callback is None
        mock_client.upsert.assert_called_once()

    @pytest.mark.asyncio
    async def test_with_callable_and_response_callback(self, chain, mock_client):
        fwd_callback = lambda req: req
        resp_callback = lambda req, resp: resp
        result = await chain.forward(fwd_callback, resp_callback)
        mock_client._register_websocket_callback.assert_called_once_with(
            "forward", fwd_callback, resp_callback
        )
        assert chain._expectation.http_forward_object_callback.response_callback is True


class TestForwardWithDelay:
    @pytest.mark.asyncio
    async def test_sets_delay_on_forward(self, chain, mock_client):
        forward = HttpForward(host="example.com")
        delay = Delay(time_unit="MILLISECONDS", value=500)
        result = await chain.forward_with_delay(forward, delay)
        assert forward.delay is delay
        assert chain._expectation.http_forward is forward
        mock_client.upsert.assert_called_once()


class TestError:
    @pytest.mark.asyncio
    async def test_sets_error(self, chain, mock_client):
        error = HttpError(drop_connection=True)
        result = await chain.error(error)
        assert chain._expectation.http_error is error
        mock_client.upsert.assert_called_once()


class TestRespondTypeError:
    @pytest.mark.asyncio
    async def test_respond_with_invalid_type_raises(self, chain):
        with pytest.raises(TypeError, match="Expected HttpResponse"):
            await chain.respond("not a response")

    @pytest.mark.asyncio
    async def test_respond_with_dict_raises(self, chain):
        with pytest.raises(TypeError, match="Expected HttpResponse"):
            await chain.respond({"status": 200})


class TestForwardTypeError:
    @pytest.mark.asyncio
    async def test_forward_with_invalid_type_raises(self, chain):
        with pytest.raises(TypeError, match="Expected HttpForward"):
            await chain.forward("not a forward")

    @pytest.mark.asyncio
    async def test_forward_with_int_raises(self, chain):
        with pytest.raises(TypeError, match="Expected HttpForward"):
            await chain.forward(42)


class TestRespondWithSse:
    @pytest.mark.asyncio
    async def test_with_http_sse_response(self, chain, mock_client):
        sse_response = HttpSseResponse(status_code=200)
        result = await chain.respond_with_sse(sse_response)
        assert chain._expectation.http_sse_response is sse_response
        mock_client.upsert.assert_called_once_with(chain._expectation)

    @pytest.mark.asyncio
    async def test_with_invalid_type_raises(self, chain):
        with pytest.raises(TypeError, match="Expected HttpSseResponse"):
            await chain.respond_with_sse("not an sse response")

    @pytest.mark.asyncio
    async def test_with_dict_raises(self, chain):
        with pytest.raises(TypeError, match="Expected HttpSseResponse"):
            await chain.respond_with_sse({"status_code": 200})


class TestRespondWithWebSocket:
    @pytest.mark.asyncio
    async def test_with_http_websocket_response(self, chain, mock_client):
        ws_response = HttpWebSocketResponse(subprotocol="graphql-ws")
        result = await chain.respond_with_websocket(ws_response)
        assert chain._expectation.http_websocket_response is ws_response
        mock_client.upsert.assert_called_once_with(chain._expectation)

    @pytest.mark.asyncio
    async def test_with_invalid_type_raises(self, chain):
        with pytest.raises(TypeError, match="Expected HttpWebSocketResponse"):
            await chain.respond_with_websocket("not a ws response")

    @pytest.mark.asyncio
    async def test_with_dict_raises(self, chain):
        with pytest.raises(TypeError, match="Expected HttpWebSocketResponse"):
            await chain.respond_with_websocket({"subprotocol": "test"})


class TestChaining:
    @pytest.mark.asyncio
    async def test_chained_with_id_and_priority(self, chain, mock_client):
        result = await (
            chain.with_id("chained-id")
            .with_priority(5)
            .respond(HttpResponse(status_code=200))
        )
        assert chain._expectation.id == "chained-id"
        assert chain._expectation.priority == 5
        mock_client.upsert.assert_called_once()
