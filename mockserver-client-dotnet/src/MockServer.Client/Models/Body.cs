using System.Text.Json;
using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// A fully-typed representation of a MockServer body matcher (on <c>httpRequest.body</c>) or
/// response body (on <c>httpResponse.body</c>), covering every wire variant the server accepts:
/// <c>STRING</c>, <c>JSON</c>, <c>JSON_SCHEMA</c>, <c>JSON_PATH</c>, <c>XML</c>, <c>XML_SCHEMA</c>,
/// <c>XPATH</c>, <c>REGEX</c>, <c>PARAMETERS</c>, <c>MULTIPART</c>, <c>BINARY</c>, <c>GRAPHQL</c>,
/// <c>JSON_RPC</c>, <c>FUZZY</c>, <c>FILE</c>, <c>WASM</c> and <c>ALL_OF</c>, plus the common
/// <c>not</c>/<c>optional</c>/<c>contentType</c> modifiers.
/// </summary>
/// <remarks>
/// <para>
/// Serialisation is handled by <see cref="BodyJsonConverter"/>. The type is <b>additive</b>: the
/// <c>Body</c> property on <see cref="HttpRequest"/> / <see cref="HttpResponse"/> stays typed as
/// <c>object?</c>, so existing code that assigns a raw string, <see cref="TypedBody"/>,
/// <see cref="FileBody"/> or <see cref="AllOfBody"/> continues to work unchanged. Assigning a
/// <c>Body</c> instance opts into the full typed model.
/// </para>
/// <para>
/// <b>Round-trip guarantees (no silent drops).</b> Two independent mechanisms preserve fidelity:
/// <list type="bullet">
/// <item>Shorthand forms the typed model deliberately does not decompose — a bare JSON string, a
/// bare JSON object, or a bare JSON array body — are captured verbatim in <see cref="RawShorthand"/>
/// and re-emitted byte-for-byte.</item>
/// <item>Object forms carrying a <c>type</c> discriminator are parsed into the typed fields below;
/// any property not modelled here is retained in <see cref="AdditionalProperties"/> and re-emitted,
/// so a body produced by a newer server never loses fields when it passes through this client.</item>
/// </list>
/// Because a deserialised object body is reconstructed from its typed fields plus
/// <see cref="AdditionalProperties"/> (not from a stored blob), the model is the single source of
/// truth; mutating a typed field after deserialisation is reflected on re-serialisation.
/// </para>
/// </remarks>
[JsonConverter(typeof(BodyJsonConverter))]
public sealed class Body
{
    /// <summary>
    /// The body-type discriminator (e.g. <c>"JSON"</c>, <c>"STRING"</c>, <c>"REGEX"</c>). Null only
    /// for a shorthand body captured in <see cref="RawShorthand"/>.
    /// </summary>
    public string? Type { get; set; }

    /// <summary>Negates the matcher — the body matches when it does <i>not</i> match this matcher.</summary>
    public bool? Not { get; set; }

    /// <summary>When true the body matcher is optional (a missing request body still matches).</summary>
    public bool? Optional { get; set; }

    /// <summary>Media/content type associated with the body (JSON, STRING, XML, BINARY, FILE variants).</summary>
    public string? ContentType { get; set; }

    // ---- STRING ----
    /// <summary>The literal text for a <c>STRING</c> body (wire property <c>string</c>).</summary>
    public string? StringValue { get; set; }

    /// <summary>When true a <c>STRING</c> matcher matches a substring rather than the whole body.</summary>
    public bool? SubString { get; set; }

    // ---- JSON ----
    /// <summary>The JSON document (as a string) for a <c>JSON</c> body.</summary>
    public string? Json { get; set; }

    /// <summary>JSON match strictness: <c>STRICT</c> or <c>ONLY_MATCHING_FIELDS</c>.</summary>
    public string? MatchType { get; set; }

    // ---- JSON_SCHEMA ----
    /// <summary>
    /// The JSON schema for a <c>JSON_SCHEMA</c> body. The server accepts either a schema string or an
    /// inline schema object, so this is kept as a raw <see cref="JsonElement"/> and re-emitted verbatim.
    /// </summary>
    public JsonElement? JsonSchema { get; set; }

    // ---- JSON_PATH ----
    /// <summary>The JSONPath expression for a <c>JSON_PATH</c> body.</summary>
    public string? JsonPath { get; set; }

    // ---- XML / XML_SCHEMA / XPATH ----
    /// <summary>The XML document for an <c>XML</c> body.</summary>
    public string? Xml { get; set; }

