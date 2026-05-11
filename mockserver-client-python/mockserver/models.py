from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


_FIELD_MAP = {
    "status_code": "statusCode",
    "reason_phrase": "reasonPhrase",
    "keep_alive": "keepAlive",
    "query_string_parameters": "queryStringParameters",
    "path_parameters": "pathParameters",
    "socket_address": "socketAddress",
    "time_unit": "timeUnit",
    "time_to_live": "timeToLive",
    "remaining_times": "remainingTimes",
    "close_socket": "closeSocket",
    "close_socket_delay": "closeSocketDelay",
    "suppress_content_length_header": "suppressContentLengthHeader",
    "content_length_header_override": "contentLengthHeaderOverride",
    "suppress_connection_header": "suppressConnectionHeader",
    "keep_alive_override": "keepAliveOverride",
    "connection_options": "connectionOptions",
    "callback_class": "callbackClass",
    "client_id": "clientId",
    "response_callback": "responseCallback",
    "drop_connection": "dropConnection",
    "response_bytes": "responseBytes",
    "http_request": "httpRequest",
    "http_response": "httpResponse",
    "http_response_template": "httpResponseTemplate",
    "http_response_class_callback": "httpResponseClassCallback",
    "http_response_object_callback": "httpResponseObjectCallback",
    "http_forward": "httpForward",
    "http_forward_template": "httpForwardTemplate",
    "http_forward_class_callback": "httpForwardClassCallback",
    "http_forward_object_callback": "httpForwardObjectCallback",
    "http_override_forwarded_request": "httpOverrideForwardedRequest",
    "http_error": "httpError",
    "template_type": "templateType",
    "base64_bytes": "base64Bytes",
    "not_body": "not",
    "content_type": "contentType",
    "at_least": "atLeast",
    "at_most": "atMost",
    "expectation_id": "expectationId",
    "expectation_ids": "expectationIds",
    "http_requests": "httpRequests",
    "spec_url_or_payload": "specUrlOrPayload",
    "operations_and_responses": "operationsAndResponses",
    "operation_id": "operationId",
    "request_modifier": "requestModifier",
    "response_modifier": "responseModifier",
    "maximum_number_of_request_to_return_in_verification_failure": "maximumNumberOfRequestToReturnInVerificationFailure",
    "http_sse_response": "httpSseResponse",
    "http_websocket_response": "httpWebSocketResponse",
    "after_actions": "afterActions",
}


def _to_camel(snake_str: str) -> str:
    if snake_str in _FIELD_MAP:
        return _FIELD_MAP[snake_str]
    parts = snake_str.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def _strip_none(d: dict) -> dict:
    return {k: v for k, v in d.items() if v is not None}


def _serialize_value(value: Any) -> Any:
    if hasattr(value, "to_dict"):
        return value.to_dict()
    if isinstance(value, list):
        return [_serialize_value(item) for item in value]
    return value


_REVERSE_FIELD_MAP = {v: k for k, v in _FIELD_MAP.items()}


def _from_camel(camel_str: str) -> str:
    if camel_str in _REVERSE_FIELD_MAP:
        return _REVERSE_FIELD_MAP[camel_str]
    result = []
    for ch in camel_str:
        if ch.isupper():
            result.append("_")
            result.append(ch.lower())
        else:
            result.append(ch)
    return "".join(result)


def _serialize_body(body: Body | str | dict | None) -> Any:
    if body is None:
        return None
    if isinstance(body, str):
        return body
    if isinstance(body, dict):
        return body
    if hasattr(body, "to_dict"):
        return body.to_dict()
    return body


_BODY_TYPES = {"STRING", "JSON", "REGEX", "XML", "BINARY", "JSON_SCHEMA", "JSON_PATH", "XPATH", "XML_SCHEMA", "JSON_RPC", "GRAPHQL"}


def _deserialize_body(data: Any) -> Body | JsonRpcBody | str | dict | None:
    if data is None:
        return None
    if isinstance(data, str):
        return data
    if isinstance(data, dict):
        if data.get("type") == "JSON_RPC":
            return JsonRpcBody(
                method=data.get("method", ""),
                params_schema=data.get("paramsSchema"),
                not_body=data.get("not", False),
                optional=data.get("optional", False),
            )
        if data.get("type") == "GRAPHQL":
            return GraphQLBody(
                query=data.get("query", ""),
                operation_name=data.get("operationName"),
                variables_schema=data.get("variablesSchema"),
                not_body=data.get("not", False),
                optional=data.get("optional", False),
            )
        if data.get("type") in _BODY_TYPES:
            return Body.from_dict(data)
        return data
    return data


def _serialize_key_multi_values(items: list[KeyToMultiValue] | None) -> list[dict] | None:
    if items is None:
        return None
    return [item.to_dict() for item in items]


