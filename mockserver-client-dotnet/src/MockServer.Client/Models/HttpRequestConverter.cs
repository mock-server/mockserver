using System.Linq;
using System.Runtime.CompilerServices;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// <see cref="System.Text.Json"/> converter that lets <see cref="HttpRequest"/>'s matcher maps
/// (<see cref="HttpRequest.HeaderMatchers"/> and friends) stand in for their plain-string
/// counterparts under the SAME wire key.
/// </summary>
/// <remarks>
/// A header, query parameter, cookie or path-parameter value whose first character is <c>'!'</c> or
/// <c>'?'</c> is a marker the server strips when reading, so <c>Headers["X"] = ["!foo"]</c> asks for
/// "X is anything but foo", not "X is !foo". A <see cref="MatcherValue"/> keeps the text and the flags
/// apart and escapes to the object form only when the plain form would be misread; a date-time matcher
/// (<c>{"after":"now -1 hours"}</c>) is always an object, so it always lands in the matcher map. This converter is
/// the sibling-aware serialisation that the additive matcher maps need: a per-property converter
/// cannot consult a sibling field to decide whether to override the plain map.
/// <para>
/// It is registered on the client's <see cref="JsonSerializerOptions"/> (not via a type attribute) so
/// it can default-serialise the rest of the request through a recursion-free copy of those options,
/// keeping every other field byte-identical to the plain model without re-listing it here.
/// </para>
/// <para>
/// The escape is symmetric: the read half decodes both wire forms, so retrieving an expectation that
/// carries an escaped value — written by this client, a MockServer, or another client — round-trips.
/// A field whose values all survive the plain form is mirrored into the plain map (matcher map left
/// null), so ordinary traffic decodes exactly as before. An object-form <c>path</c> or <c>method</c>,
/// which this model types as a plain string, is flattened to its plain string rather than abandoning
/// the whole request (that would drop the response); it is recorded as a residual gap in the
/// round-trip fidelity ledger.
/// </para>
/// </remarks>
public sealed class HttpRequestConverter : JsonConverter<HttpRequest>
{
    // A recursion-free copy of the caller's options with THIS converter removed, so the "rest" of the
    // request can be (de)serialised the default way. Cached per options instance — there are only a
    // handful in a process — because a fresh JsonSerializerOptions defeats System.Text.Json's metadata
    // cache and is slow to build.
    private static readonly ConditionalWeakTable<JsonSerializerOptions, JsonSerializerOptions> InnerOptionsCache = new();

    private JsonSerializerOptions Inner(JsonSerializerOptions options) =>
        InnerOptionsCache.GetValue(options, source =>
        {
            var copy = new JsonSerializerOptions(source);
            for (var i = copy.Converters.Count - 1; i >= 0; i--)
            {
                if (copy.Converters[i] is HttpRequestConverter)
                {
                    copy.Converters.RemoveAt(i);
                }
            }
            return copy;
        });

    public override void Write(Utf8JsonWriter writer, HttpRequest value, JsonSerializerOptions options)
    {
        var node = JsonSerializer.SerializeToNode(value, Inner(options))!.AsObject();

        // A non-empty matcher map REPLACES the plain map that default serialisation already emitted (if
        // any) under the same key. Empty/null matcher maps leave the plain field untouched.
        ReplaceWithMatchers(node, "headers", value.HeaderMatchers, options);
        ReplaceWithMatchers(node, "queryStringParameters", value.QueryStringParameterMatchers, options);
        ReplaceWithMatchers(node, "cookies", value.CookieMatchers, options);
        ReplaceWithMatchers(node, "pathParameters", value.PathParameterMatchers, options);

        // Conditional and anyOf branches go back through this converter so their own matcher maps survive.
        WriteBranch(node, "if", value.If, options);
        WriteBranch(node, "then", value.Then, options);
        WriteBranch(node, "else", value.Else, options);
        if (value.AnyOf is not null)
        {
            node["anyOf"] = JsonSerializer.SerializeToNode(value.AnyOf, options);
        }

        node.WriteTo(writer, options);
    }

    private static void ReplaceWithMatchers<TMap>(JsonObject node, string key, TMap? matchers, JsonSerializerOptions options)
        where TMap : class
    {
        switch (matchers)
        {
            case Dictionary<string, List<MatcherValue>> multi when multi.Count > 0:
                node[key] = JsonSerializer.SerializeToNode(multi, options);
                break;
            case Dictionary<string, MatcherValue> single when single.Count > 0:
                node[key] = JsonSerializer.SerializeToNode(single, options);
                break;
        }
    }

    private static void WriteBranch(JsonObject node, string key, HttpRequest? branch, JsonSerializerOptions options)
    {
        if (branch is not null)
        {
            node[key] = JsonSerializer.SerializeToNode(branch, options);
        }
    }

    public override HttpRequest Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (JsonNode.Parse(ref reader) is not JsonObject node)
        {
            throw new JsonException("Expected a JSON object for httpRequest");
        }

