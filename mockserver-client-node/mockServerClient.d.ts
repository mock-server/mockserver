/*
 * mockserver
 * http://mock-server.com
 *
 * Original definitions by: David Tanner <https://github.com/DavidTanner>
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */

import {BinaryResponse, ChaosExperiment, DnsResponse, Expectation, ExpectationId, GenerateLoadScenarioFromOpenAPIRequest, GenerateLoadScenarioFromRecordingRequest, GrpcStreamResponse, HttpChaosProfile, HttpClassCallback, HttpError, HttpForward, HttpOverrideForwardedRequest, HttpRequest, HttpRequestAndHttpResponse, HttpResponse, HttpSseResponse, HttpTemplate, HttpWebSocketResponse, KeyToMultiValue, LoadScenario, LoadScenarioGenerationResult, LoadScenarioReport, LoadScenarioStatus, LoadScenarioEntry, LoadScenarioList, LoadScenarioRegistration, LoadScenarioStartResult, LoadScenarioStopResult, OpenAPIExpectation, RequestDefinition, SloCriteria, SloVerdict, Times, TimeToLive, RequestExplanation, DebugMismatchResult, ExplainUnmatchedResult, ExplainUnmatchedOptions,} from './mockServer';
import {Llm, LlmConversationBuilder, LlmFailoverBuilder, LlmMockBuilder} from './llmTypes';
import {McpMockBuilder} from './mcpMockBuilder';
import {A2aMockBuilder} from './a2aMockBuilder';

export type Host = string;
export type Port = number;
export type ContextPath = string;
export type TLS = boolean;
export type CaCertPemFilePath = string;

/**
 * Optional control-plane authentication and mutual-TLS settings, supplied as a
 * trailing argument to mockServerClient(...). All fields are optional and
 * additive — omitting the object preserves the default (unauthenticated, no
 * client certificate) behaviour.
 */
export interface MockServerClientOptions {
    /**
     * Static control-plane JWT. When set, every control-plane request, and in
     * Node the callback/breakpoint WebSocket upgrade, carries an
     * `Authorization: Bearer <bearerToken>` header. Use this when the server
     * is started with `controlPlaneJWTAuthenticationRequired=true`.
     */
    bearerToken?: string;

    /**
     * A supplier evaluated per control-plane request and per WebSocket
     * (re)connect to obtain the bearer token (so the token can be refreshed).
     * Takes precedence over `bearerToken`.
     */
    bearerTokenSupplier?: () => string;

    /**
     * Control-plane HTTP Basic username, sent with `basicAuthPassword` as an
     * `Authorization: Basic` header on every control-plane request and, in
     * Node, on the callback/breakpoint WebSocket upgrade. Use this when the
     * server is started with `controlPlaneBasicAuthenticationRequired=true`;
     * MockServer refuses Basic credentials without TLS, so also pass `tls`.
     * A bearer token, when also configured, is sent instead.
     */
    basicAuthUsername?: string;

    /**
     * Control-plane HTTP Basic password, paired with `basicAuthUsername`.
     */
    basicAuthPassword?: string;

    /**
     * Path to a PEM client certificate presented for mutual TLS, for when the
     * server requires `controlPlaneTLSMutualAuthenticationRequired=true`, on
     * control-plane requests and the callback WebSocket.
     */
    clientCertPemFilePath?: string;

    /**
     * Path to the PEM private key paired with `clientCertPemFilePath` for
     * mutual TLS.
     */
    clientKeyPemFilePath?: string;

    /**
     * How long registering a callback or breakpoint waits for its WebSocket to
     * open and receive its client id from MockServer before it rejects.
     * Defaults to 10000 (10 seconds).
     */
    callbackWebSocketTimeoutMillis?: number;
}
// Retains backwards compatability.
export type KeysToMultiValues = KeyToMultiValue;

export type ClearType = 'EXPECTATIONS' | 'LOG' | 'ALL';

/** A metadata value to select by: a string is compared as text, any other value as its JSON. */
export type ExpectationMetadataValue = string | number | boolean | any[] | { [key: string]: any };

/**
 * Selects expectations to retrieve or clear. Every property is optional, but an invalid filter, a
 * filtered clear that selects nothing, and a clear with limit or offset are all rejected before
 * anything is sent.
 */
export interface ExpectationFilter {
    /** select expectations whose metadata has this key (non-blank) */
    metadataKey?: string;
    /** with metadataKey, select only that key with this value; must not be null, pass the string 'null' to select a JSON null */
    metadataValue?: ExpectationMetadataValue;
    /** select expectations whose metadata object this JSONPath selects at least one value from */
    metadataJsonPath?: string;
    /** select only expectations that have never matched a request */
    unmatched?: boolean;
    /** retrieve only: the maximum number of expectations to return, an integer zero or more */
    limit?: number;
    /** retrieve only: the number of expectations to skip, an integer zero or more */
    offset?: number;
}