def _deserialize_key_multi_values(data: list | dict | None) -> list[KeyToMultiValue] | None:
    if data is None:
        return None
    if isinstance(data, dict):
        return [
            KeyToMultiValue(name=k, values=v if isinstance(v, list) else [v])
            for k, v in data.items()
        ]
    result = []
    for item in data:
        if isinstance(item, dict):
            result.append(KeyToMultiValue.from_dict(item))
        elif isinstance(item, str):
            result.append(KeyToMultiValue(name=item, values=[]))
        else:
            result.append(KeyToMultiValue.from_dict(item))
    return result


@dataclass
class DelayDistribution:
    type: str | None = None
    min: int | None = None
    max: int | None = None
    median: int | None = None
    p99: int | None = None
    mean: int | None = None
    std_dev: int | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "type": self.type,
            "min": self.min,
            "max": self.max,
            "median": self.median,
            "p99": self.p99,
            "mean": self.mean,
            "stdDev": self.std_dev,
        })

    @classmethod
    def from_dict(cls, data: dict) -> DelayDistribution:
        if data is None:
            return None
        return cls(
            type=data.get("type"),
            min=data.get("min"),
            max=data.get("max"),
            median=data.get("median"),
            p99=data.get("p99"),
            mean=data.get("mean"),
            std_dev=data.get("stdDev"),
        )


@dataclass
class Delay:
    time_unit: str = "MILLISECONDS"
    value: int = 0
    distribution: DelayDistribution | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "timeUnit": self.time_unit,
            "value": self.value,
            "distribution": self.distribution.to_dict() if self.distribution else None,
        })

    @classmethod
    def from_dict(cls, data: dict) -> Delay:
        if data is None:
            return None
        dist_data = data.get("distribution")
        return cls(
            time_unit=data.get("timeUnit", "MILLISECONDS"),
            value=data.get("value", 0),
            distribution=DelayDistribution.from_dict(dist_data) if dist_data else None,
        )


@dataclass
class Times:
    remaining_times: int | None = None
    unlimited: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "remainingTimes": self.remaining_times,
            "unlimited": self.unlimited,
        })

    @classmethod
    def from_dict(cls, data: dict) -> Times:
        if data is None:
            return None
        return cls(
            remaining_times=data.get("remainingTimes"),
            unlimited=data.get("unlimited"),
        )


def _times_unlimited() -> Times:
    return Times(unlimited=True)


def _times_exactly(count: int) -> Times:
    return Times(remaining_times=count, unlimited=False)


Times.unlimited = staticmethod(_times_unlimited)
Times.exactly = staticmethod(_times_exactly)


@dataclass
class TimeToLive:
    time_unit: str | None = None
    time_to_live: int | None = None
    unlimited: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "timeUnit": self.time_unit,
            "timeToLive": self.time_to_live,
            "unlimited": self.unlimited,
        })

    @classmethod
    def from_dict(cls, data: dict) -> TimeToLive:
        if data is None:
            return None
        return cls(
            time_unit=data.get("timeUnit"),
            time_to_live=data.get("timeToLive"),
            unlimited=data.get("unlimited"),
        )


def _ttl_unlimited() -> TimeToLive:
    return TimeToLive(unlimited=True)


def _ttl_exactly(time_to_live: int, time_unit: str) -> TimeToLive:
    return TimeToLive(
        time_unit=time_unit,
        time_to_live=time_to_live,
        unlimited=False,
    )


TimeToLive.unlimited = staticmethod(_ttl_unlimited)
TimeToLive.exactly = staticmethod(_ttl_exactly)


@dataclass
class KeyToMultiValue:
    name: str = ""
    values: list[str] = field(default_factory=list)

    # name and values are always emitted (not stripped via _strip_none) because
    # the MockServer protocol requires both fields on every header/cookie/parameter.
    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "values": self.values,
        }

    @classmethod
    def from_dict(cls, data: dict) -> KeyToMultiValue:
        if data is None:
            return None
        return cls(
            name=data.get("name", ""),
            values=data.get("values", []),
        )


@dataclass
class Body:
    type: str | None = None
    string: str | None = None
    json: Any | None = None
    base64_bytes: str | None = None
    not_body: bool | None = None
    content_type: str | None = None
    charset: str | None = None

    def to_dict(self) -> dict:
        result = {}
        if self.type is not None:
            result["type"] = self.type
        if self.string is not None:
            result["string"] = self.string
        if self.json is not None:
            result["json"] = self.json
        if self.base64_bytes is not None:
            result["base64Bytes"] = self.base64_bytes
        if self.not_body is not None:
            result["not"] = self.not_body
        if self.content_type is not None:
            result["contentType"] = self.content_type
        if self.charset is not None:
            result["charset"] = self.charset
        return result

    @classmethod
    def from_dict(cls, data: dict) -> Body:
        if data is None:
            return None
        return cls(
            type=data.get("type"),
            string=data.get("string"),
            json=data.get("json"),
            base64_bytes=data.get("base64Bytes"),
            not_body=data.get("not"),
            content_type=data.get("contentType"),
            charset=data.get("charset"),
        )


