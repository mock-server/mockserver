from __future__ import annotations

import pytest

from mockserver.models import (
    Body,
    ConnectionOptions,
    Delay,
    DelayDistribution,
    Expectation,
    ExpectationId,
    HttpClassCallback,
    HttpError,
    HttpForward,
    HttpObjectCallback,
    HttpOverrideForwardedRequest,
    HttpRequest,
    HttpRequestAndHttpResponse,
    HttpResponse,
    HttpTemplate,
    KeyToMultiValue,
    OpenAPIDefinition,
    OpenAPIExpectation,
    Ports,
    RequestDefinition,
    SocketAddress,
    TimeToLive,
    Times,
    Verification,
    VerificationSequence,
    VerificationTimes,
    _from_camel,
    _serialize_body,
    _serialize_value,
    _strip_none,
    _to_camel,
)


class TestToCamel:
    def test_mapped_field(self):
        assert _to_camel("status_code") == "statusCode"

    def test_mapped_field_not_body(self):
        assert _to_camel("not_body") == "not"

    def test_mapped_field_query_string_parameters(self):
        assert _to_camel("query_string_parameters") == "queryStringParameters"

    def test_unmapped_single_word(self):
        assert _to_camel("method") == "method"

    def test_unmapped_two_words(self):
        assert _to_camel("my_field") == "myField"

    def test_unmapped_three_words(self):
        assert _to_camel("my_long_field") == "myLongField"

    def test_mapped_time_to_live(self):
        assert _to_camel("time_to_live") == "timeToLive"

    def test_mapped_keep_alive(self):
        assert _to_camel("keep_alive") == "keepAlive"


class TestFromCamel:
    def test_mapped_field(self):
        assert _from_camel("statusCode") == "status_code"

    def test_mapped_field_not(self):
        assert _from_camel("not") == "not_body"

    def test_unmapped_single_word(self):
        assert _from_camel("method") == "method"

    def test_unmapped_camel(self):
        assert _from_camel("myField") == "my_field"


class TestStripNone:
    def test_removes_none_values(self):
        assert _strip_none({"a": 1, "b": None, "c": 3}) == {"a": 1, "c": 3}

    def test_keeps_false(self):
        assert _strip_none({"a": False}) == {"a": False}

    def test_keeps_zero(self):
        assert _strip_none({"a": 0}) == {"a": 0}

    def test_keeps_empty_string(self):
        assert _strip_none({"a": ""}) == {"a": ""}

    def test_keeps_empty_list(self):
        assert _strip_none({"a": []}) == {"a": []}

    def test_all_none(self):
        assert _strip_none({"a": None, "b": None}) == {}

    def test_empty_dict(self):
        assert _strip_none({}) == {}


class TestSerializeValue:
    def test_with_to_dict(self):
        d = Delay(time_unit="SECONDS", value=5)
        assert _serialize_value(d) == {"timeUnit": "SECONDS", "value": 5}

    def test_with_list(self):
        items = [Delay(time_unit="SECONDS", value=1), Delay(time_unit="SECONDS", value=2)]
        result = _serialize_value(items)
        assert result == [
            {"timeUnit": "SECONDS", "value": 1},
            {"timeUnit": "SECONDS", "value": 2},
        ]

    def test_with_primitive(self):
        assert _serialize_value("hello") == "hello"
        assert _serialize_value(42) == 42


class TestSerializeBody:
    def test_none(self):
        assert _serialize_body(None) is None

    def test_string(self):
        assert _serialize_body("hello") == "hello"

    def test_dict(self):
        assert _serialize_body({"key": "val"}) == {"key": "val"}

    def test_body_object(self):
        b = Body(type="STRING", string="hello")
        assert _serialize_body(b) == {"type": "STRING", "string": "hello"}


class TestDelayDistribution:
    def test_defaults(self):
        d = DelayDistribution()
        assert d.type is None
        assert d.min is None
        assert d.max is None

    def test_uniform(self):
        d = DelayDistribution(type="UNIFORM", min=100, max=500)
        assert d.to_dict() == {"type": "UNIFORM", "min": 100, "max": 500}

    def test_log_normal(self):
        d = DelayDistribution(type="LOG_NORMAL", median=200, p99=800)
        assert d.to_dict() == {"type": "LOG_NORMAL", "median": 200, "p99": 800}

    def test_gaussian(self):
        d = DelayDistribution(type="GAUSSIAN", mean=200, std_dev=50)
        assert d.to_dict() == {"type": "GAUSSIAN", "mean": 200, "stdDev": 50}

    def test_from_dict(self):
        d = DelayDistribution.from_dict({"type": "UNIFORM", "min": 10, "max": 20})
        assert d.type == "UNIFORM"
        assert d.min == 10
        assert d.max == 20

    def test_from_dict_none(self):
        assert DelayDistribution.from_dict(None) is None

    def test_round_trip(self):
        original = DelayDistribution(type="GAUSSIAN", mean=100, std_dev=25)
        restored = DelayDistribution.from_dict(original.to_dict())
        assert restored.type == original.type
        assert restored.mean == original.mean
        assert restored.std_dev == original.std_dev


class TestDelay:
    def test_defaults(self):
        d = Delay()
        assert d.time_unit == "MILLISECONDS"
        assert d.value == 0
        assert d.distribution is None

    def test_construction(self):
        d = Delay(time_unit="SECONDS", value=5)
        assert d.time_unit == "SECONDS"
        assert d.value == 5

    def test_to_dict(self):
        d = Delay(time_unit="SECONDS", value=10)
        assert d.to_dict() == {"timeUnit": "SECONDS", "value": 10}

    def test_from_dict(self):
        d = Delay.from_dict({"timeUnit": "SECONDS", "value": 3})
        assert d.time_unit == "SECONDS"
        assert d.value == 3

    def test_from_dict_none(self):
        assert Delay.from_dict(None) is None

    def test_from_dict_defaults(self):
        d = Delay.from_dict({})
        assert d.time_unit == "MILLISECONDS"
        assert d.value == 0

    def test_round_trip(self):
        original = Delay(time_unit="MINUTES", value=2)
        restored = Delay.from_dict(original.to_dict())
        assert restored.time_unit == original.time_unit
        assert restored.value == original.value

    def test_with_distribution(self):
        dist = DelayDistribution(type="UNIFORM", min=100, max=500)
        d = Delay(time_unit="MILLISECONDS", distribution=dist)
        result = d.to_dict()
        assert result == {
            "timeUnit": "MILLISECONDS",
            "value": 0,
            "distribution": {"type": "UNIFORM", "min": 100, "max": 500},
        }

    def test_from_dict_with_distribution(self):
        d = Delay.from_dict({
            "timeUnit": "MILLISECONDS",
            "value": 0,
            "distribution": {"type": "LOG_NORMAL", "median": 200, "p99": 800},
        })
        assert d.distribution is not None
        assert d.distribution.type == "LOG_NORMAL"
        assert d.distribution.median == 200
        assert d.distribution.p99 == 800

    def test_round_trip_with_distribution(self):
        dist = DelayDistribution(type="GAUSSIAN", mean=200, std_dev=50)
        original = Delay(time_unit="MILLISECONDS", distribution=dist)
        restored = Delay.from_dict(original.to_dict())
        assert restored.distribution is not None
        assert restored.distribution.type == original.distribution.type
        assert restored.distribution.mean == original.distribution.mean
        assert restored.distribution.std_dev == original.distribution.std_dev


class TestTimes:
    def test_defaults(self):
        t = Times()
        assert t.remaining_times is None
        assert t.unlimited is None

    def test_construction(self):
        t = Times(remaining_times=5, unlimited=False)
        assert t.remaining_times == 5
        assert t.unlimited is False

    def test_unlimited_factory(self):
        t = Times.unlimited()
        assert t.unlimited is True
        assert t.remaining_times is None

    def test_exactly_factory(self):
        t = Times.exactly(3)
        assert t.remaining_times == 3
        assert t.unlimited is False

    def test_to_dict(self):
        t = Times(remaining_times=2, unlimited=False)
        assert t.to_dict() == {"remainingTimes": 2, "unlimited": False}

    def test_to_dict_strips_none(self):
        t = Times(unlimited=True)
        assert t.to_dict() == {"unlimited": True}

    def test_from_dict(self):
        t = Times.from_dict({"remainingTimes": 7, "unlimited": False})
        assert t.remaining_times == 7
        assert t.unlimited is False

    def test_from_dict_none(self):
        assert Times.from_dict(None) is None

    def test_round_trip(self):
        original = Times.exactly(10)
        restored = Times.from_dict(original.to_dict())
        assert restored.remaining_times == 10
        assert restored.unlimited is False


