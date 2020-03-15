package org.mockserver.netty.integration.mock.tls.outbound;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;

import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ForwardWithCustomTrustManagerWithMockServerCAMockingIntegrationTest extends AbstractForwardViaHttpsProxyMockingIntegrationTest {

    private static MockServer mockServer;
    private static ForwardProxyTLSX509CertificatesTrustManager originalForwardProxyTLSX509CertificatesTrustManager;
    private static String originalForwardProxyTLSCustomTrustX509Certificates;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalForwardProxyTLSX509CertificatesTrustManager = forwardProxyTLSX509CertificatesTrustManagerType();
        originalForwardProxyTLSCustomTrustX509Certificates = forwardProxyTLSCustomTrustX509Certificates();

        forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.CUSTOM.name());
        forwardProxyTLSCustomTrustX509Certificates(certificateAuthorityCertificate());

        mockServer = new MockServer();

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort(), servletContext);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);

        // set back to original value
        forwardProxyTLSX509CertificatesTrustManagerType(originalForwardProxyTLSX509CertificatesTrustManager.name());
        forwardProxyTLSCustomTrustX509Certificates(originalForwardProxyTLSCustomTrustX509Certificates);
    }

    @Override
    public int getServerPort() {
        return mockServer.getLocalPort();
    }

}
