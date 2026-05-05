import json
import urllib.request

import pytest

from mockserver import (
    Body,
    Delay,
    Expectation,
    HttpForward,
    HttpRequest,
    HttpResponse,
    MockServerClient,
    MockServerVerificationError,
    Times,
    VerificationTimes,
)

pytestmark = pytest.mark.integration


class TestConnection:
    def test_has_started(self, mockserver_client):
        assert mockserver_client.has_started()

    def test_client_context_manager(self, mockserver_host, mockserver_port):
        with MockServerClient(mockserver_host, mockserver_port) as client:
            assert client.has_started()


class TestExpectationLifecycle:
    def test_upsert_and_retrieve(self, mockserver_client, mockserver_port):
        expectation = Expectation(
            http_request=HttpRequest(method="GET", path="/test"),
            http_response=HttpResponse(status_code=200, body="hello"),
            times=Times.unlimited(),
        )
        result = mockserver_client.upsert(expectation)
        assert len(result) == 1
        assert result[0].http_request.path == "/test"

        active = mockserver_client.retrieve_active_expectations()
        assert any(e.http_request.path == "/test" for e in active)

    def test_clear_expectations(self, mockserver_client, mockserver_port):
        mockserver_client.upsert(
            Expectation(
                http_request=HttpRequest(method="GET", path="/to-clear"),
                http_response=HttpResponse(status_code=200, body="gone"),
            )
        )
        mockserver_client.clear(HttpRequest(path="/to-clear"))
        active = mockserver_client.retrieve_active_expectations()
        assert not any(e.http_request.path == "/to-clear" for e in active)

    def test_reset(self, mockserver_client, mockserver_port):
        mockserver_client.upsert(
            Expectation(
                http_request=HttpRequest(method="GET", path="/before-reset"),
                http_response=HttpResponse(status_code=200),
            )
        )
        mockserver_client.reset()
        active = mockserver_client.retrieve_active_expectations()
        assert len(active) == 0


class TestRequestMatching:
    def _make_request(self, host, port, method, path, body=None, headers=None):
        url = f"http://{host}:{port}{path}"
        data = body.encode() if body else None
        req = urllib.request.Request(url, data=data, method=method)
        if headers:
            for k, v in headers.items():
                req.add_header(k, v)
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status, resp.read().decode()
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode()

    def test_simple_get(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(method="GET", path="/api/hello")
        ).respond(
            HttpResponse(status_code=200, body="world")
        )

        status, body = self._make_request(mockserver_host, mockserver_port, "GET", "/api/hello")
        assert status == 200
        assert body == "world"

    def test_post_with_json_body(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(
                method="POST",
                path="/api/data",
                body=Body.json({"key": "value"}),
            )
        ).respond(
            HttpResponse(
                status_code=201,
                body=json.dumps({"result": "created"}),
            )
        )

        status, body = self._make_request(
            mockserver_host,
            mockserver_port,
            "POST",
            "/api/data",
            body=json.dumps({"key": "value"}),
            headers={"Content-Type": "application/json"},
        )
        assert status == 201
        resp = json.loads(body)
        assert resp["result"] == "created"

    def test_custom_status_code(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(method="DELETE", path="/api/resource")
        ).respond(
            HttpResponse(status_code=204)
        )

        status, _ = self._make_request(mockserver_host, mockserver_port, "DELETE", "/api/resource")
        assert status == 204

    def test_unmatched_returns_404(self, mockserver_client, mockserver_host, mockserver_port):
        status, _ = self._make_request(mockserver_host, mockserver_port, "GET", "/no-such-path")
        assert status == 404

    def test_response_headers(self, mockserver_client, mockserver_host, mockserver_port):
        from mockserver import KeyToMultiValue

        mockserver_client.when(
            HttpRequest(method="GET", path="/with-headers")
        ).respond(
            HttpResponse(
                status_code=200,
                body="ok",
                headers=[KeyToMultiValue(name="X-Custom", values=["test-value"])],
            )
        )

        url = f"http://{mockserver_host}:{mockserver_port}/with-headers"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=5) as resp:
            assert resp.getheader("X-Custom") == "test-value"

    def test_regex_path_matching(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(method="GET", path="/users/[0-9]+")
        ).respond(
            HttpResponse(status_code=200, body="user found")
        )

        status, body = self._make_request(mockserver_host, mockserver_port, "GET", "/users/42")
        assert status == 200
        assert body == "user found"

    def test_times_exactly(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(method="GET", path="/once-only"),
            times=Times.exactly(1),
        ).respond(
            HttpResponse(status_code=200, body="first")
        )

        status1, body1 = self._make_request(mockserver_host, mockserver_port, "GET", "/once-only")
        assert status1 == 200
        assert body1 == "first"

        status2, _ = self._make_request(mockserver_host, mockserver_port, "GET", "/once-only")
        assert status2 == 404