class TestTimeToLive:
    def test_defaults(self):
        ttl = TimeToLive()
        assert ttl.time_unit is None
        assert ttl.time_to_live is None
        assert ttl.unlimited is None

    def test_unlimited_factory(self):
        ttl = TimeToLive.unlimited()
        assert ttl.unlimited is True
        assert ttl.time_unit is None
        assert ttl.time_to_live is None

    def test_exactly_factory(self):
        ttl = TimeToLive.exactly(60, "SECONDS")
        assert ttl.time_to_live == 60
        assert ttl.time_unit == "SECONDS"
        assert ttl.unlimited is False

    def test_to_dict(self):
        ttl = TimeToLive(time_unit="SECONDS", time_to_live=30, unlimited=False)
        assert ttl.to_dict() == {"timeUnit": "SECONDS", "timeToLive": 30, "unlimited": False}

    def test_to_dict_strips_none(self):
        ttl = TimeToLive(unlimited=True)
        assert ttl.to_dict() == {"unlimited": True}

    def test_from_dict(self):
        ttl = TimeToLive.from_dict({"timeUnit": "MINUTES", "timeToLive": 5, "unlimited": False})
        assert ttl.time_unit == "MINUTES"
        assert ttl.time_to_live == 5
        assert ttl.unlimited is False

    def test_from_dict_none(self):
        assert TimeToLive.from_dict(None) is None

    def test_round_trip(self):
        original = TimeToLive.exactly(120, "SECONDS")
        restored = TimeToLive.from_dict(original.to_dict())
        assert restored.time_to_live == 120
        assert restored.time_unit == "SECONDS"
        assert restored.unlimited is False


class TestKeyToMultiValue:
    def test_defaults(self):
        kv = KeyToMultiValue()
        assert kv.name == ""
        assert kv.values == []

    def test_construction(self):
        kv = KeyToMultiValue(name="Content-Type", values=["application/json"])
        assert kv.name == "Content-Type"
        assert kv.values == ["application/json"]

    def test_to_dict(self):
        kv = KeyToMultiValue(name="Accept", values=["text/html", "text/plain"])
        assert kv.to_dict() == {"name": "Accept", "values": ["text/html", "text/plain"]}

    def test_to_dict_always_includes_name_and_values(self):
        kv = KeyToMultiValue()
        assert kv.to_dict() == {"name": "", "values": []}

    def test_from_dict(self):
        kv = KeyToMultiValue.from_dict({"name": "X-Custom", "values": ["v1"]})
        assert kv.name == "X-Custom"
        assert kv.values == ["v1"]

    def test_from_dict_none(self):
        assert KeyToMultiValue.from_dict(None) is None

    def test_from_dict_defaults(self):
        kv = KeyToMultiValue.from_dict({})
        assert kv.name == ""
        assert kv.values == []

    def test_round_trip(self):
        original = KeyToMultiValue(name="key", values=["a", "b"])
        restored = KeyToMultiValue.from_dict(original.to_dict())
        assert restored.name == original.name
        assert restored.values == original.values


class TestBody:
    def test_defaults(self):
        b = Body()
        assert b.type is None
        assert b.string is None
        assert b.json is None
        assert b.not_body is None

    def test_string_factory(self):
        b = Body.string("hello")
        assert b.type == "STRING"
        assert b.string == "hello"

    def test_json_factory_with_dict(self):
        b = Body.json({"key": "value"})
        assert b.type == "JSON"
        assert b.json == {"key": "value"}

    def test_json_factory_with_list(self):
        b = Body.json([1, 2, 3])
        assert b.type == "JSON"
        assert b.json == [1, 2, 3]

    def test_regex_factory(self):
        b = Body.regex("^/api/.*")
        assert b.type == "REGEX"
        assert b.string == "^/api/.*"

    def test_exact_factory(self):
        b = Body.exact("exact match")
        assert b.type == "STRING"
        assert b.string == "exact match"

    def test_xml_factory(self):
        b = Body.xml("<root><child/></root>")
        assert b.type == "XML"
        assert b.string == "<root><child/></root>"

    def test_to_dict_string_body(self):
        b = Body(type="STRING", string="hello")
        assert b.to_dict() == {"type": "STRING", "string": "hello"}

    def test_to_dict_json_body(self):
        b = Body(type="JSON", json={"a": 1})
        assert b.to_dict() == {"type": "JSON", "json": {"a": 1}}

    def test_to_dict_not_body_key_mapping(self):
        b = Body(type="STRING", string="test", not_body=True)
        result = b.to_dict()
        assert result["not"] is True
        assert "not_body" not in result

    def test_to_dict_with_content_type(self):
        b = Body(type="JSON", json={}, content_type="application/json")
        result = b.to_dict()
        assert result["contentType"] == "application/json"

    def test_to_dict_with_base64_bytes(self):
        b = Body(type="BINARY", base64_bytes="AQID")
        result = b.to_dict()
        assert result["base64Bytes"] == "AQID"

    def test_to_dict_with_charset(self):
        b = Body(type="STRING", string="hi", charset="UTF-8")
        result = b.to_dict()
        assert result["charset"] == "UTF-8"

    def test_to_dict_excludes_none_fields(self):
        b = Body(type="STRING", string="hello")
        result = b.to_dict()
        assert "json" not in result
        assert "base64Bytes" not in result
        assert "not" not in result
        assert "contentType" not in result
        assert "charset" not in result

    def test_from_dict(self):
        b = Body.from_dict({"type": "STRING", "string": "hello"})
        assert b.type == "STRING"
        assert b.string == "hello"

    def test_from_dict_with_not(self):
        b = Body.from_dict({"type": "STRING", "string": "x", "not": True})
        assert b.not_body is True

    def test_from_dict_with_base64_bytes(self):
        b = Body.from_dict({"type": "BINARY", "base64Bytes": "AQID"})
        assert b.base64_bytes == "AQID"

    def test_from_dict_none(self):
        assert Body.from_dict(None) is None

    def test_round_trip_string(self):
        original = Body.string("test-string")
        restored = Body.from_dict(original.to_dict())
        assert restored.type == "STRING"
        assert restored.string == "test-string"

    def test_round_trip_json(self):
        original = Body.json({"nested": {"data": [1, 2]}})
        restored = Body.from_dict(original.to_dict())
        assert restored.type == "JSON"
        assert restored.json == {"nested": {"data": [1, 2]}}

    def test_round_trip_not_body(self):
        original = Body(type="REGEX", string=".*", not_body=True, content_type="text/plain")
        restored = Body.from_dict(original.to_dict())
        assert restored.not_body is True
        assert restored.content_type == "text/plain"
        assert restored.type == "REGEX"
        assert restored.string == ".*"


class TestSocketAddress:
    def test_defaults(self):
        sa = SocketAddress()
        assert sa.host is None
        assert sa.port is None
        assert sa.scheme is None

    def test_construction(self):
        sa = SocketAddress(host="localhost", port=443, scheme="HTTPS")
        assert sa.host == "localhost"
        assert sa.port == 443
        assert sa.scheme == "HTTPS"

    def test_to_dict(self):
        sa = SocketAddress(host="example.com", port=80, scheme="HTTP")
        assert sa.to_dict() == {"host": "example.com", "port": 80, "scheme": "HTTP"}

    def test_to_dict_strips_none(self):
        sa = SocketAddress(host="example.com")
        assert sa.to_dict() == {"host": "example.com"}

    def test_from_dict(self):
        sa = SocketAddress.from_dict({"host": "h", "port": 9090, "scheme": "HTTPS"})
        assert sa.host == "h"
        assert sa.port == 9090
        assert sa.scheme == "HTTPS"

    def test_from_dict_none(self):
        assert SocketAddress.from_dict(None) is None

    def test_round_trip(self):
        original = SocketAddress(host="localhost", port=8080, scheme="HTTP")
        restored = SocketAddress.from_dict(original.to_dict())
        assert restored.host == original.host
        assert restored.port == original.port
        assert restored.scheme == original.scheme


