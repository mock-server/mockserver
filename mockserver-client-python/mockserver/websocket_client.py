from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any, Callable

try:  # pragma: no cover - exercised via import behaviour
    import websockets
    import websockets.exceptions
except ImportError:  # websockets is only needed for WebSocket features; keep
    # `import mockserver` working without it and fail at first use instead.
    websockets = None

# Prefer the modern asyncio client (websockets >= 13). It accepts the
# ``additional_headers`` kwarg used below to carry the client-registration id.
# Fall back to the legacy client on older releases, where the equivalent kwarg
# is ``extra_headers``. ``_CONNECT_HEADERS_KWARG`` records which name to use so
# the registration header is sent regardless of the installed version.
if websockets is not None:
    try:
        from websockets.asyncio.client import connect as _ws_connect  # type: ignore

        _CONNECT_HEADERS_KWARG = "additional_headers"
    except ImportError:  # pragma: no cover - exercised only on legacy websockets
        from websockets.client import connect as _ws_connect  # type: ignore

        _CONNECT_HEADERS_KWARG = "extra_headers"
else:  # pragma: no cover - websockets not installed; fail at first use
    _ws_connect = None
    _CONNECT_HEADERS_KWARG = "additional_headers"

from mockserver.exceptions import MockServerCallbackError, MockServerWebSocketError
from mockserver.models import (
    HttpRequest,
    HttpRequestAndHttpResponse,
    HttpResponse,
    KeyToMultiValue,
)

logger = logging.getLogger(__name__)

WEB_SOCKET_CORRELATION_ID_HEADER_NAME = "WebSocketCorrelationId"
BREAKPOINT_ID_HEADER_NAME = "X-MockServer-BreakpointId"
CLIENT_REGISTRATION_ID_HEADER = "X-CLIENT-REGISTRATION-ID"

WEBSOCKET_PATH = "/_mockserver_callback_websocket"

TYPE_HTTP_REQUEST = "org.mockserver.model.HttpRequest"
TYPE_HTTP_RESPONSE = "org.mockserver.model.HttpResponse"
TYPE_HTTP_REQUEST_AND_RESPONSE = "org.mockserver.model.HttpRequestAndHttpResponse"
TYPE_CLIENT_ID_DTO = "org.mockserver.serialization.model.WebSocketClientIdDTO"
TYPE_ERROR_DTO = "org.mockserver.serialization.model.WebSocketErrorDTO"
TYPE_PAUSED_STREAM_FRAME_DTO = "org.mockserver.serialization.model.PausedStreamFrameDTO"
TYPE_STREAM_FRAME_DECISION_DTO = "org.mockserver.serialization.model.StreamFrameDecisionDTO"

MAX_RECONNECT_ATTEMPTS = 3


def _extract_correlation_id(request: HttpRequest) -> str | None:
    if request.headers is None:
        return None
    for header in request.headers:
        if header.name == WEB_SOCKET_CORRELATION_ID_HEADER_NAME:
            if header.values:
                return header.values[0]
    return None


def _extract_breakpoint_id(request: HttpRequest) -> str | None:
    """Extract the X-MockServer-BreakpointId header value from a request."""
    if request.headers is None:
        return None
    for header in request.headers:
        if header.name == BREAKPOINT_ID_HEADER_NAME:
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


def _ws_is_open(ws: Any) -> bool:
    """Return True if *ws* is a live, open connection.

    The legacy websockets client exposes a boolean ``.open`` attribute; the
    modern asyncio client does not, but reports ``.close_code is None`` while
    the connection is open. This bridges both so the transport works across
    websockets versions.
    """
    if ws is None:
        return False
    if hasattr(ws, "open"):
        return bool(ws.open)
    return getattr(ws, "close_code", None) is None


def _clean_context_path(context_path: str) -> str:
    if not context_path:
        return ""
    if not context_path.startswith("/"):
        return "/" + context_path
    return context_path


