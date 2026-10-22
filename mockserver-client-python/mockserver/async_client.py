from __future__ import annotations

import asyncio
import base64
import json
import logging
import socket
import ssl
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Callable

from mockserver.exceptions import (
    MockServerConnectionError,
    MockServerError,
    MockServerVerificationError,
)
from mockserver.fluent import ForwardChainExpectation
from mockserver.models import (
    AnyOfRequestDefinition,
    ConditionalRequestDefinition,
    DebugMismatchResult,
    DnsRequestDefinition,
    Expectation,
    ExpectationFilter,
    ExplainUnmatchedResult,
    HttpChaosProfile,
    HttpObjectCallback,
    HttpRequest,
    HttpRequestAndHttpResponse,
    HttpResponse,
    LoadScenario,
    OpenAPIExpectation,
    Ports,
    RequestExplanation,
    TimeToLive,
    Times,
    Verification,
    VerificationSequence,
    VerificationTimes,
    _deserialize_recorded_request,
)
from mockserver.websocket_client import MockServerWebSocketClient

logger = logging.getLogger(__name__)


def _log_import_warnings(format: str | None, warnings: object) -> None:
    if not isinstance(warnings, list) or not warnings:
        return
    lines = "\n".join(
        f"  [{w.get('item')}] {w.get('construct')}: {w.get('detail')}"
        for w in warnings
        if isinstance(w, dict)
    )
    logger.warning(
        "import of %s document could not fully map %d construct(s), check the imported expectations:\n%s",
        format or "auto-detected",
        len(warnings),
        lines,
    )


# Default for clear()'s expectation_filter: only an omitted argument means "no filter".
NO_FILTER: Any = object()


class AsyncScenarioHandle:
    """An async handle to a single named stateful scenario on the server.

    Obtained via :meth:`AsyncMockServerClient.scenario`. Wraps the
    ``/mockserver/scenario/{name}`` control-plane endpoints. Each method returns
    the server's JSON response as a dict (with ``scenarioName`` and
    ``currentState`` keys).
    """

    def __init__(self, client: AsyncMockServerClient, name: str) -> None:
        self._client = client
        self._name = name

    @property
    def name(self) -> str:
        return self._name

    async def state(self) -> str | None:
        """GET the current state of this scenario (``None`` if not yet set)."""
        result = await self._client._scenario_request(
            "GET", f"/mockserver/scenario/{urllib.parse.quote(self._name, safe='')}"
        )
        return result.get("currentState")

    async def set(
        self,
        state: str,
        transition_after_ms: int | None = None,
        next_state: str | None = None,
    ) -> dict:
        """PUT to set this scenario's state, optionally scheduling a timed
        transition to ``next_state`` after ``transition_after_ms`` milliseconds.
        """
        payload: dict = {"state": state}
        if transition_after_ms is not None:
            payload["transitionAfterMs"] = transition_after_ms
        if next_state is not None:
            payload["nextState"] = next_state
        return await self._client._scenario_request(
            "PUT",
            f"/mockserver/scenario/{urllib.parse.quote(self._name, safe='')}",
            json.dumps(payload),
        )

    async def trigger(self, new_state: str) -> dict:
        """PUT an external trigger advancing this scenario to ``new_state``."""
        return await self._client._scenario_request(
            "PUT",
            f"/mockserver/scenario/{urllib.parse.quote(self._name, safe='')}/trigger",
            json.dumps({"newState": new_state}),
        )


