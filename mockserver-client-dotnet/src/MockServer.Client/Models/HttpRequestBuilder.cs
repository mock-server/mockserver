using System.Linq;
using System.Text.Json;

namespace MockServer.Client.Models;

/// <summary>
/// Fluent builder for <see cref="HttpRequest"/>.
/// </summary>
public sealed class HttpRequestBuilder
{
    private readonly HttpRequest _request = new();

    public HttpRequestBuilder WithMethod(string method)
    {
        _request.Method = method;
        return this;
    }

    public HttpRequestBuilder WithPath(string path)
    {
        _request.Path = path;
        return this;
    }

    public HttpRequestBuilder WithQueryStringParameter(string name, params string[] values)
    {
        _request.QueryStringParameters ??= new Dictionary<string, List<string>>();
        _request.QueryStringParameters[name] = new List<string>(values);
        return this;
    }

    public HttpRequestBuilder WithHeader(string name, params string[] values)
    {
        _request.Headers ??= new Dictionary<string, List<string>>();
        _request.Headers[name] = new List<string>(values);
        return this;
    }

    /// <summary>
    /// Adds a header matcher whose values are taken verbatim, so a value starting with <c>'!'</c> or
    /// <c>'?'</c> means itself rather than a negation / optionality marker. Escapes to the object form
    /// only when the plain form would be misread — see <see cref="MatcherValue"/>. Any values already
    /// set through <see cref="WithHeader"/> are carried over so the two APIs compose.
    /// </summary>
    /// <remarks>
    /// A bare <c>string</c> argument is implicitly parsed with the server's marker-stripping semantics
    /// (so <c>"!foo"</c> means "not foo"); pass <c>MatcherValue.Literal("!foo")</c> to get the escape
    /// this method exists to provide. The same applies to the other <c>With*Matcher</c> methods. A
    /// date-time matcher (<c>MatcherValue.After("now -1 hours")</c>) is passed the same way.
    /// </remarks>
    public HttpRequestBuilder WithHeaderMatcher(string name, params MatcherValue[] values)
    {
        if (_request.HeaderMatchers is null)
        {
            _request.HeaderMatchers = new Dictionary<string, List<MatcherValue>>();
            if (_request.Headers is not null)
            {
                foreach (var existing in _request.Headers)
                {
                    _request.HeaderMatchers[existing.Key] = existing.Value.Select(MatcherValue.ParsePlain).ToList();
                }
            }
        }
        _request.HeaderMatchers[name] = new List<MatcherValue>(values);
        return this;
    }

    /// <summary>
    /// Adds a query-string parameter matcher whose values are taken verbatim. See
    /// <see cref="WithHeaderMatcher"/>.
    /// </summary>
    public HttpRequestBuilder WithQueryStringParameterMatcher(string name, params MatcherValue[] values)
    {
        if (_request.QueryStringParameterMatchers is null)
        {
            _request.QueryStringParameterMatchers = new Dictionary<string, List<MatcherValue>>();
            if (_request.QueryStringParameters is not null)
            {
                foreach (var existing in _request.QueryStringParameters)
                {
                    _request.QueryStringParameterMatchers[existing.Key] = existing.Value.Select(MatcherValue.ParsePlain).ToList();
                }
            }
        }
        _request.QueryStringParameterMatchers[name] = new List<MatcherValue>(values);
        return this;
    }

    /// <summary>
    /// Adds a cookie matcher whose value is taken verbatim. See <see cref="WithHeaderMatcher"/>.
    /// </summary>
    public HttpRequestBuilder WithCookieMatcher(string name, MatcherValue value)
    {
        if (_request.CookieMatchers is null)
        {
            _request.CookieMatchers = new Dictionary<string, MatcherValue>();
            if (_request.Cookies is not null)
            {
                foreach (var existing in _request.Cookies)
                {
                    _request.CookieMatchers[existing.Key] = MatcherValue.ParsePlain(existing.Value);
                }
            }
        }
        _request.CookieMatchers[name] = value;
        return this;
    }

