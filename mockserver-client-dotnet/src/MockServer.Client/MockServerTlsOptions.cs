using System.Net.Http;
using System.Net.Security;
using System.Net.WebSockets;
using System.Security.Cryptography.X509Certificates;

namespace MockServer.Client;

/// <summary>
/// TLS configuration for talking to a MockServer instance over HTTPS:
/// a custom CA to trust (so a self-signed / private-CA server certificate validates)
/// and/or a client certificate for mutual TLS (mTLS).
/// <para>
/// These options are consumed by <see cref="MockServerClientBuilder"/>, which builds
/// an <see cref="HttpClientHandler"/> from them. They only take effect when the client
/// builds its own <see cref="HttpClient"/> — if you pass your own pre-configured
/// <see cref="HttpClient"/>, configure TLS on that instead.
/// </para>
/// </summary>
/// <remarks>
/// The PEM-based certificate-loading APIs (<see cref="X509Certificate2.CreateFromPemFile(string, string)"/>)
/// are only available on net8.0 and are not present on netstandard2.0. On netstandard2.0
/// use the PFX-based options (<see cref="WithClientCertificatePfx"/> and a PFX CA bundle).
/// </remarks>
public sealed class MockServerTlsOptions
{
    private X509Certificate2? _caCertificate;
    private X509Certificate2? _clientCertificate;

    /// <summary>
    /// Trust the given CA certificate so a server certificate that chains to it validates,
    /// even when it is self-signed or issued by a private CA not in the system trust store.
    /// </summary>
    /// <param name="caCertPemPath">Path to a PEM-encoded CA certificate file.</param>
    public MockServerTlsOptions WithCaCertificate(string caCertPemPath)
    {
        if (string.IsNullOrEmpty(caCertPemPath))
            throw new ArgumentException("caCertPemPath is required", nameof(caCertPemPath));
        _caCertificate = LoadCertificateFromPem(caCertPemPath);
        return this;
    }

    /// <summary>
    /// Trust the given CA certificate (already loaded).
    /// </summary>
    public MockServerTlsOptions WithCaCertificate(X509Certificate2 caCertificate)
    {
        _caCertificate = caCertificate ?? throw new ArgumentNullException(nameof(caCertificate));
        return this;
    }

    /// <summary>
    /// Present the given client certificate for mutual TLS (mTLS), loaded from a PEM
    /// certificate file plus its PEM private-key file.
    /// </summary>
    /// <param name="certPemPath">Path to the PEM-encoded client certificate.</param>
    /// <param name="keyPemPath">Path to the PEM-encoded private key.</param>
    /// <remarks>net8.0 only — see the class remarks for netstandard2.0.</remarks>
    public MockServerTlsOptions WithClientCertificate(string certPemPath, string keyPemPath)
    {
        if (string.IsNullOrEmpty(certPemPath))
            throw new ArgumentException("certPemPath is required", nameof(certPemPath));
        if (string.IsNullOrEmpty(keyPemPath))
            throw new ArgumentException("keyPemPath is required", nameof(keyPemPath));
#if NET8_0_OR_GREATER
        // X509Certificate2.CreateFromPemFile yields a cert with the private key attached,
        // but on Windows the ephemeral key cannot be used by SChannel for client auth.
        // Round-tripping through PKCS#12 makes the key usable across platforms.
        using var fromPem = X509Certificate2.CreateFromPemFile(certPemPath, keyPemPath);
        _clientCertificate = new X509Certificate2(fromPem.Export(X509ContentType.Pkcs12));
        return this;
#else
        throw new PlatformNotSupportedException(
            "Loading a client certificate from PEM files requires net8.0 or greater. " +
            "On netstandard2.0, use WithClientCertificatePfx(pfxPath, password) instead.");
#endif
    }

    /// <summary>
    /// Present the given client certificate for mutual TLS (mTLS), loaded from a PKCS#12
    /// (.pfx/.p12) file. Available on all target frameworks.
    /// </summary>
    /// <param name="pfxPath">Path to the PKCS#12 file containing the certificate and key.</param>
    /// <param name="password">Optional password protecting the PKCS#12 file.</param>
    public MockServerTlsOptions WithClientCertificatePfx(string pfxPath, string? password = null)
    {
        if (string.IsNullOrEmpty(pfxPath))
            throw new ArgumentException("pfxPath is required", nameof(pfxPath));
        _clientCertificate = new X509Certificate2(pfxPath, password);
        return this;
    }

    /// <summary>
    /// Present the given (already loaded) client certificate for mutual TLS (mTLS).
    /// </summary>
    public MockServerTlsOptions WithClientCertificate(X509Certificate2 clientCertificate)
    {
        _clientCertificate = clientCertificate ?? throw new ArgumentNullException(nameof(clientCertificate));
        return this;
    }

    internal bool HasTlsConfiguration => _caCertificate != null || _clientCertificate != null;

    internal X509Certificate2? CaCertificate => _caCertificate;
    internal X509Certificate2? ClientCertificate => _clientCertificate;