@dataclass
class JsonRpcBody:
    method: str = ""
    params_schema: str | None = None
    not_body: bool = False
    optional: bool = False

    def to_dict(self) -> dict:
        result: dict = {"type": "JSON_RPC", "method": self.method}
        if self.params_schema is not None:
            result["paramsSchema"] = self.params_schema
        if self.not_body:
            result["not"] = True
        if self.optional:
            result["optional"] = True
        return result


@dataclass
class GraphQLBody:
    query: str = ""
    operation_name: str | None = None
    variables_schema: str | None = None
    not_body: bool = False
    optional: bool = False

    def to_dict(self) -> dict:
        result: dict = {"type": "GRAPHQL", "query": self.query}
        if self.operation_name is not None:
            result["operationName"] = self.operation_name
        if self.variables_schema is not None:
            result["variablesSchema"] = self.variables_schema
        if self.not_body:
            result["not"] = True
        if self.optional:
            result["optional"] = True
        return result


def _body_string(value: str) -> Body:
    return Body(type="STRING", string=value)


def _body_json(value: Any) -> Body:
    return Body(type="JSON", json=value)


def _body_regex(value: str) -> Body:
    return Body(type="REGEX", string=value)


def _body_exact(value: str) -> Body:
    return Body(type="STRING", string=value)


def _body_xml(value: str) -> Body:
    return Body(type="XML", string=value)


def _body_json_rpc(method: str, params_schema: str | None = None) -> JsonRpcBody:
    return JsonRpcBody(method=method, params_schema=params_schema)


def _body_graphql(query: str, operation_name: str | None = None, variables_schema: str | None = None) -> GraphQLBody:
    return GraphQLBody(query=query, operation_name=operation_name, variables_schema=variables_schema)


Body.string = staticmethod(_body_string)
Body.json = staticmethod(_body_json)
Body.regex = staticmethod(_body_regex)
Body.exact = staticmethod(_body_exact)
Body.xml = staticmethod(_body_xml)
Body.json_rpc = staticmethod(_body_json_rpc)
Body.graphql = staticmethod(_body_graphql)


@dataclass
class SocketAddress:
    host: str | None = None
    port: int | None = None
    scheme: str | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "host": self.host,
            "port": self.port,
            "scheme": self.scheme,
        })

    @classmethod
    def from_dict(cls, data: dict) -> SocketAddress:
        if data is None:
            return None
        return cls(
            host=data.get("host"),
            port=data.get("port"),
            scheme=data.get("scheme"),
        )


@dataclass
class HttpRequest:
    method: str | None = None
    path: str | None = None
    query_string_parameters: list[KeyToMultiValue] | None = None
    headers: list[KeyToMultiValue] | None = None
    cookies: list[KeyToMultiValue] | None = None
    body: Body | str | dict | None = None
    secure: bool | None = None
    keep_alive: bool | None = None
    path_parameters: list[KeyToMultiValue] | None = None
    socket_address: SocketAddress | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "method": self.method,
            "path": self.path,
            "queryStringParameters": _serialize_key_multi_values(self.query_string_parameters),
            "headers": _serialize_key_multi_values(self.headers),
            "cookies": _serialize_key_multi_values(self.cookies),
            "body": _serialize_body(self.body),
            "secure": self.secure,
            "keepAlive": self.keep_alive,
            "pathParameters": _serialize_key_multi_values(self.path_parameters),
            "socketAddress": self.socket_address.to_dict() if self.socket_address else None,
        })

    @classmethod
    def from_dict(cls, data: dict) -> HttpRequest:
        if data is None:
            return None
        return cls(
            method=data.get("method"),
            path=data.get("path"),
            query_string_parameters=_deserialize_key_multi_values(data.get("queryStringParameters")),
            headers=_deserialize_key_multi_values(data.get("headers")),
            cookies=_deserialize_key_multi_values(data.get("cookies")),
            body=_deserialize_body(data.get("body")),
            secure=data.get("secure"),
            keep_alive=data.get("keepAlive"),
            path_parameters=_deserialize_key_multi_values(data.get("pathParameters")),
            socket_address=SocketAddress.from_dict(data.get("socketAddress")),
        )

    @staticmethod
    def request(path: str | None = None) -> HttpRequest:
        return HttpRequest(path=path)

    def with_method(self, method: str) -> HttpRequest:
        self.method = method
        return self

    def with_path(self, path: str) -> HttpRequest:
        self.path = path
        return self

    def with_header(self, name: str, *values: str) -> HttpRequest:
        if self.headers is None:
            self.headers = []
        self.headers.append(KeyToMultiValue(name=name, values=list(values)))
        return self

    def with_query_param(self, name: str, *values: str) -> HttpRequest:
        if self.query_string_parameters is None:
            self.query_string_parameters = []
        self.query_string_parameters.append(KeyToMultiValue(name=name, values=list(values)))
        return self

    def with_cookie(self, name: str, value: str) -> HttpRequest:
        if self.cookies is None:
            self.cookies = []
        self.cookies.append(KeyToMultiValue(name=name, values=[value]))
        return self

    def with_body(self, body: Body | str | dict) -> HttpRequest:
        self.body = body
        return self

    def with_secure(self, secure: bool) -> HttpRequest:
        self.secure = secure
        return self

    def with_keep_alive(self, keep_alive: bool) -> HttpRequest:
        self.keep_alive = keep_alive
        return self


