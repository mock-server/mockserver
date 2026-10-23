from __future__ import annotations

import asyncio
import threading
from typing import Any, Callable

from mockserver.async_client import NO_FILTER, AsyncMockServerClient
from mockserver.fluent import _UNSET
from mockserver.models import (
    AnyOfRequestDefinition,
    ConditionalRequestDefinition,
    DebugMismatchResult,
    Delay,
    DnsRequestDefinition,
    Expectation,
    ExpectationFilter,
    ExplainUnmatchedResult,
    HttpChaosProfile,
    HttpError,
    HttpForward,
    HttpOverrideForwardedRequest,
    HttpRequest,
    HttpRequestAndHttpResponse,
    HttpResponse,
    HttpTemplate,
    LoadScenario,
    OpenAPIExpectation,
    RecordedExpectationsOptions,
    RequestExplanation,
    TimeToLive,
    Times,
    VerificationTimes,
)


class SyncForwardChainExpectation:
    def __init__(self, async_chain, run_fn: Callable) -> None:
        self._async_chain = async_chain
        self._run = run_fn

    def with_id(self, id: str) -> SyncForwardChainExpectation:
        self._async_chain.with_id(id)
        return self

    def with_priority(self, priority: int) -> SyncForwardChainExpectation:
        self._async_chain.with_priority(priority)
        return self

    def with_metadata(self, key_or_metadata, value=_UNSET) -> SyncForwardChainExpectation:
        self._async_chain.with_metadata(key_or_metadata, value)
        return self

    def with_chaos(self, chaos) -> SyncForwardChainExpectation:
        self._async_chain.with_chaos(chaos)
        return self

    def with_steps(self, steps) -> list[Expectation]:
        return self._run(self._async_chain.with_steps(steps))

    def respond(self, response_or_callback) -> list[Expectation]:
        return self._run(self._async_chain.respond(response_or_callback))

    def respond_with_delay(
        self, response: HttpResponse, delay: Delay
    ) -> list[Expectation]:
        return self._run(self._async_chain.respond_with_delay(response, delay))

    def forward(
        self, forward_or_callback, response_callback: Callable | None = None
    ) -> list[Expectation]:
        return self._run(
            self._async_chain.forward(forward_or_callback, response_callback)
        )

    def forward_with_delay(
        self, forward: HttpForward, delay: Delay
    ) -> list[Expectation]:
        return self._run(self._async_chain.forward_with_delay(forward, delay))

    def respond_with_sse(self, sse_response) -> list[Expectation]:
        return self._run(self._async_chain.respond_with_sse(sse_response))

    def respond_with_websocket(self, websocket_response) -> list[Expectation]:
        return self._run(self._async_chain.respond_with_websocket(websocket_response))

    def respond_with_grpc_stream(self, grpc_stream_response) -> list[Expectation]:
        return self._run(self._async_chain.respond_with_grpc_stream(grpc_stream_response))

    def respond_with_grpc_bidi(self, grpc_bidi_response) -> list[Expectation]:
        return self._run(self._async_chain.respond_with_grpc_bidi(grpc_bidi_response))

    def respond_with_binary(self, binary_response) -> list[Expectation]:
        return self._run(self._async_chain.respond_with_binary(binary_response))

    def respond_with_dns(self, dns_response) -> list[Expectation]:
        return self._run(self._async_chain.respond_with_dns(dns_response))

    def forward_with_template(self, template) -> list[Expectation]:
        return self._run(self._async_chain.forward_with_template(template))

    def respond_with_class_callback(self, class_callback) -> list[Expectation]:
        return self._run(self._async_chain.respond_with_class_callback(class_callback))

    def forward_with_class_callback(self, class_callback) -> list[Expectation]:
        return self._run(self._async_chain.forward_with_class_callback(class_callback))

    def error(self, error: HttpError) -> list[Expectation]:
        return self._run(self._async_chain.error(error))


