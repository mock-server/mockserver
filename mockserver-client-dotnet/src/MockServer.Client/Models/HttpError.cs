using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// Represents an HTTP error action for MockServer (drops/corrupts connections).
/// </summary>
public sealed class HttpError
{
    [JsonPropertyName("dropConnection")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? DropConnection { get; set; }

    [JsonPropertyName("responseBytes")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ResponseBytes { get; set; }

    /// <summary>
    /// Optional delay applied before the error behaviour is triggered. Mirrors the server's
    /// <c>httpError.json</c> <c>delay</c> property.
    /// </summary>
    [JsonPropertyName("delay")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Delay? Delay { get; set; }

    /// <summary>
    /// Resets the matched request stream with this error code (HTTP/2 RST_STREAM / HTTP/3
    /// RESET_STREAM) instead of returning a response. Takes precedence over
    /// <see cref="DropConnection"/>; HTTP/1.1 has no stream concept. Mirrors the server's
    /// <c>httpError.json</c> <c>streamError</c> property.
    /// </summary>
    [JsonPropertyName("streamError")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public long? StreamError { get; set; }

    /// <summary>
    /// Resets the connection with a TCP RST, so the client sees "connection reset by peer".
    /// Takes precedence over <see cref="StreamError"/> and <see cref="DropConnection"/>. Mirrors the
    /// server's <c>httpError.json</c> <c>resetConnection</c> property.
    /// </summary>
    [JsonPropertyName("resetConnection")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? ResetConnection { get; set; }

    /// <summary>Marks this as the primary action when the expectation configures more than one.</summary>
    [JsonPropertyName("primary")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? Primary { get; set; }

    /// <summary>
    /// Creates a new HttpError builder.
    /// </summary>
    public static HttpErrorBuilder Error() => new();
}
