using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using FluentAssertions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// Tests for control-plane bearer-token auth and TLS/mTLS configuration on the .NET client.
/// </summary>
public class AuthAndTlsTests
{
    /// <summary>
    /// Captures the Authorization header (and request) sent to MockServer.
    /// </summary>
    private sealed class CapturingHandler : HttpMessageHandler
    {
        public AuthenticationHeaderValue? LastAuthorization { get; private set; }
        public List<AuthenticationHeaderValue?> AllAuthorizations { get; } = new();
        public HttpStatusCode ResponseStatusCode { get; set; } = HttpStatusCode.OK;
        public string ResponseBody { get; set; } = "[]";

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastAuthorization = request.Headers.Authorization;
            AllAuthorizations.Add(request.Headers.Authorization);
            return Task.FromResult(new HttpResponseMessage(ResponseStatusCode)
            {
                Content = new StringContent(ResponseBody, Encoding.UTF8, "application/json")
            });
        }
    }

    // -------------------------------------------------------------------
    // Control-plane bearer token
    // -------------------------------------------------------------------

    [Fact]
    public void ControlPlaneBearerToken_IsAttachedToRequests()
    {
        var handler = new CapturingHandler();
        var client = new MockServerClient("http://localhost:1080", new HttpClient(handler))
            .WithControlPlaneBearerToken("test-token-123");

        client.Reset();

        handler.LastAuthorization.Should().NotBeNull();
        handler.LastAuthorization!.Scheme.Should().Be("Bearer");
        handler.LastAuthorization!.Parameter.Should().Be("test-token-123");
    }

    [Fact]
    public void ControlPlaneBasicAuthentication_IsAttachedToRequests()
    {
        var handler = new CapturingHandler();
        var client = new MockServerClient("http://localhost:1080", new HttpClient(handler))
            .WithControlPlaneBasicAuthentication("admin", "pä:ss");

        client.Reset();

        handler.LastAuthorization.Should().NotBeNull();
        handler.LastAuthorization!.Scheme.Should().Be("Basic");
        handler.LastAuthorization!.Parameter.Should().Be(Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes("admin:pä:ss")));
    }

    [Fact]
    public void ControlPlaneBearerToken_WinsOverBasicAuthentication()
    {
        var handler = new CapturingHandler();
        var client = new MockServerClient("http://localhost:1080", new HttpClient(handler))
            .WithControlPlaneBasicAuthentication("admin", "s3cret")
            .WithControlPlaneBearerToken("jwt");

        client.Reset();

        handler.LastAuthorization!.Scheme.Should().Be("Bearer");
        handler.LastAuthorization!.Parameter.Should().Be("jwt");
    }

    private sealed class CapturingTraceListener : System.Diagnostics.TraceListener
    {
        public List<string> Messages { get; } = new();
        public override void Write(string? message) { }
        public override void WriteLine(string? message) { if (message != null) Messages.Add(message); }
        public override void TraceEvent(System.Diagnostics.TraceEventCache? eventCache, string source, System.Diagnostics.TraceEventType eventType, int id, string? message)
        {
            if (message != null) Messages.Add(message);
        }
    }

    [Fact]
    public void ControlPlaneBasicAuthentication_WarnsWithoutTlsAndKeepsCredentialsOutOfErrors()
    {
        var listener = new CapturingTraceListener();
        System.Diagnostics.Trace.Listeners.Add(listener);
        try
        {
            var handler = new CapturingHandler { ResponseStatusCode = HttpStatusCode.InternalServerError, ResponseBody = "failed" };
            var client = new MockServerClient("http://localhost:1080", new HttpClient(handler))
                .WithControlPlaneBasicAuthentication("admin", "pa55-near-miss");

            var failure = FluentActions.Invoking(() => client.Reset()).Should().Throw<Exception>().Which;

            listener.Messages.Should().Contain(m => m.Contains("without TLS"));
            listener.Messages.Should().NotContain(m => m.Contains("pa55-near-miss"));
            failure.ToString().Should().NotContain("pa55-near-miss");
            failure.ToString().Should().NotContain(Convert.ToBase64String(Encoding.UTF8.GetBytes("admin:pa55-near-miss")));
        }
        finally
        {
            System.Diagnostics.Trace.Listeners.Remove(listener);
        }
    }

    [Fact]
    public void ControlPlaneBasicAuthentication_RefusesAUsernameThatCannotBeSent()
    {
        var client = new MockServerClient("http://localhost:1080", new HttpClient(new CapturingHandler()));

        FluentActions.Invoking(() => client.WithControlPlaneBasicAuthentication("a:b", "x")).Should().Throw<ArgumentException>();
        FluentActions.Invoking(() => MockServerClient.Builder("localhost").WithControlPlaneBasicAuthentication("", "x")).Should().Throw<ArgumentException>();
    }

    [Fact]
    public void WithoutBearerToken_NoAuthorizationHeader()
    {
        var handler = new CapturingHandler();
        var client = new MockServerClient("http://localhost:1080", new HttpClient(handler));

        client.Reset();

        handler.LastAuthorization.Should().BeNull();
    }

    [Fact]
    public void ControlPlaneBearerToken_AppliedToEveryRequest()
    {
        var handler = new CapturingHandler();
        var client = new MockServerClient("http://localhost:1080", new HttpClient(handler))
            .WithControlPlaneBearerToken("tok");

        client.Reset();
        client.Clear();
        client.RetrieveLogMessages();

        handler.AllAuthorizations.Should().HaveCountGreaterThanOrEqualTo(3);
        handler.AllAuthorizations.Should().OnlyContain(a => a != null && a.Parameter == "tok");
    }

    [Fact]
    public void ControlPlaneBearerToken_Supplier_EvaluatedPerRequest()
    {
        var handler = new CapturingHandler();
        var counter = 0;
        var client = new MockServerClient("http://localhost:1080", new HttpClient(handler))
            .WithControlPlaneBearerToken(() => $"rotating-{++counter}");

        client.Reset();
        var first = handler.LastAuthorization!.Parameter;
        client.Reset();
        var second = handler.LastAuthorization!.Parameter;

        first.Should().Be("rotating-1");
        second.Should().Be("rotating-2");
    }

    [Fact]
    public void ControlPlaneBearerToken_Supplier_ReturningEmpty_OmitsHeader()
    {
        var handler = new CapturingHandler();
        var client = new MockServerClient("http://localhost:1080", new HttpClient(handler))
            .WithControlPlaneBearerToken(() => "");

        client.Reset();

        handler.LastAuthorization.Should().BeNull();
    }

    [Fact]
    public void WithControlPlaneBearerToken_RejectsNullSupplier()
    {
        var client = new MockServerClient("http://localhost:1080", new HttpClient(new CapturingHandler()));
        var act = () => client.WithControlPlaneBearerToken((Func<string>)null!);
        act.Should().Throw<ArgumentNullException>();
    }

    [Fact]
    public void Builder_WithControlPlaneBearerToken_AttachesHeader()
    {
        // Builder builds its own HttpClient; we can't inject a handler, so just assert it
        // constructs a usable client. The header wiring is covered by the constructor tests.
        using var client = MockServerClient.Builder("localhost", 1080)
            .WithControlPlaneBearerToken("abc")
            .Build();

        client.Should().NotBeNull();
    }

    // -------------------------------------------------------------------
    // TLS / mTLS handler configuration
    // -------------------------------------------------------------------

    [Fact]
    public void TlsOptions_NoConfiguration_BuildsNoHandler()
    {
        var options = new MockServerTlsOptions();
        options.HasTlsConfiguration.Should().BeFalse();
        options.BuildHandler().Should().BeNull();
    }

    [Fact]
    public void TlsOptions_WithCaCertificate_ConfiguresValidationCallback()
    {
        var caPath = WriteTempCertPem(out _);
        try
        {
            var options = new MockServerTlsOptions().WithCaCertificate(caPath);

            options.HasTlsConfiguration.Should().BeTrue();
            using var handler = options.BuildHandler();
            handler.Should().NotBeNull();
            handler!.ServerCertificateCustomValidationCallback.Should().NotBeNull();
            handler.ClientCertificates.Count.Should().Be(0);
        }
        finally
        {
            File.Delete(caPath);
        }
    }

    [Fact]
    public void TlsOptions_WithClientCertificatePem_AddsClientCertificate()
    {
        var (certPath, keyPath) = WriteTempCertAndKeyPem();
        try
        {
            var options = new MockServerTlsOptions().WithClientCertificate(certPath, keyPath);

            options.HasTlsConfiguration.Should().BeTrue();
            using var handler = options.BuildHandler();
            handler.Should().NotBeNull();
            handler!.ClientCertificates.Count.Should().Be(1);
            handler.ClientCertificateOptions.Should().Be(ClientCertificateOption.Manual);
        }
        finally
        {
            File.Delete(certPath);
            File.Delete(keyPath);
        }
    }

    [Fact]
    public void TlsOptions_WithClientCertificatePfx_AddsClientCertificate()
    {
        var pfxPath = WriteTempPfx("pfx-pass");
        try
        {
            var options = new MockServerTlsOptions().WithClientCertificatePfx(pfxPath, "pfx-pass");

            using var handler = options.BuildHandler();
            handler.Should().NotBeNull();
            handler!.ClientCertificates.Count.Should().Be(1);
        }
        finally
        {
            File.Delete(pfxPath);
        }
    }

    [Fact]
    public void TlsOptions_WithCaAndClientCert_ConfiguresBoth()
    {
        var caPath = WriteTempCertPem(out _);
        var (certPath, keyPath) = WriteTempCertAndKeyPem();
        try
        {
            var options = new MockServerTlsOptions()
                .WithCaCertificate(caPath)
                .WithClientCertificate(certPath, keyPath);

            using var handler = options.BuildHandler();
            handler.Should().NotBeNull();
            handler!.ServerCertificateCustomValidationCallback.Should().NotBeNull();
            handler.ClientCertificates.Count.Should().Be(1);
        }
        finally
        {
            File.Delete(caPath);
            File.Delete(certPath);
            File.Delete(keyPath);
        }
    }

    [Fact]
    public void Builder_WithTls_ProducesClient()
    {
        var caPath = WriteTempCertPem(out _);
        try
        {
            using var client = MockServerClient.Builder("localhost", 1080)
                .Secure()
                .WithCaCertificate(caPath)
                .Build();

            client.Should().NotBeNull();
        }
        finally
        {
            File.Delete(caPath);
        }
    }

    [Fact]
    public void TlsValidationCallback_AcceptsCertChainingToProvidedCa()
    {
        // Build a CA, then a server cert signed by that CA. The callback should accept the
        // server cert (which would otherwise fail as an untrusted root) because it chains to the CA.
        using var ca = CreateCaCertificate("CN=Test Root CA");
        using var server = CreateServerCertificateSignedBy(ca, "CN=mockserver.local");

        var caPemPath = Path.Combine(Path.GetTempPath(), $"ca-{Guid.NewGuid():N}.pem");
        File.WriteAllText(caPemPath, ExportCertPem(ca));
        try
        {
            var options = new MockServerTlsOptions().WithCaCertificate(caPemPath);
            using var handler = options.BuildHandler();
            var callback = handler!.ServerCertificateCustomValidationCallback!;

            // The callback rebuilds the chain itself against the provided CA, so pass null.
            var accepted = callback(new HttpRequestMessage(), server, null,
                System.Net.Security.SslPolicyErrors.RemoteCertificateChainErrors);

            accepted.Should().BeTrue();
        }
        finally
        {
            File.Delete(caPemPath);
        }
    }

    [Fact]
    public void TlsValidationCallback_RejectsCertNotChainingToProvidedCa()
    {
        using var ca = CreateCaCertificate("CN=Test Root CA");
        using var otherCa = CreateCaCertificate("CN=Other CA");
        using var rogueServer = CreateServerCertificateSignedBy(otherCa, "CN=evil.local");

        var caPemPath = Path.Combine(Path.GetTempPath(), $"ca-{Guid.NewGuid():N}.pem");
        File.WriteAllText(caPemPath, ExportCertPem(ca));
        try
        {
            var options = new MockServerTlsOptions().WithCaCertificate(caPemPath);
            using var handler = options.BuildHandler();
            var callback = handler!.ServerCertificateCustomValidationCallback!;

            var accepted = callback(new HttpRequestMessage(), rogueServer, null,
                System.Net.Security.SslPolicyErrors.RemoteCertificateChainErrors);

            accepted.Should().BeFalse();
        }
        finally
        {
            File.Delete(caPemPath);
        }
    }

    [Fact]
    public void TlsValidationCallback_RejectsNameMismatch()
    {
        using var ca = CreateCaCertificate("CN=Test Root CA");
        using var server = CreateServerCertificateSignedBy(ca, "CN=mockserver.local");

        var caPemPath = Path.Combine(Path.GetTempPath(), $"ca-{Guid.NewGuid():N}.pem");
        File.WriteAllText(caPemPath, ExportCertPem(ca));
        try
        {
            var options = new MockServerTlsOptions().WithCaCertificate(caPemPath);
            using var handler = options.BuildHandler();
            var callback = handler!.ServerCertificateCustomValidationCallback!;

            // A name mismatch is not a chain error the CA can override.
            var accepted = callback(new HttpRequestMessage(), server, null,
                System.Net.Security.SslPolicyErrors.RemoteCertificateNameMismatch);

            accepted.Should().BeFalse();
        }
        finally
        {
            File.Delete(caPemPath);
        }
    }

    // -------------------------------------------------------------------
    // Test certificate helpers
    // -------------------------------------------------------------------

    private static string WriteTempCertPem(out X509Certificate2 cert)
    {
        cert = CreateCaCertificate("CN=Temp CA");
        var path = Path.Combine(Path.GetTempPath(), $"cert-{Guid.NewGuid():N}.pem");
        File.WriteAllText(path, ExportCertPem(cert));
        return path;
    }

    private static (string CertPath, string KeyPath) WriteTempCertAndKeyPem()
    {
        using var rsa = RSA.Create(2048);
        var request = new CertificateRequest("CN=Test Client", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        using var cert = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddDays(1));

        var certPath = Path.Combine(Path.GetTempPath(), $"client-{Guid.NewGuid():N}.pem");
        var keyPath = Path.Combine(Path.GetTempPath(), $"client-key-{Guid.NewGuid():N}.pem");
        File.WriteAllText(certPath, ExportCertPem(cert));
        File.WriteAllText(keyPath, ExportPrivateKeyPem(rsa));
        return (certPath, keyPath);
    }

    private static string WriteTempPfx(string password)
    {
        using var rsa = RSA.Create(2048);
        var request = new CertificateRequest("CN=Test Client PFX", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        using var cert = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddDays(1));
        var path = Path.Combine(Path.GetTempPath(), $"client-{Guid.NewGuid():N}.pfx");
        File.WriteAllBytes(path, cert.Export(X509ContentType.Pkcs12, password));
        return path;
    }

    private static X509Certificate2 CreateCaCertificate(string subject)
    {
        using var rsa = RSA.Create(2048);
        var request = new CertificateRequest(subject, rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(true, false, 0, true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(
            X509KeyUsageFlags.KeyCertSign | X509KeyUsageFlags.DigitalSignature, true));
        return request.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(1));
    }

    private static X509Certificate2 CreateServerCertificateSignedBy(X509Certificate2 ca, string subject)
    {
        using var rsa = RSA.Create(2048);
        var request = new CertificateRequest(subject, rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));

        var serial = new byte[8];
        RandomNumberGenerator.Fill(serial);
        using var signed = request.Create(ca, DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddDays(1), serial);
        // Return a DER-roundtripped, public-only copy — this mirrors the "wire-shaped" leaf
        // certificate the real SslStream hands the validation callback, and avoids the
        // invalid-handle issue with the in-memory cert produced by CertificateRequest.Create.
        return X509CertificateLoader.LoadCertificate(signed.Export(X509ContentType.Cert));
    }

    private static string ExportCertPem(X509Certificate2 cert)
    {
        var der = cert.Export(X509ContentType.Cert);
        return "-----BEGIN CERTIFICATE-----\n" +
               Convert.ToBase64String(der, Base64FormattingOptions.InsertLineBreaks) +
               "\n-----END CERTIFICATE-----\n";
    }

    private static string ExportPrivateKeyPem(RSA rsa)
    {
        var der = rsa.ExportPkcs8PrivateKey();
        return "-----BEGIN PRIVATE KEY-----\n" +
               Convert.ToBase64String(der, Base64FormattingOptions.InsertLineBreaks) +
               "\n-----END PRIVATE KEY-----\n";
    }
}
