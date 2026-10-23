"""Date-time comparison on the value a JSONPath or XPath body matcher selects."""

from __future__ import annotations

import json
from pathlib import Path

from mockserver import DateTimeMatcher, JsonPathBody, XPathBody
from mockserver.models import Body, Expectation, HttpRequest

FIXTURES = Path(__file__).resolve().parents[2] / "test-fixtures" / "expectations"


class TestBuilders:
    def test_json_path_with_date_time(self):
        body = Body.json_path("$.order.created", DateTimeMatcher.after("now -1 days").with_zone("Europe/London"))
        assert body.to_dict() == {
            "type": "JSON_PATH",
            "jsonPath": "$.order.created",
            "dateTime": {"after": "now -1 days", "zone": "Europe/London"},
        }

    def test_xpath_with_date_time(self):
        body = Body.xpath("/order/@expires", None, DateTimeMatcher.before("2026-01-01").with_not(True))
        assert body.to_dict() == {
            "type": "XPATH",
            "xpath": "/order/@expires",
            "dateTime": {"not": True, "before": "2026-01-01"},
        }

    def test_without_date_time_nothing_is_written(self):
        assert JsonPathBody(json_path="$.a").to_dict() == {"type": "JSON_PATH", "jsonPath": "$.a"}
        assert XPathBody(xpath="/a").to_dict() == {"type": "XPATH", "xpath": "/a"}


class TestDeserialisation:
    def test_json_path_date_time_round_trips(self):
        data = {
            "path": "/orders",
            "body": {"type": "JSON_PATH", "jsonPath": "$.created", "dateTime": {"equalToDateTime": "now", "truncateActual": "startOfDay"}},
        }
        request = HttpRequest.from_dict(data)
        assert request.body.date_time == DateTimeMatcher.equal_to_date_time("now").with_truncate_actual("startOfDay")
        assert request.to_dict() == data

    def test_xpath_date_time_round_trips(self):
        data = {
            "path": "/orders",
            "body": {"type": "XPATH", "xpath": "/a", "namespacePrefixes": {"x": "urn:x"}, "dateTime": {"before": "now"}},
        }
        request = HttpRequest.from_dict(data)
        assert request.body.date_time == DateTimeMatcher.before("now")
        assert request.to_dict() == data


class TestSharedFixtures:
    def test_fixtures_round_trip_exactly(self):
        for name in ("matcher_body_json_path_date_time.json", "matcher_body_xpath_date_time.json"):
            data = json.loads((FIXTURES / name).read_text())
            expectation = Expectation.from_dict(data)
            assert isinstance(expectation.http_request.body.date_time, DateTimeMatcher)
            assert expectation.to_dict()["httpRequest"]["body"] == data["httpRequest"]["body"]
