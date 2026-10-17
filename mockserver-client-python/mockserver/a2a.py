"""Fluent builder for mocking an A2A (Agent-to-Agent) server.

Mirrors the Java ``org.mockserver.client.A2aMockBuilder``. It produces the same
wire-level expectation JSON: a set of HTTP expectations that emulate an A2A agent
speaking JSON-RPC 2.0 over ``POST <path>`` plus a static agent-card document served
at ``GET <agent-card-path>``.

The generated control-plane expectations are:

* an **agent card** (``GET /.well-known/agent.json`` by default) — a static JSON
  document advertising the agent name/description/version/url, its capabilities
  (``streaming`` / ``pushNotifications``) and its skills;
* zero or more **custom task-send handlers** — a JSON-path match on the incoming
  ``tasks/send`` message text, responding with a per-handler result;
* a default ``tasks/send`` handler (replaced by a push-notification *delivery*
  expectation when push notifications are configured);
* ``tasks/get`` and ``tasks/cancel`` handlers;
* an optional **streaming** SSE expectation (``message/stream`` by default);
* optional **push-notification** config-echo and delivery (forward) expectations.

Each JSON-RPC response is a Velocity template that echoes the incoming JSON-RPC id
via ``$!{request.jsonRpcRawId}``.

Example::

    from mockserver import MockServerClient
    from mockserver.a2a import a2a_mock

    a2a_mock() \
        .with_agent_name("TranslatorAgent") \
        .with_skill("translate") \
            .with_name("Translation") \
            .with_description("Translates text between languages") \
            .with_tag("i18n") \
            .with_example("Translate hello to Spanish") \
            .and_() \
        .on_task_send() \
            .matching_message("translate.*") \
            .responding_with("Hola") \
            .and_() \
        .apply_to(client)
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from urllib.parse import urlparse

from mockserver.models import (
    Expectation,
    HttpOverrideForwardedRequest,
    HttpRequest,
    HttpResponse,
    HttpSseResponse,
    HttpTemplate,
    JsonPathBody,
    JsonRpcBody,
    SocketAddress,
    SseEvent,
)


def _escape_json(value: str | None) -> str:
    """Quote a string the same way Jackson ``writeValueAsString`` does, then strip
    the surrounding quotes — yielding only the escaped inner content."""
    if value is None:
        return ""
    quoted = json.dumps(value)
    return quoted[1:-1]


# group 2 consumes the metacharacter after a backslash run, is empty at the end
# and unset (empty alternative) otherwise, so no lookaround is needed
_VELOCITY_METACHARACTERS = re.compile(r"(\\+)(?:([$#]|\Z)|)|[$#]")
_VELOCITY_METACHARACTER_ESCAPES = {"$": "${esc.d}", "#": "${esc.h}", "": ""}


def _velocity_escape_match(match: re.Match) -> str:
    run, following = match.group(1), match.group(2)
    if run is None:
        return _VELOCITY_METACHARACTER_ESCAPES[match.group(0)]
    if following is None:
        return run
    return "${esc.b}" * len(run) + _VELOCITY_METACHARACTER_ESCAPES[following]


def _escape_velocity(value: str | None) -> str | None:
    """Escape Velocity metacharacters so literal ``$`` / ``#`` survive rendering.

    Velocity consumes backslashes written before a reference or directive, so a
    run of backslashes followed by ``$``, ``#`` or the end of the value is
    escaped too; other backslashes are literal to Velocity and left as-is.
    """
    if value is None:
        return None
    return _VELOCITY_METACHARACTERS.sub(_velocity_escape_match, value)


def _escape_message_pattern(pattern: str) -> str:
    """Escape a user-supplied regular expression for safe interpolation between the
    ``/`` delimiters of a JSONPath ``=~ /.../`` regex literal.

    A single left-to-right scan that *preserves* existing regex escape sequences
    (``\\d``, ``\\/``, ``\\\\`` ...) and only neutralises the cases that could break
    out of the ``/`` delimiter:

    - ``\\`` followed by any char: emit both verbatim (keep the escape sequence);
      a *trailing lone* ``\\`` becomes ``\\\\`` so it cannot escape the closing ``/``.
    - bare ``/``: emit ``\\/`` so it cannot close the literal early.
    - newline -> ``\\n``, carriage return -> ``\\r``, NUL -> stripped.
    - any other char: emit verbatim.
    """
    out: list[str] = []
    i, n = 0, len(pattern)
    while i < n:
        c = pattern[i]
        if c == "\\":
            if i + 1 < n:
                out.append("\\")
                out.append(pattern[i + 1])
                i += 2
                continue
            out.append("\\\\")  # trailing lone backslash -> literal backslash
        elif c == "/":
            out.append("\\/")
        elif c == "\n":
            out.append("\\n")
        elif c == "\r":
            out.append("\\r")
        elif c == "\0":
            pass  # strip NUL
        else:
            out.append(c)
        i += 1
    return "".join(out)


def _velocity_json_rpc_response(result_json: str) -> str:
    return (
        '{"statusCode": 200, '
        '"headers": [{"name": "Content-Type", "values": ["application/json"]}], '
        '"body": {"jsonrpc": "2.0", "result": ' + result_json
        + ', "id": $!{request.jsonRpcRawId}}}'
    )


def _template(velocity: str) -> HttpTemplate:
    return HttpTemplate(template_type="VELOCITY", template=velocity)


@dataclass
class _A2aSkillDefinition:
    id: str
    name: str | None = None
    description: str | None = None
    tags: list[str] = field(default_factory=list)
    examples: list[str] = field(default_factory=list)


@dataclass
class _A2aTaskHandler:
    message_pattern: str
    response_text: str
    is_error: bool = False


@dataclass
class _WebhookTarget:
    host: str
    port: int
    secure: bool
    path: str

    def host_header(self) -> str:
        return f"{self.host}:{self.port}"

    @staticmethod
    def parse(url: str) -> _WebhookTarget:
        parsed = urlparse(url)
        secure = (parsed.scheme or "").lower() == "https"
        host = parsed.hostname
        if not host:
            raise ValueError(f"Invalid push-notification webhook URL (no host): {url}")
        port = parsed.port
        if port is None:
            port = 443 if secure else 80
        path = parsed.path
        if not path:
            path = "/"
        return _WebhookTarget(host=host, port=port, secure=secure, path=path)


class A2aMockBuilder:
    def __init__(self, path: str = "/a2a") -> None:
        self._path = path
        self._agent_card_path = "/.well-known/agent.json"
        self._agent_name = "MockAgent"
        self._agent_description = "A mock A2A agent"
        self._agent_version = "1.0.0"
        self._agent_url: str | None = None
        self._default_task_response = "Task completed successfully"
        self._streaming = False
        self._streaming_method = "message/stream"
        self._push_notification_url: str | None = None
        self._skills: list[_A2aSkillDefinition] = []
        self._task_handlers: list[_A2aTaskHandler] = []

    # --- top-level configuration ---------------------------------------

    def with_agent_name(self, name: str) -> A2aMockBuilder:
        self._agent_name = name
        return self

    def with_agent_description(self, description: str) -> A2aMockBuilder:
        self._agent_description = description
        return self

    def with_agent_version(self, version: str) -> A2aMockBuilder:
        self._agent_version = version
        return self

    def with_agent_url(self, url: str) -> A2aMockBuilder:
        self._agent_url = url
        return self

    def with_agent_card_path(self, path: str) -> A2aMockBuilder:
        self._agent_card_path = path
        return self

    def with_default_task_response(self, response: str) -> A2aMockBuilder:
        self._default_task_response = response
        return self

    def with_streaming(self) -> A2aMockBuilder:
        """Advertise and mock the A2A streaming capability.

        When enabled the agent card reports ``capabilities.streaming: true`` and a
        streaming JSON-RPC method (default ``message/stream``, see
        :meth:`with_streaming_method`) returns an SSE stream of
        ``TaskStatusUpdateEvent`` and ``TaskArtifactUpdateEvent`` chunks, each
        wrapped in a JSON-RPC 2.0 response envelope.
        """
        self._streaming = True
        return self

    def with_streaming_method(self, method: str) -> A2aMockBuilder:
        """Override the JSON-RPC method that triggers the streaming response.

        The A2A specification uses ``message/stream``; the legacy method name is
        ``tasks/sendSubscribe``. Implies :meth:`with_streaming`.
        """
        self._streaming_method = method
        self._streaming = True
        return self

    def with_push_notifications(self, webhook_url: str) -> A2aMockBuilder:
        """Advertise and mock A2A push notifications.

        When configured the agent card reports ``capabilities.pushNotifications:
        true``, the ``tasks/pushNotificationConfig/set`` method echoes the
        registered config, and each ``tasks/send`` additionally POSTs the completed
        task (the A2A push-notification payload) to *webhook_url* while still
        returning the JSON-RPC task response to the caller.
        """
        self._push_notification_url = webhook_url
        return self

    def with_skill(self, id: str) -> A2aSkillBuilder:
        return A2aSkillBuilder(self, id)

    def on_task_send(self) -> A2aTaskHandlerBuilder:
        return A2aTaskHandlerBuilder(self)

    # --- terminal operations -------------------------------------------

    def apply_to(self, client) -> list[Expectation]:
        return client.upsert(*self.build())

    def build(self) -> list[Expectation]:
        expectations: list[Expectation] = [self._build_agent_card_expectation()]

        for handler in self._task_handlers:
            expectations.append(self._build_custom_task_handler(handler))

        if self._streaming:
            expectations.append(self._build_streaming_expectation())

        if self._push_notification_url is not None:
            expectations.append(self._build_push_notification_config_expectation())
            expectations.append(self._build_push_notification_delivery_expectation())
        else:
            expectations.append(self._build_tasks_send_expectation())

        expectations.append(self._build_tasks_get_expectation())
        expectations.append(self._build_tasks_cancel_expectation())

        return expectations

    # --- expectation builders ------------------------------------------

    def _json_rpc_request(self, method: str) -> HttpRequest:
        return HttpRequest(method="POST", path=self._path, body=JsonRpcBody(method=method))

    def _build_agent_card_expectation(self) -> Expectation:
        skills_items: list[str] = []
        for skill in self._skills:
            parts = ['{"id": "' + _escape_json(skill.id) + '"']
            name = skill.name if skill.name is not None else skill.id
            parts.append(', "name": "' + _escape_json(name) + '"')
            if skill.description is not None:
                parts.append(', "description": "' + _escape_json(skill.description) + '"')
            if skill.tags:
                tag_items = ['"' + _escape_json(tag) + '"' for tag in skill.tags]
                parts.append(', "tags": [' + ", ".join(tag_items) + "]")
            if skill.examples:
                example_items = ['"' + _escape_json(ex) + '"' for ex in skill.examples]
                parts.append(', "examples": [' + ", ".join(example_items) + "]")
            parts.append("}")
            skills_items.append("".join(parts))
        skills_json = "[" + ", ".join(skills_items) + "]"

        url = self._agent_url if self._agent_url is not None else "http://localhost" + self._path
        streaming = "true" if self._streaming else "false"
        push = "true" if self._push_notification_url is not None else "false"

        agent_card_json = (
            "{"
            '"name": "' + _escape_json(self._agent_name) + '", '
            '"description": "' + _escape_json(self._agent_description) + '", '
            '"version": "' + _escape_json(self._agent_version) + '", '
            '"url": "' + _escape_json(url) + '", '
            '"capabilities": {"streaming": ' + streaming + ', "pushNotifications": ' + push
            + ', "stateTransitionHistory": false}, '
            '"skills": ' + skills_json + "}"
        )

        return Expectation(
            http_request=HttpRequest(method="GET", path=self._agent_card_path),
            http_response=HttpResponse(status_code=200, body=agent_card_json).with_header(
                "Content-Type", "application/json"
            ),
        )

    def _build_tasks_send_expectation(self) -> Expectation:
        result_json = self._build_task_result_json(self._default_task_response, False)
        return Expectation(
            http_request=self._json_rpc_request("tasks/send"),
            http_response_template=_template(_velocity_json_rpc_response(result_json)),
        )

    def _build_tasks_get_expectation(self) -> Expectation:
        result_json = self._build_task_result_json(self._default_task_response, False)
        return Expectation(
            http_request=self._json_rpc_request("tasks/get"),
            http_response_template=_template(_velocity_json_rpc_response(result_json)),
        )

    def _build_tasks_cancel_expectation(self) -> Expectation:
        result_json = '{"id": "mock-task-id", "status": {"state": "canceled"}}'
        return Expectation(
            http_request=self._json_rpc_request("tasks/cancel"),
            http_response_template=_template(_velocity_json_rpc_response(result_json)),
        )

    def _build_streaming_expectation(self) -> Expectation:
        text = _escape_json(self._default_task_response)
        task_id = "mock-task-id"

        # A2A streaming: each SSE event data is a JSON-RPC 2.0 response envelope
        # wrapping a TaskStatusUpdateEvent or TaskArtifactUpdateEvent. The JSON-RPC
        # id is not known at build time, so a stable placeholder is used (streaming
        # clients correlate by stream).
        status_working = SseEvent(
            event="message",
            data='{"jsonrpc": "2.0", "id": "1", "result": '
            '{"taskId": "' + task_id + '", "kind": "status-update", '
            '"status": {"state": "working"}, "final": false}}',
        )
        artifact_update = SseEvent(
            event="message",
            data='{"jsonrpc": "2.0", "id": "1", "result": '
            '{"taskId": "' + task_id + '", "kind": "artifact-update", '
            '"artifact": {"parts": [{"type": "text", "text": "' + text + '"}]}}}',
        )
        status_completed = SseEvent(
            event="message",
            data='{"jsonrpc": "2.0", "id": "1", "result": '
            '{"taskId": "' + task_id + '", "kind": "status-update", '
            '"status": {"state": "completed"}, "final": true}}',
        )

        sse_response = HttpSseResponse(
            status_code=200,
            events=[status_working, artifact_update, status_completed],
            close_connection=True,
        )

        return Expectation(
            http_request=self._json_rpc_request(self._streaming_method),
            http_sse_response=sse_response,
        )

    def _build_push_notification_config_expectation(self) -> Expectation:
        # Echo the registered push-notification config back as the JSON-RPC result.
        result_json = '{"url": "' + _escape_velocity(_escape_json(self._push_notification_url)) + '"}'
        return Expectation(
            http_request=self._json_rpc_request("tasks/pushNotificationConfig/set"),
            http_response_template=_template(_velocity_json_rpc_response(result_json)),
        )

    def _build_push_notification_delivery_expectation(self) -> Expectation:
        # When push notifications are configured, a tasks/send both returns the
        # JSON-RPC task response to the caller AND POSTs the completed task (the
        # push-notification payload) to the configured webhook URL. This is modelled
        # with an override-forwarded-request: the request override targets the
        # webhook (literal body), and a Velocity response *template* produces the
        # caller's JSON-RPC response so the request's id is echoed back.
        target = _WebhookTarget.parse(self._push_notification_url)

        # Literal webhook POST body — no Velocity engine runs over a request
        # override, so only JSON escaping is applied. The push payload carries no
        # JSON-RPC id (server-initiated).
        push_body = (
            '{"jsonrpc": "2.0", "result": '
            + self._build_task_result_json_raw(self._default_task_response, False)
            + "}"
        )

        webhook_request = (
            HttpRequest(method="POST", path=target.path, body=push_body)
            .with_secure(target.secure)
            .with_header("Host", target.host_header())
            .with_header("Content-Type", "application/json")
        )
        webhook_request.socket_address = SocketAddress(
            host=target.host,
            port=target.port,
            scheme="HTTPS" if target.secure else "HTTP",
        )

        # Caller response — a Velocity template so $!{request.jsonRpcRawId} echoes
        # the request id, matching the non-push tasks/send contract.
        client_response_template = _template(
            _velocity_json_rpc_response(self._build_task_result_json(self._default_task_response, False))
        )

        return Expectation(
            http_request=self._json_rpc_request("tasks/send"),
            http_override_forwarded_request=HttpOverrideForwardedRequest(
                request_override=webhook_request,
                response_template=client_response_template,
            ),
        )

    def _build_custom_task_handler(self, handler: _A2aTaskHandler) -> Expectation:
        escaped_pattern = _escape_message_pattern(handler.message_pattern)
        json_path = (
            "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /"
            + escaped_pattern
            + "/)]"
        )
        result_json = self._build_task_result_json(handler.response_text, handler.is_error)

        return Expectation(
            http_request=HttpRequest(method="POST", path=self._path, body=JsonPathBody(json_path=json_path)),
            http_response_template=_template(_velocity_json_rpc_response(result_json)),
        )

    def _build_task_result_json(self, response_text: str, is_error: bool) -> str:
        # For Velocity-templated response bodies: the text must survive the Velocity
        # engine, so metacharacters are escaped here and un-escaped by the template
        # engine at response time.
        return self._task_result_json(_escape_velocity(_escape_json(response_text)), is_error)

    def _build_task_result_json_raw(self, response_text: str, is_error: bool) -> str:
        # For literal (non-templated) response bodies (e.g. the webhook POST
        # payload), where no Velocity engine runs, only JSON escaping is applied —
        # Velocity escaping would corrupt any '$' / '#' into "${esc.d}" / "${esc.h}".
        return self._task_result_json(_escape_json(response_text), is_error)

    @staticmethod
    def _task_result_json(escaped_text: str, is_error: bool) -> str:
        state = "failed" if is_error else "completed"
        return (
            '{"id": "mock-task-id", '
            '"status": {"state": "' + state + '"}, '
            '"artifacts": [{"parts": [{"type": "text", "text": "' + escaped_text + '"}]}]}'
        )


class A2aSkillBuilder:
    def __init__(self, parent: A2aMockBuilder, id: str) -> None:
        self._parent = parent
        self._skill = _A2aSkillDefinition(id=id)

    def with_name(self, name: str) -> A2aSkillBuilder:
        self._skill.name = name
        return self

    def with_description(self, description: str) -> A2aSkillBuilder:
        self._skill.description = description
        return self

    def with_tag(self, tag: str) -> A2aSkillBuilder:
        self._skill.tags.append(tag)
        return self

    def with_example(self, example: str) -> A2aSkillBuilder:
        self._skill.examples.append(example)
        return self

    def and_(self) -> A2aMockBuilder:
        self._parent._skills.append(self._skill)
        return self._parent


class A2aTaskHandlerBuilder:
    def __init__(self, parent: A2aMockBuilder) -> None:
        self._parent = parent
        self._message_pattern = ".*"
        self._response_text = "Task completed"
        self._is_error = False

    def matching_message(self, pattern: str) -> A2aTaskHandlerBuilder:
        self._message_pattern = pattern
        return self

    def responding_with(self, text: str, is_error: bool = False) -> A2aTaskHandlerBuilder:
        self._response_text = text
        self._is_error = is_error
        return self

    def and_(self) -> A2aMockBuilder:
        self._parent._task_handlers.append(
            _A2aTaskHandler(
                message_pattern=self._message_pattern,
                response_text=self._response_text,
                is_error=self._is_error,
            )
        )
        return self._parent


def a2a_mock(path: str = "/a2a") -> A2aMockBuilder:
    """Create a new A2A mock builder. ``path`` defaults to ``/a2a``."""
    return A2aMockBuilder(path)