/** An ExpectationFilter for a clear: no paging. */
export type ClearExpectationFilter = Omit<ExpectationFilter, 'limit' | 'offset'>;
export type CodeFormat = 'java' | 'javascript' | 'python' | 'go' | 'csharp' | 'ruby' | 'rust' | 'php'
    | 'JAVA' | 'JAVASCRIPT' | 'PYTHON' | 'GO' | 'CSHARP' | 'RUBY' | 'RUST' | 'PHP';

export interface ClockStatus {
    currentInstant: string;
    currentEpochMillis: number;
    frozen: boolean;
}

/**
 * The valid high-level operating modes (server enum MockMode):
 * - SIMULATE: match expectations, unmatched -> 404 (the default; proxy-on-no-match disabled)
 * - SPY:      match expectations; unmatched are forwarded to the real upstream and recorded
 * - CAPTURE:  forward + record (captures all traffic when no expectations are defined)
 */
export type MockMode = 'SIMULATE' | 'SPY' | 'CAPTURE';

/** String constants for the operating modes (also usable as raw strings). */
export declare const MockMode: {
    readonly SIMULATE: 'SIMULATE';
    readonly SPY: 'SPY';
    readonly CAPTURE: 'CAPTURE';
};

/** Result of GET/PUT /mockserver/mode. */
export interface ModeStatus {
    mode: MockMode;
    proxyUnmatchedRequests: boolean;
}

/** Result of PUT /mockserver/files/store. */
export interface StoredFile {
    name: string;
    size: number;
}

/** Result of pactVerify — the verification report. Inspect `verified`. */
export interface PactVerificationReport {
    verified: boolean;
    interactions?: any[];
    [key: string]: any;
}

export interface SuccessFullRequest {
    statusCode: number;
    body: string;
}

export interface GrpcMethod {
    name: string;
    inputType: string;
    outputType: string;
    clientStreaming: boolean;
    serverStreaming: boolean;
}

export interface GrpcService {
    name: string;
    methods: GrpcMethod[];
}

/**
 * The current state of a single scenario, as returned by
 * client.scenario(name).state(), .set(...) and .trigger(...).
 */
export interface ScenarioState {
    scenarioName: string;
    currentState: string;
    /** present only when .set(...) scheduled a timed transition */
    nextState?: string;
    /** present only when .set(...) scheduled a timed transition */
    transitionAfterMs?: number;
}

/**
 * The list of all known scenarios and their current states, as returned by
 * client.scenarios().
 */
export interface ScenarioList {
    scenarios: Array<{ scenarioName: string; currentState: string }>;
}

/**
 * Options for client.scenario(name).set(state, options) — schedule a timed
 * transition to nextState after transitionAfterMs milliseconds.
 */
export interface ScenarioSetOptions {
    transitionAfterMs?: number;
    nextState?: string;
}

/**
 * A handle to a single named scenario's state machine. Returned by
 * client.scenario(name); wraps the /mockserver/scenario REST endpoints.
 */
export interface ScenarioHandle {
    /** GET /mockserver/scenario/{name} — the scenario's current state */
    state(): Promise<ScenarioState>;

    /**
     * PUT /mockserver/scenario/{name} — set the scenario's state, optionally
     * scheduling a timed transition to options.nextState after
     * options.transitionAfterMs milliseconds.
     */
    set(state: string, options?: ScenarioSetOptions): Promise<ScenarioState>;

    /** PUT /mockserver/scenario/{name}/trigger — set the state to newState immediately */
    trigger(newState: string): Promise<ScenarioState>;
}

export type RequestResponse = SuccessFullRequest | string;

export type PathOrRequestDefinition = string | Expectation | RequestDefinition | undefined | null;

/** Recorded requests and responses as a HAR 1.2 document, as MockServer writes it for format=HAR. */
export interface Har {
    log: HarLog;
}

export interface HarLog {
    version: string;
    creator: HarCreator;
    entries: HarEntry[];
}

export interface HarCreator {
    name: string;
    version: string;
}

export interface HarEntry {
    startedDateTime: string;
    /** total time of the exchange in milliseconds, 0 when it was not measured */
    time: number;
    request: HarRequest;
    response: HarResponse;
    cache: object;
    timings: HarTimings;
    serverIPAddress?: string;
    connection?: string;
}

export interface HarNameValuePair {
    name: string;
    value: string;
}

export interface HarCookie {
    name: string;
    value: string;
    path?: string;
    domain?: string;
    expires?: string;
    httpOnly?: boolean;
    secure?: boolean;
}

export interface HarRequest {
    method: string;
    url: string;
    httpVersion: string;
    cookies: HarCookie[];
    headers: HarNameValuePair[];
    queryString: HarNameValuePair[];
    postData?: HarPostData;
    headersSize: number;
    /** -1 when unknown */
    bodySize: number;
}

