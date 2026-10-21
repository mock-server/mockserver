using System.Text.Json.Serialization;
using MockServer.Client.Llm;

namespace MockServer.Client.Models;

/// <summary>
/// Represents a MockServer expectation (request matcher + action).
/// </summary>
public sealed class Expectation
{
    [JsonPropertyName("id")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Id { get; set; }

    [JsonPropertyName("priority")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? Priority { get; set; }

    /// <summary>
    /// Probabilistic match percentage (0-100): when set, the expectation matches only this percentage
    /// of otherwise-matching requests. Drives simple probabilistic fault/behaviour injection.
    /// </summary>
    [JsonPropertyName("percentage")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? Percentage { get; set; }

    /// <summary>
    /// Declarative HTTP chaos / fault-injection profile applied to this expectation.
    /// </summary>
    [JsonPropertyName("chaos")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpChaosProfile? Chaos { get; set; }

    /// <summary>
    /// Declarative, protocol-agnostic rate limit / quota applied to this expectation.
    /// </summary>
    [JsonPropertyName("rateLimit")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public RateLimit? RateLimit { get; set; }

    [JsonPropertyName("httpRequest")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpRequest? HttpRequest { get; set; }

    [JsonPropertyName("httpResponse")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpResponse? HttpResponse { get; set; }

    /// <summary>
    /// Multiple responses for a stateful scenario. When set, takes priority over the singular
    /// <see cref="HttpResponse"/>; the response served per match is selected by <see cref="ResponseMode"/>.
    /// </summary>
    [JsonPropertyName("httpResponses")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<HttpResponse>? HttpResponses { get; set; }

    [JsonPropertyName("httpForward")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpForward? HttpForward { get; set; }

    [JsonPropertyName("httpResponseTemplate")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpTemplate? HttpResponseTemplate { get; set; }

    [JsonPropertyName("httpForwardTemplate")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpTemplate? HttpForwardTemplate { get; set; }

    /// <summary>
    /// Override-forwarded-request action: the matched request is forwarded after applying a
    /// request override, while a response template produces the caller's response.
    /// </summary>
    [JsonPropertyName("httpOverrideForwardedRequest")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpOverrideForwardedRequest? HttpOverrideForwardedRequest { get; set; }

    /// <summary>
    /// Forward action that falls back to a canned response when the upstream forward fails or returns
    /// a configured status code.
    /// </summary>
    [JsonPropertyName("httpForwardWithFallback")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpForwardWithFallback? HttpForwardWithFallback { get; set; }

    /// <summary>
    /// Forward action that validates the request and/or response against an OpenAPI spec.
    /// </summary>
    [JsonPropertyName("httpForwardValidateAction")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpForwardValidateAction? HttpForwardValidateAction { get; set; }

    [JsonPropertyName("httpError")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpError? HttpError { get; set; }

    /// <summary>
    /// Response class-callback action: a server-side class produces the response.
    /// REST-only — no WebSocket involved.
    /// </summary>
    [JsonPropertyName("httpResponseClassCallback")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpClassCallback? HttpResponseClassCallback { get; set; }

    /// <summary>
    /// Forward class-callback action: a server-side class produces the request to forward.
    /// REST-only — no WebSocket involved.
    /// </summary>
    [JsonPropertyName("httpForwardClassCallback")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpClassCallback? HttpForwardClassCallback { get; set; }

    /// <summary>
    /// Response object-callback action: the request is dispatched over the callback
    /// WebSocket to the client identified by <c>clientId</c>, whose closure produces the response.
    /// </summary>
    [JsonPropertyName("httpResponseObjectCallback")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpObjectCallback? HttpResponseObjectCallback { get; set; }

    /// <summary>
    /// Forward object-callback action: the request is dispatched over the callback
    /// WebSocket to the client identified by <c>clientId</c>, whose closure produces the request to forward.
    /// </summary>
    [JsonPropertyName("httpForwardObjectCallback")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpObjectCallback? HttpForwardObjectCallback { get; set; }

    [JsonPropertyName("httpSseResponse")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpSseResponse? HttpSseResponse { get; set; }

    [JsonPropertyName("httpWebSocketResponse")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpWebSocketResponse? HttpWebSocketResponse { get; set; }

    [JsonPropertyName("grpcStreamResponse")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public GrpcStreamResponse? GrpcStreamResponse { get; set; }

    /// <summary>
    /// Bidirectional-streaming gRPC response action.
    /// </summary>
    [JsonPropertyName("grpcBidiResponse")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public GrpcBidiResponse? GrpcBidiResponse { get; set; }

    [JsonPropertyName("binaryResponse")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public BinaryResponse? BinaryResponse { get; set; }

    [JsonPropertyName("dnsResponse")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public DnsResponse? DnsResponse { get; set; }

    [JsonPropertyName("httpLlmResponse")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpLlmResponse? HttpLlmResponse { get; set; }

    /// <summary>
    /// Side-effect actions run before the primary action produces a response.
    /// </summary>
    [JsonPropertyName("beforeActions")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<AfterAction>? BeforeActions { get; set; }

    /// <summary>
    /// Side-effect actions run after the primary action produces a response.
    /// </summary>
    [JsonPropertyName("afterActions")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<AfterAction>? AfterActions { get; set; }

    /// <summary>
    /// Ordered multi-step action sequence; exactly one step is the responder.
    /// </summary>
    [JsonPropertyName("steps")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<ExpectationStep>? Steps { get; set; }

    /// <summary>
    /// Value-capture rules that extract values from the matched request into scenario state.
    /// </summary>
    [JsonPropertyName("capture")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<CaptureRule>? Capture { get; set; }

    /// <summary>
    /// Optional namespace (tenant) this expectation belongs to; when set, the expectation only matches
    /// requests carrying the configured match-namespace header with this value and stays isolated from
    /// other namespaces.
    /// </summary>
    [JsonPropertyName("namespace")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Namespace { get; set; }

    /// <summary>
    /// Optional free-form metadata (string keys, any JSON value) used to group expectations, for example by
    /// owning team or ticket, and to retrieve or clear them by it with <see cref="ExpectationFilter"/>. It is not
    /// used for request matching. Values read back from the server are <see cref="System.Text.Json.JsonElement"/>.
    /// </summary>
    [JsonPropertyName("metadata")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Dictionary<string, object?>? Metadata { get; set; }

    [JsonPropertyName("scenarioName")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ScenarioName { get; set; }

    [JsonPropertyName("scenarioState")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ScenarioState { get; set; }

    [JsonPropertyName("newScenarioState")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? NewScenarioState { get; set; }

    /// <summary>
    /// How a response is selected from <see cref="HttpResponses"/> on each match:
    /// SEQUENTIAL (default), RANDOM, WEIGHTED or SWITCH.
    /// </summary>
    [JsonPropertyName("responseMode")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public ResponseMode? ResponseMode { get; set; }

    /// <summary>
    /// Relative weights index-aligned with <see cref="HttpResponses"/>; only meaningful when
    /// <see cref="ResponseMode"/> is <see cref="Models.ResponseMode.WEIGHTED"/>.
    /// </summary>
    [JsonPropertyName("responseWeights")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<int>? ResponseWeights { get; set; }

    /// <summary>
    /// Number of requests served per response block before advancing to the next; only meaningful when
    /// <see cref="ResponseMode"/> is <see cref="Models.ResponseMode.SWITCH"/> (default 1 on the server).
    /// </summary>
    [JsonPropertyName("switchAfter")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? SwitchAfter { get; set; }

    /// <summary>
    /// Cross-protocol triggers that advance a named scenario when a DNS query, WebSocket connect,
    /// gRPC request or HTTP request is observed.
    /// </summary>
    [JsonPropertyName("crossProtocolScenarios")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<CrossProtocolScenario>? CrossProtocolScenarios { get; set; }

    [JsonPropertyName("times")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Times? Times { get; set; }

    [JsonPropertyName("timeToLive")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public TimeToLive? TimeToLive { get; set; }

    /// <summary>
    /// Optional timestamp carried so request/response log output can be round-tripped into an
    /// expectation. Preserved verbatim; not otherwise interpreted by the client.
    /// </summary>
    [JsonPropertyName("timestamp")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Timestamp { get; set; }
}
