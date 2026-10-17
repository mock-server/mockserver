from __future__ import annotations

import json
import logging
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from mockserver.client import MockServerClient
from mockserver.exceptions import MockServerError
from mockserver.models import Expectation, MockMode


class ControlPlaneHandler(BaseHTTPRequestHandler):
    """A tiny stub MockServer that records the last request and replies with a
    configurable status/body, mirroring the harness used in ``test_client.py``.
    """

    response_status = 200
    response_body = "[]"
    last_request_body = None
    last_content_type = None
    last_path = None
    last_method = None

    def _respond(self):
        self.send_response(ControlPlaneHandler.response_status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(ControlPlaneHandler.response_body.encode("utf-8"))

    def do_PUT(self):
        content_length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(content_length) if content_length > 0 else b""
        ControlPlaneHandler.last_request_body = raw.decode("utf-8", errors="replace")
        ControlPlaneHandler.last_content_type = self.headers.get("Content-Type")
        ControlPlaneHandler.last_path = self.path
        ControlPlaneHandler.last_method = "PUT"
        self._respond()

    def do_GET(self):
        ControlPlaneHandler.last_request_body = None
        ControlPlaneHandler.last_path = self.path
        ControlPlaneHandler.last_method = "GET"
        self._respond()

    def log_message(self, format, *args):
        pass


@pytest.fixture
def control_plane_server():
    server = HTTPServer(("127.0.0.1", 0), ControlPlaneHandler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever)
    thread.daemon = True
    thread.start()
    ControlPlaneHandler.response_status = 200
    ControlPlaneHandler.response_body = "[]"
    ControlPlaneHandler.last_request_body = None
    ControlPlaneHandler.last_content_type = None
    ControlPlaneHandler.last_path = None
    ControlPlaneHandler.last_method = None
    yield port
    server.shutdown()


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------


class TestMetrics:
    def test_retrieve_metrics(self, control_plane_server):
        ControlPlaneHandler.response_body = json.dumps({"REQUESTS_RECEIVED_COUNT": 7})
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.retrieve_metrics()
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path.startswith("/mockserver/retrieve")
            assert "type=METRICS" in ControlPlaneHandler.last_path
            assert result["REQUESTS_RECEIVED_COUNT"] == 7

    def test_scrape_metrics(self, control_plane_server):
        ControlPlaneHandler.response_body = "# HELP foo\nfoo 1.0\n"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.scrape_metrics()
            assert ControlPlaneHandler.last_method == "GET"
            assert ControlPlaneHandler.last_path == "/mockserver/metrics"
            assert "foo 1.0" in result

    def test_scrape_metrics_disabled(self, control_plane_server):
        ControlPlaneHandler.response_status = 404
        ControlPlaneHandler.response_body = ""
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            with pytest.raises(MockServerError, match="metrics are disabled"):
                client.scrape_metrics()


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------


class TestConfiguration:
    def test_retrieve_configuration(self, control_plane_server):
        ControlPlaneHandler.response_body = json.dumps({"logLevel": "INFO"})
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.retrieve_configuration()
            assert ControlPlaneHandler.last_method == "GET"
            assert ControlPlaneHandler.last_path == "/mockserver/configuration"
            assert json.loads(result)["logLevel"] == "INFO"

    def test_update_configuration(self, control_plane_server):
        ControlPlaneHandler.response_body = json.dumps({"logLevel": "DEBUG"})
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.update_configuration('{"logLevel":"DEBUG"}')
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path == "/mockserver/configuration"
            assert json.loads(ControlPlaneHandler.last_request_body)["logLevel"] == "DEBUG"
            assert json.loads(result)["logLevel"] == "DEBUG"

    def test_update_configuration_none_sends_empty(self, control_plane_server):
        ControlPlaneHandler.response_body = "{}"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            client.update_configuration(None)
            assert ControlPlaneHandler.last_request_body == ""


# ---------------------------------------------------------------------------
# Drift detection
# ---------------------------------------------------------------------------


class TestDrift:
    def test_retrieve_drift(self, control_plane_server):
        ControlPlaneHandler.response_body = json.dumps(
            {"count": 1, "drifts": [{"path": "/foo", "driftType": "STATUS_CODE"}]}
        )
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.retrieve_drift()
            assert ControlPlaneHandler.last_method == "GET"
            assert ControlPlaneHandler.last_path == "/mockserver/drift"
            assert result["count"] == 1
            assert result["drifts"][0]["path"] == "/foo"

    def test_retrieve_drift_empty(self, control_plane_server):
        ControlPlaneHandler.response_body = ""
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.retrieve_drift()
            assert result == {}

    def test_clear_drift(self, control_plane_server):
        ControlPlaneHandler.response_body = '{"status":"cleared"}'
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            client.clear_drift()
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path == "/mockserver/drift/clear"


# ---------------------------------------------------------------------------
# Pact
# ---------------------------------------------------------------------------


class TestPact:
    def test_pact_import(self, control_plane_server):
        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = "[]"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            client.pact_import('{"consumer":{"name":"c"}}')
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path == "/mockserver/pact/import"
            assert json.loads(ControlPlaneHandler.last_request_body)["consumer"]["name"] == "c"

    def test_pact_import_rejects_blank(self, control_plane_server):
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            with pytest.raises(ValueError):
                client.pact_import("   ")

    def test_pact_export(self, control_plane_server):
        ControlPlaneHandler.response_body = json.dumps({"interactions": []})
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.pact_export("my-consumer", "my-provider")
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path.startswith("/mockserver/pact")
            assert "consumer=my-consumer" in ControlPlaneHandler.last_path
            assert "provider=my-provider" in ControlPlaneHandler.last_path
            assert "interactions" in result

    def test_pact_export_omits_blank_params(self, control_plane_server):
        ControlPlaneHandler.response_body = "{}"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            client.pact_export()
            assert ControlPlaneHandler.last_path == "/mockserver/pact"

    def test_pact_verify_pass(self, control_plane_server):
        ControlPlaneHandler.response_status = 202
        ControlPlaneHandler.response_body = json.dumps({"verified": True})
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.pact_verify('{"interactions":[]}')
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path == "/mockserver/pact/verify"
            assert json.loads(result)["verified"] is True

    def test_pact_verify_fail_returns_report(self, control_plane_server):
        # A 406 FAIL verdict is an expected outcome, not an exception: the report
        # body is returned so the caller can inspect it (matches Java semantics).
        ControlPlaneHandler.response_status = 406
        ControlPlaneHandler.response_body = json.dumps({"verified": False})
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.pact_verify('{"interactions":[]}')
            assert ControlPlaneHandler.last_path == "/mockserver/pact/verify"
            assert json.loads(result)["verified"] is False

    def test_pact_verify_bad_request_raises(self, control_plane_server):
        ControlPlaneHandler.response_status = 400
        ControlPlaneHandler.response_body = json.dumps({"error": "malformed"})
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            with pytest.raises(MockServerError, match="Failed to verify pact"):
                client.pact_verify('{"interactions":[]}')


# ---------------------------------------------------------------------------
# File store
# ---------------------------------------------------------------------------


class TestFileStore:
    def test_store_file_text(self, control_plane_server):
        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = json.dumps({"name": "a.txt", "size": 5})
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.store_file("a.txt", "hello")
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path == "/mockserver/files/store"
            sent = json.loads(ControlPlaneHandler.last_request_body)
            assert sent["name"] == "a.txt"
            assert sent["content"] == "hello"
            assert "base64" not in sent
            assert result["size"] == 5

    def test_store_file_bytes_base64(self, control_plane_server):
        import base64

        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = json.dumps({"name": "b.bin", "size": 3})
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            client.store_file("b.bin", b"\x00\x01\x02")
            sent = json.loads(ControlPlaneHandler.last_request_body)
            assert sent["base64"] is True
            assert base64.b64decode(sent["content"]) == b"\x00\x01\x02"

    def test_retrieve_file(self, control_plane_server):
        ControlPlaneHandler.response_status = 200
        ControlPlaneHandler.response_body = "file-content-here"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.retrieve_file("a.txt")
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path == "/mockserver/files/retrieve"
            assert json.loads(ControlPlaneHandler.last_request_body)["name"] == "a.txt"
            assert result == "file-content-here"

    def test_retrieve_file_not_found_raises(self, control_plane_server):
        ControlPlaneHandler.response_status = 404
        ControlPlaneHandler.response_body = "file not found: missing.txt"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            with pytest.raises(MockServerError, match="file not found: missing.txt"):
                client.retrieve_file("missing.txt")

    def test_list_files(self, control_plane_server):
        ControlPlaneHandler.response_body = json.dumps(["a.txt", "b.bin"])
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.list_files()
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path == "/mockserver/files/list"
            assert result == ["a.txt", "b.bin"]

    def test_delete_file(self, control_plane_server):
        ControlPlaneHandler.response_status = 200
        ControlPlaneHandler.response_body = ""
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            client.delete_file("a.txt")
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path == "/mockserver/files/delete"
            assert json.loads(ControlPlaneHandler.last_request_body)["name"] == "a.txt"

    def test_delete_file_not_found_raises(self, control_plane_server):
        ControlPlaneHandler.response_status = 404
        ControlPlaneHandler.response_body = "file not found: a.txt"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            with pytest.raises(MockServerError, match="Failed to delete file"):
                client.delete_file("a.txt")


# ---------------------------------------------------------------------------
# Import (HAR / Postman / auto-detect)
# ---------------------------------------------------------------------------


class TestImport:
    def test_import_har(self, control_plane_server):
        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = json.dumps(
            [{"httpRequest": {"path": "/x"}, "httpResponse": {"statusCode": 200}}]
        )
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.import_har('{"log":{"entries":[]}}')
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path.startswith("/mockserver/import")
            assert "format=har" in ControlPlaneHandler.last_path
            assert len(result) == 1
            assert isinstance(result[0], Expectation)

    def test_import_postman_collection(self, control_plane_server):
        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = "[]"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            client.import_postman_collection('{"info":{},"item":[]}')
            assert "format=postman" in ControlPlaneHandler.last_path

    def test_import_document_auto_detect(self, control_plane_server):
        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = "[]"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            client.import_document('{"log":{"entries":[]}}')
            assert ControlPlaneHandler.last_path == "/mockserver/import"

    def test_import_document_returns_expectations_and_logs_warnings(
        self, control_plane_server, caplog
    ):
        # the wiremock, mountebank and mockoon importers reply with an object
        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = json.dumps(
            {
                "expectations": [
                    {"httpRequest": {"path": "/api/xml"}, "httpResponse": {"statusCode": 200}}
                ],
                "warnings": [
                    {
                        "item": "GET /api/xml",
                        "construct": "response.transformers",
                        "detail": "response templating is not translated",
                    }
                ],
            }
        )
        with caplog.at_level(logging.WARNING, logger="mockserver.async_client"):
            with MockServerClient("127.0.0.1", control_plane_server) as client:
                result = client.import_document('{"mappings":[]}', "wiremock")
        assert "format=wiremock" in ControlPlaneHandler.last_path
        assert len(result) == 1
        assert isinstance(result[0], Expectation)
        assert result[0].http_request.path == "/api/xml"
        warnings = [r.getMessage() for r in caplog.records if r.levelno == logging.WARNING]
        assert len(warnings) == 1
        assert "wiremock" in warnings[0]
        assert (
            "[GET /api/xml] response.transformers: response templating is not translated"
            in warnings[0]
        )

    def test_import_document_object_reply_without_warnings_does_not_log(
        self, control_plane_server, caplog
    ):
        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = json.dumps(
            {
                "expectations": [
                    {"httpRequest": {"path": "/r"}, "httpResponse": {"statusCode": 200}}
                ],
                "warnings": [],
            }
        )
        with caplog.at_level(logging.WARNING, logger="mockserver.async_client"):
            with MockServerClient("127.0.0.1", control_plane_server) as client:
                result = client.import_document('{"routes":[]}', "mockoon")
        assert len(result) == 1
        assert not [r for r in caplog.records if r.levelno == logging.WARNING]

    def test_import_document_rejects_recording_without_sending(self, control_plane_server):
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            for format in ("recording", "Recording", " RECORDING "):
                with pytest.raises(
                    ValueError, match=r"PUT /mockserver/import\?format=recording"
                ):
                    client.import_document('{"httpRequest":{}}', format)
        assert ControlPlaneHandler.last_method is None

    def test_import_document_object_reply_without_expectations_raises(
        self, control_plane_server
    ):
        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = '{"error":"upstream unavailable"}'
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            with pytest.raises(MockServerError, match="upstream unavailable"):
                client.import_document('{"mappings":[]}', "wiremock")

    def test_import_document_rejects_blank(self, control_plane_server):
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            with pytest.raises(ValueError):
                client.import_document("")


# ---------------------------------------------------------------------------
# Operating mode
# ---------------------------------------------------------------------------


class TestMode:
    def test_set_mode(self, control_plane_server):
        ControlPlaneHandler.response_body = json.dumps(
            {"mode": "SPY", "proxyUnmatchedRequests": True}
        )
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.set_mode(MockMode.SPY)
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path.startswith("/mockserver/mode")
            assert "mode=SPY" in ControlPlaneHandler.last_path
            assert result["mode"] == "SPY"
            assert result["proxyUnmatchedRequests"] is True

    def test_mock_mode_constants(self):
        assert MockMode.SIMULATE == "SIMULATE"
        assert MockMode.SPY == "SPY"
        assert MockMode.CAPTURE == "CAPTURE"

    def test_retrieve_mode(self, control_plane_server):
        ControlPlaneHandler.response_body = json.dumps(
            {"mode": "SIMULATE", "proxyUnmatchedRequests": False}
        )
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.retrieve_mode()
            assert ControlPlaneHandler.last_method == "GET"
            assert ControlPlaneHandler.last_path == "/mockserver/mode"
            assert result["mode"] == "SIMULATE"


# ---------------------------------------------------------------------------
# WSDL
# ---------------------------------------------------------------------------


class TestWsdl:
    def test_wsdl_expectation(self, control_plane_server):
        ControlPlaneHandler.response_status = 201
        ControlPlaneHandler.response_body = json.dumps(
            [{"httpRequest": {"path": "/svc"}, "httpResponse": {"statusCode": 200}}]
        )
        wsdl = "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"></definitions>"
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            result = client.wsdl_expectation(wsdl)
            assert ControlPlaneHandler.last_method == "PUT"
            assert ControlPlaneHandler.last_path == "/mockserver/wsdl"
            assert ControlPlaneHandler.last_content_type.startswith("text/xml")
            assert ControlPlaneHandler.last_request_body == wsdl
            assert len(result) == 1
            assert isinstance(result[0], Expectation)

    def test_wsdl_expectation_rejects_blank(self, control_plane_server):
        with MockServerClient("127.0.0.1", control_plane_server) as client:
            with pytest.raises(ValueError):
                client.wsdl_expectation("  ")
