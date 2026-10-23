using System.Text.Json;
using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// <see cref="System.Text.Json"/> converter for <see cref="Body"/>. Reads any of MockServer's body
/// wire shapes into the typed model and writes them back losslessly.
/// </summary>
/// <remarks>
/// Read strategy:
/// <list type="bullet">
/// <item>A bare JSON string / array, or an object <b>without</b> a <c>type</c> discriminator, is a
/// shorthand body — captured verbatim in <see cref="Body.RawShorthand"/>.</item>
/// <item>An object <b>with</b> a <c>type</c> is decomposed into the typed fields; unrecognised
/// properties are retained in <see cref="Body.AdditionalProperties"/>.</item>
/// </list>
/// Write strategy mirrors this: a shorthand body is re-emitted verbatim; otherwise a JSON object is
/// built from the non-null typed fields followed by any <see cref="Body.AdditionalProperties"/>.
/// </remarks>
public sealed class BodyJsonConverter : JsonConverter<Body>
{
    public override Body Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        using var doc = JsonDocument.ParseValue(ref reader);
        var root = doc.RootElement;

        // Shorthand: bare string or bare array -> keep verbatim.
        if (root.ValueKind is JsonValueKind.String or JsonValueKind.Array
            or JsonValueKind.Number or JsonValueKind.True or JsonValueKind.False or JsonValueKind.Null)
        {
            return new Body { RawShorthand = root.Clone() };
        }

        if (root.ValueKind != JsonValueKind.Object)
        {
            return new Body { RawShorthand = root.Clone() };
        }

        // Object shorthand (no type discriminator) -> keep verbatim (matches the server's
        // "additionalProperties: true" bare-JSON-object body form).
        if (!root.TryGetProperty("type", out var typeEl) || typeEl.ValueKind != JsonValueKind.String)
        {
            return new Body { RawShorthand = root.Clone() };
        }

        var body = new Body { Type = typeEl.GetString() };

        foreach (var prop in root.EnumerateObject())
        {
            switch (prop.Name)
            {
                case "type":
                    break;
                case "not":
                    body.Not = prop.Value.GetBoolean();
                    break;
                case "optional":
                    body.Optional = prop.Value.GetBoolean();
                    break;
                case "contentType":
                    body.ContentType = prop.Value.GetString();
                    break;
                case "string":
                    body.StringValue = prop.Value.GetString();
                    break;
                case "subString":
                    body.SubString = prop.Value.GetBoolean();
                    break;
                case "json":
                    body.Json = prop.Value.GetString();
                    break;
                case "matchType":
                    body.MatchType = prop.Value.GetString();
                    break;
                case "jsonSchema":
                    body.JsonSchema = prop.Value.Clone();
                    break;
                case "jsonPath":
                    body.JsonPath = prop.Value.GetString();
                    break;
                case "xml":
                    body.Xml = prop.Value.GetString();
                    break;
                case "xmlSchema":
                    body.XmlSchema = prop.Value.GetString();
                    break;
                case "xpath":
                    body.Xpath = prop.Value.GetString();
                    break;
                case "dateTime":
                    body.DateTime = prop.Value.Deserialize<MatcherValue>(options);
                    break;
                case "regex":
                    body.Regex = prop.Value.GetString();
                    break;
                case "base64Bytes":
                    body.Base64Bytes = prop.Value.GetString();
                    break;
                case "parameters":
                    body.Parameters = ReadKeyToMultiValue(prop.Value);
                    break;
                case "filenames":
                    body.Filenames = ReadKeyToMultiValue(prop.Value);
                    break;
                case "partContentTypes":
                    body.PartContentTypes = ReadKeyToMultiValue(prop.Value);
                    break;
                case "fields":
                    // Ambiguous by wire name: MULTIPART -> object (keyToMultiValue); GRAPHQL -> array of strings.
                    if (prop.Value.ValueKind == JsonValueKind.Array)
                    {
                        body.GraphQlFields = prop.Value.EnumerateArray()
                            .Select(e => e.GetString() ?? string.Empty).ToList();
                    }
                    else
                    {
                        body.MultipartFields = ReadKeyToMultiValue(prop.Value);
                    }
                    break;
                case "method":
                    body.Method = prop.Value.GetString();
                    break;
                case "paramsSchema":
                    body.ParamsSchema = prop.Value.GetString();
                    break;
                case "query":
                    body.Query = prop.Value.GetString();
                    break;
                case "operationName":
                    body.OperationName = prop.Value.GetString();
                    break;
                case "variablesSchema":
                    body.VariablesSchema = prop.Value.GetString();
                    break;
                case "selectionSetMatchType":
                    body.SelectionSetMatchType = prop.Value.GetString();
                    break;
                case "schema":
                    body.Schema = prop.Value.GetString();
                    break;
                case "fuzzy":
                    body.Fuzzy = prop.Value.GetString();
                    break;
                case "threshold":
                    body.Threshold = prop.Value.GetDouble();
                    break;
                case "ignoreCase":
                    body.IgnoreCase = prop.Value.GetBoolean();
                    break;
                case "filePath":
                    body.FilePath = prop.Value.GetString();
                    break;
                case "templateType":
                    body.TemplateType = prop.Value.GetString();
                    break;
                case "templatedFilePath":
                    body.TemplatedFilePath = prop.Value.GetBoolean();
                    break;
                case "moduleName":
                    body.ModuleName = prop.Value.GetString();
                    break;
                case "bodyAllOf":
                    body.BodyAllOf = prop.Value.EnumerateArray()
                        .Select(e => e.Deserialize<Body>(options)!).ToList();
                    break;
                default:
                    (body.AdditionalProperties ??= new Dictionary<string, JsonElement>())[prop.Name] = prop.Value.Clone();
                    break;
            }
        }

