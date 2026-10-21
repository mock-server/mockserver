using System.Text.Json;
using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// Represents a template action for MockServer (response template or forward template).
/// The template engine processes the template content against the incoming request.
/// </summary>
public sealed class HttpTemplate
{
    [JsonPropertyName("templateType")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    [JsonConverter(typeof(JsonStringEnumConverter))]
    public TemplateType? TemplateType { get; set; }

    [JsonPropertyName("template")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Template { get; set; }

    [JsonPropertyName("templateFile")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? TemplateFile { get; set; }

    /// <summary>
    /// Values the template reads as <c>parameters</c>, such as <c>$parameters.greeting</c> (Velocity),
    /// <c>{{ parameters.greeting }}</c> (Mustache) or <c>parameters.greeting</c> (JavaScript). Each value is any JSON
    /// value. Omitted when null.
    /// </summary>
    [JsonPropertyName("parameters")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Dictionary<string, JsonElement>? Parameters { get; set; }

    /// <summary>Optional delay applied before the templated action runs.</summary>
    [JsonPropertyName("delay")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Delay? Delay { get; set; }

    /// <summary>Marks this as the primary action when the expectation configures more than one.</summary>
    [JsonPropertyName("primary")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? Primary { get; set; }

    /// <summary>
    /// Creates a new HttpTemplate builder with the specified template type.
    /// </summary>
    public static HttpTemplateBuilder OfType(TemplateType type) => new(type);
}

/// <summary>
/// Template engine types supported by MockServer.
/// </summary>
[JsonConverter(typeof(JsonStringEnumConverter))]
public enum TemplateType
{
    JAVASCRIPT,
    VELOCITY,
    MUSTACHE
}
