from __future__ import annotations

import json

from mockserver import (
    AnyOfBranchComparison,
    AnyOfRequestDefinition,
    ConditionalRequestDefinition,
    Expectation,
    ExplainedExpectation,
    HttpRequest,
    HttpResponse,
    OpenAPIDefinition,
    Verification,
)


def _wire(model) -> dict:
    return json.loads(json.dumps(model.to_dict()))


class TestAnyOfRequestDefinition:
    def test_typed_serialises_to_any_of_inside_http_request(self):
        expectation = Expectation(
            http_request=AnyOfRequestDefinition.request_any_of(
                HttpRequest(method="GET", path="/pets"),
                HttpRequest(method="GET", path="/animals"),
            ),
            http_response=HttpResponse(status_code=200),
        )
        assert _wire(expectation) == {
            "httpRequest": {"anyOf": [{"method": "GET", "path": "/pets"}, {"method": "GET", "path": "/animals"}]},
            "httpResponse": {"statusCode": 200},
        }

    def test_expectation_reads_http_request_holding_any_of(self):
        data = {
            "httpRequest": {"anyOf": [{"path": "/a"}, {"path": "/b"}]},
            "httpResponse": {"statusCode": 200},
        }
        expectation = Expectation.from_dict(data)
        assert isinstance(expectation.http_request, AnyOfRequestDefinition)
        assert [b.path for b in expectation.http_request.any_of] == ["/a", "/b"]
        assert _wire(expectation) == data

    def test_every_branch_kind_and_not_round_trip(self):
        nested = {
            "not": True,
            "anyOf": [
                {"method": "GET", "path": "/pets"},
                {"specUrlOrPayload": "https://example.com/o.json", "operationId": "listPets"},
                {"if": {"method": "POST"}, "then": {"path": "/orders"}},
                {"not": True, "anyOf": [{"path": "/admin/.*"}]},
            ],
        }
        model = AnyOfRequestDefinition.from_dict(nested)
        assert model.not_any_of is True
        assert [type(b) for b in model.any_of] == [
            HttpRequest, OpenAPIDefinition, ConditionalRequestDefinition, AnyOfRequestDefinition]
        assert model.any_of[3].not_any_of is True
        assert _wire(model) == nested

    def test_conditional_branch_holding_any_of_round_trips(self):
        data = {"if": {"method": "GET"}, "then": {"anyOf": [{"path": "/a"}, {"path": "/b"}]}}
        model = ConditionalRequestDefinition.from_dict(data)
        assert isinstance(model.then_request, AnyOfRequestDefinition)
        assert _wire(model) == data

    def test_absent_not_is_omitted(self):
        assert _wire(AnyOfRequestDefinition(any_of=[HttpRequest(path="/a")])) == {"anyOf": [{"path": "/a"}]}

    def test_from_dict_none(self):
        assert AnyOfRequestDefinition.from_dict(None) is None

    def test_empty_any_of_is_sent_for_the_server_to_reject_not_dropped(self):
        # {} would match every request; "anyOf": [] is rejected by the server
        assert _wire(AnyOfRequestDefinition()) == {"anyOf": []}
        assert _wire(AnyOfRequestDefinition.request_any_of()) == {"anyOf": []}
        assert _wire(Expectation(http_request=AnyOfRequestDefinition()))["httpRequest"] == {"anyOf": []}
        assert _wire(AnyOfRequestDefinition.from_dict({"anyOf": []})) == {"anyOf": []}

    def test_verification_carries_an_any_of_matcher(self):
        verification = Verification(http_request=AnyOfRequestDefinition.request_any_of(HttpRequest(path="/a")))
        assert _wire(verification) == {"httpRequest": {"anyOf": [{"path": "/a"}]}}
        assert isinstance(Verification.from_dict(_wire(verification)).http_request, AnyOfRequestDefinition)

    def test_explained_expectation_reads_the_closest_branch(self):
        explained = ExplainedExpectation.from_dict({
            "expectationId": "any-of",
            "matches": False,
            "anyOf": {
                "closestBranch": 1,
                "branches": [
                    {"branch": 0, "matches": False, "differingFieldCount": 2, "differingFields": ["method", "path"]},
                    {"branch": 1, "matches": False, "differingFieldCount": 1, "differingFields": ["headers"]},
                ],
            },
        })
        assert isinstance(explained.any_of, AnyOfBranchComparison)
        assert explained.any_of.closest_branch == 1
        assert explained.any_of.branches[1]["differingFields"] == ["headers"]
        assert ExplainedExpectation.from_dict({"expectationId": "plain"}).any_of is None