    /// <summary>The XML schema for an <c>XML_SCHEMA</c> body.</summary>
    public string? XmlSchema { get; set; }

    /// <summary>The XPath expression for an <c>XPATH</c> body.</summary>
    public string? Xpath { get; set; }

    /// <summary>
    /// For a <c>JSON_PATH</c> or <c>XPATH</c> body: the body matches only when a value the JSONPath
    /// selects, or the text of a node the XPath selects, is a date-time satisfying this matcher
    /// (wire property <c>dateTime</c>). Build it with <see cref="MatcherValue.Before"/>,
    /// <see cref="MatcherValue.After"/> or <see cref="MatcherValue.EqualToDateTime"/>.
    /// </summary>
    public MatcherValue? DateTime { get; set; }

    // ---- REGEX ----
    /// <summary>The regular expression for a <c>REGEX</c> body.</summary>
    public string? Regex { get; set; }

    // ---- BINARY ----
    /// <summary>Base64-encoded bytes for a <c>BINARY</c> body.</summary>
    public string? Base64Bytes { get; set; }

    // ---- PARAMETERS ----
    /// <summary>Form parameters for a <c>PARAMETERS</c> body (key to multiple values).</summary>
    public Dictionary<string, List<string>>? Parameters { get; set; }

    // ---- MULTIPART ----
    /// <summary>Part fields for a <c>MULTIPART</c> body (wire property <c>fields</c>).</summary>
    public Dictionary<string, List<string>>? MultipartFields { get; set; }

    /// <summary>Part filenames for a <c>MULTIPART</c> body.</summary>
    public Dictionary<string, List<string>>? Filenames { get; set; }

    /// <summary>Per-part content types for a <c>MULTIPART</c> body.</summary>
    public Dictionary<string, List<string>>? PartContentTypes { get; set; }

    // ---- JSON_RPC ----
    /// <summary>JSON-RPC method name for a <c>JSON_RPC</c> body.</summary>
    public string? Method { get; set; }

    /// <summary>JSON schema (as a string) validating the JSON-RPC <c>params</c>.</summary>
    public string? ParamsSchema { get; set; }

    // ---- GRAPHQL ----
    /// <summary>The GraphQL query for a <c>GRAPHQL</c> body.</summary>
    public string? Query { get; set; }

    /// <summary>The GraphQL operation name for a <c>GRAPHQL</c> body.</summary>
    public string? OperationName { get; set; }

    /// <summary>JSON schema (as a string) validating GraphQL variables.</summary>
    public string? VariablesSchema { get; set; }

    /// <summary>GraphQL selection-set match mode: <c>NORMALISED_STRING</c>, <c>AST_EXACT</c> or <c>AST_SUBSET</c>.</summary>
    public string? SelectionSetMatchType { get; set; }

    /// <summary>GraphQL field selection list (wire property <c>fields</c>).</summary>
    public List<string>? GraphQlFields { get; set; }

    /// <summary>The GraphQL schema (as a string) for a <c>GRAPHQL</c> body.</summary>
    public string? Schema { get; set; }

    // ---- FUZZY ----
    /// <summary>The reference text for a <c>FUZZY</c> body.</summary>
    public string? Fuzzy { get; set; }

    /// <summary>The fuzzy-match distance threshold.</summary>
    public double? Threshold { get; set; }

    /// <summary>Whether a <c>FUZZY</c> match is case-insensitive.</summary>
    public bool? IgnoreCase { get; set; }

    // ---- FILE ----
    /// <summary>The file path for a <c>FILE</c> body.</summary>
    public string? FilePath { get; set; }

    /// <summary>The template engine for a <c>FILE</c> body: <c>VELOCITY</c> or <c>MUSTACHE</c>.</summary>
    public string? TemplateType { get; set; }

    /// <summary>Whether a <c>FILE</c> body's <see cref="FilePath"/> is itself rendered as a template; written only when <c>true</c>.</summary>
    public bool? TemplatedFilePath { get; set; }

    // ---- ALL_OF ----
    /// <summary>Sub-matchers for an <c>ALL_OF</c> body — all must match (wire property <c>bodyAllOf</c>).</summary>
    public List<Body>? BodyAllOf { get; set; }

    // ---- WASM ----
    /// <summary>The WASM module name for a <c>WASM</c> body.</summary>
    public string? ModuleName { get; set; }

