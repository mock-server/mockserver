from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any, Callable

import websockets
import websockets.client
import websockets.exceptions

from mockserver.exceptions import MockServerCallbackError, MockServerWebSocketError
from mockserver.models import (
    HttpRequest,
    HttpRequestAndHttpResponse,
    HttpResponse,
    KeyToMultiValue,
)

logger = logging.getLogger(__name__)

WEB_SOCKET_CORRELATION_ID_HEADER_NAME = "WebSocketCorrelationId"
CLIENT_REGISTRATION_ID_HEADER = "X-CLIENT-REGISTRATION-ID"

WEBSOCKET_PATH = "/_mockserver_callback_websocket"

TYPE_HTTP_REQUEST = "org.mockserver.model.HttpRequest"
TYPE_HTTP_RESPONSE = "org.mockserver.model.HttpResponse"
TYPE_HTTP_REQUEST_AND_RESPONSE = "org.mockserver.model.HttpRequestAndHttpResponse"
TYPE_CLIENT_ID_DTO = "org.mockserver.serialization.model.WebSocketClientIdDTO"
TYPE_ERROR_DTO = "org.mockserver.serialization.model.WebSocketErrorDTO"

MAX_RECONNECT_ATTEMPTS = 3


def _extract_correlation_id(request: HttpRequest) -> str | None:
    if request.headers is None:
        return None
    for header in request.headers:
        if header.name == WEB_SOCKET_CORRELATION_ID_HEADER_NAME:
            if header.values:
                return header.values[0]
    return None


def _add_correlation_id_header(
    message: HttpResponse | HttpRequest, correlation_id: str
) -> HttpResponse | HttpRequest:
    if message.headers is None:
        message.headers = []
    for header in message.headers:
        if header.name == WEB_SOCKET_CORRELATION_ID_HEADER_NAME:
            header.values = [correlation_id]
            return message
    message.headers.append(
        KeyToMultiValue(name=WEB_SOCKET_CORRELATION_ID_HEADER_NAME, values=[correlation_id])
    )
    return message


def _build_ws_message(type_name: str, value_dict: dict) -> str:
    return json.dumps({
        "type": type_name,
        "value": json.dumps(value_dict),
    })


def _build_error_message(error_msg: str, correlation_id: str) -> str:
    return json.dumps({
        "type": TYPE_ERROR_DTO,
        "value": json.dumps({
            "message": error_msg,
            "webSocketCorrelationId": correlation_id,
        }),
    })


def _clean_context_path(context_path: str) -> str:
    if not context_path:
        return ""
    if not context_path.startswith("/"):
        return "/" + context_path
    return context_path