@dataclass
class ConnectionOptions:
    close_socket: bool | None = None
    close_socket_delay: Delay | None = None
    suppress_content_length_header: bool | None = None
    content_length_header_override: int | None = None
    suppress_connection_header: bool | None = None
    chunk_size: int | None = None
    chunk_delay: Delay | None = None
    keep_alive_override: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "closeSocket": self.close_socket,
            "closeSocketDelay": self.close_socket_delay.to_dict() if self.close_socket_delay else None,
            "suppressContentLengthHeader": self.suppress_content_length_header,
            "contentLengthHeaderOverride": self.content_length_header_override,
            "suppressConnectionHeader": self.suppress_connection_header,
            "chunkSize": self.chunk_size,
            "chunkDelay": self.chunk_delay.to_dict() if self.chunk_delay else None,
            "keepAliveOverride": self.keep_alive_override,
        })

    @classmethod
    def from_dict(cls, data: dict) -> ConnectionOptions:
        if data is None:
            return None
        return cls(
            close_socket=data.get("closeSocket"),
            close_socket_delay=Delay.from_dict(data.get("closeSocketDelay")),
            suppress_content_length_header=data.get("suppressContentLengthHeader"),
            content_length_header_override=data.get("contentLengthHeaderOverride"),
            suppress_connection_header=data.get("suppressConnectionHeader"),
            chunk_size=data.get("chunkSize"),
            chunk_delay=Delay.from_dict(data.get("chunkDelay")),
            keep_alive_override=data.get("keepAliveOverride"),
        )


@dataclass
class HttpResponse:
    status_code: int | None = None
    reason_phrase: str | None = None
    headers: list[KeyToMultiValue] | None = None
    cookies: list[KeyToMultiValue] | None = None
    body: Body | str | dict | None = None
    delay: Delay | None = None
    connection_options: ConnectionOptions | None = None
    primary: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "statusCode": self.status_code,
            "reasonPhrase": self.reason_phrase,
            "headers": _serialize_key_multi_values(self.headers),
            "cookies": _serialize_key_multi_values(self.cookies),
            "body": _serialize_body(self.body),
            "delay": self.delay.to_dict() if self.delay else None,
            "connectionOptions": self.connection_options.to_dict() if self.connection_options else None,
            "primary": self.primary,
        })

    @classmethod
    def from_dict(cls, data: dict) -> HttpResponse:
        if data is None:
            return None
        return cls(
            status_code=data.get("statusCode"),
            reason_phrase=data.get("reasonPhrase"),
            headers=_deserialize_key_multi_values(data.get("headers")),
            cookies=_deserialize_key_multi_values(data.get("cookies")),
            body=_deserialize_body(data.get("body")),
            delay=Delay.from_dict(data.get("delay")),
            connection_options=ConnectionOptions.from_dict(data.get("connectionOptions")),
            primary=data.get("primary"),
        )

    @staticmethod
    def response(body: str | None = None, status_code: int | None = None) -> HttpResponse:
        resp = HttpResponse()
        if body is not None:
            resp.body = body
            if status_code is None:
                resp.status_code = 200
                resp.reason_phrase = "OK"
            else:
                resp.status_code = status_code
        elif status_code is not None:
            resp.status_code = status_code
        return resp

    @staticmethod
    def not_found_response() -> HttpResponse:
        return HttpResponse(status_code=404, reason_phrase="Not Found")

    def with_status_code(self, status_code: int) -> HttpResponse:
        self.status_code = status_code
        return self

    def with_header(self, name: str, *values: str) -> HttpResponse:
        if self.headers is None:
            self.headers = []
        self.headers.append(KeyToMultiValue(name=name, values=list(values)))
        return self

    def with_cookie(self, name: str, value: str) -> HttpResponse:
        if self.cookies is None:
            self.cookies = []
        self.cookies.append(KeyToMultiValue(name=name, values=[value]))
        return self

    def with_body(self, body: Body | str | dict) -> HttpResponse:
        self.body = body
        return self

    def with_delay(self, delay: Delay) -> HttpResponse:
        self.delay = delay
        return self

    def with_reason_phrase(self, reason_phrase: str) -> HttpResponse:
        self.reason_phrase = reason_phrase
        return self