    /// <summary>
    /// Any object-body properties this typed model does not recognise, preserved verbatim so a body
    /// from a newer server round-trips without losing fields.
    /// </summary>
    public Dictionary<string, JsonElement>? AdditionalProperties { get; set; }

    /// <summary>
    /// The verbatim JSON for a shorthand body (a bare string, a bare JSON object without a <c>type</c>,
    /// or a bare JSON array). When set, the converter re-emits it exactly and ignores the typed fields.
    /// </summary>
    public JsonElement? RawShorthand { get; set; }

    // ---- Factories (typed, object-form) ----

    /// <summary>Creates a <c>JSON</c> body matcher.</summary>
    public static Body OfJson(string json, string? matchType = null) =>
        new() { Type = "JSON", Json = json, MatchType = matchType };

    /// <summary>Creates a <c>STRING</c> body matcher (object form; set <paramref name="subString"/> for substring matching).</summary>
    public static Body OfString(string value, bool? subString = null) =>
        new() { Type = "STRING", StringValue = value, SubString = subString };

    /// <summary>Creates a <c>REGEX</c> body matcher.</summary>
    public static Body OfRegex(string regex) => new() { Type = "REGEX", Regex = regex };

    /// <summary>Creates a <c>JSON_PATH</c> body matcher.</summary>
    public static Body OfJsonPath(string jsonPath) => new() { Type = "JSON_PATH", JsonPath = jsonPath };

    /// <summary>Creates a <c>JSON_SCHEMA</c> body matcher from a schema string.</summary>
    public static Body OfJsonSchema(string jsonSchema) =>
        new() { Type = "JSON_SCHEMA", JsonSchema = JsonSerializer.SerializeToElement(jsonSchema) };

    /// <summary>Creates an <c>XML</c> body matcher.</summary>
    public static Body OfXml(string xml) => new() { Type = "XML", Xml = xml };

    /// <summary>Creates an <c>XML_SCHEMA</c> body matcher.</summary>
    public static Body OfXmlSchema(string xmlSchema) => new() { Type = "XML_SCHEMA", XmlSchema = xmlSchema };

    /// <summary>Creates an <c>XPATH</c> body matcher.</summary>
    public static Body OfXPath(string xpath) => new() { Type = "XPATH", Xpath = xpath };

    /// <summary>Creates a <c>BINARY</c> body from base64-encoded bytes.</summary>
    public static Body OfBinary(string base64Bytes, string? contentType = null) =>
        new() { Type = "BINARY", Base64Bytes = base64Bytes, ContentType = contentType };

    /// <summary>Creates a <c>PARAMETERS</c> body matcher.</summary>
    public static Body OfParameters(Dictionary<string, List<string>> parameters) =>
        new() { Type = "PARAMETERS", Parameters = parameters };

    /// <summary>Creates a <c>WASM</c> body matcher referencing a server-side module.</summary>
    public static Body OfWasm(string moduleName) => new() { Type = "WASM", ModuleName = moduleName };

    /// <summary>Creates a <c>GRAPHQL</c> body matcher.</summary>
    public static Body OfGraphQl(string query) => new() { Type = "GRAPHQL", Query = query };

    /// <summary>Creates an <c>ALL_OF</c> body matcher — every supplied sub-matcher must match.</summary>
    public static Body OfAllOf(params Body[] bodies) =>
        new() { Type = "ALL_OF", BodyAllOf = bodies.ToList() };

    /// <summary>Marks this matcher optional (fluent).</summary>
    public Body WithOptional(bool optional) { Optional = optional; return this; }

    /// <summary>Negates this matcher (fluent).</summary>
    public Body WithNot(bool not) { Not = not; return this; }

    /// <summary>
    /// Compares the value a <c>JSON_PATH</c> body selects, or the text of a node an <c>XPATH</c> body
    /// selects, as a date-time (fluent), for example
    /// <c>Body.OfJsonPath("$.order.created").WithDateTime(MatcherValue.After("now -1 days"))</c>.
    /// </summary>
    /// <exception cref="ArgumentException"><paramref name="dateTime"/> is not a date-time matcher.</exception>
    public Body WithDateTime(MatcherValue dateTime)
    {
        if (!dateTime.IsDateTime)
        {
            throw new ArgumentException("WithDateTime needs a date-time matcher; build one with MatcherValue.Before, After or EqualToDateTime", nameof(dateTime));
        }
        DateTime = dateTime;
        return this;
    }

    /// <summary>Sets the content type (fluent).</summary>
    public Body WithContentType(string contentType) { ContentType = contentType; return this; }
}
