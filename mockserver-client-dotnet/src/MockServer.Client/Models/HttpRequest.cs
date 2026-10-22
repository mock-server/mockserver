using System.Text.Json;
using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// Represents an HTTP request matcher for MockServer.
/// </summary>
public sealed class HttpRequest
{
    [JsonPropertyName("method")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Method { get; set; }

    [JsonPropertyName("path")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Path { get; set; }

    /// <summary>
    /// Path-parameter matchers, a <c>keyToMultiValue</c> map (name → list of values). Each value is a
    /// <see cref="JsonElement"/> so both the simple string form (<c>{"id":["42"]}</c>) and the
    /// schema-matcher form (<c>{"id":[{"schema":{...}}]}</c>) round-trip without loss. Use
    /// <see cref="JsonElement.GetString"/> to read simple string values.
    /// </summary>
    [JsonPropertyName("pathParameters")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Dictionary<string, List<JsonElement>>? PathParameters { get; set; }

    /// <summary>
    /// DNS query name to match (DNS-protocol requests). Mirrors the server's <c>httpRequest.json</c>
    /// <c>dnsName</c> property.
    /// </summary>
    [JsonPropertyName("dnsName")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? DnsName { get; set; }

    /// <summary>DNS record type to match (e.g. <c>A</c>, <c>AAAA</c>, <c>CNAME</c>).</summary>
    [JsonPropertyName("dnsType")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? DnsType { get; set; }

    /// <summary>DNS record class to match (e.g. <c>IN</c>).</summary>
    [JsonPropertyName("dnsClass")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? DnsClass { get; set; }

    [JsonPropertyName("queryStringParameters")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Dictionary<string, List<string>>? QueryStringParameters { get; set; }

    [JsonPropertyName("headers")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Dictionary<string, List<string>>? Headers { get; set; }

    /// <summary>
    /// Cookie matchers (single value per name). Serialised as the <c>keyToValue</c> map form.
    /// </summary>
    [JsonPropertyName("cookies")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Dictionary<string, string>? Cookies { get; set; }

    /// <summary>
    /// Header matchers whose values are taken verbatim, so a value starting with <c>'!'</c> or
    /// <c>'?'</c> means itself rather than being read as a negation or optionality marker. When set
    /// (non-empty) this REPLACES <see cref="Headers"/> on the wire, escaping to the object form only for
    /// values the plain form would misread. Additive: the plain <see cref="Headers"/> map keeps its type
    /// and meaning. Serialised by <see cref="HttpRequestConverter"/>, so it only takes effect through a
    /// <see cref="JsonSerializerOptions"/> that registers that converter (as the client's does).
    /// </summary>
    [JsonIgnore]
    public Dictionary<string, List<MatcherValue>>? HeaderMatchers { get; set; }

    /// <summary>
    /// Query-string parameter matchers whose values are taken verbatim. See <see cref="HeaderMatchers"/>;
    /// when set this REPLACES <see cref="QueryStringParameters"/> on the wire.
    /// </summary>
    [JsonIgnore]
    public Dictionary<string, List<MatcherValue>>? QueryStringParameterMatchers { get; set; }

    /// <summary>
    /// Cookie matchers whose values are taken verbatim. See <see cref="HeaderMatchers"/>; when set this
    /// REPLACES <see cref="Cookies"/> on the wire.
    /// </summary>
    [JsonIgnore]
    public Dictionary<string, MatcherValue>? CookieMatchers { get; set; }

    /// <summary>
    /// Path-parameter matchers whose values are taken verbatim. See <see cref="HeaderMatchers"/>; when
    /// set this REPLACES <see cref="PathParameters"/> on the wire. Carries the nottable object form
    /// (not/optional/value), not the schema-matcher form (<c>{"schema":{...}}</c>).
    /// </summary>
    [JsonIgnore]
    public Dictionary<string, List<MatcherValue>>? PathParameterMatchers { get; set; }

    /// <summary>
    /// The request body matcher. Accepts a raw string, a <see cref="TypedBody"/>, <see cref="FileBody"/>,
    /// <see cref="AllOfBody"/>, or the fully-typed <see cref="Models.Body"/>; on deserialisation an
    /// unrecognised shape is preserved as a raw <c>JsonElement</c> so it round-trips without loss.
    /// </summary>
    [JsonPropertyName("body")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public object? Body { get; set; }

    /// <summary>
    /// The original (pre-normalisation) body text, retained by the server so a recorded request can be
    /// turned back into an expectation. Preserved verbatim.
    /// </summary>
    [JsonPropertyName("originalBody")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? OriginalBody { get; set; }

    /// <summary>
    /// JWT matcher applied to a bearer token on the request (typically the <c>Authorization</c>
    /// header). Serialised under the <c>"jwt"</c> property alongside method/path/headers.
    /// </summary>
    [JsonPropertyName("jwt")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Jwt? Jwt { get; set; }

    [JsonPropertyName("secure")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? Secure { get; set; }

    /// <summary>
    /// Overrides the host/port/scheme used to connect when this request is forwarded
    /// (e.g. as the request override of an override-forwarded-request action), independent
    /// of the <see cref="Path"/> and <c>Host</c> header sent on the wire.
    /// </summary>
    [JsonPropertyName("socketAddress")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public SocketAddress? SocketAddress { get; set; }

    [JsonPropertyName("keepAlive")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? KeepAlive { get; set; }

    /// <summary>Negates the whole request matcher when true.</summary>
    [JsonPropertyName("not")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? Not { get; set; }

    /// <summary>
    /// When true the server responds before the request body is fully received (expect-continue style).
    /// </summary>
    [JsonPropertyName("respondBeforeBody")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? RespondBeforeBody { get; set; }

    /// <summary>The protocol the request must arrive on: <c>HTTP_1_1</c>, <c>HTTP_2</c> or <c>HTTP_3</c>.</summary>
    [JsonPropertyName("protocol")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Protocol? Protocol { get; set; }

    /// <summary>
    /// The client IP address or CIDR range the request must come from, IPv4 or IPv6, for example
    /// <c>10.0.0.0/8</c>; prefix with <c>!</c> to match any other address. This is the connection's TCP peer
    /// address, not <c>X-Forwarded-For</c>.
    /// </summary>
    [JsonPropertyName("clientIp")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ClientIp { get; set; }

    /// <summary>The local socket address the request arrived on (matcher / recorded value).</summary>
    [JsonPropertyName("localAddress")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? LocalAddress { get; set; }

    /// <summary>The remote client socket address (matcher / recorded value).</summary>
    [JsonPropertyName("remoteAddress")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? RemoteAddress { get; set; }

    /// <summary>TLS client-certificate matcher (subject / issuer / SHA-256 fingerprint).</summary>
    [JsonPropertyName("clientCertificate")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public ClientCertificate? ClientCertificate { get; set; }

    /// <summary>The presented TLS client-certificate chain (recorded value).</summary>
    [JsonPropertyName("clientCertificateChain")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<ClientCertificateInfo>? ClientCertificateChain { get; set; }

    /// <summary>
    /// OpenAPI matcher: the spec as a URL, a file/classpath reference or an inline JSON/YAML string,
    /// or an inline spec object. Setting it makes this an OpenAPI matcher (the server's
    /// <c>openAPIDefinition</c>); use it with <see cref="OperationId"/>, <see cref="ContextPathPrefix"/>
    /// and <see cref="Not"/> only. Read back as a <c>string</c> or, for an object, a <see cref="JsonElement"/>.
    /// </summary>
    [JsonPropertyName("specUrlOrPayload")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    [JsonConverter(typeof(StringOrJsonElementConverter))]
    public object? SpecUrlOrPayload { get; set; }

    /// <summary>OpenAPI matcher: the operation the request must match (all operations when unset).</summary>
    [JsonPropertyName("operationId")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? OperationId { get; set; }

    /// <summary>OpenAPI matcher: a path prefix prepended to the spec's paths.</summary>
    [JsonPropertyName("contextPathPrefix")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ContextPathPrefix { get; set; }

    /// <summary>
    /// Conditional matcher condition. Setting it makes this a conditional (if/then/else) matcher (the
    /// server's <c>conditionalRequestDefinition</c>): when <see cref="If"/> matches, <see cref="Then"/>
    /// must match too, otherwise <see cref="Else"/> must (with no else, it matches whenever if does
    /// not). Use it with <see cref="Then"/>, <see cref="Else"/> and <see cref="Not"/> only. Each branch
    /// is an HTTP, OpenAPI or conditional matcher. See <see cref="RequestIf"/>.
    /// </summary>
    [JsonPropertyName("if")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpRequest? If { get; set; }

    /// <summary>Conditional matcher: what must also match when <see cref="If"/> matches.</summary>
    [JsonPropertyName("then")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpRequest? Then { get; set; }

    /// <summary>Conditional matcher: what must match when <see cref="If"/> does not.</summary>
    [JsonPropertyName("else")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public HttpRequest? Else { get; set; }

    /// <summary>
    /// Logical OR of request matchers. Setting it makes this an anyOf matcher (the server's
    /// <c>anyOfRequestDefinition</c>): it matches a request when any branch matches it, trying them in
    /// order, and with <see cref="Not"/> when none does. Use it with <see cref="Not"/> only. Each branch
    /// is an HTTP, OpenAPI, conditional or anyOf matcher. See <see cref="RequestAnyOf"/>.
    /// </summary>
    [JsonPropertyName("anyOf")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<HttpRequest>? AnyOf { get; set; }

    /// <summary>
    /// Creates a new HttpRequest builder.
    /// </summary>
    public static HttpRequestBuilder Request() => new();

    /// <summary>
    /// Creates a conditional (if/then/else) request matcher: when <paramref name="ifRequest"/> matches,
    /// <paramref name="thenRequest"/> must match too, otherwise <paramref name="elseRequest"/> must.
    /// Branches may themselves be OpenAPI (<see cref="OpenApi"/>) or conditional matchers.
    /// </summary>
    public static HttpRequest RequestIf(HttpRequest ifRequest, HttpRequest? thenRequest = null, HttpRequest? elseRequest = null)
        => new() { If = ifRequest ?? throw new ArgumentNullException(nameof(ifRequest)), Then = thenRequest, Else = elseRequest };

    /// <summary>
    /// Creates a logical OR request matcher: it matches a request when any of <paramref name="branches"/>
    /// matches it. Branches may themselves be OpenAPI, conditional or anyOf matchers.
    /// </summary>
    public static HttpRequest RequestAnyOf(params HttpRequest[] branches)
        => new() { AnyOf = (branches ?? throw new ArgumentNullException(nameof(branches))).ToList() };

    /// <summary>
    /// Creates an OpenAPI request matcher for <paramref name="specUrlOrPayload"/> (a URL, file/classpath
    /// reference or inline JSON/YAML spec), optionally limited to one <paramref name="operationId"/>.
    /// </summary>
    public static HttpRequest OpenApi(string specUrlOrPayload, string? operationId = null)
        => new() { SpecUrlOrPayload = specUrlOrPayload ?? throw new ArgumentNullException(nameof(specUrlOrPayload)), OperationId = operationId };
}

/// <summary>
/// Reads a string-or-object value as a <c>string</c> or a <see cref="JsonElement"/>; writes whatever
/// was assigned (a string, a <see cref="JsonElement"/>, a dictionary, ...) by its runtime type.
/// </summary>
internal sealed class StringOrJsonElementConverter : JsonConverter<object>
{
    public override object? Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
        => reader.TokenType == JsonTokenType.String ? reader.GetString() : JsonElement.ParseValue(ref reader);

    public override void Write(Utf8JsonWriter writer, object value, JsonSerializerOptions options)
        => JsonSerializer.Serialize(writer, value, value.GetType(), options);
}

/// <summary>Transport protocol a request matcher constrains to.</summary>
[JsonConverter(typeof(JsonStringEnumConverter))]
public enum Protocol
{
    HTTP_1_1,
    HTTP_2,
    HTTP_3
}

/// <summary>
/// TLS client-certificate matcher. Each field is an exact-or-regex string matched against the
/// presented certificate. Mirrors the server's <c>clientCertificate</c> schema.
/// </summary>
public sealed class ClientCertificate
{
    [JsonPropertyName("subject")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Subject { get; set; }

    [JsonPropertyName("issuer")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Issuer { get; set; }

    [JsonPropertyName("fingerprintSha256")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? FingerprintSha256 { get; set; }
}

/// <summary>
/// One entry of a recorded TLS client-certificate chain. Mirrors the server's
/// <c>clientCertificateChain</c> item schema.
/// </summary>
public sealed class ClientCertificateInfo
{
    [JsonPropertyName("issuerDistinguishedName")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? IssuerDistinguishedName { get; set; }

    [JsonPropertyName("subjectDistinguishedName")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? SubjectDistinguishedName { get; set; }

    [JsonPropertyName("serialNumber")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? SerialNumber { get; set; }

    [JsonPropertyName("signatureAlgorithmName")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? SignatureAlgorithmName { get; set; }
}
