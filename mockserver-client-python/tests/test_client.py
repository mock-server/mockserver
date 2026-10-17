from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, HTTPServer
import threading

import pytest

from mockserver.client import MockServerClient, SyncForwardChainExpectation
from mockserver.exceptions import MockServerError, MockServerVerificationError
from mockserver.models import (
    Delay,
    DnsRequestDefinition,
    Expectation,
    HttpChaosProfile,
    HttpError,
    HttpForward,
    HttpRequest,
    HttpResponse,
    LoadCapture,
    LoadFeeder,
    LoadPacing,
    LoadProfile,
    LoadScenario,
    LoadShape,
    LoadStage,
    LoadStep,
    LoadThreshold,
    OpenAPIExpectation,
    Times,
    VerificationTimes,
)


class SyncMockHandler(BaseHTTPRequestHandler):
    response_status = 200
    response_body = "[]"
    last_request_body = None
    last_request_bytes = None
    last_content_type = None
    last_path = None
    last_method = None
    request_count = 0

    def do_PUT(self):
        content_length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(content_length) if content_length > 0 else b""
        SyncMockHandler.last_request_bytes = raw
        SyncMockHandler.last_content_type = self.headers.get("Content-Type")
        SyncMockHandler.last_request_body = raw.decode("utf-8", errors="replace")
        SyncMockHandler.last_path = self.path
        SyncMockHandler.last_method = "PUT"
        SyncMockHandler.request_count += 1

        self.send_response(SyncMockHandler.response_status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(SyncMockHandler.response_body.encode("utf-8"))

    def do_GET(self):
        SyncMockHandler.last_request_body = None
        SyncMockHandler.last_path = self.path
        SyncMockHandler.last_method = "GET"
        SyncMockHandler.request_count += 1

        self.send_response(SyncMockHandler.response_status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(SyncMockHandler.response_body.encode("utf-8"))

    def do_DELETE(self):
        SyncMockHandler.last_request_body = None
        SyncMockHandler.last_path = self.path
        SyncMockHandler.last_method = "DELETE"
        SyncMockHandler.request_count += 1

        self.send_response(SyncMockHandler.response_status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(SyncMockHandler.response_body.encode("utf-8"))

    def log_message(self, format, *args):
        pass


@pytest.fixture
def sync_mock_server():
    server = HTTPServer(("127.0.0.1", 0), SyncMockHandler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever)
    thread.daemon = True
    thread.start()
    SyncMockHandler.response_status = 200
    SyncMockHandler.response_body = "[]"
    SyncMockHandler.last_request_body = None
    SyncMockHandler.last_request_bytes = None
    SyncMockHandler.last_content_type = None
    SyncMockHandler.last_path = None
    SyncMockHandler.last_method = None
    SyncMockHandler.request_count = 0
    yield port
    server.shutdown()


class TestSyncClientInit:
    def test_creates_async_client(self, sync_mock_server):
        client = MockServerClient("127.0.0.1", sync_mock_server)
        try:
            assert client._async_client._host == "127.0.0.1"
            assert client._async_client._port == sync_mock_server
        finally:
            client.close()


class TestSyncContextManager:
    def test_context_manager(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            assert client is not None


class TestSyncUpsert:
    def test_upsert(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "id": "sync-exp",
            "httpRequest": {"path": "/sync"},
            "httpResponse": {"statusCode": 200},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.upsert(
                Expectation(
                    http_request=HttpRequest(path="/sync"),
                    http_response=HttpResponse(status_code=200),
                )
            )
            assert len(result) == 1
            assert result[0].id == "sync-exp"


class TestSyncClear:
    def test_clear(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.clear(HttpRequest(path="/test"))
            assert "/mockserver/clear" in SyncMockHandler.last_path

    def test_clear_by_id(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.clear_by_id("exp-abc")
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["id"] == "exp-abc"


class TestSyncReset:
    def test_reset(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.reset()
            assert SyncMockHandler.last_path == "/mockserver/reset"


class TestSyncVerify:
    def test_verify_success(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.verify(HttpRequest(path="/test"))
            assert "/mockserver/verify" in SyncMockHandler.last_path

    def test_verify_failure(self, sync_mock_server):
        SyncMockHandler.response_status = 406
        SyncMockHandler.response_body = "Verification failed"
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerVerificationError):
                client.verify(HttpRequest(path="/test"))

    def test_verify_sequence(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.verify_sequence(
                HttpRequest(path="/a"),
                HttpRequest(path="/b"),
            )
            assert "/mockserver/verifySequence" in SyncMockHandler.last_path

    def test_verify_with_response(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.verify(
                HttpRequest(path="/test"),
                response=HttpResponse(status_code=200),
            )
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["httpRequest"]["path"] == "/test"
            assert sent["httpResponse"]["statusCode"] == 200

    def test_verify_response_only(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.verify(
                response=HttpResponse(status_code=200),
            )
            sent = json.loads(SyncMockHandler.last_request_body)
            assert "httpRequest" not in sent
            assert sent["httpResponse"]["statusCode"] == 200

    def test_verify_sequence_with_responses(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.verify_sequence(
                HttpRequest(path="/a"),
                HttpRequest(path="/b"),
                responses=[
                    HttpResponse(status_code=200),
                    HttpResponse(status_code=201),
                ],
            )
            sent = json.loads(SyncMockHandler.last_request_body)
            assert len(sent["httpRequests"]) == 2
            assert len(sent["httpResponses"]) == 2
            assert sent["httpResponses"][0]["statusCode"] == 200
            assert sent["httpResponses"][1]["statusCode"] == 201

    def test_verify_zero_interactions(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.verify_zero_interactions()
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["times"]["atMost"] == 0


class TestSyncRetrieve:
    def test_retrieve_recorded_requests(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"method": "GET", "path": "/recorded"},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_recorded_requests()
            assert len(result) == 1
            assert result[0].path == "/recorded"

    def test_retrieve_recorded_requests_with_dns_filter(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"dnsName": "api.example.com", "dnsType": "A", "dnsClass": "IN"},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_recorded_requests(
                DnsRequestDefinition.dns_request("api.example.com", "A")
            )
            assert json.loads(SyncMockHandler.last_request_body)["dnsName"] == "api.example.com"
            assert len(result) == 1
            assert isinstance(result[0], DnsRequestDefinition)
            assert result[0].dns_name == "api.example.com"
            assert result[0].dns_type == "A"

    def test_retrieve_active_expectations(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"id": "ae1", "httpRequest": {"path": "/active"}},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_active_expectations()
            assert len(result) == 1
            assert result[0].id == "ae1"

    def test_retrieve_recorded_expectations(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"id": "re1"},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_recorded_expectations()
            assert len(result) == 1

    def test_retrieve_recorded_requests_by_id(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"method": "GET", "path": "/recorded"},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_recorded_requests_by_id("exp-abc")
            assert len(result) == 1
            assert result[0].path == "/recorded"
            assert json.loads(SyncMockHandler.last_request_body)["id"] == "exp-abc"
            assert "type=REQUESTS" in SyncMockHandler.last_path

    def test_retrieve_active_expectations_by_id(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"id": "exp-abc", "httpRequest": {"path": "/active"}},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_active_expectations_by_id("exp-abc")
            assert len(result) == 1
            assert result[0].id == "exp-abc"
            assert json.loads(SyncMockHandler.last_request_body)["id"] == "exp-abc"
            assert "type=ACTIVE_EXPECTATIONS" in SyncMockHandler.last_path

    def test_retrieve_recorded_expectations_by_id(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{"id": "re1"}])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_recorded_expectations_by_id("exp-abc")
            assert len(result) == 1
            assert json.loads(SyncMockHandler.last_request_body)["id"] == "exp-abc"
            assert "type=RECORDED_EXPECTATIONS" in SyncMockHandler.last_path

    def test_retrieve_recorded_requests_and_responses_by_id(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {"httpRequest": {"path": "/recorded"}, "httpResponse": {"statusCode": 200}},
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_recorded_requests_and_responses_by_id("exp-abc")
            assert len(result) == 1
            assert json.loads(SyncMockHandler.last_request_body)["id"] == "exp-abc"
            assert "type=REQUEST_RESPONSES" in SyncMockHandler.last_path

    def test_retrieve_log_messages_by_id(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(["log one"])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_log_messages_by_id("exp-abc")
            assert result == ["log one"]
            assert json.loads(SyncMockHandler.last_request_body)["id"] == "exp-abc"
            assert "type=LOGS" in SyncMockHandler.last_path

    def test_retrieve_expectations_as_code(self, sync_mock_server):
        SyncMockHandler.response_body = 'when(request().withPath("/code"));'
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            code = client.retrieve_expectations_as_code("java")
            assert isinstance(code, str)
            assert "/code" in code
            assert "type=ACTIVE_EXPECTATIONS" in SyncMockHandler.last_path
            assert "format=JAVA" in SyncMockHandler.last_path

    def test_retrieve_recorded_expectations_as_code(self, sync_mock_server):
        SyncMockHandler.response_body = "# generated python"
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            code = client.retrieve_recorded_expectations_as_code("python")
            assert code == "# generated python"
            assert "type=RECORDED_EXPECTATIONS" in SyncMockHandler.last_path
            assert "format=PYTHON" in SyncMockHandler.last_path

    def test_retrieve_log_messages(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(["msg1", "msg2"])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.retrieve_log_messages()
            assert result == ["msg1", "msg2"]


class TestSyncBind:
    def test_bind(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"ports": [9090]})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.bind(9090)
            assert result == [9090]


class TestSyncHasStarted:
    def test_has_started_true(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            assert client.has_started(attempts=1) is True

    def test_has_started_false(self):
        client = MockServerClient("127.0.0.1", 19998)
        try:
            assert client.has_started(attempts=1, timeout=0.01) is False
        finally:
            client.close()


class TestSyncStop:
    def test_stop(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.stop()
            assert SyncMockHandler.last_path == "/mockserver/stop"


class TestSyncWhen:
    def test_when_returns_sync_chain(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            chain = client.when(HttpRequest(path="/test"))
            assert isinstance(chain, SyncForwardChainExpectation)

    def test_when_respond(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "id": "when-resp",
            "httpRequest": {"path": "/test"},
            "httpResponse": {"statusCode": 200},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = (
                client.when(HttpRequest(path="/test"))
                .with_id("when-resp")
                .respond(HttpResponse(status_code=200))
            )
            assert len(result) == 1

    def test_when_forward(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "httpRequest": {"path": "/test"},
            "httpForward": {"host": "example.com"},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.when(HttpRequest(path="/test")).forward(
                HttpForward(host="example.com")
            )
            assert len(result) == 1

    def test_when_error(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "httpRequest": {"path": "/test"},
            "httpError": {"dropConnection": True},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.when(HttpRequest(path="/test")).error(
                HttpError(drop_connection=True)
            )
            assert len(result) == 1

    def test_when_respond_with_delay(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{
            "httpRequest": {"path": "/test"},
            "httpResponse": {"statusCode": 200, "delay": {"timeUnit": "SECONDS", "value": 1}},
        }])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.when(HttpRequest(path="/test")).respond_with_delay(
                HttpResponse(status_code=200),
                Delay(time_unit="SECONDS", value=1),
            )
            assert len(result) == 1


class TestSyncForwardChainExpectation:
    def test_with_id(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{"id": "chained"}])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            chain = client.when(HttpRequest(path="/test"))
            result = chain.with_id("chained")
            assert result is chain
            assert chain._async_chain._expectation.id == "chained"

    def test_with_priority(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            chain = client.when(HttpRequest(path="/test"))
            result = chain.with_priority(10)
            assert result is chain
            assert chain._async_chain._expectation.priority == 10

    def test_forward_with_delay(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([{}])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.when(HttpRequest(path="/test")).forward_with_delay(
                HttpForward(host="example.com"),
                Delay(time_unit="MILLISECONDS", value=500),
            )
            assert len(result) == 1


class TestSyncOpenApiExpectation:
    def test_open_api_expectation(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.open_api_expectation(
                OpenAPIExpectation(spec_url_or_payload="https://example.com/spec.json")
            )
            assert "/mockserver/openapi" in SyncMockHandler.last_path


class TestSyncClockControl:
    def test_freeze_clock_with_instant(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({
            "status": "freeze",
            "currentInstant": "2025-01-15T09:30:00Z",
            "currentEpochMillis": 1736933400000,
        })
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.freeze_clock("2025-01-15T09:30:00Z")
            assert SyncMockHandler.last_path == "/mockserver/clock"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["action"] == "freeze"
            assert sent["instant"] == "2025-01-15T09:30:00Z"
            assert result["status"] == "freeze"

    def test_freeze_clock_without_instant(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({
            "status": "freeze",
            "currentInstant": "2026-05-30T12:00:00Z",
            "currentEpochMillis": 1780228800000,
        })
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.freeze_clock()
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["action"] == "freeze"
            assert "instant" not in sent
            assert result["status"] == "freeze"

    def test_advance_clock(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({
            "status": "advance",
            "currentInstant": "2025-01-15T10:30:00Z",
            "currentEpochMillis": 1736937000000,
        })
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.advance_clock(3600000)
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["action"] == "advance"
            assert sent["durationMillis"] == 3600000
            assert result["status"] == "advance"

    def test_reset_clock(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({
            "status": "reset",
            "currentInstant": "2026-05-30T12:00:00Z",
            "currentEpochMillis": 1780228800000,
        })
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.reset_clock()
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["action"] == "reset"
            assert result["status"] == "reset"

    def test_clock_status(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({
            "currentInstant": "2025-01-15T09:30:00Z",
            "currentEpochMillis": 1736933400000,
            "frozen": True,
        })
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.clock_status()
            assert SyncMockHandler.last_path == "/mockserver/clock"
            assert SyncMockHandler.last_method == "GET"
            assert result["frozen"] is True
            assert result["currentInstant"] == "2025-01-15T09:30:00Z"

    def test_freeze_clock_error(self, sync_mock_server):
        SyncMockHandler.response_status = 400
        SyncMockHandler.response_body = '{"error": "bad request"}'
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="Failed to freeze clock"):
                client.freeze_clock()


class TestSyncServiceChaos:
    def test_set_service_chaos(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"status": "registered", "host": "payments.svc"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.set_service_chaos(
                "payments.svc",
                HttpChaosProfile(error_status=503, error_probability=1.0),
            )
            assert SyncMockHandler.last_path == "/mockserver/serviceChaos"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["host"] == "payments.svc"
            assert sent["chaos"]["errorStatus"] == 503
            assert "ttlMillis" not in sent
            assert result["status"] == "registered"

    def test_set_service_chaos_with_ttl(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"status": "registered", "host": "payments.svc", "ttlMillis": 300000})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.set_service_chaos(
                "payments.svc",
                HttpChaosProfile(error_status=503),
                ttl_millis=300000,
            )
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["host"] == "payments.svc"
            assert sent["ttlMillis"] == 300000

    def test_remove_service_chaos(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"status": "removed", "host": "payments.svc"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.remove_service_chaos("payments.svc")
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["host"] == "payments.svc"
            assert sent["remove"] is True

    def test_clear_service_chaos(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"status": "cleared"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.clear_service_chaos()
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["clear"] is True

    def test_set_service_chaos_error(self, sync_mock_server):
        SyncMockHandler.response_status = 400
        SyncMockHandler.response_body = '{"error": "invalid chaos profile"}'
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="Failed to set service chaos"):
                client.set_service_chaos("payments.svc", HttpChaosProfile(error_status=503))

    def test_service_chaos_status(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"services": {"payments.svc": {"errorStatus": 503}}})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.service_chaos_status()
            assert SyncMockHandler.last_path == "/mockserver/serviceChaos"
            assert SyncMockHandler.last_method == "GET"
            assert result["services"]["payments.svc"]["errorStatus"] == 503


class TestSyncVerifySlo:
    def test_verify_slo_pass(self, sync_mock_server):
        SyncMockHandler.response_status = 200
        SyncMockHandler.response_body = json.dumps({"name": "checkout", "result": "PASS"})
        criteria = {
            "name": "checkout",
            "objectives": [
                {"sli": "errorRate", "comparator": "LESS_THAN", "threshold": 0.01}
            ],
        }
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.verify_slo(criteria)
            assert SyncMockHandler.last_path == "/mockserver/verifySLO"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["name"] == "checkout"
            assert sent["objectives"][0]["sli"] == "errorRate"
            assert result["result"] == "PASS"

    def test_verify_slo_inconclusive_returns_verdict(self, sync_mock_server):
        SyncMockHandler.response_status = 200
        SyncMockHandler.response_body = json.dumps({"result": "INCONCLUSIVE", "sampleCount": 2})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.verify_slo({"name": "x"})
            assert result["result"] == "INCONCLUSIVE"

    def test_verify_slo_fail_raises_verification_error(self, sync_mock_server):
        SyncMockHandler.response_status = 406
        SyncMockHandler.response_body = json.dumps({"result": "FAIL"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerVerificationError):
                client.verify_slo({"name": "x"})

    def test_verify_slo_disabled_raises_error(self, sync_mock_server):
        # 403 is "SLO tracking is off"; it used to share 400 with malformed criteria
        SyncMockHandler.response_status = 403
        SyncMockHandler.response_body = "SLO tracking not enabled"
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="sloTrackingEnabled=true"):
                client.verify_slo({"name": "x"})

    def test_verify_slo_malformed_criteria_raises_invalid_error(self, sync_mock_server):
        SyncMockHandler.response_status = 400
        SyncMockHandler.response_body = "objectives must be an array"
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="Invalid SLO criteria"):
                client.verify_slo({"name": "x"})


class TestSyncChaosExperiment:
    def test_start_chaos_experiment(self, sync_mock_server):
        SyncMockHandler.response_status = 200
        SyncMockHandler.response_body = json.dumps({"status": "started", "name": "latency-injection"})
        experiment = {
            "name": "latency-injection",
            "loop": False,
            "stages": [
                {
                    "durationMillis": 60000,
                    "profiles": {"payments.svc": {"latencyMs": 500}},
                }
            ],
        }
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.start_chaos_experiment(experiment)
            assert SyncMockHandler.last_path == "/mockserver/chaosExperiment"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["name"] == "latency-injection"
            assert sent["stages"][0]["durationMillis"] == 60000
            assert result["status"] == "started"

    def test_start_chaos_experiment_forbidden_raises(self, sync_mock_server):
        SyncMockHandler.response_status = 403
        SyncMockHandler.response_body = "Forbidden for control plane"
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="control-plane authorization denied"):
                client.start_chaos_experiment({"name": "x", "stages": []})

    def test_start_chaos_experiment_invalid_raises(self, sync_mock_server):
        SyncMockHandler.response_status = 400
        SyncMockHandler.response_body = '{"error": "invalid"}'
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="Failed to start chaos experiment"):
                client.start_chaos_experiment({"name": "x"})


class TestSyncLoadScenario:
    def _scenario(self) -> LoadScenario:
        return LoadScenario(
            name="checkout-flow",
            template_type="VELOCITY",
            max_requests=1000,
            start_delay_millis=1500,
            labels={"team": "payments"},
            profile=LoadProfile(
                stages=[
                    LoadStage.vu_stage(60000, start_vus=1, end_vus=10, curve="LINEAR"),
                    LoadStage.rate_stage(30000, rate=50.0, max_vus=20),
                    LoadStage.pause_stage(5000),
                ],
            ),
            steps=[
                LoadStep(
                    name="login",
                    request=HttpRequest(method="POST", path="/login"),
                    think_time=Delay("MILLISECONDS", 250),
                ),
                LoadStep(
                    request=HttpRequest(method="GET", path="/cart"),
                ),
            ],
        )

    def test_load_scenario_serialization(self):
        sent = self._scenario().to_dict()
        assert sent["name"] == "checkout-flow"
        assert sent["templateType"] == "VELOCITY"
        assert sent["maxRequests"] == 1000
        assert sent["startDelayMillis"] == 1500
        assert sent["labels"] == {"team": "payments"}
        stages = sent["profile"]["stages"]
        assert len(stages) == 3
        # VU ramp stage
        assert stages[0]["type"] == "VU"
        assert stages[0]["startVus"] == 1
        assert stages[0]["endVus"] == 10
        assert stages[0]["durationMillis"] == 60000
        assert stages[0]["curve"] == "LINEAR"
        assert "vus" not in stages[0]
        assert "rate" not in stages[0]
        # RATE hold stage
        assert stages[1]["type"] == "RATE"
        assert stages[1]["rate"] == 50.0
        assert stages[1]["maxVus"] == 20
        assert stages[1]["durationMillis"] == 30000
        assert "vus" not in stages[1]
        assert "startRate" not in stages[1]
        # PAUSE stage
        assert stages[2]["type"] == "PAUSE"
        assert stages[2]["durationMillis"] == 5000
        assert "vus" not in stages[2]
        assert "rate" not in stages[2]
        assert "curve" not in stages[2]
        assert len(sent["steps"]) == 2
        assert sent["steps"][0]["name"] == "login"
        assert sent["steps"][0]["request"]["method"] == "POST"
        assert sent["steps"][0]["thinkTime"]["value"] == 250
        assert "thinkTime" not in sent["steps"][1]

    def test_load_scenario_round_trip(self):
        scenario = self._scenario()
        restored = LoadScenario.from_dict(scenario.to_dict())
        assert restored.to_dict() == scenario.to_dict()

    def test_load_scenario_registers(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"name": "checkout-flow", "state": "LOADED"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.load_scenario(self._scenario())
            assert SyncMockHandler.last_path == "/mockserver/loadScenario"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["name"] == "checkout-flow"
            assert sent["startDelayMillis"] == 1500
            assert sent["profile"]["stages"][0]["type"] == "VU"
            assert sent["steps"][0]["request"]["path"] == "/login"
            assert result == {"name": "checkout-flow", "state": "LOADED"}

    def test_load_scenario_accepts_dict(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"name": "raw", "state": "LOADED"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.load_scenario({"name": "raw", "profile": {"stages": [{"type": "VU", "vus": 5, "durationMillis": 1000}]}, "steps": []})
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["name"] == "raw"
            assert sent["profile"]["stages"][0]["vus"] == 5

    def test_load_scenarios_list(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {"scenarios": [{"name": "checkout-flow", "state": "RUNNING"}]}
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.load_scenarios()
            assert SyncMockHandler.last_path == "/mockserver/loadScenario"
            assert SyncMockHandler.last_method == "GET"
            assert result["scenarios"][0]["name"] == "checkout-flow"

    def test_get_load_scenario(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"name": "checkout-flow", "state": "LOADED"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.get_load_scenario("checkout-flow")
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/checkout-flow"
            assert SyncMockHandler.last_method == "GET"
            assert result["state"] == "LOADED"

    def test_delete_load_scenario(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"name": "checkout-flow", "state": "STOPPED"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.delete_load_scenario("checkout-flow")
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/checkout-flow"
            assert SyncMockHandler.last_method == "DELETE"
            assert result["state"] == "STOPPED"

    def test_clear_load_scenarios(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"cleared": True})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.clear_load_scenarios()
            assert SyncMockHandler.last_path == "/mockserver/loadScenario"
            assert SyncMockHandler.last_method == "DELETE"
            assert result["cleared"] is True

    def test_start_load_scenarios_single_name(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {"started": [{"name": "checkout-flow", "state": "RUNNING"}], "status": "ok"}
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.start_load_scenarios("checkout-flow")
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/start"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent == {"names": ["checkout-flow"]}
            assert result["started"][0]["state"] == "RUNNING"

    def test_start_load_scenarios_list(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"started": [], "status": "ok"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.start_load_scenarios(["a", "b"])
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent == {"names": ["a", "b"]}

    def test_stop_load_scenarios_named(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"stopped": ["a"], "status": "ok"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.stop_load_scenarios(["a"])
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/stop"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent == {"names": ["a"]}

    def test_stop_load_scenarios_all(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"stopped": ["a", "b"], "status": "ok"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.stop_load_scenarios()
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/stop"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent == {}

    def test_run_load_scenario_registers_then_starts(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps({"name": "checkout-flow", "state": "LOADED"})
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.run_load_scenario(self._scenario())
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/start"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent == {"names": ["checkout-flow"]}

    def test_load_scenario_advanced_serialization(self):
        scenario = LoadScenario(
            name="advanced",
            step_selection="WEIGHTED",
            abort_on_fail=True,
            abort_grace_millis=3000,
            thresholds=[
                LoadThreshold(metric="LATENCY_P999", comparator="LESS_THAN", threshold=500),
            ],
            pacing=LoadPacing(mode="CONSTANT_PACING", value=2000),
            feeder=LoadFeeder(
                rows=[{"sku": "A"}, {"sku": "B"}], strategy="SEQUENTIAL"
            ),
            profile=LoadProfile(
                shape=LoadShape(type="RAMP_HOLD", metric="RATE", target=100, ramp_millis=10000, hold_millis=60000)
            ),
            steps=[
                LoadStep(
                    request=HttpRequest(method="GET", path="/search"),
                    weight=2.0,
                    captures=[
                        LoadCapture(name="id", source="HEADER", expression="X-Id"),
                    ],
                ),
            ],
        )
        sent = scenario.to_dict()
        assert sent["stepSelection"] == "WEIGHTED"
        assert sent["abortOnFail"] is True
        assert sent["abortGraceMillis"] == 3000
        assert sent["thresholds"][0] == {
            "metric": "LATENCY_P999",
            "comparator": "LESS_THAN",
            "threshold": 500,
        }
        assert sent["pacing"] == {"mode": "CONSTANT_PACING", "value": 2000}
        assert sent["feeder"]["rows"] == [{"sku": "A"}, {"sku": "B"}]
        assert sent["feeder"]["strategy"] == "SEQUENTIAL"
        assert sent["profile"]["shape"]["type"] == "RAMP_HOLD"
        assert sent["profile"]["shape"]["metric"] == "RATE"
        assert sent["profile"]["shape"]["target"] == 100
        assert "stages" not in sent["profile"]
        assert sent["steps"][0]["weight"] == 2.0
        # capture without default_value omits the defaultValue key
        assert sent["steps"][0]["captures"][0] == {
            "name": "id",
            "source": "HEADER",
            "expression": "X-Id",
        }

    def test_load_scenario_advanced_round_trip(self):
        scenario = LoadScenario(
            name="rt",
            step_selection="WEIGHTED",
            abort_on_fail=False,
            thresholds=[LoadThreshold(metric="ERROR_RATE", comparator="LESS_THAN", threshold=0.05)],
            pacing=LoadPacing(mode="CONSTANT_THROUGHPUT", value=10.0),
            feeder=LoadFeeder(data='[{"k":"v"}]', format="JSON", strategy="RANDOM"),
            profile=LoadProfile(shape=LoadShape(type="SPIKE", baseline=1, peak=10, ramp_up_millis=5000, hold_millis=5000, ramp_down_millis=5000, recovery_hold_millis=2000)),
            steps=[LoadStep(request=HttpRequest(method="GET", path="/x"), weight=1.0, captures=[LoadCapture(name="t", source="BODY_REGEX", expression="tok=(.*)", default_value="x")])],
        )
        restored = LoadScenario.from_dict(scenario.to_dict())
        assert restored.to_dict() == scenario.to_dict()

    def test_get_load_scenario_report_json(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {"scenario": "checkout-flow", "verdict": "FAIL", "thresholdResults": [{"satisfied": False}]}
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.get_load_scenario_report("checkout-flow")
            assert SyncMockHandler.last_method == "GET"
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/checkout-flow/report"
            assert result["verdict"] == "FAIL"

    def test_get_load_scenario_report_junit(self, sync_mock_server):
        SyncMockHandler.response_body = "<testsuite name='checkout-flow'/>"
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.get_load_scenario_report("checkout-flow", format="junit")
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/checkout-flow/report?format=junit"
            assert isinstance(result, str)
            assert "<testsuite" in result

    def test_get_load_scenario_report_not_found(self, sync_mock_server):
        SyncMockHandler.response_status = 404
        SyncMockHandler.response_body = '{"error": "never ran"}'
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="No load scenario run"):
                client.get_load_scenario_report("missing")

    def test_generate_load_scenario_from_openapi(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {"status": "loaded", "name": "petstore-load", "state": "LOADED", "scenario": {}}
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.generate_load_scenario_from_openapi(
                "petstore-load",
                "https://example.com/petstore.yaml",
                target={"host": "petstore.svc", "port": 8080, "scheme": "http"},
            )
            assert SyncMockHandler.last_method == "PUT"
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/generateFromOpenAPI"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["name"] == "petstore-load"
            assert sent["specUrlOrPayload"] == "https://example.com/petstore.yaml"
            assert sent["target"]["host"] == "petstore.svc"
            assert "profile" not in sent
            assert result["status"] == "loaded"

    def test_generate_load_scenario_from_recording(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {"status": "loaded", "name": "replay", "state": "LOADED", "scenario": {}}
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.generate_load_scenario_from_recording(
                "replay",
                mode="TEMPLATIZED",
                max_steps=25,
                profile=LoadProfile(stages=[LoadStage.vu_stage(5000, vus=1)]),
            )
            assert SyncMockHandler.last_method == "PUT"
            assert SyncMockHandler.last_path == "/mockserver/loadScenario/generateFromRecording"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent["name"] == "replay"
            assert sent["mode"] == "TEMPLATIZED"
            assert sent["maxSteps"] == 25
            assert sent["profile"]["stages"][0]["vus"] == 1
            assert result["status"] == "loaded"

    def test_load_scenario_register_error(self, sync_mock_server):
        SyncMockHandler.response_status = 400
        SyncMockHandler.response_body = '{"error": "invalid scenario"}'
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="Failed to register load scenario"):
                client.load_scenario(self._scenario())

    def test_start_load_scenarios_forbidden_when_disabled(self, sync_mock_server):
        SyncMockHandler.response_status = 403
        SyncMockHandler.response_body = '{"error": "load generation disabled"}'
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="load generation is disabled"):
                client.start_load_scenarios("checkout-flow")


class TestSyncGrpcDescriptors:
    def test_upload_grpc_descriptor(self, sync_mock_server):
        SyncMockHandler.response_status = 201
        SyncMockHandler.response_body = '{"status":"loaded"}'
        descriptor = bytes([0x0a, 0x04, 0x74, 0x65, 0x73, 0x74, 0xff, 0x80])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.upload_grpc_descriptor(descriptor)
            assert SyncMockHandler.last_method == "PUT"
            assert SyncMockHandler.last_path == "/mockserver/grpc/descriptors"
            assert SyncMockHandler.last_request_bytes == descriptor
            assert SyncMockHandler.last_content_type == "application/octet-stream"

    def test_upload_grpc_descriptor_empty_raises(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="must not be empty"):
                client.upload_grpc_descriptor(b"")

    def test_retrieve_grpc_services(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps([
            {
                "name": "example.Greeter",
                "methods": [
                    {
                        "name": "SayHello",
                        "inputType": "example.HelloRequest",
                        "outputType": "example.HelloReply",
                        "clientStreaming": False,
                        "serverStreaming": False,
                    }
                ],
            }
        ])
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            services = client.retrieve_grpc_services()
            assert SyncMockHandler.last_path == "/mockserver/grpc/services"
            assert len(services) == 1
            assert services[0]["name"] == "example.Greeter"

    def test_clear_grpc_descriptors(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.clear_grpc_descriptors()
            assert SyncMockHandler.last_method == "PUT"
            assert SyncMockHandler.last_path == "/mockserver/grpc/clear"


class TestSyncScenario:
    def test_scenario_state(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {"scenarioName": "Deploy", "currentState": "Deploying"}
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            state = client.scenario("Deploy").state()
            assert SyncMockHandler.last_path == "/mockserver/scenario/Deploy"
            assert SyncMockHandler.last_method == "GET"
            assert state == "Deploying"

    def test_scenario_set(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {"scenarioName": "Deploy", "currentState": "Deploying"}
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.scenario("Deploy").set("Deploying")
            assert SyncMockHandler.last_path == "/mockserver/scenario/Deploy"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent == {"state": "Deploying"}
            assert "transitionAfterMs" not in sent
            assert "nextState" not in sent
            assert result["currentState"] == "Deploying"

    def test_scenario_set_timed(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {
                "scenarioName": "Deploy",
                "currentState": "Deploying",
                "nextState": "Deployed",
                "transitionAfterMs": 5000,
            }
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.scenario("Deploy").set(
                "Deploying", transition_after_ms=5000, next_state="Deployed"
            )
            assert SyncMockHandler.last_path == "/mockserver/scenario/Deploy"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent == {
                "state": "Deploying",
                "transitionAfterMs": 5000,
                "nextState": "Deployed",
            }

    def test_scenario_trigger(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {"scenarioName": "Deploy", "currentState": "Failed"}
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            result = client.scenario("Deploy").trigger("Failed")
            assert SyncMockHandler.last_path == "/mockserver/scenario/Deploy/trigger"
            assert SyncMockHandler.last_method == "PUT"
            sent = json.loads(SyncMockHandler.last_request_body)
            assert sent == {"newState": "Failed"}
            assert result["currentState"] == "Failed"

    def test_scenarios_list(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {
                "scenarios": [
                    {"scenarioName": "Deploy", "currentState": "Deploying"},
                    {"scenarioName": "Login", "currentState": "LoggedOut"},
                ]
            }
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            scenarios = client.scenarios()
            assert SyncMockHandler.last_path == "/mockserver/scenario"
            assert SyncMockHandler.last_method == "GET"
            assert len(scenarios) == 2
            assert scenarios[0]["scenarioName"] == "Deploy"
            assert scenarios[0]["currentState"] == "Deploying"

    def test_scenario_name_is_url_encoded(self, sync_mock_server):
        SyncMockHandler.response_body = json.dumps(
            {"scenarioName": "deploy flow", "currentState": "x"}
        )
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            client.scenario("deploy flow").state()
            assert SyncMockHandler.last_path == "/mockserver/scenario/deploy%20flow"

    def test_scenario_handle_name(self, sync_mock_server):
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            assert client.scenario("Deploy").name == "Deploy"

    def test_scenario_error(self, sync_mock_server):
        SyncMockHandler.response_status = 400
        SyncMockHandler.response_body = '{"error": "scenario name is required in the path"}'
        with MockServerClient("127.0.0.1", sync_mock_server) as client:
            with pytest.raises(MockServerError, match="Scenario request failed"):
                client.scenario("Deploy").set("Deploying")