class TestHttpRequest:
    def test_request_factory_no_path(self):
        r = HttpRequest.request()
        assert r.path is None
        assert r.method is None

    def test_request_factory_with_path(self):
        r = HttpRequest.request("/api/test")
        assert r.path == "/api/test"

    def test_with_method(self):
        r = HttpRequest.request().with_method("POST")
        assert r.method == "POST"

    def test_with_path(self):
        r = HttpRequest.request().with_path("/new")
        assert r.path == "/new"

    def test_with_header(self):
        r = HttpRequest.request().with_header("Content-Type", "application/json")
        assert len(r.headers) == 1
        assert r.headers[0].name == "Content-Type"
        assert r.headers[0].values == ["application/json"]

    def test_with_header_multiple_values(self):
        r = HttpRequest.request().with_header("Accept", "text/html", "text/plain")
        assert r.headers[0].values == ["text/html", "text/plain"]

    def test_with_header_accumulates(self):
        r = HttpRequest.request().with_header("H1", "v1").with_header("H2", "v2")
        assert len(r.headers) == 2

    def test_with_query_param(self):
        r = HttpRequest.request().with_query_param("q", "search")
        assert len(r.query_string_parameters) == 1
        assert r.query_string_parameters[0].name == "q"
        assert r.query_string_parameters[0].values == ["search"]

    def test_with_query_param_multiple_values(self):
        r = HttpRequest.request().with_query_param("tags", "a", "b", "c")
        assert r.query_string_parameters[0].values == ["a", "b", "c"]

    def test_with_cookie(self):
        r = HttpRequest.request().with_cookie("session", "abc123")
        assert len(r.cookies) == 1
        assert r.cookies[0].name == "session"
        assert r.cookies[0].values == ["abc123"]

    def test_with_body_string(self):
        r = HttpRequest.request().with_body("plain text")
        assert r.body == "plain text"

    def test_with_body_dict(self):
        r = HttpRequest.request().with_body({"key": "value"})
        assert r.body == {"key": "value"}

    def test_with_body_object(self):
        b = Body.json({"test": True})
        r = HttpRequest.request().with_body(b)
        assert r.body is b

    def test_with_secure(self):
        r = HttpRequest.request().with_secure(True)
        assert r.secure is True

    def test_with_secure_false(self):
        r = HttpRequest.request().with_secure(False)
        assert r.secure is False

    def test_with_keep_alive(self):
        r = HttpRequest.request().with_keep_alive(True)
        assert r.keep_alive is True

    def test_builder_chaining_returns_self(self):
        r = HttpRequest.request()
        assert r.with_method("GET") is r
        assert r.with_path("/p") is r
        assert r.with_header("H", "v") is r
        assert r.with_query_param("q", "v") is r
        assert r.with_cookie("c", "v") is r
        assert r.with_body("b") is r
        assert r.with_secure(True) is r
        assert r.with_keep_alive(True) is r

    def test_full_chaining(self):
        r = (
            HttpRequest.request("/api")
            .with_method("POST")
            .with_header("Content-Type", "application/json")
            .with_query_param("page", "1")
            .with_cookie("token", "xyz")
            .with_body(Body.json({"data": 1}))
            .with_secure(True)
            .with_keep_alive(False)
        )
        assert r.path == "/api"
        assert r.method == "POST"
        assert r.headers[0].name == "Content-Type"
        assert r.query_string_parameters[0].name == "page"
        assert r.cookies[0].name == "token"
        assert r.body.type == "JSON"
        assert r.secure is True
        assert r.keep_alive is False

    def test_to_dict_minimal(self):
        r = HttpRequest.request("/test")
        result = r.to_dict()
        assert result == {"path": "/test"}

    def test_to_dict_string_body(self):
        r = HttpRequest(path="/test", body="hello")
        result = r.to_dict()
        assert result["body"] == "hello"

    def test_to_dict_dict_body(self):
        r = HttpRequest(path="/test", body={"key": "val"})
        result = r.to_dict()
        assert result["body"] == {"key": "val"}

    def test_to_dict_body_object(self):
        r = HttpRequest(path="/test", body=Body.string("text"))
        result = r.to_dict()
        assert result["body"] == {"type": "STRING", "string": "text"}

    def test_to_dict_with_headers(self):
        r = HttpRequest.request().with_header("Accept", "text/html")
        result = r.to_dict()
        assert result["headers"] == [{"name": "Accept", "values": ["text/html"]}]

    def test_to_dict_with_query_params(self):
        r = HttpRequest.request().with_query_param("q", "val")
        result = r.to_dict()
        assert result["queryStringParameters"] == [{"name": "q", "values": ["val"]}]

    def test_to_dict_with_cookies(self):
        r = HttpRequest.request().with_cookie("sid", "abc")
        result = r.to_dict()
        assert result["cookies"] == [{"name": "sid", "values": ["abc"]}]

    def test_to_dict_camel_case_keys(self):
        r = HttpRequest(
            keep_alive=True,
            secure=False,
            query_string_parameters=[KeyToMultiValue(name="q", values=["v"])],
        )
        result = r.to_dict()
        assert "keepAlive" in result
        assert "queryStringParameters" in result
        assert "keep_alive" not in result
        assert "query_string_parameters" not in result

    def test_to_dict_with_socket_address(self):
        r = HttpRequest(
            path="/p",
            socket_address=SocketAddress(host="host", port=443, scheme="HTTPS"),
        )
        result = r.to_dict()
        assert result["socketAddress"] == {"host": "host", "port": 443, "scheme": "HTTPS"}

    def test_to_dict_excludes_none(self):
        r = HttpRequest(path="/only-path")
        result = r.to_dict()
        assert "method" not in result
        assert "headers" not in result
        assert "cookies" not in result
        assert "body" not in result
        assert "secure" not in result
        assert "keepAlive" not in result

    def test_from_dict(self):
        r = HttpRequest.from_dict({
            "method": "GET",
            "path": "/test",
            "keepAlive": True,
        })
        assert r.method == "GET"
        assert r.path == "/test"
        assert r.keep_alive is True

    def test_from_dict_with_headers(self):
        r = HttpRequest.from_dict({
            "headers": [{"name": "Accept", "values": ["text/html"]}],
        })
        assert len(r.headers) == 1
        assert r.headers[0].name == "Accept"

    def test_from_dict_with_body_string(self):
        r = HttpRequest.from_dict({"body": "plain text"})
        assert r.body == "plain text"

    def test_from_dict_with_body_typed(self):
        r = HttpRequest.from_dict({"body": {"type": "JSON", "json": {"a": 1}}})
        assert isinstance(r.body, Body)
        assert r.body.type == "JSON"
        assert r.body.json == {"a": 1}

    def test_from_dict_with_body_dict_no_type(self):
        r = HttpRequest.from_dict({"body": {"key": "value"}})
        assert r.body == {"key": "value"}
        assert not isinstance(r.body, Body)

    def test_from_dict_with_body_dict_unknown_type(self):
        r = HttpRequest.from_dict({"body": {"type": "user", "name": "Alice"}})
        assert r.body == {"type": "user", "name": "Alice"}
        assert not isinstance(r.body, Body)

    def test_from_dict_none(self):
        assert HttpRequest.from_dict(None) is None

    def test_from_dict_missing_optional_fields(self):
        r = HttpRequest.from_dict({})
        assert r.method is None
        assert r.path is None
        assert r.headers is None
        assert r.cookies is None
        assert r.body is None
        assert r.secure is None
        assert r.keep_alive is None

    def test_round_trip(self):
        original = (
            HttpRequest.request("/api/users")
            .with_method("POST")
            .with_header("Content-Type", "application/json")
            .with_query_param("page", "1")
            .with_cookie("auth", "tok")
            .with_body(Body.json({"name": "test"}))
            .with_secure(True)
            .with_keep_alive(False)
        )
        restored = HttpRequest.from_dict(original.to_dict())
        assert restored.path == "/api/users"
        assert restored.method == "POST"
        assert restored.headers[0].name == "Content-Type"
        assert restored.query_string_parameters[0].name == "page"
        assert restored.cookies[0].name == "auth"
        assert isinstance(restored.body, Body)
        assert restored.body.json == {"name": "test"}
        assert restored.secure is True
        assert restored.keep_alive is False


