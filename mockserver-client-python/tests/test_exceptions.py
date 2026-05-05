from __future__ import annotations

from mockserver.exceptions import (
    MockServerCallbackError,
    MockServerConnectionError,
    MockServerError,
    MockServerVerificationError,
    MockServerWebSocketError,
)


class TestExceptionHierarchy:
    def test_connection_error_is_mockserver_error(self):
        assert issubclass(MockServerConnectionError, MockServerError)

    def test_verification_error_is_mockserver_error(self):
        assert issubclass(MockServerVerificationError, MockServerError)

    def test_verification_error_is_assertion_error(self):
        assert issubclass(MockServerVerificationError, AssertionError)

    def test_callback_error_is_mockserver_error(self):
        assert issubclass(MockServerCallbackError, MockServerError)

    def test_websocket_error_is_mockserver_error(self):
        assert issubclass(MockServerWebSocketError, MockServerError)

    def test_mockserver_error_is_exception(self):
        assert issubclass(MockServerError, Exception)

    def test_verification_error_message(self):
        err = MockServerVerificationError("expected 3 but got 0")
        assert str(err) == "expected 3 but got 0"

    def test_connection_error_message(self):
        err = MockServerConnectionError("Connection refused")
        assert str(err) == "Connection refused"

    def test_verification_error_can_be_caught_as_assertion(self):
        try:
            raise MockServerVerificationError("fail")
        except AssertionError as e:
            assert str(e) == "fail"