    /// <summary>
    /// Build an <see cref="HttpClientHandler"/> configured with the CA-trust validation
    /// callback and/or client certificate. Returns null if no TLS options are set.
    /// </summary>
    internal HttpClientHandler? BuildHandler()
    {
        if (!HasTlsConfiguration)
            return null;

        var handler = new HttpClientHandler();

        if (_clientCertificate != null)
        {
            handler.ClientCertificateOptions = ClientCertificateOption.Manual;
            handler.ClientCertificates.Add(_clientCertificate);
        }

        if (_caCertificate != null)
        {
            var caCertificate = _caCertificate;
            handler.ServerCertificateCustomValidationCallback = (_, serverCert, chain, errors) =>
                ValidateServerCertificate(caCertificate, serverCert, chain, errors);
        }

        return handler;
    }

    /// <summary>
    /// Apply the same client certificate and CA trust as <see cref="BuildHandler"/> to a
    /// callback WebSocket, so it passes control-plane mTLS as the client's HTTP requests do.
    /// </summary>
    internal void ApplyTo(ClientWebSocketOptions options)
    {
        if (_clientCertificate != null)
            options.ClientCertificates.Add(_clientCertificate);

        if (_caCertificate != null)
        {
            var caCertificate = _caCertificate;
            RemoteCertificateValidationCallback callback = (_, serverCert, chain, errors) =>
                ValidateServerCertificate(caCertificate, ToCertificate2(serverCert), chain, errors);
#if NET8_0_OR_GREATER
            options.RemoteCertificateValidationCallback = callback;
#else
            var property = typeof(ClientWebSocketOptions).GetProperty("RemoteCertificateValidationCallback");
            if (property == null)
                throw new PlatformNotSupportedException(
                    "This runtime cannot trust a custom CA certificate on the callback WebSocket; trust the CA in the system store instead.");
            property.SetValue(options, callback);
#endif
        }
    }

    private static X509Certificate2? ToCertificate2(X509Certificate? certificate) =>
        certificate == null ? null : certificate as X509Certificate2 ?? new X509Certificate2(certificate);

    /// <summary>
    /// Validate that the server certificate chains to the provided CA certificate.
    /// If the only chain error is an untrusted-root (because the CA is private/self-signed),
    /// the chain is rebuilt with the provided CA as an extra trusted root.
    /// </summary>
    private static bool ValidateServerCertificate(
        X509Certificate2 caCertificate,
        X509Certificate2? serverCert,
        X509Chain? chain,
        System.Net.Security.SslPolicyErrors errors)
    {
        if (serverCert == null)
            return false;

        // No errors at all — already trusted by the system store.
        if (errors == System.Net.Security.SslPolicyErrors.None)
            return true;

        // We can only fix chain (untrusted-root) errors by trusting our CA. A name mismatch
        // or a missing-remote-certificate error is not something the CA can override.
        if ((errors & ~System.Net.Security.SslPolicyErrors.RemoteCertificateChainErrors) != 0)
            return false;

        // Re-instantiate the leaf from its raw bytes to guarantee a fresh, owned chain-context
        // handle (the handle on the SslStream-provided cert can be invalid by the time we build).
        using var leaf = new X509Certificate2(serverCert.RawData);

        using var verifyChain = new X509Chain();
        verifyChain.ChainPolicy.RevocationMode = X509RevocationMode.NoCheck;
        verifyChain.ChainPolicy.VerificationFlags = X509VerificationFlags.AllowUnknownCertificateAuthority;
        verifyChain.ChainPolicy.ExtraStore.Add(caCertificate);

        var built = verifyChain.Build(leaf);

        // The presented chain must actually terminate at (or include) our CA — otherwise an
        // attacker's self-signed cert would pass via AllowUnknownCertificateAuthority.
        var chainsToProvidedCa = false;
        foreach (var element in verifyChain.ChainElements)
        {
            if (element.Certificate.RawData.Length == caCertificate.RawData.Length &&
                element.Certificate.Thumbprint == caCertificate.Thumbprint)
            {
                chainsToProvidedCa = true;
                break;
            }
        }

        if (!chainsToProvidedCa)
            return false;

        // Every remaining chain-status flag must be benign (only an untrusted root is allowed,
        // which is expected for a private CA).
        foreach (var status in verifyChain.ChainStatus)
        {
            if (status.Status != X509ChainStatusFlags.NoError &&
                status.Status != X509ChainStatusFlags.UntrustedRoot)
            {
                return false;
            }
        }

        return built || chainsToProvidedCa;
    }

    private static X509Certificate2 LoadCertificateFromPem(string pemPath)
    {
#if NET8_0_OR_GREATER
        // A CA certificate is public-only (no private key). CreateFromPem(cert) parses just
        // the CERTIFICATE block; CreateFromPemFile(path) would try to also extract a key.
        var pem = System.IO.File.ReadAllText(pemPath);
        return X509Certificate2.CreateFromPem(pem);
#else
        // netstandard2.0 lacks CreateFromPem; the ctor accepts a DER/PEM file path
        // for a public certificate (no private key needed for a CA root).
        return new X509Certificate2(pemPath);
#endif
    }
}
