package org.mockserver.integration.mocking;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;
import org.mockserver.socket.PortFactory;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class PreAllocatedPortMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static int severHttpPort = PortFactory.findFreePort();

    @BeforeClass
    public static void startServer() {
        mockServerClient = startClientAndServer(severHttpPort);
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

    @Override
    public int getEchoServerPort() {
        return insecureEchoServer.getPort();
    }
}