class MockServerWebSocketClient:
    def __init__(self) -> None:
        self._ws: Any = None
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
        self._bearer_token_supplier: Callable[[], str | None] | None = None
        # Per-breakpoint-id handlers for matcher-driven breakpoints
        self._breakpoint_request_handlers: dict[str, Callable] = {}
        self._breakpoint_response_handlers: dict[str, Callable] = {}
        self._breakpoint_stream_frame_handlers: dict[str, Callable] = {}

    @property
    def is_connected(self) -> bool:
        return _ws_is_open(self._ws)

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
        bearer_token_supplier: Callable[[], str | None] | None = None,
    ) -> str:
        """Open the callback WebSocket and wait for MockServer's client id.

        ``bearer_token_supplier`` is called on every (re)connect; a non-empty
        result is sent as ``Authorization: Bearer <token>`` on the upgrade.
        """
        self._bearer_token_supplier = bearer_token_supplier
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

        registration_headers = {CLIENT_REGISTRATION_ID_HEADER: registration_id}
        if self._bearer_token_supplier is not None:
            bearer = self._bearer_token_supplier()
            if bearer:
                registration_headers["Authorization"] = f"Bearer {bearer}"

        if _ws_connect is None:  # pragma: no cover - depends on environment
            raise MockServerWebSocketError(
                "the 'websockets' package is required for WebSocket callbacks: pip install websockets"
            )
        connect_kwargs: dict[str, Any] = {
            _CONNECT_HEADERS_KWARG: registration_headers,
            "ssl": self._ssl_context if self._secure else None,
            "open_timeout": 10,
            "ping_interval": 20,
            "ping_timeout": 20,
            "close_timeout": 5,
        }
        self._ws = await _ws_connect(uri, **connect_kwargs)

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

    def set_breakpoint_request_handler(self, breakpoint_id: str, handler: Callable) -> None:
        """Register a REQUEST-phase breakpoint handler keyed by breakpoint id."""
        if breakpoint_id and handler:
            self._breakpoint_request_handlers[breakpoint_id] = handler

    def set_breakpoint_response_handler(self, breakpoint_id: str, handler: Callable) -> None:
        """Register a RESPONSE-phase breakpoint handler keyed by breakpoint id."""
        if breakpoint_id and handler:
            self._breakpoint_response_handlers[breakpoint_id] = handler

    def set_breakpoint_stream_frame_handler(self, breakpoint_id: str, handler: Callable) -> None:
        """Register a stream-frame breakpoint handler keyed by breakpoint id."""
        if breakpoint_id and handler:
            self._breakpoint_stream_frame_handlers[breakpoint_id] = handler

    def remove_breakpoint_handlers(self, breakpoint_id: str) -> None:
        """Remove all handlers for the given breakpoint id."""
        if breakpoint_id:
            self._breakpoint_request_handlers.pop(breakpoint_id, None)
            self._breakpoint_response_handlers.pop(breakpoint_id, None)
            self._breakpoint_stream_frame_handlers.pop(breakpoint_id, None)

    def clear_breakpoint_handlers(self) -> None:
        """Remove all breakpoint handlers."""
        self._breakpoint_request_handlers.clear()
        self._breakpoint_response_handlers.clear()
        self._breakpoint_stream_frame_handlers.clear()

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
                    await asyncio.sleep(min(2 ** reconnect_attempts, 8))
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
            breakpoint_id = _extract_breakpoint_id(request)

            # Route to per-breakpoint-id handler if present
            bp_handler = self._breakpoint_request_handlers.get(breakpoint_id) if breakpoint_id else None
            if bp_handler is not None:
                await self._handle_breakpoint_request(request, correlation_id, bp_handler)
            elif self._forward_callback is not None:
                await self._handle_forward_request(request, correlation_id)
            elif self._response_callback is not None:
                await self._handle_response_request(request, correlation_id)
            return

        if msg_type == TYPE_HTTP_REQUEST_AND_RESPONSE:
            req_and_resp = HttpRequestAndHttpResponse.from_dict(json.loads(msg_value))
            correlation_id = _extract_correlation_id(req_and_resp.http_request)
            breakpoint_id = _extract_breakpoint_id(req_and_resp.http_request)

            bp_handler = self._breakpoint_response_handlers.get(breakpoint_id) if breakpoint_id else None
            if bp_handler is not None:
                await self._handle_breakpoint_response(
                    req_and_resp, correlation_id, bp_handler
                )
            elif self._forward_response_callback is not None:
                await self._handle_forward_response(req_and_resp, correlation_id)
            return

        if msg_type == TYPE_PAUSED_STREAM_FRAME_DTO:
            paused_frame = json.loads(msg_value)
            await self._handle_breakpoint_stream_frame(paused_frame)
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

    async def _handle_breakpoint_request(
        self,
        request: HttpRequest,
        correlation_id: str | None,
        handler: Callable,
    ) -> None:
        """Handle a REQUEST-phase breakpoint via a per-breakpoint-id handler."""
        try:
            result = handler(request)
            if asyncio.iscoroutine(result) or asyncio.isfuture(result):
                result = await result
            if result is None:
                result = request  # auto-continue
            if correlation_id:
                _add_correlation_id_header(result, correlation_id)
            if isinstance(result, HttpResponse):
                msg = _build_ws_message(TYPE_HTTP_RESPONSE, result.to_dict())
            else:
                msg = _build_ws_message(TYPE_HTTP_REQUEST, result.to_dict())
            await self._ws.send(msg)
        except Exception:
            logger.exception("Error in breakpoint request handler, auto-continuing")
            if correlation_id:
                _add_correlation_id_header(request, correlation_id)
            msg = _build_ws_message(TYPE_HTTP_REQUEST, request.to_dict())
            await self._ws.send(msg)

    async def _handle_breakpoint_response(
        self,
        req_and_resp: HttpRequestAndHttpResponse,
        correlation_id: str | None,
        handler: Callable,
    ) -> None:
        """Handle a RESPONSE-phase breakpoint via a per-breakpoint-id handler."""
        try:
            result = handler(req_and_resp.http_request, req_and_resp.http_response)
            if asyncio.iscoroutine(result) or asyncio.isfuture(result):
                result = await result
            if result is None:
                result = req_and_resp.http_response  # auto-continue
            if not isinstance(result, HttpResponse):
                raise MockServerCallbackError(
                    f"Breakpoint response handler must return HttpResponse, got {type(result)}"
                )
            if correlation_id:
                _add_correlation_id_header(result, correlation_id)
            msg = _build_ws_message(TYPE_HTTP_RESPONSE, result.to_dict())
            await self._ws.send(msg)
        except Exception:
            logger.exception("Error in breakpoint response handler, auto-continuing")
            resp = req_and_resp.http_response or HttpResponse()
            if correlation_id:
                _add_correlation_id_header(resp, correlation_id)
            msg = _build_ws_message(TYPE_HTTP_RESPONSE, resp.to_dict())
            await self._ws.send(msg)

    async def _handle_breakpoint_stream_frame(self, paused_frame: dict) -> None:
        """Handle a PausedStreamFrameDTO for RESPONSE_STREAM / INBOUND_STREAM phase."""
        breakpoint_id = paused_frame.get("breakpointId")
        correlation_id = paused_frame.get("correlationId", "")
        handler = self._breakpoint_stream_frame_handlers.get(breakpoint_id) if breakpoint_id else None

        decision: dict
        if handler is not None:
            try:
                result = handler(paused_frame)
                if asyncio.iscoroutine(result) or asyncio.isfuture(result):
                    result = await result
                if result is None:
                    decision = {"correlationId": correlation_id, "action": "CONTINUE"}
                else:
                    decision = result
                    decision["correlationId"] = correlation_id  # ensure echoed
            except Exception:
                logger.exception("Error in breakpoint stream frame handler, auto-continuing")
                decision = {"correlationId": correlation_id, "action": "CONTINUE"}
        else:
            # No handler -- auto-continue
            decision = {"correlationId": correlation_id, "action": "CONTINUE"}

        msg = json.dumps({
            "type": TYPE_STREAM_FRAME_DECISION_DTO,
            "value": json.dumps(decision),
        })
        await self._ws.send(msg)

    async def close(self) -> None:
        self._stopped = True
        if self._listen_task and not self._listen_task.done():
            self._listen_task.cancel()
            try:
                await self._listen_task
            except asyncio.CancelledError:
                pass
        if _ws_is_open(self._ws):
            await self._ws.close()
        self._ws = None
