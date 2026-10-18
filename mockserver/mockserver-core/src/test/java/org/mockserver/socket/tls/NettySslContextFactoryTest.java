package org.mockserver.socket.tls;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * Verifies that the http2Enabled configuration property controls whether HTTP/2 is advertised via
 * ALPN - the lever that lets a user force HTTP/2 capable clients to fall back to HTTP/1.1 (#2260).
 * Also covers error paths for invalid TLS configuration.
 */
public class NettySslContextFactoryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldAdvertiseHttp2ViaAlpnByDefault() {
        // given
        SslContext serverSslContext = new NettySslContextFactory(configuration(), new MockServerLogger(), true).createServerSslContext();

        // then
        assertThat(serverSslContext.applicationProtocolNegotiator().protocols(), hasItem(ApplicationProtocolNames.HTTP_2));
        assertThat(serverSslContext.applicationProtocolNegotiator().protocols(), hasItem(ApplicationProtocolNames.HTTP_1_1));
    }

    @Test
    public void shouldNotAdvertiseHttp2ViaAlpnWhenHttp2Disabled() {
        // given
        Configuration configuration = configuration().http2Enabled(false);

        // when
        SslContext serverSslContext = new NettySslContextFactory(configuration, new MockServerLogger(), true).createServerSslContext();

        // then - only http/1.1 is advertised so HTTP/2 capable clients negotiate HTTP/1.1
        assertThat(serverSslContext.applicationProtocolNegotiator().protocols(), not(hasItem(ApplicationProtocolNames.HTTP_2)));
        assertThat(serverSslContext.applicationProtocolNegotiator().protocols(), hasItem(ApplicationProtocolNames.HTTP_1_1));
    }

    @Test
    public void shouldNotAdvertiseHttp2ViaAlpnOnClientContextWhenHttp2Disabled() {
        // given
        Configuration configuration = configuration().http2Enabled(false);

        // when
        SslContext clientSslContext = new NettySslContextFactory(configuration, new MockServerLogger(), false).createClientSslContext(true, true);

        // then - even when the caller requests HTTP/2 the disabled property strips h2 from ALPN
        assertThat(clientSslContext.applicationProtocolNegotiator().protocols(), not(hasItem(ApplicationProtocolNames.HTTP_2)));
    }

    // ---- error-path tests ----

    @Test
    public void shouldThrowWhenServerSslContextCreatedWithInvalidPrivateKey() throws IOException {
        // given — valid cert but invalid key content
        File keyFile = createTempPemFile("key.pem", "not a valid pem key");
        File certFile = createTempPemFile("cert.pem", "not a valid pem cert");
        Configuration configuration = configuration();
        configuration.privateKeyPath(keyFile.getAbsolutePath());
        configuration.x509CertificatePath(certFile.getAbsolutePath());

        // when / then — createServerSslContext wraps the exception with descriptive message
        try {
            new NettySslContextFactory(configuration, new MockServerLogger(), true).createServerSslContext();
            fail("expected RuntimeException for invalid PEM files");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Exception creating SSL context for server"));
            assertThat(e.getMessage(), containsString(keyFile.getAbsolutePath()));
        }
    }

    @Test
    public void shouldIncludeCertificatePathInServerSslContextError() throws IOException {
        // given — invalid PEM content
        File keyFile = createTempPemFile("key.pem", "invalid key content");
        File certFile = createTempPemFile("cert.pem", "invalid cert content");
        Configuration configuration = configuration();
        configuration.privateKeyPath(keyFile.getAbsolutePath());
        configuration.x509CertificatePath(certFile.getAbsolutePath());

        // when / then — error message includes the configured paths for diagnostics
        try {
            new NettySslContextFactory(configuration, new MockServerLogger(), true).createServerSslContext();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("privateKeyPath=\"" + keyFile.getAbsolutePath() + "\""));
            assertThat(e.getMessage(), containsString("x509CertificatePath=\"" + certFile.getAbsolutePath() + "\""));
        }
    }

    @Test
    public void shouldThrowWhenClientSslContextCreatedWithInvalidForwardProxyKey() throws IOException {
        // given — point forwardProxyPrivateKey to a file with garbage content
        File keyFile = createTempPemFile("proxy-key.pem", "not a real private key");
        Configuration configuration = configuration();
        configuration.forwardProxyPrivateKey(keyFile.getAbsolutePath());

        // when / then — createClientSslContext wraps with descriptive message
        try {
            new NettySslContextFactory(configuration, new MockServerLogger(), false).createClientSslContext(true, false);
            fail("expected RuntimeException for invalid forward proxy key");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Exception creating SSL context for client"));
        }
    }

    // ---- per-host outbound mTLS cert/key resolver ----

    @Test
    public void shouldResolveNullPerHostCertificateForBlankHostOrMapping() {
        assertThat(NettySslContextFactory.resolveForwardProxyClientCertificate("h=/c.pem;/k.pem", null), is(nullValue()));
        assertThat(NettySslContextFactory.resolveForwardProxyClientCertificate("h=/c.pem;/k.pem", ""), is(nullValue()));
        assertThat(NettySslContextFactory.resolveForwardProxyClientCertificate("", "h"), is(nullValue()));
        assertThat(NettySslContextFactory.resolveForwardProxyClientCertificate(null, "h"), is(nullValue()));
    }

    @Test
    public void shouldResolvePerHostCertificateForMatchingHost() {
        String[] result = NettySslContextFactory.resolveForwardProxyClientCertificate("api.internal=/certs/c.pem;/certs/k.pem", "api.internal");
        assertThat(result, is(new String[]{"/certs/c.pem", "/certs/k.pem"}));
    }

    @Test
    public void shouldMatchHostCaseInsensitivelyAndTrimWhitespace() {
        String[] result = NettySslContextFactory.resolveForwardProxyClientCertificate("  API.Internal = /certs/c.pem ; /certs/k.pem  ", "api.internal");
        assertThat(result, is(new String[]{"/certs/c.pem", "/certs/k.pem"}));
    }

    @Test
    public void shouldReturnNullWhenNoHostMatches() {
        assertThat(NettySslContextFactory.resolveForwardProxyClientCertificate("a=/c.pem;/k.pem", "b"), is(nullValue()));
    }

    @Test
    public void shouldSelectCorrectEntryFromMultipleAndSkipMalformedEntries() {
        String mapping = "noequals,blankcert==;/k.pem,nosemicolon=/only.pem,a=/ac.pem;/ak.pem,b=/bc.pem;/bk.pem";
        assertThat(NettySslContextFactory.resolveForwardProxyClientCertificate(mapping, "a"), is(new String[]{"/ac.pem", "/ak.pem"}));
        assertThat(NettySslContextFactory.resolveForwardProxyClientCertificate(mapping, "b"), is(new String[]{"/bc.pem", "/bk.pem"}));
        assertThat(NettySslContextFactory.resolveForwardProxyClientCertificate(mapping, "nosemicolon"), is(nullValue()));
    }

    // ---- per-host outbound mTLS context selection & caching ----

    @Test
    public void shouldShareClientSslContextAcrossUnmappedHosts() {
        // given — no per-host mapping configured
        NettySslContextFactory factory = new NettySslContextFactory(configuration(), new MockServerLogger(), false);

        // when — two different upstream hosts, neither mapped
        SslContext contextA = factory.createClientSslContext(true, false, "a.example.com");
        SslContext contextB = factory.createClientSslContext(true, false, "b.example.com");

        // then — both share the single global client context (cache does not grow per host)
        assertThat(contextA, sameInstance(contextB));
    }

    @Test
    public void shouldSelectPerHostForwardProxyCertificateOnlyForMappedHost() throws IOException {
        // given — a per-host mapping whose cert/key point at garbage PEM content
        File certFile = createTempPemFile("host-cert.pem", "not a real certificate");
        File keyFile = createTempPemFile("host-key.pem", "not a real private key");
        Configuration configuration = configuration()
            .forwardProxyClientCertificatesByHost("secure.host=" + certFile.getAbsolutePath() + ";" + keyFile.getAbsolutePath());
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), false);

        // when / then — the mapped host selects the (garbage) per-host key and fails to build,
        // proving per-host selection engaged
        try {
            factory.createClientSslContext(true, false, "secure.host");
            fail("expected RuntimeException for invalid per-host forward proxy key");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Exception creating SSL context for client"));
        }

        // and an unmapped host falls back to the valid global/default pair and builds successfully
        assertThat(factory.createClientSslContext(true, false, "unmapped.host"), is(notNullValue()));
    }

    // ---- outbound host name verification (HTTPS endpoint identification) ----

    @Test
    public void shouldEnableHttpsEndpointIdentificationForJvmTrustWhenHostnameVerificationEnabledOnConfigurationInstance() {
        // enabled (default) on a Configuration instance -> the outbound engine performs HTTPS host name
        // verification on top of chain validation
        Configuration enabled = configuration()
            .forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM)
            .forwardProxyTLSHostnameVerificationEnabled(true);
        SslContext enabledContext = new NettySslContextFactory(enabled, new MockServerLogger(), false)
            .createClientSslContext(true, false, "example.com");
        assertThat(endpointIdentificationAlgorithm(enabledContext), is("HTTPS"));

        // disabling it on a Configuration instance is observable: the engine no longer verifies the host name
        Configuration disabled = configuration()
            .forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM)
            .forwardProxyTLSHostnameVerificationEnabled(false);
        SslContext disabledContext = new NettySslContextFactory(disabled, new MockServerLogger(), false)
            .createClientSslContext(true, false, "example.com");
        assertThat(endpointIdentificationAlgorithm(disabledContext), is(nullValue()));
    }

    @Test
    public void shouldLeaveAnyTrustManagerUntouchedByHostnameVerification() {
        // ANY uses the insecure trust-all manager, so MockServer must NOT wrap it with the verification
        // control at all — Netty may default the engine's algorithm to HTTPS, but the insecure trust
        // manager makes that a no-op. The ANY context is therefore the raw, unwrapped one, whereas a
        // JVM/CUSTOM context is wrapped in a DelegatingSslContext.
        Configuration any = configuration()
            .forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.ANY)
            .forwardProxyTLSHostnameVerificationEnabled(true);
        SslContext anyContext = new NettySslContextFactory(any, new MockServerLogger(), false)
            .createClientSslContext(true, false, "example.com");
        assertThat(anyContext, is(not(instanceOf(io.netty.handler.ssl.DelegatingSslContext.class))));

        Configuration jvm = configuration()
            .forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM);
        SslContext jvmContext = new NettySslContextFactory(jvm, new MockServerLogger(), false)
            .createClientSslContext(true, false, "example.com");
        assertThat(jvmContext, is(instanceOf(io.netty.handler.ssl.DelegatingSslContext.class)));
    }

    @Test
    public void shouldClearHostnameVerificationOnNoHostHandlerWhenDisabled() {
        // Netty defaults HTTPS endpoint identification onto client engines (even the no-host reverse-proxy
        // relay handler), so disabling verification must ACTIVELY clear it on every newHandler overload,
        // not merely skip adding it
        Configuration configuration = configuration()
            .forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM)
            .forwardProxyTLSHostnameVerificationEnabled(false);
        SslContext clientContext = new NettySslContextFactory(configuration, new MockServerLogger(), false)
            .createClientSslContext(true, false, "example.com");
        SslHandler noHostHandler = clientContext.newHandler(UnpooledByteBufAllocator.DEFAULT);
        assertThat(noHostHandler.engine().getSSLParameters().getEndpointIdentificationAlgorithm(), is(nullValue()));
    }

    @Test
    public void shouldNotVerifyHostnameForServerContext() {
        // host name verification is an OUTBOUND (forward-proxy client) concern only; the inbound server
        // context must never have it applied
        Configuration configuration = configuration()
            .forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM)
            .forwardProxyTLSHostnameVerificationEnabled(true);
        SslContext serverContext = new NettySslContextFactory(configuration, new MockServerLogger(), true).createServerSslContext();
        SslHandler serverHandler = serverContext.newHandler(UnpooledByteBufAllocator.DEFAULT, "example.com", 443);
        assertThat(serverHandler.engine().getSSLParameters().getEndpointIdentificationAlgorithm(), is(nullValue()));
    }

    // ---- bundled default Certificate Authority detection (no global latch involved) ----

    @Test
    public void shouldDetectBundledDefaultCertificateAuthorityInUseByDefault() {
        NettySslContextFactory factory = new NettySslContextFactory(configuration(), new MockServerLogger(), true);
        assertThat(factory.usingBundledDefaultCertificateAuthority(), is(true));
    }

    @Test
    public void shouldNotDetectBundledCaWhenDynamicCaEnabled() {
        Configuration configuration = configuration().dynamicallyCreateCertificateAuthorityCertificate(true);
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        assertThat(factory.usingBundledDefaultCertificateAuthority(), is(false));
    }

    @Test
    public void shouldNotDetectBundledCaWhenFixedLeafCertificateSupplied() throws IOException {
        File keyFile = createTempPemFile("key.pem", "placeholder");
        File certFile = createTempPemFile("cert.pem", "placeholder");
        Configuration configuration = configuration()
            .privateKeyPath(keyFile.getAbsolutePath())
            .x509CertificatePath(certFile.getAbsolutePath());
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        assertThat(factory.usingBundledDefaultCertificateAuthority(), is(false));
    }

    // ---- client context built from one read of its inputs ----

    @Test
    public void shouldBuildAndCacheClientContextFromTheTrustManagerTypeItIsKeyedBy() {
        TrustTypeChangingConfiguration configuration = new TrustTypeChangingConfiguration();
        configuration.forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM);
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), false);

        // a PUT switching to ANY lands right after the factory first reads the trust manager type
        configuration.changeAfterNextRead(ForwardProxyTLSX509CertificatesTrustManager.ANY);
        SslContext builtDuringPut = factory.createClientSslContext(true, false, "example.com");
        assertThat("built from the JVM value it read first, a validating (wrapped) context", builtDuringPut, is(instanceOf(io.netty.handler.ssl.DelegatingSslContext.class)));

        configuration.forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM);
        SslContext afterReturningToJvm = factory.createClientSslContext(true, false, "example.com");
        assertThat("a JVM configuration is never served the trust-all context", afterReturningToJvm, is(instanceOf(io.netty.handler.ssl.DelegatingSslContext.class)));
    }

    @Test
    public void shouldNotCacheClientContextWhoseInputsChangedWhileItWasBuilt() {
        Configuration configuration = configuration()
            .forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM)
            .forwardProxyTLSHostnameVerificationEnabled(true);
        java.util.concurrent.atomic.AtomicInteger builds = new java.util.concurrent.atomic.AtomicInteger();
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), false)
            .withClientSslContextBuilderFunction(builder -> {
                if (builds.incrementAndGet() == 1) {
                    // stands in for a CA change the key factory would read during the build
                    configuration.forwardProxyTLSHostnameVerificationEnabled(false);
                }
                return NettySslContextFactory.clientSslContextBuilderFunction.apply(builder);
            });

        factory.createClientSslContext(true, false, "example.com");
        configuration.forwardProxyTLSHostnameVerificationEnabled(true);
        factory.createClientSslContext(true, false, "example.com");

        assertThat("the context built while its inputs changed is not served again", builds.get(), is(2));
        factory.createClientSslContext(true, false, "example.com");
        assertThat("a context built from unchanged inputs is cached", builds.get(), is(2));
    }

    @Test
    public void shouldRebuildClientContextWhenHttp2IsDisabledAtRuntime() {
        Configuration configuration = configuration().http2Enabled(true);
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), false);
        SslContext http2Context = factory.createClientSslContext(true, true, "example.com");
        assertThat(http2Context.applicationProtocolNegotiator().protocols(), hasItem(ApplicationProtocolNames.HTTP_2));

        configuration.http2Enabled(false);
        SslContext http1Context = factory.createClientSslContext(true, true, "example.com");

        assertThat("the context advertising h2 is not served once HTTP/2 is disabled", http1Context, is(not(sameInstance(http2Context))));
        assertThat(http1Context.applicationProtocolNegotiator().protocols(), not(hasItem(ApplicationProtocolNames.HTTP_2)));
        assertThat(http1Context.applicationProtocolNegotiator().protocols(), hasItem(ApplicationProtocolNames.HTTP_1_1));
    }

    @Test
    public void shouldTrustOnlyTheControlPlaneCaChainForMockServerClientMutualTls() {
        String controlPlaneCaChain = "org/mockserver/authentication/mtls/separateca/ca.pem";
        X509Certificate mockServerCa = PEMToFile.x509FromPEMFile(configuration().certificateAuthorityCertificate());

        X509Certificate[] trusted = NettySslContextFactory.controlPlaneTrustCertificates(controlPlaneCaChain);

        assertThat(Arrays.asList(trusted), contains(PEMToFile.x509ChainFromPEMFile(controlPlaneCaChain).toArray()));
        assertThat(Arrays.asList(trusted), not(hasItem(mockServerCa)));
    }

    /**
     * Applies a trust manager type change straight after the next read of it, the position of a PUT that
     * lands just after the client context factory has read its inputs.
     */
    private static class TrustTypeChangingConfiguration extends Configuration {
        private volatile ForwardProxyTLSX509CertificatesTrustManager changeTo;

        void changeAfterNextRead(ForwardProxyTLSX509CertificatesTrustManager changeTo) {
            this.changeTo = changeTo;
        }

        @Override
        public ForwardProxyTLSX509CertificatesTrustManager forwardProxyTLSX509CertificatesTrustManagerType() {
            ForwardProxyTLSX509CertificatesTrustManager current = super.forwardProxyTLSX509CertificatesTrustManagerType();
            ForwardProxyTLSX509CertificatesTrustManager pending = changeTo;
            if (pending != null) {
                changeTo = null;
                forwardProxyTLSX509CertificatesTrustManagerType(pending);
            }
            return current;
        }
    }

    private static String endpointIdentificationAlgorithm(SslContext sslContext) {
        SslHandler handler = sslContext.newHandler(UnpooledByteBufAllocator.DEFAULT, "example.com", 443);
        return handler.engine().getSSLParameters().getEndpointIdentificationAlgorithm();
    }

    private File createTempPemFile(String name, String content) throws IOException {
        File file = tempFolder.newFile(name);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }
}