export interface HarPostData {
    mimeType: string;
    /** for a form body */
    params?: HarNameValuePair[];
    /** for any other body; a binary body is base64 */
    text?: string;
}

export interface HarResponse {
    status: number;
    statusText: string;
    httpVersion: string;
    cookies: HarCookie[];
    headers: HarNameValuePair[];
    content: HarContent;
    redirectURL: string;
    headersSize: number;
    /** -1 when unknown */
    bodySize: number;
}

export interface HarContent {
    size: number;
    mimeType: string;
    text?: string;
    /** 'base64' when text holds a binary body */
    encoding?: string;
}

/** milliseconds per phase; blocked, dns, connect and ssl are -1 when not measured, send, wait and receive 0 */
export interface HarTimings {
    blocked: number;
    dns: number;
    connect: number;
    send: number;
    wait: number;
    receive: number;
    ssl: number;
}

/**
 * Fluent, chainable expectation builder returned by MockServerClient.when(...),
 * mirroring the Java client's ForwardChainExpectation. The optional builder
 * methods (withTimes/withTimeToLive/withPriority/withId) refine the expectation
 * before a terminal action method (respond/forward/error/callback/forwardCallback)
 * finishes building it and sends it to the MockServer.
 */
export interface ForwardChainExpectation {
    withTimes(times: Times | number): ForwardChainExpectation;

    withTimeToLive(timeToLive: TimeToLive): ForwardChainExpectation;

    withPriority(priority: number): ForwardChainExpectation;

    withId(id: string): ForwardChainExpectation;

    /** Add (or replace) one metadata entry on the expectation, for example an owner or a ticket number. */
    withMetadata(key: string, value: any): ForwardChainExpectation;

    /** Set all metadata on the expectation, replacing any already set; null or {} removes it. */
    withMetadata(metadata: { [key: string]: any } | null): ForwardChainExpectation;

    /**
     * Respond with the given response when the expectation is matched. Accepts a
     * plain httpResponse object, a response template (HttpTemplate), or a
     * server-side response class-callback (HttpClassCallback).
     */
    respond(response: HttpResponse | HttpTemplate | HttpClassCallback): Promise<RequestResponse>;

    /**
     * Forward the matched request. Accepts an httpForward target, a forward
     * template (HttpTemplate), a forward class-callback (HttpClassCallback), or an
     * override-forwarded-request action (HttpOverrideForwardedRequest).
     */
    forward(forward: HttpForward | HttpTemplate | HttpClassCallback | HttpOverrideForwardedRequest): Promise<RequestResponse>;

    /**
     * Return an error (e.g. drop the connection) when the expectation is matched.
     */
    error(error: HttpError): Promise<RequestResponse>;

    /**
     * Generate the response locally in this JS process via a callback invoked over
     * the callback WebSocket (equivalent to mockWithCallback).
     */
    callback(requestHandler: (request: HttpRequest) => HttpResponse): Promise<RequestResponse>;

    /**
     * Rewrite the forwarded request locally in this JS process via a callback
     * invoked over the callback WebSocket (equivalent to mockWithForwardCallback).
     */
    forwardCallback(forwardHandler: (request: HttpRequest) => HttpRequest): Promise<RequestResponse>;
}

export interface MockServerClient {
    openAPIExpectation(expectation: OpenAPIExpectation): Promise<RequestResponse>;

    mockAnyResponse(expectation: Expectation | Expectation[]): Promise<RequestResponse>;

    /**
     * LLM mocking builder factories (mirrors the Java client's Llm helpers).
     * Use to build completion/chat, tool-use, streaming, embedding, multi-turn
     * conversation, and provider-failover mocks, then pass the builder (or the
     * built expectation) to mockWithLLM().
     */
    llm: Llm;

    /**
     * Register one or more LLM mock expectations. Accepts a single expectation,
     * an array of expectations, or an LLM builder (the result of
     * client.llm.llmMock(...), .conversation(), or .llmFailover()) — builders are
     * built via their build() method.
     */
    mockWithLLM(expectationOrBuilder: Expectation | Expectation[] | LlmMockBuilder | LlmConversationBuilder | LlmFailoverBuilder): Promise<RequestResponse>;

    mockWithCallback(requestMatcher: RequestDefinition, requestHandler: (request: HttpRequest) => HttpResponse, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string, metadata?: { [key: string]: any }): Promise<RequestResponse>;

    mockWithForwardCallback(requestMatcher: RequestDefinition, forwardHandler: (request: HttpRequest) => HttpRequest, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string, metadata?: { [key: string]: any }): Promise<RequestResponse>;

    mockWithForwardAndResponseCallback(requestMatcher: RequestDefinition, forwardHandler: (request: HttpRequest) => HttpRequest, responseHandler: (request: HttpRequest, response: HttpResponse) => HttpResponse, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string): Promise<RequestResponse>;