class TestConnectionOptions:
    def test_defaults(self):
        co = ConnectionOptions()
        assert co.close_socket is None
        assert co.close_socket_delay is None

    def test_construction(self):
        co = ConnectionOptions(
            close_socket=True,
            suppress_content_length_header=True,
            content_length_header_override=100,
            suppress_connection_header=False,
            keep_alive_override=True,
        )
        assert co.close_socket is True
        assert co.content_length_header_override == 100

    def test_to_dict(self):
        co = ConnectionOptions(close_socket=True, keep_alive_override=False)
        result = co.to_dict()
        assert result == {"closeSocket": True, "keepAliveOverride": False}

    def test_to_dict_with_delay(self):
        co = ConnectionOptions(
            close_socket=True,
            close_socket_delay=Delay(time_unit="SECONDS", value=1),
        )
        result = co.to_dict()
        assert result["closeSocketDelay"] == {"timeUnit": "SECONDS", "value": 1}

    def test_from_dict(self):
        co = ConnectionOptions.from_dict({
            "closeSocket": True,
            "suppressContentLengthHeader": False,
            "contentLengthHeaderOverride": 50,
            "suppressConnectionHeader": True,
            "keepAliveOverride": False,
        })
        assert co.close_socket is True
        assert co.suppress_content_length_header is False
        assert co.content_length_header_override == 50
        assert co.suppress_connection_header is True
        assert co.keep_alive_override is False

    def test_from_dict_with_delay(self):
        co = ConnectionOptions.from_dict({
            "closeSocket": True,
            "closeSocketDelay": {"timeUnit": "SECONDS", "value": 2},
        })
        assert co.close_socket_delay.time_unit == "SECONDS"
        assert co.close_socket_delay.value == 2

    def test_from_dict_none(self):
        assert ConnectionOptions.from_dict(None) is None

    def test_round_trip(self):
        original = ConnectionOptions(
            close_socket=True,
            close_socket_delay=Delay(time_unit="SECONDS", value=3),
            suppress_content_length_header=True,
            content_length_header_override=200,
            suppress_connection_header=False,
            keep_alive_override=True,
        )
        restored = ConnectionOptions.from_dict(original.to_dict())
        assert restored.close_socket is True
        assert restored.close_socket_delay.time_unit == "SECONDS"
        assert restored.close_socket_delay.value == 3
        assert restored.suppress_content_length_header is True
        assert restored.content_length_header_override == 200
        assert restored.suppress_connection_header is False
        assert restored.keep_alive_override is True


class TestHttpResponse:
    def test_response_factory_no_args(self):
        r = HttpResponse.response()
        assert r.status_code is None
        assert r.body is None

    def test_response_factory_with_body(self):
        r = HttpResponse.response("hello")
        assert r.body == "hello"
        assert r.status_code == 200
        assert r.reason_phrase == "OK"

    def test_response_factory_with_body_and_status(self):
        r = HttpResponse.response("created", 201)
        assert r.body == "created"
        assert r.status_code == 201
        assert r.reason_phrase is None

    def test_response_factory_with_status_only(self):
        r = HttpResponse.response(status_code=204)
        assert r.status_code == 204
        assert r.body is None

    def test_not_found_response(self):
        r = HttpResponse.not_found_response()
        assert r.status_code == 404
        assert r.reason_phrase == "Not Found"

    def test_with_status_code(self):
        r = HttpResponse().with_status_code(201)
        assert r.status_code == 201

    def test_with_header(self):
        r = HttpResponse().with_header("Content-Type", "application/json")
        assert len(r.headers) == 1
        assert r.headers[0].name == "Content-Type"
        assert r.headers[0].values == ["application/json"]

    def test_with_header_accumulates(self):
        r = HttpResponse().with_header("H1", "v1").with_header("H2", "v2")
        assert len(r.headers) == 2

    def test_with_cookie(self):
        r = HttpResponse().with_cookie("session", "abc")
        assert r.cookies[0].name == "session"
        assert r.cookies[0].values == ["abc"]

    def test_with_body_string(self):
        r = HttpResponse().with_body("text")
        assert r.body == "text"

    def test_with_body_object(self):
        b = Body.json({"result": True})
        r = HttpResponse().with_body(b)
        assert r.body is b

    def test_with_delay(self):
        d = Delay(time_unit="SECONDS", value=2)
        r = HttpResponse().with_delay(d)
        assert r.delay is d

    def test_with_reason_phrase(self):
        r = HttpResponse().with_reason_phrase("Custom Reason")
        assert r.reason_phrase == "Custom Reason"

    def test_builder_chaining_returns_self(self):
        r = HttpResponse()
        assert r.with_status_code(200) is r
        assert r.with_header("H", "v") is r
        assert r.with_cookie("c", "v") is r
        assert r.with_body("b") is r
        assert r.with_delay(Delay()) is r
        assert r.with_reason_phrase("OK") is r

    def test_full_chaining(self):
        r = (
            HttpResponse.response()
            .with_status_code(200)
            .with_reason_phrase("OK")
            .with_header("Content-Type", "text/plain")
            .with_cookie("sid", "123")
            .with_body("response body")
            .with_delay(Delay(time_unit="SECONDS", value=1))
        )
        assert r.status_code == 200
        assert r.reason_phrase == "OK"
        assert r.headers[0].name == "Content-Type"
        assert r.cookies[0].name == "sid"
        assert r.body == "response body"
        assert r.delay.value == 1

    def test_to_dict(self):
        r = HttpResponse(status_code=200, reason_phrase="OK", body="hello")
        result = r.to_dict()
        assert result == {"statusCode": 200, "reasonPhrase": "OK", "body": "hello"}

    def test_to_dict_with_body_object(self):
        r = HttpResponse(body=Body.string("text"))
        result = r.to_dict()
        assert result["body"] == {"type": "STRING", "string": "text"}

    def test_to_dict_with_delay(self):
        r = HttpResponse(status_code=200, delay=Delay(time_unit="SECONDS", value=5))
        result = r.to_dict()
        assert result["delay"] == {"timeUnit": "SECONDS", "value": 5}

    def test_to_dict_with_connection_options(self):
        r = HttpResponse(
            status_code=200,
            connection_options=ConnectionOptions(close_socket=True),
        )
        result = r.to_dict()
        assert result["connectionOptions"] == {"closeSocket": True}

    def test_to_dict_excludes_none(self):
        r = HttpResponse(status_code=200)
        result = r.to_dict()
        assert "headers" not in result
        assert "cookies" not in result
        assert "body" not in result
        assert "delay" not in result

    def test_to_dict_camel_case_keys(self):
        r = HttpResponse(status_code=200, reason_phrase="OK")
        result = r.to_dict()
        assert "statusCode" in result
        assert "reasonPhrase" in result
        assert "status_code" not in result
        assert "reason_phrase" not in result

    def test_from_dict(self):
        r = HttpResponse.from_dict({"statusCode": 200, "reasonPhrase": "OK", "body": "hello"})
        assert r.status_code == 200
        assert r.reason_phrase == "OK"
        assert r.body == "hello"

    def test_from_dict_with_typed_body(self):
        r = HttpResponse.from_dict({"body": {"type": "JSON", "json": {"a": 1}}})
        assert isinstance(r.body, Body)
        assert r.body.type == "JSON"

    def test_from_dict_with_delay(self):
        r = HttpResponse.from_dict({
            "statusCode": 200,
            "delay": {"timeUnit": "SECONDS", "value": 3},
        })
        assert r.delay.time_unit == "SECONDS"
        assert r.delay.value == 3

    def test_from_dict_with_connection_options(self):
        r = HttpResponse.from_dict({
            "connectionOptions": {"closeSocket": True},
        })
        assert r.connection_options.close_socket is True

    def test_from_dict_none(self):
        assert HttpResponse.from_dict(None) is None

    def test_from_dict_missing_optional_fields(self):
        r = HttpResponse.from_dict({})
        assert r.status_code is None
        assert r.reason_phrase is None
        assert r.headers is None
        assert r.body is None
        assert r.delay is None

    def test_round_trip(self):
        original = (
            HttpResponse.response()
            .with_status_code(201)
            .with_reason_phrase("Created")
            .with_header("Location", "/resource/1")
            .with_cookie("session", "abc")
            .with_body(Body.json({"id": 1}))
            .with_delay(Delay(time_unit="MILLISECONDS", value=100))
        )
        restored = HttpResponse.from_dict(original.to_dict())
        assert restored.status_code == 201
        assert restored.reason_phrase == "Created"
        assert restored.headers[0].name == "Location"
        assert restored.cookies[0].name == "session"
        assert isinstance(restored.body, Body)
        assert restored.body.json == {"id": 1}
        assert restored.delay.value == 100


