package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;

import static org.mockserver.configuration.ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManager;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ForwardWithJVMTrustManagerMockingIntegrationTest extends AbstractForwardViaHttpsProxyMockingIntegrationTest {

    private static MockServer mockServer;
    private static ForwardProxyTLSX509CertificatesTrustManager originalForwardProxyTLSX509CertificatesTrustManager;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalForwardProxyTLSX509CertificatesTrustManager = forwardProxyTLSX509CertificatesTrustManager();

        forwardProxyTLSX509CertificatesTrustManager(ForwardProxyTLSX509CertificatesTrustManager.JVM.name());

        mockServer = new MockServer();

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort(), servletContext);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);

        // set back to original value
        forwardProxyTLSX509CertificatesTrustManager(originalForwardProxyTLSX509CertificatesTrustManager.name());
    }

    @Override
    public int getServerPort() {
        return mockServer.getLocalPort();
    }

}