@dataclass
class HttpForward:
    host: str | None = None
    port: int | None = None
    scheme: str | None = None
    delay: Delay | None = None
    primary: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "host": self.host,
            "port": self.port,
            "scheme": self.scheme,
            "delay": self.delay.to_dict() if self.delay else None,
            "primary": self.primary,
        })

    @classmethod
    def from_dict(cls, data: dict) -> HttpForward:
        if data is None:
            return None
        return cls(
            host=data.get("host"),
            port=data.get("port"),
            scheme=data.get("scheme"),
            delay=Delay.from_dict(data.get("delay")),
            primary=data.get("primary"),
        )

    @staticmethod
    def forward() -> HttpForward:
        return HttpForward()


@dataclass
class HttpTemplate:
    template_type: str = "JAVASCRIPT"
    template: str | None = None
    delay: Delay | None = None
    primary: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "templateType": self.template_type,
            "template": self.template,
            "delay": self.delay.to_dict() if self.delay else None,
            "primary": self.primary,
        })

    @classmethod
    def from_dict(cls, data: dict) -> HttpTemplate:
        if data is None:
            return None
        return cls(
            template_type=data.get("templateType", "JAVASCRIPT"),
            template=data.get("template"),
            delay=Delay.from_dict(data.get("delay")),
            primary=data.get("primary"),
        )


def _http_template_factory(template_type: str, template: str | None = None) -> HttpTemplate:
    return HttpTemplate(template_type=template_type, template=template)


HttpTemplate.template = staticmethod(_http_template_factory)


@dataclass
class HttpClassCallback:
    callback_class: str | None = None
    delay: Delay | None = None
    primary: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "callbackClass": self.callback_class,
            "delay": self.delay.to_dict() if self.delay else None,
            "primary": self.primary,
        })

    @classmethod
    def from_dict(cls, data: dict) -> HttpClassCallback:
        if data is None:
            return None
        return cls(
            callback_class=data.get("callbackClass"),
            delay=Delay.from_dict(data.get("delay")),
            primary=data.get("primary"),
        )

    @staticmethod
    def callback(callback_class: str | None = None) -> HttpClassCallback:
        return HttpClassCallback(callback_class=callback_class)


@dataclass
class HttpObjectCallback:
    client_id: str | None = None
    response_callback: bool | None = None
    delay: Delay | None = None
    primary: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "clientId": self.client_id,
            "responseCallback": self.response_callback,
            "delay": self.delay.to_dict() if self.delay else None,
            "primary": self.primary,
        })

    @classmethod
    def from_dict(cls, data: dict) -> HttpObjectCallback:
        if data is None:
            return None
        return cls(
            client_id=data.get("clientId"),
            response_callback=data.get("responseCallback"),
            delay=Delay.from_dict(data.get("delay")),
            primary=data.get("primary"),
        )


@dataclass
class HttpError:
    drop_connection: bool | None = None
    response_bytes: str | None = None
    delay: Delay | None = None
    primary: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "dropConnection": self.drop_connection,
            "responseBytes": self.response_bytes,
            "delay": self.delay.to_dict() if self.delay else None,
            "primary": self.primary,
        })

    @classmethod
    def from_dict(cls, data: dict) -> HttpError:
        if data is None:
            return None
        return cls(
            drop_connection=data.get("dropConnection"),
            response_bytes=data.get("responseBytes"),
            delay=Delay.from_dict(data.get("delay")),
            primary=data.get("primary"),
        )

    @staticmethod
    def error() -> HttpError:
        return HttpError()


@dataclass
class SseEvent:
    event: str | None = None
    data: str | None = None
    id: str | None = None
    retry: int | None = None
    delay: Delay | None = None

    def to_dict(self) -> dict:
        result: dict = {}
        if self.event is not None:
            result["event"] = self.event
        if self.data is not None:
            result["data"] = self.data
        if self.id is not None:
            result["id"] = self.id
        if self.retry is not None:
            result["retry"] = self.retry
        if self.delay is not None:
            result["delay"] = self.delay.to_dict()
        return result

    @classmethod
    def from_dict(cls, data: dict) -> SseEvent:
        if data is None:
            return None
        return cls(
            event=data.get("event"),
            data=data.get("data"),
            id=data.get("id"),
            retry=data.get("retry"),
            delay=Delay.from_dict(data.get("delay")),
        )


@dataclass
class HttpSseResponse:
    status_code: int | None = None
    headers: list[KeyToMultiValue] | None = None
    events: list[SseEvent] | None = None
    close_connection: bool | None = None
    delay: Delay | None = None
    primary: bool | None = None

    def to_dict(self) -> dict:
        result: dict = {}
        if self.status_code is not None:
            result["statusCode"] = self.status_code
        if self.headers is not None:
            result["headers"] = _serialize_key_multi_values(self.headers)
        if self.events is not None:
            result["events"] = [e.to_dict() if hasattr(e, 'to_dict') else e for e in self.events]
        if self.close_connection is not None:
            result["closeConnection"] = self.close_connection
        if self.delay is not None:
            result["delay"] = self.delay.to_dict()
        if self.primary is not None:
            result["primary"] = self.primary
        return result

    @classmethod
    def from_dict(cls, data: dict) -> HttpSseResponse:
        if data is None:
            return None
        events_data = data.get("events")
        events = None
        if events_data is not None:
            events = [SseEvent.from_dict(e) if isinstance(e, dict) else e for e in events_data]
        return cls(
            status_code=data.get("statusCode"),
            headers=_deserialize_key_multi_values(data.get("headers")),
            events=events,
            close_connection=data.get("closeConnection"),
            delay=Delay.from_dict(data.get("delay")),
            primary=data.get("primary"),
        )


