package org.mockserver.netty.integration.tls.inbound;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyStoreFactory;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.cert.X509Certificate;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Proves the ENFORCEMENT OUTCOME of enabling {@code tlsMutualAuthenticationRequired} at RUNTIME on a
 * live, already-listening MockServer, over a real TLS socket.
 *
 * <p>{@link org.mockserver.socket.tls.MutualAuthenticationRuntimeReconfigurationTest} proves only that the
 * cached {@code SslContext} instance is replaced when mTLS is toggled — its javadoc admits it cannot assert
 * the resulting {@code ClientAuth} through Netty's public API. The startup {@code ClientAuthentication*
 * IntegrationTest}s enable mTLS at STARTUP. Neither proves that after flipping mTLS on <em>at runtime</em>
 * a certificateless client is actually refused at the handshake. This test closes that gap by asserting the
 * wire-level outcome:
 * <ol>
 *   <li>with mTLS OFF, a certificateless TLS handshake to the data-plane port SUCCEEDS;</li>
 *   <li>after {@code tlsMutualAuthenticationRequired(true)} is applied at runtime, a NEW certificateless
 *       TLS handshake is REFUSED (fatal alert / {@link SSLException});</li>
 *   <li>a client presenting a certificate trusted by MockServer's CA still completes the handshake, proving
 *       the runtime change is {@code ClientAuth.REQUIRE} and not a blanket TLS breakage.</li>
 * </ol>
 *
 * <p>The default {@code tlsProtocols} negotiate TLSv1.2, under which a missing client certificate aborts the
 * handshake deterministically at {@link SSLSocket#startHandshake()} (rather than being deferred to the first
 * application read as TLSv1.3 post-handshake auth would). The client is pinned to TLSv1.2 so the assertion is
 * stable regardless of any future default protocol change.
 */
public class MutualAuthenticationRuntimeReconfigurationIntegrationTest {

    private static final int HANDSHAKE_TIMEOUT_MILLIS = 20_000;

    private Configuration configuration;
    private ClientAndServer mockServer;

    @Before
    public void startServer() {
        // a fresh server per test, always started with mTLS OFF, so each test exercises a genuine
        // runtime OFF -> ON transition on an already-listening instance with no cross-test state
        // the client certificate below is signed by the bundled CA
        configuration = configuration().tlsMutualAuthenticationAllowBundledCa(true).tlsMutualAuthenticationRequired(false);
        mockServer = ClientAndServer.startClientAndServer(configuration);
    }

    @After
    public void stopServer() {
        stopQuietly(mockServer);
    }

    @Test
    public void shouldRefuseCertificatelessClientAfterEnablingMutualAuthenticationAtRuntime() throws Exception {
        int port = mockServer.getPort();

        // given - mTLS OFF, a certificateless client completes the handshake
        assertHandshakeSucceeds(certificatelessSslContext(), port);

        // when - mTLS is required at runtime on the already-listening server
        configuration.tlsMutualAuthenticationRequired(true);

        // then - a NEW certificateless connection is refused at the handshake (enforcement outcome over the wire)
        assertHandshakeRefused(certificatelessSslContext(), port);
    }

    @Test
    public void shouldStillAcceptClientWithTrustedCertificateAfterEnablingMutualAuthenticationAtRuntime() throws Exception {
        int port = mockServer.getPort();

        // when - mTLS is required at runtime
        configuration.tlsMutualAuthenticationRequired(true);

        // then - a client presenting a certificate trusted by MockServer's CA still completes the handshake,
        // proving the runtime change is ClientAuth.REQUIRE (selective) rather than a total TLS breakage
        assertHandshakeSucceeds(new KeyStoreFactory(configuration(), new MockServerLogger()).sslContext(), port);
    }

    private void assertHandshakeSucceeds(SSLContext sslContext, int port) throws IOException {
        try (SSLSocket socket = openSocket(sslContext, port)) {
            socket.startHandshake();
            assertThat("TLS session must be established", socket.getSession().isValid(), is(true));
        }
    }

    private void assertHandshakeRefused(SSLContext sslContext, int port) throws IOException {
        try (SSLSocket socket = openSocket(sslContext, port)) {
            socket.startHandshake();
            fail("certificateless client should be refused once mutual authentication is required at runtime, "
                + "but the handshake completed with session valid=" + socket.getSession().isValid());
        } catch (SSLException | SocketException expected) {
            // The server aborts the handshake because the client presented no certificate under
            // ClientAuth.REQUIRE. Depending on timing this surfaces either as a fatal TLS alert the client
            // reads (SSLException) or as the server closing the socket before the client finishes writing its
            // handshake records (SocketException: "Broken pipe" / "Connection reset" — seen on CI). Both mean
            // the handshake was refused; only a COMPLETED handshake (the fail() above) means mTLS did not
            // take effect at runtime, which is the enforcement bug this test guards.
        }
    }

    private SSLSocket openSocket(SSLContext sslContext, int port) throws IOException {
        SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
        try {
            socket.connect(new InetSocketAddress("localhost", port), HANDSHAKE_TIMEOUT_MILLIS);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);
            socket.setEnabledProtocols(new String[]{"TLSv1.2"});
            SSLParameters sslParameters = socket.getSSLParameters();
            sslParameters.setServerNames(Collections.singletonList(new SNIHostName("localhost")));
            socket.setSSLParameters(sslParameters);
            return socket;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    private SSLContext certificatelessSslContext() throws Exception {
        // no KeyManager => the client presents no certificate; trust-all so server-cert validation never
        // masks the client-authentication outcome under test
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{TRUST_ALL}, null);
        return sslContext;
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