    /**
     * Register an expectation that delegates the response to a server-side class
     * implementing the MockServer ExpectationResponseCallback interface (a "class
     * callback"). Pure JSON / REST-only — no callback WebSocket is opened. The
     * referenced class runs inside the MockServer JVM and must be on its classpath.
     *
     * @param requestMatcher the path to match (string) or a full request matcher object
     * @param callbackClass  the fully-qualified server-side callback class name, or a full
     *                       httpResponseClassCallback action object ({ callbackClass, delay?, primary? })
     */
    respondWithClassCallback(requestMatcher: string | RequestDefinition, callbackClass: string | HttpClassCallback, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string): Promise<RequestResponse>;

    /**
     * Register an expectation that delegates request forwarding to a server-side
     * class implementing the MockServer ExpectationForwardCallback interface (a
     * forward "class callback"). Pure JSON / REST-only; the referenced class must
     * be on the MockServer classpath.
     *
     * @param requestMatcher the path to match (string) or a full request matcher object
     * @param callbackClass  the fully-qualified server-side callback class name, or a full
     *                       httpForwardClassCallback action object ({ callbackClass, delay?, primary? })
     */
    forwardWithClassCallback(requestMatcher: string | RequestDefinition, callbackClass: string | HttpClassCallback, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string): Promise<RequestResponse>;

    mockSimpleResponse<T = any>(path: string, responseBody: T, statusCode?: number): Promise<RequestResponse>;

    /**
     * Start building an expectation with a fluent, chainable API that mirrors the
     * Java client's MockServerClient.when(...). Returns a ForwardChainExpectation
     * whose terminal methods (respond/forward/error/callback/forwardCallback)
     * finish building the expectation and send it to the MockServer.
     *
     * @param requestMatcher the path to match (string) or a full request matcher object
     * @param times the number of times the requestMatcher should be matched (optional)
     * @param timeToLive the time this expectation should be used to match requests (optional)
     * @param priority the priority with which this expectation is used to match requests, high first (optional)
     */
    when(requestMatcher: string | RequestDefinition, times?: Times | number, timeToLive?: TimeToLive, priority?: number): ForwardChainExpectation;

    respondWithSse(requestMatcher: string | RequestDefinition, sseResponse: HttpSseResponse, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string): Promise<RequestResponse>;

    respondWithWebSocket(requestMatcher: string | RequestDefinition, webSocketResponse: HttpWebSocketResponse, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string): Promise<RequestResponse>;

    respondWithDns(requestMatcher: string | RequestDefinition, dnsResponse: DnsResponse, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string): Promise<RequestResponse>;

    respondWithBinary(requestMatcher: string | RequestDefinition, binaryResponse: BinaryResponse, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string): Promise<RequestResponse>;

    respondWithGrpcStream(requestMatcher: string | RequestDefinition, grpcStreamResponse: GrpcStreamResponse, times?: Times | number, priority?: number, timeToLive?: TimeToLive, id?: string): Promise<RequestResponse>;

    setDefaultHeaders(responseHeaders: KeysToMultiValues, requestHeaders: KeysToMultiValues): MockServerClient;

    verify(matcher: RequestDefinition, atLeast?: number, atMost?: number): Promise<void | string>;

    verifyResponse(responseMatcher: HttpResponse, atLeast?: number, atMost?: number): Promise<void | string>;

    verifyRequestAndResponse(requestMatcher: RequestDefinition, responseMatcher: HttpResponse, atLeast?: number, atMost?: number): Promise<void | string>;

    verifyById(expectationId: ExpectationId, atLeast?: number, atMost?: number): Promise<void | string>;

    verifySequence(...matchers: RequestDefinition[]): Promise<void | string>;

    verifySequenceWithResponses(requestsAndResponses: Array<{request: RequestDefinition, response: HttpResponse}>): Promise<void | string>;

    verifySequenceById(...expectationIds: ExpectationId[]): Promise<void | string>;

    verifyZeroInteractions(): Promise<void | string>;

    /**
     * Resets MockServer, clearing its expectations, recorded requests and logs, and closes the callback
     * WebSockets the clients of this MockServer have opened in this process, as close() does.
     */
    reset(): Promise<RequestResponse>;

    /**
     * Closes the callback WebSockets the clients of this MockServer have opened in this process, for
     * mockWithCallback, the callback and forwardCallback actions and breakpoints, so they no longer keep
     * the process running. Sends nothing to MockServer: its expectations and breakpoints that use them
     * stop working. A later callback or breakpoint opens a new WebSocket. Resolves once they have closed.
     */
    close(): Promise<void>;

    clear(pathOrRequestDefinition: PathOrRequestDefinition, type: ClearType): Promise<RequestResponse>;

