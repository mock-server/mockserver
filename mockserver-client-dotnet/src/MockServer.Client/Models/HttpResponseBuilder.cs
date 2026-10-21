namespace MockServer.Client.Models;

/// <summary>
/// Fluent builder for <see cref="HttpResponse"/>.
/// </summary>
public sealed class HttpResponseBuilder
{
    private readonly HttpResponse _response = new();

    public HttpResponseBuilder WithStatusCode(int statusCode)
    {
        _response.StatusCode = statusCode;
        return this;
    }

    public HttpResponseBuilder WithReasonPhrase(string reasonPhrase)
    {
        _response.ReasonPhrase = reasonPhrase;
        return this;
    }

    public HttpResponseBuilder WithHeader(string name, params string[] values)
    {
        _response.Headers ??= new Dictionary<string, List<string>>();
        _response.Headers[name] = new List<string>(values);
        return this;
    }

    /// <summary>
    /// Sets a plain string response body.
    /// </summary>
    public HttpResponseBuilder WithBody(string body)
    {
        _response.Body = body;
        return this;
    }

    /// <summary>
    /// Sets a typed JSON response body.
    /// </summary>
    public HttpResponseBuilder WithJsonBody(string json)
    {
        _response.Body = new TypedBody { Type = "JSON", Json = json };
        return this;
    }

    /// <summary>
    /// Sets a FILE body whose contents are served from the given path.
    /// </summary>
    public HttpResponseBuilder WithFileBody(string filePath, string? contentType = null, FileTemplateType? templateType = null)
    {
        _response.Body = new FileBody { FilePath = filePath, ContentType = contentType, TemplateType = templateType };
        return this;
    }

    /// <summary>
    /// Sets a FILE body whose path is itself rendered as a template against the request, for example
    /// <c>responses/{{ request.pathParameters.name.0 }}.json</c>, before the file is read.
    /// </summary>
    public HttpResponseBuilder WithTemplatedFileBody(string filePath, FileTemplateType templateType, string? contentType = null)
    {
        _response.Body = new FileBody { FilePath = filePath, ContentType = contentType, TemplateType = templateType, TemplatedFilePath = true };
        return this;
    }

    public HttpResponseBuilder WithDelay(TimeUnit timeUnit, long value)
    {
        _response.Delay = new Delay { TimeUnit = timeUnit, Value = value };
        return this;
    }

    public HttpResponseBuilder WithConnectionOptions(ConnectionOptions connectionOptions)
    {
        _response.ConnectionOptions = connectionOptions;
        return this;
    }

    public HttpResponse Build() => _response;

    /// <summary>
    /// Implicit conversion to HttpResponse for ergonomic use.
    /// </summary>
    public static implicit operator HttpResponse(HttpResponseBuilder builder) => builder.Build();
}
