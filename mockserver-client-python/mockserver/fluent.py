from __future__ import annotations

import asyncio
from typing import TYPE_CHECKING, Callable

from mockserver.models import (
    Delay,
    Expectation,
    HttpError,
    HttpForward,
    HttpObjectCallback,
    HttpOverrideForwardedRequest,
    HttpResponse,
    HttpSseResponse,
    HttpTemplate,
    HttpWebSocketResponse,
)

if TYPE_CHECKING:
    from mockserver.async_client import AsyncMockServerClient


class ForwardChainExpectation:
    def __init__(
        self,
        client: AsyncMockServerClient,
        expectation: Expectation,
    ) -> None:
        self._client = client
        self._expectation = expectation

    def with_id(self, id: str) -> ForwardChainExpectation:
        self._expectation.id = id
        return self

    def with_priority(self, priority: int) -> ForwardChainExpectation:
        self._expectation.priority = priority
        return self

    async def respond(self, response_or_callback) -> list[Expectation]:
        if callable(response_or_callback):
            client_id = await self._client._register_websocket_callback(
                "response", response_or_callback
            )
            self._expectation.http_response_object_callback = HttpObjectCallback(
                client_id=client_id
            )
        elif isinstance(response_or_callback, HttpResponse):
            self._expectation.http_response = response_or_callback
        elif isinstance(response_or_callback, HttpTemplate):
            self._expectation.http_response_template = response_or_callback
        else:
            raise TypeError(
                f"Expected HttpResponse, HttpTemplate, or callable, got {type(response_or_callback).__name__}"
            )
        return await self._client.upsert(self._expectation)

    async def respond_with_delay(
        self, response: HttpResponse, delay: Delay
    ) -> list[Expectation]:
        response.delay = delay
        self._expectation.http_response = response
        return await self._client.upsert(self._expectation)

    async def forward(
        self,
        forward_or_callback,
        response_callback: Callable | None = None,
    ) -> list[Expectation]:
        if callable(forward_or_callback):
            client_id = await self._client._register_websocket_callback(
                "forward", forward_or_callback, response_callback
            )
            obj_callback = HttpObjectCallback(client_id=client_id)
            if response_callback is not None:
                obj_callback.response_callback = True
            self._expectation.http_forward_object_callback = obj_callback
        elif isinstance(forward_or_callback, HttpForward):
            self._expectation.http_forward = forward_or_callback
        elif isinstance(forward_or_callback, HttpOverrideForwardedRequest):
            self._expectation.http_override_forwarded_request = forward_or_callback
        elif isinstance(forward_or_callback, HttpTemplate):
            self._expectation.http_forward_template = forward_or_callback
        else:
            raise TypeError(
                f"Expected HttpForward, HttpOverrideForwardedRequest, HttpTemplate, or callable, got {type(forward_or_callback).__name__}"
            )
        return await self._client.upsert(self._expectation)

    async def forward_with_delay(
        self, forward: HttpForward, delay: Delay
    ) -> list[Expectation]:
        forward.delay = delay
        self._expectation.http_forward = forward
        return await self._client.upsert(self._expectation)

    async def respond_with_sse(self, sse_response: HttpSseResponse) -> list[Expectation]:
        if not isinstance(sse_response, HttpSseResponse):
            raise TypeError(
                f"Expected HttpSseResponse, got {type(sse_response).__name__}"
            )
        self._expectation.http_sse_response = sse_response
        return await self._client.upsert(self._expectation)

    async def respond_with_websocket(self, websocket_response: HttpWebSocketResponse) -> list[Expectation]:
        if not isinstance(websocket_response, HttpWebSocketResponse):
            raise TypeError(
                f"Expected HttpWebSocketResponse, got {type(websocket_response).__name__}"
            )
        self._expectation.http_websocket_response = websocket_response
        return await self._client.upsert(self._expectation)

    async def error(self, error: HttpError) -> list[Expectation]:
        self._expectation.http_error = error
        return await self._client.upsert(self._expectation)