    /**
     * Clear only the expectations that match both the request matcher and the filter (PUT
     * /mockserver/clear?type=expectations). The event log is left untouched. Throws, sending nothing,
     * if the filter selects nothing (no metadataKey, metadataJsonPath or unmatched: true) or pages.
     */
    clear(pathOrRequestDefinition: PathOrRequestDefinition, filter: ClearExpectationFilter): Promise<RequestResponse>;

    /**
     * As clear(pathOrRequestDefinition, filter), but a null, undefined or non-object filter throws before
     * anything is sent, so a missing filter can never clear everything.
     */
    clearWithFilter(pathOrRequestDefinition: PathOrRequestDefinition, filter: ClearExpectationFilter): Promise<RequestResponse>;

    /** Clear the expectations whose metadata has key with a value equal to value. Throws on a blank key or a null value. */
    clearByMetadata(key: string, value: ExpectationMetadataValue): Promise<RequestResponse>;

    /** Clear the expectations whose metadata has key, whatever its value. Throws on a blank key. */
    clearByMetadataKey(key: string): Promise<RequestResponse>;

    /** Clear the expectations that have never matched a request. */
    clearUnmatchedExpectations(): Promise<RequestResponse>;

    clearById(expectationId: string, type: ClearType): Promise<RequestResponse>;

    freezeClock(instant?: string): Promise<RequestResponse>;

    advanceClock(durationMillis: number): Promise<RequestResponse>;

    resetClock(): Promise<RequestResponse>;

    clockStatus(): Promise<ClockStatus>;

    /**
     * Retrieve the JSON counter snapshot (PUT /mockserver/retrieve?type=METRICS).
     * Resolves to a flat map of metric name -> long value, or {} when disabled.
     */
    retrieveMetrics(): Promise<{ [name: string]: number }>;

    /**
     * Scrape the Prometheus exposition text (GET /mockserver/metrics). Rejects
     * with "404 Not Found" when metrics are disabled (metricsEnabled=false).
     */
    scrapeMetrics(): Promise<string>;

    /** Retrieve the effective live configuration (GET /mockserver/configuration). */
    retrieveConfiguration(): Promise<any>;

    /**
     * Update the live configuration (PUT /mockserver/configuration). Only fields
     * present in the supplied document are applied (partial update). Resolves to
     * the updated configuration. Properties fixed at startup, such as
     * forwardProxyBlockPrivateNetworks, forwardProxyAllowedHosts and
     * forwardProxyDeniedHosts, are ignored and the server logs a warning.
     */
    updateConfiguration(configuration: object | string): Promise<any>;

    /**
     * Retrieve the recorded mock drift report (GET /mockserver/drift). Resolves
     * to the parsed report of the form { count: number, drifts: any[] }.
     */
    retrieveDrift(): Promise<{ count: number, drifts: any[] }>;

    /** Clear all recorded mock drift (PUT /mockserver/drift/clear). */
    clearDrift(): Promise<RequestResponse>;

    /**
     * Import a Pact v3 contract as expectations (PUT /mockserver/pact/import).
     * Resolves to the array of upserted expectations.
     */
    pactImport(pactJson: object | string): Promise<Expectation[]>;

    /**
     * Export the active expectations as a Pact v3 consumer contract
     * (PUT /mockserver/pact). Blank consumer/provider use the server defaults.
     */
    pactExport(consumer?: string, provider?: string): Promise<any>;

    /**
     * Verify a Pact v3 contract against the active expectations
     * (PUT /mockserver/pact/verify). The server replies 202 on pass and 406 on
     * fail; the report body is returned in BOTH cases, so this RESOLVES with the
     * report for both (inspect `verified`). Any other status (400 for a malformed
     * request, 401/403, 500) or a report that is not JSON rejects.
     */
    pactVerify(pactJson: object | string): Promise<PactVerificationReport>;

    /**
     * Store a UTF-8 text file in the in-memory file store
     * (PUT /mockserver/files/store).
     */
    storeFile(name: string, content: string): Promise<StoredFile>;

    /**
     * Store a binary file (base64-encoded on the wire) in the in-memory file
     * store (PUT /mockserver/files/store).
     */
    storeBinaryFile(name: string, content: Buffer | Uint8Array | ArrayBuffer | string): Promise<StoredFile>;

    /**
     * Retrieve a file's content (PUT /mockserver/files/retrieve). Resolves with
     * the raw file content string on success; REJECTS with an Error carrying the
     * server's "file not found: <name>" message when the file is unknown (404), so
     * a missing file is caught rather than mistaken for success. Mirrors the other
     * MockServer clients, which return the content directly.
     */
    retrieveFile(name: string): Promise<string>;

    /** List all file names in the in-memory file store (PUT /mockserver/files/list). */
    listFiles(): Promise<string[]>;

    /**
     * Delete a file from the in-memory file store (PUT /mockserver/files/delete).
     * An unknown file rejects with the server's 404 text body.
     */
    deleteFile(name: string): Promise<RequestResponse>;

