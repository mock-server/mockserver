from __future__ import annotations

import dataclasses
import json
import re
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import pytest

from mockserver import (
    AsyncMockServerClient,
    DebugMismatchResult,
    ExplainedExpectation,
    ExplainUnmatchedResult,
    HttpRequest,
    MockServerClient,
    RequestExplanation,
)
from mockserver.exceptions import MockServerError

EXPLANATION = {
    "correlationId": "abc 123/x",
    "timestamp": "2026-10-14T10:00:00Z",
    "recomputed": True,
    "note": "recomputed now",
    "request": {"timestamp": "2026-10-14 10:00:00.000", "method": "GET", "path": "/orders",
                "bodyTruncated": True, "bodyTruncatedNote": "kept the start"},
    "atArrival": {"outcome": "MATCHED", "expectationId": "catch-all", "expectationStillStored": True},
    "sameOutcomeAsAtArrival": True,
    "expectationsChangedSince": False,
    "expectationsLastChanged": "2026-10-14T09:00:00Z",
    "matchedExpectation": {"expectationId": "catch-all", "position": 2, "priority": 0, "matches": True},
    "skippedExpectations": [{
        "expectationId": "gold", "expectationMethod": "GET", "expectationPath": "/orders",
        "position": 1, "priority": 10, "reason": "FIELDS_DIFFER", "explanation": "header differs",
        "matches": False, "matchedFieldCount": 17, "totalFieldCount": 18, "differingFieldCount": 1,
        "differences": {"headers": ["X-Tenant differs"]}, "remediation": {"headers": "add X-Tenant"},
    }],
    "shadowedExpectations": [{"expectationId": "late", "position": 3, "priority": 0, "reason": "LOWER_PRIORITY",
                              "explanation": "also matches"}],
    "totalExpectations": 3,
    "evaluatedExpectations": 3,
    "truncated": True,
    "maxExpectationsEvaluated": 500,
}

MISMATCH = {
    "correlationId": "c1",
    "timestamp": "2026-10-14T10:00:00Z",
    "totalExpectations": 1,
    "evaluatedExpectations": 1,
    "closestMatch": {"expectationId": "e1", "matchedFields": 17, "totalFields": 18},
    "results": [{"expectationId": "e1", "expectationPath": "/api/users", "expectationMethod": "POST",
                 "matches": False, "matchedFieldCount": 17, "totalFieldCount": 18,
                 "differences": {"method": ["expected POST found GET"]}}],
}

UNMATCHED = {
    "correlationId": "c2",
    "timestamp": "2026-10-14T10:00:00Z",
    "unmatchedRequestCount": 1,
    "truncated": False,
    "unmatchedRequests": [{
        "timestamp": "2026-10-14 10:00:00.000", "method": "GET", "path": "/api/order",
        "totalExpectationsEvaluated": 1,
        "closestExpectations": [{
            "expectationId": "e1", "matches": False, "differingFieldCount": 1,
            "suggestedFix": {"patch": [{"op": "replace", "path": "/httpRequest/path", "value": "/api/order"}],
                             "patchedFields": ["path"],
                             "unpatchedFields": [{"field": "body", "reason": "redacted"}],
                             "matchesAfterPatch": True},
        }],
    }],
}


