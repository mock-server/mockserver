from __future__ import annotations


class MockServerError(Exception):
    pass


class MockServerConnectionError(MockServerError):
    pass


class MockServerVerificationError(MockServerError, AssertionError):
    pass


class MockServerCallbackError(MockServerError):
    pass


class MockServerWebSocketError(MockServerError):
    pass
