using System.Globalization;
using System.Text;

namespace MockServer.Client.Models;

/// <summary>
/// Options that shape the expectations returned when retrieving recorded expectations, sent as query string
/// parameters on <c>PUT /mockserver/retrieve?type=RECORDED_EXPECTATIONS</c>. Each option is off unless set, and an
/// invalid value is rejected when it is set, before anything is sent.
/// </summary>
public sealed class RecordedExpectationsOptions
{
    /// <summary>True to collapse recorded exchanges that share a request shape into one unlimited-times expectation.</summary>
    public bool Consolidate { get; private set; }

    /// <summary>True to also generalise volatile path, query, header and body values into matchers (implies consolidate).</summary>
    public bool Parameterize { get; private set; }

    /// <summary>
    /// True to replay a request recorded several times with different responses in the recorded order, then keep
    /// returning the last one.
    /// </summary>
    public bool RepeatsAsScenarios { get; private set; }

    /// <summary>The request headers kept in each recorded matcher, or null to keep every recorded header.</summary>
    public IReadOnlyList<string>? CaptureHeaders { get; private set; }

    /// <summary>The body size in bytes above which response bodies are written to files, or null.</summary>
    public long? ExtractBodiesLargerThan { get; private set; }

    /// <summary>Create options with every option off.</summary>
    public static RecordedExpectationsOptions Options() => new();

    /// <summary>Collapse recorded exchanges that share a request shape into one unlimited-times expectation.</summary>
    public RecordedExpectationsOptions WithConsolidate(bool consolidate = true)
    {
        Consolidate = consolidate;
        return this;
    }

    /// <summary>Also generalise volatile path, query, header and body values into matchers (implies consolidate).</summary>
    public RecordedExpectationsOptions WithParameterize(bool parameterize = true)
    {
        Parameterize = parameterize;
        return this;
    }

    /// <summary>
    /// Replay a request recorded several times with different responses in the recorded order, then keep returning
    /// the last one.
    /// </summary>
    public RecordedExpectationsOptions WithRepeatsAsScenarios(bool repeatsAsScenarios = true)
    {
        RepeatsAsScenarios = repeatsAsScenarios;
        return this;
    }

    /// <summary>Keep only these request headers (case-insensitive) in each recorded matcher; no names keeps none.</summary>
    /// <exception cref="ArgumentException">a name is null, blank or contains a comma</exception>
    public RecordedExpectationsOptions WithCaptureHeaders(params string[] headerNames)
    {
        if (headerNames is null)
            throw new ArgumentNullException(nameof(headerNames));
        foreach (var name in headerNames)
        {
            if (string.IsNullOrWhiteSpace(name) || name.IndexOf(',') >= 0)
                throw new ArgumentException(
                    $"each capture header must be a non-blank header name without a comma, got \"{name}\"", nameof(headerNames));
        }
        CaptureHeaders = headerNames.ToList();
        return this;
    }

    /// <summary>
    /// Write each response body larger than this many bytes to the server's <c>recordedBodyFilesDirectory</c> and
    /// return a FILE body that points at it.
    /// </summary>
    /// <exception cref="ArgumentOutOfRangeException">the threshold is negative</exception>
    public RecordedExpectationsOptions WithExtractBodiesLargerThan(long thresholdBytes)
    {
        if (thresholdBytes < 0)
            throw new ArgumentOutOfRangeException(nameof(thresholdBytes), thresholdBytes, "must be zero or more");
        ExtractBodiesLargerThan = thresholdBytes;
        return this;
    }

    internal string ToQueryString()
    {
        var query = new StringBuilder();
        if (Consolidate)
            Append(query, "consolidate", "true");
        if (Parameterize)
            Append(query, "parameterize", "true");
        if (RepeatsAsScenarios)
            Append(query, "repeatsAsScenarios", "true");
        if (CaptureHeaders != null)
            Append(query, "captureHeaders", string.Join(",", CaptureHeaders));
        if (ExtractBodiesLargerThan.HasValue)
            Append(query, "extractBodiesLargerThan", ExtractBodiesLargerThan.Value.ToString(CultureInfo.InvariantCulture));
        return query.ToString();
    }

    private static void Append(StringBuilder query, string name, string value)
        => query.Append('&').Append(Uri.EscapeDataString(name)).Append('=').Append(Uri.EscapeDataString(value));
}
