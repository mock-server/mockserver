from __future__ import annotations

import mockserver


class TestPublicExports:
    def test_mockserver_client_exported(self):
        assert hasattr(mockserver, "MockServerClient")

    def test_async_client_exported(self):
        assert hasattr(mockserver, "AsyncMockServerClient")

    def test_model_classes_exported(self):
        expected = [
            "HttpRequest",
            "HttpResponse",
            "HttpForward",
            "HttpTemplate",
            "HttpClassCallback",
            "HttpObjectCallback",
            "HttpError",
            "HttpOverrideForwardedRequest",
            "HttpRequestAndHttpResponse",
            "Expectation",
            "ExpectationId",
            "OpenAPIDefinition",
            "OpenAPIExpectation",
            "Times",
            "TimeToLive",
            "Delay",
            "Verification",
            "VerificationTimes",
            "VerificationSequence",
            "Ports",
            "KeyToMultiValue",
            "Body",
            "SocketAddress",
            "ConnectionOptions",
            "RequestDefinition",
        ]
        for name in expected:
            assert hasattr(mockserver, name), f"{name} not exported from mockserver"

    def test_exception_classes_exported(self):
        expected = [
            "MockServerError",
            "MockServerConnectionError",
            "MockServerVerificationError",
            "MockServerCallbackError",
            "MockServerWebSocketError",
        ]
        for name in expected:
            assert hasattr(mockserver, name), f"{name} not exported from mockserver"

    def test_all_exports_listed(self):
        for name in mockserver.__all__:
            assert hasattr(mockserver, name), f"{name} in __all__ but not importable"
