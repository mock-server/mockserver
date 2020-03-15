package org.mockserver.netty.integration.mock.tls.outbound;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.netty.MockServer;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;

import static org.mockserver.configuration.ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ForwardWithAnyTrustManagerMockingIntegrationTest extends AbstractForwardViaHttpsProxyMockingIntegrationTest {

    private static MockServer mockServer;
    private static ForwardProxyTLSX509CertificatesTrustManager originalForwardProxyTLSX509CertificatesTrustManager;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalForwardProxyTLSX509CertificatesTrustManager = ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType();

        forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.ANY.name());

        mockServer = new MockServer();

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort(), servletContext);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);

        // set back to original value
        forwardProxyTLSX509CertificatesTrustManagerType(originalForwardProxyTLSX509CertificatesTrustManager.name());
    }

    @Override
    public int getServerPort() {
        return mockServer.getLocalPort();
    }

}
