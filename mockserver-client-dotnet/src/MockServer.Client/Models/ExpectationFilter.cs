using System.Text;
using System.Text.Json;

namespace MockServer.Client.Models;

/// <summary>
/// Control-plane filter for retrieving and clearing expectations by metadata, by whether they have ever
/// matched a request, and for paging the retrieved list. It is sent as query string parameters on
/// <c>PUT /mockserver/retrieve</c> and <c>PUT /mockserver/clear</c>.
/// <list type="bullet">
/// <item><c>metadataKey</c> (with optional <c>metadataValue</c>): the metadata has that key and, when a value is
/// given, the value equals it. A <see cref="string"/> is compared as text, any other value as JSON.</item>
/// <item><c>metadataJsonPath</c>: the JSONPath selects at least one value from the metadata object.</item>
/// <item><c>unmatched=true</c>: the expectation has not matched any request.</item>
/// <item><c>offset</c> / <c>limit</c>: page the retrieved result (retrieve only; never on clear).</item>
/// </list>
/// Every invalid selection is rejected when it is set, before anything is sent: on clear, a selection that
/// silently read as "no filter" would clear every expectation.
/// </summary>
public sealed class ExpectationFilter
{
    /// <summary>
    /// An explicit JSON <c>null</c>, for selecting a metadata key whose value is JSON null via
    /// <see cref="WithMetadata"/>. A C# <c>null</c> is rejected there, because it must not read as "any value".
    /// </summary>
    public static readonly JsonElement JsonNull = JsonDocument.Parse("null").RootElement.Clone();

    // Same naming and null handling as the client's expectation serialization, so a value compares as stored.
    private static readonly JsonSerializerOptions ValueJsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    /// <summary>The metadata key to select on, or null.</summary>
    public string? MetadataKey { get; private set; }

    /// <summary>The wire form of the metadata value to compare with, or null for any value.</summary>
    public string? MetadataValue { get; private set; }

    /// <summary>The JSONPath evaluated against the metadata object, or null.</summary>
    public string? MetadataJsonPath { get; private set; }

    /// <summary>True to select only expectations that have never matched a request.</summary>
    public bool Unmatched { get; private set; }

    /// <summary>The number of results to skip, or null.</summary>
    public int? Offset { get; private set; }

    /// <summary>The maximum number of results to return, or null.</summary>
    public int? Limit { get; private set; }

    /// <summary>Create an empty filter.</summary>
    public static ExpectationFilter Filter() => new();

    /// <summary>
    /// Select expectations whose metadata has <paramref name="key"/> with a value equal to <paramref name="value"/>.
    /// A <see cref="string"/> is sent as raw text; any other value (number, bool, list, dictionary,
    /// <see cref="JsonElement"/>, <see cref="JsonNull"/>) is sent as its compact JSON.
    /// </summary>
    /// <exception cref="ArgumentException">the key is null or blank</exception>
    /// <exception cref="ArgumentNullException">the value is null (use <see cref="WithMetadataKey"/> for any value,
    /// or <see cref="JsonNull"/> for a JSON null)</exception>
    public ExpectationFilter WithMetadata(string key, object value)
    {
        if (value is null)
            throw new ArgumentNullException(nameof(value),
                "the metadata value must not be null; use WithMetadataKey(key) to select any value, or ExpectationFilter.JsonNull to select a JSON null");
        MetadataKey = RequireNotBlank(key, "metadata key", nameof(key));
        MetadataValue = value as string ?? ToJson(value);
        return this;
    }

    /// <summary>Select expectations whose metadata has <paramref name="key"/>, whatever its value.</summary>
    /// <exception cref="ArgumentException">the key is null or blank</exception>
    public ExpectationFilter WithMetadataKey(string key)
    {
        MetadataKey = RequireNotBlank(key, "metadata key", nameof(key));
        MetadataValue = null;
        return this;
    }

    /// <summary>
    /// Select expectations whose metadata object the JSONPath selects at least one value from,
    /// for example <c>$[?(@.owner == 'team-a')]</c>.
    /// </summary>
    /// <exception cref="ArgumentException">the JSONPath is null or blank</exception>
    public ExpectationFilter WithMetadataJsonPath(string jsonPath)
    {
        MetadataJsonPath = RequireNotBlank(jsonPath, "metadata JSONPath", nameof(jsonPath));
        return this;
    }

    /// <summary>Select only expectations that have not matched any request.</summary>
    public ExpectationFilter WithUnmatched(bool unmatched = true)
    {
        Unmatched = unmatched;
        return this;
    }

    /// <summary>Skip this many results (retrieve only).</summary>
    /// <exception cref="ArgumentOutOfRangeException">the offset is negative</exception>
    public ExpectationFilter WithOffset(int offset)
    {
        Offset = RequireNonNegative(offset, nameof(offset));
        return this;
    }

    /// <summary>Return at most this many results (retrieve only).</summary>
    /// <exception cref="ArgumentOutOfRangeException">the limit is negative</exception>
    public ExpectationFilter WithLimit(int limit)
    {
        Limit = RequireNonNegative(limit, nameof(limit));
        return this;
    }

    /// <summary>True when this filter selects a subset of expectations (metadata or unmatched).</summary>
    public bool HasSelection =>
        !string.IsNullOrWhiteSpace(MetadataKey) || !string.IsNullOrWhiteSpace(MetadataJsonPath) || Unmatched;

    /// <summary>True when this filter pages the result.</summary>
    public bool HasPaging => Offset.HasValue || Limit.HasValue;

    /// <summary>
    /// The query string parameters, each starting with <c>&amp;</c>, with every name and value percent-encoded
    /// literally (a leading <c>!</c> or <c>?</c> is data, not a not/optional marker).
    /// </summary>
    internal string ToQueryString()
    {
        var query = new StringBuilder();
        if (!string.IsNullOrWhiteSpace(MetadataKey))
        {
            Append(query, "metadataKey", MetadataKey!);
            if (MetadataValue != null)
                Append(query, "metadataValue", MetadataValue);
        }
        if (!string.IsNullOrWhiteSpace(MetadataJsonPath))
            Append(query, "metadataJsonPath", MetadataJsonPath!);
        if (Unmatched)
            Append(query, "unmatched", "true");
        if (Limit.HasValue)
            Append(query, "limit", Limit.Value.ToString(System.Globalization.CultureInfo.InvariantCulture));
        if (Offset.HasValue)
            Append(query, "offset", Offset.Value.ToString(System.Globalization.CultureInfo.InvariantCulture));
        return query.ToString();
    }

    private static void Append(StringBuilder query, string name, string value)
        => query.Append('&').Append(Uri.EscapeDataString(name)).Append('=').Append(Uri.EscapeDataString(value));

    private static string ToJson(object value) => value switch
    {
        JsonElement element => element.GetRawText(),
        System.Text.Json.Nodes.JsonNode node => node.ToJsonString(),
        _ => JsonSerializer.Serialize(value, value.GetType(), ValueJsonOptions)
    };

    private static string RequireNotBlank(string value, string description, string paramName)
    {
        if (string.IsNullOrWhiteSpace(value))
            throw new ArgumentException($"the {description} must not be blank", paramName);
        return value;
    }

    private static int RequireNonNegative(int value, string paramName)
    {
        if (value < 0)
            throw new ArgumentOutOfRangeException(paramName, value, $"the {paramName} must be zero or more");
        return value;
    }
}
