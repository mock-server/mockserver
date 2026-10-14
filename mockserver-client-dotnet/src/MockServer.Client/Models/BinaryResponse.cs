using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// Represents a raw binary response action for MockServer. The
/// <see cref="BinaryData"/> field carries a Base64-encoded payload that is
/// written verbatim to the connection (used for non-HTTP binary protocols).
/// </summary>
public sealed class BinaryResponse
{
    /// <summary>
    /// Base64-encoded response payload.
    /// </summary>
    [JsonPropertyName("binaryData")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? BinaryData { get; set; }

    [JsonPropertyName("delay")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Delay? Delay { get; set; }

    [JsonPropertyName("primary")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? Primary { get; set; }

    /// <summary>
    /// What happens upstream to the matched message on a relayed connection;
    /// null means the server's default, <see cref="BinaryUpstream.ANSWER_ONLY"/>.
    /// </summary>
    [JsonPropertyName("upstream")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public BinaryUpstream? Upstream { get; set; }

    /// <summary>
    /// Creates a new binary response builder.
    /// </summary>
    public static BinaryResponseBuilder Response() => new();
}

/// <summary>
/// What happens upstream to a message a binary expectation matches on a
/// connection MockServer relays to an upstream (matchBinaryExpectationsWhenProxying).
/// ANSWER_AND_FORWARD and FORWARD_AND_REPLACE need binaryMessageFraming POSTGRESQL;
/// without it the server answers as ANSWER_ONLY.
/// </summary>
[JsonConverter(typeof(JsonStringEnumConverter))]
public enum BinaryUpstream
{
    /// <summary>Write the binary data; do not forward the message.</summary>
    ANSWER_ONLY,
    /// <summary>Write the binary data, forward the message and drop the upstream's reply.</summary>
    ANSWER_AND_FORWARD,
    /// <summary>Forward the message and write the binary data in place of the upstream's reply.</summary>
    FORWARD_AND_REPLACE
}