class StubHandler(BaseHTTPRequestHandler):
    status = 200
    body = "{}"
    received: list = []

    def do_PUT(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length).decode("utf-8") if length else ""
        StubHandler.received.append({"method": "PUT", "path": self.path, "body": raw})
        self.send_response(StubHandler.status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(StubHandler.body.encode("utf-8"))

    def log_message(self, format, *args):
        pass


@pytest.fixture
def stub():
    server = HTTPServer(("127.0.0.1", 0), StubHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    StubHandler.status = 200
    StubHandler.body = "{}"
    StubHandler.received = []
    yield server.server_address[1]
    server.shutdown()
    server.server_close()


def answer(status: int, body) -> None:
    StubHandler.status = status
    StubHandler.body = body if isinstance(body, str) else json.dumps(body)


class TestExplain:
    def test_sends_the_encoded_correlation_id_and_returns_a_typed_explanation(self, stub):
        answer(200, EXPLANATION)
        with MockServerClient("127.0.0.1", stub) as client:
            explanation = client.explain("abc 123/x")
        assert StubHandler.received == [
            {"method": "PUT", "path": "/mockserver/explain?correlationId=abc+123%2Fx", "body": ""}
        ]
        assert isinstance(explanation, RequestExplanation)
        assert explanation.correlation_id == "abc 123/x"
        assert explanation.recomputed is True
        assert explanation.note == "recomputed now"
        assert explanation.request.method == "GET"
        assert explanation.request.path == "/orders"
        assert explanation.request.body_truncated is True
        assert explanation.request.body_truncated_note == "kept the start"
        assert explanation.at_arrival.outcome == "MATCHED"
        assert explanation.at_arrival.expectation_id == "catch-all"
        assert explanation.at_arrival.expectation_still_stored is True
        assert explanation.same_outcome_as_at_arrival is True
        assert explanation.expectations_changed_since is False
        assert explanation.expectations_last_changed == "2026-10-14T09:00:00Z"
        assert explanation.matched_expectation.expectation_id == "catch-all"
        assert explanation.matched_expectation.reason is None
        skipped = explanation.skipped_expectations[0]
        assert isinstance(skipped, ExplainedExpectation)
        assert (skipped.expectation_id, skipped.expectation_method, skipped.expectation_path) == ("gold", "GET", "/orders")
        assert (skipped.position, skipped.priority, skipped.reason) == (1, 10, "FIELDS_DIFFER")
        assert skipped.explanation == "header differs"
        assert skipped.matches is False
        assert (skipped.matched_field_count, skipped.total_field_count, skipped.differing_field_count) == (17, 18, 1)
        assert skipped.differences == {"headers": ["X-Tenant differs"]}
        assert skipped.remediation == {"headers": "add X-Tenant"}
        assert explanation.shadowed_expectations[0].reason == "LOWER_PRIORITY"
        assert explanation.closest_expectation_id is None
        assert (explanation.total_expectations, explanation.evaluated_expectations) == (3, 3)
        assert explanation.truncated is True
        assert explanation.max_expectations_evaluated == 500

    def test_raises_the_server_error_for_an_unknown_correlation_id(self, stub):
        answer(404, {"error": "no request is recorded with correlationId unknown"})
        with MockServerClient("127.0.0.1", stub) as client:
            with pytest.raises(MockServerError) as raised:
                client.explain("unknown")
        assert str(raised.value) == (
            'explain failed for correlationId "unknown" (status=404): '
            "no request is recorded with correlationId unknown"
        )

    @pytest.mark.parametrize("blank", ["", "   ", None])
    def test_rejects_a_blank_correlation_id_without_calling_the_server(self, stub, blank):
        with MockServerClient("127.0.0.1", stub) as client:
            with pytest.raises(ValueError, match="explain requires a non blank correlation_id"):
                client.explain(blank)
        assert StubHandler.received == []

    async def test_async_client_returns_the_same_explanation(self, stub):
        answer(200, EXPLANATION)
        async with AsyncMockServerClient("127.0.0.1", stub) as client:
            explanation = await client.explain("abc 123/x")
        assert explanation.skipped_expectations[0].reason == "FIELDS_DIFFER"


class TestDebugMismatch:
    def test_sends_the_request_and_returns_a_typed_analysis(self, stub):
        answer(200, MISMATCH)
        request = HttpRequest(method="GET", path="/api/users")
        with MockServerClient("127.0.0.1", stub) as client:
            result = client.debug_mismatch(request)
        assert len(StubHandler.received) == 1
        assert StubHandler.received[0]["path"] == "/mockserver/debugMismatch"
        assert json.loads(StubHandler.received[0]["body"]) == {"method": "GET", "path": "/api/users"}
        assert isinstance(result, DebugMismatchResult)
        assert (result.total_expectations, result.evaluated_expectations, result.truncated) == (1, 1, False)
        assert result.closest_match.expectation_id == "e1"
        assert (result.closest_match.matched_fields, result.closest_match.total_fields) == (17, 18)
        assert result.results[0].differences == {"method": ["expected POST found GET"]}
        assert result.results[0].matched_field_count == 17

    def test_accepts_a_dict_request(self, stub):
        answer(200, MISMATCH)
        with MockServerClient("127.0.0.1", stub) as client:
            client.debug_mismatch({"method": "GET", "path": "/x"})
        assert json.loads(StubHandler.received[0]["body"]) == {"method": "GET", "path": "/x"}

    def test_raises_the_server_error_for_a_non_http_definition(self, stub):
        answer(400, {"error": "debugMismatch only supports HttpRequest definitions"})
        with MockServerClient("127.0.0.1", stub) as client:
            with pytest.raises(MockServerError, match="debugMismatch only supports HttpRequest definitions"):
                client.debug_mismatch({"specUrlOrPayload": "x"})


class TestExplainUnmatched:
    def test_sends_limit_and_suggest_fixes_and_returns_a_typed_report(self, stub):
        answer(200, UNMATCHED)
        with MockServerClient("127.0.0.1", stub) as client:
            result = client.explain_unmatched(limit=5, suggest_fixes=True)
        assert StubHandler.received[0]["path"] == "/mockserver/explainUnmatched"
        assert json.loads(StubHandler.received[0]["body"]) == {"limit": 5, "suggestFixes": True}
        assert isinstance(result, ExplainUnmatchedResult)
        assert (result.unmatched_request_count, result.truncated) == (1, False)
        unmatched = result.unmatched_requests[0]
        assert (unmatched.method, unmatched.path, unmatched.total_expectations_evaluated) == ("GET", "/api/order", 1)
        fix = unmatched.closest_expectations[0].suggested_fix
        assert fix.patch == [{"op": "replace", "path": "/httpRequest/path", "value": "/api/order"}]
        assert fix.patched_fields == ["path"]
        assert fix.unpatched_fields == [{"field": "body", "reason": "redacted"}]
        assert fix.matches_after_patch is True

    def test_sends_no_body_without_options(self, stub):
        answer(200, UNMATCHED)
        with MockServerClient("127.0.0.1", stub) as client:
            client.explain_unmatched()
        assert StubHandler.received == [{"method": "PUT", "path": "/mockserver/explainUnmatched", "body": ""}]

    def test_raises_on_a_server_failure(self, stub):
        answer(500, "boom")
        with MockServerClient("127.0.0.1", stub) as client:
            with pytest.raises(MockServerError, match=r"explainUnmatched failed \(status=500\): boom"):
                client.explain_unmatched()


SPEC = Path(__file__).resolve().parents[2] / (
    "mockserver/mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml"
)


def schema_properties(schema: str) -> list[str]:
    """The top-level property names of a components schema, read from the YAML by indentation."""
    text = SPEC.read_text(encoding="utf-8")
    block = re.search(rf"^    {schema}:\n(.*?)(?=^    \S|^\S|\Z)", text, re.M | re.S)
    assert block, f"{schema} schema not found"
    properties = re.search(r"^      properties:\n(.*)", block.group(1), re.M | re.S).group(1)
    return re.findall(r"^        (\w+):", properties, re.M)


def snake(name: str) -> str:
    return re.sub(r"([A-Z])", lambda m: "_" + m.group(1).lower(), name)


class TestModelsFollowTheSchema:
    def test_request_explanation_models_every_schema_property(self):
        fields = {f.name for f in dataclasses.fields(RequestExplanation)}
        assert fields == {snake(p) for p in schema_properties("RequestExplanation")}

    def test_explained_expectation_models_every_schema_property_and_the_suggested_fix(self):
        fields = {f.name for f in dataclasses.fields(ExplainedExpectation)}
        assert fields == {snake(p) for p in schema_properties("ExplainedExpectation")} | {"suggested_fix"}