class MockServerWebSocketClient:
    def __init__(self) -> None:
        self._ws: websockets.client.WebSocketClientProtocol | None = None
        self._client_id: str | None = None
        self._response_callback: Callable | None = None
        self._forward_callback: Callable | None = None
        self._forward_response_callback: Callable | None = None
        self._stopped = False
        self._listen_task: asyncio.Task | None = None
        self._host: str = ""
        self._port: int = 0
        self._context_path: str = ""
        self._secure: bool = False
        self._ssl_context: Any = None

    @property
    def is_connected(self) -> bool:
        return self._ws is not None and self._ws.open

    @property
    def client_id(self) -> str | None:
        return self._client_id

    async def connect(
        self,
        host: str,
        port: int,
        context_path: str = "",
        secure: bool = False,
        ca_cert_path: str | None = None,
        client_id: str | None = None,
        tls_verify: bool = True,
    ) -> str:
        self._host = host
        self._port = port
        self._context_path = context_path
        self._secure = secure

        if secure:
            import ssl
            self._ssl_context = ssl.create_default_context()
            if ca_cert_path:
                self._ssl_context.load_verify_locations(ca_cert_path)
            elif not tls_verify:
                self._ssl_context.check_hostname = False
                self._ssl_context.verify_mode = ssl.CERT_NONE

        registration_id = client_id or str(uuid.uuid4())

        await self._do_connect(registration_id)
        return self._client_id

    async def _do_connect(self, registration_id: str) -> None:
        scheme = "wss" if self._secure else "ws"
        path = _clean_context_path(self._context_path) + WEBSOCKET_PATH
        uri = f"{scheme}://{self._host}:{self._port}{path}"

        extra_headers = {CLIENT_REGISTRATION_ID_HEADER: registration_id}

        self._ws = await websockets.client.connect(
            uri,
            additional_headers=extra_headers,
            ssl=self._ssl_context if self._secure else None,
            open_timeout=10,
            ping_interval=20,
            ping_timeout=20,
            close_timeout=5,
        )

        registration_msg = await asyncio.wait_for(self._ws.recv(), timeout=10.0)
        parsed = json.loads(registration_msg)
        if parsed.get("type") == TYPE_CLIENT_ID_DTO:
            value = json.loads(parsed["value"])
            self._client_id = value["clientId"]
        else:
            raise MockServerWebSocketError(
                f"Expected WebSocketClientIdDTO but received: {parsed.get('type')}"
            )

    def register_response_callback(self, callback_fn: Callable) -> None:
        self._response_callback = callback_fn

    def register_forward_callback(
        self,
        forward_fn: Callable,
        response_fn: Callable | None = None,
    ) -> None:
        self._forward_callback = forward_fn
        self._forward_response_callback = response_fn

    async def listen(self) -> None:
        reconnect_attempts = 0
        while not self._stopped:
            try:
                async for raw_message in self._ws:
                    await self._handle_message(raw_message)
            except websockets.exceptions.ConnectionClosed:
                if self._stopped:
                    break
                reconnect_attempts += 1
                if reconnect_attempts > MAX_RECONNECT_ATTEMPTS:
                    logger.error("Max reconnect attempts reached, giving up")
                    break
                logger.warning(
                    "WebSocket disconnected, reconnecting (attempt %d/%d)",
                    reconnect_attempts,
                    MAX_RECONNECT_ATTEMPTS,
                )
                try:
                    await self._do_connect(self._client_id or str(uuid.uuid4()))
                    reconnect_attempts = 0
                except Exception:
                    logger.exception("Reconnection failed")
                    await asyncio.sleep(1.0)
            except Exception:
                if self._stopped:
                    break
                logger.exception("Unexpected error in WebSocket listen loop")
                break

    async def _handle_message(self, raw_message: str) -> None:
        parsed = json.loads(raw_message)
        msg_type = parsed.get("type")
        msg_value = parsed.get("value")

        if msg_type == TYPE_CLIENT_ID_DTO:
            value = json.loads(msg_value)
            self._client_id = value["clientId"]
            return

        if msg_type == TYPE_HTTP_REQUEST:
            request = HttpRequest.from_dict(json.loads(msg_value))
            correlation_id = _extract_correlation_id(request)

            if self._forward_callback is not None:
                await self._handle_forward_request(request, correlation_id)
            elif self._response_callback is not None:
                await self._handle_response_request(request, correlation_id)
            return

        if msg_type == TYPE_HTTP_REQUEST_AND_RESPONSE:
            req_and_resp = HttpRequestAndHttpResponse.from_dict(json.loads(msg_value))
            correlation_id = _extract_correlation_id(req_and_resp.http_request)

            if self._forward_response_callback is not None:
                await self._handle_forward_response(req_and_resp, correlation_id)
            return

        logger.warning("Received unhandled WebSocket message type: %s", msg_type)

    async def _handle_response_request(
        self, request: HttpRequest, correlation_id: str | None
    ) -> None:
        try:
            result = self._response_callback(request)
            if asyncio.iscoroutine(result) or asyncio.isfuture(result):
                result = await result
            if not isinstance(result, HttpResponse):
                raise MockServerCallbackError(
                    f"Response callback must return HttpResponse, got {type(result)}"
                )
            if correlation_id:
                _add_correlation_id_header(result, correlation_id)
            msg = _build_ws_message(TYPE_HTTP_RESPONSE, result.to_dict())
            await self._ws.send(msg)
        except Exception as exc:
            logger.exception("Error in response callback")
            if correlation_id:
                error_msg = _build_error_message(str(exc), correlation_id)
                await self._ws.send(error_msg)

    async def _handle_forward_request(
        self, request: HttpRequest, correlation_id: str | None
    ) -> None:
        try:
            result = self._forward_callback(request)
            if asyncio.iscoroutine(result) or asyncio.isfuture(result):
                result = await result
            if not isinstance(result, HttpRequest):
                raise MockServerCallbackError(
                    f"Forward callback must return HttpRequest, got {type(result)}"
                )
            if correlation_id:
                _add_correlation_id_header(result, correlation_id)
            msg = _build_ws_message(TYPE_HTTP_REQUEST, result.to_dict())
            await self._ws.send(msg)
        except Exception as exc:
            logger.exception("Error in forward callback")
            if correlation_id:
                error_msg = _build_error_message(str(exc), correlation_id)
                await self._ws.send(error_msg)

    async def _handle_forward_response(
        self,
        req_and_resp: HttpRequestAndHttpResponse,
        correlation_id: str | None,
    ) -> None:
        try:
            result = self._forward_response_callback(
                req_and_resp.http_request, req_and_resp.http_response
            )
            if asyncio.iscoroutine(result) or asyncio.isfuture(result):
                result = await result
            if not isinstance(result, HttpResponse):
                raise MockServerCallbackError(
                    f"Forward response callback must return HttpResponse, got {type(result)}"
                )
            if correlation_id:
                _add_correlation_id_header(result, correlation_id)
            msg = _build_ws_message(TYPE_HTTP_RESPONSE, result.to_dict())
            await self._ws.send(msg)
        except Exception as exc:
            logger.exception("Error in forward response callback")
            if correlation_id:
                error_msg = _build_error_message(str(exc), correlation_id)
                await self._ws.send(error_msg)

    async def close(self) -> None:
        self._stopped = True
        if self._listen_task and not self._listen_task.done():
            self._listen_task.cancel()
            try:
                await self._listen_task
            except asyncio.CancelledError:
                pass
        if self._ws and self._ws.open:
            await self._ws.close()
        self._ws = None