class TestHttpForward:
    def test_forward_factory(self):
        f = HttpForward.forward()
        assert f.host is None
        assert f.port is None
        assert f.scheme is None

    def test_construction(self):
        f = HttpForward(host="example.com", port=8080, scheme="HTTPS")
        assert f.host == "example.com"
        assert f.port == 8080
        assert f.scheme == "HTTPS"

    def test_to_dict(self):
        f = HttpForward(host="example.com", port=8080, scheme="HTTP")
        assert f.to_dict() == {"host": "example.com", "port": 8080, "scheme": "HTTP"}

    def test_to_dict_with_delay(self):
        f = HttpForward(host="h", delay=Delay(time_unit="SECONDS", value=1))
        result = f.to_dict()
        assert result["delay"] == {"timeUnit": "SECONDS", "value": 1}

    def test_to_dict_strips_none(self):
        f = HttpForward(host="example.com")
        result = f.to_dict()
        assert "port" not in result
        assert "scheme" not in result

    def test_from_dict(self):
        f = HttpForward.from_dict({"host": "h", "port": 9090, "scheme": "HTTPS"})
        assert f.host == "h"
        assert f.port == 9090
        assert f.scheme == "HTTPS"

    def test_from_dict_with_delay(self):
        f = HttpForward.from_dict({
            "host": "h",
            "delay": {"timeUnit": "SECONDS", "value": 2},
        })
        assert f.delay.time_unit == "SECONDS"
        assert f.delay.value == 2

    def test_from_dict_none(self):
        assert HttpForward.from_dict(None) is None

    def test_round_trip(self):
        original = HttpForward(
            host="proxy.local",
            port=3128,
            scheme="HTTP",
            delay=Delay(time_unit="MILLISECONDS", value=500),
        )
        restored = HttpForward.from_dict(original.to_dict())
        assert restored.host == "proxy.local"
        assert restored.port == 3128
        assert restored.scheme == "HTTP"
        assert restored.delay.value == 500


class TestHttpTemplate:
    def test_defaults(self):
        t = HttpTemplate()
        assert t.template_type == "JAVASCRIPT"
        assert t.template is None

    def test_template_factory(self):
        t = HttpTemplate.template("VELOCITY", "template-content")
        assert t.template_type == "VELOCITY"
        assert t.template == "template-content"

    def test_template_factory_no_template(self):
        t = HttpTemplate.template("JAVASCRIPT")
        assert t.template_type == "JAVASCRIPT"
        assert t.template is None

    def test_to_dict(self):
        t = HttpTemplate(template_type="VELOCITY", template="$req.path")
        assert t.to_dict() == {"templateType": "VELOCITY", "template": "$req.path"}

    def test_to_dict_with_delay(self):
        t = HttpTemplate(
            template_type="JAVASCRIPT",
            template="return {};",
            delay=Delay(time_unit="SECONDS", value=1),
        )
        result = t.to_dict()
        assert result["delay"] == {"timeUnit": "SECONDS", "value": 1}

    def test_from_dict(self):
        t = HttpTemplate.from_dict({"templateType": "VELOCITY", "template": "content"})
        assert t.template_type == "VELOCITY"
        assert t.template == "content"

    def test_from_dict_defaults(self):
        t = HttpTemplate.from_dict({})
        assert t.template_type == "JAVASCRIPT"

    def test_from_dict_none(self):
        assert HttpTemplate.from_dict(None) is None

    def test_round_trip(self):
        original = HttpTemplate(
            template_type="VELOCITY",
            template="$req.method",
            delay=Delay(time_unit="SECONDS", value=2),
        )
        restored = HttpTemplate.from_dict(original.to_dict())
        assert restored.template_type == "VELOCITY"
        assert restored.template == "$req.method"
        assert restored.delay.value == 2


class TestHttpClassCallback:
    def test_callback_factory(self):
        cb = HttpClassCallback.callback("com.example.MyCallback")
        assert cb.callback_class == "com.example.MyCallback"

    def test_callback_factory_no_args(self):
        cb = HttpClassCallback.callback()
        assert cb.callback_class is None

    def test_to_dict(self):
        cb = HttpClassCallback(callback_class="com.example.Cb")
        assert cb.to_dict() == {"callbackClass": "com.example.Cb"}

    def test_to_dict_with_delay(self):
        cb = HttpClassCallback(
            callback_class="com.example.Cb",
            delay=Delay(time_unit="SECONDS", value=1),
        )
        result = cb.to_dict()
        assert result["delay"] == {"timeUnit": "SECONDS", "value": 1}

    def test_from_dict(self):
        cb = HttpClassCallback.from_dict({"callbackClass": "com.example.Cb"})
        assert cb.callback_class == "com.example.Cb"

    def test_from_dict_none(self):
        assert HttpClassCallback.from_dict(None) is None

    def test_round_trip(self):
        original = HttpClassCallback(
            callback_class="com.example.Handler",
            delay=Delay(time_unit="MILLISECONDS", value=50),
        )
        restored = HttpClassCallback.from_dict(original.to_dict())
        assert restored.callback_class == "com.example.Handler"
        assert restored.delay.value == 50


class TestHttpObjectCallback:
    def test_defaults(self):
        cb = HttpObjectCallback()
        assert cb.client_id is None
        assert cb.response_callback is None

    def test_construction(self):
        cb = HttpObjectCallback(client_id="client-1", response_callback=True)
        assert cb.client_id == "client-1"
        assert cb.response_callback is True

    def test_to_dict(self):
        cb = HttpObjectCallback(client_id="c1", response_callback=False)
        assert cb.to_dict() == {"clientId": "c1", "responseCallback": False}

    def test_to_dict_with_delay(self):
        cb = HttpObjectCallback(
            client_id="c1",
            delay=Delay(time_unit="SECONDS", value=1),
        )
        result = cb.to_dict()
        assert result["delay"] == {"timeUnit": "SECONDS", "value": 1}

    def test_from_dict(self):
        cb = HttpObjectCallback.from_dict({
            "clientId": "c2",
            "responseCallback": True,
        })
        assert cb.client_id == "c2"
        assert cb.response_callback is True

    def test_from_dict_none(self):
        assert HttpObjectCallback.from_dict(None) is None

    def test_round_trip(self):
        original = HttpObjectCallback(
            client_id="ws-client",
            response_callback=True,
            delay=Delay(time_unit="SECONDS", value=3),
        )
        restored = HttpObjectCallback.from_dict(original.to_dict())
        assert restored.client_id == "ws-client"
        assert restored.response_callback is True
        assert restored.delay.value == 3


class TestHttpError:
    def test_error_factory(self):
        e = HttpError.error()
        assert e.drop_connection is None
        assert e.response_bytes is None

    def test_construction(self):
        e = HttpError(drop_connection=True, response_bytes="AQID")
        assert e.drop_connection is True
        assert e.response_bytes == "AQID"

    def test_to_dict(self):
        e = HttpError(drop_connection=True, response_bytes="YWJj")
        assert e.to_dict() == {"dropConnection": True, "responseBytes": "YWJj"}

    def test_to_dict_with_delay(self):
        e = HttpError(drop_connection=True, delay=Delay(time_unit="SECONDS", value=1))
        result = e.to_dict()
        assert result["delay"] == {"timeUnit": "SECONDS", "value": 1}

    def test_to_dict_strips_none(self):
        e = HttpError(drop_connection=True)
        result = e.to_dict()
        assert "responseBytes" not in result

    def test_from_dict(self):
        e = HttpError.from_dict({"dropConnection": True, "responseBytes": "YQ=="})
        assert e.drop_connection is True
        assert e.response_bytes == "YQ=="

    def test_from_dict_none(self):
        assert HttpError.from_dict(None) is None

    def test_round_trip(self):
        original = HttpError(
            drop_connection=True,
            response_bytes="YWJj",
            delay=Delay(time_unit="MILLISECONDS", value=200),
        )
        restored = HttpError.from_dict(original.to_dict())
        assert restored.drop_connection is True
        assert restored.response_bytes == "YWJj"
        assert restored.delay.value == 200


