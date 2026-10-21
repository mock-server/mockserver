using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// Represents a FILE body type for MockServer. The file contents are served as the response body.
/// When <see cref="TemplateType"/> is set, the file is processed by the named template engine
/// against the request before being returned.
/// </summary>
public sealed class FileBody
{
    [JsonPropertyName("type")]
    public string Type { get; set; } = "FILE";

    [JsonPropertyName("filePath")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? FilePath { get; set; }

    [JsonPropertyName("contentType")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ContentType { get; set; }

    [JsonPropertyName("templateType")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    [JsonConverter(typeof(JsonStringEnumConverter))]
    public FileTemplateType? TemplateType { get; set; }

    /// <summary>
    /// When <c>true</c>, and <see cref="TemplateType"/> is set, <see cref="FilePath"/> is itself rendered
    /// as a template against the request before the file is read. Omitted from the JSON when <c>false</c>.
    /// </summary>
    [JsonPropertyName("templatedFilePath")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingDefault)]
    public bool TemplatedFilePath { get; set; }
}

/// <summary>
/// Template engines supported for FILE body templating. JavaScript is intentionally excluded —
/// JavaScript templates build a full response object rather than a text fragment, so they are only
/// supported via <see cref="HttpTemplate"/> (httpResponseTemplate), not on a FILE body.
/// </summary>
public enum FileTemplateType
{
    VELOCITY,
    MUSTACHE
}
