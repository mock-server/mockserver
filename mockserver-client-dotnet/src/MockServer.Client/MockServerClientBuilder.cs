using System.Net.Http;

namespace MockServer.Client;

/// <summary>
/// Fluent builder for a <see cref="MockServerClient"/> that needs control-plane
/// authentication and/or custom TLS (CA trust, mutual TLS). The client only ever
/// issues control-plane requests, so a configured bearer token is attached to every
/// request it sends.
/// </summary>
/// <example>
/// <code>
/// using var client = MockServerClient.Builder("mockserver.internal", 1080)
///     .Secure()
///     .WithControlPlaneBearerToken("eyJ...")
///     .WithTls(tls => tls
///         .WithCaCertificate("/certs/ca.pem")
///         .WithClientCertificate("/certs/client.pem", "/certs/client-key.pem"))
///     .Build();
/// </code>
/// </example>
public sealed class MockServerClientBuilder
{
    private readonly string _host;
    private readonly int _port;
    private string _contextPath = "";
    private bool _secure;
    private TimeSpan _timeout = TimeSpan.FromSeconds(60);
    private Func<string>? _bearerTokenSupplier;
    private readonly MockServerTlsOptions _tlsOptions = new();

    /// <summary>
    /// Begin building a client for the given host and port.
    /// </summary>
    public MockServerClientBuilder(string host, int port = 1080)
    {
        if (string.IsNullOrEmpty(host))
            throw new ArgumentException("host is required", nameof(host));
        _host = host;
        _port = port;
    }

    /// <summary>
    /// Set an optional context path (e.g., "/mockserver-proxy").
    /// </summary>
    public MockServerClientBuilder WithContextPath(string contextPath)
    {
        _contextPath = contextPath ?? "";
        return this;
    }

    /// <summary>
    /// Use HTTPS to talk to the control plane.
    /// </summary>
    public MockServerClientBuilder Secure(bool secure = true)
    {
        _secure = secure;
        return this;
    }

    /// <summary>
    /// Set the HttpClient timeout (default 60 seconds).
    /// </summary>
    public MockServerClientBuilder WithTimeout(TimeSpan timeout)
    {
        _timeout = timeout;
        return this;
    }

    /// <summary>
    /// Attach <c>Authorization: Bearer &lt;token&gt;</c> to every control-plane request.
    /// </summary>
    public MockServerClientBuilder WithControlPlaneBearerToken(string token)
    {
        if (string.IsNullOrEmpty(token))
            throw new ArgumentException("token is required", nameof(token));
        _bearerTokenSupplier = () => token;
        return this;
    }

    /// <summary>
    /// Attach <c>Authorization: Bearer &lt;token&gt;</c> to every control-plane request,
    /// evaluating <paramref name="tokenSupplier"/> per request (e.g., for rotating tokens).
    /// </summary>
    public MockServerClientBuilder WithControlPlaneBearerToken(Func<string> tokenSupplier)
    {
        _bearerTokenSupplier = tokenSupplier ?? throw new ArgumentNullException(nameof(tokenSupplier));
        return this;
    }

    /// <summary>
    /// Configure TLS options (CA trust and/or mutual-TLS client certificate). Implies <see cref="Secure"/>.
    /// </summary>
    public MockServerClientBuilder WithTls(Action<MockServerTlsOptions> configure)
    {
        if (configure == null) throw new ArgumentNullException(nameof(configure));
        configure(_tlsOptions);
        _secure = true;
        return this;
    }

    /// <summary>
    /// Trust the given CA certificate (PEM path) for the control-plane TLS connection. Implies <see cref="Secure"/>.
    /// </summary>
    public MockServerClientBuilder WithCaCertificate(string caCertPemPath)
    {
        _tlsOptions.WithCaCertificate(caCertPemPath);
        _secure = true;
        return this;
    }

    /// <summary>
    /// Present a client certificate for mutual TLS, loaded from PEM cert + key files (net8.0). Implies <see cref="Secure"/>.
    /// </summary>
    public MockServerClientBuilder WithClientCertificate(string certPemPath, string keyPemPath)
    {
        _tlsOptions.WithClientCertificate(certPemPath, keyPemPath);
        _secure = true;
        return this;
    }

    /// <summary>
    /// Present a client certificate for mutual TLS, loaded from a PKCS#12 (.pfx) file (all frameworks). Implies <see cref="Secure"/>.
    /// </summary>
    public MockServerClientBuilder WithClientCertificatePfx(string pfxPath, string? password = null)
    {
        _tlsOptions.WithClientCertificatePfx(pfxPath, password);
        _secure = true;
        return this;
    }

    /// <summary>
    /// Build the configured <see cref="MockServerClient"/>.
    /// </summary>
    public MockServerClient Build()
    {
        var handler = _tlsOptions.BuildHandler();
        var httpClient = handler != null ? new HttpClient(handler) : new HttpClient();
        httpClient.Timeout = _timeout;

        return MockServerClient.CreateConfigured(
            _host, _port, _contextPath, _secure, httpClient, ownsHttpClient: true, _bearerTokenSupplier,
            _tlsOptions.HasTlsConfiguration ? _tlsOptions : null);
    }
}