    /**
     * Import a HAR, Postman collection, Pact contract, WireMock stub mapping, Mountebank
     * imposter or Mockoon environment as expectations (PUT /mockserver/import). When format
     * is blank the server auto-detects. Warnings the WireMock, Mountebank and Mockoon
     * importers report are written with console.warn. format "recording" throws: send
     * PUT /mockserver/import?format=recording directly to re-import recorded traffic.
     */
    importDocument(json: object | string, format?: 'har' | 'postman' | 'pact' | 'wiremock' | 'mountebank' | 'mockoon'): Promise<Expectation[]>;

    /** Import a HAR document as expectations (PUT /mockserver/import?format=har). */
    importHar(harJson: object | string): Promise<Expectation[]>;

    /** Import a Postman collection as expectations (PUT /mockserver/import?format=postman). */
    importPostmanCollection(collectionJson: object | string): Promise<Expectation[]>;

    /**
     * Set the high-level operating mode (PUT /mockserver/mode?mode=<MODE>). Also
     * flips attemptToProxyIfNoMatchingExpectation server-side.
     */
    setMode(mode: MockMode): Promise<ModeStatus>;

    /** Read the current high-level operating mode (GET /mockserver/mode). */
    retrieveMode(): Promise<ModeStatus>;

    /**
     * Generate and upsert expectations from a WSDL document (PUT /mockserver/wsdl).
     * The raw WSDL XML is sent as the request body. Resolves to the generated
     * (upserted) expectations.
     */
    wsdlExpectation(wsdl: string): Promise<Expectation[]>;

    setServiceChaos(host: string, chaos: HttpChaosProfile, ttlMillis?: number): Promise<RequestResponse>;

    removeServiceChaos(host: string): Promise<RequestResponse>;

    clearServiceChaos(): Promise<RequestResponse>;

    serviceChaosStatus(): Promise<{ services: { [host: string]: HttpChaosProfile } }>;

    /**
     * Evaluate a set of service-level objectives (SLOs) over a window of the
     * recorded SLI samples. Resolves with the parsed verdict on PASS or
     * INCONCLUSIVE (HTTP 200); rejects on FAIL (HTTP 406, the rejection value
     * carries the verdict body) and on a malformed/disabled request (HTTP 400).
     */
    verifySLO(criteria: SloCriteria): Promise<SloVerdict>;

    /**
     * Start a scheduled multi-stage chaos experiment. Only one experiment may
     * be active at a time; starting a new one stops the previous one.
     */
    startChaosExperiment(experiment: ChaosExperiment): Promise<{ status?: string; name?: string }>;

    loadScenario(scenario: LoadScenario): Promise<LoadScenarioRegistration>;

    loadScenarios(): Promise<LoadScenarioList>;

    getLoadScenario(name: string): Promise<LoadScenarioEntry>;

    deleteLoadScenario(name: string): Promise<RequestResponse>;

    clearLoadScenarios(): Promise<RequestResponse>;

    startLoadScenarios(names: string | string[]): Promise<LoadScenarioStartResult>;

    stopLoadScenarios(names?: string | string[]): Promise<LoadScenarioStopResult>;

    runLoadScenario(scenario: LoadScenario): Promise<LoadScenarioStartResult>;

    /**
     * Retrieve the end-of-run summary report for a load scenario run. With no
     * `format` (or any value other than "junit") the JSON report is parsed and
     * resolved as a {@link LoadScenarioReport}; with `format="junit"` the
     * JUnit-XML document is resolved as a string. Rejects 404 if the named
     * scenario has never run.
     */
    getLoadScenarioReport(name: string, format?: string): Promise<LoadScenarioReport | string>;

    /**
     * Generate (and register) an editable load scenario from an OpenAPI spec —
     * one step per operation. Loaded in the LOADED state without generating any
     * traffic; allowed even when loadGenerationEnabled is false.
     */
    generateLoadScenarioFromOpenAPI(request: GenerateLoadScenarioFromOpenAPIRequest): Promise<LoadScenarioGenerationResult>;

    /**
     * Generate (and register) an editable load scenario from traffic previously
     * recorded by MockServer in proxy/recording mode. Loaded in the LOADED state
     * without generating any traffic; allowed even when loadGenerationEnabled is
     * false.
     */
    generateLoadScenarioFromRecording(request: GenerateLoadScenarioFromRecordingRequest): Promise<LoadScenarioGenerationResult>;

    /**
     * Obtain a handle to a named stateful scenario, exposing typed helpers over
     * the /mockserver/scenario REST endpoints to read (.state()), set (.set())
     * and externally trigger (.trigger()) the scenario's state.
     *
     * @param name the scenario name
     */
    scenario(name: string): ScenarioHandle;

    /**
     * List every known scenario and its current state
     * (GET /mockserver/scenario).
     */
    scenarios(): Promise<ScenarioList>;