        // Detach the matcher-capable object-map fields so the default deserialisation below does not
        // choke on an object-form value in a string-typed field. A non-object (e.g. array) form is left
        // in place and decodes exactly as it did before (which for this model means it is unsupported).
        var headers = DetachObject(node, "headers");
        var query = DetachObject(node, "queryStringParameters");
        var cookies = DetachObject(node, "cookies");
        // pathParameters is left in place: it deserialises into List<JsonElement>, which round-trips the
        // nottable and date-time object forms AND the schema-matcher form ({"schema":{...}}) losslessly.

        // path/method are plain strings on this model; tolerate an object-form (nottable) value by
        // flattening it to the plain string, so the rest of the request still decodes.
        FlattenScalar(node, "path");
        FlattenScalar(node, "method");

        // Conditional and anyOf branches are read back through this converter (recursively), so a branch's
        // object-form matcher values decode as they do at the top level.
        var ifBranch = DetachObject(node, "if");
        var thenBranch = DetachObject(node, "then");
        var elseBranch = DetachObject(node, "else");
        JsonNode? anyOfBranches = null;
        if (node.TryGetPropertyValue("anyOf", out var anyOf) && anyOf is JsonArray)
        {
            node.Remove("anyOf");
            anyOfBranches = anyOf;
        }

        var request = node.Deserialize<HttpRequest>(Inner(options))!;

        request.If = ifBranch?.Deserialize<HttpRequest>(options) ?? request.If;
        request.Then = thenBranch?.Deserialize<HttpRequest>(options) ?? request.Then;
        request.Else = elseBranch?.Deserialize<HttpRequest>(options) ?? request.Else;
        request.AnyOf = anyOfBranches?.Deserialize<List<HttpRequest>>(options) ?? request.AnyOf;

        DecodeMulti(headers, options, plain => request.Headers = plain, matchers => request.HeaderMatchers = matchers);
        DecodeMulti(query, options, plain => request.QueryStringParameters = plain, matchers => request.QueryStringParameterMatchers = matchers);
        DecodeSingle(cookies, options, plain => request.Cookies = plain, matchers => request.CookieMatchers = matchers);

        return request;
    }

    private static JsonNode? DetachObject(JsonObject node, string key)
    {
        if (node.TryGetPropertyValue(key, out var value) && value is JsonObject)
        {
            node.Remove(key);
            return value;
        }
        return null;
    }

    private static void FlattenScalar(JsonObject node, string key)
    {
        if (node.TryGetPropertyValue(key, out var value) && value is JsonObject obj)
        {
            node[key] = JsonValue.Create(obj.Deserialize<MatcherValue>().Serialise());
        }
    }

    private static void DecodeMulti(
        JsonNode? raw,
        JsonSerializerOptions options,
        Action<Dictionary<string, List<string>>> setPlain,
        Action<Dictionary<string, List<MatcherValue>>> setMatchers)
    {
        if (raw is not JsonObject obj)
        {
            return;
        }

        var decoded = new Dictionary<string, List<MatcherValue>>();
        var needsMatchers = false;
        foreach (var entry in obj)
        {
            var values = new List<MatcherValue>();
            if (entry.Value is JsonArray array)
            {
                foreach (var element in array)
                {
                    var matcher = element is null ? MatcherValue.Literal(string.Empty) : element.Deserialize<MatcherValue>(options);
                    values.Add(matcher);
                    needsMatchers |= matcher.IsAmbiguous();
                }
            }
            else if (entry.Value is not null)
            {
                var matcher = entry.Value.Deserialize<MatcherValue>(options);
                values.Add(matcher);
                needsMatchers |= matcher.IsAmbiguous();
            }
            decoded[entry.Key] = values;
        }

        if (needsMatchers)
        {
            setMatchers(decoded);
            return;
        }

        // Every value survives the plain form: mirror into the plain map so plain-form traffic decodes
        // exactly as it always did and re-serialises byte-identically.
        setPlain(decoded.ToDictionary(kv => kv.Key, kv => kv.Value.Select(v => v.Serialise()).ToList()));
    }

    private static void DecodeSingle(
        JsonNode? raw,
        JsonSerializerOptions options,
        Action<Dictionary<string, string>> setPlain,
        Action<Dictionary<string, MatcherValue>> setMatchers)
    {
        if (raw is not JsonObject obj)
        {
            return;
        }

        var decoded = new Dictionary<string, MatcherValue>();
        var needsMatchers = false;
        foreach (var entry in obj)
        {
            var matcher = entry.Value is null ? MatcherValue.Literal(string.Empty) : entry.Value.Deserialize<MatcherValue>(options);
            decoded[entry.Key] = matcher;
            needsMatchers |= matcher.IsAmbiguous();
        }

        if (needsMatchers)
        {
            setMatchers(decoded);
            return;
        }

        setPlain(decoded.ToDictionary(kv => kv.Key, kv => kv.Value.Serialise()));
    }
}
