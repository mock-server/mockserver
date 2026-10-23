namespace MockServer.Client.Models;

/// <summary>
/// Fluent builder for <see cref="HttpError"/>.
/// </summary>
public sealed class HttpErrorBuilder
{
    private readonly HttpError _error = new();

    public HttpErrorBuilder WithDropConnection(bool drop)
    {
        _error.DropConnection = drop;
        return this;
    }

    /// <summary>
    /// Resets the connection with a TCP RST instead of returning a response. Takes precedence over
    /// <c>WithStreamError</c> and <c>WithDropConnection</c>.
    /// </summary>
    public HttpErrorBuilder WithResetConnection(bool reset = true)
    {
        _error.ResetConnection = reset;
        return this;
    }

    public HttpErrorBuilder WithResponseBytes(string base64Bytes)
    {
        _error.ResponseBytes = base64Bytes;
        return this;
    }

    public HttpErrorBuilder WithDelay(TimeUnit timeUnit, long value)
    {
        _error.Delay = new Delay { TimeUnit = timeUnit, Value = value };
        return this;
    }

    /// <summary>
    /// Resets the matched request stream with this error code instead of returning a response.
    /// Takes precedence over <c>WithDropConnection</c>.
    /// </summary>
    public HttpErrorBuilder WithStreamError(long code)
    {
        _error.StreamError = code;
        return this;
    }

    /// <summary>Marks this as the primary action when the expectation configures more than one.</summary>
    public HttpErrorBuilder WithPrimary(bool primary = true)
    {
        _error.Primary = primary;
        return this;
    }

    public HttpError Build() => _error;

    /// <summary>
    /// Implicit conversion to HttpError for ergonomic use.
    /// </summary>
    public static implicit operator HttpError(HttpErrorBuilder builder) => builder.Build();
}