class TestHttpOverrideForwardedRequest:
    def test_factory(self):
        o = HttpOverrideForwardedRequest.forward_overridden_request()
        assert o.http_request is None

    def test_factory_with_request(self):
        req = HttpRequest(path="/override")
        o = HttpOverrideForwardedRequest.forward_overridden_request(req)
        assert o.http_request is req

    def test_construction(self):
        o = HttpOverrideForwardedRequest(
            http_request=HttpRequest(path="/req"),
            http_response=HttpResponse(status_code=200),
            request_modifier={"headers": {"add": {"X-Test": ["v"]}}},
            response_modifier={"headers": {"remove": ["X-Remove"]}},
        )
        assert o.http_request.path == "/req"
        assert o.http_response.status_code == 200
        assert o.request_modifier is not None
        assert o.response_modifier is not None

    def test_to_dict(self):
        o = HttpOverrideForwardedRequest(
            http_request=HttpRequest(path="/p"),
            http_response=HttpResponse(status_code=200),
        )
        result = o.to_dict()
        assert result["httpRequest"] == {"path": "/p"}
        assert result["httpResponse"] == {"statusCode": 200}

    def test_to_dict_with_modifiers(self):
        o = HttpOverrideForwardedRequest(
            request_modifier={"headers": {"add": {}}},
            response_modifier={"headers": {"remove": []}},
        )
        result = o.to_dict()
        assert result["requestModifier"] == {"headers": {"add": {}}}
        assert result["responseModifier"] == {"headers": {"remove": []}}

    def test_to_dict_with_delay(self):
        o = HttpOverrideForwardedRequest(delay=Delay(time_unit="SECONDS", value=5))
        result = o.to_dict()
        assert result["delay"] == {"timeUnit": "SECONDS", "value": 5}

    def test_from_dict(self):
        o = HttpOverrideForwardedRequest.from_dict({
            "httpRequest": {"path": "/fwd"},
            "httpResponse": {"statusCode": 201},
            "delay": {"timeUnit": "SECONDS", "value": 1},
            "requestModifier": {"headers": {}},
            "responseModifier": {"headers": {}},
        })
        assert o.http_request.path == "/fwd"
        assert o.http_response.status_code == 201
        assert o.delay.value == 1
        assert o.request_modifier is not None
        assert o.response_modifier is not None

    def test_from_dict_none(self):
        assert HttpOverrideForwardedRequest.from_dict(None) is None

    def test_round_trip(self):
        original = HttpOverrideForwardedRequest(
            http_request=HttpRequest(method="POST", path="/api"),
            http_response=HttpResponse(status_code=202),
            delay=Delay(time_unit="MILLISECONDS", value=100),
            request_modifier={"path": {"regex": "/old/(.*)", "substitution": "/new/$1"}},
        )
        restored = HttpOverrideForwardedRequest.from_dict(original.to_dict())
        assert restored.http_request.method == "POST"
        assert restored.http_request.path == "/api"
        assert restored.http_response.status_code == 202
        assert restored.delay.value == 100
        assert restored.request_modifier["path"]["regex"] == "/old/(.*)"


class TestHttpRequestAndHttpResponse:
    def test_defaults(self):
        rr = HttpRequestAndHttpResponse()
        assert rr.http_request is None
        assert rr.http_response is None

    def test_construction_with_nested(self):
        rr = HttpRequestAndHttpResponse(
            http_request=HttpRequest(method="GET", path="/test"),
            http_response=HttpResponse(status_code=200, body="ok"),
        )
        assert rr.http_request.method == "GET"
        assert rr.http_response.status_code == 200

    def test_to_dict(self):
        rr = HttpRequestAndHttpResponse(
            http_request=HttpRequest(path="/p"),
            http_response=HttpResponse(status_code=200),
        )
        result = rr.to_dict()
        assert result == {
            "httpRequest": {"path": "/p"},
            "httpResponse": {"statusCode": 200},
        }

    def test_to_dict_strips_none(self):
        rr = HttpRequestAndHttpResponse(http_request=HttpRequest(path="/only"))
        result = rr.to_dict()
        assert "httpResponse" not in result

    def test_from_dict(self):
        rr = HttpRequestAndHttpResponse.from_dict({
            "httpRequest": {"method": "POST", "path": "/api"},
            "httpResponse": {"statusCode": 201},
        })
        assert rr.http_request.method == "POST"
        assert rr.http_response.status_code == 201

    def test_from_dict_none(self):
        assert HttpRequestAndHttpResponse.from_dict(None) is None

    def test_round_trip(self):
        original = HttpRequestAndHttpResponse(
            http_request=HttpRequest.request("/test").with_method("GET"),
            http_response=HttpResponse.response("response body"),
        )
        restored = HttpRequestAndHttpResponse.from_dict(original.to_dict())
        assert restored.http_request.path == "/test"
        assert restored.http_request.method == "GET"
        assert restored.http_response.body == "response body"
        assert restored.http_response.status_code == 200


class TestExpectationId:
    def test_defaults(self):
        eid = ExpectationId()
        assert eid.id == ""

    def test_construction(self):
        eid = ExpectationId(id="exp-123")
        assert eid.id == "exp-123"

    def test_to_dict(self):
        eid = ExpectationId(id="my-id")
        assert eid.to_dict() == {"id": "my-id"}

    def test_to_dict_empty(self):
        eid = ExpectationId()
        assert eid.to_dict() == {"id": ""}

    def test_from_dict(self):
        eid = ExpectationId.from_dict({"id": "restored-id"})
        assert eid.id == "restored-id"

    def test_from_dict_none(self):
        assert ExpectationId.from_dict(None) is None

    def test_from_dict_missing_id(self):
        eid = ExpectationId.from_dict({})
        assert eid.id == ""

    def test_round_trip(self):
        original = ExpectationId(id="abc-def")
        restored = ExpectationId.from_dict(original.to_dict())
        assert restored.id == "abc-def"