@dataclass
class WebSocketMessage:
    text: str | None = None
    binary: bytes | None = None
    delay: Delay | None = None

    def to_dict(self) -> dict:
        result: dict = {}
        if self.text is not None:
            result["text"] = self.text
        if self.binary is not None:
            import base64
            result["binary"] = base64.b64encode(self.binary).decode("utf-8")
        if self.delay is not None:
            result["delay"] = self.delay.to_dict()
        return result

    @classmethod
    def from_dict(cls, data: dict) -> WebSocketMessage:
        if data is None:
            return None
        import base64
        binary_data = data.get("binary")
        binary = base64.b64decode(binary_data) if binary_data is not None else None
        return cls(
            text=data.get("text"),
            binary=binary,
            delay=Delay.from_dict(data.get("delay")),
        )


@dataclass
class HttpWebSocketResponse:
    subprotocol: str | None = None
    messages: list[WebSocketMessage] | None = None
    close_connection: bool | None = None
    delay: Delay | None = None
    primary: bool | None = None

    def to_dict(self) -> dict:
        result: dict = {}
        if self.subprotocol is not None:
            result["subprotocol"] = self.subprotocol
        if self.messages is not None:
            result["messages"] = [m.to_dict() if hasattr(m, 'to_dict') else m for m in self.messages]
        if self.close_connection is not None:
            result["closeConnection"] = self.close_connection
        if self.delay is not None:
            result["delay"] = self.delay.to_dict()
        if self.primary is not None:
            result["primary"] = self.primary
        return result

    @classmethod
    def from_dict(cls, data: dict) -> HttpWebSocketResponse:
        if data is None:
            return None
        messages_data = data.get("messages")
        messages = None
        if messages_data is not None:
            messages = [WebSocketMessage.from_dict(m) if isinstance(m, dict) else m for m in messages_data]
        return cls(
            subprotocol=data.get("subprotocol"),
            messages=messages,
            close_connection=data.get("closeConnection"),
            delay=Delay.from_dict(data.get("delay")),
            primary=data.get("primary"),
        )


@dataclass
class HttpOverrideForwardedRequest:
    http_request: HttpRequest | None = None
    http_response: HttpResponse | None = None
    delay: Delay | None = None
    request_modifier: dict | None = None
    response_modifier: dict | None = None
    primary: bool | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "httpRequest": self.http_request.to_dict() if self.http_request else None,
            "httpResponse": self.http_response.to_dict() if self.http_response else None,
            "delay": self.delay.to_dict() if self.delay else None,
            "requestModifier": self.request_modifier,
            "responseModifier": self.response_modifier,
            "primary": self.primary,
        })

    @classmethod
    def from_dict(cls, data: dict) -> HttpOverrideForwardedRequest:
        if data is None:
            return None
        return cls(
            http_request=HttpRequest.from_dict(data.get("httpRequest")),
            http_response=HttpResponse.from_dict(data.get("httpResponse")),
            delay=Delay.from_dict(data.get("delay")),
            request_modifier=data.get("requestModifier"),
            response_modifier=data.get("responseModifier"),
            primary=data.get("primary"),
        )

    @staticmethod
    def forward_overridden_request(request: HttpRequest | None = None) -> HttpOverrideForwardedRequest:
        return HttpOverrideForwardedRequest(http_request=request)


@dataclass
class HttpRequestAndHttpResponse:
    http_request: HttpRequest | None = None
    http_response: HttpResponse | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "httpRequest": self.http_request.to_dict() if self.http_request else None,
            "httpResponse": self.http_response.to_dict() if self.http_response else None,
        })

    @classmethod
    def from_dict(cls, data: dict) -> HttpRequestAndHttpResponse:
        if data is None:
            return None
        return cls(
            http_request=HttpRequest.from_dict(data.get("httpRequest")),
            http_response=HttpResponse.from_dict(data.get("httpResponse")),
        )


@dataclass
class ExpectationId:
    id: str = ""

    def to_dict(self) -> dict:
        return {"id": self.id}

    @classmethod
    def from_dict(cls, data: dict) -> ExpectationId:
        if data is None:
            return None
        return cls(id=data.get("id", ""))