        return body;
    }

    public override void Write(Utf8JsonWriter writer, Body value, JsonSerializerOptions options)
    {
        if (value.RawShorthand is JsonElement shorthand)
        {
            shorthand.WriteTo(writer);
            return;
        }

        writer.WriteStartObject();

        if (value.Type is not null) writer.WriteString("type", value.Type);
        if (value.Not is bool not) writer.WriteBoolean("not", not);
        if (value.Optional is bool optional) writer.WriteBoolean("optional", optional);
        if (value.ContentType is not null) writer.WriteString("contentType", value.ContentType);

        if (value.StringValue is not null) writer.WriteString("string", value.StringValue);
        if (value.SubString is bool subString) writer.WriteBoolean("subString", subString);

        if (value.Json is not null) writer.WriteString("json", value.Json);
        if (value.MatchType is not null) writer.WriteString("matchType", value.MatchType);

        if (value.JsonSchema is JsonElement jsonSchema)
        {
            writer.WritePropertyName("jsonSchema");
            jsonSchema.WriteTo(writer);
        }

        if (value.JsonPath is not null) writer.WriteString("jsonPath", value.JsonPath);
        if (value.Xml is not null) writer.WriteString("xml", value.Xml);
        if (value.XmlSchema is not null) writer.WriteString("xmlSchema", value.XmlSchema);
        if (value.Xpath is not null) writer.WriteString("xpath", value.Xpath);
        if (value.DateTime is not null)
        {
            writer.WritePropertyName("dateTime");
            JsonSerializer.Serialize(writer, value.DateTime.Value, options);
        }
        if (value.Regex is not null) writer.WriteString("regex", value.Regex);
        if (value.Base64Bytes is not null) writer.WriteString("base64Bytes", value.Base64Bytes);

        if (value.Parameters is not null) WriteKeyToMultiValue(writer, "parameters", value.Parameters);
        if (value.MultipartFields is not null) WriteKeyToMultiValue(writer, "fields", value.MultipartFields);
        if (value.Filenames is not null) WriteKeyToMultiValue(writer, "filenames", value.Filenames);
        if (value.PartContentTypes is not null) WriteKeyToMultiValue(writer, "partContentTypes", value.PartContentTypes);

        if (value.Method is not null) writer.WriteString("method", value.Method);
        if (value.ParamsSchema is not null) writer.WriteString("paramsSchema", value.ParamsSchema);

        if (value.Query is not null) writer.WriteString("query", value.Query);
        if (value.OperationName is not null) writer.WriteString("operationName", value.OperationName);
        if (value.VariablesSchema is not null) writer.WriteString("variablesSchema", value.VariablesSchema);
        if (value.SelectionSetMatchType is not null) writer.WriteString("selectionSetMatchType", value.SelectionSetMatchType);
        if (value.GraphQlFields is not null)
        {
            writer.WritePropertyName("fields");
            writer.WriteStartArray();
            foreach (var f in value.GraphQlFields) writer.WriteStringValue(f);
            writer.WriteEndArray();
        }
        if (value.Schema is not null) writer.WriteString("schema", value.Schema);

        if (value.Fuzzy is not null) writer.WriteString("fuzzy", value.Fuzzy);
        if (value.Threshold is double threshold) writer.WriteNumber("threshold", threshold);
        if (value.IgnoreCase is bool ignoreCase) writer.WriteBoolean("ignoreCase", ignoreCase);

        if (value.FilePath is not null) writer.WriteString("filePath", value.FilePath);
        if (value.TemplateType is not null) writer.WriteString("templateType", value.TemplateType);
        if (value.TemplatedFilePath == true) writer.WriteBoolean("templatedFilePath", true);
        if (value.ModuleName is not null) writer.WriteString("moduleName", value.ModuleName);

        if (value.BodyAllOf is not null)
        {
            writer.WritePropertyName("bodyAllOf");
            writer.WriteStartArray();
            foreach (var b in value.BodyAllOf) JsonSerializer.Serialize(writer, b, options);
            writer.WriteEndArray();
        }

        if (value.AdditionalProperties is not null)
        {
            foreach (var kvp in value.AdditionalProperties)
            {
                writer.WritePropertyName(kvp.Key);
                kvp.Value.WriteTo(writer);
            }
        }

        writer.WriteEndObject();
    }

    private static Dictionary<string, List<string>> ReadKeyToMultiValue(JsonElement element)
    {
        var result = new Dictionary<string, List<string>>();
        if (element.ValueKind != JsonValueKind.Object)
        {
            return result;
        }
        foreach (var prop in element.EnumerateObject())
        {
            var values = new List<string>();
            if (prop.Value.ValueKind == JsonValueKind.Array)
            {
                foreach (var v in prop.Value.EnumerateArray())
                {
                    values.Add(v.GetString() ?? string.Empty);
                }
            }
            else if (prop.Value.ValueKind == JsonValueKind.String)
            {
                values.Add(prop.Value.GetString() ?? string.Empty);
            }
            result[prop.Name] = values;
        }
        return result;
    }

    private static void WriteKeyToMultiValue(Utf8JsonWriter writer, string name, Dictionary<string, List<string>> map)
    {
        writer.WritePropertyName(name);
        writer.WriteStartObject();
        foreach (var kvp in map)
        {
            writer.WritePropertyName(kvp.Key);
            writer.WriteStartArray();
            foreach (var v in kvp.Value) writer.WriteStringValue(v);
            writer.WriteEndArray();
        }
        writer.WriteEndObject();
    }
}
