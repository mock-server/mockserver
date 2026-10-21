using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// Policy governing how a failing before/after side-effect action affects request handling.
/// </summary>
[JsonConverter(typeof(JsonStringEnumConverter))]
public enum ActionFailurePolicy
{
    /// <summary>Abort and surface the failure.</summary>
    FAIL_FAST,

    /// <summary>Log and continue on failure.</summary>
    BEST_EFFORT
}

/// <summary>
/// A before/after side-effect action attached to an expectation via <see cref="Expectation.BeforeActions"/>
/// or <see cref="Expectation.AfterActions"/>. Exactly one of <see cref="HttpRequest"/>,
/// <see cref="HttpRequestTemplate"/>, <see cref="HttpClassCallback"/> or <see cref="HttpObjectCallback"/> is set. Mirrors the server's
/// <c>afterAction</c> schema.
/// </summary>
public sealed class AfterAction
{
    /// <summary>Fire-and-forget (or blocking) HTTP request to send as a side effect.</summary>
    [JsonPropertyName("httpRequest")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpRequest? HttpRequest { get; set; }

    /// <summary>
    /// A webhook whose request a Velocity, Mustache or JavaScript template renders from the triggering request.
    /// Escape request values in the template and keep them out of the destination.
    /// </summary>
    [JsonPropertyName("httpRequestTemplate")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpTemplate? HttpRequestTemplate { get; set; }

    [JsonPropertyName("httpClassCallback")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpClassCallback? HttpClassCallback { get; set; }

    [JsonPropertyName("httpObjectCallback")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpObjectCallback? HttpObjectCallback { get; set; }

    [JsonPropertyName("delay")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Delay? Delay { get; set; }

    /// <summary>When true the action runs to completion before request handling continues.</summary>
    [JsonPropertyName("blocking")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? Blocking { get; set; }

    [JsonPropertyName("timeout")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Delay? Timeout { get; set; }

    [JsonPropertyName("failurePolicy")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public ActionFailurePolicy? FailurePolicy { get; set; }
}

/// <summary>
/// One step of a multi-step expectation (<see cref="Expectation.Steps"/>). Exactly one action field is
/// set. Mirrors the server's <c>expectationStep</c> schema.
/// </summary>
public sealed class ExpectationStep
{
    [JsonPropertyName("httpRequest")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpRequest? HttpRequest { get; set; }

    [JsonPropertyName("httpClassCallback")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpClassCallback? HttpClassCallback { get; set; }

    [JsonPropertyName("httpObjectCallback")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpObjectCallback? HttpObjectCallback { get; set; }

    [JsonPropertyName("httpForward")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpForward? HttpForward { get; set; }

    [JsonPropertyName("httpOverrideForwardedRequest")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpOverrideForwardedRequest? HttpOverrideForwardedRequest { get; set; }

    [JsonPropertyName("httpResponse")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpResponse? HttpResponse { get; set; }

    [JsonPropertyName("httpError")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpError? HttpError { get; set; }

    /// <summary>When true this step produces the response returned to the caller.</summary>
    [JsonPropertyName("responder")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? Responder { get; set; }

    [JsonPropertyName("delay")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Delay? Delay { get; set; }

    [JsonPropertyName("blocking")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? Blocking { get; set; }

    [JsonPropertyName("timeout")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Delay? Timeout { get; set; }

    [JsonPropertyName("failurePolicy")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public ActionFailurePolicy? FailurePolicy { get; set; }
}

/// <summary>
/// A value-capture rule that extracts a value from the matched request into scenario state
/// (<see cref="Expectation.Capture"/>). Mirrors the server's <c>captureRule</c> schema.
/// </summary>
public sealed class CaptureRule
{
    /// <summary>
    /// Where to read the value from: <c>jsonPath</c>, <c>xpath</c>, <c>header</c>,
    /// <c>queryStringParameter</c>, <c>cookie</c> or <c>pathParameter</c>.
    /// </summary>
    [JsonPropertyName("source")]
    public string? Source { get; set; }

    /// <summary>The expression / key evaluated against the chosen <see cref="Source"/>.</summary>
    [JsonPropertyName("expression")]
    public string? Expression { get; set; }

    /// <summary>The scenario-state variable name the captured value is stored under.</summary>
    [JsonPropertyName("into")]
    public string? Into { get; set; }

    /// <summary>Creates a capture rule.</summary>
    public static CaptureRule Of(string source, string expression, string into) =>
        new() { Source = source, Expression = expression, Into = into };
}