    bind(ports: Port[]): Promise<RequestResponse>;

    retrieveRecordedRequests(pathOrRequestDefinition: PathOrRequestDefinition): Promise<RequestDefinition[]>;

    retrieveRecordedRequestsAndResponses(pathOrRequestDefinition: PathOrRequestDefinition): Promise<HttpRequestAndHttpResponse[]>;

    /**
     * The recorded requests and their responses, as retrieveRecordedRequestsAndResponses
     * selects them, as one HAR 1.2 document (PUT /mockserver/retrieve?format=HAR).
     */
    retrieveRecordedRequestsAndResponsesAsHar(pathOrRequestDefinition: PathOrRequestDefinition): Promise<Har>;

    retrieveActiveExpectations(pathOrRequestDefinition: PathOrRequestDefinition): Promise<Expectation[]>;

    /**
     * Retrieve the active expectations that match both the request matcher and the filter, for example by
     * metadata, because they have never matched a request, or one page of them. Throws, sending nothing,
     * on an invalid filter.
     */
    retrieveActiveExpectations(pathOrRequestDefinition: PathOrRequestDefinition, filter: ExpectationFilter): Promise<Expectation[]>;

    /** Retrieve the active expectations whose metadata has key with a value equal to value. Throws on a blank key or a null value. */
    retrieveActiveExpectationsByMetadata(key: string, value: ExpectationMetadataValue): Promise<Expectation[]>;

    /** Retrieve the active expectations whose metadata has key, whatever its value. Throws on a blank key. */
    retrieveActiveExpectationsByMetadataKey(key: string): Promise<Expectation[]>;

    /** Retrieve the active expectations that have never matched a request. */
    retrieveUnmatchedExpectations(): Promise<Expectation[]>;

    retrieveRecordedExpectations(pathOrRequestDefinition: PathOrRequestDefinition): Promise<Expectation[]>;

    /** Retrieve one page of the recorded expectations: skip offset, return at most limit (integers zero or more). */
    retrieveRecordedExpectations(pathOrRequestDefinition: PathOrRequestDefinition, offset: number | undefined, limit?: number): Promise<Expectation[]>;

    retrieveExpectationsAsCode(format: CodeFormat, pathOrRequestDefinition?: PathOrRequestDefinition): Promise<string>;

    retrieveRecordedExpectationsAsCode(format: CodeFormat, pathOrRequestDefinition?: PathOrRequestDefinition): Promise<string>;

    retrieveLogMessages(pathOrRequestDefinition: PathOrRequestDefinition): Promise<string[]>;

    /**
     * Retrieve by expectation id instead of by request matcher. For
     * `retrieveActiveExpectationsById` only the expectation with that id is returned; the
     * other methods return the entries matching that expectation's request, exactly as
     * verify by expectation id matches them. An unknown id is rejected with a 400.
     */
    retrieveRecordedRequestsById(expectationId: string): Promise<RequestDefinition[]>;

    retrieveRecordedRequestsAndResponsesById(expectationId: string): Promise<HttpRequestAndHttpResponse[]>;

    retrieveActiveExpectationsById(expectationId: string): Promise<Expectation[]>;

    retrieveRecordedExpectationsById(expectationId: string): Promise<Expectation[]>;

    retrieveLogMessagesById(expectationId: string): Promise<string[]>;

    /**
     * Explain how a recorded request would be served now (PUT /mockserver/explain): the expectation
     * that would serve it, why each expectation ahead of it was passed over, and which it shadows.
     * Read-only. Throws for a blank correlation id; rejects with the server's reason when no request
     * is recorded with that id (never received, cleared or evicted).
     */
    explain(correlationId: string): Promise<RequestExplanation>;

    /**
     * Compare a request with every active expectation (PUT /mockserver/debugMismatch), with
     * field-level differences and the closest match. Rejects for a non-HTTP request definition.
     */
    debugMismatch(requestDefinition: RequestDefinition): Promise<DebugMismatchResult>;

    /**
     * Explain why recent requests matched no expectation (PUT /mockserver/explainUnmatched): each
     * unmatched request with its closest expectations, closest first.
     */
    explainUnmatched(options?: ExplainUnmatchedOptions): Promise<ExplainUnmatchedResult>;

    /**
     * Register a breakpoint matcher with per-phase handlers.
     * The callback WebSocket is opened lazily on the first call and reused.
     *
     * @param requestMatcher  the request definition to match (same shape as an expectation matcher)
     * @param phases          array of phases: "REQUEST", "RESPONSE", "RESPONSE_STREAM", "INBOUND_STREAM"
     * @param requestHandler  handler for REQUEST phase: (request) => request (continue/modify) or response (abort)
     * @param responseHandler handler for RESPONSE phase: (request, response) => response
     * @param streamFrameHandler handler for streaming phases: (pausedFrame) => {correlationId, action, body?}
     * @return promise resolving to the server-assigned breakpoint matcher id (string)
     */
    addBreakpoint(
        requestMatcher: RequestDefinition,
        phases: string[],
        requestHandler?: ((request: HttpRequest) => HttpRequest | HttpResponse) | null,
        responseHandler?: ((request: HttpRequest, response: HttpResponse) => HttpResponse) | null,
        streamFrameHandler?: ((pausedFrame: any) => any) | null,
    ): Promise<string>;