class TestExpectation:
    def test_defaults(self):
        e = Expectation()
        assert e.id is None
        assert e.priority is None
        assert e.http_request is None
        assert e.http_response is None

    def test_with_response_action(self):
        e = Expectation(
            id="exp-1",
            priority=5,
            http_request=HttpRequest(method="GET", path="/test"),
            http_response=HttpResponse(status_code=200, body="ok"),
            times=Times.exactly(3),
            time_to_live=TimeToLive.exactly(60, "SECONDS"),
        )
        assert e.id == "exp-1"
        assert e.priority == 5
        assert e.http_request.path == "/test"
        assert e.http_response.status_code == 200
        assert e.times.remaining_times == 3
        assert e.time_to_live.time_to_live == 60

    def test_with_forward_action(self):
        e = Expectation(
            http_request=HttpRequest(path="/fwd"),
            http_forward=HttpForward(host="target.com", port=8080),
        )
        assert e.http_forward.host == "target.com"

    def test_with_error_action(self):
        e = Expectation(
            http_request=HttpRequest(path="/err"),
            http_error=HttpError(drop_connection=True),
        )
        assert e.http_error.drop_connection is True

    def test_with_template_actions(self):
        e = Expectation(
            http_response_template=HttpTemplate(template_type="JAVASCRIPT", template="return {};"),
            http_forward_template=HttpTemplate(template_type="VELOCITY", template="$req"),
        )
        assert e.http_response_template.template_type == "JAVASCRIPT"
        assert e.http_forward_template.template_type == "VELOCITY"

    def test_with_callback_actions(self):
        e = Expectation(
            http_response_class_callback=HttpClassCallback(callback_class="com.Resp"),
            http_forward_class_callback=HttpClassCallback(callback_class="com.Fwd"),
            http_response_object_callback=HttpObjectCallback(client_id="r1"),
            http_forward_object_callback=HttpObjectCallback(client_id="f1"),
        )
        assert e.http_response_class_callback.callback_class == "com.Resp"
        assert e.http_forward_class_callback.callback_class == "com.Fwd"
        assert e.http_response_object_callback.client_id == "r1"
        assert e.http_forward_object_callback.client_id == "f1"

    def test_with_override(self):
        e = Expectation(
            http_override_forwarded_request=HttpOverrideForwardedRequest(
                http_request=HttpRequest(path="/new"),
            ),
        )
        assert e.http_override_forwarded_request.http_request.path == "/new"

    def test_to_dict_minimal(self):
        e = Expectation(
            http_request=HttpRequest(path="/test"),
            http_response=HttpResponse(status_code=200),
        )
        result = e.to_dict()
        assert result == {
            "httpRequest": {"path": "/test"},
            "httpResponse": {"statusCode": 200},
        }

    def test_to_dict_full(self):
        e = Expectation(
            id="full-id",
            priority=10,
            http_request=HttpRequest(method="POST", path="/api"),
            http_response=HttpResponse(status_code=201),
            times=Times.exactly(5),
            time_to_live=TimeToLive.exactly(300, "SECONDS"),
        )
        result = e.to_dict()
        assert result["id"] == "full-id"
        assert result["priority"] == 10
        assert result["httpRequest"] == {"method": "POST", "path": "/api"}
        assert result["httpResponse"] == {"statusCode": 201}
        assert result["times"] == {"remainingTimes": 5, "unlimited": False}
        assert result["timeToLive"] == {"timeUnit": "SECONDS", "timeToLive": 300, "unlimited": False}

    def test_to_dict_strips_none(self):
        e = Expectation(http_request=HttpRequest(path="/test"))
        result = e.to_dict()
        assert "httpResponse" not in result
        assert "httpForward" not in result
        assert "httpError" not in result
        assert "times" not in result
        assert "id" not in result

    def test_from_dict(self):
        e = Expectation.from_dict({
            "id": "parsed-id",
            "priority": 3,
            "httpRequest": {"method": "GET", "path": "/get"},
            "httpResponse": {"statusCode": 200, "body": "ok"},
        })
        assert e.id == "parsed-id"
        assert e.priority == 3
        assert e.http_request.method == "GET"
        assert e.http_request.path == "/get"
        assert e.http_response.status_code == 200
        assert e.http_response.body == "ok"

    def test_from_dict_with_forward(self):
        e = Expectation.from_dict({
            "httpForward": {"host": "example.com", "port": 8080},
        })
        assert e.http_forward.host == "example.com"
        assert e.http_forward.port == 8080

    def test_from_dict_with_error(self):
        e = Expectation.from_dict({
            "httpError": {"dropConnection": True},
        })
        assert e.http_error.drop_connection is True

    def test_from_dict_with_times(self):
        e = Expectation.from_dict({
            "times": {"remainingTimes": 2, "unlimited": False},
            "timeToLive": {"timeUnit": "SECONDS", "timeToLive": 60, "unlimited": False},
        })
        assert e.times.remaining_times == 2
        assert e.time_to_live.time_to_live == 60

    def test_from_dict_with_templates(self):
        e = Expectation.from_dict({
            "httpResponseTemplate": {"templateType": "JAVASCRIPT", "template": "return {};"},
            "httpForwardTemplate": {"templateType": "VELOCITY", "template": "$req"},
        })
        assert e.http_response_template.template_type == "JAVASCRIPT"
        assert e.http_forward_template.template_type == "VELOCITY"

    def test_from_dict_with_callbacks(self):
        e = Expectation.from_dict({
            "httpResponseClassCallback": {"callbackClass": "com.Resp"},
            "httpForwardClassCallback": {"callbackClass": "com.Fwd"},
            "httpResponseObjectCallback": {"clientId": "r1"},
            "httpForwardObjectCallback": {"clientId": "f1"},
        })
        assert e.http_response_class_callback.callback_class == "com.Resp"
        assert e.http_forward_class_callback.callback_class == "com.Fwd"
        assert e.http_response_object_callback.client_id == "r1"
        assert e.http_forward_object_callback.client_id == "f1"

    def test_from_dict_with_override(self):
        e = Expectation.from_dict({
            "httpOverrideForwardedRequest": {
                "httpRequest": {"path": "/new"},
                "httpResponse": {"statusCode": 200},
            },
        })
        assert e.http_override_forwarded_request.http_request.path == "/new"
        assert e.http_override_forwarded_request.http_response.status_code == 200

    def test_from_dict_none(self):
        assert Expectation.from_dict(None) is None

    def test_from_dict_missing_optional_fields(self):
        e = Expectation.from_dict({})
        assert e.id is None
        assert e.http_request is None
        assert e.http_response is None
        assert e.times is None

    def test_round_trip_response(self):
        original = Expectation(
            id="rt-1",
            priority=7,
            http_request=HttpRequest.request("/api").with_method("POST").with_header("CT", "json"),
            http_response=HttpResponse.response("created", 201),
            times=Times.exactly(10),
            time_to_live=TimeToLive.exactly(300, "SECONDS"),
        )
        restored = Expectation.from_dict(original.to_dict())
        assert restored.id == "rt-1"
        assert restored.priority == 7
        assert restored.http_request.path == "/api"
        assert restored.http_request.method == "POST"
        assert restored.http_request.headers[0].name == "CT"
        assert restored.http_response.status_code == 201
        assert restored.http_response.body == "created"
        assert restored.times.remaining_times == 10
        assert restored.time_to_live.time_to_live == 300

    def test_round_trip_forward(self):
        original = Expectation(
            http_request=HttpRequest(path="/fwd"),
            http_forward=HttpForward(host="target.com", port=9090, scheme="HTTPS"),
        )
        restored = Expectation.from_dict(original.to_dict())
        assert restored.http_forward.host == "target.com"
        assert restored.http_forward.port == 9090
        assert restored.http_forward.scheme == "HTTPS"

    def test_round_trip_error(self):
        original = Expectation(
            http_request=HttpRequest(path="/err"),
            http_error=HttpError(drop_connection=True, response_bytes="YWJj"),
        )
        restored = Expectation.from_dict(original.to_dict())
        assert restored.http_error.drop_connection is True
        assert restored.http_error.response_bytes == "YWJj"


class TestOpenAPIDefinition:
    def test_defaults(self):
        d = OpenAPIDefinition()
        assert d.spec_url_or_payload is None
        assert d.operation_id is None

    def test_construction(self):
        d = OpenAPIDefinition(
            spec_url_or_payload="https://example.com/spec.json",
            operation_id="getUser",
        )
        assert d.spec_url_or_payload == "https://example.com/spec.json"
        assert d.operation_id == "getUser"

    def test_to_dict(self):
        d = OpenAPIDefinition(spec_url_or_payload="spec.yaml", operation_id="op1")
        assert d.to_dict() == {"specUrlOrPayload": "spec.yaml", "operationId": "op1"}

    def test_to_dict_strips_none(self):
        d = OpenAPIDefinition(spec_url_or_payload="spec.yaml")
        result = d.to_dict()
        assert "operationId" not in result

    def test_from_dict(self):
        d = OpenAPIDefinition.from_dict({
            "specUrlOrPayload": "https://api.io/spec",
            "operationId": "listItems",
        })
        assert d.spec_url_or_payload == "https://api.io/spec"
        assert d.operation_id == "listItems"

    def test_from_dict_none(self):
        assert OpenAPIDefinition.from_dict(None) is None

    def test_round_trip(self):
        original = OpenAPIDefinition(
            spec_url_or_payload="https://example.com/openapi.json",
            operation_id="createUser",
        )
        restored = OpenAPIDefinition.from_dict(original.to_dict())
        assert restored.spec_url_or_payload == original.spec_url_or_payload
        assert restored.operation_id == original.operation_id


class TestOpenAPIExpectation:
    def test_defaults(self):
        e = OpenAPIExpectation()
        assert e.spec_url_or_payload is None
        assert e.operations_and_responses is None

    def test_construction(self):
        e = OpenAPIExpectation(
            spec_url_or_payload="https://example.com/spec",
            operations_and_responses={"getUser": "200", "createUser": "201"},
        )
        assert e.operations_and_responses["getUser"] == "200"

    def test_to_dict(self):
        e = OpenAPIExpectation(
            spec_url_or_payload="spec.yaml",
            operations_and_responses={"op": "200"},
        )
        assert e.to_dict() == {
            "specUrlOrPayload": "spec.yaml",
            "operationsAndResponses": {"op": "200"},
        }

    def test_to_dict_strips_none(self):
        e = OpenAPIExpectation(spec_url_or_payload="spec.yaml")
        result = e.to_dict()
        assert "operationsAndResponses" not in result

    def test_from_dict(self):
        e = OpenAPIExpectation.from_dict({
            "specUrlOrPayload": "https://api.io/spec",
            "operationsAndResponses": {"list": "200"},
        })
        assert e.spec_url_or_payload == "https://api.io/spec"
        assert e.operations_and_responses == {"list": "200"}

    def test_from_dict_none(self):
        assert OpenAPIExpectation.from_dict(None) is None

    def test_round_trip(self):
        original = OpenAPIExpectation(
            spec_url_or_payload="https://example.com/spec.json",
            operations_and_responses={"getItems": "200", "deleteItem": "204"},
        )
        restored = OpenAPIExpectation.from_dict(original.to_dict())
        assert restored.spec_url_or_payload == original.spec_url_or_payload
        assert restored.operations_and_responses == original.operations_and_responses


