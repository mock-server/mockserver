package org.mockserver.integration.mocking;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServerMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static int mockServerPort;

    @BeforeClass
    public static void startServer() {
        mockServerClient = startClientAndServer();
        mockServerPort = ((ClientAndServer) mockServerClient).getLocalPort();
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

    @Override
    public int getEchoServerPort() {
        return insecureEchoServer.getPort();
    }
}