class AsyncMockServerClient:
    def __init__(
        self,
        host: str,
        port: int,
        context_path: str = "",
        secure: bool = False,
        ca_cert_path: str | None = None,
        tls_verify: bool = True,
        client_cert_path: str | None = None,
        client_key_path: str | None = None,
        control_plane_bearer_token: str | None = None,
        control_plane_bearer_token_supplier: Callable[[], str] | None = None,
    ) -> None:
        self._host = host
        self._port = port
        self._context_path = context_path
        self._secure = secure
        self._ca_cert_path = ca_cert_path
        self._tls_verify = tls_verify
        self._client_cert_path = client_cert_path
        self._client_key_path = client_key_path
        self._control_plane_bearer_token = control_plane_bearer_token
        self._control_plane_bearer_token_supplier = control_plane_bearer_token_supplier
        self._websocket_clients: list[MockServerWebSocketClient] = []

        scheme = "https" if secure else "http"
        ctx_path = ""
        if context_path:
            ctx_path = context_path if context_path.startswith("/") else f"/{context_path}"
        self._base_url = f"{scheme}://{host}:{port}{ctx_path}"

        self._ssl_context: ssl.SSLContext | None = None
        if secure:
            self._ssl_context = ssl.create_default_context()
            if ca_cert_path:
                self._ssl_context.load_verify_locations(ca_cert_path)
            elif not tls_verify:
                self._ssl_context.check_hostname = False
                self._ssl_context.verify_mode = ssl.CERT_NONE
            if client_cert_path:
                # Load the client certificate chain (and private key) for mTLS.
                self._ssl_context.load_cert_chain(
                    certfile=client_cert_path, keyfile=client_key_path
                )

    def _control_plane_bearer(self) -> str | None:
        """Resolve the control-plane bearer token for the current request.

        The supplier (if configured) is evaluated per request so rotating /
        short-lived tokens are picked up; otherwise the static token is used.
        """
        if self._control_plane_bearer_token_supplier is not None:
            return self._control_plane_bearer_token_supplier()
        return self._control_plane_bearer_token

    async def _request(
        self,
        method: str,
        path: str,
        body: str | bytes | None = None,
        query_params: dict[str, str] | None = None,
        content_type: str = "application/json; charset=utf-8",
    ) -> tuple[int, str]:
        url = f"{self._base_url}{path}"
        if query_params:
            url = f"{url}?{urllib.parse.urlencode(query_params)}"

        if isinstance(body, bytes):
            data = body
        elif body:
            data = body.encode("utf-8")
        else:
            data = None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", content_type)
        bearer = self._control_plane_bearer()
        if bearer:
            req.add_header("Authorization", f"Bearer {bearer}")

        def _do_request() -> tuple[int, str]:
            try:
                with urllib.request.urlopen(
                    req, context=self._ssl_context, timeout=60
                ) as response:
                    return response.status, response.read().decode("utf-8")
            except urllib.error.HTTPError as e:
                # HTTPError is itself a closeable, response-like object; reading
                # its body without closing leaks the underlying socket/fd.
                with e:
                    return e.code, e.read().decode("utf-8")
            except socket.timeout as e:
                raise MockServerConnectionError(
                    f"Request to MockServer at {self._base_url} timed out: {e}"
                ) from e
            except (urllib.error.URLError, OSError) as e:
                raise MockServerConnectionError(
                    f"Failed to connect to MockServer at {self._base_url}: {e}"
                ) from e

        return await asyncio.to_thread(_do_request)

    async def upsert(self, *expectations: Expectation) -> list[Expectation]:
        body = json.dumps([e.to_dict() for e in expectations])
        status, response_body = await self._request("PUT", "/mockserver/expectation", body)
        if status == 400:
            raise MockServerError(f"Invalid expectation: {response_body}")
        if status >= 400:
            raise MockServerError(
                f"Failed to upsert expectations (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return list(expectations)

    async def open_api_expectation(self, expectation: OpenAPIExpectation) -> None:
        body = json.dumps(expectation.to_dict())
        status, response_body = await self._request("PUT", "/mockserver/openapi", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to create OpenAPI expectation (status={status}): {response_body}"
            )

    async def clear(
        self,
        request: HttpRequest | None = None,
        clear_type: str | None = None,
        expectation_filter: ExpectationFilter = NO_FILTER,  # type: ignore[assignment]
    ) -> None:
        """Clear state matching ``request`` (everything when omitted).

        With ``expectation_filter``, only the expectations that match both the
        request and the filter's metadata or unmatched selection are cleared and
        the event log is left untouched. A filter that selects nothing, that pages,
        or a ``clear_type`` other than ``expectations`` raises ``ValueError``
        before anything is sent. Passing ``expectation_filter=None`` explicitly
        raises too: only omitting it means a clear without a filter.
        """
        if expectation_filter is not NO_FILTER:
            # fail closed: an explicit None (e.g. from a helper that returned nothing) must not
            # become a clear of every expectation and the whole log
            return await self._clear_with_filter(request, clear_type, expectation_filter)
        query_params = {}
        if clear_type:
            query_params["type"] = clear_type
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT", "/mockserver/clear", body, query_params or None
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to clear (status={status}): {response_body}"
            )

    async def _clear_with_filter(
        self,
        request: HttpRequest | None,
        clear_type: str | None,
        expectation_filter: ExpectationFilter,
    ) -> None:
        # fail closed: a filter that selects nothing would otherwise clear every expectation
        if not isinstance(expectation_filter, ExpectationFilter):
            raise ValueError("expectation_filter must be an ExpectationFilter")
        if not expectation_filter.has_selection():
            raise ValueError(
                "clear with a filter requires a metadata or unmatched selection; "
                "use clear(request) to clear without one"
            )
        if expectation_filter.has_paging():
            raise ValueError("offset and limit are not supported for clear")
        if clear_type is not None and clear_type.lower() != "expectations":
            raise ValueError(
                f"clear with a filter only clears expectations, not clear_type={clear_type!r}"
            )
        query_params = {"type": "expectations"}
        query_params.update(expectation_filter.to_query_params())
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT", "/mockserver/clear", body, query_params
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to clear (status={status}): {response_body}"
            )

    async def clear_by_metadata(self, key: str, value: Any) -> None:
        """Clear the expectations whose metadata has ``key`` with a value equal to ``value``.

        ``value`` must not be ``None``: use :meth:`clear_by_metadata_key` for any
        value, or ``JSON_NULL`` for a JSON null.
        """
        await self.clear(expectation_filter=ExpectationFilter().with_metadata(key, value))

    async def clear_by_metadata_key(self, key: str) -> None:
        """Clear the expectations whose metadata has ``key``, whatever its value."""
        await self.clear(expectation_filter=ExpectationFilter().with_metadata_key(key))

    async def clear_unmatched_expectations(self) -> None:
        """Clear the expectations that have not matched any request."""
        await self.clear(expectation_filter=ExpectationFilter().with_unmatched(True))

    async def clear_by_id(
        self,
        expectation_id: str,
        clear_type: str | None = None,
    ) -> None:
        query_params = {}
        if clear_type:
            query_params["type"] = clear_type
        body = json.dumps({"id": expectation_id})
        status, response_body = await self._request(
            "PUT", "/mockserver/clear", body, query_params or None
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to clear by id (status={status}): {response_body}"
            )

    async def reset(self) -> None:
        status, response_body = await self._request("PUT", "/mockserver/reset")
        if status >= 400:
            raise MockServerError(
                f"Failed to reset (status={status}): {response_body}"
            )

    async def freeze_clock(self, instant: str | None = None) -> dict:
        """Freeze the server clock at the given ISO-8601 instant.

        If *instant* is ``None``, the clock freezes at the current real time.

        Returns the JSON response as a dict with keys ``status``,
        ``currentInstant``, and ``currentEpochMillis``.
        """
        payload: dict = {"action": "freeze"}
        if instant is not None:
            payload["instant"] = instant
        body = json.dumps(payload)
        status, response_body = await self._request("PUT", "/mockserver/clock", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to freeze clock (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def advance_clock(self, duration_millis: int) -> dict:
        """Advance the frozen clock by *duration_millis* milliseconds.

        Returns the JSON response as a dict with keys ``status``,
        ``currentInstant``, and ``currentEpochMillis``.
        """
        body = json.dumps({"action": "advance", "durationMillis": duration_millis})
        status, response_body = await self._request("PUT", "/mockserver/clock", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to advance clock (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def reset_clock(self) -> dict:
        """Reset the server clock to real wall-clock time.

        Returns the JSON response as a dict with keys ``status``,
        ``currentInstant``, and ``currentEpochMillis``.
        """
        body = json.dumps({"action": "reset"})
        status, response_body = await self._request("PUT", "/mockserver/clock", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to reset clock (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def clock_status(self) -> dict:
        """Query the current clock status.

        Returns a dict with keys ``currentInstant``, ``currentEpochMillis``,
        and ``frozen``.
        """
        status, response_body = await self._request("GET", "/mockserver/clock")
        if status >= 400:
            raise MockServerError(
                f"Failed to get clock status (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def retrieve_metrics(self) -> dict:
        """Retrieve the JSON metric-counter snapshot.

        Issues ``PUT /mockserver/retrieve?type=METRICS`` and returns the flat
        JSON object mapping each metric name to its long value. When metrics are
        disabled the server returns an empty object (``{}``).
        """
        status, response_body = await self._request(
            "PUT", "/mockserver/retrieve", "", {"type": "METRICS"}
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve metrics (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def scrape_metrics(self) -> str:
        """Scrape the Prometheus metrics exposition text.

        Issues ``GET /mockserver/metrics`` and returns the Prometheus/OpenMetrics
        exposition text. Raises :class:`MockServerError` when metrics are disabled
        (the server responds ``404``).
        """
        status, response_body = await self._request("GET", "/mockserver/metrics")
        if status == 404:
            raise MockServerError(
                "Failed to scrape metrics: metrics are disabled "
                f"(start MockServer with metricsEnabled to enable it) (status=404): {response_body}"
            )
        if status >= 400:
            raise MockServerError(
                f"Failed to scrape metrics (status={status}): {response_body}"
            )
        return response_body or ""

    async def retrieve_configuration(self) -> str:
        """Read the effective live configuration.

        Issues ``GET /mockserver/configuration`` and returns the serialized
        configuration JSON document as a string.
        """
        status, response_body = await self._request("GET", "/mockserver/configuration")
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve configuration (status={status}): {response_body}"
            )
        return response_body or ""

    async def update_configuration(self, config_json: str) -> str:
        """Update the live configuration (partial update).

        Issues ``PUT /mockserver/configuration`` with *config_json* (a
        ``ConfigurationDTO`` JSON document; only the present fields are applied)
        and returns the serialized updated configuration JSON. A ``None`` body is
        sent as an empty string. Properties fixed at startup, such as
        ``forwardProxyBlockPrivateNetworks``, ``forwardProxyAllowedHosts`` and
        ``forwardProxyDeniedHosts``, are ignored and the server logs a warning.
        """
        body = config_json if config_json is not None else ""
        status, response_body = await self._request(
            "PUT", "/mockserver/configuration", body
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to update configuration (status={status}): {response_body}"
            )
        return response_body or ""

    async def retrieve_drift(self) -> dict:
        """Retrieve the recorded mock drift report.

        Issues ``GET /mockserver/drift`` and returns the parsed report, a dict of
        the form ``{"count": <n>, "drifts": [ ... ]}`` where each entry describes a
        difference detected between a mock's configured response and the live
        upstream response for the same request. When no drift has been recorded the
        server returns an empty report (``{}``).
        """
        status, response_body = await self._request("GET", "/mockserver/drift")
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve drift (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def clear_drift(self) -> None:
        """Clear all recorded mock drift.

        Issues ``PUT /mockserver/drift/clear``.
        """
        status, response_body = await self._request(
            "PUT", "/mockserver/drift/clear"
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to clear drift (status={status}): {response_body}"
            )

    async def pact_import(self, pact_json: str) -> str:
        """Import a Pact v3 contract as expectations.

        Issues ``PUT /mockserver/pact/import`` with the contract JSON and returns
        the server body (the JSON array of upserted expectations). Rejects a blank
        contract before sending.
        """
        if not pact_json or not pact_json.strip():
            raise ValueError("pact_import requires a non-empty Pact contract JSON")
        status, response_body = await self._request(
            "PUT", "/mockserver/pact/import", pact_json
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to import pact (status={status}): {response_body}"
            )
        return response_body or ""

    async def pact_export(
        self, consumer: str | None = None, provider: str | None = None
    ) -> str:
        """Export the active expectations as a Pact v3 contract.

        Issues ``PUT /mockserver/pact`` with optional ``consumer`` and
        ``provider`` query params (added only when non-blank) and returns the
        generated Pact contract JSON.
        """
        query_params: dict[str, str] = {}
        if consumer:
            query_params["consumer"] = consumer
        if provider:
            query_params["provider"] = provider
        status, response_body = await self._request(
            "PUT", "/mockserver/pact", "", query_params or None
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to export pact (status={status}): {response_body}"
            )
        return response_body or ""

    async def pact_verify(self, pact_json: str) -> str:
        """Verify a Pact v3 contract against the active expectations.

        Issues ``PUT /mockserver/pact/verify`` with the contract JSON. The server
        returns ``202`` when every interaction matches (verification passed) and
        ``406`` when verification fails. Both are expected outcomes: the
        verification report body is returned for ``202`` (pass) and ``406`` (fail)
        without raising, so the caller can inspect the report and decide. Only a
        genuinely bad request (e.g. ``400`` malformed contract) raises
        :class:`MockServerError`.
        """
        if not pact_json or not pact_json.strip():
            raise ValueError("pact_verify requires a non-empty Pact contract JSON")
        status, response_body = await self._request(
            "PUT", "/mockserver/pact/verify", pact_json
        )
        if status not in (200, 202, 406):
            raise MockServerError(
                f"Failed to verify pact (status={status}): {response_body}"
            )
        return response_body or ""

    async def store_file(self, name: str, content: str | bytes) -> dict:
        """Store a file in the in-memory file store.

        Issues ``PUT /mockserver/files/store`` with ``name`` and ``content``.
        ``str`` content is sent verbatim (UTF-8); ``bytes`` content is base64
        encoded and sent with ``base64: true``. Returns the
        ``{"name", "size"}`` JSON acknowledgement.
        """
        payload: dict = {"name": name}
        if isinstance(content, bytes):
            payload["content"] = base64.b64encode(content).decode("ascii")
            payload["base64"] = True
        else:
            payload["content"] = content
        status, response_body = await self._request(
            "PUT", "/mockserver/files/store", json.dumps(payload)
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to store file '{name}' (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def retrieve_file(self, name: str) -> str:
        """Retrieve a file from the in-memory file store.

        Issues ``PUT /mockserver/files/retrieve`` with ``{"name": name}`` and
        returns the raw ``200`` response body. An unknown file responds ``404``
        and raises :class:`MockServerError` with ``"file not found: <name>"``
        (matching the Go/.NET/Ruby/Rust clients).
        """
        body = json.dumps({"name": name})
        status, response_body = await self._request(
            "PUT", "/mockserver/files/retrieve", body
        )
        if status == 404:
            raise MockServerError(f"file not found: {name}")
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve file '{name}' (status={status}): {response_body}"
            )
        return response_body or ""

    async def list_files(self) -> list[str]:
        """List the names of the files held in the in-memory file store.

        Issues ``PUT /mockserver/files/list`` and returns the JSON array of file
        names.
        """
        status, response_body = await self._request(
            "PUT", "/mockserver/files/list"
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to list files (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return parsed
        return []

    async def delete_file(self, name: str) -> None:
        """Delete a file from the in-memory file store.

        Issues ``PUT /mockserver/files/delete`` with ``{"name": name}``. An
        unknown file responds ``404`` and raises :class:`MockServerError`.
        """
        body = json.dumps({"name": name})
        status, response_body = await self._request(
            "PUT", "/mockserver/files/delete", body
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to delete file '{name}' (status={status}): {response_body}"
            )

    async def import_har(self, har_json: str) -> list[Expectation]:
        """Import a HAR document as expectations.

        Issues ``PUT /mockserver/import?format=har`` with the HAR JSON and returns
        the created/updated expectations.
        """
        return await self.import_document(har_json, "har")

    async def import_postman_collection(self, collection_json: str) -> list[Expectation]:
        """Import a Postman collection as expectations.

        Issues ``PUT /mockserver/import?format=postman`` with the collection JSON
        and returns the created/updated expectations.
        """
        return await self.import_document(collection_json, "postman")

    async def import_document(
        self, document_json: str, format: str | None = None
    ) -> list[Expectation]:
        """Import a HAR, Postman, Pact, WireMock, Mountebank or Mockoon document as
        expectations.

        Issues ``PUT /mockserver/import`` with the document JSON. *format* (one of
        ``har``, ``postman``, ``pact``, ``wiremock``, ``mountebank``, ``mockoon``)
        selects the importer; when omitted the server auto-detects the format from
        the JSON shape. Returns the created/updated expectations. The WireMock,
        Mountebank and Mockoon importers also report each foreign construct they
        could not map faithfully; those warnings are logged at ``WARNING``.

        ``format="recording"`` raises :class:`ValueError`: it loads recorded
        request/response pairs into the request log rather than adding
        expectations, so send ``PUT /mockserver/import?format=recording`` directly.
        Raises :class:`MockServerError` if the reply is an object without an
        ``expectations`` list.
        """
        if not document_json or not document_json.strip():
            raise ValueError("import_document requires a non-empty document JSON")
        if format is not None and format.strip().lower() == "recording":
            raise ValueError(
                "import_document does not support format=recording, which loads recorded "
                "request/response pairs into the request log rather than adding expectations "
                "- send PUT /mockserver/import?format=recording directly"
            )
        query_params = {"format": format} if format else None
        status, response_body = await self._request(
            "PUT", "/mockserver/import", document_json, query_params
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to import document (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, dict):
                # the migration importers (wiremock, mountebank, mockoon) reply
                # {"expectations": [...], "warnings": [...]}
                if not isinstance(parsed.get("expectations"), list):
                    raise MockServerError(
                        "import response has no expectations list - server responded: "
                        f"{response_body}"
                    )
                _log_import_warnings(format, parsed.get("warnings"))
                parsed = parsed["expectations"]
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return []

    async def set_mode(self, mode: str) -> dict:
        """Set the high-level operating mode.

        Issues ``PUT /mockserver/mode?mode=<MODE>`` (one of ``SIMULATE``,
        ``SPY`` or ``CAPTURE``; see :class:`~mockserver.models.MockMode`). Returns
        the ``{"mode", "proxyUnmatchedRequests"}`` JSON.
        """
        status, response_body = await self._request(
            "PUT", "/mockserver/mode", "", {"mode": mode}
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to set mode '{mode}' (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def retrieve_mode(self) -> dict:
        """Read the current operating mode.

        Issues ``GET /mockserver/mode`` and returns the
        ``{"mode", "proxyUnmatchedRequests"}`` JSON.
        """
        status, response_body = await self._request("GET", "/mockserver/mode")
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve mode (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def wsdl_expectation(self, wsdl: str) -> list[Expectation]:
        """Generate expectations from a WSDL document.

        Issues ``PUT /mockserver/wsdl`` with the raw WSDL XML body
        (Content-Type ``text/xml``) and returns the generated (upserted)
        expectations.
        """
        if not wsdl or not wsdl.strip():
            raise ValueError("wsdl_expectation requires a non-empty WSDL document")
        status, response_body = await self._request(
            "PUT", "/mockserver/wsdl", wsdl, content_type="text/xml; charset=utf-8"
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to generate WSDL expectations (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return []

    async def upload_grpc_descriptor(self, descriptor_set_bytes: bytes) -> None:
        """Upload a compiled protobuf descriptor set so gRPC requests can be matched.

        *descriptor_set_bytes* must be the raw bytes of a
        ``FileDescriptorSet`` (e.g. the output of
        ``protoc --descriptor_set_out=... --include_imports``). The bytes are
        sent verbatim (not base64-encoded) as the request body.
        """
        if not descriptor_set_bytes:
            raise MockServerError("descriptor set bytes must not be empty")
        status, response_body = await self._request(
            "PUT",
            "/mockserver/grpc/descriptors",
            descriptor_set_bytes,
            content_type="application/octet-stream",
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to upload gRPC descriptor (status={status}): {response_body}"
            )

    async def retrieve_grpc_services(self) -> list[dict]:
        """Retrieve the gRPC services registered from uploaded descriptor sets.

        Returns a list of service dicts, each with a ``name`` and a list of
        ``methods`` (``name``, ``inputType``, ``outputType``,
        ``clientStreaming``, ``serverStreaming``).
        """
        status, response_body = await self._request(
            "PUT", "/mockserver/grpc/services"
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve gRPC services (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else []

    async def clear_grpc_descriptors(self) -> None:
        """Clear all uploaded gRPC descriptor sets and registered services."""
        status, response_body = await self._request(
            "PUT", "/mockserver/grpc/clear"
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to clear gRPC descriptors (status={status}): {response_body}"
            )

    async def set_service_chaos(
        self, host: str, chaos: HttpChaosProfile, ttl_millis: int | None = None
    ) -> dict:
        """Register a service-scoped HTTP chaos profile for an upstream *host*.

        The profile is applied to every matched forward expectation to that host
        that does not define its own chaos (an expectation's own chaos always
        wins). The host is matched case-insensitively, ignoring any ``:port``.

        If *ttl_millis* is set, the chaos auto-reverts after that many milliseconds
        (a dead-man's switch so it self-heals even if no clear is sent).
        """
        payload: dict = {"host": host, "chaos": chaos.to_dict()}
        if ttl_millis is not None:
            payload["ttlMillis"] = ttl_millis
        body = json.dumps(payload)
        status, response_body = await self._request("PUT", "/mockserver/serviceChaos", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to set service chaos (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def remove_service_chaos(self, host: str) -> dict:
        """Remove the service-scoped chaos profile registered for *host*."""
        body = json.dumps({"host": host, "remove": True})
        status, response_body = await self._request("PUT", "/mockserver/serviceChaos", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to remove service chaos (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def clear_service_chaos(self) -> dict:
        """Clear all service-scoped chaos profiles."""
        body = json.dumps({"clear": True})
        status, response_body = await self._request("PUT", "/mockserver/serviceChaos", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to clear service chaos (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def service_chaos_status(self) -> dict:
        """Query the current service-scoped chaos registrations.

        Returns a dict of the form ``{"services": {host: profile, ...}}``.
        """
        status, response_body = await self._request("GET", "/mockserver/serviceChaos")
        if status >= 400:
            raise MockServerError(
                f"Failed to get service chaos (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def verify_slo(self, criteria: dict) -> dict:
        """Verify a service-level objective over a window of recorded SLI samples.

        *criteria* is the SLO criteria dict (``name``, ``window``,
        ``minimumSampleCount``, ``upstreamHosts``, ``objectives`` —
        ``[{sli, comparator, threshold, scope}]``).

        Returns the verdict dict with a ``result`` of ``PASS`` or ``INCONCLUSIVE``
        (HTTP 200). A ``FAIL`` verdict (HTTP 406) raises
        :class:`MockServerVerificationError` so a CI or chaos gate can assert on it
        directly. SLO tracking is off by default — the server returns 403 (raised as
        :class:`MockServerError`) until started with ``sloTrackingEnabled=true``;
        malformed criteria are a separate 400.
        """
        body = json.dumps(criteria)
        status, response_body = await self._request("PUT", "/mockserver/verifySLO", body)
        if status == 406:
            raise MockServerVerificationError(response_body or "SLO verdict: FAIL")
        if status == 403:
            raise MockServerError(
                "SLO tracking is disabled — start MockServer with "
                f"sloTrackingEnabled=true: {response_body}"
            )
        if status == 400:
            raise MockServerError(f"Invalid SLO criteria: {response_body}")
        if status >= 400:
            raise MockServerError(
                f"Failed to verify SLO (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def start_chaos_experiment(self, experiment: dict) -> dict:
        """Start a scheduled multi-stage chaos experiment.

        The *experiment* is an ordered sequence of stages, each applying
        service-scoped chaos profiles to one or more hosts for a duration; stages
        progress automatically. Only one experiment may be active at a time;
        starting a new one stops the previous one.

        *experiment* is the definition dict (``name``, ``loop``, ``stages`` —
        ``[{durationMillis, profiles{host: profile}}]``). Returns the started status
        (``{"status": "started", "name": ...}``).

        Raises :class:`MockServerError` if the definition is invalid (HTTP 400) or
        control-plane authorization rejects the request (HTTP 403); there is no
        server setting that disables chaos experiments.
        """
        body = json.dumps(experiment)
        status, response_body = await self._request(
            "PUT", "/mockserver/chaosExperiment", body
        )
        if status == 403:
            raise MockServerError(
                "Failed to start chaos experiment: control-plane authorization denied "
                f"(status=403): {response_body}"
            )
        if status >= 400:
            raise MockServerError(
                f"Failed to start chaos experiment (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    @staticmethod
    def _load_generation_disabled_error(action: str, response_body: str) -> MockServerError:
        return MockServerError(
            f"Failed to {action}: load generation is disabled "
            "(start MockServer with loadGenerationEnabled to enable it) "
            f"(status=403): {response_body}"
        )

    async def load_scenario(self, scenario: LoadScenario | dict) -> dict:
        """Register (load) a load-injection scenario in the server-side registry.

        *scenario* is a :class:`LoadScenario` (or an equivalent dict) and must
        carry a unique ``name``. Registration adds the scenario in the ``LOADED``
        state but does **not** start generating load; it is allowed even when the
        server was started without ``loadGenerationEnabled``.

        Returns the JSON ``{"name": ..., "state": ...}`` for the registered
        scenario. Use :meth:`start_load_scenarios` to begin generating load.
        """
        payload = scenario.to_dict() if hasattr(scenario, "to_dict") else scenario
        body = json.dumps(payload)
        status, response_body = await self._request(
            "PUT", "/mockserver/loadScenario", body
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to register load scenario (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def load_scenarios(self) -> dict:
        """List every registered load scenario.

        Returns ``{"scenarios": [{"name", "state", "definition", "status"?}, ...]}``.
        """
        status, response_body = await self._request("GET", "/mockserver/loadScenario")
        if status >= 400:
            raise MockServerError(
                f"Failed to list load scenarios (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def get_load_scenario(self, name: str) -> dict:
        """Retrieve a single registered load scenario by *name*.

        Raises :class:`MockServerError` if the scenario does not exist (``404``).
        """
        status, response_body = await self._request(
            "GET", f"/mockserver/loadScenario/{urllib.parse.quote(name, safe='')}"
        )
        if status == 404:
            raise MockServerError(
                f"Load scenario '{name}' not found (status=404): {response_body}"
            )
        if status >= 400:
            raise MockServerError(
                f"Failed to get load scenario '{name}' (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def delete_load_scenario(self, name: str) -> dict:
        """Remove a single registered load scenario by *name* (stops it if running)."""
        status, response_body = await self._request(
            "DELETE", f"/mockserver/loadScenario/{urllib.parse.quote(name, safe='')}"
        )
        if status == 404:
            raise MockServerError(
                f"Load scenario '{name}' not found (status=404): {response_body}"
            )
        if status >= 400:
            raise MockServerError(
                f"Failed to delete load scenario '{name}' (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def clear_load_scenarios(self) -> dict:
        """Remove every registered load scenario (stopping any that are running)."""
        status, response_body = await self._request(
            "DELETE", "/mockserver/loadScenario"
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to clear load scenarios (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def start_load_scenarios(self, names: str | list[str]) -> dict:
        """Start one or more registered load scenarios.

        *names* may be a single scenario name or a list of names. The server must
        have been started with ``loadGenerationEnabled``; otherwise this raises
        :class:`MockServerError` reporting the ``403`` response. Honours each
        scenario's ``startDelayMillis``.

        Returns ``{"started": [{"name", "state"}, ...], "status": ...}``.
        """
        if isinstance(names, str):
            names = [names]
        body = json.dumps({"names": list(names)})
        status, response_body = await self._request(
            "PUT", "/mockserver/loadScenario/start", body
        )
        if status == 403:
            raise self._load_generation_disabled_error(
                "start load scenarios", response_body
            )
        if status == 404:
            raise MockServerError(
                f"Unknown load scenario (status=404): {response_body}"
            )
        if status >= 400:
            raise MockServerError(
                f"Failed to start load scenarios (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def _scenario_request(
        self, method: str, path: str, body: str | None = None
    ) -> dict:
        status, response_body = await self._request(method, path, body)
        if status >= 400:
            raise MockServerError(
                f"Scenario request failed (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    def scenario(self, name: str) -> AsyncScenarioHandle:
        """Return a handle to the named stateful scenario, wrapping the
        ``/mockserver/scenario/{name}`` control-plane endpoints.
        """
        return AsyncScenarioHandle(self, name)

    async def scenarios(self) -> list[dict]:
        """List every known scenario and its current state.

        Returns a list of dicts each with ``scenarioName`` and ``currentState``.
        """
        result = await self._scenario_request("GET", "/mockserver/scenario")
        return result.get("scenarios", [])

    async def stop_load_scenarios(self, names: str | list[str] | None = None) -> dict:
        """Stop running load scenarios.

        *names* may be a single name, a list of names, or ``None`` to stop every
        running scenario.

        Returns ``{"stopped": [...], "status": ...}``.
        """
        if names is None:
            payload: dict = {}
        elif isinstance(names, str):
            payload = {"names": [names]}
        else:
            payload = {"names": list(names)}
        body = json.dumps(payload)
        status, response_body = await self._request(
            "PUT", "/mockserver/loadScenario/stop", body
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to stop load scenarios (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def run_load_scenario(self, scenario: LoadScenario | dict) -> dict:
        """Convenience: register *scenario* then immediately start it.

        Requires the server to have been started with ``loadGenerationEnabled``
        (the start step responds ``403`` otherwise). Returns the start result
        (``{"started": [...], "status": ...}``).
        """
        registered = await self.load_scenario(scenario)
        name = registered.get("name") if isinstance(registered, dict) else None
        if not name:
            payload = scenario.to_dict() if hasattr(scenario, "to_dict") else scenario
            name = payload.get("name") if isinstance(payload, dict) else None
        if not name:
            raise MockServerError(
                "Cannot run load scenario: scenario has no name to start"
            )
        return await self.start_load_scenarios(name)

    async def get_load_scenario_report(
        self, name: str, format: str | None = None
    ) -> dict | str:
        """Fetch the end-of-run summary report for a load scenario run.

        Returns the JSON report (a dict with counts, latency percentiles, the
        threshold ``verdict`` and per-threshold ``thresholdResults``) by default.
        Pass ``format="junit"`` to instead receive the JUnit-XML ``<testsuite>``
        document as a string (so a load run becomes a first-class CI artifact).

        Raises :class:`MockServerError` if the scenario never ran (``404``).
        """
        query_params = {"format": format} if format else None
        status, response_body = await self._request(
            "GET",
            f"/mockserver/loadScenario/{urllib.parse.quote(name, safe='')}/report",
            query_params=query_params,
        )
        if status == 404:
            raise MockServerError(
                f"No load scenario run for '{name}' (status=404): {response_body}"
            )
        if status >= 400:
            raise MockServerError(
                f"Failed to get load scenario report for '{name}' "
                f"(status={status}): {response_body}"
            )
        if format == "junit":
            return response_body
        return json.loads(response_body) if response_body else {}

    async def generate_load_scenario_from_openapi(
        self,
        name: str | None = None,
        spec_url_or_payload: str | None = None,
        *,
        target: dict | None = None,
        profile: LoadProfile | dict | None = None,
        body: dict | None = None,
    ) -> dict:
        """Seed a load scenario from an OpenAPI specification.

        Generates an editable scenario (one step per OpenAPI operation) and loads
        (registers) it in the ``LOADED`` state — it generates no traffic and is
        allowed even when ``loadGenerationEnabled`` is off. *spec_url_or_payload*
        is the spec as an inline JSON/YAML payload, a URL, or a file/classpath
        reference. *target* is an optional ``{host, port, scheme}`` override and
        *profile* an optional :class:`LoadProfile` (a conservative default is
        applied when omitted). Pass a fully-formed *body* dict to override the
        individual arguments.

        Returns ``{"status": "loaded", "name", "state", "scenario"}``.
        """
        if body is None:
            body = {"name": name, "specUrlOrPayload": spec_url_or_payload}
            if target is not None:
                body["target"] = target
            if profile is not None:
                body["profile"] = (
                    profile.to_dict() if hasattr(profile, "to_dict") else profile
                )
        status, response_body = await self._request(
            "PUT", "/mockserver/loadScenario/generateFromOpenAPI", json.dumps(body)
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to generate load scenario from OpenAPI "
                f"(status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def generate_load_scenario_from_recording(
        self,
        name: str | None = None,
        *,
        mode: str | None = None,
        request_filter: HttpRequest | dict | None = None,
        target: dict | None = None,
        max_steps: int | None = None,
        profile: LoadProfile | dict | None = None,
        body: dict | None = None,
    ) -> dict:
        """Seed a load scenario from recorded proxy traffic.

        Generates an editable scenario from requests held by the event log and
        loads (registers) it in the ``LOADED`` state — it generates no traffic
        and is allowed even when ``loadGenerationEnabled`` is off. *mode* is
        ``VERBATIM`` (default, one step per recorded request) or ``TEMPLATIZED``
        (one step per unique route, by descending frequency). *request_filter*
        (an :class:`HttpRequest` matcher) selects which recorded requests to
        include; *max_steps* caps VERBATIM steps; *target* (``{host, port,
        scheme}``) overrides each request's routing; *profile* overrides the
        conservative default. Pass a fully-formed *body* dict to override the
        individual arguments.

        Returns ``{"status": "loaded", "name", "state", "scenario"}``.
        """
        if body is None:
            body = {"name": name}
            if mode is not None:
                body["mode"] = mode
            if request_filter is not None:
                body["requestFilter"] = (
                    request_filter.to_dict()
                    if hasattr(request_filter, "to_dict")
                    else request_filter
                )
            if max_steps is not None:
                body["maxSteps"] = max_steps
            if target is not None:
                body["target"] = target
            if profile is not None:
                body["profile"] = (
                    profile.to_dict() if hasattr(profile, "to_dict") else profile
                )
        status, response_body = await self._request(
            "PUT", "/mockserver/loadScenario/generateFromRecording", json.dumps(body)
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to generate load scenario from recording "
                f"(status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def verify(
        self,
        request: HttpRequest | ConditionalRequestDefinition | AnyOfRequestDefinition | None = None,
        times: VerificationTimes | None = None,
        *,
        response: HttpResponse | None = None,
    ) -> None:
        verification = Verification(http_request=request, http_response=response, times=times)
        body = json.dumps(verification.to_dict())
        status, response_body = await self._request("PUT", "/mockserver/verify", body)
        if status == 406:
            raise MockServerVerificationError(response_body)
        if status >= 400:
            raise MockServerError(
                f"Failed to verify (status={status}): {response_body}"
            )

    async def verify_sequence(
        self,
        *requests: HttpRequest | ConditionalRequestDefinition | AnyOfRequestDefinition,
        responses: list[HttpResponse] | None = None,
    ) -> None:
        verification = VerificationSequence(
            http_requests=list(requests) if requests else None,
            http_responses=responses,
        )
        body = json.dumps(verification.to_dict())
        status, response_body = await self._request(
            "PUT", "/mockserver/verifySequence", body
        )
        if status == 406:
            raise MockServerVerificationError(response_body)
        if status >= 400:
            raise MockServerError(
                f"Failed to verify sequence (status={status}): {response_body}"
            )

    async def verify_zero_interactions(self) -> None:
        await self.verify(
            HttpRequest(),
            VerificationTimes(at_most=0),
        )

    async def retrieve_recorded_requests(
        self, request: HttpRequest | DnsRequestDefinition | None = None
    ) -> list[HttpRequest | DnsRequestDefinition | dict]:
        """Retrieve the recorded requests matching ``request`` (all HTTP requests when
        omitted). With a DNS filter the DNS queries come back as
        :class:`DnsRequestDefinition`; a binary message comes back as its raw dict.
        """
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "REQUESTS", "format": "JSON"},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve recorded requests (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [_deserialize_recorded_request(r) for r in parsed]
        return []

    async def retrieve_active_expectations(
        self,
        request: HttpRequest | None = None,
        expectation_filter: ExpectationFilter | None = None,
    ) -> list[Expectation]:
        """Retrieve the active expectations matching ``request`` (all when omitted)
        and, when given, ``expectation_filter`` (metadata, unmatched, offset, limit)."""
        query_params = {"type": "ACTIVE_EXPECTATIONS", "format": "JSON"}
        if expectation_filter is not None:
            if not isinstance(expectation_filter, ExpectationFilter):
                raise ValueError("expectation_filter must be an ExpectationFilter")
            query_params.update(expectation_filter.to_query_params())
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            query_params,
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve active expectations (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return []

    async def retrieve_active_expectations_by_metadata(
        self, key: str, value: Any
    ) -> list[Expectation]:
        """Retrieve the active expectations whose metadata has ``key`` with a value
        equal to ``value``: a ``str`` is compared as text, any other value as JSON."""
        return await self.retrieve_active_expectations(
            None, ExpectationFilter().with_metadata(key, value)
        )

    async def retrieve_active_expectations_by_metadata_key(
        self, key: str
    ) -> list[Expectation]:
        """Retrieve the active expectations whose metadata has ``key``, whatever its value."""
        return await self.retrieve_active_expectations(
            None, ExpectationFilter().with_metadata_key(key)
        )

    async def retrieve_unmatched_expectations(self) -> list[Expectation]:
        """Retrieve the active expectations that have not matched any request."""
        return await self.retrieve_active_expectations(
            None, ExpectationFilter().with_unmatched(True)
        )

    async def retrieve_recorded_expectations(
        self,
        request: HttpRequest | None = None,
        offset: int | None = None,
        limit: int | None = None,
    ) -> list[Expectation]:
        """Retrieve the recorded expectations matching ``request`` (all when omitted),
        optionally one page of them, in the order the requests were received."""
        query_params = {"type": "RECORDED_EXPECTATIONS", "format": "JSON"}
        query_params.update(
            ExpectationFilter().with_offset(offset).with_limit(limit).to_query_params()
        )
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            query_params,
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve recorded expectations (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return []

    async def _retrieve_by_id(
        self, expectation_id: str, query_params: dict[str, str]
    ) -> tuple[int, str]:
        """Send a retrieve request filtered by expectation id.

        An expectation id may be sent in place of a request matcher. For
        ``ACTIVE_EXPECTATIONS`` the server returns only the expectation with that
        id; for every other type it returns the entries matching that
        expectation's request, exactly as verify by expectation id matches them.
        An unknown id is rejected with a 400.
        """
        return await self._request(
            "PUT",
            "/mockserver/retrieve",
            json.dumps({"id": expectation_id}),
            query_params,
        )

    async def retrieve_recorded_requests_by_id(
        self, expectation_id: str
    ) -> list[HttpRequest | DnsRequestDefinition | dict]:
        status, response_body = await self._retrieve_by_id(
            expectation_id, {"type": "REQUESTS", "format": "JSON"}
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve recorded requests by id (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [_deserialize_recorded_request(r) for r in parsed]
        return []

    async def retrieve_active_expectations_by_id(
        self, expectation_id: str
    ) -> list[Expectation]:
        status, response_body = await self._retrieve_by_id(
            expectation_id, {"type": "ACTIVE_EXPECTATIONS", "format": "JSON"}
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve active expectations by id (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return []

    async def retrieve_recorded_expectations_by_id(
        self, expectation_id: str
    ) -> list[Expectation]:
        status, response_body = await self._retrieve_by_id(
            expectation_id, {"type": "RECORDED_EXPECTATIONS", "format": "JSON"}
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve recorded expectations by id (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [Expectation.from_dict(e) for e in parsed]
        return []

    async def retrieve_recorded_requests_and_responses_by_id(
        self, expectation_id: str
    ) -> list[HttpRequestAndHttpResponse]:
        status, response_body = await self._retrieve_by_id(
            expectation_id, {"type": "REQUEST_RESPONSES", "format": "JSON"}
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve request/responses by id (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [HttpRequestAndHttpResponse.from_dict(rr) for rr in parsed]
        return []

    async def retrieve_log_messages_by_id(self, expectation_id: str) -> list[str]:
        status, response_body = await self._retrieve_by_id(
            expectation_id, {"type": "LOGS"}
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve log messages by id (status={status}): {response_body}"
            )
        if response_body:
            try:
                parsed = json.loads(response_body)
                if isinstance(parsed, list):
                    return parsed
            except json.JSONDecodeError:
                return response_body.split("------------------------------------\n")
        return []

    async def _diagnostic(
        self, path: str, body: str | None, query_params: dict[str, str] | None, failure: str
    ) -> dict:
        status, response_body = await self._request("PUT", path, body, query_params)
        try:
            parsed = json.loads(response_body) if response_body else {}
        except json.JSONDecodeError:
            parsed = None
        if status != 200 or not isinstance(parsed, dict):
            reason = parsed.get("error") if isinstance(parsed, dict) else None
            raise MockServerError(f"{failure} (status={status}): {reason or response_body}")
        return parsed

    async def explain(self, correlation_id: str) -> RequestExplanation:
        """Explain how a recorded request would be served now.

        Issues ``PUT /mockserver/explain?correlationId=<id>``, which re-checks the
        request with that correlation id against the current expectations, in the
        order MockServer evaluates them, and returns the expectation that would
        serve it, why each expectation ahead of it was passed over, and which
        lower-priority expectations it shadows. Read-only: it consumes no Times
        and moves no scenario.

        Raises :class:`ValueError` for a blank id, and :class:`MockServerError`
        with the server's reason when no request is recorded with this id (never
        received, cleared or evicted) or it is not an HTTP request.
        """
        if not correlation_id or not correlation_id.strip():
            raise ValueError("explain requires a non blank correlation_id")
        data = await self._diagnostic(
            "/mockserver/explain", None, {"correlationId": correlation_id},
            f'explain failed for correlationId "{correlation_id}"',
        )
        return RequestExplanation.from_dict(data)

    async def debug_mismatch(self, request: HttpRequest | dict) -> DebugMismatchResult:
        """Compare a request with every active expectation.

        Issues ``PUT /mockserver/debugMismatch`` and returns, for each expectation,
        whether it matches and the per-field differences, plus the closest match.
        Only an HTTP request is supported; any other definition raises
        :class:`MockServerError`.
        """
        payload = request.to_dict() if hasattr(request, "to_dict") else request
        data = await self._diagnostic(
            "/mockserver/debugMismatch", json.dumps(payload), None, "debugMismatch failed"
        )
        return DebugMismatchResult.from_dict(data)

    async def explain_unmatched(
        self, limit: int | None = None, suggest_fixes: bool | None = None
    ) -> ExplainUnmatchedResult:
        """Explain why recent requests matched no expectation.

        Issues ``PUT /mockserver/explainUnmatched`` and returns each recent
        unmatched request with its closest expectations, closest first. ``limit``
        bounds how many requests are analysed (server default 10);
        ``suggest_fixes`` adds a suggested JSON patch to each of the five closest
        non-matching expectations (nothing is applied).
        """
        options: dict[str, Any] = {}
        if limit is not None:
            options["limit"] = limit
        if suggest_fixes is not None:
            options["suggestFixes"] = suggest_fixes
        data = await self._diagnostic(
            "/mockserver/explainUnmatched", json.dumps(options) if options else None, None,
            "explainUnmatched failed",
        )
        return ExplainUnmatchedResult.from_dict(data)

    async def retrieve_expectations_as_code(
        self, fmt: str = "java", request: HttpRequest | None = None
    ) -> str:
        """Retrieve the active expectations as MockServer SDK setup code.

        Returns the builder code that recreates the expectations, generated in
        the requested language (``fmt`` is one of ``java``, ``javascript``,
        ``python``, ``go``, ``csharp``, ``ruby``, ``rust`` or ``php``).
        """
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "ACTIVE_EXPECTATIONS", "format": fmt.upper()},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve expectations as code (status={status}): {response_body}"
            )
        return response_body or ""

    async def retrieve_recorded_expectations_as_code(
        self, fmt: str = "java", request: HttpRequest | None = None
    ) -> str:
        """Retrieve the recorded (proxied) request/response pairs as MockServer
        SDK setup code, generated in the requested language (``fmt`` is one of
        ``java``, ``javascript``, ``python``, ``go``, ``csharp``, ``ruby``,
        ``rust`` or ``php``).
        """
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "RECORDED_EXPECTATIONS", "format": fmt.upper()},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve recorded expectations as code (status={status}): {response_body}"
            )
        return response_body or ""

    async def retrieve_recorded_requests_and_responses(
        self, request: HttpRequest | None = None
    ) -> list[HttpRequestAndHttpResponse]:
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "REQUEST_RESPONSES", "format": "JSON"},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve request/responses (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            if isinstance(parsed, list):
                return [HttpRequestAndHttpResponse.from_dict(rr) for rr in parsed]
        return []

    async def retrieve_log_messages(
        self, request: HttpRequest | None = None
    ) -> list[str]:
        body = json.dumps(request.to_dict()) if request else ""
        status, response_body = await self._request(
            "PUT",
            "/mockserver/retrieve",
            body,
            {"type": "LOGS"},
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to retrieve log messages (status={status}): {response_body}"
            )
        if response_body:
            try:
                parsed = json.loads(response_body)
                if isinstance(parsed, list):
                    return parsed
            except json.JSONDecodeError:
                return response_body.split("------------------------------------\n")
        return []

    async def bind(self, *ports: int) -> list[int]:
        body = json.dumps(Ports(ports=list(ports)).to_dict())
        status, response_body = await self._request("PUT", "/mockserver/bind", body)
        if status >= 400:
            raise MockServerError(
                f"Failed to bind ports (status={status}): {response_body}"
            )
        if response_body:
            parsed = json.loads(response_body)
            return Ports.from_dict(parsed).ports
        return []

    async def stop(self) -> None:
        try:
            await self._request("PUT", "/mockserver/stop")
        except MockServerConnectionError:
            pass

    async def has_started(self, attempts: int = 10, timeout: float = 0.5) -> bool:
        for i in range(attempts):
            try:
                status, _ = await self._request("PUT", "/mockserver/status")
                if status == 200:
                    return True
            except MockServerConnectionError:
                pass
            if i < attempts - 1:
                await asyncio.sleep(timeout)
        return False

    def when(
        self,
        request: HttpRequest | ConditionalRequestDefinition | AnyOfRequestDefinition,
        times: Times | None = None,
        time_to_live: TimeToLive | None = None,
        priority: int | None = None,
    ) -> ForwardChainExpectation:
        expectation = Expectation(
            http_request=request,
            times=times,
            time_to_live=time_to_live,
            priority=priority,
        )
        return ForwardChainExpectation(self, expectation)

    async def mock_with_callback(
        self,
        request: HttpRequest,
        callback: Callable,
        times: Times | None = None,
        time_to_live: TimeToLive | None = None,
    ) -> list[Expectation]:
        client_id = await self._register_websocket_callback("response", callback)
        expectation = Expectation(
            http_request=request,
            http_response_object_callback=HttpObjectCallback(client_id=client_id),
            times=times,
            time_to_live=time_to_live,
        )
        return await self.upsert(expectation)

    async def mock_with_forward_callback(
        self,
        request: HttpRequest,
        forward_callback: Callable,
        response_callback: Callable | None = None,
        times: Times | None = None,
        time_to_live: TimeToLive | None = None,
    ) -> list[Expectation]:
        client_id = await self._register_websocket_callback(
            "forward", forward_callback, response_callback
        )
        obj_callback = HttpObjectCallback(client_id=client_id)
        if response_callback is not None:
            obj_callback.response_callback = True
        expectation = Expectation(
            http_request=request,
            http_forward_object_callback=obj_callback,
            times=times,
            time_to_live=time_to_live,
        )
        return await self.upsert(expectation)

    # -------------------------------------------------------------------
    # Breakpoint matcher management
    # -------------------------------------------------------------------

    async def _ensure_breakpoint_websocket(self) -> MockServerWebSocketClient:
        """Ensure a callback WS is connected for breakpoint use, returning it."""
        # Serialise lazy creation so concurrent add_breakpoint coroutines
        # (e.g. via asyncio.gather) cannot each create a duplicate breakpoint WS
        # across the await in connect(). Creating/assigning the lock has no await
        # between the check and the set, so it is atomic on the event loop.
        lock = getattr(self, '_breakpoint_ws_lock', None)
        if lock is None:
            lock = asyncio.Lock()
            self._breakpoint_ws_lock = lock  # type: ignore[attr-defined]
        async with lock:
            # Reuse the first WS client tagged as breakpoint, or create one
            for ws in self._websocket_clients:
                if getattr(ws, '_is_breakpoint_ws', False):
                    return ws

            ws_client = MockServerWebSocketClient()
            await ws_client.connect(
                self._host,
                self._port,
                self._context_path,
                self._secure,
                self._ca_cert_path,
                tls_verify=self._tls_verify,
                bearer_token_supplier=self._control_plane_bearer,
                ssl_context=self._ssl_context,
            )
            ws_client._is_breakpoint_ws = True  # type: ignore[attr-defined]
            ws_client._listen_task = asyncio.create_task(ws_client.listen())
            self._websocket_clients.append(ws_client)
            return ws_client

    async def add_breakpoint(
        self,
        matcher: HttpRequest,
        phases: list[str],
        request_handler: Callable | None = None,
        response_handler: Callable | None = None,
        stream_frame_handler: Callable | None = None,
    ) -> str:
        """Register a breakpoint matcher with callback handlers.

        The callback WebSocket is opened lazily on the first call and reused.

        Args:
            matcher: the request definition to match (same as expectation matcher)
            phases: list of phase strings: "REQUEST", "RESPONSE",
                    "RESPONSE_STREAM", "INBOUND_STREAM"
            request_handler: callable(HttpRequest) -> HttpRequest|HttpResponse
                             for REQUEST phase (optional)
            response_handler: callable(HttpRequest, HttpResponse) -> HttpResponse
                              for RESPONSE phase (optional)
            stream_frame_handler: callable(dict) -> dict for streaming phases
                                  (optional). Receives PausedStreamFrameDTO dict,
                                  returns StreamFrameDecisionDTO dict.

        Returns:
            The server-assigned breakpoint matcher id (UUID string).
        """
        if matcher is None:
            raise ValueError("add_breakpoint requires a non-None matcher")
        if not phases:
            raise ValueError("add_breakpoint requires a non-empty phases list")

        ws_client = await self._ensure_breakpoint_websocket()
        client_id = ws_client.client_id

        body = json.dumps({
            "httpRequest": matcher.to_dict(),
            "phases": phases,
            "clientId": client_id,
        })
        status, response_body = await self._request(
            "PUT", "/mockserver/breakpoint/matcher", body
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to register breakpoint matcher (status={status}): {response_body}"
            )

        parsed = json.loads(response_body) if response_body else {}
        breakpoint_id = parsed.get("id")
        if not breakpoint_id:
            raise MockServerError("Server did not return a breakpoint id")

        # Install per-breakpoint-id handlers
        if request_handler:
            ws_client.set_breakpoint_request_handler(breakpoint_id, request_handler)
        if response_handler:
            ws_client.set_breakpoint_response_handler(breakpoint_id, response_handler)
        if stream_frame_handler:
            ws_client.set_breakpoint_stream_frame_handler(breakpoint_id, stream_frame_handler)

        return breakpoint_id

    async def add_request_breakpoint(
        self,
        matcher: HttpRequest,
        request_handler: Callable,
    ) -> str:
        """Convenience: register a REQUEST-only breakpoint."""
        return await self.add_breakpoint(
            matcher, ["REQUEST"], request_handler=request_handler
        )

    async def add_request_and_response_breakpoint(
        self,
        matcher: HttpRequest,
        request_handler: Callable,
        response_handler: Callable,
    ) -> str:
        """Convenience: register a REQUEST+RESPONSE breakpoint."""
        return await self.add_breakpoint(
            matcher,
            ["REQUEST", "RESPONSE"],
            request_handler=request_handler,
            response_handler=response_handler,
        )

    async def list_breakpoint_matchers(self) -> dict:
        """List all registered breakpoint matchers.

        Returns a dict: {"matchers": [{id, httpRequest, phases, clientId}, ...]}.
        """
        status, response_body = await self._request(
            "GET", "/mockserver/breakpoint/matchers"
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to list breakpoint matchers (status={status}): {response_body}"
            )
        return json.loads(response_body) if response_body else {}

    async def remove_breakpoint_matcher(self, breakpoint_id: str) -> dict:
        """Remove a breakpoint matcher by id.

        Returns a dict: {"status": "removed", "id": "..."} or raises on 404.
        """
        if not breakpoint_id:
            raise ValueError("remove_breakpoint_matcher requires a non-empty id")
        body = json.dumps({"id": breakpoint_id})
        status, response_body = await self._request(
            "PUT", "/mockserver/breakpoint/matcher/remove", body
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to remove breakpoint matcher (status={status}): {response_body}"
            )
        # Remove client-side handlers
        for ws in self._websocket_clients:
            if getattr(ws, '_is_breakpoint_ws', False):
                ws.remove_breakpoint_handlers(breakpoint_id)
        return json.loads(response_body) if response_body else {}

    async def clear_breakpoint_matchers(self) -> dict:
        """Clear all registered breakpoint matchers.

        Returns a dict: {"status": "cleared", "count": N}.
        """
        status, response_body = await self._request(
            "PUT", "/mockserver/breakpoint/matcher/clear"
        )
        if status >= 400:
            raise MockServerError(
                f"Failed to clear breakpoint matchers (status={status}): {response_body}"
            )
        # Clear client-side handlers
        for ws in self._websocket_clients:
            if getattr(ws, '_is_breakpoint_ws', False):
                ws.clear_breakpoint_handlers()
        return json.loads(response_body) if response_body else {}

    async def _register_websocket_callback(
        self,
        callback_type: str,
        callback_fn: Callable,
        forward_response_fn: Callable | None = None,
    ) -> str:
        ws_client = MockServerWebSocketClient()
        client_id = await ws_client.connect(
            self._host,
            self._port,
            self._context_path,
            self._secure,
            self._ca_cert_path,
            tls_verify=self._tls_verify,
            bearer_token_supplier=self._control_plane_bearer,
            ssl_context=self._ssl_context,
        )

        if callback_type == "response":
            ws_client.register_response_callback(callback_fn)
        elif callback_type == "forward":
            ws_client.register_forward_callback(callback_fn, forward_response_fn)

        ws_client._listen_task = asyncio.create_task(ws_client.listen())
        self._websocket_clients.append(ws_client)
        return client_id

    async def close(self) -> None:
        for ws_client in self._websocket_clients:
            await ws_client.close()
        self._websocket_clients.clear()

    async def __aenter__(self) -> AsyncMockServerClient:
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        await self.close()