class TestVerification:
    def _make_request(self, host, port, method, path):
        url = f"http://{host}:{port}{path}"
        req = urllib.request.Request(url, method=method)
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status
        except urllib.error.HTTPError as e:
            return e.code

    def test_verify_received(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(method="GET", path="/verify-me")
        ).respond(
            HttpResponse(status_code=200)
        )

        self._make_request(mockserver_host, mockserver_port, "GET", "/verify-me")

        mockserver_client.verify(
            HttpRequest(method="GET", path="/verify-me"),
            times=VerificationTimes.exactly(1),
        )

    def test_verify_not_received_raises(self, mockserver_client):
        with pytest.raises(MockServerVerificationError):
            mockserver_client.verify(
                HttpRequest(method="GET", path="/never-called"),
                times=VerificationTimes.exactly(1),
            )

    def test_verify_zero_interactions(self, mockserver_client):
        mockserver_client.verify_zero_interactions()

    def test_verify_multiple_calls(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(method="GET", path="/multi")
        ).respond(
            HttpResponse(status_code=200)
        )

        for _ in range(3):
            self._make_request(mockserver_host, mockserver_port, "GET", "/multi")

        mockserver_client.verify(
            HttpRequest(method="GET", path="/multi"),
            times=VerificationTimes.exactly(3),
        )

    def test_verify_sequence(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(path="/seq")
        ).respond(
            HttpResponse(status_code=200)
        )

        self._make_request(mockserver_host, mockserver_port, "GET", "/seq")
        self._make_request(mockserver_host, mockserver_port, "POST", "/seq")

        mockserver_client.verify_sequence(
            HttpRequest(method="GET", path="/seq"),
            HttpRequest(method="POST", path="/seq"),
        )


class TestRetrieve:
    def _make_request(self, host, port, method, path, body=None):
        url = f"http://{host}:{port}{path}"
        data = body.encode() if body else None
        req = urllib.request.Request(url, data=data, method=method)
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status
        except urllib.error.HTTPError as e:
            return e.code

    def test_retrieve_recorded_requests(self, mockserver_client, mockserver_host, mockserver_port):
        self._make_request(mockserver_host, mockserver_port, "GET", "/record-me")

        requests = mockserver_client.retrieve_recorded_requests(
            HttpRequest(path="/record-me")
        )
        assert len(requests) >= 1
        assert requests[0].path == "/record-me"
        assert requests[0].method == "GET"

    def test_retrieve_log_messages(self, mockserver_client, mockserver_host, mockserver_port):
        self._make_request(mockserver_host, mockserver_port, "GET", "/log-test")

        logs = mockserver_client.retrieve_log_messages()
        assert len(logs) > 0

    def test_retrieve_requests_and_responses(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(method="GET", path="/req-resp")
        ).respond(
            HttpResponse(status_code=200, body="matched")
        )

        self._make_request(mockserver_host, mockserver_port, "GET", "/req-resp")

        pairs = mockserver_client.retrieve_recorded_requests_and_responses(
            HttpRequest(path="/req-resp")
        )
        assert len(pairs) >= 1
        assert pairs[0].http_request.path == "/req-resp"
        assert pairs[0].http_response.status_code == 200


class TestFluentApi:
    def _make_request(self, host, port, method, path):
        url = f"http://{host}:{port}{path}"
        req = urllib.request.Request(url, method=method)
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status, resp.read().decode()
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode()

    def test_fluent_with_id(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(method="GET", path="/fluent-id")
        ).with_id(
            "my-expectation-id"
        ).respond(
            HttpResponse(status_code=200, body="fluent")
        )

        status, body = self._make_request(mockserver_host, mockserver_port, "GET", "/fluent-id")
        assert status == 200
        assert body == "fluent"

    def test_fluent_with_priority(self, mockserver_client, mockserver_host, mockserver_port):
        mockserver_client.when(
            HttpRequest(method="GET", path="/priority-test")
        ).with_priority(10).respond(
            HttpResponse(status_code=200, body="high-priority")
        )

        mockserver_client.when(
            HttpRequest(method="GET", path="/priority-test")
        ).with_priority(1).respond(
            HttpResponse(status_code=200, body="low-priority")
        )

        status, body = self._make_request(mockserver_host, mockserver_port, "GET", "/priority-test")
        assert status == 200
        assert body == "high-priority"