class TestVerificationTimes:
    def test_defaults(self):
        vt = VerificationTimes()
        assert vt.at_least is None
        assert vt.at_most is None

    def test_at_least_factory(self):
        vt = VerificationTimes.at_least(3)
        assert vt.at_least == 3
        assert vt.at_most is None

    def test_at_most_factory(self):
        vt = VerificationTimes.at_most(5)
        assert vt.at_most == 5
        assert vt.at_least is None

    def test_exactly_factory(self):
        vt = VerificationTimes.exactly(2)
        assert vt.at_least == 2
        assert vt.at_most == 2

    def test_once_factory(self):
        vt = VerificationTimes.once()
        assert vt.at_least == 1
        assert vt.at_most == 1

    def test_between_factory(self):
        vt = VerificationTimes.between(2, 5)
        assert vt.at_least == 2
        assert vt.at_most == 5

    def test_to_dict(self):
        vt = VerificationTimes(at_least=1, at_most=3)
        assert vt.to_dict() == {"atLeast": 1, "atMost": 3}

    def test_to_dict_strips_none(self):
        vt = VerificationTimes(at_least=1)
        result = vt.to_dict()
        assert result == {"atLeast": 1}
        assert "atMost" not in result

    def test_from_dict(self):
        vt = VerificationTimes.from_dict({"atLeast": 2, "atMost": 10})
        assert vt.at_least == 2
        assert vt.at_most == 10

    def test_from_dict_none(self):
        assert VerificationTimes.from_dict(None) is None

    def test_round_trip_at_least(self):
        original = VerificationTimes.at_least(5)
        restored = VerificationTimes.from_dict(original.to_dict())
        assert restored.at_least == 5
        assert restored.at_most is None

    def test_round_trip_between(self):
        original = VerificationTimes.between(3, 7)
        restored = VerificationTimes.from_dict(original.to_dict())
        assert restored.at_least == 3
        assert restored.at_most == 7

    def test_round_trip_once(self):
        original = VerificationTimes.once()
        restored = VerificationTimes.from_dict(original.to_dict())
        assert restored.at_least == 1
        assert restored.at_most == 1


class TestVerification:
    def test_defaults(self):
        v = Verification()
        assert v.http_request is None
        assert v.expectation_id is None
        assert v.times is None

    def test_construction(self):
        v = Verification(
            http_request=HttpRequest(path="/verify"),
            times=VerificationTimes.exactly(2),
            maximum_number_of_request_to_return_in_verification_failure=10,
        )
        assert v.http_request.path == "/verify"
        assert v.times.at_least == 2
        assert v.maximum_number_of_request_to_return_in_verification_failure == 10

    def test_with_expectation_id(self):
        v = Verification(expectation_id=ExpectationId(id="exp-1"))
        assert v.expectation_id.id == "exp-1"

    def test_to_dict(self):
        v = Verification(
            http_request=HttpRequest(path="/v"),
            times=VerificationTimes.once(),
        )
        result = v.to_dict()
        assert result == {
            "httpRequest": {"path": "/v"},
            "times": {"atLeast": 1, "atMost": 1},
        }

    def test_to_dict_with_max_requests(self):
        v = Verification(
            http_request=HttpRequest(path="/test"),
            maximum_number_of_request_to_return_in_verification_failure=5,
        )
        result = v.to_dict()
        assert result["maximumNumberOfRequestToReturnInVerificationFailure"] == 5

    def test_to_dict_with_expectation_id(self):
        v = Verification(expectation_id=ExpectationId(id="eid"))
        result = v.to_dict()
        assert result["expectationId"] == {"id": "eid"}

    def test_to_dict_strips_none(self):
        v = Verification(http_request=HttpRequest(path="/p"))
        result = v.to_dict()
        assert "expectationId" not in result
        assert "times" not in result

    def test_from_dict(self):
        v = Verification.from_dict({
            "httpRequest": {"path": "/test"},
            "times": {"atLeast": 1, "atMost": 1},
            "maximumNumberOfRequestToReturnInVerificationFailure": 15,
        })
        assert v.http_request.path == "/test"
        assert v.times.at_least == 1
        assert v.maximum_number_of_request_to_return_in_verification_failure == 15

    def test_from_dict_with_expectation_id(self):
        v = Verification.from_dict({
            "expectationId": {"id": "eid-123"},
        })
        assert v.expectation_id.id == "eid-123"

    def test_from_dict_none(self):
        assert Verification.from_dict(None) is None

    def test_round_trip(self):
        original = Verification(
            http_request=HttpRequest.request("/api").with_method("GET"),
            times=VerificationTimes.between(2, 5),
            maximum_number_of_request_to_return_in_verification_failure=20,
        )
        restored = Verification.from_dict(original.to_dict())
        assert restored.http_request.path == "/api"
        assert restored.http_request.method == "GET"
        assert restored.times.at_least == 2
        assert restored.times.at_most == 5
        assert restored.maximum_number_of_request_to_return_in_verification_failure == 20


class TestVerificationSequence:
    def test_defaults(self):
        vs = VerificationSequence()
        assert vs.http_requests is None
        assert vs.expectation_ids is None

    def test_with_requests(self):
        vs = VerificationSequence(
            http_requests=[
                HttpRequest(path="/a"),
                HttpRequest(path="/b"),
            ],
        )
        assert len(vs.http_requests) == 2

    def test_with_expectation_ids(self):
        vs = VerificationSequence(
            expectation_ids=[
                ExpectationId(id="e1"),
                ExpectationId(id="e2"),
            ],
        )
        assert len(vs.expectation_ids) == 2

    def test_to_dict(self):
        vs = VerificationSequence(
            http_requests=[HttpRequest(path="/a"), HttpRequest(path="/b")],
        )
        result = vs.to_dict()
        assert result == {
            "httpRequests": [{"path": "/a"}, {"path": "/b"}],
        }

    def test_to_dict_with_expectation_ids(self):
        vs = VerificationSequence(
            expectation_ids=[ExpectationId(id="e1"), ExpectationId(id="e2")],
        )
        result = vs.to_dict()
        assert result == {
            "expectationIds": [{"id": "e1"}, {"id": "e2"}],
        }

    def test_to_dict_strips_none(self):
        vs = VerificationSequence(http_requests=[HttpRequest(path="/a")])
        result = vs.to_dict()
        assert "expectationIds" not in result

    def test_from_dict(self):
        vs = VerificationSequence.from_dict({
            "httpRequests": [
                {"method": "GET", "path": "/first"},
                {"method": "POST", "path": "/second"},
            ],
        })
        assert len(vs.http_requests) == 2
        assert vs.http_requests[0].path == "/first"
        assert vs.http_requests[1].method == "POST"

    def test_from_dict_with_expectation_ids(self):
        vs = VerificationSequence.from_dict({
            "expectationIds": [{"id": "e1"}, {"id": "e2"}],
        })
        assert len(vs.expectation_ids) == 2
        assert vs.expectation_ids[0].id == "e1"

    def test_from_dict_none(self):
        assert VerificationSequence.from_dict(None) is None

    def test_from_dict_empty(self):
        vs = VerificationSequence.from_dict({})
        assert vs.http_requests is None
        assert vs.expectation_ids is None

    def test_round_trip(self):
        original = VerificationSequence(
            http_requests=[
                HttpRequest.request("/a").with_method("GET"),
                HttpRequest.request("/b").with_method("POST"),
            ],
            expectation_ids=[
                ExpectationId(id="ea"),
                ExpectationId(id="eb"),
            ],
        )
        restored = VerificationSequence.from_dict(original.to_dict())
        assert len(restored.http_requests) == 2
        assert restored.http_requests[0].path == "/a"
        assert restored.http_requests[1].method == "POST"
        assert len(restored.expectation_ids) == 2
        assert restored.expectation_ids[0].id == "ea"


class TestPorts:
    def test_defaults(self):
        p = Ports()
        assert p.ports == []

    def test_construction(self):
        p = Ports(ports=[8080, 8443])
        assert p.ports == [8080, 8443]

    def test_to_dict(self):
        p = Ports(ports=[1080])
        assert p.to_dict() == {"ports": [1080]}

    def test_to_dict_empty(self):
        p = Ports()
        assert p.to_dict() == {"ports": []}

    def test_from_dict(self):
        p = Ports.from_dict({"ports": [8080, 9090]})
        assert p.ports == [8080, 9090]

    def test_from_dict_none(self):
        assert Ports.from_dict(None) is None

    def test_from_dict_missing_ports(self):
        p = Ports.from_dict({})
        assert p.ports == []

    def test_round_trip(self):
        original = Ports(ports=[1080, 1443, 8080])
        restored = Ports.from_dict(original.to_dict())
        assert restored.ports == [1080, 1443, 8080]


class TestRequestDefinitionAlias:
    def test_is_http_request(self):
        assert RequestDefinition is HttpRequest

    def test_creates_http_request(self):
        r = RequestDefinition(path="/test")
        assert isinstance(r, HttpRequest)
        assert r.path == "/test"

    def test_has_request_factory(self):
        r = RequestDefinition.request("/alias")
        assert isinstance(r, HttpRequest)
        assert r.path == "/alias"