    /// <summary>
    /// Adds a path-parameter matcher whose values are taken verbatim. See <see cref="WithHeaderMatcher"/>.
    /// </summary>
    /// <remarks>
    /// String-valued and object-form (nottable or date-time) entries already set through
    /// <see cref="HttpRequest.PathParameters"/> are carried over so the two APIs compose. The
    /// schema-matcher form (<c>{"schema":{...}}</c>) cannot be expressed as a <see cref="MatcherValue"/>,
    /// so it is NOT carried by this API — do not mix a schema-matcher path parameter with this method on
    /// the same request.
    /// </remarks>
    public HttpRequestBuilder WithPathParameterMatcher(string name, params MatcherValue[] values)
    {
        if (_request.PathParameterMatchers is null)
        {
            _request.PathParameterMatchers = new Dictionary<string, List<MatcherValue>>();
            if (_request.PathParameters is not null)
            {
                foreach (var existing in _request.PathParameters)
                {
                    _request.PathParameterMatchers[existing.Key] = existing.Value
                        .Where(element => element.ValueKind == JsonValueKind.String
                                          || (element.ValueKind == JsonValueKind.Object && !element.TryGetProperty("schema", out _)))
                        .Select(element => element.Deserialize<MatcherValue>())
                        .ToList();
                }
            }
        }
        _request.PathParameterMatchers[name] = new List<MatcherValue>(values);
        return this;
    }

    /// <summary>
    /// Sets a plain string body matcher.
    /// </summary>
    public HttpRequestBuilder WithBody(string body)
    {
        _request.Body = body;
        return this;
    }

    /// <summary>
    /// Sets a typed JSON body matcher.
    /// </summary>
    public HttpRequestBuilder WithJsonBody(string json)
    {
        _request.Body = new TypedBody { Type = "JSON", Json = json };
        return this;
    }

    /// <summary>
    /// Sets a <c>JSON_SCHEMA</c> body matcher from a schema string.
    /// </summary>
    public HttpRequestBuilder WithJsonSchemaBody(string jsonSchema)
    {
        _request.Body = Models.Body.OfJsonSchema(jsonSchema);
        return this;
    }

    /// <summary>
    /// Sets a FILE body matcher with an optional template type.
    /// </summary>
    public HttpRequestBuilder WithFileBody(string filePath, string? contentType = null, FileTemplateType? templateType = null)
    {
        _request.Body = new FileBody { FilePath = filePath, ContentType = contentType, TemplateType = templateType };
        return this;
    }

    /// <summary>
    /// Sets an <c>ALL_OF</c> body matcher — the request body must match every supplied
    /// sub-matcher. Emits <c>{"type":"ALL_OF","bodyAllOf":[ ... ]}</c>.
    /// </summary>
    public HttpRequestBuilder WithAllOfBody(params BodyMatcher[] bodies)
    {
        _request.Body = new AllOfBody { BodyAllOf = bodies.Cast<object>().ToList() };
        return this;
    }

    /// <summary>
    /// Sets a JWT request matcher, serialised under the request's <c>"jwt"</c> property.
    /// </summary>
    public HttpRequestBuilder WithJwt(Jwt jwt)
    {
        _request.Jwt = jwt;
        return this;
    }

    public HttpRequestBuilder WithSecure(bool secure)
    {
        _request.Secure = secure;
        return this;
    }

    public HttpRequestBuilder WithKeepAlive(bool keepAlive)
    {
        _request.KeepAlive = keepAlive;
        return this;
    }

    /// <summary>
    /// Matches only requests from this client IP address or CIDR range (for example <c>10.0.0.0/8</c>);
    /// prefix with <c>!</c> to match any other address. The address is the TCP peer, not X-Forwarded-For.
    /// </summary>
    public HttpRequestBuilder WithClientIp(string clientIp)
    {
        _request.ClientIp = clientIp;
        return this;
    }

    /// <summary>Negates the whole matcher (the request, OpenAPI or conditional matcher) when true.</summary>
    public HttpRequestBuilder WithNot(bool not)
    {
        _request.Not = not;
        return this;
    }

    /// <summary>
    /// Makes this a conditional matcher with <paramref name="ifRequest"/> as its condition. See
    /// <see cref="HttpRequest.RequestIf"/>.
    /// </summary>
    public HttpRequestBuilder WithIf(HttpRequest ifRequest)
    {
        _request.If = ifRequest;
        return this;
    }

    /// <summary>Conditional matcher: what must also match when the condition matches.</summary>
    public HttpRequestBuilder WithThen(HttpRequest thenRequest)
    {
        _request.Then = thenRequest;
        return this;
    }

    /// <summary>Conditional matcher: what must match when the condition does not.</summary>
    public HttpRequestBuilder WithElse(HttpRequest elseRequest)
    {
        _request.Else = elseRequest;
        return this;
    }

    public HttpRequest Build() => _request;

    /// <summary>
    /// Implicit conversion to HttpRequest for ergonomic use.
    /// </summary>
    public static implicit operator HttpRequest(HttpRequestBuilder builder) => builder.Build();
}
