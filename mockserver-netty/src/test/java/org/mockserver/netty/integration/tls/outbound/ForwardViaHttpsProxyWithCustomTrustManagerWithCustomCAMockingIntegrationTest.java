package org.mockserver.netty.integration.tls.outbound;

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
public class ForwardViaHttpsProxyWithCustomTrustManagerWithCustomCAMockingIntegrationTest extends AbstractForwardViaHttpsProxyMockingIntegrationTest {

    private static MockServer mockServer;
    private static MockServer proxy;
    private static ForwardProxyTLSX509CertificatesTrustManager originalForwardProxyTLSX509CertificatesTrustManager;
    private static String originalCertificateAuthorityCertificate;
    private static String originalCertificateAuthorityPrivateKey;
    private static String originalForwardProxyTLSCustomTrustX509Certificates;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalCertificateAuthorityCertificate = certificateAuthorityCertificate();
        originalCertificateAuthorityPrivateKey = certificateAuthorityPrivateKey();
        originalForwardProxyTLSX509CertificatesTrustManager = forwardProxyTLSX509CertificatesTrustManagerType();
        originalForwardProxyTLSCustomTrustX509Certificates = forwardProxyTLSCustomTrustX509Certificates();

        // set new certificate authority values
        certificateAuthorityCertificate("org/mockserver/netty/integration/tls/ca.pem");
        certificateAuthorityPrivateKey("org/mockserver/netty/integration/tls/ca-key-pkcs8.pem");
        forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.CUSTOM);
        forwardProxyTLSCustomTrustX509Certificates("org/mockserver/netty/integration/tls/ca.pem");

        proxy = new MockServer();
        mockServer = new MockServer(proxyConfiguration(ProxyConfiguration.Type.HTTPS, "127.0.0.1:" + proxy.getLocalPort()));

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort(), servletContext);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(proxy);
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);

        // set back to original value
        certificateAuthorityCertificate(originalCertificateAuthorityCertificate);
        certificateAuthorityPrivateKey(originalCertificateAuthorityPrivateKey);
        forwardProxyTLSX509CertificatesTrustManagerType(originalForwardProxyTLSX509CertificatesTrustManager);
        forwardProxyTLSCustomTrustX509Certificates(originalForwardProxyTLSCustomTrustX509Certificates);
    }

    @Override
    public int getServerPort() {
        return mockServer.getLocalPort();
    }

}
