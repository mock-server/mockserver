from __future__ import annotations

import asyncio
import json
import logging
import socket
import ssl
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Callable

from mockserver.exceptions import (
    MockServerConnectionError,
    MockServerError,
    MockServerVerificationError,
)
from mockserver.fluent import ForwardChainExpectation
from mockserver.models import (
    Expectation,
    HttpObjectCallback,
    HttpRequest,
    HttpRequestAndHttpResponse,
    HttpResponse,
    OpenAPIExpectation,
    Ports,
    TimeToLive,
    Times,
    Verification,
    VerificationSequence,
    VerificationTimes,
)
from mockserver.websocket_client import MockServerWebSocketClient

logger = logging.getLogger(__name__)


class AsyncMockServerClient:
    def __init__(
        self,
        host: str,
        port: int,
        context_path: str = "",
        secure: bool = False,
        ca_cert_path: str | None = None,
        tls_verify: bool = True,
    ) -> None:
        self._host = host
        self._port = port
        self._context_path = context_path
        self._secure = secure
        self._ca_cert_path = ca_cert_path
        self._tls_verify = tls_verify
        self._websocket_clients: list[MockServerWebSocketClient] = []

        scheme = "https" if secure else "http"
        ctx_path = ""
        if context_path:
            ctx_path = context_path if context_path.startswith("/") else f"/{context_path}"
        self._base_url = f"{scheme}://{host}:{port}{ctx_path}"

        self._ssl_context: ssl.SSLContext | None = None
        if secure:
            self._ssl_context = ssl.create_default_context()
            if ca_cert_path:
                self._ssl_context.load_verify_locations(ca_cert_path)
            elif not tls_verify:
                self._ssl_context.check_hostname = False
                self._ssl_context.verify_mode = ssl.CERT_NONE

    async def _request(
        self,
        method: str,
        path: str,
        body: str | None = None,
        query_params: dict[str, str] | None = None,
    ) -> tuple[int, str]:
        url = f"{self._base_url}{path}"
        if query_params:
            url = f"{url}?{urllib.parse.urlencode(query_params)}"

        data = body.encode("utf-8") if body else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json; charset=utf-8")

        def _do_request() -> tuple[int, str]:
            try:
                response = urllib.request.urlopen(
                    req, context=self._ssl_context, timeout=60
                )
                return response.status, response.read().decode("utf-8")
            except urllib.error.HTTPError as e:
                return e.code, e.read().decode("utf-8")
            except socket.timeout as e:
                raise MockServerConnectionError(
                    f"Request to MockServer at {self._base_url} timed out: {e}"
                ) from e
            except (urllib.error.URLError, OSError) as e:
                raise MockServerConnectionError(
                    f"Failed to connect to MockServer at {self._base_url}: {e}"
                ) from e

        return await asyncio.to_thread(_do_request)

    async def upsert(self, *expectations: Expectation) -> list[Expectation]:
        body = json.dumps([e.to_dict() for e in expectations])
        status, response_body = await self._request("PUT", "/mockserver/expectation", body)
        if status == 400:
            raise MockServerError(f"Invalid expectation: {response_body}")
        if status >= 400:
            raise MockServerError(
                f"Failed to upsert expectations (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return list(expectations)

    async def open_api_expectation(self, expectation: OpenAPIExpectation) -> None:
        body = json.dumps(expectation.to_dict())
        status, response_body = await self._request("PUT", "/mockserver/openapi", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to create OpenAPI expectation (status={status}): {response_body}"
            )

    async def clear(
        self,
        request: HttpRequest | None = None,
        clear_type: str | None = None,
    ) -> None:
        query_params = {}
        if clear_type:
            query_params["type"] = clear_type
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT", "/mockserver/clear", body, query_params or None
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to clear (status={status}): {response_body}"
            )

    async def clear_by_id(
        self,
        expectation_id: str,
        clear_type: str | None = None,
    ) -> None:
        query_params = {}
        if clear_type:
            query_params["type"] = clear_type
        body = json.dumps({"id": expectation_id})
        status, response_body = await self._request(
            "PUT", "/mockserver/clear", body, query_params or None
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to clear by id (status={status}): {response_body}"
            )

    async def reset(self) -> None:
        status, response_body = await self._request("PUT", "/mockserver/reset")
        if status >= 400:
            raise MockServerError(
                f"Failed to reset (status={status}): {response_body}"
            )

    async def verify(
        self,
        request: HttpRequest,
        times: VerificationTimes | None = None,
    ) -> None:
        verification = Verification(http_request=request, times=times)
        body = json.dumps(verification.to_dict())
        status, response_body = await self._request("PUT", "/mockserver/verify", body)
        if status == 406:
            raise MockServerVerificationError(response_body)
        if status >= 400:
            raise MockServerError(
                f"Failed to verify (status={status}): {response_body}"
            )

    async def verify_sequence(self, *requests: HttpRequest) -> None:
        verification = VerificationSequence(
            http_requests=list(requests)
        )
        body = json.dumps(verification.to_dict())
        status, response_body = await self._request(
            "PUT", "/mockserver/verifySequence", body
        )
        if status == 406:
            raise MockServerVerificationError(response_body)
        if status >= 400:
            raise MockServerError(
                f"Failed to verify sequence (status={status}): {response_body}"
            )

    async def verify_zero_interactions(self) -> None:
        await self.verify(
            HttpRequest(),
            VerificationTimes(at_most=0),
        )

    async def retrieve_recorded_requests(
        self, request: HttpRequest | None = None
    ) -> list[HttpRequest]:
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "REQUESTS", "format": "JSON"},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve recorded requests (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [HttpRequest.from_dict(r) for r in parsed]
        return []

    async def retrieve_active_expectations(
        self, request: HttpRequest | None = None
    ) -> list[Expectation]:
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "ACTIVE_EXPECTATIONS", "format": "JSON"},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve active expectations (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return []

    async def retrieve_recorded_expectations(
        self, request: HttpRequest | None = None
    ) -> list[Expectation]:
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "RECORDED_EXPECTATIONS", "format": "JSON"},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve recorded expectations (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return []

    async def retrieve_recorded_requests_and_responses(
        self, request: HttpRequest | None = None
    ) -> list[HttpRequestAndHttpResponse]:
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "REQUEST_RESPONSES", "format": "JSON"},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve request/responses (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [HttpRequestAndHttpResponse.from_dict(rr) for rr in parsed]
        return []

    async def retrieve_log_messages(
        self, request: HttpRequest | None = None
    ) -> list[str]:
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "LOGS"},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve log messages (status={status}): {response_body}"
            )
        if response_body:
            try:
                parsed = json.loads(response_body)
                if isinstance(parsed, list):
                    return parsed
            except json.JSONDecodeError:
                return response_body.split("------------------------------------\n")
        return []

    async def bind(self, *ports: int) -> list[int]:
        body = json.dumps(Ports(ports=list(ports)).to_dict())
        status, response_body = await self._request("PUT", "/mockserver/bind", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to bind ports (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            return Ports.from_dict(parsed).ports
        return []

    async def stop(self) -> None:
        try:
            await self._request("PUT", "/mockserver/stop")
        except MockServerConnectionError:
            pass

    async def has_started(self, attempts: int = 10, timeout: float = 0.5) -> bool:
        for i in range(attempts):
            try:
                status, _ = await self._request("PUT", "/mockserver/status")
                if status == 200:
                    return True
            except MockServerConnectionError:
                pass
            if i < attempts - 1:
                await asyncio.sleep(timeout)
        return False

    def when(
        self,
        request: HttpRequest,
        times: Times | None = None,
        time_to_live: TimeToLive | None = None,
        priority: int | None = None,
    ) -> ForwardChainExpectation:
        expectation = Expectation(
            http_request=request,
            times=times,
            time_to_live=time_to_live,
            priority=priority,
        )
        return ForwardChainExpectation(self, expectation)

    async def mock_with_callback(
        self,
        request: HttpRequest,
        callback: Callable,
        times: Times | None = None,
        time_to_live: TimeToLive | None = None,
    ) -> list[Expectation]:
        client_id = await self._register_websocket_callback("response", callback)
        expectation = Expectation(
            http_request=request,
            http_response_object_callback=HttpObjectCallback(client_id=client_id),
            times=times,
            time_to_live=time_to_live,
        )
        return await self.upsert(expectation)

    async def mock_with_forward_callback(
        self,
        request: HttpRequest,
        forward_callback: Callable,
        response_callback: Callable | None = None,
        times: Times | None = None,
        time_to_live: TimeToLive | None = None,
    ) -> list[Expectation]:
        client_id = await self._register_websocket_callback(
            "forward", forward_callback, response_callback
        )
        obj_callback = HttpObjectCallback(client_id=client_id)
        if response_callback is not None:
            obj_callback.response_callback = True
        expectation = Expectation(
            http_request=request,
            http_forward_object_callback=obj_callback,
            times=times,
            time_to_live=time_to_live,
        )
        return await self.upsert(expectation)

    async def _register_websocket_callback(
        self,
        callback_type: str,
        callback_fn: Callable,
        forward_response_fn: Callable | None = None,
    ) -> str:
        ws_client = MockServerWebSocketClient()
        client_id = await ws_client.connect(
            self._host,
            self._port,
            self._context_path,
            self._secure,
            self._ca_cert_path,
            tls_verify=self._tls_verify,
        )

        if callback_type == "response":
            ws_client.register_response_callback(callback_fn)
        elif callback_type == "forward":
            ws_client.register_forward_callback(callback_fn, forward_response_fn)

        ws_client._listen_task = asyncio.create_task(ws_client.listen())
        self._websocket_clients.append(ws_client)
        return client_id

    async def close(self) -> None:
        for ws_client in self._websocket_clients:
            await ws_client.close()
        self._websocket_clients.clear()

    async def __aenter__(self) -> AsyncMockServerClient:
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        await self.close()