@dataclass
class AfterAction:
    http_request: HttpRequest | None = None
    http_class_callback: HttpClassCallback | None = None
    http_object_callback: HttpObjectCallback | None = None
    delay: Delay | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "httpRequest": self.http_request.to_dict() if self.http_request else None,
            "httpClassCallback": self.http_class_callback.to_dict() if self.http_class_callback else None,
            "httpObjectCallback": self.http_object_callback.to_dict() if self.http_object_callback else None,
            "delay": self.delay.to_dict() if self.delay else None,
        })

    @classmethod
    def from_dict(cls, data: dict) -> AfterAction:
        if data is None:
            return None
        return cls(
            http_request=HttpRequest.from_dict(data.get("httpRequest")),
            http_class_callback=HttpClassCallback.from_dict(data.get("httpClassCallback")),
            http_object_callback=HttpObjectCallback.from_dict(data.get("httpObjectCallback")),
            delay=Delay.from_dict(data.get("delay")),
        )


@dataclass
class Expectation:
    id: str | None = None
    priority: int | None = None
    http_request: HttpRequest | None = None
    http_response: HttpResponse | None = None
    http_response_template: HttpTemplate | None = None
    http_response_class_callback: HttpClassCallback | None = None
    http_response_object_callback: HttpObjectCallback | None = None
    http_forward: HttpForward | None = None
    http_forward_template: HttpTemplate | None = None
    http_forward_class_callback: HttpClassCallback | None = None
    http_forward_object_callback: HttpObjectCallback | None = None
    http_override_forwarded_request: HttpOverrideForwardedRequest | None = None
    http_error: HttpError | None = None
    http_sse_response: HttpSseResponse | None = None
    http_websocket_response: HttpWebSocketResponse | None = None
    times: Times | None = None
    time_to_live: TimeToLive | None = None
    after_actions: list[AfterAction] | None = None
    scenario_name: str | None = None
    scenario_state: str | None = None
    new_scenario_state: str | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "id": self.id,
            "priority": self.priority,
            "httpRequest": self.http_request.to_dict() if self.http_request else None,
            "httpResponse": self.http_response.to_dict() if self.http_response else None,
            "httpResponseTemplate": self.http_response_template.to_dict() if self.http_response_template else None,
            "httpResponseClassCallback": self.http_response_class_callback.to_dict() if self.http_response_class_callback else None,
            "httpResponseObjectCallback": self.http_response_object_callback.to_dict() if self.http_response_object_callback else None,
            "httpForward": self.http_forward.to_dict() if self.http_forward else None,
            "httpForwardTemplate": self.http_forward_template.to_dict() if self.http_forward_template else None,
            "httpForwardClassCallback": self.http_forward_class_callback.to_dict() if self.http_forward_class_callback else None,
            "httpForwardObjectCallback": self.http_forward_object_callback.to_dict() if self.http_forward_object_callback else None,
            "httpOverrideForwardedRequest": self.http_override_forwarded_request.to_dict() if self.http_override_forwarded_request else None,
            "httpError": self.http_error.to_dict() if self.http_error else None,
            "httpSseResponse": self.http_sse_response.to_dict() if self.http_sse_response else None,
            "httpWebSocketResponse": self.http_websocket_response.to_dict() if self.http_websocket_response else None,
            "times": self.times.to_dict() if self.times else None,
            "timeToLive": self.time_to_live.to_dict() if self.time_to_live else None,
            "afterActions": [a.to_dict() for a in self.after_actions] if self.after_actions else None,
            "scenarioName": self.scenario_name,
            "scenarioState": self.scenario_state,
            "newScenarioState": self.new_scenario_state,
        })

    @classmethod
    def from_dict(cls, data: dict) -> Expectation:
        if data is None:
            return None
        after_actions_data = data.get("afterActions")
        if isinstance(after_actions_data, dict):
            after_actions_data = [after_actions_data]
        return cls(
            id=data.get("id"),
            priority=data.get("priority"),
            http_request=HttpRequest.from_dict(data.get("httpRequest")),
            http_response=HttpResponse.from_dict(data.get("httpResponse")),
            http_response_template=HttpTemplate.from_dict(data.get("httpResponseTemplate")),
            http_response_class_callback=HttpClassCallback.from_dict(data.get("httpResponseClassCallback")),
            http_response_object_callback=HttpObjectCallback.from_dict(data.get("httpResponseObjectCallback")),
            http_forward=HttpForward.from_dict(data.get("httpForward")),
            http_forward_template=HttpTemplate.from_dict(data.get("httpForwardTemplate")),
            http_forward_class_callback=HttpClassCallback.from_dict(data.get("httpForwardClassCallback")),
            http_forward_object_callback=HttpObjectCallback.from_dict(data.get("httpForwardObjectCallback")),
            http_override_forwarded_request=HttpOverrideForwardedRequest.from_dict(data.get("httpOverrideForwardedRequest")),
            http_error=HttpError.from_dict(data.get("httpError")),
            http_sse_response=HttpSseResponse.from_dict(data.get("httpSseResponse")),
            http_websocket_response=HttpWebSocketResponse.from_dict(data.get("httpWebSocketResponse")),
            times=Times.from_dict(data.get("times")),
            time_to_live=TimeToLive.from_dict(data.get("timeToLive")),
            after_actions=[AfterAction.from_dict(a) for a in after_actions_data] if after_actions_data else None,
            scenario_name=data.get("scenarioName"),
            scenario_state=data.get("scenarioState"),
            new_scenario_state=data.get("newScenarioState"),
        )