class SyncScenarioHandle:
    """A synchronous handle to a single named stateful scenario on the server.

    Obtained via :meth:`MockServerClient.scenario`. Wraps the
    ``/mockserver/scenario/{name}`` control-plane endpoints.
    """

    def __init__(self, async_handle, run_fn: Callable) -> None:
        self._async_handle = async_handle
        self._run = run_fn

    @property
    def name(self) -> str:
        return self._async_handle.name

    def state(self) -> str | None:
        """Return the current state of this scenario (``None`` if not yet set)."""
        return self._run(self._async_handle.state())

    def set(
        self,
        state: str,
        transition_after_ms: int | None = None,
        next_state: str | None = None,
    ) -> dict:
        """Set this scenario's state, optionally scheduling a timed transition
        to ``next_state`` after ``transition_after_ms`` milliseconds.
        """
        return self._run(
            self._async_handle.set(state, transition_after_ms, next_state)
        )

    def trigger(self, new_state: str) -> dict:
        """Trigger an external advance of this scenario to ``new_state``."""
        return self._run(self._async_handle.trigger(new_state))


class MockServerClient:
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
        control_plane_basic_auth: tuple[str, str] | None = None,
    ) -> None:
        self._loop = asyncio.new_event_loop()
        self._thread = threading.Thread(
            target=self._loop.run_forever, daemon=True
        )
        self._thread.start()
        self._async_client = AsyncMockServerClient(
            host,
            port,
            context_path,
            secure,
            ca_cert_path,
            tls_verify=tls_verify,
            client_cert_path=client_cert_path,
            client_key_path=client_key_path,
            control_plane_bearer_token=control_plane_bearer_token,
            control_plane_bearer_token_supplier=control_plane_bearer_token_supplier,
            control_plane_basic_auth=control_plane_basic_auth,
        )

    def _run(self, coro):
        future = asyncio.run_coroutine_threadsafe(coro, self._loop)
        return future.result()

    def upsert(self, *expectations: Expectation) -> list[Expectation]:
        return self._run(self._async_client.upsert(*expectations))

    def open_api_expectation(self, expectation: OpenAPIExpectation) -> None:
        return self._run(self._async_client.open_api_expectation(expectation))

    def clear(
        self,
        request: HttpRequest | None = None,
        clear_type: str | None = None,
        expectation_filter: ExpectationFilter = NO_FILTER,  # type: ignore[assignment]
    ) -> None:
        return self._run(
            self._async_client.clear(request, clear_type, expectation_filter)
        )

    def clear_by_metadata(self, key: str, value: Any) -> None:
        return self._run(self._async_client.clear_by_metadata(key, value))

    def clear_by_metadata_key(self, key: str) -> None:
        return self._run(self._async_client.clear_by_metadata_key(key))

    def clear_unmatched_expectations(self) -> None:
        return self._run(self._async_client.clear_unmatched_expectations())

    def clear_by_id(
        self,
        expectation_id: str,
        clear_type: str | None = None,
    ) -> None:
        return self._run(self._async_client.clear_by_id(expectation_id, clear_type))

    def reset(self) -> None:
        return self._run(self._async_client.reset())

    def freeze_clock(self, instant: str | None = None) -> dict:
        """Freeze the server clock at the given ISO-8601 instant.

        If *instant* is ``None``, the clock freezes at the current real time.
        """
        return self._run(self._async_client.freeze_clock(instant))

    def advance_clock(self, duration_millis: int) -> dict:
        """Advance the frozen clock by *duration_millis* milliseconds."""
        return self._run(self._async_client.advance_clock(duration_millis))

    def reset_clock(self) -> dict:
        """Reset the server clock to real wall-clock time."""
        return self._run(self._async_client.reset_clock())

    def clock_status(self) -> dict:
        """Query the current clock status."""
        return self._run(self._async_client.clock_status())

    def retrieve_metrics(self) -> dict:
        """Retrieve the JSON metric-counter snapshot
        (``PUT /mockserver/retrieve?type=METRICS``).
        """
        return self._run(self._async_client.retrieve_metrics())

    def scrape_metrics(self) -> str:
        """Scrape the Prometheus metrics exposition text
        (``GET /mockserver/metrics``). Raises if metrics are disabled (``404``).
        """
        return self._run(self._async_client.scrape_metrics())

    def retrieve_configuration(self) -> str:
        """Read the effective live configuration JSON
        (``GET /mockserver/configuration``).
        """
        return self._run(self._async_client.retrieve_configuration())

    def update_configuration(self, config_json: str) -> str:
        """Update the live configuration (partial update) and return the updated
        configuration JSON (``PUT /mockserver/configuration``).

        Properties fixed at startup, such as ``forwardProxyBlockPrivateNetworks``,
        ``forwardProxyAllowedHosts`` and ``forwardProxyDeniedHosts``, are ignored
        and the server logs a warning.
        """
        return self._run(self._async_client.update_configuration(config_json))

    def retrieve_drift(self) -> dict:
        """Retrieve the recorded mock drift report
        (``GET /mockserver/drift``); returns a dict of the form
        ``{"count": <n>, "drifts": [ ... ]}``.
        """
        return self._run(self._async_client.retrieve_drift())

    def clear_drift(self) -> None:
        """Clear all recorded mock drift (``PUT /mockserver/drift/clear``)."""
        return self._run(self._async_client.clear_drift())

    def pact_import(self, pact_json: str) -> str:
        """Import a Pact v3 contract as expectations; returns the upserted
        expectations JSON (``PUT /mockserver/pact/import``).
        """
        return self._run(self._async_client.pact_import(pact_json))

    def pact_export(
        self, consumer: str | None = None, provider: str | None = None
    ) -> str:
        """Export the active expectations as a Pact v3 contract
        (``PUT /mockserver/pact``).
        """
        return self._run(self._async_client.pact_export(consumer, provider))

    def pact_verify(self, pact_json: str) -> str:
        """Verify a Pact v3 contract against the active expectations
        (``PUT /mockserver/pact/verify``).

        Returns the verification report body for both a pass (``202``) and a
        fail (``406``) so the caller can inspect it; only a genuinely bad request
        (e.g. ``400``) raises.
        """
        return self._run(self._async_client.pact_verify(pact_json))

    def store_file(self, name: str, content: str | bytes) -> dict:
        """Store a file in the in-memory file store
        (``PUT /mockserver/files/store``).

        ``str`` content is stored verbatim; ``bytes`` content is base64 encoded.
        """
        return self._run(self._async_client.store_file(name, content))

    def retrieve_file(self, name: str) -> str:
        """Retrieve a file's content from the in-memory file store
        (``PUT /mockserver/files/retrieve``).

        Returns the raw ``200`` body; an unknown file (``404``) raises
        :class:`MockServerError` with ``"file not found: <name>"``.
        """
        return self._run(self._async_client.retrieve_file(name))

    def list_files(self) -> list[str]:
        """List the names of files in the in-memory file store
        (``PUT /mockserver/files/list``).
        """
        return self._run(self._async_client.list_files())

    def delete_file(self, name: str) -> None:
        """Delete a file from the in-memory file store
        (``PUT /mockserver/files/delete``).
        """
        return self._run(self._async_client.delete_file(name))

    def import_har(self, har_json: str) -> list[Expectation]:
        """Import a HAR document as expectations
        (``PUT /mockserver/import?format=har``).
        """
        return self._run(self._async_client.import_har(har_json))

    def import_postman_collection(self, collection_json: str) -> list[Expectation]:
        """Import a Postman collection as expectations
        (``PUT /mockserver/import?format=postman``).
        """
        return self._run(
            self._async_client.import_postman_collection(collection_json)
        )

    def import_document(
        self, document_json: str, format: str | None = None
    ) -> list[Expectation]:
        """Import a HAR, Postman, Pact, WireMock, Mountebank or Mockoon document as
        expectations (``PUT /mockserver/import``).

        *format* selects the importer (``har``, ``postman``, ``pact``, ``wiremock``,
        ``mountebank``, ``mockoon``); omit it to let the server auto-detect from
        the JSON shape. Warnings the WireMock, Mountebank and Mockoon importers
        report are logged at ``WARNING``. ``format="recording"`` raises
        :class:`ValueError`; send ``PUT /mockserver/import?format=recording``
        directly to re-import recorded traffic.
        """
        return self._run(
            self._async_client.import_document(document_json, format)
        )

    def set_mode(self, mode: str) -> dict:
        """Set the high-level operating mode
        (``PUT /mockserver/mode?mode=<MODE>``).

        *mode* is one of :class:`~mockserver.models.MockMode` (``SIMULATE``,
        ``SPY``, ``CAPTURE``).
        """
        return self._run(self._async_client.set_mode(mode))

    def retrieve_mode(self) -> dict:
        """Read the current operating mode (``GET /mockserver/mode``)."""
        return self._run(self._async_client.retrieve_mode())

    def wsdl_expectation(self, wsdl: str) -> list[Expectation]:
        """Generate expectations from a WSDL document
        (``PUT /mockserver/wsdl``, raw WSDL XML body).
        """
        return self._run(self._async_client.wsdl_expectation(wsdl))

    def upload_grpc_descriptor(self, descriptor_set_bytes: bytes) -> None:
        """Upload a compiled protobuf descriptor set so gRPC requests can be matched.

        *descriptor_set_bytes* must be the raw bytes of a ``FileDescriptorSet``
        (e.g. the output of ``protoc --descriptor_set_out=... --include_imports``).
        """
        return self._run(
            self._async_client.upload_grpc_descriptor(descriptor_set_bytes)
        )

    def retrieve_grpc_services(self) -> list[dict]:
        """Retrieve the gRPC services registered from uploaded descriptor sets."""
        return self._run(self._async_client.retrieve_grpc_services())

    def clear_grpc_descriptors(self) -> None:
        """Clear all uploaded gRPC descriptor sets and registered services."""
        return self._run(self._async_client.clear_grpc_descriptors())

    def set_service_chaos(
        self, host: str, chaos: HttpChaosProfile, ttl_millis: int | None = None
    ) -> dict:
        """Register a service-scoped HTTP chaos profile for an upstream host.

        If *ttl_millis* is set, the chaos auto-reverts after that many milliseconds.
        """
        return self._run(self._async_client.set_service_chaos(host, chaos, ttl_millis))

    def remove_service_chaos(self, host: str) -> dict:
        """Remove the service-scoped chaos profile registered for *host*."""
        return self._run(self._async_client.remove_service_chaos(host))

    def clear_service_chaos(self) -> dict:
        """Clear all service-scoped chaos profiles."""
        return self._run(self._async_client.clear_service_chaos())

    def service_chaos_status(self) -> dict:
        """Query the current service-scoped chaos registrations."""
        return self._run(self._async_client.service_chaos_status())

    def verify_slo(self, criteria: dict) -> dict:
        """Verify a service-level objective over recorded SLI samples.

        Returns the verdict dict (``result`` PASS or INCONCLUSIVE). A FAIL verdict
        (HTTP 406) raises :class:`MockServerVerificationError`. SLO tracking is off
        by default (HTTP 403 until ``sloTrackingEnabled=true``); malformed criteria
        are a separate HTTP 400.
        """
        return self._run(self._async_client.verify_slo(criteria))

    def start_chaos_experiment(self, experiment: dict) -> dict:
        """Start a scheduled multi-stage chaos experiment.

        Only one experiment may be active at a time; starting a new one stops the
        previous one. Returns the started status (``{"status": "started", "name"}``).
        """
        return self._run(self._async_client.start_chaos_experiment(experiment))

    def load_scenario(self, scenario: LoadScenario | dict) -> dict:
        """Register (load) a load-injection scenario in the server-side registry.

        The scenario must carry a unique ``name``. Registration adds it in the
        ``LOADED`` state but does not start generating load; it is allowed even
        when the server was started without ``loadGenerationEnabled``. Use
        :meth:`start_load_scenarios` to begin generating load.
        """
        return self._run(self._async_client.load_scenario(scenario))

    def load_scenarios(self) -> dict:
        """List every registered load scenario."""
        return self._run(self._async_client.load_scenarios())

    def get_load_scenario(self, name: str) -> dict:
        """Retrieve a single registered load scenario by *name* (404 if absent)."""
        return self._run(self._async_client.get_load_scenario(name))

    def delete_load_scenario(self, name: str) -> dict:
        """Remove a single registered load scenario by *name* (stops it if running)."""
        return self._run(self._async_client.delete_load_scenario(name))

    def clear_load_scenarios(self) -> dict:
        """Remove every registered load scenario (stopping any that are running)."""
        return self._run(self._async_client.clear_load_scenarios())

    def start_load_scenarios(self, names: str | list[str]) -> dict:
        """Start one or more registered load scenarios by name.

        The server must have been started with ``loadGenerationEnabled``;
        otherwise this raises an error reporting the ``403`` response.
        """
        return self._run(self._async_client.start_load_scenarios(names))

    def stop_load_scenarios(self, names: str | list[str] | None = None) -> dict:
        """Stop running load scenarios (``None`` stops every running scenario)."""
        return self._run(self._async_client.stop_load_scenarios(names))

    def run_load_scenario(self, scenario: LoadScenario | dict) -> dict:
        """Convenience: register *scenario* then immediately start it."""
        return self._run(self._async_client.run_load_scenario(scenario))

    def get_load_scenario_report(
        self, name: str, format: str | None = None
    ) -> dict | str:
        """Fetch the end-of-run summary report for a load scenario run.

        Returns the JSON report by default; pass ``format="junit"`` to receive
        the JUnit-XML ``<testsuite>`` document as a string. Raises an error if
        the scenario never ran (``404``).
        """
        return self._run(
            self._async_client.get_load_scenario_report(name, format)
        )

    def generate_load_scenario_from_openapi(
        self,
        name: str | None = None,
        spec_url_or_payload: str | None = None,
        *,
        target: dict | None = None,
        profile: LoadProfile | dict | None = None,
        body: dict | None = None,
    ) -> dict:
        """Seed and register an editable load scenario from an OpenAPI spec.

        One step is produced per OpenAPI operation. *spec_url_or_payload* is the
        spec as an inline JSON/YAML payload, a URL, or a file/classpath
        reference; *target* an optional ``{host, port, scheme}`` override and
        *profile* an optional :class:`LoadProfile`. Pass a fully-formed *body*
        dict to override the individual arguments. Registration generates no
        traffic and is allowed even when ``loadGenerationEnabled`` is off.
        """
        return self._run(
            self._async_client.generate_load_scenario_from_openapi(
                name,
                spec_url_or_payload,
                target=target,
                profile=profile,
                body=body,
            )
        )

    def generate_load_scenario_from_recording(
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
        """Seed and register an editable load scenario from recorded traffic.

        *mode* is ``VERBATIM`` (default) or ``TEMPLATIZED``; *request_filter*
        selects which recorded requests to include; *max_steps* caps VERBATIM
        steps; *target* (``{host, port, scheme}``) overrides routing; *profile*
        overrides the conservative default. Pass a fully-formed *body* dict to
        override the individual arguments. Registration generates no traffic and
        is allowed even when ``loadGenerationEnabled`` is off.
        """
        return self._run(
            self._async_client.generate_load_scenario_from_recording(
                name,
                mode=mode,
                request_filter=request_filter,
                target=target,
                max_steps=max_steps,
                profile=profile,
                body=body,
            )
        )

    def scenario(self, name: str) -> SyncScenarioHandle:
        """Return a handle to the named stateful scenario, wrapping the
        ``/mockserver/scenario/{name}`` control-plane endpoints.
        """
        return SyncScenarioHandle(self._async_client.scenario(name), self._run)

    def scenarios(self) -> list[dict]:
        """List every known scenario and its current state.

        Returns a list of dicts each with ``scenarioName`` and ``currentState``.
        """
        return self._run(self._async_client.scenarios())

    def verify(
        self,
        request: HttpRequest | ConditionalRequestDefinition | AnyOfRequestDefinition | None = None,
        times: VerificationTimes | None = None,
        *,
        response: HttpResponse | None = None,
    ) -> None:
        return self._run(self._async_client.verify(request, times, response=response))

    def verify_sequence(
        self,
        *requests: HttpRequest | ConditionalRequestDefinition | AnyOfRequestDefinition,
        responses: list[HttpResponse] | None = None,
    ) -> None:
        return self._run(self._async_client.verify_sequence(*requests, responses=responses))

    def verify_zero_interactions(self) -> None:
        return self._run(self._async_client.verify_zero_interactions())

    def retrieve_recorded_requests(
        self, request: HttpRequest | DnsRequestDefinition | None = None
    ) -> list[HttpRequest | DnsRequestDefinition | dict]:
        return self._run(self._async_client.retrieve_recorded_requests(request))

    def retrieve_active_expectations(
        self,
        request: HttpRequest | None = None,
        expectation_filter: ExpectationFilter | None = None,
    ) -> list[Expectation]:
        return self._run(
            self._async_client.retrieve_active_expectations(request, expectation_filter)
        )

    def retrieve_active_expectations_by_metadata(
        self, key: str, value: Any
    ) -> list[Expectation]:
        return self._run(
            self._async_client.retrieve_active_expectations_by_metadata(key, value)
        )

    def retrieve_active_expectations_by_metadata_key(
        self, key: str
    ) -> list[Expectation]:
        return self._run(
            self._async_client.retrieve_active_expectations_by_metadata_key(key)
        )

    def retrieve_unmatched_expectations(self) -> list[Expectation]:
        return self._run(self._async_client.retrieve_unmatched_expectations())

    def retrieve_recorded_expectations(
        self,
        request: HttpRequest | None = None,
        offset: int | None = None,
        limit: int | None = None,
        options: RecordedExpectationsOptions | None = None,
    ) -> list[Expectation]:
        return self._run(
            self._async_client.retrieve_recorded_expectations(request, offset, limit, options)
        )

    def retrieve_recorded_requests_by_id(
        self, expectation_id: str
    ) -> list[HttpRequest | DnsRequestDefinition | dict]:
        return self._run(
            self._async_client.retrieve_recorded_requests_by_id(expectation_id)
        )

    def retrieve_active_expectations_by_id(
        self, expectation_id: str
    ) -> list[Expectation]:
        return self._run(
            self._async_client.retrieve_active_expectations_by_id(expectation_id)
        )

    def retrieve_recorded_expectations_by_id(
        self, expectation_id: str
    ) -> list[Expectation]:
        return self._run(
            self._async_client.retrieve_recorded_expectations_by_id(expectation_id)
        )

    def retrieve_recorded_requests_and_responses_by_id(
        self, expectation_id: str
    ) -> list[HttpRequestAndHttpResponse]:
        return self._run(
            self._async_client.retrieve_recorded_requests_and_responses_by_id(
                expectation_id
            )
        )

    def retrieve_log_messages_by_id(self, expectation_id: str) -> list[str]:
        return self._run(
            self._async_client.retrieve_log_messages_by_id(expectation_id)
        )

    def explain(self, correlation_id: str) -> RequestExplanation:
        """Explain how the recorded request with this correlation id would be
        served now (``PUT /mockserver/explain``): the expectation that would
        serve it, why each expectation ahead of it was passed over, and which
        it shadows. See :meth:`AsyncMockServerClient.explain`.
        """
        return self._run(self._async_client.explain(correlation_id))

    def debug_mismatch(self, request: HttpRequest | dict) -> DebugMismatchResult:
        """Compare a request with every active expectation
        (``PUT /mockserver/debugMismatch``), with per-field differences and the
        closest match.
        """
        return self._run(self._async_client.debug_mismatch(request))

    def explain_unmatched(
        self, limit: int | None = None, suggest_fixes: bool | None = None
    ) -> ExplainUnmatchedResult:
        """Explain why recent requests matched no expectation
        (``PUT /mockserver/explainUnmatched``), closest expectations first.
        """
        return self._run(self._async_client.explain_unmatched(limit, suggest_fixes))

    def retrieve_expectations_as_code(
        self, fmt: str = "java", request: HttpRequest | None = None
    ) -> str:
        """Retrieve the active expectations as MockServer SDK setup code in the
        requested language (``java``, ``javascript``, ``python``, ``go``,
        ``csharp``, ``ruby``, ``rust`` or ``php``).
        """
        return self._run(
            self._async_client.retrieve_expectations_as_code(fmt, request)
        )

    def retrieve_recorded_expectations_as_code(
        self, fmt: str = "java", request: HttpRequest | None = None
    ) -> str:
        """Retrieve the recorded (proxied) request/response pairs as MockServer
        SDK setup code in the requested language (``java``, ``javascript``,
        ``python``, ``go``, ``csharp``, ``ruby``, ``rust`` or ``php``).
        """
        return self._run(
            self._async_client.retrieve_recorded_expectations_as_code(fmt, request)
        )

    def retrieve_recorded_requests_and_responses(
        self, request: HttpRequest | None = None
    ) -> list[HttpRequestAndHttpResponse]:
        return self._run(
            self._async_client.retrieve_recorded_requests_and_responses(request)
        )

    def retrieve_log_messages(
        self, request: HttpRequest | None = None
    ) -> list[str]:
        return self._run(self._async_client.retrieve_log_messages(request))

    def bind(self, *ports: int) -> list[int]:
        return self._run(self._async_client.bind(*ports))

    def stop(self) -> None:
        return self._run(self._async_client.stop())

    def has_started(self, attempts: int = 10, timeout: float = 0.5) -> bool:
        return self._run(self._async_client.has_started(attempts, timeout))

    def when(
        self,
        request: HttpRequest | ConditionalRequestDefinition | AnyOfRequestDefinition,
        times: Times | None = None,
        time_to_live: TimeToLive | None = None,
        priority: int | None = None,
    ) -> SyncForwardChainExpectation:
        async_chain = self._async_client.when(request, times, time_to_live, priority)
        return SyncForwardChainExpectation(async_chain, self._run)

    def mock_with_callback(
        self,
        request: HttpRequest,
        callback: Callable,
        times: Times | None = None,
        time_to_live: TimeToLive | None = None,
    ) -> list[Expectation]:
        return self._run(
            self._async_client.mock_with_callback(request, callback, times, time_to_live)
        )

    # -------------------------------------------------------------------
    # Breakpoint matcher management
    # -------------------------------------------------------------------

    def add_breakpoint(
        self,
        matcher,
        phases: list[str],
        request_handler=None,
        response_handler=None,
        stream_frame_handler=None,
    ) -> str:
        """Register a breakpoint matcher with callback handlers.

        Returns the server-assigned breakpoint matcher id (UUID string).
        """
        return self._run(
            self._async_client.add_breakpoint(
                matcher, phases, request_handler, response_handler, stream_frame_handler
            )
        )

    def add_request_breakpoint(self, matcher, request_handler) -> str:
        """Convenience: register a REQUEST-only breakpoint."""
        return self._run(
            self._async_client.add_request_breakpoint(matcher, request_handler)
        )

    def add_request_and_response_breakpoint(
        self, matcher, request_handler, response_handler
    ) -> str:
        """Convenience: register a REQUEST+RESPONSE breakpoint."""
        return self._run(
            self._async_client.add_request_and_response_breakpoint(
                matcher, request_handler, response_handler
            )
        )

    def list_breakpoint_matchers(self) -> dict:
        """List all registered breakpoint matchers."""
        return self._run(self._async_client.list_breakpoint_matchers())

    def remove_breakpoint_matcher(self, breakpoint_id: str) -> dict:
        """Remove a breakpoint matcher by id."""
        return self._run(
            self._async_client.remove_breakpoint_matcher(breakpoint_id)
        )

    def clear_breakpoint_matchers(self) -> dict:
        """Clear all registered breakpoint matchers."""
        return self._run(self._async_client.clear_breakpoint_matchers())

    def mock_with_forward_callback(
        self,
        request: HttpRequest,
        forward_callback: Callable,
        response_callback: Callable | None = None,
        times: Times | None = None,
        time_to_live: TimeToLive | None = None,
    ) -> list[Expectation]:
        return self._run(
            self._async_client.mock_with_forward_callback(
                request, forward_callback, response_callback, times, time_to_live
            )
        )

    def close(self) -> None:
        self._run(self._async_client.close())
        self._loop.call_soon_threadsafe(self._loop.stop)
        self._thread.join(timeout=5)

    def __enter__(self) -> MockServerClient:
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.close()
