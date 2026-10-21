"""Date-time matcher object form for header, query, path parameter and cookie values."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from mockserver import DateTimeMatcher
from mockserver.models import Expectation, HttpRequest, KeyToMultiValue

FIXTURE = (
    Path(__file__).resolve().parents[2] / "test-fixtures" / "expectations" / "matcher_request_date_time.json"
)


class TestBuilders:
    def test_operators(self):
        assert DateTimeMatcher.before("2026-10-21T00:00:00Z").to_dict() == {"before": "2026-10-21T00:00:00Z"}
        assert DateTimeMatcher.after("now -1 hours").to_dict() == {"after": "now -1 hours"}
        assert DateTimeMatcher.equal_to_date_time("now").to_dict() == {"equalToDateTime": "now"}

    def test_every_option(self):
        matcher = (
            DateTimeMatcher.equal_to_date_time("now +1 days")
            .with_actual_format("rfc1123")
            .with_truncate_expected("first day of month")
            .with_truncate_actual("startOfDay")
            .with_apply_truncation_last(True)
            .with_zone("Europe/London")
            .with_not(True)
        )
        assert matcher.to_dict() == {
            "not": True,
            "equalToDateTime": "now +1 days",
            "actualFormat": "rfc1123",
            "truncateExpected": "first day of month",
            "truncateActual": "startOfDay",
            "applyTruncationLast": True,
            "zone": "Europe/London",
        }

    def test_options_return_a_new_matcher(self):
        original = DateTimeMatcher.after("now")
        changed = original.with_zone("UTC").with_not(True)
        assert original.to_dict() == {"after": "now"}
        assert changed.to_dict() == {"not": True, "after": "now", "zone": "UTC"}
        assert changed is not original

    def test_unset_options_are_never_written(self):
        assert None not in DateTimeMatcher.before("now").with_actual_format("iso").to_dict().values()

    def test_rejects_an_unknown_operator(self):
        with pytest.raises(ValueError):
            DateTimeMatcher(operator="since", expected="now")

    def test_from_dict_rejects_two_operators_or_none(self):
        with pytest.raises(ValueError):
            DateTimeMatcher.from_dict({"before": "now", "after": "now"})
        with pytest.raises(ValueError):
            DateTimeMatcher.from_dict({"zone": "UTC"})


class TestRequestSerialisation:
    def test_every_position(self):
        request = (
            HttpRequest(path="/x", path_parameters={"day": DateTimeMatcher.before("now")})
            .with_header("X-Issued-At", DateTimeMatcher.after("now -1 hours"))
            .with_query_param("from", DateTimeMatcher.after("2026-01-01").with_actual_format("dd/MM/yyyy"))
            .with_cookie("seen", DateTimeMatcher.equal_to_date_time("now").with_not(True))
        )
        assert request.to_dict() == {
            "path": "/x",
            "headers": [{"name": "X-Issued-At", "values": [{"after": "now -1 hours"}]}],
            "queryStringParameters": [
                {"name": "from", "values": [{"after": "2026-01-01", "actualFormat": "dd/MM/yyyy"}]}
            ],
            "pathParameters": {"day": [{"before": "now"}]},
            "cookies": {"seen": {"not": True, "equalToDateTime": "now"}},
        }

    def test_with_key_match_style(self):
        request = HttpRequest(headers={"keyMatchStyle": "MATCHING_KEY", "X-At": [DateTimeMatcher.after("now")]})
        assert request.to_dict()["headers"] == {"keyMatchStyle": "MATCHING_KEY", "X-At": [{"after": "now"}]}

    def test_builder_to_json_to_model_and_back(self):
        matcher = DateTimeMatcher.before("2026-10-21T00:00:00Z").with_actual_format("rfc1123")
        request = HttpRequest(path="/x").with_header("If-Modified-Since", matcher)
        wire = json.loads(json.dumps(request.to_dict()))
        parsed = HttpRequest.from_dict(wire)
        assert parsed.headers == [KeyToMultiValue(name="If-Modified-Since", values=[matcher])]
        assert parsed.to_dict() == wire


class TestRequestDeserialisation:
    def test_operator_object_becomes_a_date_time_matcher_in_every_position(self):
        request = HttpRequest.from_dict({
            "headers": {"X-At": [{"after": "now -1 hours"}]},
            "queryStringParameters": [{"name": "from", "values": [{"before": "now", "zone": "UTC"}]}],
            "pathParameters": {"day": [{"equalToDateTime": "now"}]},
            "cookies": {"seen": {"not": True, "after": "now"}},
        })
        assert request.headers[0].values == [DateTimeMatcher.after("now -1 hours")]
        assert request.query_string_parameters[0].values == [DateTimeMatcher.before("now").with_zone("UTC")]
        assert request.path_parameters[0].values == [DateTimeMatcher.equal_to_date_time("now")]
        assert request.cookies[0].values == [DateTimeMatcher.after("now").with_not(True)]

    def test_plain_string_that_looks_like_a_date_time_stays_a_string(self):
        data = {"headers": {"X-Plain": ["before:2026-01-01"]}, "cookies": {"c": "after:now"}}
        request = HttpRequest.from_dict(data)
        assert request.headers[0].values == ["before:2026-01-01"]
        assert request.cookies[0].values == ["after:now"]
        assert request.to_dict() == {
            "headers": [{"name": "X-Plain", "values": ["before:2026-01-01"]}],
            "cookies": {"c": "after:now"},
        }

    def test_nottable_and_schema_objects_stay_dicts(self):
        nottable = {"not": True, "value": "abc"}
        schema = {"schema": {"type": "string", "format": "date-time"}}
        request = HttpRequest.from_dict({"headers": {"A": [nottable], "B": [schema]}, "cookies": {"c": nottable}})
        assert request.headers[0].values == [nottable]
        assert request.headers[1].values == [schema]
        assert request.cookies[0].values == [nottable]
        assert request.to_dict()["headers"] == [
            {"name": "A", "values": [nottable]},
            {"name": "B", "values": [schema]},
        ]

    def test_objects_outside_the_date_time_form_stay_dicts(self):
        mixed = {"after": "now", "value": "x"}
        two_operators = {"before": "now", "after": "now"}
        request = HttpRequest.from_dict({"headers": {"A": [mixed], "B": [two_operators]}})
        assert request.headers[0].values == [mixed]
        assert request.headers[1].values == [two_operators]

    def test_explicit_not_false_round_trips(self):
        data = {"headers": [{"name": "A", "values": [{"not": False, "after": "now"}]}]}
        assert HttpRequest.from_dict(data).to_dict() == data


class TestSharedFixture:
    def test_fixture_round_trips_exactly(self):
        data = json.loads(FIXTURE.read_text())
        expectation = Expectation.from_dict(data)
        request = expectation.http_request
        headers = {item.name: item.values for item in request.headers}
        assert headers["X-Issued-At"] == [DateTimeMatcher.after("now -1 hours")]
        assert headers["If-Modified-Since"] == [
            DateTimeMatcher.before("2026-10-21T00:00:00Z").with_actual_format("rfc1123")
        ]
        assert headers["X-Plain"] == ["before:2026-01-01"]
        assert request.cookies[0].values == [
            DateTimeMatcher.equal_to_date_time("now +1 days")
            .with_not(True)
            .with_truncate_expected("first day of month")
            .with_truncate_actual("startOfDay")
            .with_apply_truncation_last(True)
        ]

        wire = expectation.to_dict()["httpRequest"]
        source = data["httpRequest"]
        assert {h["name"]: h["values"] for h in wire["headers"]} == source["headers"]
        assert {q["name"]: q["values"] for q in wire["queryStringParameters"]} == source["queryStringParameters"]
        assert wire["cookies"] == source["cookies"]