@dataclass
class OpenAPIDefinition:
    spec_url_or_payload: str | None = None
    operation_id: str | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "specUrlOrPayload": self.spec_url_or_payload,
            "operationId": self.operation_id,
        })

    @classmethod
    def from_dict(cls, data: dict) -> OpenAPIDefinition:
        if data is None:
            return None
        return cls(
            spec_url_or_payload=data.get("specUrlOrPayload"),
            operation_id=data.get("operationId"),
        )


@dataclass
class OpenAPIExpectation:
    spec_url_or_payload: str | None = None
    operations_and_responses: dict | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "specUrlOrPayload": self.spec_url_or_payload,
            "operationsAndResponses": self.operations_and_responses,
        })

    @classmethod
    def from_dict(cls, data: dict) -> OpenAPIExpectation:
        if data is None:
            return None
        return cls(
            spec_url_or_payload=data.get("specUrlOrPayload"),
            operations_and_responses=data.get("operationsAndResponses"),
        )


@dataclass
class VerificationTimes:
    at_least: int | None = None
    at_most: int | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "atLeast": self.at_least,
            "atMost": self.at_most,
        })

    @classmethod
    def from_dict(cls, data: dict) -> VerificationTimes:
        if data is None:
            return None
        return cls(
            at_least=data.get("atLeast"),
            at_most=data.get("atMost"),
        )


def _vt_at_least(count: int) -> VerificationTimes:
    return VerificationTimes(at_least=count)


def _vt_at_most(count: int) -> VerificationTimes:
    return VerificationTimes(at_most=count)


def _vt_exactly(count: int) -> VerificationTimes:
    return VerificationTimes(at_least=count, at_most=count)


def _vt_once() -> VerificationTimes:
    return VerificationTimes(at_least=1, at_most=1)


def _vt_between(at_least: int, at_most: int) -> VerificationTimes:
    return VerificationTimes(at_least=at_least, at_most=at_most)


VerificationTimes.at_least = staticmethod(_vt_at_least)
VerificationTimes.at_most = staticmethod(_vt_at_most)
VerificationTimes.exactly = staticmethod(_vt_exactly)
VerificationTimes.once = staticmethod(_vt_once)
VerificationTimes.between = staticmethod(_vt_between)


@dataclass
class Verification:
    http_request: HttpRequest | None = None
    expectation_id: ExpectationId | None = None
    times: VerificationTimes | None = None
    maximum_number_of_request_to_return_in_verification_failure: int | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "httpRequest": self.http_request.to_dict() if self.http_request else None,
            "expectationId": self.expectation_id.to_dict() if self.expectation_id else None,
            "times": self.times.to_dict() if self.times else None,
            "maximumNumberOfRequestToReturnInVerificationFailure": self.maximum_number_of_request_to_return_in_verification_failure,
        })

    @classmethod
    def from_dict(cls, data: dict) -> Verification:
        if data is None:
            return None
        return cls(
            http_request=HttpRequest.from_dict(data.get("httpRequest")),
            expectation_id=ExpectationId.from_dict(data.get("expectationId")),
            times=VerificationTimes.from_dict(data.get("times")),
            maximum_number_of_request_to_return_in_verification_failure=data.get("maximumNumberOfRequestToReturnInVerificationFailure"),
        )


@dataclass
class VerificationSequence:
    http_requests: list[HttpRequest] | None = None
    expectation_ids: list[ExpectationId] | None = None

    def to_dict(self) -> dict:
        return _strip_none({
            "httpRequests": [r.to_dict() for r in self.http_requests] if self.http_requests else None,
            "expectationIds": [e.to_dict() for e in self.expectation_ids] if self.expectation_ids else None,
        })

    @classmethod
    def from_dict(cls, data: dict) -> VerificationSequence:
        if data is None:
            return None
        http_requests_data = data.get("httpRequests")
        expectation_ids_data = data.get("expectationIds")
        return cls(
            http_requests=[HttpRequest.from_dict(r) for r in http_requests_data] if http_requests_data else None,
            expectation_ids=[ExpectationId.from_dict(e) for e in expectation_ids_data] if expectation_ids_data else None,
        )


@dataclass
class Ports:
    ports: list[int] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {"ports": self.ports}

    @classmethod
    def from_dict(cls, data: dict) -> Ports:
        if data is None:
            return None
        return cls(ports=data.get("ports", []))


RequestDefinition = HttpRequest