    /**
     * Register a request-only breakpoint (convenience).
     */
    addRequestBreakpoint(
        requestMatcher: RequestDefinition,
        requestHandler: (request: HttpRequest) => HttpRequest | HttpResponse,
    ): Promise<string>;

    /**
     * Register a request+response breakpoint (convenience).
     */
    addRequestAndResponseBreakpoint(
        requestMatcher: RequestDefinition,
        requestHandler: (request: HttpRequest) => HttpRequest | HttpResponse,
        responseHandler: (request: HttpRequest, response: HttpResponse) => HttpResponse,
    ): Promise<string>;

    /**
     * List all registered breakpoint matchers.
     */
    listBreakpointMatchers(): Promise<{ matchers: any[] }>;

    /**
     * Remove a breakpoint matcher by id.
     */
    removeBreakpointMatcher(breakpointId: string): Promise<RequestResponse>;

    /**
     * Clear all registered breakpoint matchers.
     */
    clearBreakpointMatchers(): Promise<RequestResponse>;

    /**
     * Upload a compiled gRPC proto descriptor set (a FileDescriptorSet, as
     * produced by `protoc --descriptor_set_out`). Registered services then
     * become available for gRPC mocking and can be queried with
     * retrieveGrpcServices().
     *
     * @param descriptorSetBytes the raw bytes of the compiled descriptor set
     */
    uploadGrpcDescriptor(descriptorSetBytes: Buffer | Uint8Array | ArrayBuffer): Promise<RequestResponse>;

    /**
     * Retrieve the gRPC services registered from uploaded descriptor sets.
     */
    retrieveGrpcServices(): Promise<GrpcService[]>;

    /**
     * Clear all registered gRPC descriptor sets and services.
     */
    clearGrpcDescriptors(): Promise<RequestResponse>;

    /**
     * Start building a mock MCP (Model Context Protocol) server that speaks
     * JSON-RPC 2.0 over the Streamable HTTP transport. Returns a fluent
     * builder; call .applyTo() (no argument — this client is used) to register
     * the generated expectations, or .build() for the raw expectation array.
     *
     * @param path the HTTP path the MCP server is mounted on (default "/mcp")
     */
    mcpMock(path?: string): McpMockBuilder;

    /**
     * Start building a mock A2A (Agent-to-Agent) agent: a static agent-card
     * document over GET plus JSON-RPC 2.0 task methods (tasks/send, tasks/get,
     * tasks/cancel) over POST, with optional SSE streaming and push
     * notifications. Returns a fluent builder; call .applyTo() (no argument —
     * this client is used) to register the generated expectations, or .build()
     * for the raw expectation array.
     *
     * @param path the HTTP path the A2A agent is mounted on (default "/a2a")
     */
    a2aMock(path?: string): A2aMockBuilder;

    /**
     * Explicit resource management support (TC39 `await using`). Resets the
     * MockServer and closes the callback WebSockets, as reset() does, when the
     * client goes out of scope, so tests do not need a manual
     * `afterEach(() => client.reset())`. Resolves once both are done.
     *
     * Present only on runtimes that define `Symbol.asyncDispose`.
     */
    [Symbol.asyncDispose]?(): Promise<RequestResponse>;

    /**
     * Synchronous explicit resource management support (TC39 `using`). Fires a
     * best-effort reset without awaiting it; prefer `await using` when the
     * reset must complete before the next test.
     *
     * Present only on runtimes that define `Symbol.dispose`.
     */
    [Symbol.dispose]?(): void;
}

/**
 * Start the client communicating at the specified host and port
 * for example:
 *
 *   var client = mockServerClient("localhost", 1080);
 *
 * @param host {string} the host for the server to communicate with
 * @param port {number} the port for the server to communicate with
 * @param contextPath {string} the context path if server was deployed as a war
 * @param tls {boolean} enable TLS (i.e. HTTPS) for communication to server
 * @param caCertPemFilePath {string} provide custom CA Certificate (defaults to MockServer CA Certificate)
 * @param options {MockServerClientOptions} optional control-plane bearer token and mutual-TLS client certificate/key
 */
export declare function mockServerClient(
    host: Host,
    port: Port,
    contextPath?: ContextPath,
    tls?: TLS,
    caCertPemFilePath?: CaCertPemFilePath,
    options?: MockServerClientOptions
): MockServerClient;

