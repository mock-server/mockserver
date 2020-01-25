package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;

import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ForwardWithCustomTrustManagerWithCustomCAMockingIntegrationTest extends AbstractForwardViaHttpsProxyMockingIntegrationTest {

    private static MockServer mockServer;
    private static ForwardProxyTLSX509CertificatesTrustManager originalForwardProxyTLSX509CertificatesTrustManager;
    private static String originalCertificateAuthorityCertificate;
    private static String originalCertificateAuthorityPrivateKey;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalCertificateAuthorityCertificate = certificateAuthorityCertificate();
        originalCertificateAuthorityPrivateKey = certificateAuthorityPrivateKey();
        originalForwardProxyTLSX509CertificatesTrustManager = forwardProxyTLSX509CertificatesTrustManager();

        // set new certificate authority values
        certificateAuthorityCertificate("org/mockserver/netty/integration/tls/ca.pem");
        certificateAuthorityPrivateKey("org/mockserver/netty/integration/tls/ca-key-pkcs8.pem");
        forwardProxyTLSX509CertificatesTrustManager(ForwardProxyTLSX509CertificatesTrustManager.CUSTOM.name());
        forwardProxyTLSCustomTrustX509Certificates("org/mockserver/netty/integration/tls/ca.pem");

        mockServer = new MockServer();

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort(), servletContext);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);

        // set back to original value
        certificateAuthorityCertificate(originalCertificateAuthorityCertificate);
        certificateAuthorityPrivateKey(originalCertificateAuthorityPrivateKey);
        forwardProxyTLSX509CertificatesTrustManager(originalForwardProxyTLSX509CertificatesTrustManager.name());
    }

    @Override
    public int getServerPort() {
        return mockServer.getLocalPort();
    }

}
