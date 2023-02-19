package org.mockserver.netty.integration.tls.inbound;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.testing.integration.mock.AbstractBasicMockingSameJVMIntegrationTest;

import static org.mockserver.configuration.ConfigurationProperties.tlsProtocols;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class CustomTLSProtocolsMockingIntegrationTest extends AbstractBasicMockingSameJVMIntegrationTest {

    private static String originalTlsProtocols;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalTlsProtocols = tlsProtocols();

        // set new certificate authority values
        tlsProtocols("TLSv1.2,TLSv1.3");

        mockServerClient = ClientAndServer.startClientAndServer().withSecure(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        tlsProtocols(originalTlsProtocols);
    }

    @Override
    public int getServerPort() {
        return mockServerClient.getPort();
    }

}
